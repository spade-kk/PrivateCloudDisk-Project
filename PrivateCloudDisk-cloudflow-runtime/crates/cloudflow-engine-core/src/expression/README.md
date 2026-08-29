# CloudFlow 表达式子系统（`expr.cloudflow.io/v1`）

> 位置：`crates/cloudflow-engine-core/src/expression/`（独立 crate `cloudflow-engine-core`）
> 状态：**已实现并落地**。宿主 crate 根层以 `pub use cloudflow_engine_core::expression` 再导出，
> 既有 `crate::expression::*` 调用路径不变。

## 1. 定位（需求 6.1/6.31）

表达式子系统是 CloudFlow 多前端语言架构中**唯一的表达式实现方**：

- 所有前端语言（CloudFlow DSL、CloudFlow YAML，未来 JSON/Python）只把表达式**字符串**交给
  本子系统（如 YAML 的 `condition: "${{ file.size > 100 * MB && file.type == 'pdf' }}"` 只被
  切成字符串），由本子系统负责词法（pest）、解析、表达式 AST 构建、白名单函数与运行期求值。
- 前端 Parser 不重复定义任何表达式词法/文法/AST/求值代码（需求 6.21/6.31：YAML Parser /
  DSL Parser 只切字符串）。
- 表达式 AST 语法树由领域层 `crate::ast`（`ExpressionNode` / `ValueNode`）定义（需求 6.17：
  Expression AST 与 Domain AST 对应），本子系统**不复制** AST 定义。

对标 GitHub Actions Expressions（需求 6.32）：`${{ }}` 插值、`toJSON/fromJSON/
formatNumber/formatDateTime` 对齐函数。

```
YAML / DSL 前端 Parser
   │  只切出表达式字符串（不理解内容）
   ▼
expression 子系统（本目录）
   ├─ grammar.pest（pest 词法/文法，唯一事实来源）
   ├─ parser.rs（字符串 → Domain 表达式 AST，span 对齐源码）
   ├─ builtins.rs（白名单函数 + KB/MB/GB 常量 + 管道算子）
   ├─ eval.rs（call_builtin 集中实现 + API_VERSION）
   └─ evaluator.rs（IR 值上下文 $ref/$expr/$template/$pipeline 运行期求值）
   ▼
Domain AST（crate::ast）→ 语义分析 → Workflow IR → 双执行面
```

## 2. 文件职责

| 文件 | 职责 |
| --- | --- |
| `mod.rs` | 子系统对外 API：`parse_expression_string` / `parse_value_string` / `parse_interpolated_value` / `value_from_expression` / `call_builtin` / `API_VERSION`；公开 `builtins` 与 `evaluator` 子模块 |
| `grammar.pest` | **唯一事实来源**的 Peg 文法（pest 第三方库，不手工维护正则/tokenizer，需求 6.15）。表达式规则自 DSL `src/grammar.pest` 完整抽取（只增不减） |
| `parser.rs` | pest 解析 + 表达式/值 → Domain AST 构建；span 通过 `base` 偏移对齐源码绝对坐标；字符串插值拆分（`${{ }}`） |
| `builtins.rs` | 常量表（`KB/MB/GB`，解析期数字折叠）、19 个白名单函数表、管道算子表（`filter/map/reduce`）与查询 API（`constant` / `is_builtin_function` / `is_pipeline_operator`） |
| `eval.rs` | **内建函数求值唯一实现** `call_builtin`（需求 6.18/6.22/6.25/6.27）+ `API_VERSION = "expr.cloudflow.io/v1"`（版本独立于前端语言，需求 6.29） |
| `evaluator.rs` | IR 值上下文运行期求值：`evaluate_value`（`$ref`/`$expr`/`$template`/`$pipeline`，纯函数）、`truthy`、`parse_duration`、`matches_type`、`normalize_variables`；统一调度驱动与双执行面共用，不得重复实现 |

## 3. 公开 API

