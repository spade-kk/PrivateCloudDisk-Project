"""Capability Hub client with fixed endpoint and server-derived invocation context."""

from __future__ import annotations

import hashlib
import json
import time
from typing import Any

import httpx

from app.core.config import Settings
from app.core.redaction import redact
from app.core.identity import RequestIdentity
from app.domain.models import ToolExecutionResult


class CapabilityHubClient:
    """The only internal business-service HTTP client allowed in Cloud AI Agent.

    It accepts neither user-provided URLs nor model-provided permission lists. The
    Capability Hub remains the final authority for resource access and capability
    registration; this client merely creates the established agent invocation envelope.
    """

    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None) -> None:
        self._settings = settings
        self._client = client or httpx.AsyncClient(timeout=settings.llm_timeout_seconds, trust_env=False)
        self._owns_client = client is None

    async def invoke(
        self,
        *,
        capability_key: str,
        identity: RequestIdentity,
        run_id: str,
        step_id: str,
        attempt: int,
        input_data: dict[str, Any],
        permissions: tuple[str, ...],
    ) -> ToolExecutionResult:
        started = time.perf_counter()
        stable_source = json.dumps(
            {"run": run_id, "step": step_id, "capability": capability_key, "input": input_data},
            sort_keys=True,
            default=str,
        ).encode("utf-8")
        idempotency_key = hashlib.sha256(stable_source).hexdigest()
        payload = {
            "capabilityKey": capability_key,
            "executionId": run_id,
            "stepId": step_id[:128],
            "attempt": attempt,
            "userId": identity.user_id,
            "spaceId": identity.space_id,
            "input": input_data,
            # These are immutable registry policy values, not model/client input.
            "declaredPermissions": list(permissions),
            "grantedPermissions": list(permissions),
            "traceId": identity.request_id[:64],
            "idempotencyKey": idempotency_key,
        }
        headers = {
            "X-PCD-Service-Token": self._settings.internal_service_token.get_secret_value(),
            "X-Request-Id": identity.request_id,
        }
        # Do not log the request body here.  It contains user/space context,
        # idempotency material and capability parameters supplied by the model.
        # Capability Hub is the audit authority; this client only transports the
        # request and returns a redacted, stable result to AgentRuntime.
        try:
            response = await self._client.post(
                f"{self._settings.capability_hub_url.rstrip('/')}/internal/v1/capabilities/invoke",
                json=payload,
                headers=headers,
            )
            # Parse the body before interpreting the HTTP status.  Workflow
            # Service intentionally uses an HTTP 200 envelope for business
            # failures (for example WF-CAPABILITY-DATAPLANE-UNAVAILABLE),
            # while a proxy/gateway may return a bare 502/503 response.
            try:
                body = response.json()
                print(body)
            except (ValueError, json.JSONDecodeError):
                body = {}

            if response.status_code >= 400:
                retryable = response.status_code in {408, 425, 429} or response.status_code >= 500
                return ToolExecutionResult(
                    call_id=step_id,
                    tool_name=capability_key,
                    success=False,
                    error_code="AI-CAPABILITY-UNAVAILABLE",
                    error_message=f"能力服务暂时不可用（HTTP {response.status_code}）",
                    retryable=retryable,
                    duration_ms=int((time.perf_counter() - started) * 1000),
                )

            result = self._unwrap_result(body)
            if result is None:
                return ToolExecutionResult(
                    call_id=step_id,
                    tool_name=capability_key,
                    success=False,
                    error_code="AI-CAPABILITY-INVALID-RESPONSE",
                    error_message="能力服务返回了无法识别的响应",
                    retryable=True,
                    duration_ms=int((time.perf_counter() - started) * 1000),
                )
            success = bool(result.get("success", False))
            output = result.get("output")
            if not isinstance(output, dict):
                output = {"value": output} if output is not None else {}
            return ToolExecutionResult(
                call_id=step_id,
                tool_name=capability_key,
                success=success,
                output=redact(output) if success else {},
                error_code=None if success else str(result.get("errorCode", "AI-CAPABILITY-FAILED")),
                error_message=None if success else self._safe_error_message(result.get("errorSummary")),
                retryable=bool(result.get("retryable", False)),
                duration_ms=int((time.perf_counter() - started) * 1000),
            )
        except httpx.TimeoutException:
            return ToolExecutionResult(
                call_id=step_id, tool_name=capability_key, success=False,
                error_code="AI-CAPABILITY-TIMEOUT", error_message="能力调用超时，请稍后重试",
                retryable=True, duration_ms=int((time.perf_counter() - started) * 1000),
            )
        except httpx.RequestError as e:
            return ToolExecutionResult(
                call_id=step_id, tool_name=capability_key, success=False,
                error_code="AI-CAPABILITY-UNAVAILABLE", error_message="企业能力服务暂时不可用",
                retryable=True, duration_ms=int((time.perf_counter() - started) * 1000),
            )

    @staticmethod
    def _unwrap_result(body: Any) -> dict[str, Any] | None:
        """Accept both the workflow envelope and a direct CapabilityResult.

        The public shape is ``{data: CapabilityResult}``, but local adapters and
        older deployments may return ``CapabilityResult`` directly.  Some
        internal adapters also wrap the result once more in ``data.result``;
        accepting all three keeps the client backward compatible without
        exposing raw gateway/stack-trace payloads to the model.
        """
        if not isinstance(body, dict):
            return None
        data = body.get("data", body)
        if not isinstance(data, dict):
            return None
        result = data.get("result", data)
        return result if isinstance(result, dict) else None

    @staticmethod
    def _safe_error_message(value: Any) -> str:
        if not isinstance(value, str) or not value.strip():
            return "能力调用失败"
        # Keep useful business diagnostics while preventing an upstream stack
        # trace, URL or arbitrarily large response from entering the prompt.
        return " ".join(value.split())[:512]

    async def close(self) -> None:
        if self._owns_client:
            await self._client.aclose()
