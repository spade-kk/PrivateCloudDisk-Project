"""
上传会话删除消息消费者
职责: 接收上传会话删除事件 → 删除物理分块文件 → 通知业务服务标记 deleted

与 Spring Boot MQ 方案的对应:
- Spring Boot UploadsServiceImpl.markUploadSessionDeleted → 本文件
- 流程:
  1. 解析消息获取 uploads_id
  2. 删除所有与该 uploads_id 关联的分块文件
  3. 调用业务服务内部接口 POST /business/internal/storage/uploads/{id}/delete-complete
  4. 业务服务更新状态为 deleted → 发布 uploads.session.deleted 事件 → 释放配额

失败处理策略:
  - 分块目录不存在（文件已被清理） → WARN 日志 + ACK（正常跳过）
  - 分块删除 IO 异常 → 携带 failure_reason 发布到 DLQ，ACK 原消息
  - 通知业务服务失败 → 携带 failure_reason 发布到 DLQ，ACK 原消息
  - 未预期异常 → 携带 failure_reason 发布到 DLQ，ACK 原消息

企业级设计:
  不再使用 `nack(requeue=False)` 通过 DLX 路由到 DLQ，
  而是使用 `ack + publish enriched message to DLQ` 模式，
  确保 DLQ 消息携带完整的 failure_reason、task_type 等元数据。
"""
from __future__ import annotations
import json
import logging
import os
import aiohttp
from typing import Any

from core.config import settings, FailureReason
from core.rabbitmq import rabbitmq_service

logger = logging.getLogger("uploads_session_delete_consumer")


