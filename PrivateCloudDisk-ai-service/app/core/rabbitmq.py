"""
AI Processing Service - RabbitMQ 消息服务

功能:
- 声明所有交换机、队列、死信队列拓扑
- 支持消息发布和消费
- 与 storage-service 共用 RabbitMQ 集群
- 使用 aio-pika 异步客户端

消息拓扑结构:

┌─────────────────────────────────────────────────────────────────┐
│                    AI Service RabbitMQ 拓扑                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Exchanges:                                                     │
│    pcd.ai.process.exchange     (topic)  - AI 处理主交换机        │
│    pcd.ai.process.dlx          (topic)  - AI 处理死信交换机      │
│    pcd.ai.face.cluster.exchange (topic) - 人脸聚类交换机          │
│    pcd.ai.face.cluster.dlx     (topic)  - 人脸聚类死信交换机      │
│    pcd.ai.recommendation.exchange (topic) - 推荐系统交换机        │
│    pcd.ai.recommendation.dlx   (topic)  - 推荐系统死信交换机      │
│                                                                 │
│  Queues:                                                        │
│    pcd.ai.process.queue        → pcd.ai.process.exchange        │
│      ├── x-dead-letter-exchange: pcd.ai.process.dlx             │
│      └── x-dead-letter-routing-key: ai.process.dlq              │
│                                                                 │
│    pcd.ai.process.dlq          → pcd.ai.process.dlx             │
│                                                                 │
│    pcd.ai.face.cluster.queue   → pcd.ai.face.cluster.exchange   │
│      ├── x-dead-letter-exchange: pcd.ai.face.cluster.dlx        │
│      └── x-dead-letter-routing-key: ai.face.cluster.dlq         │
│                                                                 │
│    pcd.ai.face.cluster.dlq     → pcd.ai.face.cluster.dlx        │
│                                                                 │
│    pcd.ai.recommendation.queue → pcd.ai.recommendation.exchange  │
│      ├── x-dead-letter-exchange: pcd.ai.recommendation.dlx       │
│      └── x-dead-letter-routing-key: ai.recommendation.dlq        │
│                                                                 │
│    pcd.ai.recommendation.dlq   → pcd.ai.recommendation.dlx       │
│                                                                 │
│  消息流转:                                                       │
│    Storage Service                                              │
│      │ (MARK_ACTIVE 完成后)                                      │
│      │                                                          │
│      ├── publish → pcd.ai.process.exchange (ai.process)          │
│      │   └── AI Worker 消费 → 图片分类/人脸/物体/NSFW/NLP/OCR/摘要│
│      │                                                          │
│      └── publish → pcd.ai.face.cluster.exchange (定期触发)       │
│          └── Face Cluster Worker 消费 → 人脸聚类                │
│                                                                 │
│    Platform Service                                             │
│      └── publish → pcd.ai.recommendation.exchange (定期触发)    │
│          └── Recommendation Worker 消费 → 个性化推荐            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
"""
from __future__ import annotations
import asyncio
import json
import logging
from typing import Any, Callable, Optional

import aio_pika
from aio_pika import ExchangeType, Message, DeliveryMode
from aio_pika.abc import AbstractRobustConnection, AbstractRobustChannel

from app.core.config import settings

logger = logging.getLogger("ai_service.rabbitmq")


