//! CloudFlow 持久化调度器与执行协调器。
//!
//! [CLOUDFLOW-RUNTIME-EXEC-001] HTTP/MQ 创建实例后只写 READY；独立 Worker 竞争领取，按 IR DAG
//! 调度并逐步提交检查点。进程崩溃时由 stale recovery 将 RUNNING 恢复为 READY。

use crate::{
    agent::{AgentError, AgentInvocation, AuthorizationContext, CapabilityInvoker},
    engine::exponential_backoff_ms,
    ir::{NodeIr, WorkflowIrV1},
    persistence::{CreateExecution, RuntimeStore, StepFailure, StoredExecution},
    runtime::RuntimeEngine,
};
use futures_util::future::BoxFuture;
use serde_json::{Map, Number, Value};
use std::{sync::Arc, time::Duration};
use tokio::{sync::Semaphore, task::JoinSet};
use tracing::{error, warn};

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
        command.variables = normalize_variables(&command.ir, command.variables)?;
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

    async fn execute(&self, execution: StoredExecution) -> Result<(), RuntimeExecutionError> {
        let mut engine = RuntimeEngine::load(&execution.execution_id, execution.ir.clone())
            .map_err(|errors| RuntimeExecutionError::Ir(errors.join("; ")))?;
        engine.restore_completed(self.store.completed_steps(&execution.execution_id).await?);
        let max_parallel = execution.ir.runtime.max_parallel.unwrap_or(1).clamp(1, 32) as usize;
        loop {
            let Some(fresh) = self.store.get_execution(&execution.execution_id).await? else {
                return Err(RuntimeExecutionError::Ir("执行实例在运行期间消失".into()));
            };
            if fresh.cancel_requested {
                self.store
                    .finish_execution(
                        &execution.execution_id,
                        "CANCELLED",
                        Some("CF-CANCELLED"),
                        Some("用户已取消执行"),
                    )
                    .await?;
                return Ok(());
            }
            if fresh.pause_requested || fresh.status == "WAITING" {
                return Ok(());
            }
            if engine.is_complete() {
                self.store
                    .finish_execution(
                        &execution.execution_id,
                        "SUCCESS",
                        None,
                        Some("工作流执行完成"),
                    )
                    .await?;
                return Ok(());
            }
            // [V1.2-COND-DEPENDS] 条件依赖：context 先于就绪计算构建；
            // 条件求值为 false 时该节点依赖被豁免，可直接调度。
            let context = build_context(&fresh);
            let holds = |node: &NodeIr| -> Option<bool> {
                let condition = node.depends_condition.as_ref()?;
                match evaluate(condition, &context) {
                    Ok(value) => Some(truthy(&value)),
                    Err(_) => None,
                }
            };
            let ready = engine.ready_nodes_conditional(&holds);
            if ready.is_empty() {
                self.store
                    .finish_execution(
                        &execution.execution_id,
                        "FAILED",
                        Some("CF2002"),
                        Some("DAG 无可运行节点且尚未完成"),
                    )
                    .await?;
                return Ok(());
            }
            let mut tasks = JoinSet::new();
            for node_id in ready.into_iter().take(max_parallel) {
                let node = engine
                    .node(&node_id)
                    .cloned()
                    .ok_or_else(|| RuntimeExecutionError::Ir(format!("节点不存在：{node_id}")))?;
                let coordinator = self.clone();
                let execution = fresh.clone();
                let context = context.clone();
                engine.mark_running(&node_id);
                tasks.spawn(async move {
                    let result = coordinator.execute_node(&execution, &node, &context).await;
                    (node_id, result)
                });
            }
            while let Some(joined) = tasks.join_next().await {
                let (node_id, result) = joined.map_err(|error| {
                    RuntimeExecutionError::Ir(format!("节点任务异常退出：{error}"))
                })?;
                match result {
                    Ok(NodeOutcome::Completed) => {
                        engine.mark_success(&node_id);
                    }
                    Ok(NodeOutcome::CompletedWithSkips(skipped)) => {
                        engine.mark_success(&node_id);
                        for skipped_id in skipped {
                            if skipped_id != node_id && engine.mark_skipped(&skipped_id) {
                                self.store
                                    .finish_step_skipped(
                                        &execution.execution_id,
                                        &skipped_id,
                                        "未选中的控制流分支",
                                    )
                                    .await?;
                            }
                        }
                    }
                    Ok(NodeOutcome::Waiting) => return Ok(()),
                    Err(error) => {
                        // [V1.2-RETURN] 提前返回视为成功结束：结束工作流并携带返回输出。
                        if let RuntimeExecutionError::StepReturn(value) = &error {
                            self.store
                                .finish_execution(
                                    &execution.execution_id,
                                    "SUCCESS",
                                    Some("CF4417"),
                                    Some(&format!("提前返回：{value}")),
                                )
                                .await?;
                            return Ok(());
                        }
                        // [V1.2-ON_ERROR] 失败步骤的 on_error 子节点作为通知/清理钩子执行。
                        let control_signal = matches!(
                            error,
                            RuntimeExecutionError::LoopBreak
                                | RuntimeExecutionError::LoopContinue
                                | RuntimeExecutionError::StepReturn(_)
                        );
                        if !control_signal {
                            self.run_on_error(&fresh, &node_id).await;
                        }
                        self.run_failure_handlers(&fresh).await;
                        self.store
                            .finish_execution(
                                &execution.execution_id,
                                "FAILED",
                                Some(error.code()),
                                Some(&error.to_string()),
                            )
                            .await?;
                        return Ok(());
                    }
                }
            }
        }
    }

    async fn execute_node(
        &self,
        execution: &StoredExecution,
        node: &NodeIr,
        context: &Value,
    ) -> Result<NodeOutcome, RuntimeExecutionError> {
        if node.node_type == "condition" {
            let selected_true = node
                .condition
                .as_ref()
                .map(|condition| evaluate(condition, context).map(|value| truthy(&value)))
                .transpose()?
                .unwrap_or(false);
            let branches = node.error_handler.as_ref().and_then(Value::as_object);
            let key = if selected_true {
                "falseBranch"
            } else {
                "trueBranch"
            };
            let skipped = branches
                .and_then(|value| value.get(key))
                .and_then(Value::as_array)
                .into_iter()
                .flatten()
                .filter_map(Value::as_str)
                .flat_map(|root| descendants(&execution.ir, root))
                .collect();
            let attempt = self
                .store
                .begin_step(&execution.execution_id, &node.id, &Value::Null)
                .await?;
            self.store
                .finish_step_success(
                    &execution.execution_id,
                    &node.id,
                    attempt,
                    &serde_json::json!({"condition": selected_true}),
                    0,
                )
                .await?;
            return Ok(NodeOutcome::CompletedWithSkips(skipped));
        }
        if node.node_type == "try" {
            self.execute_try_node(execution, node, context, None)
                .await?;
            return Ok(NodeOutcome::CompletedWithSkips(descendants_for_children(
                &execution.ir,
                &node.children,
            )));
        }
        if node.node_type == "loop" {
            self.execute_loop_node(execution, node, context).await?;
            return Ok(NodeOutcome::CompletedWithSkips(descendants_for_children(
                &execution.ir,
                &node.children,
            )));
        }
        if node.node_type == "assert" {
            let passed = node
                .condition
                .as_ref()
                .map(|condition| evaluate(condition, context).map(|value| truthy(&value)))
                .transpose()?
                .unwrap_or(false);
            let attempt = self
                .store
                .begin_step(&execution.execution_id, &node.id, &Value::Null)
                .await?;
            if !passed {
                self.store
                    .finish_step_failure(StepFailure {
                        execution_id: &execution.execution_id,
                        step_id: &node.id,
                        attempt,
                        code: "CF2202",
                        summary: "CloudFlow assert 条件不成立",
                        retryable: false,
                        duration_ms: 0,
                    })
                    .await?;
                return Err(RuntimeExecutionError::Variable(
                    "CF2202: assert 条件不成立".into(),
                ));
            }
            self.store
                .finish_step_success(
                    &execution.execution_id,
                    &node.id,
                    attempt,
                    &serde_json::json!({"assert": true}),
                    0,
                )
                .await?;
            return Ok(NodeOutcome::Completed);
        }
        // [V1.2-VALIDATE] 顶层校验节点：condition 求值为 false 时记录失败并终止执行路径。
        if node.node_type == "validate" {
            let passed = node
                .condition
                .as_ref()
                .map(|condition| evaluate(condition, context).map(|value| truthy(&value)))
                .transpose()?
                .unwrap_or(false);
            let attempt = self
                .store
                .begin_step(&execution.execution_id, &node.id, &Value::Null)
                .await?;
            if !passed {
                self.store
                    .finish_step_failure(StepFailure {
                        execution_id: &execution.execution_id,
                        step_id: &node.id,
                        attempt,
                        code: "CF4412",
                        summary: "validate 校验未通过",
                        retryable: false,
                        duration_ms: 0,
                    })
                    .await?;
                return Err(RuntimeExecutionError::ValidateFailed(
                    "validate 表达式求值为 false".into(),
                ));
            }
            self.store
                .finish_step_success(
                    &execution.execution_id,
                    &node.id,
                    attempt,
                    &serde_json::json!({"validate": true}),
                    0,
                )
                .await?;
            return Ok(NodeOutcome::Completed);
        }
        // [V1.2-NOTIFY] 内建通知：求值 to/message 后记录载荷。真实投递依赖部署环境的通知服务。
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
                .map(|value| evaluate(value, context))
                .transpose()?;
            let message = config
                .get("message")
                .map(|value| evaluate(value, context))
                .transpose()?;
            let attempt = self
                .store
                .begin_step(&execution.execution_id, &node.id, &Value::Null)
                .await?;
            tracing::info!(execution_id = %execution.execution_id, node = %node.id, %channel, "notify 触发");
            self.store
                .finish_step_success(
                    &execution.execution_id,
                    &node.id,
                    attempt,
                    &serde_json::json!({
                        "notify": true,
                        "channel": channel,
                        "to": to,
                        "message": message,
                    }),
                    0,
                )
                .await?;
            return Ok(NodeOutcome::Completed);
        }
        // [V1.2-RETURN] 提前返回：以 StepReturn 信号结束当前工作流分支并携带输出。
        if node.node_type == "return" {
            let value = node
                .inputs
                .get("output")
                .map(|output| evaluate(output, context))
                .transpose()?
                .unwrap_or(Value::Null);
            return Err(RuntimeExecutionError::StepReturn(value));
        }
        // [V1.2-BREAK-CONTINUE] 静态 DAG 不应直接驱动 break/continue；它们只作为循环子节点存在。
        if node.node_type == "break" || node.node_type == "continue" {
            return Err(RuntimeExecutionError::Ir(
                "break/continue 只能在 for/while 循环体内执行".into(),
            ));
        }
        if let Some(condition) = &node.condition {
            if !truthy(&evaluate(condition, context)?) {
                let attempt = self
                    .store
                    .begin_step(&execution.execution_id, &node.id, &Value::Null)
                    .await?;
                self.store
                    .finish_step_success(
                        &execution.execution_id,
                        &node.id,
                        attempt,
                        &serde_json::json!({"skipped": true, "reason": "condition=false"}),
                        0,
                    )
                    .await?;
                return Ok(NodeOutcome::Completed);
            }
        }
        if node.node_type == "wait" {
            let attempt = self
                .store
                .begin_step(&execution.execution_id, &node.id, &Value::Null)
                .await?;
            self.store
                .finish_step_success(
                    &execution.execution_id,
                    &node.id,
                    attempt,
                    &serde_json::json!({
                        "waiting": true,
                        "subStatus": "WAITING_APPROVAL",
                        "waitType": node.error_handler.as_ref().and_then(|value| value.get("waitType"))
                    }),
                    0,
                )
                .await?;
            self.store.request_pause(&execution.execution_id).await?;
            return Ok(NodeOutcome::Waiting);
        }
        let Some(action) = &node.action else {
            let attempt = self
                .store
                .begin_step(&execution.execution_id, &node.id, &Value::Null)
                .await?;
            self.store
                .finish_step_success(
                    &execution.execution_id,
                    &node.id,
                    attempt,
                    &serde_json::json!({"control": node.node_type}),
                    0,
                )
                .await?;
            return Ok(NodeOutcome::Completed);
        };
        let input = evaluate(&action.arguments, context)?;
        let retry = node
            .retry
            .as_ref()
            .or(execution.ir.runtime.retry_policy.as_ref());
        let max_attempts = retry
            .map(|value| value.max_attempts)
            .unwrap_or(1)
            .clamp(1, 10);
        let timeout = parse_duration(node.timeout.as_deref())
            .or_else(|| {
                execution
                    .ir
                    .runtime
                    .timeout_seconds
                    .map(Duration::from_secs)
            })
            .unwrap_or(self.default_action_timeout)
            .min(Duration::from_secs(3600));
        let authorization = AuthorizationContext {
            user_id: execution.user_id.clone(),
            space_id: execution.space_id.clone(),
            declared_permissions: execution.declared_permissions.iter().cloned().collect(),
            granted_permissions: execution.granted_permissions.iter().cloned().collect(),
        };
        for local_attempt in 1..=max_attempts {
            let attempt = self
                .store
                .begin_step(&execution.execution_id, &node.id, &input)
                .await?;
            let started = std::time::Instant::now();
            let result = tokio::time::timeout(
                timeout,
                self.invoker.invoke(AgentInvocation {
                    execution_id: execution.execution_id.clone(),
                    step_id: node.id.clone(),
                    attempt,
                    action: action.clone(),
                    input: input.clone(),
                    authorization: authorization.clone(),
                    trace_id: execution.trace_id.clone(),
                }),
            )
            .await
            .unwrap_or_else(|_| {
                Err(AgentError {
                    code: "CF5001".into(),
                    summary: "节点执行超过 Runtime 超时上限".into(),
                    retryable: true,
                })
            });
            match result {
                Ok(output) => {
                    self.store
                        .finish_step_success(
                            &execution.execution_id,
                            &node.id,
                            attempt,
                            &output.value,
                            started.elapsed().as_millis() as u64,
                        )
                        .await?;
                    return Ok(NodeOutcome::Completed);
                }
                Err(error) => {
                    let may_retry = error.retryable && local_attempt < max_attempts;
                    self.store
                        .finish_step_failure(StepFailure {
                            execution_id: &execution.execution_id,
                            step_id: &node.id,
                            attempt,
                            code: &error.code,
                            summary: &error.summary,
                            retryable: may_retry,
                            duration_ms: started.elapsed().as_millis() as u64,
                        })
                        .await?;
                    if !may_retry {
                        return Err(RuntimeExecutionError::Agent(error));
                    }
                    let strategy = retry
                        .map(|value| value.strategy.as_str())
                        .unwrap_or("fixed");
                    let delay = if strategy == "exponential" {
                        exponential_backoff_ms(local_attempt as u8 - 1, 500, 30_000)
                    } else {
                        500
                    };
                    tokio::time::sleep(Duration::from_millis(delay)).await;
                }
            }
        }
        Err(RuntimeExecutionError::Ir("重试循环异常结束".into()))
    }

    /// 执行 foreach / while 控制节点。每一个元素最多启动 `runtime.maxParallel` 个任务；
    /// 迭代体仍经 Capability Agent 与步骤检查点，不在 Runtime 进程内直接执行用户逻辑。
    async fn execute_loop_node(
        &self,
        execution: &StoredExecution,
        node: &NodeIr,
        context: &Value,
    ) -> Result<(), RuntimeExecutionError> {
        let config = node
            .loop_config
            .as_ref()
            .and_then(Value::as_object)
            .ok_or_else(|| RuntimeExecutionError::Ir("loop 缺少 loopConfig".into()))?;
        let kind = config
            .get("kind")
            .and_then(Value::as_str)
            .unwrap_or("foreach");
        let roots = config
            .get("body")
            .and_then(Value::as_array)
            .map(|values| {
                values
                    .iter()
                    .filter_map(Value::as_str)
                    .map(str::to_owned)
                    .collect()
            })
            .unwrap_or_else(|| node.children.clone());
        let max_iterations = config
            .get("maxIterations")
            .and_then(Value::as_u64)
            .unwrap_or(1_000)
            .clamp(1, 10_000) as usize;
        let attempt = self
            .store
            .begin_step(&execution.execution_id, &node.id, &Value::Null)
            .await?;
        let started = std::time::Instant::now();
        let mut count = 0usize;
        match kind {
            "foreach" => {
                let collection = config
                    .get("collection")
                    .map(|value| evaluate(value, context))
                    .transpose()?
                    .ok_or_else(|| {
                        RuntimeExecutionError::Variable("foreach 缺少 collection".into())
                    })?;
                let values = collection.as_array().ok_or_else(|| {
                    RuntimeExecutionError::Variable("foreach collection 必须是 array".into())
                })?;
                let iterator = config
                    .get("iterator")
                    .and_then(Value::as_str)
                    .ok_or_else(|| RuntimeExecutionError::Ir("foreach 缺少 iterator".into()))?;
                if values.len() > max_iterations {
                    return Err(RuntimeExecutionError::Ir(format!(
                        "CF2201: foreach 元素数量 {} 超过 maxIterations {max_iterations}",
                        values.len()
                    )));
                }
                let parallel = execution.ir.runtime.max_parallel.unwrap_or(1).clamp(1, 32) as usize;
                for (batch_index, batch) in values.chunks(parallel).enumerate() {
                    let mut tasks = JoinSet::new();
                    for (offset, value) in batch.iter().enumerate() {
                        let mut iteration_context = context.clone();
                        iteration_context
                            .get_mut("vars")
                            .and_then(Value::as_object_mut)
                            .ok_or_else(|| {
                                RuntimeExecutionError::Variable("执行上下文缺少 vars".into())
                            })?
                            .insert(iterator.to_owned(), value.clone());
                        let coordinator = self.clone();
                        let execution = execution.clone();
                        let roots = roots.clone();
                        // [CLOUDFLOW-RUNTIME-CONTROL-002] 迭代实例必须使用独立持久化 ID，
                        // 而非只增加同一静态 step 的 attempt；否则并行元素无法独立审计、
                        // 重放或关联输出。示例：loop-42[3].process。
                        let instance_prefix = format!(
                            "{}[{}]",
                            node.id,
                            batch_index.saturating_mul(parallel).saturating_add(offset)
                        );
                        tasks.spawn(async move {
                            coordinator
                                .execute_dynamic_roots(
                                    &execution,
                                    &roots,
                                    &iteration_context,
                                    Some(instance_prefix),
                                )
                                .await
                        });
                    }
                    while let Some(result) = tasks.join_next().await {
                        result.map_err(|error| {
                            RuntimeExecutionError::Ir(format!("foreach 子任务异常退出：{error}"))
                        })??;
                        count += 1;
                    }
                }
            }
            "while" => {
                let condition = config
                    .get("condition")
                    .ok_or_else(|| RuntimeExecutionError::Ir("while 缺少 condition".into()))?;
                let mut iteration_context = context.clone();
                while truthy(&evaluate(condition, &iteration_context)?) {
                    if count >= max_iterations {
                        return Err(RuntimeExecutionError::Ir(format!(
                            "CF2201: while 超过 maxIterations {max_iterations}，已中止以避免无限循环"
                        )));
                    }
                    // [V1.2-BREAK-CONTINUE] 捕获循环控制信号：break 跳出循环，continue 重新求值条件。
                    match self
                        .execute_dynamic_roots(
                            execution,
                            &roots,
                            &iteration_context,
                            Some(format!("{}[{count}]", node.id)),
                        )
                        .await
                    {
                        Ok(()) => {}
                        Err(RuntimeExecutionError::LoopBreak) => break,
                        Err(RuntimeExecutionError::LoopContinue) => {}
                        Err(error) => return Err(error),
                    }
                    count += 1;
                    // V1 while 体只能通过受控变量/上游输出影响外层上下文；不允许隐式修改 AST。
                    // 如条件不依赖可变值，maxIterations 是最后的确定性防线。
                    iteration_context = build_context(
                        &self
                            .store
                            .get_execution(&execution.execution_id)
                            .await?
                            .ok_or_else(|| RuntimeExecutionError::Ir("执行实例消失".into()))?,
                    );
                }
            }
            // [V1.2-FOR] for i in range(from, to)：迭代变量取 [from, to) 的整数值，最多 maxIterations 次。
            "for-range" => {
                let iterator = config
                    .get("iterator")
                    .and_then(Value::as_str)
                    .ok_or_else(|| RuntimeExecutionError::Ir("for 缺少 iterator".into()))?;
                let from = evaluate(
                    config
                        .get("from")
                        .ok_or_else(|| RuntimeExecutionError::Ir("for 缺少 from".into()))?,
                    context,
                )?
                .as_f64()
                .ok_or_else(|| {
                    RuntimeExecutionError::Variable("for range 起点必须是 number".into())
                })? as i64;
                let to = evaluate(
                    config
                        .get("to")
                        .ok_or_else(|| RuntimeExecutionError::Ir("for 缺少 to".into()))?,
                    context,
                )?
                .as_f64()
                .ok_or_else(|| {
                    RuntimeExecutionError::Variable("for range 终点必须是 number".into())
                })? as i64;
                let mut current = from;
                while current < to {
                    if count >= max_iterations {
                        return Err(RuntimeExecutionError::Ir(format!(
                            "CF2201: for 超过 maxIterations {max_iterations}"
                        )));
                    }
                    let mut iteration_context = context.clone();
                    iteration_context
                        .get_mut("vars")
                        .and_then(Value::as_object_mut)
                        .ok_or_else(|| {
                            RuntimeExecutionError::Variable("执行上下文缺少 vars".into())
                        })?
                        .insert(iterator.to_owned(), serde_json::json!(current));
                    match self
                        .execute_dynamic_roots(
                            execution,
                            &roots,
                            &iteration_context,
                            Some(format!("{}[{count}]", node.id)),
                        )
                        .await
                    {
                        Ok(()) => {}
                        Err(RuntimeExecutionError::LoopBreak) => break,
                        Err(RuntimeExecutionError::LoopContinue) => {}
                        Err(error) => return Err(error),
                    }
                    count += 1;
                    current += 1;
                }
            }
            // [V1.2-FOR] for x in <collection>：迭代变量取集合元素，顺序执行并支持 break/continue。
            "for" => {
                let collection = config
                    .get("collection")
                    .map(|value| evaluate(value, context))
                    .transpose()?
                    .ok_or_else(|| RuntimeExecutionError::Variable("for 缺少 collection".into()))?;
                let values = collection.as_array().ok_or_else(|| {
                    RuntimeExecutionError::Variable("for collection 必须是 array".into())
                })?;
                let iterator = config
                    .get("iterator")
                    .and_then(Value::as_str)
                    .ok_or_else(|| RuntimeExecutionError::Ir("for 缺少 iterator".into()))?;
                if values.len() > max_iterations {
                    return Err(RuntimeExecutionError::Ir(format!(
                        "CF2201: for 元素数量 {} 超过 maxIterations {max_iterations}",
                        values.len()
                    )));
                }
                for value in values {
                    let mut iteration_context = context.clone();
                    iteration_context
                        .get_mut("vars")
                        .and_then(Value::as_object_mut)
                        .ok_or_else(|| {
                            RuntimeExecutionError::Variable("执行上下文缺少 vars".into())
                        })?
                        .insert(iterator.to_owned(), value.clone());
                    match self
                        .execute_dynamic_roots(
                            execution,
                            &roots,
                            &iteration_context,
                            Some(format!("{}[{count}]", node.id)),
                        )
                        .await
                    {
                        Ok(()) => {}
                        Err(RuntimeExecutionError::LoopBreak) => break,
                        Err(RuntimeExecutionError::LoopContinue) => {}
                        Err(error) => return Err(error),
                    }
                    count += 1;
                }
            }
            other => {
                return Err(RuntimeExecutionError::Ir(format!(
                    "不支持的 loop kind：{other}"
                )))
            }
        }
        self.store
            .finish_step_success(
                &execution.execution_id,
                &node.id,
                attempt,
                &serde_json::json!({"loop": kind, "iterations": count}),
                started.elapsed().as_millis() as u64,
            )
            .await?;
        Ok(())
    }

    /// try/catch/finally 是局部错误边界：try 失败不会立即触发工作流全局 handler；
    /// catch 收到脱敏错误对象，finally 无论前两者结果均执行。
    async fn execute_try_node(
        &self,
        execution: &StoredExecution,
        node: &NodeIr,
        context: &Value,
        instance_prefix: Option<String>,
    ) -> Result<(), RuntimeExecutionError> {
        let handler = node
            .error_handler
            .as_ref()
            .and_then(Value::as_object)
            .ok_or_else(|| RuntimeExecutionError::Ir("try 缺少 errorHandler".into()))?;
        let roots = |name: &str| -> Vec<String> {
            handler
                .get(name)
                .and_then(Value::as_array)
                .map(|values| {
                    values
                        .iter()
                        .filter_map(Value::as_str)
                        .map(str::to_owned)
                        .collect()
                })
                .unwrap_or_default()
        };
        let attempt = self
            .store
            .begin_step(&execution.execution_id, &node.id, &Value::Null)
            .await?;
        let started = std::time::Instant::now();
        let try_result = self
            .execute_dynamic_roots(
                execution,
                &roots("try"),
                context,
                instance_prefix.as_ref().map(|_| node.id.clone()),
            )
            .await;
        let mut failure = try_result.err();
        let mut caught = false;
        if let Some(error) = failure.as_ref() {
            let catch_nodes = roots("catch");
            // [V1.2-BREAK-CONTINUE / RETURN] break/continue/return 是控制信号而非业务
            // 异常，不应触发 catch；仅沿调用栈向上传播。
            let is_loop_signal = matches!(
                error,
                RuntimeExecutionError::LoopBreak
                    | RuntimeExecutionError::LoopContinue
                    | RuntimeExecutionError::StepReturn(_)
            );
            if !is_loop_signal && !catch_nodes.is_empty() {
                let mut catch_context = context.clone();
                let binding = handler
                    .get("catchBinding")
                    .and_then(Value::as_str)
                    .unwrap_or("error");
                catch_context
                    .get_mut("vars")
                    .and_then(Value::as_object_mut)
                    .ok_or_else(|| RuntimeExecutionError::Variable("执行上下文缺少 vars".into()))?
                    .insert(
                        binding.into(),
                        serde_json::json!({"code": error.code(), "message": error.public_message()}),
                    );
                match self
                    .execute_dynamic_roots(
                        execution,
                        &catch_nodes,
                        &catch_context,
                        instance_prefix.as_ref().map(|_| node.id.clone()),
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
        let finally_result = self
            .execute_dynamic_roots(
                execution,
                &roots("finally"),
                context,
                instance_prefix.as_ref().map(|_| node.id.clone()),
            )
            .await;
        if let Err(error) = finally_result {
            failure = Some(error);
        }
        if let Some(error) = failure {
            // 循环/返回控制信号不把 try 节点记为失败，直接向上传播。
            if matches!(
                error,
                RuntimeExecutionError::LoopBreak
                    | RuntimeExecutionError::LoopContinue
                    | RuntimeExecutionError::StepReturn(_)
            ) {
                return Err(error);
            }
            self.store
                .finish_step_failure(StepFailure {
                    execution_id: &execution.execution_id,
                    step_id: &node.id,
                    attempt,
                    code: error.code(),
                    summary: &error.public_message(),
                    retryable: false,
                    duration_ms: started.elapsed().as_millis() as u64,
                })
                .await?;
            return Err(error);
        }
        self.store
            .finish_step_success(
                &execution.execution_id,
                &node.id,
                attempt,
                &serde_json::json!({"try": "success", "caught": caught}),
                started.elapsed().as_millis() as u64,
            )
            .await?;
        Ok(())
    }

    /// 在动态控制流内执行根节点；静态 Engine 不会领取这些节点，因此这里显式处理分支、
    /// 并行和嵌套控制流。每个 action 仍走 `execute_node` 的超时、重试和权限链路。
    fn execute_dynamic_roots<'a>(
        &'a self,
        execution: &'a StoredExecution,
        roots: &'a [String],
        context: &'a Value,
        instance_prefix: Option<String>,
    ) -> BoxFuture<'a, Result<(), RuntimeExecutionError>> {
        Box::pin(async move {
            for root in roots {
                let mut node = execution
                    .ir
                    .spec
                    .graph
                    .nodes
                    .iter()
                    .find(|node| node.id == *root)
                    .cloned()
                    .ok_or_else(|| RuntimeExecutionError::Ir(format!("动态节点不存在：{root}")))?;
                if let Some(prefix) = instance_prefix.as_deref() {
                    node.id = format!("{prefix}.{}", node.id);
                }
                match node.node_type.as_str() {
                    "condition" => {
                        let selected = node
                            .condition
                            .as_ref()
                            .map(|value| evaluate(value, context).map(|value| truthy(&value)))
                            .transpose()?
                            .unwrap_or(false);
                        let branches = node.error_handler.as_ref().and_then(Value::as_object);
                        let key = if selected {
                            "trueBranch"
                        } else {
                            "falseBranch"
                        };
                        let branch = branches
                            .and_then(|value| value.get(key))
                            .and_then(Value::as_array)
                            .map(|values| {
                                values
                                    .iter()
                                    .filter_map(Value::as_str)
                                    .map(str::to_owned)
                                    .collect::<Vec<_>>()
                            })
                            .unwrap_or_default();
                        self.execute_dynamic_roots(
                            execution,
                            &branch,
                            context,
                            instance_prefix.as_ref().map(|_| node.id.clone()),
                        )
                        .await?;
                    }
                    "parallel" => {
                        // [V1.2-PARALLEL] 分支级 maxConcurrency 优先于全局 runtime.maxParallel。
                        let configured = node
                            .parallel
                            .as_ref()
                            .and_then(Value::as_object)
                            .and_then(|config| config.get("maxConcurrency"))
                            .and_then(Value::as_u64);
                        let global_max =
                            execution.ir.runtime.max_parallel.unwrap_or(1).clamp(1, 32) as u64;
                        let parallel =
                            configured.map_or(global_max, |value| value.clamp(1, 32)) as usize;
                        for batch in node.children.chunks(parallel) {
                            let mut tasks = JoinSet::new();
                            for child in batch {
                                let coordinator = self.clone();
                                let execution = execution.clone();
                                let child = vec![child.clone()];
                                let context = context.clone();
                                let instance_prefix =
                                    instance_prefix.as_ref().map(|_| node.id.clone());
                                tasks.spawn(async move {
                                    coordinator
                                        .execute_dynamic_roots(
                                            &execution,
                                            &child,
                                            &context,
                                            instance_prefix,
                                        )
                                        .await
                                });
                            }
                            while let Some(result) = tasks.join_next().await {
                                let task_result: Result<(), RuntimeExecutionError> = result
                                    .map_err(|error| {
                                        RuntimeExecutionError::Ir(format!(
                                            "parallel 子任务异常退出：{error}"
                                        ))
                                    })?;
                                // [V1.2-BREAK-CONTINUE] 循环控制信号穿透并行分支向上传播。
                                match task_result {
                                    Ok(()) => {}
                                    Err(
                                        signal @ (RuntimeExecutionError::LoopBreak
                                        | RuntimeExecutionError::LoopContinue),
                                    ) => {
                                        return Err(signal);
                                    }
                                    Err(other) => return Err(other),
                                }
                            }
                        }
                    }
                    "try" => {
                        self.execute_try_node(execution, &node, context, instance_prefix.clone())
                            .await?
                    }
                    "loop" => self.execute_loop_node(execution, &node, context).await?,
                    "assert" => {
                        let passed = node
                            .condition
                            .as_ref()
                            .map(|value| evaluate(value, context).map(|value| truthy(&value)))
                            .transpose()?
                            .unwrap_or(false);
                        if !passed {
                            return Err(RuntimeExecutionError::Variable(
                                "CF2202: assert 条件不成立".into(),
                            ));
                        }
                    }
                    "wait" => {
                        // 动态循环体中的 wait 尚无迭代级恢复游标；禁止悄悄重复此前副作用。
                        return Err(RuntimeExecutionError::Ir(
                            "CF2203: wait 不允许位于 foreach/while/try 的动态执行体内".into(),
                        ));
                    }
                    // [V1.2-SWITCH] 按 subject 求值结果匹配 case；未命中时走 default。
                    "switch" => {
                        let config = node
                            .switch_config
                            .as_ref()
                            .and_then(Value::as_object)
                            .ok_or_else(|| {
                                RuntimeExecutionError::Ir("switch 缺少 switchConfig".into())
                            })?;
                        let value = config
                            .get("subject")
                            .map(|subject| evaluate(subject, context))
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
                                    if let Some(body) =
                                        matched.get("body").and_then(Value::as_array)
                                    {
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
                            if let Some(default_branch) =
                                config.get("default").and_then(Value::as_array)
                            {
                                branch = default_branch
                                    .iter()
                                    .filter_map(Value::as_str)
                                    .map(str::to_owned)
                                    .collect();
                            }
                        }
                        self.execute_dynamic_roots(
                            execution,
                            &branch,
                            context,
                            instance_prefix.as_ref().map(|_| node.id.clone()),
                        )
                        .await?;
                    }
                    // [V1.2-DELAY] 固定延迟：睡眠指定时长后完成。
                    "delay" => {
                        let milliseconds = node.delay_ms.unwrap_or(0);
                        if milliseconds > 0 {
                            tokio::time::sleep(std::time::Duration::from_millis(milliseconds))
                                .await;
                        }
                    }
                    // [V1.2-VALIDATE] 校验表达式求值为 false 时产生 CF4412，终止当前执行路径。
                    "validate" => {
                        let passed = node
                            .condition
                            .as_ref()
                            .map(|value| evaluate(value, context).map(|value| truthy(&value)))
                            .transpose()?
                            .unwrap_or(false);
                        if !passed {
                            return Err(RuntimeExecutionError::ValidateFailed(
                                "validate 表达式求值为 false".into(),
                            ));
                        }
                    }
                    // [V1.2-NOTIFY] 动态体内内建通知：求值载荷并记录。
                    "notify" => match self.execute_node(execution, &node, context).await? {
                        NodeOutcome::Completed | NodeOutcome::CompletedWithSkips(_) => {}
                        NodeOutcome::Waiting => {
                            return Err(RuntimeExecutionError::Ir(
                                "CF2203: 动态执行体不支持 WAITING".into(),
                            ));
                        }
                    },
                    // [V1.2-RETURN] 提前返回信号，沿调用栈向上传播到顶层。
                    "return" => {
                        let value = node
                            .inputs
                            .get("output")
                            .map(|output| evaluate(output, context))
                            .transpose()?
                            .unwrap_or(Value::Null);
                        return Err(RuntimeExecutionError::StepReturn(value));
                    }
                    // [V1.2-BREAK-CONTINUE] 循环控制信号，由最近的 for/while 循环节点捕获。
                    "break" => return Err(RuntimeExecutionError::LoopBreak),
                    "continue" => return Err(RuntimeExecutionError::LoopContinue),
                    _ => match self.execute_node(execution, &node, context).await? {
                        NodeOutcome::Completed | NodeOutcome::CompletedWithSkips(_) => {}
                        NodeOutcome::Waiting => {
                            return Err(RuntimeExecutionError::Ir(
                                "CF2203: 动态执行体不支持 WAITING".into(),
                            ));
                        }
                    },
                }
            }
            Ok(())
        })
    }

    async fn run_failure_handlers(&self, execution: &StoredExecution) {
        let Some(handlers) = execution
            .ir
            .extensions
            .get("handlers")
            .and_then(Value::as_array)
        else {
            return;
        };
        for handler in handlers {
            let Some(nodes) = handler
                .get("graph")
                .and_then(|value| value.get("nodes"))
                .and_then(Value::as_array)
            else {
                continue;
            };
            for raw in nodes {
                let Ok(node) = serde_json::from_value::<NodeIr>(raw.clone()) else {
                    continue;
                };
                if let Err(error) = self
                    .execute_node(execution, &node, &build_context(execution))
                    .await
                {
                    warn!(execution_id=%execution.execution_id, %error, "失败处理器执行失败");
                }
            }
        }
    }

    /// [V1.2-ON_ERROR] 步骤失败时执行其 on_error 子节点（通知/清理钩子），不改变失败结果。
    async fn run_on_error(&self, execution: &StoredExecution, node_id: &str) {
        let Some(node) = execution
            .ir
            .spec
            .graph
            .nodes
            .iter()
            .find(|node| node.id == node_id)
        else {
            return;
        };
        let Some(children) = node
            .on_error
            .as_ref()
            .and_then(Value::as_object)
            .and_then(|config| config.get("nodes"))
            .and_then(Value::as_array)
        else {
            return;
        };
        let context = build_context(execution);
        for child_value in children {
            let Some(child_id) = child_value.as_str() else {
                continue;
            };
            let Some(child) = execution
                .ir
                .spec
                .graph
                .nodes
                .iter()
                .find(|node| node.id == child_id)
                .cloned()
            else {
                continue;
            };
            if let Err(error) = self.execute_node(execution, &child, &context).await {
                warn!(execution_id=%execution.execution_id, %error, node_id, "on_error 子节点执行失败");
            }
        }
    }
}

