//! AST → Workflow IR v1 生成器。

use crate::ast::{RetryNode, TriggerNode, WorkflowNode};
use crate::ir::*;
use serde_json::{Map, Value};
use std::collections::BTreeMap;

pub fn compile(workflow: &WorkflowNode) -> WorkflowIrV1 {
    let nodes = workflow
        .steps
        .iter()
        .map(|step| {
            let mut outputs = BTreeMap::new();
            if let Some(output) = &step.output {
                outputs.insert(
                    output.clone(),
                    Value::String(format!("steps.{}.output", step.id)),
                );
            }
            NodeIr {
                id: step.id.clone(),
                node_type: if step.action.as_ref().map(|a| a.provider.as_str()) == Some("plugin") {
                    "plugin".into()
                } else {
                    "task".into()
                },
                name: step.name.clone(),
                action: step.action.as_ref().map(|a| ActionIr {
                    provider: a.provider.clone(),
                    service: a.service.clone(),
                    method: a.method.clone(),
                    plugin_id: a.plugin_id.clone(),
                    function: a.function.clone(),
                    version: a.version.clone(),
                    arguments: a.arguments.clone(),
                }),
                inputs: BTreeMap::new(),
                outputs,
                depends_on: step.depends_on.clone(),
                retry: step.retry.as_ref().map(retry),
                timeout: step.timeout.clone(),
                condition: step.condition.clone(),
                loop_config: None,
                parallel: None,
                error_handler: None,
            }
        })
        .collect::<Vec<_>>();
    let edges = workflow
        .steps
        .iter()
        .flat_map(|step| {
            step.depends_on.iter().map(move |from| EdgeIr {
                from: from.clone(),
                to: step.id.clone(),
            })
        })
        .collect();
    let mut variables = BTreeMap::new();
    for variable in &workflow.variables {
        variables.insert(
            variable.name.clone(),
            VariableIr {
                type_name: variable.type_name.clone(),
                required: variable.required,
                default: variable.default.clone(),
            },
        );
    }
    let trigger = match &workflow.trigger {
        TriggerNode::Manual => TriggerIr::Manual,
        TriggerNode::Schedule { cron, timezone } => TriggerIr::Schedule {
            cron: cron.clone(),
            timezone: timezone.clone(),
        },
        TriggerNode::Event { name } => TriggerIr::Event {
            event: name.clone(),
        },
        TriggerNode::Http { path } => TriggerIr::Http { path: path.clone() },
    };
    let mut extensions = BTreeMap::new();
    let handlers = workflow
        .handlers
        .iter()
        .map(|handler| {
            serde_json::json!({
                "id": &handler.id,
                "steps": handler.steps.iter().map(|step| serde_json::json!({
                    "id": &step.id,
                    "name": &step.name,
                    "dependsOn": &step.depends_on,
                    "action": step.action.as_ref().map(|action| serde_json::json!({
                        "provider": &action.provider,
                        "service": &action.service,
                        "method": &action.method,
                        "pluginId": &action.plugin_id,
                        "function": &action.function,
                        "version": &action.version,
                        "arguments": &action.arguments,
                    }))
                })).collect::<Vec<_>>()
            })
        })
        .collect::<Vec<_>>();
    if !handlers.is_empty() {
        extensions.insert("handlers".into(), Value::Array(handlers));
    }
    WorkflowIrV1 {
        api_version: "workflow.cloudflow.io/v1".into(),
        kind: "Workflow".into(),
        metadata: MetadataIr {
            name: workflow.name.clone(),
            display_name: workflow.metadata.display_name.clone(),
            description: workflow.metadata.description.clone(),
            version: workflow.metadata.version.clone(),
            ..Default::default()
        },
        spec: SpecIr {
            trigger,
            variables,
            graph: GraphIr { nodes, edges },
            outputs: BTreeMap::new(),
        },
        runtime: RuntimeIr {
            timeout_seconds: workflow
                .runtime
                .timeout
                .as_deref()
                .and_then(parse_duration_seconds),
            max_parallel: workflow.runtime.max_parallel,
            retry_policy: workflow.runtime.retry.as_ref().map(retry),
        },
        security: SecurityIr::default(),
        extensions,
    }
}

fn retry(value: &RetryNode) -> RetryIr {
    RetryIr {
        max_attempts: value.max_attempts.max(1),
        strategy: value.strategy.clone(),
    }
}
fn parse_duration_seconds(value: &str) -> Option<u64> {
    let (number, unit) = value.trim().split_at(value.trim().len().saturating_sub(1));
    let n = number.parse::<u64>().ok()?;
    Some(match unit {
        "s" => n,
        "m" => n * 60,
        "h" => n * 3600,
        "d" => n * 86400,
        _ => n / 1000,
    })
}

/// 对 IR 做最小结构校验，Runtime 和 Workflow Service 共用。
pub fn validate_ir(ir: &WorkflowIrV1) -> Vec<String> {
    let mut errors = Vec::new();
    if ir.api_version != "workflow.cloudflow.io/v1" {
        errors.push("unsupported apiVersion".into());
    }
    if ir.kind != "Workflow" {
        errors.push("kind must be Workflow".into());
    }
    let ids: std::collections::HashSet<_> = ir
        .spec
        .graph
        .nodes
        .iter()
        .map(|node| node.id.as_str())
        .collect();
    for edge in &ir.spec.graph.edges {
        if !ids.contains(edge.from.as_str()) || !ids.contains(edge.to.as_str()) {
            errors.push(format!(
                "edge references unknown node {} -> {}",
                edge.from, edge.to
            ));
        }
    }
    errors
}

#[allow(dead_code)]
fn _empty_object() -> Value {
    Value::Object(Map::new())
}
