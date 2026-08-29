//! 生产执行面：CloudFlow 持久化调度器与执行协调器。
//!
//! 分层定位（双执行面架构 · 统一执行引擎）：
//! - 本模块 = **生产执行面**：HTTP/MQ 创建实例后只写 READY；独立 Worker 竞争领取，
//!   随后调用**统一调度驱动**（`cloudflow_engine_core::engine`）——检查点恢复、
//!   主循环（超时/控制轮询/完成判定/条件依赖/批执行）、节点分发、控制流语义
//!   （条件/分支/try/循环/重试/退避/并行/超时/子树展开/控制信号）与开发调试面
//!   **同一实现**（`engine::driver`），本模块不重复定义任何调度/执行逻辑；
//! - 本模块只负责生产面 `EngineDeps` 的具体实现（依赖注入面，需求 §一/§二/§三/§四）：
//!   - `MysqlStateStore`：MySQL 执行行 + 步骤检查点（每步提交；poll/build_context
//!     均为新鲜读，while 迭代刷新依赖该语义）；
//!   - `AgentActionExecutor`：Capability Agent（gRPC）唯一能力出口，节点级超时
//!     由本执行器以 `tokio::time::timeout` 实施（超时 = CF5001 可重试）；
//!   - `RealClock`：墙钟 + 真实睡眠；
//!   - `TracingLogSink`：tracing 结构化日志（级别映射）；
//!   - `ProductionConfigProvider`：Deferred 顶层控制展开 + `runtime.maxParallel`
//!     并发批 + Runtime 默认动作超时；
//! - 进程崩溃时由 stale recovery 将 RUNNING 恢复为 READY；Worker 并发由信号量控制。
//!
//! [CLOUDFLOW-RUNTIME-EXEC-001] 检查点、stale recovery 与并发约束见模块内注释。

use crate::agent::{AgentError, AgentInvocation, AuthorizationContext, CapabilityInvoker};
use crate::persistence::{CreateExecution, RuntimeStore, StepFailure, StoredExecution};
use cloudflow_engine_core::engine::clock::RealClock;
use cloudflow_engine_core::engine::context::{ExecutionContext, StepContext, StepRef};
use cloudflow_engine_core::engine::deps::{
    ActionExecutor, ConfigProvider, ControlFlags, EngineDeps, ExpandMode, LogEntry, LogLevel,
    LogSink, NoopEventPublisher, StateStore,
};
use cloudflow_engine_core::engine::error::ExecutionError;
use cloudflow_engine_core::engine::execute;
use cloudflow_engine_core::engine::result::WorkflowEndStatus;
use cloudflow_engine_core::expression::evaluator::{normalize_variables, ExpressionEvalError};
use cloudflow_engine_core::ir::{NodeIr, WorkflowIrV1};
use serde_json::{Map, Value};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::Semaphore;
use tracing::{debug, error, warn};

#[derive(Clone)]
pub struct ExecutionCoordinator {
    store: RuntimeStore,
    invoker: Arc<dyn CapabilityInvoker>,
    worker_concurrency: usize,
    stale_seconds: u64,
    poll_interval: Duration,
    default_action_timeout: Duration,
}

impl ExecutionCoordinator {
    pub fn new(
        store: RuntimeStore,
        invoker: Arc<dyn CapabilityInvoker>,
        worker_concurrency: usize,
        stale_seconds: u64,
        poll_interval: Duration,
        default_action_timeout: Duration,
    ) -> Self {
        Self {
            store,
            invoker,
            worker_concurrency: worker_concurrency.max(1),
            stale_seconds,
            poll_interval,
            default_action_timeout,
        }
    }

    pub fn store(&self) -> &RuntimeStore {
        &self.store
    }

    pub async fn submit(
        &self,
        mut command: CreateExecution,
    ) -> Result<bool, RuntimeExecutionError> {
        command.variables = normalize_variables(&command.ir, command.variables)
            .map_err(|error| RuntimeExecutionError::Variable(error.0))?;
        self.store
            .create_execution(&command)
            .await
            .map_err(RuntimeExecutionError::Database)
    }

