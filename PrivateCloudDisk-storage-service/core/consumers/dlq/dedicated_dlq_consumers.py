"""阶段专属死信消费者。

需求编号：W-01、W-08。

原行为：多个 backend/enhance DLQ 共用一个回调，再按 `stage/task_type` 进行大分支。
新行为：每个 DLQ 在 Worker 注册表中绑定一个独立实例；这些小类只固定来源队列和策略，
业务处理仍复用已经审计过的旧策略函数，避免删除原有注释和日志字段。
"""
from __future__ import annotations

import logging
import uuid
from typing import Any, Callable

from core.config import settings, FailureReason
from core.consumers.dlq.base import BaseDLQConsumer
from core.rabbitmq import rabbitmq_service

logger = logging.getLogger("dedicated_dlq_consumers")


# REQ-WORKER-TASKBUS-2026-07：backend/enhancement 阶段 DLQ 已统一由 Worker 注册表绑定
# `backend_dlq_consumer` / `enhance_dlq_consumer`，删除原来按阶段包装同一基类的重复入口。
# 本文件仅保留内容预处理 fail-open、文件域重放和安全隔离等非 Task Bus 专属策略。


class FileContentReadyDeadLetterConsumer(BaseDLQConsumer):
    """ready 事件故障时的 fail-open 专属消费者。"""

    def _get_dlq_source_name(self) -> str:
        return "file_content.ready"

    def _get_handler(self, failure_reason: str) -> Callable:
        return self._fallback

    async def _fallback(self, data: dict[str, Any]) -> bool:
        from app.services.file_preprocess_gate_service import file_preprocess_gate_service
        from core.event.file_content_event import FileLifecycleEvent

        event = FileLifecycleEvent(
            id=str(uuid.uuid4()),
            type="pcd.file.content.ready.dlq.fallback.v1",
            subject=f"preprocess-gates/{data.get('data', {}).get('gate_id', '')}",
            actor_user_id=str(data.get("actor_user_id") or ""),
            space_id=data.get("space_id"),
            data=data.get("data") or data,
            causation_id=str(data.get("id") or ""),
        ).to_dict()
        result = await file_preprocess_gate_service.fallback_from_event(
            event,
            reason="PREPROCESS_READY_DLQ",
            event_type="pcd.file.content.ready.dlq.fallback.v1",
        )
        logger.critical(
            "[CONTENT-READY-DLQ] FAIL_OPEN gate_id=%s outcome=%s",
            (event.get("data") or {}).get("gate_id"),
            result.get("outcome"),
        )
        return True


class FileContentProcessedDeadLetterConsumer(BaseDLQConsumer):
    """processed 事件故障时的 Gate CAS fail-open 专属消费者。"""

    def _get_dlq_source_name(self) -> str:
        return "file_content.processed"

    def _get_handler(self, failure_reason: str) -> Callable:
        return self._fallback

    async def _fallback(self, data: dict[str, Any]) -> bool:
        # 复用同一个 Gate CAS 事实源；不能调用原消息的 ack 两次，这里只处理业务并返回。
        from app.services.file_preprocess_gate_service import file_preprocess_gate_service
        from core.event.file_content_event import FileLifecycleEvent

        raw = data
        event = FileLifecycleEvent(
            id=str(uuid.uuid4()),
            type="pcd.file.content.processed.dlq.fallback.v1",
            subject=f"preprocess-gates/{(data.get('data') or {}).get('gate_id', '')}",
            actor_user_id=str(data.get("actor_user_id") or ""),
            space_id=data.get("space_id"),
            data=data.get("data") or data,
            causation_id=str(data.get("id") or ""),
        ).to_dict()
        result = await file_preprocess_gate_service.fallback_from_event(
            event,
            reason="PREPROCESS_PROCESSED_DLQ",
            event_type="pcd.file.content.processed.dlq.fallback.v1",
        )
        logger.critical("[CONTENT-PROCESSED-DLQ] FAIL_OPEN outcome=%s", result.get("outcome"))
        return True


