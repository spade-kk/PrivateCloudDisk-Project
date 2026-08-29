from __future__ import annotations

import pytest

from app.domain.models import AgentTaskSnapshot, StreamEvent
from app.memory.repository import ConversationRepository


class _MemoryRedis:
    def __init__(self) -> None:
        self.values: dict[str, str] = {}

    async def set(self, key, value, ex=None):
        self.values[key] = value

    async def get(self, key):
        return self.values.get(key)


async def _apply(repository: ConversationRepository, run_id: str, sequence: int, event: str, data: dict):
    await repository.apply_task_event(StreamEvent(event=event, run_id=run_id, sequence=sequence, data=data))


@pytest.mark.asyncio
async def test_task_snapshot_projects_blocks_and_keeps_only_tool_output_data():
    repository = ConversationRepository(_MemoryRedis())
    snapshot = AgentTaskSnapshot(
        task_id="run-task", conversation_id="conversation-a", user_id="user-a",
        space_id="space-a", user_request="读取文件并总结", model="test-model",
    )
    await repository.save_task_snapshot(snapshot)
    await _apply(repository, "run-task", 1, "agent_task_start", {"task_id": "run-task"})
    await _apply(repository, "run-task", 2, "thinking_start", {"block_id": "think"})
    await _apply(repository, "run-task", 3, "thinking_delta", {"block_id": "think", "delta": "正在确定文件范围。"})
    await _apply(repository, "run-task", 4, "plan_created", {
        "block_id": "plan", "source": "llm",
        "plan_items": [{"id": "read", "title": "读取文件", "details": "验证内容", "status": "pending"}],
    })
    await _apply(repository, "run-task", 5, "tool_call_start", {
        "block_id": "tool-call", "call_id": "call-1", "tool_name": "file.read",
        "command": "调用 file.read", "input": {"file_id": "file-1"},
    })
    await _apply(repository, "run-task", 6, "tool_call_end", {
        "block_id": "tool-call", "call_id": "call-1", "status": "completed",
        "output_data": {"content": "受限文件内容"}, "duration_ms": 8,
        # This resembles an old wrapper and must not leak into the result field.
        "result": {"success": True, "output": {"content": "wrong-wrapper"}},
    })
    await _apply(repository, "run-task", 7, "summary", {
        "block_id": "summary", "summary_text": "文件已总结。", "format": "markdown",
    })
    await _apply(repository, "run-task", 8, "task_completed", {})

    restored = await repository.get_task_snapshot("run-task")
    assert restored is not None
    assert restored.status == "completed"
    tool = next(block for block in restored.blocks if block.type == "tool_call")
    assert tool.data["output_data"] == {"content": "受限文件内容"}
    assert "result" not in tool.data
    assert next(block for block in restored.blocks if block.type == "summary").data["summary_text"] == "文件已总结。"
