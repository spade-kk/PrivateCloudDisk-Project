"""
DLQ 消费者公共基类

提供所有死信消费者共用的能力：
- 消息解析与字段提取
- Redis 持久化记录
- 统一的入口流程

企业级架构说明：
  每个领域（文件处理、上传事件、文件删除等）各自派生子类，
  通过 `_get_handler` 分发到各自的处理策略矩阵。
  基类不包含任何业务逻辑，只提供基础设施。
"""
from __future__ import annotations
import json
import logging
from abc import ABC, abstractmethod
from datetime import datetime, timezone
from typing import Any, Callable

from core.config import FailureReason

logger = logging.getLogger("dlq_base")


class BaseDLQConsumer(ABC):
    """
    DLQ 消费者抽象基类

    子类需实现:
      - _get_handler(failure_reason) → Callable  :: 返回对应策略处理函数
      - _get_dlq_source_name() → str            :: 返回 DLQ 来源名称（用于日志）
    """

    # ---- 抽象方法：子类必须实现 ----

    @abstractmethod
    def _get_handler(self, failure_reason: str) -> Callable:
        """根据 failure_reason 返回处理函数"""
        ...

    @abstractmethod
    def _get_dlq_source_name(self) -> str:
        """返回 DLQ 来源名称（如 'file_process', 'uploads_event'）"""
        ...

    # ---- 公共入口 ----

    async def handle(self, message: Any):
        """
        DLQ 消息处理统一入口

        流程:
          1. 解析消息体
          2. 提取失败原因（failure_reason）
          3. 路由到对应处理策略
          4. 成功 → ACK / 失败 → NACK(requeue=True)
        """
        source = self._get_dlq_source_name()
        data = {}
        try:
            message_body = message.body.decode("utf-8")
            data = json.loads(message_body)

            failure_reason = data.get("failure_reason", FailureReason.UNKNOWN)
            task_type = data.get("task_type", "unknown")
            file_id = data.get("file_id", data.get("uploadsSessionId", "unknown"))

            logger.error(
                f"[DLQ-{source}] 收到死信: "
                f"task_type={task_type}, "
                f"file_id={file_id}, "
                f"failure_reason={failure_reason}, "
                f"retry_count={data.get('retry_count', 0)}"
            )

            handler = self._get_handler(failure_reason)
            handled = await handler(data)

            if handled:
                await message.ack()
                logger.info(
                    f"[DLQ-{source}] 消息已处理: "
                    f"file_id={file_id}, reason={failure_reason}"
                )
            else:
                await message.nack(requeue=True)
                logger.error(
                    f"[DLQ-{source}] 消息处理失败，重新入队: "
                    f"file_id={file_id}"
                )

        except json.JSONDecodeError:
            logger.error(f"[DLQ-{source}] JSON 解析失败，丢弃")
            await message.ack()
        except Exception as e:
            logger.error(
                f"[DLQ-{source}] 处理异常: {e}",
                exc_info=True,
            )
            await message.nack(requeue=True)

    # ---- 公共辅助方法 ----

    @staticmethod
    async def _log_dlq_action(
        data: dict, action: str, detail: str, source: str = "unknown"
    ):
        """DLQ 处理动作日志（写入 Redis 持久化记录，保留 30 天）"""
        try:
            from server import redis_client

            file_id = data.get("file_id", data.get("uploadsSessionId", "unknown"))
            dlq_key = (
                f"dlq:action:{source}:{file_id}:"
                f"{datetime.now(timezone.utc).isoformat()}"
            )

            record = {
                "action": action,
                "detail": detail,
                "source": source,
                "file_id": file_id,
                "task_type": data.get("task_type", ""),
                "failure_reason": data.get("failure_reason", ""),
                "processed_at": datetime.now(timezone.utc).isoformat(),
            }

            await redis_client.setex(
                dlq_key,
                86400 * 30,
                json.dumps(record, ensure_ascii=False),
            )
        except Exception:
            pass