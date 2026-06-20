"""
AI Processing Service - 人脸检测器

基于 face_recognition (dlib) 的人脸检测和编码。
支持:
- 人脸检测 (边界框 + 关键点)
- 人脸编码 (128维向量)
- 批量处理

模型:
- dlib HOG + CNN (face_recognition 封装)
- 备选: ONNX RetinaFace
"""
from __future__ import annotations
import logging
import time
import io
from typing import List, Optional

import numpy as np
from PIL import Image

from app.core.config import settings, AITaskType, FailureReason
from app.core.services.file_reader import file_reader
from app.core.events.ai_process_event import AIProcessResult

logger = logging.getLogger("ai_service.face_detector")


class FaceDetector:
    """
    人脸检测器

    流程:
    1. 加载图片
    2. 检测人脸位置 (HOG/CNN)
    3. 提取人脸编码 (128维 embedding)
    4. 返回检测结果
    """

    MODEL_NAME = "face_detector"
    MODEL_VERSION = "1.0.0"

    def __init__(self):
        self._initialized = False
        self._face_recognition = None

    def _ensure_initialized(self):
        if self._initialized:
            return
        try:
            import face_recognition
            self._face_recognition = face_recognition
            self._initialized = True
            logger.info("人脸检测器初始化完成")
        except ImportError:
            logger.error("face_recognition 未安装，人脸检测不可用")
            raise RuntimeError("face_recognition 未安装")

    async def detect(
        self,
        file_id: str,
        user_id: str,
        storage_path: str,
        file_name: str,
    ) -> AIProcessResult:
        """
        执行人脸检测

        Returns:
            AIProcessResult:
                data.faces: [{
                    "face_index": 0,
                    "bbox": {"x": 0, "y": 0, "w": 100, "h": 100},
                    "landmarks": {"chin": [...], "left_eye": [...], ...},
                    "encoding": [0.1, 0.2, ...],  # 128维 float32 列表
                    "confidence": 0.95,
                }]
        """
        t_start = time.monotonic()
        logger.info(f"人脸检测开始: file_id={file_id}")

        try:
            self._ensure_initialized()

            # 1. 读取文件
            data = await file_reader.read_file_bytes(storage_path, file_name)
            image = Image.open(io.BytesIO(data))

            # 转换为 numpy RGB 数组
            if image.mode != "RGB":
                image = image.convert("RGB")
            img_array = np.array(image)

            # 2. 检测人脸位置
            face_locations = self._face_recognition.face_locations(
                img_array,
                model="hog",  # HOG 比 CNN 快，适合 CPU
            )

            if not face_locations:
                logger.info(f"未检测到人脸: file_id={file_id}")
                return AIProcessResult(
                    file_id=file_id,
                    task_type=AITaskType.FACE_DETECTION,
                    success=True,
                    skipped=True,
                    skipped_reason="未检测到人脸",
                    data={"face_count": 0, "faces": []},
                )

            # 3. 提取人脸编码
            face_encodings = self._face_recognition.face_encodings(
                img_array,
                face_locations,
                num_jitters=1,  # 重采样次数，越大越准确但越慢
            )

            # 4. 提取人脸关键点
            face_landmarks_list = self._face_recognition.face_landmarks(
                img_array,
                face_locations,
            )

            # 5. 构建结果
            faces = []
            for i, (location, encoding, landmarks) in enumerate(
                zip(face_locations, face_encodings, face_landmarks_list)
            ):
                top, right, bottom, left = location
                faces.append({
                    "face_index": i,
                    "bbox": {
                        "x": int(left),
                        "y": int(top),
                        "w": int(right - left),
                        "h": int(bottom - top),
                    },
                    "landmarks": landmarks,
                    "encoding": encoding.tolist(),  # 128维向量
                    "encoding_bytes": encoding.tobytes(),  # 用于 MySQL 存储
                    "confidence": 1.0,  # dlib 不提供置信度
                })

            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.info(
                f"人脸检测完成: file_id={file_id}, "
                f"faces={len(faces)}, elapsed={elapsed_ms:.0f}ms"
            )

            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.FACE_DETECTION,
                success=True,
                data={
                    "face_count": len(faces),
                    "faces": faces,
                    "model_name": self.MODEL_NAME,
                    "model_version": self.MODEL_VERSION,
                    "processing_time_ms": elapsed_ms,
                },
            )

        except RuntimeError:
            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.FACE_DETECTION,
                success=False,
                skipped=True,
                skipped_reason="face_recognition 未安装",
                failure_reason=FailureReason.MODEL_NOT_LOADED,
            )
        except Exception as e:
            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.error(f"人脸检测失败: file_id={file_id}, error={e}", exc_info=True)
            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.FACE_DETECTION,
                success=False,
                failure_reason=FailureReason.INFERENCE_ERROR,
                error=str(e),
                processing_time_ms=elapsed_ms,
            )


# =============================================================================
# 全局单例
# =============================================================================
face_detector = FaceDetector()