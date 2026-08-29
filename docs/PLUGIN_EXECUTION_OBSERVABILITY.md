# 插件执行记录、日志与能力审计

本文定义插件中心的执行详情数据面。执行列表仍然只返回摘要；日志正文、能力调用参数和导出均由 Plugin Service 在完成身份与空间授权后返回，浏览器不会连接 Plugin Runtime、对象存储或容器宿主机。

## 实施前审计与改造结论

| 范围 | 审计到的原状 | 本次落地 | 保留的边界 |
|---|---|---|---|
| Plugin Service 执行记录 | `pcd_plugin_execution_log` 只保存状态、时间和 `output_summary` | 新增行级日志、审计调用、顺序游标与内部投递去重表 | 旧列表/统计及 `/internal/v1/executions/batch` 的摘要契约不变 |
| Plugin Runtime | 容器输出可回传，但 SDK 能力调用只存在 Runtime 工作区，没有跨服务审计投影 | `pycloud` 受控能力通道记录 JSONL；Runner 折叠运行中/终态调用并带入 Runtime 结果 | 浏览器不能直接读取工作区、容器或 Runtime HTTP 接口 |
| Automation Service | 已使用稳定执行 ID 上报执行摘要 | 摘要成功后向新的内部端点投递日志行、根执行审计和 SDK 子调用 | 上报失败不反向阻塞文件生命周期；at-least-once 重放由 `observation_id` 去重 |
| 插件中心列表/抽屉 | 列表和旧侧栏仅展示输出摘要，无独立详情页 | 复用 Docker 风格日志组件、摘要/详情审计组件、受权 Drawer 和独立路由 | 真实数据只来自 Plugin Service API，前端不构造模拟调用事实 |
| 鉴权与脱敏 | 列表按插件所有者查询；没有日志/审计详情数据面 | 每个 execution/audit ID 先定位拥有者和空间，再允许拥有者或空间 `PLUGIN_MANAGE` 角色读取；服务端二次脱敏 | 审计 ID、执行 ID 都不是授权凭据；下载和 SSE 走同一门禁 |

审计中确认的生产联调前置条件：Flyway 必须执行 V6 迁移；网关必须继续向 Plugin Service
注入 `X-User-Id`；Automation 与 Plugin Service 必须配置相同的内部服务令牌；若需要容器尚未
结束时逐行实时日志，需启用 Runtime 到 Automation 的流式写入适配。后者是已有 SSE 读取能力的
上游写入改进，不可用浏览器直连 Runtime 绕过。

## 数据链路与信任边界

1. Plugin Runtime 在 Docker 沙箱中收集容器 `stdout`/`stderr`；`pycloud` 通过受控能力通道把实际 SDK 调用写入仅 Runtime 可读的 JSONL 审计文件。
2. Automation Service 使用稳定的插件执行 ID 将 Runtime 回传日志和审计事实投递至 Plugin Service 内部接口。内部投递带 `X-PCD-Service-Token`，支持按 `observation_id` 幂等重放。
3. Plugin Service 在落库前再次脱敏：凭证字段、Bearer Token 与绝对宿主路径不会返回给前端。日志按行、审计按调用分别保存，使用 `(execution_id, sequence_no)` 游标分页。
4. 外部用户仅可查看自己拥有的插件执行，或当前空间中经 Platform Service 授予 `PLUGIN_MANAGE` 的执行。审计 ID 不被视为授权凭据。

当前 Runtime 的链路响应在容器步骤结束后回传日志；Plugin Service 已提供同一授权门禁下的 SSE tail 接口，并能在受信写端持续追加日志时实时推送。生产环境若要求逐行、容器尚未结束时的实时输出，必须启用 Runtime 到 Automation 的流式写入适配，不能让浏览器直接订阅 Runtime。

## 外部 REST API

所有接口位于 Gateway 的 `/api/v1/plugins` 前缀，并要求登录身份；Gateway 负责注入 `X-User-Id`。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/executions/{executionId}` | 执行概要、入口、资源限制、日志/审计计数 |
| `GET` | `/executions/{executionId}/logs` | Docker 风格日志游标分页；支持 `cursor`、`limit`、`order=asc|desc`、`start_time`、`end_time`、`level`、`source` |
| `GET` | `/executions/{executionId}/logs/stream?after={sequence}` | 短连接 SSE tail；事件名为 `log`/`heartbeat` |
| `GET` | `/executions/{executionId}/logs/download` | 由 Plugin Service 分页流式代理下载完整 UTF-8 文本日志 |
| `GET` | `/executions/{executionId}/audit-trails` | 审计调用分页；支持 `cursor`、`limit`、`capability_type`、`status` |
| `GET` | `/audit-trails/{auditId}` | 单条调用的脱敏详情 |
| `GET` | `/executions/{executionId}/audit-trails/download` | 分页流式导出完整审计 JSON |

日志分页响应：

```json
{
  "items": [{
    "sequenceNo": 42,
    "timestamp": "2026-08-23T05:00:12.345Z",
    "level": "INFO",
    "source": "PYCLOUDSDK",
    "content": "capability api.user.info completed",
    "byteOffset": 1024
  }],
  "nextCursor": "41",
  "hasMore": false
}
```

审计调用同时提供面向普通用户的 `summary` 和面向排障人员的受控详情字段。前端“摘要/详情”切换不触发第二份数据源请求：它只改变同一条审计事实的展示方式。

```json
{
  "auditId": "66dc9d0b-14b7-4d2d-a18a-8106c2d1e001",
  "parentAuditId": "65f27d71-e11f-4d95-b48f-3e79b371d949",
  "capabilityKey": "api.user.info",
  "capabilityType": "PLATFORM_API",
  "summary": "读取了用户“u-123”",
  "inputParams": { "user_id": "u-123", "authorization": "***" },
  "outputResult": { "display_name": "张三" },
  "status": "SUCCESS",
  "durationMs": 18
}
```

## 持久化、索引和保留

迁移 `V6__plugin_execution_observability.sql` 新增：

- `pcd_plugin_execution_log_line`：以执行 ID、时间、级别、来源和 sequence 索引支持高效筛选。
- `pcd_plugin_execution_audit_trail`：保存调用树、参数/返回摘要、状态、耗时和错误摘要。
- `pcd_plugin_execution_observability_cursor`：多实例内部写入用 `SELECT … FOR UPDATE` 分配连续序号。
- `pcd_plugin_execution_observation_ingest`：内部 at-least-once 投递去重。

日志与审计保留周期遵循原 `pcd_plugin_execution_log` 的平台保留策略；清理作业必须按 execution 级联删除，不能单独清除审计根而留下子调用。下载和导出操作应继续走平台审计日志。

## 前端集成

`PrivateCloudDisk-web` 的 `pluginExecutionDetailStore` 用 execution ID 分区缓存请求、游标和流连接。`ExecutionLogViewer` 与 `ExecutionAuditTrailViewer` 被抽屉和独立路由 `/plugins/:pluginId/executions/:executionId` 复用；后者默认日志优先并在移动端自动收纳为单列。

审计摘要由后端生成，禁止前端用能力键拼装“已经修改文件”等业务断言。前端仅可将后端 `summary` 以自然语言展示，详情模式显示已经脱敏的 JSON。
