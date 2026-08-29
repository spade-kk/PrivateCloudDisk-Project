from __future__ import annotations

import time

import pytest
from fastapi import HTTPException
from starlette.requests import Request

from app.core.identity import require_identity, sign_identity


def request_with_headers(headers: dict[str, str]) -> Request:
    return Request({
        "type": "http", "method": "POST", "path": "/api/v1/ai/conversations/a/runs",
        "headers": [(key.lower().encode(), value.encode()) for key, value in headers.items()],
        "scheme": "http", "server": ("test", 80), "client": ("test", 1), "query_string": b"",
    })


@pytest.mark.asyncio
async def test_accepts_gateway_signed_identity(settings):
    timestamp = str(int(time.time()))
    headers = {
        "X-PCD-User-Id": "user-a", "X-PCD-Space-Id": "space-a", "X-PCD-Request-Id": "request-a",
        "X-PCD-Identity-Timestamp": timestamp,
        "X-PCD-Identity-Signature": sign_identity("test-signing-secret", "POST", "/api/v1/ai/conversations/a/runs", "request-a", timestamp, "user-a", "space-a"),
    }
    identity = await require_identity(request_with_headers(headers), settings.model_copy(update={"allow_unsigned_identity": False}))
    assert identity.user_id == "user-a"
    assert identity.space_id == "space-a"


@pytest.mark.asyncio
async def test_rejects_forged_identity(settings):
    headers = {"X-PCD-User-Id": "user-a", "X-PCD-Request-Id": "request-a", "X-PCD-Identity-Timestamp": str(int(time.time())), "X-PCD-Identity-Signature": "forged"}
    with pytest.raises(HTTPException) as error:
        await require_identity(request_with_headers(headers), settings.model_copy(update={"allow_unsigned_identity": False}))
    assert error.value.status_code == 401
