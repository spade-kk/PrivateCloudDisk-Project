"""
视频播放进度 API 端点
保存和恢复用户观看视频的播放进度

API 列表:
- POST /files/video/progress/{file_id}  — 保存播放进度
- GET  /files/video/progress/{file_id}  — 获取播放进度
"""
from __future__ import annotations
import logging
import json
from fastapi import APIRouter, Header, HTTPException, status
from pydantic import BaseModel, Field
from typing import Optional

from app.core.redis_client import redis_client
from core.config import settings

logger = logging.getLogger("video_progress")

router = APIRouter(prefix="/files/video/progress", tags=["视频播放进度"])

# Redis Key 前缀
_PROGRESS_KEY_PREFIX = "video:progress"


class SaveProgressRequest(BaseModel):
    """保存播放进度请求"""
    current_time: float = Field(..., ge=0, description="当前播放位置（秒）")
    duration: float = Field(..., ge=0, description="视频总时长（秒）")
    resolution: str = Field(default="auto", description="当前分辨率")
    playback_rate: float = Field(default=1.0, ge=0.25, le=3.0, description="播放速度")


class ProgressResponse(BaseModel):
    """播放进度响应"""
    current_time: float
    duration: float
    resolution: str
    playback_rate: float
    updated_at: str


@router.post("/{file_id}", summary="保存视频播放进度")
async def save_video_progress(
    file_id: str,
    body: SaveProgressRequest,
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    保存用户观看视频的播放进度

    使用 Redis 存储，TTL 30 天。
    自动去重：如果进度差小于 5 秒且时间未超过 60 秒，跳过保存以减少 Redis 写入。
    """
    key = f"{_PROGRESS_KEY_PREFIX}:{user_id}:{file_id}"

    # 读取已有进度，避免重复写入
    existing = await redis_client.get(key)
    if existing:
        try:
            old = json.loads(existing)
            time_diff = abs(body.current_time - old.get("current_time", 0))
            now = __import__("time").time()
            old_ts = old.get("_ts", 0)
            if time_diff < 5 and (now - old_ts) < 60:
                # 进度变化太小且时间间隔短，跳过
                return {"code": 200, "message": "进度无显著变化，已跳过"}
        except (json.JSONDecodeError, KeyError):
            pass

    import time
    data = {
        "current_time": body.current_time,
        "duration": body.duration,
        "resolution": body.resolution,
        "playback_rate": body.playback_rate,
        "updated_at": __import__("datetime").datetime.now().isoformat(),
        "_ts": time.time(),
    }

    # TTL: 30 天
    await redis_client.setex(key, 30 * 24 * 3600, json.dumps(data, ensure_ascii=False))

    logger.debug(f"保存播放进度: user={user_id}, file={file_id}, pos={body.current_time:.1f}s")

    return {"code": 200, "message": "播放进度已保存"}


@router.get("/{file_id}", summary="获取视频播放进度")
async def get_video_progress(
    file_id: str,
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    获取用户上次观看视频的播放进度

    返回上次播放位置、分辨率、播放速度等信息。
    若无历史记录，返回默认值。
    """
    key = f"{_PROGRESS_KEY_PREFIX}:{user_id}:{file_id}"
    raw = await redis_client.get(key)

    if not raw:
        return {
            "code": 200,
            "data": {
                "current_time": 0,
                "duration": 0,
                "resolution": "auto",
                "playback_rate": 1.0,
                "updated_at": None,
            },
        }

    try:
        data = json.loads(raw)
        return {
            "code": 200,
            "data": {
                "current_time": data.get("current_time", 0),
                "duration": data.get("duration", 0),
                "resolution": data.get("resolution", "auto"),
                "playback_rate": data.get("playback_rate", 1.0),
                "updated_at": data.get("updated_at"),
            },
        }
    except json.JSONDecodeError:
        return {
            "code": 200,
            "data": {
                "current_time": 0,
                "duration": 0,
                "resolution": "auto",
                "playback_rate": 1.0,
                "updated_at": None,
            },
        }