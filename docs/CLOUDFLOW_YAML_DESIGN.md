> **实现状态（2026-08-20，落地回写）**：本设计所依赖的 YAML 与 DSL 共享基础层——
> **CloudFlow 表达式子系统（`expr.cloudflow.io/v1`）已实现**，位于
> `crates/cloudflow-engine-core/src/expression/`（`grammar.pest` + `parser.rs` + `builtins.rs`
> + `eval.rs` + `mod.rs`），规格见 `docs/CLOUDFLOW_EXPRESSION.md`。DSL 前端已改为把表达式**字符串**委托子系统
> 解析并构建领域表达式 AST。本条重申下述设计约束：YAML Parser / DSL Parser 只负责把表达式当作
> 字符串切出，表达式词法（pest）、解析、AST 构建、白名单函数/常量全部由表达式子系统唯一承担，
> 任何前端**不得重复定义**表达式语法与 AST。\n> YAML 前端本体**已落地实现**（2026-08-20），架构、CLI、错误码与示例见文末\n> 「实现状态（YAML 前端落地回写）」一节。\n> **表达式能力补齐（0.1.5）**：求值器集中到 `eval.rs::call_builtin`（15 个白名单函数），\n> `null` 字面量与索引访问（`vars.files[0]` / `steps.parse.output[2]`）词法、语义、运行期均已实现；\n> `API_VERSION` 独立演进（需求 6.29）。
> **GitHub-Actions 对齐（0.1.6）**：YAML 前端已落地 GitHub Actions **表达式系统**与**能力引用**对齐——
> `${{ }}` 统一表达式/插值分隔符（不再定义 `${ }`）、插件能力冒号 `plugin:<id>:<function>@<version>`
> 与 DSL `action plugin {}` 同一 `ActionNode`/同一能力 Hub 键。CloudFlow YAML 只接受扁平与嵌套两种
> **本地**形态，**不解析**旧版 `automation.pcd/v1` 包装（`apiVersion/kind/metadata/spec/limits`、
> `uses/needs/result`）——旧版示例已一次性转化为新版 `examples/yaml/weekly_sales_report.flow.yaml`。
> 详细对齐表见 `docs/CLOUDFLOW_YAML_GITHUB_ACTIONS_ALIGN.md`（需求 28.x/6.32）。
> **Schema 校验层（0.1.7）**：新增 `src/yaml/schema.rs` —— YAML 编译第一步的形状校验
> （必填/类型/未知字段/非法值），一次收集多条 `CFY-SCHEMA-1001..1004` 错误（31.8），每条约带
> 字段路径（`steps[2].retry.count`）与行号（31.7）；JSON Schema 由 `emit_yaml_json_schema` 统一
> 生成（31.10/31.18），落盘 `schemas/yaml-workflow.schema.json`；模板
> `examples/yaml/template.flow.yaml`（31.28/31.29）。
> **表达式 GitHub 对齐 + 反例集 + 五维度固化（0.1.8）**：表达式子系统补齐 GitHub Actions
> Expressions 全部命名函数（`to_json`/`from_json`/`format_number`/`format_date_time`），白名单 15→19
> （`CLOUDFLOW_EXPRESSION.md` §4.1 对齐矩阵）；新增 `examples/yaml/invalid/` 反例集（11 个，头部
> `# expected: <CODE>` 标注期望错误码）与测试；「§0 五层架构 · GitHub Actions 对照矩阵 · 缺口清单（固化）」
> 完整记录 YAML Syntax + Workflow Schema + Expression System + Validation + Domain Semantics。
> **解析安全防线（0.1.9，2026-08-21）**：YAML 编译入口新增三道资源护栏
> （19.9/19.10/19.25）——源码 ≤ 1 MiB（`MAX_YAML_SOURCE_BYTES`，先于 libyaml 执行）、
> 嵌套深度 ≤ 100 层（`MAX_YAML_DEPTH`）、解析后节点总数 ≤ 10 万
> （`MAX_YAML_NODES`，约束锦点/别名展开）；超限分别报
> `CFY-SCHEMA-1005/1006/1007`，与 libyaml 自身递归限制构成双保障；
> 边界测试见 `tests/cloudflow_security_bounds.rs`。
>
CloudFlow YAML 不应该设计成“YAML 的简单包装”，而应该定位成：Cloud4 Workflow DSL 的轻量声明式前端。

也就是说：

* CloudFlow DSL：高级工作流编程语言（Programming Model）
* CloudFlow YAML：低代码工作流配置语言（Configuration Model）
* 两者最终：
```
CloudFlow DSL
     |
     | Compiler
     ↓
Workflow AST
     |
     ↓
IR
     |
     ↓
Workflow Runtime
CloudFlow YAML
     |
     | YAML Parser
     ↓
YAML AST
     |
     | Semantic Compiler
     ↓
Workflow AST
     |
     ↓
IR
     |
     ↓
Workflow Runtime
```
你的方向是正确的。

