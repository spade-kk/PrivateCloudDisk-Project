//! CloudFlow V1.2 新语法扩展契约。
//!
//! [REQ-CLOUDFLOW-V12-20260818] 覆盖 switch/case/default、delay、timeout 块、retry_on、
//! environment、namespace、tag、metadata.changelog 与 import-as 别名。语义反例验证对应的
//! CF44xx 结构化错误码。真实执行/恢复语义仍需在部署环境中做 E2E 验收。

use cloudflow_runtime::semantic::{validate, InMemoryCapabilityCatalog};
use cloudflow_runtime::{compile_source, compile_source_named};
use serde_json::Value;
use std::path::Path;

fn compile(source: &str, name: &str) -> cloudflow_runtime::ir::WorkflowIrV1 {
    compile_source_named(
        source,
        &format!("coverage/{name}.flow"),
        &InMemoryCapabilityCatalog::default(),
    )
    .unwrap_or_else(|error| panic!("{name} must compile: {error:?}"))
}

fn find_node<'a>(
    ir: &'a cloudflow_runtime::ir::WorkflowIrV1,
    node_type: &str,
) -> &'a cloudflow_runtime::ir::NodeIr {
    ir.spec
        .graph
        .nodes
        .iter()
        .find(|node| node.node_type == node_type)
        .unwrap_or_else(|| panic!("missing {node_type} node"))
}

#[test]
fn switch_compiles_to_control_node_with_cases_and_default() {
    let ir = compile(include_str!("../examples/coverage/switch.flow"), "switch");
    let node = find_node(&ir, "switch");
    let config = node
        .switch_config
        .as_ref()
        .and_then(Value::as_object)
        .expect("switchConfig present");
    assert!(config.get("subject").is_some());
    let cases = config["cases"].as_array().expect("cases array");
    assert_eq!(cases.len(), 2);
    assert!(config
        .get("default")
        .and_then(Value::as_array)
        .is_some_and(|v| !v.is_empty()));
    assert!(!node.children.is_empty());
    assert!(cloudflow_runtime::compiler::validate_ir(&ir).is_empty());
}

#[test]
fn delay_compiles_to_delay_node_with_milliseconds() {
    let ir = compile(include_str!("../examples/coverage/delay.flow"), "delay");
    let node = find_node(&ir, "delay");
    assert_eq!(node.delay_ms, Some(2_000));
    // 顶层顺序屏障：delay 是控制节点，first -> delay -> second 必须进入 edges。
    let edges = &ir.spec.graph.edges;
    let ids: Vec<&str> = ir.spec.graph.nodes.iter().map(|n| n.id.as_str()).collect();
    assert!(ids.contains(&"first"));
    assert!(ids.contains(&"second"));
    assert!(edges.iter().any(|e| e.from == "first"));
    assert!(edges.iter().any(|e| e.to == "second"));
}

#[test]
fn delay_zero_reports_structured_error() {
    let source = r#"workflow "bad_delay" {
        step a { action builtin.file.list {} }
        delay 0s
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("0s delay rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4404"));
    assert!(error.diagnostics.iter().all(|d| d.location.line >= 1));
}

#[test]
fn timeout_block_sets_duration_and_on_timeout() {
    let ir = compile(
        include_str!("../examples/coverage/timeout_block.flow"),
        "timeout_block",
    );
    let node = find_node(&ir, "task");
    assert_eq!(node.timeout.as_deref(), Some("30s"));
    assert_eq!(node.on_timeout.as_deref(), Some("fail"));
}

#[test]
fn timeout_block_rejects_invalid_on_timeout() {
    let source = r#"workflow "bad_timeout" {
        step s { action builtin.file.list {} timeout { duration = 10s on_timeout = "abort" } }
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("invalid on_timeout rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4403"));
}

