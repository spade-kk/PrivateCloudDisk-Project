# 第三方 Agent 接入 CloudFlow MCP Server

## 前提

- 使用平台管理员登记/批准的 OAuth 2.1 client，并获得面向 CloudFlow MCP resource 的 Access Token。
- MCP URL 始终为 `https://<gateway-host>/api/v1/mcp`，不要填写容器地址、Capability Hub 地址或
  带 Token 的 URL。
- Agent 只需要外部 `Authorization`（和可选 `X-Space-Id`）；不得传 `X-PCD-*`、Hub token、
  user/tenant/permission 等内部字段。

## 通用远程 MCP 配置形态

不同 Agent/IDE 对远程 MCP 配置的字段名和 OAuth UI 不同，以下是**概念性**配置，须按其已安装
版本的官方文档映射，而非复制为固定 CLI 参数：

```json
{
  "mcpServers": {
    "cloudflow": {
      "transport": "streamable-http",
      "url": "https://gateway.example.com/api/v1/mcp",
      "headers": {
        "Authorization": "Bearer ${CLOUDFLOW_MCP_ACCESS_TOKEN}",
        "X-Space-Id": "<optional-authorized-space-id>"
      }
    }
  }
}
```

将 token 交给 Agent 的安全存储/凭据管理器，不提交到 Git、工作流 YAML、截图、shell history 或
config 明文。正式 OAuth 集成应由 Agent 发起 Authorization Code + PKCE 流程；静态 header 仅适合
受控开发调试且必须使用短期 token。

## 推荐调用顺序

1. `initialize`（`protocolVersion: "2025-11-25"`）并保存 `Mcp-Session-Id`。
2. 发送 `notifications/initialized`。
3. `tools/list`，不要假定工具全集；不同用户、租户、空间返回的列表不同。
4. 严格按返回的 `inputSchema` 构造参数，再 `tools/call`。
5. 对 `isError=true` 读取用户安全的 error text；不要尝试以伪造 `user_id`/`space_id` 等参数绕过。
6. 在长时间调用时用 `notifications/cancelled` 取消等待；对超时按同一个 RPC ID 安全重试。

## Codex、Claude Code、Cursor 的使用原则

这三类客户端的核心差异是“是否已有 Remote Streamable HTTP + OAuth support，以及配置入口位置”，
而不是 CloudFlow 服务端协议。接入时均应验证：

| 检查项 | 期望结果 |
| --- | --- |
| OAuth metadata | 客户端能读取 Gateway 的 protected-resource metadata。 |
| 初始化 | 返回 MCP `2025-11-25` 与 Session header。 |
| 工具发现 | 只显示当前登录用户被授权的 `cloudflow.*` 工具。 |
| 参数模式 | 不出现 `user_id`、`tenant_id`、`space_id`、权限或内部地址。 |
| 调用 | 文件/空间/工作流实际由 Hub 执行，Hub 审计能按 request ID 找到记录。 |
| 失败 | 过期 token 返回 401；无权资源不泄漏敏感内容；Hub 不可用时无重复执行。 |

## 工具行为边界

- 可见工具是最小出口，不保证 Hub 内部全部能力均可见。
- 读文件仍受所在空间、目录与资源权限约束；搜索结果不构成读权限提升。
- 工作流启动继续受 Hub 的工作流权限、schema、审批/幂等策略控制。
- 任何涉及删除、用户管理、空间管理、插件安装的能力默认不对第三方 MCP 可见。

如需新增工具，请提交能力键、输入/输出 JSON Schema、权限、幂等性、数据分级、结果脱敏与 Agent
用途，依次经 Capability Hub 注册、MCP 导出策略、安全评审和端到端测试后发布。
