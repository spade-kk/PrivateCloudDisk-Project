# API 接口概览

## 1. 通用约定

- 公共入口：浏览器访问 `/api/v1`，生产环境由 Nginx 转发到 Gateway。
- 认证：登录后使用 `Authorization: Bearer <JWT>`；预览、下载和上传分片还可能需要短期操作凭证。
- 内部调用：`/business/internal/**` 等内部路径只允许受信服务携带内部服务凭证访问。
- 响应：各服务使用统一字段或服务专用响应包装，接入时以对应 Controller、DTO/VO 和前端 API 模块为准。
- 错误：常见状态包括 400 参数错误、401 未认证、403 无权限、404 不存在、409 冲突、413 配额/大小限制、429 限流和 5xx 服务错误。

## 2. Gateway 路由族

| 路径族 | 目标 | 说明 |
| --- | --- | --- |
| `/api/v1/business/**` | Platform Service | 用户、文件元数据、目录、空间、分享、标签、收藏、回收站和配额 |
| `/api/v1/files/**` | Storage Service | 操作凭证、上传分片、文件内容、缩略图、预览资源和转换 |
| `/api/v1/im/**` | IM Platform | 消息、会话、群组、好友、通话记录 |
| `/ws/**` | IM Server | WebSocket 长连接 |
| `/api/v1/plugins/**` | Plugin Service | 插件控制面和市场 |
| `/api/v1/workflows/**` | Workflow Service | 工作流定义、校验、运行和执行记录 |
| `/api/v1/ai/**` | Cloud AI Agent | 会话、SSE Agent Task V2 run、任务快照恢复、取消和审批恢复；Gateway 对此路由额外签名用户/空间上下文 |
| `/api/v1/mcp` | CloudFlow MCP Server | 第三方 Agent 的 MCP Streamable HTTP/JSON-RPC 入口；Gateway 校验 Bearer JWT 后签名可信身份上下文，MCP 仅经 Capability Hub 调用工具 |
| `/api/v1/.well-known/oauth-protected-resource/mcp` | CloudFlow MCP Server | 无需登录的 OAuth Protected Resource Metadata，用于 MCP Client 在取 token 前发现授权服务器 |
| `/api/v1/plugins/executions/**` | Plugin Service | 已授权的插件执行概要、Docker 风格日志、能力调用审计、SSE tail 与导出；详见 `PLUGIN_EXECUTION_OBSERVABILITY.md` |
| `/api/v1/capabilities/**` | Workflow/Plugin | 能力中心和能力解析 |
| `/api/v1/marketplace/**` | Plugin/Workflow | 插件与工作流市场 |
| `/api/v1/client-registration/**` | Client Registration | 客户端挑战、绑定和状态 |

## 3. 业务 API 能力

### 用户与认证

- 登录、注册、注销、用户信息、头像、密码、邮箱/手机号变更。
- 验证码发送、重发和校验。
- 设备/客户端状态与绑定由 Client Registration Service 负责。

### 文件与目录

- 文件详情、重命名、移动、复制、删除和位置变更。
- 文件夹创建、重命名、移动、复制、删除、分页子节点和路径浏览。
- 文件列表、最近访问、搜索和配额查询。
- 所有资源操作都应结合当前用户和空间上下文，不应仅凭客户端传入的 ID 判断权限。

### 上传、下载与预览

- 上传会话创建、状态查询、取消和完成通知。
- 操作凭证签发/撤销。
- 分片上传、已上传分片查询、合并和内容激活。
- 文件内容流式下载、Range、文件夹下载、预览令牌、元数据、缩略图、文档转换和预览缓存。
- 实际支持的预览格式由 Storage Service 的能力和部署依赖决定。

### 分享、回收站、收藏与标签

- 分享创建、详情、资源列表、访问和撤回。
- 回收站列表、恢复、彻底删除和清空。
- 文件/文件夹收藏状态、收藏列表和批量操作。
- 标签创建、更新、删除、绑定、解绑、批量管理和按标签查询。

### 空间协作

- 空间创建、列表、详情、更新和删除。
- 成员邀请、成员列表、角色调整和移除。
- 成员权限查询/更新、加入申请、可见性列表和公开发现。
- 当前空间上下文通过请求头/会话与服务端权限共同决定，不能由客户端伪造。

## 4. 插件与自动化 API

