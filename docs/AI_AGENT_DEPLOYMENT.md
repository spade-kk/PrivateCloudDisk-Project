# Cloud AI Agent Service 部署、配置与迁移

## Compose 启用方式

`cloud-ai-agent` 属于 automation profile。它依赖 Redis 与 Workflow Service 内的 Capability Hub，Gateway 保留 `/api/v1/ai/**` 路由，但不启用 automation profile 时 AI 路由会因服务不可达/未配置签名而不可用，其他服务不受影响。

```bash
cp .env.example .env
# 在安全密钥系统或 .env（仅本地）设置下列值
docker compose --profile automation up -d redis workflow-service-backend cloud-ai-agent gateway-service-backend
```

生产部署请使用编排平台 Secrets/KMS，不要将真实值写入 `.env`、Compose 文件、README、浏览器环境变量或截图。

## 必填秘密与配置

| 变量 | 位置 | 说明 |
| --- | --- | --- |
| `AI_AGENT_IDENTITY_SIGNING_SECRET` | Gateway + Agent | Gateway HMAC 签名与 Agent 验证共用的长随机秘密 |
| `PCD_INTERNAL_SERVICE_TOKEN` | Agent + Workflow | 调用 Capability Hub 内部 endpoint 的服务凭证 |
| `AI_AGENT_LLM_API_KEY` | Agent | OpenAI-compatible provider 密钥 |
| `AI_AGENT_LLM_BASE_URL` | Agent | 平台管理员配置的 provider endpoint，不可由用户请求覆盖 |
| `AI_AGENT_LLM_MODEL` | Agent | 默认模型标识 |
| `AI_AGENT_LLM_FALLBACK_MODELS` | Agent | 可选逗号分隔 fallback 列表 |

建议设置：

```dotenv
AI_AGENT_ENVIRONMENT=production
AI_AGENT_ENABLE_DOCS=false
AI_AGENT_REDIS_URL=redis://redis:6379/5
AI_AGENT_CAPABILITY_HUB_URL=http://workflow-service-backend:8087
AI_AGENT_RUN_RATE_LIMIT_PER_MINUTE=20
AI_AGENT_MAX_AGENT_ITERATIONS=8
AI_AGENT_MAX_RUN_SECONDS=120
AI_AGENT_MAX_TOOL_CONCURRENCY=4
AI_AGENT_MAX_PROVIDER_CONCURRENCY=32
AI_AGENT_ALLOWED_ORIGINS=https://console.example.com
```

`AI_AGENT_ALLOW_UNSIGNED_IDENTITY` 仅限明确的开发/测试场景，生产使用会导致进程启动失败。Gateway 的同名签名秘密可以为空以保持未启用 AI 的旧部署可启动；一旦启用 Agent，两个服务均必须配置相同秘密。

## 容器安全基线

Compose 中 Agent 使用：只读根文件系统、`/tmp` 的 `noexec,nosuid,nodev` tmpfs、`no-new-privileges`、资源上限、内部 `expose: 8001` 和健康检查。它没有：

- MySQL、RabbitMQ、MinIO、OpenSearch、模型目录或 Uploads bind mount；
- Docker socket、宿主目录、对象存储凭据；
- 文件服务、CloudFlow Runtime、Plugin Runtime 的直连 URL。

部署前核对 `docker compose config` 展开的 Agent 环境和 mount；任何新增业务服务 URL 或宿主路径均属于安全架构变更，必须经过 Capability Hub 设计评审。

## 探针与运维

| URL | 用途 | 预期 |
| --- | --- | --- |
| `GET /health` | liveness | `200 {"status":"ok"}` |
| `GET /ready` | Redis readiness | Redis 不可用时 `503` |
| `GET /metrics` | 内部 Prometheus scrape | 无 prompt/租户 label 的计数与延迟指标 |

用户侧请求只走 Gateway `/api/v1/ai/**`。排障时从 Gateway request ID 查到 Agent run ID，再查 Capability Hub 审计链路；不要从日志或 Redis 导出原始会话作为常规支持手段。

常见故障：

