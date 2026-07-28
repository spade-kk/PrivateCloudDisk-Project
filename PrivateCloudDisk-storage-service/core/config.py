from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    redis_url: str = "redis://localhost:6379/0"
    file_upload_dir: str = "../Uploads"

    # AUDIT FIX [7.4]: 预览资源以 MySQL 为事实源，Redis 仅承担查询加速与热点缓存。
    mysql_host: str = "localhost"
    mysql_port: int = 3306
    mysql_user: str = "root"
    mysql_password: str = "20070315mwz"
    mysql_database: str = "private_cloud_disk"
    mysql_pool_min_size: int = 2
    mysql_pool_max_size: int = 10
    mysql_connect_retries: int = 10
    mysql_connect_retry_delay_seconds: float = 2.0
    preview_resource_cache_ttl: int = 600

    # ========== 文件存储类型配置 ==========
    # 存储类型: localstorage（本地磁盘）/ minio（MinIO 对象存储）
    storage_type: str = "localstorage"

    # MinIO 配置（仅在 storage_type=minio 时生效）
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    minio_bucket: str = "pcd-uploads"
    minio_secure: bool = False

    business_service_url: str = "http://127.0.0.1:8080"
    private_key_path: str = "./private_key.pem"
    public_key_path: str = "./public_key.pem"
    max_concurrent: int = 3
    operation_token_expire_seconds: int = 3600
    max_requests_per_operation_token: int = 300
    rate_per_sec: int = 10
    max_range_bytes: int = 100 * 1024 * 1024
    # 需求三-2/3/4：原始内容预览授权比下载授权更短、更严格，且仅允许白名单源文件。
    preview_grant_ttl_seconds: int = 120
    preview_grant_user_max_active: int = 6
    preview_grant_user_ip_max_active: int = 3
    preview_grant_max_requests: int = 24
    preview_grant_rate_per_sec: int = 6
    preview_grant_max_concurrent: int = 2
    preview_text_max_bytes: int = 10 * 1024 * 1024
    preview_image_max_bytes: int = 25 * 1024 * 1024
    preview_max_range_bytes: int = 8 * 1024 * 1024
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

    # ====== 旧版文件处理（保留兼容，逐步迁移） ======
    file_process_exchange: str = "pcd.file.process.exchange"
    # file_process_queue: str = "pcd.file.process.queue"
    file_process_routing_key: str = "file.process"
    file_process_dlx: str = "pcd.file.process.dlx"
    file_process_dlq: str = "pcd.file.process.dlq"
    file_process_dlq_routing_key: str = "file.process.dlq"

    # ========== 文件后台处理拓扑（Backend Processing — 顺序流水线） ==========
    # 每个阶段独立 exchange + queue + DLQ，便于独立追踪耗时和运维

    # --- 后台处理主交换机 (DIRECT) ---
    file_backend_exchange: str = "pcd.file.backend.exchange"
    file_backend_dlx: str = "pcd.file.backend.dlx"

    # --- merge 合并 ---
    file_backend_merge_queue: str = "pcd.file.backend.merge.queue"
    file_backend_merge_routing_key: str = "file.backend.merge"
    file_backend_merge_dlq: str = "pcd.file.backend.merge.dlq"
    file_backend_merge_dlq_routing_key: str = "file.backend.merge.dlq"
    file_backend_merge_max_retries: int = 3

    # --- hash 哈希计算 ---
    file_backend_hash_queue: str = "pcd.file.backend.hash.queue"
    file_backend_hash_routing_key: str = "file.backend.hash"
    file_backend_hash_dlq: str = "pcd.file.backend.hash.dlq"
    file_backend_hash_dlq_routing_key: str = "file.backend.hash.dlq"
    file_backend_hash_max_retries: int = 3

    # --- virus 病毒扫描 ---
    file_backend_virus_queue: str = "pcd.file.backend.virus.queue"
    file_backend_virus_routing_key: str = "file.backend.virus"
    file_backend_virus_dlq: str = "pcd.file.backend.virus.dlq"
    file_backend_virus_dlq_routing_key: str = "file.backend.virus.dlq"
    file_backend_virus_max_retries: int = 3

    # --- mark_active 标记活跃 ---
    file_backend_mark_active_queue: str = "pcd.file.backend.mark_active.queue"
    file_backend_mark_active_routing_key: str = "file.backend.mark_active"
    file_backend_mark_active_dlq: str = "pcd.file.backend.mark_active.dlq"
    file_backend_mark_active_dlq_routing_key: str = "file.backend.mark_active.dlq"
    file_backend_mark_active_max_retries: int = 3

    # ========== 文件增强处理拓扑（Enhancement Processing — 并发流水线） ==========
    # 每个增强阶段独立 exchange + queue + DLQ，可并发消费，互不阻塞

    # --- 增强处理主交换机 (DIRECT) ---
    file_enhance_exchange: str = "pcd.file.enhance.exchange"
    file_enhance_dlx: str = "pcd.file.enhance.dlx"

    # --- thumbnail 缩略图 ---
    file_enhance_thumbnail_queue: str = "pcd.file.enhance.thumbnail.queue"
    file_enhance_thumbnail_routing_key: str = "file.enhance.thumbnail"
    file_enhance_thumbnail_dlq: str = "pcd.file.enhance.thumbnail.dlq"
    file_enhance_thumbnail_dlq_routing_key: str = "file.enhance.thumbnail.dlq"
    file_enhance_thumbnail_max_retries: int = 3

    # --- transcode 视频转码 ---
    file_enhance_transcode_queue: str = "pcd.file.enhance.transcode.queue"
    file_enhance_transcode_routing_key: str = "file.enhance.transcode"
    file_enhance_transcode_dlq: str = "pcd.file.enhance.transcode.dlq"
    file_enhance_transcode_dlq_routing_key: str = "file.enhance.transcode.dlq"
    file_enhance_transcode_max_retries: int = 3

    # --- hls_transcode HLS 流媒体转码 ---
    file_enhance_hls_queue: str = "pcd.file.enhance.hls.queue"
    file_enhance_hls_routing_key: str = "file.enhance.hls"
    file_enhance_hls_dlq: str = "pcd.file.enhance.hls.dlq"
    file_enhance_hls_dlq_routing_key: str = "file.enhance.hls.dlq"
    file_enhance_hls_max_retries: int = 3

    # --- content_index 全文索引 ---
    file_enhance_index_queue: str = "pcd.file.enhance.index.queue"
    file_enhance_index_routing_key: str = "file.enhance.index"
    file_enhance_index_dlq: str = "pcd.file.enhance.index.dlq"
    file_enhance_index_dlq_routing_key: str = "file.enhance.index.dlq"
    file_enhance_index_max_retries: int = 3

    # --- office_to_pdf Office 文件转 PDF ---
    # 将 Office 文档（Word/Excel/PPT）转换为 PDF 格式，生成统一的预览资源
    # PDF 文件本身也需要生成缩略图等预览资源，走此增强阶段
    file_enhance_office_to_pdf_queue: str = "pcd.file.enhance.office_to_pdf.queue"
    file_enhance_office_to_pdf_routing_key: str = "file.enhance.office_to_pdf"
    file_enhance_office_to_pdf_dlq: str = "pcd.file.enhance.office_to_pdf.dlq"
    file_enhance_office_to_pdf_dlq_routing_key: str = "file.enhance.office_to_pdf.dlq"
    file_enhance_office_to_pdf_max_retries: int = 3

    # --- archive_parse 压缩包目录解析 ---
    # 解析压缩包文件（ZIP/RAR/7Z/ISO/TAR/GZ/BZ2等），提取目录结构信息
    # 生成 JSON 格式目录树供前端预览，不进行完整解压
    file_enhance_archive_parse_queue: str = "pcd.file.enhance.archive_parse.queue"
    file_enhance_archive_parse_routing_key: str = "file.enhance.archive_parse"
    file_enhance_archive_parse_dlq: str = "pcd.file.enhance.archive_parse.dlq"
    file_enhance_archive_parse_dlq_routing_key: str = "file.enhance.archive_parse.dlq"
    file_enhance_archive_parse_max_retries: int = 3

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
    # AUDIT FIX [7.4]（需求一-3）: DLQ 自动恢复独立计数，避免与阶段内重试相互叠加造成无限循环。
    enhance_dlq_recovery_max_attempts: int = 2
    enhance_processing_lease_seconds: int = 1800
    # 需求一-3：可选运维告警 Webhook；为空时仅保留 critical 日志和数据库台账。
    ops_alert_webhook_url: str = ""

    # --- 病毒扫描 ---
    virus_scan_enabled: bool = False
    virus_scan_fail_open: bool = False  # True=扫描器不可用时放行, False=拒绝
    quarantine_dir: str = "../Uploads/quarantine"  # 隔离区目录

    # --- 磁盘空间 ---
    min_free_disk_bytes: int = 500 * 1024 * 1024  # 合并前至少 500MB 可用

    # ========== OpenSearch 配置 ==========
    opensearch_host: str = "http://localhost:9200"
    opensearch_username: str = "admin"
    opensearch_password: str = "MySecureP@ssw0rd"
    opensearch_use_ssl: bool = False
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

    # ========== 上传会话事件交换机 & 队列（与 Spring Boot 主业务服务一致） ==========
    # --- 上传会话事件主交换机 ---
    uploads_event_exchange: str = "pcd.uploads.event.exchange"
    # --- 上传会话事件死信交换机 ---
    uploads_event_dlx: str = "pcd.uploads.event.dlx"
    # --- 上传会话事件死信队列 ---
    uploads_event_dlq: str = "pcd.uploads.event.dlq"
    uploads_event_dlq_routing_key: str = "uploads.event.dlq"
    # --- 上传会话删除事件（文件存储服务消费 → 删除物理分块文件） ---
    uploads_session_delete_queue: str = "pcd.uploads.session.delete.queue"
    uploads_session_delete_routing_key: str = "uploads.session.delete"
    # --- 上传会话已删除事件（主业务服务消费 → 释放配额） ---
    uploads_session_deleted_queue: str = "pcd.uploads.session.deleted.queue"
    uploads_session_deleted_routing_key: str = "uploads.session.deleted"

    # ========== 文件事件交换机 & 队列（与 Spring Boot 主业务服务一致） ==========
    # 文件事件由主业务服务监听，存储服务负责发布这些事件
    file_event_exchange: str = "pcd.file.event.exchange"
    file_event_dlx: str = "pcd.file.event.dlx"
    file_event_dlq: str = "pcd.file.event.dlq"
    file_event_dlq_routing_key: str = "file.event.dlq"
    file_available_queue: str = "pcd.file.available.queue"
    file_available_routing_key: str = "file.available"
    file_merge_failed_queue: str = "pcd.file.merge.failed.queue"
    file_merge_failed_routing_key: str = "file.merge.failed"
    file_scan_failed_queue: str = "pcd.file.scan.failed.queue"
    file_scan_failed_routing_key: str = "file.scan.failed"
    file_downloaded_queue: str = "pcd.file.downloaded.queue"
    file_downloaded_routing_key: str = "file.scan.file.downloaded"

    # ========== OpenAPI 文档开关 ==========
    # 生产环境设为 false 关闭 /docs /redoc /openapi.json
    enable_docs: bool = True

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
    HLS_TRANSCODE = "hls_transcode"          # HLS 流媒体转码 (多码率 + 切片)
    MARK_ACTIVE = "mark_active"
    CONTENT_INDEX = "content_index"          # 文件内容索引 (OpenSearch)
    OFFICE_TO_PDF = "office_to_pdf"          # Office 文件转 PDF 预览资源 (增强事件)
    ARCHIVE_PARSE = "archive_parse"          # 压缩包目录结构解析 (增强事件)


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
    OFFICE_TO_PDF_ERROR = "OFFICE_TO_PDF_ERROR"  # Office 文件转 PDF 失败
    ARCHIVE_PARSE_ERROR = "ARCHIVE_PARSE_ERROR"  # 压缩包目录结构解析失败
    UPLOADS_DELETE_IO_ERROR = "UPLOADS_DELETE_IO_ERROR"
    UPLOADS_SESSION_NOTIFY_BS_ERROR = "UPLOADS_SESSION_NOTIFY_BS_ERROR"
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

