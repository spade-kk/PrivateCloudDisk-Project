"""
限流模块
提供多维度的请求限流功能
"""
from fastapi import Request, HTTPException, status
from core.config import settings
from app.core.redis_client import redis_client, enforce_fixed_window
from app.utils.helpers import stable_hash


# 限流配置
OPERATION_TOKEN_ISSUE_FILE_LIMIT = settings.operation_token_issue_file_limit
OPERATION_TOKEN_ISSUE_FILE_WINDOW_SECONDS = settings.operation_token_issue_file_window_seconds
OPERATION_TOKEN_DESTROY_USER_LIMIT = settings.operation_token_destroy_user_limit
OPERATION_TOKEN_DESTROY_USER_WINDOW_SECONDS = settings.operation_token_destroy_user_window_seconds
OPERATION_TOKEN_DESTROY_IP_LIMIT = settings.operation_token_destroy_ip_limit
OPERATION_TOKEN_DESTROY_IP_WINDOW_SECONDS = settings.operation_token_destroy_ip_window_seconds


async def enforce_operation_token_issue_limits(req, user_id: str, client_ip: str):
    """
    操作凭证申请限流检查

    基于文件和用户的组合进行限流，防止单个用户对同一文件频繁申请操作凭证

    限流维度：
    - 用户 ID + 文件 ID + 操作类型

    Args:
        req: 申请操作凭证请求模型，包含 file_id 和 operation_type
        user_id: 用户唯一标识符
        client_ip: 客户端 IP 地址

    Raises:
        HTTPException: 当请求超过限制时抛出 429 状态码异常
    """
    file_fingerprint = stable_hash(f"{req.file_id}|{req.operation_type}")
    await enforce_fixed_window(
        f"rl:operation_token:issue:file:{stable_hash(user_id)}:{file_fingerprint}",
        OPERATION_TOKEN_ISSUE_FILE_LIMIT,
        OPERATION_TOKEN_ISSUE_FILE_WINDOW_SECONDS,
        "Operation token requests are too frequent for this file"
    )


async def enforce_operation_token_destroy_limits(user_id: str, client_ip: str):
    """
    操作凭证销毁限流检查

    基于用户和 IP 两个维度进行限流，防止恶意销毁操作

    限流维度：
    1. 用户维度：限制单用户的销毁频率
    2. IP 维度：限制单 IP 的销毁频率

    Args:
        user_id: 用户唯一标识符
        client_ip: 客户端 IP 地址

    Raises:
        HTTPException: 当请求超过限制时抛出 429 状态码异常
    """
    await enforce_fixed_window(
        f"rl:operation_token:destroy:user:{stable_hash(user_id)}",
        OPERATION_TOKEN_DESTROY_USER_LIMIT,
        OPERATION_TOKEN_DESTROY_USER_WINDOW_SECONDS,
        "Operation token destroy requests are too frequent for this user"
    )
    await enforce_fixed_window(
        f"rl:operation_token:destroy:ip:{stable_hash(client_ip)}",
        OPERATION_TOKEN_DESTROY_IP_LIMIT,
        OPERATION_TOKEN_DESTROY_IP_WINDOW_SECONDS,
        "Operation token destroy requests are too frequent from this client"
    )
