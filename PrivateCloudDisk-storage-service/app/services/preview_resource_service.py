"""预览资源领域服务：数据库事实源 + Redis cache-aside。"""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any, Iterable

from app.core.redis_client import redis_client
from app.core.space_context import get_current_space_id
from app.models.preview_resource import PreviewResource
from app.repositories.preview_resource_repository import preview_resource_repository
from core.config import settings

logger = logging.getLogger("preview_resource_service")


class PreviewResourceService:
    def _cache_key(self, user_id: str, file_id: str, space_id: str | None = None) -> str:
        # 需求五-8：共享空间成员应命中同一资源缓存；旧客户端无空间头时仍保留用户维度。
        return f"preview:resources:{space_id or user_id}:{file_id}"

    async def list_resources(
        self,
        file_id: str,
        user_id: str,
        space_id: str | None = None,
    ) -> list[dict[str, Any]]:
        effective_space_id = space_id or get_current_space_id()
        key = self._cache_key(user_id, file_id, effective_space_id)
        cached = None
        try:
            cached = await redis_client.get(key)
        except Exception as exc:
            logger.warning("Redis 预览资源缓存不可用，降级读取数据库: %s", exc)
        cached = False
        if cached:
            try:
                cached_json = json.loads(cached)
                if cached_json:
                    logger.info("Redis 缓存查询 命中 Key: %s", key)
                    return cached_json
                logger.warning("Redis 预览资源缓存不可用，降级读取数据库: Redis 预览资源缓存为空 %s", cached_json)
            except json.JSONDecodeError:
                try:
                    logger.warning("缓存数据 JSON 解析失败，降级读取数据库")
                    await redis_client.delete(key)
                except Exception:
                    pass

        # AUDIT FIX [7.4]: DB 是存在性判断的事实源，避免多实例读取各自本地文件夹产生漂移。
        resources = await preview_resource_repository.list_by_file(
            file_id, user_id, space_id=effective_space_id,
        )

        try:
            await redis_client.setex(key, settings.preview_resource_cache_ttl, json.dumps(resources, ensure_ascii=False))
        except Exception as exc:
            logger.warning("Redis 预览资源缓存写入失败，本次继续返回数据库结果: %s", exc)
        return resources

    async def get_ready(
        self,
        file_id: str,
        user_id: str,
        resource_type: str,
        variant: str = "default",
        space_id: str | None = None,
    ) -> dict[str, Any] | None:
        resources = await self.list_resources(file_id, user_id, space_id)
        return next((item for item in resources if item["resource_type"] == resource_type and item["resource_variant"] == variant and item["resource_status"] == "ready"), None)

    async def upsert(self, resource: PreviewResource) -> str:
        resource_id = await preview_resource_repository.upsert(resource)
        try:
            await redis_client.delete(
                self._cache_key(resource.user_id, resource.file_id),
                self._cache_key(resource.user_id, resource.file_id, resource.space_id),
            )
        except Exception as exc:
            logger.warning("预览资源已持久化，但 Redis 缓存失效失败: %s", exc)
        return resource_id

    async def delete_file_resources(
        self,
        file_id: str,
        user_id: str,
        space_id: str | None = None,
    ) -> list[str]:
        """清理文件关联资源；仅允许删除配置上传根目录中的路径。"""
        resources = await preview_resource_repository.list_by_file(
            file_id, user_id, space_id=space_id,
        )
        root = Path(settings.file_upload_dir).resolve()
        deleted_paths: list[str] = []
        deletion_errors: list[str] = []
        for resource in resources:
            path = Path(resource["storage_path"]).resolve()
            try:
                path.relative_to(root)
            except ValueError:
                logger.error("拒绝删除越界预览资源: %s", path)
                deletion_errors.append(f"越界路径: {path}")
                continue
            try:
                if path.is_file():
                    path.unlink()
                    deleted_paths.append(str(path))
                elif path.is_dir():
                    # 目录逐层清理，避免使用宽泛递归命令。
                    # 子节点按路径深度倒序处理，避免词典序恰好先删除父目录而触发 Directory not empty。
                    for child in sorted(path.rglob("*"), key=lambda item: len(item.parts), reverse=True):
                        child.unlink() if child.is_file() or child.is_symlink() else child.rmdir()
                    path.rmdir()
                    deleted_paths.append(str(path))
            except FileNotFoundError:
                continue
            except OSError as exc:
                deletion_errors.append(f"{path}: {exc}")
                logger.warning("预览资源删除失败: path=%s, error=%s", path, exc)
        if deletion_errors:
            # 需求七-2：实体仍残留时保留数据库清单，确保消息重试仍可精确定位资源。
            raise OSError("；".join(deletion_errors[:5]))

        # 原行为仅标记 deleted；永久删除语义要求真正移除资源记录与播放进度。
        await preview_resource_repository.hard_delete_file_records(file_id, user_id, space_id)
        try:
            await redis_client.delete(
                self._cache_key(user_id, file_id),
                self._cache_key(user_id, file_id, space_id),
                f"video:progress:{user_id}:{file_id}",
                f"video:history:{user_id}:{file_id}",
            )
        except Exception as exc:
            logger.warning("资源记录已清理，但 Redis 缓存失效失败: %s", exc)
        return deleted_paths


preview_resource_service = PreviewResourceService()