# Office 文档类型 — Word / Excel / PowerPoint
# 这些文件需要转换为 PDF 才能在前端统一预览
OFFICE_TYPES = frozenset({
    "application/msword",                                                          # .doc
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",     # .docx
    "application/vnd.ms-excel",                                                    # .xls
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",           # .xlsx
    "application/vnd.ms-powerpoint",                                               # .ppt
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",   # .pptx
})

# PDF 类型 — 本身已是 PDF 格式，跳过转换但需要生成缩略图等预览资源
PDF_TYPES = frozenset({"application/pdf"})

# 压缩包文件类型 — 需要解析目录结构以在前端预览
# 支持 ZIP、RAR、7Z、ISO、TAR、GZIP、BZIP2、CAB 等主流压缩/归档格式
ARCHIVE_TYPES = frozenset({
    "application/zip",                                                              # .zip
    "application/x-zip-compressed",                                                 # .zip (旧版 MIME)
    "application/x-rar-compressed",                                                 # .rar
    "application/x-rar",                                                            # .rar
    "application/x-7z-compressed",                                                  # .7z
    "application/x-iso9660-image",                                                  # .iso
    "application/x-tar",                                                            # .tar
    "application/gzip",                                                             # .gz
    "application/x-gzip",                                                           # .gz
    "application/x-bzip2",                                                          # .bz2
    "application/x-bzip",                                                           # .bz
    "application/x-xz",                                                             # .xz
    "application/x-lzip",                                                           # .lz
    "application/x-lzma",                                                           # .lzma
    "application/vnd.ms-cab-compressed",                                            # .cab
    "application/x-compress",                                                       # .Z
    "application/x-cpio",                                                           # .cpio
    "application/x-ar",                                                             # .ar
    "application/x-archive",                                                        # .a
    "application/x-lha",                                                            # .lha
    "application/x-lzh",                                                            # .lzh
    "application/x-ace",                                                            # .ace
    "application/x-zoo",                                                            # .zoo
    "application/x-alz",                                                            # .alz
    "application/x-arj",                                                            # .arj
    "application/x-arc",                                                            # .arc
    "application/x-wim",                                                            # .wim
    "application/x-apple-diskimage",                                                # .dmg
    "application/x-raw-disk-image",                                                 # .img
})

