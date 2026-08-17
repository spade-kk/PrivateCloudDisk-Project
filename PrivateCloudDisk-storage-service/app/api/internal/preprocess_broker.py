"""Plugin Runtime 使用的文件内容候选 Broker。

需求来源：插件生态与自动化工作流平台 / 沙箱安全边界。
该路由禁止经 Gateway 暴露。Runtime 需要同时提供服务凭证和 ready 事件中的一次性
content lease；任何一个缺失都不能读取原始内容或提交候选内容。
"""
from __future__ import annotations

import hashlib
import hmac
import os
import tempfile
import uuid
from pathlib import Path

from fastapi import APIRouter, Header, HTTPException, Request, status
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from app.repositories.file_preprocess_repository import file_preprocess_repository
from core.config import settings

router = APIRouter(prefix="/internal/v1/preprocess-gates", tags=["内部预处理 Broker"])


class LeaseExchangeRequest(BaseModel):
    """Runtime 使用 MQ 引用换取执行级短期 Lease。"""

    execution_id: str = Field(pattern=r"^[0-9a-fA-F-]{36}$")
    content_lease_ref: str = Field(min_length=32, max_length=256)
    ttl_seconds: int = Field(default=120, ge=1, le=120)


def _verify_service_token(value: str | None) -> None:
    """验证 Runtime 服务身份；未配置 Secret 时默认关闭接口。"""
    # Sprint 0 向后兼容：可为 Runtime 配置独立凭证；未单独配置时复用统一服务凭证。
    expected = settings.plugin_runtime_internal_token or settings.pcd_internal_service_token
    if not expected:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="预处理 Broker 尚未配置内部服务凭证",
        )
    if not value or not hmac.compare_digest(value, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="内部服务认证失败")


async def _authorize(
    gate_id: str,
    service_token: str | None,
    content_lease: str | None,
    execution_id: str | None,
) -> dict:
    _verify_service_token(service_token)
    if not content_lease:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="缺少内容 Lease")
    if not execution_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="缺少执行标识")
    lease_hash = hashlib.sha256(content_lease.encode("utf-8")).hexdigest()
    gate = await file_preprocess_repository.authorize_content_lease(
        gate_id, execution_id, lease_hash
    )
    if not gate:
        # 不区分 ID 不存在、过期或状态已关闭，避免内部资源枚举。
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="预处理闸门不可用或已过期",
        )
    return gate


def _validated_local_path(locator: str) -> Path:
    """限制物理访问范围，防止数据库污染或路径穿越扩大到 Uploads 根目录之外。"""
    uploads_root = Path(settings.file_upload_dir).resolve()
    candidate = Path(locator).resolve()
    try:
        candidate.relative_to(uploads_root)
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="存储定位符越界",
        ) from exc
    return candidate


@router.post("/{gate_id}/lease-exchange", include_in_schema=False)
async def exchange_content_lease(
    gate_id: str,
    body: LeaseExchangeRequest,
    x_pcd_service_token: str | None = Header(default=None, alias="X-PCD-Service-Token"),
):
    """将 ready 事件的一次性引用兑换为绑定 execution_id 的短期执行 Lease。"""
    _verify_service_token(x_pcd_service_token)
    try:
        uuid.UUID(gate_id)
        uuid.UUID(body.execution_id)
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Gate 或 execution_id 格式无效",
        ) from exc
    reference_hash = hashlib.sha256(
        body.content_lease_ref.encode("utf-8")
    ).hexdigest()
    exchanged = await file_preprocess_repository.exchange_content_lease(
        gate_id=gate_id,
        execution_id=body.execution_id,
        content_lease_ref_hash=reference_hash,
        requested_ttl_seconds=body.ttl_seconds,
    )
    if not exchanged:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="内容 Lease 引用无效、已使用或 Gate 已关闭",
        )
    return exchanged


@router.get("/{gate_id}/content", include_in_schema=False)
async def read_original_content(
    gate_id: str,
    x_pcd_service_token: str | None = Header(default=None, alias="X-PCD-Service-Token"),
    x_content_lease: str | None = Header(default=None, alias="X-Content-Lease"),
    x_pcd_execution_id: str | None = Header(default=None, alias="X-PCD-Execution-Id"),
):
    """向受信 Runtime 流式返回本次预处理的不可变原始内容。"""
    gate = await _authorize(
        gate_id, x_pcd_service_token, x_content_lease, x_pcd_execution_id
    )
    original_path = _validated_local_path(gate["original_locator"])
    if not original_path.is_file():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="原始暂存内容不存在")
    return FileResponse(
        path=original_path,
        media_type="application/octet-stream",
        filename="input.bin",
        headers={
            "Cache-Control": "no-store",
            "X-Content-Type-Options": "nosniff",
            "X-PCD-Upload-Checksum": gate["upload_checksum"],
        },
    )


@router.put("/{gate_id}/candidate", include_in_schema=False)
async def write_candidate_content(
    gate_id: str,
    request: Request,
    x_pcd_service_token: str | None = Header(default=None, alias="X-PCD-Service-Token"),
    x_content_lease: str | None = Header(default=None, alias="X-Content-Lease"),
    x_pcd_execution_id: str | None = Header(default=None, alias="X-PCD-Execution-Id"),
):
    """流式接收最终候选内容并原子封存。

    原始合并文件从不被覆盖。写入先落在同一文件系统的临时文件，完成大小与 SHA-256
    校验后执行 ``os.replace``；进程崩溃最多留下未登记临时文件，不会破坏 original。
    """
    gate = await _authorize(
        gate_id, x_pcd_service_token, x_content_lease, x_pcd_execution_id
    )
    if gate.get("candidate_id"):
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="候选内容已提交")

    original_path = _validated_local_path(gate["original_locator"])
    candidate_dir = original_path.parent / ".preprocess" / gate_id
    candidate_path = candidate_dir / "candidate.cloud"
    candidate_dir.mkdir(parents=True, exist_ok=True)

    ratio_limit = max(
        int(int(gate["original_size"]) * settings.file_preprocess_candidate_max_expansion_ratio),
        int(gate["original_size"]),
    )
    max_bytes = min(settings.file_preprocess_candidate_max_bytes, ratio_limit)
    digest = hashlib.sha256()
    written = 0
    file_descriptor, temporary_name = tempfile.mkstemp(
        prefix="candidate-",
        suffix=".tmp",
        dir=str(candidate_dir),
    )
    try:
        with os.fdopen(file_descriptor, "wb") as output:
            async for chunk in request.stream():
                if not chunk:
                    continue
                written += len(chunk)
                if written > max_bytes:
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail="候选内容超过允许大小",
                    )
                output.write(chunk)
                digest.update(chunk)
            output.flush()
            os.fsync(output.fileno())
        if written <= 0:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="候选内容不能为空",
            )
        os.replace(temporary_name, candidate_path)
        candidate_id = f"candidate_{uuid.uuid4().hex}"
        checksum = digest.hexdigest()
        registered = await file_preprocess_repository.register_candidate(
            gate_id=gate_id,
            candidate_id=candidate_id,
            candidate_locator=str(candidate_path),
            checksum=checksum,
            size=written,
        )
        if not registered:
            candidate_path.unlink(missing_ok=True)
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="预处理闸门已关闭，候选内容未被接受",
            )
        return {
            "candidate_id": candidate_id,
            "candidate_checksum": checksum,
            "candidate_size": written,
        }
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)
