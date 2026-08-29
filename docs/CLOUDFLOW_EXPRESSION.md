# CloudFlow 表达式子系统（expr.cloudflow.io/v1）

- 状态：**已实现并落地**（位于独立 crate `crates/cloudflow-engine-core/src/expression/`，
  宿主 crate 根层再导出为 `crate::expression`；V0.1.5 起含
  `eval.rs` 求值器、null 字面量、索引访问运行期求值与 19 个内建函数（含 4 个 GitHub Actions 对齐函数），见 CHANGELOG 0.1.5/0.1.8）。
  该 crate 同时承载统一执行引擎（`engine/`、`dev_exec`、`execution_core`、`ir`、`ir_validate`、
  `runtime`）——表达式子系统是双执行面与全部前端语言共用的唯一表达式实现（需求 6.1/6.31）。
- 作用域：所有前端语言（CloudFlow DSL / YAML（已落地）/ 未来 JSON / Visual Editor）**唯一的**表达式
  词法、解析、AST 构建、内建函数与常量实现方；不允许多个前端各自实现表达式（需求 6.1/6.31）。

## 1. 为什么要独立成子系统

CloudFlow 未来拥有多个前端语言：CloudFlow DSL 与 YAML。二者在 `if`/`foreach`/`switch` 条件、
`depends_on if`、`validate`、action 参数等处都写表达式。如果表达式解析、表达式 AST 与求值逻辑
散落在 DSL 目录中，YAML 前端就无法共享，架构会退化。因此表达式被抽成**独立的项目内子系统**：

```plaintext
cloudflow-expression            ← 本子系统（唯一实现；crate: cloudflow-engine-core）
    ↓ 表达式语言词法 grammar.pest（pest 第三方库，禁止自制正则）
    ↓ 表达式 AST   （crate::ast::ExpressionNode —— 领域 AST，不另行定义）
    ↓ Parser       （crates/cloudflow-engine-core/src/expression/parser.rs）
    ↓ Type Checker （语义层共用，见 6.28）
    ↓ Evaluator    （crates/cloudflow-engine-core/src/expression/eval.rs：call_builtin 集中实现，执行端委托，见 6.18/6.31）
    ↓ Built-ins    （crates/cloudflow-engine-core/src/expression/builtins.rs：KB/MB/GB 常量 + 19 个函数白名单，含 GitHub 对齐 4 个）

cloudflow-dsl   ← 前端：只把表达式**字符串**交给表达式子系统（6.21/6.31）
cloudflow-yaml  ← 前端（**已落地**，2026-08-20，`src/yaml/`）：同样只传字符串，表达式能力直接复用本子系统
```

`cloudflow-template`（字符串模板 `${...}`）的目标设计与表达式子系统共用同一套表达式语法与 AST，
见 6.14/6.32（对标 GitHub Actions `${{ }}` 表达式系统）。

## 2. 前端只传字符串（委托契约）

DSL / YAML Parser **不理解不构建**表达式语义：它们只负责把

```plaintext
condition: "${file.size > 100 * MB && file.type == 'pdf'}"
```

中的表达式字符串 `file.size > 100 * MB && file.type == 'pdf'` 切出来，交 `Expression Parser`：

```plaintext
BinaryExpression
├── BinaryExpression
│   ├── PropertyAccess(file.size)
│   ├── >
│   └── BinaryExpression
│       ├── 100
│       ├── *
│       └── MB
├── &&
└── BinaryExpression
    ├── PropertyAccess(file.type)
    ├── ==
    └── "pdf"
```

解析结果（`ExpressionNode`）再**注入** Workflow Domain AST（`crate::ast`）——表达式 AST 与领域
AST 对应（需求 6.17），不重复定义一套表达式 AST 类型。为此本子系统的 `grammar.pest` 就是表达式
词法的**唯一事实来源**。