REDIS_BACKEND_MASTER_KEY = "backend:task:{backend_task_id}:master"
REDIS_BACKEND_EVENT_KEY = "backend:task:{backend_task_id}:{stage}"

MASTER_TASK_TTL = 30000
EVENT_STATUS_TTL = 30000

REDIS_ENHANCE_EVENT_KEY = "enhance:task:{enhance_task_id}:{stage}"
REDIS_ENHANCE_MASTER_KEY = "enhance:task:{enhance_task_id}:master"

# ========== 任务流水线拆分 ==========
# 旧版流水线（保留兼容）
TASK_PIPELINE = [
    TaskTypes.MERGE,
    TaskTypes.HASH_CALCULATE,
    TaskTypes.VIRUS_SCAN,
    TaskTypes.MARK_ACTIVE,
    TaskTypes.THUMBNAIL,
    TaskTypes.VIDEO_TRANSCODE,
    TaskTypes.HLS_TRANSCODE,
    TaskTypes.CONTENT_INDEX,
]

# 新版——后台处理流水线（顺序执行，影响文件可用性）
BACKEND_PIPELINE = [
    TaskTypes.MERGE,
    TaskTypes.HASH_CALCULATE,
    TaskTypes.VIRUS_SCAN,
    TaskTypes.MARK_ACTIVE,
]

