# CloudFlow Compiler 与 Runtime 使用指南

本指南对应 CloudFlow V1 正式规范。语言、IR、诊断与验收示例的真源分别是：

- [CLOUDFLOW_DESIGN.md](./CLOUDFLOW_DESIGN.md)
- [CLOUDFLOW_IR_DESIGN.md](./CLOUDFLOW_IR_DESIGN.md)
- [CLOUDFLOW_ERROR_DESIGN.md](./CLOUDFLOW_ERROR_DESIGN.md)
- [CLOUDFLOW_DEMO_DESIGN.md](./CLOUDFLOW_DEMO_DESIGN.md)

## 严格语法边界

CloudFlow 只接受 `.flow` 块结构语言，根节点固定为 `workflow "name" { ... }`。顶层关键字、
step 子块和控制流均由 Pest grammar 白名单约束；任意命名块、大小写错误关键字、单引号字符串、
非法引用或表达式都会返回结构化诊断。动态字段只允许出现在 `action` 参数对象中。

当前支持 metadata、variables、schedule/event/http/manual/interval trigger、runtime、step/action、
depends_on（含条件依赖 `depends_on A if <bool>`）、condition、retry、retry_on、timeout（简写与块形态）、
output、handlers/on_failure、on_error、notify、validate/expect、if/else、foreach、for（含 break/continue）、
while、parallel（含 max_concurrency）、switch/case/default、try/catch/finally、wait、assert、delay、
return、step group、use/with、environment、namespace、import-as、audit、tag 与 metadata.changelog。
引用统一写为 `vars.<name>`、`steps.<id>.output` 或受控控制流局部变量（`foreach item` 的 `item`、
`catch error` 的 `error`、`for i` 的 `i`、管道 `filter` 谓词的行字段）；Demo 中历史
`<id>.output` 只作为单向迁移输入，进入 AST 后立即规范化。

## V1.1 变量与动态控制流

```cloudflow
variables {
  request_id = input.string(required = true) # 启动 API 可提供
  retries: number = 3                        # 本地常量，不能被 API 覆盖
  labels: array = ["monthly", "sales"]       # JSON 数组，不是字符串
  options: object = {"archive": true}        # JSON 对象，不是字符串
  later: string                               # 仅 Runtime 受控写入
}
foreach item in vars.labels { step tag { action builtin.file.copy { label = item } } }
assert { len(vars.labels) > 0 }
while { vars.retries > 0 } { step poll { action builtin.file.list {} } }
```

`$ref` 与 `$expr` 是 IR 的唯一引用/表达式编码；运行时不做字符串到数字、布尔、数组或对象的隐式
转换。`foreach` 按 `runtime.max_parallel` 分批执行并受 `maxIterations`（默认 1000，硬上限 10000）
保护；`try/catch/finally` 是局部错误边界；`wait approval` 进入 `WAITING_APPROVAL`，由内部 resume
接口恢复。`match/case` 预留已由 V1.2 `switch/case/default` 落地取代；未知关键字仍被 Compiler 严格拒绝。`include "relative.flow"` 仅在 CLI/受信任文件
模式启用：路径必须位于入口 `.flow` 所在根目录，循环/逃逸返回 `CF3103/CF3104`；HTTP/IDE 内联源码模式
会拒绝 include，避免越权读取服务文件系统。

## Compiler CLI

```bash
cd PrivateCloudDisk-cloudflow-runtime
cargo build --release --locked --bins
./target/release/cloudflowc compile examples/weekly_sales_report.flow
./target/release/cloudflowc compile examples/weekly_sales_report.flow -o weekly_sales_report.ir.json
./target/release/cloudflowc compile -i 'workflow "demo" { trigger { manual {} } step run { action file.list {} } }'
./target/release/cloudflowc compile examples/weekly_sales_report.flow --target v1 --check-only
./target/release/cloudflowc compile broken.flow --output-format json --no-color --explain
# AST 可视化（调试/审计）：输出层级语法树
./target/release/cloudflowc compile examples/weekly_sales_report.flow --emit-ast
./target/release/cloudflowc compile examples/weekly_sales_report.flow --emit-ast --no-color      # 纯文本
./target/release/cloudflowc compile examples/weekly_sales_report.flow --emit-ast --output-format json  # JSON
./target/release/cloudflowc compile examples/weekly_sales_report.flow --emit-ast -o ast.txt     # 写文件（无色）
```

`compile` 是必需子命令；文件输入和 `-i` 互斥。`--target` 当前接受 `v1` 或
`workflow.cloudflow.io/v1`，`--compact` 输出紧凑 IR，`--check-only` 只执行语法与语义检查。
失败时退出码非零，默认 stderr 使用 miette 彩色诊断；`--output-format json` 返回
`diagnostics[]`，`--no-color` 适配日志管道。

