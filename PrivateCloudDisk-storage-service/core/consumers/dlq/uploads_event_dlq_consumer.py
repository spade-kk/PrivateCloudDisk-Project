"""
上传事件死信队列消费者 (Uploads Event DLQ Consumer)

职责：处理来自 pcd.uploads.event.dlq 的所有死信消息

消息来源:
  - pcd.uploads.session.delete.queue → 上传会话删除失败
  - pcd.uploads.session.deleted.queue → 上传会话已删除通知失败

策略矩阵:
| failure_reason                     | DLQ 处理策略                              | 后续操作               |
|------------------------------------|------------------------------------------|----------------------|
| UPLOADS_DELETE_IO_ERROR            | 重试删除分块文件（最多 3 次）              | 成功→通知业务服务      |
| UPLOADS_SESSION_NOTIFY_BS_ERROR    | 指数退避重试通知业务服务（最多 3 次）       | 成功→ACK              |
| UNKNOWN (分块文件不存在)            | 检测到目录不存在→WARN 日志→ACK             | 无需人工介入           |
| UNKNOWN (无法判断)                  | 记录完整日志 + 告警                        | 人工排查               |

与 Spring Boot MQ 方案的对应:
  - Spring Boot DeadLetterConsumer (uploads 分支) → 本文件
  - 根据 failure_reason 路由到不同处理策略
"""
from __future__ import annotations
import json
import logging
import os
import asyncio
from typing import Any, Callable

import aiohttp

from core.config import settings, FailureReason
from core.rabbitmq import rabbitmq_service
from core.consumers.dlq.base import BaseDLQConsumer

logger = logging.getLogger("uploads_event_dlq_consumer")

# DLQ 最大重试次数（仅针对可恢复的错误）
DLQ_MAX_RETRIES = 3


