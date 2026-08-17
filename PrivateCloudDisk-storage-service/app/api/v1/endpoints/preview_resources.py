"""统一预览资源 API。

所有查询先校验文件归属，再由数据库元数据定位资源；接口不再通过扫描本地目录判断资源是否存在。
"""
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, status
from fastapi.responses import FileResponse

from app.core.business_service_client import BusinessServiceError, business_service_client
from app.core.file_delivery import safe_storage_path, serve_authorized_file
from app.core.preview_delivery import serve_preview_resource
from app.core.preview_grant import get_preview_metadata
from app.core.preview_grant_limiter import preview_grant_limiter
from app.services.preview_resource_service import preview_resource_service
from core.config import settings

router = APIRouter(prefix="/files/files", tags=["预览资源"])


async def _owned_file(file_id: str, user_id: str) -> dict:
    try:
        response = await business_service_client.get_file_metadata(file_id, user_id)
        metadata = response.get("data") or {}
        if not metadata:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="文件不存在或无权访问")
        return metadata
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="文件不存在或无权访问") from exc


def _safe_preview_path(raw_path: str) -> Path:
    """统一限制预览资源只能位于配置的上传根目录内。"""
    return safe_storage_path(raw_path)


@router.get("/{file_id}/preview-resources", summary="查询文件预览资源")
async def list_preview_resources(file_id: str, user_id: str = Header(..., alias="X-User-Id")):
    await _owned_file(file_id, user_id)
    resources = await preview_resource_service.list_resources(file_id, user_id)
    return {"code": 200, "data": {"items": resources, "total": len(resources)}}


@router.get("/{file_id}/preview-info", summary="查询通用文档预览信息")
async def get_preview_info(file_id: str, user_id: str = Header(..., alias="X-User-Id")):
    metadata = await _owned_file(file_id, user_id)
    resources = await preview_resource_service.list_resources(file_id, user_id)
    ready = [item for item in resources if item["resource_status"] == "ready"]
    preferred = next((item for item in ready if item["resource_type"] in {"office_pdf", "pdf"}), None)
    return {
        "code": 200,
        "data": {
            "fileId": file_id,
            "fileName": metadata.get("name") or metadata.get("file_name") or "",
            "status": "completed" if preferred else "pending",
            "previewUrl": f"/api/v1/files/files/{file_id}/document-content?resource_type={preferred['resource_type']}" if preferred else None,
            "metadata": (preferred or {}).get("metadata", {}),
            "resources": ready,
        },
    }


