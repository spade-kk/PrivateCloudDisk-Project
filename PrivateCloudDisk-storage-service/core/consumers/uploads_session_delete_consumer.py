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
"""
from __future__ import annotations
import json
import logging
import os
import aiohttp
from typing import Any

from core.config import settings
from core.rabbitmq import rabbitmq_service

logger = logging.getLogger("uploads_session_delete_consumer")


class UploadsSessionDeleteConsumer:
    """
    上传会话删除消费者

    处理流程:
    1. 解析消息 → uploads_id, user_id
    2. 删除所有分块文件 (幂等删除)
    3. 通知业务服务删除完成
    4. 失败 → NACK → DLQ
    """

    async def handle(self, message: Any):
        """消费者入口"""
        message_body = None
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
            deleted_count = await self._delete_chunk_files(uploads_session_id)

            # 步骤2: 通知业务服务标记为 deleted
            success = await self._notify_business_service(uploads_session_id)

            if success:
                logger.info(
                    f"上传会话删除处理完成: eventId={event_id}, "
                    f"uploadsSessionId={uploads_session_id}, deletedChunks={deleted_count}"
                )
                await message.ack()
            else:
                logger.error(
                    f"通知业务服务失败: eventId={event_id}, "
                    f"uploadsSessionId={uploads_session_id}"
                )
                await message.nack(requeue=False)

        except json.JSONDecodeError:
            logger.error("上传会话删除消息 JSON 解析失败，丢弃")
            await message.ack()
        except Exception as e:
            logger.error(f"上传会话删除处理异常: {e}", exc_info=True)
            await message.ack()

    async def _delete_chunk_files(self, uploads_session_id: str) -> int:
        """
        删除所有与该 uploads_id 关联的分块文件

        Returns:
            int: 删除的文件数量
        """
        deleted_count = 0
        upload_dir = os.path.join(settings.file_upload_dir, uploads_session_id)

        if not os.path.exists(upload_dir):
            logger.debug(f"分块目录不存在，跳过: {upload_dir}")
            return 0

        try:
            for root, dirs, files in os.walk(upload_dir, topdown=False):
                for file_name in files:
                    file_path = os.path.join(root, file_name)
                    try:
                        os.remove(file_path)
                        deleted_count += 1
                        logger.debug(f"已删除分块文件: {file_path}")
                    except OSError as e:
                        logger.error(f"删除分块文件失败: {file_path}, error={e}")

                # 删除空目录
                for dir_name in dirs:
                    dir_path = os.path.join(root, dir_name)
                    try:
                        os.rmdir(dir_path)
                    except OSError:
                        pass

            # 删除 uploads_session_id 目录本身
            try:
                os.rmdir(upload_dir)
            except OSError:
                pass

            logger.info(f"分块文件删除完成: uploadsSessionId={uploads_session_id}, deleted={deleted_count}")
        except Exception as e:
            logger.error(f"删除分块文件异常: {e}", exc_info=True)

        return deleted_count

    async def _notify_business_service(self, uploads_session_id: str) -> bool:
        """
        调用业务服务内部接口标记上传会话为 deleted

        POST /business/internal/storage/uploads/{uploads_id}/delete-complete
        """
        url = f"{settings.business_service_url}/business/internal/storage/uploads/{uploads_session_id}/delete-complete"

        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(url, timeout=aiohttp.ClientTimeout(total=30)) as resp:
                    if resp.status == 200:
                        logger.info(f"业务服务通知成功: uploadsSessionId={uploads_session_id}")
                        return True
                    else:
                        response_text = await resp.text()
                        logger.error(
                            f"业务服务通知失败: uploadsSessionId={uploads_session_id}, "
                            f"status={resp.status}, body={response_text[:200]}"
                        )
                        return False
        except aiohttp.ClientError as e:
            logger.error(f"业务服务通知网络异常: uploadsSessionId={uploads_session_id}, error={e}")
            return False
        except Exception as e:
            logger.error(f"业务服务通知异常: {e}", exc_info=True)
            return False