"""
任务状态查询 API 端点
提供文件处理任务状态查询接口
"""
from fastapi import APIRouter, Header, HTTPException, status
from fastapi.responses import JSONResponse

from core.config import TaskTypes, TaskStatus
from app.core.redis_client import redis_client


# 创建路由器
router = APIRouter(tags=["任务状态"])


@router.get("/files/tasks/{task_id}", summary="查询任务状态")
async def get_task_status(
    task_id: str,
    user_id: str = Header(..., alias="X-User-Id")
):
    """
    查询文件处理任务状态
    
    功能说明：
    查询文件合并、病毒扫描、缩略图生成、视频转码等异步任务的处理进度和状态。
    支持查询每个处理步骤的详细状态。
    
    任务处理步骤：
    1. merge: 文件合并
    2. hash_calculate: 哈希计算
    3. virus_scan: 病毒扫描
    4. thumbnail: 缩略图生成（仅图片文件）
    5. video_transcode: 视频转码（仅视频文件）
    6. mark_active: 标记文件为活跃状态
    
    任务状态：
    - pending: 等待处理
    - processing: 处理中
    - completed: 处理完成
    - failed: 处理失败
    - cancelled: 任务取消
    
    Args:
        task_id: 任务唯一标识符（UUID格式）
        user_id: 用户唯一标识符（从 X-User-Id 请求头获取）
    
    Returns:
        JSONResponse:
            - code: 200 表示成功
            - data.task_id: 任务唯一标识符
            - data.file_id: 文件唯一标识符
            - data.status: 任务总体状态
            - data.current_step: 当前处理步骤
            - data.created_at: 任务创建时间
            - data.steps: 各步骤详细状态列表
            - message: 错误信息（成功时为 null）
    
    Steps 结构：
        [
            {
                "step": "merge",
                "status": "completed",
                "updated_at": "2024-01-01 12:00:00",
                "result": {"storage_path": "/path/to/file"}
            },
            {
                "step": "virus_scan",
                "status": "processing",
                "updated_at": "2024-01-01 12:01:00",
                "result": {}
            },
            ...
        ]
    
    Raises:
        HTTPException:
            - 404: 任务不存在
            - 403: 无权访问此任务
    
    Example:
        GET /files/tasks/550e8400-e29b-41d4-a716-446655440000
        Headers: X-User-Id: user123
        
        Response:
        {
            "code": 200,
            "data": {
                "task_id": "550e8400-e29b-41d4-a716-446655440000",
                "file_id": "xxx",
                "status": "processing",
                "current_step": "virus_scan",
                "created_at": "1704096000.0",
                "steps": [
                    {
                        "step": "merge",
                        "status": "completed",
                        "updated_at": "2024-01-01 12:00:00",
                        "result": {"storage_path": "/path/to/file"}
                    },
                    {
                        "step": "hash_calculate",
                        "status": "completed",
                        "updated_at": "2024-01-01 12:00:30",
                        "result": {"hash": "abc123..."}
                    },
                    {
                        "step": "virus_scan",
                        "status": "processing",
                        "updated_at": "2024-01-01 12:01:00",
                        "result": {}
                    },
                    {
                        "step": "thumbnail",
                        "status": "pending",
                        "updated_at": null,
                        "result": {}
                    },
                    {
                        "step": "video_transcode",
                        "status": "pending",
                        "updated_at": null,
                        "result": {}
                    },
                    {
                        "step": "mark_active",
                        "status": "pending",
                        "updated_at": null,
                        "result": {}
                    }
                ]
            },
            "message": null
        }
    """
    # 1. 获取任务状态
    task_data = await redis_client.hgetall(f"task:{task_id}")
    
    if not task_data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="任务不存在"
        )
    
    # 2. 验证用户权限
    if task_data.get("user_id") != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="无权访问此任务"
        )
    
    # 3. 获取各步骤状态
    steps = []
    for step in [TaskTypes.MERGE, TaskTypes.HASH_CALCULATE, TaskTypes.VIRUS_SCAN, 
                 TaskTypes.THUMBNAIL, TaskTypes.VIDEO_TRANSCODE, TaskTypes.MARK_ACTIVE]:
        step_data = await redis_client.hgetall(f"task:{task_id}:{step}")
        if step_data:
            steps.append({
                "step": step,
                "status": step_data.get("status", "pending"),
                "updated_at": step_data.get("updated_at"),
                "result": {k: v for k, v in step_data.items() if k not in ["status", "updated_at"]}
            })
        else:
            steps.append({
                "step": step,
                "status": "pending",
                "updated_at": None,
                "result": {}
            })
    
    # 4. 返回任务状态
    return JSONResponse({
        "code": 200,
        "data": {
            "task_id": task_id,
            "file_id": task_data.get("file_id"),
            "status": task_data.get("status", TaskStatus.PENDING),
            "current_step": task_data.get("current_step"),
            "created_at": task_data.get("created_at"),
            "steps": steps
        },
        "message": None
    })
