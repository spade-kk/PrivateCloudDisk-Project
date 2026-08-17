"""
文件操作 API 端点
提供文件下载和缩略图获取接口
"""
import hashlib
import logging
import aiofiles
import os
from pathlib import Path
from fastapi import APIRouter, Header, Request, Depends, Query, HTTPException, status
from fastapi.responses import Response
from typing import Optional

from core.config import settings
from app.core.download_grant_limiter import download_grant_limiter
from app.core.download_grant import get_cached_file_metadata
from app.core.file_delivery import serve_authorized_file
from app.core.business_service_client import business_service_client, BusinessServiceError
from app.services.thumbnail_service import get_thumbnail_bytes
from app.services.preview_resource_service import preview_resource_service


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

    # 原有行为注释保留（回溯）：原实现“4. 处理 Range 请求”，Range 请求返回
    # 206 流式分片；非 Range 请求返回完整文件，并限制单次读取大小。
    # 普通下载与分享下载共用文件投递核心；下载授权限流和
    # 最近访问事件仍由本路由保持，只有路径、Range 和异步分块读取被集中维护。
    return await serve_authorized_file(
        metadata["storage_path"],
        file_name=metadata["file_name"],
        media_type="application/octet-stream",
        range_header=range_header,
        max_range_bytes=MAX_RANGE_BYTES,
        max_full_bytes=MAX_RANGE_BYTES,
        expected_size=int(metadata["file_size"]),
        content_disposition_type="attachment",
    )

@router.get("/files/files/{file_id}/thumbnail", summary="获取预生成缩略图（大/中/小）")
async def get_pregenerated_thumbnail(
    file_id: str,
    request: Request,
    user_id: str = Header(..., alias="X-User-Id"),
    size: str = Query("small", description="缩略图尺寸: small(100×100), medium(400×400), large(800×800)"),
    token: str = Query(default=None, description="HLS 流媒体访问 Token（可选，用于无 Header 鉴权场景）"),
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

    # 预生成缩略图的存在性以预览资源数据库为准，不再扫描
    # uploads/thumbnails 固定目录；这样多实例部署时不会因本地目录不同而误判或越权。
    resource = await preview_resource_service.get_ready(file_id, user_id, "thumbnail", label)
    thumbnail_path = Path(str(resource["storage_path"])).resolve() if resource else None
    upload_root = Path(settings.file_upload_dir).resolve()
    if thumbnail_path:
        try:
            thumbnail_path.relative_to(upload_root)
        except ValueError as exc:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="缩略图路径不在允许范围内") from exc

    if thumbnail_path and thumbnail_path.is_file():
        # 缩略图已存在，直接返回
        try:
            mtime = os.path.getmtime(thumbnail_path)
            etag = hashlib.md5(
                f"{resource.get('resource_id')}:{resource.get('source_version')}:{mtime}".encode()
            ).hexdigest()

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
    except Exception as e:
        logger.error(f"动态生成缩略图失败: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"缩略图生成失败"
        )
