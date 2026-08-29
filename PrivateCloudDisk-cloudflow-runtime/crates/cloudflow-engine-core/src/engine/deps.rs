//! `EngineDeps`：统一执行引擎的依赖注入面（需求 1.4-1.10/1.17）。
//!
//! 执行引擎核心不区分生产/调试模式——行为完全由这六个 trait 对象的具体
//! 实现决定：
//!
//! | 依赖            | 生产实现（宿主 crate）            | 调试实现（本 crate `memory`）        |
//! |-----------------|-----------------------------------|--------------------------------------|
//! | `StateStore`    | MySQL 检查点/执行行（`MysqlStateStore`） | `InMemoryStateStore`（快照/恢复） |
//! | `LogSink`       | tracing 结构化日志                | `InMemoryLogSink`（+stdout 组合）    |
//! | `ActionExecutor`| Capability Agent（gRPC，唯一能力出口） | `MockActionExecutor`（纯内存） |
//! | `EventPublisher`| （保留扩展位；当前经 outbox 表）  | `NoopEventPublisher`                |
//! | `Clock`         | 真实时钟（`RealClock`）           | 虚拟时钟（`VirtualClock`，模拟延迟） |
//! | `ConfigProvider`| 生产策略（并行度/超时/分支展开）  | 调试策略（断点/单步/mock/注入失败）  |
//!
//! 动作调用**必须**经 `ActionExecutor` 抽象（需求 7）：执行引擎核心不直接
//! 访问数据库、文件系统或其他微服务；Agent 是唯一的能力调用入口。

use crate::engine::context::{ExecutionContext, StepContext, StepRef};
use crate::engine::error::ExecutionError;
use crate::engine::result::{NodeError, NodeFinish, NodeStatus, TerminalKind};
use crate::ir::{NodeIr, WorkflowIrV1};
use serde_json::Value;
use std::collections::HashSet;
use std::sync::Arc;
use std::time::Duration;

/// 统一执行引擎依赖（需求 1.4）：全部以 trait 对象注入。
///
/// `’a` 为借用场景保留；当前生产面与调试面均以 `’static` `Arc` 实现注入
/// （调试面动作执行器为统一驱动 `ActionExecutor` 的内存/gRPC 实现，
/// 不另定义同步动作抽象）。
#[derive(Clone)]
pub struct EngineDeps<'a> {
    pub state: Arc<dyn StateStore + 'a>,
    pub log: Arc<dyn LogSink + 'a>,
    pub action: Arc<dyn ActionExecutor + 'a>,
    pub events: Arc<dyn EventPublisher + 'a>,
    pub clock: Arc<dyn Clock + 'a>,
    pub config: Arc<dyn ConfigProvider + 'a>,
}

impl<'a> EngineDeps<'a> {
    pub fn new(
        state: Arc<dyn StateStore + 'a>,
        log: Arc<dyn LogSink + 'a>,
        action: Arc<dyn ActionExecutor + 'a>,
        events: Arc<dyn EventPublisher + 'a>,
        clock: Arc<dyn Clock + 'a>,
        config: Arc<dyn ConfigProvider + 'a>,
    ) -> Self {
        Self {
            state,
            log,
            action,
            events,
            clock,
            config,
        }
    }
}

/// 执行控制轮询结果（需求 5.8/5.9：暂停、恢复、取消；1.22 快照）。
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct ControlFlags {
    /// 取消请求（用户经管理接口 `request_cancel`）。
    pub cancelled: bool,
    /// 暂停请求或已进入 WAITING（审批挂起）。
    pub paused: bool,
}

/// 顶层控制节点的展开模式（双执行面唯一的行为分叉之一，经注入区分）：
/// - `Deferred`（生产）：顶层 condition 只评估并跳过未选中分支，选中分支由主循环
///   下一轮经依赖边调度；顶层 switch/parallel 仅写 `{"control": ...}` 检查点
///   （与历史生产执行面逐字一致）；
/// - `Inline`（调试）：顶层 condition/switch/parallel 在节点内联展开执行
///   （与历史调试执行面逐字一致）。
///
/// 动态执行体（循环/try/switch 内部）两种模式行为一致：内联展开。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExpandMode {
    Deferred,
    Inline,
}

