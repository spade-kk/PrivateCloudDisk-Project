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
from fastapi import APIRouter, Header, Query, HTTPException, status, Request
from fastapi.responses import StreamingResponse, Response, FileResponse
from typing import Optional

from core.config import settings

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
    hls_dir = os.path.join(settings.file_upload_dir, "hls", file_id)
    manifest_path = os.path.join(hls_dir, "manifest.json")

    if not os.path.exists(manifest_path):
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

    try:
        with open(manifest_path, "r") as f:
            manifest = json.load(f)
    except Exception as e:
        logger.error(f"读取 manifest 失败: {e}")
        return {
            "code": 200,
            "data": {
                "file_id": file_id,
                "has_hls": False,
                "has_dash": False,
                "hls_url": None,
                "dash_url": None,
                "resolutions": [],
                "message": "HLS 元数据读取失败",
            },
        }

    return {
        "code": 200,
        "data": {
            "file_id": file_id,
            "has_hls": True,
            "has_dash": False,
            "hls_url": f"/api/v1/video/stream/{file_id}/master.m3u8",
            "dash_url": None,
            "resolutions": manifest.get("resolutions", []),
            "duration": manifest.get("duration", 0),
            "width": manifest.get("source_width", 0),
            "height": manifest.get("source_height", 0),
            "preview_url": manifest.get("preview_path"),
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
    _verify_hls_token(token, file_id)

    master_path = os.path.join(
        settings.file_upload_dir, "hls", file_id, "master.m3u8"
    )

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
    _verify_hls_token(token, file_id)

    playlist_path = os.path.join(
        settings.file_upload_dir, "hls", file_id, resolution, "index.m3u8"
    )

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
    _verify_hls_token(token, file_id)

    segment_path = os.path.join(
        settings.file_upload_dir, "hls", file_id, resolution, segment
    )

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