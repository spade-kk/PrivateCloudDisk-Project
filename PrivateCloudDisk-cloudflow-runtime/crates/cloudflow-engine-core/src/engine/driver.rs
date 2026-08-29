//! 统一调度驱动（双执行面共用的唯一节点执行实现，需求 1.11-1.25）。
//!
//! `execute(ir, ctx, deps)` 完成：IR 加载（契约校验按配置）→ 检查点恢复 →
//! 主循环（全局超时/控制轮询/完成判定/就绪计算/批执行）→ 节点分发
//! （condition/try/loop/switch/parallel/assert/validate/notify/wait/delay/
//! return/break/continue/动作节点）→ 输出求值。
//!
//! 生产执行面与开发调试执行面**共用本实现**，仅通过 `EngineDeps` 注入的
//! 具体实现区分行为（需求 1.17/3）：
//! - 顶层批并发：生产 `runtime.maxParallel` + `JoinSet`；调试恒 1（确定性串行）；
//! - 顶层控制展开：生产 `Deferred`（顶层 condition 选中分支由主循环调度；
//!   顶层 switch/parallel 仅写控制检查点——历史行为）；调试 `Inline`（内联展开）；
//! - 时间：生产真实时钟；调试虚拟时钟（模拟动作延迟记账、`honor_delays`）；
//! - 动作：生产 Capability Agent（gRPC 唯一能力出口）；调试 Mock/测试 Agent。
//!
//! 控制流语义（条件/分支/try/循环/重试/退避/并行/子树展开/控制信号）唯一
//! 收敛于 `crate::execution_core` 与本驱动，宿主 crate 不得重复定义。

use crate::engine::context::{ExecutionContext, StepContext, StepRef};
use crate::engine::deps::{EngineDeps, ExpandMode, LogEntry, LogLevel};
use crate::engine::error::ExecutionError;
use crate::engine::result::{
    ErrorRecord, ExecutionResult, NodeError, NodeFinish, NodeStatus, TerminalKind,
    WorkflowEndStatus,
};
use crate::execution_core::{
    backoff_delay_ms, condition_branches, condition_outcome_with, descendants_for_children,
    parallel_max_concurrency, parse_loop_plan, parse_try_structure, resolve_timeout,
    retry_max_attempts, retry_strategy,
};
use crate::expression::evaluator::{evaluate_value, truthy};
use crate::ir::{NodeIr, WorkflowIrV1};
use crate::runtime::RuntimeEngine;
use serde_json::{Map, Value};
use std::collections::HashSet;
use std::future::Future;
use std::pin::Pin;
use std::sync::Arc;
use std::time::Duration;
use tokio::task::JoinSet;

/// 节点执行结果（驱动内部；与历史双执行面 `NodeOutcome` 同构并携带输出载荷）。
#[derive(Debug, Clone)]
pub(crate) enum NodeOutcome {
    /// 完成；`output` 为节点输出载荷（控制节点载荷/动作输出；无载荷为 None），
    /// `attempts` 为动作节点实际尝试次数（非动作节点 0）。
    Completed {
        output: Option<Value>,
        attempts: u32,
    },
    /// 完成且携带应跳过的未选中分支子孙（由调度层标记）。
    CompletedWithSkips(Vec<String>),
    /// 顶层 wait 挂起（等待载荷已在 `execute_node` 的 wait 分支经
    /// `finish_step_success` + `on_waiting` 落盘，调度层无需再传递）。
    Waiting,
}

/// 批执行终结事件（主循环据此收尾）。
#[derive(Debug, Clone, PartialEq)]
enum BatchTerminal {
    None,
    Waiting,
    /// 断点/单步；`node` 为断点节点（单步为 None）。
    Breakpoint {
        node: Option<String>,
    },
    StepReturn,
    Failed,
}

/// 双执行面共享的只读执行资源（IR + 依赖）。
#[derive(Clone)]
pub(crate) struct Shared {
    ir: Arc<WorkflowIrV1>,
    deps: EngineDeps<'static>,
}

/// 统一驱动（`engine` 簿记为主线程私有；节点执行经 `Shared` 共享）。
pub(crate) struct Driver {
    shared: Shared,
    ctx: ExecutionContext,
    engine: RuntimeEngine,
}

/// 统一执行入口（需求 1.2）：`execute(ir, context, deps) -> ExecutionResult`。
///
/// 语义终结（成功/失败/取消/挂起/超时/断点）在内部经
/// `StateStore::finish_workflow` 落盘（生产）或记录（调试）；本函数仅在
/// 状态 I/O 失败或内部不变量破坏时返回 `Err`。
pub async fn execute(
    ir: &WorkflowIrV1,
    ctx: ExecutionContext,
    deps: EngineDeps<'static>,
) -> Result<ExecutionResult, ExecutionError> {
    let mut driver = Driver::new(Arc::new(ir.clone()), ctx, deps)?;
    driver.run().await
}

/// 同步执行入口（需求 1.18：调试面同步接口）。
///
/// 无运行时上下文（CLI 主线程、普通测试）使用全局单线程运行时 `block_on`；
/// 已在运行时上下文（如 `#[tokio::test]` 内直接调用）改用独立线程 + 独立
/// 运行时，避免嵌套运行时 panic。
pub fn execute_sync(
    ir: &WorkflowIrV1,
    ctx: ExecutionContext,
    deps: EngineDeps<'static>,
) -> Result<ExecutionResult, ExecutionError> {
    let ir = ir.clone();
    let fut = async move {
        let mut driver = Driver::new(Arc::new(ir), ctx, deps)?;
        driver.run().await
    };
    if tokio::runtime::Handle::try_current().is_ok() {
        let (tx, rx) = std::sync::mpsc::channel();
        std::thread::spawn(move || {
            let runtime = tokio::runtime::Builder::new_current_thread()
                .enable_time()
                .build()
                .expect("调试同步运行时构建失败");
            let _ = tx.send(runtime.block_on(fut));
        })
        .join()
        .expect("调试同步执行线程异常");
        rx.recv().expect("调试同步执行结果通道异常")
    } else {
        use std::sync::OnceLock;
        static RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();
        let runtime = RUNTIME.get_or_init(|| {
            tokio::runtime::Builder::new_current_thread()
                .enable_time()
                .build()
                .expect("调试同步运行时构建失败")
        });
        runtime.block_on(fut)
    }
}

/// 标量友好的日志展示（字符串去 JSON 引号，其余按紧凑 JSON 截断 200 字符）。
pub(crate) fn display_scalar(value: &Value) -> String {
    match value {
        Value::String(text) => text.clone(),
        other => truncate_json(other),
    }
}

/// 紧凑 JSON 截断（200 字符，按字符边界回退）。
pub(crate) fn truncate_json(value: &Value) -> String {
    let text = value.to_string();
    if text.len() <= 200 {
        return text;
    }
    let mut end = 200;
    while !text.is_char_boundary(end) {
        end -= 1;
    }
    format!("{}…", &text[..end])
}

impl Driver {
    fn new(
        ir: Arc<WorkflowIrV1>,
        ctx: ExecutionContext,
        deps: EngineDeps<'static>,
    ) -> Result<Self, ExecutionError> {
        let shared = Shared {
            ir: ir.clone(),
            deps,
        };
        let engine = if shared.deps.config.skip_validation() {
            RuntimeEngine::load_unvalidated(ctx.execution_id.clone(), (*ir).clone())
        } else {
            RuntimeEngine::load(ctx.execution_id.clone(), (*ir).clone())
                .map_err(|errors| ExecutionError::Ir(errors.join("; ")))?
        };
        Ok(Self {
            shared,
            ctx,
            engine,
        })
    }

    fn ir(&self) -> &WorkflowIrV1 {
        &self.shared.ir
    }

    fn log(&self, level: LogLevel, node: Option<String>, message: String) {
        self.shared
            .deps
            .log
            .log(LogEntry::new(level, node, message));
    }

