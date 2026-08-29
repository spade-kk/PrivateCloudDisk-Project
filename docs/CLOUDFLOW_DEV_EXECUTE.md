# CloudFlow 开发调试执行入口（Dev-Execute）

> 状态：已实现并通过测试（统一执行引擎架构，2026-08-21）。代码位于
> `PrivateCloudDisk-cloudflow-runtime` 工作区：
> - `crates/cloudflow-engine-core`（独立 crate，纯执行核心）：`engine/`（统一调度驱动 +
>   `EngineDeps` 依赖注入面 + 内存依赖实现 + 双时钟）、`dev_exec.rs`（开发调试面入口）、
>   `execution_core.rs`（纯函数控制流语义）、`ir_validate.rs`（唯一 IR 契约校验）、
>   `ir.rs` / `ast.rs` / `diagnostic.rs` / `runtime.rs`（DAG 引擎）/ `expression/`（表达式子系统）；
> - `crates/cloudflow-agent`（独立 crate）：Capability Agent（gRPC），生产执行面唯一能力出口；
> - 宿主 crate `pcd-cloudflow-runtime`：生产执行面 `src/execution.rs`（持久化调度器与执行协调器，
>   注入 MySQL 状态 / Agent 动作 / tracing 日志依赖）、HTTP 调试入口 `src/http.rs`、
>   CLI `src/bin/cloudflowc.rs`。
> 生产执行入口（`/api/v1/executions` + `ExecutionCoordinator` + 数据库）的 I/O 路径未改动；
> 双执行面现在共享**同一个统一调度驱动**（`cloudflow_engine_core::engine::driver`），
> 仅通过注入的 `EngineDeps`（StateStore/LogSink/ActionExecutor/EventPublisher/Clock/
> ConfigProvider）区分行为；控制流语义与 IR 校验为同一实现（见 §2/§3/§8）。

本入口面向**开发与调试场景**：直接接收 `workflow.cloudflow.io/v1` IR JSON，校验 IR 契约后
在**纯内存**中同步执行——不写数据库、不记录执行任务 ID、不持久化日志、不依赖 MQ/Redis，
结果（节点状态、输出、错误、日志、上下文快照）直接在响应中返回。

---

## 1. 现状审计摘要（需求 §1）

生产执行链路（保持不动）：

```
POST /api/v1/executions（创建 execution_task，生成任务 ID）
  → Worker 从数据库加载 IR
  → RuntimeEngine（DAG 就绪计算 / 状态快照）
  → ExecutionCoordinator.execute_node（动作分发：builtin/api → Capability Agent gRPC，
    plugin → Plugin Runtime Sandbox）
  → 步骤/日志写回数据库 → HTTP 查询
```

审计结论（驱动本设计）：

- 执行调度核心（`RuntimeEngine::ready_nodes*` / `mark_success` / `mark_skipped` /
  `is_complete`）本身是**纯内存**的，可直接复用；数据库依赖集中在
  `ExecutionCoordinator` 的步骤记录与能力调用上。
- 动作执行、日志、状态存储三处在生产路径中耦合了数据库/Agent 调用，
  调试入口通过 **trait 抽象 + 内存实现** 替换（需求 2.6-2.11）：
  - `ActionExecutor`（动作执行器 trait，内存 `MockActionExecutor` 默认实现）；
  - 日志收集为内存 `Vec<DevLogEntry>`（需求 10.x 全量环节日志）；
  - 状态存储为 `RuntimeEngine` 内存快照 + `DevExecutionResult.context_snapshot`（可序列化）。
- 生产路径的 IR 校验（`compiler::validate_ir`）与**完整 IR 契约校验器**
  （`ir_validate::validate_ir_contracts`，纯函数、一次收集全部问题）已**统一**：
  `compiler::validate_ir` 现为 `validate_ir_contracts` 的文本适配层，
  生产 `RuntimeEngine::load`、`/ir-validate` API、微服务与调试入口共用同一校验语义。

## 2. 架构与分层（需求 §2/§11.2）

