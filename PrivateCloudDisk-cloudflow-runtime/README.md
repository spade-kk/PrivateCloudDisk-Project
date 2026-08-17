# CloudFlow Compiler & Runtime

本 crate 同时交付两个独立可执行文件：`cloudflowc` 编译 `.flow` 为
`workflow.cloudflow.io/v1` Workflow IR，`cloudflow-runtime` 通过受内部令牌保护的 Axum API
加载/校验 IR 并管理执行状态。解析器使用 Pest PEG，HTTP 使用 Tokio/Axum，JSON 使用 serde，
诊断使用统一的 CloudFlow Error Design 契约。

Runtime 不连接主业务库、不接收浏览器请求，也不持有用户 JWT 或插件包写权限；业务身份、空间
权限和 Capability 快照由 Workflow Service 作为编译上下文传入。

## 本地验证

本机需要 Rust stable（`cargo`/`rustc`）。

```bash
cargo test --locked
cargo run --bin cloudflowc -- compile examples/weekly_sales_report.flow -o /tmp/weekly.ir.json
cargo run --bin cloudflowc -- compile examples/weekly_sales_report.flow --check-only
PCD_INTERNAL_SERVICE_TOKEN=change-me cargo run --bin cloudflow-runtime
```

HTTP 编译接口为 `POST /internal/v1/cloudflow/compile`，请求头必须携带
`X-PCD-Service-Token`，请求体为 `{source, filename, userId, spaceId}`；错误响应直接返回
`CLOUDFLOW_ERROR_DESIGN.md` 的 `diagnostics[]`，不暴露 Rust Debug 文本。

## DSL 示例

完整销售周报示例见 `examples/weekly_sales_report.flow` 与
`docs/CLOUDFLOW_DEMO_DESIGN.md`，支持 metadata、variables、schedule、嵌套 action、condition、
retry、on_failure 和插件能力调用。

历史 `automation.pcd/v1` YAML 不再接受；迁移通过 Workflow Service 新建 CloudFlow 版本完成。
