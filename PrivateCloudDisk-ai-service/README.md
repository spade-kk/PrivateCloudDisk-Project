# Cloud AI Agent Service

`cloud-ai-agent` 是 PrivateCloudDisk 的企业级 AI Agent Runtime。它通过 OpenAI-compatible `AsyncOpenAI` Adapter 协调模型、会话、SSE、工具调用与审批；它**不**是旧版本地 OCR/视觉模型 Worker，也不直接访问数据库、文件系统、对象存储或业务微服务。

## Security boundary

所有企业资产操作唯一经过 Workflow Service 的 Capability Hub：

```text
authenticated user -> Gateway -> cloud-ai-agent -> Capability Hub -> registered capability
```

Gateway 对传给本服务的用户/空间上下文签名。Agent 使用静态 Tool Registry 将模型工具映射到已注册 capability，并传递可信用户/空间上下文；Capability Hub 负责最终权限校验、参数 Schema、幂等、路由白名单和审计。`file.summary`、`file.compare`、`space.capacity`、`space.trend` 仅由受限读取能力组合，不绕过 Hub，也不提供写入权限。模型、浏览器和用户输入均不能提供内部 URL、权限集合或服务间 token。

## Local development

```bash
cp .env.example .env
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
pytest -q
```

Development requires an explicit trusted identity test header signature unless `AI_AGENT_ALLOW_UNSIGNED_IDENTITY=true` is deliberately set. That escape hatch is rejected in production.

## Real LLM compatibility test

The default test suite is offline. An opt-in integration test exercises the production
`OpenAICompatibleProvider` against the approved `MiniMax` endpoint at
`https://llm-api.arkcat.cn/v1`. It reads the credential exclusively from the existing
process environment variable `REMOTE_STUDIO_AUTH_TOKEN`; the test never prints or
persists the value.

```bash
RUN_LIVE_LLM_TESTS=1 PYTHONPATH=. .venv/bin/pytest -m live_llm -q
```

Do not put `REMOTE_STUDIO_AUTH_TOKEN` in `.env.example`, Docker Compose, test fixtures,
screenshots, build output or source control. CI must inject it only through its secret
store and run this marker in a separately approved integration job.

## API

- `GET /health`, `GET /ready`
- `GET|POST /api/v1/ai/conversations`
- `GET|PATCH|DELETE /api/v1/ai/conversations/{conversationId}`
- `GET /api/v1/ai/conversations/{conversationId}/messages`
- `POST /api/v1/ai/conversations/{conversationId}/runs` (SSE by default)
- `GET /api/v1/ai/runs/{runId}` and `GET /api/v1/ai/runs/{runId}/task` (tenant-bound run state and recoverable task document)
- `POST /api/v1/ai/runs/{runId}/cancel`
- `POST /api/v1/ai/runs/{runId}/approval`
- `POST /api/v1/ai/runs/{runId}/resume`（只消费服务端绑定的审批上下文，SSE）
- `GET /metrics`（仅内部监控抓取）

## Capability Hub 故障排查

`AI_AGENT_CAPABILITY_HUB_URL` 必须与运行拓扑匹配：Compose 容器内使用
`http://workflow-service-backend:8087`，宿主机开发进程只有在 Workflow Service 发布本机 8087
端口时才使用 `http://127.0.0.1:8087`。容器内使用 `localhost` 会指向 Agent 容器自身，通常表现为
连接失败或代理 502。

Workflow Service 返回 HTTP 200 并不代表能力成功。能力结果位于 `data.success`：当
`data.success=false` 且 `errorCode=WF-CAPABILITY-DATAPLANE-UNAVAILABLE` 时，说明 Hub 已完成
能力键、Schema、权限和幂等处理，失败发生在 Platform 数据面；请使用 `X-Request-Id` 检查
`/business/internal/capability/files/list`、用户 UUID、空间上下文和根目录。客户端会将 502/503
和非 JSON 网关响应归一化为可重试的 `AI-CAPABILITY-UNAVAILABLE`，不会抛出二次异常或打印请求 payload。

The Web execution view consumes structured **Agent Task SSE V2** events:
`agent_task_start`, `thinking_*`, `context_*`, `plan_created`,
`plan_item_update`, `tool_call_start`, `tool_call_end`, `tool_call_error`,
`output`, `summary` and `task_completed|task_failed|task_cancelled`. Every event
contains server-generated `task_id/run_id`, `sequence` and `timestamp`. The Agent
uses the selected LLM to generate a user-visible JSON plan, projects each V2 event
into a Redis task snapshot, and lets a refreshed browser recover the document through
`GET /api/v1/ai/runs/{runId}/task` without replaying a capability invocation.

The task UI renders only `tool_call_end.output_data`, never a raw tool-result
wrapper. Its “thinking” block is a short, user-visible execution rationale, not a
private chain-of-thought. V1 event names remain migration-only for non-Web consumers.
See [the task execution UI contract](../docs/AI_AGENT_TASK_EXECUTION_UI.md) and
[`../docs/CLOUD_AI_AGENT_SERVICE_DESIGN.md`](../docs/CLOUD_AI_AGENT_SERVICE_DESIGN.md)
for the complete service contract, deployment, migration, and threat model.
