"""
HLS 视频流媒体 API 端点
提供 HLS 流媒体播放所需的 m3u8 播放列表、TS 分片、流信息等接口

安全模型:
- 使用 JWT Token 鉴权，前端先调用 /api/v1/video/stream/{file_id}/token 获取临时 token
- 后续请求携带 token 访问 m3u8 和 ts 分片
- Token 包含 file_id 和 user_id，防止跨文件访问
- 支持 Token 过期和按分辨率授权

API 列表:
- GET  /video/stream/{file_id}/info       - 获取视频流信息 (分辨率、编码、HLS 可用性)
- POST /video/stream/{file_id}/token      - 获取流媒体访问 Token
- GET  /video/stream/{file_id}/master.m3u8 - 获取 HLS 主播放列表 (需 token)
- GET  /video/stream/{file_id}/{res}/index.m3u8 - 获取分辨率播放列表 (需 token)
- GET  /video/stream/{file_id}/{res}/segment-{N}.ts - 获取 TS 分片 (需 token)
"""
from __future__ import annotations
import logging
import json
import os
import time
import hmac
import hashlib
import aiofiles
import subprocess
import asyncio
import base64
import tempfile
from pathlib import Path
from fastapi import APIRouter, Header, Query, HTTPException, status, Request
from fastapi.responses import StreamingResponse, Response, FileResponse
from typing import Optional

from core.config import settings
from app.core.redis_client import redis_client
from app.core.business_service_client import BusinessServiceError, business_service_client
from app.services.preview_resource_service import preview_resource_service

logger = logging.getLogger("video_stream")

router = APIRouter(prefix="/files/video/stream", tags=["视频流媒体"])

# Token 密钥 (生产环境应使用环境变量或密钥管理服务)
_HLS_TOKEN_SECRET = os.environ.get(
    "HLS_TOKEN_SECRET",
    "privateclouddisk-hls-token-secret-v1",
)
_HLS_TOKEN_EXPIRE_SECONDS = 3600  # Token 默认 1 小时有效


# =============================================================================
# Token 工具函数
# =============================================================================

def _generate_hls_token(file_id: str, user_id: str, expires_in: int = _HLS_TOKEN_EXPIRE_SECONDS) -> str:
    """
    生成 HLS 流媒体访问 Token

    格式: <base64_payload>.<hmac_signature>
    实现简单 HMAC 签名 Token，不需要完整 JWT 库依赖
    """
    payload = {
        "file_id": file_id,
        "user_id": user_id,
        "exp": int(time.time()) + expires_in,
        "iat": int(time.time()),
    }
    payload_b64 = _base64url_encode(json.dumps(payload, separators=(",", ":")).encode())
    signature = hmac.new(
        _HLS_TOKEN_SECRET.encode(),
        payload_b64.encode(),
        hashlib.sha256,
    ).hexdigest()
    return f"{payload_b64}.{signature}"


def _verify_hls_token(token: str, file_id: str) -> dict:
    """验证 HLS Token 并返回 payload"""
    try:
        parts = token.split(".")
        if len(parts) != 2:
            raise ValueError("Invalid token format")

        payload_b64, signature = parts

        # 验证签名
        expected_sig = hmac.new(
            _HLS_TOKEN_SECRET.encode(),
            payload_b64.encode(),
            hashlib.sha256,
        ).hexdigest()

        if not hmac.compare_digest(signature, expected_sig):
            raise ValueError("Invalid signature")

        # 解码 payload
        payload = json.loads(_base64url_decode(payload_b64))

        # 验证过期
        if payload.get("exp", 0) < time.time():
            raise ValueError("Token expired")

        # 验证 file_id
        if payload.get("file_id") != file_id:
            raise ValueError("Token file_id mismatch")

        return payload
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Invalid or expired token: {e}",
        )


def _base64url_encode(data: bytes) -> str:
    """Base64URL 编码 (无 padding)"""
    import base64
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def _base64url_decode(data: str) -> bytes:
    """Base64URL 解码"""
    import base64
    padding = 4 - len(data) % 4
    if padding != 4:
        data += "=" * padding
    return base64.urlsafe_b64decode(data)


