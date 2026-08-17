"""激活后云插件只读内容 Broker。

需求来源：云插件双入口语义。file.available 后原始内容已冻结，Runtime 只允许通过
该受信接口读取最终版本；Storage 会再次向 Platform 校验用户、空间和文件归属。
该路由不挂载到公网 Gateway，且绝不返回物理存储路径。
"""
from __future__ import annotations

import hmac
import uuid
from pathlib import Path

from fastapi import APIRouter, Header, HTTPException, status
from fastapi.responses import FileResponse

from app.api.internal.preprocess_broker import _validated_local_path
from app.core.business_service_client import BusinessServiceError, business_service_client
from core.config import settings

router = APIRouter(prefix="/internal/v1/automation", tags=["内部自动化 Broker"])


def _verify_service_token(value: str | None) -> None:
    expected = settings.plugin_runtime_internal_token or settings.pcd_internal_service_token
    if not expected:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="自动化 Broker 尚未配置内部服务凭证",
        )
    if not value or not hmac.compare_digest(value, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="内部服务认证失败")


@router.get("/files/{file_id}/content", include_in_schema=False)
async def read_active_file_content(
    file_id: str,
    x_pcd_service_token: str | None = Header(default=None, alias="X-PCD-Service-Token"),
    x_pcd_execution_id: str | None = Header(default=None, alias="X-PCD-Execution-Id"),
    x_pcd_actor_user_id: str | None = Header(default=None, alias="X-PCD-Actor-User-Id"),
    x_space_id: str | None = Header(default=None, alias="X-Space-Id"),
):
    """读取已激活文件最终内容；不存在、无权与越界均按最小信息原则返回。"""
    _verify_service_token(x_pcd_service_token)
    try:
        uuid.UUID(file_id)
        uuid.UUID(x_pcd_execution_id or "")
        uuid.UUID(x_pcd_actor_user_id or "")
        if x_space_id:
            uuid.UUID(x_space_id)
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="自动化执行上下文格式无效",
        ) from exc

    try:
        response = await business_service_client.get_file_metadata(
            file_id=file_id,
            user_id=x_pcd_actor_user_id or "",
            space_id=x_space_id,
            space_operation="READ",
        )
    except BusinessServiceError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="文件不存在或无读取权限",
        ) from exc
    metadata = response.get("data") or {}
    locator = str(metadata.get("storage_path") or "")
    if not locator:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="文件内容不可用")
    path: Path = _validated_local_path(locator)
    if not path.is_file():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="文件内容不存在")
    return FileResponse(
        path=path,
        media_type="application/octet-stream",
        filename="input.bin",
        headers={
            "Cache-Control": "no-store",
            "X-Content-Type-Options": "nosniff",
            "X-PCD-Content-Frozen": "true",
            "X-PCD-Content-SHA256": str(metadata.get("checksum") or ""),
        },
    )
