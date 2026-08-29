"""Opt-in real-provider and AgentRuntime task tests for the production adapter.

This test deliberately obtains its credential only from the process environment. It
never logs, serializes, asserts, prints, or persists the secret. CI keeps all tests
skipped unless an approved secret-injection job sets both required environment variables.
"""

from __future__ import annotations

import os
from typing import Any

import pytest
from pydantic import SecretStr

from app.core.identity import RequestIdentity
from app.domain.models import AgentRun, ChatMessage, StartRunRequest, ToolExecutionResult
from app.providers.openai_compatible import OpenAICompatibleProvider
from app.runtime.agent import AgentRuntime
from app.tools.registry import ToolRegistry


LIVE_BASE_URL = "https://llm-api.arkcat.cn/v1"
LIVE_MODEL = "MiniMax"


def _live_test_enabled() -> bool:
    return os.getenv("RUN_LIVE_LLM_TESTS") == "1" and bool(os.getenv("REMOTE_STUDIO_AUTH_TOKEN"))


@pytest.mark.live_llm
@pytest.mark.asyncio
@pytest.mark.skipif(
    not _live_test_enabled(),
    reason="requires RUN_LIVE_LLM_TESTS=1 and injected REMOTE_STUDIO_AUTH_TOKEN",
)
async def test_minimax_real_provider_streams_through_project_adapter(settings):
    """Verify the actual project adapter, not a raw SDK call or mock transport."""
    live_settings = settings.model_copy(update={
        "llm_base_url": LIVE_BASE_URL,
        "llm_api_key": SecretStr(os.environ["REMOTE_STUDIO_AUTH_TOKEN"]),
        "llm_model": LIVE_MODEL,
        "llm_fallback_models": "",
        "llm_timeout_seconds": 60.0,
        "max_provider_concurrency": 1,
    })
    provider = OpenAICompatibleProvider(live_settings)

    async def not_cancelled() -> bool:
        return False

    try:
        parts: list[str] = []
        async for delta in provider.stream_chat(
            messages=[
                {"role": "system", "content": "You are a concise integration-test assistant."},
                {"role": "user", "content": "Reply with exactly LIVE_PROVIDER_OK."},
            ],
            tools=[],
            cancellation_check=not_cancelled,
        ):
            parts.append(delta.content)
        response = "".join(parts).strip()
        # Provider wording may include punctuation or a short explanation. The assertion
        # proves a non-empty streamed answer reached this adapter without exposing it.
        assert response
        assert "LIVE_PROVIDER_OK" in response
    finally:
        await provider.close()


class _LiveRepository:
    """In-memory Agent state for real-model tests; no enterprise data is mutated."""

    def __init__(self) -> None:
        self.messages: list[ChatMessage] = []
        self.runs: dict[str, AgentRun] = {}
        self.tasks: dict[str, Any] = {}
        self.task_events: list[Any] = []
        self.pending: dict[str, dict[str, Any]] = {}
        self.approval_results: dict[str, dict[str, Any]] = {}
        self.cancelled: set[str] = set()

    async def save_run(self, run: AgentRun) -> None:
        self.runs[run.id] = run.model_copy(deep=True)

    async def update_run(self, run: AgentRun) -> None:
        await self.save_run(run)

    async def append_message(self, user_id: str, space_id: str | None, conversation_id: str, message: ChatMessage) -> None:
        self.messages.append(message)

    async def list_messages(self, user_id: str, space_id: str | None, conversation_id: str, offset: int = 0, limit: int = 100) -> list[ChatMessage]:
        return list(self.messages[offset:offset + limit])

    async def is_cancelled(self, run_id: str) -> bool:
        return run_id in self.cancelled

    async def save_task_snapshot(self, snapshot: Any) -> None:
        self.tasks[snapshot.task_id] = snapshot.model_copy(deep=True)

    async def apply_task_event(self, event: Any) -> None:
        self.task_events.append(event)

    async def save_pending_approval(self, run_id: str, payload: dict[str, Any]) -> None:
        self.pending[run_id] = payload

    async def take_approval_result(self, run_id: str) -> dict[str, Any] | None:
        return self.approval_results.pop(run_id, None)


