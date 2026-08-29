"""FastAPI entry point for the rebuilt Cloud AI Agent Service."""

from __future__ import annotations

from contextlib import asynccontextmanager
from time import perf_counter

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import make_asgi_app
from redis.asyncio import Redis

from app.api.routes import router
from app.core.config import get_settings
from app.core.limiter import RunRateLimiter
from app.memory.repository import ConversationRepository
from app.observability import HTTP_LATENCY, HTTP_REQUESTS
from app.providers.router import ProviderRouter
from app.runtime.agent import AgentRuntime
from app.tools.capability_hub import CapabilityHubClient
from app.tools.registry import ToolRegistry


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    redis = Redis.from_url(settings.redis_url, decode_responses=False, health_check_interval=20)
    capability_hub = CapabilityHubClient(settings)
    provider_router = ProviderRouter(settings)
    repository = ConversationRepository(redis)
    tools = ToolRegistry(settings, capability_hub)
    app.state.redis = redis
    app.state.settings = settings
    app.state.repository = repository
    app.state.provider_router = provider_router
    app.state.agent_runtime = AgentRuntime(settings, repository, provider_router, tools)
    app.state.run_limiter = RunRateLimiter(redis, settings.run_rate_limit_per_minute)
    try:
        yield
    finally:
        await capability_hub.close()
        await redis.aclose()


settings = get_settings()
app = FastAPI(
    title="Cloud AI Agent Service",
    version="1.0.0",
    docs_url="/docs" if settings.enable_docs else None,
    redoc_url=None,
    lifespan=lifespan,
)
if settings.cors_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=False,
        allow_methods=["GET", "POST", "PATCH", "DELETE"],
        allow_headers=["Authorization", "Content-Type", "Last-Event-ID"],
        expose_headers=["X-Request-Id"],
        max_age=600,
    )


@app.middleware("http")
async def observe_http(request, call_next):
    """Emit low-cardinality request metrics without recording tenant data."""
    started = perf_counter()
    response = await call_next(request)
    route = request.scope.get("route")
    route_label = getattr(route, "path", "unmatched")
    HTTP_REQUESTS.labels(method=request.method, route=route_label, status=str(response.status_code)).inc()
    HTTP_LATENCY.labels(method=request.method, route=route_label).observe(perf_counter() - started)
    return response


# The container is not exposed directly by Gateway. Operations systems scrape this
# internal endpoint; it contains counters only and never exposes prompt/message data.
app.mount("/metrics", make_asgi_app())
app.include_router(router)
