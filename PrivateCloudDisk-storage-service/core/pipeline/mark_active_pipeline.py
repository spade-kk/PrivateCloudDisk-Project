"""
标记活跃流水线
通知业务服务文件处理完成，标记文件为活跃状态
"""
from __future__ import annotations
import logging
from dataclasses import dataclass
from core.config import FailureReason
from core.services.notification_service import NotificationService

logger = logging.getLogger("mark_active_pipeline")


@dataclass
class MarkActiveResult:
    success: bool
    error: str = ""
    failure_reason: str = ""


class MarkActivePipeline:
    """文件标记活跃流水线"""

    @staticmethod
    async def execute(
        file_id: str,
        user_id: str,
        thumbnails: list | None = None,
        transcoded: list | None = None,
        storage_path: str | None = None,
        checksum: str | None = None,
        file_size: int | None = None,
        content_revision: int = 0,
        content_modified: bool = False,
        preprocess_status: str = "",
        space_id: str = "",
    ) -> MarkActiveResult:
        """
        标记文件为活跃状态

        流程:
        1. 通知业务服务文件处理完成
        2. 附带缩略图和转码文件路径信息
        """
        logger.info(f"标记文件为活跃: file_id={file_id}")

        try:
            await NotificationService.notify_file_activate(
                file_id=file_id,
                user_id=user_id,
                storage_path=storage_path,
                checksum=checksum,
                file_size=file_size,
                content_revision=content_revision,
                content_modified=content_modified,
                preprocess_status=preprocess_status,
                space_id=space_id,
            )

            # 如果有缩略图或转码信息，更新文件状态
            if thumbnails or transcoded:
                await NotificationService.notify_file_status(
                    file_id=file_id,
                    status="active",
                    user_id=user_id,
                    error_message="",
                )

            logger.info(f"文件已标记为活跃: file_id={file_id}")
            return MarkActiveResult(success=True)

        except Exception as e:
            logger.error(f"标记活跃失败: file_id={file_id}, error={e}")
            return MarkActiveResult(
                success=False,
                failure_reason=FailureReason.MARK_ACTIVE_ERROR,
                error=str(e),
            )