class _RecordingCapabilityHub:
    """Capability Hub test double returning safe, deterministic read-only fixtures."""

    def __init__(self) -> None:
        self.calls: list[dict[str, Any]] = []

    async def invoke(self, *, capability_key: str, identity: RequestIdentity, run_id: str,
                     step_id: str, attempt: int, input_data: dict[str, Any],
                     permissions: tuple[str, ...]) -> ToolExecutionResult:
        self.calls.append({
            "capability_key": capability_key,
            "input": input_data,
            "attempt": attempt,
            "run_id": run_id,
            "step_id": step_id,
            "user_id": identity.user_id,
            "space_id": identity.space_id,
            "permissions": permissions,
        })
        fixtures: dict[str, dict[str, Any]] = {
            "api:file.search": {"items": [{"file_id": "11111111-1111-4111-8111-111111111111", "name": "合同-A.md"}]},
            "api:file.content.get": {"file_id": input_data.get("file_id"), "content": "收入增长 12%，应收账款周转天数上升。"},
            "api:file.metadata.get": {"file_id": input_data.get("file_id"), "name": "合同-A.md", "size": 4096, "content_type": "text/markdown"},
            "api:file.list": {"items": [
                {"file_id": "11111111-1111-4111-8111-111111111111", "name": "一月报告.md", "size": 1024, "updated_at": "2026-01-05T00:00:00Z"},
                {"file_id": "22222222-2222-4222-8222-222222222222", "name": "二月报告.md", "size": 2048, "updated_at": "2026-02-05T00:00:00Z"},
            ], "total": 2},
            "api:space.info": {"space_id": input_data.get("space_id"), "name": "集成测试空间", "member_count": 4},
            "api:workflow.validate": {"valid": True, "apiVersion": "workflow.cloudflow.io/v1"},
            "api:workflow.status": {"execution_id": input_data.get("execution_id"), "status": "SUCCESS", "progress": 100},
            "api:workflow.execute": {"execution_id": "execution-live-test", "status": "READY"},
        }
        output = fixtures.get(capability_key, {"ok": True, "capability_key": capability_key})
        return ToolExecutionResult(
            call_id=step_id,
            tool_name=capability_key,
            success=True,
            output=output,
            duration_ms=1,
        )


class _FixedProviderRouter:
    def __init__(self, provider: Any) -> None:
        self.provider = provider

    def select(self, requested_model: str | None) -> OpenAICompatibleProvider:
        return self.provider

    def fallback_models(self, selected_model: str) -> tuple[str, ...]:
        return ()


class _ForcedFirstToolProvider:
    """Force only the first live turn to use the requested tool for stable diagnostics."""

    def __init__(self, provider: OpenAICompatibleProvider, tool_name: str) -> None:
        self._provider = provider
        self._tool_name = tool_name
        self._first_turn = True
        self.model = provider.model

    async def stream_chat(self, *, messages, tools, cancellation_check):
        tool_choice = None
        if self._first_turn and tools:
            tool_choice = {"type": "function", "function": {"name": self._tool_name}}
            self._first_turn = False
        async for delta in self._provider.stream_chat(
            messages=messages,
            tools=tools,
            tool_choice=tool_choice,
            cancellation_check=cancellation_check,
        ):
            yield delta


def _live_settings(settings):
    return settings.model_copy(update={
        "llm_base_url": LIVE_BASE_URL,
        "llm_api_key": SecretStr(os.environ["REMOTE_STUDIO_AUTH_TOKEN"]),
        "llm_model": LIVE_MODEL,
        "llm_fallback_models": "",
        "llm_timeout_seconds": 60.0,
        "max_provider_concurrency": 1,
        "max_agent_iterations": 6,
        "max_run_seconds": 90,
    })


