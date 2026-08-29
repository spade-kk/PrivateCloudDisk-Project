# Cloud AI Agent Service 企业级智能体运行时设计

> **状态：实施基线**  
> **版本：1.0**  
> **更新时间：2026-08-24**  
> **适用范围：PrivateCloudDisk `cloud-ai-agent` 服务、Capability Hub、CloudFlow、Plugin Runtime 与 Web AI 助手。**

## 1. 定位与不可突破的边界

Cloud AI Agent Service 是面向企业数字资产的 Agent Runtime：它协调模型推理、会话、工具调用、审批和流式交互；它不是文件服务、数据库代理或模型厂商 SDK 的薄封装。其工具域是文件、空间、CloudFlow 工作流和插件能力，而不是宿主机代码文件系统。

以下边界为强制约束：

1. AI 服务**不得**连接业务数据库、对象存储、共享上传目录、文件系统、搜索引擎，亦不得直接请求文件服务、空间服务、CloudFlow Runtime 或 Plugin Runtime。
2. 一切企业资产操作均经由 Workflow Service 内的 Capability Hub：`AI Agent -> Capability Hub -> allowlisted capability -> business service / plugin runtime / CloudFlow runtime`。
3. AI 服务以经过网关认证的最终用户和空间上下文发起能力调用；Capability Hub 保留最终资源授权、参数 Schema 验证、权限交集、幂等与审计职责。
4. 模型只能看到注册工具、已授权的工具结果和脱敏后的会话上下文。模型不得提供权限、服务地址、能力路由或自由 URL。
5. Agent 可以展示简短的执行计划、工具状态和可验证的观察结果，但不暴露模型私有思维链。

CloudFlow 当前的可执行源码是 `.flow` / CloudFlow DSL，IR 为 `workflow.cloudflow.io/v1`。历史 YAML 工作流定义不再作为新的 Agent 输出格式；任何遗留 YAML 需求必须走明确迁移流程，不能重新引入一条未校验的执行面。

## 2. 架构与调用链

```text
Web / Desktop / Mobile AI Assistant
  └─ HTTPS + SSE (identity established by Gateway)
       └─ Cloud AI Agent Service (FastAPI)
            ├─ Conversation and cancellation state (Redis only)
            ├─ Prompt / policy / approval guard
            ├─ Agent Runtime (plan -> act -> observe -> refine)
            ├─ LLM Adapter (AsyncOpenAI; OpenAI-compatible protocol)
            └─ Tool Registry
                 └─ Capability Hub /internal/v1/capabilities/invoke
                      ├─ File / space capabilities
                      ├─ CloudFlow workflow capabilities
                      └─ Plugin capabilities -> Plugin Runtime
```

服务仅保存小型会话元数据、消息、运行事件、取消标记、审批上下文与限流计数至 Redis。模型 usage 字段保留在消息契约中，Provider 目前尚未将 usage 作为持久化成本账本；生产成本核算须由受控审计/可观测性链路补齐。大附件、文件内容、向量检索和业务资产的读取仍必须由已注册能力返回经过限制及脱敏的结果。Redis 是可水平扩展的共享状态层而非业务资产的旁路存储。

可编辑架构图位于 [Cloud AI Agent 容器与信任边界](./architecture/cloud-ai-agent-runtime.drawio)、[SSE/Tool 调用兼容时序](./architecture/cloud-ai-agent-sse-sequence.drawio) 和 [Agent Task Execution V2 时序](./architecture/cloud-ai-agent-task-execution.drawio)。

## 3. 模块与项目结构

```text
PrivateCloudDisk-ai-service/
├── app/
│   ├── api/                 # HTTP/SSE endpoints and authenticated request context
│   ├── core/                # settings, identity verification, redaction, observability
│   ├── domain/              # stable request/message/run/event contracts
│   ├── providers/           # LLMProvider and AsyncOpenAI-compatible adapters
│   ├── runtime/             # agent loop, prompt policy, run/cancellation state
│   ├── tools/               # static Tool Registry and Capability Hub client
│   └── memory/              # Redis conversation repository
├── tests/                   # unit and HTTP/SSE integration tests
├── Dockerfile
├── requirements.txt
├── .env.example
└── README.md
```

