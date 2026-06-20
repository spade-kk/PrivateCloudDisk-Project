"""
PrivateCloudDisk AI Processing Service - FastAPI 应用入口

负责:
- HTTP API 服务 (健康检查、任务管理、结果查询)
- 生命周期管理 (启动/关闭 RabbitMQ、MySQL 连接)
- OpenAPI 文档 (开发环境)

与 Worker 进程分离:
- API 服务 (main.py): 处理 HTTP 请求，查询 AI 结果
- Worker 进程 (worker.py): 消费 RabbitMQ，执行 AI 推理
"""
from __future__ import annotations
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.core.logging_config import setup_logging
from app.core.database.connection import db_manager
from app.core.rabbitmq import rabbitmq_service

logger = logging.getLogger("ai_service.main")


# =============================================================================
# 应用生命周期
# =============================================================================
@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    # ---- 启动 ----
    logger.info("=" * 60)
    logger.info(f"  {settings.ai_service_name} 启动中...")
    logger.info(f"  Host: {settings.ai_service_host}:{settings.ai_service_port}")
    logger.info(f"  Device: {settings.ai_inference_device}")
    logger.info(f"  Model Dir: {settings.model_dir}")
    logger.info("=" * 60)

    # 连接数据库
    try:
        await db_manager.connect()
        logger.info("MySQL 连接成功")
    except Exception as e:
        logger.error(f"MySQL 连接失败: {e}")
        raise

    # 连接 RabbitMQ (API 服务仅声明拓扑，不消费)
    try:
        await rabbitmq_service.connect()
        logger.info("RabbitMQ 连接成功")
    except Exception as e:
        logger.warning(f"RabbitMQ 连接失败 (API 服务可降级): {e}")

    yield

    # ---- 关闭 ----
    logger.info(f"{settings.ai_service_name} 关闭中...")

    try:
        await rabbitmq_service.close()
    except Exception:
        pass

    try:
        await db_manager.close()
    except Exception:
        pass

    logger.info(f"{settings.ai_service_name} 已关闭")


# =============================================================================
# 创建 FastAPI 应用
# =============================================================================
app = FastAPI(
    title=settings.ai_service_name,
    description="PrivateCloudDisk AI Processing Service - 智能文件处理微服务",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs" if settings.enable_docs else None,
    redoc_url="/redoc" if settings.enable_docs else None,
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# =============================================================================
# 注册路由
# =============================================================================
from app.api.v1.endpoints.health import router as health_router
from app.api.v1.endpoints.tasks import router as tasks_router

app.include_router(health_router)
app.include_router(tasks_router, prefix="/api/v1")


# =============================================================================
# 根路径
# =============================================================================
@app.get("/")
async def root():
    """根路径 - 服务信息"""
    from datetime import datetime, timezone
    from app.core.services.model_manager import model_manager

    return {
        "service": settings.ai_service_name,
        "version": "1.0.0",
        "docs": "/docs",
        "health": "/health",
        "device": model_manager.device,
        "gpu_available": model_manager.is_gpu_available(),
        "loaded_models": model_manager.get_loaded_models(),
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


# =============================================================================
# 独立运行
# =============================================================================
if __name__ == "__main__":
    import uvicorn

    setup_logging(level=logging.INFO)

    uvicorn.run(
        "app.main:app",
        host=settings.ai_service_host,
        port=settings.ai_service_port,
        reload=False,
        log_level=settings.worker_log_level.lower(),
    )