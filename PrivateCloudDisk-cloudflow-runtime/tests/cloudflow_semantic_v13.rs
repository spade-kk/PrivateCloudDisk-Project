//! [V1.3-RULE] 统一语义层规则体系测试（需求 10.3/10.12/10.13/10.19/10.23/10.27）。
//!
//! 覆盖新增内置规则：DuplicateVariableRule（CF2003）、RetryConfigRule（CF4423）、
//! TimeoutConfigRule（CF4424）、WaitConfigRule（CF4419）、MetadataRule（CF4425），
//! 以及 `validate_with_rules` 的插件扩展入口（10.27）。

use cloudflow_runtime::{
    compile_source,
    parser::parse_source,
    semantic::{builtin_rules, validate, validate_with_rules, InMemoryCapabilityCatalog},
};

fn catalog() -> InMemoryCapabilityCatalog {
    InMemoryCapabilityCatalog::default()
}

fn codes(source: &str) -> Vec<String> {
    validate_with_rules(
        &parse_source(source, "rule.flow").expect("parse"),
        &catalog(),
        source,
        "rule.flow",
        &[],
    )
    .into_iter()
    .map(|diagnostic| diagnostic.code)
    .collect()
}

#[test]
fn baseline_workflow_passes_all_builtin_rules() {
    let source = r#"
workflow "baseline" {
  variables { count = 1 }
  step a { action file.list {} retry { max_attempts = 2 strategy = "exponential" } timeout = 30s }
  wait approval { timeout = 24h }
  tag "finance"
}
"#;
    let diagnostics = codes(source);
    assert!(
        diagnostics.is_empty(),
        "baseline should be rule-clean, got: {diagnostics:?}"
    );
    compile_source(source, &catalog()).expect("baseline must compile");
}

#[test]
fn duplicate_variable_declaration_rejected() {
    // 编译管线中重复变量由解析层先行拦截（CF2001，向后兼容既有诊断）；
    // AST 级规则（CF2003）作为统一语义层对直接构造 Domain AST 的调用方（IDE/未来前端）的兜底。
    let source = r#"
workflow "dup" {
  variables { count = 1; count = 2 }
  step a { action file.list {} }
}
"#;
    let error = compile_source(source, &catalog()).expect_err("duplicate variable must fail");
    assert!(error
        .diagnostics
        .iter()
        .any(|diagnostic| diagnostic.code == "CF2001"));

    let mut workflow = parse_source(
        r#"
workflow "dup" {
  variables { count = 1 }
  step a { action file.list {} }
}
"#,
        "rule.flow",
    )
    .expect("parse");
    use cloudflow_runtime::ast::{VariableDecl, VariableSource};
    workflow.variables.push(VariableDecl {
        name: "count".into(),
        type_name: "number".into(),
        required: false,
        source: VariableSource::Local,
        default: None,
        span: Default::default(),
    });
    let diagnostics: Vec<String> = validate_with_rules(&workflow, &catalog(), "", "rule.flow", &[])
        .into_iter()
        .map(|diagnostic| diagnostic.code)
        .collect();
    assert!(
        diagnostics.iter().any(|code| code == "CF2003"),
        "expected CF2003 at AST level, got: {diagnostics:?}"
    );
}

#[test]
fn retry_unknown_strategy_reported() {
    let source = r#"
workflow "retry" {
  step a { action file.list {} retry { max_attempts = 2 strategy = "cosmic" } }
}
"#;
    let diagnostics = codes(source);
    assert!(
        diagnostics.iter().any(|code| code == "CF4423"),
        "expected CF4423 bad strategy, got: {diagnostics:?}"
    );
}

#[test]
fn retry_zero_attempts_reported() {
    let source = r#"
workflow "retry" {
  step a { action file.list {} retry { max_attempts = 0 } }
}
"#;
    let diagnostics = codes(source);
    assert!(
        diagnostics.iter().any(|code| code == "CF4423"),
        "expected CF4423 zero attempts, got: {diagnostics:?}"
    );
}

#[test]
fn runtime_retry_policy_also_checked() {
    let source = r#"
workflow "retry" {
  runtime { retry_policy { max_attempts = 1 strategy = "cosmic" } }
  step a { action file.list {} }
}
"#;
    let diagnostics = codes(source);
    assert!(
        diagnostics.iter().any(|code| code == "CF4423"),
        "runtime-level retry policy must be checked, got: {diagnostics:?}"
    );
}

