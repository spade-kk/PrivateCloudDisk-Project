"""分享资源的 HLS 专用访问入口。

分享页面只传 share_resource_id 和分享临时访问令牌；本模块在签发 HLS
令牌时由主业务服务解析虚拟 ID，播放清单/分片 URL 始终保留分享资源边界，
不向客户端暴露真实 file_id。
"""

from pathlib import Path
from typing import Optional

import aiofiles
from fastapi import APIRouter, Body, Header, HTTPException, Query
from fastapi.responses import FileResponse, Response

from app.api.v1.endpoints.video_stream import (
    _resolve_hls_child,
)
from app.core.file_delivery import safe_storage_path
from app.core.share_access import resolve_share_file
from app.core.share_hls_token import issue_share_hls_token, verify_share_hls_token
from app.services.preview_resource_service import preview_resource_service

router = APIRouter(prefix="/files/share", tags=["分享视频流"])


async def _resolve_share_video(
    share_token: str,
    share_resource_id: str,
    access_token: str,
    user_id: str,
) -> dict:
    # AUDIT FIX [3.1]：HLS 授权入口与普通分享预览资源共用虚拟资源解析。
    return await resolve_share_file(
        share_token, share_resource_id, access_token, user_id, operation="READ",
    )


async def _share_hls_root(share_token: str, share_resource_id: str, token: str) -> tuple[dict, Path]:
    # AUDIT FIX [3.6]：分享 HLS 使用 Redis opaque token，令牌 URL 不再携带真实 file_id。
    payload = await verify_share_hls_token(token, share_token, share_resource_id)
    resources = await preview_resource_service.get_ready(
        payload["file_id"], payload["user_id"], "hls", "master", space_id=payload.get("space_id") or None,
    )
    if not resources:
        raise HTTPException(status_code=404, detail="HLS 资源尚未生成")
    root = safe_storage_path(str(resources["storage_path"]), require_file=False)
    if not root.is_dir():
        raise HTTPException(status_code=404, detail="HLS 资源目录不存在")
    return payload, root


