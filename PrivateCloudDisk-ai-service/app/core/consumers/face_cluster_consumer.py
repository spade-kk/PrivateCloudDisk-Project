"""
AI Processing Service - 人脸聚类消费者

消费 platform-service 定时触发的人脸聚类事件。
对指定用户的所有图片进行人脸聚类，生成人物相册。

处理流程:
1. 接收 FaceClusterEvent
2. 调用 FaceClusteringEngine 执行聚类
3. 聚类结果写入 MySQL
4. 更新 OpenSearch 索引 (可选)
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
from app.core.events.ai_process_event import FaceClusterEvent
from app.core.models.face_clustering import face_clustering_engine

logger = logging.getLogger("ai_service.consumer.face_cluster")


class FaceClusterConsumer:
    """人脸聚类消费者"""

    def __init__(self):
        self._running = False
        self._shutdown_event = asyncio.Event()

    async def start(self) -> None:
        """启动消费者"""
        self._running = True
        logger.info("人脸聚类消费者启动中...")

        await rabbitmq_service.consume(
            queue_name=settings.face_cluster_queue,
            callback=self._handle_message,
            prefetch_count=settings.worker_prefetch_face_cluster,
            max_concurrency=settings.worker_concurrency_face_cluster,
        )

        logger.info(
            f"人脸聚类消费者已启动: "
            f"prefetch={settings.worker_prefetch_face_cluster}, "
            f"concurrency={settings.worker_concurrency_face_cluster}"
        )

        await self._shutdown_event.wait()

    async def stop(self) -> None:
        """停止消费者"""
        self._running = False
        self._shutdown_event.set()
        logger.info("人脸聚类消费者已停止")

    async def _handle_message(self, message: AbstractIncomingMessage) -> None:
        """处理人脸聚类消息"""
        async with message.process(requeue=False):
            try:
                data = json.loads(message.body.decode("utf-8"))
                event = FaceClusterEvent.from_dict(data)

                logger.info(
                    f"收到人脸聚类事件: message_id={event.message_id}, "
                    f"user_id={event.user_id}, "
                    f"force={event.force_recluster}"
                )

                result = await face_clustering_engine.cluster(event)

                if result.success:
                    logger.info(
                        f"人脸聚类完成: user_id={event.user_id}, "
                        f"clusters={result.data.get('clusters', 0)}, "
                        f"noise={result.data.get('noise', 0)}"
                    )
                elif result.skipped:
                    logger.info(
                        f"人脸聚类跳过: user_id={event.user_id}, "
                        f"reason={result.skipped_reason}"
                    )
                else:
                    logger.error(
                        f"人脸聚类失败: user_id={event.user_id}, "
                        f"error={result.error}"
                    )
                    # 人脸聚类任务不重试，直接丢弃
                    await message.nack(requeue=False)

            except json.JSONDecodeError as e:
                logger.error(f"人脸聚类消息解析失败: error={e}")
                await message.nack(requeue=False)

            except Exception as e:
                logger.error(f"人脸聚类异常: error={e}", exc_info=True)
                await message.nack(requeue=False)


# =============================================================================
# 全局单例
# =============================================================================
face_cluster_consumer = FaceClusterConsumer()