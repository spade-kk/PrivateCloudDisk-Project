//! 开发调试执行面（需求清单 §4/§9/§10）：直接 IR 驱动、纯内存同步执行的 Dev Runner。
//!
//! 双执行面架构中的定位（与生产执行面 `宿主 crate::execution` 对照）：
//! - 生产执行面：持久化调度器与执行协调器——数据库任务表 → Worker 竞争领取 →
//!   Capability Agent 调用 → 检查点/stale recovery，异步；
//! - 本模块（开发调试面）：接收 IR（结构体或 JSON 字符串）→ IR 契约校验 →
//!   **统一调度驱动**（`crate::engine::driver`，与生产面同一实现）→ 纯内存
//!   依赖（`InMemoryStateStore` + `InMemoryLogSink` + `MockActionExecutor`（直接实现
//!   统一驱动 `ActionExecutor`）+ `VirtualClock` + 调试策略 `ConfigProvider`）→ 直接
//!   返回完整结果。
//!   **不是**第二个执行引擎——所有控制流语义（条件/分支/try/循环/重试/退避/
//!   并行/超时/子树展开/控制信号）由统一驱动唯一实现，本模块只保留调试面
//!   特有的 I/O（内存日志、快照、断点/单步、注入失败计划）与 CFD-81xx 错误码包装。
//! - 不创建执行任务 ID、不写数据库、不经过 MQ/Redis、不持久化日志（日志收集在内存，
//!   可输出到 stdout，绝不进入生产监控）。
//!
//! 分层：IR 加载 → 校验（`ir_validate::validate_ir_contracts`，与生产
//! `compiler::validate_ir` 同一校验器）→ 变量规范化（`expression::evaluator`）
//! → `engine::driver::execute_sync`（`EngineDeps` 注入内存实现）→ 结果映射
//! （`ExecutionResult` + 内存状态/日志 → `DevExecutionResult`）。

use crate::engine::context::{ExecutionContext, StepContext};
use crate::engine::deps::{
    ConfigProvider, EngineDeps, ExpandMode, FailureInjection, LogLevel, NoopEventPublisher,
    StateStore,
};
use crate::engine::execute_sync;
use crate::engine::memory::{InMemoryLogSink, InMemoryStateStore};
use crate::engine::result::{ExecutionResult, TerminalKind, WorkflowEndStatus};
use crate::engine::{clock, error as engine_error};
use crate::expression::evaluator::normalize_variables;
use crate::ir::ActionIr;
use crate::ir_validate::{validate_ir_contracts, IrContractIssue};
use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};
use std::collections::{BTreeMap, BTreeSet};
use std::sync::Arc;

/// 调试入口错误（入口级，区别于执行过程中的节点失败——后者进入结果 `errors`）。
#[derive(Debug, thiserror::Error)]
pub enum DevEntryError {
    #[error("IR JSON 解析失败：{0}")]
    InvalidJson(String),
    #[error("IR 契约校验未通过（{} 项问题）", _0.len())]
    Validation(Vec<IrContractIssue>),
    #[error("开发执行引擎内部错误：{0}")]
    Internal(String),
}

/// 工作流级执行状态（调试入口视角）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum DevWorkflowStatus {
    Success,
    Failed,
    /// wait/审批节点：工作流挂起，调试入口立即返回（无恢复游标，与生产 WAITING 语义一致）。
    Waiting,
    /// 到达 `--breakpoint` 或单步执行边界，携带上下文快照。
    Breakpoint,
    /// 全局执行超时（需求 4.14）。
    Timeout,
}

/// 节点级执行状态。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum DevTaskStatus {
    Pending,
    Running,
    Success,
    Failed,
    Skipped,
    Waiting,
}

impl std::fmt::Display for DevWorkflowStatus {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let value = match self {
            Self::Success => "success",
            Self::Failed => "failed",
            Self::Waiting => "waiting",
            Self::Breakpoint => "breakpoint",
            Self::Timeout => "timeout",
        };
        write!(formatter, "{value}")
    }
}

impl std::fmt::Display for DevTaskStatus {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let value = match self {
            Self::Pending => "pending",
            Self::Running => "running",
            Self::Success => "success",
            Self::Failed => "failed",
            Self::Skipped => "skipped",
            Self::Waiting => "waiting",
        };
        write!(formatter, "{value}")
    }
}

