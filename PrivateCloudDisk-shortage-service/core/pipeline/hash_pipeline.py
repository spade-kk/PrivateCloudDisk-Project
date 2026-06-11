"""
Hash 计算流水线
对合并后的文件计算 SHA-256，与前端上传前的哈希进行比对
"""
from __future__ import annotations
import logging
import os
import hashlib
from dataclasses import dataclass
from core.config import FailureReason

logger = logging.getLogger("hash_pipeline")


@dataclass
class HashResult:
    success: bool
    checksum: str = ""
    error: str = ""
    failure_reason: str = ""


class HashPipeline:
    """文件 Hash 计算流水线"""

    @staticmethod
    async def execute(
        storage_path: str,
        expected_checksum: str = "",
    ) -> HashResult:
        """
        计算文件 SHA-256

        Args:
            storage_path: 文件路径
            expected_checksum: 期望的校验和 (可选)
        """
        logger.info(f"开始计算哈希: {storage_path}")

        if not os.path.exists(storage_path):
            return HashResult(
                success=False,
                failure_reason=FailureReason.HASH_CALCULATE_ERROR,
                error=f"文件不存在: {storage_path}",
            )

        try:
            import asyncio

            file_hash = hashlib.sha256()

            def _compute():
                with open(storage_path, "rb") as f:
                    while chunk := f.read(128 * 1024):
                        file_hash.update(chunk)
                return file_hash.hexdigest()

            actual_checksum = await asyncio.to_thread(_compute)

            if expected_checksum and actual_checksum != expected_checksum:
                return HashResult(
                    success=False,
                    failure_reason=FailureReason.HASH_MISMATCH,
                    error=f"哈希不匹配: 期望 {expected_checksum[:16]}..., 实际 {actual_checksum[:16]}...",
                    checksum=actual_checksum,
                )

            logger.info(f"哈希计算完成: checksum={actual_checksum}")
            return HashResult(success=True, checksum=actual_checksum)

        except Exception as e:
            logger.error(f"哈希计算异常: {e}")
            return HashResult(
                success=False,
                failure_reason=FailureReason.HASH_CALCULATE_ERROR,
                error=str(e),
            )