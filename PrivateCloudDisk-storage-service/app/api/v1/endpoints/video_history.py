"""
视频观看历史 API 端点
记录和管理用户观看视频的历史

API 列表:
- POST /files/video/history/{file_id}  — 记录观看历史
- GET  /files/video/history            — 获取观看历史列表（预留）
"""
from __future__ import annotations
import logging
import json
from fastapi import APIRouter, Header, HTTPException, status, Query
from pydantic import BaseModel, Field

from app.core.redis_client import redis_client
from app.core.business_service_client import BusinessServiceError, business_service_client
from app.repositories.preview_resource_repository import preview_resource_repository
from app.core.space_context import get_current_space_id
from app.repositories.video_progress_repository import video_progress_repository

logger = logging.getLogger("video_history")

router = APIRouter(prefix="/files/video/history", tags=["视频观看历史"])

# Redis Key 前缀
_HISTORY_KEY_PREFIX = "video:history"
_HISTORY_LIST_KEY = "video:history:list"  # ZSET: user_id -> {file_id: timestamp}


class RecordHistoryRequest(BaseModel):
    """记录观看历史请求"""
    watched_duration: float = Field(..., ge=0, description="已观看时长（秒）")
    total_duration: float = Field(..., ge=0, description="视频总时长（秒）")
    completed: bool = Field(default=False, description="是否完整看完")
    file_name: str = Field(default="", max_length=512, description="文件名快照")


@router.post("/{file_id}", summary="记录视频观看历史")
async def record_video_history(
    file_id: str,
    body: RecordHistoryRequest,
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    记录视频观看历史

    同步写入数据库并用 Redis ZSET 维护最近访问热榜。
    """
    import time

    now = time.time()

    try:
        metadata_response = await business_service_client.get_file_metadata(file_id, user_id)
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="视频不存在或无权访问") from exc
    metadata = metadata_response.get("data") or {}
    file_name = body.file_name or metadata.get("name") or metadata.get("file_name") or ""

    # AUDIT FIX [7.4]: 观看历史与进度共用幂等持久表，Redis 只保留排序缓存。
    await video_progress_repository.save(
        user_id=user_id, file_id=file_id, file_name=file_name,
        current_time=body.watched_duration, duration=body.total_duration,
        resolution="auto", playback_rate=1.0, completed=body.completed,
    )

    # 存储详细历史记录
    detail_key = f"{_HISTORY_KEY_PREFIX}:{user_id}:{file_id}"
    detail = {
        "watched_duration": body.watched_duration,
        "total_duration": body.total_duration,
        "completed": body.completed,
        "updated_at": __import__("datetime").datetime.now().isoformat(),
        "_ts": now,
        "file_name": file_name,
    }

    # 使用管道批量操作
    try:
        pipe = redis_client.pipeline()
        pipe.setex(detail_key, 90 * 24 * 3600, json.dumps(detail, ensure_ascii=False))

        # 添加到用户的观看列表（ZSET，按时间排序）
        list_key = f"{_HISTORY_LIST_KEY}:{user_id}"
        pipe.zadd(list_key, {file_id: now})
        pipe.zremrangebyrank(list_key, 0, -501)  # 保留最近 500 条
        await pipe.execute()
    except Exception as exc:
        logger.warning("观看历史已持久化，但 Redis 最近访问缓存更新失败: %s", exc)

    logger.debug(f"记录观看历史: user={user_id}, file={file_id}, completed={body.completed}")

    return {"code": 200, "message": "观看历史已记录"}


@router.get("", summary="获取观看历史列表")
async def get_video_history_list(
    user_id: str = Header(..., alias="X-User-Id"),
    limit: int = Query(default=20, ge=1, le=100, description="返回条数"),
    offset: int = Query(default=0, ge=0, description="偏移量"),
):
    """
    获取用户的视频观看历史列表

    按观看时间倒序排列，返回最近观看的视频 ID 列表。
    """
    items, total = await video_progress_repository.list_history(user_id, limit, offset)
    for item in items:
        item["thumbnail_url"] = f"/api/v1/files/video/stream/{item['file_id']}/thumbnail?size=small"

    return {
        "code": 200,
        "data": {
            "items": items,
            "total": total,
        },
    }


@router.get("/statistics", summary="获取账号视频资源统计")
async def get_video_statistics(user_id: str = Header(..., alias="X-User-Id")):
    """返回数据库中已就绪的 HLS 视频数，支持多实例实时一致读取。"""
    # 空间管理能力全量集成（需求四-2/五-10）：统计仅覆盖当前空间，旧客户端无空间头时保持个人维度。
    total = await preview_resource_repository.count_ready_videos(
        user_id, get_current_space_id(),
    )
    return {"code": 200, "data": {"playable_video_count": total}}
