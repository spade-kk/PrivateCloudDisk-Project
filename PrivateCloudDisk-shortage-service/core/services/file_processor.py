"""
文件处理主服务
路由不同任务类型到对应的流水线进行处理
"""
from __future__ import annotations
import logging
from dataclasses import dataclass, field
from typing import Any
from core.config import TaskTypes, FailureReason
from core.pipeline.merge_pipeline import MergePipeline, MergeResult
from core.pipeline.hash_pipeline import HashPipeline, HashResult
from core.pipeline.virus_scan_pipeline import VirusScanPipeline, VirusScanResult
from core.pipeline.thumbnail_pipeline import ThumbnailPipeline, ThumbnailResult
from core.pipeline.transcode_pipeline import TranscodePipeline, TranscodeResult
from core.pipeline.mark_active_pipeline import MarkActivePipeline, MarkActiveResult
from core.pipeline.content_index_pipeline import ContentIndexPipeline, ContentIndexResult
from core.services.notification_service import NotificationService

logger = logging.getLogger("file_processor")


@dataclass
class ProcessResult:
    """统一的处理结果"""
    success: bool
    task_type: str = ""
    failure_reason: str = ""
    error: str = ""
    data: dict = field(default_factory=dict)


class FileProcessor:
    """
    文件处理主服务

    根据 task_type 将消息分发到对应的流水线:
    - merge → MergePipeline
    - hash_calculate → HashPipeline
    - virus_scan → VirusScanPipeline
    - thumbnail → ThumbnailPipeline
    - video_transcode → TranscodePipeline
    - mark_active → MarkActivePipeline
    - content_index → ContentIndexPipeline
    """

    @staticmethod
    async def process(event: Any) -> ProcessResult:
        """
        主处理入口

        Args:
            event: FileProcessEvent 实例

        Returns:
            ProcessResult: 统一处理结果
        """
        task_type = event.task_type
        logger.info(f"处理任务: task_type={task_type}, file_id={event.file_id}")

        try:
            match task_type:
                case TaskTypes.MERGE:
                    return await FileProcessor._do_merge(event)
                case TaskTypes.HASH_CALCULATE:
                    return await FileProcessor._do_hash_calculate(event)
                case TaskTypes.VIRUS_SCAN:
                    return await FileProcessor._do_virus_scan(event)
                case TaskTypes.THUMBNAIL:
                    return await FileProcessor._do_thumbnail(event)
                case TaskTypes.VIDEO_TRANSCODE:
                    return await FileProcessor._do_video_transcode(event)
                case TaskTypes.MARK_ACTIVE:
                    return await FileProcessor._do_mark_active(event)
                case TaskTypes.CONTENT_INDEX:
                    return await FileProcessor._do_content_index(event)
                case _:
                    logger.warning(f"未知任务类型: {task_type}")
                    return ProcessResult(
                        success=False,
                        task_type=task_type,
                        failure_reason=FailureReason.UNKNOWN,
                        error=f"未知任务类型: {task_type}",
                    )
        except Exception as e:
            logger.error(f"处理异常: task_type={task_type}, error={e}")
            return ProcessResult(
                success=False,
                task_type=task_type,
                failure_reason=FailureReason.UNKNOWN,
                error=str(e),
            )

    @staticmethod
    async def _do_merge(event) -> ProcessResult:
        result: MergeResult = await MergePipeline.execute(
            uploads_id=event.uploads_id if hasattr(event, 'uploads_id') else event.get('uploads_id', ''),
            user_id=event.user_id,
            total_chunks=event.total_chunks,
            file_name=event.file_name,
            expected_checksum=event.file_checksum,
            file_id=event.file_id
        )
        return ProcessResult(
            success=result.success,
            task_type=TaskTypes.MERGE,
            failure_reason=result.failure_reason,
            error=result.error,
            data={"file_id": result.file_id, "storage_path": result.storage_path,
                  "checksum": result.checksum, "file_size": result.file_size},
        )

    @staticmethod
    async def _do_hash_calculate(event) -> ProcessResult:
        result: HashResult = await HashPipeline.execute(
            storage_path=event.storage_path,
            expected_checksum=event.file_checksum,
        )
        return ProcessResult(
            success=result.success,
            task_type=TaskTypes.HASH_CALCULATE,
            failure_reason=result.failure_reason,
            error=result.error,
            data={"checksum": result.checksum},
        )

    @staticmethod
    async def _do_virus_scan(event) -> ProcessResult:
        result: VirusScanResult = await VirusScanPipeline.execute(
            file_id=event.file_id,
            user_id=event.user_id,
            storage_path=event.storage_path,
            file_name=event.file_name,
            file_type=event.file_type,
            file_size=event.file_size,
            task_id=event.task_id,
        )
        return ProcessResult(
            success=result.success,
            task_type=TaskTypes.VIRUS_SCAN,
            failure_reason=result.failure_reason,
            error=result.error,
            data={
                "infected": result.infected,
                "threat_name": result.threat_name,
                "skipped": result.skipped,
            },
        )

    @staticmethod
    async def _do_thumbnail(event) -> ProcessResult:
        result: ThumbnailResult = await ThumbnailPipeline.execute(
            file_id=event.file_id,
            storage_path=event.storage_path,
            file_type=event.file_type,
        )
        return ProcessResult(
            success=result.success,
            task_type=TaskTypes.THUMBNAIL,
            failure_reason=result.failure_reason,
            error=result.error,
            data={
                "thumbnails": result.thumbnails,
                "skipped": result.skipped,
            },
        )

    @staticmethod
    async def _do_video_transcode(event) -> ProcessResult:
        result: TranscodeResult = await TranscodePipeline.execute(
            file_id=event.file_id,
            storage_path=event.storage_path,
            file_type=event.file_type,
        )
        return ProcessResult(
            success=result.success,
            task_type=TaskTypes.VIDEO_TRANSCODE,
            failure_reason=result.failure_reason,
            error=result.error,
            data={
                "transcoded_files": result.transcoded_files,
                "skipped": result.skipped,
            },
        )

    @staticmethod
    async def _do_mark_active(event) -> ProcessResult:
        # 收集之前步骤的缩略图和转码信息
        thumbnails = getattr(event, 'thumbnails', [])
        transcoded = getattr(event, 'transcoded_files', [])
        result: MarkActiveResult = await MarkActivePipeline.execute(
            file_id=event.file_id,
            user_id=event.user_id,
            thumbnails=thumbnails,
            transcoded=transcoded,
        )
        return ProcessResult(
            success=result.success,
            task_type=TaskTypes.MARK_ACTIVE,
            failure_reason=result.failure_reason,
            error=result.error,
        )

    @staticmethod
    async def _do_content_index(event) -> ProcessResult:
        result: ContentIndexResult = await ContentIndexPipeline.execute(
            file_id=event.file_id,
            user_id=event.user_id,
            storage_path=event.storage_path,
            file_name=event.file_name,
            file_type=event.file_type,
            file_size=event.file_size,
            node_id=getattr(event, 'node_id', ''),
            created_at=getattr(event, 'created_at', ''),
        )
        return ProcessResult(
            success=result.success,
            task_type=TaskTypes.CONTENT_INDEX,
            failure_reason=result.failure_reason,
            error=result.error,
            data=result.data,
        )