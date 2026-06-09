"""
Redis 客户端和 Lua 脚本
提供 Redis 连接管理和原子操作支持
"""
import redis
from core.config import settings


# Redis 连接 URL
REDIS_URL = settings.redis_url

# 创建异步 Redis 客户端
redis_client = redis.asyncio.Redis.from_url(REDIS_URL, encoding="utf-8", decode_responses=True)


# ==================== Lua 脚本：原子并发控制 ====================

LUA_CONCURRENCY = """
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])
local current = redis.call('INCR', key)
redis.call('EXPIRE', key, ttl)
if current > limit then
    redis.call('DECR', key)
    return 0
end
return current
"""

LUA_RELEASE = """
local key = KEYS[1]
local current = redis.call('DECR', key)
if current <= 0 then
    redis.call('DEL', key)
    return 0
end
return current
"""

LUA_FIXED_WINDOW = """
local key = KEYS[1]
local ttl = tonumber(ARGV[1])
local current = redis.call('INCR', key)
if current == 1 then redis.call('EXPIRE', key, ttl) end
return current
"""


async def check_and_incr_concurrency(key: str, limit: int, ttl: int = 30) -> bool:
    """
    原子并发计数检查
    
    使用 Lua 脚本实现原子性的并发计数：
    1. 递增计数器
    2. 设置过期时间
    3. 如果超过限制，递减并返回 False
    4. 否则返回 True
    
    Args:
        key: Redis 键名
        limit: 并发数上限
        ttl: 键的过期时间（秒），默认 30 秒
    
    Returns:
        bool: True 表示允许通过，False 表示超过限制
    
    Example:
        >>> allowed = await check_and_incr_concurrency("concurrency:user123", 10, 30)
        >>> if allowed:
        ...     # 执行业务逻辑
        ...     pass
    """
    current = await redis_client.eval(LUA_CONCURRENCY, 1, key, limit, ttl)
    return int(current) > 0


async def release_concurrency(key: str):
    """
    释放并发计数
    
    使用 Lua 脚本实现原子性的并发计数释放：
    1. 递减计数器
    2. 如果计数器 <= 0，删除键
    
    Args:
        key: Redis 键名
    
    Example:
        >>> await release_concurrency("concurrency:user123")
    """
    await redis_client.eval(LUA_RELEASE, 1, key)


async def enforce_fixed_window(key: str, limit: int, window_seconds: int, detail: str):
    """
    固定窗口限流检查
    
    使用 Lua 脚本实现固定窗口限流：
    1. 递增计数器
    2. 如果是第一次递增，设置过期时间
    3. 如果超过限制，抛出 HTTP 429 异常
    
    Args:
        key: Redis 键名
        limit: 时间窗口内允许的最大请求数
        window_seconds: 时间窗口大小（秒）
        detail: 超过限制时的错误信息
    
    Raises:
        HTTPException: 当请求超过限制时抛出 429 状态码异常
    
    Example:
        >>> await enforce_fixed_window(
        ...     "rl:user123:download",
        ...     limit=100,
        ...     window_seconds=60,
        ...     detail="请求过于频繁"
        ... )
    """
    from fastapi import HTTPException, status
    
    if limit <= 0 or window_seconds <= 0:
        return
    current = await redis_client.eval(LUA_FIXED_WINDOW, 1, key, window_seconds)
    if int(current) > limit:
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail=detail)
