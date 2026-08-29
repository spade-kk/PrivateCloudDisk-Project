# Changelog

## 0.1.13 - 2026-08-29

### Fixed

- **VS Code CloudFlow DSL 高亮发布漂移**：扩展打包前现在从 `GRAMMAR.pest` 和 Compiler AST
  自动生成 TextMate Grammar，并同步到 `syntax-highlight/vscode/syntaxes/`；修复已生成规则未进入
  VSIX、导致 `.flow` 文件高亮缺失或不完整的问题。
- **Language Server 初始化失败**：移除扩展对 `cloudflow.clearCapabilityCache` 的手动重复注册，
  由 `vscode-languageclient` 根据 `executeCommandProvider` 单点注册，修复
  `command already exists` 导致的 LSP client 连接失败。
- **VS Code 扩展缺少 LS 二进制**：新增 `prepare:extension` 打包准备流程，默认将当前平台的
  release `cloudflow-ls` 放入 `bin/<platform>-<arch>/`；保留 `cloudflow.lsp.serverPath` 绝对路径、
  PATH 命令和 `bundled` 覆盖选项。

### Documentation

- 更新 VS Code 扩展安装、跨平台构建、Grammar 同步、LS 路径覆盖和故障排查文档；新增
  `docs/CLOUDFLOW_VSCODE_EXTENSION_AUDIT.md` 根因审计与验收记录。

## 0.1.12 - 2026-08-26

### Added

- **CloudFlow Language Server**：新增 workspace crate `crates/cloudflow-ls`。通过 stdio、TCP、Unix
  Domain Socket 与 WebSocket 提供 JSON-RPC/LSP；支持增量文档同步、UTF-16 Span 转换、诊断、completion、
  Hover、signature help、definition、references、rename、document symbols、semantic tokens、folding、formatting
  和请求取消。
- **动态 Capability Provider**：LS 以 token SHA-256 + tenant + space 做 5 分钟隔离缓存，调用网关能力发现
  API，不硬编码插件/平台能力；支持 `cloudflow/capabilitiesChanged` 与
  `cloudflow.clearCapabilityCache` 刷新当前会话。
- **多 IDE 接入**：VS Code 扩展引入 `vscode-languageclient` 并启动 `cloudflow-ls --stdio`；Web Monaco
  Bridge 通过 WSS 映射动态 completion/Hover/definition/references/rename/diagnostics。静态
  `syntax-highlight` 始终保留为离线高亮与基础补全层。
- **架构与协议文档**：新增 `docs/language-server/README.md`、`docs/language-server/PROTOCOL.md` 及
  `docs/architecture/cloudflow-language-server.drawio`（含 C4 源模型、四层可钻取页面）。

### Changed

- **编译器前端复用边界**：宿主 crate 将 Runtime 服务面置于 `runtime-service` feature，下游 LS 使用
  `compiler-api` feature 复用 DSL/YAML Parser、AST、表达式、语义规则而不调用 HTTP、MQ、持久化或 Agent。
- **syntax-highlight 定位收敛**：仅生成/转换 TextMate、Monarch、Highlight.js 与基础补全规范；删除 Web
  编辑器中的正则 DSL 诊断，避免出现与 Compiler Core 漂移的第二套语言实现。

## 0.1.11 - 2026-08-21

### Architecture

- **统一语义层规则体系 V1.3（需求 10.27/10.29）**：`src/semantic.rs` 新增
  `SemanticRule` trait、`RuleContext`、`builtin_rules()` 与
  `validate_with_rules(wf, catalog, source, filename, extra_rules)`；`validate()`
  签名不变，编译管线在 IR 生成前强制走规则管线。内置 5 条规则：
  `DuplicateVariableRule`（CF2003，AST 层兜底，解析层已有 CF2001 双保险）、
  `RetryConfigRule`（CF4423：`max_attempts > 0` 且
  strategy ∈ {fixed, exponential}，与 `execution_core::backoff_delay_ms` 一致）、
  `TimeoutConfigRule`（CF4424：step/runtime 超时毫秒 > 0）、`WaitConfigRule`
  （CF4419：wait 超时存在时必须 > 0）、`MetadataRule`（CF4425：空 tags）。
  10.17/10.18（权限/资源声明）为设计决策：Domain AST 无对应声明节点，权限由
  Agent 运行时校验 + 编译期 CF3001 能力检查承担。
- **YAML 解析资源护栏（19.9/19.10）**：`src/yaml/convert.rs` 新增三道护栏——
  源码 ≤ 1MiB（CFY-SCHEMA-1005，libyaml 之前拒绝）、结构深度 ≤ 100
  （CFY-SCHEMA-1006）、节点数（含锚点别名展开）≤ 100,000（CFY-SCHEMA-1007，
  别名炸弹防护）；与 libyaml 原生约 121 层深度上限构成双重防线。
- **表达式子系统资源防线与解析缓存（19.3/19.11/19.26）**：
  `cloudflow-engine-core` 的 `expression` 模块新增表达式长度上限 16KiB
  （CFY-EXPR-103）、嵌套深度预检 512（CFY-EXPR-104，O(n) 预扫描、跳过字符串
  字面量中的括号）与全局解析缓存 `EXPR_CACHE` / `VALUE_CACHE`
  （`LazyLock<Mutex<HashMap>>`，容量 1024、超容整体清空，缓存 rebase 前相对坐标
  节点）；提供 `expression_cache_stats` / `value_cache_stats` /
  `clear_parse_caches` 可观测接口。
- **HTTP 编译缓存（19.17）**：新增 `src/compile_cache.rs` `CompileCache`
  （容量 256，键 = SHA-256 双 u64 + 长度 + 文件名 + 语言 + target + 能力目录
  指纹，超容整体清空）；`/api/compile` 仅对匿名源（`filename` 为空，include
  不可能、无缓存过期风险）启用缓存，`.flow` 路径请求不走缓存。

