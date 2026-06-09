"""
Pydantic 数据模型定义
定义 API 请求和响应的数据结构
"""
from typing import Literal
from pydantic import BaseModel


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
