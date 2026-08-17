"""受授权文件内容读取核心。

需求三-3/三-5：普通网盘下载、普通原始内容预览、分享下载和分享预览
只在授权校验和响应策略上不同；实际的路径边界、文件大小一致性、Range
解析和异步分块读取统一由本模块实现，防止四套逻辑产生安全漂移。
"""
from __future__ import annotations

import mimetypes
from pathlib import Path
from typing import Mapping, Optional

import aiofiles
from fastapi import HTTPException, status
from fastapi.responses import FileResponse, StreamingResponse

from core.config import settings


def safe_storage_path(raw_path: str, *, require_file: bool = True) -> Path:
    """确保内部元数据给出的路径位于文件服务上传根目录内。"""
    path = Path(raw_path or "").resolve()
    root = Path(settings.file_upload_dir).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="文件存储路径不在允许范围内") from exc
    if require_file and not path.is_file():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="文件不存在")
    return path


def parse_single_range(
    value: Optional[str],
    size: int,
    max_bytes: int,
) -> tuple[int, int] | None:
    """解析单一 HTTP bytes Range，并统一执行边界和单次读取上限校验。"""
    if not value:
        return None
    unit, separator, ranges = value.partition("=")
    if unit.strip().lower() != "bytes" or not separator or "," in ranges:
        raise HTTPException(status_code=400, detail="仅支持单个 bytes Range")
    start_text, dash, end_text = ranges.strip().partition("-")
    if not dash:
        raise HTTPException(status_code=400, detail="Range 格式无效")
    try:
        if not start_text:
            suffix = int(end_text)
            if suffix <= 0:
                raise ValueError
            start, end = max(0, size - suffix), size - 1
        else:
            start, end = int(start_text), int(end_text) if end_text else size - 1
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="Range 边界必须为非负整数") from exc
    if start < 0 or start >= size or end < start:
        raise HTTPException(
            status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
            detail="Range 超出文件范围",
            headers={"Content-Range": f"bytes */{size}"},
        )
    end = min(end, size - 1)
    if end - start + 1 > max_bytes:
        raise HTTPException(
            status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
            detail="单次请求 Range 超过允许上限",
            headers={"Content-Range": f"bytes */{size}", "X-Max-Range-Size": str(max_bytes)},
        )
    return start, end


async def serve_authorized_file(
    raw_path: str,
    *,
    file_name: str = "",
    media_type: str | None = None,
    range_header: Optional[str] = None,
    max_range_bytes: int,
    max_full_bytes: int | None = None,
    expected_size: int | None = None,
    content_disposition_type: str = "inline",
    cache_control: str = "private, no-store",
    extra_headers: Mapping[str, str] | None = None,
):
    """返回完整文件或受限 Range 流；调用方只需负责授权，不再复制文件读取逻辑。"""
    path = safe_storage_path(raw_path)
    actual_size = path.stat().st_size
    if expected_size is not None and actual_size != int(expected_size):
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="文件内容已变化，请重新授权")
    resolved_media_type = media_type or mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    headers = {
        "Accept-Ranges": "bytes",
        "Cache-Control": cache_control,
    }
    if extra_headers:
        headers.update(extra_headers)
    parsed = parse_single_range(range_header, actual_size, max_range_bytes)
    if parsed:
        start, end = parsed
        length = end - start + 1

        async def iterator():
            async with aiofiles.open(path, "rb") as source:
                await source.seek(start)
                remaining = length
                while remaining > 0:
                    chunk = await source.read(min(64 * 1024, remaining))
                    if not chunk:
                        break
                    remaining -= len(chunk)
                    yield chunk

        range_headers = {
            **headers,
            "Content-Range": f"bytes {start}-{end}/{actual_size}",
            "Content-Length": str(length),
        }
        return StreamingResponse(
            iterator(),
            status_code=status.HTTP_206_PARTIAL_CONTENT,
            media_type=resolved_media_type,
            headers=range_headers,
        )
    if max_full_bytes is not None and actual_size > max_full_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="大文件必须使用 Range 分段读取",
            headers={"X-Max-File-Size": str(max_full_bytes)},
        )
    return FileResponse(
        path,
        media_type=resolved_media_type,
        filename=file_name or path.name,
        content_disposition_type=content_disposition_type,
        headers=headers,
    )
