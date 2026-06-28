"""
病毒扫描消费者 — 文件后台处理阶段 3/4

职责: 使用 ClamAV 扫描文件，发现病毒/木马则隔离
顺序: merge → hash_calculate → virus_scan → mark_active

失败策略:
  - VIRUS_FOUND → 不入 DLQ，直接隔离 + 通知业务服务
  - VIRUS_SCANNER_ERROR → 重试 / DLQ
  - VIRUS_SCANNER_UNAVAILABLE → 根据 fail_open 配置决定放行或拒绝
"""
from __future__ import annotations
import logging
from core.config import TaskTypes, FailureReason
from core.event.file_backend_event import FileBackendEvent
from core.consumers.backend.base_backend_consumer import BaseBackendConsumer
from core.services.file_processor import FileProcessor, ProcessResult

logger = logging.getLogger("virus_consumer")


class VirusConsumer(BaseBackendConsumer):
    """文件病毒扫描消费者"""

    @property
    def stage(self) -> str:
        return TaskTypes.VIRUS_SCAN

    async def process(self, event: FileBackendEvent) -> ProcessResult:
        logger.info(
            f"[VIRUS] START "
            f"backend_task_id={event.backend_task_id} "
            f"pipeline_id={event.pipeline_id} "
            f"storage_path={event.storage_path} "
            f"file_name={event.file_name}"
        )

        result = await FileProcessor._do_virus_scan(event)

        if result.success:
            skip = result.data.get("skipped", False)
            logger.info(
                f"[VIRUS] DONE "
                f"skipped={skip}"
            )
        else:
            infected = result.data.get("infected", False)
            threat = result.data.get("threat_name", "unknown")
            if infected:
                logger.critical(
                    f"[VIRUS] THREAT_FOUND "
                    f"file_id={event.file_id} "
                    f"file_name={event.file_name} "
                    f"threat={threat} "
                    f"→ 隔离"
                )
            else:
                logger.error(
                    f"[VIRUS] FAILED "
                    f"reason={result.failure_reason} "
                    f"error={result.error}"
                )

        return result


virus_consumer = VirusConsumer()


async def on_backend_virus_message(message):
    await virus_consumer.handle(message)