# Changelog

## 1.1.0 — 2026-08-25

- 将 `/app/ai` 的 Agent 执行过程升级为结构化 Task SSE V2：任务、用户可见执行依据、上下文、LLM 动态计划、工具调用、阶段输出、总结和终态都有独立事件与块模型。
- 新增 Redis `AgentTaskSnapshot` 事件投影和 `GET /api/v1/ai/runs/{runId}/task`。恢复页面或 SSE 断线时只读取快照，不重放可能产生副作用的 run。
- AgentRuntime 先向所选模型请求受校验的 JSON 展示计划；Provider/JSON 异常时用包含当前请求的显式 `request_fallback` 降级，移除固定计划文本。
- 模型输出现在以 V2 `output` 增量实时发送；工具成功事件只携带 `output_data`，不把 `ToolExecutionResult` 包装投影到前端或 task 快照。
- 新增任务快照、恢复租户边界和 SSE task identity 测试；更新实时模型测试的 V2 事件断言。

## 1.0.0 — 2026-08-24

- 修复 Capability Hub 客户端对 HTTP 502/503、非 JSON响应和 HTTP 200 业务失败信封的处理；不再访问未初始化的响应变量或打印敏感请求 payload。
- 增加 `WF-CAPABILITY-DATAPLANE-UNAVAILABLE` 业务错误透传与服务拓扑排障说明。

- 删除旧的本地视觉/NLP Worker、模型目录、直接 MySQL/RabbitMQ/MinIO/OpenSearch/共享目录依赖和旧 Compose 挂载。
- 新建 FastAPI 企业 AI Agent Runtime：OpenAI-compatible `AsyncOpenAI` Adapter、Redis 会话/取消/审批状态、受限多轮工具调用、Provider fallback、SSE token/tool/heartbeat 流。
- 所有企业资产调用改为静态 Tool Registry → Capability Hub；添加工作流 list/validate/execute/status 注册与适配，禁止 Agent 直连文件、数据面、CloudFlow Runtime 或 Plugin Runtime。
- 增加 Gateway 对 `/api/v1/ai/**` 的 HMAC 用户/空间可信上下文、Redis run 限流、Prometheus 低基数指标、审批恢复与动态工具批次门禁。
- 增加 `/app/ai` ChatGPT/Codex 风格聊天页面、悬浮助手、受认证 fetch-SSE、工具/审批状态和前端契约测试。
- 增加离线单测与 opt-in `live_llm` 集成测试；后者通过 `REMOTE_STUDIO_AUTH_TOKEN` 验证 MiniMax 的真实流式 Adapter，密钥不进入代码/日志/测试输出。
- AgentRuntime 增加显式 `REFLECTING` 状态、`reflection`/`tool_retry` SSE 事件、有界 retryable 工具重试和上下文字符预算；真实任务测试现在通过项目 AgentRuntime 验证文件多步分析、空间统计、CloudFlow 校验/状态查询及插件审批阻断。
