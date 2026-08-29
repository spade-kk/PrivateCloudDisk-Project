use axum::{
    body::Body,
    http::{Request, StatusCode},
};
use cloudflow_runtime::{
    ast::{FlowNode, ValueNode},
    compile_source_named,
    http::{build_router, HttpConfig, MAX_COMPILE_BODY_BYTES},
    parser::parse_source,
    semantic::InMemoryCapabilityCatalog,
};
use http_body_util::BodyExt;
use serde_json::Value;
use std::{process::Command, time::Instant};
use tower::ServiceExt;

const TOKEN: &str = "test-internal-token";
const FILE_APPROVAL: &str = include_str!("../examples/file_approval.flow");
const DATA_AGGREGATION: &str = include_str!("../examples/data_aggregation.flow");

fn compile(
    source: &str,
) -> Result<cloudflow_runtime::ir::WorkflowIrV1, cloudflow_runtime::CompileError> {
    compile_source_named(
        source,
        "compliance.flow",
        &InMemoryCapabilityCatalog::default(),
    )
}

#[test]
fn compiles_all_distributed_flow_examples() {
    for source in [FILE_APPROVAL, DATA_AGGREGATION] {
        let ir = compile(source).expect("published example must compile");
        assert_eq!(ir.api_version, "workflow.cloudflow.io/v1");
        assert!(!ir.spec.graph.nodes.is_empty());
    }
}

#[test]
fn rejects_unknown_top_and_step_blocks_with_aggregated_diagnostics() {
    let error = compile(
        r#"workflow "bad" { triger { manual {} } step ok { action file.list {} mystery {} } }"#,
    )
    .expect_err("must reject unknown blocks");
    assert_eq!(
        error
            .diagnostics
            .iter()
            .filter(|value| value.code == "CF1202")
            .count(),
        2
    );
    assert_eq!(error.diagnostics[0].suggestions, vec!["trigger"]);
    assert!(error
        .diagnostics
        .iter()
        .all(|value| !value.cli_output.is_empty() && !value.documentation_url.is_empty()));
}

#[test]
fn rejects_uppercase_keywords_unclosed_strings_and_invalid_expressions() {
    for source in [
        r#"Workflow "bad" { step ok { action file.list {} } }"#,
        r#"workflow "bad { step ok { action file.list {} } }"#,
        r#"workflow "bad" { step ok { condition { vars.value ?? 1 } action file.list {} } }"#,
    ] {
        assert!(
            compile(source).is_err(),
            "invalid source was accepted: {source}"
        );
    }
}

#[test]
fn builds_complete_control_flow_ast_and_ir() {
    let source = r#"
workflow "controls" {
  variables { files = input.array(required = true) }
  step seed { action file.list {} output files retry { max_attempts = 2 strategy = "exponential" } }
  if { len(vars.files) > 0 } {
    parallel { step copy { action file.copy {} } step move { action file.move {} } }
  } else { wait approval { timeout = 24h } }
  foreach item in vars.files { step each { action file.copy {} } }
  try { step run { action file.save {} } }
  catch error { step notify { action notification.send {} } }
  finally { wait cleanup { timeout = 1m } }
}"#;
    let ast = parse_source(source, "controls.flow").expect("strict AST");
    assert!(matches!(ast.controls[0], FlowNode::Condition(_)));
    assert!(matches!(ast.controls[1], FlowNode::Loop(_)));
    assert!(matches!(ast.controls[2], FlowNode::TryCatch(_)));
    assert_eq!(ast.steps[0].retry.as_ref().expect("retry").max_attempts, 2);

    let ir = compile(source).expect("control IR");
    for node_type in ["condition", "parallel", "wait", "loop", "try"] {
        assert!(
            ir.spec
                .graph
                .nodes
                .iter()
                .any(|node| node.node_type == node_type),
            "missing {node_type}"
        );
    }
    assert!(ir.spec.graph.edges.len() >= 7);
}

