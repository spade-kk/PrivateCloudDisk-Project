# CloudFlow DSL V1.0/V1.1 语法审计与 V1.2 新语法扩展

> 生成日期：2026-08-18 ｜ 对象：`PrivateCloudDisk-cloudflow-runtime`（Rust）
> 依据：`src/grammar.pest`、`crates/cloudflow-engine-core/src/ast.rs`、`crates/cloudflow-engine-core/src/ir.rs`、`src/parser.rs`、
> `src/semantic.rs`、`src/compiler.rs`、`crates/cloudflow-engine-core/src/diagnostic.rs`、`src/execution.rs`、`docs/CLOUDFLOW_DESIGN.md`

---

## 一、V1.0/V1.1 已实现语法审计对照表

| # | 规范语法结构 | 解析 | AST | IR | Runtime | 结论 |
|---|--------------|:----:|:---:|:--:|:-------:|:----:|
| 1.2 | `workflow "name" { }` 根结构 | ✓ | ✓ | ✓ | – | 正确 |
| 1.3 | `metadata` / `variables` / `trigger` / `runtime` / `steps` / `handlers` | ✓ | ✓ | ✓ | ✓ | 完整 |
| 1.4 | 任意命名顶层块拒绝 | ✓ | – | – | – | **偏差**（见 A1） |
| 1.5 | 关键字仅小写 | ✓ | ✓ | – | – | 正确 |
| 1.6 | 双引号 / 三双引号；单引号拒绝 | ✓ | ✓ | – | – | 正确 |
| 1.7 | `vars.<n>` 与 `steps.<id>.output` 引用 | ✓ | ✓ | ✓ | ✓ | 正确 |
| 1.8 | `input.<type>(...)` / 显式 / 推断 / 未初始化 | ✓ | ✓ | ✓ | ✓ | 完整 |
| 1.9 | 变量名 `[A-Za-z_][A-Za-z0-9_-]*`，变量名内拒绝点号 | ✓ | ✓ | ✓ | – | 正确 |
| 1.10 | number 整数/小数/科学计数法保留 JSON number | ✓ | ✓ | ✓ | ✓ | 正确 |
| 1.11 | ValueNode 区分 Literal/Ref/Expression；数字/布尔/数组/对象不降级 | ✓ | ✓ | ✓ | ✓ | 正确 |
| 1.12 | IR `$ref` | – | – | ✓ | ✓ | 正确 |
| 1.13 | IR `$expr` | – | – | ✓ | ✓ | 正确 |
| 1.14 | `while` + maxIterations | ✓ | ✓ | ✓ | ✓ | 完整 |
| 1.15 | `assert` + CF2202 | ✓ | ✓ | ✓ | ✓ | 完整 |
| 1.16 | 三元表达式 | ✓ | ✓ | ✓ | ✓ | 完整 |
| 1.17 | `include` 文件模式 / 相对路径 / 循环检测 / 逃逸检测 / HTTP 拒绝 | ✓ | ✓ | ✓ | – | 完整 |
| 1.18 | `match/case` 预留 + CF2301 | – | – | – | – | **遗漏**（见 A2） |
| 1.19 | `foreach` 动态执行 / 并发 / 检查点 | ✓ | ✓ | ✓ | ✓ | 完整 |
| 1.20 | `try/catch/finally` + catch 作用域 | ✓ | ✓ | ✓ | ✓ | 完整 |
| 1.21 | `wait` 持久化 / resume | ✓ | ✓ | ✓ | ✓ | 完整 |
| 1.22 | 控制节点顺序屏障写入 edges | – | – | ✓ | ✓ | 正确 |
| 1.23 | foreach/catch 绑定变量作用域 + CF2002 | ✓ | ✓ | ✓ | ✓ | 完整 |
| 1.24 | 语义：重复步骤/依赖/DAG 环/引用/action 命名/表达式白名单 | ✓ | ✓ | – | – | 完整 |
| 1.25 | CLI 参数 / HTTP 编译 / 错误输出格式 | ✓ | ✓ | – | – | 正确 |

结论：V1.0/V1.1 已声明语法在实现中**主体完整、路径正确**，无“规范已定义但完全缺位”的主干特性。

---

## 二、审计目标二：实现与规范不一致点（差距分析）

