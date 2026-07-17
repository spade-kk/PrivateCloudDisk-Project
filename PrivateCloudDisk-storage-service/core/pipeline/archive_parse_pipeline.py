"""
压缩包目录结构解析流水线

解析压缩包文件（ZIP/RAR/7Z/ISO/TAR/GZ/BZ2等），提取目录结构信息，
生成 JSON 格式目录树供前端预览，不进行完整解压。

技术方案:
  - 优先使用 libarchive-c 库（支持所有主流格式）
  - 回退使用 Python 内置 zipfile + tarfile 模块
  - 对每个条目仅读取元数据（文件名、大小、修改时间、类型），不解压文件内容
  - 生成的 JSON 目录树存储在 previews/ 目录下

与 OfficeToPdfPipeline 一致的设计模式:
  - 静态方法 execute() 作为入口
  - 返回统一的数据类结果
  - 异步执行，不阻塞事件循环
"""
from __future__ import annotations
import logging
import os
import json
import asyncio
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

from core.config import settings, FailureReason

logger = logging.getLogger("archive_parse_pipeline")


@dataclass
class ArchiveParseResult:
    """压缩包解析处理结果"""
    success: bool
    tree_json_path: str = ""        # 生成的目录树 JSON 文件路径
    total_files: int = 0            # 压缩包内文件总数
    total_dirs: int = 0             # 压缩包内目录总数
    total_size: int = 0             # 解压后总大小 (字节)
    error: str = ""
    failure_reason: str = ""


# 压缩包解析最大条目数（防止恶意压缩炸弹）
MAX_ENTRIES = 100_000
# 单个条目最大文件名长度
MAX_NAME_LENGTH = 4096
# 目录树 JSON 最大深度
MAX_TREE_DEPTH = 100