/// 调试日志级别（需求 10.6）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum DevLogLevel {
    Debug,
    Info,
    Warn,
    Error,
}

/// 结构化执行日志条目（需求 10.1/10.2/10.3）：节点 ID、类型、时间、状态、摘要。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DevLogEntry {
    pub sequence: u64,
    pub level: DevLogLevel,
    /// 节点 ID（与节点无关的全局日志为 None）。
    #[serde(skip_serializing_if = "Option::is_none")]
    pub node_id: Option<String>,
    /// 日志时间（相对执行开始的毫秒数，确定性输出便于测试）。
    pub elapsed_ms: u64,
    pub message: String,
}

/// 执行错误（需求 4.22/10.4：调试模式返回错误详情，不脱敏）。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DevError {
    pub code: String,
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub node_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
}

/// 单节点执行结果（需求 4.13/10.8：耗时与结果）。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DevNodeResult {
    pub node_id: String,
    pub node_type: String,
    pub status: DevTaskStatus,
    /// 动作调用次数（含重试）。
    #[serde(default, skip_serializing_if = "non_zero_u32")]
    pub attempts: u32,
    #[serde(default, skip_serializing_if = "non_zero_u64")]
    pub started_at_ms: u64,
    pub duration_ms: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub input: Option<Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub output: Option<Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<DevError>,
    /// 依赖关系快照（用于执行报告，需求 10.13）。
    pub depends_on: Vec<String>,
}

impl Default for DevNodeResult {
    fn default() -> Self {
        Self {
            node_id: String::new(),
            node_type: String::new(),
            status: DevTaskStatus::Pending,
            attempts: 0,
            started_at_ms: 0,
            duration_ms: 0,
            input: None,
            output: None,
            error: None,
            depends_on: Vec::new(),
        }
    }
}

/// 完整执行结果（需求 4.5/4.17/2.14/2.5）：状态、节点状态、输出变量、错误、日志、
/// 耗时与上下文快照（可序列化，支持调试输出与快照保存）。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DevExecutionResult {
    pub status: DevWorkflowStatus,
    pub node_results: BTreeMap<String, DevNodeResult>,
    /// `spec.outputs` 求值结果（成功/等待/断点时求值；失败时可能部分可用）。
    pub outputs: Value,
    pub errors: Vec<DevError>,
    pub logs: Vec<DevLogEntry>,
    pub duration_ms: u64,
    /// 执行上下文快照（vars + steps 输出 + outputs），便于调试与保存/恢复（需求 4.17/5.x）。
    pub context_snapshot: Value,
}

impl DevExecutionResult {
    /// 执行摘要（需求 10.8：总耗时、节点数、成功数、失败数）。
    pub fn summary(&self) -> serde_json::Value {
        let mut success = 0usize;
        let mut failed = 0usize;
        let mut skipped = 0usize;
        for result in self.node_results.values() {
            match result.status {
                DevTaskStatus::Success => success += 1,
                DevTaskStatus::Failed => failed += 1,
                DevTaskStatus::Skipped => skipped += 1,
                _ => {}
            }
        }
        serde_json::json!({
            "status": self.status,
            "durationMs": self.duration_ms,
            "nodes": self.node_results.len(),
            "success": success,
            "failed": failed,
            "skipped": skipped,
            "errors": self.errors.len(),
        })
    }

    /// 执行图可视化数据（需求 10.16：nodes/edges/状态着色）。
    pub fn execution_graph(&self, ir: &crate::ir::WorkflowIrV1) -> serde_json::Value {
        let nodes: Vec<Value> = ir
            .spec
            .graph
            .nodes
            .iter()
            .map(|node| {
                serde_json::json!({
                    "id": node.id,
                    "type": node.node_type,
                    "status": self.node_results.get(&node.id).map(|r| r.status),
                    "dependsOn": node.depends_on,
                })
            })
            .collect();
        let edges: Vec<Value> = ir
            .spec
            .graph
            .edges
            .iter()
            .map(|edge| serde_json::json!({"from": edge.from, "to": edge.to}))
            .collect();
        serde_json::json!({"nodes": nodes, "edges": edges})
    }