### Changed

- **CF3104 include 循环引用错误不再泄露绝对路径（19.13）**：错误消息改为展示
  用户书写的相对 `include.path`。
- `src/lib.rs` 的 `Language` 增加 `Hash`（编译缓存键需要）。

### Fixed

- **表达式解析缓存 Mutex 自死锁（线上事故）**：解析缓存首版
  `cache_lock(...).get(text)` 守卫跨 `match` 存活，miss 分支二次加锁
  自死锁（std Mutex 不可重入），导致任何含表达式的工作流首次解析即挂起
  （`cargo test` 的 `compiles_demo_workflow_to_versioned_ir` /
  `runtime_loads_demo_ir_and_schedules_dag` 与 `cloudflowc compile` 均卡死）。
  修复为先将查找结果提取为局部 `Option<Arc<..>>`（守卫在语句末释放），miss
  分支再单独加写锁；`parse_expression_string` / `parse_value_string` 同模式
  修复，并由 `tests/cloudflow_security_bounds.rs` 回归覆盖。

### Added

- **基准测试**：`benches/compile_bench.rs`（criterion，`[[bench]] harness = false`）：
  DSL 编译中位数 ~333µs、YAML 编译 ~344µs、表达式解析（命中缓存）~1.7µs。
- **安全审计脚本**：`scripts/security-audit.sh`（cargo-audit 依赖扫描，工具缺失
  时跳过 + 资源边界测试 + 护栏常量断言）。
- **新增测试套件**：`tests/cloudflow_semantic_v13.rs`（14 用例：规则体系、
  CF2003/CF4419/CF4423/CF4424/CF4425、多错误收集）与
  `tests/cloudflow_security_bounds.rs`（12 用例：1.2MiB 大源、120/300 层深度、
  1000×200 别名炸弹、20K 字符表达式、5000 括号嵌套、600 三元、字符串内括号、
  缓存跨源正确性、白名单函数拒绝、HTTP 413 且不泄露路径）。

### Docs

- `docs/CLOUDFLOW_ERROR_DESIGN.md`：新增 CF4419/CF4423/CF4424/CF4425 与
  CF2003（V1.3 语义规则体系）、CFY-SCHEMA-1005..1007、CFY-EXPR-103/104；
  CFY-EXPR-102 改写为 DSL+YAML 共用。
- `docs/CLOUDFLOW_DESIGN.md`：新增「统一语义层规则体系 V1.3」章节（规则接口、
  五规则表、10.17/18 与 10.24/25/30 设计说明）；修正「不存在 YAML 兼容
  解析器」的过期表述。
- `docs/CLOUDFLOW_YAML_DESIGN.md`：0.1.9 状态说明 + 新增「0.4 解析资源护栏」
  章节（护栏表 + libyaml 双重防线 + 测试）。
- `docs/CLOUDFLOW_EXPRESSION.md`：API 条目补充 + 新增「3.1 解析缓存与资源
  防线」（含死锁事故修复说明）。
- `docs/CLOUDFLOW_SECURITY.md`：新增安全白皮书（威胁矩阵、YAML 护栏、表达式
  沙箱、泄露防护、缓存安全、纯度/线程安全、Agent 隔离、残留风险清单：
  cargo-fuzz 目标与进程级监控为后续项）。
- `docs/README.md`：索引补充安全白皮书条目。
- `README.md`：构建与测试（bench / security-audit / 边界测试命令）、表达式
  子系统缓存条目、YAML 护栏错误码、HTTP 编译缓存说明、设计文档清单。

## 0.1.10 - 2026-08-21

### Architecture

- **统一执行引擎 crate 抽离（需求 §1/§7）**：语言无关共享层全部迁入独立 crate
  `cloudflow-engine-core`（`crates/cloudflow-engine-core`）——领域 AST（`ast`）、诊断
  （`diagnostic`）、Workflow IR（`ir`）、IR 契约校验器（`ir_validate`）、表达式子系统
  （`expression`）、执行语义核心（`execution_core`）、开发调试面（`dev_exec`）。宿主 crate
  `src/lib.rs` 仅做 `pub use` 再导出，既有 `crate::ast::*` / `crate::ir::*` /
  `crate::dev_exec::*` / `crate::ir_validate::*` 调用路径不变；`cloudflowc` CLI 仅依赖执行核心，
  不引入数据库 / gRPC / HTTP 服务面（§7.14–§7.18）。
- **Capability Agent crate 抽离（§7.19）**：`cloudflow-agent`（`crates/cloudflow-agent`，
  proto + tonic 客户端 + 服务实现）为生产执行面唯一能力调用出口；宿主 crate 以
  `crate::agent` 再导出，调用路径不变。
- **统一调度驱动 `engine`（§1.1–§1.25）**：`execute(ir, context, deps)` / `execute_sync`，
  行为完全由 `EngineDeps`（`StateStore` / `LogSink` / `ActionExecutor` / `EventPublisher` /
  `Clock` / `ConfigProvider`，trait 对象注入）决定。生产执行面（宿主 `src/execution.rs`：
  `MysqlStateStore` + `AgentActionExecutor` + `TracingLogSink` + MQ 事件）与开发调试面
  （`InMemoryStateStore` + `InMemoryLogSink` + `MockActionExecutor`）共享同一驱动，
  不重复定义执行引擎；生产面行为（CF6001/CF1301/CF2101 等错误码、日志与节点记录语义）与旧
  1874 行实现逐字节一致。
- **HTTP 调试入口生产仿真 profile（§6.3/§6.4/§6.9/§6.10）**：`POST /api/dev/execute`
  请求体支持 `profile: "inmem"`（缺省，纯内存 Mock 动作，零网络）或 `"agent"`（经
  `CLOUDFLOW_TEST_AGENT_ENDPOINT` 真实调用测试环境 Capability Agent，验证 builtin/api/plugin
  全链路；状态与日志仍仅在内存，不写生产数据库）。`profile` 非法或未配置端点 → 400，测试
  Agent 不可用 → 503，错误消息固定、不泄露内部路径。
