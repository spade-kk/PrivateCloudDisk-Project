# Changelog

## 0.1.3 - 2026-08-18

### Added

- **V1.2 DSL 扩展（Tranche 1）**：`switch/case/default`、`retry_on`、`timeout { … }` 块、
  `delay`、`environment`、`namespace`、`import "x.flow" as alias`、`metadata.changelog`、`tag`；
  新增错误码 CF4401–CF4407。
- **V1.2 DSL 扩展（Tranche 2）**：`for i in range(from,to)` 索引循环与 `for x in <array>` 集合循环、
  `break`/`continue` 循环控制、`parallel(max_concurrency=N)` 分支级并发、`validate { <bool> }` 校验；
  新增错误码 CF4408–CF4412。
- **V1.2 DSL 扩展（Tranche 3）**：`map/filter/reduce` 集合管道与 `${…}` 字符串模板、
  `interval`/`http(method)` 触发器详配、`on_error`、`notify`、`validate/expect` 别名、
  `audit` 注解、步骤级 `return`、`step group` 步骤组、`use/with` 模块默认参数注入、
  条件 `depends_on A if <bool>`；新增错误码 CF4413–CF4421。
- **统一语法高亮系统**：新增 `syntax-highlight/`，以 `GRAMMAR.pest` + `AST.rs` 为唯一事实来源，
  生成统一规范 `cloudflow.syntax-highlight.json`，并转换为 VS Code TextMate、Monaco Monarch、
  Highlight.js 三种格式；附带解析/转换脚本（`build_spec.py`、`convert.py`）、VS Code 扩展、
  Highlight.js 演示页、样例 `.flow` 与测试套件。前端 Monaco 改为引用生成规则，不再硬编码
  CloudFlow 高亮正则（Web 端 `src/languages/cloudflow.ts`）。
- **AST 可视化输出**：新增 `cloudflowc compile --emit-ast`（`-A`）与 `src/ast_printer.rs`，
  在 Parser 后输出纯语法层级树（彩色 ANSI/`--no-color`/`-o` 写文件）或 `--output-format json` 的
  AST 序列化；`--check-only` 优先于 `--emit-ast`，`--explain` 附加说明，`--target` 忽略。不生成 IR、
  不展开 include、不执行语义分析（输出不代表语义合法）。`lib::parse_ast` 暴露纯解析 AST。新增集成
  测试 `tests/ast_visualization.rs`（13 项）。
- **统一代码补全系统**：新增 `cloudflow.completion.json` 补全规范（唯一事实来源），由
  `GRAMMAR.pest` + `AST.rs` + `config.py` 生成，覆盖关键字/顶层块/结构模板/内置函数/类型/
  触发器/可重试异常/能力/错误码/括号配对/缩进/片段（`completion_builder.py` + `completion_convert.py`）。
  VS Code 扩展新增 `src/extension.js` CompletionItem+SignatureHelp provider 与
  `snippets/cloudflow.code-snippets`（`main` 已声明，vsce 正常打包）；前端 Monaco 由
  `src/languages/cloudflowCompletion.ts` 注册补全，`PluginMonacoEditor.vue` 不再硬编码 CloudFlow
  补全。新增补全测试 20 项（套件现 55 项）。

### Fixed

- `lookup` 不得用 `?` 提前返回：上下文缺少 `steps` 键时，非 steps 的点分引用（如 `vars.name`、
  管道 filter 的行字段）仍须正常解析。
- step group 在编译期展开为普通步骤，避免为组生成幻影节点导致非法 DAG 边。

### Changed

- `validate_controls` 增加 `loop_depth` 参数，编译期拒绝循环体外的 `break`/`continue`（CF4408）。
- Runtime 新增内部循环控制信号 `LoopBreak`/`LoopContinue`，由最近的 for/while 循环节点捕获，
  try 的 finally 仍执行、parallel 分支穿透向上传播；`foreach` 不建立循环作用域。
- IR schema 与覆盖校验脚本扩展 `validate`/`break`/`continue` 节点类型、
  `loopConfig.kind ∈ {for, for-range}` 与 `parallel.maxConcurrency`。

### Verification

- `cargo check` 0 error；`cargo test` 全绿（`cloudflow_v12_extension.rs` 现 41 项；coverage 6、compliance 14、contract 2、lib 9）。
- `scripts/verify_coverage.sh`：全部 `examples/coverage/*.flow` 编译通过并过 IR schema 契约（Tranche 3 新增
  pipeline/template/interval_webhook/webhook_detail/audit/notify_on_error/return/step_group/use_with/conditional_depends）。
- 运行时 for/break/continue/validate/return/条件依赖/管道真实异步执行、webhook/interval 调度与 notify 外发
  仍需部署环境 E2E（依赖 MySQL/RabbitMQ/webhook 端口）。

## 0.1.2 - 2026-08-08

### Added

- `examples/coverage/` 全语法覆盖资产、离线 IR Schema 校验器和 `verify_coverage.sh` CI 门禁；包含
  manual/schedule/event/http、变量、DAG、条件、foreach/while、parallel、retry、try/catch/finally、
  wait、assert、表达式、插件 action、handlers 和受限 include。
- `while`、`assert`、三元表达式、数组/对象字面量、成员访问、`include` AST/IR/Compiler 支持；`match/case`
  明确保持拒绝，避免未实现语法被误接受。
