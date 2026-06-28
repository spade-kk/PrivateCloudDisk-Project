"""
标记活跃消费者 — 文件后台处理阶段 4/4 (流水线终点)

职责: 通知业务服务文件已可访问 (file.available 事件)
顺序: merge → hash_calculate → virus_scan → mark_active
       ↑ 流水线终点 → 总任务 completed → 触发增强事件 (thumbnail / transcode / hls / index)

成功后:
  1. 发布 file.available 事件 → 主业务服务提交配额
  2. 总任务状态更新为 completed（由基类处理）
  3. 发布增强事件（由基类 _publish_enhance_events 扇出到 enhance exchange）
"""
from __future__ import annotations
import logging
from core.config import TaskTypes, settings
from core.event.file_backend_event import FileBackendEvent
from core.consumers.backend.base_backend_consumer import BaseBackendConsumer
from core.services.file_processor import FileProcessor, ProcessResult
from core.rabbitmq import rabbitmq_service
from datetime import datetime

logger = logging.getLogger("mark_active_consumer")


class MarkActiveConsumer(BaseBackendConsumer):
    """文件标记活跃消费者（流水线终点）"""

    @property
    def stage(self) -> str:
        return TaskTypes.MARK_ACTIVE

    async def process(self, event: FileBackendEvent) -> ProcessResult:
        logger.info(
            f"[MARK_ACTIVE] START "
            f"backend_task_id={event.backend_task_id} "
            f"pipeline_id={event.pipeline_id} "
            f"file_id={event.file_id} "
            f"file_name={event.file_name}"
        )

        result = await FileProcessor._do_mark_active(event)

        if result.success:
            logger.info(
                f"[MARK_ACTIVE] DONE "
                f"file_id={event.file_id} "
                f"→ 文件可访问"
            )
            # 发布 file.available 事件到主业务服务
            await self._publish_file_available_event(event)
        else:
            logger.error(
                f"[MARK_ACTIVE] FAILED "
                f"reason={result.failure_reason} "
                f"error={result.error}"
            )

        return result

    async def _publish_file_available_event(self, event: FileBackendEvent):
        """发布文件可获得事件到主业务服务"""
        import uuid
        import time

        # available_event = {
        #     "messageId": str(uuid.uuid4()),
        #     "fileId": event.file_id,
        #     "userId": event.user_id,
        #     "fileName": event.file_name,
        #     "fileType": event.file_type,
        #     "fileSize": event.file_size,
        #     "storagePath": event.storage_path,
        #     "checksum": event.file_checksum,
        #     "timestamp": int(time.time() * 1000),
        # }
        available_event = {
            "eventId": uuid.uuid4().hex,
            "fileId": event.file_id,
            "fileName": event.file_name,
            "fileSize": event.file_size,
            "fileType": event.file_type,
            "userId": event.user_id,
            "uploadsSessionId": getattr(event, 'uploads_id', ''),
            "eventTime": datetime.utcnow().isoformat(),#暂时不带时区
        }

        try:
            await rabbitmq_service.publish_message(
                exchange_name=settings.file_event_exchange,
                routing_key=settings.file_available_routing_key,
                message=available_event,
            )
            logger.info(
                f"[MARK_ACTIVE] file.available 已发布 "
                f"file_id={event.file_id}"
            )
        except Exception as e:
            logger.error(
                f"[MARK_ACTIVE] file.available 发布失败 "
                f"file_id={event.file_id} error={e}"
            )


mark_active_consumer = MarkActiveConsumer()


async def on_backend_mark_active_message(message):
    await mark_active_consumer.handle(message)