- **安全边界保持**：`/api/dev/execute` 与 `/api/dev/openapi.json` 仅在
  `CLOUDFLOW_ENABLE_DEBUG_EXECUTE=true` 时注册路由；关闭态为 axum 默认 404（空响应体，零特征
  泄露）。

### Changed

- **动作执行器抽象统一（消除双 trait）**：删除调试面同步
  `dev_exec::ActionExecutor`（含 `clone_box`）及其派生类型
  `DevActionInvocation` / `DevActionOutcome` / `DevActionError` 与私有适配器
  `DevActionExecutor`。调试面直接复用统一驱动的异步 `engine::deps::ActionExecutor`
  （经 `dev_exec` 再导出，`crate::dev_exec::ActionExecutor` 路径保留且指向同一
  trait）：`MockActionExecutor` 直接实现驱动 trait（行为逐字保持：CF5002/canned/
  echo 回显），`dev_execute_sync` / `dev_execute` / `dev_execute_async` 与 CLI/HTTP
  入口改为 `Arc<dyn ActionExecutor>` 注入；HTTP `profile=agent` 的
  `AgentDevActionExecutor` 改为统一驱动 trait 的原生 gRPC 异步实现（移除
  `block_on` 同步桥接）。使用规范：生产面/驱动内部 = `engine::deps::ActionExecutor`
  （`AgentActionExecutor`）；调试面 = 同一 trait 的 `MockActionExecutor` / gRPC 仿真
  实现；全链路动作调用只经该单一抽象（需求 §7）。

### Fixed

- **dev-inline DAG 死锁（CFD-8103）**：顶层控制分支（switch/condition/parallel）在调试面内联
  执行后未把分支后代标记为 DAG 终态，依赖分支根节点的下游永远不被调度。修复：调试面顶层控制
  分支返回 `CompletedWithSkips`（分支后代全部标记终态/skipped）；生产 Deferred 分支行为不变。
- **IR serde 宽松化**：可选 IR 集合字段（`extensions`/`labels`/`permissions`/
  `resource_limits`/`variables`/`outputs`/`nodes`/`edges`/`inputs`/`outputs`（节点级）/
  `depends_on`/`retry_on`/`children`/`arguments`）加 `#[serde(default)]`，最小化 IR 示例可反序列化；
  序列化输出保持字节一致。
- **dev-exec 测试 `KeyCaptor`**：`clone_box` 共享同一 `Arc` 收集器（原实现克隆内部
  `Mutex`，插件能力键捕获对断言不可见）。

### Added

- **示例 IR 回归（§7.21/§7.22）**：`examples/ir/` 三个可执行 IR（`01_sequential_flow` /
  `02_condition_branch` / `03_plugin_retry`，含插件冒号能力 `plugin:8ae47c8d:generate_report`
  + 指数重试 + 超时）+ `README.md` 运行说明；`tests/cloudflow_examples_ir.rs` 自动遍历目录做
  “反序列化 → CFI-7xxx 契约校验 → dev-execute（inmem）”回归（2 项测试）。

### Changed

- 文档同步至三 crate 布局：`docs/CLOUDFLOW_DEV_EXECUTE.md`（统一驱动 + `EngineDeps` +
  profile 语义与状态码）、`docs/CLOUDFLOW_ERROR_DESIGN.md`（错误码分层与路径修正）、
  `docs/CLOUDFLOW_COMPILER_GUIDE.md`（crate 布局与部署参数
  `CLOUDFLOW_TEST_AGENT_ENDPOINT`）、`docs/CLOUDFLOW_EXPRESSION.md`（表达式子系统路径）、
  `docs/CLOUDFLOW_DESIGN.md`（统一执行引擎架构章节）。
- 新增表达式子系统详细文档 `crates/cloudflow-engine-core/src/expression/README.md`（文件
  职责、公开 API、语法优先级、19 个白名单函数、运行期求值、安全设计、DSL 双文件同步约束、
  扩展新函数流程）；同步修正 `src/grammar.pest` 与 `builtins.rs` 中的陈旧路径注释。
- **`syntax-highlight/` 生成管线同步至新布局**：`build_spec.py` / `completion_builder.py` 的
  `AST_PATH` 由已不存在的 `src/ast.rs` 修正为 `crates/cloudflow-engine-core/src/ast.rs`
  （此前 `generate.py` 会直接读取失败）；`config.py` 登记 `null` 字面量
  （`null_literal` 分类，消除 `unclassifiedTokens`）并修正表达式子系统路径注释；
  `converters/hljs.py` 的 `literal` 关键字类纳入 `null_literal`；重新生成
  `syntax-highlight/build/` 与 `vscode/syntaxes/`、前端 Monaco 产物（元数据
  `generatedFrom.ast` 指向新路径）；`docs/CLOUDFLOW_LANGUAGE_EXTENSION_GUIDE.md` /
  `CLOUDFLOW_V1.2_DSL_EXTENSION.md` / `CLOUDFLOW_YAML_DESIGN.md` /
  `PLUGIN_AUTOMATION_PLATFORM_DESIGN.md` 中的旧路径同步更新；生成器 55 项测试全部通过。
- 测试基线：`cargo test --workspace` **205 passed / 0 failed / 3 ignored**（较 0.1.9 新增 2 项
  示例 IR 回归）。

## 0.1.9 - 2026-08-21

### Added