    /// 主循环（需求 1.12/5.x/9.x）：超时 → 完成 → 控制轮询 → 就绪 → 批执行。
    async fn run(&mut self) -> Result<ExecutionResult, ExecutionError> {
        let shared = self.shared.clone();
        let mode = shared.deps.config.expand_mode();
        let started_ms = shared.deps.clock.now_ms();

        // 检查点恢复（生产：已完成步骤；调试：空）。
        let completed = shared.deps.state.restore_completed(&self.ctx).await?;
        self.engine.restore_completed(completed);

        let mut errors: Vec<ErrorRecord> = Vec::new();
        // 主循环每条 break 路径均在跳出前显式赋值（超时/取消/挂起/成功/失败/断点）；
        // 每条路径至多一次写入，故无需 `mut`。
        let status: WorkflowEndStatus;

        loop {
            // 全局执行超时（调试面 `overall_timeout_ms`；生产 None）。
            if let Some(limit) = shared.deps.config.overall_timeout_ms() {
                if shared.deps.clock.now_ms() >= limit {
                    errors.push(ErrorRecord {
                        kind: TerminalKind::Timeout,
                        node: None,
                        error: None,
                    });
                    let skipped = shared.deps.state.skip_pending(
                        &self.ctx,
                        &TerminalKind::Timeout,
                        &HashSet::new(),
                    );
                    if mode == ExpandMode::Inline {
                        self.log_skip_pending(&skipped, "CFD-8104", "全局执行超时")
                            .await;
                    }
                    shared
                        .deps
                        .state
                        .finish_workflow(
                            &self.ctx,
                            WorkflowEndStatus::Timeout,
                            Some("CFD-8104"),
                            "全局执行超时",
                        )
                        .await?;
                    status = WorkflowEndStatus::Timeout;
                    break;
                }
            }
            // 执行控制轮询先于完成判定（与历史生产主循环顺序逐字一致：
            // 取消/暂停请求优先于“已完成”收尾）。
            let flags = shared.deps.state.poll_control(&self.ctx).await?;
            if flags.cancelled {
                shared
                    .deps
                    .state
                    .finish_workflow(
                        &self.ctx,
                        WorkflowEndStatus::Cancelled,
                        Some("CF-CANCELLED"),
                        "用户已取消执行",
                    )
                    .await?;
                status = WorkflowEndStatus::Cancelled;
                break;
            }
            if flags.paused {
                // 生产：worker 退出，执行行保持 WAITING（等待恢复/审批接口）。
                status = WorkflowEndStatus::Waiting;
                break;
            }
            if self.engine.is_complete() {
                shared
                    .deps
                    .state
                    .finish_workflow(
                        &self.ctx,
                        WorkflowEndStatus::Success,
                        None,
                        "工作流执行完成",
                    )
                    .await?;
                status = WorkflowEndStatus::Success;
                break;
            }
            // 条件依赖：context 先于就绪计算构建；求值 false 时该节点依赖被豁免。
            let context = shared.deps.state.build_context(&self.ctx).await?;
            let holds = |node: &NodeIr| -> Option<bool> {
                let condition = node.depends_condition.as_ref()?;
                match eval_value(&shared, condition, &context) {
                    Ok(value) => Some(truthy(&value)),
                    Err(_) => None,
                }
            };
            let ready = self.engine.ready_nodes_conditional(&holds);
            if ready.is_empty() {
                errors.push(ErrorRecord {
                    kind: TerminalKind::Failed,
                    node: None,
                    error: None,
                });
                shared
                    .deps
                    .state
                    .finish_workflow(
                        &self.ctx,
                        WorkflowEndStatus::Failed,
                        Some("CF2002"),
                        "DAG 无可运行节点且尚未完成",
                    )
                    .await?;
                status = WorkflowEndStatus::Failed;
                break;
            }
            let batch_size = shared.deps.config.top_level_batch_size(self.ir());
            let batch: Vec<String> = ready.into_iter().take(batch_size).collect();
            let terminal = if batch_size > 1 {
                // 并发批（生产语义）：JoinSet 全量领取、按完成序处理；
                // 首个终结事件即结束本轮（在途任务随 JoinSet 取消，与历史一致）。
                self.run_batch_concurrent(&batch, &context, &mut errors)
                    .await?
            } else {
                // 确定性串行批（调试语义）。
                self.run_batch_serial(&batch, &context, &mut errors).await?
            };
            match terminal {
                BatchTerminal::None => continue,
                BatchTerminal::Waiting => {
                    if mode == ExpandMode::Inline {
                        let skipped = shared.deps.state.skip_pending(
                            &self.ctx,
                            &TerminalKind::Waiting,
                            &HashSet::new(),
                        );
                        self.log_skip_pending(
                            &skipped,
                            "CFD-8105",
                            "工作流进入 WAITING（审批/等待节点）",
                        )
                        .await;
                    }
                    errors.push(ErrorRecord {
                        kind: TerminalKind::Waiting,
                        node: None,
                        error: None,
                    });
                    status = WorkflowEndStatus::Waiting;
                    break;
                }
                BatchTerminal::Breakpoint { node } => {
                    errors.push(ErrorRecord {
                        kind: TerminalKind::Breakpoint {
                            paused_at: node.clone(),
                        },
                        node,
                        error: None,
                    });
                    status = WorkflowEndStatus::Breakpoint;
                    break;
                }
                BatchTerminal::StepReturn => {
                    status = WorkflowEndStatus::Success;
                    break;
                }
                BatchTerminal::Failed => {
                    status = WorkflowEndStatus::Failed;
                    break;
                }
            }
        }

        // 工作流输出（spec.outputs 按终结上下文求值；失败置 Null——调试面历史行为）。
        let context = shared.deps.state.build_context(&self.ctx).await?;
        let outputs = self.evaluate_outputs(&context);
        Ok(ExecutionResult {
            status,
            errors,
            duration_ms: shared.deps.clock.now_ms().saturating_sub(started_ms),
            outputs,
        })
    }

    /// 未终结节点跳过日志（调试面：逐节点 Debug 级 `{code}：{message}`）。
    async fn log_skip_pending(&self, skipped: &[String], code: &str, message: &str) {
        for node_id in skipped {
            self.log(
                LogLevel::Debug,
                Some(node_id.clone()),
                format!("{code}：{message}"),
            );
        }
    }

