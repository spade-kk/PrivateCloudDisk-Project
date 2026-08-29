# CloudFlow DSL Compiler、Runtime 与 IDE 合规审计报告

审计日期：2026-08-08  
审计范围：`PrivateCloudDisk-cloudflow-runtime`、`PrivateCloudDisk-workflow-service`、
`PrivateCloudDisk-web` 工作流 IDE、CloudFlow 四份专项规范、Compose 与 CI。  
规范真源：`CLOUDFLOW_DESIGN.md`、`CLOUDFLOW_IR_DESIGN.md`、
`CLOUDFLOW_ERROR_DESIGN.md`、`CLOUDFLOW_DEMO_DESIGN.md`。

## 1. 审计结论

本轮完成 CloudFlow 编译链严格化，并落地了生产执行面的主体代码：Pest 是唯一语法解析器，AST、
语义分析、`workflow.cloudflow.io/v1` IR、CLI 与 Axum HTTP 共用同一套 Rust 实现；SQLx MySQL 保存
执行与步骤检查点，Lapin RabbitMQ 提供持久 Inbox/Outbox、命令队列、DLQ 和 publisher confirm，
Tonic Agent 把 Runtime 能力调用代理到 Workflow Capability Hub。Java 控制面以事务 Outbox 发出
命令、接收 accepted/completed 事件，旧 `WorkflowExecutionWorker` 默认关闭。Vue IDE 消费结构化
诊断并安全显示多行 `cliOutput`。项目交付 `cloudflowc` 与 `cloudflow-runtime` 两个独立可执行文件。

2026-08-08 的 V1.1 补充已把 `foreach` 分批执行、`while` 上限、`try/catch/finally` 局部错误边界、
`assert`、`wait approval` 的持久化暂停/恢复，以及强类型变量/结构化表达式落入 Runtime。顶层 AST
另行保留源码 Flow 顺序，Compiler 为控制节点前后写入顺序边，避免 `wait` 之后的副作用抢先执行。新增
`examples/coverage`、真实 CLI 编译+IR Schema 门禁、MySQL 动态控制流 CI 契约，修复了嵌套 control
step 可绕过 action/变量语义校验的问题。

仍不能把“代码实现”误表述为“所有生产 SLA 已验收”：本机没有可用 MySQL/RabbitMQ 测试环境，动态
控制流集成测试已编译并进入 CI、但未在本机实跑；动态子步骤现使用稳定 iteration ID，但真实
Platform/Plugin Sandbox、网络分区、重复消息和进程 kill 故障注入仍是 **P0 发布阻断项**。

## 2. 现有问题、严重程度与处置

| 编号 | 严重程度 | 问题定位 | 影响 | 本轮处置 |
|---|---|---|---|---|
| CF-A01 | P0 | 旧 PEG 使用通用命名块接受任意关键字 | 拼写错误或恶意结构可能被静默接受 | 改为严格关键字规则；未知块只进入恢复节点并聚合 `CF1202`，不进入 AST/IR |
| CF-A02 | P0 | 控制流曾只保存摘要且顶层分类存储会丢失源码相对顺序 | Runtime 看不到子图，或 `wait` 后步骤可能提前执行 | 补齐完整 AST/IR、动态执行器与顶层 `flow` 顺序视图；控制节点前后生成边界 edge |
| CF-A03 | P0 | Java 与 Rust 曾同时执行 Workflow | 语言升级后产生双语义和重复副作用 | Java legacy worker 默认关闭；Java 只发事务 Outbox 并投影 Rust 终态，保留类仅作显式回滚开关 |
| CF-A04 | P0 | Runtime 执行状态曾仅在内存 | 重启后丢失 READY/RUNNING 状态 | SQLx State Store、step checkpoint、心跳和 stale recovery 已实现；真实崩溃恢复故障注入仍是发布门禁 |
| CF-A05 | P1 | 旧 HTTP 适配手写协议/简单错误字符串 | 请求边界、JSON、超时与错误契约不可靠 | 使用 Tokio/Axum/serde_json/tower；限制 1 MiB、30 秒、并发、精确 CORS、内部令牌 |
| CF-A06 | P1 | 诊断只返回首错和 Debug 文本 | Monaco 无法精确定位，用户难以一次修复全部问题 | 统一 CF 诊断结构，聚合可恢复语法与语义错误，支持 miette 人类输出和 JSON |
| CF-A07 | P1 | `graph` 在文档中同时位于根层与 `spec` | 编译器、Runtime、画布可能读取不同路径 | 规范统一为 `spec.graph`；`edges` 是 DAG 权威数据 |
| CF-A08 | P1 | 变量引用与字符串混用，局部表达式类型可能绕过检查 | Runtime 无法判断值应解析还是原样传递 | IR 使用 `$ref`/结构化 `$expr`，字面量保持 JSON 原生类型；对可静态判定的算术/逻辑/三元表达式执行类型拒绝 |
| CF-A09 | P1 | Workflow Service Runtime 不可用时可能绕过校验 | 未校验 DSL 被保存或发布 | 熔断策略固定 fail-closed，返回 `CF-RUNTIME-UNAVAILABLE` |
| CF-A10 | P1 | IDE 画布曾用正则实现第二套 DSL 解析 | 复杂嵌套丢节点、连线或参数 | 切换到画布前调用 Rust Compiler，并以返回 IR 重建可执行节点和边；正则仅作无网络初始兼容 |
| CF-A11 | P1 | 画布生成 `with key = value` 与 `.cflow` | 生成源码不符合 Pest 语法和 `.flow` 规范 | 输出 action 动态字段/对象块，插件补齐 id/function/version，文件名改为 `workflow.flow` |
| CF-A12 | P2 | IDE 终端把多行编译信息挤成一行 | 错误位置和建议不可读 | 文本插值 + `white-space: pre-wrap` + `overflow-wrap:anywhere`，禁止 `v-html` |
| CF-A13 | P2 | Demo、CLI、IR 和错误文档存在历史表示 | 开发者按错误示例实现客户端 | 增加规范性收敛章节，并统一 `.flow`、引用与 `spec.graph` |

