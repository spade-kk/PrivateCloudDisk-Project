# CloudFlow MCP Server 部署、配置与运维

## 镜像与网络

Dockerfile 使用 Go 编译阶段和 distroless nonroot 运行阶段。运行镜像无 shell、包管理器、
数据库客户端或云 SDK。Compose 服务仅 `expose: 8093`，不发布宿主端口；公网仅经 Gateway
`/api/v1/mcp` 进入。

```text
Gateway (public 8080) -> cloudflow-mcp-server:8093 -> workflow-service-backend:8087
```

Compose 使用 `automation` profile：

```bash
export PCD_INTERNAL_SERVICE_TOKEN='<same internal token used by Hub>'
export MCP_IDENTITY_SIGNING_SECRET='<at least 32 random bytes>'
export MCP_OAUTH_AUTHORIZATION_SERVERS='https://auth.example.com'
docker compose --profile automation up -d gateway-service-backend workflow-service-backend cloudflow-mcp-server
```

`MCP_IDENTITY_SIGNING_SECRET` 必须同时配置为 Gateway 的
`gateway.mcp.identity-signing-secret` 和 MCP 的 `MCP_IDENTITY_SHARED_SECRET`；不要与 AI Agent
签名密钥复用。Gateway 还必须设置 `MCP_PUBLIC_BASE_URL=https://<canonical-gateway-host>`，以便未认证
MCP Client 收到的 `WWW-Authenticate` challenge 指向固定 HTTPS protected-resource metadata URL，而非
从请求 Host 推导 URL。生产 OAuth issuer 还必须配置 `MCP_REQUIRED_AUDIENCE` 和
`MCP_REQUIRED_SCOPE`，使 Gateway 在签发 HMAC 前验证该 access token 确实是为 MCP resource 颁发。

## Kubernetes

清单：
[`../PrivateCloudDisk-cloudflow-mcp-server/deploy/kubernetes/cloudflow-mcp-server.yaml`](../PrivateCloudDisk-cloudflow-mcp-server/deploy/kubernetes/cloudflow-mcp-server.yaml)。

它包含 ServiceAccount（禁用 token 自动挂载）、ConfigMap、Deployment、ClusterIP Service、PDB 和
NetworkPolicy。创建 secret：

```bash
kubectl create secret generic pcd-internal-service \
  --from-literal=token='<internal-service-token>'
kubectl create secret generic cloudflow-mcp-identity \
  --from-literal=gateway-hmac-secret='<32-byte-or-longer-secret>'
kubectl apply -f PrivateCloudDisk-cloudflow-mcp-server/deploy/kubernetes/cloudflow-mcp-server.yaml
```

应用前要把 NetworkPolicy 的 Gateway/Workflow label selector 调整成目标集群真实标签；不能为了
排障而添加 Platform、Storage、Plugin、Runtime、数据库、Redis、RabbitMQ 或全网 egress。

## 健康、指标与追踪

| Endpoint/配置 | 用途 |
| --- | --- |
| `GET /health/live` | 进程活性。 |
| `GET /health/ready` | Adapter 可接收请求；不因 Hub 短暂抖动被摘除，调用时仍会返回可重试 MCP 错误。 |
| `GET /metrics` | Prometheus 文本指标；仅私网 scrape。 |
| `MCP_OTEL_EXPORTER_OTLP_ENDPOINT` | 设置后用 OTLP/HTTP 导出 traces。 |
| `MCP_OTEL_EXPORTER_OTLP_INSECURE` | 仅可信内网临时允许明文 OTLP；生产默认 TLS。 |

MCP 入口与 Hub 调用会携带 W3C `traceparent`，但不会把用户/tenant/space 放进 baggage。
建议告警：5xx/MCP internal error 比例、Hub 调用 p95、限流/容量拒绝、SSE 长连接数、审计写失败。

## 运行参数和容量

- `MCP_MAX_BODY_BYTES`：默认 1 MiB，硬限制 16 MiB。
- `MCP_MAX_CONCURRENT_REQUESTS`：默认 128，按 Hub 容量和实例数调节。
- `MCP_REQUESTS_PER_MINUTE_PER_USER`：默认 120，是进程内第二道保护；网关应保留分布式限流。
- `MCP_REQUEST_TIMEOUT_SECONDS`：默认 30；非幂等能力失败后由 Hub 台账保障安全重试。
- `MCP_TOOL_LIST_CACHE_TTL_SECONDS`：默认 300，缓存按 user/tenant/space 隔离。

不在未经压测的前提下承诺连接数或吞吐。扩容前应使用真实 Hub、权限数据、审计存储及目标 Agent
并发执行压测，验证 OAuth token 刷新、Hub 熔断和集群滚动升级。

## 变更与回滚

1. 先发布 Hub 的 MCP 私网端点与导出策略，再发布 MCP 服务，再启用 Gateway 路由。
2. 完成 `initialize`、`tools/list`、读工具、写/工作流工具、越权、token 过期、Hub 不可用、
   审计查询的真实环境验收。
3. 回滚时先在 Gateway 移除 MCP 路由/流量，再下线 MCP Pod；不要删除 Hub 的通用注册、审计或
   调用台账迁移。
4. 轮换 HMAC 或服务 token 采用双密钥/短窗口策略，避免 Gateway 与 MCP 版本交错造成拒绝风暴。
