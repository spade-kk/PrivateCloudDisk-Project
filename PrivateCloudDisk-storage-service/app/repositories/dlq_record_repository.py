"""MQ 死信处置记录持久层。"""
from __future__ import annotations

import json
import uuid
from typing import Any

from app.db.database import get_database_pool


class DLQRecordRepository:
    @staticmethod
    def _uuid_or_none(value: Any) -> str | None:
        """只把合法 UUID 传给 UUID_TO_BIN，避免残缺死信再次触发数据库异常。"""
        try:
            return str(uuid.UUID(str(value))) if value else None
        except (ValueError, TypeError, AttributeError):
            return None

    async def record(self, *, source_queue: str, stage: str, payload: dict[str, Any], failure_reason: str, error: str = "") -> None:
        """幂等写入死信；同一队列、任务和阶段重复到达时累计重试次数。"""
        pool = await get_database_pool()
        # AUDIT FIX [7.4]（需求一-2）:
        # 增强事件使用 enhance_task_id，旧实现未读取该字段，所有同阶段记录会以空 task_id 冲突合并。
        task_id = str(
            payload.get("task_id")
            or payload.get("enhance_task_id")
            or payload.get("backend_task_id")
            or payload.get("message_id")
            or payload.get("event_id")
            or "unidentified"
        )
        event_id = str(payload.get("event_id") or payload.get("message_id") or "") or None
        file_id = self._uuid_or_none(payload.get("file_id"))
        user_id = self._uuid_or_none(payload.get("user_id"))
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                # AUDIT FIX [7.4]: 死信处置写入可查询的数据库台账，Redis 仅保留短期运维视图。
                await cursor.execute("""
                    INSERT INTO pcd_mq_dead_letter_record_table (
                        event_id, task_id, file_id, user_id, source_queue, process_stage,
                        failure_reason, retry_count, payload_json, last_error
                    ) VALUES (%s, %s, UUID_TO_BIN(%s), UUID_TO_BIN(%s), %s, %s, %s, 1, CAST(%s AS JSON), %s)
                    AS incoming
                    ON DUPLICATE KEY UPDATE
                        -- AUDIT FIX [7.4]（需求一-1）:
                        -- 原行为在 MySQL 8.0.19+ 的 INSERT 行别名 incoming 与目标表同时包含 retry_count 时，
                        -- 右值 retry_count 无法判定来源并触发 1052；新行为显式读取目标表当前累计值。
                        retry_count=pcd_mq_dead_letter_record_table.retry_count+1,
                        payload_json=incoming.payload_json, failure_reason=incoming.failure_reason,
                        last_error=incoming.last_error, process_status='open', last_seen_at=CURRENT_TIMESTAMP(3)
                """, (
                    event_id, task_id, file_id, user_id, source_queue, stage, failure_reason,
                    json.dumps(payload, ensure_ascii=False), error[:2000],
                ))
            await conn.commit()

    async def update_disposition(
        self,
        *,
        source_queue: str,
        stage: str,
        payload: dict[str, Any],
        status: str,
        note: str,
    ) -> None:
        """更新自动恢复结果，形成 open → retrying/resolved/discarded 的可审计状态机。"""
        task_id = str(
            payload.get("task_id")
            or payload.get("enhance_task_id")
            or payload.get("backend_task_id")
            or payload.get("message_id")
            or payload.get("event_id")
            or "unidentified"
        )
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                # AUDIT FIX [7.4]（需求一-3）: 状态值由消费者内部常量产生，不接受外部请求透传。
                await cursor.execute(
                    """
                    UPDATE pcd_mq_dead_letter_record_table
                    SET process_status=%s, resolution_note=%s,
                        resolved_at=IF(%s IN ('resolved', 'discarded'), CURRENT_TIMESTAMP(3), NULL)
                    WHERE source_queue=%s AND task_id=%s AND process_stage=%s
                    """,
                    (status, note[:1000], status, source_queue, task_id, stage),
                )
            await conn.commit()


dlq_record_repository = DLQRecordRepository()
