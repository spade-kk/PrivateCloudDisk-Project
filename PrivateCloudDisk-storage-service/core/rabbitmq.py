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

    async def connect(self):
        """建立 RabbitMQ 连接"""
        try:
            connection_url = (
                f"amqp://{settings.rabbitmq_username}:{settings.rabbitmq_password}"
                f"@{settings.rabbitmq_host}:{settings.rabbitmq_port}/{settings.rabbitmq_vhost}"
            )

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
        │  pcd.file.process.exchange (DIRECT)                 │
        │  ├── pcd.file.process.queue                         │
        │  │   └── DLX → pcd.file.process.dlx                 │
        │  └── pcd.security.quarantine.queue (病毒隔离专用)      │
        │                                                      │
        │  pcd.file.process.dlx (DIRECT)                      │
        │  └── pcd.file.process.dlq                           │
        │                                                      │
        │  pcd.file.delete.exchange (DIRECT)                  │
        │  ├── pcd.file.delete.queue                          │
        │  │   └── DLX → pcd.file.delete.dlx                  │
        │  └── pcd.file.delete.dlq                            │
        │                                                      │
        │  pcd.content.index.exchange (DIRECT)                │
        │  └── pcd.content.index.queue                        │
        │      └── DLX → pcd.content.index.dlx                │
        │                                                      │
        │  pcd.content.index.dlx (DIRECT)                     │
        │  └── pcd.content.index.dlq                          │
        └─────────────────────────────────────────────────────┘
        """
        # ========== 文件处理交换机 ==========
        fp_exchange = await self.channel.declare_exchange(
            settings.file_process_exchange,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.exchanges[settings.file_process_exchange] = fp_exchange

        # ========== 文件处理死信交换机 ==========
        fp_dlx = await self.channel.declare_exchange(
            settings.file_process_dlx,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.exchanges[settings.file_process_dlx] = fp_dlx

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

        # ========== 文件处理主队列 (绑定 DLX) ==========
        fp_queue = await self._declare_queue_safe(
            settings.file_process_queue,
            arguments={
                "x-message-ttl": 604800000,  # 7 天 TTL
                "x-dead-letter-exchange": settings.file_process_dlx,
                "x-dead-letter-routing-key": settings.file_process_dlq_routing_key,
            },
        )
        await fp_queue.bind(
            fp_exchange,
            routing_key=settings.file_process_routing_key,
        )

        # ========== 文件处理死信队列 (DLQ) ==========
        fp_dlq = await self._declare_queue_safe(
            settings.file_process_dlq,
            arguments={
                "x-message-ttl": 2592000000,  # 30 天 TTL
            },
        )
        await fp_dlq.bind(
            fp_dlx,
            routing_key=settings.file_process_dlq_routing_key,
        )

        # ========== 安全隔离队列 ==========
        sq_queue = await self._declare_queue_safe(
            settings.security_quarantine_queue,
            arguments={
                "x-message-ttl": 2592000000,  # 30 天
            },
        )
        await sq_queue.bind(
            fp_exchange,
            routing_key=settings.security_quarantine_routing_key,
        )

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

        # ========== 内容索引交换机 ==========
        ci_exchange = await self.channel.declare_exchange(
            settings.content_index_exchange,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.exchanges[settings.content_index_exchange] = ci_exchange

        # ========== 内容索引死信交换机 ==========
        ci_dlx = await self.channel.declare_exchange(
            settings.content_index_dlx,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.exchanges[settings.content_index_dlx] = ci_dlx

        # ========== 内容索引主队列 (绑定 DLX) ==========
        ci_queue = await self._declare_queue_safe(
            settings.content_index_queue,
            arguments={
                "x-message-ttl": 604800000,  # 7 天
                "x-dead-letter-exchange": settings.content_index_dlx,
                "x-dead-letter-routing-key": settings.content_index_dlq_routing_key,
            },
        )
        await ci_queue.bind(
            ci_exchange,
            routing_key=settings.content_index_routing_key,
        )

        # ========== 内容索引死信队列 ==========
        ci_dlq = await self._declare_queue_safe(
            settings.content_index_dlq,
            arguments={
                "x-message-ttl": 2592000000,  # 30 天
            },
        )
        await ci_dlq.bind(
            ci_dlx,
            routing_key=settings.content_index_dlq_routing_key,
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

        # ========== 新版文件增强处理拓扑（Enhancement — 并发流水线） ==========
        # 每个阶段独立 exchange + queue + DLQ
        await self._declare_enhance_topology()

        logger.info(
            f"RabbitMQ 拓扑声明完成: "
            f"exchanges={len(self.exchanges)}, "
            f"DLX={settings.file_process_dlx}, "
            f"DLQ={settings.file_process_dlq}"
        )

    async def _declare_backend_topology(self):
        """
        声明文件后台处理拓扑（顺序流水线）

        拓扑结构:
        ┌─────────────────────────────────────────────────────────────┐
        │  pcd.file.backend.exchange (DIRECT)                        │
        │  ├── pcd.file.backend.merge.queue                          │
        │  │   └── DLX → pcd.file.backend.dlx → .merge.dlq          │
        │  ├── pcd.file.backend.hash.queue                           │
        │  │   └── DLX → pcd.file.backend.dlx → .hash.dlq           │
        │  ├── pcd.file.backend.virus.queue                          │
        │  │   └── DLX → pcd.file.backend.dlx → .virus.dlq          │
        │  └── pcd.file.backend.mark_active.queue                    │
        │      └── DLX → pcd.file.backend.dlx → .mark_active.dlq    │
        │                                                              │
        │  流水线: merge → hash → virus → mark_active                │
        │  每个阶段成功后由消费者发布下一阶段消息                        │
        └─────────────────────────────────────────────────────────────┘
        """
        backend_stages = [
            {
                "queue": settings.file_backend_merge_queue,
                "rk": settings.file_backend_merge_routing_key,
                "dlq": settings.file_backend_merge_dlq,
                "dlq_rk": settings.file_backend_merge_dlq_routing_key,
            },
            {
                "queue": settings.file_backend_hash_queue,
                "rk": settings.file_backend_hash_routing_key,
                "dlq": settings.file_backend_hash_dlq,
                "dlq_rk": settings.file_backend_hash_dlq_routing_key,
            },
            {
                "queue": settings.file_backend_virus_queue,
                "rk": settings.file_backend_virus_routing_key,
                "dlq": settings.file_backend_virus_dlq,
                "dlq_rk": settings.file_backend_virus_dlq_routing_key,
            },
            {
                "queue": settings.file_backend_mark_active_queue,
                "rk": settings.file_backend_mark_active_routing_key,
                "dlq": settings.file_backend_mark_active_dlq,
                "dlq_rk": settings.file_backend_mark_active_dlq_routing_key,
            },
        ]

        # 主交换机
        be_exchange = await self.channel.declare_exchange(
            settings.file_backend_exchange,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.exchanges[settings.file_backend_exchange] = be_exchange

        # 死信交换机
        be_dlx = await self.channel.declare_exchange(
            settings.file_backend_dlx,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.exchanges[settings.file_backend_dlx] = be_dlx

        for stage in backend_stages:
            # 主队列（绑定 DLX）
            q = await self._declare_queue_safe(
                stage["queue"],
                arguments={
                    "x-message-ttl": 604800000,  # 7 天
                    "x-dead-letter-exchange": settings.file_backend_dlx,
                    "x-dead-letter-routing-key": stage["dlq_rk"],
                },
            )
            await q.bind(be_exchange, routing_key=stage["rk"])

            # 死信队列
            dlq = await self._declare_queue_safe(
                stage["dlq"],
                arguments={
                    "x-message-ttl": 2592000000,  # 30 天
                },
            )
            await dlq.bind(be_dlx, routing_key=stage["dlq_rk"])

        logger.info(
            f"文件后台处理拓扑声明完成: "
            f"stages={len(backend_stages)}, "
            f"exchange={settings.file_backend_exchange}"
        )

    async def _declare_enhance_topology(self):
        """
        声明文件增强处理拓扑（并发流水线）

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
        │  所有增强阶段可并发消费，互不阻塞                              │
        │  由 mark_active 消费者扇出发布                               │
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

            msg_kwargs = {
                "body": message_body,
                "content_type": "application/json",
                "delivery_mode": DeliveryMode.PERSISTENT,
                "message_id": message.get("message_id", ""),
                "headers": {
                    "x-retry-count": message.get("retry_count", 0),
                    "x-task-type": message.get("task_type", ""),
                    "x-failure-reason": message.get("failure_reason", ""),
                },
            }

            if delay_seconds > 0:
                msg_kwargs["expiration"] = str(delay_seconds * 1000)

            await exchange.publish(
                Message(**msg_kwargs),
                routing_key=routing_key,
            )

            logger.debug(
                f"消息已发布: exchange={exchange_name}, rk={routing_key}, "
                f"message_id={message.get('message_id', 'N/A')[:8]}... "
                f"delay={delay_seconds}s"
            )
        except Exception as e:
            logger.error(f"消息发布失败: {e}", exc_info=True)
            raise

    async def publish_to_dlq(
        self,
        exchange_name: str,
        routing_key: str,
        message: dict,
    ) -> None:
        """发布消息到死信队列 (DLQ)"""
        logger.warning(
            f"发布到 DLQ: message_id={message.get('message_id', 'N/A')[:8]}..., "
            f"task_type={message.get('task_type')}, "
            f"failure_reason={message.get('failure_reason')}"
        )
        await self.publish_message(exchange_name, routing_key, message)

    async def publish_security_event(self, message: dict) -> None:
        """发布安全事件到隔离队列"""
        await self.publish_message(
            settings.file_process_exchange,
            settings.security_quarantine_routing_key,
            message,
        )

    async def publish_file_event(self, routing_key: str, event_data: dict) -> None:
        """
        发布文件生命周期事件到主业务服务

        用于替代 HTTP 通知，通过 MQ 解耦文件存储服务与主业务服务。
        事件类型对应 routing_key：
        - file.available  → 文件处理完成，配额提交
        - file.merge.failed  → 合并失败，配额回滚
        - file.scan.failed   → 扫毒失败，配额回滚
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
            # 为每个消费者创建独立的 channel，实现独立的 prefetch 控制
            consumer_channel = await self.connection.channel()
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
                        try:
                            await raw_message.nack(requeue=False)
                        except Exception:
                            pass

                    logger.debug(
                        f"[MQ-DONE] queue={queue_name} "
                        f"msg_id={msg_id} "
                        f"slots_avail={semaphore._value}/{max_concurrency}"
                    )

            await queue.consume(concurrent_handler)

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
        if self.connection:
            await self.connection.close()
            logger.info("RabbitMQ 连接已关闭")


# 全局单例
rabbitmq_service = RabbitMQService()