- **开发调试执行入口 Dev-Execute（需求 §2/§4/§9/§10）**：直接接收 `workflow.cloudflow.io/v1`
  IR JSON 纯内存执行——不写数据库、不记执行任务 ID、不持久化日志、不依赖 MQ/Redis。
  - `src/dev_exec.rs`（开发调试面）：同步确定性内存执行引擎（`dev_execute_sync` /
    `dev_execute`(JSON) / `dev_execute_async`(spawn_blocking)）；控制流语义
    （condition/switch/foreach/while/for-range/try-catch-finally/parallel/wait/delay/
    assert/validate/return/break/continue）唯一收敛于新增的 `src/execution_core.rs`
    （双执行面共享层，见 0.1.9 Architecture），本面只保留调试 I/O（内存日志/快照/
    断点/注入失败）与 CFD-81xx 错误码包装；`DevConfig` 支持断点、单步、
    `skip_nodes`、`mock_outputs`、`inject_failures`、`action_latency_ms`（模拟延迟计入全局
    超时虚拟时间）、表达式开关、日志级别/节点过滤；结果含 `node_results`（status/attempts/
    耗时/input/output/error）、`outputs`、`errors`、全环节 `logs` 与可序列化
    `context_snapshot`（`{vars, steps:{id:{output}}, outputs, status}`）。
  - `src/ir_validate.rs`：**唯一 IR 契约校验实现** `validate_ir_contracts`（纯函数、一次收集
    全部问题，`IrContractIssue{code,path,node_id,message}`），错误码 **CFI-7001–CFI-7028**
    （apiVersion/kind/元数据、节点 ID 与类型、各控制节点必备配置、edges 引用、环检测、
    变量类型、trigger、retry/timeout 配置、表达式结构 `$ref/$expr/$template/$pipeline`
    与引用静态解析——管道 filter 谓词行上下文的裸标识符放行）。
  - `src/bin/cloudflowc.rs`：`dev-execute` 子命令（`IR_FILE`/`-i`/stdin、`--var k=v`、
    `--mock`、`--no-validate`、`--timeout`、`--verbose`、`--breakpoint`、`--single-step`、
    `--skip-nodes`、`--level`、`--report`(Markdown)/`--report-json`、`--output-format`；
    退出码 0=success/waiting/breakpoint，1=failed/timeout，2=IO/参数）。
  - `src/http.rs`：`POST /api/dev/execute` 与 `GET /api/dev/openapi.json`（OpenAPI 3.0.3）
    仅当 `CLOUDFLOW_ENABLE_DEBUG_EXECUTE=true` 时**注册路由**；关闭态路由不存在，
    请求命中 axum 默认 404（空响应体，零特征泄露）；开启态要求与生产相同的
    `X-PCD-Service-Token`（401）；请求体非法 400；IR 契约校验失败 422 + `issues[]`
    （CFI-7xxx）；执行经 `dev_execute_async`（spawn_blocking）不阻塞异步 worker；
    请求体受全局 1 MiB 上限约束。
  - `src/runtime.rs`：新增 `RuntimeEngine::load_unvalidated`（仅 dev 入口在
    `skip_validation` 时使用；生产 `load` 仍强制 `validate_ir`）。
- **调试入口错误码 CFD-81xx**（`CLOUDFLOW_ERROR_DESIGN.md` 已登记）：CFD-8101 变量/表达式、
  CFD-8102 未知节点/动作、CFD-8103 调度死锁、CFD-8104 全局超时、CFD-8105 WAITING、
  CFD-8106 断点/单步、CFD-8107 提前 return、CFD-8108 失败剩余跳过；节点级失败复用
  生产 CF5xxx/CF4xxx/CF2xxx。
- **错误码分层前缀**（需求：区分编译时/运行时与所属层，不再借用 DSL `CF` 前缀）：
  `CF` = DSL 编译/控制面（既有主体）；`CFY-` = YAML 前端（2026-08-20）；
  `CFI-7xxx` = IR 契约校验层（本次由 CF7xxx 更名）；`CFD-81xx` = 开发调试执行面
  （本次由 CF81xx 更名）。`docs/CLOUDFLOW_ERROR_DESIGN.md` 已补分层分类总表。
- **IR 夹具集**：`tests/fixtures/ir/` 17 个 `workflow.cloudflow.io/v1` 样例（顺序/条件/并行/
  三类循环/重试超时/try-catch-finally/wait/子工作流/变量表达式/复杂组合 + 5 个非法反例）
  与逐文件预期表（`README.md`）。
- **文档**：新增 `docs/CLOUDFLOW_DEV_EXECUTE.md`（审计摘要、架构分层、IR 校验器、内存引擎、
  动作执行器契约、CLI/HTTP 入口、生产兼容与安全、测试报告、夹具清单、开发指南）；
  `docs/CLOUDFLOW_COMPILER_GUIDE.md` 部署参数表新增 `CLOUDFLOW_ENABLE_DEBUG_EXECUTE` 并补
  Dev-Execute 章节；`docs/CLOUDFLOW_ERROR_DESIGN.md` 新增分层分类总表与 CFI/CFD 段；
  本 README 增补 dev-execute CLI 示例与 HTTP 调试入口说明。

### Architecture

- **双执行面统一（消除重复执行语义）**：新增 `src/execution_core.rs` 作为控制流语义的
  唯一事实来源——条件求值（`condition_outcome(_with)`）、condition 分支提取
  （`condition_branches`）、try 结构解析（`parse_try_structure`）、循环计划
  （`parse_loop_plan`）、重试计划（`retry_max_attempts`/`retry_strategy`）与退避
  （`backoff_delay_ms`，`exponential_backoff_ms` 实现自 `engine.rs` 移入此处并保留公开
  再导出）、节点超时（`resolve_timeout`）、并行批大小（`parallel_max_concurrency`）、
  分支子树展开（`descendants(_for_children)`）、控制信号（`ControlSignal` +
  `runtime_signal`/`dev_signal`）。生产执行面 `src/execution.rs`（持久化调度器与执行
  协调器）与开发调试面 `src/dev_exec.rs`（纯内存同步 Dev Runner）均消费该层，
  两处原有的平行实现已删除。