## 3. 技术实现

### 3.1 Compiler

- `grammar.pest` 明确定义根、顶层块、step 子块、控制流、表达式和 action 动态参数边界。
- `parser.rs` 将 Pest Pair 转成带 UTF-8 byte offset、行列的 AST；恢复规则用于多错误聚合，
  不能把未知语法写入 AST。
- AST 包含 `WaitNode`、`LoopNode`、`RetryNode`、`ConditionNode`、`ParallelNode`、
  `TryCatchNode`、`TimeoutConfig` 与强类型 `ValueNode/ExpressionNode`。
- `semantic.rs` 校验重复 ID、未定义依赖、DAG 环、变量引用、action/plugin 命名、输入类型和
  表达式函数白名单。
- `compiler.rs` 生成版本化 IR；handler 图不加入主成功路径，防止失败处理步骤提前执行。

### 3.2 CLI 与诊断

`cloudflowc compile` 支持文件、stdin、`-i`、`-o`、`--target`、`--check-only`、`--explain`、
`--output-format json`、`--no-color` 和 `--compact`。错误退出码非零，默认由 miette 输出带颜色、
源码位置和建议的诊断；JSON 模式输出与 HTTP 相同的字段。

### 3.3 Runtime HTTP 与持久化执行面

- `POST /api/v1/compile`：唯一推荐编译入口。
- `/internal/v1/cloudflow/compile`：迁移期兼容别名。
- `GET /health`、`/health/live`、`/health/ready`：探针。
- production 模式的 execution start/status/pause/retry/cancel/logs 使用 MySQL 事实源；compiler 模式
  仅保留隔离的内存适配用于本地 IDE。
- 内部接口要求 `X-PCD-Service-Token`；TraceLayer 不记录 header/body，避免令牌和源码泄漏。
- Runtime Worker 通过 `FOR UPDATE SKIP LOCKED` 竞争领取、心跳和 stale recovery 横向扩展；action
  经 gRPC Agent 调用，用户/空间/声明权限/授予权限取交集；基础设施异常使用带 `retry_count` 的有界重投，达到 3 次进入 DLQ，禁止无限 `requeue`。
- 执行状态变更与完成事件 Outbox 同事务；RabbitMQ confirm 后标记发布，Inbox 使用 event id 与
  payload hash 防止重复或同 ID 篡改；PROCESSING 领取态带 5 分钟持久租约，崩溃后的未完成
  命令可由下一实例安全接管；Outbox 发布失败最多重试 10 次，随后落入 `DEAD` 终态并告警。

### 3.4 Workflow Service

