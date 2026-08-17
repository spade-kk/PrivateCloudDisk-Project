"""
RabbitMQ 消息队列服务 - 支持多队列独立并发控制

核心改进：
- 每个队列独立 prefetch_count，避免重型任务阻塞其他消息
- 支持并发消息处理（asyncio 协程级并发）
- 独立 Worker 进程专用，与 FastAPI 主进程完全解耦
"""
import json
import asyncio
import time
import logging
import aio_pika
import aio_pika.exceptions
from aio_pika import Message, ExchangeType, DeliveryMode
from core.config import settings
from app.core.logging_config import get_logger

logger = get_logger("core.rabbitmq")


class RabbitMQService:
    """RabbitMQ 消息队列服务 (支持 DLX/DLQ + 独立并发控制)"""

    def __init__(self):
        self.connection = None
        self.channel = None
        self.exchanges = {}
        self._consumer_channels = []
        self._consumer_tags = []
        self._stopping = False

    async def connect(self):
        """建立 RabbitMQ 连接"""
        try:
            connection_url = (
                f"amqp://{settings.rabbitmq_username}:{settings.rabbitmq_password}"
                f"@{settings.rabbitmq_host}:{settings.rabbitmq_port}/{settings.rabbitmq_vhost}"
            )

            self._stopping = False
            self.connection = await aio_pika.connect_robust(
                connection_url,
                heartbeat=60,
            )
            # 创建一个专用 channel 用于声明拓扑
            self.channel = await self.connection.channel()

            await self._declare_all()
            logger.info("RabbitMQ 连接成功")
        except Exception as e:
            logger.error(f"RabbitMQ 连接失败: {e}", exc_info=True)
            raise

    async def _declare_all(self):
        """
        声明所有交换机、队列、绑定关系

        架构:
        ┌─────────────────────────────────────────────────────┐
        │  pcd.file.delete.exchange (DIRECT)                  │
        │  ├── pcd.file.delete.queue                          │
        │  │   └── DLX → pcd.file.delete.dlx                  │
        │  └── pcd.file.delete.dlq                            │
        │                                                      │
        │  pcd.file.backend.exchange (DIRECT)                  │
        │  ├── backend stage task queues                       │
        │  └── DLX → pcd.file.backend.dlx                      │
        │                                                      │
        │  pcd.file.enhance.exchange (DIRECT)             │
        │  └── enhancement task queues                         │
        └─────────────────────────────────────────────────────┘
        """
        # ========== 文件删除交换机 ==========
        fd_exchange = await self.channel.declare_exchange(
            settings.file_delete_exchange,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.exchanges[settings.file_delete_exchange] = fd_exchange

        # ========== 文件删除死信交换机 ==========
        fd_dlx = await self.channel.declare_exchange(
            settings.file_delete_dlx,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.exchanges[settings.file_delete_dlx] = fd_dlx

        # ========== 文件删除主队列 (绑定 DLX) ==========
        fd_queue = await self._declare_queue_safe(
            settings.file_delete_queue,
            arguments={
                "x-message-ttl": 259200000,  # 3 天
                "x-dead-letter-exchange": settings.file_delete_dlx,
                "x-dead-letter-routing-key": settings.file_delete_dlq_routing_key,
            },
        )
        await fd_queue.bind(
            fd_exchange,
            routing_key=settings.file_delete_routing_key,
        )

        # Sprint 0 安全基线（永久删除可靠性）：
        # 使用 RabbitMQ TTL 队列承载指数退避，避免文件 IO 失败时在消费者协程中阻塞等待。
        fd_retry_queue = await self._declare_queue_safe(
            f"{settings.file_delete_queue}.retry",
            arguments={
                "x-queue-type": "quorum",
                "x-message-ttl": 604800000,
                "x-dead-letter-exchange": settings.file_delete_exchange,
                "x-dead-letter-routing-key": settings.file_delete_routing_key,
            },
        )
        await fd_retry_queue.bind(
            fd_exchange,
            routing_key=f"{settings.file_delete_routing_key}.retry",
        )

        # ========== 文件删除死信队列 ==========
        fd_dlq = await self._declare_queue_safe(
            settings.file_delete_dlq,
            arguments={
                "x-message-ttl": 2592000000,  # 30 天
            },
        )
        await fd_dlq.bind(
            fd_dlx,
            routing_key=settings.file_delete_dlq_routing_key,
        )

        # ========== 上传会话事件交换机（与 Spring Boot 主业务服务一致） ==========
        ue_exchange = await self.channel.declare_exchange(
            settings.uploads_event_exchange,
            ExchangeType.TOPIC,
            durable=True,
        )
        self.exchanges[settings.uploads_event_exchange] = ue_exchange

        # ========== 上传会话事件死信交换机 ==========
        ue_dlx = await self.channel.declare_exchange(
            settings.uploads_event_dlx,
            ExchangeType.TOPIC,
            durable=True,
        )
        self.exchanges[settings.uploads_event_dlx] = ue_dlx

        # ========== 上传会话删除队列（文件存储服务消费 → 删除物理分块文件） ==========
        usd_queue = await self._declare_queue_safe(
            settings.uploads_session_delete_queue,
            arguments={
                "x-message-ttl": 86400000,  # 1 天
                "x-dead-letter-exchange": settings.uploads_event_dlx,
                "x-dead-letter-routing-key": settings.uploads_event_dlq_routing_key,
            },
        )
        await usd_queue.bind(
            ue_exchange,
            routing_key=settings.uploads_session_delete_routing_key,
        )

        # ========== 上传会话事件死信队列 ==========
        ue_dlq = await self._declare_queue_safe(
            settings.uploads_event_dlq,
            arguments={
                "x-message-ttl": 2592000000,  # 30 天
            },
        )
        await ue_dlq.bind(
            ue_dlx,
            routing_key=settings.uploads_event_dlq_routing_key,
        )
        uploads_retry_queue = await self._declare_queue_safe(
            settings.uploads_event_retry_queue,
            arguments={
                "x-message-ttl": 604800000,
                "x-dead-letter-exchange": settings.uploads_event_dlx,
                "x-dead-letter-routing-key": settings.uploads_event_dlq_routing_key,
            },
        )
        await uploads_retry_queue.bind(
            ue_dlx,
            routing_key=settings.uploads_event_retry_routing_key,
        )

        # ========== 上传会话已删除队列（主业务服务消费 → 释放配额） ==========
        # 虽然主业务服务会声明此队列，但存储服务也需声明以确保拓扑完整
        # （防止存储服务先启动时发布消息无队列接收）
        us_deleted_queue = await self._declare_queue_safe(
            settings.uploads_session_deleted_queue,
            arguments={
                "x-message-ttl": 259200000,  # 3 天，与 Spring Boot 一致
                "x-dead-letter-exchange": settings.uploads_event_dlx,
                "x-dead-letter-routing-key": settings.uploads_event_dlq_routing_key,
            },
        )
        await us_deleted_queue.bind(
            ue_exchange,
            routing_key=settings.uploads_session_deleted_routing_key,
        )

        # ========== 文件事件交换机（与 Spring Boot 主业务服务一致） ==========
        # 存储服务负责发布这些事件，主业务服务负责消费
        fe_exchange = await self.channel.declare_exchange(
            settings.file_event_exchange,
            ExchangeType.TOPIC,
            durable=True,
        )
        self.exchanges[settings.file_event_exchange] = fe_exchange

        # 安全隔离属于文件事件域，绑定当前 file event exchange；不参与后台 Task Bus 阶段编排。
        security_queue = await self._declare_queue_safe(
            settings.security_quarantine_queue,
            arguments={"x-message-ttl": 2592000000},
        )
        await security_queue.bind(
            fe_exchange,
            routing_key=settings.security_quarantine_routing_key,
        )

        # ========== 文件事件死信交换机 ==========
        fe_dlx = await self.channel.declare_exchange(
            settings.file_event_dlx,
            ExchangeType.TOPIC,
            durable=True,
        )
        self.exchanges[settings.file_event_dlx] = fe_dlx

        # ========== 文件可获得队列（由主业务服务消费，此处声明以确保拓扑完整） ==========
        fa_queue = await self._declare_queue_safe(
            settings.file_available_queue,
            arguments={
                "x-message-ttl": 604800000,  # 7 天
                "x-dead-letter-exchange": settings.file_event_dlx,
                "x-dead-letter-routing-key": settings.file_event_dlq_routing_key,
            },
        )
        await fa_queue.bind(
            fe_exchange,
            routing_key=settings.file_available_routing_key,
        )

        # ========== 文件合并失败队列 ==========
        fmf_queue = await self._declare_queue_safe(
            settings.file_merge_failed_queue,
            arguments={
                "x-message-ttl": 604800000,  # 7 天
                "x-dead-letter-exchange": settings.file_event_dlx,
                "x-dead-letter-routing-key": settings.file_event_dlq_routing_key,
            },
        )
        await fmf_queue.bind(
            fe_exchange,
            routing_key=settings.file_merge_failed_routing_key,
        )

        # ========== 文件扫毒失败队列 ==========
        fsf_queue = await self._declare_queue_safe(
            settings.file_scan_failed_queue,
            arguments={
                "x-message-ttl": 604800000,  # 7 天
                "x-dead-letter-exchange": settings.file_event_dlx,
                "x-dead-letter-routing-key": settings.file_event_dlq_routing_key,
            },
        )
        await fsf_queue.bind(
            fe_exchange,
            routing_key=settings.file_scan_failed_routing_key,
        )

        # ========== 文件下载完成队列（由主业务服务消费，此处声明以确保拓扑完整） ==========
        fd_queue = await self._declare_queue_safe(
            settings.file_downloaded_queue,
            arguments={
                "x-message-ttl": 259200000,  # 3 天
                "x-dead-letter-exchange": settings.file_event_dlx,
                "x-dead-letter-routing-key": settings.file_event_dlq_routing_key,
            },
        )
        await fd_queue.bind(
            fe_exchange,
            routing_key=settings.file_downloaded_routing_key,
        )

        # ========== 文件事件死信队列 ==========
        fe_dlq = await self._declare_queue_safe(
            settings.file_event_dlq,
            arguments={
                "x-message-ttl": 2592000000,  # 30 天
            },
        )
        await fe_dlq.bind(
            fe_dlx,
            routing_key=settings.file_event_dlq_routing_key,
        )

        # ========== 新版文件后台处理拓扑（Backend — 顺序流水线） ==========
        # 每个阶段独立 exchange + queue + DLQ
        await self._declare_backend_topology()

        # ========== 文件内容预处理生命周期拓扑 ==========
        # 需求：merge 与最终 hash 之间新增 fail-open 的插件预处理闸门。
        await self._declare_file_lifecycle_topology()

        # ========== 新版文件增强处理拓扑（Enhancement — 并发流水线） ==========
        # 每个阶段独立 exchange + queue + DLQ
        await self._declare_enhance_topology()

        logger.info(
            f"RabbitMQ 拓扑声明完成: "
            f"exchanges={len(self.exchanges)}"
        )

    async def _declare_backend_topology(self):
        """声明 Backend Task Bus 顺序流水线及可持久化 retry 回流拓扑。

        REQ-WORKER-TASKBUS-2026-07：恢复原有 backend task exchange/queue。每个任务主队列
        绑定一个 retry 队列；retry 消息通过消息 expiration 或队列 TTL 到期后由 RabbitMQ
        dead-letter 回原任务 routing key，不由消费者 sleep 或直接重新投递主队列。
        """
        backend_stages = [
            (settings.file_backend_merge_queue, settings.file_backend_merge_routing_key,
             settings.file_backend_merge_dlq, settings.file_backend_merge_dlq_routing_key),
            (settings.file_backend_hash_queue, settings.file_backend_hash_routing_key,
             settings.file_backend_hash_dlq, settings.file_backend_hash_dlq_routing_key),
            (settings.file_backend_virus_queue, settings.file_backend_virus_routing_key,
             settings.file_backend_virus_dlq, settings.file_backend_virus_dlq_routing_key),
            (settings.file_backend_mark_active_queue, settings.file_backend_mark_active_routing_key,
             settings.file_backend_mark_active_dlq, settings.file_backend_mark_active_dlq_routing_key),
        ]

        backend_exchange = await self.channel.declare_exchange(
            settings.file_backend_exchange,
            ExchangeType.DIRECT,
            durable=True,
        )
        backend_dlx = await self.channel.declare_exchange(
            settings.file_backend_dlx,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.exchanges[settings.file_backend_exchange] = backend_exchange
        self.exchanges[settings.file_backend_dlx] = backend_dlx

        for queue_name, routing_key, dlq_name, dlq_routing_key in backend_stages:
            queue = await self._declare_queue_safe(
                queue_name,
                arguments={
                    "x-message-ttl": 604800000,
                    "x-dead-letter-exchange": settings.file_backend_dlx,
                    "x-dead-letter-routing-key": dlq_routing_key,
                },
            )
            await queue.bind(backend_exchange, routing_key=routing_key)

            # REQ-WORKER-TASKBUS-2026-07：retry 队列必须具备明确 TTL、DLX 和原任务回流键；
            # 仅把消息发布到 .retry 路由而不声明这些参数时，消息会永久停留在 retry 队列。
            retry_queue = await self._declare_queue_safe(
                f"{queue_name}.retry",
                arguments={
                    "x-queue-type": "quorum",
                    "x-message-ttl": 604800000,
                    "x-dead-letter-exchange": settings.file_backend_exchange,
                    "x-dead-letter-routing-key": routing_key,
                },
            )
            await retry_queue.bind(backend_exchange, routing_key=f"{routing_key}.retry")

            dlq = await self._declare_queue_safe(
                dlq_name,
                arguments={"x-message-ttl": 2592000000},
            )
            await dlq.bind(backend_dlx, routing_key=dlq_routing_key)

        logger.info(
            "Backend Task Bus 拓扑声明完成: stages=%s exchange=%s",
            len(backend_stages),
            settings.file_backend_exchange,
        )

    async def _declare_file_lifecycle_topology(self):
        """声明 file.content.ready/processed/timeout 的可靠消息拓扑。

        ready 队列由 Automation 消费，但 Storage 同样声明它以避免服务启动顺序导致消息
        无路由。timeout delay queue 是固定 TTL 逃生哨兵；数据库 sweeper 是第二条逃生
        路径，两者最终调用同一个 Gate CAS。
        """
        lifecycle_exchange = await self.channel.declare_exchange(
            settings.file_lifecycle_exchange,
            ExchangeType.TOPIC,
            durable=True,
        )
        lifecycle_dlx = await self.channel.declare_exchange(
            settings.file_lifecycle_dlx,
            ExchangeType.TOPIC,
            durable=True,
        )
        self.exchanges[settings.file_lifecycle_exchange] = lifecycle_exchange
        self.exchanges[settings.file_lifecycle_dlx] = lifecycle_dlx

        quorum_common = {
            "x-queue-type": "quorum",
            "x-message-ttl": 604800000,  # 正常生命周期消息最多保留 7 天
        }

        ready_queue = await self._declare_queue_safe(
            settings.file_content_ready_queue,
            arguments={
                **quorum_common,
                "x-dead-letter-exchange": settings.file_lifecycle_dlx,
                "x-dead-letter-routing-key": settings.file_content_ready_dlq_routing_key,
            },
        )
        await ready_queue.bind(
            lifecycle_exchange,
            routing_key=settings.file_content_ready_routing_key,
        )

        processed_queue = await self._declare_queue_safe(
            settings.file_content_processed_queue,
            arguments={
                **quorum_common,
                "x-dead-letter-exchange": settings.file_lifecycle_dlx,
                "x-dead-letter-routing-key": settings.file_content_processed_dlq_routing_key,
            },
        )
        await processed_queue.bind(
            lifecycle_exchange,
            routing_key=settings.file_content_processed_routing_key,
        )

        # 不依赖 delayed-message 插件：消费者把暂时性失败按 attempt 发布到对应固定 TTL
        # 队列，TTL 到期后 RabbitMQ 再投回 processed 主路由，不占用 Worker 并发槽。
        retry_delays = [
            int(value.strip())
            for value in settings.file_content_processed_retry_delays_seconds.split(",")
            if value.strip()
        ]
        for attempt, delay_seconds in enumerate(retry_delays, start=1):
            retry_routing_key = f"{settings.file_content_processed_routing_key}.retry.{attempt}"
            retry_queue = await self._declare_queue_safe(
                f"{settings.file_content_processed_queue}.retry.{delay_seconds}s",
                arguments={
                    "x-message-ttl": delay_seconds * 1000,
                    "x-dead-letter-exchange": settings.file_lifecycle_exchange,
                    "x-dead-letter-routing-key": settings.file_content_processed_routing_key,
                },
            )
            await retry_queue.bind(
                lifecycle_exchange,
                routing_key=retry_routing_key,
            )

        # 固定 TTL 队列仅承担延迟，不配置消费者；到期后路由到真正 timeout 队列。
        timeout_delay_queue = await self._declare_queue_safe(
            settings.file_content_timeout_delay_queue,
            arguments={
                "x-message-ttl": settings.file_preprocess_deadline_seconds * 1000,
                "x-dead-letter-exchange": settings.file_lifecycle_exchange,
                "x-dead-letter-routing-key": settings.file_content_timeout_routing_key,
            },
        )
        await timeout_delay_queue.bind(
            lifecycle_exchange,
            routing_key=settings.file_content_timeout_schedule_routing_key,
        )

        timeout_queue = await self._declare_queue_safe(
            settings.file_content_timeout_queue,
            arguments={
                **quorum_common,
                "x-dead-letter-exchange": settings.file_lifecycle_dlx,
                "x-dead-letter-routing-key": settings.file_content_processed_dlq_routing_key,
            },
        )
        await timeout_queue.bind(
            lifecycle_exchange,
            routing_key=settings.file_content_timeout_routing_key,
        )

        ready_dlq = await self._declare_queue_safe(
            settings.file_content_ready_dlq,
            arguments={"x-queue-type": "quorum", "x-message-ttl": 2592000000},
        )
        await ready_dlq.bind(
            lifecycle_dlx,
            routing_key=settings.file_content_ready_dlq_routing_key,
        )
        processed_dlq = await self._declare_queue_safe(
            settings.file_content_processed_dlq,
            arguments={"x-queue-type": "quorum", "x-message-ttl": 2592000000},
        )
        await processed_dlq.bind(
            lifecycle_dlx,
            routing_key=settings.file_content_processed_dlq_routing_key,
        )

        logger.info(
            "文件内容预处理拓扑声明完成: exchange=%s ready=%s processed=%s timeout=%ss",
            settings.file_lifecycle_exchange,
            settings.file_content_ready_queue,
            settings.file_content_processed_queue,
            settings.file_preprocess_deadline_seconds,
        )

    async def _declare_enhance_topology(self):
        """
        声明文件增强 Task Bus 拓扑（并发任务）

        拓扑结构:
        ┌─────────────────────────────────────────────────────────────┐
        │  pcd.file.enhance.exchange (DIRECT)                        │
        │  ├── pcd.file.enhance.thumbnail.queue                      │
        │  │   └── DLX → pcd.file.enhance.dlx → .thumbnail.dlq      │
        │  ├── pcd.file.enhance.transcode.queue                      │
        │  │   └── DLX → pcd.file.enhance.dlx → .transcode.dlq      │
        │  ├── pcd.file.enhance.hls.queue                            │
        │  │   └── DLX → pcd.file.enhance.dlx → .hls.dlq            │
        │  └── pcd.file.enhance.index.queue                          │
        │      └── DLX → pcd.file.enhance.dlx → .index.dlq          │
        │                                                              │
        │  所有增强阶段拥有独立 file.enhance.* 任务队列，互不阻塞 │
        │  由 mark_active 消费者按文件类型投递任务                   │
        └─────────────────────────────────────────────────────────────┘
        """
        enhance_stages = [
            {
                "queue": settings.file_enhance_thumbnail_queue,
                "rk": settings.file_enhance_thumbnail_routing_key,
                "dlq": settings.file_enhance_thumbnail_dlq,
                "dlq_rk": settings.file_enhance_thumbnail_dlq_routing_key,
            },
            {
                "queue": settings.file_enhance_transcode_queue,
                "rk": settings.file_enhance_transcode_routing_key,
                "dlq": settings.file_enhance_transcode_dlq,
                "dlq_rk": settings.file_enhance_transcode_dlq_routing_key,
            },
            {
                "queue": settings.file_enhance_hls_queue,
                "rk": settings.file_enhance_hls_routing_key,
                "dlq": settings.file_enhance_hls_dlq,
                "dlq_rk": settings.file_enhance_hls_dlq_routing_key,
            },
            {
                "queue": settings.file_enhance_index_queue,
                "rk": settings.file_enhance_index_routing_key,
                "dlq": settings.file_enhance_index_dlq,
                "dlq_rk": settings.file_enhance_index_dlq_routing_key,
            },
            # Office 文件转 PDF 增强阶段
            {
                "queue": settings.file_enhance_office_to_pdf_queue,
                "rk": settings.file_enhance_office_to_pdf_routing_key,
                "dlq": settings.file_enhance_office_to_pdf_dlq,
                "dlq_rk": settings.file_enhance_office_to_pdf_dlq_routing_key,
            },
            # 压缩包目录结构解析增强阶段
            {
                "queue": settings.file_enhance_archive_parse_queue,
                "rk": settings.file_enhance_archive_parse_routing_key,
                "dlq": settings.file_enhance_archive_parse_dlq,
                "dlq_rk": settings.file_enhance_archive_parse_dlq_routing_key,
            },
        ]

        # 主交换机
        en_exchange = await self.channel.declare_exchange(
            settings.file_enhance_exchange,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.exchanges[settings.file_enhance_exchange] = en_exchange

        # 死信交换机
        en_dlx = await self.channel.declare_exchange(
            settings.file_enhance_dlx,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.exchanges[settings.file_enhance_dlx] = en_dlx

        for stage in enhance_stages:
            # 主队列（绑定 DLX）
            q = await self._declare_queue_safe(
                stage["queue"],
                arguments={
                    "x-message-ttl": 604800000,  # 7 天
                    "x-dead-letter-exchange": settings.file_enhance_dlx,
                    "x-dead-letter-routing-key": stage["dlq_rk"],
                },
            )
            await q.bind(en_exchange, routing_key=stage["rk"])

            # REQ-WORKER-TASKBUS-2026-07：任务失败不在消费者内 sleep；消息先进入持久化
            # retry 队列，expiration/x-message-ttl 到期后由 RabbitMQ 原子死信回原任务路由。
            retry_queue = await self._declare_queue_safe(
                f"{stage['queue']}.retry",
                arguments={
                    "x-queue-type": "quorum",
                    "x-message-ttl": 604800000,
                    "x-dead-letter-exchange": settings.file_enhance_exchange,
                    "x-dead-letter-routing-key": stage["rk"],
                },
            )
            await retry_queue.bind(en_exchange, routing_key=f"{stage['rk']}.retry")

            # 死信队列
            dlq = await self._declare_queue_safe(
                stage["dlq"],
                arguments={
                    "x-message-ttl": 2592000000,  # 30 天
                },
            )
            await dlq.bind(en_dlx, routing_key=stage["dlq_rk"])

        logger.info(
            f"文件增强处理拓扑声明完成: "
            f"stages={len(enhance_stages)}, "
            f"exchange={settings.file_enhance_exchange}"
        )

    async def _declare_queue_safe(
        self, queue_name: str, arguments: dict = None
    ) -> aio_pika.Queue:
        """安全声明队列：先尝试用期望参数声明，参数冲突时自动修复

        关键设计：RabbitMQ 的 passive declare 只返回 message_count 和
        consumer_count，不返回 arguments。因此不能通过 passive declare
        来比对参数是否匹配。

        正确流程：
        1. 直接用期望参数声明队列（非 passive）
           - 队列不存在 → 创建成功（参数即期望值）
           - 队列存在且参数一致 → 声明成功，返回已有队列
        2. 参数冲突（PRECONDITION_FAILED）→ 队列存在但参数不同
           - 队列为空 → 自动删除并重建
           - 队列非空 → 沿用已有配置，不丢消息，不阻塞启动
        """
        arguments = arguments or {}

        # 1. 直接用期望参数声明（队列不存在则创建，存在且参数一致则复用）
        try:
            queue = await self.channel.declare_queue(
                queue_name,
                durable=True,
                arguments=arguments or None,
            )
            logger.info(
                f"队列声明成功（参数一致或新建）: {queue_name} "
                f"(含参数: {list(arguments.keys()) if arguments else '无'})"
            )
            return queue

        except aio_pika.exceptions.ChannelPreconditionFailed as precondition_err:
            # 2. 仅捕获参数冲突（PRECONDITION_FAILED），其他异常（连接错误等）直接传播
            logger.warning(
                f"队列 {queue_name} 参数冲突，将检查是否可安全重建: {precondition_err}"
            )
            await asyncio.sleep(0.3)

            # 3. 被动检查队列状态
            try:
                queue = await self.channel.declare_queue(
                    queue_name,
                    durable=True,
                    passive=True,
                )
                msg_count = getattr(queue, 'message_count', 0) or 0
                consumer_count = getattr(queue, 'consumer_count', 0) or 0

                if msg_count == 0 and consumer_count == 0:
                    # 队列为空 → 安全删除并重建
                    logger.warning(
                        f"队列 {queue_name} 为空，自动删除并重建..."
                    )
                    await self.channel.queue_delete(queue_name)
                    await asyncio.sleep(0.3)
                    queue = await self.channel.declare_queue(
                        queue_name,
                        durable=True,
                        arguments=arguments or None,
                    )
                    logger.info(
                        f"队列 {queue_name} 重建成功 "
                        f"(含参数: {list(arguments.keys())})"
                    )
                    return queue
                else:
                    # 队列非空 → 沿用已有配置
                    logger.warning(
                        f"⚠ 队列 {queue_name} 参数不匹配，"
                        f"消息数={msg_count}, 消费者数={consumer_count}，"
                        f"队列非空，沿用已有配置。"
                        f"如需参数生效，请手动删除队列后重启: "
                        f"rabbitmqadmin delete queue name={queue_name}"
                    )
                    return queue

            except Exception as passive_err:
                # 4. 被动检查也失败 → 最终回退
                logger.error(
                    f"队列 {queue_name} 被动检查失败: {passive_err}"
                )
                raise

    async def publish_message(
        self,
        exchange_name: str,
        routing_key: str,
        message: dict,
        delay_seconds: int = 0,
    ) -> None:
        """发布消息"""
        try:
            if exchange_name not in self.exchanges:
                raise ValueError(f"Exchange {exchange_name} not found")

            exchange = self.exchanges[exchange_name]
            message_body = json.dumps(message, ensure_ascii=False).encode("utf-8")

            message_id = str(message.get("message_id") or message.get("messageId") or message.get("id") or "")
            try:
                retry_count = max(0, int(message.get("retry_count") or message.get("x-retry-count") or 0))
            except (TypeError, ValueError):
                # W-03：生产者收到旧/脏消息时将计数归零；真正的协议错误仍由消费者
                # 解析阶段送入专属 DLQ，不能让发布端因日志字段异常丢失整条消息。
                retry_count = 0
            msg_kwargs = {
                "body": message_body,
                "content_type": "application/json",
                "delivery_mode": DeliveryMode.PERSISTENT,
                "message_id": message_id,
                "headers": {
                    # retry_count 同时进入 AMQP header，确保 Task Bus DLX 转发后计数不丢失。
                    "x-retry-count": retry_count,
                    "x-task-type": message.get("task_type") or message.get("stage") or message.get("event_type", ""),
                    "x-failure-reason": message.get("failure_reason") or message.get("failure_code", ""),
                    "x-message-kind": message.get("message_kind", ""),
                    "x-schema-version": message.get("schema_version", 1),
                    "x-correlation-id": message.get("correlation_id") or message.get("pipeline_id", ""),
                },
            }

            if delay_seconds > 0:
                # REQ-TASKBUS-RETRY-TTL-2026-07：RabbitMQ 协议层 expiration 是毫秒字符串，
                # 但本项目使用的 aio-pika Message(expiration=...) 入参单位是秒，库会在编码
                # AMQP Basic.Properties 时自动转换为毫秒。原实现传入 int(delay_seconds) 的
                # 数值在 aio-pika 9.6.2 中正是正确行为；若在此处再乘 1000，会被库二次换算。
                # 消息到期后由 retry 队列 DLX 回投原任务路由键。
                msg_kwargs["expiration"] = max(1, int(delay_seconds))

            await exchange.publish(
                Message(**msg_kwargs),
                routing_key=routing_key,
            )

            logger.info(
                f"消息已发布: exchange={exchange_name}, rk={routing_key}, "
                f"message_id={message_id[:8] or 'N/A'}... "
                f"delay={delay_seconds}s"
            )
        except Exception as e:
            logger.error(f"消息发布失败: {e}", exc_info=True)
            raise

    async def publish_retry_message(
        self,
        *,
        exchange_name: str,
        routing_key: str,
        message: dict,
        delay_seconds: int,
    ) -> None:
        """发布到阶段专属 `.retry` 路由，等待发布确认后由调用方 ACK 原消息。"""

        await self.publish_message(
            exchange_name=exchange_name,
            routing_key=f"{routing_key}.retry",
            message=message,
            delay_seconds=max(0, delay_seconds),
        )

    async def publish_to_dlq(
        self,
        exchange_name: str,
        routing_key: str,
        message: dict,
    ) -> None:
        """发布消息到死信队列 (DLQ)"""
        logger.warning(
            f"发布到 DLQ: message_id={str(message.get('message_id') or message.get('id') or '')[:8] or 'N/A'}..., "
            f"task_type={message.get('task_type')}, "
            f"failure_reason={message.get('failure_reason')}"
        )
        await self.publish_message(exchange_name, routing_key, message)

    async def publish_security_event(self, message: dict) -> None:
        """发布安全事件到隔离队列"""
        await self.publish_message(
            settings.file_event_exchange,
            settings.security_quarantine_routing_key,
            message,
        )

    async def publish_file_event(self, routing_key: str, event_data: dict) -> None:
        """
        发布文件生命周期事件到主业务服务

        用于替代 HTTP 通知，通过 MQ 解耦文件存储服务与主业务服务。
        事件类型对应 routing_key：
        - file.available     → 文件处理完成，配额提交
        - file.merge.failed  → 合并失败，配额回滚
        - file.scan.failed   → 扫毒失败，配额回滚
        - file.downloaded    → 下载完成，记录最近下载
        """
        await self.publish_message(
            settings.file_event_exchange,
            routing_key,
            event_data,
        )

    async def consume(
        self,
        queue_name: str,
        callback,
        prefetch_count: int = 4,
        max_concurrency: int = 8,
    ):
        """
        消费指定队列的消息（支持并发控制）

        核心改进：
        - 每个队列独立的 prefetch_count，控制 RabbitMQ 预取数量
        - 使用 Semaphore 限制协程级并发数，防止 OOM
        - 重型任务（视频转码等）不会阻塞其他消息的消费

        Args:
            queue_name: 队列名称
            callback: 消息处理回调函数 (async)
            prefetch_count: RabbitMQ prefetch 数量（预取到客户端的消息数）
            max_concurrency: 最大协程并发数（Semaphore 限制）
        """
        try:
            if self._stopping:
                raise RuntimeError("RabbitMQ 服务正在关闭，拒绝创建新的消费者")
            # 为每个消费者创建独立的 channel，实现独立的 prefetch 控制
            consumer_channel = await self.connection.channel()
            self._consumer_channels.append(consumer_channel)
            await consumer_channel.set_qos(prefetch_count=prefetch_count)

            queue = await consumer_channel.declare_queue(
                queue_name, durable=True, passive=True,
            )

            # 并发信号量
            semaphore = asyncio.Semaphore(max_concurrency)

            async def concurrent_handler(raw_message):
                """包装回调：加并发控制 + 详细日志，确保消息处理完后 ACK/NACK"""
                msg_id = (
                    raw_message.message_id[:8]
                    if raw_message.message_id else "?"
                )

                # 等待 Semaphore (即等待处理槽位)
                acquire_start = time.monotonic()
                async with semaphore:
                    wait_ms = (time.monotonic() - acquire_start) * 1000
                    available = semaphore._value  # 剩余可用槽位
                    logger.info(
                        f"[MQ-RECV] queue={queue_name} "
                        f"msg_id={msg_id} "
                        f"slots_avail={available}/{max_concurrency} "
                        f"wait_ms={wait_ms:.1f}"
                    )

                    try:
                        await callback(raw_message)
                    except Exception as e:
                        logger.error(
                            f"[MQ-ERR] queue={queue_name} "
                            f"msg_id={msg_id} "
                            f"error={e}",
                            exc_info=True,
                        )
                        # 关键修复：requeue=False 让消息通过 DLX 进入死信队列
                        # 而不是 requeue=True 导致无限重试循环
                        # 回调通常已经完成 ACK/NACK；只在消息仍未处理时补充一次 DLX NACK，
                        # 防止底层异常导致消息既未确认又无限 requeue。
                        if not getattr(raw_message, "processed", False):
                            try:
                                await raw_message.nack(requeue=False)
                            except Exception:
                                logger.exception("MQ 异常处置失败 queue=%s", queue_name)

                    logger.debug(
                        f"[MQ-DONE] queue={queue_name} "
                        f"msg_id={msg_id} "
                        f"slots_avail={semaphore._value}/{max_concurrency}"
                    )

            consumer_tag = await queue.consume(concurrent_handler)
            self._consumer_tags.append((queue, consumer_tag))

            logger.info(
                f"消费者启动: queue={queue_name}, "
                f"prefetch={prefetch_count}, "
                f"max_concurrency={max_concurrency}"
            )
        except Exception as e:
            logger.error(
                f"消费者启动失败: queue={queue_name}, error={e}",
                exc_info=True,
            )
            raise

    async def close(self):
        """关闭连接"""
        self._stopping = True
        for queue, consumer_tag in list(self._consumer_tags):
            try:
                await queue.cancel(consumer_tag)
            except Exception:
                logger.debug("取消消费者失败 queue=%s", getattr(queue, "name", "unknown"), exc_info=True)
        self._consumer_tags.clear()
        if self.connection:
            await self.connection.close()
            logger.info("RabbitMQ 连接已关闭")
        self._consumer_channels.clear()

    def health_snapshot(self) -> dict:
        """返回不含业务载荷的 Worker MQ 健康快照。"""
        return {
            "connected": bool(self.connection and not self.connection.is_closed),
            "consumer_count": len(self._consumer_tags),
            "stopping": self._stopping,
        }


# 全局单例
rabbitmq_service = RabbitMQService()
