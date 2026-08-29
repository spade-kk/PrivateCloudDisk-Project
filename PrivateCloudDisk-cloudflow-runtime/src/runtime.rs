//! CloudFlow IR 运行时基础层：IR 校验、DAG 排序和可恢复状态模型。
//!
//! 具体 Capability/Plugin/API 适配器通过后续 Agent 注入；本模块不直接执行用户代码。

use crate::{
    compiler::validate_ir,
    ir::{NodeIr, WorkflowIrV1},
};
use std::collections::{HashMap, HashSet, VecDeque};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WorkflowStatus {
    Created,
    Ready,
    Running,
    Waiting,
    /// WAITING 的细分可观测态；持久化主状态仍用 WAITING 保持 V1 数据库兼容。
    WaitingApproval,
    Success,
    Failed,
    Cancelled,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TaskStatus {
    Pending,
    Running,
    Retrying,
    Success,
    Failed,
    Skipped,
    LoopIterating,
    WaitingApproval,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExecutionSnapshot {
    pub execution_id: String,
    pub status: WorkflowStatus,
    pub completed: HashSet<String>,
    pub row_version: u64,
}

pub struct RuntimeEngine {
    ir: WorkflowIrV1,
    execution: ExecutionSnapshot,
    task_states: HashMap<String, TaskStatus>,
}

impl RuntimeEngine {
    pub fn load(execution_id: impl Into<String>, ir: WorkflowIrV1) -> Result<Self, Vec<String>> {
        let errors = validate_ir(&ir);
        if !errors.is_empty() {
            return Err(errors);
        }
        let task_states = ir
            .spec
            .graph
            .nodes
            .iter()
            .map(|node| (node.id.clone(), TaskStatus::Pending))
            .collect();
        Ok(Self {
            ir,
            execution: ExecutionSnapshot {
                execution_id: execution_id.into(),
                status: WorkflowStatus::Ready,
                completed: HashSet::new(),
                row_version: 0,
            },
            task_states,
        })
    }

    pub fn execution(&self) -> &ExecutionSnapshot {
        &self.execution
    }

    /// 返回当前所有依赖已完成的节点；结果稳定按 IR 节点声明顺序排列。
    pub fn ready_nodes(&self) -> Vec<String> {
        // AUDIT FIX [6.8]：IR 规范以 graph.edges 作为 DAG 的权威依赖关系。
        // 原行为只读取节点 dependsOn，控制流展开后会让分支节点过早运行；
        // 新行为合并 edges 与兼容字段 dependsOn，避免调度器绕过编译器生成的控制依赖。
        self.ir
            .spec
            .graph
            .nodes
            .iter()
            .filter(|node| {
                // foreach/while/try 内部节点由所属控制节点根据运行时上下文调度；若把它们
                // 当普通 DAG 节点领取，会造成一次静态执行 + N 次动态执行的重复副作用。
                node.control_parent.is_none()
                    && !self.execution.completed.contains(&node.id)
                    && node
                        .depends_on
                        .iter()
                        .all(|dep| self.execution.completed.contains(dep))
                    && self
                        .ir
                        .spec
                        .graph
                        .edges
                        .iter()
                        .filter(|edge| edge.to == node.id)
                        .all(|edge| self.execution.completed.contains(&edge.from))
            })
            .map(|node| node.id.clone())
            .collect()
    }

    /// [V1.2-COND-DEPENDS] 条件依赖感知的就绪节点计算。
    /// `holds(node)` 求值节点的条件依赖：返回 Some(true)=条件成立（需等待依赖）、
    /// Some(false)=条件不成立（依赖被豁免，节点可直接调度）、None=尚无法确定（保守等待）。
    /// 仍保持节点声明顺序稳定。
    pub fn ready_nodes_conditional(&self, holds: &dyn Fn(&NodeIr) -> Option<bool>) -> Vec<String> {
        self.ir
            .spec
            .graph
            .nodes
            .iter()
            .filter(|node| {
                if node.control_parent.is_some() || self.execution.completed.contains(&node.id) {
                    return false;
                }
                let waive = node.depends_condition.is_some() && matches!(holds(node), Some(false));
                let deps_done = waive
                    || node
                        .depends_on
                        .iter()
                        .all(|dep| self.execution.completed.contains(dep));
                let edges_done = waive
                    || self
                        .ir
                        .spec
                        .graph
                        .edges
                        .iter()
                        .filter(|edge| edge.to == node.id)
                        .all(|edge| self.execution.completed.contains(&edge.from));
                deps_done && edges_done
            })
            .map(|node| node.id.clone())
            .collect()
    }

    pub fn mark_running(&mut self, node_id: &str) -> bool {
        self.task_states
            .get_mut(node_id)
            .map(|status| {
                *status = TaskStatus::Running;
                self.execution.status = WorkflowStatus::Running;
                self.execution.row_version += 1;
                true
            })
            .unwrap_or(false)
    }
    pub fn mark_success(&mut self, node_id: &str) -> bool {
        if let Some(status) = self.task_states.get_mut(node_id) {
            *status = TaskStatus::Success;
            self.execution.completed.insert(node_id.into());
            self.execution.row_version += 1;
            if self.execution.completed.len() == self.task_states.len() {
                self.execution.status = WorkflowStatus::Success;
            }
            return true;
        }
        false
    }

    pub fn mark_skipped(&mut self, node_id: &str) -> bool {
        if let Some(status) = self.task_states.get_mut(node_id) {
            *status = TaskStatus::Skipped;
            self.execution.completed.insert(node_id.into());
            self.execution.row_version += 1;
            if self.execution.completed.len() == self.task_states.len() {
                self.execution.status = WorkflowStatus::Success;
            }
            return true;
        }
        false
    }
    /// 从数据库步骤检查点恢复已完成节点；仅接受 IR 中存在的节点标识。
    pub fn restore_completed<I>(&mut self, completed: I)
    where
        I: IntoIterator<Item = String>,
    {
        for node_id in completed {
            if let Some(status) = self.task_states.get_mut(&node_id) {
                *status = TaskStatus::Success;
                self.execution.completed.insert(node_id);
            }
        }
        if self.execution.completed.len() == self.task_states.len() {
            self.execution.status = WorkflowStatus::Success;
        }
    }

    pub fn node(&self, node_id: &str) -> Option<&crate::ir::NodeIr> {
        self.ir
            .spec
            .graph
            .nodes
            .iter()
            .find(|node| node.id == node_id)
    }

    pub fn is_complete(&self) -> bool {
        self.execution.completed.len() == self.task_states.len()
    }
    pub fn cancel(&mut self) {
        self.execution.status = WorkflowStatus::Cancelled;
        self.execution.row_version += 1;
    }
    pub fn task_status(&self, node_id: &str) -> Option<TaskStatus> {
        self.task_states.get(node_id).copied()
    }

    pub fn topological_order(&self) -> Result<Vec<String>, String> {
        let mut indegree: HashMap<String, usize> = self
            .ir
            .spec
            .graph
            .nodes
            .iter()
            .map(|node| (node.id.clone(), 0))
            .collect();
        let mut outgoing: HashMap<String, Vec<String>> = HashMap::new();
        for edge in &self.ir.spec.graph.edges {
            *indegree.entry(edge.to.clone()).or_default() += 1;
            outgoing
                .entry(edge.from.clone())
                .or_default()
                .push(edge.to.clone());
        }
        let mut queue = VecDeque::from_iter(
            indegree
                .iter()
                .filter_map(|(id, degree)| (*degree == 0).then_some(id.clone())),
        );
        let mut order = Vec::new();
        while let Some(id) = queue.pop_front() {
            order.push(id.clone());
            for next in outgoing.get(&id).into_iter().flatten() {
                let degree = indegree.get_mut(next).expect("validated edge");
                *degree -= 1;
                if *degree == 0 {
                    queue.push_back(next.clone());
                }
            }
        }
        if order.len() == indegree.len() {
            Ok(order)
        } else {
            Err("CF2002: workflow graph contains a cycle".into())
        }
    }
}
