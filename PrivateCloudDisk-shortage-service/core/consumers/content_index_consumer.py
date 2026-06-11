"""
文件内容索引消费者

监听 pcd.content.index.queue，处理内容索引异步任务:
1. 根据文件类型匹配抽取器
2. 抽取文件内容
3. 写入 OpenSearch

支持指数退避重试和死信队列路由
"""
from __future__ import annotations
import json
import logging
import asyncio
from datetime import datetime, timezone

from core.config import settings, FailureReason
from core.event.file_process_event import ContentIndexEvent
from core.pipeline.content_index_pipeline import ContentIndexPipeline
from core.rabbitmq import rabbitmq_service

logger = logging.getLogger("content_index_consumer")


async def on_content_index_message(message):
    """
    内容索引消息消费者回调

    处理流程:
    1. 解析消息 → ContentIndexEvent
    2. 执行内容索引流水线
    3. 成功 → ACK
    4. 失败 → 未超过重试上限 → 指数退避重试 (NACK + re-queue)
    5. 失败 → 超过重试上限 → 发布到 DLQ → ACK 原消息
    """
    try:
        message_body = message.body.decode("utf-8")
        raw_data = json.loads(message_body)
        event = ContentIndexEvent.from_dict(raw_data)

        logger.info(
            f"收到内容索引消息: file_id={event.file_id}, "
            f"file_name={event.file_name}, "
            f"retry_count={event.retry_count}"
        )

        # 执行内容索引
        result = await ContentIndexPipeline.execute(
            file_id=event.file_id,
            user_id=event.user_id,
            storage_path=event.storage_path,
            file_name=event.file_name,
            file_type=event.file_type,
            file_size=event.file_size,
            node_id=event.node_id,
            created_at=event.created_at,
        )

        if result.success:
            logger.info(
                f"内容索引成功: file_id={event.file_id}, "
                f"extractor={result.data.get('extractor', 'N/A')}, "
                f"chunks={result.data.get('chunk_count', 0)}"
            )
            await message.ack()
            return

        # 索引失败处理
        await _handle_failure(message, event, result)

    except json.JSONDecodeError:
        logger.error("内容索引消息 JSON 解析失败，丢弃")
        await message.ack()
    except Exception as e:
        logger.error(f"内容索引消息处理异常: {e}", exc_info=True)
        await message.ack()


async def _handle_failure(message, event: ContentIndexEvent, result):
    """
    处理内容索引失败

    重试策略:
    - 超过最大重试次数 → 发布到 DLQ
    - 未超过 → 指数退避重试
    """
    max_retries = settings.content_index_max_retries
    failure_reason = result.failure_reason

    if event.retry_count >= max_retries:
        # 超过最大重试次数 → 发布到 DLQ
        logger.warning(
            f"内容索引超过最大重试次数 ({max_retries}): "
            f"file_id={event.file_id}, failure_reason={failure_reason}"
        )

        dlq_event = event.with_retry_increment()
        dlq_event.failure_reason = failure_reason

        await rabbitmq_service.publish_to_dlq(
            exchange_name=settings.content_index_dlx,
            routing_key=settings.content_index_dlq_routing_key,
            message=dlq_event.to_dict(),
        )
        await message.ack()
        return

    # 指数退避重试
    delay = min(
        settings.retry_base_delay_seconds * (2 ** event.retry_count),
        settings.retry_max_delay_seconds,
    )
    logger.warning(
        f"内容索引失败，将进行第 {event.retry_count + 1}/{max_retries} 次重试 "
        f"(延迟 {delay}s): file_id={event.file_id}, reason={failure_reason}"
    )

    await asyncio.sleep(delay)

    retry_event = event.with_retry_increment()
    retry_event.failure_reason = failure_reason

    await rabbitmq_service.publish_message(
        exchange_name=settings.content_index_exchange,
        routing_key=settings.content_index_routing_key,
        message=retry_event.to_dict(),
    )
    await message.ack()