class ArchiveParsePipeline:
    """
    压缩包目录结构解析流水线

    处理流程:
      1. 验证文件存在且为支持的压缩包格式
      2. 使用 libarchive-c 或内置模块读取压缩包条目
      3. 遍历所有条目，构建目录树结构
      4. 限制最大条目数、最大深度，防止恶意压缩炸弹
      5. 序列化目录树为 JSON 并存储
      6. 返回处理结果
    """

    @staticmethod
    async def execute(
        file_id: str,
        storage_path: str,
        file_name: str,
        file_type: str,
    ) -> ArchiveParseResult:
        """
        执行压缩包目录结构解析

        Args:
            file_id: 文件 ID
            storage_path: 压缩包文件在存储层中的路径
            file_name: 原始文件名
            file_type: MIME 类型

        Returns:
            ArchiveParseResult: 处理结果
        """
        logger.info(
            f"[ARCHIVE_PARSE] START "
            f"file_id={file_id} "
            f"file_name={file_name} "
            f"file_type={file_type}"
        )

        # 验证文件存在
        if not os.path.exists(storage_path):
            logger.error(f"[ARCHIVE_PARSE] 文件不存在: {storage_path}")
            return ArchiveParseResult(
                success=False,
                failure_reason=FailureReason.ARCHIVE_PARSE_ERROR,
                error="压缩包文件不存在",
            )

        # 在事件循环线程池中执行同步解析操作
        try:
            loop = asyncio.get_event_loop()
            result = await loop.run_in_executor(
                None,
                ArchiveParsePipeline._parse_archive,
                storage_path, file_id, file_name,
            )
            return result
        except Exception as e:
            logger.error(f"[ARCHIVE_PARSE] 解析异常: {e}", exc_info=True)
            return ArchiveParseResult(
                success=False,
                failure_reason=FailureReason.ARCHIVE_PARSE_ERROR,
                error=str(e),
            )

    @staticmethod
    def _parse_archive(
        storage_path: str,
        file_id: str,
        file_name: str,
    ) -> ArchiveParseResult:
        """
        同步解析压缩包文件

        尝试顺序:
          1. libarchive-c (支持所有主流格式)
          2. Python zipfile (ZIP)
          3. Python tarfile (TAR/GZ/BZ2/XZ)
        """
        # 尝试 libarchive-c
        try:
            return ArchiveParsePipeline._parse_with_libarchive(
                storage_path, file_id, file_name
            )
        except ImportError:
            logger.info("[ARCHIVE_PARSE] libarchive-c 不可用，回退到内置模块")
        except Exception as e:
            logger.warning(f"[ARCHIVE_PARSE] libarchive-c 解析失败: {e}，尝试内置模块")

        # 尝试 zipfile
        try:
            return ArchiveParsePipeline._parse_with_zipfile(
                storage_path, file_id, file_name
            )
        except Exception as e:
            logger.warning(f"[ARCHIVE_PARSE] zipfile 解析失败: {e}，尝试 tarfile")

        # 尝试 tarfile
        try:
            return ArchiveParsePipeline._parse_with_tarfile(
                storage_path, file_id, file_name
            )
        except Exception as e:
            logger.error(f"[ARCHIVE_PARSE] tarfile 解析失败: {e}")
            return ArchiveParseResult(
                success=False,
                failure_reason=FailureReason.ARCHIVE_PARSE_ERROR,
                error=f"无法解析压缩包文件: {e}",
            )

    @staticmethod
    def _parse_with_libarchive(
        storage_path: str, file_id: str, file_name: str
    ) -> ArchiveParseResult:
        """使用 libarchive-c 解析压缩包"""
        import libarchive

        tree_root: dict[str, Any] = {
            "name": "",
            "type": "directory",
            "children": [],
        }
        total_files = 0
        total_dirs = 0
        total_size = 0
        entry_count = 0

        with libarchive.file_reader(storage_path) as archive:
            for entry in archive:
                entry_count += 1
                if entry_count > MAX_ENTRIES:
                    logger.warning(
                        f"[ARCHIVE_PARSE] 超过最大条目数 {MAX_ENTRIES}，停止解析"
                    )
                    break

                entry_path = str(entry.pathname).lstrip("/")
                if not entry_path or len(entry_path) > MAX_NAME_LENGTH:
                    continue

                entry_size = getattr(entry, 'size', 0) or 0
                entry_mtime = None
                if hasattr(entry, 'mtime') and entry.mtime:
                    try:
                        entry_mtime = datetime.fromtimestamp(
                            entry.mtime, tz=timezone.utc
                        ).isoformat()
                    except (ValueError, OSError):
                        pass

                is_dir = str(entry.pathname).endswith("/") or getattr(
                    entry, 'filetype', None
                ) == 'DIR'

                if is_dir:
                    total_dirs += 1
                else:
                    total_files += 1
                    total_size += entry_size

                ArchiveParsePipeline._insert_into_tree(
                    tree_root, entry_path, entry_size, entry_mtime, is_dir
                )

        # 存储 JSON
        json_path = ArchiveParsePipeline._save_tree_json(
            file_id, tree_root, total_files, total_dirs, total_size, file_name
        )

        logger.info(
            f"[ARCHIVE_PARSE] libarchive 解析完成 "
            f"files={total_files} dirs={total_dirs} "
            f"total_size={total_size}"
        )
        return ArchiveParseResult(
            success=True,
            tree_json_path=json_path,
            total_files=total_files,
            total_dirs=total_dirs,
            total_size=total_size,
        )

    @staticmethod
    def _parse_with_zipfile(
        storage_path: str, file_id: str, file_name: str
    ) -> ArchiveParseResult:
        """使用 Python 内置 zipfile 模块解析 ZIP 文件"""
        import zipfile

        tree_root: dict[str, Any] = {
            "name": "",
            "type": "directory",
            "children": [],
        }
        total_files = 0
        total_dirs = 0
        total_size = 0
        entry_count = 0

        with zipfile.ZipFile(storage_path, "r") as zf:
            for info in zf.infolist():
                entry_count += 1
                if entry_count > MAX_ENTRIES:
                    logger.warning(
                        f"[ARCHIVE_PARSE] 超过最大条目数 {MAX_ENTRIES}，停止解析"
                    )
                    break

                entry_path = info.filename.rstrip("/").lstrip("/")
                if not entry_path or len(entry_path) > MAX_NAME_LENGTH:
                    continue

                is_dir = info.is_dir()
                entry_size = info.file_size if not is_dir else 0
                entry_mtime = datetime(
                    *info.date_time, tzinfo=timezone.utc
                ).isoformat() if info.date_time else None

                if is_dir:
                    total_dirs += 1
                else:
                    total_files += 1
                    total_size += entry_size

                ArchiveParsePipeline._insert_into_tree(
                    tree_root, entry_path, entry_size, entry_mtime, is_dir
                )

        json_path = ArchiveParsePipeline._save_tree_json(
            file_id, tree_root, total_files, total_dirs, total_size, file_name
        )

        logger.info(
            f"[ARCHIVE_PARSE] zipfile 解析完成 "
            f"files={total_files} dirs={total_dirs} "
            f"total_size={total_size}"
        )
        return ArchiveParseResult(
            success=True,
            tree_json_path=json_path,
            total_files=total_files,
            total_dirs=total_dirs,
            total_size=total_size,
        )

    @staticmethod
    def _parse_with_tarfile(
        storage_path: str, file_id: str, file_name: str
    ) -> ArchiveParseResult:
        """使用 Python 内置 tarfile 模块解析 TAR 文件"""
        import tarfile

        tree_root: dict[str, Any] = {
            "name": "",
            "type": "directory",
            "children": [],
        }
        total_files = 0
        total_dirs = 0
        total_size = 0
        entry_count = 0

        with tarfile.open(storage_path, "r:*") as tf:
            for member in tf:
                entry_count += 1
                if entry_count > MAX_ENTRIES:
                    logger.warning(
                        f"[ARCHIVE_PARSE] 超过最大条目数 {MAX_ENTRIES}，停止解析"
                    )
                    break

                entry_path = member.name.lstrip("/")
                if not entry_path or len(entry_path) > MAX_NAME_LENGTH:
                    continue

                is_dir = member.isdir()
                entry_size = member.size if not is_dir else 0
                entry_mtime = datetime.fromtimestamp(
                    member.mtime, tz=timezone.utc
                ).isoformat() if member.mtime else None

                if is_dir:
                    total_dirs += 1
                else:
                    total_files += 1
                    total_size += entry_size

                ArchiveParsePipeline._insert_into_tree(
                    tree_root, entry_path, entry_size, entry_mtime, is_dir
                )

        json_path = ArchiveParsePipeline._save_tree_json(
            file_id, tree_root, total_files, total_dirs, total_size, file_name
        )

        logger.info(
            f"[ARCHIVE_PARSE] tarfile 解析完成 "
            f"files={total_files} dirs={total_dirs} "
            f"total_size={total_size}"
        )
        return ArchiveParseResult(
            success=True,
            tree_json_path=json_path,
            total_files=total_files,
            total_dirs=total_dirs,
            total_size=total_size,
        )

    @staticmethod
    def _insert_into_tree(
        root: dict[str, Any],
        entry_path: str,
        size: int,
        mtime: str | None,
        is_dir: bool,
    ):
        """
        将条目插入目录树结构

        基于路径分隔符逐级创建目录节点，确保层级结构正确。
        使用深度限制防止恶意递归路径。
        """
        parts = [p for p in entry_path.split("/") if p]
        if not parts:
            return

        current = root
        depth = 0

        for i, part in enumerate(parts):
            depth += 1
            if depth > MAX_TREE_DEPTH:
                logger.warning(f"[ARCHIVE_PARSE] 超过最大深度 {MAX_TREE_DEPTH}")
                return

            is_last = (i == len(parts) - 1)

            # 查找或创建子节点
            children: list[dict] = current.setdefault("children", [])
            found = None
            for child in children:
                if child["name"] == part:
                    found = child
                    break

            if found is None:
                node: dict[str, Any] = {
                    "name": part,
                    "type": "directory" if (not is_last or is_dir) else "file",
                    "children": [] if (not is_last or is_dir) else None,
                }
                if is_last and not is_dir:
                    node["size"] = size
                    if mtime:
                        node["modified"] = mtime
                children.append(node)
                current = node
            else:
                current = found

    @staticmethod
    def _save_tree_json(
        file_id: str,
        tree_root: dict[str, Any],
        total_files: int,
        total_dirs: int,
        total_size: int,
        file_name: str,
    ) -> str:
        """
        序列化目录树为 JSON 并存储到 previews 目录

        返回 JSON 文件路径
        """
        previews_dir = os.path.join(settings.file_upload_dir, "previews")
        os.makedirs(previews_dir, exist_ok=True)

        # 文件名格式: {file_id}_archive_tree.json
        json_filename = f"{file_id}_archive_tree.json"
        json_path = os.path.join(previews_dir, json_filename)

        output = {
            "fileId": file_id,
            "fileName": file_name,
            "totalFiles": total_files,
            "totalDirs": total_dirs,
            "totalSize": total_size,
            "parsedAt": datetime.now(timezone.utc).isoformat(),
            "tree": tree_root,
        }

        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(output, f, ensure_ascii=False, indent=2)

        logger.info(
            f"[ARCHIVE_PARSE] 目录树 JSON 已保存: {json_path} "
            f"size={os.path.getsize(json_path)} bytes"
        )
        return json_path