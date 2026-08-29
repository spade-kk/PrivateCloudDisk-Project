# PrivateCloudDisk-workflow-service

工作流定义、能力中心、执行和市场服务。负责工作流 DSL 校验、版本与发布、**能力中心
（Capability Hub）统一解析/校验/分发/审计**、执行记录以及工作流模板市场。

## 技术栈

- Java 21（以构建配置为准）
- Spring Boot 3.4.7
- MyBatis、Flyway、MySQL
- RabbitMQ、REST API

## 职责边界

- 管理工作流定义、版本、发布和执行记录
- **能力中心**：统一注册、解析、Schema 校验、权限收敛、路由分发与审计
  （内置能力 + 平台 API 能力 + 插件能力，Rust Agent 仅透传）
- 接收自动化或调度触发，执行插件/平台能力链
- 提供工作流市场的发现、发布和导入入口
- 不负责文件存储、插件包生命周期或 Cron 扫描

## 能力中心

- 能力键命名：`builtin:{svc}.{method}` / `api:{svc}.{method}` / `plugin:{plugin_id}:{cap}:{ver}` /
  `local_plugin:{...}`，格式白名单校验（`CapabilityKeyValidator`，防注入）。
- 平台 API 能力（11 项，V4 注册）：文件 `metadata/scan/content/list/search/tag`、空间 `info/members`、
  用户 `user.info`、`notification.send`、`share.create`。路由与权限矩阵见
  `docs/CAPABILITY_HUB.md`（仓库根 `docs/`）。
- 统一入口：`POST /internal/v1/capabilities/invoke`，需 `X-PCD-Service-Token`（`pcd.internal-service-token`），
  缺失/伪造返回 `AUTH-UNAUTHENTICATED`（401）。
- 审计表：`pcd_capability_audit`（能力键、调用方、execution/step、用户/空间、参数摘要、结果与耗时）。

### 面向 CloudFlow Language Server 的能力发现

`GET /capabilities` 与 `GET /capabilities/{key}` 是已认证的能力发现接口，供 Web IDE 和
`cloudflow-ls` 读取当前主体可见的 Action/Schemas，**不是匿名能力目录**：

- Gateway 校验 `Authorization: Bearer <access-token>`，移除客户端伪造身份头并注入可信
  `X-User-Id`；可选 `X-Space-Id`、`X-Tenant-Id` 仅用于当前上下文过滤。
- `CapabilityHubService.searchVisibleTo` / `getVisibleTo` 先读取当前用户在空间中的授权，随后校验
  `required_permissions_json` 和 `availability_policy_json` 的 `enabled`、`tenant_ids`/`tenantIds`、
  `space_ids`/`spaceIds`。策略不合法或上下文不足时 deny-by-default。
- 未认证返回 `WF-CAPABILITY-UNAUTHENTICATED`（401）；无权访问单项能力返回 404，避免将隐藏能力作为
  编辑器补全信息泄露。能力调用仍只能走服务间 `POST /internal/v1/capabilities/invoke`，不能被 LS 绕过。
- LS 按 `SHA-256(token) + tenant + space` 缓存结果，默认 5 分钟；插件、权限或策略变更时客户端发送
  `cloudflow/capabilitiesChanged` 或执行 `cloudflow.clearCapabilityCache` 以刷新当前会话。

CloudFlow LS 的传输、客户端和安全契约见
`PrivateCloudDisk-cloudflow-runtime/docs/language-server/README.md`。

## 快速开始

    ./gradlew test
    ./gradlew bootRun

服务通过根目录 Compose 的 automation profile 参与联调。运行参数以 src/main/resources/application.yml 为准
（平台 API 数据面需配置 `pcd.platform-url` / `pcd.storage-url`，默认 `http://localhost:8081` /
`http://localhost:8093`；服务凭证默认 `PCD_INTERNAL_SERVICE_TOKEN=test`，生产必须覆盖）。