class RabbitMQService:
    """
    RabbitMQ 连接管理服务

    特性:
    - 自动重连 (RobustConnection)
    - 声明完整的消息拓扑 (交换机 + 队列 + 绑定 + DLX)
    - 支持消息发布 (持久化 + 非持久化)
    - 支持消费者注册 (独立 channel + prefetch + 并发控制)
    """

    def __init__(self):
        self._connection: Optional[AbstractRobustConnection] = None
        self._channel: Optional[AbstractRobustChannel] = None
        self._consumer_channels: dict[str, AbstractRobustChannel] = {}
        self._consumer_tags: dict[str, str] = {}

    # ==================================================================
    # 连接管理
    # ==================================================================

    async def connect(self) -> None:
        """连接到 RabbitMQ 并声明拓扑"""
        url = (
            f"amqp://{settings.rabbitmq_user}:{settings.rabbitmq_pass}"
            f"@{settings.rabbitmq_host}:{settings.rabbitmq_port}"
            f"/{settings.rabbitmq_vhost}"
        )

        logger.info(f"连接 RabbitMQ: {settings.rabbitmq_host}:{settings.rabbitmq_port}")

        self._connection = await aio_pika.connect_robust(
            url,
            client_properties={
                "connection_name": "PrivateCloudDisk-AI-Service",
            },
        )

        self._channel = await self._connection.channel()
        await self._channel.set_qos(prefetch_count=1)

        await self._declare_topology()
        logger.info("RabbitMQ 连接成功，拓扑已声明")

    async def close(self) -> None:
        """关闭 RabbitMQ 连接"""
        if self._connection and not self._connection.is_closed:
            await self._connection.close()
            logger.info("RabbitMQ 连接已关闭")

    async def _declare_topology(self) -> None:
        """声明完整的消息拓扑"""
        channel = self._channel

        # ---- AI 处理主拓扑 ----
        await self._declare_exchange_queue_dlx(
            channel,
            exchange_name=settings.ai_process_exchange,
            queue_name=settings.ai_process_queue,
            routing_key=settings.ai_process_routing_key,
            dlx_exchange=settings.ai_process_dlx,
            dlx_queue=settings.ai_process_dlq,
            dlx_routing_key=settings.ai_process_dlq_routing_key,
        )

        # ---- 人脸聚类拓扑 ----
        await self._declare_exchange_queue_dlx(
            channel,
            exchange_name=settings.face_cluster_exchange,
            queue_name=settings.face_cluster_queue,
            routing_key=settings.face_cluster_routing_key,
            dlx_exchange=settings.face_cluster_dlx,
            dlx_queue=settings.face_cluster_dlq,
            dlx_routing_key=settings.face_cluster_dlq_routing_key,
        )

        # ---- 推荐系统拓扑 ----
        await self._declare_exchange_queue_dlx(
            channel,
            exchange_name=settings.recommendation_exchange,
            queue_name=settings.recommendation_queue,
            routing_key=settings.recommendation_routing_key,
            dlx_exchange=settings.recommendation_dlx,
            dlx_queue=settings.recommendation_dlq,
            dlx_routing_key=settings.recommendation_dlq_routing_key,
        )

        logger.info("RabbitMQ 拓扑声明完成 (3 个主队列 + 3 个 DLQ)")

    async def _declare_exchange_queue_dlx(
        self,
        channel: AbstractRobustChannel,
        exchange_name: str,
        queue_name: str,
        routing_key: str,
        dlx_exchange: str,
        dlx_queue: str,
        dlx_routing_key: str,
    ) -> None:
        """声明一个 Exchange + Queue + DLX 拓扑"""
        # 1. 声明主交换机
        exchange = await channel.declare_exchange(
            exchange_name,
            ExchangeType.TOPIC,
            durable=True,
        )

        # 2. 声明死信交换机
        dlx = await channel.declare_exchange(
            dlx_exchange,
            ExchangeType.TOPIC,
            durable=True,
        )

        # 3. 声明死信队列
        dlq = await channel.declare_queue(
            dlx_queue,
            durable=True,
        )
        await dlq.bind(dlx, dlx_routing_key)

        # 4. 声明主队列 (绑定 DLX)
        queue = await channel.declare_queue(
            queue_name,
            durable=True,
            arguments={
                "x-dead-letter-exchange": dlx_exchange,
                "x-dead-letter-routing-key": dlx_routing_key,
                "x-message-ttl": 86400000,  # 24小时 TTL
            },
        )
        await queue.bind(exchange, routing_key)

        logger.debug(f"拓扑声明: {queue_name} → {exchange_name} (DLX: {dlx_exchange})")

    # ==================================================================
    # 消息发布
    # ==================================================================

    async def publish_message(
        self,
        exchange_name: str,
        routing_key: str,
        message: dict[str, Any],
        persistent: bool = True,
        expiration: Optional[int] = None,
    ) -> None:
        """
        发布消息到交换机

        Args:
            exchange_name: 交换机名称
            routing_key: 路由键
            message: 消息体 (dict)
            persistent: 是否持久化
            expiration: 消息过期时间 (毫秒)
        """
        if not self._channel or self._channel.is_closed:
            raise RuntimeError("RabbitMQ 未连接")

        exchange = await self._channel.get_exchange(exchange_name)

        body = json.dumps(message, ensure_ascii=False, default=str).encode("utf-8")

        msg = Message(
            body=body,
            content_type="application/json",
            delivery_mode=DeliveryMode.PERSISTENT if persistent else DeliveryMode.NOT_PERSISTENT,
            expiration=str(expiration) if expiration else None,
            message_id=message.get("message_id", ""),
        )

        await exchange.publish(msg, routing_key=routing_key)
        logger.debug(f"消息发布: {exchange_name} → {routing_key}")

    # ==================================================================
    # 消息消费
    # ==================================================================

    async def consume(
        self,
        queue_name: str,
        callback: Callable,
        prefetch_count: int = 4,
        max_concurrency: int = 8,
    ) -> None:
        """
        注册消费者

        每个消费者使用独立的 channel，支持:
        - prefetch_count: 预取消息数
        - max_concurrency: 最大并发协程数 (Semaphore 控制)
        """
        # 创建独立 channel
        channel = await self._connection.channel()
        await channel.set_qos(prefetch_count=prefetch_count)

        queue = await channel.declare_queue(queue_name, durable=True)

        semaphore = asyncio.Semaphore(max_concurrency)

        async def _wrapped_callback(message: aio_pika.abc.AbstractIncomingMessage):
            async with semaphore:
                try:
                    await callback(message)
                except Exception as e:
                    logger.error(
                        f"消费者回调异常: queue={queue_name}, error={e}",
                        exc_info=True,
                    )
                    try:
                        await message.nack(requeue=False)
                    except Exception:
                        pass

        consumer_tag = await queue.consume(_wrapped_callback)
        self._consumer_channels[queue_name] = channel
        self._consumer_tags[queue_name] = consumer_tag

        logger.info(
            f"消费者注册: queue={queue_name}, "
            f"prefetch={prefetch_count}, "
            f"concurrency={max_concurrency}"
        )

    # ==================================================================
    # 拓扑查询
    # ==================================================================

    async def get_queue_message_count(self, queue_name: str) -> int:
        """获取队列中的消息数量"""
        if not self._channel or self._channel.is_closed:
            return -1
        try:
            queue = await self._channel.declare_queue(queue_name, durable=True, passive=True)
            return queue.declaration_result.message_count
        except Exception:
            return -1


# =============================================================================
# 全局单例
# =============================================================================
rabbitmq_service = RabbitMQService()