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
        TaskTypes.HLS_TRANSCODE,
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
            # 关键修复：未预期异常不应 ACK，应 NACK(requeue=False) 让消息
            # 通过 RabbitMQ DLX 机制自动路由到死信队列，确保不丢失
            try:
                await message.nack(requeue=False)
                logger.warning(
                    f"[TASK-DLQ-ROUTE] file_id={file_id_str} "
                    f"task={task_type_str} "
                    f"reason=UNHANDLED_EXCEPTION "
                    f"error={e}"
                )
            except Exception as nack_err:
                logger.critical(
                    f"[TASK-NACK-FAIL] file_id={file_id_str} "
                    f"无法 NACK 消息: {nack_err}",
                    exc_info=True,
                )

    def _run_sync_process(self, event: FileProcessEvent):
        """
        在线程中同步执行 FileProcessor.process()
        用于重型任务，避免 asyncio 事件循环被长时间占用
        """
        return asyncio.run(FileProcessor.process(event))

    async def _on_success(self, message: Any, event: FileProcessEvent, result):
        """处理成功"""
        logger.info(
            f"[TASK-OK] file_id={event.file_id} "
            f"task={event.task_type} "
            f"result=success"
        )
        if event.task_type == TaskTypes.MERGE or event.task_type == TaskTypes.HASH_CALCULATE or event.task_type == TaskTypes.VIRUS_SCAN:
            await self._update_task_status(
                event.task_id, event.task_type, TaskStatus.PROCESSING, result.data,
            )
        else:
            # 先更新 Redis 任务状态（在 ACK 之前，确保状态持久化）
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
        if result.data.get("hls_resolutions"):
            accumulated.setdefault("hls_resolutions", []).extend(
                result.data["hls_resolutions"]
            )
        if result.data.get("hls_dir"):
            accumulated["hls_dir"] = result.data["hls_dir"]
        if result.data.get("hls_master_playlist"):
            accumulated["hls_master_playlist"] = result.data["hls_master_playlist"]
        if result.data.get("file_id"):
            accumulated["file_id"] = result.data["file_id"]
        if result.data.get("storage_path"):
            accumulated["storage_path"] = result.data["storage_path"]
        if result.data.get("checksum"):
            accumulated["checksum"] = result.data["checksum"]

        # 发送下一个任务（在 ACK 之前，确保流水线触发）
        await self._send_next_task(event, accumulated)

        # 当 MARK_ACTIVE 成功时，发布 file.available MQ 事件
        # 通知主业务服务提交配额（released -= fileSize, used += fileSize）
        if event.task_type == TaskTypes.MARK_ACTIVE:
            await self._publish_file_available_event(event)

        # 最后 ACK：确保所有后续操作都成功后才确认消息消费
        await message.ack()
        logger.debug(
            f"[TASK-ACK] file_id={event.file_id} "
            f"task={event.task_type} "
            f"status=success"
        )

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
        """发现病毒 → 先更新状态和通知业务服务，再 ACK"""
        logger.critical(
            f"[TASK-VIRUS] file_id={event.file_id} "
            f"threat={result.data.get('threat_name', 'unknown')} "
            f"status=quarantined"
        )

        # 先更新 Redis 任务状态
        await self._update_task_status(
            event.task_id,
            event.task_type,
            TaskStatus.FAILED,
            {"error": result.error, "failure_reason": result.failure_reason},
        )

        # 通知业务服务（在 ACK 之前，确保通知成功）
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

        # 发布 MQ 事件：通知主业务服务回滚配额
        await self._publish_file_scan_failed_event(
            event, result.data.get('threat_name', 'unknown')
        )

        # 最后 ACK：确保状态更新和通知都完成后才确认
        await message.ack()

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

        retry_event = event.with_retry_increment()
        retry_dict = retry_event.to_dict()
        retry_dict["failure_reason"] = result.failure_reason

        await asyncio.sleep(delay)

        try:
            await rabbitmq_service.publish_message(
                settings.file_process_exchange,
                settings.file_process_routing_key,
                retry_dict,
            )
            # 重试消息发布成功后才 ACK 原消息
            await message.ack()
            logger.info(
                f"[TASK-RETRY-OK] file_id={event.file_id} "
                f"task={event.task_type} "
                f"attempt={next_retry}/{self.retry.max_attempts} "
                f"delay_s={delay}"
            )
        except Exception as pub_err:
            # 重试发布失败 → 原消息不能 ACK，应 NACK 进入 DLQ
            logger.error(
                f"[TASK-RETRY-PUB-FAIL] file_id={event.file_id} "
                f"task={event.task_type} "
                f"重试消息发布失败，原消息进入 DLQ: {pub_err}",
                exc_info=True,
            )
            await message.nack(requeue=False)

    async def _send_to_dlq(self, message: Any, event: FileProcessEvent, result):
        """重试耗尽 → 先更新状态，再 NACK → 自动进入 DLQ (通过 DLX 配置)"""
        logger.error(
            f"[TASK-DLQ] file_id={event.file_id} "
            f"task={event.task_type} "
            f"retry_exhausted={event.retry_count} "
            f"reason={result.failure_reason} "
            f"error={result.error}"
        )

        # 先更新 Redis 任务状态（在 NACK 之前，确保状态持久化）
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

        # 再 NACK(requeue=False)：通过 RabbitMQ DLX 自动路由到死信队列
        await message.nack(requeue=False)

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
        if next_task == TaskTypes.HLS_TRANSCODE and file_type not in VIDEO_TYPES:
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

    @staticmethod
    async def _publish_file_scan_failed_event(event: FileProcessEvent, threat_name: str):
        """
        发布文件扫毒失败 MQ 事件 → 主业务服务回滚配额

        在以下场景中调用：
        1. 发现病毒/木马（_handle_virus_found）
        2. 病毒扫描器异常（进入 DLQ 后由 DeadLetterConsumer 处理）
        """
        try:
            import uuid
            from core.config import settings as _settings
            from core.rabbitmq import rabbitmq_service as _rabbitmq

            event_data = {
                "eventId": uuid.uuid4().hex,
                "fileId": event.file_id,
                "fileName": event.file_name,
                "fileSize": event.file_size,
                "fileType": event.file_type,
                "userId": event.user_id,
                "uploadsSessionId": getattr(event, 'uploads_id', ''),
                "threatName": threat_name,
                "eventTime": datetime.now(timezone.utc).isoformat(),
            }
            await _rabbitmq.publish_file_event(
                _settings.file_scan_failed_routing_key, event_data
            )
            logger.info(
                f"已发布 file.scan.failed 事件: fileId={event.file_id}, "
                f"threat={threat_name}"
            )
        except Exception as e:
            logger.error(f"发布 file.scan.failed 事件失败: {e}", exc_info=True)

    @staticmethod
    async def _publish_file_available_event(event: FileProcessEvent):
        """
        发布文件可获得 MQ 事件 → 主业务服务提交配额

        在 mark_active 流水线成功完成后调用。
        """
        try:
            import uuid
            from core.config import settings as _settings
            from core.rabbitmq import rabbitmq_service as _rabbitmq

            event_data = {
                "eventId": uuid.uuid4().hex,
                "fileId": event.file_id,
                "fileName": event.file_name,
                "fileSize": event.file_size,
                "fileType": event.file_type,
                "userId": event.user_id,
                "uploadsSessionId": getattr(event, 'uploads_id', ''),
                "eventTime": datetime.utcnow().isoformat(),#暂时不带时区
            }
            await _rabbitmq.publish_file_event(
                _settings.file_available_routing_key, event_data
            )
            logger.info(
                f"已发布 file.available 事件: fileId={event.file_id}"
            )
        except Exception as e:
            logger.error(f"发布 file.available 事件失败: {e}", exc_info=True)


# =============================================================================
# 入口工厂函数
# =============================================================================

async def on_file_process_message(message: Any):
    """文件处理消息入口 (预取多消息 + 并发处理)"""
    consumer = FileProcessConsumer()
    await consumer.handle(message)