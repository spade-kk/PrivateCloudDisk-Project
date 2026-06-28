"""
哈希计算消费者 — 文件后台处理阶段 2/4

职责: 计算文件 SHA-256 哈希值，与客户端上报的校验和比对
顺序: merge → hash_calculate → virus_scan → mark_active
"""
from __future__ import annotations
import logging
from core.config import TaskTypes
from core.event.file_backend_event import FileBackendEvent
from core.consumers.backend.base_backend_consumer import BaseBackendConsumer
from core.services.file_processor import FileProcessor, ProcessResult

logger = logging.getLogger("hash_consumer")


class HashConsumer(BaseBackendConsumer):
    """文件哈希计算消费者"""

    @property
    def stage(self) -> str:
        return TaskTypes.HASH_CALCULATE

    async def process(self, event: FileBackendEvent) -> ProcessResult:
        logger.info(
            f"[HASH] START "
            f"backend_task_id={event.backend_task_id} "
            f"pipeline_id={event.pipeline_id} "
            f"storage_path={event.storage_path} "
            f"expected_checksum={event.file_checksum[:12] if event.file_checksum else 'N/A'}..."
        )

        result = await FileProcessor._do_hash_calculate(event)

        if result.success:
            logger.info(
                f"[HASH] DONE "
                f"checksum={result.data.get('checksum', 'N/A')[:12]}..."
            )
        else:
            logger.error(
                f"[HASH] FAILED "
                f"reason={result.failure_reason} "
                f"error={result.error}"
            )

        return result


hash_consumer = HashConsumer()


async def on_backend_hash_message(message):
    await hash_consumer.handle(message)