```
IR JSON（字符串或结构体）
   │  dev_execute / dev_execute_sync / dev_execute_async
   ▼
IR 契约校验器  ir_validate::validate_ir_contracts        ── 唯一 IR 校验实现（生产同一）；可 --no-validate / skipValidation 关闭
   │  Vec<IrContractIssue>（CFI-7001..CFI-7028，path + node_id + message）
   ▼
变量规范化  normalize_variables（cloudflow-engine-core::expression::evaluator，双执行面同一实现）
   │  input/local/deferred 语义；local 两遍求值（先 input 后 local，local 可引用 input）
   ▼
统一调度驱动  cloudflow_engine_core::engine::driver（execute / execute_sync）
   │  检查点恢复 → 主循环（全局超时/取消/暂停轮询/完成判定/条件依赖/批执行）
   │  调度：RuntimeEngine::ready_nodes_conditional（`crates/cloudflow-engine-core/src/runtime.rs`）
   │  控制流语义：execution_core（纯函数层）+ driver 节点分发（双执行面唯一实现）
   │  控制流：condition / switch / loop(foreach|while|for-range) / try-catch-finally /
   │          parallel / wait / delay / assert / validate / return / break / continue
   │  依赖注入（EngineDeps，调试面全部为内存实现）：
   │    StateStore = InMemoryStateStore（快照/恢复/断点）
   │    LogSink    = InMemoryLogSink（+ 可选 StdoutLogSink 组合）
   │    ActionExecutor = 统一驱动异步 trait（双执行面共用同一契约）：
   │               MockActionExecutor（默认）/ gRPC 生产仿真（HTTP agent 画像）
   │    EventPublisher = NoopEventPublisher；Clock = VirtualClock；ConfigProvider = 调试策略
   ▼
DevExecutionResult（status / node_results / outputs / errors / logs / duration_ms /
context_snapshot）

双执行面共享层  execution_core（crates/cloudflow-engine-core/src/execution_core.rs，纯同步纯函数）
   条件求值 condition_outcome(_with) / 分支提取 condition_branches / try 结构解析
   parse_try_structure / 循环计划 parse_loop_plan / 重试 retry_max_attempts +
   retry_strategy + backoff_delay_ms（含 exponential_backoff_ms）/ 超时 resolve_timeout /
   并行 parallel_max_concurrency / 子树展开 descendants(_for_children) /
   控制信号 ControlSignal
   生产执行面（宿主 crate src/execution.rs）与调试执行面（engine-core dev_exec.rs）
   经同一 driver 消费本层，不各自定义。
```

关键不变量：

- **纯函数核心**：`dev_execute_sync(&ir, supplied, &config, Arc<dyn ActionExecutor>)` 无全局
  状态、无 I/O（动作执行器为注入 trait），可单线程同步执行（需求 2.16），异步入口仅
  `spawn_blocking` 包装（需求 2.17）。
- **确定性**：就绪节点按 IR 声明顺序执行；mock 回显稳定；同输入必同输出
  （`e2e_concurrency_two_independent_workflows` 验证并发下互不干扰）。
- **与生产共用的唯一实现**：
  - 控制流语义：`execution_core`（条件/分支/try/循环/重试/退避/并行/超时/子树/控制信号）；
  - 统一调度驱动：`cloudflow_engine_core::engine::driver`（`execute`/`execute_sync`，
    主循环/节点分发/重试/退避/并行/超时/on_error/失败处理器）；
  - 控制流纯函数层：`execution_core`（`crates/cloudflow-engine-core/src/execution_core.rs`）；
  - 求值与变量：`normalize_variables`、`evaluate_value`、`truthy`、`parse_duration`
    （`cloudflow_engine_core::expression::evaluator`）；
  - 调度：`RuntimeEngine`（`crates/cloudflow-engine-core/src/runtime.rs`）；
  - IR 校验：`ir_validate::validate_ir_contracts`（生产与调试入口同一校验器）。
  调试面只保留调试面 I/O（内存日志/快照/断点/注入失败）与 CFD-81xx 错误码包装
  （需求 12.x 行为一致）。生产面则注入 MySQL 状态存储（`MysqlStateStore`）、
  Agent 动作执行器（`AgentActionExecutor`，gRPC + 节点级超时）、`RealClock`、
  `TracingLogSink` 与 `ProductionConfigProvider`——两执行面行为分叉**仅**由
  `EngineDeps` 具体实现决定（需求 §1.17）。

## 3. IR 契约校验器（需求 11.4）

`pub fn validate_ir_contracts(ir: &WorkflowIrV1) -> Vec<IrContractIssue>`
（`crates/cloudflow-engine-core/src/ir_validate.rs`，宿主 crate 根层 `pub use` 再导出；纯函数，一次遍历收集全部问题）。

**统一校验策略**：本模块是 crate 内唯一的 IR 校验实现。`compiler::validate_ir`
（`Vec<String>` 文本形态）内部委托本模块并把 `IrContractIssue` 映射为
`"{code}: {message} ({path})"` 字符串。因此：

