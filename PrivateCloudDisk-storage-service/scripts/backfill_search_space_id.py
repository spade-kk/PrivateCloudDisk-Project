#!/usr/bin/env python3
"""
历史 OpenSearch 文件索引空间字段回填工具。

空间管理能力全量集成（需求五-9、七）：
数据库迁移 007 已将文件元数据回填到个人/协作空间，但旧 OpenSearch 文档没有
space_id。查询端仅对个人历史文档保留 user_id 兼容，自定义空间必须执行本脚本后
才能在全文搜索中出现。

安全约束：
1. 默认 dry-run，只有显式传入 --apply 才修改 OpenSearch；
2. 空间归属只读取 MySQL 文件事实表，不根据路径或上传者猜测；
3. 只更新已有索引文档，不创建缺失文档、不重新抽取内容；
4. 可重复执行，更新操作天然幂等。
"""
from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from pathlib import Path

SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

from app.db.database import close_database, get_database_pool
from core.config import settings
from core.search.opensearch_client import close_opensearch_client, get_opensearch_client

logger = logging.getLogger("search_space_backfill")


async def _load_scopes(limit: int) -> list[dict]:
    pool = await get_database_pool()
    async with pool.acquire() as connection:
        async with connection.cursor() as cursor:
            sql = (
                "SELECT BIN_TO_UUID(file_id) AS file_id, "
                "BIN_TO_UUID(file_space_id) AS space_id "
                "FROM pcd_file_info_table "
                "WHERE file_space_id IS NOT NULL ORDER BY file_uploaded_time ASC"
            )
            params: tuple[int, ...] = ()
            if limit > 0:
                sql += " LIMIT %s"
                params = (limit,)
            await cursor.execute(sql, params)
            return list(await cursor.fetchall())


async def _update_existing_document(client, index_name: str, file_id: str, space_id: str) -> bool:
    exists = await client.exists(index=index_name, id=file_id)
    if not exists:
        return False
    await client.update(
        index=index_name,
        id=file_id,
        body={"doc": {"space_id": space_id}},
        refresh=False,
    )
    return True


async def main() -> int:
    parser = argparse.ArgumentParser(description="历史 OpenSearch 索引 space_id 幂等回填")
    parser.add_argument("--apply", action="store_true", help="确认写入；默认仅 dry-run")
    parser.add_argument("--limit", type=int, default=0, help="最多处理文件数，0 表示不限")
    args = parser.parse_args()

    scopes = await _load_scopes(max(0, args.limit))
    logger.info("发现 %s 个已归属空间的文件，模式=%s", len(scopes), "apply" if args.apply else "dry-run")
    if not args.apply:
        custom_count = sum(1 for item in scopes if item.get("space_id"))
        logger.info("计划回填文档=%s；未连接或修改 OpenSearch", custom_count)
        return 0

    client = get_opensearch_client()
    updated = 0
    missing = 0
    for item in scopes:
        for index_name in (settings.opensearch_file_index, settings.opensearch_content_index):
            if await _update_existing_document(
                client, index_name, item["file_id"], item["space_id"]
            ):
                updated += 1
            else:
                missing += 1
    await client.indices.refresh(
        index=f"{settings.opensearch_file_index},{settings.opensearch_content_index}"
    )
    logger.info("空间字段回填完成: updated=%s, missing=%s", updated, missing)
    return 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    async def _entrypoint() -> int:
        try:
            return await main()
        finally:
            await close_database()
            await close_opensearch_client()

    raise SystemExit(asyncio.run(_entrypoint()))
