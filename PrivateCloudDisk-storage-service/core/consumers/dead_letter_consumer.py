"""
文件处理死信队列消费者 (File Process DLQ Consumer)

职责：处理来自 pcd.file.process.dlq 的所有死信消息

策略矩阵:
| 失败原因                    | DLQ 处理策略                                      | 后续操作                     |
|----------------------------|---------------------------------------------------|------------------------------|
| MERGE_IO_ERROR             | 清理残留分片文件，更新任务状态为 FAILED            | 人工介入                     |
| MERGE_DISK_FULL            | 清理残留文件 + 告警日志                            | 运维扩容磁盘                 |
| MERGE_CHUNK_MISSING        | 清理残留文件，标记上传会话为 incomplete            | 用户重新上传                 |
| MERGE_CHECKSUM_MISMATCH    | 删除不完整文件                                    | 用户重新上传                 |
| HASH_CALCULATE_ERROR       | 记录错误日志                                      | 人工检查文件                 |
| HASH_MISMATCH              | 删除不匹配文件                                    | 用户重新上传                 |
| VIRUS_SCANNER_ERROR        | 记录日志 + 通知业务服务                           | 检查 ClamAV 服务             |
| VIRUS_SCANNER_UNAVAILABLE  | 记录日志 + 告警                                   | 部署/启动 ClamAV             |
| THUMBNAIL_ERROR            | 标记文件为 DEGRADED (无缩略图)                    | 人工检查 libvips             |
| TRANSCODE_ERROR            | 标记文件为 DEGRADED (无转码)                      | 人工检查 ffmpeg              |
| MARK_ACTIVE_ERROR          | 重试通知业务服务 (最多 3 次)                      | 检查业务服务是否在线         |
| NOTIFY_BS_ERROR            | 重试通知业务服务 (最多 3 次)                      | 检查业务服务是否在线         |
| DELETE_IO_ERROR            | 记录日志                                          | 人工清理磁盘                 |
| UNKNOWN                    | 全面记录日志                                      | 人工排查                     |

注意：上传事件相关的 DLQ 处理已迁移到 dlq/uploads_event_dlq_consumer.py
     本文件仅处理文件处理流水线（merge/hash/virus/transcode/...）的死信。

与 Spring Boot MQ 方案的对应:
- Spring Boot DeadLetterConsumer (file process 分支) → 本文件
- 多 Channel 分发 (@RabbitListener) → 根据 failure_reason 路由
- sendLog / sendAlert → SecurityReporter + logger
"""
from __future__ import annotations
import json
import logging
import os
from datetime import datetime, timezone
from typing import Any, Callable

from core.config import settings, TaskTypes, FailureReason
from core.rabbitmq import rabbitmq_service
from core.event.file_process_event import FileProcessEvent
from core.services.notification_service import NotificationService
from core.services.retry_service import RetryService
from core.consumers.dlq.base import BaseDLQConsumer

logger = logging.getLogger("dead_letter_consumer")

# DLQ 最大重试次数（仅针对可恢复的错误）
DLQ_MAX_RETRIES = 3


