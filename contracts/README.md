# PrivateCloudDisk 事件与插件契约

此目录是跨语言契约的唯一版本源。Storage、Automation、Plugin、Runtime 与 Web 只能
引用带版本的 Schema，禁止静默改变已发布字段语义。

- `events/file-content-ready-v1.schema.json`：合并完成后的预处理触发事件。
- `events/file-content-processed-v1.schema.json`：Automation 的唯一预处理终态。
- `events/file-available-v1.schema.json`：既有 `file.available` 在 Automation 内的规范化模型。
- `plugin/manifest-v1.schema.json`：插件包 `manifest.yaml` 的结构约束。

兼容规则：新增可选字段属于向后兼容；删除字段、改变类型或收紧既有枚举必须发布新主版本。
CI 应执行 JSON 语法、示例校验和破坏性变更检查。