- **IR 校验器统一**：`compiler::validate_ir` 由“轻量结构校验”改为
  `ir_validate::validate_ir_contracts` 的文本适配层（`CFI-xxxx: 消息 (路径)` 形态，
  签名仍为 `Vec<String>`）。生产 `RuntimeEngine::load`、HTTP
  `/internal/v1/cloudflow/validate-ir`（`/ir-validate` API）、Workflow Service 等微服务
  与开发调试入口从此共用同一校验器，开发面与生产面 IR 预校验行为一致
  （有意行为变化：由本 crate 编译器生成的 IR 恒通过；旧 `ir_has_cycle` 等重复实现删除）。
- **调试 HTTP 入口安全加固**：`/api/dev/execute` 与 `/api/dev/openapi.json` 改为
  `CLOUDFLOW_ENABLE_DEBUG_EXECUTE=true` 时**条件注册**（关闭态路由不存在 → axum 默认
  404，**空响应体**，不再返回含环境变量名的 JSON，端点存在性零泄露）；开启态两端点
  要求与生产相同的 `X-PCD-Service-Token`（此前无鉴权）；`/api/dev/execute` 执行改为
  `dev_execute_async`（spawn_blocking，不再阻塞 tokio worker）；请求体 1 MiB 上限与
  30s 超时沿用全局层。

### Fixed

- `normalize_variables`（生产与 dev 共用）：两遍求值——先落实 input/deferred 变量，
  再按序求值 local 变量，使 local 初始值可引用 input 变量（消除 BTreeMap 字母序依赖）。
- IR 表达式算术结果：整数运算保持 integer 数字类型（`json_number` 不再一律产生 5.0，
  对齐 GitHub Actions 表达式行为）。
- 管道 filter 谓词行上下文：`ir_validate` 的裸标识符 `$ref`（元素字段）不再误报 CFI-7023。

### Added (tests)

- `tests/cloudflow_dev_exec.rs` **68 项**（校验器 10 / DAG 4 / 循环 5 / switch+assert 4 /
  重试超时 5 / 异常 2 / wait+子工作流+动作键 3 / 表达式变量 3 / 输出快照 3 / 调试能力 11 /
  端到端 5（含 DSL 与 YAML 编译产物在 dev 引擎执行）/ HTTP 8（关闭态 404 空响应体 ×2、
  开启态 401、422、mock/skip、OpenAPI）/ 执行器契约+异步+回归 5（含生产 `validate_ir`
  与 `validate_ir_contracts` 对同一非法 IR 结论一致）），全部纯内存、无 DB/MQ/Redis。
- 全量 `cargo test`：**202 passed / 3 ignored / 0 failed**（既有 compiler/IR/YAML/表达式/
  V1.2/合规/契约全部回归全绿）；`cargo fmt --check` 通过；新增代码 `cargo clippy` 无新告警。

## 0.1.8 - 2026-08-20

### Added

- **表达式子系统补齐 GitHub Actions Expressions 全部命名函数（需求 6.11/6.32）**：在
  `src/expression/eval.rs::call_builtin` 新增 4 个与 GitHub 同名对齐函数——`to_json`（toJSON）、
  `from_json`（fromJSON，非字符串原样返回）、`format_number(number, [format])`（formatNumber，
  支持小数位 `0.00` 与千分位 `#,##0.00`）、`format_date_time(value, [format], [timezone])`
  （formatDateTime，.NET token `yyyy MM dd HH mm ss` + UTC 偏移/常见 IANA 时区，缺省 RFC3339）。
  白名单 `builtins.rs` 15 → **19 个**；`semantic.rs::inferred_expression_type` 补 3 个字符串返回
  （`from_json` 归 unknown，同 `get`）；`syntax-highlight/generator/config.py` 同步并重新生成补全
  产物（`builtinFunctions` 19）。GitHub 的 7 个命名函数全部同名对齐并额外 12 个，为严格超集。
- **YAML 反例集（需求 32.27/32.28）**：新增 `examples/yaml/invalid/` 11 个反例文件，头部用
  `# expected: <CODE>` 标注期望错误码（缺 `steps` / 缺 step `id` / 缺 `action` / `retry.count` 类型 /
  `retry.count` 非法值 / 未知字段 / `trigger.type` 非法 / `timeout` 非法 / `steps` 非列表 /
  `action.provider` 非法 / YAML 语法非法），覆盖 `CFY-SCHEMA-1001..1004` 与 `CFY-1001`。
- **设计文档固化（需求 35.x）**：`docs/CLOUDFLOW_YAML_DESIGN.md` 新增「§0 五层架构 · GitHub Actions
  对照矩阵 · 缺口清单（固化）」——五维度（YAML Syntax + Workflow Schema + Expression System +
  Validation + Domain Semantics）逐层落到代码、逐项 GitHub 对照矩阵、缺口清单（完整对齐 /
  形态不同 / 明确不支持 / 未实现）。`docs/CLOUDFLOW_EXPRESSION.md` 新增 §4.1 GitHub Actions
  Expressions 对齐矩阵。

### Changed

- **文档计数同步**：函数数 15→19、`tests/cloudflow_expression.rs` 16→17、`tests/cloudflow_yaml.rs`
  20→21，涉及 README、`CLOUDFLOW_YAML_DESIGN.md`、`CLOUDFLOW_EXPRESSION.md`、`CLOUDFLOW_COMPLETION.md`、
  `CLOUDFLOW_LANGUAGE_EXTENSION_GUIDE.md`、`CLOUDFLOW_YAML_GITHUB_ACTIONS_ALIGN.md`。

### Added (tests)