enum NodeOutcome {
    Completed,
    CompletedWithSkips(Vec<String>),
    Waiting,
}

fn descendants(ir: &WorkflowIrV1, root: &str) -> Vec<String> {
    let mut result = Vec::new();
    let mut stack = vec![root.to_owned()];
    while let Some(id) = stack.pop() {
        if result.iter().any(|value| value == &id) {
            continue;
        }
        result.push(id.clone());
        if let Some(node) = ir.spec.graph.nodes.iter().find(|node| node.id == id) {
            stack.extend(node.children.iter().cloned());
        }
    }
    result
}

fn descendants_for_children(ir: &WorkflowIrV1, children: &[String]) -> Vec<String> {
    children
        .iter()
        .flat_map(|child| descendants(ir, child))
        .collect()
}

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
    fn code(&self) -> &str {
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

fn normalize_variables(ir: &WorkflowIrV1, supplied: Value) -> Result<Value, RuntimeExecutionError> {
    let supplied = supplied.as_object().cloned().unwrap_or_default();
    let mut result = Map::new();
    for (name, declaration) in &ir.spec.variables {
        match declaration.source.as_str() {
            "input" => {
                if let Some(value) = supplied.get(name) {
                    if !matches_type(value, &declaration.type_name) {
                        return Err(RuntimeExecutionError::Variable(format!(
                            "变量 {name} 不符合 {} 类型",
                            declaration.type_name
                        )));
                    }
                    result.insert(name.clone(), value.clone());
                } else if let Some(default) = &declaration.default {
                    result.insert(name.clone(), default.clone());
                } else if declaration.required {
                    return Err(RuntimeExecutionError::Variable(format!(
                        "缺少必填变量 {name}"
                    )));
                }
            }
            "local" => {
                if supplied.contains_key(name) {
                    return Err(RuntimeExecutionError::Variable(format!(
                        "本地变量 {name} 不允许由启动请求覆盖"
                    )));
                }
                let Some(value) = &declaration.value else {
                    return Err(RuntimeExecutionError::Variable(format!(
                        "本地变量 {name} 缺少初始值"
                    )));
                };
                let context =
                    serde_json::json!({"vars": Value::Object(result.clone()), "steps": {}});
                let evaluated = evaluate(value, &context)?;
                if !matches_type(&evaluated, &declaration.type_name)
                    && declaration.type_name != "unknown"
                {
                    return Err(RuntimeExecutionError::Variable(format!(
                        "本地变量 {name} 不符合 {} 类型",
                        declaration.type_name
                    )));
                }
                result.insert(name.clone(), evaluated);
            }
            "deferred" => {
                if supplied.contains_key(name) {
                    return Err(RuntimeExecutionError::Variable(format!(
                        "延迟变量 {name} 只能由受控 Runtime 写入"
                    )));
                }
            }
            source => {
                return Err(RuntimeExecutionError::Variable(format!(
                    "变量 {name} 使用未知来源 {source}"
                )));
            }
        }
    }
    Ok(Value::Object(result))
}

fn matches_type(value: &Value, type_name: &str) -> bool {
    match type_name {
        "string" | "file" | "user" | "space" => value.is_string(),
        "number" => value.is_number(),
        "boolean" => value.is_boolean(),
        "array" => value.is_array(),
        "object" => value.is_object(),
        _ => false,
    }
}

fn build_context(execution: &StoredExecution) -> Value {
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

fn evaluate(value: &Value, context: &Value) -> Result<Value, RuntimeExecutionError> {
    if let Some(reference) = value.get("$ref").and_then(Value::as_str) {
        return lookup(context, reference)
            .cloned()
            .ok_or_else(|| RuntimeExecutionError::Variable(format!("引用不存在：{reference}")));
    }
    if let Some(expression) = value.get("$expr") {
        if let (Some(condition), Some(when_true), Some(when_false)) = (
            expression.get("condition"),
            expression.get("whenTrue"),
            expression.get("whenFalse"),
        ) {
            return if truthy(&evaluate(condition, context)?) {
                evaluate(when_true, context)
            } else {
                evaluate(when_false, context)
            };
        }
        if let Some(operator) = expression.get("operator").and_then(Value::as_str) {
            if let Some(operand) = expression.get("operand") {
                let operand = evaluate(operand, context)?;
                return match operator {
                    "!" => Ok(Value::Bool(!truthy(&operand))),
                    "-" => number(&operand).map(|value| json_number(-value)),
                    _ => Err(RuntimeExecutionError::Variable(format!(
                        "未知一元运算符 {operator}"
                    ))),
                };
            }
            let left = evaluate(expression.get("left").unwrap_or(&Value::Null), context)?;
            let right = evaluate(expression.get("right").unwrap_or(&Value::Null), context)?;
            return binary(operator, left, right);
        }
        if let Some(function) = expression.get("function").and_then(Value::as_str) {
            let arguments = expression
                .get("arguments")
                .and_then(Value::as_array)
                .cloned()
                .unwrap_or_default()
                .iter()
                .map(|value| evaluate(value, context))
                .collect::<Result<Vec<_>, _>>()?;
            return call(function, &arguments);
        }
    }
    // [V1.2-INTERPOLATION] 字符串模板：逐段求值并拼接。
    if let Some(segments) = value.get("$template").and_then(Value::as_array) {
        let mut out = String::new();
        for segment in segments {
            match evaluate(segment, context)? {
                Value::String(text) => out.push_str(&text),
                other => out.push_str(&other.to_string()),
            }
        }
        return Ok(Value::String(out));
    }
    // [V1.2-PIPELINE] 集合处理管道。
    if let Some(pipeline) = value.get("$pipeline").and_then(Value::as_object) {
        let input = pipeline
            .get("input")
            .map(|value| evaluate(value, context))
            .transpose()?
            .unwrap_or(Value::Null);
        let op = pipeline.get("op").cloned().unwrap_or_default();
        return apply_pipeline(input, &op, context);
    }
    match value {
        Value::Array(values) => Ok(Value::Array(
            values
                .iter()
                .map(|value| evaluate(value, context))
                .collect::<Result<_, _>>()?,
        )),
        Value::Object(values) => Ok(Value::Object(
            values
                .iter()
                .map(|(key, value)| Ok((key.clone(), evaluate(value, context)?)))
                .collect::<Result<_, RuntimeExecutionError>>()?,
        )),
        _ => Ok(value.clone()),
    }
}

fn lookup<'a>(context: &'a Value, reference: &str) -> Option<&'a Value> {
    let normalized = if let Some(step) = reference.strip_prefix("steps.") {
        format!("steps.{step}")
    } else if let Some((step, rest)) = reference.split_once('.') {
        // 不可用 `?` 提前返回：当上下文缺少 steps 键时，非 steps 的点分引用
        // （如 vars.name、行字段 size）也必须能继续解析。
        let is_step = context.get("steps").and_then(|s| s.get(step)).is_some();
        if is_step {
            format!("steps.{step}.{rest}")
        } else {
            reference.to_owned()
        }
    } else {
        reference.to_owned()
    };
    if !normalized.contains('.') {
        if let Some(value) = context.get("vars").and_then(|vars| vars.get(&normalized)) {
            return Some(value);
        }
    }
    normalized
        .split('.')
        .try_fold(context, |current, segment| current.get(segment))
}

