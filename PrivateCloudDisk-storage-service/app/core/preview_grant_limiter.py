"""Preview Token 依赖注入限流器。

需求三-2/4：按 Token 总次数、Token 秒级速率、并发请求数校验；用户/IP 的活跃
Token 上限在颁发阶段完成。并发计数使用 Redis Lua 原子增减，异常响应同样释放。
"""
import time

from fastapi import Header, HTTPException, Request, status

from app.core.preview_grant import verify_preview_grant
from app.core.redis_client import check_and_incr_concurrency, redis_client, release_concurrency
from core.config import settings


class PreviewGrantRateLimiter:
    async def __call__(
        self,
        request: Request,
        token: str = Header(..., alias="X-Preview-Grant"),
        user_id: str = Header(..., alias="X-User-Id"),
    ):
        file_id = str(request.path_params.get("file_id") or "")
        grant = await verify_preview_grant(token, user_id, file_id)
        token_hash = grant["token_hash"]

        total_key = f"total:preview_grant:{token_hash}"
        total = await redis_client.incr(total_key)
        if total == 1:
            await redis_client.expire(total_key, settings.preview_grant_ttl_seconds + 10)
        if total > settings.preview_grant_max_requests:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="当前预览授权请求次数已达上限，请刷新页面重试",
                headers={"Retry-After": "2"},
            )

        rate_key = f"rate:preview_grant:{token_hash}:{int(time.time())}"
        current_rate = await redis_client.incr(rate_key)
        if current_rate == 1:
            await redis_client.expire(rate_key, 2)
        if current_rate > settings.preview_grant_rate_per_sec:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="预览请求过于频繁，请稍后重试",
                headers={"Retry-After": "1"},
            )

        concurrency_key = f"concurrency:preview_grant:{token_hash}"
        allowed = await check_and_incr_concurrency(
            concurrency_key,
            settings.preview_grant_max_concurrent,
            ttl=30,
        )
        if not allowed:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="同时预览的文件过多，请稍后重试",
                headers={"Retry-After": "2"},
            )

        request.state.preview_grant_data = grant
        try:
            yield
        finally:
            await release_concurrency(concurrency_key)


preview_grant_limiter = PreviewGrantRateLimiter()
