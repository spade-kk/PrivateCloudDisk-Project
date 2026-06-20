"""
PrivateCloudDisk AI Processing Service - Worker 独立进程入口

Worker 进程独立于 FastAPI HTTP 服务，专门负责:
- 消费 RabbitMQ 消息 (AI 处理、人脸聚类、推荐)
- 执行 AI 模型推理 (CPU/GPU)
- 结果持久化到 MySQL

与 API 服务 (main.py) 通过数据库和 RabbitMQ 通信。

使用方式:
    python app/worker.py
    # 或通过 Docker:
    docker compose -f docker-compose.ai.yml up ai-worker

水平扩展:
    docker compose -f docker-compose.ai.yml up ai-worker --scale ai-worker=4
"""
from __future__ import annotations
import asyncio
import logging
import signal
import sys
from pathlib import Path

# 确保项目根目录在 sys.path 中
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.core.config import settings
from app.core.logging_config import setup_logging
from app.core.database.connection import db_manager
from app.core.rabbitmq import rabbitmq_service


logger = logging.getLogger("ai_service.worker")


class Worker:
    """
    AI Worker 主进程

    负责:
    1. 连接基础设施 (MySQL, RabbitMQ)
    2. 启动所有消费者 (AI 处理、人脸聚类、推荐)
    3. 优雅关闭 (SIGINT, SIGTERM)
    """

    def __init__(self):
        self._consumers = []
        self._shutdown_event = asyncio.Event()
        self._consumer_tasks = []

    async def start(self) -> None:
        """启动 Worker"""
        logger.info("=" * 60)
        logger.info("  PrivateCloudDisk AI Worker 启动中...")
        logger.info(f"  Device: {settings.ai_inference_device}")
        logger.info(f"  Model Dir: {settings.model_dir}")
        logger.info("=" * 60)

        # 1. 连接数据库
        await self._connect_db()

        # 2. 连接 RabbitMQ
        await self._connect_rabbitmq()

        # 3. 启动消费者
        await self._start_consumers()

        logger.info("AI Worker 启动完成，等待消息...")

        # 4. 等待关闭信号
        await self._shutdown_event.wait()

        # 5. 优雅关闭
        await self._shutdown()

    async def stop(self) -> None:
        """停止 Worker"""
        self._shutdown_event.set()

    async def _connect_db(self) -> None:
        """连接数据库"""
        try:
            await db_manager.connect()
            logger.info("MySQL 连接成功")
        except Exception as e:
            logger.error(f"MySQL 连接失败: {e}")
            raise

    async def _connect_rabbitmq(self) -> None:
        """连接 RabbitMQ"""
        try:
            await rabbitmq_service.connect()
            logger.info("RabbitMQ 连接成功 (拓扑已声明)")
        except Exception as e:
            logger.error(f"RabbitMQ 连接失败: {e}")
            raise

    async def _start_consumers(self) -> None:
        """启动所有消费者"""
        from app.core.consumers.ai_consumer import ai_consumer
        from app.core.consumers.face_cluster_consumer import face_cluster_consumer
        from app.core.consumers.recommendation_consumer import recommendation_consumer

        # 将消费者作为后台任务启动
        self._consumer_tasks = [
            asyncio.create_task(ai_consumer.start(), name="ai_consumer"),
            asyncio.create_task(face_cluster_consumer.start(), name="face_cluster_consumer"),
            asyncio.create_task(recommendation_consumer.start(), name="recommendation_consumer"),
        ]

        logger.info(
            f"消费者已启动: "
            f"ai_consumer, face_cluster_consumer, recommendation_consumer"
        )

    async def _shutdown(self) -> None:
        """优雅关闭"""
        logger.info("AI Worker 正在关闭...")

        # 取消所有消费者任务
        for task in self._consumer_tasks:
            if not task.done():
                task.cancel()

        # 等待任务取消
        await asyncio.gather(*self._consumer_tasks, return_exceptions=True)

        # 关闭 RabbitMQ
        try:
            await rabbitmq_service.close()
        except Exception:
            pass

        # 关闭数据库
        try:
            await db_manager.close()
        except Exception:
            pass

        logger.info("AI Worker 已关闭")


# =============================================================================
# 入口
# =============================================================================
def main():
    """Worker 主入口"""
    setup_logging(
        level=getattr(logging, settings.worker_log_level.upper(), logging.INFO),
        enable_color=True,
    )

    worker = Worker()
    loop = asyncio.new_event_loop()

    # 注册信号处理 (优雅关闭)
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, lambda: asyncio.ensure_future(worker.stop()))
        except NotImplementedError:
            # Windows 不支持 add_signal_handler
            signal.signal(sig, lambda s, f: asyncio.ensure_future(worker.stop()))

    try:
        loop.run_until_complete(worker.start())
    except KeyboardInterrupt:
        logger.info("收到中断信号")
    finally:
        loop.close()


if __name__ == "__main__":
    main()