> **pest 约束说明**：pest 不支持跨文件 `include` 语法。CloudFlow DSL 的 `src/grammar.pest` 仍保留
> 一批表达式/值规则，**仅作为定位器**用于在 DSL 上下文中精确切出表达式字符串（例如区分
> `if { expr } { body }` 里外层大括号与表达式内对象字面量）。这些规则不参与任何语义构建：
> `parser.rs` 拿到字符串后一律委托给 `crate::expression`。
>
> **两处 grammar 必须逐字同步（重要）**：表达式语法扩展只在子系统 `grammar.pest` 修改（唯一事实
> 来源），随后必须把 `primary`/`reference`/新增规则原样同步到 DSL `src/grammar.pest`（含顺序）；
> 校验用 `tests/cloudflow_expression.rs` 的 `dsl_sync_*` 用例，或直接 diff 两份文件。新增引用
> 命名空间/函数时，除 grammar 外还要同步 `builtins.rs`、`syntax-highlight/generator/config.py`
> （`REFERENCE_PREFIXES` / `COMPLETION_REF_PREFIXES` / `BUILTIN_FUNCTIONS`）并重新生成
> `syntax-highlight/build/`。

## 3. 代码与职责

| 文件 | 职责 |
| --- | --- |
| `crates/cloudflow-engine-core/src/expression/grammar.pest` | Peg 词法，`expr_entry`/`value_entry` 两个入口（含 SOI/EOI 完整消费检查） |
| `crates/cloudflow-engine-core/src/expression/parser.rs` | pest 解析 + 表达式/值 → Domain AST 构建（从 DSL `parser.rs` 完整抽取，只增不减） |
| `crates/cloudflow-engine-core/src/expression/builtins.rs` | `KB/MB/GB` 常量与内建函数白名单（**已实现** 19 个：`size/len/contains/starts_with/ends_with/now/get/trim/to_upper/to_lower/range/abs/round/floor/ceil` + GitHub 对齐 `to_json/from_json/format_number/format_date_time`；管道 `filter/map/reduce`；可扩展，唯一事实来源） |
| `crates/cloudflow-engine-core/src/expression/eval.rs` | **求值器集中实现**（`call_builtin`，需求 6.18/6.22/6.25/6.27）+ `API_VERSION = "expr.cloudflow.io/v1"`（需求 6.29）；生产执行端 `execution.rs::call` 只委托不改写 |
| `crates/cloudflow-engine-core/src/expression/mod.rs` | 子系统对外 API：`parse_expression_string` / `parse_value_string` /
  `parse_interpolated_value`（${{ }} 插值，6.14/6.32）/ `value_from_expression` / `builtins` /
  `call_builtin` / `API_VERSION` |

公开 API：

```rust
pub fn parse_expression_string(text: &str, source: &str, filename: &str, base: usize)
    -> Result<ExpressionNode, Box<Diagnostic>>;   // 表达式 → ExpressionNode（span 对齐源码绝对坐标）
pub fn parse_value_string(text: &str, source: &str, filename: &str, base: usize)
    -> Result<ValueNode, Box<Diagnostic>>;        // 值上下文（字符串/数字/数组/对象/调用/枚举）
pub fn parse_interpolated_value(text: &str, source: &str, filename: &str, base: usize)
    -> Option<ValueNode>;                         // ${{ }} 插值 → ValueNode::Template（6.14/6.32）
pub fn value_from_expression(expr: ExpressionNode) -> ValueNode;
pub mod builtins;                                  // constant() / is_builtin_function()
pub fn call_builtin(function: &str, args: &[Value]) -> Result<Value, String>;  // 内建函数求值入口
pub const API_VERSION: &str = "expr.cloudflow.io/v1";                          // 需求 6.29

// —— 资源防线与缓存（2026-08-21 落地，需求 19.3/19.16）——
pub const MAX_EXPRESSION_CHARS: usize;      // 16 384，超长报 CFY-EXPR-103
pub const MAX_EXPRESSION_NESTING: usize;    // 512，超嵌套报 CFY-EXPR-104
pub fn expression_cache_stats() -> (usize, usize);  // (缓存项数, 容量)
pub fn value_cache_stats() -> (usize, usize);
pub fn clear_parse_caches();                // 清空两个解析缓存（测试隔离/内存调整）
```

