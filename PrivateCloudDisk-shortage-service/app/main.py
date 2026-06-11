"""
PrivateCloudDisk 文件服务主入口
企业级 FastAPI 应用
"""
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI

from app.api.v1.router import api_router
from app.middleware.timing import add_process_time_header
from app.core.redis_client import redis_client
from app.core.logging_config import setup_logging, get_logger
from core.rabbitmq import rabbitmq_service
from core.config import settings
from core.consumers import on_file_process_message, on_file_delete_message, on_dead_letter_message, on_content_index_message


# 配置日志系统
setup_logging(level=logging.INFO, enable_color=True)
logger = get_logger("app.main")


# ==================== 应用生命周期管理 ====================

async def start_rabbitmq_consumers():
    """
    启动 RabbitMQ 消费者

    功能说明：
    启动文件处理相关的消息消费者，监听消息队列并处理异步任务。

    启动的消费者：
    1. 文件处理消费者 (file_process_queue)：
       处理文件合并、Hash 计算、病毒扫描、缩略图生成、视频转码等流水线任务
    2. 文件删除消费者 (file_delete_queue)：
       处理文件彻底删除任务
    3. 死信文件处理消费者 (file_process_dlq)：
       处理重试耗尽后进入死信队列的消息，根据 failure_reason 执行不同补偿策略
    4. 死信文件删除消费者 (file_delete_dlq)：
       处理删除失败进入死信队列的消息
    5. 安全隔离消费者 (security_quarantine_queue)：
       处理病毒/木马文件隔离事件
    6. 内容索引消费者 (content_index_queue)：
       抽取文件内容并写入 OpenSearch 索引

    消息队列架构:
    ┌──────────────────────────────────────────────────┐
    │  pcd.file.process.exchange (DIRECT)              │
    │  ├── pcd.file.process.queue ── DLX ── DLQ        │
    │  │   消费者: on_file_process_message              │
    │  ├── pcd.file.process.dlq                        │
    │  │   消费者: on_dead_letter_message               │
    │  └── pcd.security.quarantine.queue               │
    │      消费者: on_dead_letter_message (安全事件)     │
    │                                                   │
    │  pcd.file.delete.exchange (DIRECT)               │
    │  ├── pcd.file.delete.queue ── DLX ── DLQ         │
    │  │   消费者: on_file_delete_message               │
    │  └── pcd.file.delete.dlq                         │
    │      消费者: on_dead_letter_message               │
    │                                                   │
    │  pcd.content.index.exchange (DIRECT)             │
    │  ├── pcd.content.index.queue ── DLX ── DLQ       │
    │  │   消费者: on_content_index_message             │
    │  └── pcd.content.index.dlq                       │
    │      消费者: on_dead_letter_message               │
    └──────────────────────────────────────────────────┘
    """
    logger.info("🚀 开始启动 RabbitMQ 消费者...")

    # 连接 RabbitMQ
    logger.info("🔗 连接 RabbitMQ 服务...")
    await rabbitmq_service.connect()
    logger.info("✅ RabbitMQ 连接成功")

    # 1. 文件处理主消费者
    logger.info(f"🔄 启动文件处理消费者 - queue: {settings.file_process_queue}")
    await rabbitmq_service.consume(
        settings.file_process_queue,
        on_file_process_message,
    )
    logger.info(f"✅ 文件处理消费者启动成功")

    # 2. 文件删除主消费者
    logger.info(f"🔄 启动文件删除消费者 - queue: {settings.file_delete_queue}")
    await rabbitmq_service.consume(
        settings.file_delete_queue,
        on_file_delete_message,
    )
    logger.info(f"✅ 文件删除消费者启动成功")

    # 3. 文件处理死信消费者 (DLQ)
    logger.info(f"🔄 启动文件处理死信消费者 - queue: {settings.file_process_dlq}")
    await rabbitmq_service.consume(
        settings.file_process_dlq,
        on_dead_letter_message,
    )
    logger.info(f"✅ 文件处理死信消费者启动成功")

    # 4. 文件删除死信消费者 (DLQ)
    logger.info(f"🔄 启动文件删除死信消费者 - queue: {settings.file_delete_dlq}")
    await rabbitmq_service.consume(
        settings.file_delete_dlq,
        on_dead_letter_message,
    )
    logger.info(f"✅ 文件删除死信消费者启动成功")

    # 5. 安全隔离消费者
    logger.info(f"🔄 启动安全隔离消费者 - queue: {settings.security_quarantine_queue}")
    await rabbitmq_service.consume(
        settings.security_quarantine_queue,
        on_dead_letter_message,
    )
    logger.info(f"✅ 安全隔离消费者启动成功")

    # 6. 内容索引消费者
    logger.info(f"🔄 启动内容索引消费者 - queue: {settings.content_index_queue}")
    await rabbitmq_service.consume(
        settings.content_index_queue,
        on_content_index_message,
    )
    logger.info(f"✅ 内容索引消费者启动成功")

    # 7. 内容索引死信消费者
    logger.info(f"🔄 启动内容索引死信消费者 - queue: {settings.content_index_dlq}")
    await rabbitmq_service.consume(
        settings.content_index_dlq,
        on_dead_letter_message,
    )
    logger.info(f"✅ 内容索引死信消费者启动成功")

    # 8. 初始化 OpenSearch 索引
    from core.search.opensearch_client import ensure_indices
    logger.info("🔍 初始化 OpenSearch 索引...")
    await ensure_indices()
    logger.info("✅ OpenSearch 索引初始化完成")

    logger.info("🎉 RabbitMQ 消费者全部启动完成")


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
    logger.info("=" * 60)
    logger.info("📦 PrivateCloudDisk 文件服务启动中...")
    logger.info("=" * 60)
    
    try:
        try:
            await start_rabbitmq_consumers()
            logger.info("✅ 所有服务初始化完成")
        except Exception as e:
            logger.warning(f"⚠️ RabbitMQ 消费者启动失败 (HTTP 服务仍可用): {e}")
            logger.info("✅ HTTP 服务初始化完成 (无消息队列)")
        logger.info("=" * 60)
        yield
    finally:
        # 关闭阶段
        logger.info("=" * 60)
        logger.info("🛑 PrivateCloudDisk 文件服务关闭中...")
        logger.info("=" * 60)
        
        logger.info("🔌 关闭 Redis 连接...")
        await redis_client.close()
        logger.info("✅ Redis 连接已关闭")
        
        logger.info("🔌 关闭 RabbitMQ 连接...")
        await rabbitmq_service.close()
        logger.info("✅ RabbitMQ 连接已关闭")
        
        logger.info("=" * 60)
        logger.info("🛑 PrivateCloudDisk 文件服务已关闭")
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
