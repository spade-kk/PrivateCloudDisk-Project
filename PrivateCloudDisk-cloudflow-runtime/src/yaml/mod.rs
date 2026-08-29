//! CloudFlow YAML 前端（yaml.cloudflow.io/v1）。
//!
//! 第二前端语言：CloudFlow YAML 面向低门槛、配置型工作流；编译流程与 DSL 一致地汇入
//! **同一 Workflow Domain AST**（`crate::ast::WorkflowNode`），随后共用语义分析、IR 生成
//! 与 Runtime（需求 2.1/8.x）。YAML 词法/解析由第三方库 `serde_yaml_ng` 完成（需求 7.x），
//! 只允许本前端把表达式字符串切出并交给表达式子系统（需求 6.31）。
//!
//! **分层约定**：本模块只暴露 YAML **自身**的能力（YAML → 领域 AST 的 `parse_yaml` /
//! `parse_yaml_detailed`；Schema 形状校验是内部第一步，见 `schema.rs`）。跨前端调度
//! （`Language` 枚举、`language_of` 扩展名识别、`parse_frontend_detailed` 语言分发）属于
//! crate 根层的「前端调度器」，定义在 `src/lib.rs`，以 `cloudflow_runtime::{Language,
//! language_of, parse_frontend_detailed}` 对外；本模块**不再**重复定义这些共享概念。

mod convert;
mod locator;
mod model;
pub(crate) mod schema;

pub use convert::{parse_yaml, parse_yaml_detailed};
