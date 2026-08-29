//! CloudFlow DSL 编译核心：Pest → AST → 语义分析 → Workflow IR v1。
//!
//! 本 crate 不连接业务数据库，也不直接执行用户脚本；身份、空间权限和能力快照由控制面
//! 传入，执行面由 Runtime 的 action adapter 负责。

pub mod agent;
pub mod ast_printer;
pub mod ast;
pub mod broker;
pub mod compiler;
pub mod config;
pub mod diagnostic;
pub mod engine;
pub mod error;
pub mod execution;
pub mod http;
pub mod ir;
pub mod observability;
pub mod parser;
pub mod persistence;
pub mod runtime;
pub mod semantic;

use compiler::compile;
use diagnostic::Diagnostic;
use ir::WorkflowIrV1;
use semantic::CapabilityCatalog;
use std::{
    collections::{BTreeMap, HashSet},
    fmt::{Display, Formatter},
    fs,
    path::{Path, PathBuf},
};

#[derive(Debug, Clone, PartialEq)]
pub struct CompileError {
    pub diagnostics: Vec<Diagnostic>,
}
impl Display for CompileError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> std::fmt::Result {
        write!(formatter, "{} diagnostic(s)", self.diagnostics.len())
    }
}
impl std::error::Error for CompileError {}

pub fn compile_source(
    source: &str,
    catalog: &dyn CapabilityCatalog,
) -> Result<WorkflowIrV1, CompileError> {
    compile_source_named(source, "<stdin>", catalog)
}

pub fn compile_source_named(
    source: &str,
    filename: &str,
    catalog: &dyn CapabilityCatalog,
) -> Result<WorkflowIrV1, CompileError> {
    let (mut workflow, mut diagnostics) =
        parser::parse_source_detailed(source, filename).map_err(|diagnostic| CompileError {
            diagnostics: vec![*diagnostic],
        })?;
    // [CLOUDFLOW-INCLUDE-001] include 只允许 CLI/本地文件编译模式在工作流根目录内解析。
    // HTTP/IDE 传入的源码没有物理路径时明确拒绝，绝不把用户字符串转为任意文件系统读取。
    if !workflow.includes.is_empty() {
        let mut resolving = HashSet::new();
        if let Ok(path) = Path::new(filename).canonicalize() {
            resolving.insert(path);
        }
        resolve_includes(
            &mut workflow,
            filename,
            source,
            include_root(filename),
            &mut resolving,
            0,
            &mut diagnostics,
        );
    }
    // [V1.2-USE-WITH] include/import 合并后，把模块默认参数注入带 `use <alias>` 的步骤。
    apply_use_defaults(&mut workflow);
    diagnostics.extend(semantic::validate(&workflow, catalog, source, filename));
    if !diagnostics.is_empty() {
        return Err(CompileError { diagnostics });
    }
    Ok(compile(&workflow))
}

/// 仅执行 Parser 阶段，返回纯语法 AST（供 `--emit-ast` 使用）。
///
/// 与 `compile_source_named` 不同：不展开 include、不注入 use 默认参数、不执行语义分析、
/// 不生成 IR，只返回入口文件经 Pest 解析得到的 `WorkflowNode`（需求 5.1/5.3/5.12）。
/// 语义合法性不代表语法；解析恢复规则产生的非致命诊断仍返回并随 AST 一并带上（需求 5.6/5.7）。
pub fn parse_ast(source: &str, filename: &str) -> Result<ast::WorkflowNode, CompileError> {
    let (workflow, diagnostics) =
        parser::parse_source_detailed(source, filename).map_err(|diagnostic| CompileError {
            diagnostics: vec![*diagnostic],
        })?;
    if !diagnostics.is_empty() {
        return Err(CompileError { diagnostics });
    }
    Ok(workflow)
}

/// [V1.2-USE-WITH] 把 `use <alias>` / `with <alias>` 解析为模块默认参数注入。
/// 已声明别名（import "x.flow" as alias）指向的模块默认变量，会在步骤尚未显式给出同名参数时
/// 作为 action 参数补充，从而减少重复代码。注入在语义校验前完成，编译期即可确定最终参数。
fn apply_use_defaults(workflow: &mut ast::WorkflowNode) {
    let defaults = workflow.module_defaults.clone();
    apply_use_to_nodes(&mut workflow.flow, &defaults);
    apply_use_to_nodes(&mut workflow.controls, &defaults);
    for group in &mut workflow.step_groups {
        for step in &mut group.steps {
            inject_step_defaults(step, &defaults);
        }
    }
    for handler in &mut workflow.handlers {
        apply_use_to_nodes(&mut handler.nodes, &defaults);
    }
}

