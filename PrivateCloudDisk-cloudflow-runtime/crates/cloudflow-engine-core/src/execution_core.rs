//! 执行语义核心（双执行面共用，需求 2.11/11.2）。
//!
//! 生产执行面（宿主 crate `src/execution.rs`：持久化调度器与执行协调器，
//! async + 数据库 + Capability Agent）与开发调试执行面（本 crate `dev_exec`：
//! 纯内存执行器）**共享本模块**作为控制流语义的唯一事实来源，避免两处各自定义：
//!
//! - 步骤级条件求值（`condition_outcome`）；
//! - condition 分支提取（`condition_branches`）、try 结构解析（`parse_try_structure`）；
//! - 循环参数解析（`parse_loop_plan`）、并行批大小（`parallel_max_concurrency`）；
//! - 重试计划（`retry_max_attempts`/`retry_strategy`）与退避延迟（`backoff_delay_ms`）；
//! - 节点超时解析（`resolve_timeout`）；
//! - 控制信号分类（`ControlSignal` + 各执行面的归约函数）；
//! - 分支子树展开（`descendants`/`descendants_for_children`）。
//!
//! 本模块是**纯同步纯函数**：不依赖 async、数据库、MQ、Capability Agent，
//! 只做 IR 结构解析与参数计划计算；表达式求值委托
//! `crate::expression::evaluator`（表达式子系统唯一求值实现）。

use crate::ir::{NodeIr, RuntimeIr, WorkflowIrV1};
use serde_json::Value;
use std::time::Duration;

/// 控制信号（break/continue/return）：不是业务异常，try/catch 不捕获，
/// 沿调用栈向上传播（两执行面同一语义契约，需求 5.9/12.6）。
/// 统一驱动（`crate::engine::driver`）内部以 `ControlSignal` 直接传递；
/// 各执行面在错误类型 ↔ 信号映射处复用本枚举。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ControlSignal {
    Break,
    Continue,
    StepReturn,
}

/// 步骤级条件求值（assert/validate/condition/depends 共用，需求 5.7）：
/// 无条件 → false。求值器由调用方注入——统一驱动传 `crate::expression::evaluator::evaluate_value`
/// （生产面）或带 `enable_expressions` 开关的调试面包装（保留原值直通语义与
/// CFD-81xx 错误码包装），两执行面因此共享同一控制流语义、各自保留 I/O 与错误码。
pub fn condition_outcome_with<E, F>(node: &NodeIr, context: &Value, evaluate: F) -> Result<bool, E>
where
    F: Fn(&Value, &Value) -> Result<Value, E>,
{
    Ok(node
        .condition
        .as_ref()
        .map(|condition| {
            evaluate(condition, context).map(|value| crate::expression::evaluator::truthy(&value))
        })
        .transpose()?
        .unwrap_or(false))
}

/// 从 IR 数组值提取字符串根节点列表（try/finally/catch/branch/cases 共用）。
pub fn extract_roots(value: Option<&Value>) -> Vec<String> {
    value
        .and_then(Value::as_array)
        .map(|values| {
            values
                .iter()
                .filter_map(Value::as_str)
                .map(str::to_owned)
                .collect()
        })
        .unwrap_or_default()
}

/// condition 节点：返回（选中分支, 未选中分支）根节点列表（需求 5.7）。
pub fn condition_branches(handler: Option<&Value>, selected: bool) -> (Vec<String>, Vec<String>) {
    let object = handler.and_then(Value::as_object);
    let (active_key, inactive_key) = if selected {
        ("trueBranch", "falseBranch")
    } else {
        ("falseBranch", "trueBranch")
    };
    (
        extract_roots(object.and_then(|o| o.get(active_key))),
        extract_roots(object.and_then(|o| o.get(inactive_key))),
    )
}

/// try 节点结构解析（需求 5.9）：try/catch/finally 根 + catchBinding（默认 "error"）。
#[derive(Debug, Clone)]
pub struct TryStructure {
    pub try_roots: Vec<String>,
    pub catch_roots: Vec<String>,
    pub finally_roots: Vec<String>,
    /// catch 分支将 `{code,message}` 绑定到 `vars.<catch_binding>`。
    pub catch_binding: String,
}

pub fn parse_try_structure(handler: &Value) -> TryStructure {
    let get = |name: &str| -> Vec<String> { extract_roots(handler.get(name)) };
    TryStructure {
        try_roots: get("try"),
        catch_roots: get("catch"),
        finally_roots: get("finally"),
        catch_binding: handler
            .get("catchBinding")
            .and_then(Value::as_str)
            .unwrap_or("error")
            .to_owned(),
    }
}

/// 循环参数解析（loopConfig，需求 5.8/12.2）。
///
/// 只做结构解析；“collection 必须是 array”“iterator 缺失”等语义校验
/// 仍由执行面按自己的错误码执行（见 `collection`/`condition`/`iterator` 的 Option）。
#[derive(Debug, Clone)]
pub struct LoopPlan {
    /// "foreach" | "while" | "for-range"。
    pub kind: String,
    /// 迭代体根节点：`body` 优先，缺省用节点 children。
    pub roots: Vec<String>,
    /// 迭代上限：默认 1000，clamp(1, 10000)（确定性防线）。
    pub max_iterations: usize,
    /// foreach 集合表达式（未求值）。
    pub collection: Option<Value>,
    /// while 条件表达式（未求值）。
    pub condition: Option<Value>,
    /// foreach / for-range 迭代变量名。
    pub iterator: Option<String>,
    /// for-range 端点（未求值，[from, to)）。
    pub range_from: Option<Value>,
    pub range_to: Option<Value>,
}

