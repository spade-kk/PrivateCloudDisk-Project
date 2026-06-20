"""
AI Processing Service - 核心配置

所有配置通过环境变量注入，支持 .env 文件覆盖。
与 storage-service 和 platform-service 共用基础设施配置。
"""
from __future__ import annotations
import os
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # ================================================================
    # 服务基础配置
    # ================================================================
    ai_service_host: str = "0.0.0.0"
    ai_service_port: int = 8001
    ai_service_name: str = "PrivateCloudDisk AI Service"
    enable_docs: bool = False  # 生产环境默认关闭 OpenAPI 文档

    # ================================================================
    # RabbitMQ 配置 (与 storage-service 共用集群)
    # ================================================================
    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = 5672
    rabbitmq_user: str = "guest"
    rabbitmq_pass: str = "guest"
    rabbitmq_vhost: str = "/"

    # --- AI 处理主交换机 & 队列 ---
    ai_process_exchange: str = "pcd.ai.process.exchange"
    ai_process_queue: str = "pcd.ai.process.queue"
    ai_process_routing_key: str = "ai.process"

    # --- AI 处理死信交换机 & 队列 ---
    ai_process_dlx: str = "pcd.ai.process.dlx"
    ai_process_dlq: str = "pcd.ai.process.dlq"
    ai_process_dlq_routing_key: str = "ai.process.dlq"

    # --- 人脸聚类交换机 & 队列 ---
    face_cluster_exchange: str = "pcd.ai.face.cluster.exchange"
    face_cluster_queue: str = "pcd.ai.face.cluster.queue"
    face_cluster_routing_key: str = "ai.face.cluster"
    face_cluster_dlx: str = "pcd.ai.face.cluster.dlx"
    face_cluster_dlq: str = "pcd.ai.face.cluster.dlq"
    face_cluster_dlq_routing_key: str = "ai.face.cluster.dlq"

    # --- 推荐系统交换机 & 队列 ---
    recommendation_exchange: str = "pcd.ai.recommendation.exchange"
    recommendation_queue: str = "pcd.ai.recommendation.queue"
    recommendation_routing_key: str = "ai.recommendation"
    recommendation_dlx: str = "pcd.ai.recommendation.dlx"
    recommendation_dlq: str = "pcd.ai.recommendation.dlq"
    recommendation_dlq_routing_key: str = "ai.recommendation.dlq"

    # --- 重试策略 ---
    retry_max_attempts: int = 3
    retry_base_delay_seconds: int = 5
    retry_max_delay_seconds: int = 300

    # ================================================================
    # Redis 配置
    # ================================================================
    redis_url: str = "redis://localhost:6379/1"

    # ================================================================
    # MySQL 配置 (与 platform-service 共用)
    # ================================================================
    mysql_host: str = "localhost"
    mysql_port: int = 3306
    mysql_user: str = "root"
    mysql_password: str = "20070315mwz"
    mysql_database: str = "private_cloud_disk"

    @property
    def mysql_url(self) -> str:
        return (
            f"mysql+aiomysql://{self.mysql_user}:{self.mysql_password}"
            f"@{self.mysql_host}:{self.mysql_port}/{self.mysql_database}"
            f"?charset=utf8mb4"
        )

    @property
    def mysql_url_asyncmy(self) -> str:
        return (
            f"mysql+asyncmy://{self.mysql_user}:{self.mysql_password}"
            f"@{self.mysql_host}:{self.mysql_port}/{self.mysql_database}"
            f"?charset=utf8mb4"
        )

    # ================================================================
    # MinIO 对象存储配置
    # ================================================================
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    minio_bucket: str = "private-cloud-disk"
    minio_secure: bool = False

    # ================================================================
    # OpenSearch 配置
    # ================================================================
    opensearch_host: str = "https://localhost:9200"
    opensearch_username: str = "admin"
    opensearch_password: str = "MySecureP@ssw0rd"
    opensearch_use_ssl: bool = True
    opensearch_verify_certs: bool = False
    opensearch_timeout: int = 30
    opensearch_max_retries: int = 3

    opensearch_file_index: str = "pcd_file_basic"
    opensearch_content_index: str = "pcd_file_content"

    # ================================================================
    # 模型目录
    # ================================================================
    model_dir: str = "./models"
    model_cache_dir: str = "./models/.cache"

    # ================================================================
    # AI 功能开关
    # ================================================================
    ai_image_classification_enabled: bool = True
    ai_face_detection_enabled: bool = True
    ai_object_detection_enabled: bool = True
    ai_nsfw_detection_enabled: bool = True
    ai_nlp_tagging_enabled: bool = True
    ai_ocr_enabled: bool = True
    ai_summarization_enabled: bool = True
    ai_recommendation_enabled: bool = True

    # ================================================================
    # 推理配置
    # ================================================================
    ai_inference_device: str = "cpu"  # cpu | cuda | mps
    ai_inference_batch_size: int = 4
    ai_inference_timeout_seconds: int = 120
    ai_max_file_size_mb: int = 500

    # ================================================================
    # Worker 配置
    # ================================================================
    worker_prefetch_ai: int = 4
    worker_concurrency_ai: int = 8
    worker_prefetch_face_cluster: int = 1
    worker_concurrency_face_cluster: int = 2
    worker_prefetch_recommendation: int = 1
    worker_concurrency_recommendation: int = 2
    worker_prefetch_dlq: int = 1
    worker_concurrency_dlq: int = 2
    worker_log_level: str = "INFO"

    # ================================================================
    # 人脸聚类配置
    # ================================================================
    face_cluster_min_faces: int = 10
    face_cluster_eps: float = 0.5
    face_cluster_min_samples: int = 2
    face_cluster_batch_size: int = 100

    # ================================================================
    # 推荐系统配置
    # ================================================================
    recommendation_update_interval_hours: int = 6
    recommendation_top_k: int = 20

    # ================================================================
    # 文件存储路径 (与 storage-service 共享)
    # ================================================================
    shared_storage_path: str = "/data/uploads"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


