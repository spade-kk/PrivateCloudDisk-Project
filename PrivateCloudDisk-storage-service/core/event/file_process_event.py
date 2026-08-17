"""
文件删除事件模型
定义在 RabbitMQ 消息中传递的文件删除数据结构

注意：
- FileProcessEvent 和 ContentIndexEvent 已迁移到新架构
  - FileProcessEvent → file_backend_event.py (BackendTaskEvent) + file_enhance_event.py (EnhanceTaskEvent)
  - ContentIndexEvent → 已整合到 Enhancement 流水线的 content_index 阶段
  - SecurityQuarantineEvent → 已整合到 Backend DLQ 消费者
"""
from __future__ import annotations
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
import uuid


@dataclass
class FileDeleteEvent:
    """文件删除事件消息体"""

    message_id: str
    file_id: str
    storage_path: str = ""
    thumbnail_paths: list = field(default_factory=list)
    transcoded_paths: list = field(default_factory=list)
    user_id: str = ""
    space_id: str = ""            # 需求五-9：永久删除必须限定文件和派生资源所属空间
    retry_count: int = 0
    failure_reason: str = ""
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())

    def __post_init__(self):
        if not self.message_id:
            self.message_id = f"delete:{self.file_id}:{self.space_id or 'personal'}"

    @classmethod
    def from_dict(cls, data: dict) -> "FileDeleteEvent":
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})

    def to_dict(self) -> dict:
        return asdict(self)

    def with_retry_increment(self) -> "FileDeleteEvent":
        d = self.to_dict()
        d["retry_count"] = self.retry_count + 1
        d["created_at"] = datetime.now(timezone.utc).isoformat()
        return FileDeleteEvent.from_dict(d)

    @staticmethod
    def generate_message_id() -> str:
        return uuid.uuid4().hex
