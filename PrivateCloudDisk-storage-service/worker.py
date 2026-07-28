"""
独立 Worker 进程 - 与 FastAPI 主进程完全解耦

用途：
- 启动所有 RabbitMQ 消费者，以独立进程运行
- 处理文件后台处理、增强、删除等重型任务时，不影响 FastAPI HTTP 请求处理
- 支持多实例水平扩展（启动多个 Worker 进程）

使用方式：
  python worker.py

环境变量：
  WORKER_PREFETCH_FD           - 文件删除队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_DLQ          - 死信队列 prefetch 数 (默认 1)
  WORKER_PREFETCH_USD          - 上传会话删除队列 prefetch 数 (默认 4)
  WORKER_PREFETCH_SQ           - 安全隔离队列 prefetch 数 (默认 1)
  WORKER_PREFETCH_BE_MERGE     - 后台合并队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_BE_HASH      - 后台哈希队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_BE_VIRUS     - 后台病毒扫描队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_BE_MA        - 后台标记活跃队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_EN_THUMB     - 增强缩略图队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_EN_TRANS     - 增强转码队列 prefetch 数 (默认 1)
  WORKER_PREFETCH_EN_HLS       - 增强 HLS 队列 prefetch 数 (默认 1)
  WORKER_PREFETCH_EN_INDEX     - 增强索引队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_EN_OFFICE_PDF - 增强 Office 转 PDF 队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_EN_ARCHIVE_PARSE - 增强归档解析队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_DLQ_BE       - 后台 DLQ prefetch 数 (默认 1)
  WORKER_PREFETCH_DLQ_EN       - 增强 DLQ prefetch 数 (默认 1)
  WORKER_LOG_LEVEL             - 日志级别 (默认 INFO)

架构：
┌──────────────────────────────────────────────────────────────────────┐
│  FastAPI 进程 (uvicorn)                                              │
│  - HTTP API 端点                                                    │
│  - 不运行消费者                                                      │
│  - 发布消息到 RabbitMQ                                              │
└──────────────────────────────────────────────────────────────────────┘
         │
         ▼ RabbitMQ
┌──────────────────────────────────────────────────────────────────────┐
│  Worker 进程 (独立)                                                  │
│  - 文件删除消费者 (prefetch=2, concur=4)                             │
│  - 后台处理消费者 (merge/hash/virus/mark_active, 各独立队列)           │
│  - 增强消费者 (thumbnail/transcode/hls/index, 各独立队列)              │
│  - 后台 DLQ 消费者 (prefetch=1, concur=2)                            │
│  - 增强 DLQ 消费者 (prefetch=1, concur=2)                            │
│  - 上传会话删除消费者 (prefetch=4, concur=4)                          │
│  - 异步事件循环 + 线程池执行重型任务                                  │
└──────────────────────────────────────────────────────────────────────┘
"""
import os
import sys
import asyncio
import logging
import signal
from typing import Optional

from core.config import settings
from core.rabbitmq import rabbitmq_service
from core.consumers import (
    on_file_delete_message,
    on_uploads_session_delete_message,
    on_uploads_event_dlq_message,
    # Backend — 顺序流水线
    on_backend_merge_message,
    on_backend_hash_message,
    on_backend_virus_message,
    on_backend_mark_active_message,
    # Enhancement — 并发流水线
    on_enhance_thumbnail_message,
    on_enhance_transcode_message,
    on_enhance_hls_message,
    on_enhance_index_message,
    on_enhance_office_to_pdf_message,
    on_enhance_archive_parse_message,
    # DLQ — Backend + Enhancement
    on_backend_dlq_message,
    on_enhance_dlq_message,
)
from app.core.logging_config import setup_logging, get_logger


