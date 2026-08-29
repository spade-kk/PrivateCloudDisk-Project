//! CloudFlow 表达式子系统（expr.cloudflow.io/v1）。
//!
//! 唯一的表达式实现方：**所有前端语言**（CloudFlow DSL / YAML / 未来 JSON）只把表达式
//! **字符串**交给本子系统，由本子系统负责：
//!   - 词法（`grammar.pest`，基于 pest 第三方库，不手工维护正则/tokenizer，需求 6.15）；
//!   - 解析并构建表达式 AST（输出 `crate::ast::ExpressionNode`，需求 6.16/6.17）；
//!   - 内建函数与常量白名单（`builtins`，需求 6.11/6.22/6.27）；
//!   - 值上下文解析（`parse_value_string`，产出 `crate::ast::ValueNode`）。
//!
//! 表达式 AST 语法树由领域层 `crate::ast` 定义（6.17：Expression AST 与 Domain AST 对应，
//! 不另行定义独立 AST 类型），本子系统不复制 AST 定义。
//!
//! 运行期求值（IR 值上下文 `$ref`/`$expr`/`$template`/`$pipeline`）收敛于
//! `evaluator` 模块：统一调度驱动与双执行面共用，宿主 crate 不得重复实现。

pub mod builtins;
pub mod evaluator;

mod parser;

pub mod eval;

pub use eval::{call_builtin, API_VERSION};
pub use parser::{
    clear_parse_caches, expression_cache_stats, parse_expression_string, parse_interpolated_value,
    parse_value_string, value_cache_stats, value_from_expression, MAX_EXPRESSION_CHARS,
    MAX_EXPRESSION_NESTING,
};