    /// 导出 Markdown 执行报告（需求 10.15）：每节点详情 + 依赖关系 + 摘要。
    pub fn render_markdown(&self, ir: &crate::ir::WorkflowIrV1) -> String {
        let mut text = String::new();
        text.push_str("# CloudFlow 调试执行报告\n\n");
        let summary = self.summary();
        text.push_str(&format!(
            "- 状态：`{}`\n- 总耗时：{}ms\n- 节点：{}（成功 {} / 失败 {} / 跳过 {}）\n\n",
            self.status,
            self.duration_ms,
            summary["nodes"],
            summary["success"],
            summary["failed"],
            summary["skipped"],
        ));
        text.push_str("## 节点明细\n\n");
        text.push_str("| 节点 | 类型 | 状态 | 尝试 | 耗时(ms) | 依赖 |\n");
        text.push_str("| --- | --- | --- | --- | --- | --- |\n");
        for node in &ir.spec.graph.nodes {
            if let Some(result) = self.node_results.get(&node.id) {
                text.push_str(&format!(
                    "| {} | {} | {} | {} | {} | {} |\n",
                    node.id,
                    result.node_type,
                    result.status,
                    result.attempts,
                    result.duration_ms,
                    result.depends_on.join(", "),
                ));
            }
        }
        if !self.errors.is_empty() {
            text.push_str("\n## 错误\n\n");
            for error in &self.errors {
                text.push_str(&format!(
                    "- `{}`：{}{}\n",
                    error.code,
                    error.message,
                    error
                        .node_id
                        .as_ref()
                        .map(|id| format!("（节点 `{id}`）"))
                        .unwrap_or_default()
                ));
            }
        }
        text.push_str("\n## 输出\n\n```json\n");
        text.push_str(&serde_json::to_string_pretty(&self.outputs).unwrap_or_default());
        text.push_str("\n```\n");
        text
    }
}

/// 动作执行器（统一驱动契约，需求 1.7/4.1）：调试面**不另定义**动作抽象，直接复用
/// 统一驱动的异步 `engine::deps::ActionExecutor` 并再导出于此（历史
/// `crate::dev_exec::ActionExecutor` 路径保持可用，但现在指向同一 trait）。
///
/// 调试面实现 = `MockActionExecutor`（纯内存确定性 echo/canned/strict，零网络）；
/// HTTP `profile=agent` 注入 gRPC 生产仿真适配器（宿主 `http` 模块）。实现经
/// `Arc<dyn ActionExecutor>` 注入 `EngineDeps`（统一驱动并行 spawn 要求 `'static`），
/// 全链路动作调用只经该单一抽象（需求 7），不重复定义。
pub use crate::engine::deps::ActionExecutor;

/// 确定性 Mock 动作执行器（需求 4.15/8.17）：
/// - 默认对任意动作返回确定性 echo 输出（不调用真实插件）；
/// - `strict` 模式下仅接受 `known_actions` 中注册的动作键（用于“动作不存在”反例，需求 8.16）；
/// - `canned` 支持按动作键固定输出（模拟特定上游结果，需求 4.24 的动作级 mock）。
#[derive(Debug, Clone, Default)]
pub struct MockActionExecutor {
    pub strict: bool,
    pub known_actions: BTreeSet<String>,
    pub canned: BTreeMap<String, Value>,
}

impl MockActionExecutor {
    pub fn new() -> Self {
        Self::default()
    }

    /// 严格模式：未知动作键返回 CF5002。
    pub fn strict() -> Self {
        Self {
            strict: true,
            ..Self::default()
        }
    }

    pub fn with_known_actions(actions: impl IntoIterator<Item = String>) -> Self {
        Self {
            strict: true,
            known_actions: actions.into_iter().collect(),
            ..Self::default()
        }
    }

    pub fn with_canned(&mut self, action_key: String, output: Value) -> &mut Self {
        self.canned.insert(action_key, output);
        self
    }
}

