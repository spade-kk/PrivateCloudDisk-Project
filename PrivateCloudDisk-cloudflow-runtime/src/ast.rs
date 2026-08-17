//! CloudFlow 编译器内部 AST。
//!
//! AST 与网络 IR 有意分离：AST 保留源码 Span，IR 只保留稳定的机器契约。

use serde_json::Value;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct Span {
    pub start: usize,
    pub end: usize,
    pub line: usize,
    pub column: usize,
    pub end_line: usize,
    pub end_column: usize,
}

#[derive(Debug, Clone, PartialEq)]
pub struct WorkflowNode {
    pub name: String,
    pub metadata: MetadataNode,
    pub variables: Vec<VariableDecl>,
    pub trigger: TriggerNode,
    pub runtime: RuntimeConfig,
    pub steps: Vec<StepNode>,
    pub handlers: Vec<HandlerNode>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq, Default)]
pub struct MetadataNode {
    pub display_name: Option<String>,
    pub description: Option<String>,
    pub version: Option<String>,
    pub author: Option<String>,
    pub tags: Vec<String>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub enum TriggerNode {
    Manual,
    Schedule {
        cron: String,
        timezone: Option<String>,
    },
    Event {
        name: String,
    },
    Http {
        path: String,
    },
}

impl Default for TriggerNode {
    fn default() -> Self {
        Self::Manual
    }
}

#[derive(Debug, Clone, PartialEq, Default)]
pub struct RuntimeConfig {
    pub timeout: Option<String>,
    pub max_parallel: Option<u32>,
    pub retry: Option<RetryNode>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct VariableDecl {
    pub name: String,
    pub type_name: String,
    pub required: bool,
    pub default: Option<Value>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq, Default)]
pub struct StepNode {
    pub id: String,
    pub name: Option<String>,
    pub action: Option<ActionNode>,
    pub depends_on: Vec<String>,
    pub condition: Option<String>,
    pub retry: Option<RetryNode>,
    pub timeout: Option<String>,
    pub output: Option<String>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq, Default)]
pub struct ActionNode {
    pub provider: String,
    pub service: Option<String>,
    pub method: Option<String>,
    pub plugin_id: Option<String>,
    pub function: Option<String>,
    pub version: Option<String>,
    pub arguments: Value,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub struct RetryNode {
    pub max_attempts: u32,
    pub strategy: String,
}

#[derive(Debug, Clone, PartialEq, Default)]
pub struct HandlerNode {
    pub id: String,
    pub steps: Vec<StepNode>,
    pub span: Span,
}