错误码 `CFY-EXPR-102`（`EXPRESSION_ERROR`），携带行号、列号与修复建议；span 以 `base` 对齐到
前端源码绝对坐标，多字节安全。

### 3.1 解析缓存与资源防线（19.3/19.11/19.16，2026-08-21 落地）

- **解析缓存（19.3）**：`parse_expression_string` / `parse_value_string` 各维护一个全局
  `Mutex<HashMap<String, Arc<Node>>>`（容量各 1024，超容整体清空，粗粒度策略避免额外依赖）。
  缓存的是 **rebase 前** 的相对坐标解析结果：pest 词法与节点构造（重量级）只执行一次，
  轻量级的 span 对齐每次执行，结果与无缓存逐节点等价（19.27 纯函数义务；
  缓存仅影响性能，不改变可观测结果）。正确性见
  `tests/cloudflow_security_bounds.rs::expression_parse_cache_is_correct_across_sources`（不同
  源码基准基准下 span 各自对齐）。
- **长度防线（19.16，`CFY-EXPR-103`）**：表达式文本超过 16K 字符先于 pest 报错。
- **嵌套防线（19.16，`CFY-EXPR-104`）**：PEG 递归解析本身无嵌套限制，5000 层括号
  会直接栈溢出（已实测）。O(n) 预扫（跳过字符串）约束两类递归结构：
  平衡括号最大深度与三元符 `?` 总数，上限均为 `MAX_EXPRESSION_NESTING` = 512
  （512 层 pest 递归堆消耗约 1MB，线程堆上保留边际）。
- **求值沙箱（19.11/19.12）**：`call_builtin` 只允许白名单纯函数，未登记函数分支一律报错；
  `system/exec/drop_table/spawn/fetch` 等均不可调用（边界测试覆盖）。
- **线程安全（19.14）**：缓存为全局 `Mutex`；锁守卫严格限在单个暂时对象内释放
  （get 与 put 分开两次加锁，不可重入——此前曾因守卫跨 match 活存造成自锁死锁，
  已修复并由全套编译测试回归保护）。

## 4. 表达式语法（与 DSL 表达式语法统一，可扩展）

- 字面量：数字（保留 `serde_json::Number`，杜绝文本降级）、字符串、布尔、`null`、时长、数组、对象。
- 引用：`vars.<name>`、`steps.<id>.output[.field]`、`input.<name>`、`env.<key>`、`workflow.<name>`；
  控制流局部引用（`foreach item`、`catch error`、`for i`）与通用属性/索引路径（如 `file.size`、
  `list[0]`，需求 6.5/6.6；索引访问**已实现**：语义层按基名校验、执行端 `execution.rs::deref_path`
  运行期求值，支持 `vars.files[1].name` / `steps.parse.output[2]`）。
- 运算符：算术 `+ - * / %`、比较 `> < >= <= == !=`、逻辑 `&& || !`、三元 `?:`（6.7–6.10）。
- 调用：白名单函数 19 个（**已实现**，6.11/6.22/6.27/6.32）—— `size/len/contains/starts_with/ends_with/
  now/get/trim/to_upper/to_lower/range/abs/round/floor/ceil` + GitHub Actions 对齐
  `to_json/from_json/format_number/format_date_time`；求值实现在 `eval.rs::call_builtin`，
  新增函数必须先在 `eval.rs` 实现、再登记 `builtins.rs` 并同步消费方。
