"""
图片文件抽取器

支持: .jpg, .jpeg, .png, .gif, .webp, .bmp, .tiff
功能: OCR 文字提取 + 图片语义标签
"""
import logging
from pathlib import Path

from core.extractors.base import BaseExtractor, ExtractionResult
from core.config import settings

logger = logging.getLogger("image_extractor")


class ImageExtractor(BaseExtractor):
    name = "image_extractor"

    async def extract(self, file_id: str, path: Path) -> ExtractionResult:
        result = ExtractionResult(
            file_id=file_id,
            extractor=self.name,
            extractor_version=self.version,
            tags=["image"],
        )

        # OCR 提取
        if settings.enable_ocr:
            await self._extract_ocr(result, path)

        # 图片标签
        if settings.enable_image_tags:
            await self._extract_tags(result, path)

        return result

    async def _extract_ocr(self, result: ExtractionResult, path: Path):
        """使用 pytesseract 提取文字"""
        try:
            from PIL import Image
            import pytesseract

            img = Image.open(path)
            # 限制图片尺寸避免 OOM
            if img.width > 4000 or img.height > 4000:
                img.thumbnail((4000, 4000), Image.LANCZOS)

            text = pytesseract.image_to_string(img, lang="eng+chi_sim")
            text = text.strip()

            if text:
                result.ocr_text = text[:settings.content_max_chars]
                result.has_ocr = True
                result.content_text = text[:settings.content_max_chars]
                logger.debug(f"OCR 提取成功: {len(text)} 字符")
        except ImportError:
            logger.warning("pytesseract 未安装，跳过 OCR")
            result.warnings.append("pytesseract 未安装，OCR 不可用")
        except Exception as e:
            logger.warning(f"OCR 提取失败: {e}")
            result.warnings.append(f"OCR 失败: {e}")

    async def _extract_tags(self, result: ExtractionResult, path: Path):
        """使用 PIL 提取图片元数据标签"""
        try:
            from PIL import Image

            img = Image.open(path)
            tags = []

            # 基础属性标签
            mode = img.mode.lower()
            if "c" in mode and "m" in mode and "y" in mode and "k" in mode:
                tags.append("cmyk")
            elif mode == "rgba":
                tags.append("rgba")
            elif mode == "rgb":
                tags.append("rgb")
            elif mode == "l" or mode == "la":
                tags.append("grayscale")
            elif mode == "1":
                tags.append("binary")

            if img.width > 2000 or img.height > 2000:
                tags.append("high_resolution")

            # EXIF 信息
            exif = img.getexif() if hasattr(img, "getexif") else None
            if exif:
                if exif.get(271):  # Make
                    tags.append("camera_photo")
                if exif.get(40962):  # PixelXDimension
                    tags.append("high_resolution")

            result.image_labels = tags
            result.tags.extend(tags)
            result.has_image_tags = True
            img.close()
        except Exception as e:
            logger.warning(f"图片标签提取失败: {e}")
            result.warnings.append(f"标签提取失败: {e}")