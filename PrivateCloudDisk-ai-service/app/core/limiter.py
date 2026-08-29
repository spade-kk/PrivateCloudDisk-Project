"""Redis-backed, tenant-scoped admission control for costly Agent runs."""

from __future__ import annotations

import time

from redis.asyncio import Redis


class RunRateLimiter:
    """Fixed-window limiter for user+space Agent starts.

    [AI-AGENT-SECURITY-004] The limiter protects model/provider quotas and Capability
    Hub from a single identity exhausting the shared service. It is intentionally
    Redis-backed so all horizontally scaled Agent instances enforce one boundary.
    """

    def __init__(self, redis: Redis, maximum: int, window_seconds: int = 60) -> None:
        self._redis = redis
        self._maximum = maximum
        self._window_seconds = window_seconds

    async def allow(self, user_id: str, space_id: str | None) -> tuple[bool, int]:
        window = int(time.time() // self._window_seconds)
        scope = f"{user_id}:{space_id or '_personal'}"
        key = f"ai:rate:run:{scope}:{window}"
        count = await self._redis.incr(key)
        if count == 1:
            await self._redis.expire(key, self._window_seconds + 2)
        return count <= self._maximum, max(self._maximum - count, 0)