- 生产 `RuntimeEngine::load`（`/api/v1/executions` 启动前的 IR 预校验）；
- 生产 HTTP `POST /internal/v1/cloudflow/validate-ir`（`/ir-validate` API，供
  Workflow Service 与其他微服务调用）；
- 开发调试入口（CLI `dev-execute` / HTTP `/api/dev/execute`）

拿到**完全一致**的校验结果（同一 `CFI-xxxx` 码集合），开发面与生产面行为一致。

`IrContractIssue { code, path, node_id: Option<String>, message }`：`path` 为 IR JSON 字段路径
（如 `spec.graph.nodes[2].retry.strategy`），`node_id` 在可定位时附带。

错误码（CFI-xxxx 段：IR 契约校验层；与 DSL 编译 CFxxxx、YAML 前端 CFY-xxxx、
调试运行面 CFD-xxxx 分层区分，见 `CLOUDFLOW_ERROR_DESIGN.md` 分层分类表）：

| 码 | 检查 |
| --- | --- |
| CFI-7001 | `apiVersion` 必须为 `workflow.cloudflow.io/v1` |
| CFI-7002 | `kind` 必须为 `Workflow` |
| CFI-7003 | `metadata.name` 缺失或非法 |
| CFI-7004 | `spec.graph.nodes` 缺失或为空 |
| CFI-7005 | 节点 ID 重复 |
| CFI-7006 | 节点类型非法（允许集合见 `VALID_NODE_TYPES`） |
| CFI-7007/CFI-7008 | task/plugin 节点缺少 `action` 或 action 结构不完整 |
| CFI-7009 | try 节点缺少 `errorHandler` |
| CFI-7010 | parallel 节点缺少 `parallel` 配置 |
| CFI-7011 | loop 节点缺少 `loopConfig.kind` |
| CFI-7012 | wait 节点 `errorHandler.waitType` 缺失 |
| CFI-7013 | errorHandler 结构非法 |
| CFI-7014 | switch 节点缺少 `switchConfig` |
| CFI-7015 | condition 节点缺少条件表达式 |
| CFI-7016 | `delayMs` 类型非法 |
| CFI-7017 | `edges` 的 from/to 引用不存在的节点 |
| CFI-7018 | DAG 存在环（拓扑检测） |
| CFI-7019 | 变量 `type` 非法 |
| CFI-7020 | trigger 配置非法（如 http 缺 path/method） |
| CFI-7021 | `retryPolicy.strategy` 非法 |
| CFI-7022 | 表达式结构非法（`$expr` 需为 condition/whenTrue/whenFalse、operator/operand、operator/left/right 或 function/arguments 之一；`$template`/`$pipeline` 结构检查） |
| CFI-7023 | `$ref` 指向未声明变量或不存在的步骤输出（`vars.`/`steps.` 命名空间静态解析；管道 filter 谓词行上下文的裸标识符视为元素字段，放行） |
| CFI-7024 | `controlParent` 指向不存在的控制节点 |
| CFI-7026 | 子工作流（provider=workflow）action 缺少 service/method |
| CFI-7027 | `onTimeout` 配置非法 |
| CFI-7028 | `retry.strategy` 非法 |

表达式结构（`$ref` / `$expr` / `$template` / `$pipeline`）递归校验：引用必须可解析、
管道 filter 谓词处于“行上下文”（裸标识符 = 元素字段，不要求静态声明）。

## 4. 内存执行引擎（需求 11.5/11.10）

### 4.1 公共 API（宿主 crate 根层再导出自 `cloudflow_engine_core::dev_exec`）

实现位于 `crates/cloudflow-engine-core/src/dev_exec.rs`；宿主 crate `src/lib.rs`
以 `pub use cloudflow_engine_core::{dev_exec, ...}` 再导出，既有
`cloudflow_runtime::dev_exec::*` / `cloudflow_runtime::dev_execute_sync` 调用路径不变
（CLI `cloudflowc` 与 HTTP 调试入口均经此路径消费）。

