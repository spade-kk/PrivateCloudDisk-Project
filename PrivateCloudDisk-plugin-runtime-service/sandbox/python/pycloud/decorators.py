"""源码元数据装饰器；只登记标记，不执行任何动态代码。"""

from __future__ import annotations

from typing import Callable, TypeVar

F = TypeVar("F", bound=Callable[..., object])


def test(function: F) -> F:
    """标记可被 Runtime 测试 API 调用的入口。"""
    return function


def capability(name: str):
    """标记可被 Capability Hub 发现的能力函数。"""
    if not isinstance(name, str) or not name:
        raise ValueError("能力名称不能为空")

    def decorate(function: F) -> F:
        return function

    return decorate