@router.get("/{file_id}/document-thumbnail", summary="读取 Office/PDF 四档封面预览图")
async def get_document_thumbnail(
    file_id: str,
    request: Request,
    size: str = Query(default="small", description="图片规格：original/large/medium/small"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    根据持久化资源台账返回 Office/PDF 首页封面图。
    原实现没有文档缩略图专用接口，前端只能猜测 thumbnails 目录文件名；
    新接口先做所有权校验，再以 file_id + variant 查询数据库，支持 ETag 与长期浏览器缓存。
    """
    if size not in {"original", "large", "medium", "small"}:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="无效的文档预览图规格")

    await _owned_file(file_id, user_id)
    resource = await preview_resource_service.get_ready(
        file_id, user_id, "office_thumbnail", size,
    )
    if not resource:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="文档预览图尚未生成")

    # AUDIT FIX [3.5]：普通 Office/PDF 封面响应与分享封面响应共用台账投递适配器。
    return serve_preview_resource(
        resource,
        request=request,
        filename=f"{file_id}-{size}.jpg",
        media_type="image/jpeg",
        variant=size,
    )


@router.get("/{file_id}/document-content", summary="读取 PDF 或 Office 转换后的专用预览资源")
async def get_document_content(
    file_id: str,
    resource_type: str = Query(..., pattern="^(office_pdf|pdf)$"),
    variant: str = Query(default="default"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    转换资源保留专用接口，不再与源文件 preview-content 混用。

    Office 页面读取 office_pdf，PDF 页面读取已登记的 pdf 资源；存在性仍以数据库台账为准。
    """
    metadata = await _owned_file(file_id, user_id)
    resource = await preview_resource_service.get_ready(file_id, user_id, resource_type, variant)
    if not resource:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="文档预览资源尚未生成")
    path = _safe_preview_path(str(resource["storage_path"]))
    if not path.is_file():
        raise HTTPException(status_code=status.HTTP_410_GONE, detail="文档预览资源记录存在但文件缺失")
    return FileResponse(
        path,
        media_type=resource.get("mime_type") or "application/pdf",
        filename=metadata.get("name") or path.name,
        content_disposition_type="inline",
        headers={"Accept-Ranges": "bytes", "Cache-Control": "private, max-age=300"},
    )


@router.get("/{file_id}/preview-content", summary="使用 Preview Token 临时读取白名单源文件")
async def get_preview_content(
    file_id: str,
    request: Request,
    user_id: str = Header(..., alias="X-User-Id"),
    preview_grant: str = Header(..., alias="X-Preview-Grant"),
    range_header: Optional[str] = Header(default=None, alias="Range"),
    _: None = Depends(preview_grant_limiter),
):
    """
    只读取 Preview Token 已授权的原始文件，并支持受限 HTTP Range。

    原行为会在无专用资源时直接回退 storage_path，且同时承担 Office/PDF 转换资源读取；
    新行为只使用颁发 Token 时缓存的源文件元数据，不扫描预览目录、不发布下载事件、
    不写最近访问记录。单 Range 最大值独立受 preview_max_range_bytes 限制。
    """
    grant = request.state.preview_grant_data
    metadata = await get_preview_metadata(preview_grant)
    if not metadata:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="预览授权元数据已过期，请刷新后重试")

    granted_size = int(grant.get("fileSize") or 0)
    size_limit = int(grant.get("sizeLimit") or 0)
    actual_path = safe_storage_path(str(metadata.get("storage_path") or ""))
    actual_size = actual_path.stat().st_size
    if actual_size != granted_size or actual_size > size_limit:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="文件内容已变化或超过预览限制，请刷新页面重新授权",
        )

    # 原有行为注释保留（回溯）：预览内容按固定小块异步读取，客户端断开时释放
    # 文件句柄；Range 超限返回 416，完整文件仍使用 inline 响应。
    # AUDIT FIX [3.3]：普通预览与分享预览共用同一文件读取核心，授权仍由本路由的
    # PreviewGrantRateLimiter 完成；这里不再维护第二套 Range/异步读取实现。
    return await serve_authorized_file(
        str(metadata.get("storage_path") or ""),
        file_name=str(metadata.get("file_name") or ""),
        media_type=str(metadata.get("response_mime") or "") or None,
        range_header=range_header,
        max_range_bytes=settings.preview_max_range_bytes,
        expected_size=granted_size,
        content_disposition_type="inline",
        extra_headers={"X-Content-Type-Options": "nosniff"},
    )


@router.get("/{file_id}/archive-preview-status", summary="查询压缩包解析状态")
async def archive_preview_status(file_id: str, user_id: str = Header(..., alias="X-User-Id")):
    await _owned_file(file_id, user_id)
    resource = await preview_resource_service.get_ready(file_id, user_id, "archive", "tree")
    return {"code": 200, "data": {"fileId": file_id, "status": "completed" if resource else "pending", "ready": bool(resource)}}


@router.get("/{file_id}/archive-tree", summary="读取压缩包目录树")
async def archive_tree(file_id: str, user_id: str = Header(..., alias="X-User-Id")):
    await _owned_file(file_id, user_id)
    resource = await preview_resource_service.get_ready(file_id, user_id, "archive", "tree")
    if not resource:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="压缩包目录尚未解析")
    path = _safe_preview_path(str(resource["storage_path"]))
    if not path.is_file():
        raise HTTPException(status_code=status.HTTP_410_GONE, detail="预览资源记录存在但文件缺失")
    import json
    return {"code": 200, "data": json.loads(path.read_text(encoding="utf-8"))}
