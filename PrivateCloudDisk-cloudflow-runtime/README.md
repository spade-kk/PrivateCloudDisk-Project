# CloudFlow Compiler & Runtime

本仓库是 Cargo 工作区，宿主 crate `pcd-cloudflow-runtime` 交付两个独立可执行文件：

- `cloudflowc`：Pest → 带 Span AST → 语义分析 → `workflow.cloudflow.io/v1` IR；`dev-execute`
  子命令直接执行 IR（纯内存，不依赖数据库/gRPC）；
- `cloudflow-runtime`：使用 Tokio/Axum/serde_json 暴露编译、IR 校验和执行状态适配接口；
- `cloudflow-ls`：使用 JSON-RPC/LSP 为 VS Code、Web Studio、JetBrains/桌面端提供编辑期语义智能，
  只依赖 Compiler API，不调用 Runtime 执行面。

Crate 布局（依赖方向：宿主 crate 与 CLI → `cloudflow-engine-core`；宿主 crate → `cloudflow-agent`）：

| crate | 位置 | 职责 |
| --- | --- | --- |
| `pcd-cloudflow-runtime` | 仓库根 | 双前端（DSL `parser` + YAML `yaml`）、语义分析、IR 生成、HTTP 服务、生产执行面 `execution`（持久化调度器 + 执行协调器）、Agent gRPC 服务入口 |
| `cloudflow-engine-core` | `crates/cloudflow-engine-core` | 语言无关共享层：领域 AST（`ast`）、诊断（`diagnostic`）、Workflow IR（`ir`）、IR 契约校验器（`ir_validate`）、表达式子系统（`expression`）、执行语义核心（`execution_core`）、统一调度驱动（`engine`，`EngineDeps` 依赖注入）、开发调试面（`dev_exec`，内存状态/日志/Mock 执行） |
| `cloudflow-agent` | `crates/cloudflow-agent` | Capability Agent（gRPC）：生产执行面唯一能力调用出口（能力解析、最小权限校验、审计、builtin/api/plugin 路由） |
| `cloudflow-ls` | `crates/cloudflow-ls` | Language Server：stdio/TCP/UDS/WebSocket、会话文档管理、Compiler 诊断/符号投影、按用户/租户/空间隔离的 Capability Hub 动态补全 |

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

安全与性能（需求 19.x）：

```bash
cargo bench --bench compile_bench   # 编译性能基线（19.18/19.22）
scripts/security-audit.sh          # 边界测试 + 依赖扫描（cargo-audit 可用时）+ 护栏常量一致性
cargo test --test cloudflow_security_bounds   # YAML 炸弹/表达式超长与超嵌套/求值沙箱/路径泄露
```

构建产物：`target/release/cloudflowc` 与 `target/release/cloudflow-runtime`。

## 静态语法高亮、基础补全与 Language Server

CloudFlow DSL 的静态语法高亮与基础补全均以 `src/grammar.pest` + `crates/cloudflow-engine-core/src/ast.rs` 生成的统一规范为**唯一事实来源**
（高亮：`syntax-highlight/build/cloudflow.syntax-highlight.json`；补全：
`syntax-highlight/build/cloudflow.completion.json`）。高亮可转换为 VS Code TextMate、Monaco Monarch
与 Highlight.js 三种格式；基础补全驱动 VS Code 扩展与前端 Monaco 的离线 CompletionItem / Snippet。
动态诊断、类型、符号、能力 Hover/SignatureHelp/Definition/References/Rename 则由独立的
`cloudflow-ls` 复用 Compiler API 提供，绝不在 `syntax-highlight` 或浏览器中复制语言语义。

```bash
python3 syntax-highlight/generator/generate.py --verbose   # 一键：规范+补全+三格式+Web 分发
# 分步：build_spec.py（高亮规范）、completion_builder.py（补全规范）、
#       convert.py（三格式）、completion_convert.py（VS Code 片段 + Web）
node --check syntax-highlight/vscode/src/extension.js       # 校验补全提供器语法
cd syntax-highlight/vscode
npm run test:extension                                       # 校验扩展命令、Grammar 与打包契约
npm run vsce:package                                         # 生成并打包当前平台 cloudflow-ls
cd ../..
python3 -m unittest discover -s syntax-highlight/generator/tests -p "test_*.py"
# LSP：复用 DSL/YAML Parser、AST、表达式与现有语义规则，且不启用 Runtime 服务面
cargo check -p cloudflow-ls
cargo test -p cloudflow-ls
cargo check -p pcd-cloudflow-runtime --no-default-features --features compiler-api
```