GitHub Actions 本质也是类似思想：它使用 YAML 作为工作流文件格式，然后在 YAML 结构里面定义 jobs、steps、uses、with、if、表达式上下文等能力。它并不是纯 YAML，而是在 YAML 数据结构之上建立了一套工作流语义。 

下面我给你设计一个完整的 Cloud4 YAML 规范。

⸻

Cloud4 Workflow YAML Design Specification v1.0

1. 设计目标

Cloud4 YAML 用于：

使用简单声明式配置描述企业自动化流程。

目标：

简单场景

例如：
```
上传文件
↓
解析文件
↓
生成缩略图
↓
AI分析
↓
保存结果
```
用户只需要 YAML。

⸻

中等复杂场景

支持：

* 条件判断
* 参数传递
* 表达式
* 并行
* 重试
* 超时
* 错误处理

⸻

高级场景

转 Cloud4 DSL：

支持：

* 复杂循环
* 动态流程生成
* 高级变量系统
* 自定义函数
* 模块化

⸻

2. 顶层结构设计

一个 Cloud4 YAML：
```
workflow:
  name:
  version:
  description:
  trigger:
  inputs:
  variables:
  steps:
  outputs:
  policies:
```
对应：
```
Workflow
 |
 |-- Metadata
 |
 |-- Trigger
 |
 |-- Input
 |
 |-- Variables
 |
 |-- Execution Graph
 |
 |-- Output
 |
 |-- Runtime Policy
```
⸻

3. Workflow Metadata

例如：
```
workflow:
  name: file-ai-processing
  version: 1.0
  description:
    文件AI处理流程
```
作用：

用于：

* 注册
* 管理
* 发布
* 版本控制

类似：

GitHub Action：

name: CI

⸻

4. Trigger 触发器

支持：

HTTP触发

trigger:
  type: http
  method: POST
  path: /file/process

⸻

MQ事件触发

trigger:
  type: event
  topic: file.created

⸻

定时

trigger:
  type: cron
  expression:
    "0 0 * * *"

⸻

5. 输入参数系统

类似函数参数。

例如：
```
inputs:
  fileId:
    type: string
    required: true
  userId:
    type: long
    required: true
```
等价：

Java:
```
workflow(
 String fileId,
 Long userId
)
```
⸻

6. Variables变量系统

定义运行时变量：
```
variables:
  retryCount: 0
  parserType: pdf

支持表达式：

variables:
  fileSize:
    expression:
      input.file.size
```
⸻

7. 核心 Steps

这是最重要部分。

结构：
```
steps:
  - id:
    name:
    action:
    input:
    output:
    depends:
    condition:
    retry:
    timeout:
```
⸻

例如：
```
steps:
  - id: parse_file
    name:
      文件解析
    action:
      file.parser
    input:
      file:
        ${input.file}
```
⸻

action

表示调用什么能力。

例如：

插件：

file.parser
ai.summary
email.send
http.request

⸻

8. 输入映射

例如：
```
input:
  fileId:
    ${workflow.fileId}
  mode:
    pdf
```
支持表达式：

${}

⸻

9. 输出

步骤输出：
```
output:
  result:
    type:
      object
```
后续引用：
```
${steps.parse.result}
```
⸻

10. 依赖关系

顺序执行：
```
steps:
 - id:a
 - id:b
   depends:
    - a
```
形成 DAG：
```
A
↓
B
↓
C
```
⸻

11. 条件判断

Cloud4 YAML 不采用复杂嵌套。

例如：

传统 YAML：
```
if:
  condition:
    expression:
      xxx
```
Cloud4：
```
condition:
  when:
    ${file.size > 100}
```
简单。

例如：
```
- id: large_file
  condition:
    ${input.size > 100MB}
  action:
    large.process
```
⸻

12. Else 分支

设计：
```
switch:
  expression:
    ${file.type}
  cases:
    pdf:
      goto:
        pdf_parser
    word:
      goto:
        word_parser
  default:
    goto:
      normal_parser
```
⸻

13. 并行执行

例如你的 OCR：
```
- id: analyze
  parallel:
    - ocr
    - summary
    - tag
```
对应：
```
        |
       analyze
     /    |     \
 OCR Summary Tag
     \    |     /
        merge
```
⸻

14. 重试机制

例如：
```
retry:
  max:
    3
  strategy:
    exponential
  interval:
    5s
```
生成：

第一次失败
↓
5s
↓
第二次失败
↓
10s
↓
第三次失败

⸻

15. 超时

timeout:
  30s

⸻

16. 异常处理

例如：
```
onError:
  retry:
    3
  fallback:
    notify_admin
```
⸻

