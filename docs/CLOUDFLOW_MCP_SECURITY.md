# CloudFlow MCP Server 安全与多租户隔离

## 信任边界

```text
Untrusted third-party Agent
  | Bearer token
Gateway: validate JWT, strip internal headers, sign immutable private-hop context
  | short-lived pcd-mcp-v1 HMAC
MCP Server: validate binding, adapt/limit/audit protocol
  | service token + trusted request body + W3C trace context
Capability Hub: final capability/schema/tenant/space/resource authorization and dispatch
```

MCP Server 不是第二个宽权限业务网关。它不接受任意 URL，不连接数据面，也不持久化用户
Token、资源或工具结果。

## 认证与身份绑定

- 外部请求由 Gateway 的 Bearer JWT 校验。Gateway 删除客户端输入的 `X-PCD-*`、`X-User-Id`
  等内部头之后才写入可信头。
- HMAC canonical input 为 `pcd-mcp-v1 + method + /mcp + requestId + RFC3339 timestamp + userId +
  tenantId + spaceId`；MCP 以常量时间比较并限制签名年龄。
- MCP 不转发 Bearer Token，Hub 只接受 MCP 的内部服务凭证与经签名的显式用户上下文。
- tenant 只可来自 JWT 已签名 claim，不能来自 HTTP header；无 tenant 的个人 Token 对需要
  tenant 作用域的能力由 Hub 采用拒绝优先策略。
- `X-Space-Id` 是可选上下文选择，用户仍必须有 Hub 侧空间成员和资源级权限。

## 授权与工具最小化

1. Hub 查询先过滤 ACTIVE、注册、权限和可用性策略。
2. MCP Adapter 再执行代码审核的外部导出 allowlist，默认拒绝管理/删除/安装能力。
3. 工具 JSON Schema 删除所有服务端字段；调用参数同样二次删除 snake_case/camelCase 的身份、
   tenant、space、permission、trace、execution、idempotency 字段。
4. Hub 在调用时再次做实时用户、租户、空间、资源、Schema、能力状态和策略校验，防止缓存或
   客户端上下文造成 TOCTOU 越权。
5. Platform 与 Plugin Runtime 的内部能力模型、地址、storage node、权限上下文不返回 MCP。

## 审计、隐私与日志

- 每个 `tools/call` 由 Hub 持久化用户、空间、能力键、参数摘要、结果状态、错误码、耗时、
  request/trace ID 和调用者 `cloudflow-mcp-server`。
- `initialize`、发现、资源、提示、取消、限流等协议操作也以最佳努力方式进入同一个 Hub
  审计管线。
- Token、Authorization、原始工具输出、密码、敏感字段不写入日志；参数摘要由 Hub/Recorder
  统一截断和脱敏。
- 审计查询与导出应继续走平台管理后台权限模型，而不是通过 MCP 暴露。

## 抗滥用与资源控制

| 风险 | 控制 |
| --- | --- |
| 重放/篡改内部身份 | HMAC 请求绑定、RFC3339 短期窗口、常量时间比较、Gateway 删除伪造头。 |
| 横向/纵向越权 | 用户/tenant/space 缓存隔离，Hub 最终资源授权，默认不导出管理工具。 |
| 参数/能力键注入 | 固定 Hub base URL、审核映射、Hub key validator、JSON Schema、长度/体积限制。 |
| SSRF | Go 服务仅有固定 `MCP_CAPABILITY_HUB_URL`；Agent 不能传 destination URL。 |
| 重复副作用 | 确定性 MCP 幂等键 + Hub 调用台账。 |
| DoS | HTTP body cap、响应 cap、请求超时、并发 semaphore、每用户固定窗口限流、SSE 断线清理。 |
| 租户缓存串扰 | 缓存 key 固定含 user/tenant/space；缓存仅保存已筛选工具页。 |
| 观测泄密 | 仅传播 W3C traceparent；身份不进入 baggage，OTLP 导出端点由配置固定。 |

## OAuth 上线要求

源码可提供 OAuth Protected Resource Metadata，并已经以 Gateway Bearer JWT + 内部 HMAC 保证当前
链路认证。然而审计范围内没有可运行的 OAuth 2.1 Authorization Server、PKCE、动态客户端注册、
scope/audience 签发与吊销服务。因此生产发布必须：

1. 配置 `MCP_OAUTH_AUTHORIZATION_SERVERS` 为真实授权服务器 metadata issuer。
2. 在 Gateway 配置 `MCP_PUBLIC_BASE_URL`，确保未认证 challenge 使用固定 canonical HTTPS URL，而不从
   `Host`/`X-Forwarded-Host` 反射 URL。
3. 让该 issuer 对 MCP resource 签发受众/范围正确的短期 access token，完成 PKCE 与回收策略；并把
   `MCP_REQUIRED_AUDIENCE` 和 `MCP_REQUIRED_SCOPE` 配置到 Gateway，使 `/api/v1/mcp` 只接受该 token。
4. 使用 TLS、轮换 HMAC/Hub service token、配置告警并完成真实集成/渗透测试。

这项前置条件被记录为上线门禁，而非用空配置或模拟 endpoint 伪装为已完成的 OAuth 流程。
