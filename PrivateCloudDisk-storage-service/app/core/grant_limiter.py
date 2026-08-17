"""Preview/Download Grant 通用限流执行器。

需求三-2/三-5：普通 file_id Grant 与分享 Grant 的校验入口不同，但总请求次数、
秒级速率、并发计数和异常释放必须保持同一套实现。调用方只负责提供已经校验过的
Grant 数据和 state 字段名，避免分享路由复制普通限流逻辑后出现配额漂移。
"""
from __future__ import annotations

import time

from fastapi import HTTPException, Request, status

from app.core.redis_client import check_and_incr_concurrency, redis_client, release_concurrency


async def enforce_grant_limits(
    request: Request,
    grant: dict,
    *,
    kind: str,
    max_concurrent: int,
    max_requests: int,
    rate_per_sec: int,
    ttl_seconds: int,
    state_attr: str,
):
    """执行统一 Grant 限流并在请求结束后释放并发计数。"""
    token_hash = grant["token_hash"]
    total_key = f"total:{kind}_grant:{token_hash}"
    total = await redis_client.incr(total_key)
    if total == 1:
        await redis_client.expire(total_key, ttl_seconds + 10)
    if total > max_requests:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="当前授权请求次数已达上限，请重新授权",
            headers={"Retry-After": "2"},
        )

    rate_key = f"rate:{kind}_grant:{token_hash}:{int(time.time())}"
    rate = await redis_client.incr(rate_key)
    if rate == 1:
        await redis_client.expire(rate_key, 2)
    if rate > rate_per_sec:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="授权请求过于频繁，请稍后重试",
            headers={"Retry-After": "1"},
        )

    concurrency_key = f"concurrency:{kind}_grant:{token_hash}"
    if not await check_and_incr_concurrency(concurrency_key, max_concurrent, ttl=30):
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="同时处理的文件过多，请稍后重试",
            headers={"Retry-After": "2"},
        )
    setattr(request.state, state_attr, grant)
    try:
        yield
    finally:
        await release_concurrency(concurrency_key)
