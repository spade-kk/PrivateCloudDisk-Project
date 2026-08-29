//! CloudFlow 统一执行核心（`cloudflow-engine-core`）。
//!
//! 分层定位（多前端语言编译器平台 · 执行引擎统一架构）：
//!
//! ```text
//! cloudflow-dsl（Pest 前端）  cloudflow-yaml（serde_yaml_ng 前端）
//!            └────────────┬────────────┘
//!                   Workflow Domain AST（`ast`）
//!                          语义分析（宿主 crate 的 `semantic`）
//!                          统一 IR（`ir`，`workflow.cloudflow.io/v1`）
//!                          IR 契约校验（`ir_validate`，唯一实现）
//!                          执行语义核心（`execution_core`，纯函数）
//!                          统一调度驱动（`engine::driver`，双执行面共用）
//!                          ├─ 生产执行面（宿主 crate：数据库 + Agent + MQ 依赖实现）
//!                          └─ 开发调试面（`dev_exec`：纯内存依赖实现）
//! ```
//!
//! 本 crate 是**纯执行核心**：
//! - 不依赖数据库（MySQL/Postgres）、Redis、RabbitMQ、HTTP 框架、gRPC；
//! - 不持有任何全局可变状态；所有执行状态经 `engine::context::ExecutionContext`
//!   与 `EngineDeps` 注入的抽象实现传递；
//! - 表达式子系统（`expression`）是全部前端语言与双执行面的**唯一**表达式
//!   词法/解析/白名单函数/运行时求值实现，宿主 crate 不得重复定义。

pub mod ast;
pub mod dev_exec;
pub mod diagnostic;
pub mod engine;
pub mod execution_core;
pub mod expression;
pub mod ir;
pub mod ir_validate;
pub mod runtime;

/// Workflow IR（`workflow.cloudflow.io/v1`）：双执行面与调试入口直接消费的结构。
pub use ir::WorkflowIrV1;
/// IR 契约校验（唯一 IR 校验实现）：纯函数、一次收集全部问题。
pub use ir_validate::{validate_ir_contracts, IrContractIssue, IR_API_VERSION, VALID_NODE_TYPES};
