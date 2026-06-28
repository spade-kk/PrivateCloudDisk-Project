"""
文件后台处理事件模型 — 顺序流水线

每个阶段独立的 MQ 消息:
  merge → hash_calculate → virus_scan → mark_active

与 Enhance 事件的关键区别:
  - Backend: 顺序执行，影响文件是否可访问
  - Enhance: 并发执行，失败仅标记 DEGRADED

Idempotent 设计:
  - backend_task_id 在所有后台阶段共享（同一文件处理任务）
  - 幂等键: backend:task:{backend_task_id}:stage:{stage}
  - 仅在上一个阶段完成后才发送下一个阶段
"""
from __future__ import annotations
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
import uuid


@dataclass
class FileBackendEvent:
    """文件后台处理事件 — 顺序流水线消息体"""

    backend_task_id: str          # 后台任务 ID（同一文件处理任务的所有阶段共享）
    stage: str                    # 当前阶段: merge / hash_calculate / virus_scan / mark_active
    pipeline_id: str              # 流水线追踪 ID（不变，用于关联所有阶段日志）
    file_id: str                  # 文件 ID
    user_id: str                  # 用户 ID
    file_name: str                # 原始文件名
    file_type: str                # MIME 类型
    file_size: int = 0            # 文件大小 (字节)
    storage_path: str = ""        # 当前文件存储路径
    node_id: str = ""             # 目录节点 ID
    total_chunks: int = 0         # 总分片数
    file_checksum: str = ""       # 期望的文件 SHA-256
    uploads_id: str = ""          # 上传会话 ID
    retry_count: int = 0          # 已重试次数
    failure_reason: str = ""      # 失败原因
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    first_published_at: str = ""  # 首次发布时间
    accumulated: dict = field(default_factory=dict)  # 累积的上下文数据

    @classmethod
    def from_dict(cls, data: dict) -> "FileBackendEvent":
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})

    def to_dict(self) -> dict:
        return asdict(self)

    def with_retry_increment(self) -> "FileBackendEvent":
        d = self.to_dict()
        d["retry_count"] = self.retry_count + 1
        d["created_at"] = datetime.now(timezone.utc).isoformat()
        return FileBackendEvent.from_dict(d)

    def with_next_stage(self, stage: str, accumulated: dict | None = None) -> "FileBackendEvent":
        """构建下一阶段事件（复用 backend_task_id 和 pipeline_id）"""
        merged = {**self.accumulated, **(accumulated or {})}
        return FileBackendEvent(
            backend_task_id=self.backend_task_id,
            stage=stage,
            pipeline_id=self.pipeline_id,
            file_id=merged.get("file_id", self.file_id),
            user_id=self.user_id,
            file_name=self.file_name,
            file_type=self.file_type,
            file_size=merged.get("file_size", self.file_size),
            storage_path=merged.get("storage_path", self.storage_path),
            node_id=self.node_id,
            total_chunks=self.total_chunks,
            file_checksum=merged.get("checksum", self.file_checksum),
            uploads_id=self.uploads_id,
            accumulated=merged,
            first_published_at=self.first_published_at or datetime.now(timezone.utc).isoformat(),
        )

    @staticmethod
    def generate_backend_task_id() -> str:
        """生成后台任务 ID（UUID hex）"""
        return uuid.uuid4().hex

    @staticmethod
    def generate_pipeline_id() -> str:
        """生成流水线追踪 ID"""
        return uuid.uuid4().hex[:12]