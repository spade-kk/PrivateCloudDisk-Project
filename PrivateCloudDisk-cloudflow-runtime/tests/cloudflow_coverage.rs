//! CloudFlow V1.1 语法覆盖契约。
//!
//! [CLOUDFLOW-COVERAGE-001] 每一个 examples/coverage/*.flow 都是用户可运行的规范资产，
//! 此测试与 CLI 覆盖脚本互补：这里验证 AST/IR 语义映射，脚本验证真实二进制输出 JSON。

use cloudflow_runtime::{compile_source_named, semantic::InMemoryCapabilityCatalog};
use serde_json::Value;
use std::path::Path;

const CASES: &[(&str, &str)] = &[
    (
        "basic_workflow",
        include_str!("../examples/coverage/basic_workflow.flow"),
    ),
    (
        "schedule_trigger",
        include_str!("../examples/coverage/schedule_trigger.flow"),
    ),
    (
        "event_trigger",
        include_str!("../examples/coverage/event_trigger.flow"),
    ),
    (
        "http_trigger",
        include_str!("../examples/coverage/http_trigger.flow"),
    ),
    (
        "variables_basic",
        include_str!("../examples/coverage/variables_basic.flow"),
    ),
    (
        "depends_on",
        include_str!("../examples/coverage/depends_on.flow"),
    ),
    ("if_else", include_str!("../examples/coverage/if_else.flow")),
    ("foreach", include_str!("../examples/coverage/foreach.flow")),
    (
        "parallel",
        include_str!("../examples/coverage/parallel.flow"),
    ),
    (
        "retry_timeout",
        include_str!("../examples/coverage/retry_timeout.flow"),
    ),
    (
        "try_catch_finally",
        include_str!("../examples/coverage/try_catch_finally.flow"),
    ),
    ("wait", include_str!("../examples/coverage/wait.flow")),
    (
        "expression_complex",
        include_str!("../examples/coverage/expression_complex.flow"),
    ),
    (
        "plugin_action",
        include_str!("../examples/coverage/plugin_action.flow"),
    ),
    (
        "while_assert",
        include_str!("../examples/coverage/while_assert.flow"),
    ),
    (
        "full_workflow",
        include_str!("../examples/coverage/full_workflow.flow"),
    ),
    (
        "handlers",
        include_str!("../examples/coverage/handlers.flow"),
    ),
    ("switch", include_str!("../examples/coverage/switch.flow")),
    ("delay", include_str!("../examples/coverage/delay.flow")),
    (
        "timeout_block",
        include_str!("../examples/coverage/timeout_block.flow"),
    ),
    (
        "retry_on",
        include_str!("../examples/coverage/retry_on.flow"),
    ),
    (
        "environment_namespace",
        include_str!("../examples/coverage/environment_namespace.flow"),
    ),
    (
        "tags_changelog",
        include_str!("../examples/coverage/tags_changelog.flow"),
    ),
    (
        "for_range",
        include_str!("../examples/coverage/for_range.flow"),
    ),
    (
        "for_collection",
        include_str!("../examples/coverage/for_collection.flow"),
    ),
    (
        "validate",
        include_str!("../examples/coverage/validate.flow"),
    ),
    (
        "parallel_max",
        include_str!("../examples/coverage/parallel_max.flow"),
    ),
    (
        "pipeline",
        include_str!("../examples/coverage/pipeline.flow"),
    ),
    (
        "template",
        include_str!("../examples/coverage/template.flow"),
    ),
    (
        "step_group",
        include_str!("../examples/coverage/step_group.flow"),
    ),
    (
        "conditional_depends",
        include_str!("../examples/coverage/conditional_depends.flow"),
    ),
    (
        "interval_webhook",
        include_str!("../examples/coverage/interval_webhook.flow"),
    ),
    (
        "webhook_detail",
        include_str!("../examples/coverage/webhook_detail.flow"),
    ),
    ("audit", include_str!("../examples/coverage/audit.flow")),
    (
        "notify_on_error",
        include_str!("../examples/coverage/notify_on_error.flow"),
    ),
    ("return", include_str!("../examples/coverage/return.flow")),
];