class UploadsEventDLQConsumer(BaseDLQConsumer):
    """
    上传事件死信队列消费者

    处理所有因重试耗尽而进入 pcd.uploads.event.dlq 的消息
    根据 failure_reason 采用不同的处置策略
    """

    def _get_dlq_source_name(self) -> str:
        return "uploads_event"

    def _get_handler(self, failure_reason: str) -> Callable:
        """根据 failure_reason 返回对应的处理函数"""
        handlers: dict[str, Callable] = {
            FailureReason.UPLOADS_DELETE_IO_ERROR: self._handle_delete_io_error,
            FailureReason.UPLOADS_SESSION_NOTIFY_BS_ERROR: self._handle_notify_bs_error,
            FailureReason.UNKNOWN: self._handle_unknown,
        }
        return handlers.get(failure_reason, self._handle_unknown)

    # =====================================================================
    # 分块文件删除 IO 异常
    # =====================================================================

    async def _handle_delete_io_error(self, data: dict) -> bool:
        """
        上传会话临时分块文件删除 IO 异常

        策略:
          1. 重试删除分块文件（最多 3 次）
          2. 如果分块目录不存在 → 跳过删除（预期行为，文件可能已被其他进程清理）
          3. 部分删除成功 → 记录警告日志，仍尝试通知业务服务
          4. 全部删除成功 → 通知业务服务标记已删除
        """
        uploads_session_id = data.get("uploadsSessionId", "")
        user_id = data.get("userId", "")
        event_id = data.get("eventId", "unknown")
        retry_count = data.get("dlq_retry_count", 0)

        logger.warning(
            f"[DLQ-UPLOADS] 重试删除分块文件: "
            f"uploadsSessionId={uploads_session_id}, "
            f"retry={retry_count + 1}/{DLQ_MAX_RETRIES}"
        )

        # 超过最大重试次数 → 放弃，记录日志
        if retry_count >= DLQ_MAX_RETRIES:
            logger.error(
                f"[DLQ-UPLOADS] 删除重试已耗尽: "
                f"uploadsSessionId={uploads_session_id}"
            )
            await self._log_dlq_action(
                data,
                "DELETE_RETRY_EXHAUSTED",
                "分块文件删除重试已耗尽，请手动清理",
                source="uploads_event",
            )
            return True

        # 重试删除
        deleted_count, expected_count, error_msg = await self._retry_delete_chunks(
            uploads_session_id
        )

        if error_msg:
            # 删除失败 → 递增重试计数，重新入队
            logger.error(
                f"[DLQ-UPLOADS] 删除重试失败: "
                f"uploadsSessionId={uploads_session_id}, error={error_msg}"
            )
            # 更新重试计数后重新入队
            data["dlq_retry_count"] = retry_count + 1
            data["failure_reason"] = FailureReason.UPLOADS_DELETE_IO_ERROR
            data["error"] = error_msg
            await self._republish_to_dlq(data)
            return True

        # 删除成功（或文件不存在）→ 通知业务服务
        if expected_count == 0:
            logger.warning(
                f"[DLQ-UPLOADS] 分块目录不存在（可能已被清理）: "
                f"uploadsSessionId={uploads_session_id}"
            )
        elif deleted_count < expected_count:
            logger.warning(
                f"[DLQ-UPLOADS] 部分分块删除成功: "
                f"uploadsSessionId={uploads_session_id}, "
                f"deleted={deleted_count}/{expected_count}"
            )
        else:
            logger.info(
                f"[DLQ-UPLOADS] 分块文件删除成功: "
                f"uploadsSessionId={uploads_session_id}, "
                f"deleted={deleted_count}"
            )

        # 通知业务服务标记删除完成
        notify_success = await self._notify_business_service(uploads_session_id)
        if notify_success:
            logger.info(
                f"[DLQ-UPLOADS] 业务服务通知成功: "
                f"uploadsSessionId={uploads_session_id}"
            )
            await self._log_dlq_action(
                data,
                "DELETE_RECOVERED",
                f"分块删除恢复成功: deleted={deleted_count}/{expected_count}",
                source="uploads_event",
            )
            return True
        else:
            # 通知失败 → 进入通知重试流程
            logger.error(
                f"[DLQ-UPLOADS] 业务服务通知失败，进入通知重试: "
                f"uploadsSessionId={uploads_session_id}"
            )
            data["failure_reason"] = FailureReason.UPLOADS_SESSION_NOTIFY_BS_ERROR
            data["dlq_retry_count"] = 0
            await self._republish_to_dlq(data)
            return True

    # =====================================================================
    # 通知业务服务失败
    # =====================================================================

    async def _handle_notify_bs_error(self, data: dict) -> bool:
        """
        通知业务服务失败 → 指数退避重试

        这是少数从 DLQ 仍可重试的场景
        """
        uploads_session_id = data.get("uploadsSessionId", "")
        retry_count = data.get("dlq_retry_count", 0)

        if retry_count >= DLQ_MAX_RETRIES:
            logger.error(
                f"[DLQ-UPLOADS] 通知业务服务重试已耗尽: "
                f"uploadsSessionId={uploads_session_id}"
            )
            await self._log_dlq_action(
                data,
                "NOTIFY_EXHAUSTED",
                "通知业务服务重试已耗尽，请检查业务服务是否在线",
                source="uploads_event",
            )
            return True

        # 指数退避
        delay = min(5 * (2 ** retry_count), 300)
        logger.info(
            f"[DLQ-UPLOADS] 重试通知业务服务: "
            f"uploadsSessionId={uploads_session_id}, "
            f"attempt={retry_count + 1}/{DLQ_MAX_RETRIES}, "
            f"delay_s={delay}"
        )
        await asyncio.sleep(delay)

        success = await self._notify_business_service(uploads_session_id)
        if success:
            logger.info(
                f"[DLQ-UPLOADS] 通知业务服务重试成功: "
                f"uploadsSessionId={uploads_session_id}"
            )
            await self._log_dlq_action(
                data,
                "NOTIFY_RECOVERED",
                "通知业务服务恢复成功",
                source="uploads_event",
            )
            return True
        else:
            # 重试失败 → 递增计数，重新入队
            data["dlq_retry_count"] = retry_count + 1
            data["failure_reason"] = FailureReason.UPLOADS_SESSION_NOTIFY_BS_ERROR
            await self._republish_to_dlq(data)
            return True

    # =====================================================================
    # 未知错误
    # =====================================================================

    async def _handle_unknown(self, data: dict) -> bool:
        """
        未知错误 → 全面记录日志 + 告警

        对于上传事件，会尝试从原始消息中提取有用信息
        """
        uploads_session_id = data.get("uploadsSessionId", "unknown")
        user_id = data.get("userId", "unknown")
        file_name = data.get("fileName", "unknown")
        event_id = data.get("eventId", "unknown")

        logger.error(
            f"[DLQ-UPLOADS] 未知错误，完整消息:\n"
            f"  uploadsSessionId={uploads_session_id}\n"
            f"  userId={user_id}\n"
            f"  fileName={file_name}\n"
            f"  eventId={event_id}\n"
            f"  error={data.get('error', 'N/A')}\n"
            f"  full_data={json.dumps(data, indent=2, ensure_ascii=False)[:2000]}"
        )

        await self._log_dlq_action(
            data,
            "UNKNOWN_ERROR",
            data.get("error", "未知错误"),
            source="uploads_event",
        )
        return True

    # =====================================================================
    # 私有辅助方法
    # =====================================================================

    async def _retry_delete_chunks(
        self, uploads_session_id: str
    ) -> tuple[int, int, str | None]:
        """
        重试删除分块文件

        Returns:
            (deleted_count, expected_count, error_msg)
            - deleted_count: 实际删除的文件数
            - expected_count: 预期删除的文件数（目录中的文件总数）
            - error_msg: 如有异常则返回错误消息，否则 None
        """
        deleted_count = 0
        expected_count = 0
        upload_dir = os.path.join(settings.file_upload_dir, uploads_session_id)

        if not os.path.exists(upload_dir):
            logger.debug(f"[DLQ-UPLOADS] 分块目录不存在: {upload_dir}")
            return 0, 0, None

        error_msg = None
        try:
            for root, dirs, files in os.walk(upload_dir, topdown=False):
                for file_name in files:
                    file_path = os.path.join(root, file_name)
                    expected_count += 1
                    try:
                        os.remove(file_path)
                        deleted_count += 1
                    except OSError as e:
                        error_msg = f"删除失败: {file_path}, error={e}"
                        logger.error(error_msg)

                for dir_name in dirs:
                    try:
                        os.rmdir(os.path.join(root, dir_name))
                    except OSError:
                        pass

            try:
                os.rmdir(upload_dir)
            except OSError:
                pass

        except Exception as e:
            error_msg = f"删除分块文件异常: {e}"
            logger.error(error_msg, exc_info=True)

        return deleted_count, expected_count, error_msg

    async def _notify_business_service(self, uploads_session_id: str) -> bool:
        """
        调用业务服务内部接口标记上传会话为 deleted

        POST api/v1/business/internal/storage/uploads/{uploads_id}/delete-complete
        """
        url = (
            f"{settings.business_service_url}"
            f"/api/v1/business/internal/storage/uploads/{uploads_session_id}/delete-complete"
        )
        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(
                    url, timeout=aiohttp.ClientTimeout(total=30)
                ) as resp:
                    if resp.status == 200:
                        return True
                    else:
                        response_text = await resp.text()
                        logger.error(
                            f"[DLQ-UPLOADS] 业务服务通知失败: "
                            f"uploadsSessionId={uploads_session_id}, "
                            f"status={resp.status}, body={response_text[:200]}"
                        )
                        return False
        except aiohttp.ClientError as e:
            logger.error(
                f"[DLQ-UPLOADS] 业务服务通知网络异常: "
                f"uploadsSessionId={uploads_session_id}, error={e}"
            )
            return False
        except Exception as e:
            logger.error(
                f"[DLQ-UPLOADS] 业务服务通知异常: {e}",
                exc_info=True,
            )
            return False

    async def _republish_to_dlq(self, data: dict):
        """
        将更新后的消息重新发布到 DLQ

        用于 DLQ 内部重试（如递增 retry_count 后重新入队）
        """
        try:
            await rabbitmq_service.publish_message(
                settings.uploads_event_dlx,
                settings.uploads_event_dlq_routing_key,
                data,
            )
            logger.info(
                f"[DLQ-UPLOADS] 消息已重新发布到 DLQ: "
                f"uploadsSessionId={data.get('uploadsSessionId', 'unknown')}"
            )
        except Exception as e:
            logger.error(
                f"[DLQ-UPLOADS] 重新发布到 DLQ 失败: {e}",
                exc_info=True,
            )


# =============================================================================
# 入口工厂函数
# =============================================================================

async def on_uploads_event_dlq_message(message: Any):
    """上传事件死信队列消息入口"""
    consumer = UploadsEventDLQConsumer()
    await consumer.handle(message)