- **A1（高）**：顶层任意命名块 `A { }` 规范要求产生 `CF1201`，实现通过 `unknown_keyword` 产生 **`CF1202`**（`unknown_top_block`）。二者编码不一致。
- **A2（中）**：`match/case` 预留与错误码 `CF2301` 未接线：语法文件无 `match` 规则，`match x {}` 会落入 `unknown_top_block` → `CF1202`，而非规范声明的预留扩展位错误码 `CF2301`。
- **A3（中）**：`timeout` 仅支持 `timeout = 5s` 简写，无块形态（`on_timeout` 行为）——**本次已扩展**。
- **A4（中）**：`retry` 无“指定可重试异常类型”能力——**本次已扩展 `retry_on`**。
- **A5（中）**：无“按索引 `for` 循环 + `break/continue`”，仅 `foreach`——**Tranche 2 已实现**。
- **A6（低）**：`parallel` 并发数全局取 `runtime.max_parallel`，不支持分支级 `max_concurrency`——**Tranche 2 已实现**。

修复优先级：A1（编码对齐，需与错误设计文档同步）、A2（接入 CF2301 或正式落地 switch）、A3/A4（本次完成）、A5/A6（V1.2 后续）。

---

## 三、V1.2 已实现新语法（本交付）

| 需求点 | 语法 | grammar | AST | IR | 语义 | Runtime | 错误码 |
|--------|------|:-------:|:---:|:--:|:----:|:-------:|:------:|
| 2.1 | `switch <expr> { case <v> => {..} default => {..} }` | ✓ | SwitchNode | node_type=switch + switchConfig | ✓ | ✓ | CF4401 |
| 2.2 | `retry_on [Exception, ...]` | ✓ | StepNode.retry_on | NodeIr.retry_on | CF4402 | 预留 | CF4402 |
| 2.3 | `timeout { duration = 30s; on_timeout = "fail" }` | ✓ | StepNode.on_timeout | NodeIr.timeout/on_timeout | CF4403 | 预留 | CF4403 |
| 2.19 | `delay 5s` | ✓ | DelayNode | node_type=delay + delayMs | CF4404 | ✓（sleep） | CF4404 |
| 2.12 | `environment { KEY = val }` | ✓ | EnvironmentDecl | SpecIr.environment | CF4405 | 预留 | CF4405 |
| 2.13 | `namespace com.example.workflows` | ✓ | WorkflowNode.namespace | MetadataIr.namespace | CF4406 | – | CF4406 |
| 2.14 | `import "x.flow" as alias` | ✓ | IncludeNode.alias | extensions.importAliases | CF4407 | – | CF4407 |
| 2.26 | `metadata { changelog=... }` | ✓ | MetadataNode.changelog | MetadataIr.changelog | – | – | – |
| 2.27 | `tag "finance"` | ✓ | MetadataNode.tags | MetadataIr.tags | – | – | – |

### 3.x AST 新增
`FlowNode::Switch(SwitchNode)`、`FlowNode::Delay(DelayNode)`、`SwitchCase`、`EnvironmentDecl`；
扩展 `StepNode{retry_on,on_timeout}`、`WorkflowNode{namespace,environment}`、`MetadataNode{changelog}`、`IncludeNode{alias}`。

### 4.x IR 新增
`NodeIr{retry_on,on_timeout,switch_config,delay_ms}`、`MetadataIr{namespace,changelog,tags}`、`SpecIr.environment`。均为 `#[serde(default, skip_serializing_if=...)]`，向后兼容旧 IR。

### 7.x 新增错误码（CF44xx，不与现有冲突）
`CF4401` switch 多 default ｜ `CF4402` retry_on 未知异常 ｜ `CF4403` on_timeout 非法
`CF4404` delay 时长为 0 ｜ `CF4405` environment 非字面量 ｜ `CF4406` namespace 非法 ｜ `CF4407` import 别名重复

---

## 四、V1.2 待扩展（已全部交付）

早期规划的 `for + break/continue`（2.5/2.6）、`parallel max_concurrency`（2.8）、`interval` / `webhook`
详配触发器（2.9/2.10）、`on_error`（2.17）、`notify`（2.18）、`validate/expect`（2.20）、
`map/filter/reduce`（2.21）、字符串模板（2.22）、`audit` 注解（2.25）、`step group`（2.16）、
`use/with`（2.15）、条件 `depends_on`（2.4）、步骤级 `return`（2.7）**均已由 Tranche 2 / Tranche 3 交付**，
详见下方第六、七节。

---

## 五、验证状态

- `cargo check`：通过（0 error）。
- `cargo test`：全部通过（含新增 `tests/cloudflow_v12_extension.rs` 13 项；V1.1 回归全绿）。
- 新增 `examples/coverage/*.flow`：switch/delay/timeout_block/retry_on/environment_namespace/tags_changelog，
  并已纳入 `cloudflow_coverage::CASES`。
- 未在线验证：真实异步执行（delay 睡眠）、跨进程调度、webhook/interval 真实调度与 notify 外发仍需部署环境 E2E。

---