fn apply_use_to_nodes(
    nodes: &mut [ast::FlowNode],
    defaults: &BTreeMap<String, Vec<(String, ast::ValueNode)>>,
) {
    for node in nodes {
        match node {
            ast::FlowNode::Step(step) => {
                inject_step_defaults(step, defaults);
                apply_use_to_nodes(&mut step.controls, defaults);
                apply_use_to_nodes(&mut step.on_error, defaults);
            }
            ast::FlowNode::StepGroup(group) => {
                for step in &mut group.steps {
                    inject_step_defaults(step, defaults);
                    apply_use_to_nodes(&mut step.controls, defaults);
                    apply_use_to_nodes(&mut step.on_error, defaults);
                }
            }
            ast::FlowNode::Condition(value) => {
                apply_use_to_nodes(&mut value.true_branch, defaults);
                apply_use_to_nodes(&mut value.false_branch, defaults);
            }
            ast::FlowNode::Loop(value) => apply_use_to_nodes(&mut value.body, defaults),
            ast::FlowNode::For(value) => apply_use_to_nodes(&mut value.body, defaults),
            ast::FlowNode::While(value) => apply_use_to_nodes(&mut value.body, defaults),
            ast::FlowNode::Parallel(value) => apply_use_to_nodes(&mut value.branches, defaults),
            ast::FlowNode::TryCatch(value) => {
                apply_use_to_nodes(&mut value.try_nodes, defaults);
                apply_use_to_nodes(&mut value.catch_nodes, defaults);
                apply_use_to_nodes(&mut value.finally_nodes, defaults);
            }
            ast::FlowNode::Switch(value) => {
                for case in &mut value.cases {
                    apply_use_to_nodes(&mut case.body, defaults);
                }
                apply_use_to_nodes(&mut value.default_branch, defaults);
            }
            ast::FlowNode::Wait(_)
            | ast::FlowNode::Assert(_)
            | ast::FlowNode::Delay(_)
            | ast::FlowNode::Notify(_)
            | ast::FlowNode::Return(_)
            | ast::FlowNode::Break(_)
            | ast::FlowNode::Continue(_)
            | ast::FlowNode::Validate(_) => {}
        }
    }
}

fn inject_step_defaults(
    step: &mut ast::StepNode,
    defaults: &BTreeMap<String, Vec<(String, ast::ValueNode)>>,
) {
    let Some(alias) = step.use_alias.as_deref() else {
        return;
    };
    let Some(module_defaults) = defaults.get(alias) else {
        return;
    };
    let Some(action) = step.action.as_mut() else {
        return;
    };
    for (name, value) in module_defaults {
        action
            .arguments
            .entry(name.clone())
            .or_insert_with(|| value.clone());
    }
}

fn include_root(filename: &str) -> Option<PathBuf> {
    let path = Path::new(filename);
    path.is_file()
        .then(|| {
            path.parent()
                .unwrap_or_else(|| Path::new("."))
                .canonicalize()
                .ok()
        })
        .flatten()
}

