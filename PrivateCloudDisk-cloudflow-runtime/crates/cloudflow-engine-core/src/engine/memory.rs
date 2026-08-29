//! 内存依赖实现（需求 2.5/3.5/6.3）：纯内存状态存储、日志收集与 stdout 日志。
//!
//! 开发调试执行面（`crate::dev_exec`）使用本模块 + `MockActionExecutor` 构成
//! 完整可运行的内存执行环境：无数据库、无 MQ、无网络。
//! 生产执行面使用宿主 crate 的 MySQL/Agent/tracing 实现（接口相同）。

use crate::engine::context::{ExecutionContext, StepRef};
use crate::engine::deps::{Clock, ControlFlags, LogEntry, LogLevel, LogSink, StateStore};
use crate::engine::error::ExecutionError;
use crate::engine::result::{NodeError, NodeFinish, NodeStatus, TerminalKind, WorkflowEndStatus};
use crate::ir::WorkflowIrV1;
use serde_json::{Map, Value};
use std::collections::{BTreeMap, HashSet};
use std::sync::{Arc, Mutex};

/// 纯内存状态存储（需求 2.5/2.6：无锁外部 I/O、支持快照与恢复）。
pub struct InMemoryStateStore {
    ir: Arc<WorkflowIrV1>,
    data: Mutex<MemState>,
}

struct MemState {
    vars: Value,
    /// 步骤输出（`steps.<id>.output` 数据源）。
    outputs: BTreeMap<String, Value>,
    /// 节点结果簿记（调试面 DevNodeResult 的内存形态）。
    results: BTreeMap<String, NodeRecord>,
    /// 节点 begin_step 尝试序号。
    attempts: BTreeMap<String, u64>,
    /// 终结记录（finish_workflow）。
    terminal: Option<(WorkflowEndStatus, Option<String>, String)>,
}

/// 节点结果簿记（调试面 `DevNodeResult` 的内存形态；`node_records` 导出）。
#[derive(Debug, Clone)]
pub struct NodeRecord {
    pub node_type: String,
    pub depends_on: Vec<String>,
    pub status: RecordStatus,
    pub started_at_ms: u64,
    pub duration_ms: u64,
    pub input: Option<Value>,
    pub output: Option<Value>,
    pub attempts: u32,
    pub error: Option<crate::engine::result::NodeError>,
}

/// 节点簿记状态（与调试面 `DevTaskStatus` 同序）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RecordStatus {
    Pending,
    Running,
    Success,
    Failed,
    Skipped,
    Waiting,
}

impl InMemoryStateStore {
    pub fn new(ir: Arc<WorkflowIrV1>) -> Self {
        Self {
            ir,
            data: Mutex::new(MemState {
                vars: Value::Object(Map::new()),
                outputs: BTreeMap::new(),
                results: BTreeMap::new(),
                attempts: BTreeMap::new(),
                terminal: None,
            }),
        }
    }

    fn with_data<R>(&self, f: impl FnOnce(&mut MemState) -> R) -> R {
        f(&mut self.data.lock().expect("内存状态锁中毒"))
    }

    /// 快照（需求 2.5/4.x 断点恢复：vars + 步骤输出 + 节点结果）。
    pub fn snapshot(&self) -> Value {
        let state = self.data.lock().expect("内存状态锁中毒");
        let status_str: &str = match state.terminal {
            Some((status, ..)) => match status {
                WorkflowEndStatus::Success => "success",
                WorkflowEndStatus::Failed | WorkflowEndStatus::Cancelled => "failed",
                WorkflowEndStatus::Waiting => "waiting",
                WorkflowEndStatus::Timeout => "timeout",
                WorkflowEndStatus::Breakpoint => "breakpoint",
            },
            None => "Running",
        };
        let steps = state
            .outputs
            .iter()
            .map(|(id, value)| (id.clone(), serde_json::json!({ "output": value })))
            .collect::<Map<String, Value>>();
        serde_json::json!({
            "vars": state.vars,
            "steps": steps,
            "status": status_str,
        })
    }

    /// 节点结果导出（调试面映射 DevNodeResult）。
    pub fn node_records(&self) -> BTreeMap<String, NodeRecord> {
        self.data.lock().expect("内存状态锁中毒").results.clone()
    }

