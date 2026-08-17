"""分享资源的专用预览资源接口。

需求三-5：分享页不能把真实 file_id 直接拼进原有预览资源 URL。此模块只接受
share_resource_id + 临时分享访问令牌，主业务服务完成资源范围与空间校验后，
再复用现有预览资源台账查询和文件响应逻辑；响应中不回传 file_id/storage_path。
"""

import json
from pathlib import Path

from fastapi import APIRouter, Header, HTTPException, Query, Request

from app.core.file_delivery import safe_storage_path
from app.core.preview_delivery import serve_preview_resource
from app.core.share_access import resolve_share_file
from app.services.preview_resource_service import preview_resource_service

router = APIRouter(prefix="/files/share", tags=["分享专用预览资源"])


async def _resolve_share_resource(
    share_token: str,
    share_resource_id: str,
    access_token: str,
    user_id: str,
) -> dict:
    # AUDIT FIX [3.1]：所有分享预览资源统一走同一个虚拟资源解析适配器。
    return await resolve_share_file(
        share_token, share_resource_id, access_token, user_id, operation="READ",
    )


def _public_resource(resource: dict, share_resource_id: str) -> dict:
    """删除预览台账中的内部定位字段，保持分享资源边界。"""
    return {
        "resource_type": resource.get("resource_type"),
        "resource_variant": resource.get("resource_variant"),
        "mime_type": resource.get("mime_type"),
        "resource_status": resource.get("resource_status"),
        "size_bytes": resource.get("size_bytes", 0),
        "width": resource.get("width"),
        "height": resource.get("height"),
        "duration_seconds": resource.get("duration_seconds"),
        "page_count": resource.get("page_count"),
        "metadata": resource.get("metadata") or {},
        "share_resource_id": share_resource_id,
    }


def _safe_preview_path(raw_path: str) -> Path:
    # AUDIT FIX [3.2]：普通预览与分享预览共用同一存储路径边界检查。
    return safe_storage_path(raw_path)


@router.get("/{share_token}/resources/{share_resource_id}/preview-resources")
async def list_share_preview_resources(
    share_token: str,
    share_resource_id: str,
    access_token: str = Header(..., alias="X-Share-Access-Token"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    data = await _resolve_share_resource(share_token, share_resource_id, access_token, user_id)
    resources = await preview_resource_service.list_resources(
        str(data["file_id"]), user_id, space_id=data.get("space_id") or None,
    )
    return {
        "code": 200,
        "data": {
            "items": [_public_resource(item, share_resource_id) for item in resources],
            "total": len(resources),
            "share_resource_id": share_resource_id,
        },
    }


@router.get("/{share_token}/resources/{share_resource_id}/document-thumbnail")
async def get_share_document_thumbnail(
    share_token: str,
    share_resource_id: str,
    request: Request,
    size: str = Query(default="small"),
    access_token: str = Header(..., alias="X-Share-Access-Token"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    if size not in {"original", "large", "medium", "small"}:
        raise HTTPException(status_code=400, detail="无效的文档预览图规格")
    data = await _resolve_share_resource(share_token, share_resource_id, access_token, user_id)
    resource = await preview_resource_service.get_ready(
        str(data["file_id"]), user_id, "office_thumbnail", size, space_id=data.get("space_id") or None,
    )
    if not resource:
        raise HTTPException(status_code=404, detail="文档预览图尚未生成")
    # AUDIT FIX [3.5]：分享 Office/PDF 封面只替换对外文件名，台账响应逻辑复用普通接口。
    return serve_preview_resource(
        resource,
        request=request,
        filename=f"{share_resource_id}-{size}.jpg",
        media_type="image/jpeg",
        variant=size,
    )


@router.get("/{share_token}/resources/{share_resource_id}/thumbnail")
async def get_share_thumbnail(
    share_token: str,
    share_resource_id: str,
    request: Request,
    size: str = Query(default="small"),
    access_token: str = Header(..., alias="X-Share-Access-Token"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    """读取图片/通用文件缩略图；只接受分享资源 ID，不回传真实文件 ID。"""
    if size not in {"original", "large", "medium", "small"}:
        raise HTTPException(status_code=400, detail="无效的缩略图规格")
    data = await _resolve_share_resource(share_token, share_resource_id, access_token, user_id)
    resource = await preview_resource_service.get_ready(
        str(data["file_id"]), user_id, "thumbnail", size,
        space_id=data.get("space_id") or None,
    )
    if not resource:
        raise HTTPException(status_code=404, detail="文件缩略图尚未生成")
    return serve_preview_resource(
        resource,
        request=request,
        filename=f"{share_resource_id}-{size}.jpg",
    )


@router.get("/{share_token}/resources/{share_resource_id}/archive-preview-status")
async def share_archive_preview_status(
    share_token: str,
    share_resource_id: str,
    access_token: str = Header(..., alias="X-Share-Access-Token"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    data = await _resolve_share_resource(share_token, share_resource_id, access_token, user_id)
    resource = await preview_resource_service.get_ready(
        str(data["file_id"]), user_id, "archive", "tree", space_id=data.get("space_id") or None,
    )
    return {"code": 200, "data": {"share_resource_id": share_resource_id, "status": "completed" if resource else "pending", "ready": bool(resource)}}


@router.get("/{share_token}/resources/{share_resource_id}/archive-tree")
async def share_archive_tree(
    share_token: str,
    share_resource_id: str,
    access_token: str = Header(..., alias="X-Share-Access-Token"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    data = await _resolve_share_resource(share_token, share_resource_id, access_token, user_id)
    resource = await preview_resource_service.get_ready(
        str(data["file_id"]), user_id, "archive", "tree", space_id=data.get("space_id") or None,
    )
    if not resource:
        raise HTTPException(status_code=404, detail="压缩包目录尚未解析")
    path = _safe_preview_path(str(resource["storage_path"]))
    if not path.is_file():
        raise HTTPException(status_code=410, detail="预览资源记录存在但文件缺失")
    return {"code": 200, "data": {"share_resource_id": share_resource_id, "tree": json.loads(path.read_text(encoding="utf-8"))}}
