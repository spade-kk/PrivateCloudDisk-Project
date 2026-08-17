"""预览资源领域模型。"""
from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any, Optional


@dataclass(slots=True)
class PreviewResource:
    """跨预览类型统一的资源元数据，类型特有字段放入 metadata。"""

    file_id: str
    user_id: str
    resource_type: str
    storage_path: str
    # 空间管理能力全量集成（需求五-8/9）：预览资源与源文件使用同一空间定位维度。
    # 历史资源可为空；迁移脚本会优先根据 file_id 回填。
    space_id: Optional[str] = None
    resource_variant: str = "default"
    storage_backend: str = "localstorage"
    mime_type: Optional[str] = None
    resource_status: str = "ready"
    size_bytes: int = 0
    checksum: Optional[str] = None
    width: Optional[int] = None
    height: Optional[int] = None
    duration_seconds: Optional[float] = None
    page_count: Optional[int] = None
    metadata: dict[str, Any] = field(default_factory=dict)
    error_message: Optional[str] = None
    source_version: int = 1
    resource_id: Optional[str] = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)
