# Changelog

## 0.1.0 — 2026-08-29

- Added the server-only CloudFlow MCP integration service in Go 1.24.
- Implemented MCP `2025-11-25` JSON-RPC initialization, tool discovery/tool
  calling, static safe resources/prompts, Streamable HTTP and optional SSE.
- Added Gateway-bound HMAC trusted identity verification; external Bearer
  credentials are never forwarded to Capability Hub.
- Added reviewed Capability Hub export policy, context stripping, per-identity
  tool-list cache, rate limits, response/body limits and request cancellation.
- Added Capability Hub MCP internal discovery, invocation and audit endpoints
  that reuse final authorization, schema validation, dispatch and audit paths.
- Added Gateway MCP route/signature configuration, Docker Compose, hardened
  Kubernetes deployment, Prometheus metrics and OpenTelemetry OTLP tracing.
- Added protocol, security, deployment, Agent-onboarding and test documents,
  plus an editable C4 architecture diagram.