# =============================================================================
# 并发配置（可通过环境变量覆盖）
# =============================================================================
# 每个队列独立的 prefetch_count
# - 后台处理队列：合并/哈希/病毒/标记活跃各 2 条，保证顺序流水线
# - 增强队列：缩略图/索引各 2 条，转码/HLS 各 1 条（重型任务）
# - 文件删除队列：预取 2 条
# - 死信队列：预取 1 条（低频，不需要高并发）
CONFIG = {
    "file_delete": {
        "queue": settings.file_delete_queue,
        "callback": on_file_delete_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_FD", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_FD", "4")),
    },
    "dlq_delete": {
        "queue": settings.file_delete_dlq,
        "callback": on_backend_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ", "2")),
    },
    "security_quarantine": {
        "queue": settings.security_quarantine_queue,
        "callback": on_backend_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_SQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_SQ", "2")),
    },
    "dlq_content": {
        "queue": settings.content_index_dlq,
        "callback": on_enhance_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ", "2")),
    },
    "uploads_session_delete": {
        "queue": settings.uploads_session_delete_queue,
        "callback": on_uploads_session_delete_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_USD", "4")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_USD", "4")),
    },
    "dlq_uploads_event": {
        "queue": settings.uploads_event_dlq,
        "callback": on_uploads_event_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ", "2")),
    },
    "dlq_file_event": {
        "queue": settings.file_event_dlq,
        "callback": on_backend_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ", "2")),
    },
    # ========== Backend — 顺序流水线（每个阶段独立队列） ==========
    "backend_merge": {
        "queue": settings.file_backend_merge_queue,
        "callback": on_backend_merge_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_BE_MERGE", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_BE_MERGE", "4")),
    },
    "backend_hash": {
        "queue": settings.file_backend_hash_queue,
        "callback": on_backend_hash_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_BE_HASH", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_BE_HASH", "4")),
    },
    "backend_virus": {
        "queue": settings.file_backend_virus_queue,
        "callback": on_backend_virus_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_BE_VIRUS", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_BE_VIRUS", "4")),
    },
    "backend_mark_active": {
        "queue": settings.file_backend_mark_active_queue,
        "callback": on_backend_mark_active_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_BE_MA", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_BE_MA", "4")),
    },
    # ========== Enhancement — 并发流水线（各阶段独立并行） ==========
    "enhance_thumbnail": {
        "queue": settings.file_enhance_thumbnail_queue,
        "callback": on_enhance_thumbnail_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_EN_THUMB", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_EN_THUMB", "4")),
    },
    "enhance_transcode": {
        "queue": settings.file_enhance_transcode_queue,
        "callback": on_enhance_transcode_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_EN_TRANS", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_EN_TRANS", "2")),
    },
    "enhance_hls": {
        "queue": settings.file_enhance_hls_queue,
        "callback": on_enhance_hls_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_EN_HLS", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_EN_HLS", "2")),
    },
    "enhance_index": {
        "queue": settings.file_enhance_index_queue,
        "callback": on_enhance_index_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_EN_INDEX", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_EN_INDEX", "4")),
    },
    "enhance_office_to_pdf": {
        "queue": settings.file_enhance_office_to_pdf_queue,
        "callback": on_enhance_office_to_pdf_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_EN_OFFICE_PDF", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_EN_OFFICE_PDF", "4")),
    },
    "enhance_archive_parse": {
        "queue": settings.file_enhance_archive_parse_queue,
        "callback": on_enhance_archive_parse_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_EN_ARCHIVE_PARSE", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_EN_ARCHIVE_PARSE", "4")),
    },
    # ========== DLQ — Backend + Enhancement ==========
    "dlq_backend_merge": {
        "queue": settings.file_backend_merge_dlq,
        "callback": on_backend_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ_BE", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ_BE", "2")),
    },
    "dlq_backend_hash": {
        "queue": settings.file_backend_hash_dlq,
        "callback": on_backend_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ_BE", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ_BE", "2")),
    },
    "dlq_backend_virus": {
        "queue": settings.file_backend_virus_dlq,
        "callback": on_backend_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ_BE", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ_BE", "2")),
    },
    "dlq_backend_mark_active": {
        "queue": settings.file_backend_mark_active_dlq,
        "callback": on_backend_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ_BE", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ_BE", "2")),
    },
    "dlq_enhance_thumbnail": {
        "queue": settings.file_enhance_thumbnail_dlq,
        "callback": on_enhance_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ_EN", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ_EN", "2")),
    },
    "dlq_enhance_transcode": {
        "queue": settings.file_enhance_transcode_dlq,
        "callback": on_enhance_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ_EN", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ_EN", "2")),
    },
    "dlq_enhance_hls": {
        "queue": settings.file_enhance_hls_dlq,
        "callback": on_enhance_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ_EN", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ_EN", "2")),
    },
    "dlq_enhance_index": {
        "queue": settings.file_enhance_index_dlq,
        "callback": on_enhance_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ_EN", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ_EN", "2")),
    },
    "dlq_enhance_office_to_pdf": {
        "queue": settings.file_enhance_office_to_pdf_dlq,
        "callback": on_enhance_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ_EN", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ_EN", "2")),
    },
    "dlq_enhance_archive_parse": {
        "queue": settings.file_enhance_archive_parse_dlq,
        "callback": on_enhance_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ_EN", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ_EN", "2")),
    },
}


# =============================================================================
# 日志
# =============================================================================
log_level = os.getenv("WORKER_LOG_LEVEL", "INFO").upper()
setup_logging(level=getattr(logging, log_level, logging.INFO), enable_color=True)
logger = get_logger("worker")


def _init_storage():
    """初始化文件存储层（Worker 进程）"""
    from core.storage.factory import create_storage

    if settings.storage_type == "minio":
        create_storage(
            storage_type="minio",
            endpoint=settings.minio_endpoint,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            bucket=settings.minio_bucket,
            secure=settings.minio_secure,
            base_dir=settings.file_upload_dir,
        )
        logger.info(f"文件存储层初始化完成: MinIO ({settings.minio_endpoint}/{settings.minio_bucket})")
    else:
        create_storage(
            storage_type="localstorage",
            base_dir=settings.file_upload_dir,
        )
        logger.info(f"文件存储层初始化完成: LocalStorage ({settings.file_upload_dir})")