## 六、V1.2 Tranche 2（本交付）：for 循环 / break·continue / parallel 并发 / validate

| 需求点 | 语法 | grammar | AST | IR | 语义 | Runtime | 错误码 |
|--------|------|:-------:|:---:|:--:|:----:|:-------:|:------:|
| 2.5 | `for i in range(0, vars.max) { ... }` | for_decl | ForNode | loop kind=for-range | CF4410 | ✓（索引循环） | CF4410 |
| 2.5 | `for x in <array> { ... }` | for_decl | ForNode | loop kind=for | ✓ | ✓（元素循环） | – |
| 2.6 | `break` / `continue` | break_decl/continue_decl | BreakNode/ContinueNode | node_type=break/continue | CF4408 | ✓（循环控制信号） | CF4408 |
| 2.8 | `parallel(max_concurrency=3) { ... }` | parallel_opt | ParallelNode.max_concurrency | parallel.maxConcurrency | CF4411 | ✓（分支级并发覆盖） | CF4411 |
| 2.20 | `validate { <bool 表达式> }` | validate_decl | ValidateNode | node_type=validate+condition | CF4409 | ✓（CF4412 运行时失败） | CF4409/CF4412 |

### 语义规则
- `break`/`continue` 仅允许出现在 `for`/`while` 循环体内（语义层以 `loop_depth` 追踪嵌套）；
  `foreach`（并行分片迭代）不建立循环作用域，break/continue 在 foreach 体中出现会在编译期报 CF4408。
- `for i in range(from, to)`：`i` 取 `[from, to)` 的整数值；from/to 必须可求值为 number（CF4410），运行时同样强制。
- `for x in <array>`：`x` 依次取集合元素，顺序执行，支持 break/continue。
- `validate { expr }`：expr 必须为布尔（字面量反例 CF4409）；求值为 false 时 Runtime 产生 CF4412。
- `parallel(max_concurrency=N)`：N 必须为正整数（CF4411）；运行时 N 覆盖全局 `runtime.max_parallel`。

### Runtime 控制信号
`break`/`continue` 编译为 `node_type=break/continue` 子节点。`execute_dynamic_roots` 遇之返回内部信号
`RuntimeExecutionError::LoopBreak/LoopContinue`（不是业务错误），由最近的 `for`/`while` 循环节点捕获：
break 跳出、continue 进入下次迭代。信号穿过 `if`/`switch`/`try(finally 仍执行)`/`parallel` 向上传播，
`foreach` 与顶层静态 DAG 不直接驱动它们（静态引擎有防御性 CF4408 报错）。

### 验证
- `cargo check`：0 error。
- `cargo test`：全绿（`cloudflow_v12_extension.rs` 现 22 项；Tranche 2 新增 for-range/for/validate/parallel max_concurrency 及 CF4408/09/10/11 反例）。V1.1 回归全绿。
- `scripts/verify_coverage.sh`：全部 `examples/coverage/*.flow` 编译通过并过 IR schema 契约（新增 for_range/for_collection/validate/parallel_max）。
- schema：`schemas/workflow-ir-v1.schema.json` 扩展 `validate/break/continue` 节点类型与 `loopConfig.kind ∈ {for, for-range}`、`parallel` 字段；`validate_coverage_ir.py` NODE_TYPES 同步。
- 未在线验证（同前）：for/break/continue 真实异步执行、validate 运行时失败路径需部署环境 E2E（依赖 MySQL 测试库）。

---

## 七、V1.2 Tranche 3（本交付）：管道/字符串模板/触发器详配/通知·错误处理·审计/步骤组·use·条件依赖·return

