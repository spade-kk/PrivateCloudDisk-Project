"""
文件后台处理死信队列消费者 (Backend DLQ Consumer)

处理所有 backend 阶段死信，策略矩阵:

| 阶段        | 失败原因                | 策略                                |
|------------|------------------------|-------------------------------------|
| merge      | IO_ERROR               | 清理残留分片，通知业务服务回滚配额      |
| merge      | DISK_FULL              | 清理残留 + 告警 + 通知回滚配额        |
| merge      | CHUNK_MISSING          | 清理残留，标记上传会话 incomplete     |
| merge      | CHECKSUM_MISMATCH      | 删除不完整文件，通知回滚配额          |
| hash       | CALCULATE_ERROR        | 记录日志，人工检查文件                |
| hash       | HASH_MISMATCH          | 删除不匹配文件，通知回滚配额          |
| virus      | VIRUS_FOUND            | 隔离文件 + 通知安全服务（不重试）     |
| virus      | SCANNER_ERROR          | 重试扫描，超限标记为 SKIPPED         |
| virus      | SCANNER_UNAVAILABLE    | 根据 fail_open 配置决定放行/拒绝     |
| mark_active| NOTIFY_BS_ERROR        | 重试通知业务服务 (最多 3 次)          |
| null       | DELETE_IO_ERROR        | 日志记录 +  人工清理磁盘              |
| 全部       | UNKNOWN                | 全面记录日志，人工排查                |

关键: DLQ 处理时更新 Redis 事件状态键为 failed，并更新总任务状态为 failed
"""
from __future__ import annotations
import json
import logging
import os
from datetime import datetime, timezone
from typing import Callable

from core.config import (
    settings, FailureReason, TaskTypes, TaskStatus,
    REDIS_BACKEND_EVENT_KEY, REDIS_BACKEND_MASTER_KEY,
    MASTER_TASK_TTL,
)
from core.rabbitmq import rabbitmq_service
from core.consumers.dlq.base import BaseDLQConsumer
from core.services.notification_service import NotificationService
from core.services.retry_service import RetryService
from datetime import datetime

logger = logging.getLogger("backend_dlq_consumer")


