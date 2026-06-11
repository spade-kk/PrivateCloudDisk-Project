"""
文件处理事件模型
定义在 RabbitMQ 消息中传递的数据结构
"""
from __future__ import annotations
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from typing import Optional
import uuid


@dataclass
class FileProcessEvent:
    """文件处理主事件消息体"""

    message_id: str            # 消息唯一 ID
    task_id: str               # 任务追踪 ID
    task_type: str             # 当前处理步骤 (merge/hash_calculate/virus_scan/...)
    file_id: str               # 文件 ID (合并完成后由业务服务返回)
    user_id: str               # 用户 ID
    file_name: str             # 原始文件名
    file_type: str             # MIME 类型
    file_size: int = 0         # 文件大小 (字节)
    storage_path: str = ""     # 当前文件存储路径
    node_id: str = ""          # 目录节点 ID
    total_chunks: int = 0      # 总分片数
    file_checksum: str = ""    # 期望的文件 SHA-256
    retry_count: int = 0       # 已重试次数
    failure_reason: str = ""   # 失败原因 (FailureReason 枚举值)
    uploads_id: str = ""
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())

    @classmethod
    def from_dict(cls, data: dict) -> "FileProcessEvent":
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})

    def to_dict(self) -> dict:
        return asdict(self)

    def with_retry_increment(self) -> "FileProcessEvent":
        """返回重试次数 +1 的副本"""
        d = self.to_dict()
        d["retry_count"] = self.retry_count + 1
        d["created_at"] = datetime.now(timezone.utc).isoformat()
        return FileProcessEvent.from_dict(d)

    @staticmethod
    def generate_message_id() -> str:
        return uuid.uuid4().hex


@dataclass
class FileDeleteEvent:
    """文件删除事件消息体"""

    message_id: str
    file_id: str
    storage_path: str = ""
    thumbnail_paths: list = field(default_factory=list)
    transcoded_paths: list = field(default_factory=list)
    user_id: str = ""
    retry_count: int = 0
    failure_reason: str = ""
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())

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


@dataclass
class SecurityQuarantineEvent:
    """安全隔离事件消息体 (病毒/木马发现时发送)"""

    message_id: str
    file_id: str
    user_id: str
    file_name: str
    file_type: str
    file_size: int
    storage_path: str               # 原始路径
    quarantine_path: str = ""       # 隔离后路径
    threat_name: str = ""           # 病毒/木马名称
    scanner_output: str = ""        # 扫描器原始输出
    severity: str = "HIGH"          # CRITICAL / HIGH / MEDIUM / LOW
    task_id: str = ""
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())

    @classmethod
    def from_dict(cls, data: dict) -> "SecurityQuarantineEvent":
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})

    def to_dict(self) -> dict:
        return asdict(self)

    @staticmethod
    def generate_message_id() -> str:
        return uuid.uuid4().hex


@dataclass
class ContentIndexEvent:
    """
    文件内容索引事件消息体

    当文件处理完成 (mark_active) 后，触发内容索引:
    1. 根据文件类型匹配抽取器
    2. 抽取文本/表格/OCR 内容
    3. 写入 OpenSearch 基本信息索引 + 内容索引
    """

    message_id: str
    file_id: str
    user_id: str
    file_name: str
    file_type: str
    file_size: int = 0
    storage_path: str = ""
    node_id: str = ""
    task_id: str = ""
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    retry_count: int = 0
    failure_reason: str = ""

    @classmethod
    def from_dict(cls, data: dict) -> "ContentIndexEvent":
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})

    def to_dict(self) -> dict:
        return asdict(self)

    def with_retry_increment(self) -> "ContentIndexEvent":
        d = self.to_dict()
        d["retry_count"] = self.retry_count + 1
        d["created_at"] = datetime.now(timezone.utc).isoformat()
        return ContentIndexEvent.from_dict(d)

    @staticmethod
    def generate_message_id() -> str:
        return uuid.uuid4().hex