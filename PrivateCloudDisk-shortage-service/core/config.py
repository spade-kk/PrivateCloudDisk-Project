from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    redis_url: str = "redis://localhost:6379/0"
    file_upload_dir: str = "../Uploads"
    business_service_url: str = "http://127.0.0.1:8081"
    private_key_path: str = "./private_key.pem"
    public_key_path: str = "./public_key.pem"
    max_concurrent: int = 3
    operation_token_expire_seconds: int = 3600
    max_requests_per_operation_token: int = 300
    rate_per_sec: int = 10
    max_range_bytes: int = 100 * 1024 * 1024
    thumbnail_ttl: int = 3600

    class Config:
        env_file = ".env"

settings = Settings()