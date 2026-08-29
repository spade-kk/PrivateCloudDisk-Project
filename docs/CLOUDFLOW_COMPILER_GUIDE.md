# CloudFlow Compiler 与 Runtime 使用指南

本指南对应 CloudFlow V1 正式规范。语言、IR、诊断与验收示例的真源分别是：

- [CLOUDFLOW_DESIGN.md](./CLOUDFLOW_DESIGN.md)
- [CLOUDFLOW_IR_DESIGN.md](./CLOUDFLOW_IR_DESIGN.md)
- [CLOUDFLOW_ERROR_DESIGN.md](./CLOUDFLOW_ERROR_DESIGN.md)
- [CLOUDFLOW_DEMO_DESIGN.md](./CLOUDFLOW_DEMO_DESIGN.md)
- [CLOUDFLOW_EXPRESSION.md](./CLOUDFLOW_EXPRESSION.md)（表达式子系统，唯一表达式实现方）

## Cargo 工作区与 crate 布局（2026-08-21 统一执行引擎）

`PrivateCloudDisk-cloudflow-runtime` 为 Cargo 工作区，三个 crate：

| crate | 职责 | 关键约束 |
|---|---|---|
| `crates/cloudflow-engine-core` | **纯执行核心**：领域 AST（`ast`）、诊断（`diagnostic`）、Workflow IR（`ir`）、IR 契约校验（`ir_validate`，唯一实现）、表达式子系统（`expression`）、执行语义纯函数层（`execution_core`）、统一调度驱动（`engine/driver` + `EngineDeps` 依赖注入面 + `InMemory*` 依赖实现 + `RealClock/VirtualClock`）、开发调试面（`dev_exec`）、DAG 引擎（`runtime`） | 无数据库/Redis/MQ/HTTP/gRPC 依赖；不持有全局可变状态；双执行面与 CLI 共用的唯一实现 |
| `crates/cloudflow-agent` | **Capability Agent**（gRPC）：`CapabilityInvoker`/`GrpcCapabilityInvoker`（客户端）+ `CapabilityAgentProxy`/`CapabilityAgentServer`（服务端，转交 Workflow Capability Hub）；能力解析、最小权限校验、审计与 builtin/api/plugin 路由 | 仅宿主 crate（生产执行面）依赖；proto 与 build.rs 随 crate 迁移 |
| 根 crate `pcd-cloudflow-runtime` | 前端编译器（`parser`/`grammar.pest`/`semantic`/`compiler`/`yaml`）、**生产执行面**（`src/execution.rs`：`MysqlStateStore`/`AgentActionExecutor`/`TracingLogSink`/`ProductionConfigProvider` 四个依赖实现 + `ExecutionCoordinator`）、HTTP 服务（`http.rs`，含开发调试入口与生产端点）、MQ broker、CLI（`src/bin/cloudflowc.rs`） | 根层 `pub use cloudflow_engine_core::{...}` 再导出，既有 `crate::ast` / `crate::ir` / `crate::dev_exec` 等路径不变 |

依赖方向（无环）：`cloudflow-agent` → `cloudflow-engine-core`；根 crate → 两者。
生产面与调试面行为分叉**仅**由注入 `cloudflow_engine_core::engine::deps::EngineDeps`
的具体实现决定（需求 §1.17）；控制流语义双执行面不重复定义。

## 表达式子系统（统一表达式解析）

表达式的词法、解析与 AST 构建由**表达式子系统**统一承担（`crates/cloudflow-engine-core/src/expression/`，宿主 crate 根层再导出为 `crate::expression`，真源规格见
[CLOUDFLOW_EXPRESSION.md](./CLOUDFLOW_EXPRESSION.md)）：CloudFlow DSL 前端只把表达式**字符串**
交给 `crate::expression::parse_expression_string` / `parse_value_string`，不在前端重复构建表达式。
表达式语法与领域表达式 AST（`crate::ast::ExpressionNode`）由此前的 DSL `parser.rs` **完整抽取**
进子系统 `crates/cloudflow-engine-core/src/expression/parser.rs`（只增不减），并通过 `--emit-ast` / IR 输出回归测试确认与
抽取前完全一致。YAML / 未来前端语言直接复用本子系统，不得自行实现表达式（需求 6.1/6.31）。

