//! [19.x] 性能与安全边界测试：异常输入必须得到优雅错误，不允许 panic / 资源失控。
//!
//! 覆盖：YAML 尺寸/深度/别名展开护栏（19.9/19.10/19.25）、表达式长度防线
//! （19.16）、解析缓存正确性（19.3/19.27）、表达式求值沙箱白名单（19.11/19.12）、
//! 以及 HTTP 编译接口的超大请求体处理（19.13 不泄露内部信息）。

use cloudflow_runtime::expression::{self, parse_expression_string, MAX_EXPRESSION_CHARS};

// —— YAML 资源护栏 -----------------------------------------------------------

fn parse_yaml_source(source: &str) -> Result<Vec<String>, String> {
    match cloudflow_runtime::yaml::parse_yaml_detailed(source, "bound.yaml") {
        Ok((_, diagnostics)) => Ok(diagnostics.into_iter().map(|d| d.code).collect()),
        Err(diagnostic) => Err(diagnostic.code),
    }
}

#[test]
fn yaml_source_over_1mib_rejected() {
    // 注释行撑大体积：超过 1 MiB 且语法合法，必须在进入 libyaml 前被护栏拦截。
    let line = "# pad ".to_string() + &"x".repeat(96) + "\n";
    let source = vec![line; 12_000].join("");
    assert!(source.len() > 1024 * 1024);
    let error = parse_yaml_source(&source).expect_err("oversized source must be rejected");
    assert_eq!(error, "CFY-SCHEMA-1005");
}

#[test]
fn yaml_deep_nesting_rejected_by_cloudflow_guard() {
    // 120 层嵌套：仍在 libyaml 内置限制之内（它约 121 层失败），
    // 由 CloudFlow 自身的 MAX_YAML_DEPTH=100 防线拦截（双保障层 1）。
    let source = "[".repeat(120) + &"]".repeat(120);
    let error = parse_yaml_source(&source).expect_err("deep nesting must be rejected");
    assert_eq!(error, "CFY-SCHEMA-1006");
}

#[test]
fn yaml_beyond_library_limit_rejected_by_library() {
    // 300 层嵌套：libyaml 先于 CloudFlow 防线报错（双保障层 2），仍为优雅错误。
    let source = "[".repeat(300) + &"]".repeat(300);
    let error = parse_yaml_source(&source).expect_err("ultra-deep nesting must be rejected");
    assert!(
        matches!(error.as_str(), "CFY-SCHEMA-1006" | "CFY-1001"),
        "expected guard rejection, got {error}"
    );
}

#[test]
fn yaml_alias_explosion_rejected() {
    // 别名炸弹变体：1 个 1000 元素序列被 200 个别名引用，展开后节点数 ~20 万。
    let mut source = String::from("base: &b [");
    for index in 0..1000 {
        if index > 0 {
            source.push_str(", ");
        }
        source.push_str(&index.to_string());
    }
    source.push_str("]\n");
    for index in 0..200 {
        source.push_str(&format!("k{index:03}: *b\n"));
    }
    let error = parse_yaml_source(&source).expect_err("alias explosion must be rejected");
    assert_eq!(error, "CFY-SCHEMA-1007");
}

#[test]
fn yaml_within_limits_still_validated_by_schema() {
    // 护栏不得误伤合法输入：小文档仍走正常 Schema 校验（steps 缺失报错）。
    let source = "name: s\n";
    match parse_yaml_source(source) {
        Ok(codes) => assert!(
            codes.iter().any(|code| code.starts_with("CFY-SCHEMA-")),
            "small doc must reach schema validation, got: {codes:?}"
        ),
        Err(code) => panic!("small doc must parse, got {code}"),
    }
}

// —— 表达式长度与缓存 ---------------------------------------------------------

#[test]
fn expression_over_length_rejected_without_panic() {
    let long = "vars.a + ".repeat(4000).trim_end().to_string();
    assert!(long.chars().count() > MAX_EXPRESSION_CHARS);
    let error = parse_expression_string(&long, "src", "e.expr", 0)
        .expect_err("oversized expression must be rejected");
    assert_eq!(error.code, "CFY-EXPR-103");
}