17. 完整示例一：简单文件处理
```
workflow:
  name:
    file-process
  trigger:
    type:
      event
    topic:
      file.created
  inputs:
    fileId:
      type:
        string
  steps:
    - id:
        parse
      name:
        文件解析
      action:
        file.parser
      input:
        id:
          ${input.fileId}
    - id:
        thumbnail
      depends:
        - parse
      action:
        image.thumbnail
    - id:
        save
      depends:
        - thumbnail
      action:
        file.save
```
⸻

18. 完整示例二：复杂AI流程

你的例子：

PDF/Word解析 + OCR + AI摘要 + 标签 + 重试 + 异常

Cloud4 YAML：
```
workflow:
  name:
    file-ai-process
  inputs:
    file:
      type:
        object
  steps:
    - id:
        check_file
      action:
        file.check
    - id:
        parser
      switch:
        expression:
          ${file.type}
        cases:
          pdf:
            action:
              pdf.parser
          word:
            action:
              word.parser
        default:
          action:
            normal.parser
    - id:
        ai_tasks
      parallel:
        - id:
            ocr
          action:
            ai.ocr
        - id:
            summary
          action:
            ai.summary
        - id:
            tag
          action:
            ai.tag
    - id:
        save_metadata
      depends:
        - ai_tasks
      action:
        metadata.save
      retry:
        max:
          3
      onError:
        notify:
          admin
```
⸻

19. 与 Cloud4 DSL 的关系

Cloud4 DSL：

类似：
```
workflow FileAI {
 input file;
 step check {
 }
 if(file.type=="pdf"){
 }
 parallel{
 }
}
```
它表达：

* 变量
* 控制流
* 函数
* 模块

属于：

Workflow Programming Language

⸻

Cloud4 YAML：

表达：

* 节点
* 参数
* 依赖
* 配置

属于：

Workflow Configuration Language

⸻

20. 编译流程

最终：
```
Cloud4 YAML
       |
       |
YAML Parser
       |
YAML AST
       |
Semantic Analyzer
       |
Workflow AST
       |
IR Generator
       |
Workflow IR
       |
Runtime Engine
```
⸻

21. 为什么 YAML 不直接生成 IR？

因为：

YAML：

steps:
 - action:a
 - action:b

只知道：

这里有两个节点

不知道：

* 节点是什么语义
* action是否合法
* 输入是否匹配
* 类型是否正确
* 权限是否允许

所以需要：

Semantic Layer。

⸻

22. 最终 Cloud4 架构
```
                 User
        +----------------+
        | Cloud4 YAML    |
        +----------------+
                 |
             YAML AST
                 |
        +----------------+
        | Semantic AST   |
        +----------------+
                 ↑
        +----------------+
        | Cloud4 DSL     |
        +----------------+
                 |
               IR
                 |
        Workflow Engine
                 |
        Plugin Runtime
```
⸻
⸻

## 实现状态（YAML 前端落地回写，2026-08-20）

> 本节记录本设计在 `PrivateCloudDisk-cloudflow-runtime` 中的**实际落地实现**，与上文设计意图对照，
> 标注已实现、映射差异与延后项。落地遵循“强制需求清单”第 2 条：YAML 为第二前端语言，与 CloudFlow
> DSL 共存并统一编译到 `workflow.cloudflow.io/v1` IR；架构严格分层、表达式唯一收敛于子系统。

### 0. 五层架构 · GitHub Actions 对照矩阵 · 缺口清单（固化）

> 本节把 CloudFlow YAML 的定位**固化**为五个维度——**YAML Syntax + Workflow Schema +
> Expression System + Validation + Domain Semantics**（需求 5.31），并给出与 GitHub Actions
> 的**逐项对照矩阵**与**缺口清单**。它不是“纯 YAML + Domain AST 硬解析”，而是建立在这五个
> 分层之上、由独立子系统承载的成熟前端。交叉参考：`docs/CLOUDFLOW_YAML_GITHUB_ACTIONS_ALIGN.md`
> （对齐表）、`docs/CLOUDFLOW_EXPRESSION.md` §4.1（表达式对齐矩阵）。

#### 0.1 五个维度（分层职责与落地）