**表达式语法同步**：子系统 `crates/cloudflow-engine-core/src/expression/grammar.pest` 是唯一事实来源；pest 不支持跨文件
include，故 DSL `src/grammar.pest` 保留结构一致的表达式/值规则作为切分定位器，两处必须
**逐字同步**（扩展表达式语法时，二者、以及 `syntax-highlight/generator/config.py` 的引用/函数
配置需一起改，详见 `crates/cloudflow-engine-core/src/expression/README.md` 第 5 节与 `docs/CLOUDFLOW_EXPRESSION.md`）。

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
`<id>.output` 只作为单向迁移输入，进入 AST 后立即规范化。表达式语法层（与表达式子系统同步）
同时识别 `input.<name>` / `env.<key>` 引用、属性访问 `object.property` 与索引访问 `list[0]`
（`--emit-ast` 可见）；但这些扩展命名空间/裸路径目前由语义层单独把关——完整编译时仍须是
`vars.` / `steps.` / `workflow.` 或作用域局部引用，否则返回明确的 `CF2002` 非法引用诊断。

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

## YAML 前端（第二前端语言）与 `--lang`

`cloudflowc` 支持两种前端语言，最终统一编译到 `workflow.cloudflow.io/v1` IR（需求 2.x/13.x）：

- **CloudFlow DSL**（`.flow`，默认）；
- **CloudFlow YAML**（`.flow.yaml` / `.workflow.yaml` / `.yaml` / `.yml`，使用 `serde_yaml_ng`
  解析，`src/yaml/`，设计见 `docs/CLOUDFLOW_YAML_DESIGN.md`）。

```bash
./target/release/cloudflowc compile examples/yaml/simple_file_process.flow.yaml        # 自动识别 YAML
./target/release/cloudflowc compile examples/yaml/switch_document_parser.flow.yaml --emit-ast --no-color
./target/release/cloudflowc compile --lang yaml -i 'workflow: {name: demo}
steps: []'
cat x.flow.yaml | ./target/release/cloudflowc compile --lang yaml                     # stdin
./target/release/cloudflowc compile examples/yaml/simple_file_process.flow.yaml --check-only
./target/release/cloudflowc compile examples/yaml/simple_file_process.flow.yaml -o /tmp/ir.json
```

- `--lang dsl|yaml` 显式指定语言，覆盖扩展名自动识别；`-i`/stdin 读取源码时必须配合 `--lang`
  （CLI 以 `language` 字段承载，见 `src/bin/cloudflowc.rs`）。
- YAML 与 DSL 走同一「YAML→Domain AST→统一语义→IR」路径（`compile_source_named_for_language`），
  语义合法的同名 DSL/YAML 生成**等价 IR**（回归 `tests/cloudflow_yaml.rs`）。
- `--emit-ast` 与 `--emit-domain-ast`（别名）对 YAML 同样生效：输出 YAML 解析得到的领域 AST。
- YAML 表达式字段（`when/if/switch/foreach` 等）均为 `${{ ... }}` 字符串，由表达式子系统解析，
  YAML 不重复实现表达式能力（需求 6.31/28.56；CloudFlow YAML 只定义 `${{ }}` 分隔符，对标 GitHub Actions）。
- YAML 错误码：`CFY-1001`（解析）/ `CFY-1002`（转换·语义，如重复声明）/ `CFY-SCHEMA-1001..1004`
  （Schema 形状校验：必填/类型/未知字段/非法值），诊断结构与 DSL 一致，并标注 `yaml` 语言、
  YAML 字段路径与行/列。`CFY-1003` 已由 `CFY-SCHEMA-1003/1004` 取代。

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

### 编译产物缓存（19.17，2026-08-21 落地）

HTTP 编译接口内置进程内缓存（`src/compile_cache.rs`，`CompileCache`）：

- 缓存粒度为**完整编译结果**（成功 IR 或失败诊断列表均可缓），键 =
  源码 SHA-256 + 文件名 + 语言 + 目标 IR 版本 + 能力目录指纹（排序后哈希，
  与注入顺序无关）；