```rust
// 三种入口（同一核心，需求 4.6/4.7）：
pub fn dev_execute_sync(ir: &WorkflowIrV1, supplied: Value,
                        config: &DevConfig, executor: &dyn ActionExecutor)
                        -> Result<DevExecutionResult, DevEntryError>;
pub fn dev_execute(ir_json: &str, supplied: Value, config: &DevConfig,
                   executor: &dyn ActionExecutor) -> Result<DevExecutionResult, DevEntryError>;
pub async fn dev_execute_async(ir: &WorkflowIrV1, supplied: Value, config: DevConfig,
                               executor: Arc<dyn ActionExecutor>)
                              -> Result<DevExecutionResult, DevEntryError>; // spawn_blocking

pub enum DevEntryError { InvalidJson(String), Validation(Vec<IrContractIssue>), Internal(String) }
pub struct DevExecutionResult {
    pub status: DevWorkflowStatus,          // success | failed | waiting | breakpoint | timeout
    pub node_results: BTreeMap<String, DevNodeResult>,
    pub outputs: Value,                     // spec.outputs 求值结果
    pub errors: Vec<DevError>,              // CFD-81xx / 复用生产 CF5xxx/CF4xxx/CF2xxx
    pub logs: Vec<DevLogEntry>,             // 全环节日志（需求 10.x）
    pub duration_ms: u64,
    pub context_snapshot: Value,            // {vars, steps:{id:{output}}, outputs, status}
}
pub struct DevNodeResult { node_id, node_type, status /*pending|running|success|failed|skipped|waiting*/,
    attempts, started_at_ms, duration_ms, input: Option<Value>, output: Option<Value>,
    error: Option<DevError>, depends_on: Vec<String> }
```

### 4.2 `DevConfig` 关键项

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `skip_validation` | false | 跳过 IR 契约校验（需求 4.11/9.4；引擎改经 `RuntimeEngine::load_unvalidated` 构造，生产路径仍强制校验） |
| `enable_expressions` | true | false 时 `$ref/$expr/$template/$pipeline` 按字面量透传（纯结构执行，需求 4.16） |
| `max_parallel` | 4 | 记录于结果；同步执行按声明顺序串行（确定性） |
| `default_timeout_ms` | 30000 | 节点默认超时 |
| `overall_timeout_ms` | None | 全局执行超时（需求 4.14；模拟延迟计入虚拟时间） |
| `breakpoint` / `single_step` | None/false | 断点/单步（需求 6.11/6.12）：暂停节点保持 Pending 并返回快照，其余未执行节点记 Skipped |
| `skip_nodes` | [] | 直接标记 Skipped，并以 null 占位其步骤输出（下游 `$ref` 可解析） |
| `inject_failures` | {} | 按节点注入失败计划（`DevFailureSpec{code,message,retryable}`，按尝试顺序消费） |
| `action_latency_ms` | {} | 模拟动作延迟；> 节点超时 → CF5001（可重试，与生产 tokio timeout 同语义）；同时计入全局超时的虚拟时间 |
| `mock_outputs` | {} | 按节点覆盖输出（需求 4.24），绕过执行器 |
| `log_level` / `log_node_filter` | Info / None | 日志级别下限（需求 10.6）/ 节点过滤（需求 10.9） |
| `honor_delays` | true | delay 节点是否真实睡眠（≤5s 上限；测试可关） |

### 4.3 执行语义要点

- **调度**：每轮取 `ready_nodes_conditional`（与生产同一函数，含条件依赖豁免与
  `controlParent` 过滤），整批同步执行；空就绪集且未完成 → 死锁 CFD-8103。
- **控制流**：动态执行体（condition/try/loop 子图）由控制节点按运行时上下文执行；
  未选中分支与 try 的失败子节点在**引擎簿记**上终结（`mark_skipped`，保证
  `is_complete` 收敛），但调试结果保留其真实 `Success/Failed` 状态。
- **重试/超时**：`retry.maxAttempts` + `strategy`（fixed/exponential，退避与超时解析经
  `execution_core::backoff_delay_ms` / `resolve_timeout`，与生产执行面同一实现）；
  `retryOn` 白名单外的错误码不重试。
- **异常**：try/catch/finally —— `break/continue/return` 为控制信号不触发 catch；
  catch 经 `catchBinding` 绑定 `{code,message}` 到 vars；finally 始终执行。
- **wait**：顶层 wait 使工作流进入 `waiting`（CFD-8105 终结事件入 errors，快照返回）；
  动态执行体内的 wait 与生产一致报 CF2203。
- **表达式**：`$ref` 支持 `vars.`/`steps.<id>.output`/`env.`/`input.` 与 `[n]` 索引；
  整数算术结果保持 integer 数字类型（对齐 GitHub Actions 表达式，不产生 5.0）。

### 4.4 调试入口错误码（CFD-81xx 段）

`CFD-` 前缀 = 开发调试执行面（Dev Runner）专属错误码段，与 DSL 编译 CFxxxx、
YAML 前端 CFY-xxxx、IR 契约校验 CFI-7xxx 分层区分（见
`CLOUDFLOW_ERROR_DESIGN.md` 分层分类表）：

