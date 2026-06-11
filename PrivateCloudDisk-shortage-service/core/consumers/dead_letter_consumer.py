"""
死信队列消费者 (Dead Letter Consumer)
处理所有进入 DLQ 的消息，根据失败原因执行不同的补偿策略

策略矩阵:
| 失败原因                    | DLQ 处理策略                                      | 后续操作                     |
|----------------------------|---------------------------------------------------|------------------------------|
| MERGE_IO_ERROR             | 清理残留分片文件，更新任务状态为 FAILED            | 人工介入                     |
| MERGE_DISK_FULL            | 清理残留文件 + 告警日志                            | 运维扩容磁盘                 |
| MERGE_CHUNK_MISSING        | 清理残留文件，标记上传会话为 incomp可让             | 用户重新上传                 |
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

与 Spring Boot MQ 方案的对应:
- Spring Boot DeadLetterConsumer → 本文件的 DeadLetterConsumer
- 多 Channel 分发 (@RabbitListener) → 根据 failure_reason 路由
- sendLog / sendAlert → SecurityReporter + logger
"""
from __future__ import annotations
import json
import logging
import asyncio
from datetime import datetime, timezone
from typing import Any

from core.config import settings, TaskTypes, TaskStatus, FailureReason
from core.rabbitmq import rabbitmq_service
from core.event.file_process_event import FileProcessEvent
from core.services.notification_service import NotificationService
from core.services.retry_service import RetryService

logger = logging.getLogger("dead_letter_consumer")

# DLQ 最大重试次数（仅针对可恢复的错误）
DLQ_MAX_RETRIES = 3