#[test]
fn emits_nested_step_control_nodes_and_edges_instead_of_summary_only() {
    let ir = compile(
        r#"workflow "nested" {
          step orchestrate {
            if { true } { step child { action file.list {} } }
          }
        }"#,
    )
    .expect("nested controls");
    let condition = ir
        .spec
        .graph
        .nodes
        .iter()
        .find(|node| node.node_type == "condition")
        .expect("condition node");
    assert!(ir.spec.graph.nodes.iter().any(|node| node.id == "child"));
    assert!(ir
        .spec
        .graph
        .edges
        .iter()
        .any(|edge| edge.from == "orchestrate" && edge.to == condition.id));
    assert!(ir
        .spec
        .graph
        .edges
        .iter()
        .any(|edge| edge.from == condition.id && edge.to == "child"));
}

#[test]
fn preserves_typed_values_and_normalizes_references() {
    let source = r#"
workflow "typed" {
  variables { source = input.string(required = true) threshold = input.number(required = false, default = 10) }
  step list { action file.list { node = vars.source limit = 25 enabled = true } output files }
  step save { depends_on list condition { steps.list.output.count > 0 } action file.save { source = list.output.file_id target = "archive" } }
}"#;
    let ast = parse_source(source, "typed.flow").expect("typed AST");
    assert!(matches!(
        ast.steps[0].action.as_ref().expect("action").arguments["limit"],
        ValueNode::Number(_)
    ));
    let ir = compile(source).expect("typed IR");
    let first = ir
        .spec
        .graph
        .nodes
        .iter()
        .find(|node| node.id == "list")
        .expect("list");
    assert_eq!(
        first.action.as_ref().expect("action").arguments["node"]["$ref"],
        "vars.source"
    );
    let save = ir
        .spec
        .graph
        .nodes
        .iter()
        .find(|node| node.id == "save")
        .expect("save");
    assert_eq!(
        save.action.as_ref().expect("action").arguments["source"]["$ref"],
        "steps.list.output.file_id"
    );
}

#[test]
fn reports_all_missing_dependencies_and_invalid_plugin_contract() {
    let error = compile(
        r#"
workflow "broken" {
  step a { depends_on missing_a action plugin { id = "" function = "" } }
  step b { depends_on missing_b action file.list {} }
}"#,
    )
    .expect_err("semantic errors");
    assert_eq!(
        error
            .diagnostics
            .iter()
            .filter(|value| value.code == "CF2002")
            .count(),
        2
    );
    assert!(error.diagnostics.iter().any(|value| value.code == "CF3001"));
}

#[test]
fn rejects_duplicate_variables_unknown_input_options_and_invalid_required_type() {
    let error = compile(
        r#"workflow "bad_variables" {
          variables {
            item = input.string(required = "yes", custom = true)
            item = input.number(default = 1)
          }
          step run { action file.list {} }
        }"#,
    )
    .expect_err("variable declaration must be strict");
    for code in ["CF2101", "CF1202", "CF2001"] {
        assert!(
            error.diagnostics.iter().any(|value| value.code == code),
            "missing {code}: {:?}",
            error.diagnostics
        );
    }
}

#[test]
fn compiles_one_hundred_steps_within_budget() {
    let mut source = String::from("workflow \"performance\" {");
    for index in 0..100 {
        source.push_str(&format!(
            " step step_{index} {{ action file.list {{ limit = {index} }} }}"
        ));
    }
    source.push('}');
    let started = Instant::now();
    let ir = compile(&source).expect("100 steps");
    assert_eq!(ir.spec.graph.nodes.len(), 100);
    assert!(
        started.elapsed().as_millis() < 500,
        "compile took {:?}",
        started.elapsed()
    );
}