| 维度 | 含义 | 代码落点 | 状态 |
| --- | --- | --- | --- |
| **YAML Syntax** | 底层词法与文档结构解析；**不自研解析器** | `serde_yaml_ng 0.9`（libyaml）→ `src/yaml/model.rs`（`YamlDocument`）+ `src/yaml/locator.rs`（行/列近似定位） | ✅ 成熟第三方库；深度/节点数上限防 YAML 炸弹；UTF-8 |
| **Workflow Schema** | 形状/必填/类型/未知字段/非法值校验（**编译第一步**） | `src/yaml/schema.rs::validate_schema`；`emit_json_schema` → `schemas/yaml-workflow.schema.json` | ✅ `CFY-SCHEMA-1001..1004` 多错误收集 + 字段路径 + 行号 + 修复建议 |
| **Expression System** | 表达式词法（pest）/解析/AST/求值，**唯一收敛**、双前端共用 | `crates/cloudflow-engine-core/src/expression/`（`grammar.pest` + `parser.rs` + `eval.rs` + `builtins.rs`，独立 crate `cloudflow-engine-core`），`expr.cloudflow.io/v1` | ✅ 19 个白名单函数（含 4 个 GitHub 对齐）+ 3 管道 + `${{ }}` 插值 + 索引访问 |
| **Validation** | 领域语义校验（**DSL 与 YAML 共用**，IR 生成前强制） | `src/semantic.rs`（对统一 `WorkflowNode` 校验） | ✅ 变量/步骤唯一性/依赖环（拓扑）/能力 Hub 键/控制流/类型推断 |
| **Domain Semantics** | 统一领域 AST + IR + Runtime（前端无关） | `crates/cloudflow-engine-core/src/ast.rs`（`WorkflowNode`，engine-core）→ `crates/cloudflow-engine-core/src/ir.rs`（`workflow.cloudflow.io/v1`，engine-core）→ `crates/cloudflow-engine-core/src/runtime.rs` / `src/execution.rs`（宿主 crate） | ✅ 两条前端产出**等价 IR**（测试 `dsl_and_yaml_compile_to_equivalent_ir`） |

```
 .flow.yaml / .workflow.yaml / .yaml / .yml
        │  ① YAML Syntax：serde_yaml_ng（libyaml）→ YamlValue → YamlDocument
        ▼
  ② Workflow Schema：src/yaml/schema.rs（必填/类型/未知/非法值，CFY-SCHEMA-*）
        │  ③ Expression System：只切出 ${ ... } 字符串 → crates/cloudflow-engine-core/src/expression
        ▼
  ④→⑤ Domain Semantics：src/yaml/convert.rs → crate::ast::WorkflowNode（领域 AST）
        │  ④ Validation：src/semantic.rs（DSL/YAML 共用）
        ▼
     crates/cloudflow-engine-core/src/ir.rs → workflow.cloudflow.io/v1 → crates/cloudflow-engine-core/src/runtime.rs / src/execution.rs
```

> 关键点：**YAML 前端不定义任何表达式文法/AST/求值**，也不定义能力 Hub 键；这两块分别是
> ③（表达式子系统）与 ④（统一语义层）的唯一事实来源。DSL 与 YAML 只在 ①（各自词法）与
> ②（YAML 专属 Schema 形状）分叉，其余全共享——这正是“五个维度”的分层含义。

#### 0.2 GitHub Actions 对照矩阵（逐项，含 YAML 步骤/触发器/表达式上下文）

| GitHub Actions 概念 | CloudFlow YAML 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `.yaml` 工作流文件 | `.flow.yaml` / `.workflow.yaml` / `.yaml` / `.yml` | ✅ | `language_of` 按扩展名识别 |
| `jobs.<id>.steps[]` | 顶层 `steps[]` | ✅ | 单步序列 + `depends` 表达 DAG |
| `uses: actions/checkout@v4`（内置） | `action: builtin:<service>.<method>` | ✅ | 亦支持 `builtin.`/裸 `service.method` |
| `uses: owner/repo@v1`（第三方 action） | `action: "plugin:<plugin_id>:<function>@<version>"` | ✅ | 与 DSL `action plugin {}` 同一 `ActionNode`/同一能力 Hub 键 |
| `with:` 参数 | `with:` / `input:` | ✅ | 键 → `action.arguments` |
| `if:` 条件 | `when:` / `condition:` / `if:` | ✅ | 值统一 `${{ ... }}` |
| `needs:` | `depends:` / `depends_on:` | ✅ | 字符串或数组 |
| `id:` + `${{ steps.id.outputs.x }}` | `output:` + `${{ steps.<id>.output[.field] }}` | ✅ | 语义分析 + 运行期索引求值 |
| 工作流级 `env:` | 工作流级 `env:` | ✅ | 步骤级 `env:` 不支持（用工作流级） |
| `timeout-minutes:` | `runtime.timeout` / 步骤 `timeout:` | ✅ | 顶层 `runtime: {timeout, max_parallel, retry}` |
| `concurrency:` | `runtime.max_parallel` | ✅ | 直接对应 |
| 步骤失败重试 | `retry: { count\|max_attempts, strategy }` | ✅ | `fixed`/`linear`/`exponential` |
| `continue-on-error` | 步骤 `on_error` / 顶层 `catch`/`finally` | ✅ | 步骤级 `on_error` + 顶层 `catch` 通配/类型匹配 |
| 表达式 `${{ }}` | `${{ }}`（**唯一**分隔符） | ✅ | 不再定义 `${ }`；与 GitHub 同一双大括号 |
| 表达式函数（7 个命名） | `contains/starts_with/ends_with/format_number/format_date_time/from_json/to_json` | ✅ | 同名对齐；另加 12 个扩展，合计 19（`CLOUDFLOW_EXPRESSION.md` §4.1） |
| 表达式运算符 | 比较/逻辑/三元 + **额外算术 `+ - * / %`** | ✅ | GitHub 表达式无算术，CloudFlow 超集 |
| 上下文 `github.*` | `workflow.*` | ◐ | `workflow.failed`/`failedStep` 等 |
| 上下文 `vars`/`secrets`/`env` | `vars.`（变量/输入）/ `env.` | ◐ | 无 `secrets` 上下文（敏感值走 `env`/plugin runtime） |
| 上下文 `inputs`/`needs` | `input.` / `steps.<id>.output` | ✅ | 输入即 `input.`，步骤输出即 `steps.` |
| 上下文 `strategy`/`matrix`/`job`/`runner` | 无直接等价 | ⛔ | 用 `switch`/`parallel`/`foreach` 与局部变量表达 |
| `on:` 多事件路由 | 单一 `trigger`（schedule/event/http/webhook/interval/manual） | ◐ | 每工作流单一 trigger；多事件拆分多工作流 |
| `permissions:` | 权限声明（DSL `permissions`） | ◐ | 工作流级；步骤级不支持 |
| `run:`（直接命令） | 无 | ⛔ | 面向能力编排；代码执行封装为 plugin 能力 |
| `strategy.matrix` | 无直接等价 | ◐ | 用 `switch`/`parallel`/`foreach` 表达 |
| reusable/composite workflows | 子工作流 `workflow:` 节点 + DSL `include` | ◐ | 子工作流引用（provider=workflow）；跨文件 include 见缺口 |

