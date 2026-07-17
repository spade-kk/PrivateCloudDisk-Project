"""
文件增强处理死信队列消费者 (Enhance DLQ Consumer)

处理所有增强阶段死信，策略矩阵:

| 阶段        | 失败原因              | 策略                                |
|------------|----------------------|-------------------------------------|
| thumbnail  | THUMBNAIL_ERROR      | 标记文件为 DEGRADED (无缩略图)      |
| transcode  | TRANSCODE_ERROR      | 标记文件为 DEGRADED (无转码)        |
| hls        | TRANSCODE_ERROR      | 标记文件为 DEGRADED (无 HLS)        |
| index      | CONTENT_EXTRACT_ERROR| 标记文件为 DEGRADED (无索引)        |
| index      | CONTENT_INDEX_ERROR  | 标记文件为 DEGRADED (无索引)        |
| office/PDF | OFFICE_TO_PDF_ERROR  | 标记文件为 DEGRADED (无 PDF 预览)    |
| markdown   | MARKDOWN_TO_HTML_ERR | 标记文件为 DEGRADED (无 HTML 预览)   |
| archive    | ARCHIVE_PARSE_ERROR  | 标记文件为 DEGRADED (无目录预览)      |
| 全部       | UNKNOWN              | 全面记录日志，人工排查               |

与 Backend DLQ 的关键区别:
  - 增强失败不触发回滚配额，仅标记文件为 DEGRADED
  - 增强失败不通知用户（透明降级）
  - 更新 Redis 增强事件状态键为 failed
"""
from __future__ import annotations
import logging
from datetime import datetime, timezone
from typing import Callable

from core.config import (
    FailureReason, TaskStatus,
    REDIS_ENHANCE_EVENT_KEY, REDIS_ENHANCE_MASTER_KEY,
    MASTER_TASK_TTL,
)
from core.consumers.dlq.base import BaseDLQConsumer
from core.services.notification_service import NotificationService

logger = logging.getLogger("enhance_dlq_consumer")


class EnhanceDLQConsumer(BaseDLQConsumer):
    """增强处理死信消费者"""

    def _get_dlq_source_name(self) -> str:
        return "file_enhance"

    def _get_handler(self, failure_reason: str) -> Callable:
        handlers: dict[str, Callable] = {
            FailureReason.THUMBNAIL_ERROR: self._handle_degraded,
            FailureReason.TRANSCODE_ERROR: self._handle_degraded,
            FailureReason.CONTENT_EXTRACT_ERROR: self._handle_degraded,
            FailureReason.CONTENT_INDEX_ERROR: self._handle_degraded,
            FailureReason.OFFICE_TO_PDF_ERROR: self._handle_degraded,
            FailureReason.MARKDOWN_TO_HTML_ERROR: self._handle_degraded,
            FailureReason.ARCHIVE_PARSE_ERROR: self._handle_degraded,
            FailureReason.UNKNOWN: self._handle_unknown,
        }
        return handlers.get(failure_reason, self._handle_unknown)

    async def _mark_event_failed(self, data: dict):
        """
        更新 Redis 增强事件状态键为 failed

        增强失败不更新总任务状态（增强不影响文件可用性）
        """
        enhance_task_id = data.get("enhance_task_id", "")
        stage = data.get("stage", "unknown")

        if not enhance_task_id:
            logger.warning("[ENHANCE-DLQ] 缺少 enhance_task_id，无法更新事件状态")
            return

        try:
            from app.core.redis_client import redis_client

            event_key = REDIS_ENHANCE_EVENT_KEY.format(
                enhance_task_id=enhance_task_id, stage=stage
            )
            await redis_client.setex(event_key, 86400 * 30, TaskStatus.FAILED)
            logger.error(
                f"[ENHANCE-DLQ] EVENT→FAILED "
                f"key={event_key} "
                f"stage={stage}"
            )
        except Exception as e:
            logger.error(f"[ENHANCE-DLQ] 更新 Redis 失败状态失败: {e}")

    async def _handle_degraded(self, data: dict) -> bool:
        """
        增强失败 → 标记文件为 DEGRADED

        增强失败不影响文件可用性，文件仍可下载/预览，
        只是缺少缩略图/转码/索引等增强功能。
        """
        stage = data.get("stage", "unknown")
        file_id = data.get("file_id", "")
        reason = data.get("failure_reason", "")

        logger.warning(
            f"[ENHANCE-DLQ] DEGRADED "
            f"stage={stage} "
            f"file_id={file_id} "
            f"reason={reason}"
        )

        # 更新 Redis 事件状态为 failed
        await self._mark_event_failed(data)

        await self._log_dlq_action(
            data, "DEGRADED",
            f"增强阶段 {stage} 失败，文件标记为 DEGRADED",
            source="file_enhance",
        )

        logger.warning(
            f"[ENHANCE-DLQ] 非核心功能降级, file_id={file_id}, stage={stage}"
        )

        # 通知业务服务文件状态为 DEGRADED
        try:
            await NotificationService.notify_file_status(
                file_id=file_id,
                status="degraded",
                user_id=data.get("user_id"),
                error_message=f"增强阶段 {stage} 失败: {reason}",
            )
        except Exception as e:
            logger.error(f"[ENHANCE-DLQ] 通知业务服务失败: {e}")

        return True

    async def _handle_unknown(self, data: dict) -> bool:
        logger.error(
            f"[ENHANCE-DLQ] UNKNOWN "
            f"stage={data.get('stage')} "
            f"file_id={data.get('file_id')} "
            f"reason={data.get('failure_reason')}"
        )
        await self._mark_event_failed(data)
        await self._log_dlq_action(
            data, "UNKNOWN",
            f"未知失败: {data.get('failure_reason')}",
            source="file_enhance",
        )
        return True


enhance_dlq_consumer = EnhanceDLQConsumer()


async def on_enhance_dlq_message(message):
    """处理所有增强阶段 DLQ 的消息"""
    await enhance_dlq_consumer.handle(message)