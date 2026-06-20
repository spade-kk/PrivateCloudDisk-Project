"""
限流模块
提供多维度的请求限流功能
"""
import time
from fastapi import Request, Header, HTTPException, status, Depends
from core.config import settings
from app.core.redis_client import redis_client, check_and_incr_concurrency, release_concurrency, enforce_fixed_window
from app.core.security import verify_operation_token
from app.utils.helpers import get_client_ip, stable_hash


# 限流配置
MAX_CONCURRENT = settings.max_concurrent
RATE_PER_SEC = settings.rate_per_sec
OPERATION_TOKEN_EXPIRE_SECONDS = settings.operation_token_expire_seconds
MAX_REQUESTS_PER_OPERATION_TOKEN = settings.max_requests_per_operation_token
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
    
    Example:
        >>> await enforce_operation_token_issue_limits(req, "user123", "192.168.1.100")
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
    
    Example:
        >>> await enforce_operation_token_destroy_limits("user123", "192.168.1.100")
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


class OperationRateLimiter:
    """
    操作级多维限流器
    
    实现操作级别的多维度限流：
    1. 总请求次数限制：限制单个操作凭证的总请求次数
    2. 每秒请求速率限制：限制单秒内的请求数
    3. 并发连接数限制：限制同时进行的请求数
    
    Attributes:
        max_concurrent: 最大并发连接数
        rate_per_sec: 每秒最大请求数
    
    Example:
        >>> limiter = OperationRateLimiter(max_concurrent=4, rate_per_sec=10)
        >>> # 在路由中使用 Depends(limiter)
    """
    
    def __init__(self, max_concurrent: int = MAX_CONCURRENT, rate_per_sec: int = RATE_PER_SEC):
        """
        初始化限流器
        
        Args:
            max_concurrent: 最大并发连接数，默认从配置读取
            rate_per_sec: 每秒最大请求数，默认从配置读取
        """
        self.max_concurrent = max_concurrent
        self.rate_per_sec = rate_per_sec

    async def __call__(
        self,
        request: Request,
        token: str = Header(..., alias="X-Operation-Token"),
        user_id: str = Header(..., alias="X-User-Id")
    ):
        """
        执行限流检查
        
        限流流程：
        1. 验证 JWT，提取限制信息
        2. 检查总请求次数限制
        3. 检查每秒请求速率限制
        4. 检查并发连接数限制
        5. 将限流信息存储到 request.state 中
        6. 请求完成后释放并发计数
        
        Args:
            request: FastAPI 请求对象
            token: 操作凭证 JWT（从 X-Operation-Token 头获取）
            user_id: 用户 ID（从 X-User-Id 头获取）
        
        Raises:
            HTTPException:
                - 403: 用户不匹配
                - 429: 超过任一限流限制
        
        Yields:
            None: 限流检查通过后继续执行请求
        """
        # 1. 验证 JWT，提取限制信息
        payload = await verify_operation_token(token)
        if payload["sub"] != user_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Operation token user mismatch")
        jti = payload["jti"]
        rlimit = payload["rlimit"]

        # 2. 总请求次数限制
        total_key = f"total:operation_token:{jti}"
        total = await redis_client.incr(total_key)
        if total == 1:
            await redis_client.expire(total_key, OPERATION_TOKEN_EXPIRE_SECONDS + 10)
        if total > rlimit:
            raise HTTPException(
                status_code=429,
                detail="The current number of operation requests has reached the upper limit"
            )

        # 3. 每秒请求速率限制（固定窗口）
        rate_key = f"rate:operation_token:{jti}:{int(time.time())}"
        current_rate = await redis_client.incr(rate_key)
        if current_rate == 1:
            await redis_client.expire(rate_key, 2)
        if current_rate > self.rate_per_sec:
            raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Rate limit exceeded")

        # 4. 并发连接数限制
        concurrency_key = f"concurrency:operation_token:{jti}"
        allowed = await check_and_incr_concurrency(concurrency_key, self.max_concurrent)
        if not allowed:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many concurrent requests for this operation"
            )

        # 存储并发 key 以便释放
        request.state.operation_token_concurrency_key = concurrency_key

        # 将 payload 存入 state 供路由使用
        request.state.operation_token_payload = payload

        try:
            yield
        finally:
            await release_concurrency(concurrency_key)


# 创建默认限流器实例
operation_limiter = OperationRateLimiter(max_concurrent=4)
