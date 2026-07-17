"""
文件增强处理消费者基类

供 thumbnail / transcode / hls / index 四个增强阶段复用。

核心能力:
  - 基于 enhance_task_id + stage 的幂等检查（Redis 事件状态键）
  - 事件状态追踪: processing → completed / failed
  - 指数退避重试 (exponential backoff)
  - 超限发布到阶段专属 DLQ（同时更新 Redis 为 failed）
  - 增强失败不影响文件可用性，仅标记 DEGRADED

与 Backend 消费者的关键区别:
  - 增强失败不影响文件可用性，仅标记为 DEGRADED
  - 无流水线顺序约束，多个增强可并发
  - 增强事件不提供接口查询（仅内部追踪）
  - 失败后最多重试 N 次，超限入 DLQ 但不回滚文件状态
"""
from __future__ import annotations
import json
import asyncio
import logging
from abc import ABC, abstractmethod
from datetime import datetime, timezone
from typing import Any

from core.config import (
    settings, TaskTypes, TaskStatus,
    REDIS_ENHANCE_EVENT_KEY, REDIS_ENHANCE_MASTER_KEY,
    EVENT_STATUS_TTL, MASTER_TASK_TTL,
)
from core.rabbitmq import rabbitmq_service
from core.event.file_enhance_event import FileEnhanceEvent
from core.services.file_processor import FileProcessor, ProcessResult

logger = logging.getLogger("enhance_consumer")


