//! CloudFlow 多前端编译核心：DSL（Pest）与 YAML（serde_yaml_ng）→ 共享 Workflow Domain AST
//! → 统一语义分析 → `workflow.cloudflow.io/v1` IR。
//!
//! 分层约定：
//! - 本 crate 根层（`src/lib.rs`）即「前端调度器」——`Language` / `language_of` /
//!   `parse_frontend_detailed` 在此统一分发到 DSL（`parser`）或 YAML（`yaml`）前端；
//!   DSL 前端主体位于根层模块（`parser` / `grammar.pest`），YAML 前端收敛在 `yaml`
//!   子模块（仅暴露 `yaml::parse_yaml{,_detailed}`）。表达式能力唯一收敛于 `expression` 子系统。
//! - IR 契约校验唯一实现在 `ir_validate`；`compiler::validate_ir` 是其文本适配层，
//!   生产 `/ir-validate` API、`RuntimeEngine::load`、微服务与开发调试入口共用同一校验。
//! - 执行语义分双执行面：生产执行面 `execution`（持久化调度器与执行协调器，
//!   数据库 + Capability Agent + 检查点）与开发调试面 `dev_exec`（纯内存同步 Dev Runner）；
//!   两执行面的控制流语义（条件/分支/try/循环/重试/退避/并行/超时/子树展开/控制信号）
//!   唯一收敛于统一调度驱动与 `execution_core`（`cloudflow-engine-core` crate），不重复定义。
//! - 语言无关的共享层——领域 AST（`ast`）、诊断（`diagnostic`）、Workflow IR（`ir`）、
//!   IR 契约校验（`ir_validate`）、表达式子系统（`expression`）、执行语义核心
//!   （`execution_core`）、统一调度驱动（`engine`）与开发调试面（`dev_exec`）——
//!   实现位于独立 crate `cloudflow-engine-core`（`crates/cloudflow-engine-core`），
//!   本 crate 根层只做再导出（`pub use cloudflow_engine_core::{...}`），
//!   既有 `crate::ast::*` / `crate::ir::*` / `crate::dev_exec::*` 等调用路径不变。
//!   独立 crate 保证 `cloudflowc` CLI 可仅依赖执行核心而不引入
//!   数据库 / gRPC / HTTP 服务面代码。
//!
//! 本 crate 不连接业务数据库，也不直接执行用户脚本；身份、空间权限和能力快照由控制面
//! 传入，执行面由 Runtime 的 action adapter 负责。

pub mod ast_printer;
#[cfg(feature = "runtime-service")]
pub mod broker;
#[cfg(feature = "runtime-service")]
pub mod compile_cache;
pub mod compiler;
#[cfg(feature = "runtime-service")]
pub mod config;
#[cfg(feature = "runtime-service")]
pub mod error;
#[cfg(feature = "runtime-service")]
pub mod execution;
#[cfg(feature = "runtime-service")]
pub mod http;
#[cfg(feature = "runtime-service")]
pub mod observability;
pub mod parser;
#[cfg(feature = "runtime-service")]
pub mod persistence;
pub mod semantic;
pub mod yaml;

/// 共享执行核心（独立 crate `cloudflow-engine-core`）再导出：领域 AST、诊断、
/// Workflow IR、IR 契约校验、表达式子系统、执行语义核心、统一调度驱动与
/// 开发调试面。宿主 crate 与 `cloudflowc` CLI 共用这唯一一份实现。
/// Capability Agent（gRPC）独立 crate：生产执行面唯一能力调用出口
/// （能力解析、最小权限校验、审计、builtin/api/plugin 路由）。
#[cfg(feature = "runtime-service")]
pub use cloudflow_agent as agent;
pub use cloudflow_engine_core::{
    ast, dev_exec, diagnostic, engine, execution_core, expression, ir, ir_validate, runtime,
};

/// 前端语言标识：CloudFlow DSL 与 CloudFlow YAML（第二前端语言）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Language {
    Dsl,
    Yaml,
}

