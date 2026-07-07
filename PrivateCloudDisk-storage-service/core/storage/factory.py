"""
存储工厂模块 (StorageFactory)

根据配置动态创建存储提供者实例，实现存储后端的灵活切换。
通过环境变量或配置文件指定存储类型，无需修改业务代码。

支持的存储类型:
- localstorage: 本地磁盘存储（默认）
- minio: MinIO 对象存储
"""

from __future__ import annotations
import logging
import os

from core.storage.base import StorageProvider
from core.storage.local_storage import LocalStorageProvider

logger = logging.getLogger("storage_factory")

# 全局单例存储实例
_storage_instance: StorageProvider | None = None


def create_storage(storage_type: str = "localstorage", **kwargs) -> StorageProvider:
    """
    根据类型创建存储提供者实例

    创建后会自动设置为全局单例，后续可通过 get_storage() 获取。

    Args:
        storage_type: 存储类型，支持 "localstorage" / "minio"
        **kwargs: 传递给具体 Provider 的构造参数

    Returns:
        StorageProvider 实例

    Raises:
        ValueError: 不支持的存储类型
    """
    global _storage_instance
    storage_type = storage_type.lower().strip()

    if storage_type == "localstorage":
        base_dir = kwargs.pop("base_dir", "../Uploads")
        _storage_instance = LocalStorageProvider(base_dir=base_dir, **kwargs)
    elif storage_type == "minio":
        from core.storage.minio_storage import MinIOStorageProvider
        _storage_instance = MinIOStorageProvider(**kwargs)
    else:
        raise ValueError(
            f"不支持的存储类型: {storage_type}，"
            f"可选值: localstorage, minio"
        )

    logger.info(f"存储提供者已创建: type={storage_type}, class={type(_storage_instance).__name__}")
    return _storage_instance


def get_storage() -> StorageProvider:
    """
    获取全局存储提供者实例

    如果尚未初始化且未配置，则默认使用 LocalStorageProvider。
    建议在应用启动时通过 create_storage() 显式初始化。

    Returns:
        StorageProvider 实例
    """
    global _storage_instance
    if _storage_instance is None:
        # 延迟初始化：从配置创建
        from core.config import settings
        if settings.storage_type == "minio":
            _storage_instance = create_storage(
                storage_type="minio",
                endpoint=settings.minio_endpoint,
                access_key=settings.minio_access_key,
                secret_key=settings.minio_secret_key,
                bucket=settings.minio_bucket,
                secure=settings.minio_secure,
                base_dir=settings.file_upload_dir,
            )
        else:
            _storage_instance = create_storage(
                storage_type="localstorage",
                base_dir=settings.file_upload_dir,
            )
    return _storage_instance


def reset_storage():
    """重置存储实例（用于测试）"""
    global _storage_instance
    _storage_instance = None