模块间只通过明确的 domain contract 协作。工具实现不能导入数据层、文件 I/O、SDK 的业务服务客户端或任意 HTTP URL 客户端；唯一企业内部网络出口为 `CapabilityHubClient` 的固定 base URL。

## 4. 身份、租户和服务间信任

### 4.1 用户请求身份

Gateway 在完成用户认证后向 AI 服务注入以下可信请求上下文，并以共享密钥 HMAC 签名：

| Header | 说明 |
| --- | --- |
| `X-PCD-User-Id` | 已认证用户 ID |
| `X-PCD-Space-Id` | 可选、当前空间上下文 |
| `X-PCD-Request-Id` | 请求追踪 ID |
| `X-PCD-Identity-Timestamp` | Unix 秒；服务拒绝过期重放 |
| `X-PCD-Identity-Signature` | `HMAC-SHA256(method + path + request-id + timestamp + user-id + space-id)` |

AI 服务拒绝缺失、过期或签名错误的上下文，绝不信任浏览器自行提交的 `userId`、`spaceId`。生产环境不允许为空的 identity secret；开发环境必须显式打开不安全开关，且该开关默认关闭。

### 4.2 到 Capability Hub 的调用

`ToolRegistry` 把面向模型的工具名映射到服务器静态 capability key、输入 Schema、超时、风险级别和最小权限集合。模型与客户端只可提供工具参数，不能选择内部 URL、权限集合或用户身份。

每次调用使用 Capability Hub 已有的 `AgentCapabilityInvocation` 契约：

```json
{
  "capabilityKey": "...",
  "executionId": "<agent run UUID>",
  "stepId": "ai:<turn>:<tool>",
  "attempt": 1,
  "userId": "<gateway identity>",
  "spaceId": "<gateway identity or null>",
  "input": { "...": "tool parameters" },
  "declaredPermissions": ["server-static"],
  "grantedPermissions": ["server-static"],
  "traceId": "<request id>",
  "idempotencyKey": "<stable derived key>"
}
```

静态权限只是 Agent 所能声明的最小上限；Capability Hub 仍对用户、空间、目标资源、注册能力和 input Schema 执行最终检查。Capability Hub 的 `X-PCD-Service-Token` 仅由部署配置注入，永不暴露给客户端或模型。

## 5. LLM Adapter

所有模型统一采用 OpenAI-compatible client，不为每家模型自建传输协议：

```python
from openai import AsyncOpenAI

client = AsyncOpenAI(base_url=provider.base_url, api_key=provider.api_key)
stream = await client.chat.completions.create(
    model=provider.model,
    messages=messages,
    tools=tool_definitions,
    stream=True,
)
```

`LLMProvider` 当前提供 `stream_chat`、统一响应映射和可用性检查插槽。OpenAI、DeepSeek、Ollama、vLLM、LMDeploy 等均通过同一适配器的 `base_url/api_key/model/timeout` 配置接入；Claude 保留 adapter 插槽但不伪造 OpenAI 协议兼容性。Provider Router 按部署允许模型和 fallback 顺序选择模型；连接池并发由 `AsyncOpenAI` 与服务端配置约束。空间/用户级模型路由、provider 健康探测与密钥轮换应通过受控配置中心在此边界增量接入，不能由浏览器请求携带。

密钥仅来自部署密钥管理/环境注入，不写进会话、日志、响应或前端配置。用户无法提交任意 provider base URL，以避免 SSRF。

## 6. Agent Runtime

一次运行按如下状态机执行：

```text
CREATED -> PLANNING -> GENERATING <-> CALLING_TOOL <-> OBSERVING -> REFLECTING
    -> PLANNING/GENERATING -> AWAITING_APPROVAL -> GENERATING
    -> COMPLETED | FAILED | CANCELLED | TIMED_OUT
```