#[test]
fn retry_on_whitelists_exception_types() {
    let ir = compile(
        include_str!("../examples/coverage/retry_on.flow"),
        "retry_on",
    );
    let node = find_node(&ir, "task");
    assert_eq!(node.retry_on, vec!["NetworkException", "TimeoutException"]);
}

#[test]
fn retry_on_rejects_unknown_exception() {
    let source = r#"workflow "bad_retry" {
        step s { action builtin.file.list {} retry_on [MyCustomException] }
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("unknown exception");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4402"));
}

#[test]
fn environment_and_namespace_flow_into_ir() {
    let ir = compile(
        include_str!("../examples/coverage/environment_namespace.flow"),
        "environment_namespace",
    );
    assert_eq!(
        ir.metadata.namespace.as_deref(),
        Some("com.example.workflows")
    );
    assert_eq!(
        ir.spec.environment["NODE_ENV"],
        Value::String("production".into())
    );
    assert_eq!(ir.spec.environment["MAX_RETRY"], Value::from(3));
}

#[test]
fn environment_rejects_reference_value() {
    let source = r#"workflow "bad_env" {
        environment { KEY = vars.other }
        step a { action builtin.file.list {} }
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("env ref rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4405"));
}

#[test]
fn namespace_rejects_uppercase_segment() {
    let source = r#"workflow "bad_ns" {
        namespace Com.Example
        step a { action builtin.file.list {} }
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("uppercase namespace rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4406"));
}

#[test]
fn tags_and_changelog_flow_into_ir() {
    let ir = compile(
        include_str!("../examples/coverage/tags_changelog.flow"),
        "tags_changelog",
    );
    assert_eq!(ir.metadata.tags, vec!["finance", "weekly"]);
    assert_eq!(ir.metadata.changelog.as_deref(), Some("initial release"));
    assert_eq!(ir.metadata.version.as_deref(), Some("1.0.0"));
}

#[test]
fn import_with_alias_parses_and_duplicate_alias_is_rejected() {
    // 解析层确认 alias 被捕获（include 解析仅在真实文件模式执行，这里只验证语法与 AST）。
    let (workflow, diagnostics) = cloudflow_runtime::parser::parse_source_detailed(
        r#"workflow "imp" { import "common.flow" as common step a { action builtin.file.list {} } }"#,
        "imp.flow",
    )
    .expect("parse");
    assert!(diagnostics.is_empty());
    assert_eq!(workflow.includes.len(), 1);
    assert_eq!(workflow.includes[0].alias.as_deref(), Some("common"));

    // 语义层：重复别名必须产生 CF4407。
    let mut node = workflow.clone();
    node.includes.push(cloudflow_runtime::ast::IncludeNode {
        path: "other.flow".into(),
        alias: Some("common".into()),
        span: Default::default(),
    });
    let diagnostics = validate(&node, &InMemoryCapabilityCatalog::default(), "", "imp.flow");
    assert!(
        diagnostics.iter().any(|d| d.code == "CF4407"),
        "expected CF4407, got: {:?}",
        diagnostics.iter().map(|d| &d.code).collect::<Vec<_>>()
    );
}

#[test]
fn v12_features_are_backwards_compatible_and_ir_is_machine_readable() {
    let source = r#"workflow "mixed" {
        metadata { version = "2.0.0" }
        namespace com.docs.samples
        tag "demo"
        environment { REGION = "cn-north" }
        variables { enabled = true }
        trigger { manual {} }
        step collect { action builtin.file.list {} }
        switch vars.enabled {
            case true => { step on_yes { action builtin.file.save {} } }
            default => { step on_no { action builtin.file.delete {} } }
        }
    }"#;
    let ir = compile(source, "mixed");
    assert_eq!(ir.api_version, "workflow.cloudflow.io/v1");
    assert!(ir.spec.environment.contains_key("REGION"));
    assert!(find_node(&ir, "switch").switch_config.is_some());
    serde_json::to_string(&ir).expect("IR must serialize");
    assert!(Path::new("examples/coverage/switch.flow").exists());
}

