"""
独立 Worker 进程 - 与 FastAPI 主进程完全解耦

用途：
- 启动所有 RabbitMQ 消费者，以独立进程运行
- 处理视频转码等重型任务时，不会影响 FastAPI HTTP 请求处理
- 支持多实例水平扩展（启动多个 Worker 进程）

使用方式：
  python worker.py

环境变量：
  WORKER_PREFETCH_FP    - 文件处理队列 prefetch 数 (默认 4)
  WORKER_PREFETCH_FD    - 文件删除队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_CI    - 内容索引队列 prefetch 数 (默认 2)
  WORKER_PREFETCH_DLQ   - 死信队列 prefetch 数 (默认 1)
  WORKER_CONCURRENCY    - 全局最大协程并发数 (默认 16)
  WORKER_LOG_LEVEL      - 日志级别 (默认 INFO)

架构：
┌────────────────────────────────────────────┐
│  FastAPI 进程 (uvicorn)                     │
│  - HTTP API 端点                            │
│  - 不运行消费者                              │
│  - 发布消息到 RabbitMQ                       │
└────────────────────────────────────────────┘
         │
         ▼ RabbitMQ
┌────────────────────────────────────────────┐
│  Worker 进程 (独立)                          │
│  - 文件处理消费者 (prefetch=4, concur=8)     │
│  - 文件删除消费者 (prefetch=2, concur=4)     │
│  - 内容索引消费者 (prefetch=2, concur=4)     │
│  - 死信消费者 (prefetch=1, concur=2)         │
│  - 安全隔离消费者 (prefetch=1, concur=2)     │
│  - 异步事件循环 + 线程池执行重型任务          │
└────────────────────────────────────────────┘
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
    on_file_process_message,
    on_file_delete_message,
    on_dead_letter_message,
    on_content_index_message,
    on_uploads_session_delete_message,
    on_uploads_event_dlq_message,
)
from app.core.logging_config import setup_logging, get_logger


# =============================================================================
# 并发配置（可通过环境变量覆盖）
# =============================================================================
# 每个队列独立的 prefetch_count
# - 文件处理队列：预取 4 条，允许并发处理（视频转码不阻塞缩略图/合并）
# - 文件删除队列：预取 2 条
# - 内容索引队列：预取 2 条
# - 死信/安全队列：预取 1 条（低频，不需要高并发）
CONFIG = {
    "file_process": {
        "queue": settings.file_process_queue,
        "callback": on_file_process_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_FP", "4")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_FP", "8")),
    },
    "file_delete": {
        "queue": settings.file_delete_queue,
        "callback": on_file_delete_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_FD", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_FD", "4")),
    },
    "content_index": {
        "queue": settings.content_index_queue,
        "callback": on_content_index_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_CI", "2")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_CI", "4")),
    },
    "dlq_process": {
        "queue": settings.file_process_dlq,
        "callback": on_dead_letter_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ", "2")),
    },
    "dlq_delete": {
        "queue": settings.file_delete_dlq,
        "callback": on_dead_letter_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ", "2")),
    },
    "security_quarantine": {
        "queue": settings.security_quarantine_queue,
        "callback": on_dead_letter_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_SQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_SQ", "2")),
    },
    "dlq_content": {
        "queue": settings.content_index_dlq,
        "callback": on_dead_letter_message,
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
        "callback": on_dead_letter_message,
        "prefetch": int(os.getenv("WORKER_PREFETCH_DLQ", "1")),
        "concurrency": int(os.getenv("WORKER_CONCURRENCY_DLQ", "2")),
    },
}


# =============================================================================
# 日志
# =============================================================================
log_level = os.getenv("WORKER_LOG_LEVEL", "INFO").upper()
setup_logging(level=getattr(logging, log_level, logging.INFO), enable_color=True)
logger = get_logger("worker")


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