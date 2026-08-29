# Cloud AI Agent Service API 与 SSE 协议

> **版本：2.0 · 2026-08-25**  
> 网关公共前缀：`/api/v1/ai`；Agent 容器内部前缀相同。浏览器只携带现有登录凭据和可选 `X-Space-Id`，**不得**构造 `X-PCD-*` 可信身份头。

## 调用前提

Gateway 完成 JWT/会话认证后，剥离浏览器伪造的 `X-PCD-*` 头，再为 `/api/v1/ai/**` 生成带 HMAC 的用户、空间、请求 ID 和时间戳。AI 服务验证签名、时间窗和请求路径后才处理请求。无签名或签名过期返回 `401`；Gateway 未配置签名密钥时，AI 路由返回 `503`，不会影响其他已有路由。

所有会话、run、审批读取都以签名后的 `user_id + space_id` 为范围。会话 ID 和 run ID 单独泄漏并不授予访问权。

## 会话 API

| Method | Path | Body / Query | 说明 |
| --- | --- | --- | --- |
| `GET` | `/conversations?limit=50` | `limit: 1..100` | 当前用户/空间的未归档会话 |
| `POST` | `/conversations` | `title?`, `model?` | 新建会话，返回 `201` |
| `GET` | `/conversations/{id}` | — | 读取同一租户会话 |
| `PATCH` | `/conversations/{id}` | `title?`, `archived?` | 重命名或归档 |
| `DELETE` | `/conversations/{id}` | — | 删除会话及消息 |
| `GET` | `/conversations/{id}/messages?offset=0&limit=100` | 分页 | 返回持久化消息；不返回原始 provider 请求 |
| `GET` | `/models` | — | 安全模型视图（`id`, `provider`），不返回 base URL 或密钥 |

## 创建 Agent Run

`POST /conversations/{conversationId}/runs`

请求体：

```json
{
  "message": "找出本空间的合同并概括主要风险",
  "model": "gpt-4.1-mini",
  "mode": "agent",
  "stream": true,
  "attachments": [
    {"file_id": "f0b1c2d3-1111-2222-3333-444455556666", "name": "合同.pdf", "type": "application/pdf"}
  ]
}
```

- `mode=agent`：模型可从静态 Tool Registry 选择已注册工具。
- `mode=api`：不传工具定义，适用于纯模型回答；它仍使用同一身份、限流和脱敏边界。
- `attachments` 只能是文件引用，不得内嵌文件内容、URL、绝对路径或用户指定服务地址。
- 运行创建受 `AI_AGENT_RUN_RATE_LIMIT_PER_MINUTE` 限制。超过限制返回 `429`，附 `Retry-After` 和 `X-RateLimit-Remaining`。
- `Accept: text/event-stream` 或 `stream=true` 返回 SSE；否则返回聚合的安全事件列表。

### SSE 格式

每个事件均是：

```text
id: 19
event: tool_call_end
data: {"task_id":"run-123","run_id":"run-123","sequence":19,"timestamp":"2026-08-25T09:10:11.012Z","block_id":"tool-call-7","tool_name":"file.read","status":"completed","output_data":{"content":"已脱敏内容"},"duration_ms":18}

```

### Agent Task 事件（V2，Web 默认契约）

每个 V2 业务事件都有服务端生成的 `task_id`（等同 `run_id`）、`run_id`、单调 `sequence` 和 UTC `timestamp`。`Thinking` 事件只能携带用户可见的执行依据，**绝不**携带或伪造模型私有 chain-of-thought。

| 事件 | 关键字段 | 前端任务块行为 |
| --- | --- | --- |
| `agent_task_start` | `task_id`, `conversation_id`, `user_request`, `model`, `schema_version` | 创建 `AgentTask`。 |
| `thinking_start/delta/end` | `block_id`, `delta?` | 维护可折叠的高层执行依据块。 |
| `context_start/item/end` | `block_id`, `context_summary?`, `item?` | 维护会话、附件和已验证观察的上下文列表。 |
| `plan_created` | `block_id`, `plan_items`, `source` | 渲染模型动态生成的计划；`source=llm` 或显式降级 `request_fallback`。 |
| `plan_item_update` | `block_id`, `plan_item_id`, `status`, `details?` | 更新计划项状态。 |
| `tool_call_start` | `block_id`, `call_id`, `tool_name`, `command`, `input` | 添加可折叠工具调用块。 |
| `tool_call_end` | `block_id`, `status`, `output_data`, `duration_ms`, `message?` | **只**展示 `output_data`；可表示 `awaiting_approval`。 |
| `tool_call_error` | `block_id`, `message`, `code?`, `duration_ms` | 标记该工具失败，不显示下游堆栈。 |
| `output` | `block_id`, `output_text`, `format`, `delta` | 实时追加阶段文本/Markdown/JSON。 |
| `summary` | `block_id`, `summary_text`, `format` | 追加最终总结块。 |
| `task_completed/failed/cancelled` | `iterations?`, `tool_calls?`, `code?`, `message?` | 结束任务并更新状态。 |

