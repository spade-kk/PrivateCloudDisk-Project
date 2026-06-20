"""
隔离区管理器
当发现病毒/木马文件时，将文件移动到隔离目录并记录详细信息
"""
from __future__ import annotations
import logging
import os
import shutil
import hashlib
from datetime import datetime, timezone
from typing import Optional
from core.config import settings

logger = logging.getLogger("quarantine_manager")


class QuarantineManager:
    """
    文件隔离管理器

    职责:
    1. 将受感染文件移动到隔离目录
    2. 生成隔离元数据文件 (.meta.json)
    3. 计算隔离文件的 SHA-256 哈希
    4. 支持隔离区文件列表查询
    """

    def __init__(self, quarantine_dir: str | None = None):
        self.quarantine_dir = quarantine_dir or settings.quarantine_dir
        os.makedirs(self.quarantine_dir, exist_ok=True)

    async def quarantine_file(
        self,
        file_path: str,
        file_id: str,
        user_id: str,
        threat_name: str,
        scanner_output: str = "",
    ) -> dict:
        """
        将文件移入隔离区

        Args:
            file_path: 原始文件路径
            file_id: 文件 ID
            user_id: 上传用户 ID
            threat_name: 病毒/木马名称
            scanner_output: 扫描器原始输出

        Returns:
            dict: {
                "quarantine_path": 隔离后的文件路径,
                "file_hash": 文件 SHA-256,
                "meta_path": 元数据文件路径,
            }
        """
        quarantine_path = self._build_quarantine_path(file_id, file_path)
        os.makedirs(os.path.dirname(quarantine_path), exist_ok=True)

        # 计算原始文件哈希
        file_hash = await self._compute_sha256(file_path)

        # 移动文件到隔离区
        shutil.move(file_path, quarantine_path)
        os.chmod(quarantine_path, 0o400)  # 只读，防止误执行
        logger.warning(f"⚠ 文件已隔离: {file_path} → {quarantine_path}")

        # 写入隔离元数据
        meta = {
            "file_id": file_id,
            "user_id": user_id,
            "original_path": file_path,
            "quarantine_path": quarantine_path,
            "threat_name": threat_name,
            "scanner_output": scanner_output,
            "file_hash": file_hash,
            "quarantined_at": datetime.now(timezone.utc).isoformat(),
            "status": "quarantined",
        }
        meta_path = quarantine_path + ".meta.json"
        import json
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump(meta, f, indent=2, ensure_ascii=False)

        logger.warning(
            f"⚠ 隔离元数据已保存: {meta_path}\n"
            f"  文件ID: {file_id}\n"
            f"  用户ID: {user_id}\n"
            f"  威胁名称: {threat_name}\n"
            f"  文件哈希: {file_hash}\n"
            f"  隔离路径: {quarantine_path}"
        )

        return {
            "quarantine_path": quarantine_path,
            "file_hash": file_hash,
            "meta_path": meta_path,
        }

    async def remove_from_quarantine(self, file_id: str) -> bool:
        """从隔离区彻底删除文件"""
        for item in os.listdir(self.quarantine_dir):
            if item.startswith(file_id):
                full_path = os.path.join(self.quarantine_dir, item)
                if os.path.isfile(full_path):
                    os.remove(full_path)
                    logger.info(f"隔离文件已删除: {full_path}")
                elif os.path.isdir(full_path):
                    shutil.rmtree(full_path)
                    logger.info(f"隔离目录已删除: {full_path}")
        return True

    async def release_from_quarantine(self, file_id: str, original_path: str) -> bool:
        """
        从隔离区释放文件回原路径 (误报处理)
        仅管理员手动操作时调用
        """
        for item in os.listdir(self.quarantine_dir):
            if item.startswith(file_id) and not item.endswith(".meta.json"):
                full_path = os.path.join(self.quarantine_dir, item)
                os.makedirs(os.path.dirname(original_path), exist_ok=True)
                shutil.move(full_path, original_path)
                os.chmod(original_path, 0o644)
                logger.info(f"文件已从隔离区释放: {full_path} → {original_path}")
                return True
        return False

    def _build_quarantine_path(self, file_id: str, original_path: str) -> str:
        """构建隔离区文件路径"""
        ext = os.path.splitext(original_path)[1] or ".bin"
        date_prefix = datetime.now().strftime("%Y%m%d")
        return os.path.join(
            self.quarantine_dir,
            date_prefix,
            f"{file_id}{ext}",
        )

    @staticmethod
    async def _compute_sha256(file_path: str) -> str:
        """计算文件 SHA-256"""
        import asyncio
        h = hashlib.sha256()

        def _read():
            with open(file_path, "rb") as f:
                while chunk := f.read(128 * 1024):
                    h.update(chunk)
            return h.hexdigest()

        return await asyncio.to_thread(_read)


# 全局单例
quarantine_manager = QuarantineManager()