"""
文件处理主服务
路由不同任务类型到对应的流水线进行处理
"""
from __future__ import annotations
import logging
import os
from dataclasses import dataclass, field
from typing import Any
from core.config import TaskTypes, FailureReason
from core.pipeline.merge_pipeline import MergePipeline, MergeResult
from core.pipeline.hash_pipeline import HashPipeline, HashResult
from core.pipeline.virus_scan_pipeline import VirusScanPipeline, VirusScanResult
from core.pipeline.thumbnail_pipeline import ThumbnailPipeline, ThumbnailResult
from core.pipeline.transcode_pipeline import TranscodePipeline, TranscodeResult
from core.pipeline.hls_transcode_pipeline import HlsTranscodePipeline, HlsTranscodeResult
from core.pipeline.mark_active_pipeline import MarkActivePipeline, MarkActiveResult
from core.pipeline.content_index_pipeline import ContentIndexPipeline, ContentIndexResult
from core.pipeline.office_to_pdf_pipeline import OfficeToPdfPipeline, OfficeToPdfResult
from core.pipeline.archive_parse_pipeline import ArchiveParsePipeline, ArchiveParseResult

logger = logging.getLogger("file_processor")


@dataclass
class ProcessResult:
    """统一的处理结果"""
    success: bool
    task_type: str = ""
    failure_reason: str = ""
    error: str = ""
    data: dict = field(default_factory=dict)
    # W-03：业务流水线可明确声明异常是否可重试；None 时由消费者按 failure_reason 分类。
    # 原有调用方未传该字段时行为保持兼容。
    retryable: bool | None = None