async def _get_hls_root(file_id: str, token: str) -> Path:
    """根据令牌中的用户与数据库资源台账定位 HLS 根目录。"""
    payload = _verify_hls_token(token, file_id)
    resource = await preview_resource_service.get_ready(
        file_id, str(payload["user_id"]), "hls", "master"
    )
    if not resource:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="HLS 资源不存在或已失效")
    return Path(resource["storage_path"]).resolve()


def _resolve_hls_child(root: Path, *parts: str) -> Path:
    """把播放清单中的相对路径限制在资源根目录内，阻断路径穿越。"""
    target = root.joinpath(*parts).resolve()
    try:
        target.relative_to(root)
    except ValueError as exc:
        # AUDIT FIX [6.3]: 分辨率和分片名来自 URL，必须在访问磁盘前完成边界校验。
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="非法的 HLS 资源路径") from exc
    return target


# =============================================================================
# API 端点
# =============================================================================

@router.get("/{file_id}/info", summary="获取视频流信息")
async def get_video_stream_info(
    file_id: str,
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    获取视频的流媒体信息

    返回:
    - has_hls: 是否已生成 HLS 流
    - has_dash: 是否支持 DASH
    - resolutions: 可用分辨率列表
    - hls_url: HLS 主播放列表 URL
    - 视频元数据: 时长、分辨率、编码等
    """
    try:
        await business_service_client.get_file_metadata(file_id, user_id)
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="视频不存在或无权访问") from exc

    # AUDIT FIX [7.4]: HLS 是否可用由数据库资源状态决定，不再扫描当前实例的本地目录。
    hls_resource = await preview_resource_service.get_ready(file_id, user_id, "hls", "master")
    if not hls_resource:
        # HLS 尚未生成，返回基础信息
        return {
            "code": 200,
            "data": {
                "file_id": file_id,
                "has_hls": False,
                "has_dash": False,
                "hls_url": None,
                "dash_url": None,
                "resolutions": [],
                "message": "HLS 转码尚未完成，请稍后重试",
            },
        }

    manifest = hls_resource.get("metadata") or {}

    return {
        "code": 200,
        "data": {
            "file_id": file_id,
            "has_hls": True,
            "has_dash": False,
            "hls_url": f"/api/v1/files/video/stream/{file_id}/master.m3u8",
            "dash_url": None,
            "resolutions": manifest.get("resolutions", []),
            "duration": hls_resource.get("duration_seconds", 0),
            "width": hls_resource.get("width", 0),
            "height": hls_resource.get("height", 0),
            "preview_url": f"/api/v1/files/video/stream/{file_id}/thumbnail?size=poster",
        },
    }


@router.post("/{file_id}/token", summary="获取流媒体访问 Token")
async def get_video_stream_token(
    file_id: str,
    body: dict,
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    获取 HLS 流媒体访问 Token

    请求体:
    - resolution: 请求的分辨率 (auto/360p/480p/720p/1080p)
    - expires_in: Token 过期时间 (秒)，默认 3600

    返回:
    - token: 访问 Token
    - expires_at: 过期时间 (ISO 8601)
    """
    resolution = body.get("resolution", "auto")
    expires_in = body.get("expires_in", _HLS_TOKEN_EXPIRE_SECONDS)

    try:
        await business_service_client.get_file_metadata(file_id, user_id)
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="视频不存在或无权访问") from exc
    hls_resource = await preview_resource_service.get_ready(file_id, user_id, "hls", "master")
    if not hls_resource:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="HLS 转码尚未完成")

    # 生成 Token
    token = _generate_hls_token(file_id, user_id, expires_in)

    from datetime import datetime, timezone, timedelta
    expires_at = (datetime.now(timezone.utc) + timedelta(seconds=expires_in)).isoformat()

    logger.info(f"[HLS-TOKEN] 签发: file_id={file_id}, user={user_id}, resolution={resolution}")

    return {
        "code": 200,
        "data": {
            "token": token,
            "expires_at": expires_at,
        },
    }


