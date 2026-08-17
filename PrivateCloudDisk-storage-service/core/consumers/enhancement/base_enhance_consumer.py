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
import os
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
from core.messaging.errors import RetryableWorkerError, classify_exception, exception_summary
from core.messaging.metrics import worker_metrics

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
             - completed/failed → 幂等跳过
             - processing：原行为直接 ACK；新行为复制到持久化延迟队列，租约过期后可恢复
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

            # 使用带租约的原子 NX 抢占替代“先读后写”竞态。
            # Worker 异常退出后租约会自动过期；处理中重复消息进入持久化延迟队列，不再直接 ACK 丢失。
            claim_state = await self._claim_event(event.enhance_task_id)
            if claim_state == TaskStatus.PROCESSING:
                logger.warning(
                    f"[ENHANCE-{self.stage}] DUPLICATE (processing) "
                    f"enhance_task_id={event.enhance_task_id} → DELAYED_RECHECK"
                )
                await rabbitmq_service.publish_message(
                    exchange_name=self.exchange,
                    routing_key=f"{self._get_routing_key()}.retry",
                    message=event.to_dict(),
                    delay_seconds=min(60, settings.enhance_processing_lease_seconds),
                )
                await message.ack()
                return
            if claim_state in {TaskStatus.COMPLETED, TaskStatus.FAILED}:
                logger.warning(
                    f"[ENHANCE-{self.stage}] DUPLICATE ({claim_state}) "
                    f"enhance_task_id={event.enhance_task_id} → ACK"
                )
                await message.ack()
                return

            # 执行
            result = await self.process(event)

            elapsed = asyncio.get_event_loop().time() - start_time

            if result.success:
                # AUDIT FIX [7.4]: 增强产物成功后先持久化元数据，再 ACK，避免 MQ 已确认但资源不可查询。
                await self._persist_preview_resources(event, result)
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

        except json.JSONDecodeError as exc:
            logger.error(f"[ENHANCE-{self.stage}] JSON_PARSE_ERROR → 专属 DLQ")
            invalid = FileEnhanceEvent(
                enhance_task_id="invalid",
                stage=self.stage,
                file_id="",
                user_id="",
                file_name="",
                file_type="",
                failure_reason="MESSAGE_SCHEMA_ERROR",
                failure_detail=exception_summary(exc),
            )
            try:
                await self._publish_to_dlq(invalid, ProcessResult(
                    success=False,
                    task_type=self.stage,
                    failure_reason="MESSAGE_SCHEMA_ERROR",
                    error=exception_summary(exc),
                    retryable=False,
                ))
                await message.ack()
            except Exception:
                logger.exception("[ENHANCE-%s] SCHEMA_DLQ_PUBLISH_FAILED → 原消息重新入队", self.stage)
                if not getattr(message, "processed", False):
                    await message.nack(requeue=True)
        except Exception as e:
            logger.error(
                f"[ENHANCE-{self.stage}] EXCEPTION "
                f"error={e}",
                exc_info=True,
            )
            # 修复原实现引用未定义 result、直接 ACK 的缺陷。统一补全错误摘要并
            # 进入有界恢复流程；未知异常按不可重试处理，避免 stage=unknown 空死信。
            if event:
                classified = classify_exception(e)
                unexpected = ProcessResult(
                    success=False,
                    failure_reason=self._failure_reason_for_stage()
                    if isinstance(classified, RetryableWorkerError)
                    else "UNEXPECTED_ERROR",
                    error=exception_summary(classified),
                    retryable=isinstance(classified, RetryableWorkerError),
                )
                await self._on_failure(message, event, unexpected)
            elif not getattr(message, "processed", False):
                await message.nack(requeue=False)

    @abstractmethod
    async def process(self, event: FileEnhanceEvent) -> ProcessResult:
        """执行业务处理逻辑"""
        ...

    async def _on_failure(
        self, message: Any, event: FileEnhanceEvent, result: ProcessResult
    ):
        """增强失败 → 重试 / DLQ（不影响文件可用性）"""
        non_retryable = result.retryable is False or result.failure_reason in {
            "UNKNOWN", "UNEXPECTED_ERROR", "MESSAGE_SCHEMA_ERROR"
        }
        if non_retryable or event.retry_count >= self.max_retries:
            # 超过最大重试次数 → 更新事件状态为 failed → 发布到 DLQ
            await self._set_event_status(event.enhance_task_id, TaskStatus.FAILED)
            # W-03：只有专属 DLQ 发布确认后才 ACK；发布失败时重新入队，避免死信丢失。
            try:
                await self._publish_to_dlq(event, result)
                await message.ack()
                await worker_metrics.record(self.stage, "dlq")
            except Exception:
                logger.exception(
                    "[ENHANCE-%s] DLQ_PUBLISH_FAILED enhance_task_id=%s → 原消息重新入队",
                    self.stage,
                    event.enhance_task_id,
                )
                if not getattr(message, "processed", False):
                    await message.nack(requeue=True)
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

        retry_event = event.with_retry_increment()
        retry_event.failure_reason = result.failure_reason
        retry_event.failure_detail = (result.error or result.failure_reason or "未知增强异常")[:1000]

        # 先持久化发布再 ACK，且由 RabbitMQ 延迟队列承担等待和进程恢复。
        try:
            # W-02：先持久化发布到阶段专属 `.retry` 队列，再 ACK 原消息。
            await rabbitmq_service.publish_retry_message(
                exchange_name=self.exchange,
                routing_key=self._get_routing_key(),
                message=retry_event.to_dict(),
                delay_seconds=delay,
            )
            await message.ack()
            await worker_metrics.record(self.stage, "retry")
        except Exception:
            logger.exception(
                "[ENHANCE-%s] RETRY_PUBLISH_FAILED enhance_task_id=%s",
                self.stage,
                event.enhance_task_id,
            )
            if not getattr(message, "processed", False):
                await message.nack(requeue=True)

    async def _publish_to_dlq(self, event: FileEnhanceEvent, result: ProcessResult):
        """发布到增强 DLQ"""
        dlq_rks = {
            TaskTypes.THUMBNAIL: settings.file_enhance_thumbnail_dlq_routing_key,
            TaskTypes.VIDEO_TRANSCODE: settings.file_enhance_transcode_dlq_routing_key,
            TaskTypes.HLS_TRANSCODE: settings.file_enhance_hls_dlq_routing_key,
            TaskTypes.CONTENT_INDEX: settings.file_enhance_index_dlq_routing_key,
            TaskTypes.OFFICE_TO_PDF: settings.file_enhance_office_to_pdf_dlq_routing_key,
            TaskTypes.ARCHIVE_PARSE: settings.file_enhance_archive_parse_dlq_routing_key,
        }

        dlq_event = event.with_retry_increment()
        dlq_event.failure_reason = result.failure_reason
        # AUDIT FIX [7.4]（需求一-2）：标准 failure_reason 用于策略路由，原始异常摘要另行保留用于根因排查。
        dlq_event.failure_detail = (result.error or result.failure_reason or "未知增强异常")[:1000]

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

    async def _persist_preview_resources(self, event: FileEnhanceEvent, result: ProcessResult) -> None:
        """把各增强流水线的异构结果归一化为预览资源记录。"""
        if result.data.get("skipped"):
            return
        from app.models.preview_resource import PreviewResource
        from app.services.preview_resource_service import preview_resource_service

        resources: list[PreviewResource] = []
        common = {
            "file_id": event.file_id,
            "user_id": event.user_id,
            "space_id": event.space_id or None,
            "resource_status": "ready",
        }

        if self.stage == TaskTypes.HLS_TRANSCODE and result.data.get("hls_dir"):
            metadata = {
                "resolutions": result.data.get("resolutions", []),
                "master_playlist": result.data.get("hls_master_playlist"),
                "manifest_path": result.data.get("manifest_path"),
            }
            resources.append(PreviewResource(
                **common, resource_type="hls", resource_variant="master",
                storage_path=result.data["hls_dir"], mime_type="application/vnd.apple.mpegurl",
                width=result.data.get("source_width"), height=result.data.get("source_height"),
                duration_seconds=result.data.get("duration"), metadata=metadata,
            ))
            for variant, path in (result.data.get("preview_paths") or {}).items():
                resources.append(PreviewResource(
                    **common, resource_type="video_thumbnail", resource_variant=variant,
                    storage_path=path, mime_type="image/jpeg",
                    width=None if variant == "poster" else {"small": 320, "medium": 640, "large": 1280}.get(variant),
                    height=None if variant == "poster" else {"small": 180, "medium": 360, "large": 720}.get(variant),
                ))
            hover_preview_path = result.data.get("hover_preview_path")
            if hover_preview_path:
                resources.append(PreviewResource(
                    **common,
                    resource_type="video_preview",
                    resource_variant="30s",
                    storage_path=hover_preview_path,
                    mime_type="video/mp4",
                    size_bytes=os.path.getsize(hover_preview_path) if os.path.isfile(hover_preview_path) else 0,
                    width=result.data.get("source_width"),
                    height=result.data.get("source_height"),
                    duration_seconds=min(float(result.data.get("duration") or 0), 30.0),
                    metadata={"purpose": "file_browser_hover", "max_duration_seconds": 30},
                ))
        elif self.stage == TaskTypes.OFFICE_TO_PDF and result.data.get("pdf_path"):
            resources.append(PreviewResource(
                **common, resource_type="office_pdf", storage_path=result.data["pdf_path"],
                mime_type="application/pdf", size_bytes=result.data.get("pdf_size") or 0,
                page_count=result.data.get("page_count") or None,
                metadata={"source_type": result.data.get("source_type")},
            ))
            # AUDIT FIX [5.2]（需求五-4/5）：四档封面图逐档入库，接口只按数据库台账定位资源。
            preview_metadata = result.data.get("preview_metadata") or {}
            for variant, path in (result.data.get("preview_paths") or {}).items():
                variant_metadata = preview_metadata.get(variant) or {}
                resources.append(PreviewResource(
                    **common,
                    resource_type="office_thumbnail",
                    resource_variant=variant,
                    storage_path=path,
                    mime_type="image/jpeg",
                    size_bytes=variant_metadata.get("size_bytes") or 0,
                    width=variant_metadata.get("width"),
                    height=variant_metadata.get("height"),
                    page_count=result.data.get("page_count") or None,
                    metadata={
                        "source_type": result.data.get("source_type"),
                        "quality": variant_metadata.get("quality"),
                    },
                ))
        elif self.stage == TaskTypes.ARCHIVE_PARSE and result.data.get("tree_json_path"):
            resources.append(PreviewResource(
                **common, resource_type="archive", resource_variant="tree",
                storage_path=result.data["tree_json_path"], mime_type="application/json",
                metadata={k: result.data.get(k) for k in ("total_files", "total_dirs", "total_size")},
            ))
        elif self.stage == TaskTypes.THUMBNAIL:
            for item in result.data.get("thumbnails") or []:
                path = item if isinstance(item, str) else item.get("path", "")
                if path:
                    variant = "default" if isinstance(item, str) else str(item.get("size") or item.get("variant") or "default")
                    resources.append(PreviewResource(
                        **common, resource_type="thumbnail", resource_variant=variant,
                        storage_path=path, mime_type="image/jpeg",
                    ))
            # PDF 本身就是可直接预览的就绪资源；不能只登记首页缩略图，否则 preview-info 会永久 pending。
            if event.file_type == "application/pdf" and event.storage_path:
                resources.append(PreviewResource(
                    **common, resource_type="pdf", resource_variant="default",
                    storage_path=event.storage_path, mime_type="application/pdf",
                    size_bytes=event.file_size or 0,
                ))

        for resource in resources:
            await preview_resource_service.upsert(resource)

    def _get_routing_key(self) -> str:
        rks = {
            TaskTypes.THUMBNAIL: settings.file_enhance_thumbnail_routing_key,
            TaskTypes.VIDEO_TRANSCODE: settings.file_enhance_transcode_routing_key,
            TaskTypes.HLS_TRANSCODE: settings.file_enhance_hls_routing_key,
            TaskTypes.CONTENT_INDEX: settings.file_enhance_index_routing_key,
            TaskTypes.OFFICE_TO_PDF: settings.file_enhance_office_to_pdf_routing_key,
            TaskTypes.ARCHIVE_PARSE: settings.file_enhance_archive_parse_routing_key,
        }
        return rks.get(self.stage, "")

    def _failure_reason_for_stage(self) -> str:
        """把未预期异常规范到当前阶段，确保 failure_reason 可用于恢复策略和告警聚合。"""
        reasons = {
            TaskTypes.THUMBNAIL: "THUMBNAIL_ERROR",
            TaskTypes.VIDEO_TRANSCODE: "TRANSCODE_ERROR",
            TaskTypes.HLS_TRANSCODE: "TRANSCODE_ERROR",
            TaskTypes.CONTENT_INDEX: "CONTENT_INDEX_ERROR",
            TaskTypes.OFFICE_TO_PDF: "OFFICE_TO_PDF_ERROR",
            TaskTypes.ARCHIVE_PARSE: "ARCHIVE_PARSE_ERROR",
        }
        return reasons.get(self.stage, "UNKNOWN")

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

    async def _claim_event(self, enhance_task_id: str) -> str:
        """原子抢占增强任务处理租约。

        返回 ``claimed`` 表示当前 Worker 获得执行权；Redis 故障时允许降级执行，
        由数据库资源唯一键继续提供结果幂等，避免缓存故障阻断全部增强任务。
        """
        try:
            from app.core.redis_client import redis_client

            key = self._event_key(enhance_task_id)
            acquired = await redis_client.set(
                key,
                TaskStatus.PROCESSING,
                ex=settings.enhance_processing_lease_seconds,
                nx=True,
            )
            if acquired:
                return "claimed"
            return str(await redis_client.get(key) or TaskStatus.PROCESSING)
        except Exception as exc:
            logger.warning(
                "[ENHANCE-%s] Redis 租约不可用，降级依赖资源表幂等: %s",
                self.stage,
                exc,
            )
            return "claimed"

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
