"""
文件处理消息消费者 (主消费者)
职责: 接收消息 → 分发到流水线 → 成功则继续下一个任务 / 失败则根据策略重试或进入 DLQ

与 Spring Boot MQ 方案的对应:
- Spring Boot @RabbitListener → aio_pika queue.consume()
- 手动 ACK → message.ack() / message.nack()
- DLQ → x-dead-letter-exchange 配置
- 指数退避重试 → RetryService + delay_seconds
"""
from __future__ import annotations
import json
import logging
import asyncio
from datetime import datetime, timezone
from typing import Any

from core.config import settings, TaskTypes, TaskStatus, TASK_PIPELINE, FailureReason
from core.rabbitmq import rabbitmq_service
from core.event.file_process_event import FileProcessEvent
from core.services.file_processor import FileProcessor
from core.services.retry_service import RetryService

logger = logging.getLogger("file_process_consumer")


class FileProcessConsumer:
    """
    文件处理主消费者

    处理流程:
    1. 解析消息 → FileProcessEvent
    2. 根据 task_type 分发到 FileProcessor
    3. 成功 → ACK + 发送下一个任务
    4. 失败 → 判断是否重试
       - 可重试: 指数退避后重新发布 (retry_count + 1)
       - 不可重试: 直接 NACK (进入 DLQ)
       - 超过最大重试: NACK (进入 DLQ)
    """

    def __init__(self):
        self.retry = RetryService()

    async def handle(self, message: Any):
        """消费者入口 (作为 aio_pika callback)"""
        try:
            message_body = message.body.decode("utf-8")
            raw_data = json.loads(message_body)
            event = FileProcessEvent.from_dict(raw_data)

            logger.info(
                f"收到消息: task_type={event.task_type}, "
                f"file_id={event.file_id}, "
                f"retry_count={event.retry_count}"
            )
            # 执行处理
            result = await FileProcessor.process(event)

            if result.success:
                await self._on_success(message, event, result)
            else:
                await self._on_failure(message, event, result)

        except json.JSONDecodeError:
            logger.error("消息 JSON 解析失败，丢弃")
            await message.ack()
        except Exception as e:
            logger.error(f"消息处理异常: {e}", exc_info=True)
            # 无法解析的消息直接 ACK (避免死循环)
            await message.ack()

    async def _on_success(self, message: Any, event: FileProcessEvent, result):
        """处理成功"""
        await message.ack()
        logger.info(f"任务成功: task_type={event.task_type}, file_id={event.file_id}")

        # 更新 Redis 任务状态
        await self._update_task_status(
            event.task_id, event.task_type, TaskStatus.COMPLETED, result.data,
        )

        # 累积缩略图和转码信息
        accumulated = event.to_dict()
        if result.data.get("thumbnails"):
            accumulated.setdefault("thumbnails", []).extend(result.data["thumbnails"])
        if result.data.get("transcoded_files"):
            accumulated.setdefault("transcoded_files", []).extend(result.data["transcoded_files"])
        if result.data.get("file_id"):
            accumulated["file_id"] = result.data["file_id"]
        if result.data.get("storage_path"):
            accumulated["storage_path"] = result.data["storage_path"]
        if result.data.get("checksum"):
            accumulated["checksum"] = result.data["checksum"]

        # 发送下一个任务
        await self._send_next_task(event, accumulated)

    async def _on_failure(self, message: Any, event: FileProcessEvent, result):
        """处理失败 - 决定重试还是进 DLQ"""
        failure_reason = result.failure_reason or FailureReason.UNKNOWN

        # 病毒发现 → 不重试，直接进 DLQ + 安全队列
        if failure_reason == FailureReason.VIRUS_FOUND:
            await self._handle_virus_found(message, event, result)
            return

        # 判断是否应该重试
        if not self.retry.should_retry(event.retry_count, failure_reason):
            # 不再重试 → NACK (不 requeue) → 进入 DLQ
            await self._send_to_dlq(message, event, result)
            return

        # 重试
        await self._retry_with_backoff(message, event, result)

    async def _handle_virus_found(self, message: Any, event: FileProcessEvent, result):
        """发现病毒 → ACK 并发布安全事件"""
        await message.ack()
        logger.critical(
            f"⚠ 病毒已处理，消息已确认: file_id={event.file_id}, "
            f"threat={result.data.get('threat_name', 'unknown')}"
        )

        # 更新任务状态为 FAILED
        await self._update_task_status(
            event.task_id, event.task_type, TaskStatus.FAILED,
            {"error": result.error, "failure_reason": result.failure_reason},
        )

        # 通知业务服务文件状态
        try:
            from core.services.notification_service import NotificationService
            await NotificationService.notify_file_status(
                file_id=event.file_id,
                status="failed",
                error_message=f"文件包含病毒/木马: {result.data.get('threat_name', 'unknown')}",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")

    async def _retry_with_backoff(self, message: Any, event: FileProcessEvent, result):
        """指数退避重试"""
        next_retry = event.retry_count + 1
        delay = self.retry.get_delay_seconds(next_retry)

        logger.warning(
            f"任务失败，准备重试: task_type={event.task_type}, "
            f"retry={next_retry}/{self.retry.max_attempts}, "
            f"delay={delay}s, "
            f"reason={result.failure_reason}"
            f"error={result.error}"
        )

        # ACK 当前消息
        await message.ack()

        # 重新发布带延迟的消息
        retry_event = event.with_retry_increment()
        retry_dict = retry_event.to_dict()
        retry_dict["failure_reason"] = result.failure_reason

        await asyncio.sleep(delay)
        await rabbitmq_service.publish_message(
            settings.file_process_exchange,
            settings.file_process_routing_key,
            retry_dict,
        )

    async def _send_to_dlq(self, message: Any, event: FileProcessEvent, result):
        """
        重试耗尽 → 发送到 DLQ

        策略:
        - NACK (requeue=False) → 自动进入 DLQ (通过 DLX 配置)
        - 同时在消息 header 中记录失败原因
        """
        logger.error(
            f"任务失败，重试耗尽 → 进入 DLQ: "
            f"task_type={event.task_type}, "
            f"file_id={event.file_id}, "
            f"retry_count={event.retry_count}, "
            f"failure_reason={result.failure_reason}"
            f"error={result.error}"
        )

        # 使用 nack (不 requeue) 让消息自动进入 DLX → DLQ
        await message.nack(requeue=False)

        # 更新 Redis 任务状态
        await self._update_task_status(
            event.task_id, event.task_type, TaskStatus.FAILED,
            {
                "error": result.error,
                "failure_reason": result.failure_reason,
                "retry_exhausted": True,
            },
        )

    async def _send_next_task(self, event: FileProcessEvent, accumulated: dict):
        """发送流水线中下一个任务"""
        next_task = self._get_next_task(event.task_type, event.file_type)

        if next_task is None:
            logger.info(f"流水线完成: file_id={event.file_id}")
            return

        # 从 accumulated 中合并所有信息
        next_event = FileProcessEvent(
            message_id=FileProcessEvent.generate_message_id(),
            task_id=event.task_id,
            task_type=next_task,
            file_id=accumulated.get("file_id", event.file_id),
            user_id=event.user_id,
            file_name=event.file_name,
            file_type=event.file_type,
            file_size=accumulated.get("file_size", event.file_size),
            storage_path=accumulated.get("storage_path", event.storage_path),
            node_id=event.node_id,
            total_chunks=event.total_chunks,
            file_checksum=accumulated.get("checksum", event.file_checksum),
            retry_count=0,
            created_at=datetime.now(timezone.utc).isoformat(),
        )

        await rabbitmq_service.publish_message(
            settings.file_process_exchange,
            settings.file_process_routing_key,
            next_event.to_dict(),
        )

        logger.info(f"发送下一个任务: {event.task_type} → {next_task}, file_id={event.file_id}")

    @staticmethod
    def _get_next_task(current_task: str, file_type: str) -> str | None:
        """获取流水线中下一个任务"""
        try:
            idx = TASK_PIPELINE.index(current_task)
        except ValueError:
            return None

        if idx >= len(TASK_PIPELINE) - 1:
            return None

        next_task = TASK_PIPELINE[idx + 1]

        # 根据文件类型跳过不适用的任务
        from core.config import IMAGE_TYPES, VIDEO_TYPES
        if next_task == TaskTypes.THUMBNAIL and file_type not in IMAGE_TYPES:
            return FileProcessConsumer._get_next_task(next_task, file_type)
        if next_task == TaskTypes.VIDEO_TRANSCODE and file_type not in VIDEO_TYPES:
            return FileProcessConsumer._get_next_task(next_task, file_type)

        return next_task

    @staticmethod
    async def _update_task_status(task_id: str, task_type: str, status: str, data: dict):
        """更新 Redis 任务状态"""
        try:
            from server import redis_client
            task_key = f"task:{task_id}:{task_type}"
            mapping = {
                "status": status,
                "updated_at": datetime.now(timezone.utc).isoformat(),
            }
            mapping.update({k: str(v) for k, v in data.items()})
            await redis_client.hset(task_key, mapping=mapping)
            await redis_client.expire(task_key, 86400 * 7)
        except Exception as e:
            logger.error(f"更新任务状态失败: {e}")


# 入口工厂函数 (用于 server.py 注册)
async def on_file_process_message(message: Any):
    consumer = FileProcessConsumer()
    await consumer.handle(message)