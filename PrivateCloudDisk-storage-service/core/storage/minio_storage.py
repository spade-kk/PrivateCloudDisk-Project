"""
MinIO 对象存储提供者 (MinIOStorageProvider)

实现 StorageProvider 接口，基于 MinIO SDK 提供对象存储能力。
MinIO 是兼容 AWS S3 的对象存储，可直接替换为 S3/OSS/COS/OBS 等。

核心实现：
- put:    调用 put_object 上传对象
- get:    调用 get_object 下载对象（支持 offset/length 范围读取）
- delete: 调用 remove_object 删除对象
- exists: 调用 stat_object 检查对象是否存在
- stat:   调用 stat_object 获取对象元数据
- get_real_path: 下载对象到本地缓存目录，返回本地路径供外部工具使用

注意：MinIO 没有目录概念，make_dirs 为空操作，delete_dir 通过前缀删除实现。
"""

from __future__ import annotations
import os
import asyncio
import logging
import hashlib
from typing import Optional
from io import BytesIO

from core.storage.base import StorageProvider

logger = logging.getLogger("minio_storage")


class MinIOStorageProvider(StorageProvider):
    """
    MinIO 对象存储提供者

    将文件存储到 MinIO 对象存储，所有操作通过 MinIO Python SDK 完成。

    Usage:
        storage = MinIOStorageProvider(
            endpoint="localhost:9000",
            access_key="minioadmin",
            secret_key="minioadmin",
            bucket="pcd-uploads",
            base_dir="../Uploads",  # 本地缓存目录
        )
        await storage.put("path/to/file.txt", b"Hello World")
        data = await storage.get("path/to/file.txt")
    """

    def __init__(
        self,
        endpoint: str,
        access_key: str,
        secret_key: str,
        bucket: str = "pcd-uploads",
        secure: bool = False,
        base_dir: str = "../Uploads",
    ):
        """
        初始化 MinIO 存储

        Args:
            endpoint: MinIO 服务地址 (host:port)
            access_key: 访问密钥
            secret_key: 密钥
            bucket: 存储桶名称
            secure: 是否使用 HTTPS
            base_dir: 本地缓存目录（用于 get_real_path 下载对象到本地）
        """
        self._endpoint = endpoint
        self._access_key = access_key
        self._secret_key = secret_key
        self._bucket = bucket
        self._secure = secure
        self._base_dir = os.path.abspath(base_dir)
        self._client = None

        # 确保缓存目录存在
        os.makedirs(self._base_dir, exist_ok=True)
        logger.info(
            f"MinIOStorageProvider 初始化: endpoint={endpoint}, bucket={bucket}, "
            f"cache_dir={self._base_dir}"
        )

    def _get_client(self):
        """
        懒加载 MinIO 客户端

        首次调用时创建客户端并确保 bucket 存在。
        """
        if self._client is None:
            from minio import Minio

            self._client = Minio(
                self._endpoint,
                access_key=self._access_key,
                secret_key=self._secret_key,
                secure=self._secure,
            )
            # 确保 bucket 存在
            if not self._client.bucket_exists(self._bucket):
                self._client.make_bucket(self._bucket)
                logger.info(f"创建 MinIO bucket: {self._bucket}")
        return self._client

    # ========== 路径解析 ==========

    def _resolve_path(self, path: str) -> str:
        """
        将路径解析为对象键（object key）

        MinIO 使用 "/" 作为路径分隔符，去掉开头的 "/" 确保是相对路径。

        Args:
            path: 相对于存储根目录的文件路径

        Returns:
            MinIO 对象键
        """
        # 去掉开头的斜杠和多余的路径分隔符
        key = path.lstrip("/")
        # 规范化路径（处理 .. 和多余的 /）
        parts = [p for p in key.split("/") if p and p != "."]
        while ".." in parts:
            idx = parts.index("..")
            if idx > 0:
                parts.pop(idx)
                parts.pop(idx - 1)
            else:
                parts.pop(idx)
        return "/".join(parts)

    def get_real_path(self, path: str) -> str:
        """
        获取文件系统真实路径

        MinIO 实现：将对象下载到本地缓存目录，返回缓存路径。
        如果对象已缓存且未过期，直接返回缓存路径。

        Args:
            path: 对象路径

        Returns:
            本地缓存文件的绝对路径
        """
        key = self._resolve_path(path)
        # 使用对象键的哈希作为缓存文件名，避免路径冲突
        key_hash = hashlib.md5(key.encode()).hexdigest()
        cache_path = os.path.join(self._base_dir, f"_minio_cache_{key_hash}")

        # 如果缓存不存在，下载对象到本地
        if not os.path.exists(cache_path):
            self._download_to_cache(key, cache_path)

        return cache_path

    def get_base_dir(self) -> str:
        return self._base_dir

    def _download_to_cache(self, key: str, cache_path: str):
        """
        将 MinIO 对象下载到本地缓存文件

        Args:
            key: MinIO 对象键
            cache_path: 本地缓存文件路径
        """
        client = self._get_client()
        try:
            response = client.get_object(self._bucket, key)
            os.makedirs(os.path.dirname(cache_path), exist_ok=True)
            with open(cache_path, "wb") as f:
                while True:
                    data = response.read(128 * 1024)
                    if not data:
                        break
                    f.write(data)
            response.close()
            response.release_conn()
            logger.debug(f"MinIO 对象已缓存到本地: {key} → {cache_path}")
        except Exception as e:
            logger.error(f"下载 MinIO 对象到缓存失败: {key}, error={e}")
            raise

    # ========== 基本 CRUD ==========

    async def put(self, path: str, data: bytes) -> None:
        """
        上传对象到 MinIO

        使用 put_object 直接上传字节数据。
        """
        key = self._resolve_path(path)
        client = self._get_client()

        def _put():
            client.put_object(
                self._bucket,
                key,
                data=BytesIO(data),
                length=len(data),
            )

        await asyncio.to_thread(_put)

    async def put_file(self, path: str, source_path: str) -> None:
        """[REQ-GIT-OBJECT-6.2] 使用 MinIO fput_object 流式上传临时文件。"""
        key = self._resolve_path(path)
        client = self._get_client()
        await asyncio.to_thread(client.fput_object, self._bucket, key, str(source_path))

    async def get(self, path: str, offset: int = 0, length: Optional[int] = None) -> bytes:
        """
        从 MinIO 获取对象数据

        使用 get_object 的 offset/length 参数实现范围读取，
        无需下载整个文件。

        Args:
            path: 对象路径
            offset: 起始偏移量
            length: 读取长度，None 表示读取全部

        Returns:
            对象内容的字节数据
        """
        key = self._resolve_path(path)
        client = self._get_client()

        def _get():
            response = client.get_object(
                self._bucket,
                key,
                offset=offset if offset > 0 else 0,
                length=length if length is not None else 0,
            )
            try:
                data = response.read()
                return data
            finally:
                response.close()
                response.release_conn()

        return await asyncio.to_thread(_get)

    async def delete(self, path: str) -> None:
        """
        删除 MinIO 对象，幂等操作
        """
        key = self._resolve_path(path)
        client = self._get_client()

        def _delete():
            try:
                client.remove_object(self._bucket, key)
            except Exception:
                pass  # 幂等：对象不存在不报错

        await asyncio.to_thread(_delete)

    async def exists(self, path: str) -> bool:
        """
        检查 MinIO 对象是否存在

        通过 stat_object 判断，如果抛出异常则不存在。
        """
        key = self._resolve_path(path)
        client = self._get_client()

        def _exists():
            try:
                client.stat_object(self._bucket, key)
                return True
            except Exception:
                return False

        return await asyncio.to_thread(_exists)

    # ========== 元数据操作 ==========

    async def stat(self, path: str) -> dict:
        """
        获取 MinIO 对象元数据

        Returns:
            {"size": int, "mtime": float}
        """
        key = self._resolve_path(path)
        client = self._get_client()

        def _stat():
            obj_stat = client.stat_object(self._bucket, key)
            return {
                "size": obj_stat.size,
                "mtime": obj_stat.last_modified.timestamp(),
            }

        return await asyncio.to_thread(_stat)

    # ========== 目录操作 ==========

    async def make_dirs(self, path: str) -> None:
        """
        创建目录

        MinIO 没有目录概念，此方法为空操作。
        对象上传时会自动创建隐式目录前缀。
        """
        pass  # MinIO 无需显式创建目录

    async def delete_dir(self, path: str) -> None:
        """
        递归删除目录（通过前缀批量删除对象实现）

        列出所有匹配前缀的对象，然后批量删除。
        """
        key_prefix = self._resolve_path(path)
        if not key_prefix.endswith("/"):
            key_prefix += "/"
        client = self._get_client()

        def _delete_dir():
            objects_to_delete = client.list_objects(
                self._bucket, prefix=key_prefix, recursive=True
            )
            # 收集所有要删除的对象名
            obj_names = [obj.object_name for obj in objects_to_delete]
            if obj_names:
                # 使用 remove_objects 批量删除
                errors = client.remove_objects(self._bucket, obj_names)
                for err in errors:
                    logger.warning(f"删除 MinIO 对象失败: {err}")

        await asyncio.to_thread(_delete_dir)

    async def walk(self, path: str) -> list[tuple[str, list[str], list[str]]]:
        """
        遍历目录树

        MinIO 实现：通过 list_objects 列出所有对象，按目录分组建模。

        Returns:
            列表，每个元素为 (dirpath, dirnames, filenames)
        """
        key_prefix = self._resolve_path(path)
        if key_prefix and not key_prefix.endswith("/"):
            key_prefix += "/"
        client = self._get_client()

        def _walk():
            objects = client.list_objects(
                self._bucket, prefix=key_prefix, recursive=True
            )
            # 收集所有路径
            all_paths = []
            for obj in objects:
                obj_key = obj.object_name
                if obj_key.endswith("/"):
                    continue  # 跳过目录标记
                all_paths.append(obj_key)

            # 构建目录结构
            dirs = {}
            files = {}
            for p in all_paths:
                rel = p[len(key_prefix):] if key_prefix else p
                parts = rel.split("/")
                if len(parts) == 1:
                    # 当前目录下的文件
                    files.setdefault("", []).append(parts[0])
                else:
                    # 子目录
                    dir_name = parts[0]
                    dirs.setdefault("", set()).add(dir_name)

            result = []
            # 添加当前目录
            result.append(("", list(dirs.get("", set())), files.get("", [])))
            # 添加子目录
            for dir_name in dirs.get("", set()):
                sub_prefix = key_prefix + dir_name + "/"
                sub_objects = client.list_objects(
                    self._bucket, prefix=sub_prefix, recursive=True
                )
                sub_files = []
                sub_dirs = set()
                for obj in sub_objects:
                    obj_key = obj.object_name
                    if obj_key.endswith("/"):
                        continue
                    rel = obj_key[len(sub_prefix):]
                    parts = rel.split("/")
                    if len(parts) == 1:
                        sub_files.append(parts[0])
                    else:
                        sub_dirs.add(parts[0])
                result.append((dir_name, list(sub_dirs), sub_files))

            return result

        return await asyncio.to_thread(_walk)

    # ========== 文件操作 ==========

    async def move(self, src: str, dst: str) -> None:
        """
        移动对象

        MinIO 实现：先 copy_object，再 remove_object。
        """
        src_key = self._resolve_path(src)
        dst_key = self._resolve_path(dst)
        client = self._get_client()

        def _move():
            # 复制源对象到目标
            try:
                result = client.copy_object(
                    self._bucket,
                    dst_key,
                    f"{self._bucket}/{src_key}",
                )
                # 删除源对象
                client.remove_object(self._bucket, src_key)
                logger.debug(f"MinIO 对象移动: {src_key} → {dst_key}")
            except Exception as e:
                logger.error(f"MinIO 对象移动失败: {src_key} → {dst_key}, error={e}")
                raise

        await asyncio.to_thread(_move)

    # ========== 扩展方法（非抽象接口） ==========

    async def chmod(self, path: str, mode: int) -> None:
        """
        设置权限

        MinIO 不支持文件权限，此方法为空操作。
        """
        logger.debug(f"MinIO 不支持 chmod 操作，已忽略: {path}")
        pass  # MinIO 不支持 POSIX 权限

    async def list_dir(self, path: str) -> list[str]:
        """
        列出目录中的对象名称

        Args:
            path: 目录路径

        Returns:
            对象名称列表
        """
        key_prefix = self._resolve_path(path)
        if key_prefix and not key_prefix.endswith("/"):
            key_prefix += "/"
        client = self._get_client()

        def _list():
            objects = client.list_objects(
                self._bucket, prefix=key_prefix, recursive=False
            )
            names = []
            for obj in objects:
                name = obj.object_name[len(key_prefix):]
                if name and not name.endswith("/"):
                    names.append(name)
            return names

        return await asyncio.to_thread(_list)
