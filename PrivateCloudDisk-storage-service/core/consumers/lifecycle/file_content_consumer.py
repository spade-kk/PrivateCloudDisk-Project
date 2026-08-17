"""file.content.processed / timeout / DLQ 消费者。

需求来源：文件生命周期事件扩展。
三个入口最终调用同一个 Gate CAS；任何一个入口先关闭 OPEN Gate 后，其他迟到消息都会
被标记为 IGNORED 并 ACK，因此只会产生一条 hash Outbox。
"""
from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime, timezone
from typing import Any

from app.services.file_preprocess_gate_service import file_preprocess_gate_service
from core.config import (
    EVENT_STATUS_TTL,
    MASTER_TASK_TTL,
    REDIS_BACKEND_EVENT_KEY,
    REDIS_BACKEND_MASTER_KEY,
    TaskStatus,
    TaskTypes,
    settings,
)
from core.rabbitmq import rabbitmq_service

logger = logging.getLogger("file_content_consumer")


async def _update_progress_projection(raw: dict[str, Any], outcome: dict[str, Any]) -> None:
    """更新 Redis UI 投影；失败不影响数据库事实源与 hash Outbox。"""
    if outcome.get("outcome") not in {"selected", "fallback"}:
        return
    data = raw.get("data") or {}
    backend_task_id = str(data.get("backend_task_id") or "")
    if not backend_task_id:
        return
    try:
        from app.core.redis_client import redis_client

        event_key = REDIS_BACKEND_EVENT_KEY.format(
            backend_task_id=backend_task_id,
            stage=TaskTypes.CONTENT_PREPROCESS,
        )
        master_key = REDIS_BACKEND_MASTER_KEY.format(backend_task_id=backend_task_id)
        await redis_client.setex(event_key, EVENT_STATUS_TTL, TaskStatus.COMPLETED)
        await redis_client.hset(
            master_key,
            mapping={
                "status": TaskStatus.PROCESSING,
                "current_stage": TaskTypes.HASH_CALCULATE,
                "preprocess_status": outcome.get("preprocess_status", "fallback_unavailable"),
                "content_modified": "1" if outcome.get("content_modified") else "0",
                "updated_at": datetime.now(timezone.utc).isoformat(),
            },
        )
        await redis_client.expire(master_key, MASTER_TASK_TTL)
    except Exception:
        logger.exception(
            "[PREPROCESS-PROGRESS] Redis 投影更新失败 backend_task_id=%s",
            backend_task_id,
        )


async def on_file_content_processed_message(message: Any) -> None:
    """Automation 正常结果入口。异常消息进入专属 DLQ，由 DLQ 消费者降级继续。"""
    try:
        raw = json.loads(message.body.decode("utf-8"))
        outcome = await file_preprocess_gate_service.handle_processed_event(raw)
        if outcome["outcome"] == "invalid":
            logger.error(
                "[CONTENT-PROCESSED] SECURITY_REJECT event_id=%s reason=%s",
                raw.get("id"),
                outcome.get("reason"),
            )
        elif outcome["outcome"] == "late":
            logger.warning(
                "[CONTENT-PROCESSED] LATE_RESULT_IGNORED event_id=%s gate_id=%s",
                raw.get("id"),
                outcome.get("gate_id"),
            )
        await _update_progress_projection(raw, outcome)
        await message.ack()
    except (json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
        # 契约错误属于不可重试消息，但必须进入 processed DLQ 以触发可识别 gate 的降级。
        logger.error("[CONTENT-PROCESSED] INVALID_MESSAGE error=%s", exc, exc_info=True)
        await message.nack(requeue=False)
    except Exception as exc:
        logger.exception("[CONTENT-PROCESSED] CONSUME_FAILED")
        # 需求四-3：5s/30s/120s 固定 TTL 退避，不在消费者内 asyncio.sleep。
        # 发布重试失败时 NACK 到 DLQ；processed DLQ 与 DB sweeper 均会 fail-open。
        try:
            raw = json.loads(message.body.decode("utf-8"))
            delays = [
                int(value.strip())
                for value in settings.file_content_processed_retry_delays_seconds.split(",")
                if value.strip()
            ]
            retry_count = int(raw.get("lifecycle_retry_count", 0))
            if retry_count < len(delays):
                attempt = retry_count + 1
                retry_event = {
                    **raw,
                    "lifecycle_retry_count": attempt,
                    "last_failure_summary": str(exc)[:500],
                }
                await rabbitmq_service.publish_message(
                    settings.file_lifecycle_exchange,
                    f"{settings.file_content_processed_routing_key}.retry.{attempt}",
                    retry_event,
                )
                await message.ack()
                return
        except Exception:
            logger.exception("[CONTENT-PROCESSED] RETRY_PUBLISH_FAILED")
        await message.nack(requeue=False)


async def on_file_content_timeout_message(message: Any) -> None:
    """Rabbit TTL sentinel 入口；Automation 完全不可用时也能推进文件。"""
    try:
        raw = json.loads(message.body.decode("utf-8"))
        outcome = await file_preprocess_gate_service.fallback_from_event(
            raw,
            reason="PREPROCESS_TIMEOUT",
            event_type="pcd.file.content.timeout.v1",
        )
        await _update_progress_projection(raw, outcome)
        await message.ack()
    except Exception:
        logger.exception("[CONTENT-TIMEOUT] FALLBACK_FAILED")
        await message.nack(requeue=False)


async def on_file_content_processed_dlq_message(message: Any) -> None:
    """processed DLQ 逃生入口。

    原 processed event_id 可能已经留下 FAILED Inbox，因此这里生成新的 Storage 补偿
    event_id，复用 gate_id 并走同一个 CAS。人工重放不能覆盖已经关闭的 Gate。
    """
    try:
        original = json.loads(message.body.decode("utf-8"))
        data = dict(original.get("data") or {})
        compensation = {
            **original,
            "id": str(uuid.uuid4()),
            "type": "pcd.file.content.processed.dlq.fallback.v1",
            "causation_id": original.get("id", ""),
            "data": data,
        }
        outcome = await file_preprocess_gate_service.fallback_from_event(
            compensation,
            reason="PREPROCESS_PROCESSED_DLQ",
            event_type="pcd.file.content.processed.dlq.fallback.v1",
        )
        await _update_progress_projection(compensation, outcome)
        logger.critical(
            "[CONTENT-PROCESSED-DLQ] FAIL_OPEN gate_id=%s backend_task_id=%s outcome=%s",
            data.get("gate_id"),
            data.get("backend_task_id"),
            outcome.get("outcome"),
        )
        await message.ack()
    except Exception:
        logger.exception("[CONTENT-PROCESSED-DLQ] FALLBACK_FAILED")
        # 保留在 DLQ 中等待运维恢复，DB sweeper 仍会作为第二条逃生路径。
        await message.nack(requeue=True)
