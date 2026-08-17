# CloudFlow Compiler 与 Runtime 使用指南

本指南对应 CloudFlow V1 正式规范。语言、IR、错误诊断的真源分别是：

- [CLOUDFLOW_DESIGN.md](./CLOUDFLOW_DESIGN.md)
- [CLOUDFLOW_IR_DESIGN.md](./CLOUDFLOW_IR_DESIGN.md)
- [CLOUDFLOW_ERROR_DESIGN.md](./CLOUDFLOW_ERROR_DESIGN.md)
- [CLOUDFLOW_DEMO_DESIGN.md](./CLOUDFLOW_DEMO_DESIGN.md)

## 编译器

```bash
cd PrivateCloudDisk-cloudflow-runtime
cargo build --release --locked --bin cloudflowc
cloudflowc compile examples/weekly_sales_report.flow -o weekly_sales_report.ir.json
cloudflowc compile examples/weekly_sales_report.flow --check-only
cloudflowc compile -i 'workflow "demo" { step run { action file.list {} } }'
```

Parser 使用 Pest PEG，禁止在 JavaScript、Java 或插件脚本中重新解析 CloudFlow。`cloudflowc`
成功时输出格式化 `workflow.cloudflow.io/v1` IR；失败时退出码为 1，stderr 输出
`CF110x/CF120x/CF130x/CF200x/CF300x/CF400x` 诊断，包含文件、行、列、源码行、指针、建议和
help。`--explain` 会额外打印修复提示。

## Runtime 内部接口

Runtime 只绑定内部网络地址，必须配置非空 `PCD_INTERNAL_SERVICE_TOKEN`。Workflow Service 使用
`X-PCD-Service-Token` 调用：

```http
POST /internal/v1/cloudflow/compile
Content-Type: application/json
X-PCD-Service-Token: <service-token>

{"source":"workflow ...","filename":"weekly.flow","userId":"...","spaceId":"..."}
```

成功返回 `valid=true` 和 `ir`；失败返回 `valid=false`、`diagnostics[]`，不会返回 Rust Debug
字符串或内部绝对路径。请求体上限 2 MiB、请求超时 30 秒，Axum/serde 负责 HTTP 与 JSON 解析。
`/internal/v1/cloudflow/validate-ir` 校验 IR 版本和图边；`/internal/v1/cloudflow/executions*`
提供执行状态 API，后续由 Workflow Agent 接入 Capability Hub、MQ Outbox/Inbox 和持久化状态。

## Workflow Service 约束

Workflow Service 只负责用户/空间权限、草稿和版本持久化。保存、发布、执行前均调用 Runtime
Compiler；Java 不再包含 CloudFlow grammar、AST、DAG 或能力解析代码。编译结果的 IR graph 会
投影为旧检查点读取器需要的 `steps` 字段，迁移期间不改变已有数据库响应结构。

## 运行与部署

```bash
PCD_INTERNAL_SERVICE_TOKEN='change-me' \
CLOUDFLOW_LISTEN_ADDRESS='0.0.0.0:8091' \
./target/release/cloudflow-runtime
```

生产镜像使用非 root Distroless 用户；Runtime 不挂载 Docker Socket，不接收浏览器流量，不持有
JWT。插件节点必须进入 Plugin Runtime Sandbox，文件、空间、通知等调用必须经过带
`execution_id + step_id + attempt` 的 Agent 权限校验。

## 验证命令

```bash
cargo fmt --all -- --check
cargo test --locked
cargo run --locked --bin cloudflowc -- compile examples/weekly_sales_report.flow --check-only
```

测试覆盖 Demo golden、IR 序列化、缺失依赖诊断、DAG 拓扑和 Runtime 状态加载。Java Workflow
Service 使用 `./gradlew test --offline` 验证 Runtime 委托契约；真实 MQ、Agent、数据库恢复和
沙箱逃逸压测必须在 CI/集群发布门禁中执行。
