"""
缩略图消费者 — 文件增强阶段 1/4

职责: 为图片/视频文件生成缩略图
特性: 并发执行，失败不影响文件可用性，仅标记 DEGRADED
"""
from __future__ import annotations
import logging
from core.config import TaskTypes
from core.event.file_enhance_event import FileEnhanceEvent
from core.consumers.enhancement.base_enhance_consumer import BaseEnhanceConsumer
from core.services.file_processor import FileProcessor, ProcessResult

logger = logging.getLogger("thumbnail_consumer")


class ThumbnailConsumer(BaseEnhanceConsumer):
    """缩略图生成消费者"""

    @property
    def stage(self) -> str:
        return TaskTypes.THUMBNAIL

    async def process(self, event: FileEnhanceEvent) -> ProcessResult:
        logger.info(
            f"[THUMBNAIL] START "
            f"enhance_task_id={event.enhance_task_id} "
            f"file_id={event.file_id} "
            f"file_type={event.file_type}"
        )

        result = await FileProcessor._do_thumbnail(event)

        if result.success:
            skip = result.data.get("skipped", False)
            count = len(result.data.get("thumbnails", []))
            logger.info(
                f"[THUMBNAIL] DONE "
                f"thumbnails={count} "
                f"skipped={skip}"
            )
        else:
            logger.error(
                f"[THUMBNAIL] FAILED "
                f"reason={result.failure_reason} "
                f"error={result.error}"
            )

        return result


thumbnail_consumer = ThumbnailConsumer()


async def on_enhance_thumbnail_message(message):
    await thumbnail_consumer.handle(message)