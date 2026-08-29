from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from app.core.identity import RequestIdentity
from app.domain.models import AgentRun, ChatMessage, MessageRole, StartRunRequest, ToolExecutionResult
from app.providers.base import ProviderDelta
from app.runtime.agent import AgentRuntime


class FakeRepository:
    def __init__(self) -> None:
        self.messages: list[ChatMessage] = []
        self.runs = {}
        self.tasks = {}
        self.task_events = []

    async def save_run(self, run): self.runs[run.id] = run.model_copy(deep=True)
    async def update_run(self, run): self.runs[run.id] = run.model_copy(deep=True)
    async def append_message(self, user_id, space_id, conversation_id, message): self.messages.append(message)
    async def list_messages(self, user_id, space_id, conversation_id, limit=40): return list(self.messages)
    async def is_cancelled(self, run_id): return False
    async def save_pending_approval(self, run_id, payload): raise AssertionError("unexpected approval")
    async def save_task_snapshot(self, snapshot): self.tasks[snapshot.task_id] = snapshot.model_copy(deep=True)
    async def apply_task_event(self, event): self.task_events.append(event)


class FakeProvider:
    model = "test-model"

    async def stream_chat(self, **kwargs) -> AsyncIterator[ProviderDelta]:
        yield ProviderDelta(content="已完成文件分析。")


class FakeProviderRouter:
    def select(self, requested_model): return FakeProvider()
    def fallback_models(self, selected_model): return ()


class FakeTools:
    def definitions_for_model(self): return []


@pytest.mark.asyncio
async def test_agent_streams_task_output_and_persists_final_answer(settings):
    repository = FakeRepository()
    runtime = AgentRuntime(settings, repository, FakeProviderRouter(), FakeTools())
    run = AgentRun(conversation_id="conversation-a", user_id="user-a", space_id="space-a", model="test-model")
    events = [event async for event in runtime.run(
        run=run,
        identity=RequestIdentity("user-a", "space-a", "request-a"),
        request=StartRunRequest(message="分析文件"),
    )]
    assert any(event.event == "agent_task_start" for event in events)
    assert any(event.event == "plan_created" for event in events)
    assert any(event.event == "output" and event.data["output_text"] == "已完成文件分析。" for event in events)
    assert events[-1].event == "task_completed"
    assert repository.messages[-1].role == MessageRole.ASSISTANT
    assert repository.messages[-1].content == "已完成文件分析。"
    assert repository.messages[-1].run_id == run.id


class ApprovalRepository(FakeRepository):
    def __init__(self) -> None:
        super().__init__()
        self.pending: dict[str, object] | None = None
        self.decision: dict[str, object] | None = None

    async def save_pending_approval(self, run_id, payload):
        self.pending = payload

    async def take_approval_result(self, run_id):
        decision, self.decision = self.decision, None
        return decision


class ApprovalProvider:
    model = "test-model"

    async def stream_chat(self, *, tools, **kwargs) -> AsyncIterator[ProviderDelta]:
        if tools:
            yield ProviderDelta(tool_call_deltas=[{
                "index": 0,
                "id": "call-approved",
                "name": "workflow.execute",
                "arguments": '{"workflow_id":"workflow-1","inputs":{}}',
            }])
            return
        yield ProviderDelta(content="已在你的确认后执行工作流。")


class ApprovalProviderRouter:
    def select(self, requested_model): return ApprovalProvider()
    def fallback_models(self, selected_model): return ()


class ApprovalTools:
    def __init__(self) -> None:
        self.executed = False

    def definitions_for_model(self):
        return [{"type": "function", "function": {"name": "workflow.execute", "parameters": {}}}]

    def approval_required_call(self, calls):
        return calls[0]

    async def execute(self, call, identity, run_id, iteration, *, approval_granted=False, attempt=1):
        assert approval_granted is True
        self.executed = True
        return ToolExecutionResult(
            call_id=call.id, tool_name=call.name, success=True,
            output={"execution_id": "execution-1", "status": "READY"}, duration_ms=7,
        )


class LoopProvider:
    model = "test-model"

    def __init__(self) -> None:
        self.turn = 0

    async def stream_chat(self, *, tools, **kwargs) -> AsyncIterator[ProviderDelta]:
        if tools and self.turn == 0:
            self.turn += 1
            yield ProviderDelta(tool_call_deltas=[{
                "index": 0,
                "id": "call-observe",
                "name": "file.read",
                "arguments": '{"file_id":"11111111-1111-4111-8111-111111111111"}',
            }])
            return
        yield ProviderDelta(content="已根据工具观测完成分析。")


