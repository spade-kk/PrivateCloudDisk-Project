#!/usr/bin/env python3
"""
历史预览媒体资源入库工具。

需求四-1：
扫描 uploads 根目录中的 HLS、视频悬停素材、视频转码、图片/视频/Office 缩略图、
Office/PDF 预览与压缩包目录树，并批量写入 pcd_preview_resource_table。
Markdown 已改为前端读取原始内容并实时渲染，本脚本永久忽略历史 markdown_html 资源。

安全约束：
1. 默认仅 dry-run，必须显式传入 --apply 才写数据库；
2. 文件 ID 必须为标准 UUID，用户 ID 从文件元数据表读取，不猜测归属；
3. 使用现有 Repository 的幂等 upsert，脚本可重复执行；
4. 不修改或删除任何磁盘文件。
"""
from __future__ import annotations

import argparse
import asyncio
import json
import logging
import re
import sys
from pathlib import Path
from typing import Any

# 允许从项目根目录直接执行脚本。
SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

from app.db.database import close_database, get_database_pool
from app.models.preview_resource import PreviewResource
from app.repositories.preview_resource_repository import preview_resource_repository
from core.config import settings

logger = logging.getLogger("preview_resource_backfill")
UUID_PATTERN = re.compile(
    r"(?P<file_id>[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-"
    r"[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})"
)


async def _find_owner(file_id: str) -> str | None:
    """从主文件元数据表读取归属用户，避免历史资源被错误绑定到其他账号。"""
    pool = await get_database_pool()
    async with pool.acquire() as connection:
        async with connection.cursor() as cursor:
            await cursor.execute(
                "SELECT BIN_TO_UUID(file_author_id) AS user_id "
                "FROM pcd_file_info_table WHERE file_id=UUID_TO_BIN(%s)",
                (file_id,),
            )
            row = await cursor.fetchone()
    return row["user_id"] if row else None


def _resource(
    file_id: str,
    user_id: str,
    resource_type: str,
    variant: str,
    path: Path,
    mime_type: str,
    **extra: Any,
) -> PreviewResource:
    return PreviewResource(
        file_id=file_id,
        user_id=user_id,
        resource_type=resource_type,
        resource_variant=variant,
        storage_path=str(path.resolve()),
        mime_type=mime_type,
        resource_status="ready",
        size_bytes=path.stat().st_size if path.is_file() else 0,
        metadata={"backfilled": True, **extra.pop("metadata", {})},
        **extra,
    )


async def _discover(root: Path) -> list[PreviewResource]:
    """发现已存在的可管理资源；Markdown HTML 按需求显式排除。"""
    discovered: list[PreviewResource] = []
    owner_cache: dict[str, str | None] = {}

    async def owner_of(file_id: str) -> str | None:
        if file_id not in owner_cache:
            owner_cache[file_id] = await _find_owner(file_id)
        return owner_cache[file_id]

    hls_root = root / "hls"
    if hls_root.is_dir():
        for master in hls_root.glob("*/master.m3u8"):
            match = UUID_PATTERN.fullmatch(master.parent.name)
            if not match:
                logger.warning("跳过非 UUID HLS 目录: %s", master.parent)
                continue
            file_id = match.group("file_id")
            user_id = await owner_of(file_id)
            if not user_id:
                logger.warning("跳过无文件元数据的 HLS 资源: file_id=%s", file_id)
                continue
            manifest_path = master.parent / "manifest.json"
            manifest: dict[str, Any] = {}
            if manifest_path.is_file():
                try:
                    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
                except (OSError, json.JSONDecodeError) as exc:
                    logger.warning("HLS manifest 读取失败: %s", exc)
            discovered.append(_resource(
                file_id, user_id, "hls", "master", master.parent,
                "application/vnd.apple.mpegurl",
                width=manifest.get("source_width"),
                height=manifest.get("source_height"),
                duration_seconds=manifest.get("duration"),
                metadata={
                    "manifest_path": str(manifest_path.resolve()) if manifest_path.is_file() else None,
                    "resolutions": manifest.get("resolutions", []),
                },
            ))

    patterns = [
        ("video_previews", "*_30s.mp4", "video_preview", "30s", "video/mp4"),
        ("transcoded", "*_*.mp4", "video_transcode", None, "video/mp4"),
        ("previews", "*_archive_tree.json", "archive", "tree", "application/json"),
        ("previews", "*.pdf", "office_pdf", "default", "application/pdf"),
    ]
    for directory, glob_pattern, resource_type, fixed_variant, mime_type in patterns:
        for path in (root / directory).glob(glob_pattern):
            match = UUID_PATTERN.search(path.name)
            if not match:
                continue
            file_id = match.group("file_id")
            user_id = await owner_of(file_id)
            if not user_id:
                logger.warning("跳过无文件元数据的历史资源: %s", path)
                continue
            variant = fixed_variant
            if variant is None:
                variant = path.stem[len(file_id) + 1:] or "default"
            discovered.append(_resource(
                file_id, user_id, resource_type, variant, path, mime_type,
                duration_seconds=30.0 if resource_type == "video_preview" else None,
            ))

    thumbnail_root = root / "thumbnails"
    if thumbnail_root.is_dir():
        for path in thumbnail_root.glob("*.jpg"):
            match = UUID_PATTERN.search(path.name)
            if not match:
                continue
            file_id = match.group("file_id")
            user_id = await owner_of(file_id)
            if not user_id:
                continue
            suffix = path.stem[len(file_id):].lstrip("_")
            if suffix.startswith("office_"):
                resource_type, variant = "office_thumbnail", suffix.removeprefix("office_")
            elif suffix == "pdf_preview":
                resource_type, variant = "office_thumbnail", "medium"
            elif (hls_root / file_id).is_dir() and suffix in {"poster", "large", "medium", "small"}:
                resource_type, variant = "video_thumbnail", suffix
            else:
                resource_type, variant = "thumbnail", suffix or "default"
            discovered.append(_resource(
                file_id, user_id, resource_type, variant, path, "image/jpeg",
            ))

    return discovered


async def main() -> int:
    parser = argparse.ArgumentParser(description="历史预览资源幂等回填")
    parser.add_argument(
        "--root",
        default="../../Uploads",
        help="uploads 根目录，默认读取 FILE_UPLOAD_DIR",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="确认写入数据库；未提供时仅输出 dry-run 统计",
    )
    args = parser.parse_args()
    root = Path(args.root).expanduser().resolve()
    if not root.is_dir():
        logger.error("uploads 根目录不存在: %s", root)
        return 2

    resources = await _discover(root)
    logger.info("发现 %s 条历史预览资源，模式=%s", len(resources), "apply" if args.apply else "dry-run")
    for resource in resources:
        logger.info(
            "%s file_id=%s type=%s variant=%s path=%s",
            "写入" if args.apply else "计划",
            resource.file_id,
            resource.resource_type,
            resource.resource_variant,
            resource.storage_path,
        )
        if args.apply:
            await preview_resource_repository.upsert(resource)
    return 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    async def _entrypoint() -> int:
        try:
            return await main()
        finally:
            # 在创建连接池的同一事件循环内关闭，避免跨 loop 释放连接。
            await close_database()

    raise SystemExit(asyncio.run(_entrypoint()))