    /// 终结记录。
    pub fn terminal(&self) -> Option<(WorkflowEndStatus, Option<String>, String)> {
        self.data.lock().expect("内存状态锁中毒").terminal.clone()
    }

    /// 上下文部件导出（调试面 `DevExecutionResult` 快照）：`(vars, steps)`，
    /// `steps` 与表达式上下文同形：`{id: {"output": value}}`
    /// （仅含动作输出与 skip 空占位——历史调试面语义）。
    pub fn context_parts(&self) -> (Value, Map<String, Value>) {
        let state = self.data.lock().expect("内存状态锁中毒");
        let steps = state
            .outputs
            .iter()
            .map(|(id, value)| (id.clone(), serde_json::json!({ "output": value })))
            .collect();
        (state.vars.clone(), steps)
    }
}

#[async_trait::async_trait]
impl StateStore for InMemoryStateStore {
    async fn restore_completed(
        &self,
        _ctx: &ExecutionContext,
    ) -> Result<Vec<String>, ExecutionError> {
        Ok(Vec::new())
    }

    async fn poll_control(&self, _ctx: &ExecutionContext) -> Result<ControlFlags, ExecutionError> {
        Ok(ControlFlags::default())
    }

    async fn build_context(&self, _ctx: &ExecutionContext) -> Result<Value, ExecutionError> {
        let state = self.data.lock().expect("内存状态锁中毒");
        let steps = state
            .outputs
            .iter()
            .map(|(id, value)| (id.clone(), serde_json::json!({ "output": value })))
            .collect::<Map<String, Value>>();
        Ok(serde_json::json!({
            "vars": state.vars,
            "steps": steps
        }))
    }

    fn init_variables(&self, _ctx: &ExecutionContext, vars: &Value) -> Result<(), ExecutionError> {
        let ir = self.ir.clone();
        self.with_data(|state| {
            state.vars = vars.clone();
            state.results = ir
                .spec
                .graph
                .nodes
                .iter()
                .map(|node| {
                    (
                        node.id.clone(),
                        NodeRecord {
                            node_type: node.node_type.clone(),
                            depends_on: node.depends_on.clone(),
                            status: RecordStatus::Pending,
                            started_at_ms: 0,
                            duration_ms: 0,
                            input: None,
                            output: None,
                            attempts: 0,
                            error: None,
                        },
                    )
                })
                .collect();
        });
        Ok(())
    }

    async fn begin_step(
        &self,
        _ctx: &ExecutionContext,
        step: &StepRef,
        _input: &Value,
    ) -> Result<u64, ExecutionError> {
        Ok(self.with_data(|state| {
            let next = state
                .attempts
                .get(&step.logical_id)
                .copied()
                .unwrap_or(0)
                .saturating_add(1);
            state.attempts.insert(step.logical_id.clone(), next);
            // 与历史调试面一致：节点结果 attempts 反映最后一次已开始的尝试
            // （成功动作随后经 record_step_output 覆盖为成功尝试序号）。
            if let Some(record) = state.results.get_mut(&step.logical_id) {
                record.attempts = next as u32;
            }
            next
        }))
    }

    async fn finish_step_success(
        &self,
        _ctx: &ExecutionContext,
        step: &StepRef,
        _attempt: u64,
        output: &Value,
        _duration_ms: u64,
    ) -> Result<(), ExecutionError> {
        // 调试面：仅登记节点输出载荷（`DevNodeResult.output`）；
        // 表达式上下文 steps 映射只含动作输出与 skip 空占位（历史调试面语义）。
        self.with_data(|state| {
            if let Some(record) = state.results.get_mut(&step.logical_id) {
                record.output = Some(output.clone());
            }
        });
        Ok(())
    }

    async fn finish_step_failure(
        &self,
        _ctx: &ExecutionContext,
        step: &StepRef,
        _attempt: u64,
        code: &str,
        summary: &str,
        retryable: bool,
        _duration_ms: u64,
    ) -> Result<(), ExecutionError> {
        self.with_data(|state| {
            if let Some(record) = state.results.get_mut(&step.logical_id) {
                record.error = Some(crate::engine::result::NodeError {
                    code: code.into(),
                    message: summary.into(),
                    detail: Some(format!("retryable={retryable}")),
                });
            }
        });
        Ok(())
    }

