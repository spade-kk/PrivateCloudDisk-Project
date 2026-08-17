"""分享 HLS 不透明令牌。

需求三-5：普通 HLS 令牌可以把 file_id 放在签名载荷中，因为 URL 本身已经
使用真实 file_id；分享 HLS 令牌不能这样做。这里使用随机 opaque token，真实
file_id、用户和空间只存储在 Redis 短期映射中，播放请求通过 share_token 与
share_resource_id 二次绑定后才取出内部定位信息。
"""
from __future__ import annotations

import hashlib
import hmac
import os
import secrets
import time

from fastapi import HTTPException, status

from app.core.redis_client import redis_client

_SECRET = os.environ.get("HLS_TOKEN_SECRET", "privateclouddisk-hls-token-secret-v1")
_PREFIX = "share_hls_token:"


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _signature(random_value: str) -> str:
    return hmac.new(_SECRET.encode(), random_value.encode(), hashlib.sha256).hexdigest()


async def issue_share_hls_token(
    *,
    file_id: str,
    user_id: str,
    space_id: str | None,
    share_token: str,
    share_resource_id: str,
    expires_in: int,
) -> str:
    random_value = secrets.token_urlsafe(32)
    token = f"sht_v1.{random_value}.{_signature(random_value)}"
    expires_at = int(time.time()) + expires_in
    key = f"{_PREFIX}{_hash(token)}"
    await redis_client.hset(key, mapping={
        "file_id": file_id,
        "user_id": user_id,
        "space_id": space_id or "",
        "share_token": share_token,
        "share_resource_id": share_resource_id,
        "expires_at": expires_at,
    })
    await redis_client.expire(key, expires_in + 30)
    return token


async def verify_share_hls_token(token: str, share_token: str, share_resource_id: str) -> dict:
    try:
        prefix, random_value, signature = token.split(".", 2)
        if prefix != "sht_v1" or not hmac.compare_digest(signature, _signature(random_value)):
            raise ValueError("令牌签名无效")
    except (ValueError, TypeError) as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="分享视频流令牌无效或已过期") from exc
    data = await redis_client.hgetall(f"{_PREFIX}{_hash(token)}")
    if not data or data.get("share_token") != share_token or data.get("share_resource_id") != share_resource_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="分享视频流令牌与资源不匹配")
    if int(data.get("expires_at") or 0) < int(time.time()) or not data.get("file_id"):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="分享视频流令牌已过期")
    return data
