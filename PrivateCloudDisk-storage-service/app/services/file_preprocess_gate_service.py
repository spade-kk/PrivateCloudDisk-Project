"""文件内容预处理闸门应用服务。

需求来源：文件生命周期事件扩展。
该服务连接既有 Backend 流水线与新的生命周期事件，但不执行任何插件代码。插件系统
成功、失败、超时或不可用最终都通过同一个数据库 CAS 关闭闸门并继续 hash。
"""
from __future__ import annotations

import asyncio
import hashlib
import logging
import os
import secrets
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

from app.repositories.file_preprocess_repository import file_preprocess_repository
from core.config import settings
from core.event.file_backend_event import FileBackendEvent
from core.event.file_content_event import FileContentReadyData, FileLifecycleEvent, build_ready_event

logger = logging.getLogger("file_preprocess_gate")


class FilePreprocessGateService:
    """预处理闸门编排服务。"""

    async def open_after_merge(
        self,
        event: FileBackendEvent,
        merge_data: dict[str, Any],
    ) -> bool:
        """merge 成功后持久化 OPEN Gate、ready 和 timeout Outbox。

        原行为：merge 消费者直接发布 hash。
        新行为：merge 仅创建闸门；processed/timeout/sweeper 任一路径关闭闸门后才发布
        hash。原始合并文件保持只读，候选内容只能由后续受信 Broker 单独登记。
        """
        gate_id = str(uuid.uuid4())
        ready_event_id = str(uuid.uuid4())
        ready_outbox_id = str(uuid.uuid4())
        timeout_event_id = str(uuid.uuid4())
        timeout_outbox_id = str(uuid.uuid4())
        deadline = datetime.now(timezone.utc) + timedelta(
            seconds=settings.file_preprocess_deadline_seconds
        )

        physical_locator = str(merge_data.get("storage_path") or event.storage_path)
        upload_checksum = str(merge_data.get("checksum") or event.file_checksum)
        original_size = int(merge_data.get("file_size") or event.file_size or 0)
        lease_plaintext = secrets.token_urlsafe(48)
        lease_hash = hashlib.sha256(lease_plaintext.encode("utf-8")).hexdigest()
        opaque_staging_locator = f"pcd-staging://{gate_id}/original"

        continuation = event.to_dict()
        continuation.update(
            {
                "storage_path": physical_locator,
                "file_checksum": upload_checksum,
                "file_size": original_size,
                "upload_checksum": upload_checksum,
                "preprocess_gate_id": gate_id,
                "stage": "content_preprocess",
            }
        )
        accumulated = dict(continuation.get("accumulated") or {})
        accumulated.update(
            {
                **merge_data,
                "storage_path": physical_locator,
                "checksum": upload_checksum,
                "file_size": original_size,
                "upload_checksum": upload_checksum,
                "preprocess_gate_id": gate_id,
            }
        )
        continuation["accumulated"] = accumulated

        ready = build_ready_event(
            event_id=ready_event_id,
            actor_user_id=event.user_id,
            space_id=event.space_id or None,
            correlation_id=event.pipeline_id,
            causation_id=event.backend_task_id,
            data=FileContentReadyData(
                gate_id=gate_id,
                backend_task_id=event.backend_task_id,
                pipeline_id=event.pipeline_id,
                file_id=event.file_id,
                uploads_session_id=event.uploads_id,
                name=event.file_name,
                mime_type=event.file_type,
                size=original_size,
                upload_checksum=upload_checksum,
                staging_locator=opaque_staging_locator,
                # 该引用只用于 Runtime 以 mTLS 身份换取执行 Lease；数据库仅保存其摘要。
                content_lease_ref=lease_plaintext,
                preprocess_deadline_at=deadline.isoformat(),
            ),
        ).to_dict()
        timeout = FileLifecycleEvent(
            id=timeout_event_id,
            type="pcd.file.content.timeout.v1",
            subject=ready["subject"],
            actor_user_id=event.user_id,
            space_id=event.space_id or None,
            correlation_id=event.pipeline_id,
            causation_id=ready_event_id,
            data={
                "gate_id": gate_id,
                "backend_task_id": event.backend_task_id,
                "ready_event_id": ready_event_id,
                "deadline_at": deadline.isoformat(),
            },
        ).to_dict()

        created = await file_preprocess_repository.create_gate_with_outbox(
            gate_id=gate_id,
            ready_event_id=ready_event_id,
            backend_event=continuation,
            content_lease_hash=lease_hash,
            original_locator=physical_locator,
            upload_checksum=upload_checksum,
            original_size=original_size,
            deadline_at=deadline.replace(tzinfo=None),
            ready_outbox_id=ready_outbox_id,
            ready_event=ready,
            timeout_outbox_id=timeout_outbox_id,
            timeout_event=timeout,
            lifecycle_exchange=settings.file_lifecycle_exchange,
            ready_routing_key=settings.file_content_ready_routing_key,
            timeout_schedule_routing_key=settings.file_content_timeout_schedule_routing_key,
        )
        logger.info(
            "[PREPROCESS-GATE] %s backend_task_id=%s pipeline_id=%s file_id=%s "
            "space_id=%s gate_id=%s deadline=%s",
            "OPENED" if created else "DUPLICATE",
            event.backend_task_id,
            event.pipeline_id,
            event.file_id,
            event.space_id or "personal",
            gate_id,
            deadline.isoformat(),
        )
        return created

    async def handle_processed_event(self, raw: dict[str, Any]) -> dict[str, Any]:
        envelope = FileLifecycleEvent.from_dict(raw)
        data = envelope.data
        if envelope.type != "pcd.file.content.processed.v1":
            raise ValueError(f"不支持的预处理结果事件类型: {envelope.type}")
        status = str(data.get("status") or "")
        if status not in {"success", "skipped", "failed", "timeout"}:
            raise ValueError(f"非法预处理结果状态: {status}")
        return await file_preprocess_repository.finalize_from_processed(
            event_id=envelope.id,
            gate_id=str(data["gate_id"]),
            ready_event_id=str(data["ready_event_id"]),
            backend_task_id=str(data["backend_task_id"]),
            result_status=status,
            content_modified=bool(data.get("content_modified", False)),
            candidate_id=data.get("candidate_id"),
            candidate_checksum=data.get("candidate_checksum"),
            candidate_size=data.get("candidate_size"),
            failure_code=data.get("failure_code"),
            failure_summary=data.get("failure_summary"),
            raw_event=raw,
            backend_exchange=settings.file_backend_exchange,
            hash_routing_key=settings.file_backend_hash_routing_key,
        )

    async def fallback_from_event(
        self,
        raw: dict[str, Any],
        *,
        reason: str,
        event_type: str,
    ) -> dict[str, Any]:
        envelope = FileLifecycleEvent.from_dict(raw)
        gate_id = str(envelope.data["gate_id"])
        return await file_preprocess_repository.fallback_and_continue(
            event_id=envelope.id,
            gate_id=gate_id,
            reason=reason,
            failure_summary=f"{event_type} 触发存储侧降级，继续使用合并后的原始内容",
            raw_event=raw,
            backend_exchange=settings.file_backend_exchange,
            hash_routing_key=settings.file_backend_hash_routing_key,
            event_type=event_type,
        )

    async def sweep_expired(self) -> int:
        """扫描超时 OPEN Gate；与 Rabbit timeout sentinel 构成双重逃生路径。"""
        expired = await file_preprocess_repository.list_expired_open_gates(
            settings.file_preprocess_sweeper_batch_size
        )
        closed = 0
        for gate in expired:
            sweep_event = FileLifecycleEvent(
                id=str(uuid.uuid4()),
                type="pcd.file.content.timeout.sweeper.v1",
                subject=f"preprocess-gates/{gate['gate_id']}",
                actor_user_id="",
                space_id=None,
                correlation_id=gate["backend_task_id"],
                data={
                    "gate_id": gate["gate_id"],
                    "backend_task_id": gate["backend_task_id"],
                },
            ).to_dict()
            result = await self.fallback_from_event(
                sweep_event,
                reason="PREPROCESS_TIMEOUT",
                event_type="pcd.file.content.timeout.sweeper.v1",
            )
            if result["outcome"] == "fallback":
                closed += 1
        return closed

    async def mark_activation_and_cleanup(self, gate_id: str) -> bool:
        """提交激活清理点并尽力清理未选中的副本。

        原行为：候选被选中后原始副本长期残留，回退时候选副本也不会回收。
        新行为：只有业务激活和 file.available 发布均成功后才写提交点；物理清理失败
        不影响文件可访问，交由周期 Sweeper 补偿。影响范围仅为预处理临时副本。
        """
        committed = await file_preprocess_repository.mark_activation_committed(gate_id)
        if not committed:
            return False
        await self.cleanup_committed(settings.file_preprocess_sweeper_batch_size)
        return True

    async def cleanup_committed(self, limit: int = 100) -> int:
        """清理已提交 Gate 的未选中副本，所有路径均受 uploads 根目录边界约束。"""
        gates = await file_preprocess_repository.list_cleanup_ready_gates(limit)
        cleaned = 0
        for gate in gates:
            try:
                await asyncio.to_thread(self._delete_unselected_copy, gate)
                if await file_preprocess_repository.mark_gate_cleaned(gate["gate_id"]):
                    cleaned += 1
            except Exception as exc:
                await file_preprocess_repository.record_cleanup_failure(
                    gate["gate_id"], str(exc)
                )
                logger.exception(
                    "[PREPROCESS-CLEANUP] 清理失败 gate_id=%s status=%s",
                    gate["gate_id"],
                    gate["status"],
                )
        return cleaned

    @staticmethod
    def _delete_unselected_copy(gate: dict[str, Any]) -> None:
        """删除非最终版本；拒绝越界路径、目录和符号链接。"""
        selected = str(gate.get("selected_locator") or "")
        original = str(gate.get("original_locator") or "")
        candidate = str(gate.get("candidate_locator") or "")
        unselected = original if gate["status"] == "SELECTED" else candidate
        if not unselected or unselected == selected:
            return

        uploads_root = Path(settings.file_upload_dir).resolve()
        target = Path(unselected)
        # 先检查原始路径自身，避免通过符号链接把删除目标指向根目录外。
        if target.is_symlink():
            raise ValueError("拒绝清理符号链接形式的预处理副本")
        resolved = target.resolve()
        try:
            resolved.relative_to(uploads_root)
        except ValueError as exc:
            raise ValueError("预处理清理路径越过 uploads 根目录") from exc
        if resolved.exists():
            if not resolved.is_file():
                raise ValueError("预处理清理目标不是普通文件")
            resolved.unlink()

        # 仅尝试回收 Broker 创建的空候选目录；任何非空目录都保留给运维审计。
        parent = resolved.parent
        if parent.name == str(gate["gate_id"]) and parent.parent.name == ".preprocess":
            try:
                os.rmdir(parent)
            except OSError:
                pass


file_preprocess_gate_service = FilePreprocessGateService()
