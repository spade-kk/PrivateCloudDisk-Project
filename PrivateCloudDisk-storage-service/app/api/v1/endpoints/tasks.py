"""
任务状态查询 API 端点
提供后台文件处理任务状态查询接口（仅查询后台处理事件，不包含增强事件）

Redis 键结构:
  - 总任务: backend:task:{backend_task_id}:master (Hash)
    字段: status, current_stage, file_id, user_id, file_name, created_at, updated_at
  - 事件状态: backend:task:{backend_task_id}:stage:{stage} (String)
    值: "processing" | "completed" | "failed"
"""
from fastapi import APIRouter, Header, HTTPException, status
from fastapi.responses import JSONResponse

from core.config import (
    TaskTypes, TaskStatus,
    REDIS_BACKEND_MASTER_KEY, REDIS_BACKEND_EVENT_KEY,
)
from app.core.redis_client import redis_client

router = APIRouter(tags=["任务状态"])

# 后台处理流水线阶段（按顺序，不包含增强事件）
BACKEND_PIPELINE_STAGES = [
    TaskTypes.MERGE,
    TaskTypes.HASH_CALCULATE,
    TaskTypes.VIRUS_SCAN,
    TaskTypes.MARK_ACTIVE,
]


@router.get("/files/tasks/{backend_task_id}", summary="查询后台文件处理任务状态")
async def get_task_status(
    backend_task_id: str,
    user_id: str = Header(..., alias="X-User-Id")
):
    """
    查询后台文件处理任务状态（仅查询后台处理事件，不包含增强事件）

    后台处理步骤:
      1. merge: 文件合并
      2. hash_calculate: 哈希计算
      3. virus_scan: 病毒扫描
      4. mark_active: 标记文件为活跃状态

    任务状态:
      - processing: 处理中
      - completed: 处理完成
      - failed: 处理失败

    Args:
        backend_task_id: 后台任务唯一标识符（UUID hex）
        user_id: 用户唯一标识符（从 X-User-Id 请求头获取）

    Returns:
        JSONResponse:
            - code: 200 表示成功
            - data.backend_task_id: 后台任务 ID
            - data.file_id: 文件 ID
            - data.status: 总任务状态
            - data.current_stage: 当前处理阶段
            - data.created_at: 任务创建时间
            - data.stages: 各阶段详细状态列表

    Stages 结构:
        [
            {
                "stage": "merge",
                "status": "completed",
                "summary": "文件合并完成"
            },
            {
                "stage": "hash_calculate",
                "status": "processing",
                "summary": "哈希计算中"
            },
            ...
        ]

    Raises:
        HTTPException:
            - 404: 任务不存在
            - 403: 无权访问此任务

    Example:
        GET /files/tasks/a1b2c3d4e5f6...
        Headers: X-User-Id: user123

        Response:
        {
            "code": 200,
            "data": {
                "backend_task_id": "a1b2c3d4e5f6...",
                "file_id": "xxx",
                "file_name": "report.pdf",
                "status": "processing",
                "current_stage": "virus_scan",
                "created_at": "2024-01-01T12:00:00+00:00",
                "updated_at": "2024-01-01T12:01:00+00:00",
                "stages": [
                    {"stage": "merge", "status": "completed", "summary": "completed"},
                    {"stage": "hash_calculate", "status": "completed", "summary": "completed"},
                    {"stage": "virus_scan", "status": "processing", "summary": "processing"},
                    {"stage": "mark_active", "status": "pending", "summary": "none"}
                ]
            },
            "message": null
        }
    """
    # 1. 获取总任务状态
    master_key = REDIS_BACKEND_MASTER_KEY.format(backend_task_id=backend_task_id)
    master_data = await redis_client.hgetall(master_key)

    if not master_data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="任务不存在"
        )

    # 2. 验证用户权限
    if master_data.get("user_id") != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="无权访问此任务"
        )

    # 3. 获取各阶段状态（仅后台处理阶段，不包含增强）
    stages = []
    for stage_name in BACKEND_PIPELINE_STAGES:
        event_key = REDIS_BACKEND_EVENT_KEY.format(
            backend_task_id=backend_task_id, stage=stage_name
        )
        event_status = await redis_client.get(event_key)
        stages.append({
            "stage": stage_name,
            "status": event_status if event_status else "pending",
            "summary": event_status if event_status else "none",
        })

    # 4. 返回任务状态
    return JSONResponse({
        "code": 200,
        "data": {
            "backend_task_id": backend_task_id,
            "file_id": master_data.get("file_id"),
            "file_name": master_data.get("file_name", ""),
            "status": master_data.get("status", TaskStatus.PROCESSING),
            "current_stage": master_data.get("current_stage"),
            "created_at": master_data.get("created_at"),
            "updated_at": master_data.get("updated_at"),
            "stages": stages,
        },
        "message": None,
    })


@router.get("/files/tasks/{backend_task_id}/stages/{stage_name}", summary="查询指定阶段状态")
async def get_stage_status(
    backend_task_id: str,
    stage_name: str,
    user_id: str = Header(..., alias="X-User-Id")
):
    """
    查询指定阶段的事件状态

    Args:
        backend_task_id: 后台任务 ID
        stage_name: 阶段名称 (merge / hash_calculate / virus_scan / mark_active)
        user_id: 用户 ID

    Returns:
        { "stage": "...", "status": "..." }
    """
    # 验证 stage_name 合法性
    if stage_name not in BACKEND_PIPELINE_STAGES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"无效的阶段名称: {stage_name}，合法值: {BACKEND_PIPELINE_STAGES}"
        )

    # 验证任务存在且用户有权访问
    master_key = REDIS_BACKEND_MASTER_KEY.format(backend_task_id=backend_task_id)
    master_data = await redis_client.hgetall(master_key)
    if not master_data:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="任务不存在")
    if master_data.get("user_id") != user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="无权访问此任务")

    # 查询阶段状态
    event_key = REDIS_BACKEND_EVENT_KEY.format(
        backend_task_id=backend_task_id, stage=stage_name
    )
    event_status = await redis_client.get(event_key)

    return JSONResponse({
        "code": 200,
        "data": {
            "backend_task_id": backend_task_id,
            "stage": stage_name,
            "status": event_status if event_status else "pending",
        },
        "message": None,
    })