# CloudFlow MCP Server 定向验证报告

> 执行日期：2026-08-29。此报告记录本次工作区的构建/契约验证；它不替代带真实 OAuth issuer、
> Gateway、Hub 数据库、业务数据面、Kubernetes 网络策略的联调、压测和渗透测试。

## 已执行验证

| 层 | 命令/范围 | 结果 | 覆盖要点 |
| --- | --- | --- | --- |
| Go MCP Server | `go test ./...`、`go vet ./...` | 通过 | HMAC 绑定/时效、工具 export/schema 清理、初始化、分页/缓存、调用、Hub-only 边界、伪造 context 删除、未签名拒绝。 |
| Go MCP 核心覆盖率 | `go test -coverprofile=... ./internal/mcp` | 通过，`88.6%` | 初始化、工具发现/调用、静态 resources/prompts、SSE、取消、超时/容量/限流、健康/metadata、metrics 与异常分支。 |
| Go SSE 回归 | `internal/mcp/server_test.go` | 通过 | 认证 challenge 使用 Gateway metadata URL；SSE 响应是单个有效 event frame。 |
| Workflow Capability Hub | `./gradlew test --rerun-tasks --tests org.project.workflow.controller.CapabilityInvokeWebTest --tests org.project.workflow.service.CapabilityHubServiceExtTest` | 通过 | MCP 私网端点 service token、防止不在 export policy 的 destructive capability、ACTIVE/权限过滤。 |
| Gateway | `./gradlew test --rerun-tasks --tests org.project.privateclouddiskgatewayservice.filter.global.AuthGlobalFilterMcpTest` | 通过 | MCP 路由配置、独立 HMAC 属性与 JWT tenant claim 解析；无 Bearer 时返回固定 canonical OAuth resource-metadata challenge。 |
| Compose | `docker compose --profile automation config --no-interpolate` | 通过（仅提示现有 `version` obsolete warning） | `cloudflow-mcp-server`、Gateway 路由依赖、OTLP 和 canonical public URL 配置均已解析。 |
| 图 | `drawio-skill ... validate.py docs/architecture/cloudflow-mcp-server.drawio` | 通过 | 3 页、16 个元素、0 errors、0 warnings。 |

Go 依赖整理后使用 OpenTelemetry `v1.36.0`，`go test ./...` 和 `go vet ./...` 均无失败。

## 安全回归矩阵（代码/单测覆盖）

| 场景 | 预期 | 本次状态 |
| --- | --- | --- |
| 无 Gateway HMAC | 401 + OAuth metadata challenge | 已测试。 |
| 修改已签名 space | HMAC 验证失败 | 已测试。 |
| 过期 signed context | 拒绝 | 已测试。 |
| Agent 伪造 `space_id`/`tenant_id` 参数 | Adapter 删除，Hub 使用签名上下文 | 已测试。 |
| 未审核能力导出/调用 | Adapter/Hub 拒绝 | 已测试。 |
| 未带 Hub service token 的 MCP 私网端点 | 401 | 已测试。 |
| 过大 body、并发满、每用户限流 | 413/503/429 且不调 Hub | 实现已覆盖，需压力测试。 |
| Hub 不可用/超时 | MCP 安全失败，不自动重复非幂等调用 | 实现已覆盖，需真实 Hub 联调。 |
| OAuth PKCE/audience/scope | 真实授权后发现和调用 | **未在当前项目发现 issuer，列为上线前置项。** |

## 待真实环境验收

1. OAuth 2.1 metadata、Authorization Code + PKCE、Gateway 已实现的 audience/scope 策略、刷新/吊销、401 恢复。
2. Gateway→MCP HMAC secret 轮换、负载均衡下 Session/SSE、TLS/反向代理 header 信任边界。
3. Hub→Platform/Plugin/Workflow 的最终资源权限，用户 A/B、tenant A/B、多个 space 的越权矩阵。
4. 写/工作流工具的幂等、取消、审计完整性和结果脱敏。
5. Kubernetes NetworkPolicy、非 root/read-only filesystem、Prometheus/OTLP collector、告警。
6. 并发 Agent、SSE 长连接、大工具列表、Hub 故障注入、DoS/SSRF/参数注入渗透测试。

本机 `kubectl apply --dry-run=client` 因没有可连接的 Kubernetes API server（默认
`localhost:8080`）而无法完成 schema 验证；清单已完成 Compose 解析和人工最小权限审查，仍须在
目标集群/CI 的真实 API schema 下执行 `kubectl apply --dry-run=server` 作为发布门禁。

本机 Docker CLI 可解析 Compose，但 Docker daemon 未运行，因而未能执行 Dockerfile 的实际镜像
build；Go 宿主构建/测试已通过，镜像 build 与 distroless nonroot 运行探针同样列入 CI/部署前验收。

只有上述真实集成和安全测试通过后，才能将服务标记为第三方公网 Agent 可用。
