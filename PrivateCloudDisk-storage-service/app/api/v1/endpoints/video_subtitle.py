"""
视频字幕 API 端点
提供视频字幕列表和字幕内容获取

API 列表:
- GET /files/video/subtitle/{file_id}        — 获取字幕列表
- GET /files/video/subtitle/{file_id}/{lang} — 获取字幕内容（WebVTT）

对标 YouTube/Bilibili 字幕体验：
- 支持多语言字幕（中/英/日/韩等）
- 返回 WebVTT 格式字幕内容
- 自动检测文件系统中存在的字幕文件
"""
from __future__ import annotations
import logging
import os
import glob as glob_mod
from pathlib import Path
from fastapi import APIRouter, Header, HTTPException, status
from fastapi.responses import PlainTextResponse

from app.core.business_service_client import BusinessServiceError, business_service_client
from app.services.preview_resource_service import preview_resource_service
from core.config import settings

logger = logging.getLogger("video_subtitle")

router = APIRouter(prefix="/files/video/subtitle", tags=["视频字幕"])

# 字幕语言映射
_LANG_LABELS = {
    "zh": "中文",
    "zh-CN": "中文（简体）",
    "zh-TW": "中文（繁体）",
    "en": "English",
    "ja": "日本語",
    "ko": "한국어",
    "fr": "Français",
    "de": "Deutsch",
    "es": "Español",
    "pt": "Português",
    "ru": "Русский",
    "ar": "العربية",
    "th": "ไทย",
    "vi": "Tiếng Việt",
}


@router.get("/{file_id}", summary="获取视频字幕列表")
async def get_video_subtitles(
    file_id: str,
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    获取视频可用的字幕列表

    自动扫描 HLS 目录下的 .vtt 字幕文件，
    返回字幕 ID、语言标签和获取 URL。
    """
    # AUDIT FIX [4.3]：字幕目录访问必须先验证用户、空间与文件归属，避免仅凭 UUID 枚举。
    try:
        await business_service_client.get_file_metadata(file_id, user_id)
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="视频不存在或无权访问") from exc
    # AUDIT FIX [3.1]：先用 HLS 资源台账确定目录，再在已授权根目录内读取字幕文件。
    hls_resource = await preview_resource_service.get_ready(file_id, user_id, "hls", "master")
    hls_dir = str(Path(hls_resource["storage_path"]).resolve()) if hls_resource else ""
    if hls_dir:
        try:
            Path(hls_dir).relative_to(Path(settings.file_upload_dir).resolve())
        except ValueError as exc:
            raise HTTPException(status_code=403, detail="HLS 资源路径不在允许范围内") from exc

    if not os.path.isdir(hls_dir):
        return {"code": 200, "data": {"subtitles": []}}

    subtitles = []
    base_url = f"/api/v1/files/video/subtitle/{file_id}"

    # 扫描字幕文件: *.vtt, *.srt
    vtt_pattern = os.path.join(hls_dir, "*.vtt")
    srt_pattern = os.path.join(hls_dir, "*.srt")
    sub_files = glob_mod.glob(vtt_pattern) + glob_mod.glob(srt_pattern)

    for sub_path in sub_files:
        filename = os.path.basename(sub_path)
        # 提取语言代码: 如 "zh-CN.vtt" -> "zh-CN"
        lang = os.path.splitext(filename)[0]

        # 处理常见命名: "subtitle_zh.vtt" -> "zh"
        if lang.startswith("subtitle_") or lang.startswith("sub_"):
            lang = lang.split("_", 1)[1] if "_" in lang else lang

        subtitles.append({
            "id": lang,
            "label": _LANG_LABELS.get(lang, lang),
            "url": f"{base_url}/{lang}",
        })

    logger.debug(f"字幕列表: file={file_id}, count={len(subtitles)}")

    return {"code": 200, "data": {"subtitles": subtitles}}


@router.get("/{file_id}/{lang}", summary="获取字幕内容")
async def get_video_subtitle_content(
    file_id: str,
    lang: str,
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    获取指定语言的字幕内容（WebVTT 格式）

    自动将 SRT 格式转换为 WebVTT 格式。
    若字幕文件不存在，返回 404。
    """
    # AUDIT FIX [4.3]：内容读取与字幕列表使用同一空间权限校验。
    try:
        await business_service_client.get_file_metadata(file_id, user_id)
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="视频不存在或无权访问") from exc
    hls_resource = await preview_resource_service.get_ready(file_id, user_id, "hls", "master")
    hls_dir = str(Path(hls_resource["storage_path"]).resolve()) if hls_resource else ""
    if hls_dir:
        try:
            Path(hls_dir).relative_to(Path(settings.file_upload_dir).resolve())
        except ValueError as exc:
            raise HTTPException(status_code=403, detail="HLS 资源路径不在允许范围内") from exc

    if not os.path.isdir(hls_dir):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="字幕文件不存在",
        )

    # 尝试多种文件命名
    candidates = [
        os.path.join(hls_dir, f"{lang}.vtt"),
        os.path.join(hls_dir, f"{lang}.srt"),
        os.path.join(hls_dir, f"subtitle_{lang}.vtt"),
        os.path.join(hls_dir, f"subtitle_{lang}.srt"),
        os.path.join(hls_dir, f"sub_{lang}.vtt"),
        os.path.join(hls_dir, f"sub_{lang}.srt"),
    ]

    sub_path = None
    for candidate in candidates:
        if os.path.isfile(candidate):
            sub_path = candidate
            break

    if not sub_path:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"字幕文件不存在: {lang}",
        )

    # 读取字幕内容
    with open(sub_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 如果是 SRT 格式，转换为 WebVTT
    if sub_path.endswith(".srt"):
        content = _srt_to_vtt(content)

    return PlainTextResponse(
        content=content,
        media_type="text/vtt",
        headers={
            "Cache-Control": "public, max-age=3600",
            "Access-Control-Allow-Origin": "*",
        },
    )


def _srt_to_vtt(srt_content: str) -> str:
    """将 SRT 字幕格式转换为 WebVTT 格式"""
    # WebVTT header
    vtt = "WEBVTT\n\n"

    # 替换 SRT 的时间格式: 00:00:00,000 --> 00:00:00.000
    # 同时移除序号行
    import re
    lines = srt_content.strip().split("\n")
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        # 跳过序号行（纯数字）
        if line.isdigit():
            i += 1
            continue
        # 时间行
        if "-->" in line:
            # 替换逗号为点
            vtt_line = line.replace(",", ".")
            vtt += vtt_line + "\n"
            i += 1
            # 后续文本行，直到空行
            while i < len(lines) and lines[i].strip():
                vtt += lines[i].strip() + "\n"
                i += 1
            vtt += "\n"
        else:
            i += 1

    return vtt