// ---------------------------------------------------------------------------
// Tranche 2：[V1.2] for 循环 + break/continue、parallel max_concurrency、validate
// ---------------------------------------------------------------------------

#[test]
fn for_range_compiles_to_for_range_loop_with_break() {
    let ir = compile(
        include_str!("../examples/coverage/for_range.flow"),
        "for_range",
    );
    let node = find_node(&ir, "loop");
    let config = node
        .loop_config
        .as_ref()
        .and_then(Value::as_object)
        .expect("loopConfig present");
    assert_eq!(config["kind"], "for-range");
    assert_eq!(config["from"], Value::from(0));
    assert_eq!(config["to"], serde_json::json!({"$ref": "vars.limit"}));
    // 循环体包含 break 控制节点。
    assert!(
        ir.spec.graph.nodes.iter().any(|n| n.node_type == "break"),
        "for 循环体应包含 break 节点"
    );
    assert!(cloudflow_runtime::compiler::validate_ir(&ir).is_empty());
}

#[test]
fn for_collection_compiles_to_for_loop() {
    let ir = compile(
        include_str!("../examples/coverage/for_collection.flow"),
        "for_collection",
    );
    let node = find_node(&ir, "loop");
    let config = node
        .loop_config
        .as_ref()
        .and_then(Value::as_object)
        .expect("loopConfig present");
    assert_eq!(config["kind"], "for");
    assert_eq!(config["iterator"], "item");
    assert!(config.get("collection").is_some());
}

#[test]
fn validate_compiles_to_validate_node() {
    let ir = compile(
        include_str!("../examples/coverage/validate.flow"),
        "validate",
    );
    let node = find_node(&ir, "validate");
    assert!(node.condition.is_some(), "validate 节点应携带 condition");
}

#[test]
fn validate_rejects_non_boolean_expression() {
    let source = r#"workflow "bad_validate" {
        step s { action builtin.file.list {} }
        validate { 1 }
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("validate 1 rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4409"));
}

#[test]
fn parallel_max_concurrency_flows_into_ir() {
    let ir = compile(
        include_str!("../examples/coverage/parallel_max.flow"),
        "parallel_max",
    );
    let node = find_node(&ir, "parallel");
    let config = node
        .parallel
        .as_ref()
        .and_then(Value::as_object)
        .expect("parallel config present");
    assert_eq!(config["maxConcurrency"], Value::from(3));
}

#[test]
fn parallel_rejects_zero_concurrency() {
    let source = r#"workflow "bad_parallel" {
        parallel(max_concurrency = 0) {
            step a { action builtin.file.list {} }
        }
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("max_concurrency=0 rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4411"));
}

#[test]
fn break_outside_loop_reports_structured_error() {
    let source = r#"workflow "bad_break" {
        step a { action builtin.file.list {} }
        break
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("top-level break rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4408"));
}

#[test]
fn continue_outside_loop_reports_structured_error() {
    let source = r#"workflow "bad_continue" {
        step a { action builtin.file.list {} }
        continue
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("top-level continue rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4408"));
}

#[test]
fn for_range_rejects_non_number_endpoint() {
    let source = r#"workflow "bad_range" {
        step a { action builtin.file.list {} }
        for i in range(0, "x") {
            step b { action builtin.file.list {} }
        }
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("string endpoint rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4410"));
}

// ---------- V1.2 Tranche 3：pipeline / template / interval-webhook / audit / notify / on_error / return ----------

#[test]
fn pipeline_compiles_to_staged_collection_operations() {
    let ir = compile(
        include_str!("../examples/coverage/pipeline.flow"),
        "pipeline",
    );
    let serialized = serde_json::to_string(&ir).unwrap();
    assert!(
        serialized.contains("$pipeline"),
        "pipeline IR: {serialized}"
    );
    assert!(serialized.contains("\"filter\""), "filter stage");
    assert!(serialized.contains("\"map\""), "map stage");
    assert!(serialized.contains("\"reduce\""), "reduce stage");
}

