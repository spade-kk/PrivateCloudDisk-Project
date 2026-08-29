from __future__ import annotations

import pytest
from fastapi import HTTPException

from app.api.routes import get_task_snapshot
from app.core.identity import RequestIdentity
from app.domain.models import AgentRun, AgentTaskSnapshot


class _TaskRepository:
    def __init__(self, run: AgentRun | None, snapshot: AgentTaskSnapshot | None) -> None:
        self.run = run
        self.snapshot = snapshot

    async def get_run(self, run_id: str):
        return self.run if self.run and self.run.id == run_id else None

    async def get_task_snapshot(self, run_id: str):
        return self.snapshot if self.snapshot and self.snapshot.task_id == run_id else None


@pytest.mark.asyncio
async def test_task_recovery_returns_only_the_callers_tenant_snapshot():
    run = AgentRun(conversation_id="conversation", user_id="user-a", space_id="space-a", model="MiniMax")
    snapshot = AgentTaskSnapshot(
        task_id=run.id,
        conversation_id=run.conversation_id,
        user_id="user-a",
        space_id="space-a",
        user_request="读取资料并总结",
        model="MiniMax",
    )
    identity = RequestIdentity(user_id="user-a", space_id="space-a", request_id="request-a")

    restored = await get_task_snapshot(run.id, identity, _TaskRepository(run, snapshot))

    assert restored.task_id == run.id
    assert restored.user_request == "读取资料并总结"
    assert "user_id" not in restored.model_dump()


@pytest.mark.asyncio
async def test_task_recovery_hides_cross_tenant_runs_with_not_found():
    run = AgentRun(conversation_id="conversation", user_id="user-a", space_id="space-a", model="MiniMax")
    snapshot = AgentTaskSnapshot(
        task_id=run.id,
        conversation_id=run.conversation_id,
        user_id="user-a",
        space_id="space-a",
        user_request="私有任务",
        model="MiniMax",
    )
    identity = RequestIdentity(user_id="user-b", space_id="space-a", request_id="request-b")

    with pytest.raises(HTTPException) as error:
        await get_task_snapshot(run.id, identity, _TaskRepository(run, snapshot))

    assert error.value.status_code == 404
