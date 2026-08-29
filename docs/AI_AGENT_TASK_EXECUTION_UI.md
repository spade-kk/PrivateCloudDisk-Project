# Cloud AI Agent 任务执行视图 V2

> **版本：2.0 · 状态：已实现，待真实浏览器验收 · 更新：2026-08-25**  
> 本文定义 `/app/ai` 的 Agent 任务执行视图、服务端 SSE V2 契约、刷新恢复和验收清单。它替换了“把计划、工具和运行过程塞进普通聊天气泡”的展示方式。

## 1. 目标、范围与非目标

一个用户请求对应一个 `AgentTask`。任务不是一串模型自述，而是一份随执行实时增长的、可恢复的任务文档：

```text
用户请求
  → 受控上下文收集
  → 模型生成的可见计划
  → 用户可见的执行依据
  → 一个或多个受限工具调用
  → 阶段输出
  → 最终总结
```

页面的阅读体验应接近 Codex 的任务记录，而不是瀑布式“聊天气泡墙”：按时间顺序自然排版、工具细节默认收起、长结果在本块内滚动、最终总结像 Markdown 报告一样阅读。它不使用大量分隔线、霓虹背景、重复状态图标或虚假的终端输出。

本次范围是 Web 的 `/app/ai` 页面、悬浮入口兼容、Cloud AI Agent Service 的 SSE/快照投影及其文档和测试。普通非 Agent 聊天历史仍按原有消息气泡显示；外部业务资产依然只能经由 Capability Hub 调用。

以下内容**不**在视图中显示：模型私有思维链、系统提示词、服务地址、令牌、未脱敏的企业数据、原始 `ToolExecutionResult` 包装对象。`ThinkingBlock` 是运行时生成的、面向用户的高层执行依据，不是 chain-of-thought。

## 2. 实施前审计与差距结论

审计范围包含 `PrivateCloudDisk-web/src/views/AiAssistantView.vue`、`src/stores/aiAgentStore.ts`、`src/api/aiAgent.ts`、`PrivateCloudDisk-ai-service/app/runtime/agent.py`、`app/api/routes.py`、`app/memory/repository.py`、既有 SSE/运行时测试及 AI 服务设计文档。结果如下。

| # | 审计点 | 原有行为/风险 | V2 落地结论 |
| --- | --- | --- | --- |
| 1 | 路由 | `/app/ai` 已存在 | 保持路由、会话侧栏和悬浮入口，重构主消息区。 |
| 2 | 页面组件 | 过程信息与普通 assistant 气泡耦合 | 引入 `AgentTaskView` 和六类块组件。 |
| 3 | SSE 消费 | 使用受认证 fetch stream，适合携带身份和取消信号 | 保留 fetch-SSE，不改成无法携带既有认证头的裸 `EventSource`。 |
| 4 | SSE 类型 | 旧 `status/plan/token/tool_result/done` 粒度不足 | 增加 V2 任务、上下文、计划、工具、输出、总结和终态事件。 |
| 5 | Plan | 原有计划是运行时固定文案 | 先调用模型生成 JSON 计划，校验后再发送 `plan_created`。 |
| 6 | 计划降级 | 模型可能返回非法 JSON | 只在解析/Provider 失败时生成包含当前请求的 `request_fallback`，并显式标记来源；不会复用固定文案。 |
| 7 | 思考展示 | 旧反思文本与普通流混合 | 使用高层 `thinking_*` 块，不输出或伪造私有推理。 |
| 8 | 上下文 | 没有独立 UI 事件 | 用 `context_start/item/end` 记录历史、附件和受限工具观察。 |
| 9 | 工具开始 | 缺少可读 command 描述 | `tool_call_start` 提供脱敏的工具名称、参数摘要和结构化输入。 |
| 10 | 工具结果 | 前端可看到包装结果或字符串化结果 | 服务端仅发送 `output_data`；前端只读取该字段。 |
| 11 | 长输出 | 普通聊天文本不易定位/复制 | 工具 JSON 在可折叠块中显示，固定最大高度与横向滚动。 |
| 12 | 阶段输出 | token 到普通消息，过程与最终回答混杂 | 所有新运行时正文走 V2 `output` 增量块。 |
| 13 | 最终结果 | 仅由普通 assistant 气泡表现 | `summary` 是单独的 Markdown 总结块，同时保存普通消息以兼容历史。 |
| 14 | 状态管理 | 普通消息结构承载 plan/tools | 新建 `useAgentTaskStore`，消息只保存 `run_id/taskId` 引用。 |
| 15 | 刷新恢复 | 只查 run 和消息，不能恢复过程 | Redis 保存按事件投影的 task snapshot，新增受租户约束的恢复接口。 |
| 16 | 断线 | 可能误把重连理解为重新执行 | 重连只读取 `/runs/{id}/task`，绝不重复 `POST /runs`。 |
| 17 | 幂等/乱序 | 事件可能重投或跨代理到达 | 前端以 event id/`run_id:sequence` 去重，按 order/timestamp 排序。 |
| 18 | 多任务 | 仅一个消息流状态 | Store 以 `task_id` 分桶；切换会话不会清除后台任务快照。 |
| 19 | 折叠 | 过程块没有统一偏好 | 每块存储折叠状态；支持全部展开/折叠及快捷键。 |
| 20 | 移动端 | 长 JSON/窄工具栏易挤压 | 小屏默认收起过程块，命令/结果横向滚动，触控按钮最小 44px。 |
| 21 | 主题 | 原消息主题覆盖过程不足 | 块组件使用现有主题变量并有暗色安全对比。 |
| 22 | Markdown 安全 | 模型输出直接作为富内容有注入风险 | 使用 escape-first 的 `safeMarkdown`；不接受模型 HTML。 |
| 23 | 审批 | 高风险动作需服务端绑定的恢复 | 工具块显示确认，不把 capability、参数或身份从浏览器回传。 |
| 24 | 普通聊天回归 | 旧历史没有任务快照 | 无 `run_id` 或取不到快照时回退为普通消息渲染。 |
| 25 | 可观察性/测试 | 没有面向任务文档的契约测试 | 增加快照投影、租户恢复、SSE task identity、前端结构契约测试。 |

