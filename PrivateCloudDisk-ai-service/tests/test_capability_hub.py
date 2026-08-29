from __future__ import annotations

import httpx
import json
import pytest

from app.core.identity import RequestIdentity
from app.tools.capability_hub import CapabilityHubClient


@pytest.mark.asyncio
async def test_capability_invocation_uses_fixed_hub_url_and_static_identity(settings):
    captured: dict[str, object] = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["headers"] = dict(request.headers)
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"success": True, "output": {"name": "report.md"}})

    client = CapabilityHubClient(settings, httpx.AsyncClient(transport=httpx.MockTransport(handler)))
    result = await client.invoke(
        capability_key="api:file.search",
        identity=RequestIdentity("user-a", "space-a", "request-a"),
        run_id="00000000-0000-0000-0000-000000000001",
        step_id="ai:1:call-a", attempt=1, input_data={"keyword": "report"}, permissions=("file.read",),
    )
    assert result.success is True
    assert captured["url"] == "http://capability-hub.test/internal/v1/capabilities/invoke"
    body = captured["body"]
    assert body["userId"] == "user-a"
    assert body["declaredPermissions"] == ["file.read"]
    assert "http" not in body["input"]
    await client.close()


@pytest.mark.asyncio
async def test_workflow_envelope_business_failure_is_returned_without_raising(settings):
    """HTTP 200 is the Hub contract even when the data-plane operation failed."""

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "code": "OK",
                "message": "操作成功",
                "data": {
                    "success": False,
                    "output": {},
                    "errorCode": "WF-CAPABILITY-DATAPLANE-UNAVAILABLE",
                    "errorSummary": "数据面服务暂时不可用：500 Internal Server Error",
                    "retryable": True,
                },
            },
        )

    client = CapabilityHubClient(settings, httpx.AsyncClient(transport=httpx.MockTransport(handler)))
    result = await client.invoke(
        capability_key="api:file.list",
        identity=RequestIdentity("user-a", "space-a", "request-a"),
        run_id="00000000-0000-0000-0000-000000000001",
        step_id="ai:1:call-a",
        attempt=1,
        input_data={},
        permissions=("file.read",),
    )

    assert result.success is False
    assert result.error_code == "WF-CAPABILITY-DATAPLANE-UNAVAILABLE"
    assert result.retryable is True
    assert "500 Internal Server Error" in (result.error_message or "")
    await client.close()


@pytest.mark.asyncio
async def test_gateway_502_is_normalized_without_unbound_body_or_payload_logging(settings, capsys):
    async def handler(request: httpx.Request) -> httpx.Response:
        # A gateway commonly returns HTML or a plain-text body rather than JSON.
        return httpx.Response(502, text="upstream workflow-service unavailable")

    client = CapabilityHubClient(settings, httpx.AsyncClient(transport=httpx.MockTransport(handler)))
    result = await client.invoke(
        capability_key="api:file.search",
        identity=RequestIdentity("user-a", "space-a", "request-a"),
        run_id="00000000-0000-0000-0000-000000000001",
        step_id="ai:1:call-a",
        attempt=1,
        input_data={"keyword": "secret"},
        permissions=("file.read",),
    )

    assert result.success is False
    assert result.error_code == "AI-CAPABILITY-UNAVAILABLE"
    assert result.retryable is True
    assert "HTTP 502" in (result.error_message or "")
    captured = capsys.readouterr()
    assert captured.out == ""
    assert captured.err == ""
    await client.close()