```rust
// 编译期（前端共用，需求 6.15/6.16/6.21）
parse_expression_string(text, source, filename, base) -> Result<ExpressionNode, Box<Diagnostic>>
parse_value_string(text, source, filename, base)      -> Result<ValueNode, Box<Diagnostic>>
parse_interpolated_value(text, source, filename, base) -> Option<ValueNode> // YAML `${{ }}` 插值
value_from_expression(expression) -> ValueNode

// 运行期（双执行面共用）
evaluator::evaluate_value(&Value, &Value) -> Result<Value, ExpressionEvalError>
evaluator::{truthy, parse_duration, matches_type, normalize_variables}

// 内建函数（白名单，唯一实现）
call_builtin(function: &str, arguments: &[Value]) -> Result<Value, String>
API_VERSION: &str // "expr.cloudflow.io/v1"

// 白名单查询
builtins::{CONSTANTS, BUILTIN_FUNCTIONS, PIPELINE_OPERATORS,
           constant, is_builtin_function, is_pipeline_operator}
```

## 4. 语法与优先级

入口两个（均含 SOI/EOI 完整消费检查）：`expr_entry`（表达式）、`value_entry`（值）。

优先级（低 → 高）：

| 级别 | 语法 | 示例 |
| --- | --- | --- |
| 管道 | `expr \| filter(pred) \| map(field) \| reduce(func)`（需求 6.13） | `files \| filter(size > 0) \| map(name)` |
| 三元 | `c ? a : b`（需求 6.10） | `ok ? "yes" : "no"` |
| 逻辑 | `\|\|`、`&&`、一元 `!`（需求 6.9） | `a \|\| b && !c` |
| 相等 | `==`、`!=`（需求 6.8） | `x == 1` |
| 比较 | `>`、`<`、`>=`、`<=`（需求 6.8） | `size > 1` |
| 加减 | `+`、`-`（需求 6.7） | `a + b` |
| 乘除取余 | `*`、`/`、`%`（需求 6.7） | `100 * MB` |
| 一元 | `!`、`-` | `-1`、`!flag` |
| 原子 | 函数调用 / 索引访问 / 引用 / 属性路径 / 字面量 / `local_ref` / `(...)` | 见下 |

支持的语言特性（需求 6.3–6.14）：

- **字面量**：数字（JSON 兼容，保留 `serde_json::Number`）、字符串、`true`/`false`、
  `null`、三引号字符串、数组 `[...]`、对象 `{...}`、时长 `30s/5m/2h`（需求 6.3）；
- **引用**：`vars.x.y`、`steps.<id>.output[.field]`（含 `x.output` 旧式）、`input.x`、
  `env.K`、`workflow.<field>`（需求 6.4）；
- **属性访问**：`file.size`、`item.name`（通用运行时对象路径，需求 6.5）；
- **索引访问**：`list[0]`、`vars.a[0]`、`steps.parse.output[2]`（需求 6.6）；
- **函数调用**：`size(files)`、`get(arr, 0)`（白名单，需求 6.11）；
- **常量**：`KB/MB/GB`（解析期折叠为数字，需求 6.22）；
- **字符串插值**：YAML 唯一分隔符 `${{ ... }}`（`parse_interpolated_value`，对标 GitHub
  Actions，需求 6.14/6.32）；DSL `${...}` 由 `parse_value_string` 路径处理，两侧互不重复定义。

## 5. 内建函数白名单（19 个）+ 常量 + 管道

| 函数 | 说明 |
| --- | --- |
| `size` / `len` | 数组/对象/字符串长度 |
| `contains` | 容器包含判断 |
| `starts_with` / `ends_with` | 字符串前缀/后缀 |
| `now` | 当前 Unix 秒 |
| `get` | 数组/对象按索引/键取值 |
| `trim` / `to_upper` / `to_lower` | 字符串处理 |
| `range` | `range(stop)` / `range(start, stop, step)` → 数字数组 |
| `abs` / `round` / `floor` / `ceil` | 数值 |
| `to_json` / `from_json` | GitHub `toJSON` / `fromJSON` 对齐（需求 6.32） |
| `format_number` | GitHub `formatNumber` 对齐（小数位 + 千分位） |
| `format_date_time` | GitHub `formatDateTime` 对齐（.NET token + 时区） |

