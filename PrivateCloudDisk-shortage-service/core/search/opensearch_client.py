"""
OpenSearch 异步客户端

提供连接的创建、索引确保、健康检查等能力
"""
from __future__ import annotations
import logging
from opensearchpy import AsyncOpenSearch

from core.config import settings

logger = logging.getLogger("opensearch_client")

# 全局单例
_os_client: AsyncOpenSearch | None = None


def get_opensearch_client() -> AsyncOpenSearch:
    """获取或创建 OpenSearch 异步客户端 (单例)"""
    global _os_client
    if _os_client is None:
        http_auth = None
        if settings.opensearch_username and settings.opensearch_password:
            http_auth = (settings.opensearch_username, settings.opensearch_password)

        _os_client = AsyncOpenSearch(
            hosts=[settings.opensearch_host],
            http_auth=http_auth,
            use_ssl=settings.opensearch_use_ssl,
            verify_certs=settings.opensearch_verify_certs,
            timeout=settings.opensearch_timeout,
            max_retries=settings.opensearch_max_retries,
            retry_on_timeout=True,
        )
        logger.info(f"OpenSearch 客户端已创建: {settings.opensearch_host}")
    return _os_client


async def ensure_indices() -> bool:
    """
    确保 OpenSearch 索引存在

    创建文件基本信息索引 (pcd_file_basic) 和文件内容索引 (pcd_file_content)

    Returns:
        bool: 是否成功
    """
    from core.search.index_mapping import (
        FILE_BASIC_INDEX_BODY,
        FILE_CONTENT_INDEX_BODY,
    )

    try:
        client = get_opensearch_client()

        # 文件基本信息索引
        basic_exists = await client.indices.exists(index=settings.opensearch_file_index)
        if not basic_exists:
            await client.indices.create(
                index=settings.opensearch_file_index,
                body=FILE_BASIC_INDEX_BODY,
            )
            logger.info(f"文件基本信息索引已创建: {settings.opensearch_file_index}")
        else:
            logger.debug(f"文件基本信息索引已存在: {settings.opensearch_file_index}")

        # 文件内容索引
        content_exists = await client.indices.exists(index=settings.opensearch_content_index)
        if not content_exists:
            await client.indices.create(
                index=settings.opensearch_content_index,
                body=FILE_CONTENT_INDEX_BODY,
            )
            logger.info(f"文件内容索引已创建: {settings.opensearch_content_index}")
        else:
            logger.debug(f"文件内容索引已存在: {settings.opensearch_content_index}")

        return True
    except Exception:
        logger.warning(
            "OpenSearch 服务不可用，跳过索引初始化 "
            "(文件内容搜索功能暂不可用，其他功能不受影响)"
        )
        return False


async def close_opensearch_client():
    """关闭 OpenSearch 客户端"""
    global _os_client
    if _os_client is not None:
        try:
            await _os_client.close()
            _os_client = None
            logger.info("OpenSearch 客户端已关闭")
        except Exception as e:
            logger.warning(f"关闭 OpenSearch 客户端异常: {e}")