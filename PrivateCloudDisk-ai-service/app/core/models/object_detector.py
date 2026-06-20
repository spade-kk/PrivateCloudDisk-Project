"""
AI Processing Service - 物体检测器

基于 ONNX Runtime 的物体检测，使用 YOLOv8-nano 模型。
支持 80 类 COCO 物体检测，输出边界框、类别和置信度。

模型: YOLOv8-nano (ONNX 导出)
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

logger = logging.getLogger("ai_service.object_detector")

# COCO 80类标签
COCO_LABELS = [
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
    "boat", "traffic_light", "fire_hydrant", "stop_sign", "parking_meter", "bench",
    "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
    "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
    "skis", "snowboard", "sports_ball", "kite", "baseball_bat", "baseball_glove",
    "skateboard", "surfboard", "tennis_racket", "bottle", "wine_glass", "cup",
    "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
    "broccoli", "carrot", "hot_dog", "pizza", "donut", "cake", "chair", "couch",
    "potted_plant", "bed", "dining_table", "toilet", "tv", "laptop", "mouse",
    "remote", "keyboard", "cell_phone", "microwave", "oven", "toaster", "sink",
    "refrigerator", "book", "clock", "vase", "scissors", "teddy_bear", "hair_drier",
    "toothbrush",
]

# 中文标签
COCO_LABELS_ZH = {
    "person": "人", "bicycle": "自行车", "car": "汽车", "motorcycle": "摩托车",
    "airplane": "飞机", "bus": "公交车", "train": "火车", "truck": "卡车",
    "boat": "船", "traffic_light": "交通灯", "fire_hydrant": "消防栓",
    "stop_sign": "停止标志", "parking_meter": "停车计时器", "bench": "长椅",
    "bird": "鸟", "cat": "猫", "dog": "狗", "horse": "马", "sheep": "羊",
    "cow": "牛", "elephant": "大象", "bear": "熊", "zebra": "斑马",
    "giraffe": "长颈鹿", "backpack": "背包", "umbrella": "雨伞",
    "handbag": "手提包", "tie": "领带", "suitcase": "行李箱",
    "frisbee": "飞盘", "skis": "滑雪板", "snowboard": "滑雪板",
    "sports_ball": "运动球", "kite": "风筝", "baseball_bat": "棒球棒",
    "baseball_glove": "棒球手套", "skateboard": "滑板", "surfboard": "冲浪板",
    "tennis_racket": "网球拍", "bottle": "瓶子", "wine_glass": "酒杯",
    "cup": "杯子", "fork": "叉子", "knife": "刀", "spoon": "勺子",
    "bowl": "碗", "banana": "香蕉", "apple": "苹果", "sandwich": "三明治",
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
}


class ObjectDetector:
    """
    物体检测器

    流程:
    1. 预处理: 缩放至 640x640, 归一化
    2. 推理: ONNX Runtime
    3. 后处理: NMS (非极大值抑制)
    """

    MODEL_NAME = "object_detector_yolov8n"
    MODEL_VERSION = "1.0.0"
    INPUT_SIZE = 640
    CONFIDENCE_THRESHOLD = 0.35
    IOU_THRESHOLD = 0.45

    def __init__(self):
        self._registered = False

    def _ensure_model_registered(self):
        if self._registered:
            return
        model_manager.register_model(ModelInfo(
            name=self.MODEL_NAME,
            version=self.MODEL_VERSION,
            backend="onnx",
            model_path=f"{settings.model_dir}/yolov8n.onnx",
            model_size_mb=6.2,
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
        执行物体检测
        """
        t_start = time.monotonic()
        logger.info(f"物体检测开始: file_id={file_id}")

        try:
            # 1. 读取文件
            data = await file_reader.read_file_bytes(storage_path, file_name)
            image = Image.open(io.BytesIO(data)).convert("RGB")
            orig_w, orig_h = image.size

            # 2. 预处理
            input_tensor = self._preprocess(image)

            # 3. 推理
            self._ensure_model_registered()
            try:
                session = model_manager.get_model(self.MODEL_NAME)
            except (RuntimeError, FileNotFoundError) as e:
                logger.warning(f"YOLOv8 ONNX 模型未安装: {e}")
                return AIProcessResult(
                    file_id=file_id,
                    task_type=AITaskType.OBJECT_DETECTION,
                    success=False,
                    skipped=True,
                    skipped_reason="YOLOv8 模型未安装",
                    failure_reason=FailureReason.MODEL_NOT_LOADED,
                )

            input_name = session.get_inputs()[0].name
            outputs = session.run(None, {input_name: input_tensor})

            # 4. 后处理 + NMS
            detections = self._postprocess(outputs, orig_w, orig_h)

            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.info(
                f"物体检测完成: file_id={file_id}, "
                f"objects={len(detections)}, elapsed={elapsed_ms:.0f}ms"
            )

            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.OBJECT_DETECTION,
                success=True,
                data={
                    "detections": detections,
                    "object_count": len(detections),
                    "model_name": self.MODEL_NAME,
                    "model_version": self.MODEL_VERSION,
                    "processing_time_ms": elapsed_ms,
                },
            )

        except Exception as e:
            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.error(f"物体检测失败: file_id={file_id}, error={e}", exc_info=True)
            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.OBJECT_DETECTION,
                success=False,
                failure_reason=FailureReason.INFERENCE_ERROR,
                error=str(e),
                processing_time_ms=elapsed_ms,
            )

    def _preprocess(self, image: Image.Image) -> np.ndarray:
        """YOLOv8 预处理: letterbox + 归一化"""
        # Letterbox: 保持宽高比缩放
        img_w, img_h = image.size
        scale = self.INPUT_SIZE / max(img_w, img_h)
        new_w, new_h = int(img_w * scale), int(img_h * scale)

        image = image.resize((new_w, new_h), Image.LANCZOS)

        # 创建 640x640 画布并居中放置
        canvas = Image.new("RGB", (self.INPUT_SIZE, self.INPUT_SIZE), (114, 114, 114))
        paste_x = (self.INPUT_SIZE - new_w) // 2
        paste_y = (self.INPUT_SIZE - new_h) // 2
        canvas.paste(image, (paste_x, paste_y))

        # 转换为 numpy 并归一化
        img_array = np.array(canvas, dtype=np.float32) / 255.0
        img_array = img_array.transpose(2, 0, 1)  # HWC → CHW
        img_array = np.expand_dims(img_array, axis=0)  # 添加 batch 维度

        return img_array.astype(np.float32)

    def _postprocess(
        self,
        outputs: list,
        orig_w: int,
        orig_h: int,
    ) -> list[dict]:
        """YOLOv8 后处理: 解析输出 + NMS"""
        # YOLOv8 输出: [1, 84, 8400] (80 classes + 4 bbox)
        predictions = outputs[0][0]  # [84, 8400]

        # 转置为 [8400, 84]
        predictions = predictions.T

        # 提取 bbox 和 scores
        bboxes = predictions[:, :4]  # cx, cy, w, h (归一化)
        scores = predictions[:, 4:]  # 80 class scores

        # 获取每个检测的最高分和类别
        max_scores = np.max(scores, axis=1)
        class_ids = np.argmax(scores, axis=1)

        # 过滤低置信度
        mask = max_scores > self.CONFIDENCE_THRESHOLD
        bboxes = bboxes[mask]
        scores = max_scores[mask]
        class_ids = class_ids[mask]

        if len(bboxes) == 0:
            return []

        # 转换 cx,cy,w,h → x1,y1,x2,y2
        x1 = bboxes[:, 0] - bboxes[:, 2] / 2
        y1 = bboxes[:, 1] - bboxes[:, 3] / 2
        x2 = bboxes[:, 0] + bboxes[:, 2] / 2
        y2 = bboxes[:, 1] + bboxes[:, 3] / 2

        # 映射回原始图像尺寸
        scale_x = orig_w / self.INPUT_SIZE
        scale_y = orig_h / self.INPUT_SIZE
        x1 = np.clip(x1 * orig_w, 0, orig_w)
        y1 = np.clip(y1 * orig_h, 0, orig_h)
        x2 = np.clip(x2 * orig_w, 0, orig_w)
        y2 = np.clip(y2 * orig_h, 0, orig_h)

        # NMS (简化版: 按类别分组)
        keep_indices = self._nms_simple(
            np.column_stack([x1, y1, x2, y2]),
            scores,
            class_ids,
        )

        # 构建结果
        detections = []
        for idx in keep_indices:
            detections.append({
                "name": COCO_LABELS[int(class_ids[idx])],
                "label_zh": COCO_LABELS_ZH.get(
                    COCO_LABELS[int(class_ids[idx])],
                    COCO_LABELS[int(class_ids[idx])],
                ),
                "confidence": round(float(scores[idx]), 4),
                "bbox": {
                    "x": int(x1[idx]),
                    "y": int(y1[idx]),
                    "w": int(x2[idx] - x1[idx]),
                    "h": int(y2[idx] - y1[idx]),
                },
            })

        return detections

    def _nms_simple(
        self,
        boxes: np.ndarray,
        scores: np.ndarray,
        class_ids: np.ndarray,
    ) -> list[int]:
        """简化版 NMS (按类别分组)"""
        keep = []
        unique_classes = np.unique(class_ids)

        for cls in unique_classes:
            cls_mask = class_ids == cls
            cls_boxes = boxes[cls_mask]
            cls_scores = scores[cls_mask]
            cls_indices = np.where(cls_mask)[0]

            # 按分数降序排列
            order = np.argsort(cls_scores)[::-1]

            while len(order) > 0:
                i = order[0]
                keep.append(int(cls_indices[i]))

                if len(order) == 1:
                    break

                # 计算 IoU
                iou = self._compute_iou_batch(cls_boxes[i], cls_boxes[order[1:]])
                order = order[1:][iou < self.IOU_THRESHOLD]

        return keep

    @staticmethod
    def _compute_iou_batch(box: np.ndarray, boxes: np.ndarray) -> np.ndarray:
        """计算 IoU (批量)"""
        x1 = np.maximum(box[0], boxes[:, 0])
        y1 = np.maximum(box[1], boxes[:, 1])
        x2 = np.minimum(box[2], boxes[:, 2])
        y2 = np.minimum(box[3], boxes[:, 3])

        inter_area = np.maximum(0, x2 - x1) * np.maximum(0, y2 - y1)
        box_area = (box[2] - box[0]) * (box[3] - box[1])
        boxes_area = (boxes[:, 2] - boxes[:, 0]) * (boxes[:, 3] - boxes[:, 1])
        union_area = box_area + boxes_area - inter_area

        return inter_area / (union_area + 1e-6)


# =============================================================================
# 全局单例
# =============================================================================
object_detector = ObjectDetector()