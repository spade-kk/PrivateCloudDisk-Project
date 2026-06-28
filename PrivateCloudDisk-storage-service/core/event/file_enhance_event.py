"""
文件增强事件模型 — 并发流水线

每个增强阶段独立 MQ 消息，互不阻塞:
  - thumbnail     → 缩略图生成
  - transcode      → 视频转码
  - hls_transcode  → HLS 流媒体转码
  - content_index  → 全文搜索索引

与 Backend 事件的关键区别:
  - Backend: 顺序执行，影响文件是否可访问
  - Enhance: 并发执行，失败仅标记 DEGRADED

Idempotent 设计:
  - enhance_task_id 在所有增强阶段共享（同一文件增强任务）
  - 幂等键: enhance:task:{enhance_task_id}:stage:{stage}
  - 增强事件不提供接口查询（仅内部追踪）
"""
from __future__ import annotations
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
import uuid


@dataclass
class FileEnhanceEvent:
    """文件增强事件 — 并发流水线消息体"""

    enhance_task_id: str          # 增强任务 ID（同一文件增强任务的所有阶段共享）
    stage: str                    # 当前阶段: thumbnail / video_transcode / hls_transcode / content_index
    file_id: str                  # 文件 ID
    user_id: str                  # 用户 ID
    file_name: str                # 原始文件名
    file_type: str                # MIME 类型
    file_size: int = 0            # 文件大小 (字节)
    storage_path: str = ""        # 当前文件存储路径
    node_id: str = ""             # 目录节点 ID
    file_checksum: str = ""       # 文件 SHA-256
    backend_task_id: str = ""     # 关联的后台任务 ID（用于追溯）
    retry_count: int = 0          # 已重试次数
    failure_reason: str = ""      # 失败原因
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    accumulated: dict = field(default_factory=dict)  # 累积的上下文数据

    @classmethod
    def from_dict(cls, data: dict) -> "FileEnhanceEvent":
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})

    def to_dict(self) -> dict:
        return asdict(self)

    def with_retry_increment(self) -> "FileEnhanceEvent":
        d = self.to_dict()
        d["retry_count"] = self.retry_count + 1
        d["created_at"] = datetime.now(timezone.utc).isoformat()
        return FileEnhanceEvent.from_dict(d)

    @staticmethod
    def generate_enhance_task_id() -> str:
        """生成增强任务 ID（UUID hex）"""
        return uuid.uuid4().hex