| 码 | 含义 |
| --- | --- |
| CFD-8101 | 变量规范化/表达式求值/引用失败（含 local 变量被调用方覆盖拒绝） |
| CFD-8102 | 未知节点/类型/动作、缺少必要配置 |
| CFD-8103 | 调度死锁 |
| CFD-8104 | 全局执行超时 |
| CFD-8105 | 进入 WAITING（审批/等待） |
| CFD-8106 | 到达断点/单步边界 |
| CFD-8107 | 提前 return（剩余节点跳过，工作流 success） |
| CFD-8108 | 工作流失败：剩余节点跳过 |

节点级失败复用生产错误码（CF5001 超时 / CF5002 未知动作 / CF5003 动作失败 /
CF5004 参数校验 / CF4412 validate / CF4417 wait 非法位置 / CF2201 迭代上限 / CF2202 assert）。

## 5. 动作执行器契约（需求 11.10/§7：双执行面单一抽象，不重复定义）

动作调用**只有一个** trait——统一驱动的异步契约（`cloudflow_engine_core::engine::deps`，
调试面经 `dev_exec` 再导出，`crate::dev_exec::ActionExecutor` 与
`crate::engine::deps::ActionExecutor` 是同一类型）：

```rust
#[async_trait::async_trait]
pub trait ActionExecutor: Send + Sync {
    async fn execute(&self, step: &StepContext) -> Result<Value, ExecutionError>;
}
pub fn action_key(action: &ActionIr) -> String;  // dev_exec 公共工具
// builtin/api → "provider:service.method"；plugin → "plugin:<pluginId>:<function>"；
// workflow   → "workflow:<service>[.<method>]"
```

实现者（全部经 `EngineDeps` 注入，引擎核心不感知具体实现）：

| 实现 | 位置 | 说明 |
| --- | --- | --- |
| `AgentActionExecutor` | 宿主 `src/execution.rs` | 生产面：Capability Agent gRPC（鉴权/审计/限流 + 节点级超时） |
| `MockActionExecutor` | engine-core `dev_exec.rs` | 调试面默认：纯内存确定性 echo，零网络 |
| `AgentDevActionExecutor` | 宿主 `src/http.rs` | HTTP `profile=agent` 生产仿真：同一 gRPC 接口的异步实现（非同步桥接） |

`MockActionExecutor`：确定性回显
`{"ok":true,"mock":true,"action":"<key>","attempt":N,"arguments":<input>}`；
`with_canned(key, value)` 固定输出；`with_known_actions(...)`/`strict()` 对未知动作
返回 CF5002。实现该 trait 是接入“本地真实能力”或外部执行器的唯一扩展点。

## 6. CLI 调试入口（需求 9.1-9.10/11.8）

```bash
cloudflowc dev-execute IR_FILE [选项]
cloudflowc dev-execute -i '{"apiVersion":"workflow.cloudflow.io/v1",...}' [选项]
cat 01_sequential.json | cloudflowc dev-execute        # 缺省读 stdin
```

| 参数 | 说明 |
| --- | --- |
| `IR_FILE` / `-i, --source` | IR 文件路径 / 直接 IR JSON（互斥；缺省 stdin） |
| `--var KEY=VALUE`（可重复） | 初始变量覆盖；value 先按 JSON 解析，失败按字符串（需求 9.2） |
| `--mock` | mock 动作（本入口执行器始终为内存 Mock，参数保留语义兼容） |
| `--no-validate` | 跳过 IR 契约校验（测试校验器本身） |
| `--timeout DURATION` | 全局超时，如 `30s`/`5m`/`100ms`（需求 9.6） |
| `--verbose` | Debug 级日志（需求 9.7） |
| `--breakpoint NODE_ID` | 指定节点执行前暂停（需求 9.8） |
| `--single-step` | 每个顶层节点完成后暂停 |
| `--skip-nodes A,B` | 跳过节点（逗号分隔） |
| `--level debug\|info\|warn\|error` | 日志级别 |
| `--report FILE` / `--report-json FILE` | 导出 Markdown / JSON 执行报告（需求 10.14/10.15） |
| `--output-format human\|json` | 结果输出格式（JSON 为结构化结果，与 HTTP 响应同形） |

退出码：`0` success/waiting/breakpoint；`1` failed/timeout/引擎内部错误；`2` IR JSON 解析失败、
IR 契约校验未通过（逐条输出 `CFI-7xxx` 问题，`--output-format json` 时含 `issues[]`）与参数/IO 错误。

示例：