`CloudFlowRuntimeClient` 只负责 HTTP、身份/空间上下文、结构化诊断投影和熔断；Java 不再实现
grammar、AST、DAG 编译或错误规则。`cloudflow.runtime.compile-url` 配置完整编译地址，Runtime
不可用时拒绝校验。

发布/执行前都使用 Rust Compiler IR；Java 以本地事务保存执行记录和
`cloudflow.execution.start.v1` Outbox，Publisher Confirm 后投递。Runtime accepted/completed 事件
回写 Java 执行摘要。`WorkflowExecutionWorker` 仅保留为默认关闭的紧急回滚实现，不参与默认生产
执行；待灰度完成可在后续迁移删除。

### 3.5 Web IDE

- Monaco 注册 `cloudflow` Monarch tokenizer、关键字补全和 Runtime 外部 marker。
- 校验错误的 line/column/cliOutput 原样进入 Problems 与终端。
- 终端使用 Vue 文本插值，不执行服务端返回的 HTML；长行折行但保留空行。
- 源码切换画布时优先使用 Runtime IR，避免浏览器再实现完整 Parser。

## 4. 优化前后对比

| 维度 | 优化前 | 优化后 |
|---|---|---|
| 语法 | 任意命名块可能被接受 | 关键字白名单、大小写敏感、非法结构化诊断 |
| 控制流 | 摘要或丢失 | 完整 AST 与可调度 IR 节点/边 |
| 错误 | 单一字符串/首错 | 多错误、CF 编码、Span、源码、建议、文档链接 |
| CLI | 参数不完整 | Clap 标准子命令与双输出格式 |
| HTTP | 最小适配 | Axum/serde/tower、鉴权、限流、大小、超时、CORS、优雅关闭 |
| Java 校验 | 自行解析 | 单一调用 Rust Compiler |
| IDE 终端 | 多行挤压 | 安全换行、长行折叠、Monaco 精确标记 |
| 画布 | 正则解析 DSL | 以编译器 IR 为机器真源 |

## 5. 验证结果

### 5.1 已执行并通过

```text
Rust:
  cargo fmt --all -- --check
  cargo clippy --locked --all-targets --all-features -- -D warnings
  cargo check --locked
  结果：本轮 broker/persistence 改动后的 check、clippy、fmt 均通过；完整 test 需要重新编译测试
  二进制，但本机磁盘不足，未把上一轮 23 项基线结果冒充为本轮结果。

Workflow Service:
  ./gradlew test --offline
  结果：7 tests passed（新增权限交集回归用例）

Web:
  npm run test:cloudflow
  结果：2 tests passed
  npm run build -- --outDir /tmp/pcd-web-dist-cloudflow-final2
  结果：production build passed（并确认旧 PluginEditorView 删除后路由仍可构建）

新增服务配置回归：
  Plugin/Automation/Scheduler Workflow `./gradlew compileJava --offline`
  结果：四个新增服务编译通过；固定数据库密码和 `test` 内部令牌回退已移除。

Storage share contract:
  python3 -m unittest discover -s PrivateCloudDisk-storage-service/tests -p 'test_share_authorization_contract.py' -v
  结果：9 tests passed。

HTTP 实机冒烟：
  使用 release 版 cloudflow-runtime 监听 127.0.0.1:18091
  cloudflow-runtime --healthcheck
  curl POST /api/v1/compile（携带内部服务令牌）
  结果：健康检查通过；示例源码返回 valid=true 和 workflow.cloudflow.io/v1 IR
```

Rust 默认测试当前为 28 项通过（另有 2 项环境依赖集成测试由 CI 显式执行），覆盖 Demo golden、公开 `.flow` 示例、严格非法输入、完整控制流 AST/IR、typed value、
依赖/插件语义、CLI 组合和颜色、HTTP 认证/422/超大 body/CORS、执行控制、顶层顺序边以及 100 step
< 500 ms。`examples/coverage` 还由真实 `cloudflowc` 二进制逐个编译，并以离线 JSON Schema 契约校验
生成 IR；MySQL 动态控制流/审批恢复用例已作为 CI 的 ignored integration test 显式执行。

### 5.2 基线限制

- Web 全仓 `vue-tsc --noEmit` 仍有其他历史页面错误；本轮筛选 CloudFlow 触及文件未发现新增错误。
- 本机未运行真实 RabbitMQ/MySQL；CI 已配置 MySQL 8.4、RabbitMQ 3.13 服务并显式执行 Inbox/Outbox/
  stale recovery 契约。真实 Tonic gRPC→Axum mock Agent 契约已单独通过。