- `tests/cloudflow_expression.rs` 16 → **17 项**（`expression_github_actions_parity_functions`）。
- `tests/cloudflow_yaml.rs` 20 → **21 项**（`invalid_yaml_examples_fail_with_expected_code`）。
- 全量 `cargo test --locked --all-features`：**124 → 126 passed**；`cargo fmt --check` 通过；
  `all_yaml_examples_compile` 跳过 `*.legacy.workflow.yaml`（旧版留档不编译）。


## 0.1.7 - 2026-08-20

### Added

- **CloudFlow YAML Workflow Schema 校验层（需求 5.22/5.27/31.x/35.x）**：新增
  `src/yaml/schema.rs`，作为 YAML 编译**第一步**（31.2）做形状校验：
  - **必填字段**（CFY-SCHEMA-1001）：顶层 `steps`、step 的 `id`/`action`（或 `workflow`/`approval`/控制字段）、
    `catch` 的 `action`、`parallel.tasks`、`foreach.do`（需求 31.3）。
  - **类型校验**（CFY-SCHEMA-1002）：`steps` 非列表、step 非对象、`input`/`with` 非对象、
    `retry.count` 为字符串、`runtime`/`trigger`/`workflow` 形状等（需求 31.4）。
  - **未知字段**（CFY-SCHEMA-1003）：顶层/`workflow` 元数据/step/`retry`/`timeout`/`on_error`/`parallel`/
    `switch`/`foreach`/`catch`/`runtime` 的未知键，含 31.22“是否想使用 X 而不是 Y？”近邻建议。
  - **非法值**（CFY-SCHEMA-1004）：`retry.count` 负数、`strategy`/`trigger.type`/`action.provider`
    白名单外、`timeout` 时长格式错误（需求 31.6）。
  - 每一条错误携带 **YAML 字段路径**（`steps[2].retry.count`）与行号（容器级错误定位到“容器首个键”
    所在行，逐值错误定位到标量行），一次收集多条（31.7/31.8）。
- **JSON Schema 生成（需求 31.10/31.18）**：`cloudflow_runtime::emit_yaml_json_schema`
  （`src/yaml/schema.rs::emit_json_schema`）从统一定义生成 draft-07，落盘
  `schemas/yaml-workflow.schema.json`；测试 `yaml_json_schema_matches_ondisk` 校验不漂移，
  重新生成为 `UPDATE_YAML_SCHEMA=1 cargo test --test cloudflow_yaml yaml_json_schema_regenerate_with_env`。
- **模板文件（需求 31.28/31.29）**：`examples/yaml/template.flow.yaml`（含字段注释与示例），
  纳入示例编译循环（`all_yaml_examples_compile`）。

### Changed

- **错误码分工**：`CFY-1001` 保持 YAML 解析；`CFY-1002` 收敛为转换期结构/语义错误（如输入/变量
  重复声明）；原 `CFY-1003` 由 `CFY-SCHEMA-1003 UNKNOWN_FIELD` / `CFY-SCHEMA-1004 INVALID_VALUE`
  取代。`parse_yaml_detailed` 先跑 Schema 收集全部 `CFY-SCHEMA-*`，再尽力构建 AST 并合并转换/
  语义诊断；形状错误不再让 serde 强类型反序列化重复报 `CFY-1001`。
- **消除重复诊断**：`convert.rs` 删除 `yaml_field_error`（CFY-1003）与全部形状诊断分支
  （缺字段/类型/未知/非法值），形状信息收敛为 Schema 层唯一事实源；转换层保留构建语义
  （丢弃非法步骤保住尽力构建）与重复声明等语义诊断。

### Added (tests)

- `tests/cloudflow_yaml.rs` 15 → **20 项**：新增 Schema 多错误收集（路径+行号）、未知字段修复
  建议、compile 对非法值拦截、JSON Schema 与生成器一致性；`examples/yaml/` 13 → **14 个**
  （含 `template.flow.yaml`）。
- 全量 `cargo test --locked --all-features`：**119 → 124 passed**；`cargo fmt --check` 通过；
  `src/yaml/*` clippy 无告警；DSL 核心既有结构告警保持不改（需求 4/18.x）。

## 0.1.6 - 2026-08-20

### Added

- **YAML 前端对齐 GitHub Actions 表达式与能力引用（需求 28.x/6.32）**：
  - 插件能力冒号形式：`action: "plugin:<plugin_id>:<function>@<version>"`（`split_action_version`
    拆 `@version`；兼容 `plugin:<id>:<function>` 不带版本与对象/点号形式），与 DSL
    `action plugin {}` 解析到同一 `ActionNode`（provider/plugin_id/function/version），语义层
    `action_key` `plugin:<id>:<function>` 与能力 Hub 注册键一致。
  - **表达式/插值统一为 `${{ }}`**：新增 `src/expression/parser.rs::parse_interpolated_value`
    （`${{ ... }}` → `ValueNode::Template` 段，简单引用折叠为 `VariableRef`、复杂表达式折叠为
    `Expression`；无插值返回 `None`），`src/expression/mod.rs` 导出；YAML
    `convert.rs::whole_expression_index` / `string_to_value` 只匹配 `${{`，**不再接受 `${ }`**。
  - 表达式 `workflow.failed` / `workflow.failedStep` 上下文在 YAML 中可直接引用（语义层已放行
    `workflow.` 命名空间）。

### Changed

- **移除旧版 `automation.pcd/v1` 兼容解析**（不做重复 YAML 解析规则）：删除
  `normalize_document` 的 `apiVersion/kind/metadata/spec/limits` 提升、`limits_to_runtime`、
  `YamlDocument.kind`、`YamlWorkflowMeta.displayName` 驼峰别名；`convert_step` 只认本地字段
  （移除 `uses`/`needs`/`result` 别名）、`convert_retry`/`convert_runtime` 移除驼峰
  `maxAttempts`/`max-attempts`/`backoff`/`maxParallel`（只保留 `count`/`max_attempts`+`strategy`、
  `max_parallel`）。旧版示例已一次性转化为新版
  `examples/yaml/weekly_sales_report.flow.yaml`（含各步骤 `name`、`${{ }}`）。
