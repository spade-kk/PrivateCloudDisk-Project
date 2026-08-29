//! 执行上下文（需求 1.20/4.2）：工作流级元数据与步骤级调用上下文。
//!
//! 执行引擎核心不持有任何全局可变状态；一切跨层传递的元数据都放在
//! `ExecutionContext`（工作流级）与 `StepContext`（单次动作调用）中。

/// 工作流级执行上下文：身份、权限与追踪信息。
///
/// 生产面来自执行任务行（`StoredExecution`）；调试面使用空值/占位值。
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct ExecutionContext {
    /// 执行实例 ID（生产：执行任务 ID；调试：`dev-execution`）。
    pub execution_id: String,
    /// 发起用户（Agent 调用鉴权上下文）。
    pub user_id: String,
    /// 空间 ID（Agent 调用鉴权上下文；空串表示无空间）。
    pub space_id: String,
    /// 工作流声明的权限（Agent 最小权限校验输入）。
    pub declared_permissions: Vec<String>,
    /// 实际授予的权限（Agent 最小权限校验输入）。
    pub granted_permissions: Vec<String>,
    /// 追踪 ID（全链路日志关联）。
    pub trace_id: String,
}

/// 步骤引用：逻辑节点 ID + 实例 ID。
///
/// 循环迭代体使用实例前缀（如 `loop-42[3].process`）保证并行迭代的
/// 检查点独立审计（生产 DB 步骤行）；调试面按逻辑 ID 记录。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StepRef {
    /// IR 中的节点 ID（调度/簿记始终使用逻辑 ID）。
    pub logical_id: String,
    /// 带实例前缀的 ID（生产步骤行；顶层节点与逻辑 ID 相同）。
    pub instance_id: String,
}

impl StepRef {
    pub fn top_level(id: &str) -> Self {
        Self {
            logical_id: id.into(),
            instance_id: id.into(),
        }
    }

    pub fn nested(logical_id: &str, prefix: Option<&str>) -> Self {
        let instance_id = match prefix {
            Some(prefix) if !prefix.is_empty() => format!("{prefix}.{logical_id}"),
            _ => logical_id.into(),
        };
        Self {
            logical_id: logical_id.into(),
            instance_id,
        }
    }
}

/// 单次动作调用上下文（`ActionExecutor::execute` 入参，需求 4.2/4.3）。
#[derive(Debug, Clone)]
pub struct StepContext {
    /// 工作流级上下文。
    pub execution: ExecutionContext,
    /// 逻辑节点 ID。
    pub node_id: String,
    /// 带实例前缀的步骤 ID（Agent `stepId`）。
    pub step_id: String,
    /// 第几次尝试（生产：检查点表 attempt；调试：本地尝试序号）。
    pub attempt: u64,
    /// 动作定义。
    pub action: crate::ir::ActionIr,
    /// 已求值的动作入参。
    pub input: serde_json::Value,
    /// 节点超时（驱动按 IR/运行时配置解析后传入；执行器自行实施）。
    pub timeout: std::time::Duration,
}
