"""
文件删除消息消费者
职责: 接收删除消息 → 删除文件/缩略图/转码文件 → 通知业务服务

与 Spring Boot MQ 方案的对应:
- Spring Boot FileDeleteConsumer → 本文件
- 幂等性: 文件不存在也算删除成功
- DLQ: 重试耗尽后进入 pcd.file.delete.dlq
"""
from __future__ import annotations
import json
import logging
import os
import asyncio
from datetime import datetime, timezone
from typing import Any

from core.config import settings, FailureReason
from core.rabbitmq import rabbitmq_service
from core.event.file_process_event import FileDeleteEvent
from core.services.retry_service import RetryService
from core.services.notification_service import NotificationService

logger = logging.getLogger("file_delete_consumer")


class FileDeleteConsumer:
    """
    文件删除消费者

    处理流程:
    1. 解析消息 → FileDeleteEvent
    2. 删除主文件、缩略图、转码文件 (幂等删除)
    3. 通知业务服务删除完成
    4. 失败 → 重试 (指数退避, 最多 3 次)
    5. 重试耗尽 → NACK → DLQ
    """

    def __init__(self):
        self.retry = RetryService()

    async def handle(self, message: Any):
        """消费者入口"""
        try:
            message_body = message.body.decode("utf-8")
            raw_data = json.loads(message_body)
            event = FileDeleteEvent.from_dict(raw_data)

            logger.info(
                f"收到删除消息: file_id={event.file_id}, "
                f"retry_count={event.retry_count}"
            )

            result = await self._delete_files(event)

            if result["success"]:
                await self._on_success(message, event, result)
            else:
                await self._on_failure(message, event, result)

        except json.JSONDecodeError:
            logger.error("删除消息 JSON 解析失败，丢弃")
            await message.ack()
        except Exception as e:
            logger.error(f"删除消息处理异常: {e}", exc_info=True)
            await message.ack()

    async def _delete_files(self, event: FileDeleteEvent) -> dict:
        """
        删除文件 (幂等设计: 文件不存在也算成功)

        Returns:
            dict: {"success": bool, "deleted": list, "errors": list, "failure_reason": str}
        """
        deleted = []
        errors = []

        # 删除主文件
        try:
            if event.storage_path and os.path.exists(event.storage_path):
                os.remove(event.storage_path)
                deleted.append(event.storage_path)
                logger.debug(f"已删除主文件: {event.storage_path}")
            else:
                logger.debug(f"主文件不存在或已删除: {event.storage_path}")
        except OSError as e:
            errors.append(f"主文件删除失败: {e}")
            logger.error(f"主文件删除失败: {e}")
            return {
                "success": False,
                "deleted": deleted,
                "errors": errors,
                "failure_reason": FailureReason.DELETE_IO_ERROR,
            }

        # 删除缩略图
        for thumb_path in event.thumbnail_paths:
            path = thumb_path if isinstance(thumb_path, str) else thumb_path.get("path", "")
            try:
                if path and os.path.exists(path):
                    os.remove(path)
                    deleted.append(path)
                    logger.debug(f"已删除缩略图: {path}")
            except OSError as e:
                errors.append(f"缩略图删除失败: {path}, {e}")
                logger.warning(f"缩略图删除失败: {path}, {e}")

        # 删除转码文件
        for trans_path in event.transcoded_paths:
            path = trans_path if isinstance(trans_path, str) else trans_path.get("path", "")
            try:
                if path and os.path.exists(path):
                    os.remove(path)
                    deleted.append(path)
                    logger.debug(f"已删除转码文件: {path}")
            except OSError as e:
                errors.append(f"转码文件删除失败: {path}, {e}")
                logger.warning(f"转码文件删除失败: {path}, {e}")

        # 判断结果
        if errors:
            # 有错误但仍有部分成功 → 部分成功
            return {
                "success": False,
                "deleted": deleted,
                "errors": errors,
                "failure_reason": FailureReason.DELETE_IO_ERROR,
            }
        else:
            return {
                "success": True,
                "deleted": deleted,
                "errors": [],
                "failure_reason": "",
            }

    async def _on_success(self, message: Any, event: FileDeleteEvent, result: dict):
        """删除成功 → ACK + 通知业务服务"""
        await message.ack()
        logger.info(f"文件删除完成: file_id={event.file_id}, deleted={len(result['deleted'])}个文件")

        try:
            await NotificationService.notify_file_delete_complete(
                file_id=event.file_id,
                deleted_files=result["deleted"],
                user_id=event.user_id
            )
        except Exception as e:
            logger.error(f"通知业务服务删除完成失败: {e}")

    async def _on_failure(self, message: Any, event: FileDeleteEvent, result: dict):
        """删除失败 → 判断重试或进 DLQ"""
        failure_reason = result.get("failure_reason", FailureReason.DELETE_IO_ERROR)

        if not self.retry.should_retry(event.retry_count, failure_reason):
            # 重试耗尽 → NACK → DLQ
            logger.error(
                f"删除任务重试耗尽 → 进入 DLQ: "
                f"file_id={event.file_id}, "
                f"retry_count={event.retry_count}, "
                f"errors={result['errors']}"
            )
            await message.nack(requeue=False)
            return

        # 重试
        next_retry = event.retry_count + 1
        delay = self.retry.get_delay_seconds(next_retry)

        logger.warning(
            f"删除失败，准备重试: file_id={event.file_id}, "
            f"retry={next_retry}/{self.retry.max_attempts}, "
            f"delay={delay}s, "
            f"errors={result['errors']}"
        )

        await message.ack()
        await asyncio.sleep(delay)

        retry_event = event.with_retry_increment()
        retry_dict = retry_event.to_dict()
        retry_dict["failure_reason"] = failure_reason

        await rabbitmq_service.publish_message(
            settings.file_delete_exchange,
            settings.file_delete_routing_key,
            retry_dict,
        )


# 入口工厂函数
async def on_file_delete_message(message: Any):
    consumer = FileDeleteConsumer()
    await consumer.handle(message)