- 服务分层：`src/yaml/mod.rs` 只导出 `parse_yaml` / `parse_yaml_detailed`；跨前端调度
  （`Language` / `language_of` / `parse_frontend_detailed`）收敛于 crate 根层 `src/lib.rs`。

### Fixed

- **UTF-8 多字节定位 panic**：`src/yaml/locator.rs::char_boundary` 与 `src/diagnostic.rs::line_column`
  改用 `char_boundary` 对齐，修复中文内容既有列数/span 计算的切片越界；表达式
  `rebase_expression`/`rebase_value` 同步按字符边界对齐 base（中文 `if`/`when`/插值不再 panic）。

### Added (tests)

- `tests/cloudflow_yaml.rs` 13 → **15 项**：新增 `yaml_plugin_action_matches_dsl_hub_key`（插件冒号
  与 DSL 同一 hub 键）、`yaml_double_brace_expression_and_template`（`${{ }}` 整串 + 插值）；
  移除旧版 `automation.pcd/v1` 兼容用例（`yaml_legacy_*`），全部 YAML 测试改用 `${{ }}`。
- `tests/cloudflow_expression.rs` 15 → **16 项**：`expression_interpolation_github_actions_double_brace`
  （`parse_interpolated_value` 只匹配 `${{ }`；`${ }` 不再插值）。
- `examples/yaml/` 12 → **13 个** `.flow.yaml`（新增 `weekly_sales_report.flow.yaml`）。
  旧版原文按需求留档于 `examples/yaml/weekly_sales_report.legacy.workflow.yaml`（注解
  “留档不编译”），`all_yaml_examples_compile` 跳过 `*.legacy.workflow.yaml`。
- 全量 `cargo test --locked --all-features`：**116 → 119 passed**；`cargo fmt --check` 通过；
  清理 `src/yaml/*` 全部 clippy 告警（needless_borrow/redundant_closure/single_match/预留字段
  allow(dead_code)），DSL 核心既有结构告警保持不改；DSL 示例/IR/AST 与错误行为不变（需求 4/18.x）。

## 0.1.5 - 2026-08-20


## 0.1.5 - 2026-08-20

### Changed

- **表达式子系统增强（需求 6.x 补齐，只增不减）**：
  - 新增 `null` 字面量（`ValueNode::Null`）：`src/expression/grammar.pest` 与 DSL
    `src/grammar.pest`（唯一事实来源 + 同步定位器）均新增 `null` 规则，parser/语义/编译器/
    AST 打印/YAML 转换全链路补齐（需求 6.3）。
  - **索引访问运行期求值（需求 6.6）**：重写 `src/execution.rs::lookup`（拆分 `deref_path` /
    `apply_index_tokens` / `split_index_tokens`），支持 `vars.files[1].name`、
    `steps.parse.output[2]` 越界→变量错误；语义层 `validate_reference::first_segment` 按基名
    校验 `vars.files[0]` / `items[0]` 并放行局部变量索引。
  - **求值器集中到子系统**：新增 `src/expression/eval.rs::call_builtin`（需求 6.18/6.22/6.25/6.27）
    ——内建函数实现唯一收敛于此，生产执行端 `execution.rs::call` 委托；白名单
    `builtins::BUILTIN_FUNCTIONS` 扩至 **15 个**（`size/len/contains/starts_with/ends_with/now/get/
    trim/to_upper/to_lower/range/abs/round/floor/ceil`），旧消息 `未知内置函数` 保持兼容。
  - **API 版本（需求 6.29）**：新增 `API_VERSION = "expr.cloudflow.io/v1"`，独立于前端语言版本。
  - 同步 `src/grammar.pest` 表达式区段与子系统逐字一致（`dsl_sync_null_literal` 等回归）；语义层
    `inferred_expression_type` 补齐 15 个函数返回类型。
  - 工具链同步：`syntax-highlight/generator/config.py::BUILTIN_FUNCTIONS` 新增 10 个函数签名并
    重新生成 `syntax-highlight/build/` 与 Web 补全（`PrivateCloudDisk-web`）。

### Added

- 测试：`tests/cloudflow_expression.rs` 扩至 15 项（null、扩展内建函数、API 版本、`dsl_sync_*`）；
  `src/execution.rs` 新增索引访问与扩展函数单元测试（2 项）；`tests/cloudflow_yaml.rs` 扩至 13 项
  （YAML `null` → `ValueNode::Null`）。全量 `cargo test --locked --all-features` **116 passed**。

## 0.1.4 - 2026-08-20

### Changed

- **表达式子系统落地（抽取 DSL 表达式实现，供多前端复用）**：新增 `src/expression/` 子系统
  （`grammar.pest` + `parser.rs` + `builtins.rs` + `mod.rs`），作为 CloudFlow 唯一表达式实现方
  （`expr.cloudflow.io/v1`，规格见 `docs/CLOUDFLOW_EXPRESSION.md`）。
  - 将 DSL `src/parser.rs` 的 `parse_expression` / `parse_value` / `parse_call` /
    `parse_pipeline_op` / `parse_string_value` / `is_simple_reference` / `normalize_reference` /
    `value_from_expression` 等表达式/值构建逻辑**完整抽取**（等价移植、只增不减）到子系统；
    表达式 AST 仍由领域 `ast.rs` 定义，不重复定义。
  - 将 DSL `src/grammar.pest` 的表达式规则**完整抽取**到 `src/expression/grammar.pest`，并新增
    `runtime_path`（属性访问）、`index_access`（索引访问）、`input.`/`env.` 引用、`KB/MB/GB`
    常量等只增扩展；DSL 语法中保留的表达式规则降级为“切分定位器”并在注释中标明真源
    （pest 不支持跨文件 grammar include）。
  - DSL 前端改为委托：`parser.rs` 通过 `expr_node`/`value_node`/`value_node_text` 把表达式
    **字符串**交给子系统解析（需求 6.21/6.31），DSL 不再保留表达式构建代码。
  - 新增 `src/expression/builtins.rs`：`KB/MB/GB` 常量与内建函数白名单（`size/len/get/now/range/trim`，
    可扩展，需求 6.11/6.22/6.27）。
  - 新增集成测试 `tests/cloudflow_expression.rs`（11 项：字面量、引用/属性/索引、运算符/三元、
    函数/管道、常量、值上下文与插值、错误定位、DSL 委托等价性、`dsl_sync_*` 双文件语法同步）。

