"""
文件内容索引流水线

负责:
1. 根据文件类型匹配抽取器
2. 抽取文件内容 (文本/表格/OCR/图片标签)
3. 生成文本分块
4. 构建丰富的关键词提示 (keyword_hints)
5. 写入 OpenSearch 基本信息索引 + 内容索引

优化:
- 新增 keyword_hints: 从文件名、标签、内容中自动提取关键词，大幅提升搜索召回率
- 新增 content_snippet: 前2000字符摘要，写入基本信息索引便于快速搜索
- 新增 image_labels: 写入基本信息索引，同一个关键词在两个索引都能命中
- 新增 tenant_id: 多租户权限隔离
"""
from __future__ import annotations
import logging
import re
from pathlib import Path

from core.config import settings, FailureReason
from core.extractors.registry import get_extractor_for_file
from core.extractors.base import ExtractionResult
from core.search.index_service import IndexService
from dataclasses import dataclass, field

logger = logging.getLogger("content_index_pipeline")

# ========== 常见文件扩展名对应的中文描述 ==========
FILE_EXT_HINTS = {
    "pdf": ["PDF文档", "PDF"],
    "doc": ["Word文档", "Word"],
    "docx": ["Word文档", "Word"],
    "xls": ["Excel表格", "Excel", "电子表格"],
    "xlsx": ["Excel表格", "Excel", "电子表格"],
    "ppt": ["PPT演示文稿", "PPT", "幻灯片"],
    "pptx": ["PPT演示文稿", "PPT", "幻灯片"],
    "txt": ["文本文件", "纯文本"],
    "md": ["Markdown文档", "Markdown", "文档"],
    "csv": ["CSV表格", "CSV", "电子表格"],
    "jpg": ["JPEG图片", "图片", "照片"],
    "jpeg": ["JPEG图片", "图片", "照片"],
    "png": ["PNG图片", "图片", "截屏"],
    "gif": ["GIF动图", "图片", "动图"],
    "webp": ["WebP图片", "图片"],
    "bmp": ["BMP图片", "图片"],
    "svg": ["SVG矢量图", "图片", "矢量图"],
    "mp4": ["MP4视频", "视频"],
    "mov": ["MOV视频", "视频"],
    "mkv": ["MKV视频", "视频"],
    "avi": ["AVI视频", "视频"],
    "mp3": ["MP3音频", "音频", "音乐"],
    "wav": ["WAV音频", "音频"],
    "zip": ["ZIP压缩包", "压缩文件"],
    "rar": ["RAR压缩包", "压缩文件"],
    "7z": ["7z压缩包", "压缩文件"],
    "tar": ["TAR压缩包", "压缩文件"],
    "gz": ["GZ压缩包", "压缩文件"],
    "py": ["Python代码", "代码"],
    "java": ["Java代码", "代码"],
    "js": ["JavaScript代码", "代码"],
    "ts": ["TypeScript代码", "代码"],
    "html": ["HTML文件", "网页"],
    "css": ["CSS样式", "样式表"],
    "json": ["JSON数据", "配置文件"],
    "xml": ["XML数据", "配置文件"],
    "yaml": ["YAML配置", "配置文件"],
    "yml": ["YAML配置", "配置文件"],
    "sql": ["SQL脚本", "数据库"],
}