1. 校验请求和用户上下文，加载当前用户/空间会话短期记忆。
2. 组装不可覆盖的 system policy、可配置提示模板、有限历史、可用工具定义和用户输入。
3. 收集当前会话和安全附件引用后，先以无工具的模型请求生成 2–6 项、面向用户的 JSON 展示计划；解析通过后发出 `plan_created`。Provider/JSON 异常时只允许生成含当前请求的、带 `request_fallback` 标记的最小计划，不能复用固定 UI 文案。
4. 流式调用主模型；将增量文本转换为结构化 `output` 事件，并收集 tool calls。
5. 对独立的 tool calls 并发执行；通过 Tool Registry 进行 JSON Schema、风险与审批检查，再由 Capability Hub 执行。
6. 将脱敏结果作为 `tool` 消息回馈模型，进入 `REFLECTING`，发出用户可见的高层依据/调整事件，再继续循环，直到生成最终回答、到达迭代/时间/token 上限或用户取消。
7. 写入普通会话最终消息，同时按 V2 事件投影 Redis `AgentTaskSnapshot`，发出 `summary` 和 `task_completed|task_failed|task_cancelled`。

默认保护：最大 8 个 Agent 轮次、单次运行 120 秒、每个可重试工具最多 3 次、单工具 30 秒、每轮最多 4 个并发工具调用，全部可由受控配置收紧。重试只对 Capability Hub 返回的 `retryable=true` 结果生效，并产生 `tool_retry` 事件；不可重试的权限/参数错误不会被盲目重复。删除、改权限、写文件、执行高风险工作流等能力被标记为 `approval_required`；Agent 必须发出审批事件，只有用户批准的短期、绑定参数的 approval token 才可继续。可逆动作应由对应 capability / CloudFlow 提供补偿或版本恢复，不允许 AI 服务私下回滚数据。

## 7. Tool Registry

初始工具集（实际 capability key 在部署时由注册表配置并由 Capability Hub 校验）：

| Agent tool | 域 | 风险 | 作用 |
| --- | --- | --- | --- |
| `file.search`, `file.read`, `file.metadata`, `file.list`, `file.summary`, `file.compare` | 文件 | read | 已注册的搜索、受限内容、元数据、列表，以及由受限读取组合出的摘要/比较能力 |
| `space.info`, `space.members`, `space.capacity`, `space.trend`, `user.info` | 空间/用户 | read | 已注册的空间查询和基于文件列表的容量/趋势分析；身份与空间权限仍由 Hub 校验 |
| `workflow.list`, `workflow.status`, `workflow.validate` | CloudFlow | read/validate | 经 Hub 调用 `.flow` 列表、状态与编译校验 |
| `workflow.execute` | CloudFlow | approval | 仅对已发布工作流，经显式审批后执行 |
| `plugin.call` | 插件 | approval | 仅对 `plugin:` 注册能力，经显式审批后调用 |

`file.preview`、`file.write`、空间写操作、空间列表和插件列表**不能被模型伪造为已存在工具**：它们需先在 Capability Hub 增加受审计的 capability、Schema、权限/风险级别和数据面实现，才可加入 Registry。`file.summary`、`file.compare`、`space.capacity`、`space.trend` 当前只是受限读取的服务端组合工具，不代表已获得文件写入或空间写权限。这样不会把“设计清单”误当成未实现的安全接口。

工具参数必须采用 JSON Schema 验证，输出为大小受限、脱敏、可供模型解析的 JSON。绝对路径、访问 token、密码、Cookie、密钥、内部服务地址和任意二进制内容一律从输出与日志移除。运行时不使用模型生成的 DSL 直接执行：先让 CloudFlow 编译/校验 capability 返回成功，再经审批（如需要）调用执行 capability。

## 8. SSE 与 API

所有生成型交互均使用 SSE；浏览器可以用 `fetch` POST 加 `ReadableStream` 消费 SSE，以便携带现有认证头和 `AbortController`。

