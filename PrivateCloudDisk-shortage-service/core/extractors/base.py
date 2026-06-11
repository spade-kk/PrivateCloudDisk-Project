"""
内容抽取器基类与结果模型
"""
from __future__ import annotations
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class TextChunk:
    """文本分块"""
    chunk_id: str
    source_type: str
    text: str
    page: int | None = None
    sheet: str | None = None
    slide: int | None = None
    row: int | None = None


@dataclass
class ExtractionResult:
    """抽取结果"""
    file_id: str
    extractor: str
    extractor_version: str = "1.0"

    content_text: str = ""
    ocr_text: str = ""

    chunks: list[TextChunk] = field(default_factory=list)

    tags: list[str] = field(default_factory=list)
    image_labels: list[str] = field(default_factory=list)

    summary: str | None = None
    language: str | None = None

    has_ocr: bool = False
    has_image_tags: bool = False

    warnings: list[str] = field(default_factory=list)


class BaseExtractor:
    """抽取器基类"""
    name = "base"
    version = "1.0"

    async def extract(self, file_id: str, path: Path) -> ExtractionResult:
        raise NotImplementedError


def normalize_text(text: str) -> str:
    """规范化文本: 去除空字符、合并空白"""
    return " ".join((text or "").replace("\x00", " ").split())


def split_text(
    text: str,
    source_type: str,
    chunk_size: int,
    overlap: int,
    page: int | None = None,
    sheet: str | None = None,
    slide: int | None = None,
    row: int | None = None,
) -> list[TextChunk]:
    """将文本按块大小分割，支持重叠"""
    text = normalize_text(text)
    if not text:
        return []

    chunks: list[TextChunk] = []
    start = 0
    index = 0

    while start < len(text):
        end = min(start + chunk_size, len(text))
        part = text[start:end]

        chunks.append(
            TextChunk(
                chunk_id=f"{source_type}-{page or sheet or slide or row or 'main'}-{index}",
                source_type=source_type,
                text=part,
                page=page,
                sheet=sheet,
                slide=slide,
                row=row,
            )
        )

        if end == len(text):
            break

        start = max(0, end - overlap)
        index += 1

    return chunks