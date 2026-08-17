"""
存储服务空间请求上下文。

需求：空间管理能力全量集成
原行为：存储服务调用主业务服务时只透传 user_id，无法还原 X-Space-Id。
新行为：HTTP 中间件把可选空间头写入 ContextVar，异步 SDK 自动透传；
ContextVar 对 asyncio task 隔离，避免并发请求发生空间串读。
"""
from __future__ import annotations

from contextvars import ContextVar, Token


_current_space_id: ContextVar[str | None] = ContextVar(
    "private_cloud_disk_space_id", default=None
)


def set_current_space_id(space_id: str | None) -> Token:
    normalized = (space_id or "").strip() or None
    return _current_space_id.set(normalized)


def get_current_space_id() -> str | None:
    return _current_space_id.get()


def reset_current_space_id(token: Token) -> None:
    _current_space_id.reset(token)
