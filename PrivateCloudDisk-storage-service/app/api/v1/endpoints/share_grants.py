"""分享资源授权与内容读取接口。

需求二/三：分享页只暴露 share_resource_id（真实 UUID 或加密虚拟 ID），
文件服务不把 file_id 回传给客户端。授权申请时由主业务服务校验分享访问令牌
和资源范围，文件服务再把结果绑定到独立的 Opaque Grant；内容读取复用现有
Range/路径边界逻辑，但通过分享资源 ID 路径消费，不与普通 file_id 接口混用。
"""
from fastapi import APIRouter, Depends, Header, HTTPException, Request, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app.core.file_delivery import serve_authorized_file
from app.core.grant_limiter import enforce_grant_limits
from app.core.download_grant import (
    get_cached_file_metadata,
    issue_download_grant,
    verify_download_grant_for_share,
)
from app.core.share_access import resolve_share_file, to_grant_metadata
from app.core.preview_grant import (
    get_preview_metadata,
    issue_preview_grant,
    verify_preview_grant_for_share,
)
from app.core.redis_client import redis_client
from app.utils.helpers import get_client_ip
from core.config import settings

router = APIRouter(prefix="/files/share", tags=["分享资源授权"])


class ShareGrantRequest(BaseModel):
    """分享授权请求；真实文件 ID 只在服务间响应中存在。"""

    share_resource_id: str = Field(..., min_length=1, max_length=512)


async def _resolve_share(
    share_token: str,
    resource_id: str,
    access_token: str,
    operation: str,
    user_id: str = "",
) -> dict:
    # AUDIT FIX [3.1]：分享入口只做虚拟 ID + 分享访问令牌解析，统一复用边界适配器。
    return await resolve_share_file(share_token, resource_id, access_token, user_id, operation=operation)


async def _issue_preview(
    user_id: str,
    client_ip: str,
    share_token: str,
    resource_id: str,
    access_token: str,
) -> tuple[str, dict]:
    data = await _resolve_share(share_token, resource_id, access_token, "READ", user_id)
    metadata = to_grant_metadata(data)
    return await issue_preview_grant(
        user_id,
        str(data["file_id"]),
        client_ip,
        metadata=metadata,
        grant_source="share",
        share_token=share_token,
        share_resource_id=resource_id,
    )


async def _issue_download(
    user_id: str,
    client_ip: str,
    share_token: str,
    resource_id: str,
    access_token: str,
) -> str:
    data = await _resolve_share(share_token, resource_id, access_token, "DOWNLOAD", user_id)
    metadata = to_grant_metadata(data)
    return await issue_download_grant(
        user_id=user_id,
        file_id=str(data["file_id"]),
        client_ip=client_ip,
        metadata=metadata,
        grant_source="share",
        share_token=share_token,
        share_resource_id=resource_id,
    )


class _SharePreviewLimiter:
    async def __call__(
        self,
        request: Request,
        share_token: str,
        share_resource_id: str,
        preview_grant: str = Header(..., alias="X-Preview-Grant"),
        user_id: str = Header(..., alias="X-User-Id"),
    ):
        grant = await verify_preview_grant_for_share(
            preview_grant, user_id, share_token, share_resource_id,
        )
        # AUDIT FIX [3.4]：分享预览只替换 Grant 的分享边界校验，限流和释放
        # 与普通 PreviewGrant 完全复用，避免两个入口的配额策略漂移。
        async for _ in enforce_grant_limits(
            request,
            grant,
            kind="preview",
            max_concurrent=settings.preview_grant_max_concurrent,
            max_requests=settings.preview_grant_max_requests,
            rate_per_sec=settings.preview_grant_rate_per_sec,
            ttl_seconds=settings.preview_grant_ttl_seconds,
            state_attr="share_preview_grant_data",
        ):
            yield


class _ShareDownloadLimiter:
    async def __call__(
        self,
        request: Request,
        share_token: str,
        share_resource_id: str,
        download_grant: str = Header(..., alias="X-Download-Grant"),
        user_id: str = Header(..., alias="X-User-Id"),
    ):
        grant = await verify_download_grant_for_share(
            download_grant, user_id, share_token, share_resource_id,
        )
        # AUDIT FIX [3.4]：分享下载沿用普通 DownloadGrant 的请求次数、速率和
        # 并发释放机制，只有 verify 函数增加 share_resource_id 绑定。
        async for _ in enforce_grant_limits(
            request,
            grant,
            kind="download",
            max_concurrent=settings.max_concurrent,
            max_requests=settings.max_requests_per_operation_token,
            rate_per_sec=settings.rate_per_sec,
            ttl_seconds=settings.operation_token_expire_seconds,
            state_attr="share_download_grant_data",
        ):
            yield


