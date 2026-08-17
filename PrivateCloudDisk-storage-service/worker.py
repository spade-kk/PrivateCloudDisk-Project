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
  WORKER_PREFETCH_BE_MERGE     - 后台合并任务队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_BE_HASH      - 后台哈希任务队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_BE_VIRUS     - 后台病毒扫描任务队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_BE_MA        - 后台标记活跃任务队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_CONTENT_PROCESSED - 内容预处理结果队列 prefetch 数 (默认 4)
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
│  - 后台处理消费者 (merge/hash/virus/mark_active, 各独立任务队列)         │
│  - 增强消费者 (thumbnail/transcode/hls/index, 各独立任务队列)            │
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
import socket
import json
import multiprocessing
import time
from typing import Optional

from core.config import settings
from core.rabbitmq import rabbitmq_service
from core.consumers import (
    on_file_delete_message,
    on_uploads_session_delete_message,
    on_uploads_event_dlq_message,
    # Backend Task Bus — 顺序流水线
    on_backend_merge_message,
    on_backend_hash_message,
    on_backend_virus_message,
    on_backend_mark_active_message,
    # Lifecycle — merge 与 hash 之间的内容预处理闸门
    on_file_content_processed_message,
    on_file_content_timeout_message,
    on_file_content_processed_dlq_message,
    # Enhancement — 并发流水线
    on_enhance_thumbnail_message,
    on_enhance_transcode_message,
    on_enhance_hls_message,
    on_enhance_index_message,
    on_enhance_office_to_pdf_message,
    on_enhance_archive_parse_message,
    # Task Bus DLQ：使用现有按阶段统一策略的消费者
    on_backend_dlq_message,
    on_enhance_dlq_message,
    # 其他域 DLQ / 生命周期降级消费者
    on_file_content_ready_dlq_message,
    on_file_content_processed_dedicated_dlq_message,
    on_file_delete_dlq_message,
    on_file_event_dlq_message,
    on_security_quarantine_message,
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
        "callback": on_file_delete_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ", "2")),
    },
    "security_quarantine": {
        "queue": settings.security_quarantine_queue,
        "callback": on_security_quarantine_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_SQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_SQ", "2")),
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
    # ========== Backend Task Bus — 每个阶段独立任务队列 ==========
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
    # ========== Lifecycle — 内容预处理闸门 ==========
    "file_content_processed": {
        "queue": settings.file_content_processed_queue,
        "callback": on_file_content_processed_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_CONTENT_PROCESSED", "4")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_CONTENT_PROCESSED", "8")),
    },
    "file_content_timeout": {
        "queue": settings.file_content_timeout_queue,
        "callback": on_file_content_timeout_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_CONTENT_TIMEOUT", "4")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_CONTENT_TIMEOUT", "8")),
    },
    "dlq_file_content_processed": {
        "queue": settings.file_content_processed_dlq,
        "callback": on_file_content_processed_dedicated_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_CONTENT_DLQ", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_CONTENT_DLQ", "4")),
    },
    "dlq_file_content_ready": {
        "queue": settings.file_content_ready_dlq,
        "callback": on_file_content_ready_dlq_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_CONTENT_READY_DLQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_CONTENT_READY_DLQ", "2")),
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

# 保留每个队列的细粒度并发配置；只有未设置任何旧式
# WORKER_CONCURRENCY_<QUEUE> 变量时，才使用统一 WORKER_CONCURRENCY 作为默认值。
# 这样既支持新部署的统一调优，也不会覆盖既有生产环境的队列级参数。
if os.getenv("WORKER_CONCURRENCY") and not any(
    key.startswith("WORKER_CONCURRENCY_") for key in os.environ
):
    try:
        _global_concurrency = max(1, int(os.getenv("WORKER_CONCURRENCY", "1")))
    except (TypeError, ValueError):
        _global_concurrency = 1
        # 配置错误不应阻止 Worker 启动；使用保守并发并让运维日志继续可见。
        logging.getLogger("worker").warning("WORKER_CONCURRENCY 非法，降级为 1")
    for _queue_config in CONFIG.values():
        _queue_config["concurrency"] = _global_concurrency


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
        self.worker_id = os.getenv("WORKER_ID") or f"{socket.gethostname()}-{os.getpid()}"
        self._running = False
        self._shutdown_event: Optional[asyncio.Event] = None
        self._background_stop_event: Optional[asyncio.Event] = None
        self._background_tasks: list[asyncio.Task] = []
        self._health_server: asyncio.AbstractServer | None = None

    async def start(self):
        """启动 Worker"""
        logger.info("=" * 60)
        logger.info("PrivateCloudDisk Worker 进程启动 worker_id=%s", self.worker_id)
        logger.info("=" * 60)

        self._running = True
        self._shutdown_event = asyncio.Event()
        self._background_stop_event = asyncio.Event()

        await self._start_health_server()

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

        # 需求：Outbox Publisher 和 DB Sweeper 与消费者并行运行。
        # Outbox 保证 Gate 提交后消息最终可达；Sweeper 保证 MQ/Automation 全不可用时仍可继续 hash。
        from app.services.storage_outbox_service import storage_outbox_service
        self._background_tasks = [
            asyncio.create_task(
                storage_outbox_service.run_publisher(self._background_stop_event),
                name="storage-outbox-publisher",
            ),
            asyncio.create_task(
                storage_outbox_service.run_gate_sweeper(self._background_stop_event),
                name="file-preprocess-gate-sweeper",
            ),
        ]
        logger.info("Outbox Publisher 和 DB Sweeper 并行任务创建成功")
        # 1.5. 初始化文件存储层
        #_init_storage()

        # 2. 初始化 OpenSearch 索引
        try:
            from core.search.opensearch_client import ensure_indices
            logger.info("初始化 OpenSearch 索引...")
            opensearch_ready = await ensure_indices()
            if opensearch_ready:
                logger.info("OpenSearch 索引初始化完成")
            else:
                logger.warning("OpenSearch 不可用，已禁用本进程内容索引增强；其他流水线继续运行")
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

        if self._background_stop_event:
            self._background_stop_event.set()
        if self._background_tasks:
            try:
                await asyncio.wait_for(
                    asyncio.gather(*self._background_tasks, return_exceptions=True),
                    timeout=10,
                )
            except asyncio.TimeoutError:
                for task in self._background_tasks:
                    task.cancel()
                await asyncio.gather(*self._background_tasks, return_exceptions=True)
            self._background_tasks.clear()

        if self._health_server:
            self._health_server.close()
            await self._health_server.wait_closed()
            self._health_server = None

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

    async def _start_health_server(self) -> None:
        """启动本地健康端点，不暴露文件载荷或凭据。"""
        try:
            port = int(os.getenv("WORKER_HEALTH_PORT", str(settings.worker_health_port)))
            self._health_server = await asyncio.start_server(
                self._handle_health_request,
                settings.worker_health_host,
                port,
            )
            logger.info("Worker 健康端点已启动 worker_id=%s address=%s:%s", self.worker_id, settings.worker_health_host, port)
        except OSError as exc:
            # 多实例若未配置端口偏移，不应阻止消息消费者启动；以日志告警。
            logger.warning("Worker 健康端点启动失败（不影响消费）worker_id=%s error=%s", self.worker_id, exc)

    async def _handle_health_request(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        try:
            request = await asyncio.wait_for(reader.read(1024), timeout=2)
            path = request.split(b" ", 2)[1].decode("ascii", "ignore") if b" " in request else "/"
            if path != "/health":
                status, body = "404 Not Found", {"status": "not_found"}
            else:
                from core.messaging.metrics import worker_metrics

                status, body = "200 OK", {
                    "status": "ok" if self._running and rabbitmq_service.health_snapshot()["connected"] else "degraded",
                    "worker_id": self.worker_id,
                    "pid": os.getpid(),
                    "rabbitmq": rabbitmq_service.health_snapshot(),
                    "metrics": worker_metrics.snapshot(),
                }
            encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
            writer.write(
                f"HTTP/1.1 {status}\r\nContent-Type: application/json; charset=utf-8\r\n"
                f"Content-Length: {len(encoded)}\r\nConnection: close\r\n\r\n".encode("ascii") + encoded
            )
            await writer.drain()
        except Exception:
            logger.debug("健康端点请求处理失败", exc_info=True)
        finally:
            writer.close()
            try:
                await writer.wait_closed()
            except Exception:
                pass


# =============================================================================
# 入口
# =============================================================================

def _run_worker_process(index: int = 0):
    """单个子进程入口；每个进程独立创建 asyncio/MQ/DB 连接。"""
    os.environ["WORKER_ID"] = f"{socket.gethostname()}-{os.getpid()}-{index}"
    base_port = int(os.getenv("WORKER_HEALTH_PORT", str(settings.worker_health_port)))
    os.environ["WORKER_HEALTH_PORT"] = str(base_port + index)
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


def main():
    """Worker 主入口：主进程管理多个独立 asyncio 子进程。"""
    configured = int(os.getenv("WORKER_PROCESSES", str(settings.worker_processes or 0)))
    process_count = configured if configured > 0 else (os.cpu_count() or 1)
    # W-05：未显式配置时按 CPU 核心数启动；容器/开发环境可通过
    # WORKER_PROCESSES=1 主动限制资源，默认策略与企业级多进程要求一致。
    if process_count <= 1:
        _run_worker_process(0)
        return

    context = multiprocessing.get_context("spawn")
    processes: list[multiprocessing.Process] = []
    shutting_down = False

    def _parent_signal_handler(signum, _frame):
        nonlocal shutting_down
        if shutting_down:
            return
        shutting_down = True
        logger.warning("主 Worker 收到信号=%s，通知子进程优雅退出", signum)
        for index, proc in enumerate(processes):
            if proc.is_alive():
                try:
                    os.kill(proc.pid, signum)
                except ProcessLookupError:
                    pass

    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, _parent_signal_handler)

    for index in range(process_count):
        proc = context.Process(target=_run_worker_process, args=(index,), name=f"pcd-worker-{index + 1}")
        proc.start()
        processes.append(proc)
    logger.info("Worker 主进程已启动子进程数量=%s", process_count)

    try:
        while processes:
            alive = [proc for proc in processes if proc.is_alive()]
            if not alive:
                break
            for proc in processes:
                proc.join(timeout=0.5)
            if shutting_down:
                deadline = time.monotonic() + settings.worker_shutdown_timeout_seconds
                while time.monotonic() < deadline and any(proc.is_alive() for proc in processes):
                    time.sleep(0.2)
                for proc in processes:
                    if proc.is_alive():
                        logger.error("子 Worker 未在超时内退出，强制终止 pid=%s", proc.pid)
                        proc.terminate()
                break
    finally:
        for proc in processes:
            proc.join(timeout=2)
        logger.info("Worker 主进程退出")


if __name__ == "__main__":
    main()
