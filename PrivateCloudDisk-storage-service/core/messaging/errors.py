"""Worker 统一异常分类。

需求编号：W-03、W-06。
业务消费者只需抛出明确异常；框架层把临时性错误放入 TTL retry queue，把协议/代码
错误送入专属 DLQ。未知异常默认不可重试，避免原实现的无限 requeue。
"""
from __future__ import annotations

import asyncio
import errno
from typing import Any


class WorkerProcessingError(Exception):
    """消息处理异常基类。"""

    def __init__(self, message: str, *, failure_reason: str = "UNKNOWN", cause: Exception | None = None):
        super().__init__(message)
        self.failure_reason = failure_reason
        self.cause = cause


class RetryableWorkerError(WorkerProcessingError):
    """重试后有望成功的临时错误。"""


class NonRetryableWorkerError(WorkerProcessingError):
    """协议错误、业务冲突和代码缺陷，不应重新投递。"""


def classify_exception(exc: BaseException) -> WorkerProcessingError:
    """将未包装异常转换为统一分类。

    网络超时、连接重置、常见临时 I/O 和数据库死锁视为可重试；JSON/Pydantic/类型
    错误和其它未知异常视为不可重试。取消异常必须继续向上抛出，保证优雅关闭。
    """

    if isinstance(exc, asyncio.CancelledError):
        raise exc
    if isinstance(exc, WorkerProcessingError):
        return exc
    if isinstance(exc, (TimeoutError, ConnectionError, ConnectionResetError, BrokenPipeError)):
        return RetryableWorkerError(str(exc) or type(exc).__name__, failure_reason="TEMPORARY_IO_ERROR", cause=exc)
    if isinstance(exc, OSError) and getattr(exc, "errno", None) in {
        errno.EAGAIN,
        errno.EBUSY,
        errno.ENOSPC,
        errno.ETIMEDOUT,
        errno.EIO,
    }:
        return RetryableWorkerError(str(exc) or type(exc).__name__, failure_reason="TEMPORARY_IO_ERROR", cause=exc)
    # `ValueError`/`TypeError`/KeyError 常来自非法消息或代码缺陷，默认不重试。
    return NonRetryableWorkerError(str(exc) or type(exc).__name__, failure_reason="UNEXPECTED_ERROR", cause=exc)


def exception_summary(exc: BaseException, max_length: int = 1000) -> str:
    """输出不含绝对路径的有限长度摘要，避免完整堆栈进入消息或用户可见日志。"""

    value = str(exc) or type(exc).__name__
    return value.replace("/backend_file_service", "<storage-root>")[:max_length]