#[async_trait::async_trait]
impl ActionExecutor for MockActionExecutor {
    async fn execute(&self, step: &StepContext) -> Result<Value, engine_error::ExecutionError> {
        let key = action_key(&step.action);
        if self.strict && !self.known_actions.contains(&key) {
            return Err(engine_error::ExecutionError::Action {
                code: "CF5002".into(),
                message: format!("动作不存在：{key}"),
                retryable: false,
            });
        }
        if let Some(output) = self.canned.get(&key) {
            return Ok(output.clone());
        }
        Ok(serde_json::json!({
            "ok": true,
            "mock": true,
            "action": key,
            "attempt": step.attempt,
            "arguments": step.input,
        }))
    }
}

/// 动作键规范化（与 semantic.rs 的能力名规则对齐）。
pub fn action_key(action: &ActionIr) -> String {
    match action.provider.as_str() {
        "plugin" => format!(
            "plugin:{}:{}",
            action.plugin_id.as_deref().unwrap_or("<unknown>"),
            action.function.as_deref().unwrap_or("<unknown>")
        ),
        "workflow" => {
            if action.method.as_ref().is_some_and(|m| !m.is_empty()) {
                format!(
                    "workflow:{}.{}",
                    action.service.as_deref().unwrap_or(""),
                    action.method.as_deref().unwrap_or("")
                )
            } else {
                format!("workflow:{}", action.service.as_deref().unwrap_or(""))
            }
        }
        _ => format!(
            "{}:{}.{}",
            action.provider,
            action.service.as_deref().unwrap_or(""),
            action.method.as_deref().unwrap_or("")
        ),
    }
}

/// 注入的节点级失败计划（需求 4.24/12.x 测试支持）：按尝试次数顺序消费。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DevFailureSpec {
    /// CF5001 超时 / CF5002 动作失败 等。
    pub code: String,
    pub message: String,
    pub retryable: bool,
}

/// 调试执行配置（需求 2.18/4.x/9.x）。
#[derive(Debug, Clone)]
pub struct DevConfig {
    /// 动作执行器为内存 Mock（默认；真实 Agent 仿真由宿主 HTTP 面 profile=agent 提供）。
    pub mock: bool,
    /// 跳过 IR 契约校验（需求 4.11，用于测试校验器本身）。
    pub skip_validation: bool,
    /// 是否启用表达式求值（需求 4.16；false 时 `$ref`/`$expr`/`$template`/`$pipeline`
    /// 按字面量透传，仅做纯结构执行）。
    pub enable_expressions: bool,
    /// 最大并行数（记录于结果；同步单线程执行按声明顺序串行，保证确定性）。
    pub max_parallel: usize,
    /// 默认动作超时（毫秒）。
    pub default_timeout_ms: u64,
    /// 全局执行超时（需求 4.14）；None 表示不限制。
    pub overall_timeout_ms: Option<u64>,
    /// 断点节点（需求 9.8）：执行到该顶层节点前暂停并返回上下文快照。
    pub breakpoint: Option<String>,
    /// 单步执行：每个顶层节点完成后暂停。
    pub single_step: bool,
    /// 跳过的节点 ID（直接标记 skipped）。
    pub skip_nodes: Vec<String>,
    /// 按节点 ID 注入的失败计划（每次失败消费一条，按尝试顺序）。
    pub inject_failures: BTreeMap<String, Vec<DevFailureSpec>>,
    /// 按动作键模拟的动作延迟（毫秒）：用于触发节点超时路径。
    pub action_latency_ms: BTreeMap<String, u64>,
    /// mock 节点输出覆盖（需求 4.24）：按节点 ID 直接给定输出，绕过动作执行器。
    pub mock_outputs: BTreeMap<String, Value>,
    /// 日志级别下限（需求 10.6）。
    pub log_level: DevLogLevel,
    /// 仅记录指定节点（None=全部，需求 10.9）。
    pub log_node_filter: Option<String>,
    /// 模拟 delay 节点的等待：false 时 delay 仅记录日志不睡眠（测试加速）。
    pub honor_delays: bool,
}

impl Default for DevConfig {
    fn default() -> Self {
        Self {
            mock: true,
            skip_validation: false,
            enable_expressions: true,
            max_parallel: 4,
            default_timeout_ms: 30_000,
            overall_timeout_ms: None,
            breakpoint: None,
            single_step: false,
            skip_nodes: Vec::new(),
            inject_failures: BTreeMap::new(),
            action_latency_ms: BTreeMap::new(),
            mock_outputs: BTreeMap::new(),
            log_level: DevLogLevel::Info,
            log_node_filter: None,
            honor_delays: true,
        }
    }
}

