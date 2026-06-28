"""
HLS 转码消费者 — 文件增强阶段 3/4

职责: 将视频文件转码为 HLS 流媒体格式 (多码率 + 切片)
特性: 并发执行，失败不影响文件可用性，仅标记 DEGRADED
"""
from __future__ import annotations
import logging
from core.config import TaskTypes
from core.event.file_enhance_event import FileEnhanceEvent
from core.consumers.enhancement.base_enhance_consumer import BaseEnhanceConsumer
from core.services.file_processor import FileProcessor, ProcessResult

logger = logging.getLogger("hls_consumer")


class HlsConsumer(BaseEnhanceConsumer):
    """HLS 流媒体转码消费者"""

    @property
    def stage(self) -> str:
        return TaskTypes.HLS_TRANSCODE

    async def process(self, event: FileEnhanceEvent) -> ProcessResult:
        logger.info(
            f"[HLS] START "
            f"enhance_task_id={event.enhance_task_id} "
            f"file_id={event.file_id} "
            f"file_type={event.file_type}"
        )

        result = await FileProcessor._do_hls_transcode(event)

        if result.success:
            skip = result.data.get("skipped", False)
            resolutions = result.data.get("resolutions", [])
            logger.info(
                f"[HLS] DONE "
                f"resolutions={resolutions} "
                f"skipped={skip}"
            )
        else:
            logger.error(
                f"[HLS] FAILED "
                f"reason={result.failure_reason} "
                f"error={result.error}"
            )

        return result


hls_consumer = HlsConsumer()


async def on_enhance_hls_message(message):
    await hls_consumer.handle(message)