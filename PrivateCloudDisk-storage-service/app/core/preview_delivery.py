"""预览台账资源响应适配器。

需求三-5：普通文件和分享资源的 Office/图片缩略图都来自同一预览资源台账。
本模块集中处理路径边界、ETag、304 和缓存头；分享路由只负责把文件名替换为
虚拟资源 ID，避免复制一套缩略图响应逻辑。
"""
from __future__ import annotations

import hashlib

from fastapi import HTTPException, Request, status
from fastapi.responses import FileResponse, Response

from app.core.file_delivery import safe_storage_path


def serve_preview_resource(
    resource: dict,
    *,
    request: Request | None = None,
    filename: str,
    media_type: str | None = None,
    cache_control: str = "private, max-age=86400",
    variant: str | None = None,
):
    """返回数据库台账中的预览资源，并统一处理缓存和路径安全。"""
    path = safe_storage_path(str(resource.get("storage_path") or ""))
    if not path.is_file():
        raise HTTPException(status_code=status.HTTP_410_GONE, detail="预览资源记录存在但文件缺失")
    etag = hashlib.sha256(
        f"{resource.get('resource_id')}:{resource.get('source_version')}:{path.stat().st_mtime_ns}".encode()
    ).hexdigest()
    if request is not None and request.headers.get("if-none-match", "").strip('"') == etag:
        return Response(status_code=status.HTTP_304_NOT_MODIFIED, headers={"ETag": f'"{etag}"'})
    headers = {"Cache-Control": cache_control, "ETag": f'"{etag}"'}
    if variant:
        headers["X-Preview-Variant"] = variant
    return FileResponse(
        path,
        media_type=media_type or resource.get("mime_type") or "image/jpeg",
        filename=filename,
        content_disposition_type="inline",
        headers=headers,
    )