#[test]
fn template_compiles_to_interpolated_segments() {
    let ir = compile(
        include_str!("../examples/coverage/template.flow"),
        "template",
    );
    let serialized = serde_json::to_string(&ir).unwrap();
    assert!(
        serialized.contains("$template"),
        "template IR: {serialized}"
    );
    assert!(serialized.contains("vars.name"), "references vars.name");
}

#[test]
fn pipeline_filter_rejects_undefined_variable_ref() {
    // filter 谓词中的裸标识符是元素字段（放行），但 vars./steps. 仍须真实存在。
    let source = r#"workflow "bad_pipe" {
        variables { files: array = [] }
        step s { action builtin.file.list {} }
    }"#;
    // 语义正确路径：裸字段 + 显式 vars 引用于 action 参数。
    let bad = r#"workflow "bad_pipe" {
        variables {
            files: array = []
            picked = vars.files | filter(vars.missing > 1) | map(name)
        }
        step s { action builtin.file.list {} output r }
    }"#;
    let error = compile_source(bad, &InMemoryCapabilityCatalog::default())
        .expect_err("undefined vars.missing inside filter rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF2002"));
}

#[test]
fn interval_trigger_compiles_to_interval_ir() {
    let ir = compile(
        include_str!("../examples/coverage/interval_webhook.flow"),
        "interval",
    );
    assert!(
        matches!(ir.spec.trigger, cloudflow_runtime::ir::TriggerIr::Interval { ref every } if every == "5m")
    );
}

#[test]
fn webhook_trigger_compiles_to_http_with_method() {
    let ir = compile(
        include_str!("../examples/coverage/webhook_detail.flow"),
        "webhook",
    );
    assert!(matches!(
        ir.spec.trigger,
        cloudflow_runtime::ir::TriggerIr::Http { ref path, ref method }
            if path == "/webhook/start" && method.as_deref() == Some("POST")
    ));
}

#[test]
fn webhook_rejects_invalid_http_method() {
    let source = r#"workflow "bad_webhook" {
        trigger { http { path = "/x" method = "FETCH" } }
        step s { action builtin.file.list {} }
    }"#;
    let error =
        compile_source(source, &InMemoryCapabilityCatalog::default()).expect_err("invalid method");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4413"));
}

#[test]
fn interval_rejects_zero_duration() {
    let source = r#"workflow "bad_interval" {
        trigger { interval = 0s }
        step s { action builtin.file.list {} }
    }"#;
    let error =
        compile_source(source, &InMemoryCapabilityCatalog::default()).expect_err("zero interval");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4414"));
}

#[test]
fn audit_compiles_to_spec_audit_and_rejects_bad_level() {
    let ir = compile(include_str!("../examples/coverage/audit.flow"), "audit");
    let audit = ir.spec.audit.as_ref().expect("spec.audit present");
    assert_eq!(audit.level, "high");
    let bad = r#"workflow "bad_audit" {
        audit { level = "critical" description = "x" }
        step s { action builtin.file.list {} }
    }"#;
    let error =
        compile_source(bad, &InMemoryCapabilityCatalog::default()).expect_err("bad audit level");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4415"));
}

#[test]
fn notify_and_on_error_compile_to_ir() {
    let ir = compile(
        include_str!("../examples/coverage/notify_on_error.flow"),
        "notify_on_error",
    );
    let serialized = serde_json::to_string(&ir).unwrap();
    assert!(
        serialized.contains("notifyConfig"),
        "notifyConfig: {serialized}"
    );
    assert!(serialized.contains("onError"), "onError present");
    assert!(ir.spec.graph.nodes.iter().any(|n| n.node_type == "notify"));
}