### Added

- **YAML 前端（第二前端语言）落地**（需求 2.x/28.x，设计见 `docs/CLOUDFLOW_YAML_DESIGN.md`）：
  与 CloudFlow DSL 共存，统一编译到 `workflow.cloudflow.io/v1` IR。
  - 新增 `src/yaml/`：`model.rs`（serde derive 强类型 `YamlWorkflow`，模块私有）、`convert.rs`
    （YAML→共享 Domain AST：`parse_yaml` / `parse_yaml_detailed` / `normalize_document` /
    `normalize_refs` / `parse_duration_ms`，其中 `parse_yaml{,_detailed}` 为模块**唯一**对外导出）、
    `locator.rs`（`pub(crate)` 文档序标量近似回填行/列）、`mod.rs`（仅 `pub use
    convert::{parse_yaml, parse_yaml_detailed}`，不含任何跨前端调度逻辑）。
  - 使用**成熟第三方库 `serde_yaml_ng 0.9`** 解析 YAML（需求 7.x，禁止自研），并加深度/节点上限、
    重复键检测等加固。
  - 新增分层入口 `compile_source_named_for_language` / `parse_ast_for_language`
    （`src/lib.rs`）；YAML 复用领域 `ast.rs`、统一语义与 IR 生成，不复制 AST/表达式。
  - **前端调度上移 crate 根层（分层修正）**：`Language` / `language_of` / `parse_frontend_detailed`
    定义于 `src/lib.rs`（对外 `cloudflow_runtime::{Language, language_of, parse_frontend_detailed}`），
    不再放置于 `yaml` 子模块；原 `yaml::Language` / `yaml::language_of` /
    `yaml::parse_frontend_detailed` 调用点（`lib.rs`、`http.rs`、`bin/cloudflowc.rs`、
    `tests/cloudflow_yaml.rs`）已同步改为根层路径。
  - CLI：`--lang dsl|yaml`（`cloudflowc.rs`，clap 用 `language` 字段 + `#[arg(long="lang")]`，
    因 `lang` 与 clap 内建属性名冲突）；`-A/--emit-ast` 别名 `--emit-domain-ast`；支持
    `--lang yaml -i` 内联与 stdin。
  - HTTP：`CompileRequest.language: Option<String>`（`"dsl"|"yaml"`），`src/http.rs` 按语言路由。
  - AST：`WorkflowNode.outputs: BTreeMap<String, ValueNode>`（YAML `outputs:` 使用）；IR
    `spec.outputs` 由 `workflow.outputs` 回填（`src/compiler.rs`）；语义层 `validate_reference`
    放开 `env.`/`input.` 命名空间（需求 6.4，DSL 行为不变）。
  - 表达式委托（需求 6.31/28.56）：YAML 只切出 `${...}` 字符串，交给表达式子系统
    `parse_expression_string` / `parse_value_string`，不重复定义表达式文法/AST。
  - 控制流映射：`switch/cases/default`、`foreach`、`parallel`、`approval`→`approval.request`、
    顶层 `catch/finally`→`TryCatch`；`depends` 支持字符串/数组与别名展开。
  - 错误码：`CFY-1001`（YAML 解析）/ `CFY-1002`（结构）/ `CFY-1003`（字段），与 DSL 共用诊断结构。
  - 示例与测试：`examples/yaml/`（12 个 `.flow.yaml`，对应 DEMO 1–18）+ `tests/cloudflow_yaml.rs`
    （12 项：识别、trigger 映射、引用规整、retry/timeout/on_error、parallel/foreach/switch/approval、
    catch/finally、CFY 错误码、DSL↔YAML IR 等价、全示例编译）。

### Fixed

- 表达式解析错误码统一为 `CFY-EXPR-102`，span 对齐前端源码绝对坐标（多字节安全）。
- **表达式语法双文件同步**：`src/expression/grammar.pest` 的扩展（`input.`/`env.` 引用、属性访问
  `object.property`、索引访问 `list[0]`、`KB/MB/GB` 常量）已逐字同步到 DSL `src/grammar.pest`
  对应区段（`primary`/`reference` 顺序一致），并新增 `dsl_sync_*` 测试验证 DSL 前端能切出
  这些扩展表达式（56 条共有规则逐条比对一致）。
- 白名单统一收敛到 `src/expression/builtins.rs`（`size/len/contains/starts_with/ends_with` +
  `filter/map/reduce` 管道）：语义校验 `src/semantic.rs` 改为调用 `builtins::is_builtin_function`，
  不再内联重复白名单。
- 语法高亮/补全同步：`syntax-highlight/generator/config.py` 新增 `input`/`env` 引用
  （`REFERENCE_PREFIXES` / `COMPLETION_REF_PREFIXES` / `environmentReference` 类别），并重新生成
  `syntax-highlight/build/`。
- 新增表达式子系统实现细节文档 `src/expression/README.md`（文件职责、公开 API、语法、两处
  grammar 同步规则、语义/执行端状态、扩展流程）。

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