fn resolve_includes(
    workflow: &mut ast::WorkflowNode,
    filename: &str,
    source: &str,
    root: Option<PathBuf>,
    resolving: &mut HashSet<PathBuf>,
    depth: u8,
    diagnostics: &mut Vec<Diagnostic>,
) {
    let includes = workflow.includes.clone();
    for include in includes {
        if depth >= 8 {
            diagnostics.push(Diagnostic::new(
                "CF3104",
                "INCLUDE_ERROR",
                "include 嵌套深度不能超过 8 层",
                source,
                filename,
                include.span.start,
                include.span.end,
                vec![],
                Some("合并公共模块或减少 include 层级".into()),
            ));
            continue;
        }
        let Some(root) = root.as_ref() else {
            diagnostics.push(Diagnostic::new(
                "CF3103",
                "INCLUDE_ERROR",
                format!("include `{}` 只能在 .flow 文件编译模式使用", include.path),
                source,
                filename,
                include.span.start,
                include.span.end,
                vec![],
                Some(
                    "请使用 cloudflowc compile <file>；HTTP/IDE 请先由控制面解析受信任模块".into(),
                ),
            ));
            continue;
        };
        if !include.path.ends_with(".flow") || Path::new(&include.path).is_absolute() {
            diagnostics.push(Diagnostic::new(
                "CF3103",
                "INCLUDE_ERROR",
                format!(
                    "include 路径 `{}` 必须是工作流根目录内的相对 .flow 文件",
                    include.path
                ),
                source,
                filename,
                include.span.start,
                include.span.end,
                vec![],
                None,
            ));
            continue;
        }
        let current = Path::new(filename);
        let candidate = current
            .parent()
            .unwrap_or_else(|| Path::new("."))
            .join(&include.path);
        let resolved = match candidate.canonicalize() {
            Ok(path) if path.starts_with(root) => path,
            Ok(_) => {
                diagnostics.push(Diagnostic::new(
                    "CF3103",
                    "INCLUDE_ERROR",
                    "include 路径越出工作流根目录",
                    source,
                    filename,
                    include.span.start,
                    include.span.end,
                    vec![],
                    Some("不得使用 ../ 访问工作流目录之外的文件".into()),
                ));
                continue;
            }
            Err(error) => {
                diagnostics.push(Diagnostic::new(
                    "CF3103",
                    "INCLUDE_ERROR",
                    format!("无法读取 include `{}`：{error}", include.path),
                    source,
                    filename,
                    include.span.start,
                    include.span.end,
                    vec![],
                    None,
                ));
                continue;
            }
        };
        if !resolving.insert(resolved.clone()) {
            diagnostics.push(Diagnostic::new(
                "CF3104",
                "INCLUDE_ERROR",
                format!("检测到循环 include：{}", resolved.display()),
                source,
                filename,
                include.span.start,
                include.span.end,
                vec![],
                Some("移除模块间循环引用".into()),
            ));
            continue;
        }
        let include_source = match fs::read_to_string(&resolved) {
            Ok(value) => value,
            Err(error) => {
                diagnostics.push(Diagnostic::new(
                    "CF3103",
                    "INCLUDE_ERROR",
                    format!("无法读取 include `{}`：{error}", include.path),
                    source,
                    filename,
                    include.span.start,
                    include.span.end,
                    vec![],
                    None,
                ));
                resolving.remove(&resolved);
                continue;
            }
        };
        let include_name = resolved.display().to_string();
        match parser::parse_source_detailed(&include_source, &include_name) {
            Ok((mut module, mut module_diagnostics)) => {
                resolve_includes(
                    &mut module,
                    &include_name,
                    &include_source,
                    Some(root.clone()),
                    resolving,
                    depth + 1,
                    &mut module_diagnostics,
                );
                diagnostics.append(&mut module_diagnostics);
                merge_module(
                    workflow,
                    module,
                    &include_source,
                    &include_name,
                    include.alias.as_deref(),
                    diagnostics,
                );
            }
            Err(error) => diagnostics.push(*error),
        }
        resolving.remove(&resolved);
    }
}

fn merge_module(
    target: &mut ast::WorkflowNode,
    module: ast::WorkflowNode,
    source: &str,
    filename: &str,
    alias: Option<&str>,
    diagnostics: &mut Vec<Diagnostic>,
) {
    // 模块以普通 workflow 外壳保存，只有 variables/steps/controls/handlers 能被导入；
    // metadata、trigger、runtime 仍由入口工作流拥有，防止隐式修改触发或资源配额。
    if !matches!(module.trigger, ast::TriggerNode::Manual)
        || module.runtime != ast::RuntimeConfig::default()
    {
        diagnostics.push(Diagnostic::new(
            "CF3105",
            "INCLUDE_ERROR",
            "被 include 的模块不能声明 trigger 或 runtime",
            source,
            filename,
            module.span.start,
            module.span.end,
            vec![],
            Some("模块只应包含 variables、step、控制流和 handlers".into()),
        ));
        return;
    }
    // [V1.2-USE-WITH] 先记录模块别名 → 默认变量（带初始值），供 `use <alias>` 注入步骤参数。
    if let Some(alias) = alias {
        let mut defaults = target
            .module_defaults
            .get(alias)
            .cloned()
            .unwrap_or_default();
        for variable in &module.variables {
            if let Some(default) = &variable.default {
                defaults.push((variable.name.clone(), default.clone()));
            }
        }
        target.module_defaults.insert(alias.to_owned(), defaults);
    }
    target.variables.extend(module.variables);
    target.flow.extend(module.flow);
    target.steps.extend(module.steps);
    target.controls.extend(module.controls);
    target.handlers.extend(module.handlers);
}

