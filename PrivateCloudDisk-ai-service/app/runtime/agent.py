"""Autonomous but bounded plan-act-observe Agent Runtime."""

from __future__ import annotations

import asyncio
import json
import re
import secrets
import time
from collections import defaultdict
from collections.abc import AsyncIterator
from contextlib import suppress
from datetime import datetime, timezone
from typing import Any

from app.core.config import Settings
from app.core.identity import RequestIdentity
from app.core.redaction import clamp_text, redact
from app.domain.models import AgentRun, AgentTaskSnapshot, ChatMessage, MessageRole, PlanItem, RunStatus, StartRunRequest, StreamEvent, ToolCall
from app.memory.repository import ConversationRepository
from app.observability import AGENT_RUNS, TOOL_CALLS
from app.providers.openai_compatible import ProviderUnavailableError
from app.providers.router import ProviderRouter
from app.runtime.prompt import build_system_prompt
from app.tools.registry import ToolRegistry


class AgentRuntime:
    """A bounded, auditable tool-calling loop.

    The loop presents plans and verifiable tool transitions, not opaque model reasoning.
    It never reaches enterprise assets itself: ToolRegistry is its only action surface.
    """

    def __init__(
        self,
        settings: Settings,
        repository: ConversationRepository,
        providers: ProviderRouter,
        tools: ToolRegistry,
    ) -> None:
        self._settings = settings
        self._repository = repository
        self._providers = providers
        self._tools = tools

    async def run(
        self,
        *,
        run: AgentRun,
        identity: RequestIdentity,
        request: StartRunRequest,
    ) -> AsyncIterator[StreamEvent]:
        deadline = time.monotonic() + self._settings.max_run_seconds

        async def emit(event: str, data: dict[str, Any]) -> StreamEvent:
            run.sequence += 1
            stream_event = StreamEvent(event=event, run_id=run.id, sequence=run.sequence, data=redact(data))  # type: ignore[arg-type]
            # [AI-AGENT-TASK-003] The snapshot is projected from the exact structured
            # event that reaches the browser.  It never tries to reconstruct steps
            # from model prose, so refresh/reconnect cannot repeat an action.
            await self._repository.apply_task_event(stream_event)
            return stream_event

        async def cancelled() -> bool:
            return await self._repository.is_cancelled(run.id)

        task = AgentTaskSnapshot(
            task_id=run.id,
            conversation_id=run.conversation_id,
            user_id=identity.user_id,
            space_id=identity.space_id,
            user_request=clamp_text(redact(request.message), self._settings.max_message_chars),
            model=request.model or run.model,
        )
        await self._repository.save_task_snapshot(task)
        await self._repository.save_run(run)
        await self._repository.append_message(
            identity.user_id,
            identity.space_id,
            run.conversation_id,
            ChatMessage(role=MessageRole.USER, content=request.message),
        )
        run.status = RunStatus.PLANNING
        await self._repository.update_run(run)
        thinking_id = f"thinking-plan-{run.id}"
        context_id = f"context-initial-{run.id}"
        plan_id = f"plan-{run.id}"
        output_id = f"output-{run.id}"
        yield await emit("agent_task_start", {
            "task_id": run.id,
            # Let the browser create a complete in-memory task immediately.  The
            # snapshot remains the source of truth after a reload/reconnect.
            "conversation_id": run.conversation_id,
            "user_request": task.user_request,
            "model": task.model,
            "schema_version": task.schema_version,
            "timestamp": task.started_at,
        })
        yield await emit("thinking_start", {"block_id": thinking_id, "timestamp": datetime.now(timezone.utc)})
        yield await emit("thinking_delta", {
            "block_id": thinking_id,
            "delta": "正在根据任务目标、当前会话和已授权工具准备可见执行计划。",
        })
        yield await emit("context_start", {
            "block_id": context_id,
            "context_summary": "正在收集本次任务可用的受限上下文。",
        })
        history = await self._repository.list_messages(identity.user_id, identity.space_id, run.conversation_id, limit=80)
        yield await emit("context_item", {
            "block_id": context_id,
            "item": {"type": "conversation", "summary": f"已加载 {max(len(history) - 1, 0)} 条同一会话的历史消息。"},
        })
        for attachment in request.attachments:
            yield await emit("context_item", {
                "block_id": context_id,
                "item": {"type": "attachment_reference", "summary": f"已引用附件：{attachment.get('name') or attachment.get('file_id', '未命名文件')}"},
            })
        yield await emit("context_end", {"block_id": context_id, "timestamp": datetime.now(timezone.utc)})
        messages: list[dict[str, Any]] = [{"role": "system", "content": build_system_prompt(identity)}]
        messages.extend(self._to_provider_message(item) for item in history)
        messages = self._fit_context(messages)
        selected_model = request.model or run.model
        provider = self._providers.select(selected_model)
        plan_items, plan_source = await self._create_execution_plan(
            provider=provider,
            user_request=request.message,
            history=history,
            cancellation_check=cancelled,
        )
        yield await emit("plan_created", {
            "block_id": plan_id,
            "plan_items": [item.model_dump(mode="json") for item in plan_items],
            "source": plan_source,
        })
        yield await emit("thinking_delta", {
            "block_id": thinking_id,
            "delta": "计划已生成；后续结论只会基于已验证的工具结果。",
        })
        yield await emit("thinking_end", {"block_id": thinking_id, "timestamp": datetime.now(timezone.utc)})
        final_text = ""

        try:
            for iteration in range(1, self._settings.max_agent_iterations + 1):
                if time.monotonic() > deadline:
                    await self._finish(run, RunStatus.TIMED_OUT, "AI-RUN-TIMEOUT", "任务执行超时")
                    yield await emit("task_failed", {"code": "AI-RUN-TIMEOUT", "message": "任务执行超时，请缩小任务范围后重试。"})
                    return
                if await cancelled():
                    await self._finish(run, RunStatus.CANCELLED)
                    yield await emit("task_cancelled", {"message": "已停止生成"})
                    return

                run.iterations = iteration
                run.status = RunStatus.GENERATING
                await self._repository.update_run(run)
                active_plan = plan_items[min(iteration - 1, len(plan_items) - 1)]
                yield await emit("plan_item_update", {
                    "block_id": plan_id, "plan_item_id": active_plan.id, "status": "running",
                })
                if iteration > 1:
                    reflection_id = f"thinking-observe-{run.id}-{iteration}"
                    yield await emit("thinking_start", {"block_id": reflection_id})
                    yield await emit("thinking_delta", {
                        "block_id": reflection_id,
                        "delta": "正在根据上一轮已经验证的结果重新评估下一步，并在必要时调整工具选择。",
                    })
                    yield await emit("thinking_end", {"block_id": reflection_id})

                content_parts: list[str] = []
                raw_tool_calls: dict[int, dict[str, str]] = defaultdict(lambda: {"id": "", "name": "", "arguments": ""})
                stream_complete = False
                providers_to_try = (provider.model, *self._providers.fallback_models(provider.model))
                last_provider_error: ProviderUnavailableError | None = None
                for model_name in providers_to_try:
                    active_provider = provider if model_name == provider.model else self._providers.select(model_name)
                    try:
                        async for delta in active_provider.stream_chat(
                            messages=messages,
                            tools=self._tools.definitions_for_model() if request.mode == "agent" else [],
                            cancellation_check=cancelled,
                        ):
                            if delta.content:
                                content_parts.append(delta.content)
                                # [AI-AGENT-TASK-003] The task document consumes a
                                # structured output block in real time.  Do not make
                                # the browser infer execution/progress from a legacy
                                # token chat bubble and then duplicate it at the end.
                                yield await emit("output", {
                                    "block_id": output_id,
                                    "output_text": delta.content,
                                    "format": "markdown",
                                    "delta": True,
                                })
                            for partial in delta.tool_call_deltas:
                                index = int(partial.get("index", 0))
                                target = raw_tool_calls[index]
                                target["id"] = partial.get("id") or target["id"]
                                target["name"] = partial.get("name") or target["name"]
                                target["arguments"] += partial.get("arguments") or ""
                        stream_complete = True
                        if model_name != provider.model:
                            yield await emit("output", {
                                "block_id": output_id, "output_text": f"已切换到可用模型 {model_name} 继续任务。\n", "format": "text", "delta": True,
                            })
                        break
                    except ProviderUnavailableError as error:
                        last_provider_error = error
                        continue
                if not stream_complete:
                    await self._finish(run, RunStatus.FAILED, "AI-PROVIDER-UNAVAILABLE", "模型服务暂时不可用")
                    yield await emit("task_failed", {"code": "AI-PROVIDER-UNAVAILABLE", "message": str(last_provider_error or "模型服务暂时不可用")})
                    return

                calls = self._decode_tool_calls(raw_tool_calls)
                assistant_content = "".join(content_parts)
                if not calls:
                    final_text += assistant_content
                    await self._repository.append_message(
                        identity.user_id, identity.space_id, run.conversation_id,
                        ChatMessage(role=MessageRole.ASSISTANT, content=final_text, run_id=run.id),
                    )
                    yield await emit("plan_item_update", {
                        "block_id": plan_id, "plan_item_id": active_plan.id, "status": "completed",
                    })
                    await self._finish(run, RunStatus.COMPLETED)
                    yield await emit("summary", {"block_id": f"summary-{run.id}", "summary_text": final_text, "format": "markdown"})
                    yield await emit("task_completed", {"iterations": run.iterations, "tool_calls": run.tool_calls})
                    return

                messages.append({
                    "role": "assistant",
                    "content": assistant_content or None,
                    "tool_calls": [
                        {"id": call.id, "type": "function", "function": {"name": call.name, "arguments": json.dumps(call.arguments, ensure_ascii=False)}}
                        for call in calls
                    ],
                })
                run.status = RunStatus.CALLING_TOOL
                run.tool_calls += len(calls)
                await self._repository.update_run(run)
                for call in calls:
                    yield await emit("tool_call_start", {
                        "block_id": f"tool-{call.id}", "call_id": call.id, "tool_name": call.name,
                        "command": self._tool_command_description(call), "input": call.arguments,
                    })

                # Check the reviewed tool policy before launching any call in this model
                # batch. This prevents a mixed batch from partially changing state before
                # the user has confirmed its high-risk operation.
                requires_approval = self._tools.approval_required_call(calls)
                if requires_approval is not None:
                    approval_token = secrets.token_urlsafe(32)
                    await self._repository.save_pending_approval(run.id, {
                        "approval_token": approval_token,
                        "tool_name": requires_approval.name,
                        "call_id": requires_approval.id,
                        "calls": [call.model_dump() for call in calls],
                        "messages": messages,
                        "model": selected_model,
                        "next_iteration": iteration + 1,
                    })
                    run.status = RunStatus.AWAITING_APPROVAL
                    await self._repository.update_run(run)
                    yield await emit("tool_call_end", {
                        "block_id": f"tool-{requires_approval.id}", "call_id": requires_approval.id,
                        "status": "awaiting_approval", "output_data": {}, "duration_ms": 0,
                        "approval_token": approval_token,
                        "message": "该操作可能改变企业数据，需由你确认后继续。",
                    })
                    return

                results, retries = await self._execute_calls(calls, identity, run.id, iteration)
                for retry in retries:
                    yield await emit("output", {
                        "block_id": output_id,
                        "output_text": f"{retry['name']} 发生可重试错误，正在进行第 {retry['next_attempt']} 次尝试。\n",
                        "format": "text", "delta": True,
                    })
                run.status = RunStatus.OBSERVING
                await self._repository.update_run(run)
                observation_context_id = f"context-observed-{run.id}-{iteration}"
                yield await emit("context_start", {
                    "block_id": observation_context_id,
                    "context_summary": "正在将本轮已验证的工具结果加入任务上下文。",
                })
                for result in results:
                    model_result = {"success": result.success, "output": result.output, "error_code": result.error_code, "error_message": result.error_message}
                    messages.append({"role": "tool", "tool_call_id": result.call_id, "content": json.dumps(redact(model_result), ensure_ascii=False)})
                    if result.success:
                        yield await emit("tool_call_end", {
                            "block_id": f"tool-{result.call_id}", "call_id": result.call_id,
                            "status": "completed", "output_data": result.output, "duration_ms": result.duration_ms,
                        })
                        yield await emit("context_item", {
                            "block_id": observation_context_id,
                            "item": {"type": "tool_result", "tool_name": result.tool_name, "summary": f"已获得 {result.tool_name} 的受限结果。"},
                        })
                    else:
                        yield await emit("tool_call_error", {
                            "block_id": f"tool-{result.call_id}", "call_id": result.call_id,
                            "message": result.error_message or "能力调用失败", "code": result.error_code, "duration_ms": result.duration_ms,
                        })
                yield await emit("context_end", {"block_id": observation_context_id})
                yield await emit("plan_item_update", {
                    "block_id": plan_id, "plan_item_id": active_plan.id,
                    "status": "completed" if all(result.success for result in results) else "failed",
                })
            await self._finish(run, RunStatus.FAILED, "AI-AGENT-MAX-ITERATIONS", "已达到最大工具调用轮次")
            yield await emit("task_failed", {"code": "AI-AGENT-MAX-ITERATIONS", "message": "任务步骤过多，已安全停止。请缩小范围后重试。"})
        except asyncio.CancelledError:
            with suppress(Exception):
                await self._finish(run, RunStatus.CANCELLED)
            raise
        except Exception:
            await self._finish(run, RunStatus.FAILED, "AI-RUN-FAILED", "Agent 运行失败")
            yield await emit("task_failed", {"code": "AI-RUN-FAILED", "message": "智能体运行失败，请稍后重试。"})

    async def resume(self, *, run: AgentRun, identity: RequestIdentity) -> AsyncIterator[StreamEvent]:
        """Resume exactly the server-stored tool batch after a user decision.

        [AI-AGENT-APPROVAL-001] The browser only submits an approval token. Tool
        arguments, prior LLM messages and selected model are recovered from Redis and
        bound to the authenticated run, so an approval endpoint cannot be abused as a
        generic privileged tool executor.
        """

        async def emit(event: str, data: dict[str, Any]) -> StreamEvent:
            run.sequence += 1
            stream_event = StreamEvent(event=event, run_id=run.id, sequence=run.sequence, data=redact(data))  # type: ignore[arg-type]
            await self._repository.apply_task_event(stream_event)
            return stream_event

        decision = await self._repository.take_approval_result(run.id)
        if decision is None or run.status != RunStatus.AWAITING_APPROVAL:
            yield await emit("task_failed", {"code": "AI-APPROVAL-NOT-READY", "message": "没有可恢复的审批任务。"})
            return
        if not bool(decision.get("approved")):
            await self._repository.append_message(
                identity.user_id, identity.space_id, run.conversation_id,
                ChatMessage(role=MessageRole.ASSISTANT, content="已按你的拒绝停止该高风险操作，未执行任何待审批工具调用。", run_id=run.id),
            )
            await self._finish(run, RunStatus.CANCELLED)
            yield await emit("summary", {"block_id": f"summary-{run.id}", "summary_text": "已拒绝并停止执行；未执行任何待审批操作。", "format": "markdown"})
            yield await emit("task_cancelled", {"message": "已拒绝并停止执行。"})
            return

        try:
            messages = decision.get("messages")
            raw_calls = decision.get("calls")
            if not isinstance(messages, list) or not isinstance(raw_calls, list):
                raise ValueError("审批上下文格式无效")
            calls = [ToolCall.model_validate(item) for item in raw_calls]
            approved_call_id = str(decision.get("call_id", ""))
            if not calls or not approved_call_id:
                raise ValueError("审批工具调用不存在")
            iteration = max(int(decision.get("next_iteration", run.iterations + 1)) - 1, 1)
            run.status = RunStatus.CALLING_TOOL
            await self._repository.update_run(run)
            yield await emit("thinking_start", {"block_id": f"thinking-resume-{run.id}"})
            yield await emit("thinking_delta", {
                "block_id": f"thinking-resume-{run.id}",
                "delta": "已收到你的确认，正在从服务端绑定的待审批操作安全恢复任务。",
            })
            yield await emit("thinking_end", {"block_id": f"thinking-resume-{run.id}"})
            for call in calls:
                yield await emit("tool_call_start", {
                    "block_id": f"tool-{call.id}", "call_id": call.id, "tool_name": call.name,
                    "command": self._tool_command_description(call), "input": call.arguments,
                })

            results, retries = await self._execute_calls(
                calls, identity, run.id, iteration, approval_granted_call_ids={approved_call_id}
            )
            run.status = RunStatus.OBSERVING
            await self._repository.update_run(run)
            for retry in retries:
                yield await emit("output", {
                    "block_id": f"output-{run.id}",
                    "output_text": f"{retry['name']} 发生可重试错误，正在进行第 {retry['next_attempt']} 次尝试。\n",
                    "format": "text", "delta": True,
                })
            for result in results:
                model_result = {
                    "success": result.success,
                    "output": result.output,
                    "error_code": result.error_code,
                    "error_message": result.error_message,
                }
                messages.append({"role": "tool", "tool_call_id": result.call_id, "content": json.dumps(redact(model_result), ensure_ascii=False)})
                if result.success:
                    yield await emit("tool_call_end", {
                        "block_id": f"tool-{result.call_id}", "call_id": result.call_id,
                        "status": "completed", "output_data": result.output, "duration_ms": result.duration_ms,
                    })
                else:
                    yield await emit("tool_call_error", {
                        "block_id": f"tool-{result.call_id}", "call_id": result.call_id,
                        "message": result.error_message or "能力调用失败", "code": result.error_code, "duration_ms": result.duration_ms,
                    })

            # Synthesize the observed, approved result. Tools are intentionally omitted
            # from this single post-approval turn: a new side effect must enter the normal
            # planning loop and obtain its own explicit approval instead of piggybacking on
            # an earlier decision.
            run.status = RunStatus.GENERATING
            await self._repository.update_run(run)
            content_parts: list[str] = []
            selected_model = str(decision.get("model") or run.model)
            provider = self._providers.select(selected_model)
            stream_complete = False
            last_error: ProviderUnavailableError | None = None
            for model_name in (provider.model, *self._providers.fallback_models(provider.model)):
                active_provider = provider if model_name == provider.model else self._providers.select(model_name)
                try:
                    async for delta in active_provider.stream_chat(
                        messages=messages,
                        tools=[],
                        cancellation_check=lambda: self._repository.is_cancelled(run.id),
                    ):
                        if delta.content:
                            content_parts.append(delta.content)
                            yield await emit("output", {
                                "block_id": f"output-{run.id}", "output_text": delta.content,
                                "format": "markdown", "delta": True,
                            })
                    stream_complete = True
                    break
                except ProviderUnavailableError as error:
                    last_error = error
            if not stream_complete:
                raise last_error or ProviderUnavailableError("模型服务暂时不可用")
            final_text = "".join(content_parts).strip() or "已完成你确认的操作；详细结果已显示在工具调用记录中。"
            await self._repository.append_message(
                identity.user_id, identity.space_id, run.conversation_id,
                ChatMessage(role=MessageRole.ASSISTANT, content=final_text, run_id=run.id),
            )
            await self._finish(run, RunStatus.COMPLETED)
            yield await emit("summary", {"block_id": f"summary-{run.id}", "summary_text": final_text, "format": "markdown"})
            yield await emit("task_completed", {"iterations": run.iterations, "tool_calls": run.tool_calls})
        except asyncio.CancelledError:
            with suppress(Exception):
                await self._finish(run, RunStatus.CANCELLED)
            raise
        except Exception:
            await self._finish(run, RunStatus.FAILED, "AI-APPROVAL-RESUME-FAILED", "审批后的任务恢复失败")
            yield await emit("task_failed", {"code": "AI-APPROVAL-RESUME-FAILED", "message": "审批后的任务恢复失败，请重新发起任务。"})

    async def _create_execution_plan(
        self,
        *,
        provider: Any,
        user_request: str,
        history: list[ChatMessage],
        cancellation_check: Any,
    ) -> tuple[list[PlanItem], str]:
        """Ask the selected model for a concise *display plan*, never private CoT.

        The planner has no tools and receives no enterprise data beyond the user's
        request plus a count of the current conversation.  Its JSON is validated and
        cannot become an executable action; ToolRegistry remains the only action
        surface.  A malformed model reply falls back to a request-derived one-step
        plan rather than a previous fixed UI sentence.
        """
        planner_messages = [
            {
                "role": "system",
                "content": (
                    "你负责生成给用户看的企业任务执行计划，不输出隐藏推理、内部策略或任何工具结果。"
                    "只返回 JSON 数组，包含 2 到 6 个对象，每个对象为"
                    '{"id":"step-1","title":"不超过40字的动作","details":"可选、120字以内"}。'
                    "计划必须针对用户当前请求动态生成；不要使用 Markdown，不要执行工具。"
                ),
            },
            {
                "role": "user",
                "content": f"当前请求：{clamp_text(user_request, 4_000)}\n可用同一会话历史条数：{max(len(history) - 1, 0)}。",
            },
        ]
        pieces: list[str] = []
        try:
            async for delta in provider.stream_chat(
                messages=planner_messages,
                tools=[],
                cancellation_check=cancellation_check,
            ):
                if delta.content:
                    pieces.append(delta.content)
        except ProviderUnavailableError:
            return self._request_derived_plan(user_request), "request_fallback"
        plan = self._parse_plan_json("".join(pieces))
        if plan:
            return plan, "llm"
        return self._request_derived_plan(user_request), "request_fallback"

    @staticmethod
    def _parse_plan_json(raw: str) -> list[PlanItem]:
        candidate = raw.strip()
        fenced = re.search(r"```(?:json)?\s*([\s\S]*?)```", candidate, re.IGNORECASE)
        if fenced:
            candidate = fenced.group(1).strip()
        start = candidate.find("[")
        end = candidate.rfind("]")
        if start < 0 or end <= start:
            return []
        try:
            decoded = json.loads(candidate[start:end + 1])
        except json.JSONDecodeError:
            return []
        if not isinstance(decoded, list):
            return []
        plan: list[PlanItem] = []
        for index, item in enumerate(decoded[:6], start=1):
            if not isinstance(item, dict) or not str(item.get("title", "")).strip():
                continue
            try:
                plan.append(PlanItem(
                    id=str(item.get("id") or f"step-{index}")[:80],
                    title=clamp_text(str(item["title"]).strip(), 240),
                    details=clamp_text(str(item["details"]).strip(), 1_000) if item.get("details") else None,
                ))
            except (TypeError, ValueError):
                continue
        return plan

    @staticmethod
    def _request_derived_plan(user_request: str) -> list[PlanItem]:
        # The fallback intentionally incorporates the current request.  It is not a
        # hidden static plan and is marked as request_fallback in the event payload so
        # clients and operators can distinguish it from LLM-generated planning.
        return [PlanItem(id="request-goal", title=clamp_text(user_request.strip(), 240), details="根据已授权工具逐步核实并完成当前请求。")]

    @staticmethod
    def _tool_command_description(call: ToolCall) -> str:
        """A compact, redacted command description for the execution document."""
        argument_keys = ", ".join(sorted(str(key) for key in call.arguments)[:6])
        return f"调用 {call.name}" + (f"（参数：{argument_keys}）" if argument_keys else "")

    async def _execute_calls(
        self,
        calls: list[ToolCall],
        identity: RequestIdentity,
        run_id: str,
        iteration: int,
        approval_granted_call_ids: set[str] | None = None,
    ) -> tuple[list[Any], list[dict[str, Any]]]:
        semaphore = asyncio.Semaphore(self._settings.max_tool_concurrency)
        approved = approval_granted_call_ids or set()

        async def execute(call: ToolCall):
            async with semaphore:
                retries: list[dict[str, Any]] = []
                result = None
                for attempt in range(1, self._settings.max_tool_attempts + 1):
                    result = await self._tools.execute(
                        call, identity, run_id, iteration,
                        approval_granted=call.id in approved,
                        attempt=attempt,
                    )
                    if result.success or not result.retryable or attempt >= self._settings.max_tool_attempts:
                        break
                    retries.append({
                        "id": call.id,
                        "name": call.name,
                        "attempt": attempt,
                        "next_attempt": attempt + 1,
                        "error_code": result.error_code,
                    })
                assert result is not None
                return result, retries

        executed = await asyncio.gather(*(execute(call) for call in calls))
        results = [item[0] for item in executed]
        retries = [retry for _, items in executed for retry in items]
        for result in results:
            TOOL_CALLS.labels(tool=result.tool_name, result="success" if result.success else "failed").inc()
        return results, retries

    def _fit_context(self, messages: list[dict[str, Any]]) -> list[dict[str, Any]]:
        """Keep the system policy and newest context within a bounded request size.

        [AI-AGENT-CONTEXT-001] Redis retains the full conversation for recovery. The
        provider request is bounded independently so a long-running conversation cannot
        consume the model context indefinitely. Older turns are represented by a neutral
        marker; enterprise facts still have to be re-read through tools when needed.
        """
        total = sum(len(json.dumps(item, ensure_ascii=False, default=str)) for item in messages)
        if total <= self._settings.max_context_chars:
            return messages
        system = messages[:1]
        newest: list[dict[str, Any]] = []
        used = len(json.dumps(system[0], ensure_ascii=False, default=str))
        for message in reversed(messages[1:]):
            size = len(json.dumps(message, ensure_ascii=False, default=str))
            if used + size > self._settings.max_context_chars - 256:
                break
            newest.insert(0, message)
            used += size
        return system + [{"role": "system", "content": "较早的会话内容已压缩；如需企业事实，请重新调用授权工具。"}] + newest

    @staticmethod
    def _decode_tool_calls(raw_calls: dict[int, dict[str, str]]) -> list[ToolCall]:
        calls: list[ToolCall] = []
        for index in sorted(raw_calls):
            raw = raw_calls[index]
            if not raw["id"] or not raw["name"]:
                continue
            try:
                arguments = json.loads(raw["arguments"] or "{}")
            except json.JSONDecodeError:
                arguments = {"_invalid_json": raw["arguments"]}
            calls.append(ToolCall(id=raw["id"], name=raw["name"], arguments=arguments))
        return calls

    @staticmethod
    def _to_provider_message(message: ChatMessage) -> dict[str, Any]:
        return {"role": message.role.value, "content": message.content}

    async def _finish(self, run: AgentRun, status: RunStatus, error_code: str | None = None, error_message: str | None = None) -> None:
        run.status = status
        run.error_code = error_code
        run.error_message = error_message
        run.finished_at = datetime.now(timezone.utc)
        await self._repository.update_run(run)
        AGENT_RUNS.labels(status=status.value).inc()