class FileProcessor:
    """
    文件处理主服务

    根据 task_type 将消息分发到对应的流水线:
    - merge → MergePipeline
    - hash_calculate → HashPipeline
    - virus_scan → VirusScanPipeline
    - thumbnail → ThumbnailPipeline
    - video_transcode → TranscodePipeline
    - hls_transcode → HlsTranscodePipeline
    - mark_active → MarkActivePipeline
    - content_index → ContentIndexPipeline
    """

    @staticmethod
    async def process(event: Any) -> ProcessResult:
        """
        主处理入口

        Args:
            event: BackendTaskEvent 或 EnhanceTaskEvent 实例

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
                case TaskTypes.HLS_TRANSCODE:
                    return await FileProcessor._do_hls_transcode(event)
                case TaskTypes.MARK_ACTIVE:
                    return await FileProcessor._do_mark_active(event)
                case TaskTypes.CONTENT_INDEX:
                    return await FileProcessor._do_content_index(event)
                case TaskTypes.OFFICE_TO_PDF:
                    return await FileProcessor._do_office_to_pdf(event)
                case TaskTypes.ARCHIVE_PARSE:
                    return await FileProcessor._do_archive_parse(event)
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
            file_id=event.file_id,
            space_id=getattr(event, "space_id", ""),
            space_type=getattr(event, "space_type", ""),
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
    async def _do_hls_transcode(event) -> ProcessResult:
        async def persist_hover_preview(path: str, source_info: dict) -> None:
            """
            需求六-1：快速悬停预览使用独立数据库事务即时入库。

            原行为由 BaseEnhanceConsumer 在整条 HLS 流水线结束后统一写入；新行为在 30 秒
            MP4 生成后立即提交。最终统一持久化仍保留为幂等兜底，不影响 HLS/VTT 一致性。
            """
            from app.models.preview_resource import PreviewResource
            from app.services.preview_resource_service import preview_resource_service

            await preview_resource_service.upsert(PreviewResource(
                file_id=event.file_id,
                user_id=event.user_id,
                space_id=event.space_id or None,
                resource_type="video_preview",
                resource_variant="30s",
                storage_path=path,
                mime_type="video/mp4",
                size_bytes=os.path.getsize(path) if os.path.isfile(path) else 0,
                width=source_info.get("width"),
                height=source_info.get("height"),
                duration_seconds=min(float(source_info.get("duration") or 0), 30.0),
                metadata={
                    "purpose": "file_browser_hover",
                    "max_duration_seconds": 30,
                    "commit_scope": "independent_before_hls",
                },
            ))

        result: HlsTranscodeResult = await HlsTranscodePipeline.execute(
            file_id=event.file_id,
            storage_path=event.storage_path,
            file_type=event.file_type,
            hover_preview_ready=persist_hover_preview,
        )
        return ProcessResult(
            success=result.success,
            task_type=TaskTypes.HLS_TRANSCODE,
            failure_reason=result.failure_reason,
            error=result.error,
            data={
                "hls_dir": result.hls_dir,
                "hls_master_playlist": result.hls_master_playlist,
                "hls_resolutions": result.hls_resolutions,
                "resolutions": result.resolutions,
                "preview_paths": result.preview_paths,
                "hover_preview_path": result.hover_preview_path,
                "manifest_path": result.manifest_path,
                "source_width": result.source_width,
                "source_height": result.source_height,
                "duration": result.duration,
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
            storage_path=event.storage_path,
            checksum=event.file_checksum,
            file_size=event.file_size,
            content_revision=event.content_revision,
            content_modified=event.content_modified,
            preprocess_status=event.preprocess_status,
            space_id=event.space_id,
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
            # 需求五-9：增强事件恢复空间上下文，全文索引以 space_id 隔离。
            space_id=getattr(event, 'space_id', ''),
        )
        return ProcessResult(
            success=result.success,
            task_type=TaskTypes.CONTENT_INDEX,
            failure_reason=result.failure_reason,
            error=result.error,
            data=result.data,
        )

    @staticmethod
    async def _do_office_to_pdf(event) -> ProcessResult:
        """
        执行 Office 文件转 PDF 增强处理

        将 Office 文档（Word/Excel/PPT）转换为 PDF 格式，
        同时为 PDF 文件生成首页缩略图。

        Args:
            event: FileEnhanceEvent 增强事件

        Returns:
            ProcessResult: 统一处理结果
        """
        result: OfficeToPdfResult = await OfficeToPdfPipeline.execute(
            file_id=event.file_id,
            storage_path=event.storage_path,
            file_type=event.file_type,
        )
        return ProcessResult(
            success=result.success,
            task_type=TaskTypes.OFFICE_TO_PDF,
            failure_reason=result.failure_reason,
            error=result.error,
            data={
                "pdf_path": result.pdf_path,
                "pdf_size": result.pdf_size,
                "preview_path": result.preview_path,
                # AUDIT FIX [5.2]（需求五）：向持久化层传递四档预览图及真实尺寸，旧字段继续保留兼容。
                "preview_paths": result.preview_paths,
                "preview_metadata": result.preview_metadata,
                "page_count": result.page_count,
                "source_type": result.source_type,
                "skipped": result.skipped,
            },
        )

    @staticmethod
    async def _do_archive_parse(event) -> ProcessResult:
        """
        执行压缩包目录结构解析增强处理

        解析压缩包文件（ZIP/RAR/7Z/ISO等），提取目录结构信息，
        生成 JSON 格式目录树供前端预览。不解压完整文件内容。

        Args:
            event: FileEnhanceEvent 增强事件

        Returns:
            ProcessResult: 统一处理结果
        """
        result: ArchiveParseResult = await ArchiveParsePipeline.execute(
            file_id=event.file_id,
            storage_path=event.storage_path,
            file_name=event.file_name,
            file_type=event.file_type,
        )
        return ProcessResult(
            success=result.success,
            task_type=TaskTypes.ARCHIVE_PARSE,
            failure_reason=result.failure_reason,
            error=result.error,
            data={
                "tree_json_path": result.tree_json_path,
                "total_files": result.total_files,
                "total_dirs": result.total_dirs,
                "total_size": result.total_size,
            },
        )