fn binary(operator: &str, left: Value, right: Value) -> Result<Value, RuntimeExecutionError> {
    Ok(match operator {
        "==" => Value::Bool(left == right),
        "!=" => Value::Bool(left != right),
        "&&" => Value::Bool(truthy(&left) && truthy(&right)),
        "||" => Value::Bool(truthy(&left) || truthy(&right)),
        ">" => Value::Bool(number(&left)? > number(&right)?),
        ">=" => Value::Bool(number(&left)? >= number(&right)?),
        "<" => Value::Bool(number(&left)? < number(&right)?),
        "<=" => Value::Bool(number(&left)? <= number(&right)?),
        "+" => json_number(number(&left)? + number(&right)?),
        "-" => json_number(number(&left)? - number(&right)?),
        "*" => json_number(number(&left)? * number(&right)?),
        "/" => {
            let divisor = number(&right)?;
            if divisor == 0.0 {
                return Err(RuntimeExecutionError::Variable("表达式除零".into()));
            }
            json_number(number(&left)? / divisor)
        }
        "%" => {
            let divisor = number(&right)?;
            if divisor == 0.0 {
                return Err(RuntimeExecutionError::Variable("表达式取模除零".into()));
            }
            json_number(number(&left)? % divisor)
        }
        _ => {
            return Err(RuntimeExecutionError::Variable(format!(
                "未知运算符 {operator}"
            )))
        }
    })
}