# 新版——增强处理流水线（并发执行，不影响文件可用性）
ENHANCE_PIPELINE = [
    TaskTypes.THUMBNAIL,
    TaskTypes.VIDEO_TRANSCODE,
    TaskTypes.HLS_TRANSCODE,
    TaskTypes.CONTENT_INDEX,
    TaskTypes.OFFICE_TO_PDF,       # Office 文件转 PDF 预览资源
    TaskTypes.ARCHIVE_PARSE,       # 压缩包目录结构解析
]

# 后台处理阶段 → 下一阶段的映射
BACKEND_NEXT_STAGE = {
    TaskTypes.MERGE: TaskTypes.HASH_CALCULATE,
    TaskTypes.HASH_CALCULATE: TaskTypes.VIRUS_SCAN,
    TaskTypes.VIRUS_SCAN: TaskTypes.MARK_ACTIVE,
    TaskTypes.MARK_ACTIVE: None,  # 流水线终点，触发增强事件
}

# 后台处理阶段 → routing_key 映射
BACKEND_STAGE_ROUTING_KEY = {
    TaskTypes.MERGE: "file.backend.merge",
    TaskTypes.HASH_CALCULATE: "file.backend.hash",
    TaskTypes.VIRUS_SCAN: "file.backend.virus",
    TaskTypes.MARK_ACTIVE: "file.backend.mark_active",
}