### `--emit-ast`：AST 可视化输出

`--emit-ast`（短参数 `-A`）在 Parser 之后、语义分析之前，输出纯语法 AST（`src/ast_printer.rs`），
用于语法调试与审计，不生成 IR。行为约定：

- 优先级：与 `--check-only` 同时出现时 `--check-only` 优先（仅校验，不输出 AST）。
- `--output-format json` → AST 的 JSON 序列化（`{"ast": {...}}`）；默认输出层级树形文本。
- 颜色：仅终端文本使用 ANSI；`--no-color` 关闭；写文件（`-o`）默认无色。
- `--explain` 在树后附加一句说明，不改变树结构。
- `--target` 在 `--emit-ast` 下无意义（不生成 IR），按忽略处理。
- 只解析入口文件，不展开 `include`、不做语义分析（不校验依赖/能力/DAG），因此输出不代表语义合法。
- 输出不含 Span 内部偏移/列号（避免干扰可读性），只展示语法结构；相同输入产生可重复输出。
- 解析失败退出码非零，并输出既有结构化诊断。

示例（`--emit-ast --no-color`）：

```
Workflow
├── name: upload_process
├── Metadata
│   ├── display_name: <none>
│   └── ...
├── Variables
│   └── Variable
│       ├── name: max
│       ├── type: number
│       └── default
│           └── 3
├── Trigger
│   └── manual
├── steps
│   └── Step
│       ├── id: collect_files
│       ├── depends_on: init
│       └── Action
│           ├── provider: builtin
│           ├── service: file
│           └── method: list
└── Handlers
```

## Workflow IR

成功输出的唯一网络/持久化契约是 `workflow.cloudflow.io/v1`。`spec.graph.edges` 是运行时 DAG
真源，节点 `dependsOn` 仅用于源码映射；引用和表达式分别编码为 `{ "$ref": "..." }` 和
`{ "$expr": ... }`，不与普通字符串混淆。Runtime 对未知大版本拒绝，对 `extensions` 中未知扩展
保持前向兼容。

## Runtime HTTP 编译接口

Runtime 默认只绑定内部网络，必须配置非空 `PCD_INTERNAL_SERVICE_TOKEN`。Workflow Service 使用：

```http
POST /api/v1/compile
Content-Type: application/json
X-PCD-Service-Token: <service-token>

{
  "source": "workflow ...",
  "filename": "weekly.flow",
  "target_ir_version": "v1",
  "userId": "...",
  "spaceId": "..."
}
```

成功返回 `valid=true` 与 `ir`；失败返回 422、`valid=false` 和完整 `diagnostics[]`。诊断包含
`code/severity/category/message/location/source/suggestions/help/documentationUrl/cliOutput`，不会
返回 Rust Debug 文本或内部绝对路径。请求体上限 1 MiB、超时 30 秒，并发上限由
`CLOUDFLOW_HTTP_MAX_CONCURRENCY` 配置。旧 `/internal/v1/cloudflow/compile` 仅作兼容别名。

执行控制接口为 `POST /api/v1/executions`、`GET /api/v1/executions/{id}`、`POST
/api/v1/executions/{id}/pause|resume|retry|cancel` 和 `GET /api/v1/executions/{id}/logs`。resume 请求体为
`{"approval":{"approved":true,"comment":"..."}}`，只接受处于 WAITING 的实例。生产模式下这些接口
写入 MySQL 事实源；执行 Worker 按 `spec.graph.edges` 调度，经 gRPC Capability Agent 调用能力，
步骤检查点、Inbox/Outbox 和执行日志均可在进程重启后恢复。compiler 模式仍只提供隔离的内存控制
适配，不能用于生产执行。

健康检查为 `GET /health`、`/health/live`、`/health/ready`。CORS 默认关闭；确需浏览器直连时，只能
通过 `CLOUDFLOW_CORS_ALLOWED_ORIGINS` 配置精确 Origin 白名单。正式 Web IDE 仍应经 Workflow
Service 调用，避免向浏览器暴露内部服务令牌。

## Workflow Service 集成

Workflow Service 只负责身份、`X-Space-Id`、权限、版本和数据库事务。保存、发布、执行前统一调用
`cloudflow.runtime.compile-url`，Java 不维护 grammar、AST、DAG 或诊断规则。连续故障会触发短时
熔断，策略固定 fail-closed，禁止将未校验 DSL 标记为可发布。

部署配置：