@router.get("/{share_token}/resources/{share_resource_id}/video/info")
async def share_video_info(
    share_token: str,
    share_resource_id: str,
    access_token: str = Header(..., alias="X-Share-Access-Token"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    """返回分享视频的播放信息；响应中的资源身份始终是虚拟 ID。"""
    context = await _resolve_share_video(share_token, share_resource_id, access_token, user_id)
    resource = await preview_resource_service.get_ready(
        str(context["file_id"]), user_id, "hls", "master", space_id=context.get("space_id") or None,
    )
    base = f"/api/v1/files/share/{share_token}/resources/{share_resource_id}/video"
    if not resource:
        return {"code": 200, "data": {
            "share_resource_id": share_resource_id,
            "file_name": context.get("file_name", ""),
            "has_hls": False,
            "hls_url": None,
            "preview_url": None,
        }}
    manifest = resource.get("metadata") or {}
    return {"code": 200, "data": {
        "share_resource_id": share_resource_id,
        "file_name": context.get("file_name", ""),
        "file_size": int(context.get("file_size") or 0),
        "has_hls": True,
        "hls_url": f"{base}/master.m3u8",
        "preview_url": f"{base}/thumbnail?size=poster",
        "resolutions": manifest.get("resolutions", []),
        "duration": resource.get("duration_seconds", 0),
        "width": resource.get("width", 0),
        "height": resource.get("height", 0),
    }}


@router.post("/{share_token}/resources/{share_resource_id}/video/token")
async def create_share_video_token(
    share_token: str,
    share_resource_id: str,
    body: dict | None = Body(default=None),
    access_token: str = Header(..., alias="X-Share-Access-Token"),
    user_id: str = Header(..., alias="X-User-Id"),
    space_id: Optional[str] = Header(default=None, alias="X-Space-Id"),
):
    data = await _resolve_share_video(share_token, share_resource_id, access_token, user_id)
    hls = await preview_resource_service.get_ready(
        str(data["file_id"]), user_id, "hls", "master", space_id=data.get("space_id") or space_id,
    )
    if not hls:
        raise HTTPException(status_code=409, detail="HLS 转码尚未完成")
    expires_in = int((body or {}).get("expires_in", 3600))
    expires_in = min(max(expires_in, 60), 3600)
    token = await issue_share_hls_token(
        file_id=str(data["file_id"]),
        user_id=user_id,
        space_id=data.get("space_id") or space_id,
        share_token=share_token,
        share_resource_id=share_resource_id,
        expires_in=expires_in,
    )
    base = f"/api/v1/files/share/{share_token}/resources/{share_resource_id}/video"
    return {"code": 200, "data": {"token": token, "hls_master": f"{base}/master.m3u8?token={token}", "expires_in": expires_in}}


@router.get("/{share_token}/resources/{share_resource_id}/video/master.m3u8")
async def share_master_playlist(share_token: str, share_resource_id: str, token: str = Query(...)):
    _, root = await _share_hls_root(share_token, share_resource_id, token)
    path = _resolve_hls_child(root, "master.m3u8")
    if not path.is_file():
        raise HTTPException(status_code=404, detail="HLS 播放列表不存在")
    async with aiofiles.open(path, "r", encoding="utf-8") as source:
        content = await source.read()
    base = f"/api/v1/files/share/{share_token}/resources/{share_resource_id}/video"
    lines = []
    for line in content.splitlines():
        lines.append(f"{base}/{line.strip()}?token={token}" if line.strip() and not line.startswith("#") else line)
    return Response("\n".join(lines), media_type="application/vnd.apple.mpegurl", headers={"Cache-Control": "no-cache", "Access-Control-Allow-Origin": "*"})


@router.get("/{share_token}/resources/{share_resource_id}/video/{resolution}/index.m3u8")
async def share_variant_playlist(share_token: str, share_resource_id: str, resolution: str, token: str = Query(...)):
    _, root = await _share_hls_root(share_token, share_resource_id, token)
    path = _resolve_hls_child(root, resolution, "index.m3u8")
    if not path.is_file():
        raise HTTPException(status_code=404, detail="HLS 分辨率播放列表不存在")
    async with aiofiles.open(path, "r", encoding="utf-8") as source:
        content = await source.read()
    base = f"/api/v1/files/share/{share_token}/resources/{share_resource_id}/video/{resolution}"
    lines = [f"{base}/{line.strip()}?token={token}" if line.strip() and not line.startswith("#") else line for line in content.splitlines()]
    return Response("\n".join(lines), media_type="application/vnd.apple.mpegurl", headers={"Cache-Control": "no-cache", "Access-Control-Allow-Origin": "*"})


@router.get("/{share_token}/resources/{share_resource_id}/video/{resolution}/{segment}")
async def share_segment(share_token: str, share_resource_id: str, resolution: str, segment: str, token: str = Query(...)):
    if not segment.endswith(".ts") or "/" in segment or "\\" in segment:
        raise HTTPException(status_code=400, detail="非法的视频分片路径")
    _, root = await _share_hls_root(share_token, share_resource_id, token)
    path = _resolve_hls_child(root, resolution, segment)
    if not path.is_file():
        raise HTTPException(status_code=404, detail="视频分片不存在")
    return FileResponse(path, media_type="video/mp2t", headers={"Cache-Control": "private, max-age=60", "Access-Control-Allow-Origin": "*"})


@router.get("/{share_token}/resources/{share_resource_id}/video/sprite.jpg")
async def share_sprite(share_token: str, share_resource_id: str, token: str = Query(...)):
    _, root = await _share_hls_root(share_token, share_resource_id, token)
    path = _resolve_hls_child(root, "sprite.jpg")
    if not path.is_file():
        raise HTTPException(status_code=404, detail="视频雪碧图尚未生成")
    return FileResponse(path, media_type="image/jpeg", headers={"Cache-Control": "private, max-age=300"})


@router.get("/{share_token}/resources/{share_resource_id}/video/sprite.vtt")
async def share_sprite_vtt(share_token: str, share_resource_id: str, token: str = Query(...)):
    _, root = await _share_hls_root(share_token, share_resource_id, token)
    path = _resolve_hls_child(root, "sprite.vtt")
    if not path.is_file():
        raise HTTPException(status_code=404, detail="视频 VTT 尚未生成")
    return FileResponse(path, media_type="text/vtt", headers={"Cache-Control": "private, max-age=300"})


@router.get("/{share_token}/resources/{share_resource_id}/video/thumbnail")
async def share_video_thumbnail(
    share_token: str,
    share_resource_id: str,
    token: str = Query(...),
    size: str = Query(default="poster"),
):
    if size not in {"poster", "large", "medium", "small"}:
        raise HTTPException(status_code=400, detail="无效的视频预览图规格")
    _, root = await _share_hls_root(share_token, share_resource_id, token)
    candidates = [f"thumbnail-{size}.jpg", f"poster-{size}.jpg", "thumbnail.jpg", "poster.jpg"]
    path = next((_resolve_hls_child(root, name) for name in candidates if _resolve_hls_child(root, name).is_file()), None)
    if not path:
        raise HTTPException(status_code=404, detail="视频首帧预览图尚未生成")
    return FileResponse(path, media_type="image/jpeg", headers={"Cache-Control": "private, max-age=300"})
