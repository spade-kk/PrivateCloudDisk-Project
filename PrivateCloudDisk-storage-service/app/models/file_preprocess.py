"""文件内容预处理闸门领域模型。

需求来源：插件生态与自动化工作流平台 / 文件生命周期事件扩展。
该模型只描述 Storage Service 自己拥有的持久化状态；Automation Service 和插件沙箱
只能看到不透明的 staging locator，不能得到这里保存的物理路径。
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any


@dataclass(frozen=True)
class FilePreprocessGate:
    """预处理闸门快照。

    ``status`` 只有 OPEN 才能接收 Automation 的处理结果。SELECTED/FALLBACK/CLEANED
    都是终态或清理态，迟到消息必须 ACK 并记录为忽略，不能再次发布 hash。
    """

    gate_id: str
    ready_event_id: str
    backend_task_id: str
    pipeline_id: str
    file_id: str
    user_id: str
    space_id: str | None
    uploads_id: str | None
    file_name: str
    file_type: str
    original_locator: str
    upload_checksum: str
    original_size: int
    status: str
    deadline_at: datetime
    candidate_locator: str | None = None
    selected_locator: str | None = None
    candidate_checksum: str | None = None
    final_checksum: str | None = None
    candidate_size: int | None = None
    final_size: int | None = None
    result_status: str | None = None
    content_modified: bool = False
    content_revision: int = 0
    failure_code: str | None = None
    failure_summary: str | None = None
    row_version: int = 0


@dataclass(frozen=True)
class OutboxMessage:
    """待发布的 Storage Outbox 消息。"""

    outbox_id: str
    aggregate_id: str
    event_type: str
    exchange_name: str
    routing_key: str
    payload: dict[str, Any]
    retry_count: int = 0

