"""
纯文本文件抽取器

支持: .txt, .md, .json, .xml, .text 等
"""
from pathlib import Path
import aiofiles

from core.extractors.base import BaseExtractor, ExtractionResult, split_text
from core.config import settings


class TextExtractor(BaseExtractor):
    name = "text_extractor"

    async def extract(self, file_id: str, path: Path) -> ExtractionResult:
        try:
            async with aiofiles.open(path, "r", encoding="utf-8", errors="ignore") as f:
                text = await f.read()
        except Exception as e:
            return ExtractionResult(
                file_id=file_id,
                extractor=self.name,
                warnings=[f"读取失败: {e}"],
            )

        text = text[:settings.content_max_chars]

        chunks = split_text(
            text=text,
            source_type="text",
            chunk_size=settings.chunk_size_chars,
            overlap=settings.chunk_overlap_chars,
        )

        return ExtractionResult(
            file_id=file_id,
            extractor=self.name,
            extractor_version=self.version,
            content_text=text,
            chunks=chunks,
            tags=["text"],
        )