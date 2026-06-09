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
    
    # RabbitMQ配置
    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = 5672
    rabbitmq_username: str = "guest"
    rabbitmq_password: str = "guest"
    rabbitmq_vhost: str = "/"
    
    # 文件处理队列配置
    file_process_queue: str = "pcd.file.process.queue"
    file_process_exchange: str = "pcd.file.process.exchange"
    file_process_routing_key: str = "file.process"
    
    file_delete_queue: str = "pcd.file.delete.queue"
    file_delete_exchange: str = "pcd.file.delete.exchange"
    file_delete_routing_key: str = "file.delete"

    class Config:
        env_file = ".env"

settings = Settings()

# 任务类型常量
class TaskTypes:
    MERGE = "merge"
    HASH_CALCULATE = "hash_calculate"
    VIRUS_SCAN = "virus_scan"
    THUMBNAIL = "thumbnail"
    VIDEO_TRANSCODE = "video_transcode"
    MARK_ACTIVE = "mark_active"

# 任务状态常量
class TaskStatus:
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"