#[test]
fn cli_supports_compile_json_check_only_and_rejects_invalid_target() {
    let binary = env!("CARGO_BIN_EXE_cloudflowc");
    let valid = Command::new(binary)
        .args([
            "compile",
            "-i",
            "workflow \"cli\" { step run { action file.list {} } }",
            "--check-only",
            "--output-format",
            "json",
            "--no-color",
        ])
        .output()
        .expect("run CLI");
    assert!(valid.status.success());
    assert!(String::from_utf8_lossy(&valid.stdout).contains("\"valid\":true"));

    let invalid = Command::new(binary)
        .args([
            "compile",
            "-i",
            "workflow \"cli\" { triger {} }",
            "--output-format",
            "json",
            "--no-color",
            "--explain",
        ])
        .output()
        .expect("run invalid CLI");
    assert_eq!(invalid.status.code(), Some(1));
    let stderr = String::from_utf8_lossy(&invalid.stderr);
    assert!(stderr.contains("CF1202") && stderr.contains("cliOutput"));

    let target = Command::new(binary)
        .args([
            "compile",
            "-i",
            "workflow \"cli\" {}",
            "--target",
            "v2",
            "--no-color",
        ])
        .output()
        .expect("run target CLI");
    assert_eq!(target.status.code(), Some(2));

    let human = Command::new(binary)
        .args([
            "compile",
            "-i",
            "workflow \"cli\" { triger {} }",
            "--explain",
        ])
        // CI 中 stderr 通常不是 TTY，强制开启颜色后验证 miette 彩色诊断。
        .env("CLICOLOR_FORCE", "1")
        .output()
        .expect("run colored CLI");
    assert_eq!(human.status.code(), Some(1));
    assert!(
        human.stderr.windows(2).any(|bytes| bytes == b"\x1b["),
        "human diagnostics should contain ANSI color when color is forced"
    );
}

#[tokio::test]
async fn http_compile_health_auth_and_diagnostics_contract() {
    let app = build_router(HttpConfig {
        service_token: TOKEN.into(),
        max_concurrency: 2,
        ..Default::default()
    });
    let health = app
        .clone()
        .oneshot(
            Request::builder()
                .uri("/health")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(health.status(), StatusCode::OK);

    let unauthorized = app
        .clone()
        .oneshot(compile_request(
            None,
            "workflow \"ok\" { step run { action file.list {} } }",
        ))
        .await
        .unwrap();
    assert_eq!(unauthorized.status(), StatusCode::UNAUTHORIZED);

    let valid = app
        .clone()
        .oneshot(compile_request(
            Some(TOKEN),
            "workflow \"ok\" { step run { action file.list {} } }",
        ))
        .await
        .unwrap();
    assert_eq!(valid.status(), StatusCode::OK);
    let body: Value =
        serde_json::from_slice(&valid.into_body().collect().await.unwrap().to_bytes()).unwrap();
    assert_eq!(body["valid"], true);
    assert_eq!(body["targetIrVersion"], "workflow.cloudflow.io/v1");

    let invalid = app
        .clone()
        .oneshot(compile_request(
            Some(TOKEN),
            "workflow \"bad\" { triger {} }",
        ))
        .await
        .unwrap();
    assert_eq!(invalid.status(), StatusCode::UNPROCESSABLE_ENTITY);
    let body: Value =
        serde_json::from_slice(&invalid.into_body().collect().await.unwrap().to_bytes()).unwrap();
    assert_eq!(body["diagnostics"][0]["code"], "CF1202");
    assert!(body["diagnostics"][0]["cliOutput"]
        .as_str()
        .unwrap()
        .contains('\n'));

    let malformed = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/api/v1/compile")
                .header("content-type", "application/json")
                .header("X-PCD-Service-Token", TOKEN)
                .body(Body::from("{not-json"))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(malformed.status(), StatusCode::BAD_REQUEST);
    let body: Value =
        serde_json::from_slice(&malformed.into_body().collect().await.unwrap().to_bytes()).unwrap();
    assert_eq!(body["diagnostics"][0]["code"], "CF1101");
}

#[tokio::test]
async fn http_rejects_body_larger_than_one_megabyte() {
    let app = build_router(HttpConfig {
        service_token: TOKEN.into(),
        ..Default::default()
    });
    let oversized = "x".repeat(MAX_COMPILE_BODY_BYTES + 1);
    let response = app
        .oneshot(compile_request(Some(TOKEN), &oversized))
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::PAYLOAD_TOO_LARGE);
    let body: Value =
        serde_json::from_slice(&response.into_body().collect().await.unwrap().to_bytes()).unwrap();
    assert_eq!(body["diagnostics"][0]["code"], "CF1104");
}

