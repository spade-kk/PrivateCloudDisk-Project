"""视频播放进度与观看历史持久层。"""
from __future__ import annotations

from typing import Any

from app.db.database import get_database_pool
from app.core.space_context import get_current_space_id

import math


class VideoProgressRepository:
    @staticmethod
    def _sanitize_float(value: float, default: float = 0.0) -> float:
        """将异常浮点数（NaN, Inf）或负数置为默认值。"""
        if not math.isfinite(value) or value < 0:
            return default
        return value
    
    async def save(
        self, *, user_id: str, file_id: str, file_name: str, current_time: float,
        duration: float, resolution: str, playback_rate: float, completed: bool,
        space_id: str | None = None,
    ) -> None:
        # 清洗数值参数
        current_time = self._sanitize_float(current_time)
        duration = self._sanitize_float(duration)
        playback_rate = self._sanitize_float(playback_rate, default=1.0)  # 默认 1.0
        # 分享视频由主业务服务解析出空间后显式传入；普通请求继续读取 X-Space-Id 上下文。
        space_id = space_id or get_current_space_id()

        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                # 需求六-2：
                # 原行为每次进度心跳都执行 INSERT ... ON DUPLICATE KEY UPDATE；
                # 新行为在同一事务内先锁定主键，首播 INSERT，后续心跳只 UPDATE 现有行。
                await cursor.execute(
                    "SELECT 1 FROM pcd_video_watch_progress_table "
                    "WHERE user_id=UUID_TO_BIN(%s) AND file_id=UUID_TO_BIN(%s) FOR UPDATE",
                    (user_id, file_id),
                )
                exists = await cursor.fetchone()
                if exists:
                    await cursor.execute("""
                        UPDATE pcd_video_watch_progress_table
                        SET file_name=IF(%s='', file_name, %s),
                            space_id=COALESCE(UUID_TO_BIN(NULLIF(%s, '')), space_id),
                            current_time_seconds=%s,
                            duration_seconds=%s,
                            resolution=%s,
                            playback_rate=%s,
                            completed=%s,
                            last_watched_at=CURRENT_TIMESTAMP(3)
                        WHERE user_id=UUID_TO_BIN(%s) AND file_id=UUID_TO_BIN(%s)
                    """, (
                        file_name, file_name, space_id or "", current_time, duration, resolution,
                        playback_rate, completed, user_id, file_id,
                    ))
                else:
                    await cursor.execute("""
                        INSERT INTO pcd_video_watch_progress_table (
                            user_id, file_id, space_id, file_name, current_time_seconds, duration_seconds,
                            resolution, playback_rate, completed, last_watched_at
                        ) VALUES (
                            UUID_TO_BIN(%s), UUID_TO_BIN(%s), UUID_TO_BIN(NULLIF(%s, '')),
                            %s, %s, %s, %s, %s, %s,
                            CURRENT_TIMESTAMP(3)
                        )
                    """, (
                        user_id, file_id, space_id or "", file_name, current_time, duration,
                        resolution, playback_rate, completed,
                    ))
            await conn.commit()

    async def get(self, user_id: str, file_id: str, space_id: str | None = None) -> dict[str, Any] | None:
        pool = await get_database_pool()
        space_id = space_id or get_current_space_id()
        # 空间管理能力全量集成（需求五-8/9）：
        # 显式空间必须精确匹配；旧客户端无请求头时沿用 user_id 维度，兼容迁移前个人记录。
        scope_clause = "AND space_id=UUID_TO_BIN(%s)" if space_id else ""
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(f"""
                    SELECT BIN_TO_UUID(file_id) file_id, file_name,
                        current_time_seconds AS `current_time`, 
                        duration_seconds AS `duration`,
                        resolution, playback_rate, completed, updated_at, last_watched_at
                    FROM pcd_video_watch_progress_table
                    WHERE user_id=UUID_TO_BIN(%s) AND file_id=UUID_TO_BIN(%s)
                    {scope_clause}
                """, (user_id, file_id, *((space_id,) if space_id else ())))
                row = await cursor.fetchone()
        return self._normalize(row) if row else None

    async def list_history(self, user_id: str, limit: int, offset: int) -> tuple[list[dict[str, Any]], int]:
        pool = await get_database_pool()
        space_id = get_current_space_id()
        scope_clause = " AND space_id=UUID_TO_BIN(%s)" if space_id else ""
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    "SELECT COUNT(*) total FROM pcd_video_watch_progress_table "
                    f"WHERE user_id=UUID_TO_BIN(%s){scope_clause}",
                    (user_id, *((space_id,) if space_id else ())),
                )
                total_row = await cursor.fetchone()
                await cursor.execute(f"""
                    SELECT BIN_TO_UUID(file_id) file_id, file_name,
                           current_time_seconds watched_duration, duration_seconds total_duration,
                           completed, resolution, playback_rate, updated_at, last_watched_at
                    FROM pcd_video_watch_progress_table
                    WHERE user_id=UUID_TO_BIN(%s) {scope_clause}
                    ORDER BY last_watched_at DESC LIMIT %s OFFSET %s
                """, (user_id, *((space_id,) if space_id else ()), limit, offset))
                rows = await cursor.fetchall()
        return [self._normalize(row) for row in rows], int(total_row["total"])

    async def delete_by_file(self, user_id: str, file_id: str) -> None:
        pool = await get_database_pool()
        async with pool.acquire() as conn:
            async with conn.cursor() as cursor:
                await cursor.execute(
                    "DELETE FROM pcd_video_watch_progress_table WHERE user_id=UUID_TO_BIN(%s) AND file_id=UUID_TO_BIN(%s)",
                    (user_id, file_id),
                )
            await conn.commit()

    @staticmethod
    def _normalize(row: dict[str, Any]) -> dict[str, Any]:
        result = dict(row)
        for key, value in list(result.items()):
            if key in {"current_time", "duration", "watched_duration", "total_duration", "playback_rate"} and value is not None:
                result[key] = float(value)
            elif hasattr(value, "isoformat"):
                result[key] = value.isoformat()
            elif key == "completed":
                result[key] = bool(value)
        return result


video_progress_repository = VideoProgressRepository()
