# CloudFlow YAML × GitHub Actions 对齐规范

- 状态：**已落地**（`PrivateCloudDisk-cloudflow-runtime/src/yaml/`，V0.1.6）
- 定位：CloudFlow YAML 是 CloudFlow Workflow 的轻量声明式前端，在**自身层级结构**（workflow /
  trigger / runtime / input / variables / env / steps / outputs / catch/finally）基础上，
  **对标 GitHub Actions**：表达式系统（`${{ }}`）、能力引用（`plugin:<id>:<fn>@<v>`）、
  `with / if / retry / timeout` 等步骤层语义。
- CloudFlow YAML 只接受扁平与嵌套两种**本地**书写形态；**不接受**旧版 `automation.pcd/v1`
  包装（`apiVersion/kind/metadata/spec/limits`、`uses/needs/result`）。历史旧版示例已一次性
  转化为新版 `examples/yaml/weekly_sales_report.flow.yaml`（见第 3 节）。

> 本文与 `docs/CLOUDFLOW_YAML_DESIGN.md`（结构设计）、`docs/CLOUDFLOW_YAML_DEMO_DESIGN.md`
> （示例矩阵）、`docs/CLOUDFLOW_EXPRESSION.md`（表达式子系统）配套阅读。

## 1. 对标 GitHub Actions 的能力表

| GitHub Actions（步骤） | CloudFlow YAML（步骤） | 实现 | 说明 |
| --- | --- | --- | --- |
| `uses: owner/repo@v1`（第三方 action） | `action: plugin:<plugin_id>:<function>@<version>` | ✅ | 冒号两段 + `@version`，映射到 `ActionNode{provider=plugin, plugin_id, function, version}`，与 DSL `action plugin {}` 同一节点、同一能力 Hub 键 |
| `uses: actions/checkout@v4`（内置） | `action: builtin:<service>.<method>` | ✅ | 支持 `builtin:` 冒号、`builtin.` 点号、裸 `service.method` 三种写法 |
| `run:`（直接命令） | 无（CloudFlow 面向能力编排，代码执行走 plugin runtime） | — | 见「不支持项」 |
| `with:` 参数映射 | `with:` / `input:` | ✅ | 键 → action.arguments |
| `env:` 工作流环境 | 工作流级 `env:` | ✅ | 步骤级 env 不支持（用工作流级 env 表达） |
| `needs:` 依赖 | `depends:` / `depends_on:` | ✅ | 本地字段即 `depends`/`depends_on`；支持字符串/数组 |
| `if:` 条件 | `when:` / `condition:` / `if:` | ✅ | 值统一 `${{ ... }}` |
| `id:` + `${{ steps.id.outputs.x }}` | `output:` + `${{ steps.<id>.output[.field] }}` | ✅ | `steps.<id>.output[.field]` 语义分析 + 运行期索引求值 |
| `timeout-minutes:` | `runtime.timeout` / 步骤 `timeout:` | ✅ | 顶层 `runtime: {timeout, max_parallel, retry}` 或步骤级 `timeout` |
| `strategy.matrix` | 无直接等价 | — | 用 `parallel` / `foreach` 表达 |
| `continue-on-error` | `retry.on_error` / `catch` | ✅ | 步骤级 `on_error`、顶层 `catch`/`finally` |
| 失败重启（re-run） | `retry: {count\|max_attempts, strategy}` | ✅ | 本地键为 `count`/`max_attempts` + `strategy` |
| 表达式 `${{ }}` | `${{ }}` | ✅ | CloudFlow YAML **唯一**表达式/插值分隔符（第 4 节） |
| 事件触发器 `on: schedule` | `trigger: {type: schedule\|event\|http\|manual\|interval, ...}` | ✅ | 每工作流单一 trigger |
| 并发/资源 `concurrency` | `runtime.max_parallel` | ✅ | `runtime.max_parallel` 直接对应 |

## 2. 表达式 `${{ }}`（统一，不从双语法）

CloudFlow YAML **只定义 `${{ ... }}` 一种表达式分隔符**（对标 GitHub Actions），不再定义 `${ }`：