/// 同步执行入口（需求 4.6/2.16）：IR 结构体 → 校验 → 纯内存执行 → 完整结果。
/// 执行过程中的节点失败不返回 `Err`，而是反映在 `DevExecutionResult` 中（需求 4.22）。
pub fn dev_execute_sync(
    ir: &crate::ir::WorkflowIrV1,
    supplied: Value,
    config: &DevConfig,
    executor: Arc<dyn ActionExecutor>,
) -> Result<DevExecutionResult, DevEntryError> {
    if !config.skip_validation {
        let issues = validate_ir_contracts(ir);
        if !issues.is_empty() {
            return Err(DevEntryError::Validation(issues));
        }
    }
    // 变量规范化与生产入口一致（input/local/deferred 语义；错误包装为 CFD-8101 契约问题）。
    let vars = normalize_variables(ir, supplied).map_err(|error| {
        DevEntryError::Validation(vec![IrContractIssue::new(
            "CFD-8101",
            "spec.variables",
            None,
            error.0,
        )])
    })?;

    // 内存状态存储（调试面唯一状态来源；不写任何生产存储）。
    let store = Arc::new(InMemoryStateStore::new(Arc::new(ir.clone())));
    let ctx = ExecutionContext {
        execution_id: "dev-execution".into(),
        ..Default::default()
    };
    // 登记规范化变量（同步）：写入内存快照并初始化节点结果簿记。
    store.init_variables(&ctx, &vars).map_err(|error| {
        DevEntryError::Internal(format!("变量登记失败：{}", error.dev_message()))
    })?;

    let clock = Arc::new(clock::VirtualClock::new(config.honor_delays));
    let log_sink = Arc::new(
        InMemoryLogSink::new(clock.clone())
            .with_filter(level_of(config.log_level), config.log_node_filter.clone()),
    );
    // 统一驱动依赖：全部内存实现；动作执行器直接使用调用方注入的统一驱动
    // `ActionExecutor`（默认 `MockActionExecutor`；HTTP `profile=agent` 注入 gRPC
    // 生产仿真适配器）——调试面不另设动作抽象，不重复定义。
    let deps = EngineDeps::new(
        store.clone(),
        log_sink.clone(),
        executor,
        Arc::new(NoopEventPublisher),
        clock,
        Arc::new(DevConfigProvider(config.clone())),
    );

    // 入口日志（历史调试面首行；经内存日志收集器，时序与节点日志一致）。
    log_sink.push(
        level_of(config.log_level),
        None,
        format!(
            "dev 执行开始：workflow={}, nodes={}, vars={}",
            ir.metadata.name,
            ir.spec.graph.nodes.len(),
            vars.as_object().map(|v| v.len()).unwrap_or(0)
        ),
    );

    // 统一驱动同步执行（内部含运行时嵌套防护：tokio 上下文内改独立线程运行时）。
    let outcome = execute_sync(ir, ctx, deps)
        .map_err(|error| DevEntryError::Internal(error.dev_message()))?;

    // 快照部件导出（steps 与表达式上下文同形：{id: {"output": value}}）。
    let (vars_final, steps_final) = store.context_parts();
    Ok(to_dev_result(
        &outcome,
        &store,
        &log_sink,
        vars_final,
        steps_final,
    ))
}

/// 同步执行入口（JSON 字符串形态，需求 4.2/8.21）：解析 IR JSON 后执行。
pub fn dev_execute(
    ir_json: &str,
    supplied: Value,
    config: &DevConfig,
    executor: Arc<dyn ActionExecutor>,
) -> Result<DevExecutionResult, DevEntryError> {
    let ir: crate::ir::WorkflowIrV1 = serde_json::from_str(ir_json)
        .map_err(|error| DevEntryError::InvalidJson(error.to_string()))?;
    dev_execute_sync(&ir, supplied, config, executor)
}

