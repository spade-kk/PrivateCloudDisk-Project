"""Gateway-to-Agent trusted request-context verification."""

from __future__ import annotations

import hashlib
import hmac
import time
from dataclasses import dataclass

from fastapi import HTTPException, Request, status

from app.core.config import Settings


@dataclass(frozen=True, slots=True)
class RequestIdentity:
    user_id: str
    space_id: str | None
    request_id: str


def canonical_identity_payload(method: str, path: str, request_id: str, timestamp: str, user_id: str, space_id: str | None) -> bytes:
    """Keep Gateway and Agent signing bytes deliberately simple and versioned by contract."""
    return "\n".join(("pcd-ai-v1", method.upper(), path, request_id, timestamp, user_id, space_id or "")).encode("utf-8")


def sign_identity(secret: str, method: str, path: str, request_id: str, timestamp: str, user_id: str, space_id: str | None) -> str:
    return hmac.new(
        secret.encode("utf-8"),
        canonical_identity_payload(method, path, request_id, timestamp, user_id, space_id),
        hashlib.sha256,
    ).hexdigest()


async def require_identity(request: Request, settings: Settings) -> RequestIdentity:
    """Reject browser-forged identity headers unless Gateway authenticated and signed them.

    `X-User-Id` remains the platform's existing downstream identity header. The
    `X-PCD-*` headers are Agent-specific trusted-context additions, stripped and
    reissued by Gateway before traffic reaches this service.
    """
    user_id = request.headers.get("X-PCD-User-Id")
    space_id = request.headers.get("X-PCD-Space-Id") or None
    request_id = request.headers.get("X-PCD-Request-Id")
    timestamp = request.headers.get("X-PCD-Identity-Timestamp")
    signature = request.headers.get("X-PCD-Identity-Signature")

    if not user_id or not request_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="缺少受信用户上下文")

    if settings.allow_unsigned_identity and settings.environment in {"development", "test"}:
        return RequestIdentity(user_id=user_id, space_id=space_id, request_id=request_id)

    if not timestamp or not signature or not settings.identity_shared_secret.get_secret_value():
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="身份上下文签名无效")
    try:
        timestamp_value = int(timestamp)
    except ValueError as error:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="身份上下文时间戳无效") from error
    if abs(int(time.time()) - timestamp_value) > settings.identity_max_age_seconds:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="身份上下文已过期")

    expected = sign_identity(
        settings.identity_shared_secret.get_secret_value(),
        request.method,
        request.url.path,
        request_id,
        timestamp,
        user_id,
        space_id,
    )
    if not hmac.compare_digest(expected, signature):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="身份上下文签名无效")
    return RequestIdentity(user_id=user_id, space_id=space_id, request_id=request_id)