- 整串表达式：`action: "${{ vars.node }}"`、`when: ${{ steps.a.output.count > 0 }}` 切为
  `vars.node` / `steps.a.output.count > 0` 表达式（`whole_expression_index` 只匹配 `${{`）。
- 字符串插值：`"失败步骤：${{ workflow.failedStep }}"` → `ValueNode::Template`
  `["失败步骤：", VariableRef("workflow.failedStep")]`（`parse_interpolated_value` 只匹配 `${{`；
  IR 序列化为 `$template`，执行端逐段递归求值）。
- 引用上下文：`vars.`（变量/输入）、`steps.<id>.output[.field]`（步骤输出，运行期索引取值）、
  `env.`、`workflow.`。
- 内建函数白名单（19 个，含 4 个 GitHub 对齐：`to_json`/`from_json`/`format_number`/`format_date_time`）+ 管道 `filter/map/reduce` 与 GitHub Expressions 语义对齐。

> DSL 侧表达式/插值仍使用 `${...}` 字符串（由表达式子系统 `parse_value_string` 处理）；YAML 与
> DSL 的表达式**内部语义**经同一表达式子系统对齐，仅**前端分隔符**不同（YAML=`${{ }}`）。

## 3. 旧版示例 → 新版（一次性转化，示例即产物）

旧版 `automation.pcd/v1 weekly_sales_report` **不允许**再被解析；已转化并落盘为
`examples/yaml/weekly_sales_report.flow.yaml`（DSL 同义 `examples/weekly_sales_report.flow`）。
转化映射（供人工参考，编译期不生效）：

```yaml
metadata.name                   ──►  workflow.name
metadata.displayName            ──►  workflow.display_name
metadata.description            ──►  workflow.description
spec.trigger.type/cron/timezone ──►  trigger.{type,cron,timezone}
spec.limits.timeoutSeconds      ──►  runtime.timeout（1800 → "30m"）
spec.limits.maxParallel         ──►  runtime.max_parallel
spec.inputs                     ──►  input（输入变量声明）
spec.steps[].uses: builtin:…    ──►  steps[].action: builtin:…
spec.steps[].uses: plugin:…@v   ──►  steps[].action: "plugin:<id>:<fn>@<v>"
spec.steps[].with               ──►  steps[].with | input
spec.steps[].needs              ──►  steps[].depends
spec.steps[].if                 ──►  steps[].when
spec.steps[].result             ──►  steps[].output
spec.steps[].retry.maxAttempts  ──►  steps[].retry.max_attempts
spec.steps[].retry.backoff      ──►  steps[].retry.strategy
${{ expr }}                     ──►  ${{ expr }}（分隔符沿用）
```

新版关键片断（含 `name`、`${{ }}`、插件能力、插值）：

```yaml
workflow:
  name: weekly_sales_report
  display_name: 销售周报
  description: 每周一生成并保存销售周报

trigger:
  type: schedule
  cron: "0 8 * * 1"
  timezone: Asia/Shanghai

runtime: { timeout: 30m, max_parallel: 4 }

input:
  sales_node_id:    { type: string, required: true }
  template_file_id: { type: string, required: true }
  report_node_id:   { type: string, required: true }

steps:
  - id: collect_files
    name: 收集销售文件
    action: builtin:file.list
    input: { nodeId: ${{ input.sales_node_id }}, pattern: "*.xlsx" }
    output: excel_files
  - id: aggregate_data
    name: 销售数据统计
    depends: [collect_files]
    action: builtin:data.aggregate_excel
    input: { files: ${{ steps.collect_files.output }}, groupBy: region,
             metrics: ["sum(sales)", "avg(profit)"] }
    output: report_data
  - id: generate_report
    name: 生成销售报告
    depends: [aggregate_data]
    when: ${{ steps.aggregate_data.output.rowCount > 0 }}
    action: "plugin:8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7:generate_report@1"
    input: { data: ${{ steps.aggregate_data.output }},
             templateFileId: ${{ input.template_file_id }} }
    retry: { max_attempts: 2, strategy: exponential }
    output: report_file
  - id: save_report
    name: 保存报告
    depends: [generate_report]
    action: builtin:file.save
    input: { source: ${{ steps.generate_report.output.file_id }},
             targetNodeId: ${{ input.report_node_id }} }
  - id: notify_failure
    name: 失败通知
    when: ${{ workflow.failed }}
    action: api:user.notify
    input: { title: 销售周报生成失败, body: "失败步骤：${{ workflow.failedStep }}" }
```