    /// 确定性串行批（调试面）：按声明顺序逐个执行，首个失败/挂起/断点即停。
    async fn run_batch_serial(
        &mut self,
        batch: &[String],
        context: &Value,
        errors: &mut Vec<ErrorRecord>,
    ) -> Result<BatchTerminal, ExecutionError> {
        let shared = self.shared.clone();
        for node_id in batch {
            let node = self
                .engine
                .node(node_id)
                .cloned()
                .ok_or_else(|| ExecutionError::Ir(format!("节点不存在：{node_id}")))?;

            // skip_nodes（调试面）：直接标记跳过，输出置 Null 使下游引用可解析。
            if shared.deps.config.skip_node(node_id) {
                self.engine.mark_skipped(node_id);
                let step = StepRef::top_level(node_id);
                shared.deps.state.record_null_step_output(&self.ctx, &step);
                shared.deps.state.node_finished(
                    &self.ctx,
                    &step,
                    &NodeFinish {
                        status: NodeStatus::Skipped,
                        input: None,
                        output: None,
                        attempts: 0,
                        error: None,
                        started_at_ms: 0,
                        duration_ms: 0,
                    },
                );
                self.log(
                    LogLevel::Info,
                    Some(node_id.clone()),
                    "skip_nodes 配置：节点直接跳过".into(),
                );
                continue;
            }
            // 断点（调试面）：执行到该节点前暂停。
            if let Some(kind) = shared.deps.config.before_node(node_id) {
                self.log(
                    LogLevel::Info,
                    Some(node_id.clone()),
                    "到达断点，暂停执行并返回上下文快照".into(),
                );
                let exclude = HashSet::from([node_id.clone()]);
                let skipped = shared.deps.state.skip_pending(&self.ctx, &kind, &exclude);
                let (code, message) = match &kind {
                    TerminalKind::Breakpoint { .. } => ("CFD-8106", "到达断点/单步边界"),
                    other => unreachable!("断点钩子只返回 Breakpoint：{other:?}"),
                };
                self.log_skip_pending(&skipped, code, message).await;
                return Ok(BatchTerminal::Breakpoint {
                    node: Some(node_id.clone()),
                });
            }

            self.engine.mark_running(node_id);
            let step = StepRef::top_level(node_id);
            let input = node.action.as_ref().map(|_| Value::Null);
            shared.deps.state.node_started(
                &self.ctx,
                &step,
                input.as_ref().unwrap_or(&Value::Null),
            );
            self.log(
                LogLevel::Info,
                Some(node_id.clone()),
                format!("开始执行 [{}]", node.node_type),
            );
            let started = shared.deps.clock.now_ms();
            let outcome = execute_node(&shared, &self.ctx, &node, context, &step, true).await;
            let finished = shared.deps.clock.now_ms();

            match outcome {
                Ok(NodeOutcome::Completed { output, attempts }) => {
                    self.engine.mark_success(node_id);
                    shared.deps.state.node_finished(
                        &self.ctx,
                        &step,
                        &NodeFinish {
                            status: NodeStatus::Success,
                            input: None,
                            output,
                            attempts,
                            error: None,
                            started_at_ms: started,
                            duration_ms: finished - started,
                        },
                    );
                    self.log(
                        LogLevel::Info,
                        Some(node_id.clone()),
                        format!("执行成功（{}ms）", finished - started),
                    );
                    if let Some(kind) = shared.deps.config.after_node(node_id) {
                        self.log(
                            LogLevel::Info,
                            Some(node_id.clone()),
                            "单步模式：节点完成后暂停".into(),
                        );
                        let skipped =
                            shared
                                .deps
                                .state
                                .skip_pending(&self.ctx, &kind, &HashSet::new());
                        let (code, message) = ("CFD-8106", "到达断点/单步边界");
                        self.log_skip_pending(&skipped, code, message).await;
                        return Ok(BatchTerminal::Breakpoint { node: None });
                    }
                }
                Ok(NodeOutcome::CompletedWithSkips(skipped)) => {
                    for skipped_id in &skipped {
                        if skipped_id != node_id && self.engine.mark_skipped(skipped_id) {
                            shared
                                .deps
                                .state
                                .finish_step_skipped(
                                    &self.ctx,
                                    &StepRef::top_level(skipped_id),
                                    "未选中的控制流分支",
                                )
                                .await?;
                        }
                    }
                    self.engine.mark_success(node_id);
                    shared.deps.state.node_finished(
                        &self.ctx,
                        &step,
                        &NodeFinish {
                            status: NodeStatus::Success,
                            input: None,
                            output: None,
                            attempts: 0,
                            error: None,
                            started_at_ms: started,
                            duration_ms: finished - started,
                        },
                    );
                    self.log(
                        LogLevel::Info,
                        Some(node_id.clone()),
                        format!(
                            "执行成功，跳过分支节点 {} 个（{}ms）",
                            skipped.len(),
                            finished - started
                        ),
                    );
                    if let Some(kind) = shared.deps.config.after_node(node_id) {
                        self.log(
                            LogLevel::Info,
                            Some(node_id.clone()),
                            "单步模式：节点完成后暂停".into(),
                        );
                        let skipped =
                            shared
                                .deps
                                .state
                                .skip_pending(&self.ctx, &kind, &HashSet::new());
                        let (code, message) = ("CFD-8106", "到达断点/单步边界");
                        self.log_skip_pending(&skipped, code, message).await;
                        return Ok(BatchTerminal::Breakpoint { node: None });
                    }
                }
                Ok(NodeOutcome::Waiting) => {
                    self.engine.mark_success(node_id);
                    shared.deps.state.node_finished(
                        &self.ctx,
                        &step,
                        &NodeFinish {
                            status: NodeStatus::Waiting,
                            input: None,
                            output: None,
                            attempts: 0,
                            error: None,
                            started_at_ms: started,
                            duration_ms: finished - started,
                        },
                    );
                    self.log(
                        LogLevel::Warn,
                        Some(node_id.clone()),
                        "进入 WAITING（审批/等待），调试入口立即返回".into(),
                    );
                    return Ok(BatchTerminal::Waiting);
                }
                Err(error @ ExecutionError::StepReturn(_)) => {
                    // 提前返回：顶层祖先记 Success，剩余节点跳过，工作流成功结束。
                    if shared.deps.config.expand_mode() != ExpandMode::Inline {
                        // 生产面：提前返回视为成功结束并携带返回输出（历史主循环逐字）。
                        let value = match &error {
                            ExecutionError::StepReturn(value) => value,
                            _ => unreachable!(),
                        };
                        shared
                            .deps
                            .state
                            .finish_workflow(
                                &self.ctx,
                                WorkflowEndStatus::Success,
                                Some("CF4417"),
                                &format!("提前返回：{value}"),
                            )
                            .await?;
                    }
                    self.engine.mark_success(node_id);
                    shared.deps.state.node_finished(
                        &self.ctx,
                        &step,
                        &NodeFinish {
                            status: NodeStatus::Success,
                            input: None,
                            output: None,
                            attempts: 0,
                            error: None,
                            started_at_ms: started,
                            duration_ms: finished - started,
                        },
                    );
                    let skipped = shared.deps.state.skip_pending(
                        &self.ctx,
                        &TerminalKind::StepReturn,
                        &HashSet::new(),
                    );
                    self.log_skip_pending(&skipped, "CFD-8107", "提前 return：剩余节点跳过")
                        .await;
                    return Ok(BatchTerminal::StepReturn);
                }
                Err(error) if error.is_control_signal() => {
                    // 调试面历史行为：循环控制信号泄漏到顶层 = 入口级错误。
                    return Err(ExecutionError::Internal(format!(
                        "循环控制信号泄漏到顶层：{node_id}"
                    )));
                }
                Err(error) => {
                    let inline = shared.deps.config.expand_mode() == ExpandMode::Inline;
                    if inline {
                        self.log(
                            LogLevel::Error,
                            Some(node_id.clone()),
                            format!(
                                "执行失败（{}ms）：{:?}",
                                finished - started,
                                dev_debug_repr(&error)
                            ),
                        );
                    }
                    shared.deps.state.node_finished(
                        &self.ctx,
                        &step,
                        &NodeFinish {
                            status: NodeStatus::Failed,
                            input: None,
                            output: None,
                            attempts: 0,
                            error: None,
                            started_at_ms: started,
                            duration_ms: finished - started,
                        },
                    );
                    errors.push(ErrorRecord {
                        kind: TerminalKind::Failed,
                        node: Some(node_id.clone()),
                        error: Some(error.clone()),
                    });
                    // 步骤级 on_error 钩子（不改变失败结果）。
                    run_on_error(&shared, &self.ctx, &node, context).await;
                    if inline {
                        let skipped = shared.deps.state.skip_pending(
                            &self.ctx,
                            &TerminalKind::Failed,
                            &HashSet::new(),
                        );
                        self.log_skip_pending(&skipped, "CFD-8108", "工作流失败：剩余节点跳过")
                            .await;
                        shared
                            .deps
                            .state
                            .finish_workflow(
                                &self.ctx,
                                WorkflowEndStatus::Failed,
                                Some(error.dev_code().as_str()),
                                &error.dev_message(),
                            )
                            .await?;
                    } else {
                        // 生产面：与历史主循环 Err 臂逐字一致（错误码 + Display 文案）。
                        shared
                            .deps
                            .state
                            .finish_workflow(
                                &self.ctx,
                                WorkflowEndStatus::Failed,
                                Some(error.production_code().as_str()),
                                &error.production_display(),
                            )
                            .await?;
                    }
                    return Ok(BatchTerminal::Failed);
                }
            }
        }
        Ok(BatchTerminal::None)
    }

    /// 并发批（生产语义）：全量 spawn、按完成序 join、首个终结事件收尾。
    async fn run_batch_concurrent(
        &mut self,
        batch: &[String],
        context: &Value,
        errors: &mut Vec<ErrorRecord>,
    ) -> Result<BatchTerminal, ExecutionError> {
        let shared = self.shared.clone();
        let mut tasks: JoinSet<(String, Result<NodeOutcome, ExecutionError>)> = JoinSet::new();
        for node_id in batch {
            let node = self
                .engine
                .node(node_id)
                .cloned()
                .ok_or_else(|| ExecutionError::Ir(format!("节点不存在：{node_id}")))?;
            self.engine.mark_running(node_id);
            let shared = shared.clone();
            let ctx = self.ctx.clone();
            let context = context.clone();
            tasks.spawn(async move {
                let outcome = execute_node(
                    &shared,
                    &ctx,
                    &node,
                    &context,
                    &StepRef::top_level(&node.id),
                    true,
                )
                .await;
                (node.id, outcome)
            });
        }
        while let Some(joined) = tasks.join_next().await {
            let (node_id, result) =
                joined.map_err(|error| ExecutionError::Ir(format!("节点任务异常退出：{error}")))?;
            match result {
                Ok(NodeOutcome::Completed { .. }) => {
                    self.engine.mark_success(&node_id);
                }
                Ok(NodeOutcome::CompletedWithSkips(skipped)) => {
                    self.engine.mark_success(&node_id);
                    for skipped_id in &skipped {
                        if skipped_id != &node_id && self.engine.mark_skipped(skipped_id) {
                            shared
                                .deps
                                .state
                                .finish_step_skipped(
                                    &self.ctx,
                                    &StepRef::top_level(skipped_id),
                                    "未选中的控制流分支",
                                )
                                .await?;
                        }
                    }
                }
                Ok(NodeOutcome::Waiting) => {
                    // 与历史一致：立即退出本轮（在途任务随 JoinSet 取消）。
                    self.engine.mark_success(&node_id);
                    return Ok(BatchTerminal::Waiting);
                }
                Err(ExecutionError::StepReturn(value)) => {
                    shared
                        .deps
                        .state
                        .finish_workflow(
                            &self.ctx,
                            WorkflowEndStatus::Success,
                            Some("CF4417"),
                            &format!("提前返回：{value}"),
                        )
                        .await?;
                    return Ok(BatchTerminal::StepReturn);
                }
                Err(error) => {
                    // 步骤级 on_error 钩子（控制信号不触发）。
                    if !error.is_control_signal() {
                        if let Some(node) = self.engine.node(&node_id).cloned() {
                            run_on_error(&shared, &self.ctx, &node, context).await;
                        }
                    }
                    // 全局失败处理器（生产：IR extensions.handlers；调试为空）。
                    run_failure_handlers(&shared, &self.ctx).await;
                    shared
                        .deps
                        .state
                        .finish_workflow(
                            &self.ctx,
                            WorkflowEndStatus::Failed,
                            Some(error.production_code().as_str()),
                            &error.production_display(),
                        )
                        .await?;
                    let _ = errors;
                    return Ok(BatchTerminal::Failed);
                }
            }
        }
        Ok(BatchTerminal::None)
    }

    /// 工作流输出求值（spec.outputs；失败置 Null）。
    fn evaluate_outputs(&self, context: &Value) -> Value {
        let shared = self.shared.clone();
        let mut outputs = Map::new();
        for (name, expression) in &self.ir().spec.outputs {
            match eval_value(&shared, expression, context) {
                Ok(value) => {
                    outputs.insert(name.clone(), value);
                }
                Err(_) => {
                    outputs.insert(name.clone(), Value::Null);
                }
            }
        }
        Value::Object(outputs)
    }
}

/// 调试面日志使用的错误 Debug 形态（与历史 `DevExecError` Debug 输出对齐）。
fn dev_debug_repr(error: &ExecutionError) -> String {
    match error {
        ExecutionError::Action {
            code,
            message,
            retryable,
        } => format!(
            "Business(DevError {{ code: \"{code}\", message: \"{message}\", node_id: None, detail: Some(\"retryable={retryable}\") }})"
        ),
        _ => {
            let code = error.dev_code();
            let message = error.dev_message();
            format!(
                "Business(DevError {{ code: \"{code}\", message: \"{message}\", node_id: None, detail: None }})"
            )
        }
    }
}

/// 顶层/动态统一的节点分发（双执行面唯一实现）。
///
/// `engine_log` 保留为参数占位（日志经 `StateStore`/`LogSink` 统一出口，
/// 不直接依赖引擎）。
pub(crate) async fn execute_node(
    shared: &Shared,
    ctx: &ExecutionContext,
    node: &NodeIr,
    context: &Value,
    step: &StepRef,
    top_level: bool,
) -> Result<NodeOutcome, ExecutionError> {
    let deps = &shared.deps;
    let ir = &shared.ir;
    let state = &deps.state;
    let config = &deps.config;
    let mode = config.expand_mode();
    let inline = mode == ExpandMode::Inline;
    let dynamic = !top_level;

    // ---------- condition ----------
    if node.node_type == "condition" {
        let selected =
            condition_outcome_with(node, context, |expr, ctx| eval_value(shared, expr, ctx))?;
        let (active_roots, skipped_roots) =
            condition_branches(node.error_handler.as_ref(), selected);
        let skipped = descendants_for_children(ir, &skipped_roots);
        if dynamic {
            // 动态体内（两执行面一致）：内联执行选中分支；调试面额外标记未选中分支。
            execute_dynamic_roots(
                shared,
                ctx,
                &active_roots,
                context,
                Some(step.instance_id.clone()),
            )
            .await?;
            if inline {
                for skipped_id in &skipped {
                    state
                        .finish_step_skipped(
                            ctx,
                            &StepRef::top_level(skipped_id),
                            "未选中的控制流分支",
                        )
                        .await?;
                }
                let _ = log_entry(
                    shared,
                    LogLevel::Info,
                    Some(node.id.clone()),
                    format!("condition={selected}：执行分支 {active_roots:?}，跳过 {skipped:?}"),
                )
                .await;
            }
            return Ok(NodeOutcome::Completed {
                output: None,
                attempts: 0,
            });
        }
        if inline {
            // 调试面顶层：记录输出 + 内联执行选中分支 + 跳过未选中分支。
            state
                .finish_step_success(ctx, step, 1, &serde_json::json!({"condition": selected}), 0)
                .await?;
            execute_dynamic_roots(
                shared,
                ctx,
                &active_roots,
                context,
                Some(step.instance_id.clone()),
            )
            .await?;
            let _ = log_entry(
                shared,
                LogLevel::Info,
                Some(node.id.clone()),
                format!("condition={selected}：执行分支 {active_roots:?}，跳过 {skipped:?}"),
            )
            .await;
            // 调试面内联展开后，选中分支根已实际执行：全部分支子孙在 DAG 引擎
            // 终结（已执行者保留 Success 记录——`finish_step_skipped` 不覆盖
            // Success/Failed），下游依赖方可被调度（与历史调试面行为一致）。
            return Ok(NodeOutcome::CompletedWithSkips(descendants_for_children(
                ir,
                &node.children,
            )));
        }
        // 生产面顶层（Deferred）：评估 + 未选中分支跳过；选中分支由主循环调度。
        let attempt = state.begin_step(ctx, step, &Value::Null).await?;
        state
            .finish_step_success(
                ctx,
                step,
                attempt,
                &serde_json::json!({"condition": selected}),
                0,
            )
            .await?;
        return Ok(NodeOutcome::CompletedWithSkips(skipped));
    }

    // ---------- try ----------
    if node.node_type == "try" {
        let handler = node
            .error_handler
            .as_ref()
            .filter(|value| value.is_object())
            .ok_or_else(|| ExecutionError::MissingConfig("try 缺少 errorHandler".into()))?;
        let structure = parse_try_structure(handler);
        let attempt = if !inline {
            Some(state.begin_step(ctx, step, &Value::Null).await?)
        } else {
            None
        };
        let started = deps.clock.now_ms();
        let try_result = execute_dynamic_roots(
            shared,
            ctx,
            &structure.try_roots,
            context,
            Some(step.instance_id.clone()),
        )
        .await;
        let mut failure = try_result.err();
        let mut caught = false;
        if let Some(error) = failure.as_ref() {
            // 控制信号（break/continue/return）不触发 catch，沿调用栈向上传播。
            if !error.is_control_signal() && !structure.catch_roots.is_empty() {
                let mut catch_context = context.clone();
                let binding = &structure.catch_binding;
                let (code, message) = if inline {
                    (error.dev_code(), error.dev_message())
                } else {
                    (error.production_code(), error.public_message_for(false))
                };
                if let Some(vars) = catch_context.get_mut("vars").and_then(Value::as_object_mut) {
                    vars.insert(
                        binding.clone(),
                        serde_json::json!({ "code": code, "message": message }),
                    );
                }
                if inline {
                    let _ = log_entry(
                        shared,
                        LogLevel::Warn,
                        Some(node.id.clone()),
                        format!(
                            "try 捕获异常（{binding}={{code:{}, message:{}}}），执行 catch 分支",
                            error.dev_code(),
                            error.dev_message()
                        ),
                    )
                    .await;
                }
                match execute_dynamic_roots(
                    shared,
                    ctx,
                    &structure.catch_roots,
                    &catch_context,
                    Some(step.instance_id.clone()),
                )
                .await
                {
                    Ok(()) => {
                        failure = None;
                        caught = true;
                    }
                    Err(catch_error) => failure = Some(catch_error),
                }
            }
        }
        let finally_result = execute_dynamic_roots(
            shared,
            ctx,
            &structure.finally_roots,
            context,
            Some(step.instance_id.clone()),
        )
        .await;
        if let Err(error) = finally_result {
            failure = Some(error);
        }
        let duration = deps.clock.now_ms() - started;
        let payload = serde_json::json!({"try": "success", "caught": caught});
        match failure {
            None => {
                if let Some(attempt) = attempt {
                    state
                        .finish_step_success(ctx, step, attempt, &payload, duration)
                        .await?;
                } else {
                    state
                        .finish_step_success(ctx, step, 1, &payload, duration)
                        .await?;
                }
                return Ok(NodeOutcome::CompletedWithSkips(descendants_for_children(
                    ir,
                    &node.children,
                )));
            }
            Some(error) if !error.is_control_signal() => {
                if let Some(attempt) = attempt {
                    state
                        .finish_step_failure(
                            ctx,
                            step,
                            attempt,
                            &error.production_code(),
                            &error.public_message_for(false),
                            false,
                            duration,
                        )
                        .await?;
                } else {
                    // 调试面：与历史一致——失败时也把 try 载荷记为节点输出，
                    // 节点 Failed 状态由调度层 node_finished 记录。
                    state
                        .finish_step_success(ctx, step, 1, &payload, duration)
                        .await?;
                }
                return Err(error);
            }
            Some(signal) => return Err(signal.clone()),
        }
    }

    // ---------- loop ----------
    if node.node_type == "loop" {
        return execute_loop_node(shared, ctx, node, context, step, inline).await;
    }

    // ---------- switch ----------
    if node.node_type == "switch" {
        let config = node
            .switch_config
            .as_ref()
            .and_then(Value::as_object)
            .ok_or_else(|| ExecutionError::MissingConfig("switch 缺少 switchConfig".into()))?;
        let value = config
            .get("subject")
            .map(|subject| eval_value(shared, subject, context))
            .transpose()?
            .unwrap_or(Value::Null);
        let mut branch: Vec<String> = Vec::new();
        if let Some(cases) = config.get("cases").and_then(Value::as_array) {
            for case in cases {
                let Some(matched) = case.as_object() else {
                    continue;
                };
                if matched
                    .get("value")
                    .is_some_and(|candidate| candidate == &value)
                {
                    if let Some(body) = matched.get("body").and_then(Value::as_array) {
                        branch = body
                            .iter()
                            .filter_map(Value::as_str)
                            .map(str::to_owned)
                            .collect();
                    }
                    break;
                }
            }
        }
        if branch.is_empty() {
            if let Some(default_branch) = config.get("default").and_then(Value::as_array) {
                branch = default_branch
                    .iter()
                    .filter_map(Value::as_str)
                    .map(str::to_owned)
                    .collect();
            }
        }
        if inline {
            let _ = log_entry(
                shared,
                LogLevel::Info,
                Some(node.id.clone()),
                format!("switch subject={} → 分支 {branch:?}", value),
            )
            .await;
        }
        if dynamic {
            execute_dynamic_roots(
                shared,
                ctx,
                &branch,
                context,
                Some(step.instance_id.clone()),
            )
            .await?;
            if inline {
                let skipped: Vec<String> = node
                    .children
                    .iter()
                    .filter(|child| !branch.contains(child))
                    .flat_map(|child| crate::execution_core::descendants(ir, child))
                    .collect();
                for skipped_id in &skipped {
                    state
                        .finish_step_skipped(
                            ctx,
                            &StepRef::top_level(skipped_id),
                            "未选中的控制流分支",
                        )
                        .await?;
                }
            }
            return Ok(NodeOutcome::Completed {
                output: None,
                attempts: 0,
            });
        }
        if !inline {
            // 生产面顶层历史行为：仅写控制检查点（switch 体不展开）。
            let attempt = state.begin_step(ctx, step, &Value::Null).await?;
            state
                .finish_step_success(
                    ctx,
                    step,
                    attempt,
                    &serde_json::json!({"control": "switch"}),
                    0,
                )
                .await?;
            return Ok(NodeOutcome::Completed {
                output: None,
                attempts: 0,
            });
        }
        // 调试面顶层：内联执行选中分支 + 标记未选中子节点。
        execute_dynamic_roots(
            shared,
            ctx,
            &branch,
            context,
            Some(step.instance_id.clone()),
        )
        .await?;
        let skipped: Vec<String> = node
            .children
            .iter()
            .filter(|child| !branch.contains(child))
            .flat_map(|child| crate::execution_core::descendants(ir, child))
            .collect();
        for skipped_id in &skipped {
            state
                .finish_step_skipped(ctx, &StepRef::top_level(skipped_id), "未选中的控制流分支")
                .await?;
        }
        // 与 condition/try/loop 一致：switch 子节点带 `controlParent`，永不由
        // 主循环直接调度；内联执行结束后全部分支子孙在 DAG 引擎终结，
        // 否则下游依赖（如 cond 依赖三个分支根）将永久不可调度（死锁）。
        return Ok(NodeOutcome::CompletedWithSkips(descendants_for_children(
            ir,
            &node.children,
        )));
    }

    // ---------- parallel ----------
    if node.node_type == "parallel" {
        let parallel = parallel_max_concurrency(node, &ir.runtime);
        if inline {
            let _ = log_entry(
                shared,
                LogLevel::Info,
                Some(node.id.clone()),
                format!(
                    "parallel：{} 个分支，maxConcurrency={parallel}",
                    node.children.len()
                ),
            )
            .await;
        }
        if !dynamic && !inline {
            // 生产面顶层历史行为：仅写控制检查点（分支不展开）。
            let attempt = state.begin_step(ctx, step, &Value::Null).await?;
            state
                .finish_step_success(
                    ctx,
                    step,
                    attempt,
                    &serde_json::json!({"control": "parallel"}),
                    0,
                )
                .await?;
            return Ok(NodeOutcome::Completed {
                output: None,
                attempts: 0,
            });
        }
        for batch in node.children.chunks(parallel) {
            if !dynamic && !inline {
                continue;
            }
            if dynamic && !inline {
                // 生产面动态 parallel：分批 JoinSet（分支级 maxConcurrency 优先）。
                let mut tasks: JoinSet<Result<(), ExecutionError>> = JoinSet::new();
                for child in batch {
                    let shared = shared.clone();
                    let ctx = ctx.clone();
                    let context = context.clone();
                    let child = child.clone();
                    let prefix = Some(step.instance_id.clone());
                    tasks.spawn(async move {
                        execute_dynamic_roots(&shared, &ctx, &[child], &context, prefix).await
                    });
                }
                while let Some(result) = tasks.join_next().await {
                    let task_result: Result<(), ExecutionError> = result.map_err(|error| {
                        ExecutionError::Ir(format!("parallel 子任务异常退出：{error}"))
                    })?;
                    match task_result {
                        Ok(()) => {}
                        Err(
                            signal @ (ExecutionError::LoopBreak | ExecutionError::LoopContinue),
                        ) => {
                            return Err(signal);
                        }
                        Err(other) => return Err(other),
                    }
                }
            } else {
                // 调试面：同步按声明顺序串行执行各分支（确定性）。
                for child in batch {
                    execute_dynamic_roots(
                        shared,
                        ctx,
                        &[child.clone()],
                        context,
                        Some(step.instance_id.clone()),
                    )
                    .await?;
                }
            }
        }
        if !dynamic && inline {
            // 调试面顶层：parallel 子节点带 `controlParent`，主循环不会调度它们；
            // 内联执行结束后在 DAG 引擎终结全部分支子孙（与 switch/condition 一致）。
            return Ok(NodeOutcome::CompletedWithSkips(descendants_for_children(
                ir,
                &node.children,
            )));
        }
        return Ok(NodeOutcome::Completed {
            output: None,
            attempts: 0,
        });
    }

    // ---------- assert ----------
    if node.node_type == "assert" {
        let passed = self_condition_outcome(shared, node, context)?;
        if !passed {
            if !inline {
                let attempt = state.begin_step(ctx, step, &Value::Null).await?;
                state
                    .finish_step_failure(
                        ctx,
                        step,
                        attempt,
                        "CF2202",
                        "CloudFlow assert 条件不成立",
                        false,
                        0,
                    )
                    .await?;
            } else {
                state
                    .finish_step_success(ctx, step, 1, &serde_json::json!({"assert": false}), 0)
                    .await?;
            }
            record_dev_failure(shared, ctx, step, &ExecutionError::AssertFailed).await;
            return Err(ExecutionError::AssertFailed);
        }
        if !inline {
            let attempt = state.begin_step(ctx, step, &Value::Null).await?;
            state
                .finish_step_success(ctx, step, attempt, &serde_json::json!({"assert": true}), 0)
                .await?;
        } else {
            state
                .finish_step_success(ctx, step, 1, &serde_json::json!({"assert": true}), 0)
                .await?;
        }
        return Ok(NodeOutcome::Completed {
            output: Some(serde_json::json!({"assert": true})),
            attempts: 0,
        });
    }

    // ---------- validate ----------
    if node.node_type == "validate" {
        let passed = self_condition_outcome(shared, node, context)?;
        if !passed {
            if !inline {
                let attempt = state.begin_step(ctx, step, &Value::Null).await?;
                state
                    .finish_step_failure(
                        ctx,
                        step,
                        attempt,
                        "CF4412",
                        "validate 校验未通过",
                        false,
                        0,
                    )
                    .await?;
            } else {
                state
                    .finish_step_success(ctx, step, 1, &serde_json::json!({"validate": false}), 0)
                    .await?;
            }
            record_dev_failure(shared, ctx, step, &ExecutionError::ValidateFailed).await;
            return Err(ExecutionError::ValidateFailed);
        }
        if !inline {
            let attempt = state.begin_step(ctx, step, &Value::Null).await?;
            state
                .finish_step_success(
                    ctx,
                    step,
                    attempt,
                    &serde_json::json!({"validate": true}),
                    0,
                )
                .await?;
        } else {
            state
                .finish_step_success(ctx, step, 1, &serde_json::json!({"validate": true}), 0)
                .await?;
        }
        return Ok(NodeOutcome::Completed {
            output: Some(serde_json::json!({"validate": true})),
            attempts: 0,
        });
    }

    // ---------- notify ----------
    if node.node_type == "notify" {
        let config = node
            .notify_config
            .as_ref()
            .and_then(Value::as_object)
            .cloned()
            .unwrap_or_default();
        let channel = config
            .get("channel")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_string();
        let to = config
            .get("to")
            .map(|value| eval_value(shared, value, context))
            .transpose()?;
        let message = config
            .get("message")
            .map(|value| eval_value(shared, value, context))
            .transpose()?;
        let payload = serde_json::json!({
            "notify": true,
            "channel": channel,
            "to": to,
            "message": message
        });
        if !inline {
            let attempt = state.begin_step(ctx, step, &Value::Null).await?;
            let _ = log_entry(
                shared,
                LogLevel::Info,
                Some(node.id.clone()),
                "notify 触发".into(),
            )
            .await;
            state
                .finish_step_success(ctx, step, attempt, &payload, 0)
                .await?;
        } else {
            state.finish_step_success(ctx, step, 1, &payload, 0).await?;
            let _ = log_entry(
                shared,
                LogLevel::Info,
                Some(node.id.clone()),
                format!("notify 触发（channel={channel}）"),
            )
            .await;
        }
        return Ok(NodeOutcome::Completed {
            output: Some(payload),
            attempts: 0,
        });
    }

    // ---------- wait ----------
    if node.node_type == "wait" {
        if dynamic {
            // 动态执行体内 wait：CF2203（两执行面一致）；调试面把该 wait 节点记为失败
            // （历史 record_failure 目标即 wait 节点本身）。
            let error = ExecutionError::DynamicWait;
            record_dev_failure(shared, ctx, step, &error).await;
            return Err(error);
        }
        let wait_type = node
            .error_handler
            .as_ref()
            .and_then(|value| value.get("waitType"))
            .cloned();
        let payload = serde_json::json!({
            "waiting": true,
            "subStatus": "WAITING_APPROVAL",
            "waitType": wait_type
        });
        if !inline {
            // 生产面历史行为：步骤行 + 挂起请求（执行行转 WAITING，等待审批/恢复接口）。
            let attempt = state.begin_step(ctx, step, &Value::Null).await?;
            state
                .finish_step_success(ctx, step, attempt, &payload, 0)
                .await?;
            state.on_waiting(ctx, step, &payload).await?;
        }
        return Ok(NodeOutcome::Waiting);
    }

    // ---------- delay ----------
    if node.node_type == "delay" {
        let milliseconds = node.delay_ms.unwrap_or(0);
        if milliseconds > 0 && (dynamic || inline) {
            // 生产面顶层历史行为：不睡眠（仅控制检查点）；其余场景真实/模拟睡眠。
            deps.clock.sleep_delay(milliseconds).await;
        }
        if !dynamic && !inline {
            let attempt = state.begin_step(ctx, step, &Value::Null).await?;
            state
                .finish_step_success(
                    ctx,
                    step,
                    attempt,
                    &serde_json::json!({"control": "delay"}),
                    0,
                )
                .await?;
        } else if inline {
            let _ = log_entry(
                shared,
                LogLevel::Debug,
                Some(node.id.clone()),
                format!("delay {milliseconds}ms 完成"),
            )
            .await;
        }
        return Ok(NodeOutcome::Completed {
            output: None,
            attempts: 0,
        });
    }

    // ---------- return ----------
    if node.node_type == "return" {
        let value = node
            .inputs
            .get("output")
            .map(|output| eval_value(shared, output, context))
            .transpose()?
            .unwrap_or(Value::Null);
        return Err(ExecutionError::StepReturn(value));
    }

    // ---------- break / continue ----------
    if node.node_type == "break" || node.node_type == "continue" {
        let signal = if node.node_type == "break" {
            ExecutionError::LoopBreak
        } else {
            ExecutionError::LoopContinue
        };
        if dynamic {
            return Err(signal);
        }
        if !inline {
            // 生产面历史行为：顶层 break/continue 为 IR 结构错误。
            return Err(ExecutionError::Ir(
                "break/continue 只能在 for/while 循环体内执行".into(),
            ));
        }
        return Err(signal);
    }

    // ---------- 动作节点（task / plugin / subworkflow）----------
    if matches!(node.node_type.as_str(), "task" | "plugin" | "subworkflow") {
        return execute_action_node(shared, ctx, node, context, step).await;
    }

    // ---------- 未知节点类型 ----------
    if !inline {
        // 生产面历史行为：无动作节点写控制检查点后完成。
        let attempt = state.begin_step(ctx, step, &Value::Null).await?;
        state
            .finish_step_success(
                ctx,
                step,
                attempt,
                &serde_json::json!({"control": node.node_type}),
                0,
            )
            .await?;
        return Ok(NodeOutcome::Completed {
            output: None,
            attempts: 0,
        });
    }
    let error = ExecutionError::MissingConfig(format!("未知节点类型：{}", node.node_type));
    record_dev_failure(shared, ctx, step, &error).await;
    Err(error)
}

