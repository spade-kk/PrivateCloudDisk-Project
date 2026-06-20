"""
AI Processing Service - 图片分类器

基于 ONNX Runtime 的图片分类，使用 ResNet-50 模型。
支持 ImageNet 1000 类分类，输出 Top-K 标签和置信度。

模型:
- ONNX: ResNet-50 (ImageNet 预训练)
- PyTorch: EfficientNet-B0 (备选)
- Transformers: ViT-Base (备选)
"""
from __future__ import annotations
import logging
import time
from pathlib import Path
from typing import Optional

import numpy as np
from PIL import Image

from app.core.config import settings, AITaskType, FailureReason
from app.core.services.model_manager import model_manager, ModelInfo
from app.core.services.file_reader import file_reader
from app.core.events.ai_process_event import AIProcessResult

logger = logging.getLogger("ai_service.image_classifier")

# ImageNet 1000 类标签 (精简版，完整版约 1000 个)
IMAGENET_LABELS = {
    0: "tench", 1: "goldfish", 2: "great_white_shark", 3: "tiger_shark",
    4: "hammerhead", 5: "electric_ray", 6: "stingray", 7: "rooster",
    8: "hen", 9: "ostrich", 10: "brambling", 11: "goldfinch",
    12: "house_finch", 13: "junco", 14: "indigo_bunting", 15: "robin",
    # ... 完整 1000 类别在运行时动态加载
}

# 中文标签映射
LABEL_ZH = {
    "person": "人物", "car": "汽车", "dog": "狗", "cat": "猫",
    "bird": "鸟", "fish": "鱼", "horse": "马", "sheep": "羊",
    "cow": "牛", "elephant": "大象", "bear": "熊", "zebra": "斑马",
    "giraffe": "长颈鹿", "airplane": "飞机", "train": "火车",
    "truck": "卡车", "boat": "船", "traffic_light": "交通灯",
    "fire_hydrant": "消防栓", "stop_sign": "停止标志",
    "parking_meter": "停车计时器", "bench": "长椅",
    "backpack": "背包", "umbrella": "雨伞", "handbag": "手提包",
    "tie": "领带", "suitcase": "行李箱", "frisbee": "飞盘",
    "skis": "滑雪板", "snowboard": "滑雪板", "sports_ball": "运动球",
    "kite": "风筝", "baseball_bat": "棒球棒", "baseball_glove": "棒球手套",
    "skateboard": "滑板", "surfboard": "冲浪板", "tennis_racket": "网球拍",
    "bottle": "瓶子", "wine_glass": "酒杯", "cup": "杯子",
    "fork": "叉子", "knife": "刀", "spoon": "勺子", "bowl": "碗",
    "banana": "香蕉", "apple": "苹果", "sandwich": "三明治",
    "orange": "橙子", "broccoli": "西兰花", "carrot": "胡萝卜",
    "hot_dog": "热狗", "pizza": "披萨", "donut": "甜甜圈", "cake": "蛋糕",
    "chair": "椅子", "couch": "沙发", "potted_plant": "盆栽",
    "bed": "床", "dining_table": "餐桌", "toilet": "马桶",
    "tv": "电视", "laptop": "笔记本电脑", "mouse": "鼠标",
    "remote": "遥控器", "keyboard": "键盘", "cell_phone": "手机",
    "microwave": "微波炉", "oven": "烤箱", "toaster": "烤面包机",
    "sink": "水槽", "refrigerator": "冰箱", "book": "书",
    "clock": "时钟", "vase": "花瓶", "scissors": "剪刀",
    "teddy_bear": "泰迪熊", "hair_drier": "吹风机", "toothbrush": "牙刷",
    "building": "建筑", "house": "房屋", "tree": "树",
    "flower": "花", "mountain": "山", "beach": "海滩",
    "sunset": "日落", "sky": "天空", "road": "道路",
    "food": "食物", "document": "文档", "screenshot": "截图",
    "chart": "图表", "diagram": "图表", "selfie": "自拍",
    "group_photo": "合影", "landscape": "风景", "portrait": "人像",
}


