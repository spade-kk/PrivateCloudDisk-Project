"""
文件存储模块

提供统一的文件存储抽象层，业务代码通过此模块获取存储实例。
"""

from core.storage.base import StorageProvider
from core.storage.factory import create_storage, get_storage, reset_storage
from core.storage.local_storage import LocalStorageProvider

__all__ = [
    "StorageProvider",
    "create_storage",
    "get_storage",
    "reset_storage",
    "LocalStorageProvider",
]