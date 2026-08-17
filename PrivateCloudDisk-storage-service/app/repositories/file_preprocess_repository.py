"""文件内容预处理闸门、Inbox 与 Outbox 的 MySQL 持久层。

需求来源：插件生态与自动化工作流平台 / 文件生命周期事件扩展。
这里的事务边界用于保证“闸门终态”和“继续 hash 的命令”同时提交。任何消费者都不得
绕过本 Repository 直接发布 hash，否则 MQ 至少一次投递可能让流水线被推进两次。
"""
from __future__ import annotations

import hashlib
import json
import secrets
from datetime import datetime, timedelta, timezone
from typing import Any

from app.db.database import get_database_pool


def candidate_matches_processed_result(
    gate: dict[str, Any],
    *,
    requested_status: str,
    requested_modified: bool,
    candidate_id: str | None,
    candidate_checksum: str | None,
    candidate_size: int | None,
) -> bool:
    """验证 Automation 摘要是否与受信 Broker 登记的候选对象完全一致。"""
    return (
        requested_status == "success"
        and requested_modified
        and bool(candidate_id)
        and candidate_id == gate.get("candidate_id")
        and bool(gate.get("candidate_locator"))
        and candidate_checksum == gate.get("candidate_checksum")
        and int(candidate_size or -1) == int(gate.get("candidate_size") or -2)
    )


