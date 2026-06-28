"""
视频转码消费者 — 文件增强阶段 2/4

职责: 将视频文件转码为多分辨率 MP4
特性: 并发执行，失败不影响文件可用性，仅标记 DEGRADED
"""
from __future__ import annotations
import logging
from core.config import TaskTypes
from core.event.file_enhance_event import FileEnhanceEvent
from core.consumers.enhancement.base_enhance_consumer import BaseEnhanceConsumer
from core.services.file_processor import FileProcessor, ProcessResult

logger = logging.getLogger("transcode_consumer")


class TranscodeConsumer(BaseEnhanceConsumer):
    """视频转码消费者"""

    @property
    def stage(self) -> str:
        return TaskTypes.VIDEO_TRANSCODE

    async def process(self, event: FileEnhanceEvent) -> ProcessResult:
        logger.info(
            f"[TRANSCODE] START "
            f"enhance_task_id={event.enhance_task_id} "
            f"file_id={event.file_id} "
            f"file_type={event.file_type}"
        )

        result = await FileProcessor._do_video_transcode(event)

        if result.success:
            skip = result.data.get("skipped", False)
            count = len(result.data.get("transcoded_files", []))
            logger.info(
                f"[TRANSCODE] DONE "
                f"files={count} "
                f"skipped={skip}"
            )
        else:
            logger.error(
                f"[TRANSCODE] FAILED "
                f"reason={result.failure_reason} "
                f"error={result.error}"
            )

        return result


transcode_consumer = TranscodeConsumer()


async def on_enhance_transcode_message(message):
    await transcode_consumer.handle(message)