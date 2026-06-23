"""
操作凭证 API 端点（已迁移至 Opaque Token 体系）

旧版 JWT 操作凭证接口已废弃，下载操作请使用 download_grants 模块。

原端点映射：
  POST   /files/operation-tokens  →  POST /files/download-grants
  DELETE /files/operation-tokens  →  DELETE /files/download-grants

保留此文件仅用于兼容性路由定义，实际业务逻辑已迁移。
"""
from fastapi import APIRouter


# 创建路由器（空路由，保留模块结构）
router = APIRouter(tags=["操作凭证（已废弃）"])