- 本轮已在本机通过 CloudFlow 默认 Rust 测试、fmt、clippy 和真实 CLI 覆盖门禁；需要 MySQL/RabbitMQ
  的两项测试依赖 CI 服务容器，不能因本机未提供连接串而伪报通过。
- 未运行真实 Platform、Plugin Runtime Sandbox 和 Capability Hub 集群 E2E。
- 未完成 200 节点持续基准、并发压测、重启恢复、网络分区和重复消息故障注入。
- 本轮同时移除了 Workflow/Plugin/Automation/Scheduler 四个新增服务配置中的固定数据库密码和
  `PCD_INTERNAL_SERVICE_TOKEN=test` 回退；缺少部署密钥时应启动失败。现有历史业务服务仍需按同一
  门禁逐一迁移，不能把 Compose 的必填变量当作源码安全证明。

## 6. 风险与规避

1. **动态实例投影风险（P1）**：foreach/while 已按 `max_parallel` 分批并使用稳定 iteration ID 持久化，
   try/catch/finally 与 wait/resume 已持久化；当前 Runtime 已有单轮检查点与输出键，但 Java/前端尚未
   提供逐项筛选、单项重放和可视化进度，需要在控制面状态投影中补齐。
2. **基础设施验证风险（P0）**：State Store、Inbox/Outbox 和 Agent 已实现，但本机未完成真实
   MySQL/RabbitMQ 故障注入；必须由 CI/集群证据放行。
3. **双执行面风险（P1）**：旧 Java Worker 默认关闭但源码仍保留；部署必须锁定
   `WORKFLOW_LEGACY_WORKER_ENABLED=false`，同一 execution id 只允许 Rust 所有者。
4. **能力越权风险（P0）**：Capability Agent 每次调用都必须带 user/space；Workflow Service 以
   工作流声明权限与当前授予权限的交集校验 capability 所需权限，不能信任编译时快照代替执行时校验。
5. **源码/日志泄漏（P1）**：HTTP 诊断不返回 `full_source`，日志不记录 header/body；完整执行日志
   仅管理员按审计权限下载。
6. **图源码无损往返风险（P1）**：复杂控制流不能由 UI 正则反向生成；IR 不足以恢复注释和格式，
   因此源码仍是发布事实，画布保存独立布局映射，切换时必须先编译成功。

## 7. 生产落地顺序

1. 将已经存在的动态 iteration ID、单轮输出和重放模型投影到 Java 与前端，补齐节点级实时进度和
   单项重放入口。
2. 在 CI/集群运行 MySQL/RabbitMQ、重复/乱序、Broker 中断、进程 kill 与 stale recovery 测试。
3. 联调真实 Platform API、Plugin Runtime Sandbox 和 Capability Hub 权限矩阵，覆盖跨空间越权。
4. 灰度期间保留 Java Worker 开关但禁止双写副作用；确认 Rust 结果后永久移除 legacy worker。
5. 完成 200 节点、最大并发、超时、DLQ、Outbox 积压和沙箱逃逸压测后再开放市场执行。

## 8. 验收判定

| 交付项 | 当前状态 |
|---|---|
| 正式语言/AST/IR/错误规范 | 已完成 |
| `cloudflowc` 独立可执行文件 | 已完成 |
| `cloudflow-runtime` Axum 编译服务 | 已完成 |
| Demo 和示例编译 | 已完成 |
| Workflow Service 编译单一真源 | 已完成 |
| Monaco 诊断与多行终端 | 已完成 |
| Rust 默认生产执行唯一真源 | 已完成代码切换；灰度/故障注入待验收 |
| RabbitMQ Inbox/Outbox | 已实现；真实 Broker CI/集群证据待验收 |
| 数据库状态恢复 | 已实现；真实进程崩溃恢复待验收 |
| gRPC Agent/Capability Hub 调用 | gRPC→HTTP 契约通过；真实平台/插件联调待验收 |
| foreach/while/try/catch/finally/wait 运行语义 | **已实现并有默认测试/CI MySQL 契约；本机基础设施实跑待验收** |
| 集群 E2E、故障注入与压测 | **未完成，P0 门禁** |

因此，本轮适合合并为“CloudFlow 编译器 + 持久化执行面主体代码”，仍不应以“所有 CloudFlow 控制流
和企业集成已具备生产 SLA”名义发布。
