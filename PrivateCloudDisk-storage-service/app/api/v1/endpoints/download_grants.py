"""
下载授权 Grant 管理端点
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
提供 Download Grant 的完整生命周期管理：

  POST   /files/download-grants           — 申请下载授权（颁发 Opaque Token）
  GET    /files/download-grants/status    — 查询授权状态
  DELETE /files/download-grants           — 取消下载授权
  POST   /files/download-grants/release   — 释放下载授权（下载完成后调用）

与旧 operation_tokens.py 的主要区别：
  - Token 类型：Opaque Token（非 JWT）
  - 仅需 file_id，无需 operation_type
  - 多层级并发限制（用户级 + 用户+IP 级）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""
import time
from fastapi import APIRouter, Request, Header, HTTPException, status
from app.models.schemas import (
    InitDownloadGrantRequest,
    DownloadGrantCancelRequest,
    DownloadGrantReleaseRequest,
    DownloadGrantResponse,
    DownloadGrantStatusResponse,
)
from app.core.download_grant import (
    issue_download_grant,
    cancel_download_grant,
    release_download_grant,
    get_download_grant_status,
    validate_token_format,
    _hash_token,
)
from app.utils.helpers import get_client_ip

router = APIRouter()


@router.post(
    "/files/download-grants",
    summary="申请下载授权",
    response_model=DownloadGrantResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_download_grant(
    req: InitDownloadGrantRequest,
    request: Request,
    user_id: str = Header(..., alias="X-User-Id"),
    session_id: str = Header("", alias="X-Session-Id"),
):
    """
    申请下载授权 Grant（颁发 Opaque Token）

    请求参数：
      - file_id: 文件唯一标识符（必填）

    请求头：
      - X-User-Id: 用户 ID（必填）
      - X-Session-Id: 会话 ID（可选，预留 L3 层级限流）

    多层级并发限制：
      - L1 用户级：最多 15 个活跃 Grant
      - L2 用户+IP 级：最多 5 个活跃 Grant

    响应：
      - download_grant: 下载授权 Token（Opaque Token）
      - expires_at: 过期时间（毫秒时间戳）
      - max_parallel_chunks: 最大并行分块数
      - file_name: 文件名
      - file_size: 文件大小 (bytes)
    """
    client_ip = get_client_ip(request)

    token = await issue_download_grant(
        user_id=user_id,
        file_id=req.file_id,
        client_ip=client_ip,
        session_id=session_id if session_id else None,
    )

    # 获取 grant 信息用于响应
    token_hash = _hash_token(token)
    from app.core.download_grant import _token_key, PREFIX_GRANT_TOKEN
    from app.core.redis_client import redis_client
    token_key = _token_key(token_hash)
    grant_data = await redis_client.hgetall(token_key)

    return DownloadGrantResponse(
        download_grant=token,
        expires_at=int(grant_data.get("expiresAt", 0)),
        max_parallel_chunks=int(grant_data.get("maxParallelChunks", 4)),
        file_name=grant_data.get("fileName", ""),
        file_size=int(grant_data.get("fileSize", 0)),
    )


@router.get(
    "/files/download-grants/status",
    summary="查询下载授权状态",
    response_model=DownloadGrantStatusResponse,
)
async def query_download_grant_status(
    download_grant: str = Header(..., alias="X-Download-Grant"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    查询下载授权 Grant 状态

    请求头：
      - X-Download-Grant: 下载授权 Token（必填）
      - X-User-Id: 用户 ID（必填）

    响应：
      - status: Grant 状态 (ACTIVE/COMPLETED/CANCELLED)
      - file_id: 文件 ID
      - file_name: 文件名
      - file_size: 文件大小
      - other fields ...
    """
    result = await get_download_grant_status(download_grant)
    if result is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Download grant not found"
        )

    return DownloadGrantStatusResponse(
        status=result["status"],
        file_id=result.get("file_id"),
        file_name=result.get("file_name"),
        file_size=result.get("file_size", 0),
        issued_at=result.get("issued_at", 0),
        expires_at=result.get("expires_at", 0),
        max_parallel_chunks=result.get("max_parallel_chunks", 0),
    )


@router.delete(
    "/files/download-grants",
    summary="取消下载授权",
    status_code=status.HTTP_200_OK,
)
async def cancel_download_grant_endpoint(
    req: DownloadGrantCancelRequest,
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    取消下载授权 Grant

    取消一个活跃的下载授权，释放对应的多层级配额。

    请求体：
      - download_grant: 需要取消的下载授权 Token

    请求头：
      - X-User-Id: 用户 ID（必填，用于验证操作权限）

    响应：
      - message: "Download grant cancelled"
    """
    await cancel_download_grant(token=req.download_grant, user_id=user_id)
    return {"message": "Download grant cancelled"}


@router.post(
    "/files/download-grants/release",
    summary="释放下载授权（下载完成）",
    status_code=status.HTTP_200_OK,
)
async def release_download_grant_endpoint(
    req: DownloadGrantReleaseRequest,
):
    """
    释放下载授权 Grant（下载完成后调用）

    标记下载授权为已完成，释放对应的多层级配额。
    客户端在完成文件下载后应调用此接口释放资源。

    请求体：
      - download_grant: 需要释放的下载授权 Token

    响应：
      - message: "Download grant released"
    """
    await release_download_grant(token=req.download_grant)
    return {"message": "Download grant released"}