#### 0.3 缺口清单（如实标注）

**已完整对齐**：步骤模型（action/with/if/needs/id/output/timeout/retry/on_error）、`${{ }}`
表达式与 7 个 GitHub 命名函数（同名对齐，且为超集）、能力 Hub 插件引用、触发器五型、顶层
`catch/finally`、`runtime`（timeout/max_parallel/retry）、工作流级 `env`。

**部分对齐（形态不同，需替代表达）**：
- `strategy.matrix` → `switch` / `parallel` / `foreach`
- 步骤级 `env:`/`permissions:`/`concurrency:` → 工作流级 `env` / 权限声明 / `runtime.max_parallel`
- `strategy/matrix/job/runner` 上下文 → `switch`/`parallel`/`foreach` + 局部变量
- reusable/composite workflows → 子工作流 `workflow:` 节点 +（跨文件）DSL `include`

**明确不支持（GitHub 特有，CloudFlow 有意不实现，附替代）**：
- `run:` 直接命令 → 封装为 plugin 能力（能力 Hub）
- 多事件 `on:` 工作级路由 → 拆分为多个单 trigger 工作流
- `secrets` 上下文 → 经 `env`（运行时注入）或 plugin runtime，不入库不编译进 IR
- 市场 `uses: owner/repo@v1` → `plugin:<id>:<fn>@<v>`（能力中心注册键）

**未实现（后续迭代）**：`include` 跨文件展开（`--emit-ast-all`）、权限/资源/审计高级注解、
前端 IDE YAML 编辑体验（需求 33.x，JSON Schema 已就绪）、`--convert-to yaml|dsl` 互转、
表达式成员方法调用 `string.toUpperCase()`（需求 6.12 可选；已由 `to_upper`/`to_lower`/`trim` 等价提供）。

> 结论：CloudFlow YAML 是“五个维度”完整落地的独立前端子系统，对 GitHub Actions 的**步骤/
> 表达式/触发/能力**层面达到对齐并局部超集；对 GitHub 特有的 **代码执行（`run:`）、矩阵/多事件
> 路由、`secrets`/`runner` 上下文**不做等价（有明确替代），并非“纯 YAML 包装”。

### 0.4 解析资源护栏（19.9/19.10/19.25，2026-08-21 落地）

`parse_yaml_detailed`（`src/yaml/convert.rs`）是 YAML 编译的唯一入口，按序执行三道护栏（均为纯函数，19.27）：

| 护栏 | 上限 | 错误码 | 拦截目标 |
|---|---|---|---|
| 源码字节数 | `MAX_YAML_SOURCE_BYTES` = 1 MiB | CFY-SCHEMA-1005 | 超大文件不进入 libyaml；与 HTTP 编译请求体上限（`MAX_COMPILE_BODY_BYTES`）一致 |
| 嵌套深度 | `MAX_YAML_DEPTH` = 100 层 | CFY-SCHEMA-1006 | 嵌套罪形；与 libyaml 内置递归限制（约 121 层）双保障 |
| 节点总数 | `MAX_YAML_NODES` = 100,000 | CFY-SCHEMA-1007 | 锦点/别名在事件期展开为完整值树，以总节点数约束别名放大（billion-laughs 变体） |