## 3. 信息架构与显示规则

### 3.1 页面层级

```text
会话消息列表
  ├─ 用户消息（普通气泡）
  ├─ AgentTaskView（每个 Agent run 一份执行文档）
  │   ├─ 标题、模型、任务状态、总耗时、导出/折叠操作
  │   ├─ ThinkingBlock（高层依据，默认收起）
  │   ├─ ContextBlock（已读取/已检索/已引用项，默认收起）
  │   ├─ PlanBlock（模型动态计划，默认展开）
  │   ├─ ToolCallBlock（按时间顺序的工具活动，默认收起）
  │   ├─ OutputBlock（阶段 Markdown 输出）
  │   └─ SummaryBlock（最终 Markdown 总结）
  └─ 历史普通 assistant 消息（兼容显示）
```

块是轻量、文档化的列表，而不是通过连接线模拟 DAG。每个块只有一个轻量标题行；展开后内容缩进。工具调用按真实工具事件顺序展示：例如“读取文件”“搜索能力”“运行受限命令”的标题可折叠，展开后才显示命令、输入和结果。用户提供的示例中用作解释的 `Tips`、占位用的文字 `[图标]` 不属于运行时数据，页面用稳定 SVG/Font Awesome 图标和真实事件数据代替，绝不渲染那些提示文字。

### 3.2 默认折叠策略

| 块 | 桌面默认 | 移动端默认 | 原因 |
| --- | --- | --- | --- |
| 思考 | 收起；流式阶段临时展开 | 收起 | 降低高层说明的视觉权重。 |
| 上下文 | 收起 | 收起 | 仅在核验素材范围时展开。 |
| 计划 | 展开 | 展开 | 用户应看到模型动态生成的目标与进度。 |
| 工具调用 | 收起 | 收起 | 结果可能很长，避免淹没任务叙事。 |
| 阶段输出 | 展开 | 展开 | 正文是执行中最有价值的信息。 |
| 最终总结 | 展开 | 展开 | 是用户首要阅读结果。 |

`Ctrl+E` 展开当前任务全部块，`Ctrl+Shift+E` 收起全部块。每个折叠按钮有 `aria-expanded` 和文字/图标标签；小屏工具区域保留 44px 触控热区。任务块数超过 60 时，最早块自动收起而不删除快照。

