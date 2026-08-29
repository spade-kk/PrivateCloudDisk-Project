# Cloud AI Agent 15.x 能力对照矩阵

本矩阵以当前仓库代码、测试和 Capability Hub 注册表为准。`已实现` 表示已有可执行路径和离线测试；`部分` 表示核心边界已具备但仍缺少上游能力或生产化持久化；`阻塞` 表示没有可安全声称的实现。真实 MiniMax 测试只使用只读能力中心夹具，不伪造生产写入能力。

| 条目 | 当前状态 | 代码/边界证据与缺口 |
| --- | --- | --- |
| 15.1 规划→执行→观察→反思→调整 | 已实现 | `AgentRuntime` 先用 LLM 生成并校验 V2 `plan_created`，随后发出 `context_*`、`thinking_*`、`tool_call_*`、`output` 和计划状态更新；观察结果进入下一轮，受最大迭代限制。 |
| 15.2 复杂多步骤任务分解 | 已实现 | Function Calling 闭环可连续调用多个工具；真实文件/空间/工作流任务覆盖。 |
| 15.3 动态任务规划 | 已实现 | 每轮将工具观察结果回填模型并重新规划。 |
| 15.4 工具调用闭环 | 已实现 | LLM→Tool Registry→Capability Hub→LLM，含结构化错误回传。 |
| 15.5 并行工具调用 | 已实现 | 同轮独立调用使用 `asyncio.gather`，单次任务仍受限流保护。 |
| 15.6 工具依赖链 | 已实现 | 多轮消息上下文传递上游工具结果。 |
| 15.7 深度上下文 | 已实现 | Redis/仓储消息历史、工具结果和运行轨迹进入上下文。 |
| 15.8 长上下文管理 | 部分 | 有字符预算和保留系统/最新消息的压缩标记；尚未接入摘要模型或向量记忆。 |
| 15.9 DSL/YAML/Python/JS 代码生成 | 部分 | Agent 可生成 CloudFlow DSL 文本；YAML/Python/JS 生成与校验工具尚未全部注册。 |
| 15.10 DSL/YAML 语义校验 | 部分 | `workflow.validate` 可校验 DSL；YAML 校验及统一回写链路尚未接通。 |
| 15.11 Python 插件 AST/沙盒校验 | 阻塞 | 当前 Agent Service 没有可调用的 Python 静态/沙盒校验能力。 |
| 15.12 文件读写 | 部分 | `file.search`、`file.read`、`file.metadata` 走 Hub；`file.write` 未注册，因此报告保存不可声称完成。 |
| 15.13 文件搜索 | 已实现 | `file.search` 通过 Capability Hub，身份和空间由上游校验。 |
| 15.14 创建/修改空间 | 阻塞 | 只有空间查询/成员读取能力，没有安全的写入能力注册。 |
| 15.15 工作流执行和状态 | 已实现 | `workflow.execute`、`workflow.status` 通过 Hub；真实测试默认只执行 validate/status，避免生产副作用。 |
| 15.16 插件能力调用 | 部分 | 工具和审批门已实现；真实插件能力需 Hub 中存在对应注册和授权。 |
| 15.17 错误恢复 | 已实现 | 仅对 `retryable` 工具错误有限重试，并通过 `tool_retry`/`reflection` 暴露。 |
| 15.18 用户中途干预 | 部分 | 支持 cancel、approval/resume；暂停后注入新指令的完整会话控制尚未实现。 |
| 15.19 人工审批 | 已实现 | 高风险工具以 `tool_call_end.status=awaiting_approval` 进入 task view；未批准不会调用 Hub。 |
| 15.20 可观测性/回放/审计 | 部分 | V2 SSE、Redis `AgentTaskSnapshot`、运行轨迹和指标已存在；持久化审计账本和跨服务全量回放仍依赖上游服务接入。 |
| 15.21 流式输出 | 已实现 | Provider stream 标准化为 V2 `output`、上下文、计划、工具和终态事件；浏览器以 task block 实时渲染。 |
| 15.22 思考过程展示 | 已实现（安全范围内） | `thinking_*` 仅展示高层用户可见执行依据，明确不暴露或伪造模型私有 CoT。 |
| 15.23 多模态输入 | 部分 | API 支持附件引用元数据；解析图片/PDF/Office 的能力中心工具尚未全部接入。 |
| 15.24 会话延续 | 部分 | Redis 历史、取消和审批可恢复；页面刷新/SSE 断线可经租户受限 `GET /runs/{id}/task` 恢复 task 文档，不重放工具；进程重启后的后台长任务接管尚未完成。 |
| 15.25 跨会话记忆 | 阻塞 | 当前没有长期用户/空间记忆和 RAG 存储实现。 |
| 15.26 自定义指令 | 阻塞 | Prompt 模板为服务内置配置，尚无用户/空间级安全编辑接口。 |
| 15.27 安全防护 | 已实现 | Gateway HMAC 身份、租户边界、Hub 权限、脱敏、限流、固定 Provider URL 和审批门。 |
| 15.28 多模型复杂度路由 | 部分 | Provider allowlist、主备 fallback 已有；按任务复杂度的路由规则尚未实现。 |
| 15.29 取消与回滚 | 部分 | 支持取消；资源回滚必须由对应能力提供幂等补偿接口。 |
| 15.30 长任务数小时执行 | 部分 | 单次请求有超时/迭代上限；后台队列、租约和断点恢复尚未接入。 |
| 15.31 并发会话 | 已实现 | 会话按 user/space/run 隔离，运行状态和取消键独立。 |
| 15.32 任务模板 | 阻塞 | 尚未提供模板存储、版本和复用接口。 |
| 15.33 上下文引用 | 部分 | 支持 file/space/workflow ID 和附件引用；自然语言实体解析器仍需补齐。 |
| 15.34 结果导出 | 部分 | Agent 可输出 Markdown/JSON 文本并由客户端复制；服务端 PDF/文件保存依赖未注册能力。 |
| 15.35 自我纠错 | 部分 | 工具错误会回填并触发重新规划；不存在资源的静态事实校验器尚未接入。 |
| 15.36 工具选择优化 | 已实现 | 工具定义包含 schema/权限/描述，模型按上下文选择；未知工具被拒绝。 |
| 15.37 调用次数/总时限控制 | 已实现 | `max_iterations`、`max_tool_attempts`、请求超时和 Provider 限流均有边界。 |
| 15.38 代码理解 | 部分 | 可通过 `file.read` 读取 DSL/代码/配置；AST 分析和跨文件索引能力未接入。 |
| 15.39 干中学/动态调整 | 已实现 | 工具观察→高层依据→下一轮模型选择，动态计划进度和工具观察均以 V2 事件可见；离线测试验证事件顺序。 |
| 15.40 合同分析并保存报告 | 部分 | 读取、分析、Markdown 生成链路可运行；保存报告需要 `file.write` 和必要审批能力。 |

## 验证入口

- 离线闭环：`PrivateCloudDisk-ai-service/tests/test_agent_runtime.py`。
- Task 展示投影/恢复：`tests/test_task_snapshot.py`、`tests/test_task_api.py`、`tests/test_sse.py` 和 `PrivateCloudDisk-web/tests/ai-agent-contract.test.mjs`。
- 真实模型任务矩阵：`PrivateCloudDisk-ai-service/tests/test_live_llm_provider.py`，设置 `RUN_LIVE_LLM_TESTS=1` 后使用进程环境变量 `REMOTE_STUDIO_AUTH_TOKEN`；测试代码不会输出、记录或持久化密钥。
- 真实测试包含文件搜索/读取/总结/比较/报告、空间查询/容量/趋势、工作流生成校验与状态查询、插件能力审批门。
- 真实测试的 Hub 是只读确定性夹具，不代表生产 Capability Hub 已注册所有写入能力。

## 补齐顺序

优先补齐 `file.write`/报告存储、空间写入、Python 插件验证、长期记忆、任务模板和后台长任务队列；每项能力必须先在 Capability Hub 注册、完成租户权限和审计，再加入 Agent 工具注册表和真实集成测试。
