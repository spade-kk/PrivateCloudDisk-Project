# CloudFlow Compiler & Runtime

本 crate 交付两个独立可执行文件：

- `cloudflowc`：Pest → 带 Span AST → 语义分析 → `workflow.cloudflow.io/v1` IR；
- `cloudflow-runtime`：使用 Tokio/Axum/serde_json 暴露编译、IR 校验和执行状态适配接口。

诊断遵循 `docs/CLOUDFLOW_ERROR_DESIGN.md`，CLI 与 HTTP 共用同一结构，不返回 Rust Debug 字符串。
Runtime 不持有用户 JWT，不执行用户脚本；插件节点必须经 Plugin Runtime Sandbox，平台能力必须经
带用户、空间和执行上下文的 Agent 授权。

## 构建与测试

```bash
cargo fmt --all -- --check
cargo clippy --locked --all-targets --all-features -- -D warnings
cargo test --locked --all-features
cargo build --release --locked --bins
```

构建产物：`target/release/cloudflowc` 与 `target/release/cloudflow-runtime`。

## 语法高亮与代码补全（统一规范生成）

CloudFlow DSL 的语法高亮与代码补全均以 `GRAMMAR.pest` + `AST.rs` 生成的统一规范为**唯一事实来源**
（高亮：`syntax-highlight/build/cloudflow.syntax-highlight.json`；补全：
`syntax-highlight/build/cloudflow.completion.json`）。高亮可转换为 VS Code TextMate、Monaco Monarch
与 Highlight.js 三种格式；补全驱动 VS Code 扩展与前端 Monaco 的 CompletionItem / SignatureHelp、
括号配对、缩进与错误提示。前端与编辑器插件不硬编码任何高亮正则或补全规则。

```bash
python3 syntax-highlight/generator/generate.py --verbose   # 一键：规范+补全+三格式+Web 分发
# 分步：build_spec.py（高亮规范）、completion_builder.py（补全规范）、
#       convert.py（三格式）、completion_convert.py（VS Code 片段 + Web）
node --check syntax-highlight/vscode/src/extension.js       # 校验补全提供器语法
python3 -m unittest discover -s syntax-highlight/generator/tests -p "test_*.py"
```

目录：`syntax-highlight/generator/`（解析/转换/补全脚本）、`syntax-highlight/build/`（生成产物）、
`syntax-highlight/vscode/`（VS Code 扩展，含补全 provider 与片段）、`syntax-highlight/demo/`（演示）。
用法详见 `syntax-highlight/generator/README.md`、`docs/CLOUDFLOW_LANGUAGE_EXTENSION_GUIDE.md` 与
`docs/CLOUDFLOW_COMPLETION.md`。

## CLI

```bash
./target/release/cloudflowc compile examples/weekly_sales_report.flow
./target/release/cloudflowc compile examples/weekly_sales_report.flow -o /tmp/weekly.ir.json
./target/release/cloudflowc compile -i 'workflow "demo" { trigger { manual {} } step run { action file.list {} } }' --check-only
./target/release/cloudflowc compile broken.flow --output-format json --no-color --explain
# AST 可视化：输出层级语法树（调试/审计），不生成 IR
./target/release/cloudflowc compile examples/weekly_sales_report.flow --emit-ast --no-color
./target/release/cloudflowc compile examples/weekly_sales_report.flow --emit-ast --output-format json
```

完整参数见 `cloudflowc compile --help`。`--emit-ast`（`-A`）在 Parser 后输出纯语法 AST 树
（`src/ast_printer.rs`），支持彩色文本 / JSON / 写文件，`--check-only` 优先于 `--emit-ast`。
旧 `automation.pcd/v1` YAML 不再接受。

## HTTP

```bash
PCD_INTERNAL_SERVICE_TOKEN='replace-with-high-entropy-secret' \
CLOUDFLOW_LISTEN_ADDRESS='0.0.0.0:8091' \
CLOUDFLOW_HTTP_MAX_CONCURRENCY='32' \
./target/release/cloudflow-runtime
```

主编译接口为 `POST /api/v1/compile`，旧 `/internal/v1/cloudflow/compile` 是迁移兼容别名。请求头
必须包含 `X-PCD-Service-Token`，JSON 为 `{source, filename, target_ir_version, userId, spaceId}`。
请求体上限 1 MiB、超时 30 秒；健康检查为 `GET /health`、`/health/live`、`/health/ready`。
执行状态接口提供 start/status/pause/retry/cancel/logs，路径统一位于 `/api/v1/executions`。

