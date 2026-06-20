"""
PDF 文件抽取器

支持: .pdf
"""
from pathlib import Path

from pypdf import PdfReader

from core.extractors.base import BaseExtractor, ExtractionResult, split_text, TextChunk
from core.config import settings


class PdfExtractor(BaseExtractor):
    name = "pdf_extractor"

    async def extract(self, file_id: str, path: Path) -> ExtractionResult:
        warnings: list[str] = []
        chunks: list[TextChunk] = []
        all_parts: list[str] = []

        try:
            reader = PdfReader(str(path))
            pages = min(len(reader.pages), 500)  # 最多 500 页

            for page_num in range(pages):
                page = reader.pages[page_num]
                page_text = page.extract_text() or ""

                if page_text.strip():
                    all_parts.append(page_text)
                    chunks.extend(
                        split_text(
                            text=page_text,
                            source_type="pdf",
                            chunk_size=settings.chunk_size_chars,
                            overlap=settings.chunk_overlap_chars,
                            page=page_num + 1,
                        )
                    )

            if len(reader.pages) > 500:
                warnings.append(f"PDF 共 {len(reader.pages)} 页，仅处理前 500 页")
        except Exception as e:
            return ExtractionResult(
                file_id=file_id,
                extractor=self.name,
                warnings=[f"PDF 解析失败: {e}"],
            )

        text = "\n".join(all_parts)[:settings.content_max_chars]

        return ExtractionResult(
            file_id=file_id,
            extractor=self.name,
            extractor_version=self.version,
            content_text=text,
            chunks=chunks,
            tags=["document", "pdf"],
            warnings=warnings,
        )