/// 异步执行入口（需求 2.17/9.23）：核心为同步纯函数，此处以 `spawn_blocking` 包装，
/// 避免阻塞生产 tokio runtime 上的异步任务。
pub async fn dev_execute_async(
    ir: &crate::ir::WorkflowIrV1,
    supplied: Value,
    config: DevConfig,
    executor: Arc<dyn ActionExecutor>,
) -> Result<DevExecutionResult, DevEntryError> {
    let ir = ir.clone();
    tokio::task::spawn_blocking(move || dev_execute_sync(&ir, supplied, &config, executor.clone()))
        .await
        .map_err(|error| DevEntryError::Internal(format!("执行任务异常退出：{error}")))?
}

/// 调试面运行时配置（统一驱动 `ConfigProvider` 的调试实现）。
struct DevConfigProvider(DevConfig);

impl ConfigProvider for DevConfigProvider {
    fn top_level_batch_size(&self, _ir: &crate::ir::WorkflowIrV1) -> usize {
        // 调试面恒 1：按声明顺序确定性串行（与历史行为一致）。
        1
    }

    fn skip_validation(&self) -> bool {
        self.0.skip_validation
    }

    fn overall_timeout_ms(&self) -> Option<u64> {
        self.0.overall_timeout_ms
    }

    fn expand_mode(&self) -> ExpandMode {
        ExpandMode::Inline
    }

    fn before_node(&self, node_id: &str) -> Option<TerminalKind> {
        (self.0.breakpoint.as_deref() == Some(node_id)).then(|| TerminalKind::Breakpoint {
            paused_at: Some(node_id.to_owned()),
        })
    }

    fn after_node(&self, _node_id: &str) -> Option<TerminalKind> {
        self.0
            .single_step
            .then_some(TerminalKind::Breakpoint { paused_at: None })
    }

    fn skip_node(&self, node_id: &str) -> bool {
        self.0
            .skip_nodes
            .iter()
            .any(|item| item.as_str() == node_id)
    }

    fn default_action_timeout_ms(&self) -> u64 {
        self.0.default_timeout_ms
    }

    fn expressions_enabled(&self) -> bool {
        self.0.enable_expressions
    }

    fn simulated_latency_ms(&self, node_id: &str) -> u64 {
        self.0.action_latency_ms.get(node_id).copied().unwrap_or(0)
    }

    fn mock_output(&self, node_id: &str) -> Option<Value> {
        self.0.mock_outputs.get(node_id).cloned()
    }

    fn injected_failure(&self, node_id: &str, attempt: u32) -> Option<FailureInjection> {
        self.0
            .inject_failures
            .get(node_id)
            .and_then(|specs| specs.get(attempt.saturating_sub(1) as usize))
            .map(|spec| FailureInjection {
                code: spec.code.clone(),
                message: spec.message.clone(),
                retryable: spec.retryable,
            })
    }
}