fn compile(source: &str, name: &str) -> cloudflow_runtime::ir::WorkflowIrV1 {
    compile_source_named(
        source,
        &format!("coverage/{name}.flow"),
        &InMemoryCapabilityCatalog::default(),
    )
    .unwrap_or_else(|error| panic!("{name} must compile: {error:?}"))
}

#[test]
fn every_coverage_flow_compiles_to_machine_readable_v1_ir() {
    for (name, source) in CASES {
        let ir = compile(source, name);
        assert_eq!(ir.api_version, "workflow.cloudflow.io/v1", "{name}");
        assert!(!ir.spec.graph.nodes.is_empty(), "{name}");
        assert!(
            cloudflow_runtime::compiler::validate_ir(&ir).is_empty(),
            "{name}"
        );
        serde_json::to_string(&ir).expect("IR JSON must serialize");
    }
}

#[test]
fn typed_variables_references_and_expression_are_not_downgraded_to_strings() {
    let ir = compile(
        include_str!("../examples/coverage/variables_basic.flow"),
        "variables_basic",
    );
    assert_eq!(ir.spec.variables["x"].value, Some(Value::from(10)));
    assert_eq!(
        ir.spec.variables["ratio"].value,
        Some(serde_json::json!(1.25e3))
    );
    assert_eq!(ir.spec.variables["flag"].value, Some(Value::Bool(true)));
    assert!(ir.spec.variables["list"].value.as_ref().unwrap().is_array());
    assert!(ir.spec.variables["options"]
        .value
        .as_ref()
        .unwrap()
        .is_object());
    assert_eq!(ir.spec.variables["source"].source, "input");

    let expressions = compile(
        include_str!("../examples/coverage/expression_complex.flow"),
        "expression_complex",
    );
    let action = expressions
        .spec
        .graph
        .nodes
        .iter()
        .find(|node| node.id == "evaluate")
        .and_then(|node| node.action.as_ref())
        .expect("evaluate action");
    assert!(action.arguments["score"].get("$expr").is_some());
    assert_eq!(
        action.arguments["upstream"]["$ref"],
        Value::String("steps.collect.output.files".into())
    );
    assert!(action.arguments["typed_object"]["region"]
        .get("$ref")
        .is_some());
}

#[test]
fn control_flow_has_complete_ir_mapping() {
    let ir = compile(
        include_str!("../examples/coverage/full_workflow.flow"),
        "full_workflow",
    );
    for node_type in ["condition", "loop", "parallel", "try", "wait", "assert"] {
        assert!(
            ir.spec
                .graph
                .nodes
                .iter()
                .any(|node| node.node_type == node_type),
            "IR must contain {node_type}"
        );
    }
    let foreach = ir
        .spec
        .graph
        .nodes
        .iter()
        .find(|node| node.node_type == "loop")
        .expect("foreach");
    assert_eq!(foreach.loop_config.as_ref().unwrap()["kind"], "foreach");
    let waiting = ir
        .spec
        .graph
        .nodes
        .iter()
        .find(|node| node.node_type == "wait")
        .expect("wait");
    assert_eq!(waiting.timeout.as_deref(), Some("24h"));
    let try_node = ir
        .spec
        .graph
        .nodes
        .iter()
        .find(|node| node.node_type == "try")
        .expect("try");
    assert!(try_node
        .error_handler
        .as_ref()
        .unwrap()
        .get("catch")
        .is_some());

    // [CLOUDFLOW-ORDER-001] `finalize` 位于 wait 之后；它不能因为没有显式 depends_on
    // 而被 Worker 在审批前抢先领取。Compiler 必须把控制节点作为顺序边界写入 edges。
    let wait_id = waiting.id.clone();
    assert!(ir
        .spec
        .graph
        .edges
        .iter()
        .any(|edge| edge.from == wait_id && edge.to == "finalize"));
}