# =============================================================================
# 全局单例
# =============================================================================
settings = Settings()


# =============================================================================
# AI 任务类型常量
# =============================================================================
class AITaskType:
    IMAGE_CLASSIFICATION = "image_classification"
    FACE_DETECTION = "face_detection"
    OBJECT_DETECTION = "object_detection"
    NSFW_DETECTION = "nsfw_detection"
    NLP_TAGGING = "nlp_tagging"
    OCR = "ocr"
    SUMMARIZATION = "summarization"
    FACE_CLUSTERING = "face_clustering"
    RECOMMENDATION = "recommendation"


# =============================================================================
# AI 任务状态常量
# =============================================================================
class AITaskStatus:
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"
    DEGRADED = "degraded"


# =============================================================================
# 失败原因枚举
# =============================================================================
class FailureReason:
    FILE_NOT_FOUND = "FILE_NOT_FOUND"
    FILE_TOO_LARGE = "FILE_TOO_LARGE"
    FILE_TYPE_UNSUPPORTED = "FILE_TYPE_UNSUPPORTED"
    MODEL_NOT_LOADED = "MODEL_NOT_LOADED"
    INFERENCE_TIMEOUT = "INFERENCE_TIMEOUT"
    INFERENCE_ERROR = "INFERENCE_ERROR"
    MODEL_DOWNLOAD_ERROR = "MODEL_DOWNLOAD_ERROR"
    DB_WRITE_ERROR = "DB_WRITE_ERROR"
    OPENSEARCH_ERROR = "OPENSEARCH_ERROR"
    UNKNOWN = "UNKNOWN"

    NO_RETRY_REASONS = frozenset({
        FILE_TYPE_UNSUPPORTED,
        FILE_TOO_LARGE,
    })


# =============================================================================
# 支持的文件类型
# =============================================================================
IMAGE_MIME_TYPES = frozenset({
    "image/jpeg", "image/png", "image/gif", "image/webp",
    "image/bmp", "image/tiff", "image/heic", "image/heif",
})

DOCUMENT_MIME_TYPES = frozenset({
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "text/plain",
    "text/csv",
    "text/markdown",
})

VIDEO_MIME_TYPES = frozenset({
    "video/mp4", "video/mpeg", "video/quicktime",
    "video/webm", "video/x-msvideo", "video/x-matroska",
})

# AI 任务 → 适用文件类型映射
AI_TASK_FILE_TYPES = {
    AITaskType.IMAGE_CLASSIFICATION: IMAGE_MIME_TYPES,
    AITaskType.FACE_DETECTION: IMAGE_MIME_TYPES,
    AITaskType.OBJECT_DETECTION: IMAGE_MIME_TYPES,
    AITaskType.NSFW_DETECTION: IMAGE_MIME_TYPES,
    AITaskType.NLP_TAGGING: DOCUMENT_MIME_TYPES,
    AITaskType.OCR: IMAGE_MIME_TYPES | {"application/pdf"},
    AITaskType.SUMMARIZATION: DOCUMENT_MIME_TYPES,
}