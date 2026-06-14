"""
文件上传 API 端点
提供文件分片上传和合并接口
"""
import uuid
import time
import logging
import aiofiles
import requests
from fastapi import APIRouter, Header, Form, File, UploadFile, HTTPException, status
from fastapi.responses import JSONResponse

from core.config import settings, TaskTypes, TaskStatus
from app.core.redis_client import redis_client
from core.rabbitmq import rabbitmq_service


# 创建路由器
router = APIRouter(tags=["文件上传"])

# 配置
BUSINESS_SERVICE_URL = settings.business_service_url
UPLOAD_DIR = settings.file_upload_dir

# 日志记录器
logger = logging.getLogger(__name__)


@router.post("/files/uploads/{uploads_id}/chunks", summary="上传文件分片")
@router.post("/files/uploads/{uploads_id}/chunks/", summary="上传文件分片")
async def upload_chunk(
    uploads_id: str,
    chunk_index: int = Form(...),
    file: UploadFile = File(...),
    user_id: str = Header(..., alias="X-User-Id")
):
    """
    上传文件分片
    
    功能说明：
    上传大文件的分片数据。支持断点续传，已上传的分片会被跳过。
    分片上传完成后，会通知业务服务记录分片状态。
    
    业务流程：
    1. 调用业务服务验证上传会话是否存在
    2. 验证用户身份匹配
    3. 检查分片是否已上传（防止重复上传）
    4. 验证分片大小不超过限制
    5. 验证分片索引在有效范围内
    6. 将分片数据写入临时文件
    7. 通知业务服务分片上传完成
    
    分片存储：
    - 临时文件路径：{UPLOAD_DIR}/{uploads_id}-{chunk_index}.part
    - 分片大小限制：由上传会话的 chunks_max_size 字段控制
    
    Args:
        uploads_id: 上传会话唯一标识符（UUID格式）
        chunk_index: 分片索引（从1开始）
        file: 上传的分片文件
        user_id: 用户唯一标识符（从 X-User-Id 请求头获取）
    
    Returns:
        JSONResponse:
            - code: 200 表示成功
            - data.chunk: 已上传的分片索引
            - message: 错误信息（成功时为 null）
    
    Raises:
        HTTPException:
            - 503: 上传会话不存在、分片已上传、分片大小超限、分片索引无效
            - 403: 用户身份不匹配
    
    Example:
        POST /files/uploads/xxx/chunks
        Headers: X-User-Id: user123
        Form Data:
            - chunk_index: 1
            - file: [分片文件数据]
        
        Response:
        {
            "code": 200,
            "data": {"chunk": 1},
            "message": null
        }
    """
    # 构建分片临时文件路径
    chunk_path = f"{UPLOAD_DIR}/{uploads_id}-{chunk_index}.part"

    # 1. 验证上传会话
    response = requests.get(
        f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/uploads/{uploads_id}"
    )
    result = response.json()
    
    # 2. 验证分片状态
    response = requests.get(
        f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/uploads/{uploads_id}/chunks/{chunk_index}"
    )
    chunk_result = response.json()

    # 会话不存在
    if result["code"] == 15000:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="会话不存在"
        )
    
    # 用户身份不匹配
    if result["data"]["user_id"] != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="上传操作令牌持有用户与当前用户不匹配"
        )
    
    # 分片已上传
    if chunk_result["data"] is not None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="此索引切片已经上传过了"
        )
    
    # 分片大小超限
    if file.size > result["data"]["chunks_max_size"]:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="切片大小超过限制"
        )
    
    # 分片索引无效
    if chunk_index <= 0 or chunk_index > result["data"]["total_chunks"]:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="切片索引不在总范围内"
        )
    
    # 3. 写入分片数据
    async with aiofiles.open(chunk_path, 'wb') as f:
        while chunk := await file.read(64 * 1024):
            await f.write(chunk)
    
    # 4. 通知业务服务分片上传完成
    response = requests.post(
        f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/uploads/{uploads_id}/chunks/{chunk_index}/complete",
        params={"storage_path": chunk_path}
    )

    return JSONResponse({
        "code": 200,
        "data": {"chunk": chunk_index},
        "message": None
    })