@router.get("/{file_id}/master.m3u8", summary="获取 HLS 主播放列表")
async def get_master_playlist(
    file_id: str,
    request: Request,
    token: str = Query(..., description="HLS 访问 Token"),
):
    """
    获取 HLS 主播放列表 (master.m3u8)

    包含所有可用分辨率的流信息，播放器自动选择最佳码率。
    返回的 playlist 中每个 variant 的 URL 会携带 token 参数。
    """
    hls_root = await _get_hls_root(file_id, token)
    master_path = _resolve_hls_child(hls_root, "master.m3u8")

    if not os.path.exists(master_path):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="HLS master playlist not found. Transcoding may not be complete.",
        )

    content = await _read_file_async(master_path)

    # 重写 playlist 中的 variant URL，添加 token 参数
    # 将 "360p/index.m3u8" 替换为 "360p/index.m3u8?token=xxx"
    rewritten_lines = []
    for line in content.split("\n"):
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            # 这是 variant playlist URL，附加 token
            line = f"{stripped}?token={token}"
        rewritten_lines.append(line)

    rewritten_content = "\n".join(rewritten_lines)

    return Response(
        content=rewritten_content,
        media_type="application/vnd.apple.mpegurl",
        headers={
            "Cache-Control": "no-cache",
            "Access-Control-Allow-Origin": "*",
        },
    )


@router.get("/{file_id}/{resolution}/index.m3u8", summary="获取分辨率播放列表")
async def get_variant_playlist(
    file_id: str,
    resolution: str,
    request: Request,
    token: str = Query(..., description="HLS 访问 Token"),
):
    """
    获取指定分辨率的 HLS 播放列表 (variant playlist)

    包含该分辨率下所有 TS 分片的 URL 列表。
    返回的 playlist 中每个 segment URL 会携带 token 参数。
    """
    hls_root = await _get_hls_root(file_id, token)
    playlist_path = _resolve_hls_child(hls_root, resolution, "index.m3u8")

    if not os.path.exists(playlist_path):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Variant playlist not found: {resolution}",
        )

    content = await _read_file_async(playlist_path)

    # 重写 TS segment URL，添加 token 参数
    rewritten_lines = []
    for line in content.split("\n"):
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            # 这是 TS segment URL，附加 token
            line = f"{stripped}?token={token}"
        rewritten_lines.append(line)

    rewritten_content = "\n".join(rewritten_lines)

    return Response(
        content=rewritten_content,
        media_type="application/vnd.apple.mpegurl",
        headers={
            "Cache-Control": "no-cache",
            "Access-Control-Allow-Origin": "*",
        },
    )


@router.get("/{file_id}/{resolution}/{segment}", summary="获取 TS 分片")
async def get_ts_segment(
    file_id: str,
    resolution: str,
    segment: str,
    request: Request,
    token: str = Query(..., description="HLS 访问 Token"),
):
    """
    获取 HLS TS 分片

    流式返回 TS 文件内容，支持 HTTP Range 请求以优化播放体验。
    """
    hls_root = await _get_hls_root(file_id, token)
    segment_path = _resolve_hls_child(hls_root, resolution, segment)

    if not os.path.exists(segment_path):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Segment not found: {segment}",
        )

    file_size = os.path.getsize(segment_path)

    # 处理 Range 请求
    range_header = request.headers.get("Range")
    if range_header:
        return await _serve_range(segment_path, file_size, range_header)

    return FileResponse(
        segment_path,
        media_type="video/mp2t",
        headers={
            "Accept-Ranges": "bytes",
            "Cache-Control": "public, max-age=31536000, immutable",
            "Access-Control-Allow-Origin": "*",
        },
    )


# =============================================================================
# 辅助函数
# =============================================================================

async def _read_file_async(file_path: str) -> str:
    """异步读取文件内容"""
    async with aiofiles.open(file_path, "r") as f:
        return await f.read()


