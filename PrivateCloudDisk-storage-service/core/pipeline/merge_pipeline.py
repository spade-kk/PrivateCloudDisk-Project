"""
文件合并流水线
负责将分片文件合并为完整文件，并进行 SHA-256 校验
"""
from __future__ import annotations
import logging
import os
import hashlib
import shutil
from dataclasses import dataclass
from typing import Optional
from core.config import settings, FailureReason
from core.services.notification_service import NotificationService

logger = logging.getLogger("merge_pipeline")


@dataclass
class MergeResult:
    success: bool
    file_id: str = ""
    storage_path: str = ""
    checksum: str = ""
    file_size: int = 0
    error: str = ""
    failure_reason: str = ""


class MergePipeline:
    """文件合并处理流水线"""

    @staticmethod
    async def execute(
        uploads_id: str,
        user_id: str,
        file_id: str,
        total_chunks: int,
        file_name: str,
        expected_checksum: str,
    ) -> MergeResult:
        """
        执行文件合并

        流程:
        1. 检查磁盘空间
        2. 按顺序合并分片
        3. 边合并边计算 SHA-256
        4. 校验文件完整性
        5. 通知业务服务创建文件记录
        6. 清理分片文件
        """
        logger.info(f"开始合并文件: uploads_id={uploads_id}, file_name={file_name}, total_chunks={total_chunks}")

        session_dir = settings.file_upload_dir
        final_dir = os.path.join(session_dir, "storage")
        os.makedirs(final_dir, exist_ok=True)

        final_path = os.path.join(final_dir, f"{uploads_id}-{total_chunks}.cloud")

        try:
            # 1. 检查磁盘空间
            await MergePipeline._check_disk_space(final_dir)

            # 2. 合并分片
            file_hash = hashlib.sha256()
            total_bytes = 0

            with open(final_path, "wb") as final_file:
                for i in range(1, total_chunks + 1):
                    chunk_path = os.path.join(session_dir, f"{uploads_id}-{i}.part")

                    if not os.path.exists(chunk_path):
                        # 清理已合并的部分
                        MergePipeline._cleanup_merge(final_path, session_dir, uploads_id, total_chunks, i)
                        return MergeResult(
                            success=False,
                            failure_reason=FailureReason.MERGE_CHUNK_MISSING,
                            error=f"分片第 {i} 块缺失: {chunk_path}",
                        )

                    try:
                        with open(chunk_path, "rb") as chunk_file:
                            while content := chunk_file.read(128 * 1024):
                                final_file.write(content)
                                file_hash.update(content)
                                total_bytes += len(content)
                    except IOError as e:
                        MergePipeline._cleanup_merge(final_path, session_dir, uploads_id, total_chunks, i)
                        return MergeResult(
                            success=False,
                            failure_reason=FailureReason.MERGE_IO_ERROR,
                            error=f"读取分片 {i} 失败: {e}",
                        )

            # 3. 校验
            actual_checksum = file_hash.hexdigest()
            if expected_checksum and actual_checksum != expected_checksum:
                MergePipeline._cleanup_merge(final_path, session_dir, uploads_id, total_chunks, total_chunks)
                return MergeResult(
                    success=False,
                    failure_reason=FailureReason.MERGE_CHECKSUM_MISMATCH,
                    error=f"校验和不匹配: 期望 {expected_checksum[:16]}..., 实际 {actual_checksum[:16]}...",
                )

            # 4. 通知业务服务创建文件记录
            try:
                await NotificationService.notify_file_merged(
                    uploads_id=uploads_id,
                    file_id=file_id,
                    storage_path=final_path,
                    user_id=user_id
                )
            except Exception as e:
                MergePipeline._cleanup_merge(final_path, session_dir, uploads_id, total_chunks, total_chunks)
                return MergeResult(
                    success=False,
                    failure_reason=FailureReason.NOTIFY_BS_ERROR,
                    error=f"通知业务服务失败: {e}",
                )

            # 5. 清理分片文件
            MergePipeline._cleanup_chunks(session_dir, uploads_id, total_chunks)

            logger.info(
                f"文件合并完成: file_id={file_id}, file_name={file_name}, "
                f"size={total_bytes} bytes, checksum={actual_checksum}"
            )

            return MergeResult(
                success=True,
                file_id=file_id,
                storage_path=final_path,
                checksum=actual_checksum,
                file_size=total_bytes,
            )

        except Exception as e:
            MergePipeline._cleanup_merge(final_path, session_dir, uploads_id, total_chunks, total_chunks)
            logger.error(f"文件合并异常: {e}")
            return MergeResult(
                success=False,
                failure_reason=FailureReason.MERGE_IO_ERROR,
                error=str(e),
            )

    @staticmethod
    async def _check_disk_space(directory: str):
        """检查磁盘剩余空间"""
        stat = shutil.disk_usage(directory)
        if stat.free < settings.min_free_disk_bytes:
            free_mb = stat.free / (1024 * 1024)
            required_mb = settings.min_free_disk_bytes / (1024 * 1024)
            raise OSError(
                f"磁盘空间不足: 可用 {free_mb:.1f}MB, 需要至少 {required_mb:.1f}MB"
            )

    @staticmethod
    def _cleanup_merge(final_path: str, session_dir: str, uploads_id: str, total_chunks: int, current_chunk: int):
        """清理合并失败的文件"""
        if os.path.exists(final_path):
            os.remove(final_path)
            logger.warning(f"已清理合并失败的文件: {final_path}")

    @staticmethod
    def _cleanup_chunks(session_dir: str, uploads_id: str, total_chunks: int):
        """清理所有分片文件"""
        for i in range(1, total_chunks + 1):
            chunk_path = os.path.join(session_dir, f"{uploads_id}-{i}.part")
            if os.path.exists(chunk_path):
                os.remove(chunk_path)
        logger.debug(f"已清理 {total_chunks} 个分片文件")