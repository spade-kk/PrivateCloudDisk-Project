"""
下载授权限流模块
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
提供 DownloadGrantRateLimiter 中间件，基于 Opaque Token 的多维限流。

与旧的 OperationRateLimiter 的区别：
  - Token 类型：Opaque Token（非 JWT）
  - 请求头：X-Download-Grant（替代 X-Operation-Token）
  - 验证方式：Redis 查询（替代 JWT 解码）
  - 限流逻辑：继承原 OperationRateLimiter 的全套限流（总请求次数 + 速率 + 并发）

注册方式（与 files.py 一致）：
  download_grant_limiter = DownloadGrantRateLimiter(max_concurrent=4)
  ...
  _: None = Depends(download_grant_limiter)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""
import time
from fastapi import Request, Header, HTTPException, status, Depends
from core.config import settings
from app.core.redis_client import redis_client, check_and_incr_concurrency, release_concurrency
from app.core.download_grant import verify_download_grant, _hash_token

# 限流配置
MAX_CONCURRENT = settings.max_concurrent
RATE_PER_SEC = settings.rate_per_sec
DOWNLOAD_GRANT_TTL_SECONDS = settings.operation_token_expire_seconds  # 复用原 TTL 配置
MAX_REQUESTS_PER_GRANT = settings.max_requests_per_operation_token  # 复用原限制配置


class DownloadGrantRateLimiter:
    """
    下载授权多维限流器

    基于 Opaque Token 的下载操作限流，实现多维度限流：
      1. 总请求次数限制：限制单个 Download Grant 的总请求次数
      2. 每秒请求速率限制：限制单秒内的请求数
      3. 并发连接数限制：限制同时进行的请求数

    使用方式：
      download_grant_limiter = DownloadGrantRateLimiter(max_concurrent=4)
      @router.get(...)
      async def download(..., _: None = Depends(download_grant_limiter)):
          ...

    Attributes:
        max_concurrent: 最大并发连接数
        rate_per_sec: 每秒最大请求数
    """

    def __init__(self, max_concurrent: int = MAX_CONCURRENT, rate_per_sec: int = RATE_PER_SEC):
        self.max_concurrent = max_concurrent
        self.rate_per_sec = rate_per_sec

    async def __call__(
        self,
        request: Request,
        token: str = Header(..., alias="X-Download-Grant"),
        user_id: str = Header(..., alias="X-User-Id")
    ):
        """
        执行限流检查

        限流流程：
          1. 验证 Opaque Token（通过 Redis 查询授权信息）
          2. 验证用户 ID 匹配
          3. 检查总请求次数限制
          4. 检查每秒请求速率限制
          5. 检查并发连接数限制
          6. 将授权信息存储到 request.state 中
          7. 请求完成后释放并发计数

        Args:
            request: FastAPI 请求对象
            token: 下载授权 Opaque Token（从 X-Download-Grant 头获取）
            user_id: 用户 ID（从 X-User-Id 头获取）

        Raises:
            HTTPException:
                - 401: Token 无效或过期
                - 403: 用户不匹配
                - 429: 超过任一限流限制

        Yields:
            None: 限流检查通过后继续执行请求
        """
        # 1. 验证 Opaque Token - 从 request.path_params 提取 file_id 用于验证
        #    file_id 在路由层面作为路径参数，但这里我们先用 token 自身验证
        #    实际的 file_id 校验在 verify_download_grant 中会做
        grant_data = await self._verify_grant(token, user_id)

        token_hash = grant_data["token_hash"]

        # 2. 总请求次数限制
        total_key = f"total:download_grant:{token_hash}"
        total = await redis_client.incr(total_key)
        if total == 1:
            await redis_client.expire(total_key, DOWNLOAD_GRANT_TTL_SECONDS + 10)
        if total > MAX_REQUESTS_PER_GRANT:
            raise HTTPException(
                status_code=429,
                detail="The current number of download requests has reached the upper limit"
            )

        # 3. 每秒请求速率限制（固定窗口）
        rate_key = f"rate:download_grant:{token_hash}:{int(time.time())}"
        current_rate = await redis_client.incr(rate_key)
        if current_rate == 1:
            await redis_client.expire(rate_key, 2)
        if current_rate > self.rate_per_sec:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Download rate limit exceeded"
            )

        # 4. 并发连接数限制
        concurrency_key = f"concurrency:download_grant:{token_hash}"
        allowed = await check_and_incr_concurrency(concurrency_key, self.max_concurrent)
        if not allowed:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many concurrent download requests"
            )

        # 存储并发 key 以便释放
        request.state.download_grant_concurrency_key = concurrency_key
        # 将授权信息存入 state 供路由使用
        request.state.download_grant_data = grant_data

        try:
            yield
        finally:
            await release_concurrency(concurrency_key)

    async def _verify_grant(self, token: str, user_id: str) -> dict:
        """
        验证 Opaque Token 并返回授权信息

        与 verify_download_grant 不同，此方法不要求 file_id，
        仅验证 Token 有效性和用户匹配。

        Args:
            token: 下载授权 Token
            user_id: 用户 ID

        Returns:
            dict: 包含 token_hash 和授权信息的字典

        Raises:
            HTTPException 401: Token 无效
            HTTPException 403: 用户不匹配
        """
        from app.core.download_grant import (
            validate_token_format, PREFIX_GRANT_TOKEN, GRANT_STATUS_ACTIVE
        )

        if not validate_token_format(token):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid download grant format"
            )

        token_hash = _hash_token(token)
        token_key = f"{PREFIX_GRANT_TOKEN}{token_hash}"

        grant = await redis_client.hgetall(token_key)
        if not grant:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Download grant not found or expired"
            )

        if grant.get("status") != GRANT_STATUS_ACTIVE:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Download grant is not active"
            )

        if grant.get("userId") != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Download grant user mismatch"
            )

        now_ms = int(time.time() * 1000)
        if now_ms > int(grant.get("expiresAt", 0)):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Download grant expired"
            )

        grant["token_hash"] = token_hash
        return grant


# 创建默认限流器实例
download_grant_limiter = DownloadGrantRateLimiter(max_concurrent=4)