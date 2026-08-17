"""跨进程消息幂等存储。

需求编号：W-04。Redis SET NX 是快速原子闸门；业务数据库仍可通过自己的唯一约束做
最终幂等。Redis 暂时不可用时不阻断文件主链，调用方必须依赖业务 CAS/资源唯一键。
"""
from __future__ import annotations

import hashlib
import logging
from typing import Any

logger = logging.getLogger("worker.idempotency")


class MessageIdempotencyStore:
    def __init__(self, *, namespace: str = "pcd:worker:idem", ttl_seconds: int = 86400):
        self.namespace = namespace
        self.ttl_seconds = max(60, int(ttl_seconds))

    def _key(self, key: str) -> str:
        digest = hashlib.sha256(str(key).encode("utf-8")).hexdigest()
        return f"{self.namespace}:{digest}"

    async def claim(self, key: str) -> bool:
        """首次返回 True；重复消息返回 False。"""

        if not key:
            return True
        try:
            from app.core.redis_client import redis_client

            claimed = await redis_client.set(self._key(key), "processing", ex=self.ttl_seconds, nx=True)
            return bool(claimed)
        except Exception as exc:
            # 核心文件流程有 DB CAS/资源唯一索引兜底，Redis 故障不应阻塞上传激活。
            logger.warning("幂等 Redis 不可用，降级到业务事务: %s", exc)
            return True

    async def complete(self, key: str) -> None:
        if not key:
            return
        try:
            from app.core.redis_client import redis_client

            await redis_client.setex(self._key(key), self.ttl_seconds, "completed")
        except Exception as exc:
            logger.warning("幂等完成标记写入失败 key=%s error=%s", key, exc)

    async def release(self, key: str) -> None:
        """业务失败时释放 processing 标记，允许后续 retry 消息执行。"""

        if not key:
            return
        try:
            from app.core.redis_client import redis_client

            await redis_client.delete(self._key(key))
        except Exception as exc:
            logger.warning("幂等失败标记释放失败 key=%s error=%s", key, exc)
