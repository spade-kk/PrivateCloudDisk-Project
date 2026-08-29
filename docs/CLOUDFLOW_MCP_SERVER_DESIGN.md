# CloudFlow MCP Server：服务端能力出口设计与审计

> 状态：首版实现（`2026-08-29`）。本文将已经通过代码/定向测试验证的契约与仍需在
> 真实 OAuth、Hub、Gateway、集群环境验证的项目明确分开，避免把设计结论写成生产事实。

## 1. 定位、边界与非目标

CloudFlow MCP Server 是面向 Codex、Claude Code、Cursor 等第三方 Agent 的服务端
**Integration Plane**。它将 CloudFlow 可授权能力转为标准 MCP 工具，不做推理、规划、
工作流执行、编译或数据库访问。

```text
Capability Hub (Ability Plane)
             ├─ Cloud AI Agent (first-party Intelligence Plane, does not use MCP)
             └─ CloudFlow MCP Server (external Integration Plane)
                        └─ External MCP Agents
```

不可突破的边界：

| MCP Server 可以做 | MCP Server 不可以做 |
| --- | --- |
| MCP JSON-RPC / SSE、工具名称与 JSON Schema 转换、限流、会话、身份映射、协议审计 | 直连数据库、文件系统、对象存储、File/Workflow/Plugin/Runtime 服务，执行工作流，解释内部能力模型 |
| 通过一个固定的 Capability Hub URL 发现并调用能力 | 根据 Agent 参数拼接目标 URL 或转发用户 Bearer Token |
| 过滤已审核的工具并将服务端身份上下文交给 Hub | 将 `user_id`、`tenant_id`、`space_id`、权限、执行链路参数暴露为工具入参 |

CloudFlow LS、`syntax-highlight` 与 MCP 不重复：LS 解决编辑器语法/类型/补全；
`syntax-highlight` 生成静态高亮和基础补全规则；本服务仅解决**外部 Agent 能力发现与调用**。

## 2. 实施前审计结论

| 审计对象 | 已确认实现 | 设计决策 |
| --- | --- | --- |
| Capability Hub | 已有注册表、能力状态、实时可见性、JSON Schema 校验、API/插件/内置分发、调用台账与 `pcd_capability_audit` | MCP 只新增私网适配端点，复用这些最终校验，绝不绕过 Hub。 |
| Hub 内部调用 | 原 `invoke` 面向 Runtime 信封，缺 MCP 调用者/导出策略 | 新增 `/internal/v1/capabilities/mcp/tools`、`/invoke`、`/audit`，仍受 `InternalServiceFilter` 服务凭证保护。 |
| Gateway | 已验证 Bearer JWT 并剥离伪造的内部头；AI 路由已有独立签名协议 | 新增独立 `pcd-mcp-v1` HMAC，不改变 AI 的 epoch-seconds 既有协议；MCP 使用 RFC3339。 |
| 认证体系 | 当前项目可验证平台 JWT，但未审计到完整 OAuth 2.1 Authorization Server/PKCE/DCR 实现 | MCP 可发布受保护资源元数据；生产必须配置现有/新增 OAuth issuer，OAuth 全流程属于身份平台上线前置条件。 |
| 文件/工作流/插件服务 | Hub 才是它们的统一能力入口 | Go 模块只含 `internal/hub` 一个企业 HTTP Client；编译与审计均可机械验证无其他业务 Client。 |
| 审计 | Hub 已存储用户、空间、能力、参数摘要、状态、耗时、追踪 ID | 工具执行由 `invokeMcp` 同步持久化，协议方法最佳努力写同一审计表；Token/原始结果不落日志。 |

## 3. 可编辑架构图

源文件为 [`architecture/cloudflow-mcp-server.c4.json`](./architecture/cloudflow-mcp-server.c4.json)，
生成并校验的可编辑图为
[`architecture/cloudflow-mcp-server.drawio`](./architecture/cloudflow-mcp-server.drawio)。图包括
Context、Container、Component 三页，描绘 Agent→Gateway→MCP→Hub→能力目标，以及审计、
指标、OTel 链路。

## 4. 请求链路

1. Agent 向 Gateway 的 `POST /api/v1/mcp` 发送 MCP JSON-RPC，携带 `Authorization: Bearer`。
2. Gateway 验证 JWT，移除 Agent 伪造的 `X-PCD-*` 头；为 method、私网 `/mcp` path、request ID、
   user、token 中可选 tenant、请求选择的 space 生成短期 HMAC。
3. MCP 验证 HMAC、时间窗和请求绑定；它不再读取/转发 Bearer Token。
4. `tools/list` 使用 `(user, tenant, space)` 维度的五分钟内存缓存向 Hub 查询；Hub 只返回 ACTIVE
   且用户此刻可见的能力。
5. Adapter 使用**代码审核的出口策略**将内部能力映射成 `cloudflow.*` 工具，并从 input schema
   删除身份/权限/执行字段。