async def _run_real_agent_task(settings, prompt: str, hub: _RecordingCapabilityHub, required_first_tool: str):
    live_settings = _live_settings(settings)
    provider = OpenAICompatibleProvider(live_settings)
    repository = _LiveRepository()
    runtime_provider = _ForcedFirstToolProvider(provider, required_first_tool)
    runtime = AgentRuntime(live_settings, repository, _FixedProviderRouter(runtime_provider), ToolRegistry(live_settings, hub))
    identity = RequestIdentity("live-test-user", "live-test-space", "live-test-request")
    run = AgentRun(conversation_id="live-test-conversation", user_id=identity.user_id, space_id=identity.space_id, model=LIVE_MODEL)
    try:
        events = [event async for event in runtime.run(run=run, identity=identity, request=StartRunRequest(message=prompt))]
        return events, repository, runtime, identity, run
    finally:
        await provider.close()


def _capability_keys(hub: _RecordingCapabilityHub) -> list[str]:
    return [str(call["capability_key"]) for call in hub.calls]


@pytest.mark.live_llm
@pytest.mark.asyncio
@pytest.mark.skipif(not _live_test_enabled(), reason="requires RUN_LIVE_LLM_TESTS=1 and injected REMOTE_STUDIO_AUTH_TOKEN")
async def test_minimax_real_agent_file_search_read_summarize_compare_and_report(settings):
    """Real MiniMax drives the project AgentRuntime through a read-only multi-step task.

    The fake Hub is deliberately read-only: this validates model planning, tool-call
    decoding, observation/reflection events and context hand-off without touching a
    user's file. The task asks for search -> read -> compare -> Markdown report in one
    conversation, which is the safe equivalent of the user's file-analysis workflow.
    """
    hub = _RecordingCapabilityHub()
    events, _, _, _, _ = await _run_real_agent_task(
        settings,
        """完成一个真实的企业文件分析任务，并且不要凭空编造内容。必须先调用 file.search 搜索“合同”，
        再根据搜索结果调用 file.read 读取文件；如果需要比较，请再读取 file_id
        22222222-2222-4222-8222-222222222222。观察工具结果后，比较两个文件并输出一份简短 Markdown 报告，
        包含结论、风险和建议。整个过程中不得声称执行了未调用的操作。""",
        hub,
        "file.search",
    )
    keys = _capability_keys(hub)
    assert "api:file.search" in keys, {
        "capabilities": keys,
        "events": [(event.event, event.data.get("name"), event.data.get("code")) for event in events],
    }
    assert "api:file.content.get" in keys, keys
    assert any(event.event == "plan_created" for event in events), [event.event for event in events]
    assert any(event.event == "tool_call_end" for event in events), [event.event for event in events]
    assert events[-1].event == "task_completed", [(event.event, event.data.get("code")) for event in events]


@pytest.mark.live_llm
@pytest.mark.asyncio
@pytest.mark.skipif(not _live_test_enabled(), reason="requires RUN_LIVE_LLM_TESTS=1 and injected REMOTE_STUDIO_AUTH_TOKEN")
async def test_minimax_real_agent_composes_file_compare_into_report(settings):
    """Force the reviewed composite compare tool and let MiniMax explain its result."""
    hub = _RecordingCapabilityHub()
    events, _, _, _, _ = await _run_real_agent_task(
        settings,
        """请比较文件 11111111-1111-4111-8111-111111111111 和
        22222222-2222-4222-8222-222222222222 的内容，必须调用 file.compare，
        然后根据工具返回结果生成中文 Markdown 摘要和风险建议。不要编造文件内容。""",
        hub,
        "file.compare",
    )
    keys = _capability_keys(hub)
    assert keys.count("api:file.content.get") == 2, keys
    assert any(event.event == "plan_created" for event in events), [event.event for event in events]
    assert events[-1].event == "task_completed", [(event.event, event.data.get("code")) for event in events]


@pytest.mark.live_llm
@pytest.mark.asyncio
@pytest.mark.skipif(not _live_test_enabled(), reason="requires RUN_LIVE_LLM_TESTS=1 and injected REMOTE_STUDIO_AUTH_TOKEN")
async def test_minimax_real_agent_space_query_capacity_and_trend(settings):
    """Real MiniMax combines space metadata and file inventory into an analysis."""
    hub = _RecordingCapabilityHub()
    events, _, _, _, _ = await _run_real_agent_task(
        settings,
        """请分析空间 live-test-space：必须调用 space.info 查询空间，再调用 file.list 获取文件清单。
        基于真实工具结果统计容量，并根据 updated_at 分析文件变化趋势；只使用工具返回的数据回答。""",
        hub,
        "space.info",
    )
    keys = _capability_keys(hub)
    assert "api:space.info" in keys, keys
    assert "api:file.list" in keys, keys
    assert any(event.event == "context_item" for event in events), [event.event for event in events]
    assert events[-1].event == "task_completed", [(event.event, event.data.get("code")) for event in events]


