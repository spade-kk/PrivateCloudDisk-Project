"""
操作凭证 API 端点
提供文件操作凭证的申请和销毁接口
"""
import time
import uuid
import json
import jwt
import requests
from fastapi import APIRouter, Header, Request, Query, HTTPException, status
from fastapi.responses import JSONResponse
from typing import Optional

from app.models.schemas import InitOperationTokenRequest, OperationTokenCancelRequest
from core.config import settings
from core.security import verify_operation_token, PRIVATE_KEY, OPERATION_TOKEN_EXPIRE_SECONDS
from core.redis_client import redis_client
from core.rate_limiter import (
    enforce_operation_token_issue_limits,
    enforce_operation_token_destroy_limits,
    MAX_REQUESTS_PER_OPERATION_TOKEN
)
from app.utils.helpers import get_client_ip


# 创建路由器
router = APIRouter(tags=["操作凭证"])

# 业务服务地址
BUSINESS_SERVICE_URL = settings.business_service_url


@router.post("/files/operation-tokens", summary="申请操作凭证")
async def init_operation(
    req: InitOperationTokenRequest,
    request: Request,
    user_id: str = Header(..., alias="X-User-Id")
):
    """
    申请文件操作凭证
    
    功能说明：
    为用户申请一个临时的文件操作凭证（JWT），用于后续的文件下载、预览或流式播放操作。
    操作凭证包含用户信息、文件信息、操作类型、有效期和请求次数限制。
    
    业务流程：
    1. 执行限流检查（基于用户+文件+操作类型）
    2. 调用业务服务验证文件是否存在且用户有权限访问
    3. 生成 JWT 操作凭证
    4. 将文件元数据缓存到 Redis（用于后续请求加速）
    5. 返回操作凭证
    
    限流策略：
    - 基于用户ID + 文件ID + 操作类型的组合限流
    - 防止单个用户对同一文件频繁申请凭证
    
    Args:
        req: 申请凭证请求体
            - file_id: 文件唯一标识符（UUID格式）
            - operation_type: 操作类型（download/preview/stream）
        request: FastAPI 请求对象
        user_id: 用户唯一标识符（从 X-User-Id 请求头获取）
    
    Returns:
        JSONResponse: 
            - code: 200 表示成功
            - data.operation_token: JWT 操作凭证字符串
            - message: 错误信息（成功时为 null）
    
    Raises:
        HTTPException:
            - 429: 请求过于频繁
            - 503: 业务服务不可用
            - 404: 文件不存在或用户无权限
    
    Example:
        POST /files/operation-tokens
        Headers: X-User-Id: user123
        Body: {"file_id": "xxx", "operation_type": "download"}
        
        Response:
        {
            "code": 200,
            "data": {"operation_token": "eyJhbGciOiJSUzI1NiIs..."},
            "message": null
        }
    """
    # 1. 限流检查
    await enforce_operation_token_issue_limits(req, user_id, get_client_ip(request))
    
    # 2. 调用业务服务验证文件权限
    try:
        response = requests.get(
            f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/files/{req.file_id}?uid={user_id}",
            timeout=5
        )
        response.raise_for_status()
        result = response.json()
    except requests.RequestException:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Business service unavailable"
        )

    if result["code"] != 200:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="下载文件不存在用户网盘, 或者路径目录不存在"
        )

    # 3. 生成 JWT 操作凭证
    now = int(time.time())
    payload = {
        "sub": user_id,
        "file_id": req.file_id,
        "operation_type": req.operation_type,
        "jti": str(uuid.uuid4()),
        "iat": now,
        "exp": now + OPERATION_TOKEN_EXPIRE_SECONDS,
        "rlimit": MAX_REQUESTS_PER_OPERATION_TOKEN,
    }
    token = jwt.encode(payload, PRIVATE_KEY, algorithm="RS256")

    # 4. 缓存文件元数据到 Redis
    sub = payload["sub"]
    jti = payload["jti"]
    file_storage_path = result["data"]["storage_path"]
    await redis_client.setex(
        f"operation_token_meta:{sub}:{jti}",
        OPERATION_TOKEN_EXPIRE_SECONDS + 30,
        json.dumps({
            "storage_path": file_storage_path,
            "file_size": result["data"]["size"],
            "file_name": result["data"]["name"]
        })
    )

    # 5. 返回操作凭证
    return JSONResponse({
        "code": 200,
        "data": {
            "operation_token": token
        },
        "message": None
    })


@router.delete("/files/operation-tokens", summary="销毁操作凭证")
@router.delete("/files/operation-tokens/", summary="销毁操作凭证")
async def destroy_operation_token(
    request: Request,
    req: Optional[OperationTokenCancelRequest] = None,
    operation_token: Optional[str] = Query(None),
    user_id: str = Header(..., alias="X-User-Id")
):
    """
    销毁文件操作凭证
    
    功能说明：
    主动销毁一个操作凭证，使其立即失效。销毁后，使用该凭证的所有请求都将被拒绝。
    
    业务流程：
    1. 执行限流检查（基于用户和IP两个维度）
    2. 获取操作凭证（从请求体或查询参数）
    3. 验证操作凭证有效性
    4. 验证用户身份匹配
    5. 将凭证标记为已撤销（存入Redis）
    6. 清理凭证相关的缓存数据
    
    限流策略：
    - 用户维度：限制单用户的销毁频率
    - IP维度：限制单IP的销毁频率
    
    Args:
        request: FastAPI 请求对象
        req: 销毁凭证请求体（可选）
            - operation_token: 需要销毁的操作凭证
        operation_token: 操作凭证查询参数（可选）
        user_id: 用户唯一标识符（从 X-User-Id 请求头获取）
    
    Returns:
        JSONResponse:
            - code: 200 表示成功
            - data: null
            - message: 错误信息（成功时为 null）
    
    Raises:
        HTTPException:
            - 400: 缺少操作凭证参数
            - 401: 操作凭证无效或已过期
            - 403: 用户身份不匹配
            - 429: 请求过于频繁
    
    Example:
        DELETE /files/operation-tokens?operation_token=xxx
        Headers: X-User-Id: user123
        
        Response:
        {
            "code": 200,
            "data": null,
            "message": null
        }
    """
    # 1. 限流检查
    client_ip = get_client_ip(request)
    await enforce_operation_token_destroy_limits(user_id, client_ip)

    # 2. 获取操作凭证
    token = operation_token or (req.operation_token if req else None)
    if not token:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="operation_token is required"
        )

    # 3. 验证操作凭证
    payload = await verify_operation_token(token)
    if payload["sub"] != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Operation token user mismatch"
        )

    # 4. 标记凭证为已撤销
    now = int(time.time())
    ttl = max(1, int(payload["exp"]) - now + 30)
    jti = payload["jti"]
    sub = payload["sub"]
    await redis_client.setex(f"revoked:operation_token:{jti}", ttl, sub)
    
    # 5. 清理凭证相关缓存
    await redis_client.delete(
        f"operation_token_meta:{sub}:{jti}",
        f"total:operation_token:{jti}",
        f"concurrency:operation_token:{jti}"
    )
    
    return JSONResponse({"code": 200, "data": None, "message": None})
