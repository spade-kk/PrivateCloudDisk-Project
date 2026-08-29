# Cloud AI Agent Service 安全边界与运维门禁

> **版本：1.0 · 2026-08-24**  
> 本文描述已重构的 `cloud-ai-agent`，不适用于已删除的本地视觉/NLP Worker。

## 不可突破的边界

Cloud AI Agent 不是超级管理员，也不是通用网络代理。它只能进行两类出站调用：

1. 到受配置控制的 OpenAI-compatible LLM provider；用户、模型和 prompt 均不可提交任意 provider URL。
2. 到受配置控制的 Workflow Service Capability Hub 固定内部入口：`POST /internal/v1/capabilities/invoke`。

它**不**导入或配置 MySQL、RabbitMQ、MinIO、OpenSearch、对象存储、共享 Uploads、宿主文件路径、文件服务 HTTP 客户端、CloudFlow Runtime 客户端或 Plugin Runtime 客户端。企业资产只可经以下链路到达：

```text
browser -> Gateway authentication/HMAC -> cloud-ai-agent -> Capability Hub
        -> registered/authorized capability -> data plane or runtime
```

Capability Hub 是资源权限、能力注册、参数 Schema、权限交集、幂等、路由白名单和全局审计的最终裁决者；AI 服务的静态 Tool Registry 只构成更窄的一层上限。

## 身份和多租户

| 层 | 控制 | 失败行为 |
| --- | --- | --- |
| Browser → Gateway | 既有用户认证；清除外部伪造 `X-PCD-*` | 未认证拒绝 |
| Gateway → Agent | HMAC-SHA256 签名 `method/path/request-id/timestamp/user/space` | 签名缺失、错误或过期均 `401` |
| Agent session | Redis key 必含 `user:space` scope；每次 ID 查询重新比较 owner | 跨租户 `404` |
| Agent → Hub | 仅服务 token；payload user/space 来自已验证身份 | 无服务凭证拒绝 |
| Hub → data plane | 用户、空间、资源及 capability permissions 二次校验 | 允许集之外拒绝 |

生产环境必须同时满足：

- `AI_AGENT_ALLOW_UNSIGNED_IDENTITY=false`；该开关在 `production` 被配置校验硬拒绝。
- `AI_AGENT_IDENTITY_SHARED_SECRET` 与 Gateway `AI_AGENT_IDENTITY_SIGNING_SECRET` 一致且为随机高熵秘密。
- `AI_AGENT_INTERNAL_SERVICE_TOKEN` 与 Workflow Service 内部服务凭证一致。
- provider API key 只能由部署密钥系统/环境注入，绝不存入 Redis、前端、日志或 Git。

Gateway 不配置 AI 签名秘密时，只拒绝 `/api/v1/ai/**`（`503`）；非 AI 既有业务链路保持可用。这避免把新可选 automation 能力变成全平台启动的隐式破坏性依赖。

## Tool 与审批

- 模型只能看到服务器定义的 JSON Schema，不能制造 URL、permission、service token、capability route 或用户身份。
- 工具调用的 `declaredPermissions/grantedPermissions` 由静态 Registry 生成，不接受模型/浏览器的权限列表。
- `workflow.execute` 和 `plugin.call` 默认需要显式审批；审批不会提交工具参数，而是恢复 Redis 保存、绑定 run 的原调用。
- 一个模型批次包含任何审批工具时会整体暂停，防止非高风险工具与高风险工具产生难以解释的部分执行。
- Hub 仍执行最终权限和 capability Schema 校验。Agent 的“批准”不是越权许可。

高风险的文件写入、空间成员/权限改变、空间创建和未知插件副作用尚未在当前 Registry 暴露；只有它们先有 Hub 注册、数据面处理、补偿/版本策略、审计与测试后才可添加。

## 输入、输出、提示与数据泄露

- 附件仅传 `file_id/name/type` 引用；Agent 不接受上传的文件正文、绝对路径和外部 URL 作为旁路数据。
- System prompt 明确要求模型只通过工具取得企业事实；计划卡片展示高层可验证步骤，不输出模型私有思维链。
- 所有工具结果先行脱敏、截断。password、token、secret、authorization、cookie、key 和类似字段，以及明显绝对路径，不得回显给模型/UI/日志。
- Markdown UI 先 HTML 转义再渲染受限格式；模型输出不以 raw HTML 注入 DOM。
- Provider base URL、Hub URL、参数 Schema 和 SSRF 目标都不可由用户输入决定。

## DoS、限流与可观测性

`RunRateLimiter` 在 Redis 对 `user + space` 使用固定一分钟窗口，`AI_AGENT_RUN_RATE_LIMIT_PER_MINUTE` 默认 20。还应在 Gateway 保留按用户/IP 的入口限制，并在 provider/Hub 上配置自身并发、超时、队列和熔断保护。Agent 的最大轮数、运行总时长和工具并发均由服务端设置。

Prometheus 指标只使用方法、路由、HTTP 状态、工具名和结果等低基数字段。严禁把用户、空间、会话、文件 ID、提示词、参数、token 或 provider API key 变为标签。`/metrics` 是容器内部运维端点，不通过 Gateway 公开。

## 威胁模型与验证

| 威胁 | 防线 | 验证 |
| --- | --- | --- |
| 伪造用户/空间头 | Gateway 清洗 + HMAC + 时钟窗口 | `test_identity.py`；Gateway Filter 编译/路由验证 |
| Agent 直连业务资产 | 代码模块与 Compose 禁止 direct client/挂载 | `rg` 静态扫描；Compose 审计 |
| 模型调用任意 URL/能力 | 静态 Registry + 固定 Hub endpoint + Hub 注册表 | `test_capability_hub.py`、参数 Schema 测试 |
| Prompt 注入 | 不可覆盖 system policy、只经工具取数、输出脱敏 | 人工红队与端到端 capability 权限测试 |
| 副作用越权 | 高风险审批 + Hub 用户权限 | `test_agent_runtime.py` 审批恢复；Hub 集成测试 |
| 租户越界 | scope key + 所有 run/conversation owner 比对 | API 集成测试（不同 user/space） |
| 高频滥用 | Redis 限流、总超时/轮数/并发限制 | limiter 单测和压测 |
| 日志泄漏 | redaction、低基数 metrics、无 stack 回显 | 日志/指标审计 |

未接入的长期 Memory/RAG、模型管理后台、Prompt A/B、空间共享会话和长任务队列不应以“已安全支持”对外宣称；接入前必须延续本文件的身份、经 Hub 取数、脱敏、租户与审计门禁。
