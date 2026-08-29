"""Minimal Prometheus metrics with no enterprise-data labels."""

from __future__ import annotations

from prometheus_client import Counter, Histogram


# Never label metrics with user, space, conversation, prompt, file or tool arguments:
# high-cardinality labels leak tenant data and can exhaust the metrics backend.
HTTP_REQUESTS = Counter("cloud_ai_agent_http_requests_total", "HTTP requests handled", ("method", "route", "status"))
HTTP_LATENCY = Histogram("cloud_ai_agent_http_request_seconds", "HTTP request latency", ("method", "route"))
AGENT_RUNS = Counter("cloud_ai_agent_runs_total", "Agent runs by terminal state", ("status",))
TOOL_CALLS = Counter("cloud_ai_agent_tool_calls_total", "Tool calls by result", ("tool", "result"))
