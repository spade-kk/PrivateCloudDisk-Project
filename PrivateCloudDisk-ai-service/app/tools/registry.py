"""Model-visible tools with server-static capability and permission mappings."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any

from jsonschema import Draft202012Validator

from app.core.config import Settings
from app.core.identity import RequestIdentity
from app.core.redaction import redact
from app.domain.models import ToolCall, ToolExecutionResult
from app.tools.capability_hub import CapabilityHubClient


@dataclass(frozen=True, slots=True)
class ToolDefinition:
    name: str
    description: str
    parameters: dict[str, Any]
    capability_key: str | None
    permissions: tuple[str, ...]
    risk: str = "read"
    enabled: bool = True
    dynamic_plugin_capability: bool = False
    composite: str | None = None

    def openai_definition(self) -> dict[str, Any]:
        return {
            "type": "function",
            "function": {"name": self.name, "description": self.description, "parameters": self.parameters, "strict": False},
        }


class ToolRegistry:
    """Validates model calls before delegating to Capability Hub.

    [AI-AGENT-TOOLS-001] Previous direct-worker behavior was capable of reaching
    storage and infrastructure itself. New behavior has a static registry, rejects
    unknown schemas, and uses only registered Capability Hub keys. The impact is that
    adding a tool now requires a reviewed capability registration and a test.
    """

    def __init__(self, settings: Settings, capability_hub: CapabilityHubClient) -> None:
        self._hub = capability_hub
        self._tools = self._build_tools(settings)

    @staticmethod
    def _object(properties: dict[str, Any], required: list[str] | None = None) -> dict[str, Any]:
        return {"type": "object", "properties": properties, "required": required or [], "additionalProperties": False}

    def _build_tools(self, settings: Settings) -> dict[str, ToolDefinition]:
        tools = [
            ToolDefinition("file.search", "搜索当前用户有权限访问的企业文件。", self._object({"keyword": {"type": "string", "minLength": 1, "maxLength": 200}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 100}}, ["keyword"]), "api:file.search", ("file.read",)),
            ToolDefinition("file.read", "读取文本、代码或 Markdown 文件的受限内容。", self._object({"file_id": {"type": "string", "pattern": "^[0-9a-fA-F-]{36}$"}, "max_bytes": {"type": "integer", "minimum": 1024, "maximum": 1048576}, "text_only": {"type": "boolean"}}, ["file_id"]), "api:file.content.get", ("file.read",)),
            ToolDefinition("file.metadata", "读取文件元数据，不下载文件。", self._object({"file_id": {"type": "string", "pattern": "^[0-9a-fA-F-]{36}$"}}, ["file_id"]), "api:file.metadata.get", ("file.read",)),
            ToolDefinition("file.summary", "读取文件受限内容后生成摘要；事实来源仍是文件能力。", self._object({"file_id": {"type": "string", "pattern": "^[0-9a-fA-F-]{36}$"}, "max_bytes": {"type": "integer", "minimum": 1024, "maximum": 1048576}}, ["file_id"]), "api:file.content.get", ("file.read",)),
            ToolDefinition("file.compare", "通过两次受限文件读取比较两个文件，不直接访问文件系统。", self._object({"left_file_id": {"type": "string", "pattern": "^[0-9a-fA-F-]{36}$"}, "right_file_id": {"type": "string", "pattern": "^[0-9a-fA-F-]{36}$"}, "max_bytes": {"type": "integer", "minimum": 1024, "maximum": 1048576}}, ["left_file_id", "right_file_id"]), None, ("file.read",), composite="file.compare"),
            ToolDefinition("file.list", "列出当前空间或目录中的可访问文件。", self._object({"parent_id": {"type": "string", "maxLength": 128}, "keyword": {"type": "string", "maxLength": 200}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 200}}), "api:file.list", ("file.read",)),
            ToolDefinition("space.info", "读取一个空间的基本信息。", self._object({"space_id": {"type": "string", "maxLength": 128}}, ["space_id"]), "api:space.info", ("space.read",)),
            ToolDefinition("space.members", "读取一个空间中用户有权限查看的成员列表。", self._object({"space_id": {"type": "string", "maxLength": 128}, "keyword": {"type": "string", "maxLength": 200}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 200}}, ["space_id"]), "api:space.members.list", ("space.read",)),
            # The Hub's file.list capability owns the identity/space membership check;
            # declaring only its registered file.read permission avoids inventing a
            # second permission contract for this read-only aggregation.
            ToolDefinition("space.capacity", "统计空间当前可访问文件的容量；使用文件列表能力汇总。", self._object({"space_id": {"type": "string", "maxLength": 128}, "size": {"type": "integer", "minimum": 1, "maximum": 200}}, ["space_id"]), "api:file.list", ("file.read",)),
            ToolDefinition("space.trend", "基于文件列表的更新时间和大小分析空间趋势。", self._object({"space_id": {"type": "string", "maxLength": 128}, "size": {"type": "integer", "minimum": 1, "maximum": 200}}, ["space_id"]), "api:file.list", ("file.read",)),
            ToolDefinition("user.info", "读取脱敏后的用户基本资料。", self._object({"user_id": {"type": "string", "maxLength": 128}}), "api:user.info", ("user.profile.read",)),
            # The following keys are enabled only after the matching reviewed Capability Hub
            # registrations are deployed. Keeping these mappings explicit prevents a model
            # from inventing a direct CloudFlow or Plugin Runtime HTTP endpoint.
            ToolDefinition("workflow.list", "列出当前用户可访问的工作流。", self._object({"page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 100}}), "api:workflow.list", ("workflow.read",), enabled=settings.enable_workflow_tools),
            ToolDefinition("workflow.validate", "编译并校验 CloudFlow .flow DSL，不执行工作流。", self._object({"dsl": {"type": "string", "minLength": 1, "maxLength": 1048576}}, ["dsl"]), "api:workflow.validate", ("workflow.write",), enabled=settings.enable_workflow_tools),
            ToolDefinition("workflow.execute", "执行已经发布的工作流；高风险场景必须先审批。", self._object({"workflow_id": {"type": "string", "maxLength": 128}, "inputs": {"type": "object"}}, ["workflow_id"]), "api:workflow.execute", ("workflow.execute",), risk="approval", enabled=settings.enable_workflow_tools),
            ToolDefinition("workflow.status", "查询工作流执行状态。", self._object({"execution_id": {"type": "string", "maxLength": 128}}, ["execution_id"]), "api:workflow.status", ("workflow.read",), enabled=settings.enable_workflow_tools),
            # Plugin capability side effects cannot be inferred safely from a model name.
            # Requiring explicit user approval keeps arbitrary third-party/plugin actions
            # inside the same recoverable, auditable guard as workflow execution.
            ToolDefinition("plugin.call", "调用当前用户已获授权的插件能力；执行前需要确认。", self._object({"capability_key": {"type": "string", "pattern": "^plugin:[a-zA-Z0-9._:-]{1,220}$"}, "input": {"type": "object"}}, ["capability_key"]), None, ("plugin.execute",), risk="approval", enabled=settings.enable_plugin_tools, dynamic_plugin_capability=True),
        ]
        return {tool.name: tool for tool in tools}

    def definitions_for_model(self) -> list[dict[str, Any]]:
        return [tool.openai_definition() for tool in self._tools.values() if tool.enabled]

    def approval_required_call(self, calls: list[ToolCall]) -> ToolCall | None:
        """Return the first reviewed high-risk call before executing a batch.

        A model can emit several function calls in one response. Detecting approval
        first avoids executing neighbouring calls and then pausing partway through a
        logical plan, which would make user intent and rollback boundaries ambiguous.
        """
        for call in calls:
            tool = self._tools.get(call.name)
            if tool is not None and tool.enabled and tool.risk == "approval":
                return call
        return None

    async def execute(
        self,
        call: ToolCall,
        identity: RequestIdentity,
        run_id: str,
        iteration: int,
        *,
        attempt: int = 1,
        approval_granted: bool = False,
    ) -> ToolExecutionResult:
        tool = self._tools.get(call.name)
        if tool is None or not tool.enabled:
            return ToolExecutionResult(call_id=call.id, tool_name=call.name, success=False, error_code="AI-TOOL-NOT-AVAILABLE", error_message="该工具当前不可用")
        errors = sorted(Draft202012Validator(tool.parameters).iter_errors(call.arguments), key=lambda item: item.path)
        if errors:
            return ToolExecutionResult(call_id=call.id, tool_name=call.name, success=False, error_code="AI-TOOL-INVALID-ARGUMENTS", error_message=f"工具参数无效：{errors[0].message}")
        if tool.risk == "approval" and not approval_granted:
            return ToolExecutionResult(call_id=call.id, tool_name=call.name, success=False, error_code="AI-APPROVAL-REQUIRED", error_message="该操作需要用户确认")
        capability_key = call.arguments["capability_key"] if tool.dynamic_plugin_capability else tool.capability_key
        if tool.composite == "file.compare":
            return await self._compare_files(call, identity, run_id, iteration, attempt)
        if not capability_key:
            return ToolExecutionResult(call_id=call.id, tool_name=call.name, success=False, error_code="AI-TOOL-NOT-CONFIGURED", error_message="工具尚未配置能力映射")
        input_data = call.arguments.get("input", {}) if tool.dynamic_plugin_capability else call.arguments
        result = await self._hub.invoke(
            capability_key=capability_key,
            identity=identity,
            run_id=run_id,
            step_id=f"ai:{iteration}:{call.id}"[:128],
            attempt=attempt,
            input_data=input_data,
            permissions=tool.permissions,
        )
        return result.model_copy(update={"call_id": call.id, "tool_name": call.name, "output": redact(result.output)})

    async def _compare_files(
        self,
        call: ToolCall,
        identity: RequestIdentity,
        run_id: str,
        iteration: int,
        attempt: int,
    ) -> ToolExecutionResult:
        """Compose two allowlisted content reads without creating a new data-plane path."""
        results = await asyncio.gather(
            self._hub.invoke(
                capability_key="api:file.content.get", identity=identity, run_id=run_id,
                step_id=f"ai:{iteration}:{call.id}:left"[:128], attempt=attempt,
                input_data={"file_id": call.arguments["left_file_id"], "max_bytes": call.arguments.get("max_bytes", 262144), "text_only": True},
                permissions=("file.read",),
            ),
            self._hub.invoke(
                capability_key="api:file.content.get", identity=identity, run_id=run_id,
                step_id=f"ai:{iteration}:{call.id}:right"[:128], attempt=attempt,
                input_data={"file_id": call.arguments["right_file_id"], "max_bytes": call.arguments.get("max_bytes", 262144), "text_only": True},
                permissions=("file.read",),
            ),
        )
        if not all(item.success for item in results):
            failed = next(item for item in results if not item.success)
            return ToolExecutionResult(
                call_id=call.id, tool_name=call.name, success=False,
                error_code=failed.error_code or "AI-CAPABILITY-FAILED",
                error_message=failed.error_message or "文件比较所需的内容读取失败",
                retryable=failed.retryable,
                duration_ms=sum(item.duration_ms for item in results),
            )
        return ToolExecutionResult(
            call_id=call.id,
            tool_name=call.name,
            success=True,
            output={"left": redact(results[0].output), "right": redact(results[1].output)},
            duration_ms=sum(item.duration_ms for item in results),
        )
