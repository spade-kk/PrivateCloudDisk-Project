# Changelog

All notable changes to `PrivateCloudDisk-plugin-service` are documented in this file.

## [Unreleased]

### 插件执行可观测性（PLUGIN-EXEC-OBS-001）

- 新增执行详情、Docker 风格日志分页/SSE、日志下载、能力审计分页、单条审计详情与审计 JSON
  导出接口；所有读取路径复用插件所有者或空间 `PLUGIN_MANAGE` 授权门禁。
- 新增 `V6__plugin_execution_observability.sql`：日志行、审计调用链、每执行序号游标和内部
  at-least-once 投递去重表。日志与审计均支持游标分页和对应索引，避免列表接口扫描大字段。
- 新增受信内部 `/internal/v1/executions/observability/batch` 写入接口；公开浏览器接口不能写入
  运行日志或能力审计。
- 日志、JSON 参数、输出与自然语言摘要在持久化前统一二次脱敏；下载接口改为分页流式输出，
  不再用隐藏条数上限截断完整执行记录。

### 兼容性

- 原 `pcd_plugin_execution_log` 摘要列表和 `/internal/v1/executions/batch` 保持不变；新数据表
  只补充执行详情能力，不改变已有文件生命周期和插件执行状态语义。
