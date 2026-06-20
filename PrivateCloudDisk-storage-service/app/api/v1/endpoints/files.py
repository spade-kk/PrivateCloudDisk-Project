"""
文件操作 API 端点
提供文件下载和缩略图获取接口
"""
import json
import logging
import aiofiles
import requests
from fastapi import APIRouter, Header, Request, Depends, Query, HTTPException, status
from fastapi.responses import StreamingResponse, FileResponse, Response
from typing import Optional

from core.config import settings
from app.core.redis_client import redis_client
from app.core.rate_limiter import operation_limiter, OPERATION_TOKEN_EXPIRE_SECONDS
from app.services.thumbnail_service import get_thumbnail_bytes


# 创建路由器
router = APIRouter(tags=["文件操作"])

# 配置
BUSINESS_SERVICE_URL = settings.business_service_url
MAX_RANGE_BYTES = settings.max_range_bytes

# 日志记录器
logger = logging.getLogger(__name__)


@router.get("/files/files/{file_id}/content", summary="下载文件")
async def download_file(
    file_id: str,
    request: Request,
    user_id: str = Header(..., alias="X-User-Id"),
    range_header: Optional[str] = Header(None, alias="Range"),
    _: None = Depends(operation_limiter)
):
    """
    下载文件（支持 Range 请求）
    
    功能说明：
    下载指定文件的内容，支持 HTTP Range 请求实现断点续传和分段下载。
    通过操作凭证进行身份验证和限流控制。
    
    业务流程：
    1. 从 request.state 获取已验证的操作凭证载荷
    2. 验证用户身份和文件ID匹配
    3. 从 Redis 缓存获取文件元数据（未命中则调用业务服务）
    4. 处理 Range 请求（如果存在）
    5. 流式返回文件内容
    
    Range 请求支持：
    - 格式：Range: bytes=start-end
    - 示例：Range: bytes=0-1023（下载前1024字节）
    - 示例：Range: bytes=1024-（从第1024字节下载到末尾）
    - 单次 Range 最大字节数：由 MAX_RANGE_BYTES 配置
    
    限流策略：
    - 总请求次数限制：由操作凭证中的 rlimit 字段控制
    - 每秒请求速率限制：由 RATE_PER_SEC 配置
    - 并发连接数限制：由 MAX_CONCURRENT 配置
    
    Args:
        file_id: 文件唯一标识符（UUID格式）
        request: FastAPI 请求对象
        user_id: 用户唯一标识符（从 X-User-Id 请求头获取）
        range_header: HTTP Range 请求头（可选）
        _: 操作凭证限流依赖注入
    
    Returns:
        StreamingResponse: Range 请求返回 206 状态码，流式返回文件片段
        FileResponse: 非 Range 请求返回 200 状态码，返回完整文件
    
    Raises:
        HTTPException:
            - 403: 用户身份不匹配或文件ID不匹配
            - 404: 文件不存在
            - 416: Range 请求范围不满足
            - 400: Range 头格式错误或文件过大
    
    Example:
        GET /files/files/xxx/content
        Headers:
            X-User-Id: user123
            X-Operation-Token: eyJhbGciOiJSUzI1NiIs...
            Range: bytes=0-1023
        
        Response:
            Status: 206 Partial Content
            Headers:
                Content-Range: bytes 0-1023/10240
                Content-Length: 1024
                Accept-Ranges: bytes
            Body: [文件字节流]
    """
    # 1. 获取已验证的操作凭证载荷
    payload = request.state.operation_token_payload
    jti = payload["jti"]
    sub = payload["sub"]
    
    # 2. 验证用户身份和文件ID
    if sub != user_id:
        raise HTTPException(status_code=403, detail="Operation token user mismatch")
    if payload["file_id"] != file_id:
        raise HTTPException(status_code=403, detail="Operation Token not for this file")
    
    # 3. 获取文件元数据（优先从Redis缓存）
    data = await redis_client.get(f"operation_token_meta:{sub}:{jti}")
    if data:
        metadata = json.loads(data)
    else:
        # 缓存未命中，调用业务服务
        response = requests.get(
            f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/files/{file_id}?uid={user_id}"
        )
        result = response.json()
        if result["code"] != 200:
            raise HTTPException(status_code=404, detail="文件不存在用户网盘, 或者路径目录不存在")
        
        metadata = {
            "storage_path": result["data"]["storage_path"],
            "file_size": result["data"]["size"],
            "file_name": result["data"]["name"]
        }
        
        # 缓存到Redis
        await redis_client.setex(
            f"operation_token_meta:{sub}:{jti}",
            OPERATION_TOKEN_EXPIRE_SECONDS,
            json.dumps(metadata)
        )

    file_storage_path = metadata["storage_path"]
    file_size = metadata["file_size"]
    file_name = metadata["file_name"]
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
                logging.info(ranges)
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
            """
            文件流迭代器
            异步读取文件指定范围的内容
            """
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
            headers={"Accept-Ranges": "bytes"}
        )


