"""
AI Processing Service - AI 处理事件定义

定义 RabbitMQ 消息体结构，与 storage-service 发布的事件格式兼容。
"""
from __future__ import annotations
import uuid
from datetime import datetime, timezone
from dataclasses import dataclass, field, asdict
from typing import Any, Optional


@dataclass
class AIProcessEvent:
    """
    AI 处理事件

    由 storage-service 在 MARK_ACTIVE 完成后发布到 pcd.ai.process.exchange
    """
    message_id: str
    file_id: str
    user_id: str
    tenant_id: str = ""
    file_name: str = ""
    file_type: str = ""          # MIME 类型
    file_size: int = 0
    storage_path: str = ""       # 文件存储路径 (本地或 MinIO object key)
    node_id: str = ""            # 目录节点 ID
    created_at: str = ""         # ISO 格式时间
    retry_count: int = 0
    failure_reason: str = ""
    # 任务过滤 (可选，指定只执行哪些任务)
    enabled_tasks: list[str] = field(default_factory=list)

    @staticmethod
    def generate_message_id() -> str:
        return str(uuid.uuid4())

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "AIProcessEvent":
        return cls(
            message_id=data.get("message_id", cls.generate_message_id()),
            file_id=data.get("file_id", ""),
            user_id=data.get("user_id", ""),
            tenant_id=data.get("tenant_id", ""),
            file_name=data.get("file_name", ""),
            file_type=data.get("file_type", ""),
            file_size=data.get("file_size", 0),
            storage_path=data.get("storage_path", ""),
            node_id=data.get("node_id", ""),
            created_at=data.get("created_at", ""),
            retry_count=data.get("retry_count", 0),
            failure_reason=data.get("failure_reason", ""),
            enabled_tasks=data.get("enabled_tasks", []),
        )

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    def with_retry_increment(self) -> "AIProcessEvent":
        return AIProcessEvent(
            message_id=self.message_id,
            file_id=self.file_id,
            user_id=self.user_id,
            tenant_id=self.tenant_id,
            file_name=self.file_name,
            file_type=self.file_type,
            file_size=self.file_size,
            storage_path=self.storage_path,
            node_id=self.node_id,
            created_at=self.created_at,
            retry_count=self.retry_count + 1,
            failure_reason=self.failure_reason,
            enabled_tasks=self.enabled_tasks,
        )


@dataclass
class FaceClusterEvent:
    """
    人脸聚类事件

    由 storage-service 定时触发 (如每周一次)，或手动触发。
    消费后对指定用户的所有图片进行人脸聚类。
    """
    message_id: str
    user_id: str
    tenant_id: str = ""
    force_recluster: bool = False   # 是否强制重新聚类
    created_at: str = ""

    @staticmethod
    def generate_message_id() -> str:
        return str(uuid.uuid4())

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "FaceClusterEvent":
        return cls(
            message_id=data.get("message_id", cls.generate_message_id()),
            user_id=data.get("user_id", ""),
            tenant_id=data.get("tenant_id", ""),
            force_recluster=data.get("force_recluster", False),
            created_at=data.get(
                "created_at",
                datetime.now(timezone.utc).isoformat(),
            ),
        )

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class RecommendationEvent:
    """
    推荐系统事件

    由 platform-service 定时触发 (如每6小时一次)。
    消费后为用户更新个性化文件推荐。
    """
    message_id: str
    user_id: str
    tenant_id: str = ""
    update_type: str = "full"       # full | incremental
    created_at: str = ""

    @staticmethod
    def generate_message_id() -> str:
        return str(uuid.uuid4())

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "RecommendationEvent":
        return cls(
            message_id=data.get("message_id", cls.generate_message_id()),
            user_id=data.get("user_id", ""),
            tenant_id=data.get("tenant_id", ""),
            update_type=data.get("update_type", "full"),
            created_at=data.get(
                "created_at",
                datetime.now(timezone.utc).isoformat(),
            ),
        )

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class AIProcessResult:
    """
    AI 处理结果 (用于内部传递)
    """
    file_id: str
    task_type: str
    success: bool
    data: dict[str, Any] = field(default_factory=dict)
    error: str = ""
    failure_reason: str = ""
    skipped: bool = False
    skipped_reason: str = ""
    processing_time_ms: float = 0.0