/// 统一 IR 值求值（驱动内唯一表达式求值出口）：
/// 调试面 `enable_expressions=false` 时按字面量透传（历史纯结构执行语义），
/// 其余路径经表达式子系统求值器（`expression::evaluator::evaluate_value`）。
fn eval_value(shared: &Shared, value: &Value, context: &Value) -> Result<Value, ExecutionError> {
    if !shared.deps.config.expressions_enabled() {
        return Ok(value.clone());
    }
    evaluate_value(value, context).map_err(|error| ExecutionError::Variable(error.0))
}

/// 自由函数版条件求值（避免在 `execute_node` 自由函数中使用 Self 方法）。
fn self_condition_outcome(
    shared: &Shared,
    node: &NodeIr,
    context: &Value,
) -> Result<bool, ExecutionError> {
    condition_outcome_with(node, context, |expr, ctx| eval_value(shared, expr, ctx))
}

/// 动作重试退避日志（调试面；生产面仅真实睡眠不记录计划延迟——历史行为一致）。
async fn log_retry_backoff(shared: &Shared, node: &NodeIr, attempt: u32, max_attempts: u32) {
    if shared.deps.config.expand_mode() != ExpandMode::Inline {
        return;
    }
    let strategy = retry_strategy(node, &shared.ir.runtime);
    let delay_ms = backoff_delay_ms(&strategy, attempt);
    let _ = log_entry(
        shared,
        LogLevel::Debug,
        Some(node.id.clone()),
        format!(
            "重试退避：strategy={strategy}，计划延迟 {delay_ms}ms 后进入第 {}/{} 次尝试",
            attempt + 1,
            max_attempts
        ),
    )
    .await;
}

/// 调试面失败自记录：最内层失败节点记录自身（与历史 `record_failure` 的
/// “错误自带节点优先、否则回退根节点”目标语义等价——驱动内错误均由其
/// 产生节点在返回前记录）。生产面 no-op（失败经步骤行审计）。
async fn record_dev_failure(
    shared: &Shared,
    ctx: &ExecutionContext,
    step: &StepRef,
    error: &ExecutionError,
) {
    if shared.deps.config.expand_mode() == ExpandMode::Inline {
        shared
            .deps
            .state
            .node_outcome(ctx, step, NodeStatus::Failed, Some(dev_node_error(error)));
    }
}

/// 调试面节点错误记录（`DevNodeResult.error` 的内存形态）。
fn dev_node_error(error: &ExecutionError) -> NodeError {
    let detail = match error {
        ExecutionError::Action { retryable, .. } => Some(format!("retryable={retryable}")),
        _ => None,
    };
    NodeError {
        code: error.dev_code(),
        message: error.dev_message(),
        detail,
    }
}

/// foreach / for 迭代体（双执行面唯一实现）。
#[allow(clippy::too_many_arguments)] // 参数与 LoopPlan/执行上下文一一对应，收敛为结构体将失去可读性
async fn run_foreach(
    shared: &Shared,
    ctx: &ExecutionContext,
    node: &NodeIr,
    context: &Value,
    step: &StepRef,
    plan: &crate::execution_core::LoopPlan,
    count: &mut usize,
    inline: bool,
) -> Result<(), ExecutionError> {
    let collection = match plan.collection.as_ref() {
        Some(value) => eval_value(shared, value, context)?,
        None => {
            let error = ExecutionError::ValueProblem("foreach 缺少 collection".into());
            record_dev_failure(shared, ctx, step, &error).await;
            return Err(error);
        }
    };
    let values = match collection.as_array() {
        Some(values) => values,
        None => {
            let error = ExecutionError::ValueProblem("foreach collection 必须是 array".into());
            record_dev_failure(shared, ctx, step, &error).await;
            return Err(error);
        }
    };
    let Some(iterator) = plan.iterator.as_deref() else {
        let error = ExecutionError::MissingConfig("foreach 缺少 iterator".into());
        record_dev_failure(shared, ctx, step, &error).await;
        return Err(error);
    };
    if values.len() > plan.max_iterations {
        let error = ExecutionError::LoopLimit {
            kind: "foreach",
            elements: Some(values.len()),
            max: plan.max_iterations,
        };
        record_dev_failure(shared, ctx, step, &error).await;
        return Err(error);
    }
    if inline {
        // 调试面：按声明顺序顺序执行（确定性），迭代变量注入 vars。
        for (offset, value) in values.iter().enumerate() {
            let mut iteration_context = context.clone();
            if let Some(vars) = iteration_context
                .get_mut("vars")
                .and_then(Value::as_object_mut)
            {
                vars.insert(iterator.to_owned(), value.clone());
            }
            let _ = log_entry(
                shared,
                LogLevel::Debug,
                Some(node.id.clone()),
                format!(
                    "foreach 迭代 [{offset}]（{iterator}={}）",
                    display_scalar(value)
                ),
            )
            .await;
            match execute_dynamic_roots(shared, ctx, &plan.roots, &iteration_context, None).await {
                Ok(()) => {}
                Err(ExecutionError::LoopBreak) => break,
                Err(ExecutionError::LoopContinue) => {}
                Err(error) => return Err(error),
            }
            *count += 1;
        }
    } else {
        // 生产面：每一元素最多启动 runtime.maxParallel 个任务；迭代实例使用
        // 独立持久化 ID（示例：loop-42[3].process），并行元素可独立审计/重放。
        let parallel = shared.ir.runtime.max_parallel.unwrap_or(1).clamp(1, 32) as usize;
        for (batch_index, batch) in values.chunks(parallel).enumerate() {
            let mut tasks: JoinSet<Result<(), ExecutionError>> = JoinSet::new();
            for (offset, value) in batch.iter().enumerate() {
                let mut iteration_context = context.clone();
                if let Some(vars) = iteration_context
                    .get_mut("vars")
                    .and_then(Value::as_object_mut)
                {
                    vars.insert(iterator.to_owned(), value.clone());
                }
                let instance_prefix = format!(
                    "{}[{}]",
                    node.id,
                    batch_index.saturating_mul(parallel).saturating_add(offset)
                );
                let shared = shared.clone();
                let ctx = ctx.clone();
                let roots = plan.roots.clone();
                tasks.spawn(async move {
                    execute_dynamic_roots(
                        &shared,
                        &ctx,
                        &roots,
                        &iteration_context,
                        Some(instance_prefix),
                    )
                    .await
                });
            }
            while let Some(result) = tasks.join_next().await {
                // `JoinSet` 外层 Result 承载 `JoinError`（任务 panic/取消），
                // 内层 Result 为迭代体自身结果——两层都要展开。
                let inner = result.map_err(|error| {
                    ExecutionError::Ir(format!("foreach 子任务异常退出：{error}"))
                })?;
                inner?;
                *count += 1;
            }
        }
    }
    Ok(())
}

