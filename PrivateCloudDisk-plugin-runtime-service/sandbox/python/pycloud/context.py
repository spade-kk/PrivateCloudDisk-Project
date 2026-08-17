"""执行上下文只读访问。"""

from __future__ import annotations

from types import MappingProxyType

_context = MappingProxyType({})


def configure(value: dict) -> None:
    global _context
    _context = MappingProxyType(dict(value))


def current_context():
    return _context


def require_permission(permission: str) -> None:
    permissions = _context.get("permissions", ())
    if permission not in permissions:
        raise PermissionError(f"插件未获得权限：{permission}")