class Worker:
    """
    独立 Worker 进程

    功能：
    - 连接 RabbitMQ 并声明拓扑
    - 启动所有消费者（每个队列独立 channel + prefetch）
    - 优雅关闭（SIGTERM / SIGINT）
    - 支持多 Worker 实例水平扩展
    """

    def __init__(self):
        self._running = False
        self._shutdown_event: Optional[asyncio.Event] = None

    async def start(self):
        """启动 Worker"""
        logger.info("=" * 60)
        logger.info("PrivateCloudDisk Worker 进程启动")
        logger.info("=" * 60)

        self._running = True
        self._shutdown_event = asyncio.Event()

        # AUDIT FIX [7.4]: Worker 与 HTTP 进程分别维护连接池，保证增强流水线和 DLQ 可持久化。
        from app.db.database import init_database
        await init_database()

        # ---- 打印配置汇总 ----
        logger.info("--- Worker 配置汇总 ---")
        import os as _os
        cpu_count = _os.cpu_count() or 1
        logger.info(f"  CPU 核心数: {cpu_count}")
        logger.info(f"  Prefetch 策略:")
        for name, cfg in CONFIG.items():
            logger.info(
                f"    [{name:22s}] prefetch={cfg['prefetch']}, "
                f"concurrency={cfg['concurrency']}, "
                f"queue={cfg['queue']}"
            )
        logger.info("--- 配置汇总结束 ---")

        # 1. 连接 RabbitMQ（声明拓扑）
        logger.info("连接 RabbitMQ 并声明拓扑...")
        await rabbitmq_service.connect()
        logger.info("RabbitMQ 连接成功")

        # 1.5. 初始化文件存储层
        #_init_storage()

        # 2. 初始化 OpenSearch 索引
        try:
            from core.search.opensearch_client import ensure_indices
            logger.info("初始化 OpenSearch 索引...")
            await ensure_indices()
            logger.info("OpenSearch 索引初始化完成")
        except Exception as e:
            logger.warning(f"OpenSearch 索引初始化失败 (非致命): {e}")

        # 3. 启动所有消费者
        logger.info("启动消费者...")
        for name, cfg in CONFIG.items():
            try:
                await rabbitmq_service.consume(
                    queue_name=cfg["queue"],
                    callback=cfg["callback"],
                    prefetch_count=cfg["prefetch"],
                    max_concurrency=cfg["concurrency"],
                )
                logger.info(
                    f"  [{name}] queue={cfg['queue']}, "
                    f"prefetch={cfg['prefetch']}, "
                    f"concurrency={cfg['concurrency']}"
                )
            except Exception as e:
                logger.error(f"  [{name}] 启动失败: {e}", exc_info=True)

        logger.info("=" * 60)
        logger.info("所有消费者已启动，等待消息...")
        logger.info("=" * 60)

        # 4. 等待关闭信号
        await self._shutdown_event.wait()

    async def shutdown(self):
        """优雅关闭"""
        if not self._running:
            return

        logger.info("收到关闭信号，开始优雅关闭...")
        self._running = False

        # 关闭 OpenSearch 客户端
        try:
            from core.search.opensearch_client import close_opensearch_client
            await close_opensearch_client()
        except Exception as e:
            logger.error(f"关闭 OpenSearch 失败: {e}")

        # 关闭 RabbitMQ 连接
        try:
            await rabbitmq_service.close()
        except Exception as e:
            logger.error(f"关闭 RabbitMQ 失败: {e}")

        # 关闭 Redis 连接
        try:
            from app.core.redis_client import redis_client
            await redis_client.close()
        except Exception as e:
            logger.error(f"关闭 Redis 失败: {e}")

        # 关闭 MySQL 连接池
        try:
            from app.db.database import close_database
            await close_database()
        except Exception as e:
            logger.error(f"关闭 MySQL 失败: {e}")

        if self._shutdown_event:
            self._shutdown_event.set()

        logger.info("Worker 已关闭")


# =============================================================================
# 入口
# =============================================================================

def main():
    """Worker 主入口"""
    worker = Worker()

    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    # 注册信号处理
    def _signal_handler():
        logger.info("收到 SIGTERM/SIGINT")
        if worker._shutdown_event:
            worker._shutdown_event.set()

    for sig in (signal.SIGTERM, signal.SIGINT):
        try:
            loop.add_signal_handler(sig, _signal_handler)
        except NotImplementedError:
            # Windows 不支持 add_signal_handler
            signal.signal(sig, lambda s, f: _signal_handler())

    try:
        loop.run_until_complete(worker.start())
    except KeyboardInterrupt:
        pass
    finally:
        loop.run_until_complete(worker.shutdown())
        loop.close()

    logger.info("Worker 进程退出")


if __name__ == "__main__":
    main()
