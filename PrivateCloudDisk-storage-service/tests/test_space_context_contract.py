"""空间上下文在存储流水线、MQ 与搜索索引之间传播的契约测试。"""
from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from core.event.file_backend_event import FileBackendEvent
from core.event.file_enhance_event import FileEnhanceEvent
from core.pipeline.content_index_pipeline import ContentIndexPipeline
from core.search.index_mapping import FILE_BASIC_INDEX_BODY, FILE_CONTENT_INDEX_BODY


class SpaceContextContractTests(unittest.IsolatedAsyncioTestCase):
    def _backend_event(self) -> FileBackendEvent:
        return FileBackendEvent(
            backend_task_id="backend-task",
            stage="merge",
            pipeline_id="pipeline",
            file_id="11111111-1111-4111-8111-111111111111",
            user_id="22222222-2222-4222-8222-222222222222",
            file_name="demo.txt",
            file_type="text/plain",
            space_id="33333333-3333-4333-8333-333333333333",
            space_type="team",
        )

    async def test_backend_next_stage_keeps_space_context(self):
        next_event = self._backend_event().with_next_stage("hash_calculate")
        self.assertEqual(next_event.space_id, "33333333-3333-4333-8333-333333333333")
        self.assertEqual(next_event.space_type, "team")

    async def test_enhance_message_round_trip_keeps_space_context(self):
        backend = self._backend_event()
        event = FileEnhanceEvent(
            enhance_task_id="enhance-task",
            stage="content_index",
            file_id=backend.file_id,
            user_id=backend.user_id,
            file_name=backend.file_name,
            file_type=backend.file_type,
            space_id=backend.space_id,
            space_type=backend.space_type,
        )
        restored = FileEnhanceEvent.from_dict(event.to_dict())
        self.assertEqual(restored.space_id, backend.space_id)
        self.assertEqual(restored.space_type, "team")

    async def test_basic_search_document_contains_space_id(self):
        with patch(
            "core.pipeline.content_index_pipeline.IndexService.index_file_basic",
            new=AsyncMock(),
        ) as index:
            await ContentIndexPipeline._index_basic_only(
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222",
                "44444444-4444-4444-8444-444444444444",
                "demo.bin",
                "bin",
                "application/octet-stream",
                8,
                "2026-07-27T00:00:00Z",
                "",
                "33333333-3333-4333-8333-333333333333",
            )
        document = index.await_args.args[0]
        self.assertEqual(document["space_id"], "33333333-3333-4333-8333-333333333333")

    async def test_both_search_mappings_define_keyword_space_id(self):
        self.assertEqual(
            FILE_BASIC_INDEX_BODY["mappings"]["properties"]["space_id"],
            {"type": "keyword"},
        )
        self.assertEqual(
            FILE_CONTENT_INDEX_BODY["mappings"]["properties"]["space_id"],
            {"type": "keyword"},
        )


if __name__ == "__main__":
    unittest.main()