| API | 方法 | 描述 |
| --- | --- | --- |
| `/health` | GET | 活性探针，不泄漏依赖凭据 |
| `/ready` | GET | Redis 与 Provider 配置可用性探针 |
| `/api/v1/ai/conversations` | GET/POST | 会话列表/新建会话 |
| `/api/v1/ai/conversations/{id}` | GET/PATCH/DELETE | 会话读取、重命名、归档/删除 |
| `/api/v1/ai/conversations/{id}/messages` | GET | 分页历史 |
| `/api/v1/ai/conversations/{id}/runs` | POST | 创建 Agent run，`Accept: text/event-stream` 时流式返回 |
| `/api/v1/ai/runs/{id}` | GET | 查询长任务状态 |
| `/api/v1/ai/runs/{id}/task` | GET | 读取当前用户/空间可见的可恢复 Task 执行文档，不重放 Agent 或工具 |
| `/api/v1/ai/runs/{id}/cancel` | POST | 取消生成或等待状态 |
| `/api/v1/ai/runs/{id}/approval` | POST | 批准/拒绝绑定的高风险操作 |
| `/api/v1/ai/runs/{id}/resume` | POST | 只执行 Redis 保存、与该 run 绑定的审批结果；SSE 返回观察和最终摘要 |
| `/api/v1/ai/models` | GET | 用户可用模型的安全视图 |

SSE 事件统一采用 `event: <type>\ndata: <JSON>\n\n`。Web 的默认契约为 Task V2：`agent_task_start`、`thinking_start/delta/end`、`context_start/item/end`、`plan_created`、`plan_item_update`、`tool_call_start/end/error`、`output`、`summary`、`task_completed/failed/cancelled`。每项 data 都由服务端包含 `task_id`（等同 `run_id`）、`run_id`、UTC `timestamp` 和单调 `sequence`，SSE `id` 同样使用 sequence。`thinking_*` 仅描述“已观察并重新评估/调整”的用户可见依据，不暴露私有思维链；`tool_call_end` 的 UI 可见结果只有脱敏 `output_data`。当 Provider/工具在 `AI_AGENT_SSE_HEARTBEAT_SECONDS` 时间内尚未产生事件时，服务只发送不会重放业务动作的 heartbeat。断线时客户端应读取 `/runs/{id}/task` 和消息列表恢复，不应重放可能已完成的工具调用。`task_failed` 和工具失败不包含堆栈、密钥或下游内部细节；V1 名称仅为滚动发布期非 Web 消费者兼容。

## 9. 会话、记忆与可观测性

Redis key 带 tenant scope：`ai:conversation:{user}:{space}:{conversation}`、`ai:run:{run}`、`ai:task:{run}`、`ai:cancel:{run}`、`ai:rate:{scope}`。`ai:task:{run}` 是根据已发出的 V2 业务事件生成的展示快照，包含块、计划进度、工具的 `output_data` 和终态；它不包含 provider 原始请求、私有思维、服务凭据或原始工具包装。访问时总是先按经过认证的用户/空间检查 run，禁止仅凭 conversation/run ID 读取。短期记忆目前按条数与字符预算截断，并保留系统策略、最近上下文以及“旧上下文已压缩、需重新调用工具核实事实”的标记；这避免把过期事实当作权威信息。基于摘要模型的可审计压缩、长期 RAG、嵌入、文件索引属于后续受控能力，AI 服务不得私建旁路索引或拉取未授权资产。

结构化日志、metrics 和 trace 都必须包含 request/run/conversation 的关联 ID；当前 Prometheus `/metrics` 提供无租户标签的 HTTP、Agent terminal state 和工具结果指标。模型审计记录模型、provider、耗时、token usage 和状态，工具审计记录工具名、capability key、参数摘要、用户/空间、状态和耗时。原始 prompt、文件正文、API key、Authorization、service token 永不写入日志。审计可异步投递，但不得影响用户权限判定。

## 10. 前端体验与安全

