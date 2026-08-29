from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
import json

import pytest

from app.api.routes import encode_sse_events
from app.domain.models import AgentRun, StreamEvent


async def delayed_stream(run_id: str) -> AsyncIterator[StreamEvent]:
    await asyncio.sleep(0.03)
    yield StreamEvent(event="token", run_id=run_id, sequence=1, data={"delta": "ok"})


@pytest.mark.asyncio
async def test_sse_emits_non_replaying_heartbeat_while_waiting(settings):
    run = AgentRun(conversation_id="conversation", user_id="user", space_id=None, model="model")
    fast_heartbeat_settings = settings.model_copy(update={"sse_heartbeat_seconds": 0.01})
    chunks = [
        chunk async for chunk in encode_sse_events(delayed_stream(run.id), run, fast_heartbeat_settings)
    ]
    joined = b"".join(chunks).decode("utf-8")
    assert "event: heartbeat" in joined
    assert "event: token" in joined


def test_structured_sse_payload_always_contains_task_identity_and_timestamp():
    event = StreamEvent(
        event="agent_task_start",
        run_id="run-structured",
        sequence=7,
        data={"user_request": "整理项目资料"},
    )
    encoded = event.encode().decode("utf-8")

    payload = json.loads(next(line[6:] for line in encoded.splitlines() if line.startswith("data: ")))
    assert payload["task_id"] == "run-structured"
    assert payload["run_id"] == "run-structured"
    assert payload["sequence"] == 7
    assert payload["timestamp"]
    assert event.payload()["task_id"] == payload["task_id"]
