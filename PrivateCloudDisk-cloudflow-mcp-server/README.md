# CloudFlow MCP Server

CloudFlow MCP Server is the **server-only Integration Plane** for third-party
AI Agents. It implements MCP JSON-RPC 2.0 over Streamable HTTP/SSE and exposes
only a reviewed, user- and tenant-filtered subset of CloudFlow capabilities.

It is deliberately **not** an Agent, a CloudFlow Runtime, a compiler, an API
gateway, or a direct business-service client.

```text
External Agent
  -> Gateway (Bearer JWT validation + signed trusted context)
  -> CloudFlow MCP Server (MCP protocol, adapter, rate limits)
  -> Capability Hub (visibility, JSON Schema, resource permission, dispatch, audit)
  -> Platform capability target / plugin runtime / workflow target
```

The only enterprise dependency in this service is Capability Hub. There is no
database, Redis, object-store, file-service, workflow-service data API,
plugin-service, runtime, or filesystem client in this module.

## Implemented protocol surface

- MCP stable protocol version `2025-11-25`, JSON-RPC 2.0.
- `initialize`, `notifications/initialized`, `ping`, `notifications/cancelled`.
- `tools/list` with opaque cursors and a reviewed export allowlist.
- `tools/call` with context binding, timeout, cancellation and deterministic
  idempotency keys.
- Safe static `resources/list` / `resources/read` and `prompts/list` /
  `prompts/get` entries; neither discloses tenant data.
- Streamable HTTP `POST /mcp`, optional session SSE `GET /mcp`, and protected
  resource metadata at `GET /.well-known/oauth-protected-resource/mcp`.
- Liveness/readiness health endpoints and Prometheus text metrics.

## Security model

1. The public endpoint is only exposed by Gateway as `/api/v1/mcp`.
2. Gateway validates the external Bearer JWT, removes client-forged internal
   headers, and signs method/path/request/user/tenant/space context with a
   short-lived HMAC (`pcd-mcp-v1`).
3. This service verifies that binding but never parses or forwards the external
   bearer token.
4. Agent-supplied identity, tenant, space, permission, trace and idempotency
   fields are removed before `tools/call` reaches Hub.
5. Capability Hub performs the final live capability, JSON Schema, tenant,
   space and resource authorization checks, dispatches the capability and
   persists execution audit data.
6. Protocol-only operations are written to Hub's audit pipeline using sanitized
   parameter summaries. Tokens and raw tool results are never logged.

## Local verification

The service is not a local MCP mode. Running it locally only starts the same
server mode used in production:

```bash
cd PrivateCloudDisk-cloudflow-mcp-server
export MCP_CAPABILITY_HUB_URL=http://localhost:8087
export MCP_INTERNAL_SERVICE_TOKEN='<internal-service-token>'
export MCP_IDENTITY_SHARED_SECRET='replace-with-another-long-random-secret'
go run ./cmd/mcp-server
```

Run verification:

```bash
go test -cover ./...
go vet ./...
```

The direct process accepts only Gateway-signed private-hop requests. A third
party Agent must use the Gateway URL and Bearer authentication rather than
connecting to `:8093`:

```text
https://<gateway-host>/api/v1/mcp
```

## Configuration

| Variable | Required | Purpose |
| --- | --- | --- |
| `MCP_CAPABILITY_HUB_URL` | yes | Fixed internal Capability Hub base URL; the sole capability dependency. |
| `MCP_INTERNAL_SERVICE_TOKEN` | yes | Hub internal-service credential. |
| `MCP_IDENTITY_SHARED_SECRET` | yes | 32+ byte HMAC secret shared only with Gateway. |
| `MCP_OAUTH_AUTHORIZATION_SERVERS` | production | Comma-separated OAuth 2.1 authorization-server issuer metadata URLs advertised in protected-resource metadata. |
| `MCP_PUBLIC_BASE_URL` | Gateway production | Canonical Gateway base URL used in `WWW-Authenticate` resource-metadata challenges; configure on Gateway, not this process. |
| `MCP_REQUIRED_AUDIENCE` / `MCP_REQUIRED_SCOPE` | Gateway production | Optional development, mandatory production policy for the OAuth token audience/scope accepted on `/api/v1/mcp`. |
| `MCP_REQUEST_TIMEOUT_SECONDS` | no | End-to-end Hub invocation timeout; default `30`. |
| `MCP_TOOL_LIST_CACHE_TTL_SECONDS` | no | Per signed identity/tenant/space discovery cache; default `300`. |
| `MCP_MAX_BODY_BYTES` | no | JSON-RPC request cap; default `1048576`. |
| `MCP_MAX_CONCURRENT_REQUESTS` | no | In-process request cap; default `128`. |
| `MCP_REQUESTS_PER_MINUTE_PER_USER` | no | Secondary per-user fixed-window cap; default `120`. |
| `MCP_OTEL_EXPORTER_OTLP_ENDPOINT` | no | OTLP/HTTP collector host:port. Empty means W3C propagation only. |
| `MCP_OTEL_EXPORTER_OTLP_INSECURE` | no | Permit plaintext OTLP only in a protected internal network; default `false`. |

Do not assign this service database, storage, broker or arbitrary network
credentials. Deployment and Agent examples are documented in
[`../docs/CLOUDFLOW_MCP_DEPLOYMENT.md`](../docs/CLOUDFLOW_MCP_DEPLOYMENT.md) and
[`../docs/CLOUDFLOW_MCP_AGENT_GUIDE.md`](../docs/CLOUDFLOW_MCP_AGENT_GUIDE.md).

## Documentation

- [Architecture and audit](../docs/CLOUDFLOW_MCP_SERVER_DESIGN.md)
- [Wire protocol and JSON-RPC examples](../docs/CLOUDFLOW_MCP_PROTOCOL.md)
- [Security and tenant isolation](../docs/CLOUDFLOW_MCP_SECURITY.md)
- [Deployment and operations](../docs/CLOUDFLOW_MCP_DEPLOYMENT.md)
- [Third-party Agent onboarding](../docs/CLOUDFLOW_MCP_AGENT_GUIDE.md)
- [Test and verification report](../docs/CLOUDFLOW_MCP_TEST_REPORT.md)
- [Editable C4 architecture diagram](../docs/architecture/cloudflow-mcp-server.drawio)