#[test]
fn deep_parenthesis_expression_rejected_before_parse() {
    // 5000 层嵌套括号：必须被 CFY-EXPR-104 防线拦截（硬捧栈溢出）。
    let deep = "(".repeat(5000) + "1" + &")".repeat(5000);
    let error =
        parse_expression_string(&deep, "src", "e.expr", 0).expect_err("must reject deep nesting");
    assert_eq!(error.code, "CFY-EXPR-104");
}

#[test]
fn deep_ternary_expression_rejected_before_parse() {
    // 600 个右嵌套三元运符：长度在限制内，但超过三元符数上限 512。
    let deep = "vars.x ? 1 : ".repeat(600) + "0";
    let error =
        parse_expression_string(&deep, "src", "e.expr", 0).expect_err("must reject ternary bomb");
    assert_eq!(error.code, "CFY-EXPR-104");
}

#[test]
fn parentheses_inside_strings_do_not_trip_guard() {
    // 字符串内的括号/三元符不属于解析嵌套，不得误伤。
    let source = r#"vars.label == "(((((((((( ?" && vars.y > 0"#;
    assert!(
        parse_expression_string(source, "src", "e.expr", 0).is_ok(),
        "string content must not count toward nesting"
    );
}

#[test]
fn expression_parse_cache_is_correct_across_sources() {
    expression::clear_parse_caches();
    let source_a = "line one\nvars.dir";
    let source_b = "vars.dir";
    // base 为表达式文本在 source 中的字节偏移：'line one' 占 0..8，换行在第 8 位。
    let in_a = parse_expression_string("vars.dir", source_a, "a.expr", 9).expect("source a");
    let in_b = parse_expression_string("vars.dir", source_b, "b.expr", 0).expect("source b");
    // 缓存命中同一相对解析结果，但 rebase 后的源码坐标必须各自正确。
    assert_eq!(in_a.span.line, 2, "span in source_a must point at line 2");
    assert_eq!(in_b.span.line, 1, "span in source_b must point at line 1");
    let (entries, capacity) = expression::expression_cache_stats();
    assert!(entries >= 1 && capacity > 0, "cache must hold entries");
    expression::clear_parse_caches();
    assert_eq!(expression::expression_cache_stats().0, 0);
}

// —— 求值沙箱白名单（19.11/19.12）--------------------------------------------

#[test]
fn evaluator_rejects_non_whitelisted_functions() {
    use cloudflow_runtime::expression::eval::call_builtin;
    for name in ["system", "exec", "drop_table", "spawn", "fetch"] {
        let result = call_builtin(name, &[]);
        assert!(
            result.is_err(),
            "function {name} must not be callable through the whitelist"
        );
    }
}

// —— HTTP 超大请求体（19.13）--------------------------------------------------

#[tokio::test]
async fn http_rejects_oversized_compile_body_without_leaking_paths() {
    use axum::{body::Body, http::Request};
    use cloudflow_runtime::http::{build_router, HttpConfig};
    use http_body_util::BodyExt;
    use tower::ServiceExt;

    let router = build_router(HttpConfig {
        service_token: "bounds-token".into(),
        capabilities: vec![],
        max_concurrency: 2,
        allowed_origins: vec![],
        enable_dev_execute: false,
    });
    let body = "name: s\nsteps:\n  - id: a\n".to_string() + &"# x".repeat(700_000);
    assert!(body.len() > 1024 * 1024);
    let request = Request::builder()
        .method("POST")
        .uri("/api/v1/compile")
        .header("X-PCD-Service-Token", "bounds-token")
        .header("content-type", "application/json")
        .body(Body::from(
            serde_json::json!({ "source": body, "language": "yaml" }).to_string(),
        ))
        .expect("request");
    let response = router.oneshot(request).await.expect("dispatch");
    assert_eq!(response.status(), axum::http::StatusCode::PAYLOAD_TOO_LARGE);
    let bytes = response
        .into_body()
        .collect()
        .await
        .expect("body")
        .to_bytes();
    let text = String::from_utf8_lossy(&bytes);
    assert!(
        !text.contains("/Users/") && !text.contains("ProgramDir"),
        "error body must not leak absolute paths: {text}"
    );
}