目录：`syntax-highlight/generator/`（解析/转换/补全脚本）、`syntax-highlight/build/`（生成产物）、
`syntax-highlight/vscode/`（VS Code 扩展，含补全 provider、片段、打包脚本和内置 LS）、
`syntax-highlight/demo/`（演示）。VS Code 扩展的默认 LS 配置是 `cloudflow.lsp.serverPath=bundled`；
需要自定义版本时仍可配置绝对路径或 PATH 命令名。
用法详见 `syntax-highlight/generator/README.md`、`docs/CLOUDFLOW_LANGUAGE_EXTENSION_GUIDE.md` 与
`docs/CLOUDFLOW_COMPLETION.md`。Language Server 的架构、认证、部署、协议和 IDE 边界见
[`docs/language-server/README.md`](docs/language-server/README.md) 与
[`docs/language-server/PROTOCOL.md`](docs/language-server/PROTOCOL.md)。

## CLI

```bash
./target/release/cloudflowc compile examples/weekly_sales_report.flow
./target/release/cloudflowc compile examples/weekly_sales_report.flow -o /tmp/weekly.ir.json
./target/release/cloudflowc compile -i 'workflow "demo" { trigger { manual {} } step run { action file.list {} } }' --check-only
./target/release/cloudflowc compile broken.flow --output-format json --no-color --explain
# AST 可视化：输出层级语法树（调试/审计），不生成 IR
./target/release/cloudflowc compile examples/weekly_sales_report.flow --emit-ast --no-color
./target/release/cloudflowc compile examples/weekly_sales_report.flow --emit-ast --output-format json
# YAML 前端（第二前端语言）：按扩展名自动识别，也可 --lang 显式指定
./target/release/cloudflowc compile examples/yaml/simple_file_process.flow.yaml -o /tmp/yaml.ir.json
./target/release/cloudflowc compile examples/yaml/simple_file_process.flow.yaml --emit-ast --no-color
./target/release/cloudflowc compile --lang yaml -i 'workflow: {name: demo}\nsteps: []'
# 开发调试执行入口：直接执行 IR（纯内存，无数据库；详见 docs/CLOUDFLOW_DEV_EXECUTE.md）
./target/release/cloudflowc dev-execute tests/fixtures/ir/01_sequential.json --var 'files=["a.xlsx"]'
./target/release/cloudflowc dev-execute tests/fixtures/ir/11_variables_expressions.json --var a=2 --var b=3 --var items="[{\"size\": 5}, {\"size\": 0}]"  --output-format json
./target/release/cloudflowc dev-execute tests/fixtures/ir/08_exception_try_catch_finally.json --breakpoint compensate --report /tmp/dev-report.md
```

完整参数见 `cloudflowc compile --help` 与 `cloudflowc dev-execute --help`。`--emit-ast`（`-A`）在 Parser 后输出纯语法 AST 树
（`src/ast_printer.rs`），支持彩色文本 / JSON / 写文件，`--check-only` 优先于 `--emit-ast`。
CloudFlow YAML 只定义 `${{ ... }}` 一种表达式/插值分隔符（对标 GitHub Actions），**不接受**旧
`automation.pcd/v1` 包装（`apiVersion/kind/metadata/spec/limits`、`uses/needs/result`）；旧示例已
一次性转化为 `examples/yaml/weekly_sales_report.flow.yaml`。详见 `docs/CLOUDFLOW_YAML_GITHUB_ACTIONS_ALIGN.md`。

## 表达式子系统（唯一表达式实现方）

表达式的词法、解析与 AST 构建统一由**表达式子系统**承担：`crates/cloudflow-engine-core/src/expression/`
（`grammar.pest` +
`parser.rs` + `builtins.rs` + `eval.rs`），规格见 `docs/CLOUDFLOW_EXPRESSION.md`
（`expr.cloudflow.io/v1`，`API_VERSION` 独立于前端语言版本）。

- 表达式语法与领域表达式 AST（`ast.rs` 的 `ExpressionNode`）由此前的 DSL `parser.rs` **完整抽取**
  进子系统（只增不减），DSL 前端只把表达式**字符串**交给 `parse_expression_string` /
  `parse_value_string`，不再保留任何表达式构建代码。
- 供 CloudFlow DSL 与 YAML 等前端共享（需求 6.1/6.31）：YAML 前端已落地并把 `${{ ... }}` 表达式
  **字符串**委托本子系统解析（见下一节）；表达式扩展只允许写入子系统 `grammar.pest` 与
  `builtins.rs`（`KB/MB/GB` 常量 + 白名单函数可按需登记）。