    async fn finish_step_skipped(
        &self,
        _ctx: &ExecutionContext,
        step: &StepRef,
        _reason: &str,
    ) -> Result<(), ExecutionError> {
        // 调试面：已执行成功/失败的节点不被 Skipped 覆盖（与历史行为一致）。
        self.with_data(|state| {
            if let Some(record) = state.results.get_mut(&step.logical_id) {
                if !matches!(record.status, RecordStatus::Success | RecordStatus::Failed) {
                    record.status = RecordStatus::Skipped;
                }
            }
        });
        Ok(())
    }

    async fn on_waiting(
        &self,
        _ctx: &ExecutionContext,
        _step: &StepRef,
        _payload: &Value,
    ) -> Result<(), ExecutionError> {
        // 调试面：Waiting 状态经 `node_finished` 记录；挂起请求无对象（无执行行）。
        Ok(())
    }

    async fn finish_workflow(
        &self,
        _ctx: &ExecutionContext,
        status: WorkflowEndStatus,
        code: Option<&str>,
        message: &str,
    ) -> Result<(), ExecutionError> {
        self.with_data(|state| {
            state.terminal = Some((status, code.map(str::to_owned), message.to_owned()));
        });
        Ok(())
    }

    fn node_started(&self, _ctx: &ExecutionContext, step: &StepRef, _input: &Value) {
        self.with_data(|state| {
            if let Some(record) = state.results.get_mut(&step.logical_id) {
                record.status = RecordStatus::Running;
            }
        });
    }

    fn node_finished(&self, _ctx: &ExecutionContext, step: &StepRef, finish: &NodeFinish) {
        self.with_data(|state| {
            if let Some(record) = state.results.get_mut(&step.logical_id) {
                record.status = match finish.status {
                    crate::engine::result::NodeStatus::Success => RecordStatus::Success,
                    crate::engine::result::NodeStatus::Failed => RecordStatus::Failed,
                    crate::engine::result::NodeStatus::Skipped => {
                        if matches!(record.status, RecordStatus::Success | RecordStatus::Failed) {
                            record.status
                        } else {
                            RecordStatus::Skipped
                        }
                    }
                    crate::engine::result::NodeStatus::Waiting => RecordStatus::Waiting,
                };
                record.started_at_ms = finish.started_at_ms;
                record.duration_ms = finish.duration_ms;
                if let Some(output) = &finish.output {
                    record.output = Some(output.clone());
                }
                if finish.attempts > 0 {
                    record.attempts = finish.attempts;
                }
                if let Some(error) = &finish.error {
                    record.error = Some(error.clone());
                }
            }
        });
    }

    fn skip_pending(
        &self,
        _ctx: &ExecutionContext,
        _kind: &TerminalKind,
        exclude: &HashSet<String>,
    ) -> Vec<String> {
        self.with_data(|state| {
            let pending: Vec<String> = state
                .results
                .iter()
                .filter(|(id, record)| {
                    !exclude.contains(*id)
                        && matches!(record.status, RecordStatus::Pending | RecordStatus::Running)
                })
                .map(|(id, _)| id.clone())
                .collect();
            for id in &pending {
                if let Some(record) = state.results.get_mut(id) {
                    record.status = RecordStatus::Skipped;
                    record.error = None;
                }
            }
            pending
        })
    }

    fn record_step_input(&self, _ctx: &ExecutionContext, step: &StepRef, input: &Value) {
        self.with_data(|state| {
            if let Some(record) = state.results.get_mut(&step.logical_id) {
                record.input = Some(input.clone());
            }
        });
    }

    fn record_step_output(
        &self,
        _ctx: &ExecutionContext,
        step: &StepRef,
        output: &Value,
        attempts: u32,
    ) {
        self.with_data(|state| {
            state
                .outputs
                .insert(step.logical_id.clone(), output.clone());
            if let Some(record) = state.results.get_mut(&step.logical_id) {
                record.output = Some(output.clone());
                record.attempts = attempts;
            }
        });
    }