较对方：在实验中 libyaml 自身约 121 层报错（CFY-1001），CloudFlow 防线把可控上限改到 100 层，
并提供中文诊断与修复建议；测试要求见 `tests/cloudflow_security_bounds.rs`（超大源码 / 120 层 / 300 层 /
别名爆炸 / 合法小文档不误伤）。

表达式侧的对应防线（与 YAML 同管道，但位于表达式子系统）：长度 ≤ 16K 字符
（`CFY-EXPR-103`）、嵌套 ≤ 512（`CFY-EXPR-104`），见 `CLOUDFLOW_EXPRESSION.md`。

### 1. 落地架构（与设计对照）

```
.flow.yaml / .workflow.yaml / .yaml / .yml（或 --lang yaml 内联/stdin）
        │
        ▼
YAML Parser（serde_yaml_ng 0.9，成熟第三方库，禁止自研，需求 7.x）
        │  加固：深度/节点数上限（防 YAML 炸弹）、重复键检测、UTF-8
        ▼
YamlDocument（YamlWorkflow 强类型结构体，serde derive，需求 8.3/8.4）
        │  Locator：按文档序标量文本近似回填行号/列（需求 7.7 取舍：serde_yaml_ng
        │  反序列化后不保留逐值位置，故用 Locator 近似定位，见 src/yaml/locator.rs）
        ▼
YAML Compiler（src/yaml/convert.rs）
        │  normalize_document：兼容「嵌套 workflow: {...}」「平铺 trigger:/input:/steps:」两种本地形态
        │  表达式：只切出 `${{ ... }}` 字符串 → crate::expression（parse_expression_string / parse_value_string / parse_interpolated_value）
        │  （表达式能力全部由子系统承担：null 字面量、索引访问、19 个内建函数求值（含 4 个 GitHub 对齐），需求 6.x）
        │  引用规整：input.x→vars.x、steps.<id>.f→steps.<id>.output.f（与 DSL 输入表示一致 ⇒ IR 等价）
        ▼
Workflow Domain AST  ← 复用 crate::ast（WorkflowNode 等，与 DSL 前端共享，需求 2.x/9.x）
        │
        ▼
统一语义分析（crate::semantic，validate_reference 已放开 env./input. 命名空间）
        ▼
IR（workflow.cloudflow.io/v1，crate::ir，spec.outputs 由 workflow.outputs 回填）
```

要点（需求 2.1–2.12）：Source → 语言特定 Parser → 语言特定 AST（YAML 侧即
`YamlDocument`+`YamlWorkflow`）→ **统一 Workflow Domain AST** → 统一语义 → 统一 IR → Runtime。
DSL 与 YAML 共用同一领域 AST、语义与 IR 生成路径，Runtime 不感知前端语言。

### 2. 代码落点（新增模块）

| 文件 | 职责 |
| --- | --- |
| `src/yaml/mod.rs` | 仅 `pub use convert::{parse_yaml, parse_yaml_detailed}`（YAML→领域 AST）；`model`/`locator`/`convert`/`schema` 均为私有子模块 |
| `src/yaml/model.rs` | `YamlWorkflow` 等强类型 YAML 结构体（serde derive 反序列化） |
| `src/yaml/schema.rs` | **Workflow Schema 校验层（需求 5.22/31.x）**：编译第一步形状校验（必填/类型/未知字段/非法值），一次收集 `CFY-SCHEMA-1001..1004` 多条错误，字段路径+行号+建议；`emit_json_schema` 生成 JSON Schema（crate 根层 `emit_yaml_json_schema` 对外） |
| `src/yaml/locator.rs` | 文档序标量文本近似回填行/列，产出 CFY-1001/1002 诊断（原 CFY-1003 由 Schema 层 `CFY-SCHEMA-1003` 取代） |
| `src/yaml/convert.rs` | YAML→Domain AST：`parse_yaml`/`parse_yaml_detailed`/`normalize_document`/`normalize_refs`/`parse_duration_ms`；形状诊断收敛于 Schema 层，此处只留语义/转换诊断 |
| `src/lib.rs` | 前端调度器：`Language` / `language_of` / `parse_frontend_detailed` + `compile_source_named_for_language(source, filename, Language, catalog)`、`parse_ast_for_language` |
| `src/bin/cloudflowc.rs` | `--lang dsl|yaml`；`-A/--emit-ast`（别名 `--emit-domain-ast`）；`--output-format text|json` |
| `src/http.rs` | `CompileRequest.language: Option<String>`（"dsl"|"yaml"）路由 |
| `crates/cloudflow-engine-core/src/ast.rs` | `WorkflowNode.outputs: BTreeMap<String, ValueNode>`（YAML `outputs:` 使用；DSL 留空） |
| `src/compiler.rs` | 将 `workflow.outputs` 写入 IR `spec.outputs` |
| `src/semantic.rs` | `validate_reference` 允许 `env.`/`input.` 命名空间 |

