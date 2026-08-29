//! 统一执行结果模型（需求 1.2/7.9）。

use serde_json::Value;

/// 工作流终结状态（统一驱动视角；各执行面映射到自身状态机）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WorkflowEndStatus {
    Success,
    Failed,
    Cancelled,
    /// 顶层 wait 挂起（生产：执行行保持 WAITING，等待审批/恢复接口）。
    Waiting,
    /// 全局执行超时（调试面 `overall_timeout_ms`）。
    Timeout,
    /// 断点/单步暂停（调试面）。
    Breakpoint,
}

/// 节点终结状态（调试面结果模型；生产面节点状态由检查点表承载）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NodeStatus {
    Success,
    Failed,
    Skipped,
    Waiting,
}

/// 节点终结记录（`StateStore::node_finished`；调试面落 DevNodeResult）。
#[derive(Debug, Clone)]
pub struct NodeFinish {
    pub status: NodeStatus,
    /// 节点输入摘要（动作入参；控制节点为 Null/None）。
    pub input: Option<Value>,
    /// 节点输出（动作输出/控制节点载荷；wait 与部分控制节点为 None）。
    pub output: Option<Value>,
    /// 实际尝试次数（动作节点）；非动作节点为 0。
    pub attempts: u32,
    /// 失败信息（code + 面内文案 + 详情）。
    pub error: Option<NodeError>,
    /// 节点开始时刻（毫秒，经 `Clock`；调试面 DevNodeResult.started_at_ms）。
    pub started_at_ms: u64,
    /// 节点耗时（毫秒；调试面 DevNodeResult.duration_ms）。
    pub duration_ms: u64,
}

/// 节点失败记录（面内错误码/文案；统一驱动按执行面渲染）。
#[derive(Debug, Clone)]
pub struct NodeError {
    pub code: String,
    pub message: String,
    pub detail: Option<String>,
}

/// 终结原因分类（`StateStore::skip_pending` / 结果错误记录共用）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TerminalKind {
    /// 全局执行超时（CFD-8104）。
    Timeout,
    /// 进入 WAITING（CFD-8105）。
    Waiting,
    /// 断点/单步（CFD-8106）；`paused_at` 为断点节点（单步为 None）。
    Breakpoint { paused_at: Option<String> },
    /// 提前 return（CFD-8107）。
    StepReturn,
    /// 工作流失败（CFD-8108）。
    Failed,
}

/// 结果级错误记录（调试面 `DevExecutionResult.errors` 的来源；
/// 生产面不消费，仅保留统一结构便于未来可观测性埋点）。
#[derive(Debug, Clone)]
pub struct ErrorRecord {
    /// 终结分类（节点失败为 `Failed` + `error` 携带 ExecutionError）。
    pub kind: TerminalKind,
    /// 关联节点（节点级错误；全局错误为 None）。
    pub node: Option<String>,
    /// 节点失败时的统一执行错误（全局终结错误为 None，文案由 kind 决定）。
    pub error: Option<crate::engine::error::ExecutionError>,
}

/// 统一执行结果（`execute` 返回值，需求 1.2/6.7）。
#[derive(Debug, Clone)]
pub struct ExecutionResult {
    /// 工作流终结状态。
    pub status: WorkflowEndStatus,
    /// 终结/失败错误记录（调试面映射为 `errors`；生产面忽略）。
    pub errors: Vec<ErrorRecord>,
    /// 总耗时（毫秒；经 `Clock`，含调试面模拟延迟）。
    pub duration_ms: u64,
    /// 工作流输出（`spec.outputs` 按终结上下文求值；求值失败置 Null）。
    pub outputs: Value,
}
