"""
标记活跃消费者 — 文件后台处理阶段 5/5 (流水线终点)

职责: 通知业务服务文件已可访问 (file.available 事件)
顺序: merge → content_preprocess → hash_calculate → virus_scan → mark_active
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
            if event.preprocess_gate_id:
                # 需求四-4：file.available 成功发布后才允许回收未选中副本。
                # 清理失败只记录并由 Sweeper 补偿，不能把已可访问文件重新打入失败态。
                from app.services.file_preprocess_gate_service import file_preprocess_gate_service

                try:
                    await file_preprocess_gate_service.mark_activation_and_cleanup(
                        event.preprocess_gate_id
                    )
                except Exception:
                    logger.exception(
                        "[MARK_ACTIVE] 预处理副本清理提交失败，将由 Sweeper 补偿 "
                        "file_id=%s gate_id=%s",
                        event.file_id,
                        event.preprocess_gate_id,
                    )
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
        # 文件生命周期可靠性修复：原行为每次重试生成随机 eventId，若 file.available
        # 已发布但后续 ACK 前进程退出，重试会让配额消费者误认为新事件。新行为使用
        # backend_task_id 派生稳定 ID，保留字段格式且让旧消费者自然获得幂等能力。
        stable_event_id = uuid.uuid5(
            uuid.NAMESPACE_URL,
            f"pcd:file.available:{event.backend_task_id}",
        ).hex
        available_event = {
            "eventId": stable_event_id,
            "fileId": event.file_id,
            "fileName": event.file_name,
            "fileSize": event.file_size,
            "fileType": event.file_type,
            "userId": event.user_id,
            "spaceId": event.space_id,
            "uploadsSessionId": getattr(event, 'uploads_id', ''),
            "eventTime": datetime.utcnow().isoformat(),#暂时不带时区
            # 文件生命周期预处理需求：全部为可选追加字段，旧 Java 消费者可安全忽略。
            "checksum": event.file_checksum,
            "storagePath": event.storage_path,
            "contentRevision": event.content_revision,
            "contentModified": event.content_modified,
            "preprocessStatus": event.preprocess_status,
            "correlationId": event.pipeline_id,
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
            # 原行为只记录日志仍返回成功，会导致消息 ACK 且 Platform 永远收不到
            # file.available；新行为抛出异常交给既有 Backend 重试/DLQ 机制。
            raise


mark_active_consumer = MarkActiveConsumer()


async def on_backend_mark_active_message(message):
    await mark_active_consumer.handle(message)
