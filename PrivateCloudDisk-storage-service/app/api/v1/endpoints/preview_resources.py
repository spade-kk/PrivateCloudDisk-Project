"""统一预览资源 API。

所有查询先校验文件归属，再由数据库元数据定位资源；接口不再通过扫描本地目录判断资源是否存在。
"""
from __future__ import annotations

import mimetypes
import hashlib
import aiofiles
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, Response, status
from fastapi.responses import FileResponse, StreamingResponse

from app.core.business_service_client import BusinessServiceError, business_service_client
from app.core.preview_grant import get_preview_metadata
from app.core.preview_grant_limiter import preview_grant_limiter
from app.services.preview_resource_service import preview_resource_service
from core.config import settings

router = APIRouter(prefix="/files/files", tags=["预览资源"])


async def _owned_file(file_id: str, user_id: str) -> dict:
    try:
        response = await business_service_client.get_file_metadata(file_id, user_id)
        return response.get("data") or {}
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="文件不存在或无权访问") from exc


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

    AUDIT FIX [5.2]（需求五-5）：
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

    path = Path(resource["storage_path"])
    if not path.is_file():
        raise HTTPException(status_code=status.HTTP_410_GONE, detail="文档预览图记录存在但文件缺失")

    etag = hashlib.sha256(
        f"{resource.get('resource_id')}:{resource.get('source_version')}:{path.stat().st_mtime_ns}".encode()
    ).hexdigest()
    if request.headers.get("if-none-match", "").strip('"') == etag:
        return Response(status_code=status.HTTP_304_NOT_MODIFIED, headers={"ETag": f'"{etag}"'})

    return FileResponse(
        path,
        media_type="image/jpeg",
        filename=f"{file_id}-{size}.jpg",
        content_disposition_type="inline",
        headers={
            "Cache-Control": "private, max-age=86400",
            "ETag": f'"{etag}"',
            "X-Preview-Variant": size,
        },
    )


@router.get("/{file_id}/document-content", summary="读取 PDF 或 Office 转换后的专用预览资源")
async def get_document_content(
    file_id: str,
    resource_type: str = Query(..., pattern="^(office_pdf|pdf)$"),
    variant: str = Query(default="default"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    需求三-1：转换资源保留专用接口，不再与源文件 preview-content 混用。

    Office 页面读取 office_pdf，PDF 页面读取已登记的 pdf 资源；存在性仍以数据库台账为准。
    """
    metadata = await _owned_file(file_id, user_id)
    resource = await preview_resource_service.get_ready(file_id, user_id, resource_type, variant)
    if not resource:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="文档预览资源尚未生成")
    path = Path(resource["storage_path"])
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
    需求三-1/2/3/4：只读取 Preview Token 已授权的原始文件，并支持受限 HTTP Range。

    原行为会在无专用资源时直接回退 storage_path，且同时承担 Office/PDF 转换资源读取；
    新行为只使用颁发 Token 时缓存的源文件元数据，不扫描预览目录、不发布下载事件、
    不写最近访问记录。单 Range 最大值独立受 preview_max_range_bytes 限制。
    """
    grant = request.state.preview_grant_data
    metadata = await get_preview_metadata(preview_grant)
    if not metadata:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="预览授权元数据已过期，请刷新后重试")

    path = Path(str(metadata.get("storage_path") or "")).resolve()
    upload_root = Path(settings.file_upload_dir).resolve()
    try:
        path.relative_to(upload_root)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="文件存储路径不在允许的预览范围内") from exc
    if not path.is_file():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="原始文件不存在")

    actual_size = path.stat().st_size
    granted_size = int(grant.get("fileSize") or 0)
    size_limit = int(grant.get("sizeLimit") or 0)
    if actual_size != granted_size or actual_size > size_limit:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="文件内容已变化或超过预览限制，请刷新页面重新授权",
        )

    media_type = str(metadata.get("response_mime") or mimetypes.guess_type(path.name)[0] or "application/octet-stream")
    headers = {
        "Accept-Ranges": "bytes",
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
    }
    if not range_header:
        return FileResponse(
            path,
            media_type=media_type,
            filename=str(metadata.get("file_name") or path.name),
            content_disposition_type="inline",
            headers=headers,
        )

    unit, separator, ranges = range_header.partition("=")
    if unit.strip().lower() != "bytes" or not separator or "," in ranges:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="仅支持单个 bytes Range")
    start_text, dash, end_text = ranges.strip().partition("-")
    if not dash:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Range 格式无效")
    try:
        if not start_text:
            suffix_length = int(end_text)
            if suffix_length <= 0:
                raise ValueError
            start = max(0, actual_size - suffix_length)
            end = actual_size - 1
        else:
            start = int(start_text)
            end = int(end_text) if end_text else actual_size - 1
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Range 边界必须为非负整数") from exc

    if start < 0 or start >= actual_size or end < start:
        raise HTTPException(
            status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
            detail="Range 超出文件范围",
            headers={"Content-Range": f"bytes */{actual_size}"},
        )
    end = min(end, actual_size - 1)
    content_length = end - start + 1
    if content_length > settings.preview_max_range_bytes:
        raise HTTPException(
            status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
            detail="单次预览 Range 超过允许上限",
            headers={
                "Content-Range": f"bytes */{actual_size}",
                "X-Max-Range-Size": str(settings.preview_max_range_bytes),
            },
        )

    async def file_iterator():
        """按固定小块异步读取，客户端断开时由 StreamingResponse 结束生成器并释放文件句柄。"""
        async with aiofiles.open(path, "rb") as source:
            await source.seek(start)
            remaining = content_length
            while remaining > 0:
                chunk = await source.read(min(64 * 1024, remaining))
                if not chunk:
                    break
                remaining -= len(chunk)
                yield chunk

    headers.update({
        "Content-Range": f"bytes {start}-{end}/{actual_size}",
        "Content-Length": str(content_length),
    })
    return StreamingResponse(file_iterator(), status_code=status.HTTP_206_PARTIAL_CONTENT, media_type=media_type, headers=headers)


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
    path = Path(resource["storage_path"])
    if not path.is_file():
        raise HTTPException(status_code=status.HTTP_410_GONE, detail="预览资源记录存在但文件缺失")
    import json
    return {"code": 200, "data": json.loads(path.read_text(encoding="utf-8"))}
