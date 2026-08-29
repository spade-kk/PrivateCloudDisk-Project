//! 统一执行引擎（双执行面共用的唯一调度/执行实现，需求 §一/§七）。
//!
//! 分层：
//! - `context`：`ExecutionContext`（工作流级元数据）与 `StepContext`（单次动作调用）；
//! - `deps`：`EngineDeps` 六个 trait 对象（`StateStore`/`LogSink`/`ActionExecutor`/
//!   `EventPublisher`/`Clock`/`ConfigProvider`）——生产/调试行为分叉的唯一入口；
//! - `error`：`ExecutionError`（统一错误模型 + 双执行面错误码映射）；
//! - `result`：`ExecutionResult`/`NodeFinish`/`TerminalKind` 等结果模型；
//! - `memory`：纯内存依赖实现（开发调试面；支持快照/级别过滤/节点过滤）；
//! - `clock`：`RealClock`（生产）与 `VirtualClock`（调试，模拟延迟记账）；
//! - `driver`：统一调度驱动——检查点恢复、主循环、节点分发（condition/try/loop/
//!   switch/parallel/assert/validate/notify/wait/delay/return/break/continue/
//!   动作节点）、重试/退避/超时、on_error 与全局失败处理器。
//!
//! 生产执行面（宿主 crate `execution`）与开发调试面（`crate::dev_exec`）都经
//! `driver::execute` / `driver::execute_sync` 进入，仅依赖实现不同。

pub mod clock;
pub mod context;
pub mod deps;
mod driver;
pub mod error;
pub mod memory;
pub mod result;

/// 统一执行入口（需求 1.2）：`execute(ir, context, deps) -> ExecutionResult`。
pub use driver::execute;
/// 同步执行入口（需求 1.18：调试面同步接口，含运行时嵌套防护）。
pub use driver::execute_sync;
