"""Preview Token 依赖注入限流器。

需求三-2/4：按 Token 总次数、Token 秒级速率、并发请求数校验；用户/IP 的活跃
Token 上限在颁发阶段完成。并发计数使用 Redis Lua 原子增减，异常响应同样释放。
"""
from fastapi import Header, HTTPException, Request, status

from app.core.grant_limiter import enforce_grant_limits
from app.core.preview_grant import verify_preview_grant
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
        requested_space = (request.headers.get("X-Space-Id") or "").strip()
        granted_space = str(grant.get("spaceId") or "").strip()
        if requested_space and requested_space != granted_space:
            # 需求三-2：令牌消费阶段再次绑定空间，防止把个人空间头替换为其他空间。
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="预览授权与当前空间不匹配")
        # AUDIT FIX [3.4]：普通预览使用共享限流执行器，分享预览也调用同一实现。
        async for _ in enforce_grant_limits(
            request,
            grant,
            kind="preview",
            max_concurrent=settings.preview_grant_max_concurrent,
            max_requests=settings.preview_grant_max_requests,
            rate_per_sec=settings.preview_grant_rate_per_sec,
            ttl_seconds=settings.preview_grant_ttl_seconds,
            state_attr="preview_grant_data",
        ):
            yield


preview_grant_limiter = PreviewGrantRateLimiter()