#[test]
fn notify_rejects_unknown_channel() {
    let source = r#"workflow "bad_notify" {
        step s { action builtin.file.list {} }
        notify { channel = "sms-turbo" to = "ops" message = "hi" }
    }"#;
    let error =
        compile_source(source, &InMemoryCapabilityCatalog::default()).expect_err("unknown channel");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4416"));
}

#[test]
fn step_return_compiles_to_output_ref() {
    let ir = compile(include_str!("../examples/coverage/return.flow"), "return");
    let serialized = serde_json::to_string(&ir).unwrap();
    assert!(serialized.contains("output"), "return IR: {serialized}");
}

// ---------- V1.2 Tranche 4：step_group / use-with / conditional depends_on ----------

#[test]
fn step_group_flattens_into_ordered_steps() {
    let ir = compile(
        include_str!("../examples/coverage/step_group.flow"),
        "step_group",
    );
    let ids: Vec<&str> = ir.spec.graph.nodes.iter().map(|n| n.id.as_str()).collect();
    assert!(
        ids.contains(&"agg") && ids.contains(&"publish"),
        "group steps present: {ids:?}"
    );
    assert!(cloudflow_runtime::compiler::validate_ir(&ir).is_empty());
    // start -> finish 之间通过组内步骤连接，无幻影节点。
    let text = serde_json::to_string(&ir).unwrap();
    assert!(!text.contains("__group"), "no phantom group node");
}

#[test]
fn step_group_rejects_empty_group() {
    let source = r#"workflow "empty_group" {
        step a { action builtin.file.list {} }
        step group g {}
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("empty group rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4418"));
}

#[test]
fn step_group_rejects_group_name_clashing_with_step() {
    let source = r#"workflow "clash_group" {
        step run { action builtin.file.list {} }
        step group run { step inner { action builtin.file.list {} } }
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("group/step id clash rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4418"));
}

#[test]
fn step_group_rejects_duplicate_step_ids_inside_group() {
    let source = r#"workflow "dup_group" {
        step group g {
            step x { action builtin.file.list {} }
            step x { action builtin.file.list {} }
        }
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("duplicate group step id rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF2001"));
}

#[test]
fn use_with_rejects_unknown_alias() {
    let source = r#"workflow "bad_use" {
        import "modules/params.flow" as params
        step s { use missing action builtin.file.list {} }
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("use of undefined alias rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4420"));
}

#[test]
fn use_with_accepts_declared_alias() {
    let source = r#"workflow "ok_use" {
        import "modules/params.flow" as params
        step s { use params action builtin.file.list {} output r }
    }"#;
    // 内联模式(compile_source)下 include 会被 CF3103 拒绝，但语义校验仍应通过 use 别名检查；
    // 此处仅验证未引入 CF4420（use 别名合法）。
    let result = compile_source(source, &InMemoryCapabilityCatalog::default());
    if let Err(error) = result {
        assert!(
            error.diagnostics.iter().all(|d| d.code != "CF4420"),
            "自定义 alias 不应报 CF4420: {error:?}"
        );
    }
}

#[test]
fn conditional_depends_emits_depends_condition_ir() {
    let ir = compile(
        include_str!("../examples/coverage/conditional_depends.flow"),
        "cond_depends",
    );
    let report = ir
        .spec
        .graph
        .nodes
        .iter()
        .find(|n| n.id == "report")
        .expect("report node");
    assert!(
        report.depends_condition.is_some(),
        "dependsCondition present"
    );
    assert!(report.depends_on.contains(&"collect".to_string()));
    assert!(cloudflow_runtime::compiler::validate_ir(&ir).is_empty());
}

#[test]
fn conditional_depends_rejects_non_boolean_condition() {
    let source = r#"workflow "bad_cond_dep" {
        variables { n: number = 3 }
        step a { action builtin.file.list {} output r }
        step b { depends_on a if vars.n + 1 action builtin.file.list {} }
    }"#;
    let error = compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect_err("non-boolean depends condition rejected");
    assert!(error.diagnostics.iter().any(|d| d.code == "CF4421"));
}