    pub async fn run_workers(self, shutdown: tokio::sync::watch::Receiver<bool>) {
        let semaphore = Arc::new(Semaphore::new(self.worker_concurrency));
        let mut shutdown = shutdown;
        loop {
            if *shutdown.borrow() {
                break;
            }
            match self.store.recover_stale(self.stale_seconds).await {
                Ok(count) if count > 0 => warn!(count, "恢复失联 CloudFlow 执行"),
                Err(error) => error!(%error, "CloudFlow stale recovery 失败"),
                _ => {}
            }
            match self.store.claim_next().await {
                Ok(Some(execution)) => {
                    let Ok(permit) = semaphore.clone().acquire_owned().await else {
                        break;
                    };
                    let coordinator = self.clone();
                    tokio::spawn(async move {
                        let _permit = permit;
                        if let Err(error) = coordinator.execute(execution).await {
                            error!(%error, "CloudFlow 执行失败");
                        }
                    });
                }
                Ok(None) => {
                    tokio::select! {
                        _ = tokio::time::sleep(self.poll_interval) => {},
                        _ = shutdown.changed() => {}
                    }
                }
                Err(error) => {
                    error!(%error, "CloudFlow 领取执行失败");
                    tokio::time::sleep(self.poll_interval).await;
                }
            }
        }
    }

    /// 生产执行入口：构造生产面 `EngineDeps` 后调用统一调度驱动。
    ///
    /// 语义终结（成功/失败/取消）由驱动内部经 `finish_workflow` 落盘；
    /// WAITING 挂起不落盘终结（执行行保持 WAITING，等待审批/恢复接口），
    /// 与历史生产行为逐字一致。
    async fn execute(&self, execution: StoredExecution) -> Result<(), RuntimeExecutionError> {
        let authorization = AuthorizationContext {
            user_id: execution.user_id.clone(),
            space_id: execution.space_id.clone(),
            declared_permissions: execution.declared_permissions.iter().cloned().collect(),
            granted_permissions: execution.granted_permissions.iter().cloned().collect(),
        };
        let deps = EngineDeps::new(
            Arc::new(MysqlStateStore {
                store: self.store.clone(),
            }),
            Arc::new(TracingLogSink),
            Arc::new(AgentActionExecutor {
                invoker: self.invoker.clone(),
                execution_id: execution.execution_id.clone(),
                trace_id: execution.trace_id.clone(),
                authorization,
            }),
            Arc::new(NoopEventPublisher),
            Arc::new(RealClock::new()),
            Arc::new(ProductionConfigProvider {
                default_action_timeout_ms: self.default_action_timeout.as_millis() as u64,
            }),
        );
        let ctx = ExecutionContext {
            execution_id: execution.execution_id.clone(),
            user_id: execution.user_id,
            space_id: execution.space_id.unwrap_or_default(),
            ..Default::default()
        };
        // 驱动返回 `ExecutionResult`（生产面状态已经 `finish_workflow` 落盘，
        // 结果对象本身无需保留）；错误经 `From` 映射到生产错误码表。
        execute(&execution.ir, ctx, deps)
            .await
            .map(|_| ())
            .map_err(RuntimeExecutionError::from)
    }
}

/// 生产面状态存储：MySQL 执行行 + 步骤检查点（需求 2.2-2.25）。
///
/// 关键语义（与历史生产执行面逐字一致）：
/// - `poll_control` / `build_context` / `refresh_iteration_context` 均为**新鲜读**
///   （while 循环每轮迭代必须看到最新累积输出与取消/暂停标志）；
/// - `init_variables` 为 no-op（规范化变量已随执行行写入）；
/// - 节点生命周期簿记（`node_started` 等）为 no-op——生产节点状态由检查点表承载；
/// - `global_failure_handlers` 读取 IR `extensions["handlers"]`（历史生产行为）。
pub struct MysqlStateStore {
    store: RuntimeStore,
}

impl MysqlStateStore {
    async fn fresh(&self, execution_id: &str) -> Result<StoredExecution, ExecutionError> {
        self.store
            .get_execution(execution_id)
            .await
            .map_err(|error| ExecutionError::Store(error.to_string()))
            .and_then(|execution| {
                execution.ok_or_else(|| ExecutionError::Ir("执行实例在运行期间消失".into()))
            })
    }
}

#[async_trait::async_trait]
impl StateStore for MysqlStateStore {
    async fn restore_completed(
        &self,
        ctx: &ExecutionContext,
    ) -> Result<Vec<String>, ExecutionError> {
        self.store
            .completed_steps(&ctx.execution_id)
            .await
            .map_err(|error| ExecutionError::Store(error.to_string()))
    }