### 3. CLI 用法（需求 13.x）

```bash
cloudflowc compile examples/yaml/simple_file_process.flow.yaml            # 按扩展名自动识别 YAML
cloudflowc compile x.workflow.yaml --emit-ast --no-color                  # AST 树形文本（不生成 IR）
cloudflowc compile x.flow.yaml --emit-ast --output-format json           # AST JSON 序列化
cloudflowc compile --lang yaml -i 'trigger: {type: manual}\nsteps: []'    # 内联源码显式指定语言
cat x.flow.yaml | cloudflowc compile --lang yaml                          # stdin 读取
cloudflowc compile x.flow.yaml --check-only                               # 仅校验（优先于 --emit-ast）
cloudflowc compile x.flow.yaml -o /tmp/out.json                           # 输出 IR 到文件
```

`--lang` 显式指定前端语言，覆盖扩展名自动识别（需求 13.4）；`-i`/stdin 需配合 `--lang`（13.5/13.12）。

### 4. 支持的 YAML 形态（对照设计 5.x/28.x）

- **顶层字段**：`workflow {name, version, description, display_name}`、`trigger`、`input`/`inputs`、
  `variables`、`env`、`steps`、`outputs`、`catch`/`finally`、`runtime`（含 `timeout`/`max_parallel`/
  `retry`）。仅扁平与嵌套两种**本地**形态；不接受 `apiVersion`/`kind`/`metadata`/`spec` 旧版包装。
- **Step 字段**：`id`、`name`/`display_name`、`action`（`service.method` / `provider.service.method` /
  冒号 `builtin:` `api:` `plugin:<id>:<fn>@<version>`）、`input`/`with`、`depends`/`depends_on`、
  `when`/`condition`/`if`、`retry`（`count`/`max_attempts` + `strategy`）、`timeout`、`on_error`、
  `foreach`、`parallel`、`switch`、`approval`、`output`。不接受旧版 `uses`/`needs`/`result` 别名与
  驼峰 `maxAttempts`/`backoff`。
- **触发器（需求 22.x）**：`schedule`（cron）、`event`、`http`/`webhook`、`manual`、`interval`。
- **控制流（映射到真实 FlowNode，需求 8.x）**：`switch/cases/default`、`foreach`、`parallel`、
  `approval`→`approval.request` action、顶层 `catch/finally`→`TryCatch`。
- 表达式字段统一写 `${{ ... }}` 字符串，由表达式子系统解析（需求 28.56–58/6.32）；CloudFlow YAML
  只定义 `${{ }}` 一种分隔符（对标 GitHub Actions），不再定义 `${ }`，避免重复语法。
- **Schema 形状校验（0.1.7，需求 31.x）**：必填（`steps`、step 的 `id`/`action`、`catch` 的 `action`、
  `parallel.tasks`、`foreach.do`）、类型、未知字段（含“是否想使用 X 而不是 Y？”建议）、非法值
  （`retry.count` 负数、`strategy`/`trigger.type`/`action.provider` 白名单、`timeout` 时长格式）。
  一次收集多条错误，每条携带字段路径（`steps[2].retry.count`）与行号；JSON Schema 由
  `emit_yaml_json_schema`（`schemas/yaml-workflow.schema.json`）一键生成，模板见
  `examples/yaml/template.flow.yaml`。`convert.rs` 不再重复报形状错误（避免重复诊断）。

### 5. 错误码（需求 14.x，前缀 CFY 区别于 DSL 的 CFwww）

| 错误码 | 阶段 | 说明 |
| --- | --- | --- |
| `CFY-1001` | YAML 解析 | serde_yaml_ng/libyaml 解析失败（含行/列，不经过 Schema 层） |
| `CFY-1002` | 转换/语义 | 转换期结构/语义错误（如输入/变量重复声明）；形状错误已移交 `CFY-SCHEMA-*` |
| `CFY-1003` | （已移除） | 原“未知/非法字段”由 `CFY-SCHEMA-1003 UNKNOWN_FIELD` / `CFY-SCHEMA-1004 INVALID_VALUE` 取代 |
| `CFY-SCHEMA-1001` | Schema | 必填字段缺失（`steps`、step `id`/`action`、`catch.action`、`parallel.tasks`、`foreach.do`） |
| `CFY-SCHEMA-1002` | Schema | 字段类型不匹配（如 `retry.count` 应为数字而非字符串） |
| `CFY-SCHEMA-1003` | Schema | 未知字段（含 31.22 “是否想使用 X 而不是 Y？”建议） |
| `CFY-SCHEMA-1004` | Schema | 非法值（`retry.count` 负数、`strategy`/`trigger.type`/`action.provider` 白名单外、`timeout` 时长格式错误） |

