"""
文件后台处理消费者基类

供 merge / hash / virus / mark_active 四个实际计算阶段消费者复用。
content_preprocess 是持久化等待闸门，由生命周期消费者关闭，不占用计算消费者。

核心能力:
  - 基于 backend_task_id + stage 的幂等检查（Redis 事件状态键）
  - 事件状态追踪: processing → completed / failed
  - 总任务状态更新: 记录当前阶段 + 状态（processing / completed / failed）
  - 指数退避重试 (exponential backoff)
  - 超限发布到阶段专属 DLQ（同时更新 Redis 为 failed）
  - 成功 → 发布下一阶段消息（仅在上一个阶段 completed 后）
  - 流水线终点 (mark_active) → 更新总任务为 completed → 触发增强事件
  - 任何阶段失败 → 总任务状态更新为 failed
"""
from __future__ import annotations
import json
import asyncio
import logging
import time
from abc import ABC, abstractmethod
from datetime import datetime, timezone
from typing import Any

from core.config import (
    settings, TaskTypes, TaskStatus,
    REDIS_BACKEND_EVENT_KEY, REDIS_BACKEND_MASTER_KEY,
    EVENT_STATUS_TTL, MASTER_TASK_TTL,
    BACKEND_NEXT_STAGE, BACKEND_STAGE_ROUTING_KEY,
)
from core.rabbitmq import rabbitmq_service
from core.event.file_backend_event import FileBackendEvent
from core.services.file_processor import FileProcessor, ProcessResult
from core.messaging.errors import RetryableWorkerError, classify_exception, exception_summary
from core.messaging.metrics import worker_metrics

logger = logging.getLogger("backend_consumer")