```bash
cloudflowc dev-execute tests/fixtures/ir/11_variables_expressions.json \
  --var a=2 --var b=3 --var 'items=[{"size":5},{"size":0}]' --output-format json
cloudflowc dev-execute tests/fixtures/ir/08_exception_try_catch_finally.json --breakpoint compensate
```

## 7. HTTP 调试入口（需求 9.11-9.18/11.9）

**开关与安全模型**：环境变量 `CLOUDFLOW_ENABLE_DEBUG_EXECUTE=true` 才**注册**
`POST /api/dev/execute` 与 `GET /api/dev/openapi.json` 两条路由（`http.rs` 中条件挂载）。
默认关闭时两条路由**不存在于路由表**，任何请求命中 axum 默认 404——**空响应体**，
不返回任何 JSON、不提及环境变量名，端点存在性零泄露（需求 4.19）。

开启态下的防护（与生产端点对齐）：

- **鉴权**：两个端点均要求与生产端点相同的 `X-PCD-Service-Token`
  （`authorized()`，常量时间比较，fail-closed；令牌未配置时全部拒绝）；
- **请求体上限**：全局 `DefaultBodyLimit` 1 MiB（与编译端点同一约束）；
- **非阻塞**：执行经 `dev_execute_async`（`spawn_blocking`），不阻塞 tokio 异步 worker；
- **并发/超时**：全局 `ConcurrencyLimitLayer` + 30s `TimeoutLayer` 同样覆盖调试端点；
- 响应中的 CFI-7xxx/CFD-81xx 错误码仅在 401/422/200 之后出现，不会出现在 404。

`POST /api/dev/execute` 请求体（camelCase，`ir` 必填）：

```json
{
  "ir": { "apiVersion": "workflow.cloudflow.io/v1", "kind": "Workflow", "...": "..." },
  "variables": { "files": ["a.xlsx"] },
  "mock": true,
  "skipValidation": false,
  "enableExpressions": true,
  "maxParallel": 4,
  "defaultTimeoutMs": 30000,
  "overallTimeoutMs": null,
  "breakpoint": null,
  "skipNodes": [],
  "mockOutputs": { "save": { "done": true } },
  "logLevel": "info",
  "profile": "inmem"
}
```

**执行画像 `profile`（需求 6.3/6.4/6.9/6.10，2026-08-21 新增）**：

| profile | 动作执行器 | 说明 |
| --- | --- | --- |
| `inmem`（缺省） | `MockActionExecutor` | 纯内存：确定性回显，零网络；支持 `mockOutputs` 覆盖 |
| `agent` | `AgentDevActionExecutor`（统一驱动 `ActionExecutor` 的 gRPC 异步实现） | **生产仿真**：经 `CLOUDFLOW_TEST_AGENT_ENDPOINT`（环境变量）真实调用测试环境 Capability Agent，验证 builtin/api/plugin 全链路；状态与日志**仍仅在内存**（`InMemoryStateStore`/`InMemoryLogSink`），不写生产数据库 |

`profile=agent` 的附加约束：

- 未配置 `CLOUDFLOW_TEST_AGENT_ENDPOINT` → `400`（固定消息，不回显内部细节）；
- 内部服务令牌未配置 → `503`（Agent 连接需与生产相同的 `PCD_INTERNAL_SERVICE_TOKEN`）；
- 测试 Agent 连接失败 → `503`（固定消息“测试 Agent 服务不可用”）；
- 执行经 `spawn_blocking` + `Handle::block_on` 桥接同步→异步 gRPC，仅阻塞线程内安全；
- 鉴权与 `inmem` 完全一致（`X-PCD-Service-Token`），不降低安全等级。

响应（开启态；未启用时两条路由均为无响应体的 404）：

| 状态码 | 场景 |
| --- | --- |
| 404 | 端点未启用（路由未注册；空响应体，无任何 JSON 特征） |
| 401 | 开启但缺少/无效 `X-PCD-Service-Token` |
| 400 | 请求体非法（JSON 反序列化失败）/ `profile` 非法 / `profile=agent` 未配置端点 |
| 422 | IR 契约校验失败：`{valid:false,status:"validationFailed",issues:[IrContractIssue]}`（CFI-7xxx） |
| 200 | 执行完成（含 failed/timeout/waiting/breakpoint）：`DevExecutionResult` 的 JSON 序列化（status 为小写枚举值） |
| 503 | `profile=agent` 且测试 Agent 不可用 / 服务令牌未配置 |

`GET /api/dev/openapi.json`：OpenAPI 3.0.3 文档（需求 9.18）；与 `/api/dev/execute`
同受路由门控与令牌鉴权。