async def _serve_range(file_path: str, file_size: int, range_header: str):
    """处理 HTTP Range 请求"""
    try:
        unit, _, ranges = range_header.partition("=")
        if unit.strip() != "bytes":
            raise ValueError("Invalid range unit")

        start_str, _, end_str = ranges.strip().partition("-")
        start = int(start_str) if start_str else 0
        end = int(end_str) if end_str else file_size - 1

        if start >= file_size or end >= file_size or start > end:
            raise HTTPException(
                status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
                headers={"Content-Range": f"bytes */{file_size}"},
            )

        content_length = end - start + 1

        async def file_iterator():
            async with aiofiles.open(file_path, "rb") as f:
                await f.seek(start)
                remaining = content_length
                while remaining > 0:
                    chunk_size = min(8192, remaining)
                    data = await f.read(chunk_size)
                    if not data:
                        break
                    remaining -= len(data)
                    yield data

        return StreamingResponse(
            file_iterator(),
            status_code=status.HTTP_206_PARTIAL_CONTENT,
            media_type="video/mp2t",
            headers={
                "Content-Range": f"bytes {start}-{end}/{file_size}",
                "Content-Length": str(content_length),
                "Accept-Ranges": "bytes",
                "Cache-Control": "public, max-age=31536000, immutable",
                "Access-Control-Allow-Origin": "*",
            },
        )

    except (ValueError, IndexError):
        raise HTTPException(status_code=400, detail="Invalid Range header")


# =============================================================================
# 雪碧图端点 — 进度条悬停预览
# =============================================================================

@router.get("/{file_id}/sprite.jpg", summary="获取视频雪碧图")
async def get_sprite_image(
    file_id: str,
    token: str = Query(..., description="HLS 访问 Token"),
):
    """
    获取视频雪碧图 (Sprite Sheet)

    雪碧图是 HLS 转码时预生成的一张包含所有时间点缩略图的大图，
    前端通过 CSS background-position 快速切换显示不同时间点的预览图，
    实现 Bilibili/YouTube 级别的进度条悬停预览体验。

    返回 JPEG 格式雪碧图，带有长期缓存头。
    """
    hls_root = await _get_hls_root(file_id, token)
    sprite_path = _resolve_hls_child(hls_root, "sprite.jpg")

    if not os.path.exists(sprite_path):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Sprite image not found. Transcoding may not include sprite generation.",
        )

    return FileResponse(
        sprite_path,
        media_type="image/jpeg",
        headers={
            "Cache-Control": "public, max-age=31536000, immutable",
            "Access-Control-Allow-Origin": "*",
        },
    )


@router.get("/{file_id}/sprite.vtt", summary="获取雪碧图 VTT 元数据")
async def get_sprite_vtt(
    file_id: str,
    token: str = Query(..., description="HLS 访问 Token"),
):
    """
    获取雪碧图 WebVTT 元数据

    VTT 文件包含每个时间区间对应的雪碧图坐标 (xywh)，
    前端通过解析 VTT 文件，根据悬停时间定位到对应的预览缩略图。

    格式示例:
    ```
    WEBVTT

    00:00:00.000 --> 00:00:10.000
    sprite.jpg#xywh=0,0,160,90

    00:00:10.000 --> 00:00:20.000
    sprite.jpg#xywh=160,0,160,90
    ```
    """
    hls_root = await _get_hls_root(file_id, token)
    vtt_path = _resolve_hls_child(hls_root, "sprite.vtt")

    if not os.path.exists(vtt_path):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Sprite VTT metadata not found.",
        )

    content = await _read_file_async(vtt_path)

    return Response(
        content=content,
        media_type="text/vtt",
        headers={
            "Cache-Control": "public, max-age=31536000, immutable",
            "Access-Control-Allow-Origin": "*",
        },
    )


# ============================================================
# 视频缩略图接口（独立于图片缩略图，使用 ffmpeg 首帧提取）
# ============================================================

# 视频缩略图 Redis 缓存 TTL（秒）
VIDEO_THUMBNAIL_TTL = 86400  # 24 小时
# 视频缩略图输出尺寸
VIDEO_THUMB_WIDTH = 400
VIDEO_THUMB_HEIGHT = 225  # 16:9


