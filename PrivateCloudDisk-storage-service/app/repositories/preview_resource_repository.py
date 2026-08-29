"""预览资源 MySQL 持久层。"""
from __future__ import annotations

import logging
import json
from typing import Any, Iterable

from app.db.database import get_database_pool
from app.models.preview_resource import PreviewResource


class PreviewResourceRepository:
    """封装预览资源的事务写入与查询，禁止上层直接拼接 SQL。"""

    async def upsert(self, resource: PreviewResource) -> str:
        pool = await get_database_pool()
        sql = """
            INSERT INTO pcd_preview_resource_table (
                resource_id, file_id, user_id, space_id, resource_type, resource_variant,
                storage_backend, storage_path, mime_type, resource_status,
                size_bytes, checksum, width, height, duration_seconds, page_count,
                metadata_json, error_message, source_version, ready_at
            ) VALUES (
                UUID_TO_BIN(COALESCE(%s, UUID())), UUID_TO_BIN(%s), UUID_TO_BIN(%s),
                UUID_TO_BIN(NULLIF(%s, '')), %s, %s,
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, CAST(%s AS JSON), %s, %s,
                IF(%s = 'ready', CURRENT_TIMESTAMP(3), NULL)
            ) AS incoming
            ON DUPLICATE KEY UPDATE
                storage_backend=incoming.storage_backend, storage_path=incoming.storage_path,
                mime_type=incoming.mime_type, resource_status=incoming.resource_status,
                size_bytes=incoming.size_bytes, checksum=incoming.checksum,
                width=incoming.width, height=incoming.height,
                duration_seconds=incoming.duration_seconds, page_count=incoming.page_count,
                metadata_json=incoming.metadata_json, error_message=incoming.error_message,
                source_version=incoming.source_version,
                ready_at = IF(incoming.resource_status = 'ready', CURRENT_TIMESTAMP(3), pcd_preview_resource_table.ready_at),
                deleted_at=NULL
        """
        values = (
            resource.resource_id, resource.file_id, resource.user_id, resource.space_id,
            resource.resource_type,
            resource.resource_variant, resource.storage_backend, resource.storage_path,
            resource.mime_type, resource.resource_status, resource.size_bytes, resource.checksum,
            resource.width, resource.height, resource.duration_seconds, resource.page_count,
            json.dumps(resource.metadata, ensure_ascii=False), resource.error_message,
            resource.source_version, resource.resource_status,
        )
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(sql, values)
                await cursor.execute(
                    "SELECT BIN_TO_UUID(resource_id) resource_id FROM pcd_preview_resource_table "
                    "WHERE file_id=UUID_TO_BIN(%s) AND resource_type=%s AND resource_variant=%s",
                    (resource.file_id, resource.resource_type, resource.resource_variant),
                )
                row = await cursor.fetchone()
            await conn.commit()
        return row["resource_id"]

    async def list_by_file(
        self,
        file_id: str,
        user_id: str,
        include_deleted: bool = False,
        space_id: str | None = None,
    ) -> list[dict[str, Any]]:
        pool = await get_database_pool()
        deleted_clause = "" if include_deleted else "AND resource_status <> 'deleted'"
        # 需求二/五-8：显式空间请求必须使用 (space_id, file_id) 定位。
        # 无请求头的旧客户端仍由主业务服务先完成个人空间授权，此处保留 file_id + user_id 兼容查询。
        space_clause = (
            "AND space_id=UUID_TO_BIN(%s)"
            if space_id
            else "AND user_id=UUID_TO_BIN(%s)"
        )
        sql = f"""
            SELECT BIN_TO_UUID(resource_id) resource_id, BIN_TO_UUID(file_id) file_id,
                   BIN_TO_UUID(user_id) user_id, BIN_TO_UUID(space_id) space_id,
                   resource_type, resource_variant,
                   storage_backend, storage_path, mime_type, resource_status, size_bytes,
                   checksum, width, height, duration_seconds, page_count, metadata_json,
                   error_message, source_version, created_at, updated_at, ready_at
            FROM pcd_preview_resource_table
            WHERE file_id=UUID_TO_BIN(%s) {space_clause} {deleted_clause}
            ORDER BY resource_type, resource_variant
        """
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(sql, (file_id, space_id or user_id))
                rows = await cursor.fetchall()
        return [self._normalize(row) for row in rows]

    async def get_ready(
        self,
        file_id: str,
        user_id: str,
        resource_type: str,
        variant: str = "default",
        space_id: str | None = None,
    ) -> dict[str, Any] | None:
        resources = await self.list_by_file(file_id, user_id, space_id=space_id)
        return next((r for r in resources if r["resource_type"] == resource_type and r["resource_variant"] == variant and r["resource_status"] == "ready"), None)

    async def mark_file_deleted(self, file_id: str, user_id: str, space_id: str | None = None) -> None:
        pool = await get_database_pool()
        scope_clause = "space_id=UUID_TO_BIN(%s)" if space_id else "user_id=UUID_TO_BIN(%s)"
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    "UPDATE pcd_preview_resource_table SET resource_status='deleted', deleted_at=CURRENT_TIMESTAMP(3) "
                    f"WHERE file_id=UUID_TO_BIN(%s) AND {scope_clause}",
                    (file_id, space_id or user_id),
                )
            await conn.commit()

    async def hard_delete_file_records(
        self,
        file_id: str,
        user_id: str,
        space_id: str | None = None,
    ) -> None:
        """
        需求七-1/2：在同一个 MySQL 事务中删除预览资源台账和播放进度。

        物理文件系统无法参加 MySQL ACID 事务，因此调用方必须先完成幂等物理删除，
        再调用本方法；任一 SQL 失败都会回滚两张表，消息随后重试。
        """
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            try:
                async with conn.cursor() as cursor:
                    await cursor.execute(
                        "DELETE FROM pcd_video_watch_progress_table "
                        "WHERE user_id=UUID_TO_BIN(%s) AND file_id=UUID_TO_BIN(%s)",
                        (user_id, file_id),
                    )
                    await cursor.execute(
                        "DELETE FROM pcd_preview_resource_table "
                        "WHERE file_id=UUID_TO_BIN(%s) AND "
                        + ("space_id=UUID_TO_BIN(%s)" if space_id else "user_id=UUID_TO_BIN(%s)"),
                        (file_id, space_id or user_id),
                    )
                await conn.commit()
            except Exception:
                await conn.rollback()
                raise

    async def count_ready_videos(self, user_id: str, space_id: str | None = None) -> int:
        pool = await get_database_pool()
        scope_clause = "space_id=UUID_TO_BIN(%s)" if space_id else "user_id=UUID_TO_BIN(%s)"
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    "SELECT COUNT(DISTINCT file_id) total FROM pcd_preview_resource_table "
                    f"WHERE {scope_clause} AND resource_type='hls' AND resource_status='ready'",
                    (space_id or user_id,),
                )
                row = await cursor.fetchone()
        return int(row["total"])

    @staticmethod
    def _normalize(row: dict[str, Any]) -> dict[str, Any]:
        result = dict(row)
        raw_metadata = result.pop("metadata_json", None)
        if isinstance(raw_metadata, str):
            try:
                result["metadata"] = json.loads(raw_metadata)
            except json.JSONDecodeError:
                result["metadata"] = {}
        else:
            result["metadata"] = raw_metadata or {}
        for key, value in list(result.items()):
            if hasattr(value, "isoformat"):
                result[key] = value.isoformat()
            elif key == "duration_seconds" and value is not None:
                result[key] = float(value)
        return result


preview_resource_repository = PreviewResourceRepository()
