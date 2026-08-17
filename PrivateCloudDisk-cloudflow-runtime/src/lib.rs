//! CloudFlow DSL 编译核心：Pest → AST → 语义分析 → Workflow IR v1。
//!
//! 本 crate 不连接业务数据库，也不直接执行用户脚本；身份、空间权限和能力快照由控制面
//! 传入，执行面由 Runtime 的 action adapter 负责。

pub mod agent;
pub mod ast;
pub mod broker;
pub mod compiler;
pub mod config;
pub mod diagnostic;
pub mod engine;
pub mod error;
pub mod ir;
pub mod observability;
pub mod parser;
pub mod runtime;
pub mod semantic;

use compiler::compile;
use diagnostic::Diagnostic;
use ir::WorkflowIrV1;
use semantic::CapabilityCatalog;
use std::fmt::{Display, Formatter};

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
    let workflow = parser::parse_source(source, filename).map_err(|diagnostic| CompileError {
        diagnostics: vec![diagnostic],
    })?;
    let diagnostics = semantic::validate(&workflow, catalog, source, filename);
    if !diagnostics.is_empty() {
        return Err(CompileError { diagnostics });
    }
    Ok(compile(&workflow))
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
}