## 4. 插件能力 Hub 对齐

`action: "plugin:<plugin_id>:<function>@<version>"`（冒号形式，GitHub-Actions 风格）与 DSL
`action plugin { id = "…" function = "…" version = "…" }`（块形式）解析到**同一 `ActionNode`**：
`provider=plugin, plugin_id, function, version`；语义层 `action_key` 均为 `plugin:<id>:<function>`，
能力中心按插件 ID 注册校验，版本进入 IR `ActionIr.version`（需求 21.x）。写法一览：

| 写法 | 解析结果 |
| --- | --- |
| `action: "plugin:8ae…:generate_report@1"` | provider=plugin, id=8ae…, function=generate_report, version=1 |
| `action: "plugin:8ae…:generate_report"` | provider=plugin, id=8ae…, function=generate_report, version=None |
| `action: { provider: plugin, id: …, function: …, version: … }` | 对象形式（同 DSL 块） |
| `action: plugin.8ae….generate_report` | 点号形式（DSL 遗留，等同冒号两段） |

## 5. 明确不支持 / 建议（如实标注）

- **旧版 `automation.pcd/v1` 包装**：`apiVersion/kind/metadata/spec/limits`、`uses/needs/result`、
  驼峰 `maxAttempts`/`backoff` 均**不再解析**；旧示例请按第 3 节一次性迁移到新版。
- **`run:` 直接命令**：CloudFlow 面向能力编排；若需任意代码执行，用 plugin runtime 封装成能力。
- **`strategy.matrix`**：多分支组合用 `switch` / `parallel` / `foreach` 表达。
- **步骤级 `env:` / `permissions:` / `concurrency:`**：统一用工作流级 `env`、`runtime`、权限声明表达。
- **工作级 `on:` 多事件路由**：CloudFlow 每个工作流单一 `trigger`（schedule/event/http/interval）。
- **步骤级 `continue-on-error` / `timeout-minutes`**：用 `retry.on_error` / 步骤 `timeout` 表达。

## 6. 测试与回归（需求 15.x / 29.x）

- `tests/cloudflow_yaml.rs`（**21 项**，含 Schema 校验层 + `examples/yaml/invalid/` 反例集：多错误收集、字段路径+行号、未知字段建议、JSON Schema 一致性）：
  - `yaml_plugin_action_matches_dsl_hub_key`（插件冒号与 DSL 同一 hub 键）
  - `yaml_double_brace_expression_and_template`（`${{ }}` 整串 + 插值）
  - `invalid_yaml_examples_fail_with_expected_code`（`examples/yaml/invalid/` 反例集按标注期望错误码逐一校验）
  - 其余：识别、映射、引用规整、控制流、CFY 错误码、全示例编译、DSL↔YAML IR 等价等
- `tests/cloudflow_expression.rs`（17 项，含 `expression_github_actions_parity_functions`）：含 `expression_interpolation_github_actions_double_brace`
  （`parse_interpolated_value` 只匹配 `${{ }`；`${ }` 不再插值）。
- `all_yaml_examples_compile` 现覆盖 **14 个** `examples/yaml/*.flow.yaml`（含模板 `template.flow.yaml`）；跳过 `*.legacy.workflow.yaml`（旧版 `automation.pcd/v1` 留档，不编译）。
- **Schema 校验（需求 31.x）**：`src/yaml/schema.rs` 作为编译第一步做形状校验，`CFY-SCHEMA-1001..1004`
  错误带字段路径（`steps[2].retry.count`）与行号；JSON Schema 生成自 `emit_yaml_json_schema`。
- 全量：`cargo test --locked --all-features` **126 passed**；`cargo fmt --check` 通过；`src/yaml/*`
  clippy 无警告（DSL 核心既有结构告警保持不改）。
