"""
AI Processing Service - 模型管理器

职责:
1. 模型生命周期管理 (加载、缓存、卸载)
2. 模型下载 (首次使用时自动下载预训练模型)
3. 推理设备管理 (CPU / CUDA / MPS)
4. 模型版本管理

支持的推理后端:
- ONNX Runtime (推荐，跨平台，高性能)
- PyTorch (GPU 训练/推理)
- TensorFlow (部分模型)
- HuggingFace Transformers (NLP)
- PaddleOCR (增强 OCR)
"""
from __future__ import annotations
import os
import logging
import threading
from pathlib import Path
from typing import Any, Optional
from dataclasses import dataclass, field

from app.core.config import settings

logger = logging.getLogger("ai_service.model_manager")


@dataclass
class ModelInfo:
    """模型元信息"""
    name: str
    version: str
    backend: str           # onnx | pytorch | tensorflow | transformers | paddle
    model_path: str = ""   # 本地模型文件路径
    model_size_mb: float = 0.0
    loaded: bool = False
    device: str = "cpu"


class ModelManager:
    """
    模型管理器

    特性:
    - 懒加载: 首次使用时加载模型
    - 线程安全: 使用锁保护加载过程
    - 自动下载: 模型不存在时自动下载
    - 设备感知: 自动检测可用设备 (CUDA > MPS > CPU)
    """

    def __init__(self):
        self._models: dict[str, Any] = {}
        self._model_info: dict[str, ModelInfo] = {}
        self._lock = threading.Lock()
        self._device = self._detect_device()

        # 确保模型目录存在
        os.makedirs(settings.model_dir, exist_ok=True)
        os.makedirs(settings.model_cache_dir, exist_ok=True)

        logger.info(f"模型管理器初始化: device={self._device}, model_dir={settings.model_dir}")

    @staticmethod
    def _detect_device() -> str:
        """自动检测推理设备"""
        device = settings.ai_inference_device.lower()
        if device == "cuda":
            try:
                import torch
                if torch.cuda.is_available():
                    logger.info(f"检测到 CUDA: {torch.cuda.get_device_name(0)}")
                    return "cuda"
                else:
                    logger.warning("CUDA 不可用，回退到 CPU")
                    return "cpu"
            except ImportError:
                logger.warning("PyTorch 未安装，使用 CPU")
                return "cpu"
        elif device == "mps":
            try:
                import torch
                if torch.backends.mps.is_available():
                    logger.info("检测到 Apple MPS")
                    return "mps"
                else:
                    logger.warning("MPS 不可用，回退到 CPU")
                    return "cpu"
            except ImportError:
                return "cpu"
        return "cpu"

    @property
    def device(self) -> str:
        return self._device

    def is_gpu_available(self) -> bool:
        return self._device in ("cuda", "mps")

    def get_onnx_providers(self) -> list[str]:
        """获取 ONNX Runtime 可用的执行提供者"""
        providers = ["CPUExecutionProvider"]
        if self._device == "cuda":
            try:
                import onnxruntime as ort
                if "CUDAExecutionProvider" in ort.get_available_providers():
                    providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]
            except ImportError:
                pass
        elif self._device == "mps":
            try:
                import onnxruntime as ort
                if "CoreMLExecutionProvider" in ort.get_available_providers():
                    providers = ["CoreMLExecutionProvider", "CPUExecutionProvider"]
            except ImportError:
                pass
        return providers

    def get_torch_device(self):
        """获取 PyTorch 设备"""
        import torch
        if self._device == "cuda":
            return torch.device("cuda:0")
        elif self._device == "mps":
            return torch.device("mps")
        return torch.device("cpu")

    def register_model(self, info: ModelInfo) -> None:
        """注册模型信息"""
        self._model_info[info.name] = info

    def get_model(self, name: str) -> Any:
        """
        获取已加载的模型

        Raises:
            KeyError: 模型未注册
            RuntimeError: 模型加载失败
        """
        if name in self._models:
            return self._models[name]

        if name not in self._model_info:
            raise KeyError(f"模型未注册: {name}")

        with self._lock:
            # 双重检查
            if name in self._models:
                return self._models[name]

            info = self._model_info[name]
            logger.info(f"加载模型: {name} (v{info.version}, backend={info.backend})")

            try:
                model = self._load_model(info)
                self._models[name] = model
                info.loaded = True
                logger.info(f"模型加载完成: {name}")
                return model
            except Exception as e:
                logger.error(f"模型加载失败: {name}, error={e}", exc_info=True)
                raise RuntimeError(f"模型加载失败: {name}") from e

    def _load_model(self, info: ModelInfo) -> Any:
        """根据后端类型加载模型"""
        if info.backend == "onnx":
            return self._load_onnx_model(info)
        elif info.backend == "pytorch":
            return self._load_pytorch_model(info)
        elif info.backend == "transformers":
            return self._load_transformers_model(info)
        elif info.backend == "paddle":
            return self._load_paddle_model(info)
        else:
            raise ValueError(f"不支持的模型后端: {info.backend}")

    def _load_onnx_model(self, info: ModelInfo) -> Any:
        """加载 ONNX 模型"""
        import onnxruntime as ort

        model_path = info.model_path or self._resolve_model_path(info.name)
        providers = self.get_onnx_providers()

        session_options = ort.SessionOptions()
        session_options.graph_optimization_level = (
            ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        )
        session_options.intra_op_num_threads = os.cpu_count() or 4

        return ort.InferenceSession(
            model_path,
            sess_options=session_options,
            providers=providers,
        )

    def _load_pytorch_model(self, info: ModelInfo) -> Any:
        """加载 PyTorch 模型"""
        import torch

        model_path = info.model_path or self._resolve_model_path(info.name)
        device = self.get_torch_device()

        model = torch.load(model_path, map_location=device, weights_only=False)
        model.eval()
        return model

    def _load_transformers_model(self, info: ModelInfo) -> Any:
        """加载 HuggingFace Transformers 模型"""
        from transformers import AutoModel, AutoTokenizer, AutoModelForSequenceClassification

        model_name = info.model_path or self._get_transformers_model_name(info.name)

        try:
            tokenizer = AutoTokenizer.from_pretrained(
                model_name,
                cache_dir=settings.model_cache_dir,
            )
            model = AutoModelForSequenceClassification.from_pretrained(
                model_name,
                cache_dir=settings.model_cache_dir,
            )
            model.to(self.get_torch_device())
            model.eval()
            return {"model": model, "tokenizer": tokenizer}
        except Exception as e:
            logger.warning(f"Transformers 加载失败: {e}, 尝试 AutoModel")
            model = AutoModel.from_pretrained(
                model_name,
                cache_dir=settings.model_cache_dir,
            )
            model.to(self.get_torch_device())
            model.eval()
            return {"model": model, "tokenizer": None}

    def _load_paddle_model(self, info: ModelInfo) -> Any:
        """加载 PaddlePaddle 模型"""
        try:
            from paddleocr import PaddleOCR
            return PaddleOCR(
                use_angle_cls=True,
                lang="ch",
                use_gpu=(self._device == "cuda"),
                show_log=False,
            )
        except ImportError:
            logger.error("PaddleOCR 未安装")
            raise

    def _resolve_model_path(self, model_name: str) -> str:
        """解析模型文件路径"""
        # 检查模型目录
        model_dir = Path(settings.model_dir)
        candidates = [
            model_dir / f"{model_name}.onnx",
            model_dir / f"{model_name}.pt",
            model_dir / f"{model_name}.pth",
            model_dir / model_name / "model.onnx",
        ]

        for candidate in candidates:
            if candidate.exists():
                return str(candidate)

        raise FileNotFoundError(
            f"模型文件未找到: {model_name}. "
            f"请将模型文件放在 {settings.model_dir} 目录下"
        )

    def _get_transformers_model_name(self, model_name: str) -> str:
        """获取 Transformers 模型名称映射"""
        model_map = {
            "nlp_tagger": "bert-base-chinese",
            "text_classifier": "bert-base-chinese",
            "summarizer": "fnlp/bart-base-chinese",
            "image_classifier_vit": "google/vit-base-patch16-224",
        }
        return model_map.get(model_name, model_name)

    def unload_model(self, name: str) -> None:
        """卸载模型释放内存"""
        with self._lock:
            if name in self._models:
                del self._models[name]
                if name in self._model_info:
                    self._model_info[name].loaded = False
                logger.info(f"模型已卸载: {name}")

    def get_loaded_models(self) -> list[str]:
        """获取已加载的模型列表"""
        return list(self._models.keys())

    def get_all_model_info(self) -> dict[str, ModelInfo]:
        """获取所有模型信息"""
        return dict(self._model_info)


# =============================================================================
# 全局单例
# =============================================================================
model_manager = ModelManager()