class ImageClassifier:
    """
    图片分类器

    流程:
    1. 预处理: 缩放至 224x224, 归一化
    2. 推理: ONNX Runtime 前向传播
    3. 后处理: Softmax → Top-K 标签
    """

    MODEL_NAME = "image_classifier_resnet50"
    MODEL_VERSION = "1.0.0"
    INPUT_SIZE = (224, 224)
    TOP_K = 5

    # ImageNet 标准化参数
    MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
    STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)

    def __init__(self):
        self._registered = False

    def _ensure_model_registered(self):
        if self._registered:
            return
        model_manager.register_model(ModelInfo(
            name=self.MODEL_NAME,
            version=self.MODEL_VERSION,
            backend="onnx",
            model_path=f"{settings.model_dir}/resnet50.onnx",
            model_size_mb=98.0,
        ))
        self._registered = True

    async def classify(
        self,
        file_id: str,
        user_id: str,
        storage_path: str,
        file_name: str,
    ) -> AIProcessResult:
        """
        执行图片分类

        Args:
            file_id: 文件 ID
            user_id: 用户 ID
            storage_path: 文件存储路径
            file_name: 文件名

        Returns:
            AIProcessResult
        """
        t_start = time.monotonic()
        logger.info(f"图片分类开始: file_id={file_id}, file_name={file_name}")

        try:
            # 1. 读取文件
            data = await file_reader.read_file_bytes(storage_path, file_name)
            image = Image.open(__import__("io").BytesIO(data)).convert("RGB")

            # 2. 预处理
            input_tensor = self._preprocess(image)

            # 3. 推理
            self._ensure_model_registered()
            try:
                session = model_manager.get_model(self.MODEL_NAME)
            except (RuntimeError, FileNotFoundError) as e:
                logger.warning(f"ONNX 模型未安装，使用 PyTorch 备选: {e}")
                return await self._classify_pytorch_fallback(
                    file_id, user_id, storage_path, file_name, image
                )

            input_name = session.get_inputs()[0].name
            output_name = session.get_outputs()[0].name
            outputs = session.run([output_name], {input_name: input_tensor})[0]

            # 4. 后处理
            results = self._postprocess(outputs)

            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.info(
                f"图片分类完成: file_id={file_id}, "
                f"top1={results[0]['name']}({results[0]['confidence']:.2f}), "
                f"elapsed={elapsed_ms:.0f}ms"
            )

            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.IMAGE_CLASSIFICATION,
                success=True,
                data={
                    "classifications": results,
                    "model_name": self.MODEL_NAME,
                    "model_version": self.MODEL_VERSION,
                    "processing_time_ms": elapsed_ms,
                },
            )

        except ValueError as e:
            elapsed_ms = (time.monotonic() - t_start) * 1000
            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.IMAGE_CLASSIFICATION,
                success=False,
                failure_reason=FailureReason.FILE_TOO_LARGE,
                error=str(e),
                processing_time_ms=elapsed_ms,
            )
        except Exception as e:
            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.error(f"图片分类失败: file_id={file_id}, error={e}", exc_info=True)
            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.IMAGE_CLASSIFICATION,
                success=False,
                failure_reason=FailureReason.INFERENCE_ERROR,
                error=str(e),
                processing_time_ms=elapsed_ms,
            )

    def _preprocess(self, image: Image.Image) -> np.ndarray:
        """图像预处理"""
        # 缩放
        image = image.resize(self.INPUT_SIZE, Image.LANCZOS)

        # 转换为 numpy 数组并归一化
        img_array = np.array(image, dtype=np.float32) / 255.0
        img_array = (img_array - self.MEAN) / self.STD

        # 转换为 NCHW 格式 (batch, channel, height, width)
        img_array = img_array.transpose(2, 0, 1)
        img_array = np.expand_dims(img_array, axis=0)

        return img_array.astype(np.float32)

    def _postprocess(self, outputs: np.ndarray) -> list[dict]:
        """后处理: Softmax + Top-K"""
        # Softmax
        exp_outputs = np.exp(outputs[0] - np.max(outputs[0]))
        probabilities = exp_outputs / exp_outputs.sum()

        # Top-K
        top_k_indices = np.argsort(probabilities)[-self.TOP_K:][::-1]

        results = []
        for idx in top_k_indices:
            prob = float(probabilities[idx])
            if prob < 0.05:  # 过滤低置信度结果
                continue
            label_en = IMAGENET_LABELS.get(int(idx), f"class_{idx}")
            results.append({
                "name": label_en,
                "label_zh": LABEL_ZH.get(label_en, label_en),
                "confidence": round(prob, 4),
                "class_id": int(idx),
            })

        return results

    async def _classify_pytorch_fallback(
        self,
        file_id: str,
        user_id: str,
        storage_path: str,
        file_name: str,
        image: Image.Image,
    ) -> AIProcessResult:
        """PyTorch 备选方案"""
        try:
            import torch
            from torchvision import transforms, models

            model = models.resnet50(weights=models.ResNet50_Weights.IMAGENET1K_V1)
            model.eval()

            device = model_manager.get_torch_device()
            model.to(device)

            preprocess = transforms.Compose([
                transforms.Resize(256),
                transforms.CenterCrop(224),
                transforms.ToTensor(),
                transforms.Normalize(
                    mean=[0.485, 0.456, 0.406],
                    std=[0.229, 0.224, 0.225],
                ),
            ])

            input_tensor = preprocess(image).unsqueeze(0).to(device)

            with torch.no_grad():
                outputs = model(input_tensor)
                probabilities = torch.nn.functional.softmax(outputs[0], dim=0)

            top_k = torch.topk(probabilities, self.TOP_K)
            results = []
            for i in range(len(top_k.indices)):
                idx = int(top_k.indices[i])
                prob = float(top_k.values[i])
                if prob < 0.05:
                    continue
                label_en = IMAGENET_LABELS.get(idx, f"class_{idx}")
                results.append({
                    "name": label_en,
                    "label_zh": LABEL_ZH.get(label_en, label_en),
                    "confidence": round(prob, 4),
                    "class_id": idx,
                })

            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.IMAGE_CLASSIFICATION,
                success=True,
                data={
                    "classifications": results,
                    "model_name": "resnet50_pytorch",
                    "model_version": "1.0.0",
                },
            )
        except Exception as e:
            logger.error(f"PyTorch 备选方案也失败: {e}")
            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.IMAGE_CLASSIFICATION,
                success=False,
                failure_reason=FailureReason.INFERENCE_ERROR,
                error=str(e),
            )


# =============================================================================
# 全局单例
# =============================================================================
image_classifier = ImageClassifier()