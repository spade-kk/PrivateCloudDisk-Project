"""
AI Processing Service - 文件读取服务

支持多种文件读取方式:
1. 本地文件系统 (与 storage-service 共享存储卷)
2. MinIO/S3 对象存储 (通过 HTTP 下载)
3. HTTP 文件流 (通过 storage-service 内部 API)

优先级: 本地路径 > MinIO > HTTP API
"""
from __future__ import annotations
import logging
import tempfile
import os
from pathlib import Path
from typing import Optional
from io import BytesIO

import httpx
from minio import Minio
from minio.error import S3Error

from app.core.config import settings

logger = logging.getLogger("ai_service.file_reader")


class FileReader:
    """
    文件读取器

    根据文件路径策略自动选择读取方式:
    - 如果本地路径存在 → 直接读取
    - 如果 MinIO 配置了 → 从 MinIO 下载
    - 否则 → 通过 storage-service HTTP API 下载
    """

    def __init__(self):
        self._minio_client: Optional[Minio] = None
        self._http_client: Optional[httpx.AsyncClient] = None

    async def _ensure_http_client(self) -> httpx.AsyncClient:
        if self._http_client is None:
            self._http_client = httpx.AsyncClient(
                timeout=httpx.Timeout(settings.ai_inference_timeout_seconds),
                limits=httpx.Limits(max_connections=20),
            )
        return self._http_client

    def _ensure_minio_client(self) -> Minio:
        if self._minio_client is None:
            self._minio_client = Minio(
                settings.minio_endpoint,
                access_key=settings.minio_access_key,
                secret_key=settings.minio_secret_key,
                secure=settings.minio_secure,
            )
        return self._minio_client

    async def read_file_bytes(
        self,
        storage_path: str,
        file_name: str = "",
        max_size_mb: int | None = None,
    ) -> bytes:
        """
        读取文件内容为 bytes

        Args:
            storage_path: 文件存储路径 (本地路径或 MinIO object key)
            file_name: 文件名 (用于 MinIO 对象名)
            max_size_mb: 最大文件大小 (MB)，超过则抛出异常

        Returns:
            文件字节内容

        Raises:
            FileNotFoundError: 文件不存在
            ValueError: 文件过大
        """
        max_size = (max_size_mb or settings.ai_max_file_size_mb) * 1024 * 1024

        # 1. 尝试本地文件系统
        local_path = Path(storage_path)
        if local_path.exists() and local_path.is_file():
            file_size = local_path.stat().st_size
            if file_size > max_size:
                raise ValueError(
                    f"文件过大: {file_size} bytes > {max_size} bytes (max {max_size_mb}MB)"
                )
            logger.debug(f"读取本地文件: {storage_path} ({file_size} bytes)")
            return local_path.read_bytes()

        # 2. 尝试共享存储路径
        shared_path = Path(settings.shared_storage_path) / storage_path.lstrip("/")
        if shared_path.exists() and shared_path.is_file():
            file_size = shared_path.stat().st_size
            if file_size > max_size:
                raise ValueError(
                    f"文件过大: {file_size} bytes > {max_size} bytes"
                )
            logger.debug(f"读取共享存储文件: {shared_path} ({file_size} bytes)")
            return shared_path.read_bytes()

        # 3. 尝试 MinIO 对象存储
        try:
            minio = self._ensure_minio_client()
            object_name = file_name or storage_path.lstrip("/")

            # 先检查对象是否存在
            stat = minio.stat_object(settings.minio_bucket, object_name)
            if stat.size > max_size:
                raise ValueError(
                    f"文件过大: {stat.size} bytes > {max_size} bytes"
                )

            response = minio.get_object(settings.minio_bucket, object_name)
            data = response.read()
            response.close()
            response.release_conn()
            logger.debug(f"从 MinIO 读取文件: {object_name} ({len(data)} bytes)")
            return data

        except S3Error as e:
            logger.debug(f"MinIO 读取失败: {e}, 尝试 HTTP API")

        except Exception as e:
            logger.debug(f"MinIO 读取异常: {e}, 尝试 HTTP API")

        # 4. 通过 storage-service HTTP API 下载
        # 注意: 这需要 storage-service 提供内部文件下载 API
        return await self._read_via_http(storage_path)

    async def _read_via_http(self, storage_path: str) -> bytes:
        """通过 HTTP API 下载文件"""
        client = await self._ensure_http_client()

        # storage-service 内部文件下载 API
        # 实际部署时需根据 storage-service 的内部 API 调整
        url = f"http://pcd-storage-service:8000/api/v1/files/internal/download"

        try:
            response = await client.get(
                url,
                params={"storage_path": storage_path, "internal": "true"},
            )
            response.raise_for_status()
            logger.debug(f"通过 HTTP API 读取文件: {storage_path} ({len(response.content)} bytes)")
            return response.content
        except httpx.HTTPError as e:
            raise FileNotFoundError(f"无法读取文件: {storage_path}, error={e}")

    async def read_file_to_temp(self, storage_path: str, file_name: str = "") -> str:
        """
        读取文件并写入临时文件路径

        某些模型 (如 PaddleOCR) 需要文件路径作为输入。

        Returns:
            临时文件路径
        """
        data = await self.read_file_bytes(storage_path, file_name)

        # 保留原始文件扩展名
        suffix = Path(file_name or storage_path).suffix
        if not suffix:
            suffix = ".tmp"

        tmp_file = tempfile.NamedTemporaryFile(suffix=suffix, delete=False)
        tmp_file.write(data)
        tmp_file.close()

        logger.debug(f"创建临时文件: {tmp_file.name}")
        return tmp_file.name

    async def cleanup_temp_file(self, temp_path: str) -> None:
        """清理临时文件"""
        try:
            os.unlink(temp_path)
            logger.debug(f"清理临时文件: {temp_path}")
        except OSError as e:
            logger.warning(f"清理临时文件失败: {temp_path}, error={e}")

    async def close(self) -> None:
        """关闭 HTTP 客户端"""
        if self._http_client:
            await self._http_client.aclose()
            self._http_client = None


# =============================================================================
# 全局单例
# =============================================================================
file_reader = FileReader()