#[tokio::test]
async fn http_cors_only_echoes_configured_origin() {
    let app = build_router(HttpConfig {
        service_token: TOKEN.into(),
        allowed_origins: vec!["https://ide.example.com".into()],
        ..Default::default()
    });
    let mut allowed_request = compile_request(
        Some(TOKEN),
        "workflow \"cors\" { step run { action file.list {} } }",
    );
    allowed_request
        .headers_mut()
        .insert("origin", "https://ide.example.com".parse().unwrap());
    let response = app.clone().oneshot(allowed_request).await.unwrap();
    assert_eq!(
        response
            .headers()
            .get("access-control-allow-origin")
            .and_then(|value| value.to_str().ok()),
        Some("https://ide.example.com")
    );

    let mut denied_request = compile_request(
        Some(TOKEN),
        "workflow \"cors\" { step run { action file.list {} } }",
    );
    denied_request
        .headers_mut()
        .insert("origin", "https://attacker.example".parse().unwrap());
    let response = app.oneshot(denied_request).await.unwrap();
    assert!(response
        .headers()
        .get("access-control-allow-origin")
        .is_none());
}

#[tokio::test]
async fn runtime_execution_api_supports_status_pause_cancel_retry_and_logs() {
    let app = build_router(HttpConfig {
        service_token: TOKEN.into(),
        ..Default::default()
    });
    let ir = compile("workflow \"run\" { step one { action file.list {} } }").unwrap();
    let start = authorized_json_request(
        "POST",
        "/api/v1/executions",
        serde_json::json!({"executionId":"exec-test","ir":ir,"variables":{}}),
    );
    let response = app.clone().oneshot(start).await.unwrap();
    assert_eq!(response.status(), StatusCode::ACCEPTED);

    for (path, expected) in [
        ("/api/v1/executions/exec-test/pause", "WAITING"),
        ("/api/v1/executions/exec-test/resume", "READY"),
        ("/api/v1/executions/exec-test/cancel", "CANCELLED"),
        ("/api/v1/executions/exec-test/retry", "READY"),
    ] {
        let response = app
            .clone()
            .oneshot(authorized_json_request("POST", path, serde_json::json!({})))
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK);
        let body: Value =
            serde_json::from_slice(&response.into_body().collect().await.unwrap().to_bytes())
                .unwrap();
        assert_eq!(body["status"], expected);
    }

    let response = app
        .oneshot(authorized_json_request(
            "GET",
            "/api/v1/executions/exec-test/logs",
            serde_json::json!({}),
        ))
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    let body: Value =
        serde_json::from_slice(&response.into_body().collect().await.unwrap().to_bytes()).unwrap();
    assert_eq!(body["logs"].as_array().unwrap().len(), 5);
}

fn compile_request(token: Option<&str>, source: &str) -> Request<Body> {
    let body =
        serde_json::json!({"source": source, "filename": "http.flow", "target_ir_version": "v1"})
            .to_string();
    let mut builder = Request::builder()
        .method("POST")
        .uri("/api/v1/compile")
        .header("content-type", "application/json");
    if let Some(token) = token {
        builder = builder.header("X-PCD-Service-Token", token);
    }
    builder.body(Body::from(body)).unwrap()
}

fn authorized_json_request(method: &str, uri: &str, body: Value) -> Request<Body> {
    Request::builder()
        .method(method)
        .uri(uri)
        .header("content-type", "application/json")
        .header("X-PCD-Service-Token", TOKEN)
        .body(Body::from(body.to_string()))
        .unwrap()
}
