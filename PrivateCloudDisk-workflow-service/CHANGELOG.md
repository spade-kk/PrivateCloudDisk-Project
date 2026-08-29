# PrivateCloudDisk-workflow-service 更新日志

## [0.2.2-SNAPSHOT] - 2026-08-26

- **CloudFlow LS 能力发现边界**：`GET /capabilities` 与详情查询改为消费 Gateway 的可信用户/空间/租户上下文，
  不再把 Capability Registry 作为匿名目录返回。
- **最小可见集合**：新增 `searchVisibleTo` / `getVisibleTo`，对能力声明权限、`enabled`、租户和空间策略做
  deny-by-default 过滤；未认证返回 401，隐藏/无权限详情返回 404。
- **回归测试**：覆盖权限缺失、租户/空间不匹配、畸形策略和缺失主体，确保 Capability Hub 供 Language Server
  动态补全时不泄露其他用户、租户或空间的能力。

## [0.2.1-SNAPSHOT] - 2026-08-24

- 修复 `invokeAgent` 在幂等 claim 之后才校验能力键的问题；非法键不再写入幂等台账。
- 防止同一幂等键跨能力复用，新增 `WF-CAPABILITY-IDEMPOTENCY-CONFLICT`，避免回放旧的数据面错误。
- 补充能力中心与 AI Agent 的 502、数据面 500 排障文档及回归测试。

## [0.2.0-SNAPSHOT] - 2026-08-21

能力中心审计与扩展（Capability Hub）：

- **新增平台 API 能力（V4 迁移）**：`api:file.metadata.get`、`api:file.scan`、`api:file.content.get`、
  `api:file.list`、`api:file.search`、`api:space.info`、`api:space.members.list`、`api:user.info`、
  `api:notification.send`、`api:tag.list`、`api:share.create`。
- **统一解析管线（CapabilityHubService）**：能力键校验 → 注册表/状态 → JSON Schema 校验 →
  权限收敛（实时授权 / 声明∩授权交集）→ 分发 → 审计；`invokeAgent` 也纳入统一 key/schema 校验。
- **平台 API 调用器（ApiCapabilityInvoker）**：代码内白名单路由（防 SSRF）、uid + 空间上下文透传、
  幂等重试、能力级熔断、`api:file.content.get` 文本类型白名单（≤1MiB）+ storage operation-token 流程。
- **安全强化**：`CapabilityKeyValidator`（能力键防注入）、`CapabilitySchemaValidator`（参数防注入）、
  `SimpleCapabilityBreaker`（熔断）、`InternalServiceFilter`（服务身份认证）。
- **审计台账**：`pcd_capability_audit` 表 + `CapabilityAuditMapper`（参数摘要不落敏感值，失败不阻断）。
- **测试**：新增 6 个测试类（Key/Schema/Breaker/Invoker/HubExt/WebTest），全量 `./gradlew test` 通过。

## [0.1.0-SNAPSHOT] - 基线

工作流定义、DSL 校验、版本/发布、执行记录、能力发现与市场基础能力。