### 3.3 工具结果的唯一展示源

工具成功结束事件的稳定数据为：

```json
{
  "event": "tool_call_end",
  "task_id": "run-123",
  "sequence": 19,
  "timestamp": "2026-08-25T09:10:11.012Z",
  "block_id": "tool-call-7",
  "tool_name": "file.read",
  "status": "completed",
  "output_data": {"content": "已脱敏的文件内容"},
  "duration_ms": 184
}
```

前端 `ToolCallBlock.vue` 仅序列化 `block.data.output_data`。它不展示 `success/output/errorCode` 等 `ToolExecutionResult` 外层对象，也不会把包装对象放入 summary 或浏览器日志。失败走 `tool_call_error`，审批等待走 `tool_call_end.status=awaiting_approval`，二者均没有把内部错误/令牌当作结果展示。

## 4. V2 数据模型

### 4.1 任务和块

```ts
type AgentTaskStatus = 'running' | 'paused' | 'completed' | 'failed' | 'cancelled'
type AgentTaskBlockType = 'thinking' | 'context' | 'plan' | 'tool_call' | 'output' | 'summary'

interface AgentTask {
  schema_version: 2
  task_id: string
  conversation_id: string
  // user_id 只存于服务端快照，用于授权；公开恢复 DTO 不返回该字段
  space_id: string | null
  user_request: string
  model: string
  status: AgentTaskStatus
  started_at: string
  ended_at?: string
  total_duration_ms?: number
  blocks: AgentTaskBlock[]
}

interface AgentTaskBlock {
  id: string
  type: AgentTaskBlockType
  order: number
  parent_id?: string           // 为未来嵌套工具组预留，不改变平铺阅读顺序
  status?: string
  data: Record<string, unknown>
  started_at: string
  ended_at?: string
}
```

`PlanBlock.data.plan_items` 每项为 `id/title/details/status`，状态只能是 `pending/running/completed/failed/superseded`。计划由模型返回的 JSON 解析并验证，不能成为可执行命令；实际动作始终由 Tool Registry、审批门和 Capability Hub 决定。

### 4.2 前端数据流

`useAgentTaskStore` 的职责是服务端快照的展示投影：

1. `agent_task_start` 创建任务；
2. 每个 V2 SSE 事件幂等更新一个块；
3. `seenEventIds` 防止重连导致重复块；
4. `collapsedMap` 用 localStorage 保存纯展示偏好；
5. 刷新/会话恢复调用受鉴权的 task snapshot API；
6. 任务终态和最终总结继续写回普通会话消息，确保旧客户端仍有可读结果。

前端不扫描模型文本中的“计划：”“工具：”等词来推断过程；普通消息只有最终内容和可选 `run_id` 引用。

## 5. SSE V2 契约

所有业务事件均为 `event: <type>\ndata: <json>\n\n`。每个 JSON 都由服务端加入 `task_id`（同 `run_id`）、`run_id`、单调 `sequence` 和 UTC `timestamp`。heartbeat 不代表业务动作，不能被持久化为任务块。

| 事件 | 核心字段 | 任务视图行为 |
| --- | --- | --- |
| `agent_task_start` | `task_id`, `conversation_id`, `user_request`, `model`, `schema_version` | 初始化任务文档。 |
| `thinking_start` | `block_id` | 创建高层执行依据块。 |
| `thinking_delta` | `block_id`, `delta` | 追加面向用户的依据摘要。 |
| `thinking_end` | `block_id` | 结束并默认收起该块。 |
| `context_start` | `block_id`, `context_summary` | 创建上下文块。 |
| `context_item` | `block_id`, `item` | 追加已读取/搜索/附件/观察项。 |
| `context_end` | `block_id` | 结束上下文块。 |
| `plan_created` | `block_id`, `plan_items`, `source` | 展开动态计划；`source` 为 `llm` 或可审计的 `request_fallback`。 |
| `plan_item_update` | `block_id`, `plan_item_id`, `status`, `details?` | 实时更新计划项。 |
| `tool_call_start` | `block_id`, `tool_name`, `command`, `input` | 创建可折叠工具块。 |
| `tool_call_end` | `block_id`, `status`, `output_data`, `duration_ms` | 只投影 `output_data`；可显示审批等待。 |
| `tool_call_error` | `block_id`, `message`, `code`, `duration_ms` | 标记失败，但不暴露下游堆栈。 |
| `output` | `block_id`, `output_text`, `format`, `delta` | 追加阶段 Markdown/text/JSON 输出。 |
| `summary` | `block_id`, `summary_text`, `format` | 写入最终 Markdown 总结。 |
| `task_completed` | `iterations`, `tool_calls` | 任务完成。 |
| `task_failed` | `code`, `message` | 任务安全失败。 |
| `task_cancelled` | `message` | 任务被用户/系统取消。 |

