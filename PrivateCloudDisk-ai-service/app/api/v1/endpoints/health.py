"""
AI Processing Service - 健康检查 API

提供:
- /health - 服务健康检查
- /health/ready - 就绪检查 (含依赖服务状态)
- /health/live - 存活检查
"""
from __future__ import annotations
import logging
from datetime import datetime, timezone

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.core.database.connection import db_manager
from app.core.rabbitmq import rabbitmq_service
from app.core.services.model_manager import model_manager

logger = logging.getLogger("ai_service.api.health")

router = APIRouter(tags=["health"])


@router.get("/health")
async def health_check():
    """基础健康检查"""
    return {
        "status": "ok",
        "service": settings.ai_service_name,
        "version": "1.0.0",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


@router.get("/health/ready")
async def readiness_check():
    """
    就绪检查

    检查所有依赖服务是否可用:
    - MySQL
    - RabbitMQ
    - Redis (可选)
    - MinIO (可选)
    """
    checks = {}

    # MySQL 检查
    try:
        await db_manager.execute("SELECT 1", {})
        checks["mysql"] = {"status": "ok"}
    except Exception as e:
        checks["mysql"] = {"status": "error", "message": str(e)}

    # RabbitMQ 检查
    try:
        if rabbitmq_service._connection and not rabbitmq_service._connection.is_closed:
            checks["rabbitmq"] = {"status": "ok"}
        else:
            checks["rabbitmq"] = {"status": "error", "message": "未连接"}
    except Exception as e:
        checks["rabbitmq"] = {"status": "error", "message": str(e)}

    # 模型管理器检查
    loaded_models = model_manager.get_loaded_models()
    checks["models"] = {
        "status": "ok",
        "device": model_manager.device,
        "loaded_count": len(loaded_models),
        "loaded_models": loaded_models,
    }

    # 判断整体状态
    all_ok = all(
        v.get("status") == "ok"
        for v in checks.values()
        if isinstance(v, dict)
    )

    status_code = 200 if all_ok else 503

    return JSONResponse(
        content={
            "status": "ready" if all_ok else "not_ready",
            "checks": checks,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        },
        status_code=status_code,
    )


@router.get("/health/live")
async def liveness_check():
    """存活检查 (Kubernetes liveness probe)"""
    return {
        "status": "alive",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }