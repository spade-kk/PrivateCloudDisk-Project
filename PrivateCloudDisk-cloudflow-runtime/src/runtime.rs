//! CloudFlow IR 运行时基础层：IR 校验、DAG 排序和可恢复状态模型。
//!
//! 具体 Capability/Plugin/API 适配器通过后续 Agent 注入；本模块不直接执行用户代码。

use crate::{compiler::validate_ir, ir::WorkflowIrV1};
use std::collections::{HashMap, HashSet, VecDeque};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WorkflowStatus {
    Created,
    Ready,
    Running,
    Waiting,
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
        self.ir
            .spec
            .graph
            .nodes
            .iter()
            .filter(|node| {
                !self.execution.completed.contains(&node.id)
                    && node
                        .depends_on
                        .iter()
                        .all(|dep| self.execution.completed.contains(dep))
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