fn call(function: &str, arguments: &[Value]) -> Result<Value, RuntimeExecutionError> {
    let first = arguments.first().unwrap_or(&Value::Null);
    match function {
        "len" | "size" => Ok(Value::Number(Number::from(
            first
                .as_array()
                .map(Vec::len)
                .or_else(|| first.as_object().map(Map::len))
                .or_else(|| first.as_str().map(str::len))
                .unwrap_or(0) as u64,
        ))),
        "contains" => {
            let needle = arguments.get(1).unwrap_or(&Value::Null);
            Ok(Value::Bool(match first {
                Value::String(value) => {
                    needle.as_str().is_some_and(|needle| value.contains(needle))
                }
                Value::Array(values) => values.iter().any(|value| value == needle),
                Value::Object(values) => needle
                    .as_str()
                    .is_some_and(|needle| values.contains_key(needle)),
                _ => false,
            }))
        }
        "starts_with" => Ok(Value::Bool(
            first
                .as_str()
                .zip(arguments.get(1).and_then(Value::as_str))
                .is_some_and(|(value, prefix)| value.starts_with(prefix)),
        )),
        "ends_with" => Ok(Value::Bool(
            first
                .as_str()
                .zip(arguments.get(1).and_then(Value::as_str))
                .is_some_and(|(value, suffix)| value.ends_with(suffix)),
        )),
        _ => Err(RuntimeExecutionError::Variable(format!(
            "未知内置函数 {function}"
        ))),
    }
}

