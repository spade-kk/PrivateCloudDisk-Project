"""
文件后台处理消费者基类

供 merge / hash / virus / mark_active 四个阶段消费者复用。

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

            # 2. 幂等检查（backend_task_id + stage）
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

            # 标记事件状态为 processing
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
                    f"elapsed={elapsed:.2f}s"
                )
                await self._on_failure(message, event, result)

        except json.JSONDecodeError:
            logger.error(
                f"[BACKEND-{self.stage}] JSON_PARSE_ERROR → 丢弃"
            )
            await message.ack()
        except Exception as e:
            logger.error(
                f"[BACKEND-{self.stage}] EXCEPTION "
                f"backend_task_id={event.backend_task_id if event else 'N/A'} "
                f"error={e}",
                exc_info=True,
            )
            # 未预期异常 → 更新事件状态为 failed → 进入 DLQ
            if event:
                await self._set_event_status(event.backend_task_id, TaskStatus.FAILED)
                await self._update_master_status(event, TaskStatus.FAILED)
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

        await message.ack()

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
        routing_key = BACKEND_STAGE_ROUTING_KEY[next_stage]

        logger.info(
            f"[BACKEND-{self.stage}] → {next_stage} "
            f"backend_task_id={event.backend_task_id} "
            f"pipeline_id={event.pipeline_id}"
        )

        await rabbitmq_service.publish_message(
            exchange_name=self.exchange,
            routing_key=routing_key,
            message=next_event.to_dict(),
        )

    # ===== 失败处理 =====

    async def _on_failure(
        self, message: Any, event: FileBackendEvent, result: ProcessResult
    ):
        logger.warning(f"{event.retry_count}:{self.max_retries}")
        """处理失败 → 重试 / DLQ"""
        if event.retry_count >= self.max_retries:
            # 超过最大重试次数 → 更新事件状态为 failed → 更新总任务为 failed → 发布到 DLQ
            await self._set_event_status(event.backend_task_id, TaskStatus.FAILED)
            await self._update_master_status(event, TaskStatus.FAILED)
            await self._publish_to_dlq(event, result)
            await message.ack()
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

        await asyncio.sleep(delay)
        await message.ack()

        retry_event = event.with_retry_increment()
        retry_event.failure_reason = result.failure_reason

        routing_key = BACKEND_STAGE_ROUTING_KEY[self.stage]
        await rabbitmq_service.publish_message(
            exchange_name=self.exchange,
            routing_key=routing_key,
            message=retry_event.to_dict(),
        )

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


# 导出流水线常量供子类使用
BACKEND_PIPELINE = [TaskTypes.MERGE, TaskTypes.HASH_CALCULATE, TaskTypes.VIRUS_SCAN, TaskTypes.MARK_ACTIVE]