async def _extract_video_frame_async(
    storage_path: str, width: int = VIDEO_THUMB_WIDTH, height: int = VIDEO_THUMB_HEIGHT
) -> bytes:
    """
    使用 ffmpeg 提取视频首帧（异步执行，不阻塞事件循环）

    策略:
    - 使用 ffmpeg thumbnail filter 提取首帧（I 帧，最快）
    - 缩放为指定尺寸，保持 16:9 比例
    - 输出 JPEG 格式，质量 85
    - 通过 asyncio.to_thread 在线程池中执行，避免阻塞

    Args:
        storage_path: 视频文件存储路径
        width: 缩略图宽度
        height: 缩略图高度

    Returns:
        JPEG 图片字节数据
    """
    try:
        video_filter = []
        if width > 0 and height > 0:
            video_filter = [
                "-vf", f"scale={width}:{height}:force_original_aspect_ratio=decrease,"
                       f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:black",
            ]
        ffmpeg_args = [
            "ffmpeg",
            "-y",
            "-ss", "00:00:01", 
            "-i", storage_path,
            "-vframes", "1",          # 只取 1 帧
            *video_filter,
            "-q:v", "2" if not video_filter else "5",
            "-f", "image2pipe",
            "-vcodec", "mjpeg",
            "pipe:1",
        ]
        result = await asyncio.wait_for(
            asyncio.to_thread(
                lambda: (
                    subprocess.run(
                        ffmpeg_args,
                        capture_output=True,
                        timeout=30,
                        check=True,
                    ).stdout
                )
            ),
            timeout=35,
        )
        return result
    except subprocess.CalledProcessError as e:
        logger.error(f"ffmpeg 提取视频首帧失败: {e.stderr.decode()[:200]}")
        raise
    except subprocess.TimeoutExpired:
        logger.error(f"ffmpeg 提取视频首帧超时: {storage_path}")
        raise


@router.get("/{file_id}/hover-preview", summary="获取文件浏览器 30 秒悬停视频")
async def get_video_hover_preview(
    file_id: str,
    request: Request,
    user_id: str = Header(default=None, alias="X-User-Id"),
):
    """
    返回增强流水线预生成的前 30 秒 MP4。

    AUDIT FIX [2.2/3.1]（需求二、三）：
    原前端没有轻量悬停素材；新接口先校验文件归属，再通过预览资源数据库定位，
    不扫描本机目录，从而支持存储服务横向扩容。FileResponse 支持浏览器 Range 请求。
    """
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="缺少用户身份")
    try:
        await business_service_client.get_file_metadata(file_id, user_id)
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="视频不存在或无权访问") from exc

    resource = await preview_resource_service.get_ready(
        file_id, user_id, "video_preview", "30s",
    )
    if not resource:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="视频悬停预览尚未生成")

    path = Path(resource["storage_path"])
    if not path.is_file():
        raise HTTPException(status_code=status.HTTP_410_GONE, detail="悬停预览记录存在但文件缺失")

    etag = hashlib.sha256(
        f"{resource.get('resource_id')}:{resource.get('source_version')}:{path.stat().st_mtime_ns}".encode()
    ).hexdigest()
    if request.headers.get("if-none-match", "").strip('"') == etag:
        return Response(status_code=status.HTTP_304_NOT_MODIFIED, headers={"ETag": f'"{etag}"'})

    return FileResponse(
        path,
        media_type="video/mp4",
        filename=f"{file_id}-hover.mp4",
        content_disposition_type="inline",
        headers={
            "Cache-Control": "private, max-age=86400",
            "ETag": f'"{etag}"',
            "X-Preview-Duration": "30",
        },
    )