常量：`KB=1024`、`MB=1048576`、`GB=1073741824`。管道算子（非普通函数，单独分类）：
`filter` / `map` / `reduce`。

⚠️ `builtins.rs` 是白名单的**唯一事实来源**：宿主语义层校验（`src/semantic.rs`）与补全规范
（`syntax-highlight/generator/config.py` 的 `BUILTIN_FUNCTIONS`）必须与本表一致。

## 6. 运行期求值（IR 值上下文）

IR 中的表达式值以 `serde_json::Value` 标记对象表示，求值上下文为
`{"vars": {...}, "steps": {"<id>": {"output": ...}}}`（可含行上下文顶层键）：

| 形态 | 语义 |
| --- | --- |
| `$ref`（字符串） | 路径引用（`vars.x` / `steps.a.output.y`），缺失即报错 |
| `$expr`（对象） | `condition/whenTrue/whenFalse`（三元）或 `operator/left/right`（二元）或 `function/arguments`（函数调用） |
| `$template`（数组） | 模板段（文本段 + 表达式段）拼接为字符串 |
| `$pipeline` | `filter/map/reduce` 集合处理管道 |

`evaluate_value` 是纯函数：不访问文件系统/网络/全局状态；时间函数仅经白名单受控提供
（需求 6.27/34.6：表达式不执行任意代码）。求值错误为面向用户的诊断文本
（`ExpressionEvalError`），由执行面按自身错误码体系包装：生产 `CF2101` / 调试面 `CFD-8101`
（见 `docs/CLOUDFLOW_ERROR_DESIGN.md`）。

## 7. 安全设计（需求 6.27/34.5/34.14）

- 白名单之外的函数调用在语义层即被拒绝；运行期 `call_builtin` 对未登记函数直接报错；
- 表达式求值无任意代码执行通道（无动态求值、无宏、无反射调用）；
- 解析器为纯函数，不访问文件系统/网络（需求 34.28）；
- YAML 侧插值解析失败时占位符保留为文本段，不产生吞噬性错误（与 DSL 行为一致）。

## 8. 与 DSL 前端的双文件同步约束

pest 不支持跨文件 grammar include，因此宿主 crate `src/grammar.pest` **保留了一份表达式/值
规则作为“切分定位器”**（只切出字符串、不构建语义，见该文件头部注释）。约束：

1. 两份文法（`src/grammar.pest` 定位器规则 ↔ 本目录 `grammar.pest` 完整文法）必须保持同步；
2. 扩展表达式功能一律进本子系统（`grammar.pest` 只增不减），随后同步 DSL 定位器规则；
3. 回归保障：`tests/cloudflow_expression.rs` 的 `dsl_sync_*` 测试
   （属性访问/常量、索引访问、`input.`/`env.` 引用、`null` 字面量）。

## 9. 扩展新函数流程（需求 6.25/6.30）

1. `eval.rs::call_builtin` 实现求值（需要文法支持时同步 `grammar.pest`）；
2. 登记 `builtins.rs::BUILTIN_FUNCTIONS`（常量/管道算子同理）；
3. 同步宿主语义层校验（`src/semantic.rs`）与补全规范
   （`syntax-highlight/generator/config.py`）；
4. 新增单元测试（`tests/cloudflow_expression.rs` 或 crate 内测试）；
5. 更新文档：本 README、`docs/CLOUDFLOW_EXPRESSION.md`、CHANGELOG。

## 10. 版本与测试

- `API_VERSION = "expr.cloudflow.io/v1"`（需求 6.29）：表达式子系统独立版本化，独立于
  前端语言（DSL/YAML）与 IR（`workflow.cloudflow.io/v1`）版本；
- 回归测试：`tests/cloudflow_expression.rs`（17 项：null 字面量、扩展内建函数、GitHub-Actions
  对齐函数、API 版本、`${{ }}` 双大括号插值、`dsl_sync_*` 双文件同步）+ crate 内 15 项单元测试；
- 规格全文：`docs/CLOUDFLOW_EXPRESSION.md`。
