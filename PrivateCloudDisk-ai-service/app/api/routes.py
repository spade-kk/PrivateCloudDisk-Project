"""Authenticated conversation, Agent-run, approval and health endpoints."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import JSONResponse, Response, StreamingResponse

from app.core.config import Settings, get_settings
from app.core.identity import RequestIdentity, require_identity
from app.core.limiter import RunRateLimiter
from app.domain.models import AgentRun, AgentTaskSnapshotResponse, ApprovalRequest, Conversation, CreateConversationRequest, RunStatus, StartRunRequest, StreamEvent, UpdateConversationRequest
from app.memory.repository import ConversationRepository
from app.runtime.agent import AgentRuntime

router = APIRouter()


def get_repository(request: Request) -> ConversationRepository:
    return request.app.state.repository


def get_runtime(request: Request) -> AgentRuntime:
    return request.app.state.agent_runtime


def get_run_limiter(request: Request) -> RunRateLimiter:
    return request.app.state.run_limiter


async def identity_dependency(request: Request, settings: Annotated[Settings, Depends(get_settings)]) -> RequestIdentity:
    return await require_identity(request, settings)


Identity = Annotated[RequestIdentity, Depends(identity_dependency)]
Repository = Annotated[ConversationRepository, Depends(get_repository)]
Runtime = Annotated[AgentRuntime, Depends(get_runtime)]
RunLimiter = Annotated[RunRateLimiter, Depends(get_run_limiter)]


async def encode_sse_events(
    events: AsyncIterator[StreamEvent],
    run: AgentRun,
    settings: Settings,
) -> AsyncIterator[bytes]:
    """Emit application heartbeats while a provider/tool awaits a response.

    [AI-AGENT-SSE-001] A provider may remain silent for tens of seconds before its
    first token. The wrapper keeps proxy/browser connections alive without creating a
    second Agent execution or replaying tools. Heartbeats use the same monotonic run
    sequence but are intentionally not persisted as business messages.
    """
    iterator = events.__aiter__()
    pending: asyncio.Task[StreamEvent] | None = None
    try:
        while True:
            pending = asyncio.create_task(anext(iterator))
            while not pending.done():
                done, _ = await asyncio.wait({pending}, timeout=settings.sse_heartbeat_seconds)
                if done:
                    break
                run.sequence += 1
                yield StreamEvent(
                    event="heartbeat", run_id=run.id, sequence=run.sequence,
                    data={"status": run.status},
                ).encode()
            try:
                event = pending.result()
            except StopAsyncIteration:
                return
            yield event.encode()
            pending = None
    finally:
        if pending is not None and not pending.done():
            pending.cancel()


@router.get("/health", include_in_schema=False)
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "cloud-ai-agent"}


@router.get("/ready", include_in_schema=False)
async def ready(request: Request) -> dict[str, str]:
    try:
        await request.app.state.redis.ping()
    except Exception as error:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="会话存储暂不可用") from error
    return {"status": "ready", "service": "cloud-ai-agent"}


@router.get("/api/v1/ai/models")
async def models(request: Request, _: Identity) -> dict[str, list[dict[str, str]]]:
    return {"items": [{"id": model, "provider": "openai-compatible"} for model in request.app.state.provider_router.allowed_models()]}


@router.get("/api/v1/ai/conversations")
async def list_conversations(identity: Identity, repository: Repository, limit: int = 50) -> dict[str, list[Conversation]]:
    return {"items": await repository.list_conversations(identity.user_id, identity.space_id, min(max(limit, 1), 100))}


@router.post("/api/v1/ai/conversations", status_code=status.HTTP_201_CREATED)
async def create_conversation(body: CreateConversationRequest, identity: Identity, repository: Repository) -> Conversation:
    conversation = Conversation(user_id=identity.user_id, space_id=identity.space_id, title=body.title or "新对话", model=body.model)
    return await repository.create_conversation(conversation)


async def owned_conversation(repository: ConversationRepository, identity: RequestIdentity, conversation_id: str) -> Conversation:
    conversation = await repository.get_conversation(identity.user_id, identity.space_id, conversation_id)
    if conversation is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="会话不存在或无权访问")
    return conversation


@router.get("/api/v1/ai/conversations/{conversation_id}")
async def get_conversation(conversation_id: str, identity: Identity, repository: Repository) -> Conversation:
    return await owned_conversation(repository, identity, conversation_id)


@router.patch("/api/v1/ai/conversations/{conversation_id}")
async def update_conversation(conversation_id: str, body: UpdateConversationRequest, identity: Identity, repository: Repository) -> Conversation:
    conversation = await owned_conversation(repository, identity, conversation_id)
    if body.title is not None:
        conversation.title = body.title
    if body.archived is not None:
        conversation.archived = body.archived
    return await repository.update_conversation(conversation)


@router.delete(
    "/api/v1/ai/conversations/{conversation_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    response_class=Response,
)
async def delete_conversation(conversation_id: str, identity: Identity, repository: Repository) -> Response:
    # [AI-AGENT-API-002] FastAPI 0.115 rejects a 204 endpoint with an inferred
    # response model/body. Previous `-> None` relied on older inference behavior;
    # returning an explicit empty Response preserves HTTP 204 and lets the router start.
    await owned_conversation(repository, identity, conversation_id)
    await repository.delete_conversation(identity.user_id, identity.space_id, conversation_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/api/v1/ai/conversations/{conversation_id}/messages")
async def messages(conversation_id: str, identity: Identity, repository: Repository, offset: int = 0, limit: int = 100) -> dict[str, object]:
    await owned_conversation(repository, identity, conversation_id)
    return {"items": await repository.list_messages(identity.user_id, identity.space_id, conversation_id, max(offset, 0), min(max(limit, 1), 200)), "offset": max(offset, 0)}


@router.post("/api/v1/ai/conversations/{conversation_id}/runs", response_model=None)
async def start_run(
    conversation_id: str,
    body: StartRunRequest,
    request: Request,
    identity: Identity,
    repository: Repository,
    runtime: Runtime,
    limiter: RunLimiter,
) -> Response:
    conversation = await owned_conversation(repository, identity, conversation_id)
    admitted, remaining = await limiter.allow(identity.user_id, identity.space_id)
    if not admitted:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="当前 AI 任务创建频率过高，请稍后重试。",
            headers={"Retry-After": "60", "X-RateLimit-Remaining": str(remaining)},
        )
    model = body.model or conversation.model or request.app.state.provider_router.allowed_models()[0]
    if model not in request.app.state.provider_router.allowed_models():
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="请求的模型未获授权")
    run = AgentRun(conversation_id=conversation.id, user_id=identity.user_id, space_id=identity.space_id, model=model)

    async def events() -> AsyncIterator[bytes]:
        async for payload in encode_sse_events(runtime.run(run=run, identity=identity, request=body), run, get_settings()):
            yield payload
        # Keeps intermediaries from holding an SSE response open after terminal output.
        yield b": stream complete\n\n"

    accepts_sse = "text/event-stream" in request.headers.get("accept", "") or body.stream
    if accepts_sse:
        return StreamingResponse(
            events(),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache, no-transform", "Connection": "keep-alive", "X-Accel-Buffering": "no"},
        )
    collected = []
    async for event in runtime.run(run=run, identity=identity, request=body):
        # Keep JSON aggregation semantically identical to the SSE `data` document:
        # browser/API consumers receive the same run-bound task identity and timestamp.
        collected.append({"event": event.event, **event.payload()})
    terminal = collected[-1] if collected else {"event": "error", "data": {"message": "未生成响应"}}
    return JSONResponse({"run_id": run.id, "events": collected, "result": terminal})


@router.get("/api/v1/ai/runs/{run_id}")
async def get_run(run_id: str, identity: Identity, repository: Repository) -> AgentRun:
    run = await repository.get_run(run_id)
    if run is None or run.user_id != identity.user_id or run.space_id != identity.space_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="运行不存在或无权访问")
    return run


@router.get("/api/v1/ai/runs/{run_id}/task")
async def get_task_snapshot(run_id: str, identity: Identity, repository: Repository) -> AgentTaskSnapshotResponse:
    """Return the tenant-authorized task document without replaying its execution.

    [AI-AGENT-TASK-004] Refresh and SSE reconnect use this endpoint rather than
    repeating POST /runs, so a disconnected browser cannot duplicate a capability
    invocation or a high-risk approval request.
    """
    run = await repository.get_run(run_id)
    if run is None or run.user_id != identity.user_id or run.space_id != identity.space_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="运行不存在或无权访问")
    snapshot = await repository.get_task_snapshot(run_id)
    if snapshot is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="任务执行视图已过期或不存在")
    # Do not expose the server-only snapshot user_id merely because the requester is
    # entitled to this run.  The browser needs the task document, not an identity echo.
    return AgentTaskSnapshotResponse.model_validate(snapshot.model_dump(exclude={"user_id"}))


@router.post("/api/v1/ai/runs/{run_id}/cancel", status_code=status.HTTP_202_ACCEPTED)
async def cancel_run(run_id: str, identity: Identity, repository: Repository) -> dict[str, bool]:
    run = await repository.get_run(run_id)
    if run is None or run.user_id != identity.user_id or run.space_id != identity.space_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="运行不存在或无权访问")
    if run.status in {RunStatus.COMPLETED, RunStatus.FAILED, RunStatus.CANCELLED, RunStatus.TIMED_OUT}:
        return {"cancel_requested": False}
    await repository.request_cancellation(run_id)
    return {"cancel_requested": True}


@router.post("/api/v1/ai/runs/{run_id}/approval")
async def approve_run(run_id: str, body: ApprovalRequest, identity: Identity, repository: Repository) -> dict[str, object]:
    run = await repository.get_run(run_id)
    if run is None or run.user_id != identity.user_id or run.space_id != identity.space_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="运行不存在或无权访问")
    pending = await repository.consume_approval(run_id, body.approval_token, body.approved)
    if pending is None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="审批已过期、已处理或不匹配")
    # The pending decision is persisted and bound to the run/call. The following resume
    # endpoint consumes only this server-stored payload; no browser-supplied arguments
    # are ever used to execute an approved operation.
    return {"accepted": True, "approved": body.approved, "run_id": run_id, "status": run.status, "resume_required": True}


@router.post("/api/v1/ai/runs/{run_id}/resume")
async def resume_run(
    run_id: str,
    request: Request,
    identity: Identity,
    repository: Repository,
    runtime: Runtime,
) -> StreamingResponse:
    run = await repository.get_run(run_id)
    if run is None or run.user_id != identity.user_id or run.space_id != identity.space_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="运行不存在或无权访问")

    async def events() -> AsyncIterator[bytes]:
        async for payload in encode_sse_events(runtime.resume(run=run, identity=identity), run, get_settings()):
            yield payload
        yield b": stream complete\n\n"

    return StreamingResponse(
        events(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache, no-transform", "Connection": "keep-alive", "X-Accel-Buffering": "no"},
    )