share_preview_limiter = _SharePreviewLimiter()
share_download_limiter = _ShareDownloadLimiter()


@router.post("/{share_token}/preview-grants", status_code=status.HTTP_201_CREATED)
async def create_share_preview_grant(
    share_token: str,
    body: ShareGrantRequest,
    request: Request,
    access_token: str = Header(..., alias="X-Share-Access-Token"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    """登录用户通过分享访问令牌申请原始内容预览授权。"""
    token, grant = await _issue_preview(
        user_id, get_client_ip(request), share_token, body.share_resource_id, access_token,
    )
    return JSONResponse({
        "code": 200,
        "data": {
            "preview_grant": token,
            "expires_at": int(grant["expiresAt"]),
            "file_name": grant["fileName"],
            "file_size": int(grant["fileSize"]),
            "preview_kind": grant["previewKind"],
            "share_resource_id": body.share_resource_id,
        },
        "message": None,
    })


@router.post("/{share_token}/download-grants", status_code=status.HTTP_201_CREATED)
async def create_share_download_grant(
    share_token: str,
    body: ShareGrantRequest,
    request: Request,
    access_token: str = Header(..., alias="X-Share-Access-Token"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    """登录用户通过分享访问令牌申请下载授权；不返回真实 file_id。"""
    token = await _issue_download(
        user_id, get_client_ip(request), share_token, body.share_resource_id, access_token,
    )
    from app.core.download_grant import _hash_token, _token_key
    grant = await redis_client.hgetall(_token_key(_hash_token(token)))
    return JSONResponse({
        "code": 200,
        "data": {
            "download_grant": token,
            "expires_at": int(grant.get("expiresAt", 0)),
            "max_parallel_chunks": int(grant.get("maxParallelChunks", 4)),
            "file_name": grant.get("fileName", ""),
            "file_size": int(grant.get("fileSize", 0)),
            "share_resource_id": body.share_resource_id,
        },
        "message": None,
    })

@router.get("/{share_token}/resources/{share_resource_id}/preview-content")
async def get_share_preview_content(
    share_token: str,
    share_resource_id: str,
    request: Request,
    _: None = Depends(share_preview_limiter),
):
    """分享预览内容读取；令牌依赖在函数内显式校验以支持虚拟资源 ID。"""
    grant_token = request.headers.get("X-Preview-Grant", "")
    grant = request.state.share_preview_grant_data
    metadata = await get_preview_metadata(grant_token)
    if not metadata:
        raise HTTPException(status_code=401, detail="预览授权元数据已过期，请刷新后重试")
    return await serve_authorized_file(
        str(metadata.get("storage_path") or ""),
        file_name=str(metadata.get("file_name") or ""),
        media_type=str(metadata.get("response_mime") or "") or None,
        range_header=request.headers.get("Range"),
        max_range_bytes=settings.preview_max_range_bytes,
        expected_size=int(grant.get("fileSize") or 0),
        content_disposition_type="inline",
        extra_headers={"X-Content-Type-Options": "nosniff"},
    )


@router.get("/{share_token}/resources/{share_resource_id}/content")
async def get_share_download_content(
    share_token: str,
    share_resource_id: str,
    request: Request,
    _: None = Depends(share_download_limiter),
):
    """分享下载内容读取；必须同时匹配用户、分享令牌和虚拟资源 ID。"""
    grant_token = request.headers.get("X-Download-Grant", "")
    grant = request.state.share_download_grant_data
    metadata = await get_cached_file_metadata(grant_token)
    if not metadata:
        raise HTTPException(status_code=401, detail="下载授权元数据已过期，请重新授权")
    return await serve_authorized_file(
        str(metadata.get("storage_path") or ""),
        file_name=str(metadata.get("file_name") or ""),
        range_header=request.headers.get("Range"),
        max_range_bytes=settings.max_range_bytes,
        max_full_bytes=settings.max_range_bytes,
        expected_size=int(grant.get("fileSize") or 0),
        content_disposition_type="attachment",
    )