旧事件名称仍被模型类型接受，以支持滚动发布期间的非 Web 消费者；V2 Web 页面只依赖上述结构化事件。迁移期不能让新页面重建旧 `plan/token/tool_result` 文本。

## 6. 后端执行、动态计划与快照

`AgentRuntime` 在 run 开始时创建 `AgentTaskSnapshot`，并将每一个结构化 V2 事件同时用于 SSE 与 Redis 投影。流程如下：

1. 写入任务、run 和用户消息；发送 `agent_task_start`。
2. 记录当前会话和安全附件引用的上下文块；不把文件正文或受信服务细节塞进 planner。
3. 用已选择 LLM 的无工具请求生成 2–6 项 JSON 计划，解析为 `PlanItem` 后发送 `plan_created`。Provider 失败或非法 JSON 时，生成带当前用户请求的最小降级项并标记 `request_fallback`。
4. 主 Agent 进入有界的计划 → 工具 → 观察 → 调整循环。每轮更新活动计划项，工具开始/结束/失败均发送独立事件；独立工具调用仍可受并发上限并行执行。
5. 模型内容以 `output` 增量实时发送。工具结果只有 `output_data` 进入 task 快照和 UI，完整受控结果只在运行时消息上下文中作为脱敏工具结果使用。
6. 写入最终普通 assistant 消息与 `summary`，之后发送终态事件。

刷新或断线恢复使用：

```http
GET /api/v1/ai/runs/{runId}/task
```

服务先用 run 的 `user_id + space_id` 校验调用者，再读取 `ai:task:{runId}`。找不到或跨租户统一返回 404，避免枚举 run。客户端恢复该快照，而不是重新提交原始 `POST /runs`，防止重复 capability 调用、高风险审批或副作用。

## 7. 视觉、交互、主题和响应式验收

1. 执行视图比聊天背景略深，但保持透明/轻量；块之间以 2px 的自然留白区分，不堆叠粗边框。
2. 标题行显示图标、真实名称、状态和耗时；展开箭头仅作为次要操作。
3. 思考文字使用较浅颜色；上下文用简短列表；计划用进度和状态图标；工具命令和 JSON 使用等宽字体。
4. 成功、失败、等待审批使用绿、红、琥珀色，不能只靠颜色传达状态。
5. 所有 Markdown 先 HTML 转义，再做有限的标题/列表/代码块渲染；不执行模型提供的 HTML 或脚本。
6. 长 command/result 只在本工具块内滚动；移动端保持横向滚动，不能撑开整个页面。
7. 复制命令、复制结果、复制总结和 Markdown/JSON 导出都从已脱敏快照生成。
8. 移动端过程块默认收起，工具栏触控区不小于 44px；暗/亮主题共享 CSS 变量并遵循现有 IDE 的响应式体系。
9. `Ctrl+E`、`Ctrl+Shift+E`、按钮的 `aria-label` 和 `aria-expanded` 必须可用；`prefers-reduced-motion` 关闭折叠动画。
10. 审批按钮只提交服务端签发、run 绑定、短期有效的 token；不会回传工具参数、身份或 capability key。

## 8. 实施清单与代码映射

