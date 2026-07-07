"""
文件存储抽象提供者 (StorageProvider Protocol)

定义统一的文件存储接口，上层业务逻辑只调用此接口方法，不关注底层实现。
支持本地磁盘存储、MinIO 对象存储等多种实现。

设计原则：
- 只有通用方法：put / get / delete / exists / stat / make_dirs / delete_dir / move / walk / get_real_path
- 不提供流式读写（read_stream / write_stream），因为这是本地文件系统的特性，
  MinIO 等对象存储通过 get_object / put_object 实现，不需要流式抽象
- get_real_path() 返回文件系统绝对路径供外部工具（pyvips/ffmpeg/clamd）使用，
  MinIO 实现会先下载到本地缓存再返回路径

所有方法均为异步，确保与 FastAPI 异步架构兼容。
"""

from __future__ import annotations
from abc import ABC, abstractmethod
from typing import Optional


class StorageProvider(ABC):
    """
    文件存储抽象提供者

    定义了文件存储的核心操作接口，所有实现类必须实现全部抽象方法。

    核心方法：
    - put(path, data):       存储数据到指定路径
    - get(path, offset, len):获取文件数据，支持范围读取
    - delete(path):          删除文件（幂等）
    - exists(path):          检查路径是否存在
    - stat(path):            获取文件元数据 {size, mtime}
    - make_dirs(path):       创建目录（幂等）
    - delete_dir(path):      递归删除目录（幂等）
    - move(src, dst):        移动文件
    - walk(path):            遍历目录树
    - get_real_path(path):   获取文件系统绝对路径
    """

    # ========== 基本 CRUD ==========

    @abstractmethod
    async def put(self, path: str, data: bytes) -> None:
        """
        存储数据到指定路径

        自动创建父目录（如果不存在）。

        Args:
            path: 相对于存储根目录的文件路径
            data: 要写入的字节数据
        """
        ...

    @abstractmethod
    async def get(self, path: str, offset: int = 0, length: Optional[int] = None) -> bytes:
        """
        获取文件数据

        支持范围读取（offset + length），适用于 HTTP Range 请求等场景。

        Args:
            path: 相对于存储根目录的文件路径
            offset: 读取起始偏移量（字节），默认 0
            length: 读取长度（字节），None 表示读取到文件末尾

        Returns:
            文件内容的字节数据
        """
        ...

    @abstractmethod
    async def delete(self, path: str) -> None:
        """
        删除文件

        幂等操作：文件不存在时不报错。

        Args:
            path: 相对于存储根目录的文件路径
        """
        ...

    @abstractmethod
    async def exists(self, path: str) -> bool:
        """
        检查文件或目录是否存在

        Args:
            path: 相对于存储根目录的文件路径

        Returns:
            True 如果路径存在，否则 False
        """
        ...

    # ========== 元数据操作 ==========

    @abstractmethod
    async def stat(self, path: str) -> dict:
        """
        获取文件元数据

        Args:
            path: 相对于存储根目录的文件路径

        Returns:
            dict，至少包含:
            - size: int  文件大小（字节）
            - mtime: float 最后修改时间（Unix 时间戳秒）
        """
        ...

    # ========== 目录操作 ==========

    @abstractmethod
    async def make_dirs(self, path: str) -> None:
        """
        递归创建目录

        幂等操作：目录已存在时不报错。

        Args:
            path: 相对于存储根目录的目录路径
        """
        ...

    @abstractmethod
    async def delete_dir(self, path: str) -> None:
        """
        递归删除目录及其所有内容

        幂等操作：目录不存在时不报错。

        Args:
            path: 相对于存储根目录的目录路径
        """
        ...

    @abstractmethod
    async def walk(self, path: str) -> list[tuple[str, list[str], list[str]]]:
        """
        递归遍历目录树

        Args:
            path: 相对于存储根目录的目录路径

        Returns:
            列表，每个元素为 (dirpath, dirnames, filenames)
        """
        ...

    # ========== 文件操作 ==========

    @abstractmethod
    async def move(self, src: str, dst: str) -> None:
        """
        移动文件或目录

        Args:
            src: 源路径（相对于存储根目录）
            dst: 目标路径（相对于存储根目录）
        """
        ...

    # ========== 路径解析 ==========

    @abstractmethod
    def get_real_path(self, path: str) -> str:
        """
        获取文件系统真实绝对路径

        供外部工具（pyvips、ffmpeg、clamd、PDF 解析器等）使用。
        这些工具需要真实的文件系统路径才能读取文件。

        - LocalStorageProvider: 返回 base_dir + path 的绝对路径
        - MinIOStorageProvider: 将对象下载到本地缓存目录后返回缓存路径

        Args:
            path: 相对于存储根目录的文件路径

        Returns:
            文件系统上的绝对路径
        """
        ...

    @abstractmethod
    def get_base_dir(self) -> str:
        """
        获取存储根目录的绝对路径

        Returns:
            存储根目录的绝对路径
        """
        ...

    # ========== 文本便捷方法（非抽象，子类可覆盖） ==========

    async def put_text(self, path: str, data: str, encoding: str = "utf-8") -> None:
        """存储文本数据（便捷方法）"""
        await self.put(path, data.encode(encoding))

    async def get_text(self, path: str, encoding: str = "utf-8") -> str:
        """获取文本数据（便捷方法）"""
        data = await self.get(path)
        return data.decode(encoding)  