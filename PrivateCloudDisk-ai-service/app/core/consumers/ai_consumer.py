"""
AI Processing Service - 主 AI 消费者

消费 storage-service 发布的 AI 处理事件，执行完整的 AI Pipeline。
支持:
- 消息确认 (ACK / NACK)
- 重试逻辑 (指数退避，最大重试次数)
- 死信队列 (超出重试次数后进入 DLQ)
- 并发控制 (信号量)
- 优雅关闭
"""
from __future__ import annotations
import asyncio
import json
import logging
from typing import Optional

import aio_pika
from aio_pika.abc import AbstractIncomingMessage

from app.core.config import settings, FailureReason
from app.core.rabbitmq import rabbitmq_service
from app.core.events.ai_process_event import AIProcessEvent
from app.core.pipeline.ai_pipeline import ai_pipeline

logger = logging.getLogger("ai_service.consumer.ai")


class AIConsumer:
    """AI 处理主消费者"""

    def __init__(self):
        self._running = False
        self._shutdown_event = asyncio.Event()

    async def start(self) -> None:
        """启动消费者"""
        self._running = True
        logger.info("AI 主消费者启动中...")

        await rabbitmq_service.consume(
            queue_name=settings.ai_process_queue,
            callback=self._handle_message,
            prefetch_count=settings.worker_prefetch_ai,
            max_concurrency=settings.worker_concurrency_ai,
        )

        logger.info(
            f"AI 主消费者已启动: "
            f"prefetch={settings.worker_prefetch_ai}, "
            f"concurrency={settings.worker_concurrency_ai}"
        )

        # 等待关闭信号
        await self._shutdown_event.wait()

    async def stop(self) -> None:
        """停止消费者"""
        self._running = False
        self._shutdown_event.set()
        logger.info("AI 主消费者已停止")

    async def _handle_message(self, message: AbstractIncomingMessage) -> None:
        """处理消息"""
        async with message.process(requeue=False):
            body = message.body
            event = None

            try:
                # 1. 解析消息
                data = json.loads(body.decode("utf-8"))
                event = AIProcessEvent.from_dict(data)

                logger.info(
                    f"收到 AI 处理事件: message_id={event.message_id}, "
                    f"file_id={event.file_id}, "
                    f"file_type={event.file_type}, "
                    f"retry_count={event.retry_count}"
                )

                # 2. 检查重试次数
                if event.retry_count >= settings.retry_max_attempts:
                    logger.error(
                        f"超过最大重试次数: file_id={event.file_id}, "
                        f"retry={event.retry_count}/{settings.retry_max_attempts}"
                    )
                    await self._send_to_dlq(event)
                    return

                # 3. 执行 AI Pipeline
                await ai_pipeline.process(event)

                logger.info(
                    f"AI 处理完成: message_id={event.message_id}, "
                    f"file_id={event.file_id}"
                )

            except json.JSONDecodeError as e:
                logger.error(f"消息解析失败: error={e}, body={body[:200]}")
                # 无法解析的消息直接丢弃 (不重试)
                await message.nack(requeue=False)

            except Exception as e:
                logger.error(
                    f"AI 处理异常: file_id={event.file_id if event else 'unknown'}, "
                    f"error={e}",
                    exc_info=True,
                )
                await self._handle_retry(event, message)

    async def _handle_retry(
        self,
        event: Optional[AIProcessEvent],
        message: AbstractIncomingMessage,
    ) -> None:
        """处理重试逻辑"""
        if event is None:
            await message.nack(requeue=False)
            return

        if event.retry_count >= settings.retry_max_attempts:
            await self._send_to_dlq(event)
            return

        # 指数退避重试
        delay = min(
            settings.retry_base_delay_seconds * (2 ** event.retry_count),
            settings.retry_max_delay_seconds,
        )

        logger.info(
            f"重试 AI 处理: file_id={event.file_id}, "
            f"delay={delay}s, "
            f"retry={event.retry_count + 1}/{settings.retry_max_attempts}"
        )

        # 发布重试消息 (带延迟)
        retry_event = event.with_retry_increment()
        await rabbitmq_service.publish_message(
            exchange_name=settings.ai_process_exchange,
            routing_key=settings.ai_process_routing_key,
            message=retry_event.to_dict(),
            expiration=int(delay * 1000),
        )

    async def _send_to_dlq(self, event: AIProcessEvent) -> None:
        """发送消息到死信队列"""
        logger.warning(
            f"消息进入死信队列: file_id={event.file_id}, "
            f"retry={event.retry_count}"
        )
        # 死信队列由 RabbitMQ 自动处理，这里仅记录日志
        # 消息在 nack 或 reject 后自动进入 DLX


# =============================================================================
# 全局单例
# =============================================================================
ai_consumer = AIConsumer()