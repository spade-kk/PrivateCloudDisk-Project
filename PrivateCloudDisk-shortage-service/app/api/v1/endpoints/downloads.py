"""
文件下载 API 端点
提供基于 Opaque Token 的文件下载接口，支持并发控制和可撤销授权
"""
import json
import logging
import aiofiles
import uuid
from fastapi import APIRouter, Header, Request, Depends, Query, HTTPException, status
from fastapi.responses import StreamingResponse, FileResponse, Response
from typing import Optional

from core.config import settings
from core.redis_client import redis_client
from core.download_grant import (
    verify_download_grant,
    acquire_chunk_permit,
    release_chunk_permit,
    finish_download_grant,
    fetch_file_metadata_from_business_service
)


# 创建路由器
router = APIRouter(tags=["文件下载"])

# 配置
BUSINESS_SERVICE_URL = settings.business_service_url
MAX_RANGE_BYTES = settings.max_range_bytes

# 日志记录器
logger = logging.getLogger(__name__)


@router.get("/downloads/files/{file_id}/content", summary="下载文件（使用下载授权）")
async def download_file_with_grant(
    file_id: str,
    request: Request,
    download_token: str = Header(..., alias="X-Download-Token"),
    range_header: Optional[str] = Header(None, alias="Range"),
    request_id: Optional[str] = Header(None, alias="X-Request-Id")
):
    """
    使用下载授权下载文件（支持 Range 请求和并发控制）
    
    功能说明：
    使用 Opaque Token 验证下载授权，支持 HTTP Range 请求实现断点续传和分段下载。
    实现分块并发控制，防止单个用户同时发起过多下载请求。
    
    业务流程：
    1. 验证下载授权（从Redis获取授权信息）
    2. 验证文件ID匹配
    3. 申请分块并发许可证
    4. 获取文件元数据（优先从Redis缓存）
    5. 处理 Range 请求（如果存在）
    6. 流式返回文件内容
    7. 释放分块并发许可证
    
    Range 请求支持：
    - 格式：Range: bytes=start-end
    - 示例：Range: bytes=0-1023（下载前1024字节）
    - 示例：Range: bytes=1024-（从第1024字节下载到末尾）
    - 单次 Range 最大字节数：由 MAX_RANGE_BYTES 配置
    
    并发控制：
    - 单个下载token最大分块并发数：由授权中的 maxParallelChunks 控制
    - 分块请求超时时间：60秒（自动回收异常中断的请求）
    
    Args:
        file_id: 文件唯一标识符（UUID格式）
        request: FastAPI 请求对象
        download_token: 下载授权token（从 X-Download-Token 请求头获取）
        range_header: HTTP Range 请求头（可选）
        request_id: 请求ID（可选，自动生成）
    
    Returns:
        StreamingResponse: Range 请求返回 206 状态码，流式返回文件片段
        FileResponse: 非 Range 请求返回 200 状态码，返回完整文件
    
    Raises:
        HTTPException:
            - 401: 下载授权不存在、已过期或已撤销
            - 403: 文件ID不匹配
            - 404: 文件不存在
            - 416: Range 请求范围不满足
            - 429: 分块并发数超限
    
    Example:
        GET /downloads/files/xxx/content
        Headers:
            X-Download-Token: dgt_v1.xxx...
            Range: bytes=0-1023
        
        Response:
            Status: 206 Partial Content
            Headers:
                Content-Range: bytes 0-1023/10240
                Content-Length: 1024
                Accept-Ranges: bytes
            Body: [文件字节流]
    """
    # 1. 验证下载授权
    grant_info = await verify_download_grant(download_token, file_id)
    user_id = grant_info.get("userId")
    
    # 2. 申请分块并发许可证
    req_id = request_id or str(uuid.uuid4())
    await acquire_chunk_permit(download_token, req_id)
    
    try:
        # 3. 获取文件元数据（优先从Redis缓存）
        token_hash = hashlib.sha256(download_token.encode('utf-8')).digest().hex()
        data = await redis_client.get(f"download:grant:meta:{token_hash}")
        
        if data:
            metadata = json.loads(data)
        else:
            # 缓存未命中，调用业务服务
            metadata = await fetch_file_metadata_from_business_service(file_id, user_id)
            
            # 缓存到Redis（TTL比授权过期时间短30秒）
            expires_in = (grant_info.get("expiresAt", 0) - time.time() * 1000) / 1000 - 30
            if expires_in > 0:
                await redis_client.setex(
                    f"download:grant:meta:{token_hash}",
                    int(expires_in),
                    json.dumps({
                        "storage_path": metadata.get("storage_path"),
                        "file_size": metadata.get("size"),
                        "file_name": metadata.get("name")
                    })
                )
        
        file_storage_path = metadata.get("storage_path")
        file_size = metadata.get("file_size")
        file_name = metadata.get("file_name")
        
        if not file_storage_path:
            raise HTTPException(status_code=404, detail="文件不存在")
        
        start, end = 0, file_size - 1
        
        # 4. 处理 Range 请求
        if range_header:
            unit, _, ranges = range_header.partition("=")
            if unit.strip() == "bytes":
                ranges = ranges.strip()
                start_str, _, end_str = ranges.partition("-")
                try:
                    start = int(start_str) if start_str else 0
                    end = int(end_str) if end_str else file_size - 1
                except ValueError:
                    logger.info(f"Invalid range header: {ranges}")
                    raise HTTPException(status_code=400, detail="Invalid Range header")
                
                if start >= file_size or end >= file_size or start > end:
                    raise HTTPException(status_code=416, detail="Range not satisfiable")
            
            content_length = end - start + 1
            if content_length > MAX_RANGE_BYTES:
                raise HTTPException(
                    status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
                    detail=f"Requested range exceeds maximum allowed size of {MAX_RANGE_BYTES} bytes",
                    headers={
                        "Content-Range": f"bytes */{file_size}",
                        "X-Max-Range-Size": str(MAX_RANGE_BYTES)
                    }
                )
            
            # 流式返回文件片段
            async def file_iterator():
                async with aiofiles.open(file_storage_path, "rb") as f:
                    await f.seek(start)
                    remaining = content_length
                    while remaining > 0:
                        chunk_size = min(8192, remaining)
                        data = await f.read(chunk_size)
                        if not data:
                            break
                        remaining -= len(data)
                        yield data
            
            headers = {
                "Content-Range": f"bytes {start}-{end}/{file_size}",
                "Content-Length": str(content_length),
                "Accept-Ranges": "bytes",
                "X-Request-Id": req_id
            }
            
            return StreamingResponse(
                file_iterator(),
                status_code=206,
                headers=headers,
                media_type="application/octet-stream"
            )
        else:
            # 非 Range 请求，返回完整文件
            content_length = file_size
            if content_length > MAX_RANGE_BYTES:
                raise HTTPException(
                    status_code=400,
                    detail=f"File too large to download without Range header. Max allowed size: {MAX_RANGE_BYTES} bytes",
                    headers={"X-Max-File-Size": str(MAX_RANGE_BYTES)}
                )
            
            return FileResponse(
                path=file_storage_path,
                filename=file_name,
                media_type="application/octet-stream",
                headers={"Accept-Ranges": "bytes", "X-Request-Id": req_id}
            )
    finally:
        # 5. 释放分块并发许可证
        await release_chunk_permit(download_token, req_id)


