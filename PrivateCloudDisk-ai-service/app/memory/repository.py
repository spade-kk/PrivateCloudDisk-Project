"""Redis-only persistence for AI conversations and runtime state."""

from __future__ import annotations

import json
from collections.abc import Sequence
from datetime import datetime, timezone
from typing import Any

from redis.asyncio import Redis

from app.domain.models import AgentRun, AgentTaskBlock, AgentTaskSnapshot, ChatMessage, Conversation, PlanItem, RunStatus, StreamEvent


class ConversationRepository:
    """Tenant-scoped storage; every read checks authenticated user and space scope.

    This repository deliberately uses no SQL ORM or local cache as a source of truth,
    enabling horizontal service scaling without creating an asset-data side channel.
    """

    def __init__(self, redis: Redis) -> None:
        self._redis = redis

    @staticmethod
    def _scope(user_id: str, space_id: str | None) -> str:
        return f"{user_id}:{space_id or '_personal'}"

    @classmethod
    def _conversation_key(cls, user_id: str, space_id: str | None, conversation_id: str) -> str:
        return f"ai:conversation:{cls._scope(user_id, space_id)}:{conversation_id}"

    @classmethod
    def _messages_key(cls, user_id: str, space_id: str | None, conversation_id: str) -> str:
        return f"ai:messages:{cls._scope(user_id, space_id)}:{conversation_id}"

    @classmethod
    def _index_key(cls, user_id: str, space_id: str | None) -> str:
        return f"ai:conversations:{cls._scope(user_id, space_id)}"

    @staticmethod
    def _run_key(run_id: str) -> str:
        return f"ai:run:{run_id}"

    @staticmethod
    def _task_key(run_id: str) -> str:
        # [AI-AGENT-TASK-002] Task snapshots share the run retention window but are
        # isolated from conversation messages: a recovery request never has to replay
        # a mutable provider/tool stream or infer process state from assistant prose.
        return f"ai:task:{run_id}"

    async def create_conversation(self, conversation: Conversation) -> Conversation:
        key = self._conversation_key(conversation.user_id, conversation.space_id, conversation.id)
        await self._redis.set(key, conversation.model_dump_json(), ex=60 * 60 * 24 * 90)
        await self._redis.zadd(
            self._index_key(conversation.user_id, conversation.space_id),
            {conversation.id: conversation.updated_at.timestamp()},
        )
        return conversation

    async def list_conversations(self, user_id: str, space_id: str | None, limit: int = 50) -> list[Conversation]:
        ids = await self._redis.zrevrange(self._index_key(user_id, space_id), 0, max(limit - 1, 0))
        conversations: list[Conversation] = []
        for raw_id in ids:
            conversation_id = raw_id.decode() if isinstance(raw_id, bytes) else raw_id
            item = await self.get_conversation(user_id, space_id, conversation_id)
            if item is not None and not item.archived:
                conversations.append(item)
        return conversations

    async def get_conversation(self, user_id: str, space_id: str | None, conversation_id: str) -> Conversation | None:
        raw = await self._redis.get(self._conversation_key(user_id, space_id, conversation_id))
        if raw is None:
            return None
        return Conversation.model_validate_json(raw)

    async def update_conversation(self, conversation: Conversation) -> Conversation:
        conversation.updated_at = datetime.now(timezone.utc)
        await self.create_conversation(conversation)
        return conversation

    async def delete_conversation(self, user_id: str, space_id: str | None, conversation_id: str) -> bool:
        deleted = await self._redis.delete(
            self._conversation_key(user_id, space_id, conversation_id),
            self._messages_key(user_id, space_id, conversation_id),
        )
        await self._redis.zrem(self._index_key(user_id, space_id), conversation_id)
        return bool(deleted)

    async def append_message(self, user_id: str, space_id: str | None, conversation_id: str, message: ChatMessage) -> None:
        key = self._messages_key(user_id, space_id, conversation_id)
        pipeline = self._redis.pipeline()
        pipeline.rpush(key, message.model_dump_json())
        pipeline.expire(key, 60 * 60 * 24 * 90)
        pipeline.zadd(self._index_key(user_id, space_id), {conversation_id: datetime.now(timezone.utc).timestamp()})
        await pipeline.execute()

    async def list_messages(self, user_id: str, space_id: str | None, conversation_id: str, offset: int = 0, limit: int = 100) -> list[ChatMessage]:
        values: Sequence[str | bytes] = await self._redis.lrange(
            self._messages_key(user_id, space_id, conversation_id), offset, offset + max(limit - 1, 0)
        )
        return [ChatMessage.model_validate_json(value) for value in values]

    async def save_run(self, run: AgentRun) -> None:
        await self._redis.set(self._run_key(run.id), run.model_dump_json(), ex=60 * 60 * 24 * 7)

    async def get_run(self, run_id: str) -> AgentRun | None:
        raw = await self._redis.get(self._run_key(run_id))
        return AgentRun.model_validate_json(raw) if raw else None

    async def update_run(self, run: AgentRun) -> None:
        await self.save_run(run)

    async def save_task_snapshot(self, snapshot: AgentTaskSnapshot) -> None:
        await self._redis.set(self._task_key(snapshot.task_id), snapshot.model_dump_json(), ex=60 * 60 * 24 * 7)

    async def get_task_snapshot(self, run_id: str) -> AgentTaskSnapshot | None:
        raw = await self._redis.get(self._task_key(run_id))
        return AgentTaskSnapshot.model_validate_json(raw) if raw else None

    async def apply_task_event(self, event: StreamEvent) -> None:
        """Project a structured V2 SSE event into the recoverable task document.

        The stream remains the live transport, while this snapshot is the source used
        after refresh/reconnect.  Tool output is bounded here only for presentation;
        it is never reconstructed from a provider response or a chat message.
        """
        snapshot = await self.get_task_snapshot(event.run_id)
        if snapshot is None:
            return
        data = event.data
        now = datetime.now(timezone.utc)

        def find(block_id: str) -> AgentTaskBlock | None:
            return next((item for item in snapshot.blocks if item.id == block_id), None)

        def add(
            block_id: str,
            block_type: str,
            *,
            status: str | None = None,
            payload: dict[str, Any] | None = None,
            parent_id: str | None = None,
        ) -> AgentTaskBlock:
            existing = find(block_id)
            if existing is not None:
                if status is not None:
                    existing.status = status
                if payload:
                    existing.data.update(payload)
                return existing
            block = AgentTaskBlock(
                id=block_id,
                type=block_type,  # type: ignore[arg-type]  # validated at event producer
                order=len(snapshot.blocks),
                parent_id=parent_id,
                status=status,
                data=payload or {},
                started_at=now,
            )
            snapshot.blocks.append(block)
            return block

        if event.event == "agent_task_start":
            snapshot.status = "running"
        elif event.event == "thinking_start":
            add(str(data["block_id"]), "thinking", status="running", payload={"thinking_text": ""})
        elif event.event == "thinking_delta":
            block = add(str(data["block_id"]), "thinking", status="running", payload={"thinking_text": ""})
            prior = str(block.data.get("thinking_text", ""))
            block.data["thinking_text"] = (prior + str(data.get("delta", "")))[:16_000]
        elif event.event == "thinking_end":
            block = find(str(data["block_id"]))
            if block:
                block.status = "completed"
                block.ended_at = now
        elif event.event == "context_start":
            add(str(data["block_id"]), "context", status="running", payload={"context_summary": data.get("context_summary", ""), "items": []})
        elif event.event == "context_item":
            block = add(str(data["block_id"]), "context", status="running", payload={"items": []})
            items = block.data.setdefault("items", [])
            if isinstance(items, list):
                items.append(self._task_safe_value(data.get("item", {}), 8_000))
        elif event.event == "context_end":
            block = find(str(data["block_id"]))
            if block:
                block.status = "completed"
                block.ended_at = now
        elif event.event == "plan_created":
            plan_items = [PlanItem.model_validate(item).model_dump(mode="json") for item in data.get("plan_items", [])]
            add(str(data["block_id"]), "plan", status="running", payload={"plan_items": plan_items, "source": data.get("source", "llm")})
        elif event.event == "plan_item_update":
            block = find(str(data["block_id"]))
            if block:
                for item in block.data.get("plan_items", []):
                    if isinstance(item, dict) and item.get("id") == data.get("plan_item_id"):
                        item["status"] = data.get("status", item.get("status"))
                        if data.get("details"):
                            item["details"] = data["details"]
                        break
        elif event.event == "tool_call_start":
            snapshot.status = "running"
            add(
                str(data["block_id"]), "tool_call", status="running", parent_id=data.get("parent_id"),
                payload={
                    "call_id": data.get("call_id"), "tool_name": data.get("tool_name"),
                    "command": data.get("command"), "input": self._task_safe_value(data.get("input", {}), 24_000),
                },
            )
        elif event.event == "tool_call_end":
            block = find(str(data["block_id"]))
            if block:
                block.status = str(data.get("status", "completed"))
                block.data["output_data"] = self._task_safe_value(data.get("output_data", {}), 64_000)
                block.data["duration_ms"] = data.get("duration_ms", 0)
                if data.get("approval_token"):
                    # The token is already a short-lived, run-bound browser contract
                    # and the snapshot is tenant-authorized on read.  Persisting it
                    # lets a refreshed owner approve without re-running the model.
                    block.data["approval_token"] = data["approval_token"]
                if data.get("message"):
                    block.data["message"] = data["message"]
                block.ended_at = now
                if block.status == "awaiting_approval":
                    snapshot.status = "paused"
        elif event.event == "tool_call_error":
            block = find(str(data["block_id"]))
            if block:
                block.status = "failed"
                block.data["error"] = data.get("message", "工具调用失败")
                block.data["duration_ms"] = data.get("duration_ms", 0)
                block.ended_at = now
        elif event.event == "output":
            block = add(str(data["block_id"]), "output", status="running", payload={"output_text": "", "format": data.get("format", "markdown")})
            if data.get("delta"):
                block.data["output_text"] = (str(block.data.get("output_text", "")) + str(data.get("output_text", "")))[:64_000]
            else:
                block.data["output_text"] = str(data.get("output_text", ""))[:64_000]
        elif event.event == "summary":
            add(str(data["block_id"]), "summary", status="completed", payload={"summary_text": str(data.get("summary_text", ""))[:64_000], "format": data.get("format", "markdown")}).ended_at = now
        elif event.event in {"task_completed", "task_failed", "task_cancelled"}:
            snapshot.status = {"task_completed": "completed", "task_failed": "failed", "task_cancelled": "cancelled"}[event.event]
            snapshot.ended_at = now
            snapshot.total_duration_ms = max(0, int((now - snapshot.started_at).total_seconds() * 1000))
            for block in snapshot.blocks:
                if block.type == "plan" and block.status == "running":
                    block.status = "completed" if snapshot.status == "completed" else "failed"
                    block.ended_at = now

        await self.save_task_snapshot(snapshot)

    @staticmethod
    def _task_safe_value(value: Any, maximum_chars: int) -> Any:
        """Bound a presentation value without turning typed JSON into an opaque string."""
        try:
            encoded = json.dumps(value, ensure_ascii=False, default=str)
        except (TypeError, ValueError):
            return {"truncated": True, "preview": "工具结果无法安全序列化"}
        if len(encoded) <= maximum_chars:
            return value
        return {"truncated": True, "preview": encoded[:maximum_chars] + "…[truncated]"}

    async def request_cancellation(self, run_id: str) -> None:
        await self._redis.set(f"ai:cancel:{run_id}", "1", ex=60 * 60)

    async def is_cancelled(self, run_id: str) -> bool:
        return bool(await self._redis.exists(f"ai:cancel:{run_id}"))

    async def save_pending_approval(self, run_id: str, payload: dict[str, Any]) -> None:
        await self._redis.set(f"ai:approval:{run_id}", json.dumps(payload), ex=60 * 15)

    async def consume_approval(self, run_id: str, approval_token: str, approved: bool) -> dict[str, Any] | None:
        key = f"ai:approval:{run_id}"
        raw = await self._redis.get(key)
        if not raw:
            return None
        payload = json.loads(raw)
        if payload.get("approval_token") != approval_token:
            return None
        payload["approved"] = approved
        await self._redis.set(f"ai:approval-result:{run_id}", json.dumps(payload), ex=60 * 15)
        await self._redis.delete(key)
        return payload

    async def take_approval_result(self, run_id: str) -> dict[str, Any] | None:
        """Atomically consume one approved/declined decision for a resumable run.

        [AI-AGENT-APPROVAL-001] A browser may confirm a high-risk action only once.
        The stored payload was created by AgentRuntime from the model-produced tool call;
        this method deliberately returns that server-side payload rather than accepting
        any browser-provided tool name or arguments during resume.
        """
        raw = await self._redis.getdel(f"ai:approval-result:{run_id}")
        return json.loads(raw) if raw else None
