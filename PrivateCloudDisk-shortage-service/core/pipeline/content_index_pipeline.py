"""
文件内容索引流水线

负责:
1. 根据文件类型匹配抽取器
2. 抽取文件内容 (文本/表格/OCR)
3. 生成文本分块
4. 写入 OpenSearch 基本信息索引 + 内容索引
"""
from __future__ import annotations
import logging
import os
from dataclasses import dataclass, field
from pathlib import Path

from core.config import settings, FailureReason
from core.extractors.registry import get_extractor_for_file
from core.extractors.base import ExtractionResult
from core.search.index_service import IndexService

logger = logging.getLogger("content_index_pipeline")


@dataclass
class ContentIndexResult:
    """内容索引结果"""
    success: bool
    file_id: str = ""
    failure_reason: str = ""
    error: str = ""
    skipped: bool = False
    skipped_reason: str = ""
    data: dict = field(default_factory=dict)


class ContentIndexPipeline:
    """文件内容索引处理流水线"""

    @staticmethod
    async def execute(
        file_id: str,
        user_id: str,
        storage_path: str,
        file_name: str,
        file_type: str,
        file_size: int = 0,
        node_id: str = "",
        created_at: str = "",
    ) -> ContentIndexResult:
        """
        执行内容索引

        处理流程:
        1. 检查文件是否存在
        2. 匹配抽取器
        3. 抽取文件内容
        4. 写入 OpenSearch 基本信息索引
        5. 写入 OpenSearch 内容索引

        Args:
            file_id: 文件 ID
            user_id: 用户 ID
            storage_path: 文件存储路径
            file_name: 文件名
            file_type: MIME 类型
            file_size: 文件大小
            node_id: 目录节点 ID
            created_at: 创建时间 (ISO 格式)

        Returns:
            ContentIndexResult
        """
        logger.info(f"开始内容索引: file_id={file_id}, file_name={file_name}")

        file_path = Path(storage_path)
        if not file_path.exists():
            return ContentIndexResult(
                success=False,
                file_id=file_id,
                failure_reason=FailureReason.CONTENT_EXTRACT_ERROR,
                error=f"文件不存在: {storage_path}",
            )

        # 匹配抽取器
        extractor = get_extractor_for_file(file_path, file_type)
        if extractor is None:
            logger.info(f"无匹配的抽取器，跳过内容索引: file_id={file_id}, type={file_type}")
            # 仍然写入基本信息索引
            await ContentIndexPipeline._index_basic_only(
                file_id, user_id, node_id, file_name, file_type, file_size, created_at
            )
            return ContentIndexResult(
                success=True,
                file_id=file_id,
                skipped=True,
                skipped_reason=f"无匹配的抽取器: {file_type}",
            )

        # 抽取内容
        try:
            extraction: ExtractionResult = await extractor.extract(file_id, file_path)
        except Exception as e:
            logger.error(f"内容抽取失败: file_id={file_id}, error={e}", exc_info=True)
            return ContentIndexResult(
                success=False,
                file_id=file_id,
                failure_reason=FailureReason.CONTENT_EXTRACT_ERROR,
                error=str(e),
            )

        # 写入 OpenSearch
        try:
            await ContentIndexPipeline._index_to_opensearch(
                file_id=file_id,
                user_id=user_id,
                node_id=node_id,
                file_name=file_name,
                file_type=file_type,
                file_size=file_size,
                created_at=created_at,
                extraction=extraction,
            )
        except Exception as e:
            logger.error(f"OpenSearch 索引写入失败: file_id={file_id}, error={e}", exc_info=True)
            return ContentIndexResult(
                success=False,
                file_id=file_id,
                failure_reason=FailureReason.CONTENT_INDEX_ERROR,
                error=str(e),
                data={
                    "extraction_warnings": extraction.warnings,
                    "char_count": len(extraction.content_text),
                },
            )

        logger.info(
            f"内容索引完成: file_id={file_id}, "
            f"extractor={extraction.extractor}, "
            f"char_count={len(extraction.content_text)}, "
            f"chunks={len(extraction.chunks)}, "
            f"ocr={extraction.has_ocr}"
        )

        return ContentIndexResult(
            success=True,
            file_id=file_id,
            data={
                "extractor": extraction.extractor,
                "char_count": len(extraction.content_text),
                "chunk_count": len(extraction.chunks),
                "has_ocr": extraction.has_ocr,
                "has_image_tags": extraction.has_image_tags,
                "warnings": extraction.warnings,
            },
        )

    @staticmethod
    async def _index_basic_only(
        file_id: str, user_id: str, node_id: str,
        file_name: str, file_type: str, file_size: int, created_at: str,
    ):
        """仅写入基本信息索引 (没有合适抽取器时)"""
        ext = Path(file_name).suffix.lower().lstrip(".")
        basic_doc = {
            "file_id": file_id,
            "user_id": user_id,
            "node_id": node_id,
            "filename": file_name,
            "file_ext": ext,
            "file_type": file_type,
            "file_category": ContentIndexPipeline._categorize(file_type),
            "size_bytes": file_size,
            "status": "active",
            "created_at": created_at,
            "updated_at": created_at,
            "tags": [],
            "extraction": {
                "extractor": "none",
                "char_count": 0,
                "chunk_count": 0,
                "has_ocr": False,
                "has_image_tags": False,
                "warnings": ["无匹配的抽取器"],
            },
        }
        await IndexService.index_file_basic(basic_doc)

    @staticmethod
    async def _index_to_opensearch(
        file_id: str, user_id: str, node_id: str,
        file_name: str, file_type: str, file_size: int,
        created_at: str, extraction: ExtractionResult,
    ):
        """写入 OpenSearch 基本信息索引 + 内容索引"""
        ext = Path(file_name).suffix.lower().lstrip(".")

        # 基本信息索引
        basic_doc = {
            "file_id": file_id,
            "user_id": user_id,
            "node_id": node_id,
            "filename": file_name,
            "file_ext": ext,
            "file_type": file_type,
            "file_category": ContentIndexPipeline._categorize(file_type),
            "size_bytes": file_size,
            "status": "active",
            "created_at": created_at,
            "updated_at": created_at,
            "tags": extraction.tags,
            "summary": extraction.content_text[:500] if extraction.content_text else None,
            "extraction": {
                "extractor": extraction.extractor,
                "char_count": len(extraction.content_text),
                "chunk_count": len(extraction.chunks),
                "has_ocr": extraction.has_ocr,
                "has_image_tags": extraction.has_image_tags,
                "warnings": extraction.warnings,
            },
        }
        await IndexService.index_file_basic(basic_doc)

        # 内容索引
        content_doc = {
            "file_id": file_id,
            "user_id": user_id,
            "node_id": node_id,
            "filename": file_name,
            "file_ext": ext,
            "file_type": file_type,
            "file_category": ContentIndexPipeline._categorize(file_type),
            "size_bytes": file_size,
            "status": "active",
            "created_at": created_at,
            "updated_at": created_at,
            "tags": extraction.tags,
            "summary": extraction.content_text[:500] if extraction.content_text else None,
            "content_text": extraction.content_text,
            "content_chunks": [
                {
                    "chunk_id": c.chunk_id,
                    "source_type": c.source_type,
                    "page": c.page,
                    "sheet": c.sheet,
                    "slide": c.slide,
                    "row": c.row,
                    "text": c.text,
                }
                for c in extraction.chunks
            ],
            "ocr_text": extraction.ocr_text,
            "image_labels": extraction.image_labels,
            "extraction": {
                "extractor": extraction.extractor,
                "char_count": len(extraction.content_text),
                "chunk_count": len(extraction.chunks),
                "has_ocr": extraction.has_ocr,
                "has_image_tags": extraction.has_image_tags,
                "language": extraction.language,
            },
        }
        await IndexService.index_file_content(content_doc)

    @staticmethod
    def _categorize(file_type: str) -> str:
        """根据 MIME 类型分类"""
        if file_type.startswith("image/"):
            return "image"
        if file_type.startswith("video/"):
            return "video"
        if file_type.startswith("audio/"):
            return "audio"
        if "pdf" in file_type:
            return "document"
        if "word" in file_type or "document" in file_type:
            return "document"
        if "spreadsheet" in file_type or "excel" in file_type:
            return "spreadsheet"
        if "presentation" in file_type or "powerpoint" in file_type:
            return "presentation"
        if file_type.startswith("text/"):
            return "text"
        return "other"