/// [V1.2-PIPELINE] 应用一个管道操作。
fn apply_pipeline(
    input: Value,
    op: &Value,
    context: &Value,
) -> Result<Value, RuntimeExecutionError> {
    let elements = input
        .as_array()
        .cloned()
        .ok_or_else(|| RuntimeExecutionError::Variable("管道输入必须是 array".into()))?;
    match op.get("op").and_then(Value::as_str).unwrap_or("map") {
        "filter" => {
            let predicate = op.get("predicate").unwrap_or(&Value::Null);
            let mut kept = Vec::new();
            for element in elements {
                // 行上下文：把当前元素字段提升为顶层键，使谓词中的裸标识符（如 size）可解析。
                let mut row = context.clone();
                if let Some(fields) = element.as_object() {
                    for (key, value) in fields {
                        if key != "vars" && key != "steps" {
                            row[key.clone()] = value.clone();
                        }
                    }
                }
                if truthy(&evaluate(predicate, &row)?) {
                    kept.push(element);
                }
            }
            Ok(Value::Array(kept))
        }
        "map" => {
            let field = op.get("field").and_then(Value::as_str);
            let out = elements
                .into_iter()
                .map(|element| {
                    field
                        .and_then(|field| element.get(field))
                        .cloned()
                        .unwrap_or(Value::Null)
                })
                .collect();
            Ok(Value::Array(out))
        }
        "reduce" => {
            let function = op
                .get("function")
                .and_then(Value::as_str)
                .unwrap_or("count");
            reduce_values(elements, function)
        }
        other => Err(RuntimeExecutionError::Variable(format!(
            "未知管道操作 {other}"
        ))),
    }
}

