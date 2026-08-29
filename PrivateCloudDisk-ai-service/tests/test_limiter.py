from __future__ import annotations

import pytest

from app.core.limiter import RunRateLimiter


class FakeRedis:
    def __init__(self) -> None:
        self.values: dict[str, int] = {}
        self.expirations: dict[str, int] = {}

    async def incr(self, key: str) -> int:
        self.values[key] = self.values.get(key, 0) + 1
        return self.values[key]

    async def expire(self, key: str, seconds: int) -> None:
        self.expirations[key] = seconds


@pytest.mark.asyncio
async def test_run_limiter_is_scoped_by_user_and_space():
    redis = FakeRedis()
    limiter = RunRateLimiter(redis, maximum=2)

    assert await limiter.allow("user-a", "space-a") == (True, 1)
    assert await limiter.allow("user-a", "space-a") == (True, 0)
    assert await limiter.allow("user-a", "space-a") == (False, 0)
    # A second tenant has an independent admission budget.
    assert await limiter.allow("user-a", "space-b") == (True, 1)
    assert len(redis.expirations) == 2