# 增强处理阶段 → routing_key 映射
ENHANCE_STAGE_ROUTING_KEY = {
    TaskTypes.THUMBNAIL: "file.enhance.thumbnail",
    TaskTypes.VIDEO_TRANSCODE: "file.enhance.transcode",
    TaskTypes.HLS_TRANSCODE: "file.enhance.hls",
    TaskTypes.CONTENT_INDEX: "file.enhance.index",
    TaskTypes.OFFICE_TO_PDF: "file.enhance.office_to_pdf",
    TaskTypes.ARCHIVE_PARSE: "file.enhance.archive_parse",
}

# 根据文件类型判断需要触发的增强阶段
def get_enhance_stages(file_type: str) -> list[str]:
    stages = []
    if file_type in IMAGE_TYPES:
        stages.append(TaskTypes.THUMBNAIL)
    if file_type in VIDEO_TYPES:
        stages.append(TaskTypes.THUMBNAIL)
        stages.append(TaskTypes.VIDEO_TRANSCODE)
        stages.append(TaskTypes.HLS_TRANSCODE)
    # Office 文件 → 触发 PDF 转换增强阶段
    # 将 Word/Excel/PPT 转换为 PDF 格式，生成统一的预览资源
    if file_type in OFFICE_TYPES:
        stages.append(TaskTypes.OFFICE_TO_PDF)
    # PDF 文件 → 生成缩略图等预览资源（PDF 本身不需要转换，但需要缩略图）
    # 注意：PDF 文件的缩略图由 ThumbnailPipeline 处理（支持 PDF 第一页截图）
    # 此处仅触发 OFFICE_TO_PDF 阶段，PDF 文件无需转换，走缩略图 + 索引
    if file_type in PDF_TYPES:
        stages.append(TaskTypes.THUMBNAIL)
    # 文本类文件可索引
    if file_type.startswith(("text/", "application/pdf", "application/msword",
                              "application/vnd.openxmlformats", "application/vnd.ms-",
                              "application/json", "application/xml")):
        stages.append(TaskTypes.CONTENT_INDEX)
    # 压缩包文件 → 触发目录结构解析增强阶段
    # 解析 ZIP/RAR/7Z/ISO 等压缩包格式，提取目录结构 JSON 供前端预览
    if file_type in ARCHIVE_TYPES:
        stages.append(TaskTypes.ARCHIVE_PARSE)
    return stages