- 管道：`<expr> | filter(pred) | map(field) | reduce(func)`（6.13）。
- 字符串插值（YAML 前端）：`"file_${{ input.file_id }}.txt"` 解析为 `ValueNode::Template`（6.14）。
  **GitHub-Actions `${{ }}` 已落地**（6.32）：`whole_expression_index` / `parse_interpolated_value`
  只匹配 `${{`（CloudFlow YAML 唯一分隔符，不再接受 `${ }`）；简单引用折叠为 `VariableRef`、
  复杂表达式折叠为 `Expression`。DSL 前端插值仍用 `${...}`（`parse_value_string`，见第 2 节）。
- 常量：`KB`/`MB`/`GB` 在解析期折叠为数字字面量（6.22）。
- 成员方法调用 `string.toUpperCase()`（6.12，可选）：**未实现**，等价能力由 `to_upper`/`to_lower`/
  `trim` 函数提供；如需对齐 GitHub Expressions 再行只增扩展。

## 4.1 GitHub Actions Expressions 对齐（需求 6.32）

CloudFlow 表达式子系统对标 GitHub Actions Expressions。下表中 **✅ 已对齐**（同名/等价实现，
求值收敛于 `eval.rs::call_builtin`），**◐ 语义等价但形态不同**，**⛔ 明确不支持**：

| GitHub Actions Expressions | CloudFlow 表达式 | 状态 | 说明 |
| --- | --- | --- | --- |
| 比较 `== != < > <= >=`、逻辑 `&& \|\| !`、三元 `? :` | 完全相同 | ✅ | `inferred_expression_type` 推断返回类型 |
| 索引 `arr[0]` / `obj['k']` / 多维 | `list[0]` / `steps.x.output[i]` | ✅ | 语义按基名校验，执行端 `deref_path` 运行期求值 |
| 上下文/变量访问 `github.*`/`vars`/`env`/`needs` | `vars.`/`input.`/`env.`/`workflow.`/`steps.<id>.output` | ✅ | 命名空间白名单，语义层 `validate_reference` 校验 |
| `contains(value, item)` | `contains(value, item)` | ✅ | 字符串子串 / 数组元素 / 对象键 |
| `startsWith(string, prefix)` | `starts_with(text, prefix)` | ✅ | 命名用 snake_case |
| `endsWith(string, suffix)` | `ends_with(text, suffix)` | ✅ | 命名用 snake_case |
| `formatNumber(number, [format])` | `format_number(number, [format])` | ✅ | 支持小数位（`0.00`）与千分位（`#,##0.00`） |
| `formatDateTime(ts, [format], [tz])` | `format_date_time(value, [format], [timezone])` | ✅ | .NET token（`yyyy MM dd HH mm ss`）+ UTC 偏移/常见 IANA；缺省 RFC3339 |
| `fromJSON(string)` | `from_json(text)` | ✅ | 非字符串原样返回 |
| `toJSON(value)` | `to_json(value)` | ✅ | 值 → JSON 字符串 |
| 算术 `+ - * / %` | 完全相同 | ✅ | GitHub 表达式不含算术，CloudFlow 额外支持 |
| 集合管道 `\| filter/map/reduce` | 同名 | ◐ | GitHub 无内置管道；CloudFlow 作为扩展 |
| `now()` | `now()`（Unix 秒） | ✅ | 与 GitHub 对齐 |
| 字符串方法 `xxx.toUpperCase()` | `to_upper(x)`/`to_lower(x)`/`trim(x)` | ◐ | 6.12 成员方法调用未实现，函数等价 |
| `min/max`（无） | `abs/round/floor/ceil/range/get/len/size` | ✅ | CloudFlow 额外提供 |

> 说明：GitHub Actions Expressions 的**命名函数共 7 个**（contains/endsWith/formatDateTime/
> formatNumber/fromJSON/startsWith/toJSON），本子系统已全部同名对齐并额外提供 12 个扩展函数，
> 合计 **19 个**白名单函数 + 3 个管道操作符，为 GitHub 的严格超集。

## 5. 抽取与回放记录

