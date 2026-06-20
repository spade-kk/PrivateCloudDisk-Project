"""
CSV 文件抽取器
"""
import csv
from pathlib import Path

from core.extractors.base import BaseExtractor, ExtractionResult, split_text
from core.config import settings


class CsvExtractor(BaseExtractor):
    name = "csv_extractor"

    async def extract(self, file_id: str, path: Path) -> ExtractionResult:
        lines: list[str] = []
        warnings: list[str] = []

        try:
            with open(path, "r", encoding="utf-8", errors="ignore", newline="") as f:
                reader = csv.reader(f)
                for row_index, row in enumerate(reader):
                    if row_index > 10000:
                        warnings.append("超过 10000 行，已截断")
                        break
                    line = " | ".join(str(cell) for cell in row if cell is not None)
                    if line.strip():
                        lines.append(f"row {row_index}: {line}")
        except Exception as e:
            return ExtractionResult(
                file_id=file_id,
                extractor=self.name,
                warnings=[f"CSV 解析失败: {e}"],
            )

        text = "\n".join(lines)[:settings.content_max_chars]

        chunks = split_text(
            text=text,
            source_type="csv",
            chunk_size=settings.chunk_size_chars,
            overlap=settings.chunk_overlap_chars,
        )

        return ExtractionResult(
            file_id=file_id,
            extractor=self.name,
            extractor_version=self.version,
            content_text=text,
            chunks=chunks,
            tags=["table", "csv"],
            warnings=warnings,
        )