6. `tools/call` 清理 Agent 伪造的受保护字段、产生确定性幂等键，调用 Hub 的 MCP 私网端点。
7. Hub 再次做能力状态、出口策略、租户/空间可用性、实时资源权限、Schema、熔断和分发校验，
   写调用台账与审计；结果再转为 MCP `content` / `structuredContent`。

`X-Space-Id` 只是“请求空间上下文”选择，不是授权证明。Hub 必须始终校验用户对该空间和目标
资源的实时权限，因而客户端无法通过改 header 获得横向访问权。

## 5. 审核后的工具导出策略

仅下列内部键在“已注册、ACTIVE、Hub 判定当前用户可见”三个条件同时满足时可被适配；其余
包括删除用户、删除空间、安装插件等管理能力默认完全不可见。

| 内部能力键 | MCP 工具名 | 用途 |
| --- | --- | --- |
| `api:file.list` | `cloudflow.file.list` | 枚举当前已授权空间/目录的文件 |
| `api:file.search` | `cloudflow.file.search` | 搜索当前已授权范围的文件元数据 |
| `api:file.content.get` | `cloudflow.file.read` | 读取受大小与类型限制的可读文件内容 |
| `api:file.metadata.get` | `cloudflow.file.metadata` | 获取文件元数据 |
| `api:space.info` | `cloudflow.space.info` | 获取已授权空间基本信息 |
| `api:workflow.list` | `cloudflow.workflow.list` | 发现用户可运行的工作流 |
| `api:workflow.execute` | `cloudflow.workflow.run` | 按 Hub 的执行/审批策略启动工作流 |
| `api:workflow.status` | `cloudflow.workflow.status` | 查询用户可见的工作流状态 |

策略不是“把能力硬编码进 MCP”：Hub 注册表仍是能力真相源。策略只是面向外部 Agent 的最小、
可审计安全出口；新增工具需同时评审 Hub 的 schema、权限、结果脱敏、幂等性和这份映射。

## 6. 状态、缓存与失败语义

- 工具发现缓存仅含已经按用户/租户/空间过滤后的能力页，TTL 默认 5 分钟，不缓存 Token、
  结果或内部权限模型；权限变更由 Hub 的实时复核兜底。
- 相同受信身份/会话/RPC ID/能力名产生同一个 SHA-256 幂等键，Hub 调用台账负责避免重复执行。
- 取消通知会取消仍在 MCP 进程中的 Hub HTTP 请求；已经被 Hub 接收的操作仍以 Hub 幂等台账为准。
- 下游不可用、超时和过大结果会转为稳定的 MCP 错误，绝不自动重试非幂等工具。
- 缓存失效可由后续 Capability Hub 变更事件显式驱动；当前首版以短 TTL + 每次调用实时授权保证安全，
  未把 Redis 作为含权限状态的第二真相源。

## 7. 可观测性与生产前置项

- `/metrics` 暴露请求、错误、耗时、并发等 Prometheus 文本指标。
- MCP 入口启动 OpenTelemetry span，并以 W3C Trace Context 传播到 Hub。设置
  `MCP_OTEL_EXPORTER_OTLP_ENDPOINT` 后通过 OTLP/HTTP 导出；为空时仅传播且不外发遥测。
- Docker 与 Kubernetes 均使用非 root、只读根文件系统、无数据库/卷、最小网络策略，入口只允许
  Gateway，出口只允许 Hub/DNS。
- **生产上线门禁**：可用的 OAuth 2.1 授权服务器（含认证码+PKCE、token audience/scope、
  吊销/过期处理）、Gateway 与 MCP 相同 HMAC Secret、Hub 服务 token、TLS/反向代理、真实
  权限/审计/压测/渗透回归。首版源码不会伪造一个不在项目内的 OAuth issuer。

## 8. 验收追踪

| 需求组 | 已落地证据 |
| --- | --- |
| 定位/语言/目录 | 独立 `PrivateCloudDisk-cloudflow-mcp-server` Go 1.24 服务；`cmd`、`internal/mcp`、`adapter`、`auth(identity)`、`hub(capability)`、`audit`。 |
| MCP 协议 | `internal/mcp/server.go`、`internal/model/model.go`、[`CLOUDFLOW_MCP_PROTOCOL.md`](./CLOUDFLOW_MCP_PROTOCOL.md)。 |
| Adapter/Hub | `McpCapabilityExportPolicy.java`、`CapabilityHubService.invokeMcp/listMcpVisible`、`CapabilityController` 私网端点。 |
| 认证/隔离 | Gateway `McpIdentitySigningProperties`、`AuthGlobalFilter`、Go `identity.Verifier`、schema/参数清理。 |
| 审计/监控/安全 | Hub audit 表复用、`/metrics`、`internal/telemetry`、Docker/Kubernetes NetworkPolicy。 |
| 测试/文档 | Go/Java/Gateway 定向验证及 [`CLOUDFLOW_MCP_TEST_REPORT.md`](./CLOUDFLOW_MCP_TEST_REPORT.md)。 |