CORS 默认关闭，可通过 `CLOUDFLOW_CORS_ALLOWED_ORIGINS` 配置逗号分隔的精确 Origin 白名单。
生产 Web IDE 应由 Workflow Service 代理，禁止把内部令牌发给浏览器。

## 设计和部署文档

- `docs/CLOUDFLOW_DESIGN.md`
- `docs/CLOUDFLOW_IR_DESIGN.md`
- `docs/CLOUDFLOW_ERROR_DESIGN.md`
- `docs/CLOUDFLOW_DEMO_DESIGN.md`
- `docs/CLOUDFLOW_COMPILER_GUIDE.md`
- `docs/CLOUDFLOW_COMPLIANCE_AUDIT.md`

## 生产执行模式

`CLOUDFLOW_RUNTIME_MODE=production` 会启用以下链路，任何必需配置缺失都会拒绝启动，不会静默
退回进程内状态：

- SQLx MySQL State Store：执行/步骤检查点、日志、心跳、失联恢复、Inbox/Outbox；
- Lapin RabbitMQ：持久命令队列、DLQ、QoS 竞争消费与 Publisher Confirm；
- 命令处理基础设施异常使用 `retry_count` 有界重投（最多 3 次），不可重试的契约/业务错误直接
  进入 DLQ，避免 `requeue=true` 形成无限循环；
- Inbox 领取态使用 5 分钟持久租约：重复投递在租约内只 ACK，Worker 崩溃或数据库异常留下的
  `PROCESSING` 记录到期后可被下一实例接管，避免“幂等去重把未完成命令永久吞掉”。
- Outbox 发布失败最多重试 10 次；超过上限进入数据库 `DEAD` 终态并告警，后续通过运维重放流程
  处理，避免 Broker 长时间不可用时无限积压。
- Tonic gRPC Capability Agent：Runtime 不直连平台数据库，调用经 Workflow Capability Hub 重新校验
  用户、空间、工作流声明权限与能力权限交集；
- Tokio 执行协调器：DAG 并发、步骤超时、固定/指数退避、暂停、取消、重试与失败处理器。

```bash
CLOUDFLOW_RUNTIME_MODE=production \
CLOUDFLOW_DATABASE_URL='mysql://pcd_cloudflow:***@mysql:3306/pcd_cloudflow' \
CLOUDFLOW_RABBITMQ_URL='amqp://user:***@rabbitmq:5672/%2f' \
CLOUDFLOW_AGENT_LISTEN_ADDRESS='0.0.0.0:50061' \
CLOUDFLOW_CAPABILITY_AGENT_GRPC_URL='http://127.0.0.1:50061' \
CLOUDFLOW_WORKFLOW_CAPABILITY_URL='http://workflow-service-backend:8087/internal/v1/capabilities/invoke' \
./target/release/cloudflow-runtime
```

基础设施契约测试需要显式测试库和 Broker，不会误连开发环境：

```bash
CLOUDFLOW_TEST_DATABASE_URL='mysql://root:***@127.0.0.1:3306/pcd_cloudflow' \
CLOUDFLOW_TEST_RABBITMQ_URL='amqp://guest:guest@127.0.0.1:5672/%2f' \
cargo test --locked --test cloudflow_infrastructure_contract \
  mysql_inbox_outbox_recovery_and_rabbit_command_are_integrated -- --ignored --exact
```

当前发布边界：编译、持久化执行骨架、MQ 与真实 gRPC→HTTP Agent 代码已经落地；本机未具备
MySQL/RabbitMQ 容器，因此基础设施契约由 CI 服务容器执行。`foreach` 动态展开、`try/catch/finally`
分支路由、wait 外部恢复、逐步骤回写 Java 控制面、真实 Plugin Sandbox 和故障注入/压测仍是生产
发布门禁，未通过前不能宣称所有 CloudFlow 控制流具备生产 SLA。

本轮代码修改后的本机验证执行了 `cargo check --locked` 与 `cargo clippy --locked --all-targets
--all-features -- -D warnings`；完整 Rust 测试重编译受工作区磁盘不足影响，应在 CI 中重新执行。

---

## V1.2 新语法能力（2026-08-18）

在保持 V1.0/V1.1 兼容的基础上新增：`switch/case/default` 多分支、`retry_on` 异常白名单、
`timeout { duration; on_timeout }` 块、`delay` 步骤、`environment` 声明、`namespace`、
`import ... as` 别名、`tag` 注解与 `metadata.changelog`。详见 `docs/CLOUDFLOW_V1.2_DSL_EXTENSION.md`。
新增示例：`examples/coverage/{switch,delay,timeout_block,retry_on,environment_namespace,tags_changelog}.flow`。
