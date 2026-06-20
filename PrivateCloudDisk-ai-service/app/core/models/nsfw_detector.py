"""
AI Processing Service - NSFW 内容检测器

基于 ONNX Runtime 的 NSFW 内容检测。
检测图片中的敏感内容: 普通/性感/色情/暴力/血腥等。

模型: nsfw_detector (ONNX), 5分类:
- drawing: 绘画/二次元
- hentai: 成人动漫
- neutral: 普通/中性
- porn: 色情内容
- sexy: 性感内容
"""
from __future__ import annotations
import logging
import time
import io
from typing import Optional

import numpy as np
from PIL import Image

from app.core.config import settings, AITaskType, FailureReason
from app.core.services.model_manager import model_manager, ModelInfo
from app.core.services.file_reader import file_reader
from app.core.events.ai_process_event import AIProcessResult

logger = logging.getLogger("ai_service.nsfw_detector")

# NSFW 类别
NSFW_CATEGORIES = ["drawing", "hentai", "neutral", "porn", "sexy"]

NSFW_LABELS_ZH = {
    "drawing": "绘画图片",
    "hentai": "成人动漫",
    "neutral": "普通图片",
    "porn": "色情内容",
    "sexy": "性感内容",
}

# 需要标记为敏感的分类
SENSITIVE_CATEGORIES = {"hentai", "porn", "sexy"}


class NSFWBypassDetector:
    """
    NSFW 内容检测器

    流程:
    1. 加载图片并缩放至 224x224
    2. 通过 ONNX 模型推理
    3. 输出 5 分类概率
    4. 标记敏感内容
    """

    MODEL_NAME = "nsfw_detector"
    MODEL_VERSION = "1.0.0"
    INPUT_SIZE = (224, 224)
    SENSITIVE_THRESHOLD = 0.6  # 敏感内容阈值

    def __init__(self):
        self._registered = False

    def _ensure_model_registered(self):
        if self._registered:
            return
        model_manager.register_model(ModelInfo(
            name=self.MODEL_NAME,
            version=self.MODEL_VERSION,
            backend="onnx",
            model_path=f"{settings.model_dir}/nsfw_detector.onnx",
            model_size_mb=45.0,
        ))
        self._registered = True

    async def detect(
        self,
        file_id: str,
        user_id: str,
        storage_path: str,
        file_name: str,
    ) -> AIProcessResult:
        """
        执行 NSFW 检测
        """
        t_start = time.monotonic()
        logger.info(f"NSFW 检测开始: file_id={file_id}")

        try:
            # 1. 读取文件
            data = await file_reader.read_file_bytes(storage_path, file_name)
            image = Image.open(io.BytesIO(data)).convert("RGB")

            # 2. 预处理
            input_tensor = self._preprocess(image)

            # 3. 推理
            self._ensure_model_registered()
            try:
                session = model_manager.get_model(self.MODEL_NAME)
            except (RuntimeError, FileNotFoundError) as e:
                logger.warning(f"NSFW 模型未安装: {e}")
                return AIProcessResult(
                    file_id=file_id,
                    task_type=AITaskType.NSFW_DETECTION,
                    success=False,
                    skipped=True,
                    skipped_reason="NSFW 模型未安装",
                    failure_reason=FailureReason.MODEL_NOT_LOADED,
                )

            input_name = session.get_inputs()[0].name
            output_name = session.get_outputs()[0].name
            outputs = session.run([output_name], {input_name: input_tensor})[0]

            # 4. 后处理
            probabilities = self._softmax(outputs[0])
            results = self._build_results(probabilities)

            # 判断是否包含敏感内容
            is_sensitive = any(
                r["name"] in SENSITIVE_CATEGORIES
                and r["confidence"] > self.SENSITIVE_THRESHOLD
                for r in results
            )

            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.info(
                f"NSFW 检测完成: file_id={file_id}, "
                f"sensitive={is_sensitive}, "
                f"top={results[0]['name']}({results[0]['confidence']:.2f}), "
                f"elapsed={elapsed_ms:.0f}ms"
            )

            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.NSFW_DETECTION,
                success=True,
                data={
                    "classifications": results,
                    "is_sensitive": is_sensitive,
                    "sensitive_categories": [
                        r["name"] for r in results
                        if r["name"] in SENSITIVE_CATEGORIES
                        and r["confidence"] > self.SENSITIVE_THRESHOLD
                    ],
                    "model_name": self.MODEL_NAME,
                    "model_version": self.MODEL_VERSION,
                    "processing_time_ms": elapsed_ms,
                },
            )

        except Exception as e:
            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.error(f"NSFW 检测失败: file_id={file_id}, error={e}", exc_info=True)
            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.NSFW_DETECTION,
                success=False,
                failure_reason=FailureReason.INFERENCE_ERROR,
                error=str(e),
                processing_time_ms=elapsed_ms,
            )

    def _preprocess(self, image: Image.Image) -> np.ndarray:
        """图像预处理"""
        image = image.resize(self.INPUT_SIZE, Image.LANCZOS)
        img_array = np.array(image, dtype=np.float32) / 255.0

        # HWC → CHW, 添加 batch 维度
        img_array = img_array.transpose(2, 0, 1)
        img_array = np.expand_dims(img_array, axis=0)

        return img_array.astype(np.float32)

    def _softmax(self, x: np.ndarray) -> np.ndarray:
        """Softmax 计算"""
        exp_x = np.exp(x - np.max(x))
        return exp_x / exp_x.sum()

    def _build_results(self, probabilities: np.ndarray) -> list[dict]:
        """构建分类结果"""
        results = []
        for i, prob in enumerate(probabilities):
            if i < len(NSFW_CATEGORIES):
                category = NSFW_CATEGORIES[i]
                results.append({
                    "name": category,
                    "label_zh": NSFW_LABELS_ZH.get(category, category),
                    "confidence": round(float(prob), 4),
                })

        # 按置信度降序排列
        results.sort(key=lambda x: x["confidence"], reverse=True)
        return results


# =============================================================================
# 全局单例
# =============================================================================
nsfw_detector = NSFWBypassDetector()