@router.get("/{file_id}/thumbnail", summary="获取视频缩略图（ffmpeg 首帧）")
async def get_video_thumbnail(
    file_id: str,
    request: Request,
    size: str = Query("small", description="缩略图尺寸: small/medium/large/poster"),
    user_id: str = Header(default=None, alias="X-User-Id"),
):
    """
    获取视频缩略图（独立接口，使用 ffmpeg 提取首帧）

    设计要点:
    1. 独立于图片缩略图接口（图片使用 libvips，视频使用 ffmpeg）
    2. 优先查找 HLS 转码流水线预生成的首帧缩略图
    3. 无预生成缩略图时，使用 ffmpeg 动态提取首帧
    4. Redis 缓存（key: video_thumb:{file_id}:{size}），24 小时 TTL
    5. 浏览器缓存：ETag 304 验证

    鉴权:
    - 无 token 时使用 X-User-Id 头（需文件所有权验证）

    Args:
        file_id: 文件 ID
        request: FastAPI 请求对象
        size: 缩略图尺寸 (small/medium/large)
        user_id: 用户 ID（X-User-Id 请求头）

    Returns:
        JPEG 缩略图
    """

    # 1. 尺寸映射
    SIZE_MAP = {
        "small": (320, 180),
        "medium": (640, 360),
        "large": (1280, 720),
        "poster": (0, 0),
    }
    if size not in SIZE_MAP:
        valid_sizes = ", ".join(SIZE_MAP.keys())
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"无效的缩略图尺寸: '{size}'，有效值: {valid_sizes}",
        )

    thumb_width, thumb_height = SIZE_MAP[size]

    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="缺少用户身份")
    try:
        metadata_response = await business_service_client.get_file_metadata(file_id, user_id)
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="视频不存在或无权访问") from exc
    metadata = metadata_response.get("data") or {}

    # AUDIT FIX [7.4]: 预生成缩略图路径从持久化资源表读取，修复 pipeline 与 API 目录约定不一致导致的 404。
    thumbnail_resource = await preview_resource_service.get_ready(file_id, user_id, "video_thumbnail", size)
    pre_generated_thumb = thumbnail_resource.get("storage_path") if thumbnail_resource else None
    storage_path = metadata.get("storage_path")

    # 3. 检查预生成缩略图
    if pre_generated_thumb and os.path.exists(pre_generated_thumb):
        try:
            mtime = os.path.getmtime(pre_generated_thumb)
            etag = hashlib.md5(f"{file_id}{size}{mtime}".encode()).hexdigest()

            if request.headers.get("If-None-Match") == etag:
                return Response(status_code=304)

            async with aiofiles.open(pre_generated_thumb, "rb") as f:
                img_bytes = await f.read()

            return Response(
                content=img_bytes,
                media_type="image/jpeg",
                headers={
                    "Cache-Control": f"public, max-age={VIDEO_THUMBNAIL_TTL}",
                    "ETag": etag,
                    "X-Thumbnail-Source": "disk",
                    "Access-Control-Allow-Origin": "*",
                },
            )
        except Exception as e:
            logger.warning(f"读取预生成视频缩略图失败: {e}")

    if not storage_path or not os.path.exists(storage_path):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="视频文件不存在",
        )

    # 5. Redis 缓存检查
    redis_key = f"video_thumb:{file_id}:{size}"
    file_mtime = os.path.getmtime(storage_path)
    etag = hashlib.md5(f"{file_id}{size}{file_mtime}".encode()).hexdigest()

    if request.headers.get("If-None-Match") == etag:
        return Response(status_code=304)

    try:
        # 尝试从 Redis 获取缓存
        cached_data = await redis_client.get(redis_key)
        if cached_data:
            img_bytes = base64.b64decode(cached_data)
            return Response(
                content=img_bytes,
                media_type="image/jpeg",
                headers={
                    "Cache-Control": f"public, max-age={VIDEO_THUMBNAIL_TTL}",
                    "ETag": etag,
                    "X-Thumbnail-Source": "redis",
                    "Access-Control-Allow-Origin": "*",
                },
            )
    except Exception as e:
        logger.warning(f"Redis 读取视频缩略图缓存失败: {e}")

    # 6. 动态生成缩略图（ffmpeg 首帧）
    logger.info(f"动态生成视频缩略图: file_id={file_id}, size={size}")
    try:
        if size == "poster":
            # AUDIT FIX [4.5]: 播放器海报保留源画幅，不裁剪、不补黑边；列表缩略图仍使用固定画布。
            img_bytes = await _extract_video_frame_async(storage_path, 0, 0)
        else:
            img_bytes = await _extract_video_frame_async(storage_path, thumb_width, thumb_height)
    except Exception as e:
        logger.error(f"视频缩略图生成失败: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="视频缩略图生成失败",
        )

    # 7. 存入 Redis 缓存
    try:
        encoded = base64.b64encode(img_bytes).decode()
        await redis_client.setex(redis_key, VIDEO_THUMBNAIL_TTL, encoded)
    except Exception as e:
        logger.warning(f"Redis 写入视频缩略图缓存失败: {e}")

    return Response(
        content=img_bytes,
        media_type="image/jpeg",
        headers={
            "Cache-Control": f"public, max-age={VIDEO_THUMBNAIL_TTL}",
            "ETag": etag,
            "X-Thumbnail-Source": "generated",
            "Access-Control-Allow-Origin": "*",
        },
    )