/// 状态存储（需求 1.5/2.1-2.25）：工作流与节点状态读取、更新、持久化。
///
/// 生产实现将检查点写入数据库（每步提交）；调试实现使用内存（支持快照）。
#[async_trait::async_trait]
pub trait StateStore: Send + Sync {
    /// 启动时恢复已完成节点（生产：检查点表；调试：空）。
    async fn restore_completed(
        &self,
        ctx: &ExecutionContext,
    ) -> Result<Vec<String>, ExecutionError>;

    /// 轮询执行控制（生产：读执行行；取消/暂停/消失；调试：恒为 AllClear）。
    async fn poll_control(&self, ctx: &ExecutionContext) -> Result<ControlFlags, ExecutionError>;

    /// 构建表达式上下文 `{"vars": ..., "steps": {"<id>": {"output": ...}}}`。
    /// 生产：执行行 vars + 已累积输出；调试：内存快照。
    async fn build_context(&self, ctx: &ExecutionContext) -> Result<Value, ExecutionError>;

    /// 登记规范化变量（调试：写入内存快照；生产：变量已在执行行，no-op）。
    /// 同步方法：驱动在首次调度前由执行面调用；双执行面均无阻塞 I/O
    /// （调试面写内存，生产面 no-op），无需异步化。
    fn init_variables(&self, ctx: &ExecutionContext, vars: &Value) -> Result<(), ExecutionError>;

    /// 步骤检查点开始；返回该步骤的尝试序号（生产：DB attempt；调试：本地序号）。
    async fn begin_step(
        &self,
        ctx: &ExecutionContext,
        step: &StepRef,
        input: &Value,
    ) -> Result<u64, ExecutionError>;

    /// 步骤成功（输出 + 耗时毫秒）。
    async fn finish_step_success(
        &self,
        ctx: &ExecutionContext,
        step: &StepRef,
        attempt: u64,
        output: &Value,
        duration_ms: u64,
    ) -> Result<(), ExecutionError>;

    /// 步骤失败（错误码 + 摘要 + 是否可重试 + 耗时）。
    #[allow(clippy::too_many_arguments)] // 步骤行完整字段集，与持久化表结构对齐
    async fn finish_step_failure(
        &self,
        ctx: &ExecutionContext,
        step: &StepRef,
        attempt: u64,
        code: &str,
        summary: &str,
        retryable: bool,
        duration_ms: u64,
    ) -> Result<(), ExecutionError>;

    /// 步骤跳过（未选中的控制流分支等）。
    async fn finish_step_skipped(
        &self,
        ctx: &ExecutionContext,
        step: &StepRef,
        reason: &str,
    ) -> Result<(), ExecutionError>;

    /// 顶层 wait 挂起：生产写步骤行并 `request_pause`；调试记录 Waiting。
    async fn on_waiting(
        &self,
        ctx: &ExecutionContext,
        step: &StepRef,
        payload: &Value,
    ) -> Result<(), ExecutionError>;

    /// 终结工作流（生产：`finish_execution`，含 outbox 事件；调试：记录终结态）。
    async fn finish_workflow(
        &self,
        ctx: &ExecutionContext,
        status: crate::engine::result::WorkflowEndStatus,
        code: Option<&str>,
        message: &str,
    ) -> Result<(), ExecutionError>;

    /// 节点生命周期簿记（调试面 DevNodeResult；生产 no-op——生产节点状态由检查点表承载）。
    fn node_started(&self, _ctx: &ExecutionContext, _step: &StepRef, _input: &Value) {}
    fn node_finished(&self, _ctx: &ExecutionContext, _step: &StepRef, _finish: &NodeFinish) {}

    /// 将未终结节点标记为 Skipped（调试面；生产 no-op——DB 中 pending 步骤保持原状）。
    /// 返回实际被标记的节点 ID（驱动据此记录调试日志）。
    fn skip_pending(
        &self,
        _ctx: &ExecutionContext,
        _kind: &TerminalKind,
        _exclude: &HashSet<String>,
    ) -> Vec<String> {
        Vec::new()
    }

    /// while 迭代后刷新迭代上下文（调试：取最新内存快照；生产：保持循环起点快照，no-op）。
    fn refresh_iteration_context(&self, _ctx: &ExecutionContext, _context: &mut Value) {}

    /// 全局失败处理器节点（生产：IR `extensions["handlers"]`；调试：空——保持历史行为）。
    fn global_failure_handlers(&self, _ir: &WorkflowIrV1) -> Vec<NodeIr> {
        Vec::new()
    }