/// while / for-range 迭代体（双执行面唯一实现）；每轮迭代后刷新上下文
/// （生产：重读执行行累积输出；调试：取最新内存快照）。
#[allow(clippy::too_many_arguments)] // 参数与 LoopPlan/执行上下文一一对应，收敛为结构体将失去可读性
async fn run_while_like(
    shared: &Shared,
    ctx: &ExecutionContext,
    node: &NodeIr,
    context: &Value,
    step: &StepRef,
    plan: &crate::execution_core::LoopPlan,
    count: &mut usize,
    inline: bool,
) -> Result<(), ExecutionError> {
    let deps = &shared.deps;
    let state = &deps.state;
    let max_iterations = plan.max_iterations;

    if plan.kind == "while" {
        let Some(condition) = &plan.condition else {
            let error = ExecutionError::MissingConfig("while 缺少 condition".into());
            record_dev_failure(shared, ctx, step, &error).await;
            return Err(error);
        };
        let mut iteration_context = context.clone();
        while truthy(&eval_value(shared, condition, &iteration_context)?) {
            if *count >= max_iterations {
                let error = ExecutionError::LoopLimit {
                    kind: "while",
                    elements: None,
                    max: max_iterations,
                };
                record_dev_failure(shared, ctx, step, &error).await;
                return Err(error);
            }
            if inline {
                let _ = log_entry(
                    shared,
                    LogLevel::Debug,
                    Some(node.id.clone()),
                    format!("while 迭代 [{count}]"),
                )
                .await;
            }
            let prefix = if inline {
                None
            } else {
                Some(format!("{}[{}]", node.id, count))
            };
            match execute_dynamic_roots(shared, ctx, &plan.roots, &iteration_context, prefix).await
            {
                Ok(()) => {}
                Err(ExecutionError::LoopBreak) => break,
                Err(ExecutionError::LoopContinue) => {}
                Err(error) => return Err(error),
            }
            *count += 1;
            iteration_context = state.build_context(ctx).await?;
        }
        return Ok(());
    }

    // for-range：迭代变量取 [from, to) 的整数值。
    let Some(iterator) = plan.iterator.as_deref() else {
        let error = ExecutionError::MissingConfig("for-range 缺少 iterator".into());
        record_dev_failure(shared, ctx, step, &error).await;
        return Err(error);
    };
    let from = match plan.range_from.as_ref() {
        Some(value) => eval_value(shared, value, context)?
            .as_f64()
            .ok_or_else(|| ExecutionError::ValueProblem("for range 起点必须是 number".into())),
        None => Err(ExecutionError::MissingConfig("for-range 缺少 from".into())),
    };
    let from = match from {
        Ok(value) => value as i64,
        Err(error) => {
            record_dev_failure(shared, ctx, step, &error).await;
            return Err(error);
        }
    };
    let to = match plan.range_to.as_ref() {
        Some(value) => eval_value(shared, value, context)?
            .as_f64()
            .ok_or_else(|| ExecutionError::ValueProblem("for range 终点必须是 number".into())),
        None => Err(ExecutionError::MissingConfig("for-range 缺少 to".into())),
    };
    let to = match to {
        Ok(value) => value as i64,
        Err(error) => {
            record_dev_failure(shared, ctx, step, &error).await;
            return Err(error);
        }
    };
    let mut current = from;
    while current < to {
        if *count >= max_iterations {
            let error = ExecutionError::LoopLimit {
                kind: "for",
                elements: None,
                max: max_iterations,
            };
            record_dev_failure(shared, ctx, step, &error).await;
            return Err(error);
        }
        let mut iteration_context = context.clone();
        if let Some(vars) = iteration_context
            .get_mut("vars")
            .and_then(Value::as_object_mut)
        {
            vars.insert(iterator.to_owned(), serde_json::json!(current));
        }
        if inline {
            let _ = log_entry(
                shared,
                LogLevel::Debug,
                Some(node.id.clone()),
                format!("for-range 迭代 [{count}]（{iterator}={current}）"),
            )
            .await;
        }
        let prefix = if inline {
            None
        } else {
            Some(format!("{}[{}]", node.id, count))
        };
        match execute_dynamic_roots(shared, ctx, &plan.roots, &iteration_context, prefix).await {
            Ok(()) => {}
            Err(ExecutionError::LoopBreak) => break,
            Err(ExecutionError::LoopContinue) => {}
            Err(error) => return Err(error),
        }
        *count += 1;
        current += 1;
    }
    Ok(())
}

/// 执行 foreach / while / for-range / for 控制节点（双执行面唯一实现）。
///
/// 生产面：`begin_step` 检查点 + foreach 按 `maxParallel` 分批并发 + `finish_step_success`
/// 载荷 `{"loop": kind, "iterations": n}`；调试面：顺序确定性迭代 + 调试日志。
pub(crate) async fn execute_loop_node(
    shared: &Shared,
    ctx: &ExecutionContext,
    node: &NodeIr,
    context: &Value,
    step: &StepRef,
    inline: bool,
) -> Result<NodeOutcome, ExecutionError> {
    let deps = &shared.deps;
    let ir = &shared.ir;
    let state = &deps.state;

    let Some(config) = node.loop_config.as_ref().filter(|value| value.is_object()) else {
        let error = ExecutionError::MissingConfig("loop 缺少 loopConfig".into());
        record_dev_failure(shared, ctx, step, &error).await;
        return Err(error);
    };
    let plan = parse_loop_plan(config, &node.children);
    let attempt = if !inline {
        Some(state.begin_step(ctx, step, &Value::Null).await?)
    } else {
        None
    };
    let started = deps.clock.now_ms();
    let mut count = 0usize;
    let body = match plan.kind.as_str() {
        "foreach" | "for" => {
            run_foreach(shared, ctx, node, context, step, &plan, &mut count, inline).await
        }
        "while" | "for-range" => {
            run_while_like(shared, ctx, node, context, step, &plan, &mut count, inline).await
        }
        other => Err(ExecutionError::UnsupportedLoopKind(other.to_string())),
    };
    match body {
        Ok(()) => {
            let payload = serde_json::json!({"loop": plan.kind, "iterations": count});
            let duration = deps.clock.now_ms() - started;
            if let Some(attempt) = attempt {
                state
                    .finish_step_success(ctx, step, attempt, &payload, duration)
                    .await?;
            } else {
                state
                    .finish_step_success(ctx, step, 1, &payload, duration)
                    .await?;
            }
            Ok(NodeOutcome::CompletedWithSkips(descendants_for_children(
                ir,
                &node.children,
            )))
        }
        Err(error) => Err(error),
    }
}