Web 路由为 `/app/ai`，提供 ChatGPT/Codex 风格的会话侧栏和 `AgentTaskView`。一条 Agent 消息以 Markdown 文档式块序列展示：上下文、动态计划、高层执行依据、可折叠工具调用、阶段输出、最终总结；普通聊天历史保持普通消息显示。工具块命令/结果可复制，结果只读取 `output_data`；任务支持 Markdown/JSON 导出、全部展开/折叠、明暗主题和移动端默认折叠。完整交互和组件契约见 [Agent Task Execution UI V2](./AI_AGENT_TASK_EXECUTION_UI.md)。桌面支持页面、抽屉和悬浮入口；移动端切换为全屏会话优先布局。前端不保存 provider API key、Capability Hub token 或用户可伪造的身份头。

SSE 客户端通过当前 API 认证和 fetch stream 消费，出现网络中断时读取 task snapshot 恢复已发送块，不重新提交 run；取消使用明确 API 而不是关闭页面即假定任务已停止。任何审批操作需要用户可见的工具名、影响范围和参数摘要。

## 11. 部署与配置

Docker Compose 中的新服务名为 `cloud-ai-agent`。它只依赖 Redis、Gateway 入口和 Workflow Service 的 Capability Hub；不挂载 `Uploads`、模型目录、宿主机目录、Docker socket，也不配置 MySQL/MinIO/OpenSearch/RabbitMQ 直连凭据。

关键配置：

```dotenv
AI_AGENT_REDIS_URL=redis://redis:6379/5
AI_AGENT_CAPABILITY_HUB_URL=http://workflow-service:8087
AI_AGENT_INTERNAL_SERVICE_TOKEN=<secret>
AI_AGENT_IDENTITY_SHARED_SECRET=<gateway-to-agent-secret>
AI_AGENT_LLM_BASE_URL=https://api.openai.com/v1
AI_AGENT_LLM_API_KEY=<secret>
AI_AGENT_LLM_MODEL=<configured-model>
AI_AGENT_ALLOWED_ORIGINS=https://<gateway-host>
```

生产环境由 Gateway 路由 `/api/v1/ai/**`，并负责用户认证、可信上下文签名、请求大小限制、速率限制和 TLS。服务具备 health/readiness、SIGTERM 优雅关闭、超时与连接池约束；模型与工具配置通过受控配置刷新而非让请求修改。

## 12. 迁移、验收和非目标

旧 `PrivateCloudDisk-ai-service` 是一个直接访问 MySQL、RabbitMQ、MinIO、OpenSearch、共享文件目录及本地 ML 模型的异步视觉/NLP Worker。这些职责与新安全边界冲突，故按本设计整目录删除并重建；旧 API、worker、数据库 schema、模型目录、共享目录挂载与文件事件消费均不迁移。需要保留的具体业务模型应作为受审计 Plugin / Capability 单独迁移，而非藏在 Agent 服务中。

验收至少覆盖：身份头签名与跨租户隔离；静态工具映射和 Capability Hub 用户授权；OpenAI-compatible Task V2 stream；模型动态计划解析和降级标记；单/多工具循环、取消、审批、限流、fallback；task snapshot 恢复且不重放；CloudFlow `.flow` 校验后执行；插件能力调用；前端 SSE/UI 的 `output_data` 边界；Docker Compose 无直接资产挂载；安全扫描确认无数据库、文件、对象存储或业务服务客户端。

非目标：在 AI 服务中部署模型权重、直接读取业务数据、暴露 Capability Hub 内部 endpoint 给浏览器、存储原始敏感文件、输出私有思维链、绕开工作流/插件审计。

## 13. 后续演进

长期任务的后台队列/重启恢复、空间共享记忆、RAG、模型管理后台、Prompt A/B、OpenTelemetry 导出、风险中心和导出 PDF 均通过本设计的接口边界增量接入。当前运行已支持有界多轮自主闭环、动态反思事件、可重试能力、取消、审批恢复和并发工具；不能把尚未注册的数据面 capability（例如 file.write、空间设置修改、报告落盘、跨空间搜索、插件列表）伪装成已完成。每个新增工具必须先完成 Capability 注册、权限/参数 Schema、审计、风险级别、脱敏和集成测试，之后才可添加到模型的工具清单。