class BackendDLQConsumer(BaseDLQConsumer):
    """后台处理死信消费者"""

    def __init__(self):
        super().__init__()
        self.retry = RetryService(max_attempts=3)

    def _get_dlq_source_name(self) -> str:
        return "file_backend"

    def _get_handler(self, failure_reason: str) -> Callable:
        handlers: dict[str, Callable] = {
            FailureReason.MERGE_IO_ERROR: self._handle_merge_error,
            FailureReason.MERGE_DISK_FULL: self._handle_merge_disk_full,
            FailureReason.MERGE_CHUNK_MISSING: self._handle_merge_chunk_missing,
            FailureReason.MERGE_CHECKSUM_MISMATCH: self._handle_checksum_mismatch,
            FailureReason.HASH_CALCULATE_ERROR: self._handle_hash_error,
            FailureReason.HASH_MISMATCH: self._handle_checksum_mismatch,
            FailureReason.VIRUS_FOUND: self._handle_virus_found,
            FailureReason.VIRUS_SCANNER_ERROR: self._handle_virus_scanner_error,
            FailureReason.VIRUS_SCANNER_UNAVAILABLE: self._handle_virus_scanner_unavailable,
            FailureReason.MARK_ACTIVE_ERROR: self._handle_retry_notify,
            FailureReason.NOTIFY_BS_ERROR: self._handle_retry_notify,
            FailureReason.DELETE_IO_ERROR: self._handle_delete_error,
            FailureReason.UNKNOWN: self._handle_unknown,
        }
        return handlers.get(failure_reason, self._handle_unknown)

    async def _mark_event_failed(self, data: dict):
        """
        更新 Redis 事件状态键为 failed + 总任务状态为 failed

        这是 DLQ 处理的核心步骤：标记事件彻底失败，让业务服务感知
        """
        backend_task_id = data.get("backend_task_id", "")
        stage = data.get("stage", "unknown")

        if not backend_task_id:
            logger.warning("[BACKEND-DLQ] 缺少 backend_task_id，无法更新事件状态")
            return

        try:
            from app.core.redis_client import redis_client

            # 1. 更新事件状态键为 failed
            event_key = REDIS_BACKEND_EVENT_KEY.format(
                backend_task_id=backend_task_id, stage=stage
            )
            await redis_client.setex(event_key, 86400 * 30, TaskStatus.FAILED)
            logger.error(
                f"[BACKEND-DLQ] EVENT→FAILED "
                f"key={event_key} "
                f"stage={stage}"
            )

            # 2. 更新总任务状态为 failed
            master_key = REDIS_BACKEND_MASTER_KEY.format(backend_task_id=backend_task_id)
            await redis_client.hset(master_key, mapping={
                "status": TaskStatus.FAILED,
                "current_stage": stage,
                "updated_at": datetime.now(timezone.utc).isoformat(),
            })
            await redis_client.expire(master_key, MASTER_TASK_TTL)
            logger.error(
                f"[BACKEND-DLQ] MASTER→FAILED "
                f"key={master_key} "
                f"stage={stage}"
            )

        except Exception as e:
            logger.error(f"[BACKEND-DLQ] 更新 Redis 失败状态失败: {e}")

    # ===== 合并相关 =====

    async def _handle_merge_error(self, data: dict) -> bool:
        """合并 I/O 错误 → 清理残留文件"""
        uploads_id = data.get("uploads_id", "")
        total_chunks = data.get("total_chunks", 0)
        file_id = data.get("file_id", "")

        logger.warning(
            f"[BACKEND-DLQ] MERGE_ERROR "
            f"uploads_id={uploads_id} file_id={file_id}"
        )

        logger.warning(f"[BACKEND-DLQ] 清理合并残留文件, uploads_id={uploads_id}")

        session_dir = settings.file_upload_dir
        for i in range(1, total_chunks + 1):
            chunk_path = os.path.join(session_dir, f"{uploads_id}-{i}.part")
            if os.path.exists(chunk_path):
                os.remove(chunk_path)

        final_path = os.path.join(session_dir, "storage", f"{uploads_id}-{total_chunks}.cloud")
        if os.path.exists(final_path):
            os.remove(final_path)

        # 标记事件失败 + 总任务失败
        await self._mark_event_failed(data)

        if data.get("failure_reason") == FailureReason.MERGE_IO_ERROR:
            try:
                await NotificationService.notify_file_status(
                    file_id=data.get("file_id", ""),
                    status="merge_failed",
                    user_id=data.get("user_id"),
                    error_message="合并 I/O 错误",
                )
            except Exception as e:
                logger.error(f"通知业务服务失败: {e}")
            await self._publish_file_merge_failed_event(data, "合并 I/O 错误")

        # REQ-UPLOAD-SESSION-STATE-2026-07：进入最终 DLQ 后才清理会话记录；重试阶段保留分块，
        # 避免 TTL 重试尚未执行就丢失可恢复数据。成功合并使用独立的 merge-cleanup，不回滚配额。
        await self._cleanup_upload_session_record(data)
        
        await self._log_dlq_action(data, "CLEANUP_RESIDUALS", "已清理合并残留", source="file_backend")
        return True

    async def _handle_merge_disk_full(self, data: dict) -> bool:
        """磁盘空间不足 → 告警"""
        logger.critical(
            f"[BACKEND-DLQ] DISK_FULL "
            f"need={settings.min_free_disk_bytes // (1024*1024)}MB"
        )
        await self._log_dlq_action(
            data, "DISK_FULL_ALERT",
            f"磁盘不足，需至少 {settings.min_free_disk_bytes // (1024*1024)}MB",
            source="file_backend",
        )
        logger.critical("[BACKEND-DLQ] 磁盘空间不足，需要运维介入!")
        await self._mark_event_failed(data)
        # 通知业务服务上传失败
        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="merge_failed",
                user_id=data.get("user_id"),
                error_message="磁盘空间不足",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")
        await self._publish_file_merge_failed_event(data, "磁盘空间不足")
        await self._handle_merge_error(data)
        return True

    async def _handle_merge_chunk_missing(self, data: dict) -> bool:
        """分片缺失 → 清理残留，通知业务服务"""
        logger.warning(f"[BACKEND-DLQ] CHUNK_MISSING file_id={data.get('file_id')}")
        await self._mark_event_failed(data)
        # 通知业务服务合并失败
        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="merge_failed",
                user_id=data.get("user_id"),
                error_message="分片缺失",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")
        await self._publish_file_merge_failed_event(data, "分片缺失")
        await self._handle_merge_error(data)
        return True

    # ===== 校验和 =====

    async def _handle_checksum_mismatch(self, data: dict) -> bool:
        """校验和不匹配 → 删除不完整文件"""
        uploads_id = data.get("uploads_id", "")
        total_chunks = int(data.get("total_chunks") or 0)
        session_dir = settings.file_upload_dir
        for i in range(1, total_chunks + 1):
            chunk_path = os.path.join(session_dir, f"{uploads_id}-{i}.part")
            if os.path.exists(chunk_path):
                os.remove(chunk_path)
        storage_path = data.get("storage_path", "")
        if storage_path and os.path.exists(storage_path):
            os.remove(storage_path)
            logger.warning(f"[BACKEND-DLQ] 已删除校验失败文件: {storage_path}")
        await self._mark_event_failed(data)
        await self._log_dlq_action(data, "DELETE_CORRUPTED", "已删除校验失败文件", source="file_backend")
        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="merge_failed",
                user_id=data.get("user_id"),
                error_message="校验和不匹配",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")
        await self._publish_file_merge_failed_event(data, "校验和不匹配")
        await self._cleanup_upload_session_record(data)
        return True

    async def _cleanup_upload_session_record(self, data: dict) -> None:
        """最终合并失败清理上传会话；失败仅告警，避免 DLQ 消费重复触发合并。"""
        uploads_id = data.get("uploads_id", "")
        if not uploads_id:
            return
        success = await NotificationService.notify_upload_session_merge_cleanup(uploads_id)
        if not success:
            logger.error("最终合并失败后清理上传会话未完成: uploads_id=%s", uploads_id)

    # ===== 哈希 =====

    async def _handle_hash_error(self, data: dict) -> bool:
        """Hash 计算错误 → 记录日志"""
        logger.error(
            f"[BACKEND-DLQ] HASH_ERROR "
            f"file_id={data.get('file_id')} storage_path={data.get('storage_path')}"
        )
        await self._mark_event_failed(data)
        await self._log_dlq_action(data, "HASH_ERROR", "哈希计算失败，需人工检查", source="file_backend")
        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="merge_failed",
                user_id=data.get("user_id"),
                error_message="Hash 计算失败",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")
        await self._publish_file_merge_failed_event(data, "哈希计算失败，需人工检查")
        return True

    # ===== 病毒扫描 =====

    async def _handle_virus_found(self, data: dict) -> bool:
        logger.critical(
            f"[BACKEND-DLQ] VIRUS_FOUND "
            f"file_id={data.get('file_id')} threat={data.get('threat_name', 'unknown')}"
        )
        await self._mark_event_failed(data)
        await self._log_dlq_action(data, "VIRUS_ISOLATED", "文件已隔离", source="file_backend")
        #await self._publish_file_scan_failed_event(data, "发现病毒/木马")
        return True

    async def _handle_virus_scanner_error(self, data: dict) -> bool:
        """病毒扫描器异常 → 通知 + 重试通知业务服务"""
        logger.error(f"[BACKEND-DLQ] SCANNER_ERROR file_id={data.get('file_id')}")
        await self._mark_event_failed(data)
        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="scan_failed",
                user_id=data.get("user_id"),
                error_message="病毒扫描器扫描器异常暂时不可用，等待重试",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")
        await self._publish_file_scan_failed_event(data, "病毒扫描器扫描器异常暂时不可用")
        await self._log_dlq_action(data, "SCANNER_ERROR", "ClamAV 扫描器异常", source="file_backend")
        return True

    async def _handle_virus_scanner_unavailable(self, data: dict) -> bool:
        """病毒扫描器不可用 → 告警"""
        logger.critical(f"[BACKEND-DLQ] SCANNER_UNAVAILABLE file_id={data.get('file_id')}")
        await self._mark_event_failed(data)
        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="scan_failed",
                user_id=data.get("user_id"),
                error_message="病毒扫描器暂时不可用，等待重试",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")
        await self._publish_file_scan_failed_event(data, "病毒扫描器暂时不可用")
        await self._log_dlq_action(
            data, "SCANNER_UNAVAILABLE",
            "ClamAV 不可用，需检查服务",
            source="file_backend",
        )
        return True

    # ===== 通知重试 =====

    async def _handle_retry_notify(self, data: dict) -> bool:
        logger.warning(f"[BACKEND-DLQ] RETRY_NOTIFY file_id={data.get('file_id')}")
        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="marked_active",
                user_id=data.get("user_id"),
                error_message="",
            )
            return True
        except Exception as e:
            logger.error(f"[BACKEND-DLQ] 通知重试失败: {e}")
            await self._mark_event_failed(data)
            return False
    
    # ========== 删除 ==========

    async def _handle_delete_error(self, data: dict) -> bool:
        """文件删除错误 → 记录日志"""
        storage_path = data.get("storage_path", "")
        logger.error(f"[BACKEND-DLQ] 文件删除失败, storage_path={storage_path}")
        await self._log_dlq_action(
            data, "DELETE_FAILED", "文件删除失败，请手动清理",
            source="file_backend",
        )
        return True

    # ===== 未知 =====

    async def _handle_unknown(self, data: dict) -> bool:
        logger.error(
            f"[BACKEND-DLQ] UNKNOWN "
            f"file_id={data.get('file_id')} "
            f"stage={data.get('stage')} "
            f"reason={data.get('failure_reason')}"
        )
        await self._mark_event_failed(data)
        await self._log_dlq_action(
            data, "UNKNOWN",
            f"未知失败原因: {data.get('failure_reason')}",
            source="file_backend",
        )
        return True

    # ===== 辅助 =====

    async def _publish_file_merge_failed_event(self, data: dict, reason: str):
        import uuid, time
        event = {
            "eventId": uuid.uuid4().hex,
            "fileId": data.get("file_id", ""),
            "fileName": data.get("file_name", ""),
            "fileSize": data.get("file_size", 0),
            "fileType": data.get("file_type", ""),
            "userId": data.get("user_id", ""),
            # 需求五-9：失败事件必须保留空间，否则主业务服务会错误回滚个人配额。
            "spaceId": data.get("space_id", ""),
            "uploadsSessionId": data.get("uploads_id", ""),
            "eventTime":  datetime.utcnow().isoformat(),#暂时不带时区
        }
        try:
            await rabbitmq_service.publish_message(
                exchange_name=settings.file_event_exchange,
                routing_key=settings.file_merge_failed_routing_key,
                message=event,
            )
        except Exception as e:
            logger.error(f"[BACKEND-DLQ] 发布 merge.failed 事件失败: {e}")

    async def _publish_file_scan_failed_event(self, data: dict, reason: str):
        import uuid, time
        event = {
            "eventId": uuid.uuid4().hex,
            "fileId": data.get("file_id", ""),
            "fileName": data.get("file_name", ""),
            "fileSize": data.get("file_size", 0),
            "fileType": data.get("file_type", ""),
            "userId": data.get("user_id", ""),
            "spaceId": data.get("space_id", ""),
            "uploadsSessionId": data.get("uploads_id", ""),
            "eventTime":  datetime.utcnow().isoformat(),#暂时不带时区
        }
        try:
            await rabbitmq_service.publish_message(
                exchange_name=settings.file_event_exchange,
                routing_key=settings.file_scan_failed_routing_key,
                message=event,
            )
        except Exception as e:
            logger.error(f"[BACKEND-DLQ] 发布 scan.failed 事件失败: {e}")


backend_dlq_consumer = BackendDLQConsumer()


async def on_backend_dlq_message(message):
    """处理所有 backend 阶段 DLQ 的消息"""
    await backend_dlq_consumer.handle(message)
