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
import asyncio
from pathlib import Path
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
                f"space_id={event.space_id or 'personal-legacy'}, "
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
            # 【需求七】原行为对未知异常直接 ACK，会造成文件或关联数据永久残留；
            # 新行为拒绝消息并交由队列 DLQ 策略处置，保留可审计的失败证据。
            await message.nack(requeue=False)

    async def _delete_files(self, event: FileDeleteEvent) -> dict:
        """
        删除文件 (幂等设计: 文件不存在也算成功)

        Returns:
            dict: {"success": bool, "deleted": list, "errors": list, "failure_reason": str}
        """
        deleted = []
        errors = []
        storage_root = Path(settings.file_upload_dir).resolve()

        def safe_path(raw_path: str) -> Path | None:
            """只允许永久删除配置上传根目录内的实体，防止异常消息构造越界路径。"""
            if not raw_path:
                return None
            candidate = Path(raw_path).resolve()
            try:
                candidate.relative_to(storage_root)
            except ValueError as exc:
                raise OSError(f"拒绝删除上传目录之外的路径: {candidate}") from exc
            return candidate

        # 删除主文件
        try:
            main_path = safe_path(event.storage_path)
            if main_path and main_path.exists():
                main_path.unlink()
                deleted.append(str(main_path))
                logger.debug(f"已删除主文件: {main_path}")
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
            raw_path = thumb_path if isinstance(thumb_path, str) else thumb_path.get("path", "")
            try:
                path = safe_path(raw_path)
                if path and path.exists():
                    path.unlink()
                    deleted.append(str(path))
                    logger.debug(f"已删除缩略图: {path}")
            except OSError as e:
                errors.append(f"缩略图删除失败: {raw_path}, {e}")
                logger.warning(f"缩略图删除失败: {raw_path}, {e}")

        # 删除转码文件
        for trans_path in event.transcoded_paths:
            raw_path = trans_path if isinstance(trans_path, str) else trans_path.get("path", "")
            try:
                path = safe_path(raw_path)
                if path and path.exists():
                    path.unlink()
                    deleted.append(str(path))
                    logger.debug(f"已删除转码文件: {path}")
            except OSError as e:
                errors.append(f"转码文件删除失败: {raw_path}, {e}")
                logger.warning(f"转码文件删除失败: {raw_path}, {e}")

        # AUDIT FIX [7.4]: 永久删除文件时按数据库资源清单清除 HLS、文档、压缩包等全部派生产物。
        try:
            from app.services.preview_resource_service import preview_resource_service
            preview_deleted = await preview_resource_service.delete_file_resources(
                event.file_id, event.user_id, event.space_id or None,
            )
            deleted.extend(preview_deleted)
        except Exception as e:
            errors.append(f"预览资源清理失败: {e}")
            logger.error("预览资源清理失败: %s", e)

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
        """删除成功 → 通知业务事务完成 → ACK"""
        try:
            await NotificationService.notify_file_delete_complete(
                file_id=event.file_id,
                deleted_files=result["deleted"],
                user_id=event.user_id
            )
        except Exception as e:
            logger.error(f"通知业务服务删除完成失败: {e}")
            # 需求七-2：原行为先 ACK 后通知，通知失败会永久留下业务元数据；
            # 新行为保留消息并进入已有指数退避流程，物理删除本身为幂等操作。
            await self._on_failure(message, event, {
                "success": False,
                "deleted": result["deleted"],
                "errors": [f"业务服务删除事务通知失败: {e}"],
                "failure_reason": FailureReason.NOTIFY_BS_ERROR,
            })
            return

        await message.ack()
        logger.info(
            f"文件删除完成: space_id={event.space_id or 'personal-legacy'}, "
            f"file_id={event.file_id}, deleted={len(result['deleted'])}个文件"
        )

    async def _on_failure(self, message: Any, event: FileDeleteEvent, result: dict):
        """删除失败 → 判断重试或进 DLQ"""
        failure_reason = result.get("failure_reason", FailureReason.DELETE_IO_ERROR)

        if not self.retry.should_retry(event.retry_count, failure_reason):
            # 重试耗尽 → 发布 enriched 载荷到专属 DLQ；发布确认失败时保留原消息。
            logger.error(
                f"删除任务重试耗尽 → 进入 DLQ: "
                f"file_id={event.file_id}, "
                f"retry_count={event.retry_count}, "
                f"errors={result['errors']}"
            )
            try:
                failed = event.to_dict()
                failed["failure_reason"] = failure_reason
                await rabbitmq_service.publish_to_dlq(
                    settings.file_delete_dlx,
                    settings.file_delete_dlq_routing_key,
                    failed,
                )
                await message.ack()
            except Exception:
                logger.exception("文件删除 DLQ 发布失败，原消息重新入队 file_id=%s", event.file_id)
                await message.nack(requeue=True)
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

        retry_event = event.with_retry_increment()
        retry_dict = retry_event.to_dict()
        retry_dict["failure_reason"] = failure_reason
        try:
            # W-02：写入文件删除专属 TTL retry 队列，发布确认后才 ACK 原消息。
            await rabbitmq_service.publish_retry_message(
                exchange_name=settings.file_delete_exchange,
                routing_key=settings.file_delete_routing_key,
                message=retry_dict,
                delay_seconds=delay,
            )
            await message.ack()
        except Exception:
            logger.exception("文件删除重试消息发布失败，原消息重新入队: file_id=%s", event.file_id)
            await message.nack(requeue=True)


# 入口工厂函数
async def on_file_delete_message(message: Any):
    consumer = FileDeleteConsumer()
    await consumer.handle(message)