@router.get("/files/nodes/{node_id}/thumbnails/{file_name}", summary="获取缩略图")
async def get_thumbnail(
    node_id: str,
    file_name: str,
    request: Request,
    user_id: str = Header(..., alias="X-User-Id"),
    width: int = Query(200, ge=50, le=800, description="缩略图宽度（像素）"),
    height: int = Query(200, ge=50, le=800, description="缩略图高度（像素）")
):
    """
    获取文件缩略图
    
    功能说明：
    获取指定图片文件的缩略图。使用 libvips 高性能图片处理库生成，
    支持 Redis 缓存和浏览器缓存（ETag）。
    
    业务流程：
    1. 调用业务服务验证文件权限并获取文件存储路径
    2. 调用缩略图服务生成或获取缓存的缩略图
    3. 检查浏览器缓存（If-None-Match 头）
    4. 返回缩略图响应
    
    缓存策略：
    - 服务端缓存：Redis 缓存缩略图，TTL 由 THUMBNAIL_TTL 配置
    - 浏览器缓存：ETag 缓存验证，返回 304 状态码
    
    图片处理：
    - 等比缩放，不超过目标宽高
    - Lanczos3 重采样算法，高质量缩放
    - JPEG 编码优化（质量85）
    
    Args:
        node_id: 文件节点ID（UUID格式）
        file_name: 文件名
        request: FastAPI 请求对象
        user_id: 用户唯一标识符（从 X-User-Id 请求头获取）
        width: 缩略图宽度（像素），默认200，范围50-800
        height: 缩略图高度（像素），默认200，范围50-800
    
    Returns:
        Response:
            - 200: 返回缩略图（JPEG格式）
            - 304: 缓存未修改（ETag匹配）
    
    Response Headers:
        - Content-Type: image/jpeg
        - Cache-Control: public, max-age={THUMBNAIL_TTL}
        - ETag: 缩略图唯一标识
    
    Raises:
        HTTPException:
            - 404: 文件不存在或用户无权限
            - 500: 缩略图生成失败
    
    Example:
        GET /files/nodes/xxx/thumbnails/image.jpg?width=200&height=200
        Headers: X-User-Id: user123
        
        Response:
            Status: 200 OK
            Headers:
                Content-Type: image/jpeg
                Cache-Control: public, max-age=3600
                ETag: abc123...
            Body: [JPEG图片字节流]
    """
    # 1. 调用业务服务验证文件权限
    response = requests.get(
        f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/files/{node_id}/{file_name}?uid={user_id}"
    )
    result = response.json()

    if result["code"] != 200:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="文件不存在用户网盘, 或者路径目录不存在"
        )

    # 2. 获取缩略图
    img_bytes, etag = await get_thumbnail_bytes(result["data"]["storage_path"], width, height)

    # 3. 检查浏览器缓存
    if request.headers.get("If-None-Match") == etag:
        return Response(status_code=304)

    # 4. 返回缩略图响应
    return Response(
        content=img_bytes,
        media_type="image/jpeg",
        headers={
            "Cache-Control": f"public, max-age={settings.thumbnail_ttl}",
            "ETag": etag
        }
    )
