"""
AI Processing Service - 任务管理 API

提供:
- GET  /api/v1/tasks/{task_id} - 查询任务状态
- GET  /api/v1/tasks/{file_id}/results - 查询文件 AI 处理结果
- POST /api/v1/tasks/reprocess - 手动触发重新处理
- GET  /api/v1/tasks/{user_id}/recommendations - 查询用户推荐
- GET  /api/v1/tasks/{user_id}/face-clusters - 查询用户人脸聚类
"""
from __future__ import annotations
import logging
from typing import Optional

from fastapi import APIRouter, Query, HTTPException

from app.core.config import settings
from app.core.database.repository import (
    AITagRepository, OCRResultRepository, SummaryRepository,
    RecommendationRepository, TaskLogRepository,
)

logger = logging.getLogger("ai_service.api.tasks")

router = APIRouter(prefix="/tasks", tags=["tasks"])

_tag_repo = AITagRepository()
_ocr_repo = OCRResultRepository()
_summary_repo = SummaryRepository()
_rec_repo = RecommendationRepository()
_task_log = TaskLogRepository()


@router.get("/{file_id}/results")
async def get_file_ai_results(file_id: str):
    """获取文件的所有 AI 处理结果"""
    try:
        tags = await _tag_repo.get_tags_by_file(file_id)
        return {
            "file_id": file_id,
            "tags": tags,
            "tag_count": len(tags),
        }
    except Exception as e:
        logger.error(f"查询文件 AI 结果失败: file_id={file_id}, error={e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{user_id}/recommendations")
async def get_user_recommendations(
    user_id: str,
    limit: int = Query(default=20, le=100),
):
    """获取用户个性化推荐"""
    try:
        # 推荐数据存储在 pcd_ai_recommendations 表中
        # 使用原生 SQL 查询
        from app.core.database.connection import db_manager
        sql = """
            SELECT file_id, score, reason, reason_type, created_at
            FROM pcd_ai_recommendations
            WHERE user_id = :user_id
            ORDER BY score DESC
            LIMIT :limit
        """
        results = await db_manager.fetch_all(sql, {
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
            "limit": limit,
        })

        return {
            "user_id": user_id,
            "recommendations": results,
            "count": len(results),
        }
    except Exception as e:
        logger.error(f"查询用户推荐失败: user_id={user_id}, error={e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{user_id}/face-clusters")
async def get_user_face_clusters(user_id: str):
    """获取用户的人脸聚类结果"""
    try:
        from app.core.database.connection import db_manager
        sql = """
            SELECT c.cluster_id, c.cluster_label, c.representative_file_id,
                   c.face_count, c.file_count, c.created_at
            FROM pcd_ai_face_clusters c
            WHERE c.user_id = :user_id
            ORDER BY c.face_count DESC
        """
        clusters = await db_manager.fetch_all(sql, {
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
        })

        return {
            "user_id": user_id,
            "clusters": clusters,
            "count": len(clusters),
        }
    except Exception as e:
        logger.error(f"查询用户人脸聚类失败: user_id={user_id}, error={e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/status/{task_id}")
async def get_task_status(task_id: str):
    """查询 AI 任务执行状态"""
    try:
        from app.core.database.connection import db_manager
        sql = """
            SELECT task_id, file_id, user_id, task_type, status,
                   error_message, processing_time_ms, retry_count, created_at
            FROM pcd_ai_task_logs
            WHERE task_id = :task_id
            ORDER BY created_at DESC
        """
        logs = await db_manager.fetch_all(sql, {"task_id": task_id})

        return {
            "task_id": task_id,
            "logs": logs,
            "count": len(logs),
        }
    except Exception as e:
        logger.error(f"查询任务状态失败: task_id={task_id}, error={e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/reprocess")
async def reprocess_file(
    file_id: str = Query(..., description="文件 ID"),
    user_id: str = Query(..., description="用户 ID"),
    enabled_tasks: Optional[str] = Query(
        default=None,
        description="逗号分隔的任务列表，留空表示全部"
    ),
):
    """
    手动触发文件重新 AI 处理

    发布消息到 AI 处理队列。
    """
    from app.core.rabbitmq import rabbitmq_service
    from app.core.events.ai_process_event import AIProcessEvent

    try:
        # 清理旧的 AI 标签
        await _tag_repo.delete_tags_by_file(file_id)

        # 构建事件
        tasks_list = enabled_tasks.split(",") if enabled_tasks else []

        event = AIProcessEvent(
            message_id=AIProcessEvent.generate_message_id(),
            file_id=file_id,
            user_id=user_id,
            enabled_tasks=tasks_list,
        )

        await rabbitmq_service.publish_message(
            exchange_name=settings.ai_process_exchange,
            routing_key=settings.ai_process_routing_key,
            message=event.to_dict(),
        )

        return {
            "status": "accepted",
            "message_id": event.message_id,
            "file_id": file_id,
            "enabled_tasks": tasks_list or "all",
        }

    except Exception as e:
        logger.error(f"重新处理请求失败: file_id={file_id}, error={e}")
        raise HTTPException(status_code=500, detail=str(e))