#[test]
fn include_merges_only_sandboxed_flow_modules_in_file_mode() {
    let path = Path::new(env!("CARGO_MANIFEST_DIR")).join("examples/coverage/include.flow");
    let source = std::fs::read_to_string(&path).expect("include fixture");
    let ir = compile_source_named(
        &source,
        path.to_str().expect("utf8 fixture path"),
        &InMemoryCapabilityCatalog::default(),
    )
    .expect("relative module inside coverage root must compile");
    assert!(ir
        .spec
        .graph
        .nodes
        .iter()
        .any(|node| node.id == "shared_step"));
    assert_eq!(ir.extensions["includes"][0], "modules/common.flow");

    let error = compile_source_named(
        r#"workflow "unsafe" { include "../secret.flow" step run { action builtin.file.list {} } }"#,
        path.to_str().expect("utf8 fixture path"),
        &InMemoryCapabilityCatalog::default(),
    )
    .expect_err("path traversal must be rejected");
    assert!(error
        .diagnostics
        .iter()
        .any(|diagnostic| diagnostic.code == "CF3103"));
}

#[test]
fn variable_type_and_reference_errors_are_rejected_structurally() {
    for (source, code) in [
        (
            r#"workflow "bad_type" { variables { x: number = "not-a-number" } step run { action builtin.file.list {} } }"#,
            "CF2101",
        ),
        (
            r#"workflow "bad_ref" { variables { x: number = vars.missing } step run { action builtin.file.list {} } }"#,
            "CF2002",
        ),
        (
            r#"workflow "bad_step_ref" { step run { action builtin.file.list { source = steps.missing.output.file } } }"#,
            "CF2002",
        ),
        (
            r#"workflow "bad_nested_ref" { foreach item in [1] { step run { action builtin.file.list { source = vars.missing } } } }"#,
            "CF2002",
        ),
        (
            r#"workflow "escaped_loop_local" { foreach item in [1] { step run { action builtin.file.list { source = item } } } step escaped { action builtin.file.list { source = item } } }"#,
            "CF2002",
        ),
        (
            r#"workflow "bad_expression_type" { variables { total: number = true + 1 } step run { action builtin.file.list {} } }"#,
            "CF2101",
        ),
    ] {
        let error = compile_source_named(
            source,
            "invalid_variable.flow",
            &InMemoryCapabilityCatalog::default(),
        )
        .expect_err("invalid variable workflow must fail");
        assert!(
            error
                .diagnostics
                .iter()
                .any(|diagnostic| diagnostic.code == code),
            "{source}: {error:?}"
        );
    }
}

#[test]
fn use_with_imports_modules_in_file_mode() {
    let path = Path::new(env!("CARGO_MANIFEST_DIR")).join("examples/coverage/use_with.flow");
    let source = std::fs::read_to_string(&path).expect("use_with fixture");
    let filename = path.to_str().expect("utf8 path");
    let ir = cloudflow_runtime::compile_source_named(
        &source,
        filename,
        &cloudflow_runtime::semantic::InMemoryCapabilityCatalog::default(),
    )
    .expect("use_with must compile in file mode");
    let serialized = serde_json::to_value(&ir).unwrap();
    // 模块默认参数 region="cn"、timeout=30 注入 deploy 步骤的 action arguments。
    let text = serialized.to_string();
    assert!(text.contains("\"region\""), "region injected: {text}");
    assert!(text.contains("\"timeout\""), "timeout injected: {text}");
    let deploy = serialized["spec"]["graph"]["nodes"]
        .as_array()
        .unwrap()
        .iter()
        .find(|n| n["id"] == "deploy")
        .expect("deploy node");
    assert!(deploy["action"]["arguments"]["region"].is_string());
    assert!(deploy["action"]["arguments"]["timeout"].is_number());
}