class BaseEnhanceConsumer(ABC):
    """增强处理消费者基类（并发流水线）"""

    @property
    @abstractmethod
    def stage(self) -> str:
        """当前增强阶段 (thumbnail / video_transcode / hls_transcode / content_index)"""
        ...

    @property
    def max_retries(self) -> int:
        overrides = {
            TaskTypes.THUMBNAIL: settings.file_enhance_thumbnail_max_retries,
            TaskTypes.VIDEO_TRANSCODE: settings.file_enhance_transcode_max_retries,
            TaskTypes.HLS_TRANSCODE: settings.file_enhance_hls_max_retries,
            TaskTypes.CONTENT_INDEX: settings.file_enhance_index_max_retries,
            TaskTypes.OFFICE_TO_PDF: settings.file_enhance_office_to_pdf_max_retries,
            TaskTypes.ARCHIVE_PARSE: settings.file_enhance_archive_parse_max_retries,
            TaskTypes.MARKDOWN_TO_HTML: settings.file_enhance_markdown_to_html_max_retries,
        }
        return overrides.get(self.stage, 3)

    @property
    def exchange(self) -> str:
        return settings.file_enhance_exchange

    @property
    def dlx(self) -> str:
        return settings.file_enhance_dlx

    async def handle(self, message: Any):
        """
        统一消费入口

        流程:
          1. 解析消息 → FileEnhanceEvent
          2. 幂等检查（enhance_task_id + stage → Redis 事件状态键）
             - 若状态为 processing 或 completed → 幂等跳过
             - 否则设置状态为 processing 并开始消费
          3. 调用 process() 执行业务逻辑
          4. 成功 → 更新事件状态为 completed
          5. 失败 → 根据重试/死信策略:
             - 可重试: 重置事件状态为未处理，指数退避重试
             - 不可重试/超限: 更新事件状态为 failed → 发布到 DLQ
        """
        event: FileEnhanceEvent | None = None
        start_time = asyncio.get_event_loop().time()

        try:
            message_body = message.body.decode("utf-8")
            raw_data = json.loads(message_body)
            event = FileEnhanceEvent.from_dict(raw_data)

            logger.info(
                f"[ENHANCE-{self.stage}] RECEIVED "
                f"enhance_task_id={event.enhance_task_id} "
                f"file_id={event.file_id} "
                f"file_name={event.file_name} "
                f"retry={event.retry_count}/{self.max_retries}"
            )

            # 幂等检查（enhance_task_id + stage）
            event_status = await self._get_event_status(event.enhance_task_id)
            if event_status == TaskStatus.PROCESSING:
                logger.warning(
                    f"[ENHANCE-{self.stage}] DUPLICATE (processing) "
                    f"enhance_task_id={event.enhance_task_id} → ACK"
                )
                await message.ack()
                return
            if event_status == TaskStatus.COMPLETED:
                logger.warning(
                    f"[ENHANCE-{self.stage}] DUPLICATE (completed) "
                    f"enhance_task_id={event.enhance_task_id} → ACK"
                )
                await message.ack()
                return

            # 标记事件状态为 processing
            await self._set_event_status(event.enhance_task_id, TaskStatus.PROCESSING)

            # 执行
            result = await self.process(event)

            elapsed = asyncio.get_event_loop().time() - start_time

            if result.success:
                # 更新事件状态为 completed
                await self._set_event_status(event.enhance_task_id, TaskStatus.COMPLETED)
                logger.info(
                    f"[ENHANCE-{self.stage}] SUCCESS "
                    f"enhance_task_id={event.enhance_task_id} "
                    f"file_id={event.file_id} "
                    f"elapsed={elapsed:.2f}s "
                    f"data={list(result.data.keys())}"
                )
                await message.ack()
            else:
                logger.warning(
                    f"[ENHANCE-{self.stage}] FAILED "
                    f"enhance_task_id={event.enhance_task_id} "
                    f"file_id={event.file_id} "
                    f"reason={result.failure_reason} "
                    f"elapsed={elapsed:.2f}s"
                )
                await self._on_failure(message, event, result)

        except json.JSONDecodeError:
            logger.error(f"[ENHANCE-{self.stage}] JSON_PARSE_ERROR → 丢弃")
            await message.ack()
        except Exception as e:
            logger.error(
                f"[ENHANCE-{self.stage}] EXCEPTION "
                f"error={e}",
                exc_info=True,
            )
            # 未预期异常 → 更新事件状态为 failed
            if event:
                await self._set_event_status(event.enhance_task_id, TaskStatus.FAILED)
            await message.nack(requeue=False)

    @abstractmethod
    async def process(self, event: FileEnhanceEvent) -> ProcessResult:
        """执行业务处理逻辑"""
        ...

    async def _on_failure(
        self, message: Any, event: FileEnhanceEvent, result: ProcessResult
    ):
        """增强失败 → 重试 / DLQ（不影响文件可用性）"""
        if event.retry_count >= self.max_retries:
            # 超过最大重试次数 → 更新事件状态为 failed → 发布到 DLQ
            await self._set_event_status(event.enhance_task_id, TaskStatus.FAILED)
            await self._publish_to_dlq(event, result)
            await message.ack()
            return

        # 重置事件状态为未处理（允许重试消费）
        await self._reset_event_status(event.enhance_task_id)

        delay = min(
            settings.retry_base_delay_seconds * (2 ** event.retry_count),
            settings.retry_max_delay_seconds,
        )
        logger.warning(
            f"[ENHANCE-{self.stage}] RETRY "
            f"enhance_task_id={event.enhance_task_id} "
            f"attempt={event.retry_count + 1}/{self.max_retries} "
            f"delay={delay}s "
            f"reason={result.failure_reason}"
        )

        await asyncio.sleep(delay)
        await message.ack()

        retry_event = event.with_retry_increment()
        retry_event.failure_reason = result.failure_reason

        rk = self._get_routing_key()
        await rabbitmq_service.publish_message(
            exchange_name=self.exchange,
            routing_key=rk,
            message=retry_event.to_dict(),
        )

    async def _publish_to_dlq(self, event: FileEnhanceEvent, result: ProcessResult):
        """发布到增强 DLQ"""
        dlq_rks = {
            TaskTypes.THUMBNAIL: settings.file_enhance_thumbnail_dlq_routing_key,
            TaskTypes.VIDEO_TRANSCODE: settings.file_enhance_transcode_dlq_routing_key,
            TaskTypes.HLS_TRANSCODE: settings.file_enhance_hls_dlq_routing_key,
            TaskTypes.CONTENT_INDEX: settings.file_enhance_index_dlq_routing_key,
            TaskTypes.OFFICE_TO_PDF: settings.file_enhance_office_to_pdf_dlq_routing_key,
            TaskTypes.ARCHIVE_PARSE: settings.file_enhance_archive_parse_dlq_routing_key,
            TaskTypes.MARKDOWN_TO_HTML: settings.file_enhance_markdown_to_html_dlq_routing_key,
        }

        dlq_event = event.with_retry_increment()
        dlq_event.failure_reason = result.failure_reason

        logger.error(
            f"[ENHANCE-{self.stage}] → DLQ "
            f"enhance_task_id={event.enhance_task_id} "
            f"file_id={event.file_id} "
            f"reason={result.failure_reason}"
        )

        await rabbitmq_service.publish_to_dlq(
            exchange_name=self.dlx,
            routing_key=dlq_rks.get(self.stage, "file.enhance.dlq"),
            message=dlq_event.to_dict(),
        )

    def _get_routing_key(self) -> str:
        rks = {
            TaskTypes.THUMBNAIL: settings.file_enhance_thumbnail_routing_key,
            TaskTypes.VIDEO_TRANSCODE: settings.file_enhance_transcode_routing_key,
            TaskTypes.HLS_TRANSCODE: settings.file_enhance_hls_routing_key,
            TaskTypes.CONTENT_INDEX: settings.file_enhance_index_routing_key,
            TaskTypes.OFFICE_TO_PDF: settings.file_enhance_office_to_pdf_routing_key,
            TaskTypes.ARCHIVE_PARSE: settings.file_enhance_archive_parse_routing_key,
            TaskTypes.MARKDOWN_TO_HTML: settings.file_enhance_markdown_to_html_routing_key,
        }
        return rks.get(self.stage, "")

    # ===== Redis 事件状态管理 =====

    def _event_key(self, enhance_task_id: str) -> str:
        return REDIS_ENHANCE_EVENT_KEY.format(
            enhance_task_id=enhance_task_id, stage=self.stage
        )

    async def _get_event_status(self, enhance_task_id: str) -> str | None:
        """获取事件状态: processing / completed / failed / None（不存在）"""
        try:
            from app.core.redis_client import redis_client
            return await redis_client.get(self._event_key(enhance_task_id))
        except Exception:
            return None

    async def _set_event_status(self, enhance_task_id: str, status: str):
        """设置事件状态（带 TTL）"""
        try:
            from app.core.redis_client import redis_client
            key = self._event_key(enhance_task_id)
            await redis_client.setex(key, EVENT_STATUS_TTL, status)
            logger.debug(
                f"[ENHANCE-{self.stage}] EVENT_STATUS "
                f"key={key} → {status}"
            )
        except Exception as e:
            logger.error(f"设置增强事件状态失败: {e}")

    async def _reset_event_status(self, enhance_task_id: str):
        """重置事件状态（删除键，表示未处理）"""
        try:
            from app.core.redis_client import redis_client
            await redis_client.delete(self._event_key(enhance_task_id))
        except Exception as e:
            logger.error(f"重置增强事件状态失败: {e}")