| 症状 | 排查 | 安全处理 |
| --- | --- | --- |
| AI API `503` | Gateway 未设置 HMAC secret 或 automation profile 未启用 | 配置秘密/启用服务；不降级为无签名 |
| AI API `401` | 时钟偏移、签名不一致、请求绕过 Gateway | 同步时钟，核对两个 secret；禁止直连暴露端口 |
| 工具返回不可用 | Hub/服务 token/能力 migration 未部署 | 修复 Hub，不让 Agent 改用直连业务服务 |
| Agent 工具被拒绝 | 用户/空间权限、Schema 或 capability 未注册 | 根据 Hub 审计修正权限/注册；不扩大静态权限 |
| `429` | 单租户 run 限流 | 等待窗口或调整经审核配置 |

### Capability Hub 502 与数据面 500 的区分

AI Agent 的 Capability Hub 客户端只访问 Workflow Service 的统一入口；它不会绕过能力中心直连
Platform。排障时必须先区分传输层和业务层：

| 现象 | 实际含义 | 处理方式 |
| --- | --- | --- |
| AI 客户端收到 HTTP `502/503` | Agent 到 Workflow Service 的网络/反向代理路径失败，尚未得到能力结果 | Compose 内使用 `http://workflow-service-backend:8087`；宿主进程只有在 Workflow Service 发布了 `8087` 端口时才使用 `http://127.0.0.1:8087`；检查容器 DNS、端口、服务凭证和 Gateway/代理日志 |
| Workflow 响应 HTTP `200`，但 `data.success=false`、`errorCode=WF-CAPABILITY-DATAPLANE-UNAVAILABLE` | Hub 已收到请求、完成注册表/权限管线，但 Platform 数据面调用失败或返回 5xx；这不是 Hub 未启动 | 使用同一 `X-Request-Id` 查询 Workflow/Platform 日志，直接探测 Platform 的 `/business/internal/capability/files/list`，核对 `uid`、`X-Space-Id`、空间成员和用户根目录 |
| `errorCode=WF-CAPABILITY-DATAPLANE-ERROR` | Platform 已返回业务错误信封（权限、资源不存在、参数错误等） | 按错误消息修正文件/空间 ID 或权限，不要重试非幂等业务错误 |

`api:file.list` 的 Platform 端点必须返回非空分页对象。旧版本
`DirectoryTreeServiceImpl.findUserNodesByNodeIdPaged` 返回 `null`，导致控制器读取
`result.getTotal()` 时抛出 `NullPointerException`，表现为“服务已启动但文件列表一直 500”。该实现已恢复为复用空间上下文安全边界的过滤、排序和分页逻辑。

另一个常见误判是重复使用同一 `idempotencyKey` 测试不同的 `capabilityKey`。幂等键一旦绑定能力，
Capability Hub 会回放首次结果；现在非法能力键会在幂等 claim 之前拒绝，已绑定其他能力的 key 返回
`WF-CAPABILITY-IDEMPOTENCY-CONFLICT`。调试时请为每个能力和 step attempt 生成新的幂等键。

安全注意：Agent 不再打印 Capability 请求 payload 或 502 原始响应，避免用户/空间上下文、参数和内部堆栈进入日志或模型上下文。

## 从旧 AI Worker 迁移

旧 `PrivateCloudDisk-ai-service` 的 MySQL、RabbitMQ、MinIO、OpenSearch、共享文件目录和本地模型职责已移除，不能直接升级后继续使用。迁移步骤：

1. 停止旧 AI worker 消费者，确认没有遗留队列依赖。
2. 将仍需保留的视觉/NLP 任务改造成有 manifest、权限与审计的 Plugin 或 Capability，而非重新向 Agent 挂载模型/目录。
3. 部署 Capability Hub 所需 registry migration（含 workflow 读取、校验、执行、状态能力）。
4. 配置并验证 Gateway HMAC、Agent service token 和 provider secret。
5. 先以只读文件/空间工具进行测试，再小范围开启审批保护的 workflow/plugin 工具。
6. 验证取消、拒绝审批、跨空间访问拒绝、SSE 断开恢复和 Hub 审计；满足后再扩大 rollout。

回滚仅指停止 `cloud-ai-agent`/移除 Gateway AI 路由或关闭 automation profile；已经由 Capability Hub/CloudFlow/Plugin 执行的业务动作必须使用相应能力的版本、补偿或审计流程处理，不能由 Agent 服务直接修改数据库回滚。