/// [V1.2-PIPELINE] 聚合函数：count/sum/avg/min/max/join。
fn reduce_values(elements: Vec<Value>, function: &str) -> Result<Value, RuntimeExecutionError> {
    match function {
        "count" => Ok(Value::Number(Number::from(elements.len() as u64))),
        "sum" => {
            let mut total = 0.0;
            for element in &elements {
                total += number(element)?;
            }
            Ok(json_number(total))
        }
        "avg" => {
            if elements.is_empty() {
                return Ok(Value::Null);
            }
            let mut total = 0.0;
            for element in &elements {
                total += number(element)?;
            }
            Ok(json_number(total / elements.len() as f64))
        }
        "min" => {
            let mut it = elements.iter();
            let Some(first) = it.next() else {
                return Ok(Value::Null);
            };
            let mut min = number(first)?;
            for element in it {
                min = min.min(number(element)?);
            }
            Ok(json_number(min))
        }
        "max" => {
            let mut it = elements.iter();
            let Some(first) = it.next() else {
                return Ok(Value::Null);
            };
            let mut max = number(first)?;
            for element in it {
                max = max.max(number(element)?);
            }
            Ok(json_number(max))
        }
        "join" => {
            let parts = elements
                .iter()
                .map(|element| match element {
                    Value::String(value) => value.clone(),
                    other => other.to_string(),
                })
                .collect::<Vec<_>>()
                .join(",");
            Ok(Value::String(parts))
        }
        _ => Err(RuntimeExecutionError::Variable(format!(
            "未知聚合函数 {function}"
        ))),
    }
}

