"""
本地磁盘存储提供者 (LocalStorageProvider)

实现 StorageProvider 接口，完全封装本地文件系统的所有 I/O 操作。
上层业务代码不再直接使用 os / aiofiles / shutil / open 等方法，
全部通过此 Provider 完成文件读写和管理。

路径处理：
- 所有 path 参数可以是相对路径（相对于 base_dir）或绝对路径
- get_real_path() 统一规范化路径，确保跨平台兼容
- 使用 os.path.normpath 处理路径中的 .. 和符号链接
"""

from __future__ import annotations
import os
import shutil
import asyncio
import logging
from pathlib import Path
from typing import Optional

from core.storage.base import StorageProvider

logger = logging.getLogger("local_storage")


class LocalStorageProvider(StorageProvider):
    """
    本地磁盘存储提供者

    将文件存储到本地文件系统，封装所有 os/sutil/aiofiles 操作。
    所有 I/O 操作通过 asyncio.to_thread 在后台线程执行，不阻塞事件循环。

    Usage:
        storage = LocalStorageProvider(base_dir="../Uploads")
        await storage.put("path/to/file.txt", b"Hello World")
        data = await storage.get("path/to/file.txt")
        real_path = storage.get_real_path("path/to/file.txt")
    """

    def __init__(self, base_dir: str):
        """
        初始化本地存储

        Args:
            base_dir: 存储根目录，可以是相对路径或绝对路径
        """
        self._base_dir = os.path.abspath(base_dir)
        os.makedirs(self._base_dir, exist_ok=True)
        logger.info(f"LocalStorageProvider 初始化完成: base_dir={self._base_dir}")

    # ========== 路径解析 ==========

    def _resolve_path(self, path: str) -> str:
        """
        将相对路径解析为绝对路径

        支持：
        - 相对路径（如 "storage/xxx.cloud"）→ 拼接到 base_dir 后
        - 带 base_dir 前缀的路径（如 "../Uploads/storage/xxx.cloud"）→ 规范化
        - 绝对路径 → 直接返回

        Args:
            path: 待解析的路径

        Returns:
            规范化的绝对路径
        """
        if os.path.isabs(path):
            return os.path.normpath(path)
        return os.path.normpath(os.path.join(self._base_dir, path))

    def get_real_path(self, path: str) -> str:
        return self._resolve_path(path)

    def get_base_dir(self) -> str:
        return self._base_dir

    # ========== 基本 CRUD ==========

    async def put(self, path: str, data: bytes) -> None:
        """
        写入字节数据到文件

        自动创建父目录。
        """
        real_path = self._resolve_path(path)

        def _write():
            os.makedirs(os.path.dirname(real_path), exist_ok=True)
            with open(real_path, "wb") as f:
                f.write(data)

        await asyncio.to_thread(_write)

    async def get(self, path: str, offset: int = 0, length: Optional[int] = None) -> bytes:
        """
        读取文件字节数据

        支持范围读取（offset + length），适用于 HTTP Range 请求场景。
        """
        real_path = self._resolve_path(path)

        def _read():
            with open(real_path, "rb") as f:
                if offset > 0:
                    f.seek(offset)
                if length is not None:
                    return f.read(length)
                return f.read()

        return await asyncio.to_thread(_read)

    async def delete(self, path: str) -> None:
        """
        删除文件，幂等操作
        """
        real_path = self._resolve_path(path)

        def _delete():
            if os.path.isfile(real_path):
                os.remove(real_path)
            elif os.path.isdir(real_path):
                # 删除文件（不递归删除目录，目录用 delete_dir 处理）
                os.remove(real_path)

        try:
            await asyncio.to_thread(_delete)
        except FileNotFoundError:
            pass  # 幂等：文件不存在不报错

    async def exists(self, path: str) -> bool:
        """
        检查文件或目录是否存在
        """
        real_path = self._resolve_path(path)
        return await asyncio.to_thread(os.path.exists, real_path)

    # ========== 元数据操作 ==========

    async def stat(self, path: str) -> dict:
        """
        获取文件元数据

        Returns:
            {"size": int, "mtime": float}
        """
        real_path = self._resolve_path(path)

        def _stat():
            st = os.stat(real_path)
            return {
                "size": st.st_size,
                "mtime": st.st_mtime,
            }

        return await asyncio.to_thread(_stat)

    # ========== 目录操作 ==========

    async def make_dirs(self, path: str) -> None:
        """
        递归创建目录，幂等操作
        """
        real_path = self._resolve_path(path)
        await asyncio.to_thread(os.makedirs, real_path, exist_ok=True)

    async def delete_dir(self, path: str) -> None:
        """
        递归删除目录及其所有内容，幂等操作
        """
        real_path = self._resolve_path(path)

        def _delete_dir():
            if os.path.isdir(real_path):
                shutil.rmtree(real_path)

        try:
            await asyncio.to_thread(_delete_dir)
        except FileNotFoundError:
            pass  # 幂等

    async def walk(self, path: str) -> list[tuple[str, list[str], list[str]]]:
        """
        递归遍历目录树

        Returns:
            列表，每个元素为 (dirpath, dirnames, filenames)
        """
        real_path = self._resolve_path(path)
        return await asyncio.to_thread(lambda: list(os.walk(real_path)))

    # ========== 文件操作 ==========

    async def move(self, src: str, dst: str) -> None:
        """
        移动文件或目录

        自动创建目标目录。
        """
        real_src = self._resolve_path(src)
        real_dst = self._resolve_path(dst)

        def _move():
            os.makedirs(os.path.dirname(real_dst), exist_ok=True)
            shutil.move(real_src, real_dst)

        await asyncio.to_thread(_move)

    # ========== 扩展方法（非抽象接口，LocalStorage 特有） ==========

    async def chmod(self, path: str, mode: int) -> None:
        """
        设置文件权限（POSIX 系统）

        Args:
            path: 文件路径
            mode: 权限模式（如 0o444）
        """
        real_path = self._resolve_path(path)
        await asyncio.to_thread(os.chmod, real_path, mode)

    async def list_dir(self, path: str) -> list[str]:
        """
        列出目录中的文件和子目录名称

        Args:
            path: 目录路径

        Returns:
            文件名列表
        """
        real_path = self._resolve_path(path)
        return await asyncio.to_thread(os.listdir, real_path)