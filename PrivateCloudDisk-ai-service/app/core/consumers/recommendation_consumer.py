"""
AI Processing Service - 推荐系统消费者

消费 platform-service 定时触发的推荐更新事件。
为用户生成个性化文件推荐。

处理流程:
1. 接收 RecommendationEvent
2. 调用 RecommendationEngine 生成推荐
3. 推荐结果写入 MySQL
"""
from __future__ import annotations
import asyncio
import json
import logging
from typing import Optional

import aio_pika
from aio_pika.abc import AbstractIncomingMessage

from app.core.config import settings
from app.core.rabbitmq import rabbitmq_service
from app.core.events.ai_process_event import RecommendationEvent
from app.core.models.recommender import recommendation_engine

logger = logging.getLogger("ai_service.consumer.recommendation")


class RecommendationConsumer:
    """推荐系统消费者"""

    def __init__(self):
        self._running = False
        self._shutdown_event = asyncio.Event()

    async def start(self) -> None:
        """启动消费者"""
        self._running = True
        logger.info("推荐系统消费者启动中...")

        await rabbitmq_service.consume(
            queue_name=settings.recommendation_queue,
            callback=self._handle_message,
            prefetch_count=settings.worker_prefetch_recommendation,
            max_concurrency=settings.worker_concurrency_recommendation,
        )

        logger.info(
            f"推荐系统消费者已启动: "
            f"prefetch={settings.worker_prefetch_recommendation}, "
            f"concurrency={settings.worker_concurrency_recommendation}"
        )

        await self._shutdown_event.wait()

    async def stop(self) -> None:
        """停止消费者"""
        self._running = False
        self._shutdown_event.set()
        logger.info("推荐系统消费者已停止")

    async def _handle_message(self, message: AbstractIncomingMessage) -> None:
        """处理推荐消息"""
        async with message.process(requeue=False):
            try:
                data = json.loads(message.body.decode("utf-8"))
                event = RecommendationEvent.from_dict(data)

                logger.info(
                    f"收到推荐事件: message_id={event.message_id}, "
                    f"user_id={event.user_id}, "
                    f"update_type={event.update_type}"
                )

                result = await recommendation_engine.recommend(event)

                if result.success:
                    recs = result.data.get("recommendations", [])
                    logger.info(
                        f"推荐系统完成: user_id={event.user_id}, "
                        f"recommendations={len(recs)}"
                    )
                elif result.skipped:
                    logger.info(
                        f"推荐系统跳过: user_id={event.user_id}, "
                        f"reason={result.skipped_reason}"
                    )
                else:
                    logger.error(
                        f"推荐系统失败: user_id={event.user_id}, "
                        f"error={result.error}"
                    )
                    await message.nack(requeue=False)

            except json.JSONDecodeError as e:
                logger.error(f"推荐消息解析失败: error={e}")
                await message.nack(requeue=False)

            except Exception as e:
                logger.error(f"推荐系统异常: error={e}", exc_info=True)
                await message.nack(requeue=False)


# =============================================================================
# 全局单例
# =============================================================================
recommendation_consumer = RecommendationConsumer()