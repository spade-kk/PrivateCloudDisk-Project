"""
API v1 路由聚合
将所有端点路由聚合到一起
"""
from fastapi import APIRouter

from app.api.v1.endpoints import operation_tokens, files, uploads, tasks, download_grants, video_stream


# 创建 v1 版本路由器（路由前缀由各端点模块自行定义，此处仅做聚合）
api_router = APIRouter()

# 注册各模块路由
api_router.include_router(operation_tokens.router)
api_router.include_router(files.router)
api_router.include_router(uploads.router)
api_router.include_router(tasks.router)
api_router.include_router(download_grants.router)
api_router.include_router(video_stream.router)