诊断结构与 DSL 共用同一结构（code/message/location/labels/help），同时带 `yaml` 语言标识与文档链接。

### 6. 测试与回归（需求 15.x/29.x）

- `tests/cloudflow_yaml.rs`（21 项，含 `examples/yaml/invalid/` 反例集）：语言识别、trigger 映射、引用规整、retry/timeout/on_error、
  parallel/foreach/switch/approval、catch/finally、CFY 错误码、DSL↔YAML IR 等价、全示例编译、
  `--emit-ast`（YAML 语言 API）、YAML `null`→`ValueNode::Null`、插件冒号动作与 DSL 能力 Hub 键一致、
  `${{ }}` 表达式与插值、**Schema 校验层**（多错误收集 + 字段路径 + 行号、缺字段/类型/未知字段/
  非法值、未知字段修复建议、JSON Schema 与生成器一致性、compile 拦截）。
- `examples/yaml/`（14 个 `.flow.yaml`，含模板 `template.flow.yaml`，见 `CLOUDFLOW_YAML_DEMO_DESIGN.md`
  映射表）：全部通过 YAML→Schema→Domain AST→语义→IR 全流程编译；`weekly_sales_report.flow.yaml`
  与 DSL 同义文件 `examples/weekly_sales_report.flow` 语义等价。
- `schemas/yaml-workflow.schema.json`：JSON Schema（draft-07），由 `emit_yaml_json_schema` 统一生成；
  生成命令 `UPDATE_YAML_SCHEMA=1 cargo test --test cloudflow_yaml yaml_json_schema_regenerate_with_env`。
- 回归：`cargo test --locked --all-features` **126 项**全绿；`cargo fmt --check` 通过；
  `src/yaml/*` 模块 `cargo clippy` 无警告（预留 `policies` 字段按惯例 `allow(dead_code)`）；
  DSL 核心既有结构告警（`ast.rs` 大枚举、`semantic.rs` 参数数等）保持不改；原 DSL 示例/IR/AST/
  错误行为不变（需求 4/18.x）。

### 7. 设计差异与延后项（如实标注）

- **位置信息取舍**（需求 7.6/7.7）：serde_yaml_ng 反序列化后不保留逐值位置；`Locator` 按文档序
  标量文本近似回填行/列，可满足诊断定位，但非 pest 级精确 span。
- **表达式**：YAML 只切字符串，全量交给表达式子系统（需求 6.31/28.56），未重复定义文法/AST；
  子系统已补齐 `null`、索引访问运行期求值、19 个内建函数（含 4 个 GitHub 对齐）求值器（`eval.rs::call_builtin`）。
- **未落地（后续迭代）**：`include` 跨文件展开（5.11 建议：默认仅入口 AST，`--emit-ast-all` 未实现）、
  权限/资源/审计高级注解、前端 IDE YAML 编辑体验（33.x，JSON Schema 已就绪，`schemas/` 供 Monaco
  集成）、`--convert-to yaml|dsl` 互转（13.21）、`--emit-ast-semantic`（5.20）。
- **Schema 位置信息取舍**：必填/未知字段等容器级错误用“容器首个键”近似定位（`Locator` 文档序扫描，
  非 pest 级精确 span）；逐值错误（类型/非法值）定位到具体标量行。
- **GitHub-Actions 对标已落地（需求 28.x/6.32）**：`${{ }}` 统一表达式/插值（`whole_expression_index` /
  `parse_interpolated_value` 只匹配 `${{`）、`builtin:`/`api:`/`plugin:<id>:<fn>@<version>` 冒号动作
  （`convert_action` / `split_action_version`，能力 Hub 键一致）。CloudFlow YAML **不**做旧版
  `automation.pcd/v1` 包装与 `uses/needs/result`、驼峰 `maxAttempts`/`backoff` 兼容，相关示例已
  一次性转化为新版 `examples/yaml/weekly_sales_report.flow.yaml`（`convert_step` 只认本地字段）。
- **明确不对标（GitHub Actions 无法等价项）**：`run:` 直接命令（代码执行收敛于 plugin runtime）、
  `strategy.matrix`（用 `switch`/`parallel`/`foreach` 表达）、步骤级 `env:`/`permissions:`/
  `concurrency:`（统一用工作流级 `env`/`runtime`/权限声明）、工作级多事件 `on:`（每工作流单一 trigger）。
- 设计 9.x：DSL 前端编译器仍直接产出领域 AST（保留既有 `compile_source_named` 路径），经统一
  语义与 IR 生成；`--emit-domain-ast` 作为 `--emit-ast` 的别名保留，语义同为“解析后输出领域 AST”。