class DeadLetterConsumer(BaseDLQConsumer):
    """
    文件处理死信队列消费者

    处理所有因重试耗尽而进入 pcd.file.process.dlq 的消息
    根据 failure_reason 采用不同的处置策略
    """

    def __init__(self):
        super().__init__()
        self.retry = RetryService(max_attempts=DLQ_MAX_RETRIES)

    def _get_dlq_source_name(self) -> str:
        return "file_process"

    def _get_handler(self, failure_reason: str) -> Callable:
        """根据 failure_reason 返回对应的处理函数（仅文件处理域）"""
        handlers: dict[str, Callable] = {
            FailureReason.MERGE_IO_ERROR: self._handle_merge_error,
            FailureReason.MERGE_DISK_FULL: self._handle_merge_disk_full,
            FailureReason.MERGE_CHUNK_MISSING: self._handle_merge_chunk_missing,
            FailureReason.MERGE_CHECKSUM_MISMATCH: self._handle_checksum_mismatch,
            FailureReason.HASH_CALCULATE_ERROR: self._handle_hash_error,
            FailureReason.HASH_MISMATCH: self._handle_checksum_mismatch,
            FailureReason.VIRUS_SCANNER_ERROR: self._handle_virus_scanner_error,
            FailureReason.VIRUS_SCANNER_UNAVAILABLE: self._handle_virus_scanner_unavailable,
            FailureReason.THUMBNAIL_ERROR: self._handle_degraded_service,
            FailureReason.TRANSCODE_ERROR: self._handle_degraded_service,
            FailureReason.MARK_ACTIVE_ERROR: self._handle_retry_notify,
            FailureReason.NOTIFY_BS_ERROR: self._handle_retry_notify,
            FailureReason.DELETE_IO_ERROR: self._handle_delete_error,
            FailureReason.UNKNOWN: self._handle_unknown,
        }
        return handlers.get(failure_reason, self._handle_unknown)

    # ========== 合并相关 ==========

    async def _handle_merge_error(self, data: dict) -> bool:
        """合并 I/O 错误 → 清理残留文件"""
        uploads_id = data.get("uploads_id", "")
        total_chunks = data.get("total_chunks", 0)

        logger.warning(f"DLQ: 清理合并残留文件, uploads_id={uploads_id}")

        # 清理残留分片
        import os
        session_dir = settings.file_upload_dir
        for i in range(1, total_chunks + 1):
            chunk_path = os.path.join(session_dir, f"{uploads_id}-{i}.part")
            if os.path.exists(chunk_path):
                os.remove(chunk_path)

        final_path = os.path.join(
            session_dir, "storage", f"{uploads_id}-{total_chunks}.cloud"
        )
        if os.path.exists(final_path):
            os.remove(final_path)

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

        # 发布 MQ 事件：通知主业务服务回滚配额
        await self._publish_file_merge_failed_event(data, "合并 I/O 错误")
        await self._log_dlq_action(
            data, "CLEANUP_RESIDUALS", "已清理合并残留文件",
            source="file_process",
        )
        return True

    async def _handle_merge_disk_full(self, data: dict) -> bool:
        """磁盘空间不足 → 告警"""
        logger.critical("DLQ: 磁盘空间不足，需要运维介入!")
        await self._log_dlq_action(
            data, "DISK_FULL_ALERT",
            f"磁盘空间不足，需要至少 {settings.min_free_disk_bytes / (1024 * 1024):.0f}MB",
            source="file_process",
        )

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

        # 发布 MQ 事件：通知主业务服务回滚配额
        await self._publish_file_merge_failed_event(data, "磁盘空间不足")

        # 也清理残留文件
        await self._handle_merge_error(data)
        return True

    async def _handle_merge_chunk_missing(self, data: dict) -> bool:
        """分片缺失 → 清理残留，通知业务服务"""
        logger.warning("DLQ: 分片缺失, 标记上传会话为 incomplete")

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

        # 发布 MQ 事件：通知主业务服务回滚配额
        await self._publish_file_merge_failed_event(data, "分片缺失")
        await self._handle_merge_error(data)
        return True

    # ========== 校验和 ==========

    async def _handle_checksum_mismatch(self, data: dict) -> bool:
        """校验和不匹配 → 删除不完整文件"""
        storage_path = data.get("storage_path", "")

        if storage_path and os.path.exists(storage_path):
            os.remove(storage_path)
            logger.warning(f"DLQ: 已删除校验失败的文件: {storage_path}")

        await self._log_dlq_action(
            data, "DELETE_CORRUPTED", "已删除校验失败的文件",
            source="file_process",
        )
        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="merge_failed",
                user_id=data.get("user_id"),
                error_message="校验和不匹配",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")

        # 发布 MQ 事件：通知主业务服务回滚配额
        await self._publish_file_merge_failed_event(data, "校验和不匹配")
        return True

    # ========== Hash ==========

    async def _handle_hash_error(self, data: dict) -> bool:
        """Hash 计算错误 → 记录日志"""
        logger.error(f"DLQ: Hash 计算失败, file_id={data.get('file_id')}")
        await self._log_dlq_action(
            data, "LOG_ERROR", data.get("error", "Hash 计算失败"),
            source="file_process",
        )

        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="merge_failed",
                user_id=data.get("user_id"),
                error_message="Hash 计算失败",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")

        # 发布 MQ 事件：通知主业务服务回滚配额
        await self._publish_file_merge_failed_event(data, "Hash 计算失败")
        return True

    # ========== 病毒扫描 ==========

    async def _handle_virus_scanner_error(self, data: dict) -> bool:
        """病毒扫描器异常 → 通知 + 重试通知业务服务"""
        logger.error("DLQ: 病毒扫描器异常, 通知运维检查 ClamAV 服务")
        await self._log_dlq_action(
            data, "SCANNER_DOWN", "ClamAV 服务异常，需要检查",
            source="file_process",
        )

        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="scan_failed",
                user_id=data.get("user_id"),
                error_message="病毒扫描器暂时不可用，等待重试",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")

        await self._publish_file_scan_failed_event(data, "病毒扫描器异常")
        return True

    async def _handle_virus_scanner_unavailable(self, data: dict) -> bool:
        """病毒扫描器不可用 → 告警"""
        logger.critical("DLQ: ClamAV 服务不可用，需要部署/启动!")
        await self._log_dlq_action(
            data, "SCANNER_MISSING", "ClamAV 服务不可用，请部署并启动",
            source="file_process",
        )

        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="scan_failed",
                user_id=data.get("user_id"),
                error_message="病毒扫描器暂时不可用，等待重试",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")

        # 发布 MQ 事件：通知主业务服务回滚配额
        await self._publish_file_scan_failed_event(data, "病毒扫描器不可用")
        return True

    # ========== 缩略图/转码 (降级处理) ==========

    async def _handle_degraded_service(self, data: dict) -> bool:
        """
        缩略图/转码失败 → 标记文件为 DEGRADED (核心功能正常，非核心功能降级)

        文件仍然可以下载使用，但不提供缩略图和转码版本
        """
        file_id = data.get("file_id", "")
        task_type = data.get("task_type", "")

        logger.warning(
            f"DLQ: 非核心功能降级, file_id={file_id}, task_type={task_type}"
        )
        await self._log_dlq_action(
            data, "DEGRADED", f"{task_type} 非核心功能降级",
            source="file_process",
        )
        return True

    # ========== 通知重试 ==========

    async def _handle_retry_notify(self, data: dict) -> bool:
        """
        通知业务服务失败 → 从 DLQ 中重试通知

        这是少数从 DLQ 仍可重试的场景
        """
        file_id = data.get("file_id", "")
        task_type = data.get("task_type", "")
        retry_count = data.get("retry_count", 0)

        if retry_count >= DLQ_MAX_RETRIES:
            logger.error(f"DLQ: 通知重试已耗尽, file_id={file_id}")
            await self._log_dlq_action(
                data, "NOTIFY_EXHAUSTED", "通知业务服务重试已耗尽",
                source="file_process",
            )
            return True

        logger.info(
            f"DLQ: 重试通知业务服务, file_id={file_id}, "
            f"attempt={retry_count + 1}"
        )

        try:
            if task_type == TaskTypes.MARK_ACTIVE:
                await NotificationService.notify_file_activate(
                    file_id=file_id,
                    user_id=data.get("user_id", ""),
                )
            return True
        except Exception as e:
            logger.error(f"DLQ: 通知重试失败, file_id={file_id}, error={e}")
            # 通知失败不阻塞 DLQ 消费，记录后继续
            await self._log_dlq_action(
                data, "RETRY_NOTIFY_FAILED", str(e),
                source="file_process",
            )
            return True

    # ========== 删除 ==========

    async def _handle_delete_error(self, data: dict) -> bool:
        """文件删除错误 → 记录日志"""
        storage_path = data.get("storage_path", "")
        logger.error(f"DLQ: 文件删除失败, storage_path={storage_path}")
        await self._log_dlq_action(
            data, "DELETE_FAILED", "文件删除失败，请手动清理",
            source="file_process",
        )
        return True

    # ========== 未知错误 ==========

    async def _handle_unknown(self, data: dict) -> bool:
        """未知错误 → 全面记录日志"""
        file_id = data.get("file_id", "unknown")
        logger.error(
            f"DLQ: 未知错误, 完整消息:\n"
            f"  file_id={file_id}\n"
            f"  task_type={data.get('task_type')}\n"
            f"  error={data.get('error', 'N/A')}\n"
            f"  full_data={json.dumps(data, indent=2, ensure_ascii=False)[:1000]}"
        )

        await self._log_dlq_action(
            data, "UNKNOWN_ERROR", data.get("error", "未知错误"),
            source="file_process",
        )
        return True

    # ========== 辅助方法 ==========

    @staticmethod
    async def _publish_file_merge_failed_event(data: dict, fail_reason: str):
        """发布文件合并失败 MQ 事件 → 主业务服务回滚配额"""
        try:
            import uuid

            event = {
                "eventId": uuid.uuid4().hex,
                "fileId": data.get("file_id", ""),
                "fileName": data.get("file_name", ""),
                "fileSize": data.get("file_size", 0),
                "fileType": data.get("file_type", ""),
                "userId": data.get("user_id", ""),
                "uploadsSessionId": data.get("uploads_id", ""),
                "failReason": fail_reason,
                "eventTime":  datetime.utcnow().isoformat(),#暂时不带时区
            }
            await rabbitmq_service.publish_file_event(
                settings.file_merge_failed_routing_key, event
            )
            logger.info(
                f"已发布 file.merge.failed 事件: "
                f"fileId={data.get('file_id')}, reason={fail_reason}"
            )
        except Exception as e:
            logger.error(f"发布 file.merge.failed 事件失败: {e}", exc_info=True)

    @staticmethod
    async def _publish_file_scan_failed_event(data: dict, threat_name: str):
        """发布文件扫毒失败 MQ 事件 → 主业务服务回滚配额"""
        try:
            import uuid

            event = {
                "eventId": uuid.uuid4().hex,
                "fileId": data.get("file_id", ""),
                "fileName": data.get("file_name", ""),
                "fileSize": data.get("file_size", 0),
                "fileType": data.get("file_type", ""),
                "userId": data.get("user_id", ""),
                "uploadsSessionId": data.get("uploads_id", ""),
                "threatName": threat_name,
                "eventTime":  datetime.utcnow().isoformat(),#暂时不带时区
            }
            await rabbitmq_service.publish_file_event(
                settings.file_scan_failed_routing_key, event
            )
            logger.info(
                f"已发布 file.scan.failed 事件: "
                f"fileId={data.get('file_id')}, threat={threat_name}"
            )
        except Exception as e:
            logger.error(f"发布 file.scan.failed 事件失败: {e}", exc_info=True)


# =============================================================================
# 入口工厂函数（向后兼容）
# =============================================================================

async def on_dead_letter_message(message: Any):
    """文件处理死信队列消息入口（向后兼容）"""
    consumer = DeadLetterConsumer()
    await consumer.handle(message)