| 交付项 | 状态 | 关键文件 |
| --- | --- | --- |
| 现有链路审计和差距记录 | 已完成 | 本文第 2 节、`AI_AGENT_API.md`。 |
| 任务/块 DTO 与版本化 | 已完成 | `PrivateCloudDisk-ai-service/app/domain/models.py`。 |
| Redis 事件投影与 snapshot | 已完成 | `PrivateCloudDisk-ai-service/app/memory/repository.py`。 |
| 动态模型计划、结构化事件、实时 output | 已完成 | `PrivateCloudDisk-ai-service/app/runtime/agent.py`。 |
| 恢复 API 与租户校验 | 已完成 | `PrivateCloudDisk-ai-service/app/api/routes.py`。 |
| fetch-SSE V2 类型和 task API SDK | 已完成 | `PrivateCloudDisk-web/src/api/aiAgent.ts`。 |
| Pinia 任务状态、去重、折叠、导出 | 已完成 | `PrivateCloudDisk-web/src/stores/agentTaskStore.ts`。 |
| 聊天与任务视图解耦 | 已完成 | `PrivateCloudDisk-web/src/stores/aiAgentStore.ts`、`src/views/AiAssistantView.vue`。 |
| 六类可折叠块组件 | 已完成 | `PrivateCloudDisk-web/src/components/ai/task/`。 |
| 只显示 `output_data` | 已完成并有测试 | `ToolCallBlock.vue`、`test_task_snapshot.py`、`ai-agent-contract.test.mjs`。 |
| 安全 Markdown 渲染 | 已完成 | `PrivateCloudDisk-web/src/utils/safeMarkdown.ts`。 |
| 单元/契约/构建验证 | 已完成，见第 9 节 | 前后端测试文件。 |
| 真实浏览器多主题/移动端验收 | 待部署环境验收 | 需要带认证的运行中 Web、Gateway、Redis 与 Provider。 |

## 9. 测试、性能与发布门禁

### 9.1 已自动化的验证

| 范围 | 命令/测试 | 验证内容 |
| --- | --- | --- |
| Runtime | `tests/test_agent_runtime.py` | 模型生成的计划、输出块、工具循环和最终持久化。 |
| 快照 | `tests/test_task_snapshot.py` | 事件投影和仅保存 `output_data`。 |
| 恢复 API | `tests/test_task_api.py` | 同租户恢复与跨租户 404。 |
| SSE | `tests/test_sse.py` | heartbeat 与每个业务事件的 task identity/timestamp。 |
| 前端契约 | `npm run test:ai-agent` | V2 映射、组件拆分、工具结果边界、恢复/审批路径。 |
| 前端构建 | `npm run build` | Vue SFC 与生产构建。 |

### 9.2 发布前人工验收

1. 在 `/app/ai` 发起含至少两个工具调用的任务，确认计划来自当次模型，而不是固定句子。
2. 观察 context、plan、thinking、tool、output、summary 以真实事件顺序出现；没有普通消息解析出的伪块。
3. 展开工具块，确认只显示业务 `output_data`，不显示 `success/output/errorCode` 包装、密钥或内网地址。
4. 模拟 SSE 中断后刷新页面，确认从 `/runs/{id}/task` 恢复同一任务且没有重复工具调用。
5. 测试审批拒绝/批准、取消、工具失败、模型故障和任务超过上限的终态。
6. 在亮色、暗色、320–768px 手机和 768–1024px 平板检查折叠、滚动、触控大小与无横向页面溢出。
7. 对包含 100+ 工具块的夹具验证最早块自动折叠、展开动画与滚动不卡顿；大规模虚拟滚动是后续性能扩展条件，而不是未验证地声称已实现。

### 9.3 回滚和兼容

任务视图失败时，保留服务端最终 `ChatMessage`，旧客户端仍可显示最终文本。滚动发布必须先部署能产生 V2 快照的 Agent Service，再部署 Web；若 Web 先发布，它会对无 snapshot 的历史 run 回退为普通消息。不得为了恢复显示重复提交旧 run。

## 10. 可编辑架构资产

- [运行时信任边界](./architecture/cloud-ai-agent-runtime.drawio)
- [旧 SSE/工具调用时序（兼容参考）](./architecture/cloud-ai-agent-sse-sequence.drawio)
- [任务执行 V2 时序](./architecture/cloud-ai-agent-task-execution.drawio)

完整 HTTP/SSE 字段见 [Cloud AI Agent API](./AI_AGENT_API.md)；运行时边界和 Agent 能力状态见 [Cloud AI Agent Service 设计](./CLOUD_AI_AGENT_SERVICE_DESIGN.md) 与 [15.x 能力对照矩阵](./AI_AGENT_CAPABILITY_MATRIX.md)。
