"""
Pydantic 数据模型定义
定义 API 请求和响应的数据结构
"""
from typing import Literal, List, Optional
from pydantic import BaseModel, Field


class InitOperationTokenRequest(BaseModel):
    """
    申请操作凭证请求模型

    Attributes:
        file_id: 文件唯一标识符（UUID格式）
        operation_type: 操作类型，支持 download（下载）、preview（预览）、stream（流式播放）

    Example:
        >>> request = InitOperationTokenRequest(
        ...     file_id="550e8400-e29b-41d4-a716-446655440000",
        ...     operation_type="download"
        ... )
    """
    file_id: str
    operation_type: Literal["download", "preview", "stream"]


class OperationTokenCancelRequest(BaseModel):
    """
    销毁操作凭证请求模型

    Attributes:
        operation_token: 需要销毁的操作凭证 JWT 字符串

    Example:
        >>> request = OperationTokenCancelRequest(
        ...     operation_token="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        ... )
    """
    operation_token: str


# ============================
# 下载授权 Grant 模型
# ============================

class InitDownloadGrantRequest(BaseModel):
    """
    申请下载授权 Grant 请求模型

    仅需 file_id，无需 operation_type（下载授权仅用于文件下载操作）。

    Attributes:
        file_id: 文件唯一标识符（UUID 格式）

    Example:
        >>> request = InitDownloadGrantRequest(
        ...     file_id="550e8400-e29b-41d4-a716-446655440000"
        ... )
    """
    file_id: str = Field(..., min_length=1, description="文件唯一标识符")


class DownloadGrantCancelRequest(BaseModel):
    """
    取消下载授权 Grant 请求模型

    Attributes:
        download_grant: 需要取消的下载授权 Token

    Example:
        >>> request = DownloadGrantCancelRequest(
        ...     download_grant="dgt_v1.a1b2c3d4...e5f6..."
        ... )
    """
    download_grant: str = Field(..., min_length=1, description="下载授权 Token")


class DownloadGrantReleaseRequest(BaseModel):
    """
    释放下载授权 Grant 请求模型（下载完成后调用）

    Attributes:
        download_grant: 需要释放的下载授权 Token

    Example:
        >>> request = DownloadGrantReleaseRequest(
        ...     download_grant="dgt_v1.a1b2c3d4...e5f6..."
        ... )
    """
    download_grant: str = Field(..., min_length=1, description="下载授权 Token")


class DownloadGrantResponse(BaseModel):
    """
    下载授权 Grant 响应模型

    Attributes:
        download_grant: 颁发的下载授权 Token
        expires_at: 过期时间（毫秒时间戳）
        max_parallel_chunks: 最大并行分块数
        file_name: 文件名
        file_size: 文件大小 (bytes)

    Example:
        >>> response = DownloadGrantResponse(
        ...     download_grant="dgt_v1.a1b2c3d4...",
        ...     expires_at=1700000000000,
        ...     max_parallel_chunks=4,
        ...     file_name="report.pdf",
        ...     file_size=1024000
        ... )
    """
    download_grant: str
    expires_at: int
    max_parallel_chunks: int
    file_name: str
    file_size: int


class DownloadGrantStatusResponse(BaseModel):
    """
    下载授权 Grant 状态响应模型

    Attributes:
        status: Grant 状态 (ACTIVE/COMPLETED/CANCELLED)
        file_id: 文件 ID
        file_name: 文件名
        file_size: 文件大小 (bytes)
        issued_at: 颁发时间（毫秒时间戳）
        expires_at: 过期时间（毫秒时间戳）
        max_parallel_chunks: 最大并行分块数
    """
    status: str
    file_id: Optional[str] = None
    file_name: Optional[str] = None
    file_size: int = 0
    issued_at: int = 0
    expires_at: int = 0
    max_parallel_chunks: int = 0


class FolderFileItem(BaseModel):
    """
    文件夹下载文件项模型

    Attributes:
        file_id: 文件唯一标识符
        file_name: 文件名称
        file_size: 文件大小 (bytes)
        storage_path: 文件在存储服务中的绝对路径
    """
    file_id: str
    file_name: str
    file_size: int = Field(ge=0, description="文件大小 (bytes)")
    storage_path: str


class FolderDownloadRequest(BaseModel):
    """
    文件夹下载请求模型

    Attributes:
        node_name: 文件夹名称（用作 zip 包内根目录名）
        files: 文件列表

    Example:
        >>> request = FolderDownloadRequest(
        ...     node_name="我的文档",
        ...     files=[
        ...         FolderFileItem(
        ...             file_id="uuid-1",
        ...             file_name="报告.pdf",
        ...             file_size=1024000,
        ...             storage_path="/data/files/uuid-1.pdf"
        ...         )
        ...     ]
        ... )
    """
    node_name: str = Field(..., min_length=1, max_length=255, description="文件夹名称")
    files: List[FolderFileItem] = Field(..., min_length=1, max_length=10000, description="文件列表")
