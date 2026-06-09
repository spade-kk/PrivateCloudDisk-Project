"""
PrivateCloudDisk 文件服务主入口
企业级 FastAPI 应用
"""
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI

from app.api.v1.router import api_router
from app.middleware.timing import add_process_time_header
from core.redis_client import redis_client
from core.rabbitmq import rabbitmq_service
from core.config import settings
from core.consumers.file_process_consumer import FileProcessConsumer
from core.consumers.file_delete_consumer import FileDeleteConsumer


# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


# ==================== 应用生命周期管理 ====================

async def start_rabbitmq_consumers():
    """
    启动 RabbitMQ 消费者
    
    功能说明：
    启动文件处理相关的消息消费者，监听消息队列并处理异步任务。
    
    启动的消费者：
    1. 文件处理消费者：处理文件合并、病毒扫描、缩略图生成、视频转码等任务
    2. 文件删除消费者：处理文件彻底删除任务
    
    消息队列：
    - file_process_queue: 文件处理队列
    - file_delete_queue: 文件删除队列
    """
    await rabbitmq_service.connect()
    
    # 启动文件处理消费者
    await rabbitmq_service.consume(
        settings.file_process_queue,
        FileProcessConsumer.process_message
    )
    
    # 启动文件删除消费者
    await rabbitmq_service.consume(
        settings.file_delete_queue,
        FileDeleteConsumer.process_message
    )
    
    logger.info("RabbitMQ消费者已启动")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    应用生命周期管理
    
    功能说明：
    管理 FastAPI 应用的启动和关闭生命周期，负责资源的初始化和清理。
    
    启动阶段：
    1. 启动 RabbitMQ 消费者
    
    关闭阶段：
    1. 关闭 Redis 连接
    2. 关闭 RabbitMQ 连接
    
    Args:
        app: FastAPI 应用实例
    
    Yields:
        None: 应用运行期间
    """
    # 启动阶段
    await start_rabbitmq_consumers()
    yield
    # 关闭阶段
    await redis_client.close()
    await rabbitmq_service.close()


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
