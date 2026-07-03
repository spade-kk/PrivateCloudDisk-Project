"""
文件操作 API 端点
提供文件下载和缩略图获取接口
"""
import json
import hashlib
import logging
import aiofiles
import os
import asyncio
from fastapi import APIRouter, Header, Request, Depends, Query, HTTPException, status
from fastapi.responses import StreamingResponse, FileResponse, Response
from typing import Optional

from core.config import settings
from app.core.redis_client import redis_client
from app.core.download_grant_limiter import download_grant_limiter
from app.core.download_grant import get_cached_file_metadata
from app.core.business_service_client import business_service_client, BusinessServiceError
from app.services.thumbnail_service import get_thumbnail_bytes


# 创建路由器
router = APIRouter(tags=["文件操作"])

# 配置
MAX_RANGE_BYTES = settings.max_range_bytes

# 日志记录器
logger = logging.getLogger(__name__)

# 预生成缩略图尺寸映射：客户端请求的 size → 增强流水线生成的 label
THUMBNAIL_SIZE_MAP = {
    "small": "xs",    # 100×100 — 网格/列表图标
    "medium": "md",   # 400×400 — 中等预览
    "large": "lg",    # 800×800 — 大图预览（有损）
}

# 缩略图存放目录
THUMBNAIL_DIR = os.path.join(settings.file_upload_dir, "thumbnails")