@router.post("/files/uploads/{uploads_id}/merge", summary="合并文件分片")
async def complete_uploads_internal(
    uploads_id: str,
    user_id: str = Header(..., alias="X-User-Id")
):
    """
    合并文件分片（企业级异步处理）
    
    功能说明：
    将已上传的所有分片合并成完整文件。采用异步处理模式，
    通过消息队列依次执行：合并 → 哈希计算 → 病毒扫描 → 缩略图/转码 → 标记活跃。
    
    业务流程：
    1. 调用业务服务验证上传会话
    2. 验证用户身份匹配
    3. 验证会话状态为 uploading
    4. 提交合并状态申请（逻辑锁防止并发）
    5. 生成任务ID并初始化任务状态
    6. 发送合并任务消息到消息队列
    7. 返回任务ID供客户端查询进度
    
    异步处理流程：
    1. 合并任务：合并所有分片文件
    2. 哈希计算：计算文件哈希值验证完整性
    3. 病毒扫描：使用ClamAV扫描病毒
    4. 缩略图生成：图片文件生成缩略图
    5. 视频转码：视频文件转码为多分辨率
    6. 标记活跃：文件正式可用
    
    任务状态追踪：
    - 使用Redis存储任务状态
    - 任务ID格式：UUID
    - 任务过期时间：3天
    - 可通过 /files/tasks/{task_id} 接口查询进度
    
    Args:
        uploads_id: 上传会话唯一标识符（UUID格式）
        user_id: 用户唯一标识符（从 X-User-Id 请求头获取）
    
    Returns:
        JSONResponse:
            - code: 200 表示成功
            - data.task_id: 任务唯一标识符
            - data.status: 任务状态（processing）
            - data.message: 处理状态说明
            - message: 错误信息（成功时为 null）
    
    Raises:
        HTTPException:
            - 503: 会话不存在、会话状态错误、合并状态申请失败
            - 403: 用户身份不匹配
    
    Example:
        POST /files/uploads/xxx/merge
        Headers: X-User-Id: user123
        
        Response:
        {
            "code": 200,
            "data": {
                "task_id": "550e8400-e29b-41d4-a716-446655440000",
                "status": "processing",
                "message": "文件合并任务已提交，正在处理中"
            },
            "message": null
        }
    """
    # 1. 验证上传会话
    response = requests.get(
        f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/uploads/{uploads_id}"
    )
    result = response.json()

    if result["code"] == 15000:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="会话不存在"
        )
    
    if result["data"]["user_id"] != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="上传操作令牌持有用户与当前用户不匹配"
        )
    
    if result["data"]["status"] != "uploading":
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="会话状态错误"
        )
    
    # 2. 提交合并状态申请（逻辑锁）
    response = requests.post(
        f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/uploads/{uploads_id}/merge"
    )
    merging_result = response.json()

    if merging_result["code"] != 200:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="合并状态申请失败"
        )

    file_id = merging_result["data"]

    # 3. 获取上传会话详情
    upload_session = result["data"]
    file_name = upload_session.get("file_name")
    file_type = upload_session.get("file_type")
    file_size = upload_session.get("file_size")
    total_chunks = upload_session.get("total_chunks")
    file_checksum = upload_session.get("file_checksum")
    node_id = upload_session.get("node_id")

    # 4. 生成任务ID
    task_id = str(uuid.uuid4())
    
    # 5. 初始化任务状态
    await redis_client.hset(f"task:{task_id}", mapping={
        "task_id": task_id,
        "file_id": file_id,
        "user_id": user_id,
        "uploads_id": uploads_id,
        "status": TaskStatus.PENDING,
        "created_at": time.time(),
        "current_step": TaskTypes.MERGE
    })
    await redis_client.expire(f"task:{task_id}", 86400 * 3)  # 3天过期

    # 6. 发送合并任务消息（只发送合并任务，后续任务由消费者链式触发）
    merge_message = {
        "message_id": str(uuid.uuid4()),
        "task_id": task_id,
        "file_id": file_id,
        "task_type": TaskTypes.MERGE,
        "user_id": user_id,
        "file_name": file_name,
        "file_type": file_type,
        "file_size": file_size,
        "uploads_id": uploads_id,
        "total_chunks": total_chunks,
        "file_checksum": file_checksum,
        "node_id": node_id,
        "retry_count": 0,
        "created_at": time.strftime("%Y-%m-%d %H:%M:%S")
    }

    await rabbitmq_service.publish_message(
        settings.file_process_exchange,
        settings.file_process_routing_key,
        merge_message
    )

    logger.info(f"合并任务已提交: task_id={task_id}, file_name={file_name}, file_id={file_id}")

    # 7. 返回任务ID
    return JSONResponse({
        "code": 200,
        "data": {
            "task_id": task_id,
            "file_id": file_id,
            "status": "processing",
            "message": "文件合并任务已提交，正在处理中"
        },
        "message": None
    })
