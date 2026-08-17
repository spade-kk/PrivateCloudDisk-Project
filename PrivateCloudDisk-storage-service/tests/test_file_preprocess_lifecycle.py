"""文件内容预处理生命周期契约测试。

覆盖正常候选、无插件跳过、插件失败、插件超时及 Automation 不可用时的存储侧降级。
数据库 CAS 的并发语义由 Repository 集成测试补充；这里先固定公开事件与选择策略。
"""
from __future__ import annotations

import unittest
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import AsyncMock, patch

from app.repositories.file_preprocess_repository import candidate_matches_processed_result
from app.services.file_preprocess_gate_service import FilePreprocessGateService
from core.event.file_backend_event import FileBackendEvent


class FilePreprocessLifecycleTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.service = FilePreprocessGateService()
        self.backend_event = FileBackendEvent(
            backend_task_id="a" * 32,
            stage="merge",
            pipeline_id="pipeline-1",
            file_id="82964eb6-88cb-46a7-880a-02a67f939e39",
            user_id="11111111-1111-4111-8111-111111111111",
            file_name="report.txt",
            file_type="text/plain",
            file_size=12,
            storage_path="",
            uploads_id="upload-1",
            file_checksum="1" * 64,
            upload_checksum="1" * 64,
        )

    async def test_merge_opens_gate_without_exposing_physical_path(self):
        create = AsyncMock(return_value=True)
        merge_data = {
            "storage_path": "/srv/private/uploads/storage/upload-1.cloud",
            "checksum": "1" * 64,
            "file_size": 12,
        }
        with patch(
            "app.services.file_preprocess_gate_service."
            "file_preprocess_repository.create_gate_with_outbox",
            new=create,
        ):
            created = await self.service.open_after_merge(self.backend_event, merge_data)

        self.assertTrue(created)
        kwargs = create.await_args.kwargs
        ready_event = kwargs["ready_event"]
        self.assertEqual(ready_event["type"], "pcd.file.content.ready.v1")
        self.assertTrue(ready_event["data"]["staging_locator"].startswith("pcd-staging://"))
        self.assertNotIn("/srv/private", str(ready_event))
        self.assertEqual(kwargs["original_locator"], merge_data["storage_path"])
        self.assertEqual(len(kwargs["content_lease_hash"]), 64)
        self.assertNotEqual(
            kwargs["content_lease_hash"],
            ready_event["data"]["content_lease_ref"],
        )

    async def test_processed_success_is_forwarded_for_atomic_candidate_selection(self):
        finalize = AsyncMock(return_value={"outcome": "selected"})
        raw = {
            "id": "22222222-2222-4222-8222-222222222222",
            "type": "pcd.file.content.processed.v1",
            "subject": "spaces/personal/files/file",
            "actor_user_id": self.backend_event.user_id,
            "space_id": None,
            "data": {
                "gate_id": "33333333-3333-4333-8333-333333333333",
                "backend_task_id": self.backend_event.backend_task_id,
                "ready_event_id": "44444444-4444-4444-8444-444444444444",
                "status": "success",
                "content_modified": True,
                "candidate_id": "candidate_1",
                "candidate_checksum": "2" * 64,
                "candidate_size": 10,
            },
        }
        with patch(
            "app.services.file_preprocess_gate_service."
            "file_preprocess_repository.finalize_from_processed",
            new=finalize,
        ):
            result = await self.service.handle_processed_event(raw)

        self.assertEqual(result["outcome"], "selected")
        self.assertTrue(finalize.await_args.kwargs["content_modified"])

    async def test_skipped_failed_and_timeout_are_valid_fail_open_results(self):
        for result_status in ("skipped", "failed", "timeout"):
            finalize = AsyncMock(return_value={"outcome": "fallback"})
            raw = {
                "id": "22222222-2222-4222-8222-222222222222",
                "type": "pcd.file.content.processed.v1",
                "subject": "spaces/personal/files/file",
                "actor_user_id": self.backend_event.user_id,
                "space_id": None,
                "data": {
                    "gate_id": "33333333-3333-4333-8333-333333333333",
                    "backend_task_id": self.backend_event.backend_task_id,
                    "ready_event_id": "44444444-4444-4444-8444-444444444444",
                    "status": result_status,
                    "content_modified": False,
                },
            }
            with patch(
                "app.services.file_preprocess_gate_service."
                "file_preprocess_repository.finalize_from_processed",
                new=finalize,
            ):
                await self.service.handle_processed_event(raw)
            self.assertEqual(
                finalize.await_args.kwargs["result_status"],
                result_status,
            )

    async def test_timeout_sentinel_uses_same_fallback_repository_operation(self):
        fallback = AsyncMock(return_value={"outcome": "fallback"})
        raw = {
            "id": "55555555-5555-4555-8555-555555555555",
            "type": "pcd.file.content.timeout.v1",
            "subject": "preprocess-gates/gate",
            "actor_user_id": "",
            "space_id": None,
            "time": datetime.now(timezone.utc).isoformat(),
            "data": {"gate_id": "33333333-3333-4333-8333-333333333333"},
        }
        with patch(
            "app.services.file_preprocess_gate_service."
            "file_preprocess_repository.fallback_and_continue",
            new=fallback,
        ):
            result = await self.service.fallback_from_event(
                raw,
                reason="PREPROCESS_TIMEOUT",
                event_type="pcd.file.content.timeout.v1",
            )
        self.assertEqual(result["outcome"], "fallback")
        self.assertEqual(fallback.await_args.kwargs["reason"], "PREPROCESS_TIMEOUT")

    def test_only_broker_registered_candidate_can_be_selected(self):
        gate = {
            "candidate_id": "candidate_1",
            "candidate_locator": "/private/candidate.cloud",
            "candidate_checksum": "2" * 64,
            "candidate_size": 10,
        }
        self.assertTrue(
            candidate_matches_processed_result(
                gate,
                requested_status="success",
                requested_modified=True,
                candidate_id="candidate_1",
                candidate_checksum="2" * 64,
                candidate_size=10,
            )
        )
        self.assertFalse(
            candidate_matches_processed_result(
                gate,
                requested_status="success",
                requested_modified=True,
                candidate_id="candidate_forged",
                candidate_checksum="2" * 64,
                candidate_size=10,
            )
        )
        self.assertFalse(
            candidate_matches_processed_result(
                gate,
                requested_status="failed",
                requested_modified=False,
                candidate_id=None,
                candidate_checksum=None,
                candidate_size=None,
            )
        )

    def test_cleanup_deletes_only_unselected_copy(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            original = root / "original.cloud"
            candidate_dir = root / ".preprocess" / "gate-1"
            candidate_dir.mkdir(parents=True)
            candidate = candidate_dir / "candidate.cloud"
            original.write_bytes(b"original")
            candidate.write_bytes(b"candidate")
            with patch(
                "app.services.file_preprocess_gate_service.settings.file_upload_dir",
                str(root),
            ):
                self.service._delete_unselected_copy(
                    {
                        "gate_id": "gate-1",
                        "status": "FALLBACK",
                        "original_locator": str(original),
                        "candidate_locator": str(candidate),
                        "selected_locator": str(original),
                    }
                )
            self.assertTrue(original.exists())
            self.assertFalse(candidate.exists())

    def test_cleanup_rejects_locator_outside_upload_root(self):
        with tempfile.TemporaryDirectory() as tmp, tempfile.TemporaryDirectory() as outside:
            target = Path(outside) / "foreign.cloud"
            target.write_bytes(b"do-not-delete")
            with patch(
                "app.services.file_preprocess_gate_service.settings.file_upload_dir",
                tmp,
            ):
                with self.assertRaisesRegex(ValueError, "越过 uploads 根目录"):
                    self.service._delete_unselected_copy(
                        {
                            "gate_id": "gate-2",
                            "status": "SELECTED",
                            "original_locator": str(target),
                            "candidate_locator": str(Path(tmp) / "selected.cloud"),
                            "selected_locator": str(Path(tmp) / "selected.cloud"),
                        }
                    )
            self.assertTrue(target.exists())


if __name__ == "__main__":
    unittest.main()