示例：

```bash
# 需先以 CLOUDFLOW_ENABLE_DEBUG_EXECUTE=true 启动（PCD_INTERNAL_SERVICE_TOKEN 必配），
# 并携带与生产相同的 X-PCD-Service-Token：
curl -s localhost:8091/api/dev/execute \
  -H 'content-type: application/json' -H 'X-PCD-Service-Token: <token>' -d '{
  "ir": '"$(cat tests/fixtures/ir/01_sequential.json)"',
  "variables": {"files": ["a.xlsx"]}
}'
```

## 8. 生产入口兼容与安全（需求 11.11/11.23）

- 生产链路（`/api/v1/executions`、`ExecutionCoordinator`、DB/MQ/Agent）的 I/O 路径
  未改动。回归测试 `regression_compiler_validate_ir_still_used_by_engine` +
  `prod_validate_ir_and_contracts_agree_on_invalid_ir` 锁定生产 `validate_ir` 与
  `validate_ir_contracts` 结论一致（同一校验器）。
- **统一后的行为变化（有意为之）**：
  - `compiler::validate_ir` 从“轻量结构校验”升级为 `ir_validate::validate_ir_contracts`
    的文本适配层（`CFI-xxxx: 消息 (路径)`）。生产 `RuntimeEngine::load`、`/ir-validate`
    API 与微服务调用因此获得与调试入口**完全一致**的 IR 预校验（含节点必备字段、
    表达式结构、`$ref` 可解析性）。由本 crate 编译器生成的 IR 恒通过该校验
    （dev e2e 已用 DSL/YAML 编译产物验证）；
  - 控制流语义收敛到 `execution_core`：生产 `execution.rs` 与调试 `dev_exec.rs`
    均消费该层（条件求值/分支/try/循环计划/重试/退避/并行/超时/子树/控制信号），
    消除此前两处的重复实现，行为保持一致由共享代码保证；
  - `exponential_backoff_ms` 实现移入 `execution_core`，`engine.rs` 保留公开再导出。
- 对生产文件的改动仍为**增量**：`execution.rs` 纯函数改为 `pub(crate)` 复用；
  `runtime.rs` 新增 `load_unvalidated`（仅 dev 入口 `skip_validation` 时调用，
  生产 `load` 仍强制校验）。
- 调试入口安全属性：默认关闭时路由不存在（axum 默认 404、空响应体、零特征泄露）；
  开启态要求 `X-PCD-Service-Token`；纯内存无持久化（无数据泄露面）；不执行任意代码
  （动作只经 `ActionExecutor`，表达式只走白名单内建函数与 IR 表达式结构）；
  `honor_delays` 睡眠上限 5s、`maxIterations` 上限 10000、请求体 1 MiB 上限，防资源耗尽。

## 9. 测试报告（需求 11.6/11.18-11.21）

`tests/cloudflow_dev_exec.rs`：**68 项**（全部纯内存、无 DB/MQ/Redis），分组：

| 分组 | 项 | 覆盖 |
| --- | --- | --- |
| IR 契约校验器 | 10 | CFI-7001-CFI-7024 正/反例、多问题一次收集、path+node_id |
| DAG/顺序/并行/条件 | 4 | 顺序依赖、parallel、condition 真/假分支 |
| 循环 | 5 | foreach 迭代变量、空集合、while 零迭代、for-range、maxIterations 防线 |
| switch/assert | 4 | case 命中/默认分支、assert 真/假 |
| 重试/超时 | 5 | 注入瞬时失败重试、重试耗尽、不可重试立即停、CF5001 可重试、模拟延迟超节点超时 |
| 异常处理 | 2 | try/catch/finally 恢复（catchBinding 入参验证）、无 catch 传播 + finally 执行 |
| wait/子工作流/动作键 | 3 | wait 挂起、`workflow:` 动作键、plugin 动作键、strict CF5002 |
| 表达式/变量 | 3 | $ref/算术/模板/管道、local 不可覆盖、input 覆盖默认值 |
| 输出/快照 | 3 | spec.outputs 求值、快照 vars/steps/outputs、快照序列化往返 |
| 失败/校验/调试能力 | 11 | 节点失败详情、无效 IR 拒绝、skip_validation、断点快照、skip_nodes、mock 覆盖、表达式关闭透传、全局超时、日志过滤、Markdown 报告 |
| 端到端 | 5 | 全部合法夹具执行、复杂组合、**DSL 编译产物**与**YAML 编译产物**在 dev 引擎执行、并发双工作流 |
| HTTP | 8 | 200 完整结果、关闭态 404 空响应体（execute + openapi）、开启态 401 缺令牌、422 校验失败、mock/skip 组合、OpenAPI |
| 执行器契约/异步/回归 | 5 | 回显确定性、canned 优先、async==sync、生产校验回归（`compiler::validate_ir` 与 `validate_ir_contracts` 对同一非法 IR 结论一致且携带 CFI- 码）、配置安全默认 |

