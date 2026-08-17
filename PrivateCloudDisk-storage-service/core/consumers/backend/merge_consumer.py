"""
合并消费者 — 文件后台处理阶段 1/5

职责: 合并分片文件为完整文件
顺序: merge → content_preprocess → hash_calculate → virus_scan → mark_active
"""
from __future__ import annotations
import logging
from core.config import TaskTypes, FailureReason
from core.event.file_backend_event import FileBackendEvent
from core.consumers.backend.base_backend_consumer import BaseBackendConsumer
from core.services.file_processor import FileProcessor, ProcessResult

logger = logging.getLogger("merge_consumer")


class MergeConsumer(BaseBackendConsumer):
    """文件合并消费者"""

    @property
    def stage(self) -> str:
        return TaskTypes.MERGE

    async def process(self, event: FileBackendEvent) -> ProcessResult:
        logger.info(
            f"[MERGE] START "
            f"backend_task_id={event.backend_task_id} "
            f"pipeline_id={event.pipeline_id} "
            f"uploads_id={event.uploads_id} "
            f"total_chunks={event.total_chunks} "
            f"file_name={event.file_name}"
        )

        result = await FileProcessor._do_merge(event)

        if result.success:
            logger.info(
                f"[MERGE] DONE "
                f"file_id={result.data.get('file_id')} "
                f"storage_path={result.data.get('storage_path')} "
                f"size={result.data.get('file_size')} bytes "
                f"checksum={result.data.get('checksum', 'N/A')[:12]}..."
            )
        else:
            logger.error(
                f"[MERGE] FAILED "
                f"reason={result.failure_reason} "
                f"error={result.error}"
            )

        return result


# 模块级消费者实例
merge_consumer = MergeConsumer()


async def on_backend_merge_message(message):
    """RabbitMQ 回调入口"""
    await merge_consumer.handle(message)
