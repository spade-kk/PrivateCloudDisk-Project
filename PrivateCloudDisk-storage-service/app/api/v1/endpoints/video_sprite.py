"""
视频雪碧图/缩略图预览 API 端点
提供视频进度条悬停预览所需的雪碧图信息

API 列表:
- GET /files/video/sprite/{file_id}  — 获取视频雪碧图信息（含配置和 URL）
"""
from __future__ import annotations
import logging
import json
import os
import math
from pathlib import Path
from fastapi import APIRouter, Header, Query, HTTPException, status

from app.core.business_service_client import BusinessServiceError, business_service_client
from app.services.preview_resource_service import preview_resource_service
from core.config import settings

logger = logging.getLogger("video_sprite")

router = APIRouter(prefix="/files/video/sprite", tags=["视频雪碧图"])


@router.get("/{file_id}", summary="获取视频雪碧图信息")
async def get_video_sprite_info(
    file_id: str,
    user_id: str = Header(..., alias="X-User-Id"),
    token: str = Query(default=..., description="HLS 流媒体访问 Token（用于生成雪碧图/VTT 的鉴权 URL）"),
):
    """
    获取视频雪碧图信息

    雪碧图用于进度条悬停时显示缩略图预览（类似 YouTube/Bilibili 体验）。

    返回:
    - sprite_url: 雪碧图图片 URL（带 token 鉴权参数）
    - sprite_vtt_url: VTT 元数据 URL（带 token 鉴权参数）
    - sprite_image: 雪碧图图片 URL（VTT 兼容字段）
    - config: 雪碧图配置（列数、行数、间隔、缩略图尺寸）

    若雪碧图未生成，返回空数据但 code=200（不阻塞播放）。
    """
    # AUDIT FIX [4.2]：雪碧图原先只按 file_id 拼本地路径，绕过了空间三元组校验；
    # 现在先在业务服务校验 READ + (space_id,file_id) 归属，再读取预览资源。
    try:
        await business_service_client.get_file_metadata(file_id, user_id)
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="视频不存在或无权访问") from exc
    # 默认空结果
    empty_result = {
        "code": 200,
        "data": {
            "sprite_url": None,
            "sprite_vtt_url": None,
            "sprite_image": None,
            "config": {
                "cols": 10,
                "rows": 10,
                "interval": 10,
                "width": 160,
                "height": 90,
            },
        },
    }
    # AUDIT FIX [3.1]：数据库预览资源台账是存在性事实源；只在已登记的 HLS 根目录
    # 内读取 manifest，避免多实例环境扫描本机固定 uploads/hls/{file_id} 目录。
    hls_resource = await preview_resource_service.get_ready(file_id, user_id, "hls", "master")
    if not hls_resource:
        return empty_result
    hls_dir = str(Path(hls_resource["storage_path"]).resolve())
    try:
        Path(hls_dir).relative_to(Path(settings.file_upload_dir).resolve())
    except ValueError as exc:
        raise HTTPException(status_code=403, detail="HLS 资源路径不在允许范围内") from exc
    manifest_path = os.path.join(hls_dir, "manifest.json")

    # 确保有有效的 token 用于生成雪碧图/VTT URL
    effective_token = token
    if not effective_token:
        # 尝试从 manifest 或其他方式获取
        effective_token = ""

    if not os.path.exists(manifest_path):
        return empty_result

    try:
        with open(manifest_path, "r") as f:
            manifest = json.load(f)
    except Exception:
        return empty_result

    sprite_config = manifest.get("sprite", {})
    if not sprite_config:
        return empty_result

    base_url = f"/api/v1/files/video/stream/{file_id}"
    token_param = f"?token={effective_token}" if effective_token else ""

    return {
        "code": 200,
        "data": {
            "sprite_url": f"{base_url}/sprite.jpg{token_param}",
            "sprite_vtt_url": f"{base_url}/sprite.vtt{token_param}",
            "sprite_image": f"{base_url}/sprite.jpg{token_param}",
            "config": {
                "cols": sprite_config.get("cols", 10),
                "rows": sprite_config.get("rows", 10),
                "interval": sprite_config.get("interval", 10),
                "width": sprite_config.get("thumb_width", 160),
                "height": sprite_config.get("thumb_height", 90),
            },
        },
    }
