"""
OpenSearch 索引写入服务

负责将文件基本信息和内容写入 OpenSearch 索引
"""
from __future__ import annotations
import logging
from datetime import datetime, timezone
from typing import Any

from core.config import settings
from core.search.opensearch_client import get_opensearch_client, is_opensearch_available

logger = logging.getLogger("index_service")


class IndexService:
    """OpenSearch 索引写入服务"""

    @staticmethod
    async def index_file_basic(document: dict) -> bool:
        """
        写入文件基本信息索引

        Args:
            document: {
                file_id, user_id, space_id, node_id, filename, file_ext,
                file_type, file_category, size_bytes, status,
                created_at, updated_at, tags, summary, extraction
            }
        """
        # REQ-WORKER-TASKBUS-2026-07：启动降级后不再触发后续客户端连接异常。
        if not is_opensearch_available():
            logger.warning("OpenSearch 不可用，跳过文件基本信息索引: file_id=%s", document.get("file_id"))
            return False
        try:
            document["indexed_at"] = datetime.now(timezone.utc).isoformat()

            client = get_opensearch_client()
            await client.index(
                index=settings.opensearch_file_index,
                id=document["file_id"],
                body=document,
                refresh=False,  # 不强制刷新，批量写入时提升性能
            )
            logger.debug(f"文件基本信息已索引: file_id={document.get('file_id')}")
            return True
        except Exception as e:
            logger.error(f"文件基本信息索引失败: file_id={document.get('file_id')}, error={e}")
            raise

    @staticmethod
    async def index_file_content(document: dict) -> bool:
        """
        写入文件内容索引

        Args:
            document: {
                file_id, user_id, space_id, filename, file_ext, created_at,
                content_text, content_chunks, ocr_text, image_labels, extraction
            }
        """
        # REQ-WORKER-TASKBUS-2026-07：内容增强必须 fail-open，避免索引依赖拖垮后处理流水线。
        if not is_opensearch_available():
            logger.warning("OpenSearch 不可用，跳过文件内容索引: file_id=%s", document.get("file_id"))
            return False
        try:
            document["indexed_at"] = datetime.now(timezone.utc).isoformat()

            client = get_opensearch_client()
            await client.index(
                index=settings.opensearch_content_index,
                id=document["file_id"],
                body=document,
                refresh=False,
            )
            logger.debug(f"文件内容已索引: file_id={document.get('file_id')}")
            return True
        except Exception as e:
            logger.error(f"文件内容索引失败: file_id={document.get('file_id')}, error={e}")
            raise

    @staticmethod
    async def delete_file_index(file_id: str) -> bool:
        """删除文件在两个索引中的文档"""
        if not is_opensearch_available():
            logger.debug("OpenSearch 不可用，跳过删除文件索引: file_id=%s", file_id)
            return False
        try:
            client = get_opensearch_client()

            # 删除基本信息索引
            try:
                await client.delete(
                    index=settings.opensearch_file_index,
                    id=file_id,
                    ignore=[404],
                )
            except Exception:
                pass  # 404 忽略

            # 删除内容索引
            try:
                await client.delete(
                    index=settings.opensearch_content_index,
                    id=file_id,
                    ignore=[404],
                )
            except Exception:
                pass

            logger.debug(f"文件索引已删除: file_id={file_id}")
            return True
        except Exception as e:
            logger.error(f"删除文件索引失败: file_id={file_id}, error={e}")
            return False