- Plugin Service：插件 CRUD、版本、包上传、校验、发布、安装/卸载、启停、执行统计和插件市场。
- Local Plugin：客户端身份绑定、平台/客户端筛选、签名包下载授权。
- Workflow Service：工作流 CRUD、版本、DSL/图校验、发布、运行、执行详情、重试和取消。
- Capability Hub：列出、解析和投影内置能力、平台 API、插件能力和本地插件能力；统一调用入口
  `POST /internal/v1/capabilities/invoke`（仅内部网络，需 `X-PCD-Service-Token` 服务凭证）。
- Capability 数据面内部接口（Platform，`/business/internal/capability/*`）：文件元数据/列表/搜索/标签/扫描、
  空间信息/成员、用户信息（脱敏）、创建分享；由能力中心透传 uid + 空间上下文，内部再二次鉴权。
- Marketplace：插件/工作流列表、提交审核、评分和模板导入。
- Automation/Scheduler：主要为内部服务 API，负责事件匹配、执行落库、Inbox/Outbox、定时计划和幂等触发。

### Cloud AI Agent API

- Cloud AI Agent：会话、消息分页、可用模型、结构化 SSE Agent Task V2（上下文/动态计划/工具/输出/总结）、任务快照恢复、取消与审批恢复；它的浏览器入口仅为 `/api/v1/ai/**`。
- Agent 不直连 Platform、Storage、CloudFlow Runtime 或 Plugin Runtime：内部动作唯一使用 Capability Hub
  `POST /internal/v1/capabilities/invoke`，由 Hub 以最终用户/空间权限、能力注册、Schema 和审计作裁决。
- Gateway 会清除客户端伪造的 `X-PCD-*` 头并对 AI 路径签名；浏览器不可持有服务 token、Provider key、Hub URL 或签名 secret。
- 浏览器断线或刷新应读取 `GET /api/v1/ai/runs/{runId}/task` 恢复同一任务文档，不得重放创建 run 的 POST；工具 UI 只显示事件的 `output_data`。
- 完整请求/SSE/审批 API 见 [Cloud AI Agent API](./AI_AGENT_API.md)。

### CloudFlow MCP Server API

- 公开 MCP endpoint：`POST /api/v1/mcp`（Streamable HTTP），请求为 JSON-RPC 2.0，必须携带
  `Authorization: Bearer <access-token>`、`Content-Type: application/json`，并接受 JSON/SSE 响应。
- 初始化：`initialize` 返回 `Mcp-Session-Id`；后续 `tools/list`、`tools/call` 使用同一 session。
  可选的 `GET /api/v1/mcp` 用于有效 session 的 SSE keepalive。
- OAuth resource metadata：`GET /api/v1/.well-known/oauth-protected-resource/mcp`，生产环境需要
  Gateway 的 `MCP_PUBLIC_BASE_URL` 与 MCP 的 `MCP_OAUTH_AUTHORIZATION_SERVERS` 指向真实 OAuth 2.1
  授权服务。
- MCP Server 不公开 Capability Hub 私网端点。只有 MCP 服务通过 `X-PCD-Service-Token` 调用
  `/internal/v1/capabilities/mcp/tools`、`/invoke`、`/audit`；这些端点不应加入公网 Gateway 路由。
- 完整 wire contract、工具 schema、错误语义和 Agent 示例见 [CloudFlow MCP 协议](./CLOUDFLOW_MCP_PROTOCOL.md)。

## 5. 接入注意事项

1. 先阅读 Gateway 的有效路由配置，不要只依据旧文档路径。
2. 生产前端使用同源 `/api/v1`，不要把容器内部地址或固定公网 IP 编译进 Vite。
3. 文件上传/下载/预览要使用服务端签发的短期凭证，并正确处理过期、重试和 Range。
4. 修改空间、文件、插件或工作流接口时，同步检查数据库迁移、事件契约和对应客户端 API 模块。
5. 内部接口不得暴露到公网；不要信任客户端自行提交的用户/空间身份头。

## 6. 真实接口来源

- Platform Controller：`PrivateCloudDisk-platform-service/src/main/java/org/project/control/`
- Storage API：`PrivateCloudDisk-storage-service/app/api/`
- Web API 模块：`PrivateCloudDisk-web/src/api/modules/`
- Gateway 路由：`PrivateCloudDisk-gateway-service/src/main/resources/application*.properties`
- 事件契约：`contracts/events/`