- **仅默认文件名（`<request>`）请求参与缓存**：此时 include 无物理根目录必然被拒
  （CF3103），结果完全由请求内容决定，无陈旧化风险；携带 `.flow` 路径的请求禁用
  缓存（include 可能读取本地模块文件）；
- 容量 256 条超限整体清空（粗粒度策略）；命中/未命中计数供可观测性使用
  （19.8/19.23 编译侧指标）；
- 不跨进程、不落盘：进程重启即失效；CLI（`cloudflowc`/编译器内核）不经过此缓存。

### 编译性能基线（19.18/19.22，2026-08-21 落地）

`benches/compile_bench.rs`（稳定版 harness，无额外依赖）：

```bash
cargo bench --bench compile_bench
```

发布前基线（M-series，dev 前端未启用优化也可参考，release 数据更低）：

| 路径 | 中位耗时（n=50） | 需求 |
|---|---|---|
| DSL 编译（`weekly_sales_report.flow`，12 步骤） | ~0.3 ms | 19.1 不位化 |
| YAML 编译（同义示例，含 serde_yaml_ng + Schema + 表达式注入） | ~0.35 ms | 19.2 （目标 < 100ms，远低于上限） |
| 表达式解析（缓存命中路径） | ~1.7 µs | 19.3 |

安全边界测试（19.19/19.24/19.25）：`tests/cloudflow_security_bounds.rs`（YAML 嵌套/别名爆炸/
超长源码/表达式超长与超嵌套/求值沙箱/超大 HTTP 请求体不泄露路径）；依赖安全扫描与
安全墠囤常量一致性校验脚 `scripts/security-audit.sh`（19.20/19.28）。

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
| `CLOUDFLOW_TEST_AGENT_ENDPOINT` | 无 | 仅 `POST /api/dev/execute` 的 `profile=agent`（生产仿真画像）使用：测试环境 Capability Agent 的 gRPC 端点；未配置时该画像返回 400 |
| `CLOUDFLOW_ENABLE_DEBUG_EXECUTE` | `false` | 为 `true` 时才**注册**开发调试执行入口 `POST /api/dev/execute` 与 `GET /api/dev/openapi.json` 路由（纯内存执行，不写数据库；开启态要求与生产相同的 `X-PCD-Service-Token`）。默认关闭：路由不存在，请求命中 axum 默认 404（空响应体、零特征泄露）；生产环境保持关闭（见 [CLOUDFLOW_DEV_EXECUTE.md](./CLOUDFLOW_DEV_EXECUTE.md) §7/§8） |

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

## 开发调试执行入口（Dev-Execute）

开发/调试场景可直接执行 IR，无需数据库/MQ/Redis：

- CLI：`cloudflowc dev-execute IR_FILE [--var k=v] [--breakpoint NODE] [--single-step]
  [--timeout 5m] [--report report.md] [--output-format json]`；
- HTTP：`CLOUDFLOW_ENABLE_DEBUG_EXECUTE=true` 才注册 `POST /api/dev/execute`
  与 `GET /api/dev/openapi.json`（关闭态 = 路由不存在 → 默认 404 空响应体；
  开启态要求 `X-PCD-Service-Token`；422 返回 IR 契约校验问题 CFI-7xxx；
  200 返回完整 `DevExecutionResult`；请求体支持 `profile: inmem|agent`——
  `agent` 画像经 `CLOUDFLOW_TEST_AGENT_ENDPOINT` 真实调用测试 Agent，
  状态/日志仍在内存）。

架构（**统一调度驱动** `cloudflow_engine_core::engine::driver` + `EngineDeps` 依赖注入 +
`execution_core` 纯函数共享层；生产面注入 MySQL/Agent/tracing 实现，调试面注入内存实现）、
IR 契约校验器（CFI-7xxx，与生产
`RuntimeEngine::load`/`/ir-validate` API 同一校验器）、内存执行引擎语义（CFD-81xx）、
动作执行器契约、安全模型、生产入口兼容与测试报告，详见
[CLOUDFLOW_DEV_EXECUTE.md](./CLOUDFLOW_DEV_EXECUTE.md)。示例 IR 见
`PrivateCloudDisk-cloudflow-runtime/examples/ir/`（由 `tests/cloudflow_examples_ir.rs` 回归）。

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
