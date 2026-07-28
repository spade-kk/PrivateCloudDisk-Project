"""
PrivateCloudDisk 文件服务主入口
企业级 FastAPI 应用

注意：消息消费者已独立为 Worker 进程（worker.py），
FastAPI 主进程仅负责 HTTP API，不运行消费者。
"""
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi

from app.api.v1.router import api_router
from app.middleware.timing import add_process_time_header
from app.core.redis_client import redis_client
from app.core.logging_config import setup_logging, get_logger
from core.rabbitmq import rabbitmq_service
from core.config import settings
from app.db.database import close_database, init_database


# 配置日志系统
setup_logging(level=logging.INFO, enable_color=True)
logger = get_logger("app.main")


# ==================== OpenAPI 文档开关 ====================
# 通过环境变量 ENABLE_DOCS 控制，生产环境默认关闭
ENABLE_DOCS = settings.enable_docs

if ENABLE_DOCS:
    logger.info("OpenAPI 文档已启用: /docs /redoc /openapi.json")
else:
    logger.info("OpenAPI 文档已关闭（生产模式）")


# ==================== OpenAPI 标签元数据 ====================

TAGS_METADATA = [
    {
        "name": "操作凭证",
        "description": "文件操作凭证的申请与销毁，支持 download / preview / stream 三种操作类型。\n\n"
                       "申请凭证后返回 JWT Token，后续操作需携带此 Token 进行鉴权。",
        "externalDocs": {
            "description": "操作凭证设计文档",
            "url": "https://privateclouddisk.local/docs/operation-tokens",
        },
    },
    {
        "name": "文件上传",
        "description": "文件分片上传接口，支持断点续传。\n\n"
                       "**流程：** 创建上传会话 → 上传分片 → 触发合并 → 查询任务状态。\n"
                       "分片大小建议 5MB~10MB，单个文件最大支持 5GB。",
    },
    {
        "name": "文件下载",
        "description": "基于 Opaque Token 的安全文件下载接口。\n\n"
                       "先通过下载授权接口获取 grant_token，再携带 token 进行 Range 下载。\n"
                       "支持并发控制、速率限制、可撤销授权。",
    },
    {
        "name": "文件操作",
        "description": "文件操作接口，包括文件下载和缩略图获取。\n\n"
                       "缩略图基于 libvips 高性能处理，支持 JPEG/PNG/WebP 格式。",
    },
    {
        "name": "任务状态",
        "description": "异步任务状态查询接口。\n\n"
                       "可查询合并、哈希计算、病毒扫描、缩略图生成、视频转码等任务进度。\n"
                       "任务状态包括：pending / processing / completed / failed。",
    },
    {
        "name": "视频流媒体",
        "description": "HLS 视频流媒体播放接口。\n\n"
                       "提供多码率自适应 HLS 流媒体播放，支持 360p/480p/720p/1080p 分辨率。\n"
                       "包含流信息查询、Token 鉴权、m3u8 播放列表和 TS 分片获取。",
    },
]


# ==================== 应用生命周期管理 ====================