```yaml
cloudflow:
  runtime:
    compile-url: ${CLOUDFLOW_RUNTIME_COMPILE_URL:http://cloudflow-runtime:8091/api/v1/compile}
    circuit-failure-threshold: 3
    circuit-open-seconds: 15
    unavailable-policy: REJECT
```

## Runtime 部署参数

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `CLOUDFLOW_LISTEN_ADDRESS` | `127.0.0.1:8091` | 监听地址；容器中使用 `0.0.0.0:8091` |
| `PCD_INTERNAL_SERVICE_TOKEN` | 无 | 必填内部服务凭证，不得进入前端 |
| `CLOUDFLOW_HTTP_MAX_CONCURRENCY` | `32` | HTTP 并发上限 |
| `CLOUDFLOW_CORS_ALLOWED_ORIGINS` | 空 | 逗号分隔精确 Origin；空表示关闭 CORS |
| `CLOUDFLOW_CAPABILITIES` | 空 | 可选能力快照；非空时执行严格能力引用校验 |
| `CLOUDFLOW_RUNTIME_MODE` | `compiler` | `production` 才启用持久化执行面；缺少依赖时 fail-fast |
| `CLOUDFLOW_DATABASE_URL` | 无 | 生产必填，Rust SQLx MySQL DSN |
| `CLOUDFLOW_RABBITMQ_URL` | 无 | 生产必填，RabbitMQ AMQP DSN |
| `CLOUDFLOW_AGENT_LISTEN_ADDRESS` | 无 | 生产必填，Capability Agent gRPC 监听地址 |
| `CLOUDFLOW_CAPABILITY_AGENT_GRPC_URL` | 无 | 生产必填，Runtime 调用 Agent 的 gRPC URL |
| `CLOUDFLOW_WORKFLOW_CAPABILITY_URL` | 无 | 生产必填，Workflow Capability Hub 内部 HTTP 地址 |
| `CLOUDFLOW_WORKER_CONCURRENCY` | `8` | 实例内执行并发；实例间通过 DB 锁和 RabbitMQ 竞争消费扩展 |
| `CLOUDFLOW_STALE_SECONDS` | `180` | RUNNING 心跳失联后恢复到 READY 的阈值 |
| `CLOUDFLOW_ACTION_TIMEOUT_SECONDS` | `120` | capability action 默认超时上限 |

进程处理 SIGINT/SIGTERM 并优雅停止接收请求。镜像同时包含 `cloudflow-runtime` 与 `cloudflowc`，
运行用户为 nonroot，且不挂载 Docker Socket。

## 验证门禁

```bash
cargo fmt --all -- --check
cargo clippy --locked --all-targets --all-features -- -D warnings
cargo test --locked --all-features
./scripts/verify_coverage.sh
cargo build --release --locked --bins
```

默认测试覆盖 Demo golden、严格语法、多错误聚合、完整控制流 AST/IR、类型化引用、语义错误、DAG
调度、CLI 参数、HTTP 鉴权/限流/超大 body，以及 100 步工作流 500ms 编译预算。CI 另以 MySQL 8.4、
RabbitMQ 3.13 运行显式基础设施契约，并启动真实 Tonic gRPC Agent 代理到 Axum Capability Hub mock。

仍不得由编译或组件测试替代的发布门禁：真实 Platform/Plugin Sandbox 联调、200 节点并发压测、
Broker/DB 网络分区和进程
崩溃故障注入。

---

## 新增语法（V1.2）

V1.2 分三个 Tranche 落地，全部结构（语法/AST/IR/语义/Runtime/错误码/测试/文档）均已贯通：

- **Tranche 1**：`switch/case/default`、`retry_on`、`timeout { … }` 块、`delay`、`environment`、
  `namespace`、`import "x.flow" as alias`、`tag` 与 `metadata.changelog`。
- **Tranche 2**：`for i in range(from,to)` 索引循环与 `for x in <array>` 集合循环、`break`/`continue`、
  `parallel(max_concurrency=N)`、`validate { <bool> }`（`expect` 为其别名）。
- **Tranche 3**：`interval` 周期与 `http { path; method }` webhook 触发器详配、`on_error`、`notify`、
  `map/filter/reduce` 管道、`"${vars.x}"` 字符串模板、`audit` 注解、`step group` 步骤组、
  `use/with` 模块默认参数、条件 `depends_on A if <bool>`、步骤级 `return`。

全部语法与错误码见 docs/CLOUDFLOW_V1.2_DSL_EXTENSION.md；错误码集中在 CF44xx（CF4401..CF4421）。
这些特性已由 examples/coverage/*.flow 与 tests/cloudflow_v12_extension.rs 覆盖，`cargo test` 全绿。
