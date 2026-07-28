"""文件增强消息契约与 DLQ 恢复单元测试。"""
from __future__ import annotations

import json
import unittest
from unittest.mock import AsyncMock, patch

from app.repositories.dlq_record_repository import DLQRecordRepository
from core.config import FailureReason, TaskTypes, settings
from core.consumers.dlq.base import BaseDLQConsumer
from core.consumers.enhancement.base_enhance_consumer import BaseEnhanceConsumer
from core.event.file_enhance_event import FileEnhanceEvent
from core.services.file_processor import ProcessResult


class _Message:
    def __init__(self, body: dict):
        self.body = json.dumps(body).encode("utf-8")
        self.ack = AsyncMock()
        self.nack = AsyncMock()


class _DLQProbe(BaseDLQConsumer):
    def __init__(self):
        self.received: dict | None = None

    def _get_dlq_source_name(self) -> str:
        return "test"

    def _get_handler(self, failure_reason: str):
        async def handler(data: dict) -> bool:
            self.received = data
            return True
        return handler


class _EnhanceProbe(BaseEnhanceConsumer):
    @property
    def stage(self) -> str:
        return TaskTypes.OFFICE_TO_PDF

    async def process(self, event: FileEnhanceEvent) -> ProcessResult:
        return ProcessResult(success=True)


class _AsyncContext:
    def __init__(self, value):
        self.value = value

    async def __aenter__(self):
        return self.value

    async def __aexit__(self, exc_type, exc, traceback):
        return False


class _Cursor:
    def __init__(self):
        self.calls: list[tuple[str, tuple]] = []

    async def execute(self, sql: str, values: tuple):
        self.calls.append((sql, values))


class _Connection:
    def __init__(self, cursor: _Cursor):
        self._cursor = cursor
        self.commit = AsyncMock()

    def cursor(self):
        return _AsyncContext(self._cursor)


class _Pool:
    def __init__(self, connection: _Connection):
        self._connection = connection

    def acquire(self):
        return _AsyncContext(self._connection)


class EnhanceMessageContractTests(unittest.IsolatedAsyncioTestCase):
    def _event(self, **overrides) -> FileEnhanceEvent:
        data = {
            "enhance_task_id": "fcc5d4dea2654c849e6d7e259329e202",
            "stage": TaskTypes.OFFICE_TO_PDF,
            "file_id": "82964eb6-88cb-46a7-880a-02a67f939e39",
            "user_id": "11111111-1111-4111-8111-111111111111",
            "file_name": "README.md",
            "file_type": "text/markdown",
        }
        data.update(overrides)
        return FileEnhanceEvent.from_dict(data)

    async def test_stage_is_serialized_as_task_type_and_invalid_retry_is_normalized(self):
        event = self._event(retry_count="not-a-number")
        payload = event.to_dict()
        self.assertEqual(payload["stage"], TaskTypes.OFFICE_TO_PDF)
        self.assertEqual(payload["task_type"], TaskTypes.OFFICE_TO_PDF)
        self.assertEqual(payload["retry_count"], 0)

    async def test_empty_failure_fields_are_normalized_before_dispatch(self):
        consumer = _DLQProbe()
        message = _Message({
            "stage": TaskTypes.OFFICE_TO_PDF,
            "failure_reason": "",
            "retry_count": "broken",
            "file_id": "82964eb6-88cb-46a7-880a-02a67f939e39",
        })
        await consumer.handle(message)
        self.assertIsNotNone(consumer.received)
        self.assertEqual(consumer.received["task_type"], TaskTypes.OFFICE_TO_PDF)
        self.assertEqual(consumer.received["failure_reason"], FailureReason.UNKNOWN)
        self.assertEqual(consumer.received["retry_count"], 0)
        message.ack.assert_awaited_once()
        message.nack.assert_not_awaited()

    async def test_retry_publish_happens_before_ack_and_contains_failure_detail(self):
        consumer = _EnhanceProbe()
        consumer._reset_event_status = AsyncMock()
        event = self._event()
        message = _Message({})
        result = ProcessResult(
            success=False,
            failure_reason=FailureReason.OFFICE_TO_PDF_ERROR,
            error="CDN-independent server conversion failed",
        )
        with patch(
            "core.consumers.enhancement.base_enhance_consumer.rabbitmq_service.publish_message",
            new=AsyncMock(),
        ) as publish:
            await consumer._on_failure(message, event, result)

        publish.assert_awaited_once()
        published = publish.await_args.kwargs
        self.assertTrue(published["routing_key"].endswith(".retry"))
        self.assertEqual(published["message"]["task_type"], TaskTypes.OFFICE_TO_PDF)
        self.assertEqual(published["message"]["retry_count"], 1)
        self.assertIn("server conversion failed", published["message"]["failure_detail"])
        self.assertEqual(published["delay_seconds"], settings.retry_base_delay_seconds)
        message.ack.assert_awaited_once()

    async def test_dlq_upsert_qualifies_retry_count_and_uses_enhance_task_id(self):
        cursor = _Cursor()
        connection = _Connection(cursor)
        pool = _Pool(connection)
        repository = DLQRecordRepository()
        with patch(
            "app.repositories.dlq_record_repository.get_database_pool",
            new=AsyncMock(return_value=pool),
        ):
            await repository.record(
                source_queue="file_enhance",
                stage=TaskTypes.OFFICE_TO_PDF,
                payload=self._event().to_dict(),
                failure_reason=FailureReason.OFFICE_TO_PDF_ERROR,
                error="render failed",
            )

        sql, values = cursor.calls[0]
        self.assertIn(
            "retry_count=pcd_mq_dead_letter_record_table.retry_count+1",
            " ".join(sql.split()),
        )
        self.assertEqual(values[1], "fcc5d4dea2654c849e6d7e259329e202")
        connection.commit.assert_awaited_once()


if __name__ == "__main__":
    unittest.main()
