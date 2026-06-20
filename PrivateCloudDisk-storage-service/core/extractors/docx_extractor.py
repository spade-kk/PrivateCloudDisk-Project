"""
Word 文档抽取器

支持: .docx
"""
from pathlib import Path

from docx import Document

from core.extractors.base import BaseExtractor, ExtractionResult, split_text
from core.config import settings


class DocxExtractor(BaseExtractor):
    name = "docx_extractor"

    async def extract(self, file_id: str, path: Path) -> ExtractionResult:
        try:
            doc = Document(str(path))
            paragraphs = [p.text for p in doc.paragraphs if p.text.strip()]
            text = "\n".join(paragraphs)
        except Exception as e:
            return ExtractionResult(
                file_id=file_id,
                extractor=self.name,
                warnings=[f"Word 文档解析失败: {e}"],
            )

        text = text[:settings.content_max_chars]

        chunks = split_text(
            text=text,
            source_type="docx",
            chunk_size=settings.chunk_size_chars,
            overlap=settings.chunk_overlap_chars,
        )

        return ExtractionResult(
            file_id=file_id,
            extractor=self.name,
            extractor_version=self.version,
            content_text=text,
            chunks=chunks,
            tags=["document", "word"],
        )