#[cfg(test)]
mod tests {
    use super::*;
    use semantic::InMemoryCapabilityCatalog;

    const DEMO: &str = include_str!("../../docs/CLOUDFLOW_DEMO_DESIGN.md");

    #[test]
    fn compiles_demo_workflow_to_versioned_ir() {
        let source = DEMO
            .split("```cloudflow")
            .nth(1)
            .and_then(|v| v.split("```").next())
            .expect("demo block");
        let catalog = InMemoryCapabilityCatalog::default();
        let ir = compile_source_named(source, "weekly_sales_report.flow", &catalog)
            .expect("demo must compile");
        assert_eq!(ir.api_version, "workflow.cloudflow.io/v1");
        assert_eq!(ir.metadata.name, "weekly_sales_report");
        assert_eq!(ir.spec.graph.nodes.len(), 4);
        assert_eq!(ir.spec.graph.edges.len(), 3);
        assert!(ir.extensions.contains_key("handlers"));
        assert_eq!(
            ir.extensions["handlers"][0]["graph"]["nodes"][0]["action"]["service"],
            "notification"
        );
    }

    #[test]
    fn reports_missing_dependency_with_structured_code() {
        let source = r#"workflow "broken" { step a { action file.list {} depends_on missing } }"#;
        let error =
            compile_source(source, &InMemoryCapabilityCatalog::default()).expect_err("missing dep");
        assert!(error.diagnostics.iter().any(|d| d.code == "CF2002"));
        assert!(error.diagnostics[0].location.line >= 1);
    }

    #[test]
    fn ir_serialization_is_machine_readable() {
        let source = r#"workflow "demo" { trigger { manual {} } step one { action file.list {} output files } }"#;
        let ir = compile_source(source, &InMemoryCapabilityCatalog::default()).expect("valid");
        let json = serde_json::to_string_pretty(&ir).expect("json");
        assert!(json.contains("workflow.cloudflow.io/v1"));
        assert!(json.contains("\"graph\""));
    }

    #[test]
    fn runtime_loads_demo_ir_and_schedules_dag() {
        let source = DEMO
            .split("```cloudflow")
            .nth(1)
            .and_then(|v| v.split("```").next())
            .expect("demo block");
        let ir = compile_source_named(
            source,
            "weekly_sales_report.flow",
            &InMemoryCapabilityCatalog::default(),
        )
        .expect("demo");
        let runtime = runtime::RuntimeEngine::load("exec-1", ir).expect("IR");
        assert_eq!(
            runtime.topological_order().expect("DAG"),
            vec![
                "collect_files",
                "aggregate_data",
                "generate_report",
                "save_report"
            ]
        );
    }

    #[test]
    fn runtime_uses_graph_edges_as_authoritative_dependencies() {
        let source = r#"workflow "edge_authority" {
            trigger { manual {} }
            step first { action file.list {} }
            step second { action file.save {} depends_on first }
        }"#;
        let mut ir = compile_source(source, &InMemoryCapabilityCatalog::default()).expect("valid");
        // 模拟控制流 IR：控制节点通常仅通过 graph.edges 建立父子依赖，不重复写 dependsOn。
        for node in &mut ir.spec.graph.nodes {
            node.depends_on.clear();
        }
        let mut runtime = runtime::RuntimeEngine::load("exec-edge", ir).expect("IR");
        assert_eq!(runtime.ready_nodes(), vec!["first"]);
        assert!(runtime.mark_success("first"));
        assert_eq!(runtime.ready_nodes(), vec!["second"]);
    }
}