- `parser.rs`：`parse_expression` / `parse_value` / `parse_call` / `parse_pipeline_op` /
  `parse_string_value` / `is_simple_reference` / `normalize_reference` / `literal_expression` /
  `value_from_expression` 等**完整**从 CloudFlow DSL `src/parser.rs` 抽取到 `crates/cloudflow-engine-core/src/expression/parser.rs`
  （只增不减；新增 `runtime_path` / `index_access` / `input_ref` / `env_ref` / `KB/MB/GB` 常量）。
- `grammar.pest`：表达式规则从 DSL `src/grammar.pest` **完整**抽取到 `crates/cloudflow-engine-core/src/expression/grammar.pest`
  （只增不减），在此基础上只增加扩展规则。
- `ast.rs`：**不抽取**。`ExpressionNode` 是 Workflow Domain AST 的一部分（IR 生成与编译共享），
  若复制会破坏唯一性（需求 6.17）。
- DSL 前端：`src/parser.rs` 已删除全部表达式/值构建代码，改为 `expr_node`/`value_node`/
  `value_node_text` 委托；`src/grammar.pest` 表达式规则降级为定位器并注明真源。

## 6. 测试

- `tests/cloudflow_expression.rs`：字面量、引用/属性/索引、运算符/三元、函数/管道、常量、
  值上下文与插值、错误定位、DSL 前端委托等价性、`dsl_sync_*` 双文件语法同步、null 字面量、
  扩展内建函数、GitHub-Actions 对齐函数（`to_json/from_json/format_number/format_date_time`）、API 版本、GitHub-Actions `${{ }}` 双大括号插值（现 **17 个用例**）。
- `src/execution.rs` 单元测试：索引访问运行期求值、扩展内建函数委托 `call_builtin`（2 项）。
- `tests/cloudflow_yaml.rs`：YAML `null`→`ValueNode::Null`、`${{ }}` 表达式、插件冒号动作与
  Schema 校验层（21 项，含 invalid/ 反例集，见 YAML 文档）。
- 既有套件回归：`cloudflow_coverage`（示例 `.flow` 全编译）、`cloudflow_compliance`、
  `cloudflow_v12_extension`、`cloudflow_contract`、`ast_visualization` 等全部保持通过，
  证明提取不改变 IR 输出、错误诊断与 AST 可视化行为。

## 7. 后续

- **语法/语义分层现状**：`file.size` / `items[0]` / `input.x` / `env.K` 等扩展在语法层已支持
  （`--emit-ast` 可见）。语义层 `validate_reference` **已接入**：按基名（`first_segment` 拆分
  `.`/`[`）校验 `vars.files[0]`、`steps.parse.output[1]` 与局部变量 `items[0]`，并放行
  `workflow.`/`env.`/`input.` 命名空间；仍要求裸对象路径（如 `file.size`）的基名先声明为变量/
  步骤/局部，否则给出明确 `CF2002`。
- YAML 前端**已落地**（`src/yaml/convert.rs`）：仅在 YAML Compiler 中切出 `${{ ... }}`
  并调用 `parse_expression_string`/`parse_value_string`/`parse_interpolated_value`，不重复实现任何
  表达式能力；「对标 GitHub Actions Expression System」轮廓（6.32）已落地：`${{ }}` 统一分隔符、
  插值、函数白名单、步骤输出/变量上下文（详见 `docs/CLOUDFLOW_YAML_GITHUB_ACTIONS_ALIGN.md` 第 2 节）。
- **求值器（Evaluator）已集中实现**（`eval.rs::call_builtin`，6.18/6.22/6.25/6.27），执行端
  `execution.rs::call` 委托之；类型检查仍由统一语义层接入（6.19/6.28）。`API_VERSION =
  "expr.cloudflow.io/v1"` 独立于前端语言版本演进（6.29）。
- 新语言（JSON、Python 前端）同样复用本子系统（6.30）。