class BaseBackendConsumer(ABC):
    """后台处理消费者基类（顺序流水线）"""

    @property
    @abstractmethod
    def stage(self) -> str:
        """当前处理阶段 (merge / hash_calculate / virus_scan / mark_active)"""
        ...

    @property
    def max_retries(self) -> int:
        """最大重试次数"""
        overrides = {
            TaskTypes.MERGE: settings.file_backend_merge_max_retries,
            TaskTypes.HASH_CALCULATE: settings.file_backend_hash_max_retries,
            TaskTypes.VIRUS_SCAN: settings.file_backend_virus_max_retries,
            TaskTypes.MARK_ACTIVE: settings.file_backend_mark_active_max_retries,
        }
        return overrides.get(self.stage, 3)

    @property
    def exchange(self) -> str:
        return settings.file_backend_exchange

    @property
    def dlx(self) -> str:
        return settings.file_backend_dlx

    async def handle(self, message: Any):
        """
        统一消费入口

        流程:
          1. 解析消息 → FileBackendEvent
          2. 幂等检查（backend_task_id + stage → Redis 事件状态键）
             - 若状态为 processing 或 completed → 幂等跳过
             - 否则设置状态为 processing 并开始消费
          3. 调用 process() 执行业务逻辑
          4. 成功 → onSuccess: 更新事件状态为 completed + 更新总任务状态 + 发布下一阶段
          5. 失败 → 根据重试/死信策略:
             - 可重试: 重置事件状态为未处理，指数退避重试
             - 不可重试/超限: 更新事件状态为 failed + 更新总任务状态为 failed + 发布到 DLQ
        """
        event: FileBackendEvent | None = None
        start_time = asyncio.get_event_loop().time()

        try:
            # 1. 解析消息
            message_body = message.body.decode("utf-8")
            raw_data = json.loads(message_body)
            event = FileBackendEvent.from_dict(raw_data)

            logger.info(
                f"[BACKEND-{self.stage}] RECEIVED "
                f"backend_task_id={event.backend_task_id} "
                f"pipeline_id={event.pipeline_id} "
                f"file_id={event.file_id} "
                f"file_name={event.file_name} "
                f"retry={event.retry_count}/{self.max_retries}"
            )

            # 2. 幂等检查。新行为使用 Redis SET NX 原子抢占，避免多进程中“先读后写”竞态；
            # Redis 故障时仍由原有业务状态和数据库 Gate/资源唯一键兜底。
            #claimed = await self._claim_event(event.backend_task_id)
            event_status = await self._get_event_status(event.backend_task_id)
            if event_status == TaskStatus.PROCESSING:
                logger.warning(
                    f"[BACKEND-{self.stage}] DUPLICATE (processing) "
                    f"backend_task_id={event.backend_task_id} → ACK"
                )
                await message.ack()
                return
            if event_status == TaskStatus.COMPLETED:
                logger.warning(
                    f"[BACKEND-{self.stage}] DUPLICATE (completed) "
                    f"backend_task_id={event.backend_task_id} → ACK"
                )
                await message.ack()
                return

            # 检查上一阶段是否已完成（顺序流水线保证）
            if not await self._can_proceed(event.backend_task_id):
                logger.warning(
                    f"[BACKEND-{self.stage}] BLOCKED "
                    f"backend_task_id={event.backend_task_id} "
                    f"→ 上一阶段未完成，重新入队"
                )
                await message.nack(requeue=True)
                return

            # 标记事件状态为 processing（SET NX 已成功时只刷新租约状态）。
            await self._set_event_status(event.backend_task_id, TaskStatus.PROCESSING)

            # 3. 执行业务逻辑
            result = await self.process(event)

            elapsed = asyncio.get_event_loop().time() - start_time

            if result.success:
                await self._on_success(message, event, result, elapsed)
            else:
                logger.warning(
                    f"[BACKEND-{self.stage}] FAILED "
                    f"backend_task_id={event.backend_task_id} "
                    f"pipeline_id={event.pipeline_id} "
                    f"file_id={event.file_id} "
                    f"reason={result.failure_reason} "
                    f"elapsed={elapsed * 1000}ms"
                )
                await self._on_failure(message, event, result)

        except json.JSONDecodeError as exc:
            logger.error(
                f"[BACKEND-{self.stage}] JSON_PARSE_ERROR → 专属 DLQ"
            )
            # 协议错误不可重试，但不能静默 ACK 丢失；构造最小 DLQ 载荷并在
            # 发布确认后 ACK 原消息。当前专属 DLQ 消费者读取 stage/failure_reason。
            invalid = FileBackendEvent(
                backend_task_id="invalid",
                stage=self.stage,
                pipeline_id="invalid",
                file_id="",
                user_id="",
                file_name="",
                file_type="",
                failure_reason="MESSAGE_SCHEMA_ERROR",
                accumulated={"raw_error": exception_summary(exc)},
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
                logger.exception("[BACKEND-%s] SCHEMA_DLQ_PUBLISH_FAILED → 原消息重新入队", self.stage)
                if not getattr(message, "processed", False):
                    await message.nack(requeue=True)
        except Exception as e:
            logger.error(
                f"[BACKEND-{self.stage}] EXCEPTION "
                f"backend_task_id={event.backend_task_id if event else 'N/A'} "
                f"error={e}",
                exc_info=True,
            )
            # 所有未预期异常统一分类；未知异常不可重试，且必须补全 failure_reason。
            if event:
                classified = classify_exception(e)
                failure = ProcessResult(
                    success=False,
                    task_type=self.stage,
                    failure_reason=classified.failure_reason,
                    error=exception_summary(classified),
                    retryable=isinstance(classified, RetryableWorkerError),
                )
                await self._on_failure(message, event, failure)
            elif not getattr(message, "processed", False):
                await message.nack(requeue=False)

    # ===== 子类实现 =====

    @abstractmethod
    async def process(self, event: FileBackendEvent) -> ProcessResult:
        """执行业务处理逻辑"""
        ...

    # ===== 成功处理 =====

    async def _on_success(
        self, message: Any, event: FileBackendEvent, result: ProcessResult, elapsed: float
    ):
        """处理成功 → 更新事件状态为 completed → 更新总任务状态 → 发布下一阶段"""
        # 更新事件状态为 completed
        await self._set_event_status(event.backend_task_id, TaskStatus.COMPLETED)

        logger.info(
            f"[BACKEND-{self.stage}] SUCCESS "
            f"backend_task_id={event.backend_task_id} "
            f"pipeline_id={event.pipeline_id} "
            f"file_id={event.file_id} "
            f"elapsed={elapsed:.2f}s"
        )
        await worker_metrics.record(self.stage, "success")

        # 需求：原行为是先 ACK merge 再发布 hash，进程在两者之间退出会永久丢失下一阶段。
        # 新行为对 merge 先在 MySQL 同一事务写 Gate + ready/timeout Outbox，事务成功后再 ACK。
        # 其他阶段也调整为成功发布下一阶段后 ACK，保持原业务结果但收紧消息可靠性窗口。
        if self.stage == TaskTypes.MERGE:
            from app.services.file_preprocess_gate_service import file_preprocess_gate_service

            await file_preprocess_gate_service.open_after_merge(event, result.data)
            await self._update_master_status(
                event, TaskStatus.PROCESSING, TaskTypes.CONTENT_PREPROCESS
            )
            await self._set_named_event_status(
                event.backend_task_id, TaskTypes.CONTENT_PREPROCESS, TaskStatus.PROCESSING
            )
            await message.ack()
            return

        if self.stage == TaskTypes.HASH_CALCULATE and event.preprocess_gate_id:
            # 需求：candidate_checksum 只来自 Broker，final_checksum 必须由 Storage Hash
            # Worker 独立计算后回写 Gate，避免插件伪造摘要绕过后续安全扫描。
            from app.repositories.file_preprocess_repository import file_preprocess_repository

            await file_preprocess_repository.mark_final_checksum(
                event.preprocess_gate_id,
                str(result.data.get("checksum") or event.file_checksum),
                int(event.file_size),
            )

        next_stage = BACKEND_NEXT_STAGE.get(self.stage)
        if next_stage is None:
            # 流水线终点 (mark_active) → 更新总任务为 completed → 触发增强事件
            logger.info(
                f"[BACKEND-{self.stage}] PIPELINE_END "
                f"backend_task_id={event.backend_task_id} "
                f"→ 总任务完成，触发增强事件"
            )
            await self._update_master_status(event, TaskStatus.COMPLETED)
            await self._publish_enhance_events(event, result)
            # file.available 仍由 MarkActiveConsumer 按既有业务契约发布；Task Bus 不新增
            # activated 事实事件，避免把任务编排和广播事件混在同一条后台链路中。
            await message.ack()
            return

        # 更新总任务状态为 processing（记录当前阶段）
        await self._update_master_status(event, TaskStatus.PROCESSING, next_stage)

        # 构建下一阶段事件
        accumulated = {
            "file_id": event.file_id,
            "storage_path": result.data.get("storage_path", event.storage_path),
            "checksum": result.data.get("checksum", event.file_checksum),
            "file_size": result.data.get("file_size", event.file_size),
        }
        accumulated.update(result.data)

        next_event = event.with_next_stage(next_stage, accumulated)

        logger.info(
            f"[BACKEND-{self.stage}] → {next_stage} "
            f"backend_task_id={event.backend_task_id} "
            f"pipeline_id={event.pipeline_id}"
        )



        # REQ-WORKER-TASKBUS-2026-07：恢复任务总线编排。当前消费者完成本阶段后直接发布
        # 下一阶段 FileBackendEvent 到原任务队列；不新增事实事件监听适配层。
        await rabbitmq_service.publish_message(
            exchange_name=self.exchange,
            routing_key=BACKEND_STAGE_ROUTING_KEY[next_stage],
            message=next_event.to_dict(),
        )
        await message.ack()

    # ===== 失败处理 =====

    async def _on_failure(
        self, message: Any, event: FileBackendEvent, result: ProcessResult
    ):
        """处理失败 → 重试 / DLQ"""
        # 明确不可重试原因和未知异常立即进入 DLQ；正常阶段失败默认按历史策略重试。
        non_retryable_reasons = set(getattr(__import__("core.config", fromlist=["FailureReason"]), "FailureReason").NO_RETRY_REASONS)
        non_retryable = result.retryable is False or result.failure_reason in non_retryable_reasons
        if result.failure_reason in {"UNKNOWN", "MESSAGE_SCHEMA_ERROR", "UNEXPECTED_ERROR"}:
            non_retryable = True
        if non_retryable or event.retry_count >= self.max_retries:
            # 超过最大重试次数 → 更新事件状态为 failed → 更新总任务为 failed → 发布到 DLQ
            await self._set_event_status(event.backend_task_id, TaskStatus.FAILED)
            await self._update_master_status(event, TaskStatus.FAILED)
            # W-03：DLQ 发布确认前不得 ACK 原消息；Broker/网络异常时重新入队，
            # 避免“业务已失败但死信未落盘”造成消息丢失。
            try:
                await self._publish_to_dlq(event, result)
                await message.ack()
                await worker_metrics.record(self.stage, "dlq")
            except Exception:
                logger.exception(
                    "[BACKEND-%s] DLQ_PUBLISH_FAILED backend_task_id=%s → 原消息重新入队",
                    self.stage,
                    event.backend_task_id,
                )
                if not getattr(message, "processed", False):
                    await message.nack(requeue=True)
            return

        # 重置事件状态为未处理（允许重试消费）
        await self._reset_event_status(event.backend_task_id)

        # 指数退避重试
        delay = min(
            settings.retry_base_delay_seconds * (2 ** event.retry_count),
            settings.retry_max_delay_seconds,
        )
        logger.warning(
            f"[BACKEND-{self.stage}] RETRY "
            f"backend_task_id={event.backend_task_id} "
            f"pipeline_id={event.pipeline_id} "
            f"attempt={event.retry_count + 1}/{self.max_retries} "
            f"delay={delay}s "
            f"reason={result.failure_reason}"
        )

        retry_event = event.with_retry_increment()
        retry_event.failure_reason = result.failure_reason

        # Sprint 0 安全基线（生命周期可靠性）：
        # 原行为先 sleep、再 ACK、最后发布，Worker 退出时会造成重试丢失；新行为先把消息持久化到
        # 阶段专属 TTL 延迟队列，确认发布成功后再 ACK，原业务重试次数和退避时长保持不变。
        try:
            # W-02：先持久化到阶段专属 TTL retry 队列并等待 Broker 确认，再 ACK 原消息。
            await rabbitmq_service.publish_retry_message(
                exchange_name=self.exchange,
                routing_key=BACKEND_STAGE_ROUTING_KEY[self.stage],
                message=retry_event.to_dict(),
                delay_seconds=delay,
            )
            await message.ack()
            await worker_metrics.record(self.stage, "retry")
        except Exception:
            logger.exception(
                f"[BACKEND-{self.stage}] RETRY_PUBLISH_FAILED "
                f"backend_task_id={event.backend_task_id} → 原消息重新入队"
            )
            if not getattr(message, "processed", False):
                await message.nack(requeue=True)

    async def _publish_to_dlq(self, event: FileBackendEvent, result: ProcessResult):
        """发布到阶段专属死信队列"""
        dlq_routing_keys = {
            TaskTypes.MERGE: settings.file_backend_merge_dlq_routing_key,
            TaskTypes.HASH_CALCULATE: settings.file_backend_hash_dlq_routing_key,
            TaskTypes.VIRUS_SCAN: settings.file_backend_virus_dlq_routing_key,
            TaskTypes.MARK_ACTIVE: settings.file_backend_mark_active_dlq_routing_key,
        }

        dlq_event = event.with_retry_increment()
        dlq_event.failure_reason = result.failure_reason

        logger.error(
            f"[BACKEND-{self.stage}] → DLQ "
            f"backend_task_id={event.backend_task_id} "
            f"pipeline_id={event.pipeline_id} "
            f"reason={result.failure_reason}"
        )

        await rabbitmq_service.publish_to_dlq(
            exchange_name=self.dlx,
            routing_key=dlq_routing_keys.get(self.stage, "file.backend.dlq"),
            message=dlq_event.to_dict(),
        )

    # ===== 增强事件扇出 =====

    async def _publish_enhance_events(
        self, event: FileBackendEvent, result: ProcessResult
    ):
        """mark_active 成功后扇出增强事件（并发）"""
        from core.config import get_enhance_stages, ENHANCE_STAGE_ROUTING_KEY
        from core.event.file_enhance_event import FileEnhanceEvent

        enhance_stages = get_enhance_stages(event.file_type)
        if not enhance_stages:
            logger.info(
                f"[BACKEND-{self.stage}] NO_ENHANCE "
                f"file_type={event.file_type} "
                f"→ 无增强事件"
            )
            return

        # 生成增强任务 ID（所有增强阶段共享）
        enhance_task_id = FileEnhanceEvent.generate_enhance_task_id()

        accumulated = {
            "file_id": event.file_id,
            "storage_path": event.storage_path,
            "checksum": event.file_checksum,
            "file_size": event.file_size,
        }
        accumulated.update(result.data)

        # 初始化增强任务总状态
        await self._init_enhance_master(enhance_task_id, event, accumulated)

        tasks = []
        for stage_name in enhance_stages:
            enhance_event = FileEnhanceEvent(
                enhance_task_id=enhance_task_id,
                stage=stage_name,
                file_id=event.file_id,
                user_id=event.user_id,
                file_name=event.file_name,
                file_type=event.file_type,
                file_size=accumulated.get("file_size", event.file_size),
                storage_path=accumulated.get("storage_path", event.storage_path),
                node_id=event.node_id,
                file_checksum=accumulated.get("checksum", event.file_checksum),
                backend_task_id=event.backend_task_id,
                space_id=event.space_id,
                space_type=event.space_type,
                accumulated=accumulated,
            )
            rk = ENHANCE_STAGE_ROUTING_KEY[stage_name]

            logger.info(
                f"[BACKEND-{self.stage}] → ENHANCE {stage_name} "
                f"backend_task_id={event.backend_task_id} "
                f"enhance_task_id={enhance_task_id}"
            )

            tasks.append(
                rabbitmq_service.publish_message(
                    exchange_name=settings.file_enhance_exchange,
                    routing_key=rk,
                    message=enhance_event.to_dict(),
                )
            )

        await asyncio.gather(*tasks)

    # ===== Redis 事件状态管理 =====

    def _event_key(self, backend_task_id: str) -> str:
        return REDIS_BACKEND_EVENT_KEY.format(
            backend_task_id=backend_task_id, stage=self.stage
        )

    def _master_key(self, backend_task_id: str) -> str:
        return REDIS_BACKEND_MASTER_KEY.format(backend_task_id=backend_task_id)

    async def _get_event_status(self, backend_task_id: str) -> str | None:
        """获取事件状态: processing / completed / failed / None（不存在）"""
        try:
            from app.core.redis_client import redis_client
            return await redis_client.get(self._event_key(backend_task_id))
        except Exception:
            return None

    # 暂时不使用..... 因为先写后读可能回导致 未进行消息幂等检查 直接把事件状态改为处理中 如果消息幂等了呢？
    async def _claim_event(self, backend_task_id: str) -> bool:
        """使用短租约原子抢占阶段，避免 Task Bus 重投造成并发重复执行。"""
        try:
            from app.core.redis_client import redis_client

            key = self._event_key(backend_task_id)
            claimed = await redis_client.set(
                key,
                TaskStatus.PROCESSING,
                ex=max(EVENT_STATUS_TTL, settings.enhance_processing_lease_seconds),
                nx=True,
            )
            return bool(claimed)
        except Exception as exc:
            logger.warning("[BACKEND-%s] Redis 原子幂等不可用，降级业务事务: %s", self.stage, exc)
            return True

    async def _set_event_status(self, backend_task_id: str, status: str):
        """设置事件状态（带 TTL）"""
        try:
            from app.core.redis_client import redis_client
            key = self._event_key(backend_task_id)
            await redis_client.setex(key, EVENT_STATUS_TTL, status)
            logger.debug(
                f"[BACKEND-{self.stage}] EVENT_STATUS "
                f"key={key} → {status}"
            )
        except Exception as e:
            logger.error(f"设置事件状态失败: {e}")

    async def _set_named_event_status(
        self, backend_task_id: str, stage: str, status: str
    ):
        """为不对应 BaseBackendConsumer 子类的等待闸门写入 UI 兼容投影。"""
        try:
            from app.core.redis_client import redis_client
            key = REDIS_BACKEND_EVENT_KEY.format(
                backend_task_id=backend_task_id, stage=stage
            )
            await redis_client.setex(key, EVENT_STATUS_TTL, status)
        except Exception as e:
            logger.error(f"设置等待闸门状态失败: {e}")

    async def _reset_event_status(self, backend_task_id: str):
        """重置事件状态（删除键，表示未处理）"""
        try:
            from app.core.redis_client import redis_client
            await redis_client.delete(self._event_key(backend_task_id))
        except Exception as e:
            logger.error(f"重置事件状态失败: {e}")

    async def _can_proceed(self, backend_task_id: str) -> bool:
        """
        检查是否可以进入当前阶段（上一阶段必须已完成）
        对于 merge 阶段（第一个阶段），始终允许
        """
        stages = BACKEND_PIPELINE
        try:
            idx = stages.index(self.stage)
        except ValueError:
            return True

        if idx == 0:
            return True  # merge 是第一个阶段

        prev_stage = stages[idx - 1]
        if self.stage == TaskTypes.HASH_CALCULATE and prev_stage == TaskTypes.CONTENT_PREPROCESS:
            # 需求：核心生命周期不能再由 Redis-only 状态决定。Hash 只接受已由 DB CAS
            # 选择 candidate/original 的闸门，Redis 此时仅供前端展示进度。
            try:
                from app.repositories.file_preprocess_repository import (
                    file_preprocess_repository,
                )
                return await file_preprocess_repository.is_gate_closed(backend_task_id)
            except Exception as exc:
                logger.error(
                    "[BACKEND-HASH] PREPROCESS_GATE_CHECK_FAILED backend_task_id=%s error=%s",
                    backend_task_id,
                    exc,
                )
                return False
        prev_key = REDIS_BACKEND_EVENT_KEY.format(
            backend_task_id=backend_task_id, stage=prev_stage
        )
        try:
            from app.core.redis_client import redis_client
            status = await redis_client.get(prev_key)
            return status == TaskStatus.COMPLETED
        except Exception:
            return True  # Redis 不可用时放行

    # ===== 总任务状态管理 =====

    async def _update_master_status(
        self, event: FileBackendEvent, status: str, next_stage: str | None = None
    ):
        """更新后台任务总状态"""
        try:
            from app.core.redis_client import redis_client
            key = self._master_key(event.backend_task_id)
            mapping = {
                "status": status,
                "current_stage": next_stage or self.stage,
                "updated_at": datetime.now(timezone.utc).isoformat(),
            }
            await redis_client.hset(key, mapping=mapping)
            await redis_client.expire(key, MASTER_TASK_TTL)
            logger.info(
                f"[BACKEND-MASTER] backend_task_id={event.backend_task_id} "
                f"status={status} "
                f"current_stage={next_stage or self.stage}"
            )
        except Exception as e:
            logger.error(f"更新总任务状态失败: {e}")

    async def _init_enhance_master(
        self, enhance_task_id: str, event: FileBackendEvent, accumulated: dict
    ):
        """初始化增强任务总状态"""
        try:
            from app.core.redis_client import redis_client
            from core.config import REDIS_ENHANCE_MASTER_KEY
            key = REDIS_ENHANCE_MASTER_KEY.format(enhance_task_id=enhance_task_id)
            mapping = {
                "enhance_task_id": enhance_task_id,
                "backend_task_id": event.backend_task_id,
                "file_id": event.file_id,
                "user_id": event.user_id,
                "file_name": event.file_name,
                "status": TaskStatus.PROCESSING,
                "created_at": datetime.now(timezone.utc).isoformat(),
                "updated_at": datetime.now(timezone.utc).isoformat(),
            }
            await redis_client.hset(key, mapping=mapping)
            await redis_client.expire(key, MASTER_TASK_TTL)
        except Exception as e:
            logger.error(f"初始化增强任务总状态失败: {e}")


# 导出流水线常量供子类使用。
# 需求：content_preprocess 是 merge 与最终 hash 之间的持久化等待阶段。
BACKEND_PIPELINE = [
    TaskTypes.MERGE,
    TaskTypes.CONTENT_PREPROCESS,
    TaskTypes.HASH_CALCULATE,
    TaskTypes.VIRUS_SCAN,
    TaskTypes.MARK_ACTIVE,
]
