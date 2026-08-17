"""文件内容预处理生命周期事件。

需求来源：文件生命周期事件扩展。
消息遵循 CloudEvents 1.0 外形并保留项目已有 snake_case 业务字段。路由键不带版本，
``type`` 与 ``schema_version`` 承担契约版本，确保未来增加字段时旧消费者仍可忽略。
"""
from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any
import uuid


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass(frozen=True)
class FileContentReadyData:
    gate_id: str
    backend_task_id: str
    pipeline_id: str
    file_id: str
    uploads_session_id: str
    name: str
    mime_type: str
    size: int
    upload_checksum: str
    staging_locator: str
    content_lease_ref: str
    preprocess_deadline_at: str
    content_revision: int = 0
    automation_depth: int = 0


@dataclass(frozen=True)
class FileContentProcessedData:
    gate_id: str
    backend_task_id: str
    ready_event_id: str
    status: str
    content_modified: bool = False
    candidate_id: str | None = None
    candidate_checksum: str | None = None
    candidate_size: int | None = None
    matched_entrypoints: int = 0
    completed_entrypoints: int = 0
    failure_code: str | None = None
    failure_summary: str | None = None
    finished_at: str = field(default_factory=_utc_now)


@dataclass(frozen=True)
class FileLifecycleEvent:
    """CloudEvents 兼容事件信封。"""

    type: str
    subject: str
    actor_user_id: str
    space_id: str | None
    data: dict[str, Any]
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    specversion: str = "1.0"
    source: str = "pcd.storage-service"
    time: str = field(default_factory=_utc_now)
    datacontenttype: str = "application/json"
    schema_version: int = 1
    correlation_id: str = ""
    causation_id: str = ""
    traceparent: str = ""

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "FileLifecycleEvent":
        """只读取已知字段，兼容上游未来追加的 CloudEvents 扩展属性。"""
        fields = cls.__dataclass_fields__
        return cls(**{key: value for key, value in raw.items() if key in fields})


def build_ready_event(
    *,
    event_id: str,
    actor_user_id: str,
    space_id: str | None,
    correlation_id: str,
    causation_id: str,
    data: FileContentReadyData,
) -> FileLifecycleEvent:
    """构建激活前内容就绪事件；事件中不得出现物理路径或对象存储凭证。"""
    return FileLifecycleEvent(
        id=event_id,
        type="pcd.file.content.ready.v1",
        subject=f"spaces/{space_id or 'personal'}/files/{data.file_id}",
        actor_user_id=actor_user_id,
        space_id=space_id,
        correlation_id=correlation_id,
        causation_id=causation_id,
        data=asdict(data),
    )