    fn record_null_step_output(&self, _ctx: &ExecutionContext, step: &StepRef) {
        self.with_data(|state| {
            state.outputs.insert(step.logical_id.clone(), Value::Null);
        });
    }

    fn node_outcome(
        &self,
        _ctx: &ExecutionContext,
        step: &StepRef,
        status: NodeStatus,
        error: Option<NodeError>,
    ) {
        self.with_data(|state| {
            let Some(record) = state.results.get_mut(&step.logical_id) else {
                return;
            };
            record.status = match status {
                NodeStatus::Success => RecordStatus::Success,
                NodeStatus::Failed => RecordStatus::Failed,
                NodeStatus::Waiting => RecordStatus::Waiting,
                NodeStatus::Skipped => {
                    if matches!(record.status, RecordStatus::Success | RecordStatus::Failed) {
                        record.status
                    } else {
                        RecordStatus::Skipped
                    }
                }
            };
            if let Some(error) = error {
                record.error = Some(error);
            }
        });
    }

    fn refresh_iteration_context(&self, _ctx: &ExecutionContext, context: &mut Value) {
        // 调试面 while 语义：每轮取最新内存快照（步骤输出即时可见）。
        let state = self.data.lock().expect("内存状态锁中毒");
        let steps = state
            .outputs
            .iter()
            .map(|(id, value)| (id.clone(), serde_json::json!({ "output": value })))
            .collect::<Map<String, Value>>();
        *context = serde_json::json!({
            "vars": state.vars,
            "steps": steps
        });
    }
}

/// 纯内存日志收集（需求 3.5/3.6：追加 + 级别过滤；节点过滤可选）。
pub struct InMemoryLogSink {
    entries: Mutex<Vec<MemoryLogRecord>>,
    clock: Arc<dyn Clock>,
    min_level: LogLevel,
    node_filter: Option<String>,
}

/// 内存日志条目（调试面 DevLogEntry 的内存形态；含虚拟时间戳与序号）。
#[derive(Debug, Clone)]
pub struct MemoryLogRecord {
    pub seq: u64,
    pub ts_ms: u64,
    pub level: LogLevel,
    pub node: Option<String>,
    pub message: String,
}

impl InMemoryLogSink {
    pub fn new(clock: Arc<dyn Clock>) -> Self {
        Self {
            entries: Mutex::new(Vec::new()),
            clock,
            min_level: LogLevel::Debug,
            node_filter: None,
        }
    }

    /// 设置级别下限与节点过滤（None=全部）。
    pub fn with_filter(mut self, min_level: LogLevel, node_filter: Option<String>) -> Self {
        self.min_level = min_level;
        self.node_filter = node_filter;
        self
    }

    /// 同步写入一条日志（调试面入口日志等无需异步上下文的场景；
    /// 过滤规则与 `LogSink::log` 一致）。
    pub fn push(&self, level: LogLevel, node: Option<String>, message: String) {
        self.log(LogEntry::new(level, node, message));
    }

    /// 导出全部日志记录。
    pub fn records(&self) -> Vec<MemoryLogRecord> {
        self.entries.lock().expect("内存日志锁中毒").clone()
    }
}

impl LogSink for InMemoryLogSink {
    fn log(&self, entry: LogEntry) {
        if entry.level < self.min_level {
            return;
        }
        if let Some(filter) = &self.node_filter {
            if entry.node.as_deref().is_some_and(|node| node != filter) {
                return;
            }
        }
        let mut entries = self.entries.lock().expect("内存日志锁中毒");
        let seq = entries.len() as u64 + 1;
        entries.push(MemoryLogRecord {
            seq,
            ts_ms: self.clock.now_ms(),
            level: entry.level,
            node: entry.node,
            message: entry.message,
        });
    }
}

/// stdout 日志收集（需求 3.7；可与 `InMemoryLogSink` 组合使用）。
#[derive(Debug, Clone, Copy, Default)]
pub struct StdoutLogSink;

impl LogSink for StdoutLogSink {
    fn log(&self, entry: LogEntry) {
        let node = entry.node.as_deref().unwrap_or("-");
        println!("[{:?}] ({node}) {}", entry.level, entry.message);
    }
}
