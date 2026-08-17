"""分享视频播放进度接口。

需求三-5：分享页面不能把真实 file_id 放到 URL 或请求体；本模块只接收
share_resource_id + 分享临时访问令牌，主业务服务完成边界校验后才使用内部
file_id 持久化进度。响应仅返回进度字段，绝不回显真实 file_id。
"""

from fastapi import APIRouter, Header

from app.core.share_access import resolve_share_file
from app.repositories.video_progress_repository import video_progress_repository
from app.api.v1.endpoints.video_progress import SaveProgressRequest

router = APIRouter(prefix="/files/share", tags=["分享视频播放进度"])


async def _resolve(
    share_token: str,
    share_resource_id: str,
    access_token: str,
    user_id: str = "",
    operation: str = "READ",
) -> dict:
    # AUDIT FIX [3.1]：播放进度只负责调用同一分享边界解析器，内部 file_id 仅用于持久化。
    return await resolve_share_file(
        share_token, share_resource_id, access_token, user_id, operation=operation,
    )


@router.post("/{share_token}/resources/{share_resource_id}/video/progress")
async def save_share_video_progress(
    share_token: str,
    share_resource_id: str,
    body: SaveProgressRequest,
    access_token: str = Header(..., alias="X-Share-Access-Token"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    """保存分享视频进度；下载权限不影响已授权的在线播放进度记录。"""
    data = await _resolve(share_token, share_resource_id, access_token, user_id)
    completed = body.duration > 0 and body.current_time >= max(body.duration - 5, body.duration * 0.95)
    await video_progress_repository.save(
        user_id=user_id,
        file_id=str(data["file_id"]),
        file_name=body.file_name or str(data.get("file_name") or ""),
        current_time=body.current_time,
        duration=body.duration,
        resolution=body.resolution,
        playback_rate=body.playback_rate,
        completed=completed,
        space_id=data.get("space_id") or None,
    )
    return {"code": 200, "message": "分享视频进度已保存", "data": {"share_resource_id": share_resource_id}}


@router.get("/{share_token}/resources/{share_resource_id}/video/progress")
async def get_share_video_progress(
    share_token: str,
    share_resource_id: str,
    access_token: str = Header(..., alias="X-Share-Access-Token"),
    user_id: str = Header(..., alias="X-User-Id"),
):
    """读取分享视频进度；只返回播放状态，不回显内部文件定位。"""
    data = await _resolve(share_token, share_resource_id, access_token, user_id)
    persisted = await video_progress_repository.get(
        user_id, str(data["file_id"]), space_id=data.get("space_id") or None,
    )
    result = persisted or {
        "current_time": 0,
        "duration": 0,
        "resolution": "auto",
        "playback_rate": 1.0,
        "updated_at": None,
    }
    result.pop("file_id", None)
    result["share_resource_id"] = share_resource_id
    return {"code": 200, "data": result}