- **求值器集中**：内建函数实现唯一收敛于 `eval.rs::call_builtin`（19 个白名单函数，含 4 个 GitHub 对齐，需求
  6.11/6.18/6.22/6.25/6.27），生产执行端 `execution.rs::call` 只委托；`null` 字面量与索引访问
  （`vars.files[0]` / `steps.parse.output[2]`）词法、语义与运行期求值均已实现。
- **解析缓存与资源防线（19.3/19.16，2026-08-21 落地）**：`parse_expression_string`/
  `parse_value_string` 各带全局缓存（容量 1024，缓存 rebase 前的相对坐标结果，
  结果与无缓存等价）；长度 ≤ 16K 字符（`CFY-EXPR-103`）与嵌套 ≤ 512（`CFY-EXPR-104`，
  O(n) 预扫交付 PEG 递归栈溢出）；边界测试见 `tests/cloudflow_security_bounds.rs`，
  详细说明见 `docs/CLOUDFLOW_EXPRESSION.md` §3.1。
- 回归保障：`tests/cloudflow_expression.rs`（17 项新测试：含 null、扩展内建函数、GitHub-Actions 对齐函数、API 版本、GitHub-Actions `${{ }}` 双大括号插值与
  `dsl_sync_*` 双文件同步回归）+ 既有 coverage/compliance/v12/ast 套件
  全部通过，证明抽取前后 IR、错误诊断与 AST 可视化输出一致。

## YAML 前端（第二前端语言）

YAML 是 CloudFlow 的**第二个前端语言**（与 DSL 共存），面向声明式、低复杂度工作流，最终与 DSL
统一编译到 `workflow.cloudflow.io/v1` IR（`src/yaml/`，设计见 `docs/CLOUDFLOW_YAML_DESIGN.md`、
`docs/CLOUDFLOW_YAML_DEMO_DESIGN.md`）。

- **识别**：`.flow.yaml` / `.workflow.yaml` / `.yaml` / `.yml` → YAML
  （根层前端调度器 `cloudflow_runtime::language_of`）；CLI `--lang dsl|yaml` 可显式覆盖
  （`-i`/stdin 需配合）。入口：`compile_source_named_for_language(source, filename, Language, catalog)`。
- **分层**：`serde_yaml_ng 0.9`（成熟第三方库，不自行实现 YAML 解析）→ 强类型 `YamlWorkflow` →
  共享 Workflow Domain AST（`crate::ast`）→ 统一语义 → 统一 IR。YAML 不复制 `ast.rs`；
  `yaml` 模块只导出 `parse_yaml` / `parse_yaml_detailed`，跨前端概念（`Language` / `language_of` /
  `parse_frontend_detailed`）收敛在 crate 根层。
- **表达式**：YAML 只切出 `${{ ... }}` 字符串（CloudFlow YAML 唯一分隔符，对标 GitHub Actions），
  交给 `crate::expression` 子系统解析（`whole_expression_index` / `parse_interpolated_value`），
  不重复定义文法/AST；DSL 插值仍用 `${...}`。
- **控制流**：`switch/cases/default`、`foreach`、`parallel`、`approval`→`approval.request`、顶层
  `catch/finally` 均映射到真实 `FlowNode`；`depends` 支持字符串或数组并做别名展开。
- **GitHub-Actions 对齐（需求 28.x/6.32）**：`${{ }}` 统一表达式/插值、插件能力冒号
  `plugin:<id>:<function>@<version>`（与 DSL `action plugin {}` 同一 `ActionNode`、同一能力 Hub 键）、
  步骤层 `with`/`if`/`retry`/`timeout` 语义，详见 `docs/CLOUDFLOW_YAML_GITHUB_ACTIONS_ALIGN.md`。
- **Schema 校验层（需求 31.x）**：`src/yaml/schema.rs` 作为编译**第一步**做形状校验
  （必填/类型/未知字段/非法值），一次收集多条 `CFY-SCHEMA-1001..1004` 错误，每条带 **YAML 字段路径**
  （`steps[2].retry.count`）与行号、修复建议；`convert.rs` 不再重复报形状错误。
- **错误码**：`CFY-1001`（解析）/ `CFY-1002`（转换·语义，如重复声明）/ `CFY-SCHEMA-1001..1004`
  （Schema 形状校验），`CFY-1003` 已由 `CFY-SCHEMA-1003/1004` 取代；与 DSL 共用诊断结构。
- **JSON Schema**：`schemas/yaml-workflow.schema.json`（draft-07）由 `cloudflow_runtime::emit_yaml_json_schema`
  统一定义生成（需求 31.10/31.18），供前端 Monaco IDE 校验与补全；重新生成命令
  `UPDATE_YAML_SCHEMA=1 cargo test --test cloudflow_yaml yaml_json_schema_regenerate_with_env`。
