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
        fp_dlq = await self.channel.declare_queue(
            settings.file_process_dlq,
            durable=True,
            arguments={
                "x-message-ttl": 2592000000,  # 30 天 TTL
            },
        )
        await fp_dlq.bind(
            fp_dlx,
            routing_key=settings.file_process_dlq_routing_key,
        )

        # ========== 安全隔离队列 ==========
        sq_queue = await self.channel.declare_queue(
            settings.security_quarantine_queue,
            durable=True,
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
        fd_dlq = await self.channel.declare_queue(
            settings.file_delete_dlq,
            durable=True,
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
        ci_dlq = await self.channel.declare_queue(
            settings.content_index_dlq,
            durable=True,
            arguments={
                "x-message-ttl": 2592000000,  # 30 天
            },
        )
        await ci_dlq.bind(
            ci_dlx,
            routing_key=settings.content_index_dlq_routing_key,
        )

        logger.info(
            f"RabbitMQ 拓扑声明完成: "
            f"exchanges={len(self.exchanges)}, "
            f"DLX={settings.file_process_dlx}, "
            f"DLQ={settings.file_process_dlq}"
        )

    async def _declare_queue_safe(
        self, queue_name: str, arguments: dict
    ) -> aio_pika.Queue:
        """安全声明队列：先被动检查是否存在，不存在则创建带 DLX 参数

        关键修复：如果队列已存在但没有 DLX 参数，则记录严重警告。
        已存在的队列无法通过代码修改参数，需要手动删除队列后重启。
        """
        try:
            queue = await self.channel.declare_queue(
                queue_name,
                durable=True,
                passive=True,
            )
            logger.info(f"队列已存在，沿用已有配置: {queue_name}")

            # 检查已有队列是否缺少 DLX 参数
            if arguments and "x-dead-letter-exchange" in arguments:
                has_dlx = (
                    hasattr(queue, 'arguments')
                    and queue.arguments
                    and "x-dead-letter-exchange" in queue.arguments
                )
                if not has_dlx:
                    logger.critical(
                        f"⚠ 队列 {queue_name} 已存在但缺少 DLX 参数! "
                        f"消息 NACK 后将不会进入死信队列。"
                        f"请手动删除队列后重启 Worker: "
                        f"rabbitmqadmin delete queue name={queue_name}"
                    )
            return queue
        except Exception:
            await asyncio.sleep(0.5)
            queue = await self.channel.declare_queue(
                queue_name,
                durable=True,
                arguments=arguments,
            )
            logger.info(f"队列创建成功: {queue_name} (含 DLX/参数)")
            return queue

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
                        f"slots_avail={semaphore._value + 1}/{max_concurrency}"
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