@router.get("/files/files/{file_id}/content", summary="下载文件")
async def download_file(
    file_id: str,
    request: Request,
    user_id: str = Header(..., alias="X-User-Id"),
    range_header: Optional[str] = Header(None, alias="Range"),
    _: None = Depends(download_grant_limiter)
):
    """
    下载文件（支持 Range 请求）

    功能说明：
    下载指定文件的内容，支持 HTTP Range 请求实现断点续传和分段下载。
    通过 Download Grant（Opaque Token）进行身份验证和限流控制。

    请求头：
      - X-User-Id: 用户 ID（必填）
      - X-Download-Grant: 下载授权 Token（必填，由 /files/download-grants 颁发）
      - Range: HTTP Range 请求头（可选）

    业务流程：
    1. 中间件验证 Download Grant 并注入授权信息到 request.state
    2. 验证文件 ID 与 Grant 匹配
    3. 获取缓存的文件元数据（存储路径等）
    4. 处理 Range 请求（如果存在）
    5. 流式返回文件内容

    Range 请求支持：
    - 格式：Range: bytes=start-end
    - 示例：Range: bytes=0-1023（下载前 1024 字节）
    - 示例：Range: bytes=1024-（从第 1024 字节下载到末尾）
    - 单次 Range 最大字节数：由 MAX_RANGE_BYTES 配置

    限流策略（由 DownloadGrantRateLimiter 提供）：
    - 总请求次数限制：MAX_REQUESTS_PER_GRANT
    - 每秒请求速率限制：RATE_PER_SEC
    - 并发连接数限制：MAX_CONCURRENT

    Returns:
        StreamingResponse: Range 请求返回 206，流式返回文件片段
        FileResponse: 非 Range 请求返回 200，返回完整文件

    Raises:
        HTTPException:
            - 401: Download Grant 无效或过期
            - 403: 用户身份不匹配或文件 ID 不匹配
            - 404: 文件不存在
            - 416: Range 请求范围不满足
            - 400: Range 头格式错误或文件过大
    """
    # 1. 获取中间件验证后的授权信息
    grant_data = request.state.download_grant_data
    grant_token = grant_data.get("token_hash", "")

    # 2. 验证文件 ID 与 Grant 匹配
    if grant_data.get("fileId") != file_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Download Grant not for this file"
        )

    # 3. 获取文件元数据
    raw_token = request.headers.get("X-Download-Grant", "")
    metadata = await get_cached_file_metadata(raw_token)

    if not metadata:
        # 缓存未命中，通过 SDK 异步调用业务服务获取文件元数据
        result = await business_service_client.get_file_metadata(file_id, user_id)
        if result["code"] != 200:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="文件不存在用户网盘, 或者路径目录不存在"
            )
        metadata = {
            "storage_path": result["data"]["storage_path"],
            "file_size": result["data"]["size"],
            "file_name": result["data"]["name"]
        }

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
                logger.info("Invalid Range header: %s", ranges)
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
            """文件流迭代器，异步读取文件指定范围的内容"""
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
                detail=f"File too large to download without Range header. "
                       f"Max allowed size: {MAX_RANGE_BYTES} bytes",
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
    """
    # 1. 通过 SDK 异步调用业务服务，验证文件权限并获取文件存储路径
    result = await business_service_client.get_file_by_node(node_id, file_name, user_id)

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


@router.get("/files/files/{file_id}/thumbnail", summary="获取预生成缩略图（大/中/小）")
async def get_pregenerated_thumbnail(
    file_id: str,
    request: Request,
    user_id: str = Header(..., alias="X-User-Id"),
    size: str = Query("small", description="缩略图尺寸: small(100×100), medium(400×400), large(800×800)"),
):
    """
    获取预生成缩略图（大/中/小）

    功能说明：
    返回文件增强流水线中预生成的缩略图。支持三种尺寸：
    - small (xs): 100×100 — 用于文件列表/网格视图的图标
    - medium (md): 400×400 — 中等大小预览
    - large (lg): 800×800 — 大图预览（有损压缩，非原图）

    业务流程：
    1. 验证 size 参数有效性
    2. 调用业务服务获取文件存储路径
    3. 查找预生成缩略图文件（{file_id}_{label}.jpg）
    4. 缩略图存在 → 直接返回
    5. 缩略图不存在 → 调用缩略图服务动态生成，存入 Redis 缓存
    6. 浏览器缓存：ETag 304 验证

    缓存策略：
    - 磁盘缓存：缩略图文件持久化存储（增强流水线生成）
    - Redis 缓存：动态生成的缩略图缓存，TTL 由 THUMBNAIL_TTL 配置
    - 浏览器缓存：ETag 验证，返回 304

    Args:
        file_id: 文件 ID
        request: FastAPI 请求对象
        user_id: 用户 ID（X-User-Id 请求头）
        size: 缩略图尺寸，可选 small/medium/large

    Returns:
        Response:
            - 200: JPEG 缩略图
            - 304: 缓存未修改

    Raises:
        HTTPException:
            - 400: 无效的 size 参数
            - 404: 文件不存在或非图片文件
            - 500: 缩略图生成失败
    """
    # 1. 验证 size 参数
    if size not in THUMBNAIL_SIZE_MAP:
        valid_sizes = ", ".join(THUMBNAIL_SIZE_MAP.keys())
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"无效的缩略图尺寸: '{size}'，有效值: {valid_sizes}"
        )

    label = THUMBNAIL_SIZE_MAP[size]

    # 2. 通过 SDK 异步调用业务服务获取文件元数据
    try:
        result = await business_service_client.get_file_metadata(file_id, user_id)
        if result.get("code") != 200:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="文件不存在或无权访问"
            )
    except BusinessServiceError as e:
        logger.error(f"调用业务服务失败: {e}")
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="无法连接业务服务"
        )

    storage_path = result["data"]["storage_path"]

    # 3. 查找预生成的缩略图文件
    thumbnail_path = os.path.join(THUMBNAIL_DIR, f"{file_id}_{label}.jpg")

    if os.path.exists(thumbnail_path):
        # 缩略图已存在，直接返回
        try:
            mtime = os.path.getmtime(thumbnail_path)
            etag = hashlib.md5(f"{file_id}{label}{mtime}".encode()).hexdigest()

            if request.headers.get("If-None-Match") == etag:
                return Response(status_code=304)

            async with aiofiles.open(thumbnail_path, "rb") as f:
                img_bytes = await f.read()

            return Response(
                content=img_bytes,
                media_type="image/jpeg",
                headers={
                    "Cache-Control": f"public, max-age={settings.thumbnail_ttl}",
                    "ETag": etag,
                    "X-Thumbnail-Source": "disk",
                }
            )
        except Exception as e:
            logger.error(f"读取预生成缩略图失败: {e}")

    # 4. 缩略图不存在，动态生成（并缓存到 Redis）
    logger.info(f"预生成缩略图不存在，动态生成: file_id={file_id}, size={size}")

    try:
        # 从 size 映射到实际像素尺寸
        size_dimensions = {
            "small": (100, 100),
            "medium": (400, 400),
            "large": (800, 800),
        }
        width, height = size_dimensions[size]

        img_bytes, etag = await get_thumbnail_bytes(storage_path, width, height)

        if request.headers.get("If-None-Match") == etag:
            return Response(status_code=304)

        return Response(
            content=img_bytes,
            media_type="image/jpeg",
            headers={
                "Cache-Control": f"public, max-age={settings.thumbnail_ttl}",
                "ETag": etag,
                "X-Thumbnail-Source": "generated",
            }
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"动态生成缩略图失败: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"缩略图生成失败"
        )