class LoopProviderRouter:
    def __init__(self) -> None:
        self.provider = LoopProvider()

    def select(self, requested_model): return self.provider
    def fallback_models(self, selected_model): return ()


class LoopTools:
    def __init__(self) -> None:
        self.attempts = 0

    def definitions_for_model(self):
        return [{"type": "function", "function": {"name": "file.read", "parameters": {}}}]

    def approval_required_call(self, calls): return None

    async def execute(self, call, identity, run_id, iteration, *, approval_granted=False, attempt=1):
        self.attempts += 1
        if attempt == 1:
            return ToolExecutionResult(
                call_id=call.id, tool_name=call.name, success=False,
                error_code="TEMPORARY", error_message="temporary failure", retryable=True,
            )
        return ToolExecutionResult(
            call_id=call.id, tool_name=call.name, success=True,
            output={"content": "真实受控文件内容"}, duration_ms=4,
        )


@pytest.mark.asyncio
async def test_agent_loop_emits_structured_tool_context_and_retry_progress(settings):
    repository = FakeRepository()
    tools = LoopTools()
    runtime = AgentRuntime(settings, repository, LoopProviderRouter(), tools)
    run = AgentRun(conversation_id="conversation-loop", user_id="user-a", space_id="space-a", model="test-model")
    events = [event async for event in runtime.run(
        run=run,
        identity=RequestIdentity("user-a", "space-a", "request-loop"),
        request=StartRunRequest(message="读取文件后重新评估并完成分析"),
    )]
    event_names = [event.event for event in events]
    assert "tool_call_start" in event_names
    assert "tool_call_end" in event_names
    assert "context_item" in event_names
    assert "plan_item_update" in event_names
    tool_end_index = event_names.index("tool_call_end")
    assert any(index > tool_end_index for index, name in enumerate(event_names) if name == "context_item")
    assert tool_end_index < event_names.index("task_completed")
    assert tools.attempts == 2
    assert events[-1].event == "task_completed"


@pytest.mark.asyncio
async def test_approval_resume_executes_only_server_stored_tool_call(settings):
    repository = ApprovalRepository()
    tools = ApprovalTools()
    runtime = AgentRuntime(settings, repository, ApprovalProviderRouter(), tools)
    run = AgentRun(conversation_id="conversation-a", user_id="user-a", space_id="space-a", model="test-model")
    identity = RequestIdentity("user-a", "space-a", "request-a")

    initial_events = [event async for event in runtime.run(
        run=run, identity=identity, request=StartRunRequest(message="执行周报工作流"),
    )]
    assert initial_events[-1].event == "tool_call_end", [(event.event, event.data) for event in initial_events]
    assert initial_events[-1].data["status"] == "awaiting_approval"
    assert tools.executed is False
    assert repository.pending is not None
    # Simulate the approval endpoint. The client decision never adds/replaces tool args.
    repository.decision = {**repository.pending, "approved": True}  # type: ignore[arg-type]

    resumed_events = [event async for event in runtime.resume(run=run, identity=identity)]
    assert tools.executed is True
    assert any(event.event == "tool_call_end" and event.data["status"] == "completed" for event in resumed_events)
    assert resumed_events[-1].event == "task_completed"
    assert repository.messages[-1].content == "已在你的确认后执行工作流。"


class PlanningProvider:
    model = "test-model"

    def __init__(self) -> None:
        self.calls = 0

    async def stream_chat(self, **kwargs) -> AsyncIterator[ProviderDelta]:
        self.calls += 1
        if self.calls == 1:
            yield ProviderDelta(content='[{"id":"inspect","title":"检查当前文件","details":"先获取可验证信息"},{"id":"report","title":"生成报告"}]')
            return
        yield ProviderDelta(content="已完成动态计划任务。")


class PlanningProviderRouter:
    def __init__(self) -> None:
        self.provider = PlanningProvider()

    def select(self, requested_model): return self.provider
    def fallback_models(self, selected_model): return ()


@pytest.mark.asyncio
async def test_agent_emits_llm_generated_plan_items_not_fixed_plan_text(settings):
    repository = FakeRepository()
    runtime = AgentRuntime(settings, repository, PlanningProviderRouter(), FakeTools())
    run = AgentRun(conversation_id="conversation-plan", user_id="user-a", space_id="space-a", model="test-model")
    events = [event async for event in runtime.run(
        run=run,
        identity=RequestIdentity("user-a", "space-a", "request-plan"),
        request=StartRunRequest(message="检查当前文件并生成报告"),
    )]
    plan = next(event for event in events if event.event == "plan_created")
    assert plan.data["source"] == "llm"
    assert [item["title"] for item in plan.data["plan_items"]] == ["检查当前文件", "生成报告"]
