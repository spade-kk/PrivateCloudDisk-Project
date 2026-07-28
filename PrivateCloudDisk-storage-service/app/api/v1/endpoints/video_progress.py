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
from app.core.business_service_client import BusinessServiceError, business_service_client
from app.repositories.video_progress_repository import video_progress_repository
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
    file_name: str = Field(default="", max_length=512, description="文件名快照，用于观看历史展示")


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

    数据库持久化后再更新 Redis 热点缓存；缓存失效不会造成进度丢失。
    """
    key = f"{_PROGRESS_KEY_PREFIX}:{user_id}:{file_id}"

    try:
        metadata_response = await business_service_client.get_file_metadata(file_id, user_id)
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="视频不存在或无权访问") from exc
    metadata = metadata_response.get("data") or {}

    # 读取已有进度，避免重复写入
    try:
        existing = await redis_client.get(key)
    except Exception as exc:
        logger.warning("Redis 播放进度缓存不可用，本次直接持久化: %s", exc)
        existing = None
    if existing:
        try:
            old = json.loads(existing)
            time_diff = abs(body.current_time - old.get("current_time", 0))
            now = __import__("time").time()
            old_ts = old.get("_ts", 0)
            if old.get("_persisted") and time_diff < 5 and (now - old_ts) < 60:
                # 进度变化太小且时间间隔短，跳过缓存抖动；持久层已有上次有效值。
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
        "file_name": body.file_name or metadata.get("name") or metadata.get("file_name") or "",
        "_persisted": True,
    }

    completed = body.duration > 0 and body.current_time >= max(body.duration - 5, body.duration * 0.95)
    # AUDIT FIX [7.4]: 先提交数据库，再刷新 Redis；不再把 30 天 TTL 缓存当作业务事实。
    await video_progress_repository.save(
        user_id=user_id,
        file_id=file_id,
        file_name=data["file_name"],
        current_time=body.current_time,
        duration=body.duration,
        resolution=body.resolution,
        playback_rate=body.playback_rate,
        completed=completed,
    )
    # TTL: 30 天，仅用于热点读取。
    try:
        await redis_client.setex(key, 30 * 24 * 3600, json.dumps(data, ensure_ascii=False))
    except Exception as exc:
        logger.warning("播放进度已持久化，但 Redis 缓存写入失败: %s", exc)

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
    try:
        raw = await redis_client.get(key)
    except Exception as exc:
        logger.warning("Redis 播放进度缓存不可用，降级读取数据库: %s", exc)
        raw = None

    if not raw:
        persisted = await video_progress_repository.get(user_id, file_id)
        if persisted:
            cache_data = {**persisted, "_ts": __import__("time").time(), "_persisted": True}
            try:
                await redis_client.setex(key, 30 * 24 * 3600, json.dumps(cache_data, ensure_ascii=False))
            except Exception:
                pass
            return {"code": 200, "data": persisted}
        return {"code": 200, "data": {"current_time": 0, "duration": 0, "resolution": "auto", "playback_rate": 1.0, "updated_at": None}}

    try:
        data = json.loads(raw)
        # AUDIT FIX [7.4]: 首次读取旧版 Redis-only 进度时同步补写数据库，完成平滑迁移。
        if not data.get("_persisted"):
            await video_progress_repository.save(
                user_id=user_id,
                file_id=file_id,
                file_name=data.get("file_name", ""),
                current_time=float(data.get("current_time", 0)),
                duration=float(data.get("duration", 0)),
                resolution=data.get("resolution", "auto"),
                playback_rate=float(data.get("playback_rate", 1.0)),
                completed=False,
            )
            data["_persisted"] = True
            try:
                await redis_client.setex(key, 30 * 24 * 3600, json.dumps(data, ensure_ascii=False))
            except Exception:
                pass
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