class _ReplayDeadLetterConsumer(BaseDLQConsumer):
    """需要人工/自动补发的领域 DLQ 基类。"""

    source_name = ""
    exchange_name = ""
    routing_key = ""
    max_replays = 3
    retry_supported = False

    def _get_dlq_source_name(self) -> str:
        return self.source_name

    def _get_handler(self, failure_reason: str) -> Callable:
        return self._replay

    async def _replay(self, data: dict[str, Any]) -> bool:
        count = max(0, int(data.get("dlq_replay_count") or 0))
        if count >= self.max_replays:
            await self._log_dlq_action(
                data,
                "REPLAY_EXHAUSTED",
                f"{self.source_name} 自动重放次数耗尽，等待人工处理",
                source=self.source_name,
            )
            return True
        data = dict(data)
        data["dlq_replay_count"] = count + 1
        delay = min(300, 5 * (2**count))
        # W-08：重放必须进入原队列的持久化 retry 队列；直接给主队列设置
        # expiration 只会在到期后进入主队列 DLX，无法形成“延迟后执行”的语义。
        if self.retry_supported:
            await rabbitmq_service.publish_retry_message(
                exchange_name=self.exchange_name,
                routing_key=self.routing_key,
                message=data,
                delay_seconds=delay,
            )
        else:
            # 当前事件没有声明 retry 路由时立即重放；这属于该 DLQ 策略的显式降级行为，
            # 不会重新创建或依赖已移除的旧 retry 拓扑。
            await rabbitmq_service.publish_message(
                exchange_name=self.exchange_name,
                routing_key=self.routing_key,
                message=data,
            )
        await self._log_dlq_action(
            data,
            "REPLAY",
            f"已重放第 {count + 1} 次",
            source=self.source_name,
        )
        return True


class FileDeleteDeadLetterConsumer(_ReplayDeadLetterConsumer):
    source_name = "file_delete"
    exchange_name = settings.file_delete_exchange
    routing_key = settings.file_delete_routing_key
    retry_supported = True


class FileEventDeadLetterConsumer(_ReplayDeadLetterConsumer):
    source_name = "file_event"
    exchange_name = settings.file_event_exchange

    async def _replay(self, data: dict[str, Any]) -> bool:
        self.routing_key = str(data.get("routing_key") or data.get("event_type") or settings.file_available_routing_key)
        return await super()._replay(data)


class SecurityQuarantineConsumer(BaseDLQConsumer):
    """安全隔离队列独立入口，不与普通 backend DLQ 混用。"""

    def _get_dlq_source_name(self) -> str:
        return "security_quarantine"

    def _get_handler(self, failure_reason: str) -> Callable:
        return self._quarantine

    async def _quarantine(self, data: dict[str, Any]) -> bool:
        await self._log_dlq_action(
            data,
            "SECURITY_QUARANTINED",
            "安全隔离消息已登记，禁止自动重试",
            source="security_quarantine",
        )
        logger.critical(
            "[SECURITY-QUARANTINE] file_id=%s reason=%s",
            data.get("file_id"),
            data.get("failure_reason") or FailureReason.UNKNOWN,
        )
        return True


def _entry(consumer: BaseDLQConsumer):
    async def callback(message: Any):
        await consumer.handle(message)
    return callback


# 每个实例只绑定一个来源，Worker 注册表引用下列独立 callback。
on_file_content_ready_dlq_message = _entry(FileContentReadyDeadLetterConsumer())
on_file_content_processed_dedicated_dlq_message = _entry(FileContentProcessedDeadLetterConsumer())
on_file_delete_dlq_message = _entry(FileDeleteDeadLetterConsumer())
on_file_event_dlq_message = _entry(FileEventDeadLetterConsumer())
on_security_quarantine_message = _entry(SecurityQuarantineConsumer())
