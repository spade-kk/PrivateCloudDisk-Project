"""
PrivateCloudDisk 文件服务主入口
企业级 FastAPI 应用

注意：消息消费者已独立为 Worker 进程（worker.py），
FastAPI 主进程仅负责 HTTP API，不运行消费者。
"""
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI

from app.api.v1.router import api_router
from app.middleware.timing import add_process_time_header
from app.core.redis_client import redis_client
from app.core.logging_config import setup_logging, get_logger
from core.rabbitmq import rabbitmq_service


# 配置日志系统
setup_logging(level=logging.INFO, enable_color=True)
logger = get_logger("app.main")


# ==================== 应用生命周期管理 ====================


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
    """
    logger.info("=" * 60)
    logger.info("PrivateCloudDisk 文件服务启动中...")
    logger.info("=" * 60)

    try:
        # 连接 RabbitMQ（仅用于发布消息，消费者由 Worker 处理）
        await rabbitmq_service.connect()
        logger.info("RabbitMQ 连接成功 (发布模式)")
    except Exception as e:
        logger.warning(f"RabbitMQ 连接失败 (HTTP 服务仍可用): {e}")

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


# ==================== 创建 FastAPI 应用实例 ====================

app = FastAPI(
    title="PrivateCloudDisk 文件服务",
    description="""
    企业级文件服务 API
    
    功能模块：
    - 文件上传：分片上传、断点续传
    - 文件下载：Range 请求、断点续传
    - 缩略图生成：libvips 高性能图片处理
    - 异步处理：文件合并、病毒扫描、视频转码
    - 任务追踪：任务状态查询
    
    技术特点：
    - 多维度限流：总请求次数、每秒速率、并发连接数
    - 操作凭证：JWT 临时凭证，支持撤销
    - Redis 缓存：缩略图缓存、任务状态缓存
    - RabbitMQ：异步任务队列，顺序处理
    """,
    version="1.0.0",
    lifespan=lifespan
)


# ==================== 注册中间件 ====================

app.middleware("http")(add_process_time_header)


# ==================== 注册路由 ====================

app.include_router(api_router)


# ==================== 服务端启动指令 ====================
# source .venv/bin/activate
# uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