全量 `cargo test --workspace`（2026-08-21 统一执行引擎基线）：**205 passed /
3 ignored / 0 failed**（含既有 compiler/IR/YAML/表达式/V1.2/合规/契约全部回归 +
`tests/cloudflow_examples_ir.rs` 示例 IR 回归 2 项）；`cargo fmt` 通过；
`cargo clippy --workspace --lib --bins` 无新增告警（存量 ast.rs/ast_printer.rs/
semantic.rs 告警为历史遗留，不在本次范围）。

调试面分组更新（统一执行引擎后）：switch/assert/condition 顶层内联展开后，
全部分支子孙在 DAG 引擎终结标记（`CompletedWithSkips(descendants_for_children)`），
消除“已执行分支根未被标记 → 下游依赖永久不可调度”的死锁（CFD-8103）——
`switch_*`、`assert_*`、`e2e_complex_combo_full_run` 三项契约测试锁定该行为。

## 10. 示例 IR 夹具清单（需求 11.7）

`tests/fixtures/ir/`（17 个 + README）：01_sequential、02_condition、03_parallel、
04_loop_foreach、05_loop_while、06_loop_for_range、07_retry_timeout、
08_exception_try_catch_finally、09_wait_approval、10_subworkflow、
11_variables_expressions、12_complex_combo、13_invalid_structure、14_missing_field、
15_cycle、16_unknown_ref、17_unknown_action。逐文件场景与预期见
`tests/fixtures/ir/README.md`。

`examples/ir/`（3 个 + README，**展示型样例**，与测试资产分离，需求 7.21）：
01_sequential_flow、02_condition_branch、03_plugin_retry（插件 + 重试 + 超时 +
字符串模板）。`tests/cloudflow_examples_ir.rs` 保证每个样例“可反序列化 + 通过
IR 契约校验 + 内存调试入口执行成功”，任何 IR 结构变更都会触发回归。

## 11. 开发指南（如何扩展）

- 新增动作支持：实现**统一驱动**的 `ActionExecutor`（`engine::deps`，双执行面同一
  trait）注入即可，不改引擎；调试面不得再定义平行动作抽象。
- 新增 IR 契约检查：在 `ir_validate.rs` 追加检查并分配 CFI-7xxx 码，
  同步更新 `tests/cloudflow_dev_exec.rs` 校验器分组。
- 修改控制流语义（条件/try/循环/重试/并行等）：只改
  `crates/cloudflow-engine-core/src/execution_core.rs` 与 `engine/driver.rs`，
  双执行面自动生效；禁止在宿主 `src/execution.rs` / engine-core `dev_exec.rs`
  内新增平行的语义实现。
- 生产面行为调整（如数据库落盘语义、Agent 超时）：只改宿主 `src/execution.rs`
  的 `MysqlStateStore`/`AgentActionExecutor`/`TracingLogSink`/`ProductionConfigProvider`
  四个依赖实现，不动 driver。
- 新增夹具：放入 `tests/fixtures/ir/`（合法 0x/12 前缀参与 e2e 遍历；非法 1x 前缀供反例）。
- 本地演练：`CLOUDFLOW_ENABLE_DEBUG_EXECUTE=true CLOUDFLOW_LISTEN_ADDRESS=127.0.0.1:8091 \
  cloudflow-runtime` 后按 §7 调 `curl`。

## 12. 相关文档

- `CLOUDFLOW_COMPILER_GUIDE.md`（crate 布局：`cloudflow-engine-core` /
  `cloudflow-agent` / 宿主 crate；§Runtime 部署参数：`CLOUDFLOW_ENABLE_DEBUG_EXECUTE`）
- `CLOUDFLOW_IR_DESIGN.md`（IR 机器契约）
- `CLOUDFLOW_ERROR_DESIGN.md`（错误码体系与分层分类：CF / CFY- / CFI-7xxx / CFD-81xx）
- `CLOUDFLOW_EXPRESSION.md`（表达式子系统，`$expr/$template/$pipeline` 的唯一实现方）
- `PrivateCloudDisk-cloudflow-runtime/README.md`（CLI/构建入口）
