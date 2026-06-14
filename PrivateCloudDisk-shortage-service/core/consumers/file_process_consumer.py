"""
文件处理消息消费者 (主消费者) - 支持并发处理

核心改进：
- 使用 asyncio.to_thread 将重型任务（视频转码、病毒扫描、合并）卸载到线程池
- 每个队列独立 prefetch_count + Semaphore 并发控制
- 独立 Worker 进程运行，与 FastAPI 主进程完全解耦

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
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from typing import Any

from core.config import settings, TaskTypes, TaskStatus, TASK_PIPELINE, FailureReason
from core.rabbitmq import rabbitmq_service
from core.event.file_process_event import FileProcessEvent
from core.services.file_processor import FileProcessor
from core.services.retry_service import RetryService

logger = logging.getLogger("file_process_consumer")


# =============================================================================
# 线程池：用于卸载重型 CPU/IO 任务，避免阻塞 asyncio 事件循环
# =============================================================================
# 默认线程池大小 = min(32, CPU 核心数 + 4)
import os
_DEFAULT_WORKERS = min(32, (os.cpu_count() or 1) + 4)
_heavy_task_executor = ThreadPoolExecutor(
    max_workers=_DEFAULT_WORKERS,
    thread_name_prefix="heavy_task",
)


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

    并发模型:
    - RabbitMQ prefetch_count > 1 → 多个消息同时预取到客户端
    - asyncio.Semaphore 限制协程级并发数
    - 重型任务（视频转码等）通过 asyncio.to_thread 卸载到线程池
    """

    # 重型任务类型：需要卸载到线程池执行
    # 注意：CONTENT_INDEX 使用 opensearchpy (底层 aiohttp)，必须在原生 asyncio
    # 事件循环中执行，不能通过 asyncio.run() 在线程中运行，否则会报：
    #   RuntimeError: Timeout context manager should be used inside a task
    HEAVY_TASK_TYPES = frozenset({
        TaskTypes.MERGE,
        TaskTypes.VIDEO_TRANSCODE,
        TaskTypes.VIRUS_SCAN,
    })

    def __init__(self):
        self.retry = RetryService()

    async def handle(self, message: Any):
        """消费者入口 (作为 aio_pika callback)"""
        t_start = time.monotonic()
        event = None
        task_type_str = "?"
        file_id_str = "?"

        try:
            message_body = message.body.decode("utf-8")
            raw_data = json.loads(message_body)
            event = FileProcessEvent.from_dict(raw_data)
            task_type_str = event.task_type
            file_id_str = event.file_id

            is_heavy = event.task_type in self.HEAVY_TASK_TYPES
            exec_model = "THREAD_POOL" if is_heavy else "COROUTINE"

            logger.info(
                f"[TASK-RECV] file_id={file_id_str} "
                f"task={task_type_str} "
                f"retry={event.retry_count} "
                f"exec_model={exec_model}"
            )

            # ---- 重型任务通过线程池执行，避免阻塞事件循环 ----
            if is_heavy:
                # 记录线程池状态
                pool = _heavy_task_executor
                active = pool._work_queue.qsize() if hasattr(pool, '_work_queue') else -1
                logger.info(
                    f"[TASK-POOL] file_id={file_id_str} "
                    f"task={task_type_str} "
                    f"thread_pool_active_approx={active}"
                )
                t_dispatch = time.monotonic()
                result = await asyncio.to_thread(
                    self._run_sync_process, event,
                )
                thread_elapsed = (time.monotonic() - t_dispatch) * 1000
            else:
                t_dispatch = time.monotonic()
                result = await FileProcessor.process(event)
                thread_elapsed = (time.monotonic() - t_dispatch) * 1000

            total_ms = (time.monotonic() - t_start) * 1000
            if result.success:
                logger.info(
                    f"[TASK-OK] file_id={file_id_str} "
                    f"task={task_type_str} "
                    f"exec={exec_model} "
                    f"elapsed_ms={total_ms:.0f} "
                    f"work_ms={thread_elapsed:.0f}"
                )
            else:
                logger.warning(
                    f"[TASK-FAIL] file_id={file_id_str} "
                    f"task={task_type_str} "
                    f"reason={result.failure_reason} "
                    f"elapsed_ms={total_ms:.0f}"
                )

            if result.success:
                await self._on_success(message, event, result)
            else:
                await self._on_failure(message, event, result)

        except json.JSONDecodeError:
            logger.error(f"[TASK-DROP] JSON 解析失败，丢弃")
            await message.ack()
        except Exception as e:
            total_ms = (time.monotonic() - t_start) * 1000
            logger.error(
                f"[TASK-ERR] file_id={file_id_str} "
                f"task={task_type_str} "
                f"elapsed_ms={total_ms:.0f} "
                f"error={e}",
                exc_info=True,
            )
            await message.ack()

    def _run_sync_process(self, event: FileProcessEvent):
        """
        在线程中同步执行 FileProcessor.process()
        用于重型任务，避免 asyncio 事件循环被长时间占用
        """
        return asyncio.run(FileProcessor.process(event))

    async def _on_success(self, message: Any, event: FileProcessEvent, result):
        """处理成功"""
        await message.ack()
        logger.info(
            f"[TASK-ACK] file_id={event.file_id} "
            f"task={event.task_type} "
            f"status=success"
        )

        # 更新 Redis 任务状态
        await self._update_task_status(
            event.task_id, event.task_type, TaskStatus.COMPLETED, result.data,
        )

        # 累积缩略图和转码信息
        accumulated = event.to_dict()
        if result.data.get("thumbnails"):
            accumulated.setdefault("thumbnails", []).extend(result.data["thumbnails"])
        if result.data.get("transcoded_files"):
            accumulated.setdefault("transcoded_files", []).extend(
                result.data["transcoded_files"]
            )
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

        # 病毒发现 → 不重试，直接处理
        if failure_reason == FailureReason.VIRUS_FOUND:
            await self._handle_virus_found(message, event, result)
            return

        # 判断是否应该重试
        if not self.retry.should_retry(event.retry_count, failure_reason):
            await self._send_to_dlq(message, event, result)
            return

        # 重试
        await self._retry_with_backoff(message, event, result)

    async def _handle_virus_found(
        self, message: Any, event: FileProcessEvent, result
    ):
        """发现病毒 → ACK 并发布安全事件"""
        await message.ack()
        logger.critical(
            f"[TASK-VIRUS] file_id={event.file_id} "
            f"threat={result.data.get('threat_name', 'unknown')} "
            f"status=quarantined"
        )

        await self._update_task_status(
            event.task_id,
            event.task_type,
            TaskStatus.FAILED,
            {"error": result.error, "failure_reason": result.failure_reason},
        )

        try:
            from core.services.notification_service import NotificationService

            await NotificationService.notify_file_status(
                file_id=event.file_id,
                status="reject",
                user_id=event.user_id,
                error_message=f"文件包含病毒/木马: {result.data.get('threat_name', 'unknown')}",
            )
        except Exception as e:
            logger.error(f"通知业务服务失败: {e}")

    async def _retry_with_backoff(
        self, message: Any, event: FileProcessEvent, result
    ):
        """指数退避重试"""
        next_retry = event.retry_count + 1
        delay = self.retry.get_delay_seconds(next_retry)

        logger.warning(
            f"[TASK-RETRY] file_id={event.file_id} "
            f"task={event.task_type} "
            f"attempt={next_retry}/{self.retry.max_attempts} "
            f"delay_s={delay} "
            f"reason={result.failure_reason} "
            f"error={result.error}"
        )

        await message.ack()

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
        """重试耗尽 → NACK → 自动进入 DLQ (通过 DLX 配置)"""
        logger.error(
            f"[TASK-DLQ] file_id={event.file_id} "
            f"task={event.task_type} "
            f"retry_exhausted={event.retry_count} "
            f"reason={result.failure_reason} "
            f"error={result.error}"
        )

        await message.nack(requeue=False)

        await self._update_task_status(
            event.task_id,
            event.task_type,
            TaskStatus.FAILED,
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
            logger.info(f"[TASK-PIPELINE-END] file_id={event.file_id}")
            return

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

        logger.info(
            f"[TASK-NEXT] file_id={event.file_id} "
            f"from={event.task_type} → to={next_task}"
        )

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

        from core.config import IMAGE_TYPES, VIDEO_TYPES

        if next_task == TaskTypes.THUMBNAIL and file_type not in IMAGE_TYPES:
            return FileProcessConsumer._get_next_task(next_task, file_type)
        if next_task == TaskTypes.VIDEO_TRANSCODE and file_type not in VIDEO_TYPES:
            return FileProcessConsumer._get_next_task(next_task, file_type)

        return next_task

    @staticmethod
    async def _update_task_status(
        task_id: str, task_type: str, status: str, data: dict
    ):
        """更新 Redis 任务状态"""
        try:
            from app.core.redis_client import redis_client

            task_key = f"task:{task_id}:{task_type}"
            mapping = {
                "status": status,
                "updated_at": datetime.now(timezone.utc).isoformat(),
            }
            mapping.update({k: str(v) for k, v in data.items()})
            await redis_client.hset(task_key, mapping=mapping)
            await redis_client.expire(task_key, 86400 * 7)

            task_key = f"task:{task_id}"
            mapping = {"status": status, "current_step": task_type}
            await redis_client.hset(task_key, mapping=mapping)
        except Exception as e:
            logger.error(f"更新任务状态失败: {e}")


# =============================================================================
# 入口工厂函数
# =============================================================================

async def on_file_process_message(message: Any):
    """文件处理消息入口 (预取多消息 + 并发处理)"""
    consumer = FileProcessConsumer()
    await consumer.handle(message)