def _init_storage():
    """初始化文件存储层"""
    from core.storage.factory import create_storage

    if settings.storage_type == "minio":
        create_storage(
            storage_type="minio",
            endpoint=settings.minio_endpoint,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            bucket=settings.minio_bucket,
            secure=settings.minio_secure,
            base_dir=settings.file_upload_dir,
        )
        logger.info(f"文件存储层初始化完成: MinIO ({settings.minio_endpoint}/{settings.minio_bucket})")
    else:
        create_storage(
            storage_type="localstorage",
            base_dir=settings.file_upload_dir,
        )
        logger.info(f"文件存储层初始化完成: LocalStorage ({settings.file_upload_dir})")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    应用生命周期管理

    启动阶段：
    1. 连接 RabbitMQ（仅用于发布消息，不启动消费者）
    2. 消费者由独立 Worker 进程（worker.py）处理

    关闭阶段：
    1. 关闭 Redis 连接
    2. 关闭 RabbitMQ 连接
    3. 关闭 OpenSearch 连接
    """
    logger.info("=" * 60)
    logger.info("PrivateCloudDisk 文件服务启动中...")
    logger.info("=" * 60)

    try:
        # AUDIT FIX [7.4]: HTTP 进程启动时建立预览资源数据库连接池。
        await init_database()
        # 连接 RabbitMQ（仅用于发布消息，消费者由 Worker 处理）
        await rabbitmq_service.connect()
        logger.info("RabbitMQ 连接成功 (发布模式)")
    except Exception as e:
        logger.warning(f"RabbitMQ 连接失败 (HTTP 服务仍可用): {e}")

    # 初始化文件存储层
    #_init_storage()

    logger.info("HTTP 服务初始化完成")
    logger.info("=" * 60)

    yield

    # 关闭阶段
    logger.info("=" * 60)
    logger.info("PrivateCloudDisk 文件服务关闭中...")
    logger.info("=" * 60)

    logger.info("关闭 Redis 连接...")
    await redis_client.close()
    logger.info("Redis 连接已关闭")

    logger.info("关闭 MySQL 连接池...")
    await close_database()
    logger.info("MySQL 连接池已关闭")

    logger.info("关闭 OpenSearch 连接...")
    try:
        from core.search.opensearch_client import close_opensearch_client
        await close_opensearch_client()
    except Exception as e:
        logger.warning(f"关闭 OpenSearch 失败: {e}")

    logger.info("关闭 RabbitMQ 连接...")
    await rabbitmq_service.close()
    logger.info("RabbitMQ 连接已关闭")

    logger.info("=" * 60)
    logger.info("PrivateCloudDisk 文件服务已关闭")
    logger.info("=" * 60)


# ==================== 自定义 OpenAPI Schema（安全方案注入） ====================

def custom_openapi():
    """
    生成自定义 OpenAPI Schema，注入安全方案定义。

    安全方案：
    - X-User-Id: API Key（Header），所有业务接口必填
    - BearerAuth: JWT Bearer Token，操作凭证接口使用
    """
    if app.openapi_schema:
        return app.openapi_schema

    openapi_schema = get_openapi(
        title=app.title,
        version=app.version,
        description=app.description,
        routes=app.routes,
        tags=app.openapi_tags,
        servers=app.servers,
    )

    # 注入安全方案定义
    openapi_schema["components"]["securitySchemes"] = {
        "X-User-Id": {
            "type": "apiKey",
            "in": "header",
            "name": "X-User-Id",
            "description": "用户 UUID，所有业务接口必填。用于标识请求用户身份。",
        },
        "BearerAuth": {
            "type": "http",
            "scheme": "bearer",
            "bearerFormat": "JWT",
            "description": "JWT 操作凭证 Token。通过 /api/v1/operation-tokens/init 申请获取。",
        },
    }

    # 全局安全要求（Swagger UI 中"Authorize"按钮可见）
    openapi_schema["security"] = [
        {"X-User-Id": []},
        {"BearerAuth": []},
    ]

    app.openapi_schema = openapi_schema
    return app.openapi_schema


# ==================== 创建 FastAPI 应用实例 ====================

app = FastAPI(
    title="PrivateCloudDisk 文件服务",
    description="""
## 企业级文件服务 API

提供文件上传、下载、缩略图生成、异步任务处理等核心能力。

### 功能模块

| 模块 | 说明 |
|------|------|
| 操作凭证 | 文件操作 Token 的申请与销毁，支持 download / preview / stream |
| 文件上传 | 分片上传、断点续传，单文件最大 5GB |
| 文件下载 | Opaque Token 安全下载，Range 请求、并发控制 |
| 文件操作 | 缩略图获取（libvips 高性能处理） |
| 任务状态 | 异步任务进度查询（合并/哈希/扫描/转码/缩略图） |

### 技术架构

- **多维度限流**：总请求次数、每秒速率、并发连接数三层防护
- **操作凭证**：JWT 临时凭证，支持细粒度权限控制和即时撤销
- **Redis 缓存**：缩略图缓存、任务状态缓存、凭证黑名单
- **RabbitMQ**：异步任务队列，保证消息顺序处理与死信重试
- **OpenSearch**：文件内容索引与全文搜索
""",
    version="1.0.0",
    lifespan=lifespan,
    # 文档端点：生产环境通过 ENABLE_DOCS=false 关闭
    docs_url="/docs" if ENABLE_DOCS else None,
    redoc_url="/redoc" if ENABLE_DOCS else None,
    openapi_url="/openapi.json" if ENABLE_DOCS else None,
    # 标签元数据
    openapi_tags=TAGS_METADATA,
    # 多环境服务器列表
    servers=[
        {"url": "http://localhost:8000", "description": "本地开发环境"},
        {"url": "http://file-service-backend:8000", "description": "Docker 内部网络"},
    ],
    # 联系方式 & 许可证
    contact={
        "name": "PrivateCloudDisk Team",
        "email": "dev@privateclouddisk.local",
    },
    license_info={
        "name": "Internal Use Only",
        "url": "https://privateclouddisk.local/license",
    },
)

# 注入自定义 OpenAPI Schema（含安全方案）
app.openapi = custom_openapi


# ==================== 注册中间件 ====================

app.middleware("http")(add_process_time_header)


# ==================== 注册路由 ====================

app.include_router(api_router)


# ==================== 健康检查端点 ====================

@app.get("/health", tags=["系统"], include_in_schema=ENABLE_DOCS)
async def health_check():
    """
    健康检查端点

    返回服务运行状态，供 Docker healthcheck 和负载均衡器使用。
    """
    return {
        "status": "healthy",
        "service": "PrivateCloudDisk File Service",
        "version": "1.0.0",
        "docs_enabled": ENABLE_DOCS,
    }


# ==================== 服务端启动指令 ====================
# source .venv/bin/activate
# uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