- **示例**：`examples/yaml/`（14 个 `.flow.yaml`，对应 DEMO 1–18 设计示例 + 示例 21
  `weekly_sales_report` + 模板 `template.flow.yaml` + `invalid/` 反例集）；`tests/cloudflow_yaml.rs`（21 项：识别、映射、
  引用规整、错误码、DSL↔YAML IR 等价、全示例编译、插件冒号动作与 DSL hub 键一致、`${{ }}` 表达式
  与插值、Schema 校验层）。

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

开发调试执行入口（默认关闭，生产保持 `false`）：`CLOUDFLOW_ENABLE_DEBUG_EXECUTE=true` 才注册
`POST /api/dev/execute`（直接 IR JSON → 纯内存执行，不写数据库；401 缺/错令牌、
400 请求体非法或 `profile` 非法、422 IR 契约校验失败（CFI-7xxx）、503 `profile=agent` 测试 Agent
不可用、200 完整结果）与 `GET /api/dev/openapi.json`
（OpenAPI 3.0.3，同受门控与鉴权）。请求体支持 `profile: "inmem"`（缺省，纯内存 Mock 动作）或
`"agent"`（生产仿真：经 `CLOUDFLOW_TEST_AGENT_ENDPOINT` 真实调用测试环境 Capability Agent，
状态与日志仍仅在内存）。关闭态两条路由不存在，请求为 axum 默认 404（空响应体）。
调试面与生产面共享 `cloudflow-engine-core` 的同一套统一调度驱动（`engine`，行为完全由
`EngineDeps` 注入决定：生产面为 `MysqlStateStore` + `AgentActionExecutor` + `TracingLogSink`，
调试面为 `InMemoryStateStore` + `InMemoryLogSink` + `MockActionExecutor` 或 `profile=agent` 生产仿真
Agent 执行器）与 `ir_validate` 校验器，不重复定义执行语义。
示例 IR 见 `examples/ir/`（3 个可执行 IR + 运行说明）；语义、错误码（CFI-7xxx / CFD-81xx）与
安全模型见 `docs/CLOUDFLOW_DEV_EXECUTE.md`。

CORS 默认关闭，可通过 `CLOUDFLOW_CORS_ALLOWED_ORIGINS` 配置逗号分隔的精确 Origin 白名单。
生产 Web IDE 应由 Workflow Service 代理，禁止把内部令牌发给浏览器。

## 设计和部署文档

- `docs/CLOUDFLOW_DESIGN.md`
- `docs/CLOUDFLOW_IR_DESIGN.md`
- `docs/CLOUDFLOW_ERROR_DESIGN.md`
- `docs/CLOUDFLOW_DEMO_DESIGN.md`
- `docs/CLOUDFLOW_COMPILER_GUIDE.md`
- `docs/CLOUDFLOW_COMPLIANCE_AUDIT.md`
- `docs/CLOUDFLOW_DEV_EXECUTE.md`（开发调试执行入口：统一调度驱动 + IR 契约校验器 + 双执行面 + CLI/HTTP）
- `docs/CLOUDFLOW_SECURITY.md`（编译器/执行引擎安全白皮书：YAML 护栁、表达式沙箱、诊断防泄露、依赖扫描与安全报告）
- `examples/ir/README.md`（示例 IR：CLI `dev-execute` 与 HTTP `/api/dev/execute` 运行说明）

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

2026-08-21 统一执行引擎重构（`cloudflow-engine-core` / `cloudflow-agent` crate 抽离）后的本机验证：
`cargo fmt`、`cargo clippy --workspace --lib --bins`（仅存 5 条历史遗留告警）与
`cargo test --workspace`（205 passed / 0 failed / 3 ignored，含 2 项新增示例 IR 回归）全部通过；
基础设施契约（MySQL/RabbitMQ）仍由 CI 服务容器执行。

---

## V1.2 新语法能力（2026-08-18）

在保持 V1.0/V1.1 兼容的基础上新增：`switch/case/default` 多分支、`retry_on` 异常白名单、
`timeout { duration; on_timeout }` 块、`delay` 步骤、`environment` 声明、`namespace`、
`import ... as` 别名、`tag` 注解与 `metadata.changelog`。详见 `docs/CLOUDFLOW_V1.2_DSL_EXTENSION.md`。
新增示例：`examples/coverage/{switch,delay,timeout_block,retry_on,environment_namespace,tags_changelog}.flow`。