    /// 动作入参登记（调试面 `DevNodeResult.input`；生产步骤行已在 `begin_step` 写入入参，no-op）。
    fn record_step_input(&self, _ctx: &ExecutionContext, _step: &StepRef, _input: &Value) {}

    /// 动作输出登记（调试面：动作成功与 mock 覆盖，`attempts` 为实际尝试次数；
    /// 生产经 `finish_step_success` 写步骤行，no-op）。
    fn record_step_output(
        &self,
        _ctx: &ExecutionContext,
        _step: &StepRef,
        _output: &Value,
        _attempts: u32,
    ) {
    }

    /// 被跳过节点的空输出占位（调试面 `skip_nodes`：输出置 Null 使下游 `steps.<id>.output`
    /// 引用可解析；生产 no-op——DB 无该步骤行，表达式按“引用不存在”处理）。
    fn record_null_step_output(&self, _ctx: &ExecutionContext, _step: &StepRef) {}

    /// 动态执行体节点簿记（调试面：仅更新 `DevNodeResult.status/error`，不改输入输出与
    /// 起止时刻——与历史纯内存语义一致；生产 no-op——动态实例经步骤行审计）。
    fn node_outcome(
        &self,
        _ctx: &ExecutionContext,
        _step: &StepRef,
        _status: NodeStatus,
        _error: Option<NodeError>,
    ) {
    }
}

/// 日志收集（需求 1.6/3.1-3.25）：结构化日志写入，支持级别与节点过滤（实现侧）。
///
/// `log` 为同步方法：统一驱动的同步日志辅助（`Driver::log`）与异步上下文
/// 共用同一出口；生产实现（tracing）与调试实现（内存/stdout）均为同步写入，
/// 批量/异步落库由生产 `LogSink` 实现内部自行管理（对驱动透明）。
pub trait LogSink: Send + Sync {
    /// 写一条日志（`node` 为 None 表示工作流级）。
    fn log(&self, entry: LogEntry);
}

/// 日志条目（需求 3.2：级别、节点、消息；执行/工作流 ID 由实现侧附加）。
#[derive(Debug, Clone)]
pub struct LogEntry {
    pub level: LogLevel,
    pub node: Option<String>,
    pub message: String,
}

impl LogEntry {
    pub fn new(level: LogLevel, node: Option<String>, message: impl Into<String>) -> Self {
        Self {
            level,
            node,
            message: message.into(),
        }
    }
}

/// 日志级别（与调试面 `DevLogLevel` 同序；生产面映射 tracing 级别）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum LogLevel {
    Debug,
    Info,
    Warn,
    Error,
}

/// 动作执行器（需求 1.7/4.1-4.25）：执行引擎调用能力的**唯一**出口。
///
/// 生产实现 = `AgentActionExecutor`（经 Capability Agent gRPC，含鉴权/审计/限流）；
/// 调试实现 = `MockActionExecutor`（纯内存，支持注入失败/模拟延迟/mock 输出）。
#[async_trait::async_trait]
pub trait ActionExecutor: Send + Sync {
    /// 执行一次动作调用；成功返回输出值，失败返回
    /// `ExecutionError::Action { code, message, retryable }`。
    /// 超时实施（真实/模拟）由实现侧负责。
    async fn execute(&self, step: &StepContext) -> Result<Value, ExecutionError>;
}

/// 事件发布（需求 1.8）：工作流状态变更/节点完成等事件。
///
/// 当前生产面经数据库 outbox 表 + MQ 发布（与状态写入同事务，原子性优先），
/// 本 trait 为实时事件通道保留扩展位；默认实现 `NoopEventPublisher`。
#[async_trait::async_trait]
pub trait EventPublisher: Send + Sync {
    async fn publish(&self, event: WorkflowEvent) -> Result<(), ExecutionError>;
}

/// 工作流事件（需求 1.8）。
#[derive(Debug, Clone)]
pub struct WorkflowEvent {
    pub topic: &'static str,
    pub execution_id: String,
    pub payload: Value,
}

/// 时钟（需求 1.9）：当前时间与睡眠，便于测试时间相关逻辑。
#[async_trait::async_trait]
pub trait Clock: Send + Sync {
    /// 当前执行时间（毫秒；调试面为虚拟时间 = 墙钟 + 模拟延迟累计）。
    fn now_ms(&self) -> u64;