/// 统一执行结果 → 调试面结果（状态/节点簿记/日志/错误码映射，需求 4.x/10.x）。
fn to_dev_result(
    outcome: &ExecutionResult,
    store: &InMemoryStateStore,
    sink: &InMemoryLogSink,
    vars: Value,
    steps: Map<String, Value>,
) -> DevExecutionResult {
    let status = match outcome.status {
        WorkflowEndStatus::Success => DevWorkflowStatus::Success,
        WorkflowEndStatus::Failed => DevWorkflowStatus::Failed,
        WorkflowEndStatus::Cancelled => DevWorkflowStatus::Failed,
        WorkflowEndStatus::Waiting => DevWorkflowStatus::Waiting,
        WorkflowEndStatus::Timeout => DevWorkflowStatus::Timeout,
        WorkflowEndStatus::Breakpoint => DevWorkflowStatus::Breakpoint,
    };
    let node_results = store
        .node_records()
        .into_iter()
        .map(|(node_id, record)| {
            let error = record.error.map(|error| DevError {
                code: error.code,
                message: error.message,
                node_id: Some(node_id.clone()),
                detail: error.detail,
            });
            let result = DevNodeResult {
                node_id: node_id.clone(),
                node_type: record.node_type,
                status: match record.status {
                    crate::engine::memory::RecordStatus::Pending => DevTaskStatus::Pending,
                    crate::engine::memory::RecordStatus::Running => DevTaskStatus::Running,
                    crate::engine::memory::RecordStatus::Success => DevTaskStatus::Success,
                    crate::engine::memory::RecordStatus::Failed => DevTaskStatus::Failed,
                    crate::engine::memory::RecordStatus::Skipped => DevTaskStatus::Skipped,
                    crate::engine::memory::RecordStatus::Waiting => DevTaskStatus::Waiting,
                },
                attempts: record.attempts,
                started_at_ms: record.started_at_ms,
                duration_ms: record.duration_ms,
                input: record.input,
                output: record.output,
                error,
                depends_on: record.depends_on,
            };
            (node_id, result)
        })
        .collect();
    let errors = outcome
        .errors
        .iter()
        .filter_map(|record| match (&record.kind, &record.error) {
            (TerminalKind::Failed, Some(error)) => Some(DevError {
                code: error.dev_code(),
                message: error.dev_message(),
                node_id: record.node.clone(),
                detail: detail_of(error),
            }),
            (TerminalKind::Failed, None) => Some(DevError {
                code: "CFD-8103".into(),
                message: "调度死锁：存在未完成节点但无可调度节点".into(),
                node_id: None,
                detail: None,
            }),
            (TerminalKind::Timeout, _) => Some(DevError {
                code: "CFD-8104".into(),
                message: "全局执行超时：到达 overall_timeout_ms 上限".into(),
                node_id: None,
                detail: None,
            }),
            (TerminalKind::Waiting, _) => Some(DevError {
                code: "CFD-8105".into(),
                message: "工作流进入 WAITING（审批/等待节点），调试入口立即返回".into(),
                node_id: None,
                detail: None,
            }),
            (TerminalKind::Breakpoint { .. }, _) => Some(DevError {
                code: "CFD-8106".into(),
                message: "到达断点/单步边界：执行暂停并返回上下文快照".into(),
                node_id: record.node.clone(),
                detail: None,
            }),
            _ => None,
        })
        .collect();
    let logs = sink
        .records()
        .into_iter()
        .map(|record| DevLogEntry {
            sequence: record.seq,
            level: match record.level {
                LogLevel::Debug => DevLogLevel::Debug,
                LogLevel::Info => DevLogLevel::Info,
                LogLevel::Warn => DevLogLevel::Warn,
                LogLevel::Error => DevLogLevel::Error,
            },
            node_id: record.node,
            elapsed_ms: record.ts_ms,
            message: record.message,
        })
        .collect();
    // 快照中 steps 与表达式上下文同形：{id: {"output": value}}（需求 6.x 快照可复用于断点恢复）。
    let context_snapshot = serde_json::json!({
        "vars": vars,
        "steps": steps,
        "outputs": outcome.outputs,
        "status": status,
    });
    DevExecutionResult {
        status,
        node_results,
        outputs: outcome.outputs.clone(),
        errors,
        logs,
        duration_ms: outcome.duration_ms,
        context_snapshot,
    }
}

fn detail_of(error: &engine_error::ExecutionError) -> Option<String> {
    match error {
        engine_error::ExecutionError::Action { retryable, .. } => {
            Some(format!("retryable={retryable}"))
        }
        _ => None,
    }
}

fn level_of(level: DevLogLevel) -> LogLevel {
    match level {
        DevLogLevel::Debug => LogLevel::Debug,
        DevLogLevel::Info => LogLevel::Info,
        DevLogLevel::Warn => LogLevel::Warn,
        DevLogLevel::Error => LogLevel::Error,
    }
}

/// 调试执行内部错误（保留类型以兼容历史调用方；统一驱动启用后不再产生）。
#[derive(Debug)]
pub enum DevExecError {
    Business(DevError),
    LoopBreak,
    LoopContinue,
    StepReturn(Value),
}

impl std::fmt::Display for DevExecError {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Business(error) => write!(formatter, "{}：{}", error.code, error.message),
            Self::LoopBreak => write!(formatter, "break 跳出循环"),
            Self::LoopContinue => write!(formatter, "continue 进入下次迭代"),
            Self::StepReturn(value) => write!(formatter, "提前返回：{value}"),
        }
    }
}

fn non_zero_u32(value: &u32) -> bool {
    *value != 0
}

fn non_zero_u64(value: &u64) -> bool {
    *value != 0
}
