from __future__ import annotations

import pytest

from app.core.identity import RequestIdentity
from app.domain.models import ToolCall, ToolExecutionResult
from app.tools.registry import ToolRegistry


class FakeHub:
    async def invoke(self, **kwargs):
        raise AssertionError("schema-invalid tool call must not reach Capability Hub")


class CompositeHub:
    def __init__(self) -> None:
        self.keys: list[str] = []

    async def invoke(self, **kwargs):
        self.keys.append(kwargs["capability_key"])
        return ToolExecutionResult(
            call_id=kwargs["step_id"], tool_name=kwargs["capability_key"], success=True,
            output={"content": kwargs["input_data"]["file_id"]}, duration_ms=1,
        )


@pytest.mark.asyncio
async def test_tool_registry_rejects_unknown_and_invalid_arguments(settings):
    registry = ToolRegistry(settings, FakeHub())
    unknown = await registry.execute(ToolCall(id="1", name="net.fetch", arguments={}), RequestIdentity("u", None, "r"), "00000000-0000-0000-0000-000000000001", 1)
    invalid = await registry.execute(ToolCall(id="2", name="file.search", arguments={"keyword": "", "url": "http://attacker"}), RequestIdentity("u", None, "r"), "00000000-0000-0000-0000-000000000001", 1)
    assert unknown.error_code == "AI-TOOL-NOT-AVAILABLE"
    assert invalid.error_code == "AI-TOOL-INVALID-ARGUMENTS"


def test_high_risk_batches_are_gated_before_any_tool_executes(settings):
    registry = ToolRegistry(settings, FakeHub())
    approval = registry.approval_required_call([
        ToolCall(id="read", name="file.search", arguments={"keyword": "合同"}),
        ToolCall(id="write", name="workflow.execute", arguments={"workflow_id": "workflow-a"}),
    ])
    assert approval is not None
    assert approval.id == "write"


@pytest.mark.asyncio
async def test_file_compare_composes_two_allowlisted_reads(settings):
    hub = CompositeHub()
    registry = ToolRegistry(settings, hub)
    result = await registry.execute(
        ToolCall(id="compare", name="file.compare", arguments={
            "left_file_id": "11111111-1111-4111-8111-111111111111",
            "right_file_id": "22222222-2222-4222-8222-222222222222",
        }),
        RequestIdentity("u", "s", "r"), "run", 1,
    )
    assert result.success is True
    assert hub.keys == ["api:file.content.get", "api:file.content.get"]
    assert result.output["left"]["content"] == "11111111-1111-4111-8111-111111111111"