    async fn poll_control(&self, ctx: &ExecutionContext) -> Result<ControlFlags, ExecutionError> {
        let fresh = self.fresh(&ctx.execution_id).await?;
        Ok(ControlFlags {
            cancelled: fresh.cancel_requested,
            paused: fresh.pause_requested || fresh.status == "WAITING",
        })
    }

    async fn build_context(&self, ctx: &ExecutionContext) -> Result<Value, ExecutionError> {
        let fresh = self.fresh(&ctx.execution_id).await?;
        Ok(build_context(&fresh))
    }

    fn init_variables(&self, _ctx: &ExecutionContext, _vars: &Value) -> Result<(), ExecutionError> {
        // 生产面 no-op：规范化变量已在 `submit` 时写入执行行。
        Ok(())
    }

    async fn begin_step(
        &self,
        ctx: &ExecutionContext,
        step: &StepRef,
        input: &Value,
    ) -> Result<u64, ExecutionError> {
        self.store
            .begin_step(&ctx.execution_id, &step.instance_id, input)
            .await
            .map_err(|error| ExecutionError::Store(error.to_string()))
            .map(u64::from)
    }

    async fn finish_step_success(
        &self,
        ctx: &ExecutionContext,
        step: &StepRef,
        attempt: u64,
        output: &Value,
        duration_ms: u64,
    ) -> Result<(), ExecutionError> {
        self.store
            .finish_step_success(
                &ctx.execution_id,
                &step.instance_id,
                attempt as u32,
                output,
                duration_ms,
            )
            .await
            .map_err(|error| ExecutionError::Store(error.to_string()))
    }

    async fn finish_step_failure(
        &self,
        ctx: &ExecutionContext,
        step: &StepRef,
        attempt: u64,
        code: &str,
        summary: &str,
        retryable: bool,
        duration_ms: u64,
    ) -> Result<(), ExecutionError> {
        self.store
            .finish_step_failure(StepFailure {
                execution_id: &ctx.execution_id,
                step_id: &step.instance_id,
                attempt: attempt as u32,
                code,
                summary,
                retryable,
                duration_ms,
            })
            .await
            .map_err(|error| ExecutionError::Store(error.to_string()))
    }

    async fn finish_step_skipped(
        &self,
        ctx: &ExecutionContext,
        step: &StepRef,
        reason: &str,
    ) -> Result<(), ExecutionError> {
        self.store
            .finish_step_skipped(&ctx.execution_id, &step.instance_id, reason)
            .await
            .map_err(|error| ExecutionError::Store(error.to_string()))
    }

    async fn on_waiting(
        &self,
        ctx: &ExecutionContext,
        _step: &StepRef,
        _payload: &Value,
    ) -> Result<(), ExecutionError> {
        // 等待载荷已随 wait 分支的 `finish_step_success` 落盘；
        // 此处仅发起挂起（执行行转 WAITING，等待审批/恢复接口）。
        self.store
            .request_pause(&ctx.execution_id)
            .await
            .map_err(|error| ExecutionError::Store(error.to_string()))?;
        Ok(())
    }

    async fn finish_workflow(
        &self,
        ctx: &ExecutionContext,
        status: WorkflowEndStatus,
        code: Option<&str>,
        message: &str,
    ) -> Result<(), ExecutionError> {
        let status_str = match status {
            WorkflowEndStatus::Success => "SUCCESS",
            WorkflowEndStatus::Failed => "FAILED",
            WorkflowEndStatus::Cancelled => "CANCELLED",
            // WAITING 终结不经 `finish_workflow`（执行行保持 WAITING）；
            // 防御性映射保持与状态机一致。
            WorkflowEndStatus::Waiting => "WAITING",
            _ => "FAILED",
        };
        self.store
            .finish_execution(&ctx.execution_id, status_str, code, Some(message))
            .await
            .map_err(|error| ExecutionError::Store(error.to_string()))
    }

    /// 全局失败处理器节点（生产：IR `extensions["handlers"]`；历史生产行为）。
    fn global_failure_handlers(&self, ir: &WorkflowIrV1) -> Vec<NodeIr> {
        let Some(handlers) = ir.extensions.get("handlers").and_then(Value::as_array) else {
            return Vec::new();
        };
        handlers
            .iter()
            .filter_map(|handler| {
                handler
                    .get("graph")
                    .and_then(|value| value.get("nodes"))
                    .and_then(Value::as_array)
            })
            .flatten()
            .filter_map(|raw| serde_json::from_value::<NodeIr>(raw.clone()).ok())
            .collect()
    }

