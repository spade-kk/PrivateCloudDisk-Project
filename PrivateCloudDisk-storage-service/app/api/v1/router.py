"""
API v1 路由聚合
将所有端点路由聚合到一起
"""
from fastapi import APIRouter

from app.api.v1.endpoints import (
    operation_tokens, files, uploads, tasks, download_grants,
    video_stream, video_progress, video_sprite, video_subtitle,
    video_history, video_player,
    preview_resources, preview_grants,
)


# 创建 v1 版本路由器（路由前缀由各端点模块自行定义，此处仅做聚合）
api_router = APIRouter()

# 注册各模块路由
api_router.include_router(operation_tokens.router)
api_router.include_router(files.router)
api_router.include_router(uploads.router)
api_router.include_router(tasks.router)
api_router.include_router(download_grants.router)
api_router.include_router(video_stream.router)
api_router.include_router(video_progress.router)
api_router.include_router(video_sprite.router)
api_router.include_router(video_subtitle.router)
api_router.include_router(video_history.router)
api_router.include_router(video_player.router)
api_router.include_router(preview_resources.router)
api_router.include_router(preview_grants.router)