class DeadLetterConsumer:
    """
    死信队列消费者

    处理所有因重试耗尽而进入 DLQ 的消息
    根据 failure_reason 采用不同的处置策略
    """

    def __init__(self):
        self.retry = RetryService(max_attempts=DLQ_MAX_RETRIES)

    async def handle(self, message: Any):
        """
        DLQ 消息处理入口

        从 message.headers 或 body 中提取 failure_reason
        路由到对应的处理策略
        """
        try:
            message_body = message.body.decode("utf-8")
            data = json.loads(message_body)

            failure_reason = data.get("failure_reason", FailureReason.UNKNOWN)
            task_type = data.get("task_type", "unknown")
            file_id = data.get("file_id", "unknown")
            retry_count = data.get("retry_count", 0)

            logger.error(
                f"DLQ 消费者收到死信: "
                f"task_type={task_type}, "
                f"file_id={file_id}, "
                f"failure_reason={failure_reason}, "
                f"retry_count={retry_count}"
            )

            # 根据 failure_reason 分发
            handler = self._get_handler(failure_reason)
            handled = await handler(data)

            if handled:
                await message.ack()
                logger.info(f"DLQ 消息已处理: file_id={file_id}, reason={failure_reason}")
            else:
                # 处理失败，重新回到 DLQ (requeue)
                await message.nack(requeue=True)
                logger.error(f"DLQ 消息处理失败，重新入队: file_id={file_id}")

        except json.JSONDecodeError:
            logger.error("DLQ 消息 JSON 解析失败，丢弃")
            await message.ack()
        except Exception as e:
            logger.error(f"DLQ 处理异常: {e}", exc_info=True)
            await message.nack(requeue=True)

    def _get_handler(self, failure_reason: str):
        """根据失败原因返回对应的处理函数"""
        handlers = {
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

        # 清理可能的合并文件
        final_path = os.path.join(session_dir, "storage", f"{uploads_id}-{total_chunks}.cloud")
        if os.path.exists(final_path):
            os.remove(final_path)

        await self._log_dlq_action(data, "CLEANUP_RESIDUALS", "已清理合并残留文件")
        return True

    async def _handle_merge_disk_full(self, data: dict) -> bool:
        """磁盘空间不足 → 告警"""
        logger.critical(f"DLQ: 磁盘空间不足，需要运维介入!")
        await self._log_dlq_action(
            data, "DISK_FULL_ALERT",
            f"磁盘空间不足，需要至少 {settings.min_free_disk_bytes / (1024*1024):.0f}MB",
        )
        # 也清理残留文件
        await self._handle_merge_error(data)
        return True

    async def _handle_merge_chunk_missing(self, data: dict) -> bool:
        """分片缺失 → 清理残留，通知业务服务"""
        logger.warning(f"DLQ: 分片缺失, 标记上传会话为 incomplete")

        # 通知业务服务上传失败
        try:
            uploads_id = data.get("uploads_id", "")
            import httpx
            async with httpx.AsyncClient(timeout=30) as client:
                await client.patch(
                    f"{settings.business_service_url}/api/v1/business/internal/uploads/{uploads_id}/status",
                    json={"status": "incomplete", "error": "分片文件缺失"},
                )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")

        await self._handle_merge_error(data)
        return True

    # ========== 校验和 ==========

    async def _handle_checksum_mismatch(self, data: dict) -> bool:
        """校验和不匹配 → 删除不完整文件"""
        storage_path = data.get("storage_path", "")

        import os
        if storage_path and os.path.exists(storage_path):
            os.remove(storage_path)
            logger.warning(f"DLQ: 已删除校验失败的文件: {storage_path}")

        await self._log_dlq_action(data, "DELETE_CORRUPTED", "已删除校验失败的文件")
        return True

    # ========== Hash ==========

    async def _handle_hash_error(self, data: dict) -> bool:
        """Hash 计算错误 → 记录日志"""
        logger.error(f"DLQ: Hash 计算失败, file_id={data.get('file_id')}")
        await self._log_dlq_action(data, "LOG_ERROR", data.get("error", "Hash 计算失败"))
        return True

    # ========== 病毒扫描 ==========

    async def _handle_virus_scanner_error(self, data: dict) -> bool:
        """病毒扫描器异常 → 通知 + 重试通知业务服务"""
        logger.error(f"DLQ: 病毒扫描器异常, 通知运维检查 ClamAV 服务")
        await self._log_dlq_action(data, "SCANNER_DOWN", "ClamAV 服务异常，需要检查")

        # 通知业务服务标记文件状态
        try:
            await NotificationService.notify_file_status(
                file_id=data.get("file_id", ""),
                status="processing",
                error_message="病毒扫描器暂时不可用，等待重试",
            )
        except Exception:
            pass

        return True

    async def _handle_virus_scanner_unavailable(self, data: dict) -> bool:
        """病毒扫描器不可用 → 告警"""
        logger.critical(f"DLQ: ClamAV 服务不可用，需要部署/启动!")
        await self._log_dlq_action(data, "SCANNER_MISSING", "ClamAV 服务不可用，请部署并启动")
        return True

    # ========== 缩略图/转码 (降级处理) ==========

    async def _handle_degraded_service(self, data: dict) -> bool:
        """
        缩略图/转码失败 → 标记文件为 DEGRADED (核心功能正常，非核心功能降级)

        文件仍然可以下载使用，但不提供缩略图和转码版本
        """
        file_id = data.get("file_id", "")
        task_type = data.get("task_type", "")

        logger.warning(f"DLQ: 非核心功能降级, file_id={file_id}, task_type={task_type}")

        try:
            await NotificationService.notify_file_status(
                file_id=file_id,
                status="degraded",
                error_message=f"{task_type} 处理失败，文件可正常使用但缺少非核心功能",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")

        await self._log_dlq_action(data, "DEGRADED", f"{task_type} 非核心功能降级")
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
            await self._log_dlq_action(data, "NOTIFY_EXHAUSTED", "通知业务服务重试已耗尽")
            return True

        logger.info(f"DLQ: 重试通知业务服务, file_id={file_id}, attempt={retry_count + 1}")

        try:
            if task_type == TaskTypes.MARK_ACTIVE:
                await NotificationService.notify_file_activate(
                    file_id=file_id,
                    user_id=data.get("user_id", ""),
                )
            else:
                await NotificationService.notify_file_status(
                    file_id=file_id,
                    status="active",
                )
            return True
        except Exception as e:
            logger.error(f"DLQ: 通知重试失败, file_id={file_id}, error={e}")
            # 通知失败不阻塞 DLQ 消费，记录后继续
            await self._log_dlq_action(data, "RETRY_NOTIFY_FAILED", str(e))
            return True

    # ========== 删除 ==========

    async def _handle_delete_error(self, data: dict) -> bool:
        """文件删除错误 → 记录日志"""
        storage_path = data.get("storage_path", "")
        logger.error(f"DLQ: 文件删除失败, storage_path={storage_path}")
        await self._log_dlq_action(data, "DELETE_FAILED", "文件删除失败，请手动清理")
        return True

    # ========== 未知错误 ==========

    async def _handle_unknown(self, data: dict) -> bool:
        """未知错误 → 全面记录日志"""
        logger.error(
            f"DLQ: 未知错误, 完整消息:\n"
            f"  file_id={data.get('file_id')}\n"
            f"  task_type={data.get('task_type')}\n"
            f"  error={data.get('error', 'N/A')}\n"
            f"  full_data={json.dumps(data, indent=2, ensure_ascii=False)[:1000]}"
        )
        await self._log_dlq_action(data, "UNKNOWN_ERROR", data.get("error", "未知错误"))
        return True

    # ========== 辅助方法 ==========

    @staticmethod
    async def _log_dlq_action(data: dict, action: str, detail: str):
        """DLQ 处理动作日志（写入 Redis 持久化记录）"""
        try:
            from server import redis_client
            file_id = data.get("file_id", "unknown")
            dlq_key = f"dlq:action:{file_id}:{datetime.now(timezone.utc).isoformat()}"

            record = {
                "action": action,
                "detail": detail,
                "file_id": file_id,
                "task_type": data.get("task_type", ""),
                "failure_reason": data.get("failure_reason", ""),
                "processed_at": datetime.now(timezone.utc).isoformat(),
            }

            import json as _json
            await redis_client.setex(
                dlq_key,
                86400 * 30,  # 30 天保留
                _json.dumps(record, ensure_ascii=False),
            )
        except Exception:
            pass


# 入口工厂函数
async def on_dead_letter_message(message: Any):
    consumer = DeadLetterConsumer()
    await consumer.handle(message)