#[test]
fn step_zero_timeout_reported() {
    let source = r#"
workflow "timeout" {
  step a { action file.list {} timeout = 0s }
}
"#;
    let diagnostics = codes(source);
    assert!(
        diagnostics.iter().any(|code| code == "CF4424"),
        "expected CF4424 zero step timeout, got: {diagnostics:?}"
    );
}

#[test]
fn runtime_zero_timeout_reported() {
    let source = r#"
workflow "timeout" {
  runtime { timeout = 0s }
  step a { action file.list {} }
}
"#;
    let diagnostics = codes(source);
    assert!(
        diagnostics.iter().any(|code| code == "CF4424"),
        "expected CF4424 zero runtime timeout, got: {diagnostics:?}"
    );
}

#[test]
fn wait_zero_timeout_reported() {
    let source = r#"
workflow "wait" {
  step a { action file.list {} }
  wait approval { timeout = 0s }
}
"#;
    let diagnostics = codes(source);
    assert!(
        diagnostics.iter().any(|code| code == "CF4419"),
        "expected CF4419 zero wait timeout, got: {diagnostics:?}"
    );
}

#[test]
fn wait_in_nested_block_checked() {
    let source = r#"
workflow "wait" {
  if { vars.ok } {
    wait approval { timeout = 0s }
  } else { step a { action file.list {} } }
}
"#;
    let diagnostics = codes(source);
    assert!(
        diagnostics.iter().any(|code| code == "CF4419"),
        "wait inside control blocks must be checked, got: {diagnostics:?}"
    );
}

#[test]
fn wait_without_timeout_stays_valid() {
    // 无限期审批是合法语义（由审批接口恢复），不产生诊断。
    let source = r#"
workflow "wait" {
  step a { action file.list {} }
  wait approval {}
}
"#;
    let diagnostics = codes(source);
    assert!(
        diagnostics.is_empty(),
        "indefinite wait must stay valid, got: {diagnostics:?}"
    );
}

#[test]
fn empty_tag_reported() {
    let source = r#"
workflow "tags" {
  tag ""
  step a { action file.list {} }
}
"#;
    let diagnostics = codes(source);
    assert!(
        diagnostics.iter().any(|code| code == "CF4425"),
        "expected CF4425 empty tag, got: {diagnostics:?}"
    );
}

#[test]
fn pipeline_gate_fails_on_rule_diagnostics() {
    // 编译管线必须强制经过规则检查（10.29）：规则诊断使整体编译失败。
    let source = r#"
workflow "gate" {
  step a { action file.list {} retry { max_attempts = 0 } }
}
"#;
    let error = compile_source(source, &catalog()).expect_err("rule violation must fail compile");
    assert!(error
        .diagnostics
        .iter()
        .any(|diagnostic| diagnostic.code == "CF4423"));
}

#[test]
fn builtin_rules_are_registered_with_stable_names() {
    let names: Vec<&str> = builtin_rules().iter().map(|rule| rule.name()).collect();
    for expected in [
        "duplicate-variable",
        "retry-config",
        "timeout-config",
        "wait-config",
        "metadata-tags",
    ] {
        assert!(
            names.iter().any(|name| *name == expected),
            "missing built-in rule {expected}: {names:?}"
        );
    }
}

#[test]
fn extra_rules_can_be_injected() {
    // 10.27：外部规则（组织级/IDE 侧）通过 validate_with_rules 注入。
    struct StepCountRule;
    impl cloudflow_runtime::semantic::SemanticRule for StepCountRule {
        fn name(&self) -> &'static str {
            "external-step-count"
        }
        fn check(
            &self,
            ctx: &cloudflow_runtime::semantic::RuleContext,
            diagnostics: &mut Vec<cloudflow_runtime::diagnostic::Diagnostic>,
        ) {
            if ctx.workflow.steps.len() > 50 {
                diagnostics.push(cloudflow_runtime::diagnostic::Diagnostic::new(
                    "CF9001",
                    "CUSTOM_RULE",
                    "组织规范：步骤数量超过 50",
                    ctx.source,
                    ctx.filename,
                    0,
                    1,
                    vec![],
                    None,
                ));
            }
        }
    }
    let source = r#"
workflow "gate" {
  step a { action file.list {} }
}
"#;
    let workflow = parse_source(source, "rule.flow").expect("parse");
    let without_extra = validate(&workflow, &catalog(), source, "rule.flow");
    let with_extra = validate_with_rules(
        &workflow,
        &catalog(),
        source,
        "rule.flow",
        &[Box::new(StepCountRule)],
    );
    // 该工作流只有 1 个 step，外部规则不应触发；内置行为保持一致。
    assert_eq!(without_extra.len(), with_extra.len());
}