@router.post("/downloads/files/{file_id}/finish", summary="标记下载完成")
async def finish_download(
    file_id: str,
    download_token: str = Header(..., alias="X-Download-Token")
):
    """
    标记下载完成
    
    功能说明：
    下载完成后调用此接口，标记授权为已完成状态，释放相关资源。
    
    业务流程：
    1. 验证下载授权
    2. 标记授权为已完成
    3. 清理分块并发计数器
    
    Args:
        file_id: 文件唯一标识符（UUID格式）
        download_token: 下载授权token（从 X-Download-Token 请求头获取）
    
    Returns:
        Response: 200 状态码表示成功
    
    Raises:
        HTTPException:
            - 401: 下载授权不存在或已失效
    
    Example:
        POST /downloads/files/xxx/finish
        Headers:
            X-Download-Token: dgt_v1.xxx...
        
        Response:
            Status: 200 OK
    """
    # 验证下载授权
    await verify_download_grant(download_token, file_id)
    
    # 标记下载完成
    await finish_download_grant(download_token)
    
    logger.info(f"Download finished: file_id={file_id}")
    return Response(status_code=200)


@router.get("/downloads/grants/status", summary="查询下载授权状态")
async def get_grant_status(
    download_token: str = Header(..., alias="X-Download-Token")
):
    """
    查询下载授权状态
    
    功能说明：
    查询当前下载授权的状态信息，包括有效期、文件信息等。
    
    Args:
        download_token: 下载授权token（从 X-Download-Token 请求头获取）
    
    Returns:
        dict: 授权状态信息
        
    Raises:
        HTTPException:
            - 401: 下载授权不存在或已失效
    
    Example:
        GET /downloads/grants/status
        Headers:
            X-Download-Token: dgt_v1.xxx...
        
        Response:
            {
                "status": "ACTIVE",
                "file_id": "xxx",
                "file_name": "example.txt",
                "file_size": 1024,
                "expires_at": 1699999999999,
                "max_parallel_chunks": 4
            }
    """
    token_hash = hashlib.sha256(download_token.encode('utf-8')).digest().hex()
    grant_key = "download:grant:" + token_hash
    
    grant_json = await redis_client.get(grant_key)
    if not grant_json:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Download grant not found"
        )
    
    try:
        grant_info = json.loads(grant_json)
        
        return {
            "status": grant_info.get("status"),
            "file_id": grant_info.get("fileId"),
            "file_name": grant_info.get("fileName"),
            "file_size": grant_info.get("fileSize"),
            "expires_at": grant_info.get("expiresAt"),
            "max_parallel_chunks": grant_info.get("maxParallelChunks"),
            "operation_type": grant_info.get("operationType")
        }
    except json.JSONDecodeError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid download grant"
        )


# 导入依赖
import hashlib
import time