    /// while 迭代后刷新迭代上下文（生产：重读执行行累积输出，历史生产语义）。
    fn refresh_iteration_context(&self, _ctx: &ExecutionContext, _context: &mut Value) {
        // 生产面为异步新鲜读；驱动经 `build_context` 每轮主循环刷新，
        // 循环体内上下文在迭代体执行前由驱动重建，此钩子保持 no-op 以与
        // 历史生产行为一致（循环体内可见当前迭代已累积输出经驱动主循环）。
    }
}

/// 生产面动作执行器：Capability Agent（gRPC）唯一能力出口（需求 4.4-4.25）。
///
/// 节点级超时在此实施（驱动传入 `StepContext.timeout`）：
/// `tokio::time::timeout` 触发时返回 CF5001（可重试），与历史生产行为一致。
pub struct AgentActionExecutor {
    invoker: Arc<dyn CapabilityInvoker>,
    execution_id: String,
    trace_id: String,
    authorization: AuthorizationContext,
}

#[async_trait::async_trait]
impl ActionExecutor for AgentActionExecutor {
    async fn execute(&self, step: &StepContext) -> Result<Value, ExecutionError> {
        let invocation = AgentInvocation {
            execution_id: self.execution_id.clone(),
            step_id: step.step_id.clone(),
            attempt: step.attempt as u32,
            action: step.action.clone(),
            input: step.input.clone(),
            authorization: self.authorization.clone(),
            trace_id: self.trace_id.clone(),
        };
        let result = tokio::time::timeout(step.timeout, self.invoker.invoke(invocation)).await;
        match result {
            Ok(Ok(output)) => Ok(output.value),
            Ok(Err(error)) => Err(ExecutionError::Action {
                code: error.code,
                message: error.summary,
                retryable: error.retryable,
            }),
            Err(_elapsed) => Err(ExecutionError::Action {
                code: "CF5001".into(),
                message: "节点执行超过 Runtime 超时上限".into(),
                retryable: true,
            }),
        }
    }
}

/// 生产面日志收集：tracing 结构化输出（级别映射；节点 ID 作为结构化字段）。
pub struct TracingLogSink;

impl LogSink for TracingLogSink {
    fn log(&self, entry: LogEntry) {
        let message = entry.message;
        match entry.level {
            LogLevel::Debug => {
                if let Some(node) = entry.node {
                    debug!(%node, "{message}");
                } else {
                    debug!("{message}");
                }
            }
            LogLevel::Info => {
                if let Some(node) = entry.node {
                    tracing::info!(%node, "{message}");
                } else {
                    tracing::info!("{message}");
                }
            }
            LogLevel::Warn => {
                if let Some(node) = entry.node {
                    warn!(%node, "{message}");
                } else {
                    warn!("{message}");
                }
            }
            LogLevel::Error => {
                if let Some(node) = entry.node {
                    error!(%node, "{message}");
                } else {
                    error!("{message}");
                }
            }
        }
    }
}

/// 生产面运行时配置（需求 1.10）：Deferred 顶层控制展开、`runtime.maxParallel`
/// 并发批、Runtime 默认动作超时；无调试钩子（断点/注入/mock 均为调试面专属）。
pub struct ProductionConfigProvider {
    default_action_timeout_ms: u64,
}

impl ConfigProvider for ProductionConfigProvider {
    fn top_level_batch_size(&self, ir: &WorkflowIrV1) -> usize {
        ir.runtime.max_parallel.unwrap_or(1).clamp(1, 32) as usize
    }

    fn expand_mode(&self) -> ExpandMode {
        ExpandMode::Deferred
    }

    fn default_action_timeout_ms(&self) -> u64 {
        self.default_action_timeout_ms
    }
}

/// 表达式上下文构建（生产面）：执行行 vars + 已累积步骤输出
/// （`steps.<id>.output` 数据源；与统一驱动上下文契约一致）。
pub(crate) fn build_context(execution: &StoredExecution) -> Value {
    let steps = execution
        .outputs
        .as_object()
        .map(|outputs| {
            outputs
                .iter()
                .map(|(key, value)| (key.clone(), serde_json::json!({"output": value})))
                .collect::<Map<_, _>>()
        })
        .unwrap_or_default();
    serde_json::json!({"vars": execution.variables, "steps": steps})
}