/// 普通任务/插件/子工作流节点：条件跳过 + mock 覆盖 + 重试 + 退避 + 超时
/// （双执行面唯一实现；动作调用仅经 `ActionExecutor` 抽象，需求 7）。
pub(crate) async fn execute_action_node(
    shared: &Shared,
    ctx: &ExecutionContext,
    node: &NodeIr,
    context: &Value,
    step: &StepRef,
) -> Result<NodeOutcome, ExecutionError> {
    let deps = &shared.deps;
    let ir = &shared.ir;
    let state = &deps.state;
    let config = &deps.config;
    let inline = config.expand_mode() == ExpandMode::Inline;

    // 步骤级条件（false 时按生产语义记 success + skipped 标记）。
    if node.condition.is_some()
        && !condition_outcome_with(node, context, |expr, ctx2| eval_value(shared, expr, ctx2))?
    {
        let payload = serde_json::json!({"skipped": true, "reason": "condition=false"});
        if inline {
            state.finish_step_success(ctx, step, 1, &payload, 0).await?;
            let _ = log_entry(
                shared,
                LogLevel::Info,
                Some(node.id.clone()),
                "步骤条件为 false：跳过动作执行".into(),
            )
            .await;
        } else {
            let attempt = state.begin_step(ctx, step, &Value::Null).await?;
            state
                .finish_step_success(ctx, step, attempt, &payload, 0)
                .await?;
        }
        return Ok(NodeOutcome::Completed {
            output: Some(payload),
            attempts: 0,
        });
    }
    let Some(action) = node.action.as_ref() else {
        if !inline {
            // 生产面历史行为：无动作节点写控制检查点后完成（不报错）。
            let attempt = state.begin_step(ctx, step, &Value::Null).await?;
            state
                .finish_step_success(
                    ctx,
                    step,
                    attempt,
                    &serde_json::json!({"control": node.node_type}),
                    0,
                )
                .await?;
            return Ok(NodeOutcome::Completed {
                output: None,
                attempts: 0,
            });
        }
        let error = ExecutionError::MissingConfig("task 节点缺少 action".into());
        record_dev_failure(shared, ctx, step, &error).await;
        return Err(error);
    };
    let input = eval_value(shared, &action.arguments, context)?;
    state.record_step_input(ctx, step, &input);
    if inline {
        let _ = log_entry(
            shared,
            LogLevel::Info,
            Some(node.id.clone()),
            format!(
                "动作调用：{} input={}",
                crate::dev_exec::action_key(action),
                truncate_json(&input)
            ),
        )
        .await;
    }

    // 调试面 mock 节点输出覆盖（需求 4.24）：绕过动作执行器。
    if let Some(mock_output) = config.mock_output(&node.id) {
        state.record_step_output(ctx, step, &mock_output, 1);
        let _ = log_entry(
            shared,
            LogLevel::Info,
            Some(node.id.clone()),
            "使用 mock 节点输出（配置覆盖）".into(),
        )
        .await;
        return Ok(NodeOutcome::Completed {
            output: Some(mock_output),
            attempts: 1,
        });
    }

    let max_attempts = retry_max_attempts(node, &ir.runtime);
    let timeout = resolve_timeout(
        node,
        &ir.runtime,
        Duration::from_millis(config.default_action_timeout_ms()),
    );
    let simulated_latency = config.simulated_latency_ms(&node.id);

    let mut last_error: Option<ExecutionError> = None;
    for attempt in 1..=max_attempts {
        // 模拟延迟视为本次尝试实际消耗的执行时间（计入全局超时与结果耗时）。
        if simulated_latency > 0 {
            deps.clock.advance(simulated_latency);
        }
        let row_attempt = state.begin_step(ctx, step, &input).await?;
        // 注入失败计划（调试测试支持，需求 4.24/12.x）。
        if let Some(spec) = config.injected_failure(&node.id, attempt) {
            last_error = Some(ExecutionError::Action {
                code: spec.code.clone(),
                message: spec.message.clone(),
                retryable: spec.retryable,
            });
            if inline {
                let _ = log_entry(
                    shared,
                    LogLevel::Warn,
                    Some(node.id.clone()),
                    format!(
                        "注入失败（attempt {attempt}/{max_attempts}）：{} {}",
                        spec.code, spec.message
                    ),
                )
                .await;
            }
            if !spec.retryable || attempt == max_attempts {
                break;
            }
            log_retry_backoff(shared, node, attempt, max_attempts).await;
            continue;
        }
        // 模拟超时路径（模拟延迟 > 节点超时 → CF5001 可重试，与生产 tokio timeout 一致）。
        if simulated_latency > 0 && Duration::from_millis(simulated_latency) > timeout {
            last_error = Some(ExecutionError::Action {
                code: "CF5001".into(),
                message: "节点执行超过 Runtime 超时上限".into(),
                retryable: true,
            });
            if inline {
                let _ = log_entry(
                    shared,
                    LogLevel::Warn,
                    Some(node.id.clone()),
                    format!(
                        "动作超时（模拟延迟 {simulated_latency}ms > 超时 {timeout:?}，attempt {attempt}/{max_attempts}）"
                    ),
                )
                .await;
            }
            if attempt == max_attempts {
                break;
            }
            log_retry_backoff(shared, node, attempt, max_attempts).await;
            continue;
        }
        let started = deps.clock.now_ms();
        let step_context = StepContext {
            execution: ctx.clone(),
            node_id: node.id.clone(),
            step_id: step.instance_id.clone(),
            attempt: row_attempt,
            action: action.clone(),
            input: input.clone(),
            timeout,
        };
        match deps.action.execute(&step_context).await {
            Ok(output) => {
                let duration = deps.clock.now_ms() - started;
                if !inline {
                    state
                        .finish_step_success(ctx, step, row_attempt, &output, duration)
                        .await?;
                }
                state.record_step_output(ctx, step, &output, attempt);
                if inline {
                    let _ = log_entry(
                        shared,
                        LogLevel::Debug,
                        Some(node.id.clone()),
                        format!("动作返回（attempt {attempt}）：{}", truncate_json(&output)),
                    )
                    .await;
                }
                return Ok(NodeOutcome::Completed {
                    output: Some(output),
                    attempts: attempt,
                });
            }
            Err(error) => {
                let retryable = match &error {
                    ExecutionError::Action { retryable, .. } => *retryable,
                    _ => false,
                };
                let may_retry = retryable && attempt < max_attempts;
                let duration = deps.clock.now_ms() - started;
                if !inline {
                    state
                        .finish_step_failure(
                            ctx,
                            step,
                            row_attempt,
                            &error.production_code(),
                            &error.public_message_for(false),
                            may_retry,
                            duration,
                        )
                        .await?;
                }
                last_error = Some(error.clone());
                if inline {
                    let (code, message) = match &error {
                        ExecutionError::Action { code, message, .. } => {
                            (code.clone(), message.clone())
                        }
                        other => (other.dev_code(), other.dev_message()),
                    };
                    let _ = log_entry(
                        shared,
                        LogLevel::Warn,
                        Some(node.id.clone()),
                        format!("动作失败（attempt {attempt}/{max_attempts}）：{code} {message}"),
                    )
                    .await;
                }
                if !may_retry {
                    break;
                }
                log_retry_backoff(shared, node, attempt, max_attempts).await;
                let strategy = retry_strategy(node, &ir.runtime);
                deps.clock
                    .sleep_backoff(Duration::from_millis(backoff_delay_ms(&strategy, attempt)))
                    .await;
            }
        }
    }
    let error = last_error.unwrap_or_else(|| ExecutionError::Internal("重试循环异常结束".into()));
    record_dev_failure(shared, ctx, step, &error).await;
    Err(error)
}

/// 动态执行体：在控制流内部执行根节点（双执行面唯一实现，镜像历史
/// production/dev 两个 `execute_dynamic_roots` 的统一语义）。
///
/// `instance_prefix` 为生产面检查点实例前缀（如 `loop-42[3]` / `try_a`）；
/// 调试面簿记恒用逻辑节点 ID（历史纯内存语义）。
pub(crate) fn execute_dynamic_roots<'x>(
    shared: &'x Shared,
    ctx: &'x ExecutionContext,
    roots: &'x [String],
    context: &'x Value,
    instance_prefix: Option<String>,
) -> Pin<Box<dyn Future<Output = Result<(), ExecutionError>> + Send + 'x>> {
    // 返回装箱的 `dyn Future + Send`：打断 `execute_node` ⇄ 本函数的互递归
    // 类型循环（E0733），并让 `JoinSet::spawn` 的 `Send` 检查经 `dyn` 边界
    // 直接成立（`dyn Future + Send` 无条件 `Send`），无需结构归纳。
    Box::pin(async move {
        let deps = &shared.deps;
        let ir = &shared.ir;
        let state = &deps.state;
        let inline = deps.config.expand_mode() == ExpandMode::Inline;

        for root in roots {
            let mut node = ir
                .spec
                .graph
                .nodes
                .iter()
                .find(|node| node.id == *root)
                .cloned()
                .ok_or_else(|| ExecutionError::Ir(format!("动态节点不存在：{root}")))?;
            if !inline {
                if let Some(prefix) = &instance_prefix {
                    node.id = format!("{prefix}.{}", node.id);
                }
            }
            let step = if inline {
                StepRef::top_level(root)
            } else {
                StepRef::nested(root, instance_prefix.as_deref())
            };
            match execute_node(shared, ctx, &node, context, &step, false).await {
                Ok(NodeOutcome::Completed { .. }) => {
                    if inline {
                        state.node_outcome(ctx, &step, NodeStatus::Success, None);
                    }
                }
                Ok(NodeOutcome::CompletedWithSkips(skipped)) => {
                    if inline {
                        for skipped_id in &skipped {
                            state
                                .finish_step_skipped(
                                    ctx,
                                    &StepRef::top_level(skipped_id),
                                    "未选中的控制流分支",
                                )
                                .await?;
                        }
                        state.node_outcome(ctx, &step, NodeStatus::Success, None);
                    }
                }
                Ok(NodeOutcome::Waiting) => {
                    // 动态体内 wait 与两执行面一致：不支持（CF2203），仅顶层 wait 允许挂起。
                    return Err(ExecutionError::DynamicWait);
                }
                Err(error) => {
                    // 调试面失败归属由最内层失败节点自行记录（`record_dev_failure`），
                    // 此处只传播（与历史 `record_failure` 目标语义一致）。
                    return Err(error);
                }
            }
        }
        Ok(())
    })
}

/// [V1.2-ON_ERROR] 步骤失败时执行其 on_error 子节点（通知/清理钩子，
/// 不改变失败结果；双执行面共用）。
pub(crate) async fn run_on_error(
    shared: &Shared,
    ctx: &ExecutionContext,
    node: &NodeIr,
    context: &Value,
) {
    let Some(children) = node
        .on_error
        .as_ref()
        .and_then(|value| value.get("nodes"))
        .and_then(Value::as_array)
    else {
        return;
    };
    let roots: Vec<String> = children
        .iter()
        .filter_map(Value::as_str)
        .map(str::to_owned)
        .collect();
    if roots.is_empty() {
        return;
    }
    let inline = shared.deps.config.expand_mode() == ExpandMode::Inline;
    if inline {
        let _ = log_entry(
            shared,
            LogLevel::Info,
            Some(node.id.clone()),
            format!("执行 on_error 钩子：{roots:?}"),
        )
        .await;
    }
    if let Err(error) = execute_dynamic_roots(shared, ctx, &roots, context, None).await {
        let message = if inline {
            format!("on_error 子节点执行失败：{:?}", dev_debug_repr(&error))
        } else {
            format!("on_error 子节点执行失败：{}", error.production_display())
        };
        let _ = log_entry(shared, LogLevel::Warn, Some(node.id.clone()), message).await;
    }
}

/// 全局失败处理器（生产：IR `extensions["handlers"]`；调试面经
/// `StateStore::global_failure_handlers` 返回空——保持历史行为）。
pub(crate) async fn run_failure_handlers(shared: &Shared, ctx: &ExecutionContext) {
    let deps = &shared.deps;
    let ir = &shared.ir;
    let handlers = deps.state.global_failure_handlers(ir);
    if handlers.is_empty() {
        return;
    }
    let context = match deps.state.build_context(ctx).await {
        Ok(value) => value,
        Err(_) => return,
    };
    for node in &handlers {
        let step = StepRef::top_level(&node.id);
        if let Err(error) = execute_node(shared, ctx, node, &context, &step, true).await {
            let _ = log_entry(
                shared,
                LogLevel::Warn,
                Some(node.id.clone()),
                format!("失败处理器执行失败：{}", error.production_display()),
            )
            .await;
        }
    }
}

/// 日志辅助（`LogSink` 为异步 trait；统一驱动内所有日志经此出口）。
async fn log_entry(
    shared: &Shared,
    level: LogLevel,
    node: Option<String>,
    message: String,
) -> Result<(), ExecutionError> {
    shared.deps.log.log(LogEntry::new(level, node, message));
    Ok(())
}
