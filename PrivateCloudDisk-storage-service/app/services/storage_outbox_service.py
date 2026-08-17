"""Storage Outbox 发布与恢复服务。"""
from __future__ import annotations

import asyncio
import logging

from app.repositories.file_preprocess_repository import file_preprocess_repository
from core.config import settings
from core.rabbitmq import rabbitmq_service

logger = logging.getLogger("storage_outbox")


class StorageOutboxService:
    """以数据库为事实源发布生命周期与 hash 事实事件。"""

    async def run_publisher(self, stop_event: asyncio.Event) -> None:
        while not stop_event.is_set():
            try:
                await file_preprocess_repository.recover_stale_outbox(
                    settings.storage_outbox_publish_lease_seconds
                )
                rows = await file_preprocess_repository.claim_outbox_batch(
                    settings.storage_outbox_batch_size
                )
                if not rows:
                    await self._wait_or_stop(stop_event, settings.storage_outbox_poll_seconds)
                    continue
                for row in rows:
                    try:
                        await rabbitmq_service.publish_message(
                            exchange_name=row["exchange_name"],
                            routing_key=row["routing_key"],
                            message=row["payload_json"],
                        )
                        await file_preprocess_repository.mark_outbox_sent(row["outbox_id"])
                        logger.info(
                            "[STORAGE-OUTBOX] 发布 outbox_id=%s event_type=%s exchange_name=%s routing_key=%s",
                            row["outbox_id"],
                            row["event_type"],
                            row["exchange_name"],
                            row["routing_key"]
                        )
                    except Exception as exc:
                        delay = min(2 ** min(int(row["retry_count"]), 8), 300)
                        await file_preprocess_repository.mark_outbox_failed(
                            row["outbox_id"], str(exc), delay
                        )
                        logger.exception(
                            "[STORAGE-OUTBOX] 发布失败 outbox_id=%s event_type=%s",
                            row["outbox_id"],
                            row["event_type"],
                        )
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("[STORAGE-OUTBOX] 发布循环异常")
                await self._wait_or_stop(stop_event, settings.storage_outbox_poll_seconds)

    async def run_gate_sweeper(self, stop_event: asyncio.Event) -> None:
        from app.services.file_preprocess_gate_service import file_preprocess_gate_service

        while not stop_event.is_set():
            try:
                closed = await file_preprocess_gate_service.sweep_expired()
                if closed:
                    logger.warning("[PREPROCESS-SWEEPER] 已降级并继续 %s 个超时文件", closed)
                cleaned = await file_preprocess_gate_service.cleanup_committed(
                    settings.file_preprocess_sweeper_batch_size
                )
                if cleaned:
                    logger.info("[PREPROCESS-SWEEPER] 已回收 %s 个未选中内容副本", cleaned)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("[PREPROCESS-SWEEPER] 扫描异常")
            await self._wait_or_stop(stop_event, settings.file_preprocess_sweeper_interval_seconds)

    @staticmethod
    async def _wait_or_stop(stop_event: asyncio.Event, seconds: float) -> None:
        try:
            await asyncio.wait_for(stop_event.wait(), timeout=seconds)
        except asyncio.TimeoutError:
            pass


storage_outbox_service = StorageOutboxService()