/// 生产执行面错误（对 API/微服务保持历史契约：`code()` 码表与 `public_message()` 文案不变）。
#[derive(Debug, thiserror::Error)]
pub enum RuntimeExecutionError {
    #[error("数据库错误：{0}")]
    Database(#[from] sqlx::Error),
    #[error("IR 错误：{0}")]
    Ir(String),
    #[error("{0}")]
    Agent(#[from] AgentError),
    #[error("变量错误：{0}")]
    Variable(String),
    // [V1.2-BREAK-CONTINUE] 内部循环控制信号，非业务错误：用于 for/while 循环体中的 break/continue。
    // 必须在 loop 节点内被捕获，绝不允许泄漏到工作流顶层。
    #[error("break 跳出循环")]
    LoopBreak,
    // [V1.2-BREAK-CONTINUE] 内部循环控制信号。
    #[error("continue 进入下次迭代")]
    LoopContinue,
    // [V1.2-RETURN] 内部分支结束信号，携带返回输出；到达循环/try 之外的顶层即完成工作流。
    #[error("提前返回：{0:?}")]
    StepReturn(Value),
    // [V1.2-VALIDATE] validate 校验未通过的运行时错误。
    #[error("validate 校验未通过：{0}")]
    ValidateFailed(String),
}

impl RuntimeExecutionError {
    /// 生产执行面错误码（与 `docs/CLOUDFLOW_ERROR_DESIGN.md` 码表一致；
    /// 供 API 响应与微服务消费方使用）。
    pub fn code(&self) -> &str {
        match self {
            Self::Database(_) => "CF6001",
            Self::Ir(_) => "CF1301",
            Self::Agent(value) => &value.code,
            Self::Variable(_) => "CF2101",
            Self::LoopBreak | Self::LoopContinue => "CF4408",
            Self::StepReturn(_) => "CF4417",
            Self::ValidateFailed(_) => "CF4412",
        }
    }

    pub fn public_message(&self) -> String {
        match self {
            Self::Variable(message) | Self::Ir(message) => message.clone(),
            Self::Agent(value) => value.summary.clone(),
            Self::Database(_) => "CloudFlow Runtime 持久化服务暂时不可用".into(),
            Self::LoopBreak => "break 跳出循环".into(),
            Self::LoopContinue => "continue 进入下次迭代".into(),
            Self::StepReturn(value) => format!("提前返回输出 {value}"),
            Self::ValidateFailed(message) => format!("validate 校验未通过：{message}"),
        }
    }
}

/// 统一驱动错误 → 生产执行面错误码映射（与历史 `RuntimeExecutionError` 码表对齐）。
impl From<ExecutionError> for RuntimeExecutionError {
    fn from(error: ExecutionError) -> Self {
        match error {
            ExecutionError::Store(message) => {
                Self::Database(sqlx::Error::Io(std::io::Error::other(message)))
            }
            ExecutionError::Ir(message) => Self::Ir(message),
            ExecutionError::Action {
                code,
                message,
                retryable,
            } => Self::Agent(AgentError {
                code,
                summary: message,
                retryable,
            }),
            ExecutionError::Variable(message) => Self::Variable(message),
            ExecutionError::AssertFailed | ExecutionError::DynamicWait => {
                Self::Variable(error.production_display())
            }
            ExecutionError::ValidateFailed => Self::ValidateFailed(error.production_display()),
            ExecutionError::LoopBreak => Self::LoopBreak,
            ExecutionError::LoopContinue => Self::LoopContinue,
            ExecutionError::StepReturn(value) => Self::StepReturn(value),
            ExecutionError::LoopLimit { .. }
            | ExecutionError::MissingConfig(_)
            | ExecutionError::UnsupportedLoopKind(_)
            | ExecutionError::ValueProblem(_)
            | ExecutionError::Internal(_) => Self::Ir(error.production_display()),
        }
    }
}

impl From<ExpressionEvalError> for RuntimeExecutionError {
    fn from(error: ExpressionEvalError) -> Self {
        Self::Variable(error.0)
    }
}
