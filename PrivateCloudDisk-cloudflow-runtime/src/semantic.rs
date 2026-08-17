//! CloudFlow 语义校验：引用、DAG、能力和基础资源上限。

use crate::ast::{ActionNode, Span, WorkflowNode};
use crate::diagnostic::Diagnostic;
use std::collections::{HashMap, HashSet};

pub trait CapabilityCatalog {
    fn contains(&self, key: &str) -> bool;
}

#[derive(Default)]
pub struct InMemoryCapabilityCatalog {
    keys: HashSet<String>,
}
impl InMemoryCapabilityCatalog {
    pub fn insert(&mut self, key: &str) {
        self.keys.insert(key.to_string());
    }
}
impl CapabilityCatalog for InMemoryCapabilityCatalog {
    fn contains(&self, key: &str) -> bool {
        self.keys.contains(key)
    }
}

pub fn validate(
    workflow: &WorkflowNode,
    catalog: &dyn CapabilityCatalog,
    source: &str,
    filename: &str,
) -> Vec<Diagnostic> {
    let mut diagnostics = Vec::new();
    if workflow.steps.is_empty() {
        diagnostics.push(diag(
            "CF2001",
            "SEMANTIC_ERROR",
            "工作流至少需要一个 step",
            source,
            filename,
            workflow.span,
            vec![],
            None,
        ));
    }
    if workflow.steps.len() > 200 {
        diagnostics.push(diag(
            "CF2101",
            "RESOURCE_LIMIT",
            "步骤数量不能超过 200",
            source,
            filename,
            workflow.span,
            vec![],
            None,
        ));
    }
    let mut ids = HashSet::new();
    let mut graph = HashMap::<String, Vec<String>>::new();
    for step in &workflow.steps {
        if !ids.insert(step.id.clone()) {
            diagnostics.push(diag(
                "CF2001",
                "SEMANTIC_ERROR",
                format!("步骤 ID 重复：{}", step.id),
                source,
                filename,
                step.span,
                vec![],
                Some("为每个 step 使用唯一的小写标识".into()),
            ));
        }
        if let Some(action) = &step.action {
            let key = action_key(action);
            // 未提供能力快照时，Runtime 只做语法/DAG校验；提供快照后才强制检查注册表。
            if catalog.contains("__catalog_enabled__") && !catalog.contains(&key) {
                diagnostics.push(diag(
                    "CF3001",
                    "CAPABILITY_ERROR",
                    format!("能力不存在或未启用：{key}"),
                    source,
                    filename,
                    step.span,
                    vec![],
                    Some("请在 Capability Hub 注册该能力，或检查插件版本".into()),
                ));
            }
        }
        graph.insert(step.id.clone(), step.depends_on.clone());
    }
    for (id, deps) in &graph {
        for dep in deps {
            if !graph.contains_key(dep) {
                diagnostics.push(diag(
                    "CF2002",
                    "REFERENCE_ERROR",
                    format!("步骤 {id} 依赖不存在：{dep}"),
                    source,
                    filename,
                    workflow.span,
                    vec![],
                    Some("depends_on 必须引用同一 workflow 中的 step".into()),
                ));
            }
        }
    }
    if has_cycle(&graph) {
        diagnostics.push(diag(
            "CF2002",
            "REFERENCE_ERROR",
            "工作流依赖存在循环，无法构建 DAG",
            source,
            filename,
            workflow.span,
            vec![],
            Some("删除循环依赖，保证所有边最终指向前置步骤".into()),
        ));
    }
    diagnostics
}

pub fn action_key(action: &ActionNode) -> String {
    if action.provider == "plugin" {
        format!(
            "plugin:{}:{}",
            action.plugin_id.as_deref().unwrap_or_default(),
            action.function.as_deref().unwrap_or_default()
        )
    } else if let (Some(service), Some(method)) = (&action.service, &action.method) {
        format!("{}:{}.{}", action.provider, service, method)
    } else {
        action.provider.clone()
    }
}

fn has_cycle(graph: &HashMap<String, Vec<String>>) -> bool {
    fn visit(
        id: &str,
        graph: &HashMap<String, Vec<String>>,
        state: &mut HashMap<String, u8>,
    ) -> bool {
        match state.get(id).copied().unwrap_or(0) {
            1 => return true,
            2 => return false,
            _ => {}
        }
        state.insert(id.into(), 1);
        if graph
            .get(id)
            .into_iter()
            .flatten()
            .any(|dep| visit(dep, graph, state))
        {
            return true;
        }
        state.insert(id.into(), 2);
        false
    }
    let mut state = HashMap::new();
    graph.keys().any(|id| visit(id, graph, &mut state))
}

fn diag(
    code: &str,
    category: &str,
    message: impl Into<String>,
    source: &str,
    filename: &str,
    span: Span,
    suggestions: Vec<String>,
    help: Option<String>,
) -> Diagnostic {
    Diagnostic::new(
        code,
        category,
        message,
        source,
        filename,
        span.start,
        span.end,
        suggestions,
        help,
    )
}
