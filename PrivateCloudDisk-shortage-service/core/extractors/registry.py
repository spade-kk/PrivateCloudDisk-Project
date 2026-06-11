"""
抽取器注册表

根据文件扩展名或 MIME 类型匹配对应的抽取器
"""
from __future__ import annotations
import logging
from pathlib import Path

from core.extractors.base import BaseExtractor, ExtractionResult

logger = logging.getLogger("extractor_registry")

# 延迟导入，避免循环依赖
_extractors: dict[str, type[BaseExtractor]] = {}
_initialized = False


def _init_registry():
    """初始化注册表 (延迟加载)"""
    global _initialized
    if _initialized:
        return

    from core.extractors.text_extractor import TextExtractor
    from core.extractors.csv_extractor import CsvExtractor
    from core.extractors.xlsx_extractor import XlsxExtractor
    from core.extractors.docx_extractor import DocxExtractor
    from core.extractors.pdf_extractor import PdfExtractor
    from core.extractors.pptx_extractor import PptxExtractor
    from core.extractors.image_extractor import ImageExtractor

    _extractors.update({
        "txt": TextExtractor,
        "text": TextExtractor,
        "md": TextExtractor,
        "json": TextExtractor,
        "xml": TextExtractor,
        "csv": CsvExtractor,
        "xlsx": XlsxExtractor,
        "xls": XlsxExtractor,
        "docx": DocxExtractor,
        "pdf": PdfExtractor,
        "pptx": PptxExtractor,
        "ppt": PptxExtractor,
        "jpg": ImageExtractor,
        "jpeg": ImageExtractor,
        "png": ImageExtractor,
        "gif": ImageExtractor,
        "webp": ImageExtractor,
        "bmp": ImageExtractor,
        "tiff": ImageExtractor,
    })
    _initialized = True


def get_extractor_for_file(file_path: Path, file_type: str = "") -> BaseExtractor | None:
    """
    根据文件扩展名或 MIME 类型获取抽取器

    Args:
        file_path: 文件路径
        file_type: MIME 类型 (备用)

    Returns:
        BaseExtractor 实例 或 None
    """
    _init_registry()

    # 先按扩展名匹配
    ext = file_path.suffix.lower().lstrip(".")
    extractor_cls = _extractors.get(ext)
    if extractor_cls:
        return extractor_cls()

    # 按 MIME 类型匹配
    if file_type:
        if file_type.startswith("text/"):
            from core.extractors.text_extractor import TextExtractor
            return TextExtractor()
        if file_type.startswith("image/"):
            from core.extractors.image_extractor import ImageExtractor
            return ImageExtractor()

    logger.debug(f"无匹配的抽取器: path={file_path}, ext={ext}")
    return None


def get_supported_extensions() -> list[str]:
    """获取支持的扩展名列表"""
    _init_registry()
    return list(_extractors.keys())