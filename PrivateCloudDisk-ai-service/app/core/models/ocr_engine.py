"""
AI Processing Service - 增强 OCR 文字识别引擎

基于 PaddleOCR 实现高精度 OCR 文字识别。
支持:
- 中英文混合识别
- 多语言识别 (中/英/日/韩/法等)
- 多页 PDF 逐页 OCR
- 表格结构识别
- 文字方向检测与矫正
- 批量图片处理

模型: PaddleOCR (PP-OCRv4, PP-OCRv5)
备选: Tesseract 5.x (轻量级)
"""
from __future__ import annotations
import logging
import time
import io
from pathlib import Path
from typing import Optional

import numpy as np
from PIL import Image

from app.core.config import settings, AITaskType, FailureReason
from app.core.services.model_manager import model_manager, ModelInfo
from app.core.services.file_reader import file_reader
from app.core.events.ai_process_event import AIProcessResult

logger = logging.getLogger("ai_service.ocr_engine")

# 语言代码映射
LANG_CODE_MAP = {
    "ch": "中文",
    "en": "英文",
    "chinese_cht": "繁体中文",
    "japan": "日文",
    "korean": "韩文",
    "french": "法文",
    "german": "德文",
    "spanish": "西班牙文",
    "portuguese": "葡萄牙文",
    "italian": "意大利文",
    "russian": "俄文",
    "arabic": "阿拉伯文",
}