class UploadsSessionDeleteConsumer:
    """
    上传会话删除消费者

    处理流程:
    1. 解析消息 → uploads_id, user_id
    2. 删除所有分块文件 (幂等删除)
    3. 通知业务服务删除完成
    4. 失败 → 携带完整元数据发布到 DLQ
    """

    async def handle(self, message: Any):
        """消费者入口"""
        message_body = None
        raw_data = None
        try:
            message_body = message.body.decode("utf-8")
            raw_data = json.loads(message_body)

            uploads_session_id = raw_data.get("uploadsSessionId")
            user_id = raw_data.get("userId")
            file_name = raw_data.get("fileName", "unknown")
            event_id = raw_data.get("eventId", "unknown")

            logger.info(
                f"收到上传会话删除事件: eventId={event_id}, "
                f"uploadsSessionId={uploads_session_id}, fileName={file_name}"
            )

            if not uploads_session_id:
                logger.error(f"消息缺少 uploadsSessionId，丢弃: eventId={event_id}")
                await message.ack()
                return

            # 步骤1: 删除所有分块文件
            deleted_count, expected_count, io_error = await self._delete_chunk_files(
                uploads_session_id
            )

            if io_error:
                # 分块删除 IO 异常 → 携带完整元数据发布到 DLQ
                logger.error(
                    f"分块删除 IO 异常: eventId={event_id}, "
                    f"uploadsSessionId={uploads_session_id}, error={io_error}"
                )
                await self._publish_to_dlq(
                    raw_data,
                    failure_reason=FailureReason.UPLOADS_DELETE_IO_ERROR,
                    error=io_error,
                    task_type="uploads_session_delete",
                )
                await message.ack()
                return

            if expected_count == 0:
                logger.warning(
                    f"分块目录不存在（可能已被清理）: "
                    f"uploadsSessionId={uploads_session_id}, eventId={event_id}"
                )
            elif deleted_count < expected_count:
                logger.warning(
                    f"部分分块删除: "
                    f"uploadsSessionId={uploads_session_id}, "
                    f"deleted={deleted_count}/{expected_count}"
                )
            else:
                logger.info(
                    f"分块删除成功: "
                    f"uploadsSessionId={uploads_session_id}, "
                    f"deleted={deleted_count}"
                )

            # 步骤2: 通知业务服务标记为 deleted
            success = await self._notify_business_service(uploads_session_id)

            if success:
                logger.info(
                    f"上传会话删除处理完成: eventId={event_id}, "
                    f"uploadsSessionId={uploads_session_id}, "
                    f"deletedChunks={deleted_count}"
                )
                await message.ack()
            else:
                # 通知业务服务失败 → 携带完整元数据发布到 DLQ
                logger.error(
                    f"通知业务服务失败: eventId={event_id}, "
                    f"uploadsSessionId={uploads_session_id}"
                )
                await self._publish_to_dlq(
                    raw_data,
                    failure_reason=FailureReason.UPLOADS_SESSION_NOTIFY_BS_ERROR,
                    error="通知业务服务标记删除完成失败",
                    task_type="uploads_session_delete",
                )
                await message.ack()

        except json.JSONDecodeError:
            logger.error("上传会话删除消息 JSON 解析失败，丢弃")
            await message.ack()
        except Exception as e:
            logger.error(f"上传会话删除处理异常: {e}", exc_info=True)
            if raw_data:
                await self._publish_to_dlq(
                    raw_data,
                    failure_reason=FailureReason.UPLOADS_DELETE_IO_ERROR,
                    error=str(e),
                    task_type="uploads_session_delete",
                )
            await message.ack()

    async def _delete_chunk_files(
        self, uploads_session_id: str
    ) -> tuple[int, int, str | None]:
        """
        删除所有与该 uploads_id 关联的分块文件

        Returns:
            (deleted_count, expected_count, io_error)
            - deleted_count: 实际删除的文件数
            - expected_count: 预期删除的文件数（目录中文件总数）
            - io_error: 如有 IO 异常则返回错误消息，否则 None
        """
        deleted_count = 0
        expected_count = 0
        upload_dir = os.path.join(settings.file_upload_dir, uploads_session_id)

        if not os.path.exists(upload_dir):
            logger.debug(f"分块目录不存在，跳过: {upload_dir}")
            return 0, 0, None

        io_error = None
        try:
            for root, dirs, files in os.walk(upload_dir, topdown=False):
                for file_name in files:
                    file_path = os.path.join(root, file_name)
                    expected_count += 1
                    try:
                        os.remove(file_path)
                        deleted_count += 1
                        logger.debug(f"已删除分块文件: {file_path}")
                    except OSError as e:
                        io_error = f"删除分块文件失败: {file_path}, error={e}"
                        logger.error(io_error)

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
            io_error = f"删除分块文件异常: {e}"
            logger.error(io_error, exc_info=True)

        return deleted_count, expected_count, io_error

    async def _notify_business_service(self, uploads_session_id: str) -> bool:
        """
        调用业务服务内部接口标记上传会话为 deleted

        POST api/v1/business/internal/storage/uploads/{uploads_id}/delete-complete
        """
        url = (
            f"{settings.business_service_url}"
            f"api/v1/business/internal/storage/uploads/{uploads_session_id}/delete-complete"
        )

        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(
                    url, timeout=aiohttp.ClientTimeout(total=30)
                ) as resp:
                    if resp.status == 200:
                        logger.info(
                            f"业务服务通知成功: "
                            f"uploadsSessionId={uploads_session_id}"
                        )
                        return True
                    else:
                        response_text = await resp.text()
                        logger.error(
                            f"业务服务通知失败: "
                            f"uploadsSessionId={uploads_session_id}, "
                            f"status={resp.status}, body={response_text[:200]}"
                        )
                        return False
        except aiohttp.ClientError as e:
            logger.error(
                f"业务服务通知网络异常: "
                f"uploadsSessionId={uploads_session_id}, error={e}"
            )
            return False
        except Exception as e:
            logger.error(f"业务服务通知异常: {e}", exc_info=True)
            return False

    async def _publish_to_dlq(
        self,
        original_data: dict,
        failure_reason: str,
        error: str,
        task_type: str,
    ):
        """
        将携带完整失败元数据的消息发布到上传事件 DLQ

        企业级设计:
          不使用 `nack(requeue=False)` 通过 DLX 路由，
          而是主动构造携带 failure_reason 的 enriched 消息发布到 DLQ。
          原消息 ACK 掉，确保 DLQ 消费者能正确解析 failure_reason。

        消息体包含:
          - 原始上传事件的所有字段
          - failure_reason: 失败原因枚举
          - task_type: 任务类型
          - error: 详细错误信息
          - dlq_retry_count: DLQ 内部重试计数
        """
        try:
            enriched = dict(original_data)
            enriched["failure_reason"] = failure_reason
            enriched["task_type"] = task_type
            enriched["error"] = error
            enriched["dlq_retry_count"] = 0

            await rabbitmq_service.publish_message(
                settings.uploads_event_dlx,
                settings.uploads_event_dlq_routing_key,
                enriched,
            )
            logger.info(
                f"已发布 enriched 消息到上传事件 DLQ: "
                f"uploadsSessionId={original_data.get('uploadsSessionId', 'unknown')}, "
                f"failure_reason={failure_reason}"
            )
        except Exception as e:
            logger.error(
                f"发布到 DLQ 失败（消息可能丢失）: "
                f"uploadsSessionId={original_data.get('uploadsSessionId', 'unknown')}, "
                f"error={e}",
                exc_info=True,
            )