pub fn parse_loop_plan(config: &Value, children: &[String]) -> LoopPlan {
    let object = config;
    let kind = object
        .get("kind")
        .and_then(Value::as_str)
        .unwrap_or("foreach")
        .to_owned();
    let roots = object
        .get("body")
        .and_then(Value::as_array)
        .map(|values| {
            values
                .iter()
                .filter_map(Value::as_str)
                .map(str::to_owned)
                .collect()
        })
        .filter(|values: &Vec<String>| !values.is_empty())
        .unwrap_or_else(|| children.to_vec());
    let max_iterations = object
        .get("maxIterations")
        .and_then(Value::as_u64)
        .unwrap_or(1_000)
        .clamp(1, 10_000) as usize;
    LoopPlan {
        kind,
        roots,
        max_iterations,
        collection: object.get("collection").cloned(),
        condition: object.get("condition").cloned(),
        iterator: object
            .get("iterator")
            .and_then(Value::as_str)
            .map(str::to_owned),
        range_from: object.get("from").cloned(),
        range_to: object.get("to").cloned(),
    }
}

/// 重试次数：`node.retry` 优先于 `runtime.retryPolicy`；默认 1，clamp(1, 10)。
pub fn retry_max_attempts(node: &NodeIr, runtime: &RuntimeIr) -> u32 {
    node.retry
        .as_ref()
        .or(runtime.retry_policy.as_ref())
        .map(|value| value.max_attempts)
        .unwrap_or(1)
        .clamp(1, 10)
}

/// 重试策略：`fixed` | `exponential`（默认 fixed）。
pub fn retry_strategy(node: &NodeIr, runtime: &RuntimeIr) -> String {
    node.retry
        .as_ref()
        .or(runtime.retry_policy.as_ref())
        .map(|value| value.strategy.clone())
        .unwrap_or_else(|| "fixed".to_owned())
}

/// 重试退避延迟（毫秒）：fixed 恒定 500ms；exponential 500ms 起步、30s 封顶
/// （两执行面同一退避策略，需求 1.11/12.x）。
pub fn backoff_delay_ms(strategy: &str, attempt: u32) -> u64 {
    if strategy == "exponential" {
        exponential_backoff_ms(
            attempt.saturating_sub(1).try_into().unwrap_or(0u8),
            500,
            30_000,
        )
    } else {
        500
    }
}

/// 指数退避原语：`base_ms * 2^attempt`（attempt 上限 16，结果封顶 max_ms）。
pub fn exponential_backoff_ms(attempt: u8, base_ms: u64, max_ms: u64) -> u64 {
    let exponent = u32::from(attempt.min(16));
    base_ms
        .saturating_mul(2_u64.saturating_pow(exponent))
        .min(max_ms)
}

/// 节点超时：`node.timeout` → `runtime.timeoutSeconds` → default；上限 3600s。
pub fn resolve_timeout(node: &NodeIr, runtime: &RuntimeIr, default: Duration) -> Duration {
    crate::expression::evaluator::parse_duration(node.timeout.as_deref())
        .or_else(|| runtime.timeout_seconds.map(Duration::from_secs))
        .unwrap_or(default)
        .min(Duration::from_secs(3600))
}

/// 并行批大小：分支级 `parallel.maxConcurrency` 优先于全局 `runtime.maxParallel`
/// （均 clamp 到 1..=32，需求 1.13/12.4）。
pub fn parallel_max_concurrency(node: &NodeIr, runtime: &RuntimeIr) -> usize {
    let configured = node
        .parallel
        .as_ref()
        .and_then(Value::as_object)
        .and_then(|config| config.get("maxConcurrency"))
        .and_then(Value::as_u64);
    let global_max = runtime.max_parallel.unwrap_or(1).clamp(1, 32) as u64;
    configured.map_or(global_max, |value| value.clamp(1, 32)) as usize
}

/// 分支子树展开：从根节点沿 children 收集全部子孙 ID（含根自身，去重，
/// 保持 IR 声明顺序）。condition 未选中分支与 try/loop 动态子图终结标记共用。
pub fn descendants(ir: &WorkflowIrV1, root: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut stack = vec![root.to_owned()];
    while let Some(current) = stack.pop() {
        if out.contains(&current) {
            continue;
        }
        out.push(current.clone());
        if let Some(node) = ir.spec.graph.nodes.iter().find(|node| node.id == current) {
            for child in &node.children {
                stack.push(child.clone());
            }
        }
    }
    out
}

/// 节点 children 的全部子孙（不含 children 自身）。
pub fn descendants_for_children(ir: &WorkflowIrV1, children: &[String]) -> Vec<String> {
    children
        .iter()
        .flat_map(|child| descendants(ir, child))
        .collect()
}