# ========== 文件类别对应的中文描述 ==========
FILE_CATEGORY_HINTS = {
    "document": ["文档"],
    "image": ["图片", "照片", "图像"],
    "video": ["视频", "影片"],
    "audio": ["音频", "音乐", "声音"],
    "spreadsheet": ["表格", "电子表格"],
    "presentation": ["演示文稿", "幻灯片", "PPT"],
    "text": ["文本文件"],
    "archive": ["压缩文件", "压缩包"],
    "code": ["代码", "源代码"],
    "other": ["其他文件"],
}


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
        tenant_id: str = "",
    ) -> ContentIndexResult:
        """
        执行内容索引

        处理流程:
        1. 检查文件是否存在
        2. 匹配抽取器
        3. 抽取文件内容
        4. 构建丰富关键词
        5. 写入 OpenSearch 基本信息索引
        6. 写入 OpenSearch 内容索引

        Args:
            file_id: 文件 ID
            user_id: 用户 ID
            storage_path: 文件存储路径
            file_name: 文件名
            file_type: MIME 类型
            file_size: 文件大小
            node_id: 目录节点 ID
            created_at: 创建时间 (ISO 格式)
            tenant_id: 租户 ID

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

        ext = Path(file_name).suffix.lower().lstrip(".")

        # 匹配抽取器
        extractor = get_extractor_for_file(file_path, file_type)
        if extractor is None:
            logger.info(f"无匹配的抽取器，跳过内容索引: file_id={file_id}, type={file_type}")
            # 仍然写入基本信息索引（含关键词提示）
            await ContentIndexPipeline._index_basic_only(
                file_id, user_id, node_id, file_name, ext, file_type, file_size, created_at, tenant_id
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
                ext=ext,
                file_type=file_type,
                file_size=file_size,
                created_at=created_at,
                tenant_id=tenant_id,
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
        file_name: str, ext: str, file_type: str, file_size: int,
        created_at: str, tenant_id: str = "",
    ):
        """仅写入基本信息索引 (没有合适抽取器时)"""
        file_category = ContentIndexPipeline._categorize(file_type)
        keyword_hints = ContentIndexPipeline._build_keyword_hints(
            file_name=file_name,
            ext=ext,
            tags=[],
            image_labels=[],
            file_category=file_category,
            content_text="",
        )

        basic_doc = {
            "file_id": file_id,
            "user_id": user_id,
            "node_id": node_id,
            "tenant_id": tenant_id,
            "filename": file_name,
            "file_ext": ext,
            "file_type": file_type,
            "file_category": file_category,
            "size_bytes": file_size,
            "status": "active",
            "created_at": created_at,
            "updated_at": created_at,
            "tags": [],
            "keyword_hints": keyword_hints,
            "summary": "",
            "image_labels": [],
            "content_snippet": "",
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
        file_name: str, ext: str, file_type: str, file_size: int,
        created_at: str, tenant_id: str,
        extraction: ExtractionResult,
    ):
        """写入 OpenSearch 基本信息索引 + 内容索引"""
        file_category = ContentIndexPipeline._categorize(file_type)

        # 构建关键词提示
        keyword_hints = ContentIndexPipeline._build_keyword_hints(
            file_name=file_name,
            ext=ext,
            tags=extraction.tags,
            image_labels=extraction.image_labels,
            file_category=file_category,
            content_text=extraction.content_text or "",
        )

        # 内容摘要（前2000字符，用于基本信息索引快速搜索）
        content_snippet = ""
        if extraction.content_text:
            content_snippet = extraction.content_text[:2000]

        # 基本信息索引
        basic_doc = {
            "file_id": file_id,
            "user_id": user_id,
            "node_id": node_id,
            "tenant_id": tenant_id,
            "filename": file_name,
            "file_ext": ext,
            "file_type": file_type,
            "file_category": file_category,
            "size_bytes": file_size,
            "status": "active",
            "created_at": created_at,
            "updated_at": created_at,
            "tags": extraction.tags,
            "keyword_hints": keyword_hints,
            "summary": extraction.content_text[:500] if extraction.content_text else "",
            "image_labels": extraction.image_labels,
            "content_snippet": content_snippet,
            "extraction": {
                "extractor": extraction.extractor,
                "char_count": len(extraction.content_text),
                "chunk_count": len(extraction.chunks),
                "has_ocr": extraction.has_ocr,
                "has_image_tags": extraction.has_image_tags,
                "warnings": extraction.warnings,
                "language": extraction.language,
            },
        }
        await IndexService.index_file_basic(basic_doc)

        # 内容索引
        content_doc = {
            "file_id": file_id,
            "user_id": user_id,
            "node_id": node_id,
            "tenant_id": tenant_id,
            "filename": file_name,
            "file_ext": ext,
            "file_type": file_type,
            "file_category": file_category,
            "size_bytes": file_size,
            "status": "active",
            "created_at": created_at,
            "updated_at": created_at,
            "tags": extraction.tags,
            "keyword_hints": keyword_hints,
            "summary": extraction.content_text[:500] if extraction.content_text else "",
            "image_labels": extraction.image_labels,
            "content_snippet": content_snippet,
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
        if "zip" in file_type or "rar" in file_type or "7z" in file_type or "tar" in file_type or "gzip" in file_type:
            return "archive"
        if "json" in file_type or "xml" in file_type or "javascript" in file_type or "python" in file_type:
            return "code"
        return "other"

    @staticmethod
    def _build_keyword_hints(
        file_name: str,
        ext: str,
        tags: list[str],
        image_labels: list[str],
        file_category: str,
        content_text: str,
    ) -> list[str]:
        """
        从多维度构建关键词提示列表

        来源:
        1. 文件扩展名中文描述
        2. 文件类别中文描述
        3. 文件名中的词（拆分后）
        4. 标签
        5. 图片标签
        6. 内容中的高频词 (前 50 个字符中的关键词)

        这些关键词写入 keyword_hints 字段，在搜索时给予高权重，
        确保用户搜索 "图片"、"PDF"、"照片" 等通用描述时也能命中。
        """
        hints: list[str] = []

        # 1. 扩展名描述
        ext_lower = ext.lower()
        if ext_lower in FILE_EXT_HINTS:
            hints.extend(FILE_EXT_HINTS[ext_lower])

        # 2. 文件类别描述
        if file_category in FILE_CATEGORY_HINTS:
            hints.extend(FILE_CATEGORY_HINTS[file_category])

        # 3. 文件名拆分关键词
        name_without_ext = file_name.rsplit(".", 1)[0] if "." in file_name else file_name
        # 按常见分隔符拆分文件名
        name_parts = re.split(r"[-_\s]+", name_without_ext)
        for part in name_parts:
            part = part.strip()
            if len(part) >= 2 and len(part) <= 30:
                hints.append(part)

        # 4. 标签
        hints.extend(tags)

        # 5. 图片标签
        for label in image_labels:
            hints.append(label)
            # 为图片标签添加中文同义词
            label_lower = label.lower()
            if label_lower == "person":
                hints.append("人物")
                hints.append("人")
            elif label_lower == "animal":
                hints.append("动物")
                hints.append("宠物")
            elif label_lower == "plant":
                hints.append("植物")
                hints.append("花草")
            elif label_lower == "document":
                hints.append("文档")
                hints.append("文件")
            elif label_lower == "food":
                hints.append("食物")
                hints.append("美食")
            elif label_lower == "vehicle":
                hints.append("车辆")
                hints.append("汽车")
            elif label_lower == "building":
                hints.append("建筑")
                hints.append("楼房")
            elif label_lower == "landscape":
                hints.append("风景")
                hints.append("景观")
            elif label_lower == "screenshot":
                hints.append("截图")
                hints.append("截屏")
            elif label_lower == "chart":
                hints.append("图表")
                hints.append("数据图")

        # 6. 内容前 200 字符中的高频词（简单提取）
        if content_text and len(content_text) > 0:
            # 取前200字符，按常见分隔符提取候选词
            snippet = content_text[:200]
            words = re.split(r"[，。！？；：、\s\n\r\t,.!?;:]+", snippet)
            for word in words:
                word = word.strip()
                if 2 <= len(word) <= 20 and not word.isdigit():
                    hints.append(word)

        # 去重并限制数量
        seen = set()
        unique_hints = []
        for h in hints:
            h_lower = h.strip().lower()
            if h_lower and h_lower not in seen:
                seen.add(h_lower)
                unique_hints.append(h.strip())
                if len(unique_hints) >= 100:
                    break

        return unique_hints