| 需求点 | 语法 | grammar | AST | IR | 语义 | Runtime | 错误码 |
|--------|------|:-------:|:---:|:--:|:----:|:-------:|:------:|
| 2.21 | `vars.files \| filter(size > 100) \| map(name) \| reduce(sum)` | pipeline_stage/op | Pipe{Filter/Map/Reduce} | `$pipeline`（input+op） | CF4422（类型，预留） | ✓（apply_pipeline） | – |
| 2.22 | `"hello ${vars.name}"` | 字符串模板 | ValueNode::Template | `$template`（段序列） | – | ✓（拼接） | – |
| 2.9 | `trigger { interval = 5m }` | interval_trigger | TriggerNode::Interval | TriggerIr::Interval{every} | CF4414 | 预留 | CF4414 |
| 2.10 | `trigger { http { path; method } }` | http_trigger(+method) | TriggerNode::Http{path,method} | TriggerIr::Http{method} | CF4413 | 预留 | CF4413 |
| 2.17 | `on_error { ... }` | on_error_decl | StepNode.on_error | NodeIr.onError{nodes} | ✓ | ✓（失败钩子） | – |
| 2.18 | `notify { channel; to; message }` | notify_decl | FlowNode::Notify | node_type=notify+notifyConfig | CF4416 | ✓（检查点） | CF4416 |
| 2.20 | `expect { <bool> }`（validate 别名） | expect_decl | FlowNode::Validate | node_type=validate | CF4409 | ✓ | CF4409/4412 |
| 2.25 | `audit { level; description }` | audit_block | WorkflowNode.audit | SpecIr.audit | CF4415 | – | CF4415 |
| 2.7 | 步骤级 `return <expr>?` | return_decl | FlowNode::Return | NodeIr（输出引用） | – | ✓（SUCCESS/CF4417） | CF4417 |
| 2.16 | `step group g { step a {} step b {} }` | step_group_decl | FlowNode::StepGroup | 编译期扁平化 | CF4418 | ✓（普通步骤） | CF4418 |
| 2.15 | `use <alias>` / `with <alias>` | use_decl | StepNode.use_alias | 模块默认参数注入 | CF4420 | ✓（注入即生效） | CF4420 |
| 2.4 | `depends_on A if <bool>` | depends_decl(+if) | StepNode.depends_condition | NodeIr.dependsCondition | CF4421 | ✓（ready_nodes_conditional） | CF4421 |

### 语义规则
- **map/filter/reduce 管道**：`filter(<bool 谓词>)` 谓词中裸标识符视为当前数组元素字段（行上下文），
  `vars./steps.` 全局引用仍严格校验；`map(<字段>)` 投影字段；`reduce(count/sum/avg/min/max/join)` 聚合。
  管道左折叠为嵌套 `$pipeline`，运行时逐级求值。
- **字符串模板**：`${...}` 内为简单引用（小写点分/下划线/连字符）时生成 `$ref` 段；其余按普通文本保留。
  运行期逐段求值拼接。
- **trigger 详配**：`interval` 时长必须 > 0（CF4414）；`http.method` 必须属于
  `GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS`（CF4413）。
- **on_error 与 notify**：`on_error { ... }` 作为失败步骤的错误处理钩子；`notify` 记录通知渠道/接收者/消息，
  渠道白名单校验（CF4416），运行期写检查点。
- **return**：步骤内 `return <expr>?` 提前结束当前工作流分支；运行期以 `SUCCESS` + `CF4417` 结束并携带返回值，
  `try/finally` 仍先执行 finally。
- **step group**：`step group <id> { step ... }` 是编译期组合语法，组内步骤 ID 必须全局唯一；
  组在编译阶段展开为普通步骤（不产生幻影节点），组名不得与步骤/其它组名冲突（CF4418）。
- **use/with**：`use <alias>` 从 `import "x.flow" as <alias>` 声明的模块拉取默认变量，
  作为步骤 action 参数的缺省值（同名显式参数优先）；别名必须已声明（CF4420）。
- **条件 depends_on**：`depends_on A if <bool>` 求值为 false 时豁免等待 A；条件须可求值为布尔（CF4421）。
  运行期 `ready_nodes_conditional` 感知条件，避免 DAG 死锁。

### 新错误码（CF4413–CF4421，不与现有冲突）
`CF4413` webhook 方法非法 ｜ `CF4414` interval 时长 ≤ 0 ｜ `CF4415` audit level 非法
`CF4416` notify 渠道非法 ｜ `CF4417` return（运行期提前返回信号）｜ `CF4418` step group 冲突/空组
`CF4420` use 未声明模块别名 ｜ `CF4421` 条件依赖非布尔

### 验证
- `cargo check`：0 error。
- `cargo test`：全绿（`cloudflow_v12_extension.rs` 现 41 项；本交付新增 pipeline/template/trigger/audit/notify·on_error/return/step_group/use/条件 depends 及 CF4413–4421 反例）。
- `scripts/verify_coverage.sh`：全部 `examples/coverage/*.flow` 编译通过并过 IR schema 契约（新增
  pipeline/template/interval_webhook/webhook_detail/audit/notify_on_error/return/step_group/use_with/conditional_depends 及 modules/params.flow）。
- schema：`schemas/workflow-ir-v1.schema.json` 扩展 `notify/return` 节点类型、
  `notifyConfig/onError/dependsCondition` 字段、trigger/audit 与 `$template`/`$pipeline` 值形态；
  `validate_coverage_ir.py` NODE_TYPES 与节点键同步。
- 未在线验证（同前）：webhook/interval 真实调度、notify 外发、条件依赖多轮调度需要在部署环境做 E2E（依赖 MySQL/RabbitMQ/webhook 端口）。