    /// 重试退避睡眠（生产：真实 sleep；调试：no-op，仅记录计划延迟）。
    async fn sleep_backoff(&self, _delay: Duration) {}

    /// 延迟/模拟延迟消费（生产 delay 节点：真实 sleep；调试：`honor_delays` 时
    /// 线程睡眠且封顶 5s，否则 no-op；模拟动作延迟由执行器经 `advance` 记账）。
    async fn sleep_delay(&self, ms: u64) {
        self.sleep_backoff(Duration::from_millis(ms)).await
    }

    /// 记账虚拟时间（调试面模拟动作延迟；生产 no-op）。
    fn advance(&self, _ms: u64) {}
}

/// 运行时配置（需求 1.10/6.x 调试钩子）：最大并行数、超时、重试等运行参数
/// 与调试面专用策略（断点/单步/跳过/mock/注入失败）。
pub trait ConfigProvider: Send + Sync {
    /// 顶层批并发（生产：IR `runtime.maxParallel` clamp(1,32)；调试：1，确定性串行）。
    fn top_level_batch_size(&self, ir: &WorkflowIrV1) -> usize {
        ir.runtime.max_parallel.unwrap_or(1).clamp(1, 32) as usize
    }

    /// 是否跳过 IR 契约校验（调试需求 4.11；生产恒 false）。
    fn skip_validation(&self) -> bool {
        false
    }

    /// 全局执行超时（毫秒；生产 None，调试面 `overall_timeout_ms`）。
    fn overall_timeout_ms(&self) -> Option<u64> {
        None
    }

    /// 顶层控制节点展开模式（生产 Deferred / 调试 Inline）。
    fn expand_mode(&self) -> ExpandMode {
        ExpandMode::Deferred
    }

    /// 执行到该顶层节点**前**的暂停钩子（调试断点；返回 Some 即暂停）。
    fn before_node(&self, _node_id: &str) -> Option<TerminalKind> {
        None
    }

    /// 顶层节点成功完成**后**的暂停钩子（调试单步；返回 Some 即暂停）。
    fn after_node(&self, _node_id: &str) -> Option<TerminalKind> {
        None
    }

    /// 直接跳过该节点（调试 `skip_nodes`；生产无）。
    fn skip_node(&self, _node_id: &str) -> bool {
        false
    }

    /// 默认动作超时（毫秒）：生产取 Runtime 配置；调试取 `DevConfig.default_timeout_ms`。
    fn default_action_timeout_ms(&self) -> u64 {
        30_000
    }

    /// 是否启用表达式求值（调试 `enable_expressions`；false 时 `$ref`/`$expr`/`$template`/
    /// `$pipeline` 按字面量透传——历史调试面行为；生产恒 true）。
    fn expressions_enabled(&self) -> bool {
        true
    }

    /// 节点模拟动作延迟（毫秒；调试面 mock 延迟，经 `Clock::advance` 记账虚拟时间）。
    fn simulated_latency_ms(&self, _node_id: &str) -> u64 {
        0
    }

    /// mock 节点输出覆盖（调试需求 4.24：绕过动作执行器直接给输出）。
    fn mock_output(&self, _node_id: &str) -> Option<Value> {
        None
    }

    /// 节点第 N 次尝试的注入失败计划（调试测试支持；按尝试顺序消费）。
    fn injected_failure(&self, _node_id: &str, _attempt: u32) -> Option<FailureInjection> {
        None
    }
}

/// 注入的节点级失败计划（调试面测试支持；与历史 `DevFailureSpec` 同构）。
#[derive(Debug, Clone)]
pub struct FailureInjection {
    /// 能力错误码（CF5001 超时 / CF5002 动作失败 等）。
    pub code: String,
    /// 错误摘要。
    pub message: String,
    /// 是否可重试（false 时立即终止重试循环）。
    pub retryable: bool,
}

/// 空事件发布器（需求 7.6 调试面默认；生产面当前亦经 outbox，不走实时通道）。
#[derive(Debug, Clone, Copy, Default)]
pub struct NoopEventPublisher;

#[async_trait::async_trait]
impl EventPublisher for NoopEventPublisher {
    async fn publish(&self, _event: WorkflowEvent) -> Result<(), ExecutionError> {
        Ok(())
    }
}