- Runtime 支持 foreach 分批迭代、while 上限、try/catch/finally、wait 的持久化 resume；新增真实 MySQL
  动态控制流与审批恢复 CI 契约测试。

### Fixed

- 顶层 AST 不再把 `step` 与控制块分类后丢失源码顺序；控制节点前后自动写入 DAG 顺序边，避免 wait
  后的副作用在恢复前执行。
- 嵌套控制流内 action 的能力约束与变量引用现与顶层 step 一样接受语义校验，不能再绕过到 Runtime。
- foreach iterator 与 catch binding 改为真实词法作用域，循环/捕获变量泄漏到块外现在会在 Compiler 阶段
  返回 `CF2002`。
- Runtime 表达式求值补齐 `%`、`contains`、`starts_with`、`ends_with`，保持 Compiler 白名单与执行面一致。
- number 词法支持科学计数法，避免 `1.25e3` 在解析阶段被当作非法文本或字符串。
- foreach/while 动态子步骤现在使用稳定 `<control>[iteration].<node>` 检查点 ID；每个元素有独立
  attempt 和输出键，MySQL 集成测试验证三个元素不会折叠为同一静态步骤。

### Verification

- `cargo fmt --all -- --check`、覆盖编译脚本、CloudFlow compliance/contract/coverage 测试通过。
- 动态控制流 MySQL 测试已编译并接入 CI；本机未提供 `CLOUDFLOW_TEST_DATABASE_URL`，因此未伪报为本机
  基础设施通过。

## 0.1.1 - 2026-08-08

### Fixed

- RabbitMQ 命令消费者不再对 Inbox/处理异常执行无限 `requeue`；基础设施类临时错误使用同一
  `event_id` 进行 `retry_count` 有界重投，超过 3 次明确进入 DLQ，契约/业务错误直接死信。
- Capability Hub 权限判断改为“工作流声明权限 ∩ 当前授予权限”，避免把未授予的额外声明权限
  当作有效权限，同时保留能力所需权限的最小交集校验。
- Runtime 执行 ID 数据库字段扩展为 `VARCHAR(128)`，与 HTTP 输入校验和幂等键长度保持一致。

### Verification

- `cargo check --locked`、`cargo clippy --locked --all-targets --all-features -- -D warnings` 通过。
- Workflow Service 离线 Gradle 测试通过（7 项）；Web CloudFlow Node 测试通过（2 项）。
- 完整 Rust 测试需在 CI 重新执行：本机工作区磁盘不足，无法完成测试二进制重编译。

## 0.1.0 - 2026-08-02

### Added

- Pest 严格语法、完整控制流 AST、类型化值与表达式、版本化 Workflow IR。
- `cloudflowc compile` CLI：文件/内联输入、输出文件、target、check-only、explain、JSON、no-color、compact。
- miette 人类诊断与 `CLOUDFLOW_ERROR_DESIGN.md` JSON 诊断共用模型。
- Axum `POST /api/v1/compile`、健康检查、内部令牌、请求体/超时/并发/CORS 限制及优雅关闭。
- Demo golden、非法语法、语义、CLI、HTTP、100 步性能和 DAG edge 调度测试。
- SQLx MySQL 执行/步骤检查点、失联恢复、持久化 Inbox/Outbox 与执行日志。
- Lapin RabbitMQ 命令消费者、DLQ、竞争消费 QoS、持久消息和 Publisher Confirm。
- Tonic gRPC Capability Agent 契约及 Reqwest → Workflow Capability Hub 安全代理。
- 生产执行协调器：变量校验、DAG 并发调度、超时、指数退避、暂停/取消/重试和失败处理器。

### Changed

- `graph.edges` 成为 Runtime 调度的权威依赖关系，`dependsOn` 仅保留兼容语义。
- Workflow Service 改为只调用 Rust Runtime 编译，不再在 Java 重复解析 CloudFlow。
- Workflow Service 使用事务 Outbox 发布执行命令并消费 Runtime accepted/completed 事件；旧 Java Worker 默认关闭。
- Web IDE 使用结构化诊断创建 Monaco marker，并安全保留多行终端输出。
- Web IDE 源码转画布以 Runtime IR 为机器真源，画布回写生成符合 Pest 规范的 action 块。

### Compatibility

- 保留 `/internal/v1/cloudflow/compile` 兼容别名。
- `CLOUDFLOW_RUNTIME_MODE=compiler` 保留无基础设施的 IDE/编译模式；生产模式缺少 MySQL、RabbitMQ
  或 Agent 配置时 fail-fast，禁止静默退回内存执行。
- `CLOUDFLOW_DEMO_DESIGN.md` 的 `<step>.output` 历史引用在 AST 阶段规范化为
  `steps.<step>.output`；新生成内容只使用规范形式。

## [V1.2] 2026-08-18
- DSL 扩展：switch/case/default、retry_on、timeout 块、delay、environment、namespace、import-as、tag、metadata.changelog。
- 错误码：新增 CF4401..CF4407 并接入结构化诊断。
- IR：新增 switchConfig/delayMs/retryOn/onTimeout、metadata.namespace/changelog/tags、spec.environment（向后兼容）。
- 测试：新增 tests/cloudflow_v12_extension.rs（13 项）；examples/coverage 新增 6 个 .flow 并纳入覆盖集。