class FilePreprocessRepository:
    """Storage 预处理闸门数据库访问。"""

    async def create_gate_with_outbox(
        self,
        *,
        gate_id: str,
        ready_event_id: str,
        backend_event: dict[str, Any],
        content_lease_hash: str,
        original_locator: str,
        upload_checksum: str,
        original_size: int,
        deadline_at: datetime,
        ready_outbox_id: str,
        ready_event: dict[str, Any],
        timeout_outbox_id: str,
        timeout_event: dict[str, Any],
        lifecycle_exchange: str,
        ready_routing_key: str,
        timeout_schedule_routing_key: str,
    ) -> bool:
        """同一事务创建 Gate、ready Outbox 与 timeout sentinel Outbox。

        返回 False 表示同一 backend_task_id 已经创建过闸门，此时调用方应把 merge 消息
        当作幂等重投 ACK，不能重复发布 ready。
        """
        pool = await get_database_pool()
        gate_sql = """
            INSERT IGNORE INTO pcd_file_preprocess_gate (
                gate_id, ready_event_id, backend_task_id, pipeline_id,
                file_id, user_id, space_id, uploads_id, file_name, file_type,
                continuation_json, content_lease_hash, original_locator,
                upload_checksum, original_size,
                status, deadline_at
            ) VALUES (
                UUID_TO_BIN(%s), UUID_TO_BIN(%s), %s, %s,
                UUID_TO_BIN(%s), UUID_TO_BIN(%s), UUID_TO_BIN(NULLIF(%s, '')),
                %s, %s, %s, CAST(%s AS JSON), %s, %s, %s, %s, 'OPEN', %s
            )
        """
        outbox_sql = """
            INSERT INTO pcd_storage_outbox (
                outbox_id, aggregate_type, aggregate_id, event_type,
                exchange_name, routing_key, payload_json, message_status, available_at
            ) VALUES (
                UUID_TO_BIN(%s), 'FILE_PREPROCESS_GATE', UUID_TO_BIN(%s), %s,
                %s, %s, CAST(%s AS JSON), 'PENDING', CURRENT_TIMESTAMP(3)
            )
        """
        async with pool.acquire() as conn:
            try:
                async with conn.cursor() as cursor:
                    await cursor.execute(
                        gate_sql,
                        (
                            gate_id,
                            ready_event_id,
                            backend_event["backend_task_id"],
                            backend_event["pipeline_id"],
                            backend_event["file_id"],
                            backend_event["user_id"],
                            backend_event.get("space_id", ""),
                            backend_event.get("uploads_id", ""),
                            backend_event["file_name"],
                            backend_event.get("file_type") or "application/octet-stream",
                            json.dumps(backend_event, ensure_ascii=False),
                            content_lease_hash,
                            original_locator,
                            upload_checksum,
                            original_size,
                            deadline_at,
                        ),
                    )
                    if cursor.rowcount == 0:
                        await conn.rollback()
                        return False
                    await cursor.execute(
                        outbox_sql,
                        (
                            ready_outbox_id,
                            gate_id,
                            ready_event["type"],
                            lifecycle_exchange,
                            ready_routing_key,
                            json.dumps(ready_event, ensure_ascii=False),
                        ),
                    )
                    await cursor.execute(
                        outbox_sql,
                        (
                            timeout_outbox_id,
                            gate_id,
                            timeout_event["type"],
                            lifecycle_exchange,
                            timeout_schedule_routing_key,
                            json.dumps(timeout_event, ensure_ascii=False),
                        ),
                    )
                await conn.commit()
                return True
            except Exception:
                await conn.rollback()
                raise

    async def register_candidate(
        self,
        *,
        gate_id: str,
        candidate_id: str,
        candidate_locator: str,
        checksum: str,
        size: int,
    ) -> bool:
        """由受信 Storage Broker 登记候选对象，Automation 不能直接写 locator。"""
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    UPDATE pcd_file_preprocess_gate
                    SET candidate_id=%s, candidate_locator=%s, candidate_checksum=%s,
                        candidate_size=%s, row_version=row_version+1
                    WHERE gate_id=UUID_TO_BIN(%s) AND status='OPEN'
                    """,
                    (candidate_id, candidate_locator, checksum, size, gate_id),
                )
                updated = cursor.rowcount == 1
            await conn.commit()
        return updated

    async def authorize_content_lease(
        self, gate_id: str, execution_id: str, content_lease_hash: str
    ) -> dict[str, Any] | None:
        """校验 Runtime 的一次性内容 Lease；只向 Storage 内部 Broker 返回物理 locator。"""
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    SELECT BIN_TO_UUID(gate_id) gate_id, backend_task_id,
                           original_locator, original_size, upload_checksum,
                           candidate_id, candidate_locator, deadline_at
                    FROM pcd_file_preprocess_gate
                    WHERE gate_id=UUID_TO_BIN(%s)
                      AND runtime_lease_execution_id=%s
                      AND runtime_lease_hash=%s
                      AND runtime_lease_expires_at > CURRENT_TIMESTAMP(3)
                      AND status='OPEN'
                      AND deadline_at > CURRENT_TIMESTAMP(3)
                    """,
                    (gate_id, execution_id, content_lease_hash),
                )
                return await cursor.fetchone()

    async def exchange_content_lease(
        self,
        *,
        gate_id: str,
        execution_id: str,
        content_lease_ref_hash: str,
        requested_ttl_seconds: int,
    ) -> dict[str, Any] | None:
        """把 MQ 中的一次性引用兑换为绑定 execution_id 的短期 Runtime Lease。

        兑换成功后立即让引用失效。即使 MQ 消息泄露或重放，也不能再次换取文件内容。
        """
        pool = await get_database_pool()
        runtime_lease = secrets.token_urlsafe(48)
        runtime_hash = hashlib.sha256(runtime_lease.encode("utf-8")).hexdigest()
        invalidated_ref_hash = hashlib.sha256(secrets.token_bytes(48)).hexdigest()
        async with pool.acquire() as conn:
            try:
                async with conn.cursor() as cursor:
                    await cursor.execute(
                        """
                        SELECT deadline_at
                        FROM pcd_file_preprocess_gate
                        WHERE gate_id=UUID_TO_BIN(%s)
                          AND content_lease_hash=%s
                          AND runtime_lease_hash IS NULL
                          AND status='OPEN'
                          AND deadline_at > CURRENT_TIMESTAMP(3)
                        FOR UPDATE
                        """,
                        (gate_id, content_lease_ref_hash),
                    )
                    gate = await cursor.fetchone()
                    if not gate:
                        await conn.rollback()
                        return None
                    now = datetime.now(timezone.utc).replace(tzinfo=None)
                    deadline = gate["deadline_at"]
                    ttl_seconds = max(1, min(int(requested_ttl_seconds), 120))
                    expires_at = min(
                        deadline,
                        now + timedelta(seconds=ttl_seconds),
                    )
                    await cursor.execute(
                        """
                        UPDATE pcd_file_preprocess_gate
                        SET content_lease_hash=%s,
                            runtime_lease_hash=%s,
                            runtime_lease_execution_id=%s,
                            runtime_lease_expires_at=%s,
                            row_version=row_version+1
                        WHERE gate_id=UUID_TO_BIN(%s)
                          AND status='OPEN'
                          AND runtime_lease_hash IS NULL
                        """,
                        (
                            invalidated_ref_hash,
                            runtime_hash,
                            execution_id,
                            expires_at,
                            gate_id,
                        ),
                    )
                    if cursor.rowcount != 1:
                        await conn.rollback()
                        return None
                await conn.commit()
                return {
                    "execution_lease": runtime_lease,
                    "expires_at": expires_at.isoformat(),
                }
            except Exception:
                await conn.rollback()
                raise

    async def finalize_from_processed(
        self,
        *,
        event_id: str,
        gate_id: str,
        ready_event_id: str,
        backend_task_id: str,
        result_status: str,
        content_modified: bool,
        candidate_id: str | None,
        candidate_checksum: str | None,
        candidate_size: int | None,
        failure_code: str | None,
        failure_summary: str | None,
        raw_event: dict[str, Any],
        backend_exchange: str,
        hash_routing_key: str,
    ) -> dict[str, Any]:
        """消费 processed 事件并恰好一次地产生 hash Outbox。

        返回 ``outcome``：
        - selected/fallback：本次关闭了 OPEN 闸门；
        - duplicate：同 event_id 已处理；
        - late：闸门已经由其他路径关闭；
        - invalid：ready/backend 关联不匹配，调用方应记录安全告警。
        """
        return await self._close_gate_and_enqueue_hash(
            event_id=event_id,
            gate_id=gate_id,
            expected_ready_event_id=ready_event_id,
            expected_backend_task_id=backend_task_id,
            requested_status=result_status,
            requested_modified=content_modified,
            candidate_id=candidate_id,
            candidate_checksum=candidate_checksum,
            candidate_size=candidate_size,
            failure_code=failure_code,
            failure_summary=failure_summary,
            raw_event=raw_event,
            backend_exchange=backend_exchange,
            hash_routing_key=hash_routing_key,
            event_type="pcd.file.content.processed.v1",
        )

    async def fallback_and_continue(
        self,
        *,
        event_id: str,
        gate_id: str,
        reason: str,
        failure_summary: str,
        raw_event: dict[str, Any],
        backend_exchange: str,
        hash_routing_key: str,
        event_type: str,
    ) -> dict[str, Any]:
        """超时、DLQ 与定时扫描共用的 fail-open 原子操作。"""
        return await self._close_gate_and_enqueue_hash(
            event_id=event_id,
            gate_id=gate_id,
            expected_ready_event_id=None,
            expected_backend_task_id=None,
            requested_status="timeout" if reason == "PREPROCESS_TIMEOUT" else "fallback_unavailable",
            requested_modified=False,
            candidate_id=None,
            candidate_checksum=None,
            candidate_size=None,
            failure_code=reason,
            failure_summary=failure_summary,
            raw_event=raw_event,
            backend_exchange=backend_exchange,
            hash_routing_key=hash_routing_key,
            event_type=event_type,
        )

    async def _close_gate_and_enqueue_hash(
        self,
        *,
        event_id: str,
        gate_id: str,
        expected_ready_event_id: str | None,
        expected_backend_task_id: str | None,
        requested_status: str,
        requested_modified: bool,
        candidate_id: str | None,
        candidate_checksum: str | None,
        candidate_size: int | None,
        failure_code: str | None,
        failure_summary: str | None,
        raw_event: dict[str, Any],
        backend_exchange: str,
        hash_routing_key: str,
        event_type: str,
    ) -> dict[str, Any]:
        pool = await get_database_pool()
        payload_json = json.dumps(raw_event, ensure_ascii=False, sort_keys=True)
        payload_hash = hashlib.sha256(payload_json.encode("utf-8")).hexdigest()
        summary = (failure_summary or "")[:1000] or None

        async with pool.acquire() as conn:
            try:
                async with conn.cursor() as cursor:
                    await cursor.execute(
                        """
                        INSERT IGNORE INTO pcd_file_preprocess_inbox (
                            event_id, gate_id, event_type, payload_sha256
                        ) VALUES (UUID_TO_BIN(%s), UUID_TO_BIN(%s), %s, %s)
                        """,
                        (event_id, gate_id, event_type, payload_hash),
                    )
                    if cursor.rowcount == 0:
                        await conn.rollback()
                        return {"outcome": "duplicate", "gate_id": gate_id}

                    await cursor.execute(
                        """
                        SELECT BIN_TO_UUID(gate_id) gate_id,
                               BIN_TO_UUID(ready_event_id) ready_event_id,
                               backend_task_id, continuation_json, original_locator,
                               candidate_id, candidate_locator, upload_checksum,
                               candidate_checksum, original_size, candidate_size, status,
                               content_revision
                        FROM pcd_file_preprocess_gate
                        WHERE gate_id=UUID_TO_BIN(%s)
                        FOR UPDATE
                        """,
                        (gate_id,),
                    )
                    gate = await cursor.fetchone()
                    if not gate:
                        await cursor.execute(
                            """
                            UPDATE pcd_file_preprocess_inbox
                            SET process_status='FAILED', processed_at=CURRENT_TIMESTAMP(3),
                                failure_code='GATE_NOT_FOUND'
                            WHERE event_id=UUID_TO_BIN(%s)
                            """,
                            (event_id,),
                        )
                        await conn.commit()
                        return {"outcome": "invalid", "reason": "GATE_NOT_FOUND"}

                    if (
                        expected_ready_event_id
                        and gate["ready_event_id"].lower() != expected_ready_event_id.lower()
                    ) or (
                        expected_backend_task_id
                        and gate["backend_task_id"] != expected_backend_task_id
                    ):
                        await cursor.execute(
                            """
                            UPDATE pcd_file_preprocess_inbox
                            SET process_status='FAILED', processed_at=CURRENT_TIMESTAMP(3),
                                failure_code='EVENT_GATE_MISMATCH'
                            WHERE event_id=UUID_TO_BIN(%s)
                            """,
                            (event_id,),
                        )
                        await conn.commit()
                        return {"outcome": "invalid", "reason": "EVENT_GATE_MISMATCH"}

                    if gate["status"] != "OPEN":
                        await cursor.execute(
                            """
                            UPDATE pcd_file_preprocess_inbox
                            SET process_status='IGNORED', processed_at=CURRENT_TIMESTAMP(3),
                                failure_code='LATE_RESULT_IGNORED'
                            WHERE event_id=UUID_TO_BIN(%s)
                            """,
                            (event_id,),
                        )
                        await conn.commit()
                        return {"outcome": "late", "gate_id": gate_id}

                    # 需求四-4：只有受信 Broker 已登记且 processed 摘要完全一致时才能选候选。
                    candidate_valid = candidate_matches_processed_result(
                        gate,
                        requested_status=requested_status,
                        requested_modified=requested_modified,
                        candidate_id=candidate_id,
                        candidate_checksum=candidate_checksum,
                        candidate_size=candidate_size,
                    )
                    selected_locator = (
                        gate["candidate_locator"] if candidate_valid else gate["original_locator"]
                    )
                    expected_checksum = (
                        gate["candidate_checksum"] if candidate_valid else gate["upload_checksum"]
                    )
                    selected_size = (
                        int(gate["candidate_size"]) if candidate_valid else int(gate["original_size"])
                    )
                    final_status = "SELECTED" if candidate_valid else "FALLBACK"
                    result_status = requested_status
                    if requested_status == "success" and requested_modified and not candidate_valid:
                        result_status = "failed"
                        failure_code = failure_code or "CANDIDATE_VALIDATION_FAILED"
                        summary = summary or "候选内容未通过 Storage 侧完整性校验，已回退原始文件"

                    continuation = gate["continuation_json"]
                    if isinstance(continuation, str):
                        continuation = json.loads(continuation)
                    accumulated = dict(continuation.get("accumulated") or {})
                    accumulated.update(
                        {
                            "storage_path": selected_locator,
                            "checksum": expected_checksum,
                            "file_size": selected_size,
                            "preprocess_status": result_status,
                            "content_modified": candidate_valid,
                            "content_revision": int(gate["content_revision"]) + (1 if candidate_valid else 0),
                            "preprocess_gate_id": gate_id,
                            "upload_checksum": gate["upload_checksum"],
                            "candidate_checksum": gate["candidate_checksum"] if candidate_valid else "",
                        }
                    )
                    continuation.update(
                        {
                            "stage": "hash_calculate",
                            "storage_path": selected_locator,
                            "file_checksum": expected_checksum,
                            "file_size": selected_size,
                            "retry_count": 0,
                            "failure_reason": "",
                            "accumulated": accumulated,
                            "preprocess_gate_id": gate_id,
                            "preprocess_status": result_status,
                            "content_modified": candidate_valid,
                            "content_revision": accumulated["content_revision"],
                            "upload_checksum": gate["upload_checksum"],
                            "candidate_checksum": gate["candidate_checksum"] if candidate_valid else "",
                        }
                    )
                    hash_outbox_id = event_id
                    # REQ-WORKER-TASKBUS-2026-07：Gate CAS 关闭后继续写入原 hash task Outbox。
                    # 内容预处理仍由生命周期消费者完成，但 hash 阶段回到 Backend Task Bus。
                    hash_event = continuation

                    await cursor.execute(
                        """
                        UPDATE pcd_file_preprocess_gate
                        SET processed_event_id=UUID_TO_BIN(%s), selected_locator=%s,
                            final_size=%s, status=%s, result_status=%s,
                            content_modified=%s, content_revision=%s,
                            failure_code=%s, failure_summary=%s,
                            selected_at=CURRENT_TIMESTAMP(3), row_version=row_version+1
                        WHERE gate_id=UUID_TO_BIN(%s) AND status='OPEN'
                        """,
                        (
                            event_id,
                            selected_locator,
                            selected_size,
                            final_status,
                            result_status,
                            1 if candidate_valid else 0,
                            accumulated["content_revision"],
                            failure_code,
                            summary,
                            gate_id,
                        ),
                    )
                    await cursor.execute(
                        """
                        INSERT INTO pcd_storage_outbox (
                            outbox_id, aggregate_type, aggregate_id, event_type,
                            exchange_name, routing_key, payload_json
                        ) VALUES (
                            UUID_TO_BIN(%s), 'FILE_PREPROCESS_GATE', UUID_TO_BIN(%s),
                            'pcd.file.backend.hash.v1', %s, %s, CAST(%s AS JSON)
                        )
                        """,
                        (
                            hash_outbox_id,
                            gate_id,
                            backend_exchange,
                            hash_routing_key,
                            json.dumps(hash_event, ensure_ascii=False),
                        ),
                    )
                    await cursor.execute(
                        """
                        UPDATE pcd_file_preprocess_inbox
                        SET process_status='PROCESSED', processed_at=CURRENT_TIMESTAMP(3)
                        WHERE event_id=UUID_TO_BIN(%s)
                        """,
                        (event_id,),
                    )
                await conn.commit()
                return {
                    "outcome": "selected" if candidate_valid else "fallback",
                    "gate_id": gate_id,
                    "content_modified": candidate_valid,
                    "preprocess_status": result_status,
                }
            except Exception:
                await conn.rollback()
                raise

    async def claim_outbox_batch(self, batch_size: int = 50) -> list[dict[str, Any]]:
        """短事务领取待发布 Outbox；PUBLISHING 超时记录由恢复任务重新置回 PENDING。"""
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            try:
                async with conn.cursor() as cursor:
                    await cursor.execute(
                        """
                        SELECT BIN_TO_UUID(outbox_id) outbox_id,
                               BIN_TO_UUID(aggregate_id) aggregate_id,
                               event_type, exchange_name, routing_key, payload_json,
                               retry_count
                        FROM pcd_storage_outbox
                        WHERE message_status IN ('PENDING', 'FAILED')
                          AND available_at <= CURRENT_TIMESTAMP(3)
                          AND retry_count < 20
                        ORDER BY created_at
                        LIMIT %s
                        FOR UPDATE SKIP LOCKED
                        """,
                        (batch_size,),
                    )
                    rows = await cursor.fetchall()
                    if rows:
                        placeholders = ",".join(["UUID_TO_BIN(%s)"] * len(rows))
                        await cursor.execute(
                            f"""
                            UPDATE pcd_storage_outbox
                            SET message_status='PUBLISHING'
                            WHERE outbox_id IN ({placeholders})
                            """,
                            tuple(row["outbox_id"] for row in rows),
                        )
                await conn.commit()
            except Exception:
                await conn.rollback()
                raise

        normalized: list[dict[str, Any]] = []
        for row in rows:
            item = dict(row)
            if isinstance(item["payload_json"], str):
                item["payload_json"] = json.loads(item["payload_json"])
            normalized.append(item)
        return normalized

    async def mark_outbox_sent(self, outbox_id: str) -> None:
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    UPDATE pcd_storage_outbox
                    SET message_status='SENT', published_at=CURRENT_TIMESTAMP(3), last_error=NULL
                    WHERE outbox_id=UUID_TO_BIN(%s) AND message_status='PUBLISHING'
                    """,
                    (outbox_id,),
                )
            await conn.commit()

    async def mark_outbox_failed(self, outbox_id: str, error: str, delay_seconds: int) -> None:
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    UPDATE pcd_storage_outbox
                    SET message_status='FAILED', retry_count=retry_count+1,
                        last_error=%s,
                        available_at=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL %s SECOND)
                    WHERE outbox_id=UUID_TO_BIN(%s)
                    """,
                    ((error or "publish failed")[:1000], delay_seconds, outbox_id),
                )
            await conn.commit()

    async def recover_stale_outbox(self, stale_seconds: int = 60) -> int:
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    UPDATE pcd_storage_outbox
                    SET message_status='FAILED', last_error='PUBLISH_LEASE_EXPIRED',
                        available_at=CURRENT_TIMESTAMP(3)
                    WHERE message_status='PUBLISHING'
                      AND updated_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL %s SECOND)
                    """,
                    (stale_seconds,),
                )
                count = cursor.rowcount
            await conn.commit()
        return count

    async def list_expired_open_gates(self, limit: int = 100) -> list[dict[str, str]]:
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    SELECT BIN_TO_UUID(gate_id) gate_id, backend_task_id
                    FROM pcd_file_preprocess_gate
                    WHERE status='OPEN' AND deadline_at <= CURRENT_TIMESTAMP(3)
                    ORDER BY deadline_at
                    LIMIT %s
                    """,
                    (limit,),
                )
                return list(await cursor.fetchall())

    async def is_gate_closed(self, backend_task_id: str) -> bool:
        """Hash 前置校验：持久化闸门必须已经选定候选或回退原始内容。"""
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    SELECT status
                    FROM pcd_file_preprocess_gate
                    WHERE backend_task_id=%s
                    """,
                    (backend_task_id,),
                )
                row = await cursor.fetchone()
        return bool(row and row["status"] in {"SELECTED", "FALLBACK", "CLEANED"})

    async def get_progress(self, backend_task_id: str) -> dict[str, Any] | None:
        """读取可安全暴露给上传进度 API 的闸门摘要，不返回任何物理 locator。"""
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    SELECT BIN_TO_UUID(gate_id) gate_id, status, result_status,
                           content_modified, deadline_at, selected_at
                    FROM pcd_file_preprocess_gate
                    WHERE backend_task_id=%s
                    """,
                    (backend_task_id,),
                )
                row = await cursor.fetchone()
        if not row:
            return None
        return {
            "gate_id": row["gate_id"],
            "status": row["status"],
            "result_status": row["result_status"],
            "content_modified": bool(row["content_modified"]),
            "deadline_at": row["deadline_at"].replace(tzinfo=timezone.utc).isoformat()
            if row["deadline_at"]
            else None,
            "selected_at": row["selected_at"].replace(tzinfo=timezone.utc).isoformat()
            if row["selected_at"]
            else None,
        }

    async def mark_final_checksum(self, gate_id: str, checksum: str, final_size: int) -> None:
        """Hash 成功后保存 Storage 独立计算的最终摘要，供激活与审计使用。"""
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    UPDATE pcd_file_preprocess_gate
                    SET final_checksum=%s, final_size=%s, row_version=row_version+1
                    WHERE gate_id=UUID_TO_BIN(%s) AND status IN ('SELECTED', 'FALLBACK')
                    """,
                    (checksum, final_size, gate_id),
                )
            await conn.commit()

    async def mark_activation_committed(self, gate_id: str) -> bool:
        """记录最终内容已对外激活。

        需求四-4：只有业务服务成功提交最终 storage_path/checksum 且 file.available
        已发布后，才允许后台删除未选中的内容副本。重复调用保持幂等。
        """
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    UPDATE pcd_file_preprocess_gate
                    SET activation_committed_at=COALESCE(
                            activation_committed_at, CURRENT_TIMESTAMP(3)
                        ),
                        row_version=row_version+1
                    WHERE gate_id=UUID_TO_BIN(%s)
                      AND status IN ('SELECTED', 'FALLBACK', 'CLEANED')
                    """,
                    (gate_id,),
                )
                updated = cursor.rowcount > 0
            await conn.commit()
        return updated

    async def list_cleanup_ready_gates(self, limit: int = 100) -> list[dict[str, Any]]:
        """领取待清理快照；这里只读取定位符，状态 CAS 在 ``mark_gate_cleaned`` 完成。"""
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    SELECT BIN_TO_UUID(gate_id) gate_id, status,
                           original_locator, candidate_locator, selected_locator,
                           cleanup_attempts
                    FROM pcd_file_preprocess_gate
                    WHERE activation_committed_at IS NOT NULL
                      AND cleaned_at IS NULL
                      AND status IN ('SELECTED', 'FALLBACK')
                      AND cleanup_attempts < 20
                    ORDER BY activation_committed_at
                    LIMIT %s
                    """,
                    (limit,),
                )
                return list(await cursor.fetchall())

    async def mark_gate_cleaned(self, gate_id: str) -> bool:
        """把物理清理成功的 Gate 转为 CLEANED；并发补偿只允许一个调用成功。"""
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    UPDATE pcd_file_preprocess_gate
                    SET status='CLEANED', cleaned_at=CURRENT_TIMESTAMP(3),
                        last_cleanup_error=NULL, row_version=row_version+1
                    WHERE gate_id=UUID_TO_BIN(%s)
                      AND activation_committed_at IS NOT NULL
                      AND cleaned_at IS NULL
                      AND status IN ('SELECTED', 'FALLBACK')
                    """,
                    (gate_id,),
                )
                updated = cursor.rowcount == 1
            await conn.commit()
        return updated

    async def record_cleanup_failure(self, gate_id: str, error: str) -> None:
        """记录清理失败但不回滚文件激活；Sweeper 会继续进行有限次数补偿。"""
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    """
                    UPDATE pcd_file_preprocess_gate
                    SET cleanup_attempts=cleanup_attempts+1,
                        last_cleanup_error=%s,
                        row_version=row_version+1
                    WHERE gate_id=UUID_TO_BIN(%s)
                      AND activation_committed_at IS NOT NULL
                      AND cleaned_at IS NULL
                      AND status IN ('SELECTED', 'FALLBACK')
                    """,
                    ((error or "cleanup failed")[:1000], gate_id),
                )
            await conn.commit()


file_preprocess_repository = FilePreprocessRepository()