/// 按文件名扩展名推断前端语言（前端调度器）：
/// - `.flow.yaml` / `.workflow.yaml` / `.yaml` / `.yml` → YAML；
/// - 其余（含 `.flow`）→ DSL。
pub fn language_of(filename: &str) -> Language {
    let lower = filename.to_ascii_lowercase();
    if lower.ends_with(".flow.yaml")
        || lower.ends_with(".workflow.yaml")
        || lower.ends_with(".yaml")
        || lower.ends_with(".yml")
    {
        Language::Yaml
    } else {
        Language::Dsl
    }
}

/// 按前端语言选择语法解析，返回 (AST, 非致命诊断)（前端调度器）：
/// DSL ↔ `parser::parse_source_detailed`；YAML ↔ `yaml::parse_yaml_detailed`。
/// 生成 CloudFlow YAML 的 JSON Schema（需求 31.10/31.18，单一事实来源，供前端 IDE / API 使用）。
///
/// 该产物从 `yaml/schema.rs` 的统一定义生成，与 `schemas/yaml-workflow.schema.json`
/// 保持一致（由测试 `yaml_json_schema_matches_ondisk` 校验，避免漂移）。
pub fn emit_yaml_json_schema() -> serde_json::Value {
    yaml::schema::emit_json_schema()
}

pub fn parse_frontend_detailed(
    source: &str,
    filename: &str,
    language: Language,
) -> Result<(ast::WorkflowNode, Vec<Diagnostic>), Box<Diagnostic>> {
    match language {
        Language::Yaml => yaml::parse_yaml_detailed(source, filename),
        Language::Dsl => parser::parse_source_detailed(source, filename),
    }
}

/// 开发/调试执行入口（需求 §4/§9）：直接 IR 驱动、纯内存执行、无数据库/MQ。
pub use dev_exec::{
    action_key, dev_execute, dev_execute_async, dev_execute_sync, ActionExecutor, DevConfig,
    DevEntryError, DevError, DevExecError, DevExecutionResult, DevFailureSpec, DevLogLevel,
    DevNodeResult, DevTaskStatus, DevWorkflowStatus, MockActionExecutor,
};
/// Workflow IR（`workflow.cloudflow.io/v1`）——调试执行入口直接消费该结构。
pub use ir::WorkflowIrV1;
/// IR 契约校验（唯一 IR 校验实现：生产执行面加载、`/ir-validate` API、微服务与
/// 开发调试执行入口共用，需求 §3）：纯函数、一次收集全部问题。
pub use ir_validate::{validate_ir_contracts, IrContractIssue, IR_API_VERSION, VALID_NODE_TYPES};

use compiler::compile;
use diagnostic::Diagnostic;
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
    let language = language_of(filename);
    compile_source_named_for_language(source, filename, language, catalog)
}

/// 显式指定前端语言编译（CLI `--lang` / HTTP `language` 字段使用）。
pub fn compile_source_named_for_language(
    source: &str,
    filename: &str,
    language: Language,
    catalog: &dyn CapabilityCatalog,
) -> Result<WorkflowIrV1, CompileError> {
    let (mut workflow, mut diagnostics) = parse_frontend_detailed(source, filename, language)
        .map_err(|diagnostic| CompileError {
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
    diagnostics.extend(semantic::validate_with_rules(
        &workflow,
        catalog,
        source,
        filename,
        &[],
    ));
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
    let language = language_of(filename);
    parse_ast_for_language(source, filename, language)
}

/// 显式指定前端语言做语法解析（CLI `--emit-ast --lang yaml` 等场景）。
pub fn parse_ast_for_language(
    source: &str,
    filename: &str,
    language: Language,
) -> Result<ast::WorkflowNode, CompileError> {
    let (workflow, diagnostics) =
        parse_frontend_detailed(source, filename, language).map_err(|diagnostic| CompileError {
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
                // [19.13] 诊断不泄露绝对路径：只回显用户书写的相对 include 路径。
                format!("检测到循环 include：`{}`", include.path),
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
