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


@router.post("/{file_id}", summary="记录视频观看历史")
async def record_video_history(
    file_id: str,
    body: RecordHistoryRequest,
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    记录视频观看历史

    存储到 Redis ZSET，按观看时间排序。
    保留最近 500 条记录。
    """
    import time

    now = time.time()

    # 存储详细历史记录
    detail_key = f"{_HISTORY_KEY_PREFIX}:{user_id}:{file_id}"
    detail = {
        "watched_duration": body.watched_duration,
        "total_duration": body.total_duration,
        "completed": body.completed,
        "updated_at": __import__("datetime").datetime.now().isoformat(),
        "_ts": now,
    }

    # 使用管道批量操作
    pipe = redis_client.pipeline()
    pipe.setex(detail_key, 90 * 24 * 3600, json.dumps(detail, ensure_ascii=False))

    # 添加到用户的观看列表（ZSET，按时间排序）
    list_key = f"{_HISTORY_LIST_KEY}:{user_id}"
    pipe.zadd(list_key, {file_id: now})
    pipe.zremrangebyrank(list_key, 0, -501)  # 保留最近 500 条

    await pipe.execute()

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
    list_key = f"{_HISTORY_LIST_KEY}:{user_id}"

    # 获取所有 file_id（按时间倒序）
    results = await redis_client.zrevrange(list_key, offset, offset + limit - 1, withscores=True)

    items = []
    for file_id, timestamp in results:
        # 获取详细信息
        detail_key = f"{_HISTORY_KEY_PREFIX}:{user_id}:{file_id}"
        raw = await redis_client.get(detail_key)
        detail = {}
        if raw:
            try:
                detail = json.loads(raw)
            except json.JSONDecodeError:
                pass

        items.append({
            "file_id": file_id,
            "watched_duration": detail.get("watched_duration", 0),
            "total_duration": detail.get("total_duration", 0),
            "completed": detail.get("completed", False),
            "updated_at": detail.get("updated_at"),
        })

    return {
        "code": 200,
        "data": {
            "items": items,
            "total": await redis_client.zcard(list_key),
        },
    }