@pytest.mark.live_llm
@pytest.mark.asyncio
@pytest.mark.skipif(not _live_test_enabled(), reason="requires RUN_LIVE_LLM_TESTS=1 and injected REMOTE_STUDIO_AUTH_TOKEN")
async def test_minimax_real_agent_creates_validates_and_queries_workflow(settings):
    """Real MiniMax must create DSL, validate it, then query an execution status."""
    hub = _RecordingCapabilityHub()
    events, _, _, _, _ = await _run_real_agent_task(
        settings,
        """请设计一个最小的 CloudFlow .flow 工作流，先生成合法 DSL，必须调用 workflow.validate 校验它，
        不要调用 workflow.execute。校验成功后，再调用 workflow.status 查询 execution_id execution-live-test，
        最后说明 DSL 是否有效以及任务状态。""",
        hub,
        "workflow.validate",
    )
    keys = _capability_keys(hub)
    assert "api:workflow.validate" in keys, keys
    assert "api:workflow.status" in keys, keys
    assert "api:workflow.execute" not in keys, keys
    assert events[-1].event == "task_completed", [(event.event, event.data.get("code")) for event in events]


@pytest.mark.live_llm
@pytest.mark.asyncio
@pytest.mark.skipif(not _live_test_enabled(), reason="requires RUN_LIVE_LLM_TESTS=1 and injected REMOTE_STUDIO_AUTH_TOKEN")
async def test_minimax_real_agent_workflow_execute_requires_approval(settings):
    """A real model may plan execution, but the server must pause before side effects."""
    hub = _RecordingCapabilityHub()
    events, _, _, _, _ = await _run_real_agent_task(
        settings,
        """请执行已经发布的工作流 workflow-live-test，传入 inputs {\"period\": \"weekly\"}，
        这是可能改变企业数据的操作，必须先请求用户审批，不能假设执行成功。""",
        hub,
        "workflow.execute",
    )
    assert events[-1].event == "tool_call_end", [(event.event, event.data.get("code")) for event in events]
    assert events[-1].data.get("status") == "awaiting_approval"
    assert "api:workflow.execute" not in _capability_keys(hub), _capability_keys(hub)


@pytest.mark.live_llm
@pytest.mark.asyncio
@pytest.mark.skipif(not _live_test_enabled(), reason="requires RUN_LIVE_LLM_TESTS=1 and injected REMOTE_STUDIO_AUTH_TOKEN")
async def test_minimax_real_agent_plugin_call_stops_for_approval(settings):
    """Real MiniMax may plan a plugin call, but side effects stop at server approval."""
    hub = _RecordingCapabilityHub()
    events, repository, runtime, identity, run = await _run_real_agent_task(
        settings,
        """请调用插件能力 plugin:demo-plugin:generate_report@1，参数 input 为 {\"period\":\"weekly\"}。
        这是一个需要用户确认的高风险操作，请先请求系统审批，不要自行假设已执行。""",
        hub,
        "plugin.call",
    )
    assert events[-1].event == "tool_call_end", [(event.event, event.data.get("code")) for event in events]
    assert events[-1].data.get("status") == "awaiting_approval"
    assert not any(key.startswith("plugin:") for key in _capability_keys(hub)), _capability_keys(hub)
    pending = repository.pending.get(run.id)
    assert pending and pending.get("calls"), "审批上下文未保存"
    repository.approval_results[run.id] = {**pending, "approved": False}
    resumed = [event async for event in runtime.resume(run=run, identity=identity)]
    assert resumed[-1].event == "task_cancelled"
    assert run.status.value == "CANCELLED"
