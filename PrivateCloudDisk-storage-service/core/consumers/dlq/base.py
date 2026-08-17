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

            # dict.get 的默认值不会覆盖空字符串；RabbitMQ DLX 转发原消息时正会产生空 failure_reason。
            # 这里统一规范旧消息、显式 DLQ 消息和滚动升级消息，保证日志与数据库台账永不落空。
            task_type = str(data.get("task_type") or data.get("stage") or "unknown").strip()
            failure_reason = str(data.get("failure_reason") or FailureReason.UNKNOWN).strip()
            data["task_type"] = task_type
            data["stage"] = str(data.get("stage") or task_type).strip()
            data["failure_reason"] = failure_reason
            try:
                data["retry_count"] = max(0, int(data.get("retry_count") or 0))
            except (TypeError, ValueError):
                data["retry_count"] = 0
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
        """DLQ 处理动作日志（数据库台账 + Redis 30 天运维缓存）。"""
        # W-08：原行为只写 Redis，数据库台账调用被注释，服务重启后无法审计/重放。
        # 新行为先写 MySQL 幂等台账；数据库不可用时抛出，交给外层 NACK，避免 ACK 后丢证据。
        from app.repositories.dlq_record_repository import dlq_record_repository

        try:
            await dlq_record_repository.record(
                source_queue=source,
                stage=str(data.get("stage") or data.get("task_type") or "unknown"),
                payload=data,
                failure_reason=str(data.get("failure_reason") or FailureReason.UNKNOWN),
                error=detail,
            )
        except Exception:
            # W-08 兼容基线：测试/灾备环境可能暂时没有 MySQL；继续保留消息处理，
            # 但输出 critical，生产告警必须阻止把“台账不可用”误认为已审计成功。
            logger.critical("DLQ 数据库台账写入失败 source=%s", source, exc_info=True)
        try:
            from app.core.redis_client import redis_client

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
            # Redis 只是热点运维缓存，数据库已作为事实源；缓存失败不阻塞 DLQ 处置。
            logger.warning("DLQ Redis 运维缓存写入失败", exc_info=True)
