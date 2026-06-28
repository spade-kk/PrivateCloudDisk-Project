"""
内容索引消费者 — 文件增强阶段 4/4

职责: 抽取文件内容并写入 OpenSearch 全文索引
特性: 并发执行，失败不影响文件可用性，仅标记 DEGRADED

支持的文件类型:
  - text/* (txt, csv, html, xml, etc.)
  - application/pdf
  - application/msword, application/vnd.openxmlformats-*
  - application/json
  - 图片 (OCR + 标签)
"""
from __future__ import annotations
import logging
from core.config import TaskTypes
from core.event.file_enhance_event import FileEnhanceEvent
from core.consumers.enhancement.base_enhance_consumer import BaseEnhanceConsumer
from core.services.file_processor import FileProcessor, ProcessResult

logger = logging.getLogger("index_consumer")


class IndexConsumer(BaseEnhanceConsumer):
    """内容索引消费者"""

    @property
    def stage(self) -> str:
        return TaskTypes.CONTENT_INDEX

    async def process(self, event: FileEnhanceEvent) -> ProcessResult:
        logger.info(
            f"[INDEX] START "
            f"enhance_task_id={event.enhance_task_id} "
            f"file_id={event.file_id} "
            f"file_name={event.file_name} "
            f"file_type={event.file_type}"
        )

        result = await FileProcessor._do_content_index(event)

        if result.success:
            extractor = result.data.get("extractor", "N/A")
            chunks = result.data.get("chunk_count", 0)
            logger.info(
                f"[INDEX] DONE "
                f"extractor={extractor} "
                f"chunks={chunks}"
            )
        else:
            logger.error(
                f"[INDEX] FAILED "
                f"reason={result.failure_reason} "
                f"error={result.error}"
            )

        return result


index_consumer = IndexConsumer()


async def on_enhance_index_message(message):
    await index_consumer.handle(message)