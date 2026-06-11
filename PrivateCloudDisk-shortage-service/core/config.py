from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    redis_url: str = "redis://localhost:6379/0"
    file_upload_dir: str = "../Uploads"
    business_service_url: str = "http://127.0.0.1:8080"
    private_key_path: str = "./private_key.pem"
    public_key_path: str = "./public_key.pem"
    max_concurrent: int = 3
    operation_token_expire_seconds: int = 3600
    max_requests_per_operation_token: int = 300
    rate_per_sec: int = 10
    max_range_bytes: int = 100 * 1024 * 1024
    thumbnail_ttl: int = 3600
    operation_token_issue_file_limit: int = 10
    operation_token_issue_file_window_seconds: int = 60
    operation_token_destroy_user_limit: int = 60
    operation_token_destroy_user_window_seconds: int = 60
    operation_token_destroy_ip_limit: int = 180
    operation_token_destroy_ip_window_seconds: int = 60

    # ========== RabbitMQ 配置 ==========
    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = 5672
    rabbitmq_username: str = "guest"
    rabbitmq_password: str = "guest"
    rabbitmq_vhost: str = "/"

    # --- 文件处理主交换机 & 队列 ---
    file_process_exchange: str = "pcd.file.process.exchange"
    file_process_queue: str = "pcd.file.process.queue"
    file_process_routing_key: str = "file.process"

    # --- 文件处理死信交换机 & 队列 (DLX/DLQ) ---
    file_process_dlx: str = "pcd.file.process.dlx"
    file_process_dlq: str = "pcd.file.process.dlq"
    file_process_dlq_routing_key: str = "file.process.dlq"

    # --- 文件删除主交换机 & 队列 ---
    file_delete_exchange: str = "pcd.file.delete.exchange"
    file_delete_queue: str = "pcd.file.delete.queue"
    file_delete_routing_key: str = "file.delete"

    # --- 文件删除死信交换机 & 队列 ---
    file_delete_dlx: str = "pcd.file.delete.dlx"
    file_delete_dlq: str = "pcd.file.delete.dlq"
    file_delete_dlq_routing_key: str = "file.delete.dlq"

    # --- 病毒/木马隔离专用队列 ---
    security_quarantine_queue: str = "pcd.security.quarantine.queue"
    security_quarantine_routing_key: str = "security.quarantine"

    # --- 重试策略 ---
    retry_max_attempts: int = 3
    retry_base_delay_seconds: int = 5  # 指数退避基数
    retry_max_delay_seconds: int = 300  # 最大延迟 5 分钟

    # --- 病毒扫描 ---
    virus_scan_enabled: bool = True
    virus_scan_fail_open: bool = False  # True=扫描器不可用时放行, False=拒绝
    quarantine_dir: str = "../Uploads/quarantine"  # 隔离区目录

    # --- 磁盘空间 ---
    min_free_disk_bytes: int = 500 * 1024 * 1024  # 合并前至少 500MB 可用

    # ========== OpenSearch 配置 ==========
    opensearch_host: str = "https://localhost:9200"
    opensearch_username: str = "admin"
    opensearch_password: str = "MySecureP@ssw0rd"
    opensearch_use_ssl: bool = True
    opensearch_verify_certs: bool = False
    opensearch_timeout: int = 30
    opensearch_max_retries: int = 3

    # OpenSearch 索引名称
    opensearch_file_index: str = "pcd_file_basic"       # 文件基本信息索引
    opensearch_content_index: str = "pcd_file_content"   # 文件内容索引

    # --- 内容索引配置 ---
    content_max_chars: int = 200_000      # 单文件最大抽取字符数
    chunk_size_chars: int = 1200          # 分块大小
    chunk_overlap_chars: int = 200        # 分块重叠
    enable_ocr: bool = True               # 是否启用 OCR
    enable_image_tags: bool = True        # 是否启用图片标签

    # --- 内容索引交换机 & 队列 ---
    content_index_exchange: str = "pcd.content.index.exchange"
    content_index_queue: str = "pcd.content.index.queue"
    content_index_routing_key: str = "content.index"
    content_index_dlx: str = "pcd.content.index.dlx"
    content_index_dlq: str = "pcd.content.index.dlq"
    content_index_dlq_routing_key: str = "content.index.dlq"
    content_index_max_retries: int = 3

    class Config:
        env_file = ".env"


settings = Settings()


# ========== 任务类型常量 ==========
class TaskTypes:
    MERGE = "merge"
    HASH_CALCULATE = "hash_calculate"
    VIRUS_SCAN = "virus_scan"
    THUMBNAIL = "thumbnail"
    VIDEO_TRANSCODE = "video_transcode"
    MARK_ACTIVE = "mark_active"
    CONTENT_INDEX = "content_index"  # 文件内容索引 (OpenSearch)


# ========== 任务状态常量 ==========
class TaskStatus:
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"
    DEGRADED = "degraded"  # 核心成功但非核心失败（如缩略图失败）


# ========== 失败原因枚举（用于 DLQ 策略分发） ==========
class FailureReason:
    MERGE_IO_ERROR = "MERGE_IO_ERROR"
    MERGE_DISK_FULL = "MERGE_DISK_FULL"
    MERGE_CHUNK_MISSING = "MERGE_CHUNK_MISSING"
    MERGE_CHECKSUM_MISMATCH = "MERGE_CHECKSUM_MISMATCH"
    HASH_CALCULATE_ERROR = "HASH_CALCULATE_ERROR"
    HASH_MISMATCH = "HASH_MISMATCH"
    VIRUS_FOUND = "VIRUS_FOUND"           # 发现病毒/木马 → 不重试，立即隔离
    VIRUS_SCANNER_ERROR = "VIRUS_SCANNER_ERROR"   # 扫描器异常
    VIRUS_SCANNER_UNAVAILABLE = "VIRUS_SCANNER_UNAVAILABLE"
    THUMBNAIL_ERROR = "THUMBNAIL_ERROR"
    TRANSCODE_ERROR = "TRANSCODE_ERROR"
    MARK_ACTIVE_ERROR = "MARK_ACTIVE_ERROR"
    DELETE_IO_ERROR = "DELETE_IO_ERROR"
    NOTIFY_BS_ERROR = "NOTIFY_BS_ERROR"
    CONTENT_EXTRACT_ERROR = "CONTENT_EXTRACT_ERROR"
    CONTENT_INDEX_ERROR = "CONTENT_INDEX_ERROR"
    UNKNOWN = "UNKNOWN"

    # 不应该重试的失败原因集合（病毒/木马找到应立即隔离，不能重试）
    NO_RETRY_REASONS = frozenset({
        VIRUS_FOUND,
        MERGE_CHECKSUM_MISMATCH,
        HASH_MISMATCH,
    })


# ========== 文件类型常量 ==========
IMAGE_TYPES = frozenset({"image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/svg+xml"})
VIDEO_TYPES = frozenset({"video/mp4", "video/mpeg", "video/quicktime", "video/webm", "video/x-msvideo", "video/x-matroska"})

# ========== 任务流水线顺序 ==========
TASK_PIPELINE = [
    TaskTypes.MERGE,
    TaskTypes.HASH_CALCULATE,
    TaskTypes.VIRUS_SCAN,
    TaskTypes.THUMBNAIL,
    TaskTypes.VIDEO_TRANSCODE,
    TaskTypes.MARK_ACTIVE,
    TaskTypes.CONTENT_INDEX,
]