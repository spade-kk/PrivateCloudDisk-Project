"""Domain contracts shared across HTTP, Agent Runtime and persistence adapters."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from enum import StrEnum
from typing import Any, Literal
from uuid import uuid4

from pydantic import BaseModel, ConfigDict, Field, field_validator


class MessageRole(StrEnum):
    SYSTEM = "system"
    USER = "user"
    ASSISTANT = "assistant"
    TOOL = "tool"


class RunStatus(StrEnum):
    CREATED = "CREATED"
    PLANNING = "PLANNING"
    GENERATING = "GENERATING"
    CALLING_TOOL = "CALLING_TOOL"
    OBSERVING = "OBSERVING"
    REFLECTING = "REFLECTING"
    AWAITING_APPROVAL = "AWAITING_APPROVAL"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"
    TIMED_OUT = "TIMED_OUT"


class ChatMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: str = Field(default_factory=lambda: str(uuid4()))
    role: MessageRole
    content: str = ""
    tool_calls: list[dict[str, Any]] = Field(default_factory=list)
    # [AI-AGENT-TASK-001] An assistant response may be rendered as a persisted
    # task document rather than a single chat bubble.  Keeping the optional run
    # reference on the ordinary message preserves the existing conversation API
    # while allowing a refreshed client to recover its task snapshot safely.
    run_id: str | None = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    usage: dict[str, int] | None = None


class Conversation(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid4()))
    user_id: str
    space_id: str | None = None
    title: str = "新对话"
    model: str | None = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    updated_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    archived: bool = False


class CreateConversationRequest(BaseModel):
    title: str | None = Field(default=None, max_length=160)
    model: str | None = Field(default=None, max_length=120)


class UpdateConversationRequest(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=160)
    archived: bool | None = None


class StartRunRequest(BaseModel):
    message: str = Field(min_length=1, max_length=32_000)
    model: str | None = Field(default=None, max_length=120)
    mode: Literal["agent", "api"] = "agent"
    stream: bool = True
    attachments: list[dict[str, str]] = Field(default_factory=list, max_length=20)

    @field_validator("attachments")
    @classmethod
    def only_reference_safe_attachment_fields(cls, value: list[dict[str, str]]) -> list[dict[str, str]]:
        for item in value:
            if set(item) - {"file_id", "name", "type"}:
                raise ValueError("附件仅支持 file_id、name、type 引用")
        return value


class ApprovalRequest(BaseModel):
    approved: bool
    approval_token: str = Field(min_length=16, max_length=512)


class AgentRun(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid4()))
    conversation_id: str
    user_id: str
    space_id: str | None = None
    status: RunStatus = RunStatus.CREATED
    model: str
    started_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    finished_at: datetime | None = None
    iterations: int = 0
    tool_calls: int = 0
    error_code: str | None = None
    error_message: str | None = None
    sequence: int = 0


TaskBlockType = Literal["thinking", "context", "plan", "tool_call", "output", "summary"]
TaskStatus = Literal["running", "paused", "completed", "failed", "cancelled"]


class PlanItem(BaseModel):
    """A model-generated, user-visible task-plan item.

    The model never provides an executable command through this structure.  It is a
    progress projection only; actual enterprise actions remain constrained by the
    ToolRegistry and Capability Hub.
    """

    id: str
    title: str = Field(min_length=1, max_length=240)
    details: str | None = Field(default=None, max_length=1_000)
    status: Literal["pending", "running", "completed", "failed", "superseded"] = "pending"


class AgentTaskBlock(BaseModel):
    """Versioned block used by the Codex-style task execution projection.

    ``data`` is intentionally typed as a JSON object rather than exposing provider
    internals.  Its stable shape is documented per ``type`` in AI_AGENT_API.md, and
    allows new presentation-only block kinds without breaking older clients.
    """

    id: str
    type: TaskBlockType
    order: int = Field(ge=0)
    parent_id: str | None = None
    status: str | None = None
    data: dict[str, Any] = Field(default_factory=dict)
    started_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    ended_at: datetime | None = None


class AgentTaskSnapshot(BaseModel):
    """Tenant-bound recoverable projection of one Agent run for the web task view."""

    schema_version: int = 2
    task_id: str
    conversation_id: str
    user_id: str
    space_id: str | None = None
    user_request: str = Field(max_length=32_000)
    model: str
    status: TaskStatus = "running"
    started_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    ended_at: datetime | None = None
    total_duration_ms: int | None = None
    blocks: list[AgentTaskBlock] = Field(default_factory=list)


class AgentTaskSnapshotResponse(BaseModel):
    """Public, tenant-authorized task projection returned to a browser.

    ``user_id`` remains inside the Redis snapshot solely for server-side ownership
    checks.  A task owner does not need that internal field to render a task document,
    so the recovery endpoint deliberately returns this narrower contract.
    """

    schema_version: int = 2
    task_id: str
    conversation_id: str
    space_id: str | None = None
    user_request: str
    model: str
    status: TaskStatus = "running"
    started_at: datetime
    ended_at: datetime | None = None
    total_duration_ms: int | None = None
    blocks: list[AgentTaskBlock] = Field(default_factory=list)


class StreamEvent(BaseModel):
    # V2 task events are additive.  The legacy names remain available to preserve
    # non-web consumers during migration; the Vue task view consumes only V2 events.
    event: Literal[
        "agent_task_start", "thinking_start", "thinking_delta", "thinking_end",
        "context_start", "context_item", "context_end", "plan_created",
        "plan_item_update", "tool_call_start", "tool_call_end", "tool_call_error",
        "output", "summary", "task_completed", "task_failed", "task_cancelled",
        "status", "plan", "token", "tool_call", "tool_result", "tool_retry",
        "reflection", "approval_required", "heartbeat", "error", "done",
    ]
    run_id: str
    sequence: int
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    data: dict[str, Any] = Field(default_factory=dict)

    def payload(self) -> dict[str, Any]:
        """Return the stable transport projection for SSE and non-stream callers.

        Keeping this in one place prevents the JSON aggregation endpoint from losing
        V2 `task_id`/timestamp while the SSE encoder retains them.  Server transport
        fields are written after event data, so a producer cannot accidentally replace
        the run-bound identity, order or timestamp with a payload field.
        """
        return {
            **self.data,
            "task_id": self.run_id,
            "run_id": self.run_id,
            "sequence": self.sequence,
            "timestamp": self.timestamp,
        }

    def encode(self) -> bytes:
        # SSE data must carry run/sequence itself: fetch-based clients only receive the
        # event payload, not the server-side Pydantic envelope.
        payload = json.dumps(
            self.payload(),
            ensure_ascii=False,
            default=str,
            separators=(",", ":"),
        )
        return f"id: {self.sequence}\nevent: {self.event}\ndata: {payload}\n\n".encode("utf-8")


class ToolCall(BaseModel):
    id: str
    name: str
    arguments: dict[str, Any]


class ToolExecutionResult(BaseModel):
    call_id: str
    tool_name: str
    success: bool
    output: dict[str, Any] = Field(default_factory=dict)
    error_code: str | None = None
    error_message: str | None = None
    retryable: bool = False
    duration_ms: int = 0
