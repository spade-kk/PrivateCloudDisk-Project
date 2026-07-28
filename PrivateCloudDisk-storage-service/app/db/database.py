"""MySQL 异步连接池。

预览资源是跨实例共享的业务状态，必须由数据库承担事实源职责；连接池由 HTTP 与
Worker 两类进程分别初始化，避免跨事件循环复用连接。
"""
from __future__ import annotations

import asyncio
import logging
from typing import Optional

import aiomysql

from core.config import settings

logger = logging.getLogger("database")
_pool: Optional[aiomysql.Pool] = None
_pool_lock = asyncio.Lock()


async def init_database() -> aiomysql.Pool:
    """初始化数据库连接池；重复调用时复用当前事件循环内的连接池。

    容器启动时 MySQL 的端口可用时间通常晚于进程创建时间，因此这里使用有上限的
    重试，既避免一次瞬时失败导致 HTTP/Worker 永久退出，也不会无限掩盖配置错误。
    """
    global _pool
    if _pool is not None and not _pool.closed:
        return _pool

    async with _pool_lock:
        if _pool is None or _pool.closed:
            last_error: Exception | None = None
            for attempt in range(1, settings.mysql_connect_retries + 1):
                try:
                    # AUDIT FIX [7.4]: 使用事务型 MySQL 连接池替代 Redis-only 的预览元数据写入。
                    _pool = await aiomysql.create_pool(
                        host=settings.mysql_host,
                        port=settings.mysql_port,
                        user=settings.mysql_user,
                        password=settings.mysql_password,
                        db=settings.mysql_database,
                        minsize=settings.mysql_pool_min_size,
                        maxsize=settings.mysql_pool_max_size,
                        autocommit=False,
                        charset="utf8mb4",
                        cursorclass=aiomysql.DictCursor,
                    )
                    logger.info(
                        "MySQL 连接池初始化完成: %s:%s/%s",
                        settings.mysql_host,
                        settings.mysql_port,
                        settings.mysql_database,
                    )
                    break
                except Exception as exc:
                    last_error = exc
                    if attempt >= settings.mysql_connect_retries:
                        raise
                    logger.warning(
                        "MySQL 尚未就绪，第 %s/%s 次连接失败，%.1f 秒后重试: %s",
                        attempt,
                        settings.mysql_connect_retries,
                        settings.mysql_connect_retry_delay_seconds,
                        exc,
                    )
                    await asyncio.sleep(settings.mysql_connect_retry_delay_seconds)
            if _pool is None:
                raise RuntimeError("MySQL 连接池初始化失败") from last_error
    return _pool


async def get_database_pool() -> aiomysql.Pool:
    """获取连接池，支持测试和独立消费者按需初始化。"""
    return await init_database()


async def close_database() -> None:
    """优雅关闭连接池。"""
    global _pool
    if _pool is not None and not _pool.closed:
        _pool.close()
        await _pool.wait_closed()
    _pool = None