`tool_call_end.output_data` 是工具结果的唯一 UI 数据源。客户端不得渲染旧 `ToolExecutionResult` 包装（例如 `success/output/errorCode`）或将其拼进 summary。`heartbeat` 仅用于连接保活；它不创建块，也不代表模型 token 或工具重试。

在网络断开时，客户端停止读取当前流并读取 `GET /runs/{runId}/task`；不得“重放”原始 POST，以避免重复工具执行。Task snapshot 是按已发送业务事件构建的展示投影，不承诺 SSE token 逐条重放。服务端 Redis 快照保留 `user_id` 仅用于授权；该字段不在浏览器 task DTO 中返回。

## 取消与审批恢复

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/runs/{runId}` | 读取运行状态与安全错误摘要 |
| `GET` | `/runs/{runId}/task` | 读取当前用户/空间可见的 V2 task snapshot；仅用于恢复，不会重放执行 |
| `POST` | `/runs/{runId}/cancel` | 写入 Redis 取消标记，返回 `202`；已终态时为幂等 no-op |
| `POST` | `/runs/{runId}/approval` | Body: `{"approved":true,"approval_token":"..."}`，只登记一次决定 |
| `POST` | `/runs/{runId}/resume` | SSE；只消费此前服务端保存的模型调用批次，恢复执行或安全结束 |

审批完整流程：

1. Agent 在执行批次前检测到 `workflow.execute` 或 `plugin.call`，不执行该批次，而以 `tool_call_end` 的 `status=awaiting_approval` 发送该工具块。
2. 用户阅读工具名和影响说明，向 `/approval` 提交布尔决定与短期随机 token。
3. 浏览器立即调用 `/resume`；它**不提交 capability key、工具参数、用户 ID 或空间 ID**。
4. Agent 从 Redis 取回与 run 绑定的原始调用和消息上下文。批准时仅对获批 call 打开内部审批门；拒绝时终止，且没有任何待审批工具被执行。
5. 恢复完成后服务发出 `tool_call_end`（仅 `output_data`）、`output`、`summary` 和任务终态。

审批 token 单次使用、15 分钟过期；重复、过期、跨用户或跨空间调用返回 `409`/`404`。高风险调用与同批次普通调用一律先暂停，避免出现“部分执行后才询问”。

## 浏览器 fetch-SSE 参考

前端使用现有受认证 `fetch`，不使用无法稳定附带 Authorization/空间头的裸 `EventSource`：

```ts
const response = await fetch('/api/v1/ai/conversations/' + id + '/runs', {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${token}`,
    'X-Space-Id': selectedSpaceId,
    Accept: 'text/event-stream',
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({ message, stream: true, mode: 'agent' }),
  signal: abortController.signal,
})
```

Gateway 才是可信身份签名方。代码不得从前端读取或传输 `AI_AGENT_IDENTITY_SHARED_SECRET`、`PCD_INTERNAL_SERVICE_TOKEN`、provider API key 或 Capability Hub 的内部 URL。

### 旧事件迁移

`status`、`plan`、`token`、`tool_call`、`tool_result`、`reflection`、`approval_required`、`done` 等 V1 名称在服务端模型中暂留给滚动发布期的非 Web 消费者。V2 Web 任务视图不从这些事件或普通 assistant 文本猜测过程；新消费者必须使用上述 V2 事件和 task snapshot。部署顺序应为 Agent Service → Web，历史 run 无 snapshot 时回退为普通最终消息。

## 探针与监控

- `GET /health`：进程存活。
- `GET /ready`：Redis 可达；失败返回 `503`。
- `GET /metrics`：仅容器内部抓取；含 HTTP、Agent terminal state、工具成败计数，**不**以用户、空间、提示词或文件 ID 作 metrics label。

完整安全边界见 [Cloud AI Agent 安全与运维](./AI_AGENT_SECURITY.md)，实现和迁移见 [Cloud AI Agent Runtime 设计](./CLOUD_AI_AGENT_SERVICE_DESIGN.md)。