class OCREngine:
    """
    增强 OCR 文字识别引擎

    流程:
    1. 加载图片/PDF 页面
    2. 文字方向检测与矫正
    3. 文字区域检测
    4. 文字识别 (逐行)
    5. 结果后处理与结构化

    特性:
    - 自动检测文字方向并矫正
    - 支持多种图片格式 (JPEG, PNG, BMP, TIFF, WEBP)
    - 支持 PDF 逐页处理
    - 返回分页、分行的结构化结果
    - 置信度评估
    """

    MODEL_NAME = "paddleocr"
    MODEL_VERSION = "2.9.1"
    SUPPORTED_LANGS = {"ch", "en", "chinese_cht", "japan", "korean",
                        "french", "german", "spanish", "portuguese",
                        "italian", "russian"}

    # 文本块合并阈值
    MERGE_THRESHOLD_Y = 10  # 像素，Y 轴方向合并阈值
    MIN_TEXT_LENGTH = 3     # 最小文本长度

    def __init__(self):
        self._initialized = False
        self._ocr = None
        self._tesseract_available = False

    def _ensure_initialized(self):
        """初始化 PaddleOCR 引擎"""
        if self._initialized:
            return

        try:
            from paddleocr import PaddleOCR

            self._ocr = PaddleOCR(
                use_angle_cls=True,          # 启用文字方向分类
                lang="ch",                    # 默认中英文混合
                use_gpu=(model_manager.device == "cuda"),
                show_log=False,
                det_db_thresh=0.3,           # 检测阈值
                det_db_box_thresh=0.5,       # 检测框阈值
                rec_batch_num=6,             # 识别批处理大小
                max_text_length=25,          # 最大文本长度
                use_space_char=True,         # 识别空格
                cls_model_dir=None,          # 使用默认方向分类模型
                det_model_dir=None,           # 使用默认检测模型
                rec_model_dir=None,           # 使用默认识别模型
            )

            self._initialized = True
            logger.info(
                f"PaddleOCR 初始化完成: version={self.MODEL_VERSION}, "
                f"device={'GPU' if model_manager.device == 'cuda' else 'CPU'}"
            )

        except ImportError:
            logger.warning("PaddleOCR 未安装，尝试使用 Tesseract 备选")
            self._init_tesseract_fallback()
        except Exception as e:
            logger.error(f"PaddleOCR 初始化失败: {e}")
            self._init_tesseract_fallback()

    def _init_tesseract_fallback(self):
        """初始化 Tesseract 备选引擎"""
        try:
            import pytesseract
            # 测试 Tesseract 是否可用
            pytesseract.get_tesseract_version()
            self._tesseract_available = True
            self._initialized = True
            logger.info("Tesseract 备选引擎就绪")
        except ImportError:
            logger.error("Tesseract 未安装，OCR 功能不可用")
            raise RuntimeError("OCR 引擎不可用: PaddleOCR 和 Tesseract 均未安装")
        except Exception as e:
            logger.error(f"Tesseract 不可用: {e}")
            raise RuntimeError(f"OCR 引擎不可用: {e}")

    async def recognize(
        self,
        file_id: str,
        user_id: str,
        storage_path: str,
        file_name: str,
        lang: str = "ch",
        page_range: Optional[tuple[int, int]] = None,
    ) -> AIProcessResult:
        """
        执行 OCR 文字识别

        Args:
            file_id: 文件 ID
            user_id: 用户 ID
            storage_path: 文件存储路径
            file_name: 文件名
            lang: 识别语言 (ch/en/japan/korean 等)
            page_range: 页面范围 (start, end)，1-based，None 表示全部页面

        Returns:
            AIProcessResult:
                data.pages: [{
                    "page_num": 1,
                    "text": "完整文本",
                    "lines": [{"text": "...", "confidence": 0.95, "bbox": {...}}],
                    "confidence": 0.92,
                }]
                data.full_text: "全部页面文本拼接"
                data.language: "中文"
                data.total_pages: 10
        """
        t_start = time.monotonic()
        logger.info(f"OCR 识别开始: file_id={file_id}, lang={lang}")

        try:
            self._ensure_initialized()

            # 1. 读取文件
            data = await file_reader.read_file_bytes(storage_path, file_name)

            # 判断文件类型
            is_pdf = file_name.lower().endswith(".pdf") or (
                data[:4] == b"%PDF" if len(data) >= 4 else False
            )

            if is_pdf:
                return await self._process_pdf(
                    file_id, user_id, data, lang, page_range, t_start
                )
            else:
                return await self._process_image(
                    file_id, user_id, data, file_name, lang, t_start
                )

        except ValueError as e:
            return self._error_result(file_id, FailureReason.FILE_TOO_LARGE, str(e), t_start)
        except RuntimeError as e:
            return self._error_result(file_id, FailureReason.MODEL_NOT_LOADED, str(e), t_start, skipped=True)
        except Exception as e:
            logger.error(f"OCR 识别失败: file_id={file_id}, error={e}", exc_info=True)
            return self._error_result(file_id, FailureReason.INFERENCE_ERROR, str(e), t_start)

    async def _process_image(
        self,
        file_id: str,
        user_id: str,
        data: bytes,
        file_name: str,
        lang: str,
        t_start: float,
    ) -> AIProcessResult:
        """处理单张图片"""
        image = Image.open(io.BytesIO(data))

        # 转换为 numpy 数组
        if image.mode != "RGB":
            image = image.convert("RGB")
        img_array = np.array(image)

        if self._ocr is not None:
            # PaddleOCR 识别
            raw_result = self._ocr.ocr(img_array, cls=True)

            if raw_result is None or (isinstance(raw_result, list) and len(raw_result) == 0):
                return AIProcessResult(
                    file_id=file_id,
                    task_type=AITaskType.OCR,
                    success=True,
                    skipped=True,
                    skipped_reason="未检测到文字区域",
                    data={"pages": [], "full_text": "", "total_pages": 1},
                )

            # 解析结果
            page = self._parse_ocr_result(raw_result, page_num=1)
            pages = [page] if page["lines"] else []

        elif self._tesseract_available:
            # Tesseract 备选
            import pytesseract
            lang_map = {"ch": "chi_sim+eng", "en": "eng", "japan": "jpn",
                         "korean": "kor", "french": "fra", "german": "deu"}
            tess_lang = lang_map.get(lang, "chi_sim+eng")

            text = pytesseract.image_to_string(image, lang=tess_lang)
            data_dict = pytesseract.image_to_data(image, lang=tess_lang, output_type=pytesseract.Output.DICT)

            lines = []
            for i in range(len(data_dict["text"])):
                if data_dict["text"][i].strip():
                    lines.append({
                        "text": data_dict["text"][i].strip(),
                        "confidence": float(data_dict["conf"][i]) / 100.0,
                        "bbox": {
                            "x": data_dict["left"][i],
                            "y": data_dict["top"][i],
                            "w": data_dict["width"][i],
                            "h": data_dict["height"][i],
                        },
                    })

            pages = [{
                "page_num": 1,
                "text": text.strip(),
                "lines": lines,
                "confidence": sum(l["confidence"] for l in lines) / max(len(lines), 1),
            }]
        else:
            raise RuntimeError("OCR 引擎未初始化")

        elapsed_ms = (time.monotonic() - t_start) * 1000
        full_text = "\n".join(p["text"] for p in pages)

        logger.info(
            f"OCR 识别完成: file_id={file_id}, "
            f"pages=1, chars={len(full_text)}, "
            f"elapsed={elapsed_ms:.0f}ms"
        )

        return AIProcessResult(
            file_id=file_id,
            task_type=AITaskType.OCR,
            success=True,
            data={
                "pages": pages,
                "full_text": full_text,
                "total_pages": 1,
                "language": LANG_CODE_MAP.get(lang, lang),
                "engine": "paddleocr" if self._ocr else "tesseract",
                "model_version": self.MODEL_VERSION,
                "processing_time_ms": elapsed_ms,
            },
        )

    async def _process_pdf(
        self,
        file_id: str,
        user_id: str,
        data: bytes,
        lang: str,
        page_range: Optional[tuple[int, int]],
        t_start: float,
    ) -> AIProcessResult:
        """处理 PDF 文件 (逐页 OCR)"""
        try:
            import fitz  # PyMuPDF
        except ImportError:
            logger.warning("PyMuPDF 未安装，尝试将 PDF 作为图片处理")
            return await self._process_image(file_id, user_id, data, "document.pdf", lang, t_start)

        pdf_doc = fitz.open(stream=data, filetype="pdf")
        total_pages = pdf_doc.page_count

        start_page = (page_range[0] - 1) if page_range else 0
        end_page = min(page_range[1] if page_range else total_pages, total_pages)

        if start_page < 0 or start_page >= total_pages:
            return self._error_result(
                file_id, FailureReason.UNKNOWN,
                f"页面范围无效: {page_range}", t_start
            )

        pages = []
        for page_num in range(start_page, end_page):
            logger.debug(f"OCR 处理 PDF 第 {page_num + 1}/{total_pages} 页")

            page = pdf_doc[page_num]
            # 渲染为图片 (300 DPI 高质量)
            pix = page.get_pixmap(dpi=300)
            img_array = np.frombuffer(pix.samples, dtype=np.uint8).reshape(
                pix.height, pix.width, pix.n
            )

            if self._ocr is not None:
                raw_result = self._ocr.ocr(img_array, cls=True)
                page_result = self._parse_ocr_result(raw_result, page_num=page_num + 1)
            elif self._tesseract_available:
                import pytesseract
                image = Image.fromarray(img_array)
                text = pytesseract.image_to_string(image, lang="chi_sim+eng")
                page_result = {
                    "page_num": page_num + 1,
                    "text": text.strip(),
                    "lines": [],
                    "confidence": 0.0,
                }
            else:
                continue

            if page_result:
                pages.append(page_result)

        pdf_doc.close()

        elapsed_ms = (time.monotonic() - t_start) * 1000
        full_text = "\n\n".join(p["text"] for p in pages)

        logger.info(
            f"OCR 识别完成 (PDF): file_id={file_id}, "
            f"pages={len(pages)}/{total_pages}, "
            f"chars={len(full_text)}, "
            f"elapsed={elapsed_ms:.0f}ms"
        )

        return AIProcessResult(
            file_id=file_id,
            task_type=AITaskType.OCR,
            success=True,
            data={
                "pages": pages,
                "full_text": full_text,
                "total_pages": total_pages,
                "processed_pages": len(pages),
                "language": LANG_CODE_MAP.get(lang, lang),
                "engine": "paddleocr" if self._ocr else "tesseract",
                "model_version": self.MODEL_VERSION,
                "processing_time_ms": elapsed_ms,
            },
        )

    def _parse_ocr_result(
        self,
        raw_result: list,
        page_num: int = 1,
    ) -> Optional[dict]:
        """
        解析 PaddleOCR 原始结果

        PaddleOCR 返回格式:
        [
            [
                [[x1,y1], [x2,y2], [x3,y3], [x4,y4]],  # 四点坐标
                ("识别的文字", 置信度)
            ],
            ...
        ]
        """
        if raw_result is None:
            return None

        # 处理嵌套列表 (PaddleOCR 2.x 格式)
        if isinstance(raw_result, list) and len(raw_result) > 0:
            if isinstance(raw_result[0], list) and len(raw_result[0]) > 0:
                raw_result = raw_result[0]

        lines = []
        for item in raw_result:
            if item is None:
                continue

            bbox_points = item[0]
            text_info = item[1]

            if isinstance(text_info, (list, tuple)):
                text = str(text_info[0])
                confidence = float(text_info[1])
            else:
                text = str(text_info)
                confidence = 1.0

            if not text.strip():
                continue

            # 计算矩形边界框
            xs = [p[0] for p in bbox_points]
            ys = [p[1] for p in bbox_points]

            lines.append({
                "text": text.strip(),
                "confidence": round(confidence, 4),
                "bbox": {
                    "x": int(min(xs)),
                    "y": int(min(ys)),
                    "w": int(max(xs) - min(xs)),
                    "h": int(max(ys) - min(ys)),
                },
            })

        if not lines:
            return None

        # 按 Y 坐标排序 (从上到下)
        lines.sort(key=lambda l: l["bbox"]["y"])

        # 合并同一行的文本块
        merged_lines = self._merge_text_lines(lines)

        full_text = "\n".join(l["text"] for l in merged_lines)
        avg_confidence = sum(l["confidence"] for l in merged_lines) / len(merged_lines)

        return {
            "page_num": page_num,
            "text": full_text,
            "lines": merged_lines,
            "confidence": round(avg_confidence, 4),
        }

    def _merge_text_lines(self, lines: list[dict]) -> list[dict]:
        """合并同一行的文本块"""
        if not lines:
            return lines

        merged = []
        current_line = lines[0]

        for line in lines[1:]:
            # 判断是否同一行 (Y 坐标差值 < 阈值)
            if abs(line["bbox"]["y"] - current_line["bbox"]["y"]) < self.MERGE_THRESHOLD_Y:
                # 合并: 文本拼接，置信度取平均
                current_line["text"] += " " + line["text"]
                current_line["confidence"] = (
                    current_line["confidence"] + line["confidence"]
                ) / 2
                # 扩展边界框
                current_line["bbox"]["x"] = min(
                    current_line["bbox"]["x"], line["bbox"]["x"]
                )
                current_line["bbox"]["w"] = max(
                    current_line["bbox"]["x"] + current_line["bbox"]["w"],
                    line["bbox"]["x"] + line["bbox"]["w"],
                ) - current_line["bbox"]["x"]
            else:
                merged.append(current_line)
                current_line = line

        merged.append(current_line)
        return merged

    def _error_result(
        self,
        file_id: str,
        failure_reason: str,
        error: str,
        t_start: float,
        skipped: bool = False,
    ) -> AIProcessResult:
        elapsed_ms = (time.monotonic() - t_start) * 1000
        return AIProcessResult(
            file_id=file_id,
            task_type=AITaskType.OCR,
            success=False,
            skipped=skipped,
            skipped_reason=error if skipped else "",
            failure_reason=failure_reason,
            error=error,
            processing_time_ms=elapsed_ms,
        )


# =============================================================================
# 全局单例
# =============================================================================
ocr_engine = OCREngine()