fn number(value: &Value) -> Result<f64, RuntimeExecutionError> {
    value
        .as_f64()
        .ok_or_else(|| RuntimeExecutionError::Variable("表达式需要 number".into()))
}

fn json_number(value: f64) -> Value {
    Number::from_f64(value)
        .map(Value::Number)
        .unwrap_or(Value::Null)
}

fn truthy(value: &Value) -> bool {
    match value {
        Value::Bool(value) => *value,
        Value::Null => false,
        Value::Number(value) => value.as_f64().is_some_and(|value| value != 0.0),
        Value::String(value) => !value.is_empty(),
        Value::Array(value) => !value.is_empty(),
        Value::Object(value) => !value.is_empty(),
    }
}

fn parse_duration(value: Option<&str>) -> Option<Duration> {
    let value = value?;
    let split = value.find(|character: char| !character.is_ascii_digit())?;
    let amount = value[..split].parse::<u64>().ok()?;
    match &value[split..] {
        "ms" => Some(Duration::from_millis(amount)),
        "s" => Some(Duration::from_secs(amount)),
        "m" => Some(Duration::from_secs(amount.saturating_mul(60))),
        "h" => Some(Duration::from_secs(amount.saturating_mul(3600))),
        "d" => Some(Duration::from_secs(amount.saturating_mul(86_400))),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolves_refs_and_expressions_without_string_ambiguity() {
        let context = serde_json::json!({
            "vars": {"threshold": 2},
            "steps": {"collect": {"output": {"count": 3}}}
        });
        let expression = serde_json::json!({"$expr": {
            "left": {"$ref": "steps.collect.output.count"},
            "operator": ">",
            "right": {"$ref": "vars.threshold"}
        }});
        assert_eq!(evaluate(&expression, &context).unwrap(), Value::Bool(true));
    }

    // [V1.2-INTERPOLATION] 字符串模板运行期拼接：${vars.name} 替换为实际值。
    #[test]
    fn evaluates_string_template_with_variable_replacement() {
        let context = serde_json::json!({"vars": {"name": "CloudFlow"}});
        let template = serde_json::json!({
            "$template": ["hello ", {"$ref": "vars.name"}, ", welcome"]
        });
        assert_eq!(
            evaluate(&template, &context).unwrap(),
            Value::String("hello CloudFlow, welcome".into())
        );
    }

    // [V1.2-PIPELINE] map/filter/reduce 管道运行期：filter 保留谓词命中的行、
    // map 投影字段、reduce 聚合。
    #[test]
    fn evaluates_pipeline_filter_map_reduce() {
        let context = serde_json::json!({"vars": {}});
        let pipeline = serde_json::json!({
            "$pipeline": {
                "input": {
                    "$pipeline": {
                        "input": {
                            "$pipeline": {
                                "input": {"$ref": "vars.files"},
                                "op": {"op": "filter", "predicate": {"$expr": {
                                    "left": {"$ref": "size"}, "operator": ">", "right": 100
                                }}}
                            }
                        },
                        "op": {"op": "map", "field": "size"}
                    }
                },
                "op": {"op": "reduce", "function": "sum"}
            }
        });
        // vars.files 放在上下文里，但 apply_pipeline 以输入数组为准。
        let mut ctx = context.clone();
        ctx["vars"]["files"] = serde_json::json!([
            {"name": "a", "size": 50},
            {"name": "b", "size": 200},
            {"name": "c", "size": 120}
        ]);
        let result = evaluate(&pipeline, &ctx).unwrap();
        // filter(size>100) 保留 200、120；map(size) -> [200,120]；reduce(sum) -> 320。
        assert_eq!(result.as_f64(), Some(320.0));
    }

    // [V1.2-PIPELINE] 管道输入必须是数组，否则报错。
    #[test]
    fn pipeline_rejects_non_array_input() {
        let context = serde_json::json!({"vars": {"files": "not-an-array"}});
        let pipeline = serde_json::json!({
            "$pipeline": {
                "input": {"$ref": "vars.files"},
                "op": {"op": "map", "field": "name"}
            }
        });
        assert!(evaluate(&pipeline, &context).is_err());
    }
}
