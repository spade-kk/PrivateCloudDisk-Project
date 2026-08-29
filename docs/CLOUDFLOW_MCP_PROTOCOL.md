# CloudFlow MCP Server 协议与接入契约

## 1. 协议基线

服务实现 MCP stable `2025-11-25` 的 JSON-RPC 2.0 工具协议，并采用 Streamable HTTP：

- 公网资源：`https://<gateway-host>/api/v1/mcp`。
- 服务内私网资源：`http://cloudflow-mcp-server:8093/mcp`，禁止第三方 Agent 直连。
- 所有 `POST` 都是一个 JSON-RPC 消息；`Accept` 必须同时包含 `application/json` 与
  `text/event-stream`。服务会根据优先级返回 JSON 或 SSE。
- 初始化后，客户端保存服务返回的 `Mcp-Session-Id` 并在后续请求中带回；会话默认 30 分钟。
- `GET /mcp` 是可选的 keepalive SSE 流，必须携带有效 Session；业务请求仍使用 POST。
- 受保护资源元数据：
  `GET /api/v1/.well-known/oauth-protected-resource/mcp`，无需 Bearer。

不实现历史 HTTP+SSE 双端点模式，也不实现本地 stdio 模式；本服务是纯服务端入口。

## 2. HTTP 头

| 方向 | 头 | 说明 |
| --- | --- | --- |
| Agent → Gateway | `Authorization: Bearer <access-token>` | 唯一外部认证凭证；不得放入 URL、query、日志。 |
| Agent → Gateway | `Content-Type: application/json` | 所有 POST 强制。 |
| Agent → Gateway | `Accept: application/json, text/event-stream` | 发现/调用必填，两种返回类型均需接受。 |
| Agent → Gateway | `Mcp-Session-Id` | `initialize` 后服务端发放，后续请求必须携带。 |
| Agent → Gateway | `X-Space-Id`（可选） | 请求空间上下文；Hub 仍做成员和资源权限校验。 |
| Gateway → MCP | `X-PCD-*` | 内部短期 HMAC 上下文；客户端发送的同名头已在 Gateway 删除，Agent 不应构造。 |
| MCP → Hub | `X-PCD-Service-Token` | 服务端内部凭证，仅容器/集群 Secret 持有。 |

未认证时返回 `401` 和 `WWW-Authenticate: Bearer ... resource_metadata="..."`，metadata URL
固定指向 Gateway 公网路径而非容器内部地址。

## 3. 初始化

```http
POST /api/v1/mcp HTTP/1.1
Authorization: Bearer <access-token>
Content-Type: application/json
Accept: application/json, text/event-stream

{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-11-25",
    "capabilities": {},
    "clientInfo": { "name": "external-agent", "version": "1.0" }
  }
}
```

成功响应带 `Mcp-Session-Id`，并返回 `tools`、`resources`、`prompts` 能力声明和服务说明。
随后发送无 ID 的 `notifications/initialized`。`ping` 返回空对象。

## 4. 发现工具：`tools/list`

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": { "cursor": "" }
}
```

结果的 `tools` 只包含同一 `(user, tenant, space)` 下 ACTIVE、已注册、Capability Hub 当前可见、
并且符合 CloudFlow 外部导出策略的条目。若存在更多结果，`nextCursor` 是不透明游标：

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [{
      "name": "cloudflow.file.search",
      "description": "在当前授权空间内搜索文件元数据。",
      "inputSchema": {
        "type": "object",
        "properties": { "keyword": { "type": "string" } },
        "required": ["keyword"]
      }
    }],
    "nextCursor": "..."
  }
}
```

`user_id`、`tenant_id`、`space_id`、权限、trace、调用台账等字段不会出现在 schema 中，即便
Agent 主动传入也会在 Adapter 层删除。

## 5. 调用工具：`tools/call`

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "cloudflow.file.search",
    "arguments": { "keyword": "roadmap" }
  }
}
```

成功结果：

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [{ "type": "text", "text": "{\"items\":[...]}" }],
    "structuredContent": { "items": [] }
  }
}
```

经过 Hub 的业务拒绝使用 `result.isError=true`，而不是将内部异常细节暴露给 Agent。无效参数、
未知方法、过期 Session 和临时 Hub 故障分别映射为标准 JSON-RPC/HTTP 错误。执行调用同时带有
确定性幂等键；Agent 重试同一会话/请求 ID 不会重复执行可产生副作用的能力。

## 6. 资源、提示与取消

| 方法 | 当前实现 | 数据边界 |
| --- | --- | --- |
| `resources/list` / `resources/read` | 静态 `cloudflow://server/policy` | 仅说明外部导出/身份规则，无租户或业务数据。 |
| `prompts/list` / `prompts/get` | `safe-file-search` | 模板提示 Agent 限定在当前授权空间。 |
| `notifications/cancelled` | 取消仍在本进程等待 Hub 的 HTTP 调用 | 已到达 Hub 的执行依赖 Hub 幂等台账，不能以取消绕过审计。 |

## 7. SSE 帧与错误

当 `Accept` 优先选择 `text/event-stream` 时，响应是单个完整 SSE frame：

```text
id: response-...
event: message
data: {"jsonrpc":"2.0","id":3,"result":{...}}

```

服务器不会在 `data:` 与 `event:` 之间插入空行，避免严格客户端把一个响应拆为两个事件。
接口总是添加 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY` 和 `no-store`
错误缓存策略。

完整协议依据和传输兼容性以 MCP 官方规范为准；CloudFlow 的稳定兼容版本不跟随尚未发布的草案
语义自动变更。
