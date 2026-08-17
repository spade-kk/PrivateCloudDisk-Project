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
    FailureReason, TaskStatus, TaskTypes, settings,
    REDIS_ENHANCE_EVENT_KEY, REDIS_ENHANCE_MASTER_KEY,
    MASTER_TASK_TTL,
)
from core.consumers.dlq.base import BaseDLQConsumer
from core.rabbitmq import rabbitmq_service
from core.services.notification_service import NotificationService

logger = logging.getLogger("enhance_dlq_consumer")


class EnhanceDLQConsumer(BaseDLQConsumer):
    """增强处理死信消费者"""

    # 具体阶段可覆盖该值，形成独立 DLQ 恢复策略；未覆盖时沿用历史全局配置。
    dlq_recovery_max_attempts: int | None = None

    def _get_dlq_source_name(self) -> str:
        return "file_enhance"

    def _get_handler(self, failure_reason: str) -> Callable:
        handlers: dict[str, Callable] = {
            # FailureReason.THUMBNAIL_ERROR: self._handle_recoverable,
            # FailureReason.TRANSCODE_ERROR: self._handle_recoverable,
            # FailureReason.CONTENT_EXTRACT_ERROR: self._handle_recoverable,
            # FailureReason.CONTENT_INDEX_ERROR: self._handle_recoverable,
            # FailureReason.OFFICE_TO_PDF_ERROR: self._handle_recoverable,
            # FailureReason.ARCHIVE_PARSE_ERROR: self._handle_recoverable,
            # FailureReason.UNKNOWN: self._handle_recoverable,
            FailureReason.THUMBNAIL_ERROR: self._handle_degraded,
            FailureReason.TRANSCODE_ERROR: self._handle_degraded,
            FailureReason.CONTENT_EXTRACT_ERROR: self._handle_degraded,
            FailureReason.CONTENT_INDEX_ERROR: self._handle_degraded,
            FailureReason.OFFICE_TO_PDF_ERROR: self._handle_degraded,
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
        stage = str(data.get("stage") or data.get("task_type") or "unknown")

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

    async def _handle_recoverable(self, data: dict) -> bool:
        """对增强死信执行有界自动恢复，耗尽后再进入透明降级和告警流程。"""
        stage = str(data.get("stage") or data.get("task_type") or "unknown")
        routing_keys = {
            TaskTypes.THUMBNAIL: settings.file_enhance_thumbnail_routing_key,
            TaskTypes.VIDEO_TRANSCODE: settings.file_enhance_transcode_routing_key,
            TaskTypes.HLS_TRANSCODE: settings.file_enhance_hls_routing_key,
            TaskTypes.CONTENT_INDEX: settings.file_enhance_index_routing_key,
            TaskTypes.OFFICE_TO_PDF: settings.file_enhance_office_to_pdf_routing_key,
            TaskTypes.ARCHIVE_PARSE: settings.file_enhance_archive_parse_routing_key,
        }
        try:
            recovery_count = max(0, int(data.get("dlq_recovery_count") or 0))
        except (TypeError, ValueError):
            recovery_count = 0

        max_recoveries = (
            self.dlq_recovery_max_attempts
            if self.dlq_recovery_max_attempts is not None
            else settings.enhance_dlq_recovery_max_attempts
        )
        if stage in routing_keys and recovery_count < max_recoveries:
            next_recovery = recovery_count + 1
            delay = min(
                settings.retry_base_delay_seconds * (2 ** recovery_count),
                settings.retry_max_delay_seconds,
            )
            retry_data = dict(data)
            retry_data.update({
                "stage": stage,
                "task_type": stage,
                # AUDIT FIX [7.4]（需求一-3）:
                # DLQ 恢复是新的、有界重试周期；阶段内 retry_count 归零，独立计数防止无限循环。
                "retry_count": 0,
                "dlq_recovery_count": next_recovery,
                "failure_reason": str(data.get("failure_reason") or FailureReason.UNKNOWN),
            })
            from app.core.redis_client import redis_client
            enhance_task_id = str(data.get("enhance_task_id") or "")
            if enhance_task_id:
                await redis_client.delete(
                    REDIS_ENHANCE_EVENT_KEY.format(enhance_task_id=enhance_task_id, stage=stage)
                )

            await rabbitmq_service.publish_retry_message(
                exchange_name=settings.file_enhance_exchange,
                routing_key=routing_keys[stage],
                message=retry_data,
                delay_seconds=delay,
            )
            await self._log_dlq_action(
                retry_data,
                "AUTO_RETRY",
                f"增强死信将在 {delay} 秒后执行第 {next_recovery} 次恢复",
                source="file_enhance",
            )
            from app.repositories.dlq_record_repository import dlq_record_repository
            await dlq_record_repository.update_disposition(
                source_queue="file_enhance",
                stage=stage,
                payload=retry_data,
                status="retrying",
                note=f"第 {next_recovery} 次自动恢复已进入持久化延迟队列",
            )
            logger.warning(
                "[ENHANCE-DLQ] AUTO_RETRY stage=%s file_id=%s recovery=%s/%s delay=%ss",
                stage,
                data.get("file_id"),
                next_recovery,
                max_recoveries,
                delay,
            )
            return True

        # 未知阶段不能安全路由；已耗尽的已知阶段也必须停止重试并进入降级。
        if stage not in routing_keys:
            return await self._handle_unknown(data)
        return await self._handle_degraded(data)

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

        await NotificationService.notify_ops_alert(
            title=f"文件增强任务自动恢复耗尽: {stage}",
            severity="warning",
            details={
                "file_id": file_id,
                "enhance_task_id": data.get("enhance_task_id"),
                "failure_reason": reason,
                "failure_detail": data.get("failure_detail"),
                "dlq_recovery_count": data.get("dlq_recovery_count", 0),
            },
        )

        # from app.repositories.dlq_record_repository import dlq_record_repository
        # await dlq_record_repository.update_disposition(
        #     source_queue="file_enhance",
        #     stage=stage,
        #     payload=data,
        #     status="discarded",
        #     note="自动恢复次数耗尽，文件已标记为 degraded",
        # )

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
        # AUDIT FIX [7.4]（需求一-3）: 未知阶段不盲目路由，持久化丢弃状态并输出高等级告警日志。
        logger.critical(
            "[ENHANCE-DLQ] ALERT 未知增强阶段需人工介入: file_id=%s payload_stage=%s",
            data.get("file_id"),
            data.get("stage"),
        )
        await NotificationService.notify_ops_alert(
            title="文件增强死信任务类型无法识别",
            severity="critical",
            details={
                "file_id": data.get("file_id"),
                "enhance_task_id": data.get("enhance_task_id"),
                "stage": data.get("stage"),
                "failure_reason": data.get("failure_reason"),
                "failure_detail": data.get("failure_detail"),
            },
        )
        # from app.repositories.dlq_record_repository import dlq_record_repository
        # await dlq_record_repository.update_disposition(
        #     source_queue="file_enhance",
        #     stage=str(data.get("stage") or "unknown"),
        #     payload=data,
        #     status="discarded",
        #     note="任务类型无法识别，已停止自动重试并触发告警",
        # )
        return True


enhance_dlq_consumer = EnhanceDLQConsumer()


async def on_enhance_dlq_message(message):
    """处理所有增强阶段 DLQ 的消息"""
    await enhance_dlq_consumer.handle(message)
