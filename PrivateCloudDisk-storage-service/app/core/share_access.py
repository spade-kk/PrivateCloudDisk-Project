"""分享资源访问边界适配器。

需求三-1/三-5：分享专用路由与普通文件路由的核心差异只有入口和出口：
入口接收 ``share_resource_id + X-Share-Access-Token``，由主业务服务解析
虚拟资源并校验分享范围；出口只回显虚拟 ID。解析成功后的 file_id、space_id
和 storage_path 仅在存储服务内部使用，文件读取、Range、限流和资源台账逻辑
统一复用普通文件实现，避免分享接口复制一套业务规则。
"""
from __future__ import annotations

from fastapi import HTTPException

from app.core.business_service_client import BusinessServiceError, business_service_client


async def resolve_share_file(
    share_token: str,
    share_resource_id: str,
    access_token: str,
    user_id: str,
    *,
    operation: str = "READ",
) -> dict:
    """解析分享虚拟资源，返回仅供服务内部使用的文件访问上下文。"""
    if not access_token:
        raise HTTPException(status_code=401, detail="分享访问令牌不能为空")
    try:
        response = await business_service_client.resolve_share_resource(
            share_token,
            share_resource_id,
            access_token,
            operation=operation,
        )
    except BusinessServiceError as exc:
        # 不把业务服务的 file_id、storage_path 或 SQL 错误泄露给分享访问者。
        raise HTTPException(status_code=exc.status_code, detail="分享资源不存在或访问令牌无效") from exc
    data = response.get("data") or {}
    if not data.get("file_id") or not data.get("storage_path"):
        raise HTTPException(status_code=404, detail="分享资源不存在或已失效")
    # 这些字段只允许在存储服务内部继续向下游传递，HTTP 响应必须经过脱敏。
    return {
        **data,
        "_share_token": share_token,
        "_share_resource_id": share_resource_id,
        "_share_user_id": user_id,
    }


def to_grant_metadata(context: dict) -> dict:
    """把分享解析结果转换为普通 Grant 引擎使用的元数据结构。"""
    return {
        "name": context.get("file_name", ""),
        "size": int(context.get("file_size") or 0),
        "file_type": context.get("file_type", ""),
        "storage_path": context.get("storage_path"),
        "space_id": context.get("space_id", ""),
    }


def public_share_identity(context: dict) -> str:
    """统一获取对外可回显的虚拟资源 ID，禁止调用方自行拼接真实 file_id。"""
    return str(context["_share_resource_id"])
