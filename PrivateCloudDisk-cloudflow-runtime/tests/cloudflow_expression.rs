//! CloudFlow 表达式子系统测试（需求 6.23：表达式子系统有完整的单元测试）。
//!
//! 覆盖：字面量、引用/属性/索引访问、算术/比较/逻辑/三元、白名单函数、管道、
//! 字符串插值、常量、错误定位，以及 DSL 前端委托到子系统的等价性（6.21/6.31）。

use cloudflow_runtime::ast::ExpressionKind;
use cloudflow_runtime::ast::ValueNode;
use cloudflow_runtime::expression::{parse_expression_string, parse_value_string};

/// 把表达式原文嵌入一段源文本、计算字节偏移后交给子系统解析。
fn parse_ok(text: &str) -> cloudflow_runtime::ast::ExpressionNode {
    let source = format!("prefix: {text}");
    let base = source.find(text).expect("text present");
    parse_expression_string(text, &source, "test.flow", base)
        .unwrap_or_else(|err| panic!("{text:?} 解析失败：{}", err.message))
}

fn reference(name: &str) -> ExpressionKind {
    ExpressionKind::Reference(name.to_owned())
}

#[test]
fn expression_literals_parse() {
    // 需求 6.3 + 11.12：数字保持 serde_json::Number，禁止文本降级。
    match &parse_ok("1024").kind {
        ExpressionKind::Literal(ValueNode::Number(n)) => assert_eq!(n.as_i64(), Some(1024)),
        other => panic!("unexpected {other:?}"),
    }
    assert_eq!(
        parse_ok("2.5").kind,
        ExpressionKind::Literal(ValueNode::Number(
            serde_json::Number::from_f64(2.5).unwrap()
        ))
    );
    assert_eq!(
        parse_ok("\"hello\"").kind,
        ExpressionKind::Literal(ValueNode::String("hello".into()))
    );
    assert_eq!(
        parse_ok("true").kind,
        ExpressionKind::Literal(ValueNode::Boolean(true))
    );
}

#[test]
fn expression_references_and_paths() {
    // 需求 6.4/6.5/6.6：vars./steps. 引用、属性访问、索引访问。
    assert_eq!(parse_ok("vars.items").kind, reference("vars.items"));
    assert_eq!(
        parse_ok("steps.download.output.file_path").kind,
        reference("steps.download.output.file_path")
    );
    assert_eq!(parse_ok("item.name").kind, reference("item.name"));
    assert_eq!(parse_ok("files[0]").kind, reference("files[0]"));
    assert_eq!(parse_ok("vars.matrix[2]").kind, reference("vars.matrix[2]"));
    // 局部引用（foreach 迭代变量 / catch 绑定）。
    assert_eq!(parse_ok("item").kind, reference("item"));
}

#[test]
fn expression_operators_and_ternary() {
    // 需求 6.7/6.8/6.9/6.10。
    let arithmetic = parse_ok("100 * MB + 42");
    match &arithmetic.kind {
        ExpressionKind::Binary {
            operator,
            left,
            right,
        } => {
            assert_eq!(operator, "+");
            assert!(matches!(
                left.kind,
                ExpressionKind::Binary { operator: ref op, .. } if op == "*"
            ));
            assert!(matches!(
                right.kind,
                ExpressionKind::Literal(ValueNode::Number(_))
            ));
        }
        other => panic!("unexpected {other:?}"),
    }
    let comparison = parse_ok("file.size > 1");
    assert!(matches!(
        comparison.kind,
        ExpressionKind::Binary { operator: ref op, .. } if op == ">"
    ));
    let logic = parse_ok("a == 1 && b != 2");
    assert!(matches!(
        logic.kind,
        ExpressionKind::Binary { operator: ref op, .. } if op == "&&"
    ));
    let unary = parse_ok("!ready");
    assert!(matches!(
        unary.kind,
        ExpressionKind::Unary { ref operator, .. } if operator == "!"
    ));
    let ternary = parse_ok("ok ? \"yes\" : \"no\"");
    assert!(matches!(ternary.kind, ExpressionKind::Ternary { .. }));
}

#[test]
fn expression_functions_and_pipeline() {
    // 需求 6.11/6.13：白名单函数调用与 filter/map/reduce 管道。
    assert!(matches!(
        parse_ok("size(files)").kind,
        ExpressionKind::Call { function: ref f, .. } if f == "size"
    ));
    assert!(matches!(
        parse_ok("files | filter(size > 0) | map(name)").kind,
        ExpressionKind::Pipe { .. }
    ));
}

#[test]
fn expression_constants() {
    // 需求 6.22：KB/MB/GB 常量折叠为数字字面量。
    match parse_ok("MB").kind {
        ExpressionKind::Literal(ValueNode::Number(n)) => {
            assert_eq!(n.as_f64(), Some(1024.0 * 1024.0))
        }
        other => panic!("unexpected {other:?}"),
    }
    assert_eq!(
        cloudflow_runtime::expression::builtins::constant("GB"),
        Some(1024.0 * 1024.0 * 1024.0)
    );
    assert!(cloudflow_runtime::expression::builtins::is_builtin_function("size"));
    assert!(!cloudflow_runtime::expression::builtins::is_builtin_function("eval"));
}

#[test]
fn value_context_and_interpolation() {
    // 需求 6.14：值上下文与字符串插值。
    let text = "\"file_${input.file_id}.txt\"";
    let source = format!("v = {text}");
    let base = source.find(text).expect("text present");
    match parse_value_string(text, &source, "test.flow", base).expect("value") {
        ValueNode::Template(segments) => {
            assert_eq!(segments.len(), 3);
            assert!(matches!(&segments[0], ValueNode::String(s) if s == "file_"));
            assert!(matches!(&segments[1], ValueNode::VariableRef(r) if r == "input.file_id"));
            assert!(matches!(&segments[2], ValueNode::String(s) if s == ".txt"));
        }
        other => panic!("unexpected {other:?}"),
    }
    // 数组 / 枚举值上下文。
    let array_text = "[1, 2]";
    let array_source = "v = [1, 2]";
    let array_base = array_source.find(array_text).unwrap();
    match parse_value_string(array_text, array_source, "t.flow", array_base).expect("array") {
        ValueNode::Array(items) => assert_eq!(items.len(), 2),
        other => panic!("unexpected {other:?}"),
    }
    assert_eq!(
        parse_value_string("manual", "trigger: manual", "t.flow", 9).expect("enum"),
        ValueNode::Enum("manual".into())
    );
}

#[test]
fn expression_error_reports_position() {
    let text = "vars.a +"; // 非法尾部运算符
    let source = format!("  if {{ {text} }}");
    let base = source.find(text).unwrap();
    let err = parse_expression_string(text, &source, "bad.flow", base).expect_err("must fail");
    assert_eq!(err.code, "CFY-EXPR-102");
    assert!(err.location.line >= 1);
    assert_eq!(err.location.file, "bad.flow");
}

#[test]
fn dsl_frontend_delegates_to_subsystem() {
    // 需求 6.21/6.31：DSL 编译器前端把表达式作为字符串交给子系统，产出同一语法样式 AST。
    let source = r#"
        workflow "delegate" {
            trigger { manual {} }
            variables { threshold = 10 }
            step pick {
                action file.list {}
                condition { vars.threshold > 5 }
            }
        }
    "#;
    let workflow = cloudflow_runtime::parser::parse_source(source, "delegate.flow").expect("parse");
    assert_eq!(workflow.name, "delegate");
    let step = workflow
        .steps
        .iter()
        .find(|s| s.id == "pick")
        .expect("step");
    let condition = step.condition.as_ref().expect("condition");
    assert!(matches!(
        condition.kind,
        ExpressionKind::Binary { ref operator, .. } if operator == ">"
    ));
}

// ────────────────────────────────────────────────────────────────────────────
// DSL grammar.pest 与表达式子系统 grammar.pest 同步验证（需求 6.2/6.31）
// 扩展语法：属性访问 file.size、索引访问 list[0]、input./env. 引用、KB/MB/GB 常量。
// ────────────────────────────────────────────────────────────────────────────

fn parse_dsl(source: &str) -> cloudflow_runtime::ast::WorkflowNode {
    cloudflow_runtime::parser::parse_source(source, "sync.flow").expect("DSL 应能解析")
}

fn step_condition(source: &str) -> cloudflow_runtime::ast::ExpressionNode {
    let wf = parse_dsl(source);
    wf.steps
        .iter()
        .find(|s| s.id == "pick")
        .expect("step pick")
        .condition
        .clone()
        .expect("condition")
}

#[test]
fn dsl_sync_attribute_access_and_constants() {
    // extension A：DSL grammar 支持 `file.size` 属性访问与 `KB/MB/GB` 常量折叠。
    let condition = step_condition(
        r#"
        workflow "sync_a" {
            trigger { manual {} }
            step pick { action file.list {} condition { file.size > 100 * MB } }
        }
        "#,
    );
    match &condition.kind {
        ExpressionKind::Binary {
            operator,
            left,
            right,
        } => {
            assert_eq!(operator, ">");
            assert_eq!(left.kind, reference("file.size"));
            // 常量在子系统解析期折为数字：100 * MB
            match &right.kind {
                ExpressionKind::Binary { operator: op, .. } => assert_eq!(op, "*"),
                other => panic!("unexpected {other:?}"),
            }
        }
        other => panic!("unexpected {other:?}"),
    }
}

#[test]
fn dsl_sync_index_access() {
    // extension B：DSL grammar 支持 `list[0]` / `vars.a[0]` 索引访问。
    let condition = step_condition(
        r#"
        workflow "sync_b" {
            trigger { manual {} }
            step pick { action file.list {} condition { items[0] > 0 } }
        }
        "#,
    );
    assert!(matches!(
        condition.kind,
        ExpressionKind::Binary { operator: ref op, .. } if op == ">"
    ));
    // 至少断言 AST 中出现带索引的引用字符串（展开表达式）。
    let mut texts = Vec::new();
    fn collect_reference(node: &cloudflow_runtime::ast::ExpressionNode, out: &mut Vec<String>) {
        match &node.kind {
            ExpressionKind::Reference(r) => out.push(r.clone()),
            ExpressionKind::Binary { left, right, .. } => {
                collect_reference(left, out);
                collect_reference(right, out);
            }
            _ => {}
        }
    }
    collect_reference(&condition, &mut texts);
    assert!(
        texts.iter().any(|r| r.contains('[',)),
        "索引访问应被切为带方括号的引用：{texts:?}"
    );
}

#[test]
fn dsl_sync_input_env_references() {
    // extension C：DSL grammar 支持 `input.x` / `env.K` 引用命名空间。
    let condition = step_condition(
        r#"
        workflow "sync_c" {
            trigger { manual {} }
            step pick { action file.list {} condition { input.timeout > 0 && env.ENABLED } }
        }
        "#,
    );
    assert!(matches!(
        condition.kind,
        ExpressionKind::Binary { operator: ref op, .. } if op == "&&"
    ));
    let wf = parse_dsl(
        r#"
        workflow "sync_c2" {
            trigger { manual {} }
            step pick { action file.list {} condition { env.ENABLED } }
        }
        "#,
    );
    let cond = wf.steps[0].condition.clone().expect("condition");
    assert_eq!(cond.kind, reference("env.ENABLED"));
}

#[test]
fn expression_null_literal() {
    // 需求 6.3：null 字面量（词法 + 表达式 + 值上下文）。
    assert_eq!(
        parse_ok("null").kind,
        ExpressionKind::Literal(ValueNode::Null)
    );
    let cmp = parse_ok("value == null");
    match &cmp.kind {
        ExpressionKind::Binary {
            operator, right, ..
        } => {
            assert_eq!(operator, "==");
            assert!(matches!(
                right.kind,
                ExpressionKind::Literal(ValueNode::Null)
            ));
        }
        other => panic!("unexpected {other:?}"),
    }
    let source = "v = null";
    let base = source.find("null").expect("null present");
    assert_eq!(
        parse_value_string("null", source, "t.flow", base).expect("null"),
        ValueNode::Null
    );
}

#[test]
fn expression_extended_builtin_functions() {
    // 需求 6.11/6.22/6.25：now/get/trim/to_upper/to_lower/range/abs/round/floor/ceil 白名单扩展。
    use cloudflow_runtime::expression::call_builtin;
    for name in [
        "now", "get", "trim", "to_upper", "to_lower", "range", "abs", "round", "floor", "ceil",
    ] {
        assert!(
            cloudflow_runtime::expression::builtins::is_builtin_function(name),
            "{name} 应在白名单"
        );
        assert!(
            matches!(
                parse_ok(&format!("{name}(1)")).kind,
                ExpressionKind::Call { .. }
            ),
            "{name}(1) 应可解析"
        );
    }
    // 函数实现唯一收敛于子系统求值器（call_builtin）。
    assert_eq!(
        call_builtin("trim", &[serde_json::json!("  hi  ")]).unwrap(),
        serde_json::json!("hi")
    );
    assert_eq!(
        call_builtin("to_upper", &[serde_json::json!("hi")]).unwrap(),
        serde_json::json!("HI")
    );
    assert_eq!(
        call_builtin("to_lower", &[serde_json::json!("AB")]).unwrap(),
        serde_json::json!("ab")
    );
    assert_eq!(
        call_builtin(
            "get",
            &[serde_json::json!(["a", "b"]), serde_json::json!(1)]
        )
        .unwrap(),
        serde_json::json!("b")
    );
    assert_eq!(
        call_builtin("range", &[serde_json::json!(3.0)]).unwrap(),
        serde_json::json!([0, 1, 2])
    );
    assert_eq!(
        call_builtin("abs", &[serde_json::json!(-5.0)]).unwrap(),
        serde_json::json!(5.0)
    );
    assert_eq!(
        call_builtin("round", &[serde_json::json!(2.5)]).unwrap(),
        serde_json::json!(3.0)
    );
    assert_eq!(
        call_builtin("floor", &[serde_json::json!(2.9)]).unwrap(),
        serde_json::json!(2.0)
    );
    assert_eq!(
        call_builtin("ceil", &[serde_json::json!(2.1)]).unwrap(),
        serde_json::json!(3.0)
    );
    let now = call_builtin("now", &[]).unwrap();
    assert!(now.as_u64().unwrap() >= 1_600_000_000);
    assert!(call_builtin("eval", &[]).is_err());
}

#[test]
fn expression_github_actions_parity_functions() {
    // 需求 6.11/6.32：GitHub Actions Expressions 对齐函数 to_json/from_json/
    // format_number/format_date_time 已在白名单，且可由子系统解析与求值。
    use cloudflow_runtime::expression::call_builtin;
    for name in ["to_json", "from_json", "format_number", "format_date_time"] {
        assert!(
            cloudflow_runtime::expression::builtins::is_builtin_function(name),
            "{name} 应在白名单"
        );
        assert!(
            matches!(
                parse_ok(&format!("{name}(x)")).kind,
                ExpressionKind::Call { .. }
            ),
            "{name}(x) 应可解析"
        );
    }
    // to_json：值 → JSON 字符串（字符串含引号）。
    assert_eq!(
        call_builtin("to_json", &[serde_json::json!(["a", "b"])]).unwrap(),
        serde_json::json!("[\"a\",\"b\"]")
    );
    assert_eq!(
        call_builtin("to_json", &[serde_json::json!("ab")]).unwrap(),
        serde_json::json!("\"ab\"")
    );
    // from_json：JSON 字符串 → 值；非字符串原样返回。
    assert_eq!(
        call_builtin("from_json", &[serde_json::json!("[1, 2]")]).unwrap(),
        serde_json::json!([1, 2])
    );
    assert_eq!(
        call_builtin("from_json", &[serde_json::json!({"k": 1})]).unwrap(),
        serde_json::json!({"k": 1})
    );
    assert!(call_builtin("from_json", &[serde_json::json!("[bad]")]).is_err());
    // format_number：小数位 + 千分位。
    assert_eq!(
        call_builtin(
            "format_number",
            &[
                serde_json::json!(1234567.891),
                serde_json::json!("#,##0.00")
            ]
        )
        .unwrap(),
        serde_json::json!("1,234,567.89")
    );
    assert_eq!(
        call_builtin(
            "format_number",
            &[serde_json::json!(3.14159), serde_json::json!("0.00")]
        )
        .unwrap(),
        serde_json::json!("3.14")
    );
    assert_eq!(
        call_builtin(
            "format_number",
            &[serde_json::json!(-1234567), serde_json::json!("#,##0")]
        )
        .unwrap(),
        serde_json::json!("-1,234,567")
    );
    // format_date_time：Unix 时间戳 + 时区 + .NET token；缺省输出 RFC3339。
    assert_eq!(
        call_builtin(
            "format_date_time",
            &[
                serde_json::json!(0),
                serde_json::json!("yyyy-MM-dd HH:mm:ss"),
                serde_json::json!("Asia/Shanghai")
            ]
        )
        .unwrap(),
        serde_json::json!("1970-01-01 08:00:00")
    );
    assert_eq!(
        call_builtin(
            "format_date_time",
            &[
                serde_json::json!("2026-08-20T00:00:00Z"),
                serde_json::json!("yyyy-MM-dd"),
                serde_json::json!("Asia/Shanghai")
            ]
        )
        .unwrap(),
        serde_json::json!("2026-08-20")
    );
    // 缺省时区（UTC）+ 缺省格式（RFC3339）。
    assert_eq!(
        call_builtin("format_date_time", &[serde_json::json!(0)]).unwrap(),
        serde_json::json!("1970-01-01T00:00:00+00:00")
    );
    // 非法时区报错。
    assert!(call_builtin(
        "format_date_time",
        &[
            serde_json::json!(0),
            serde_json::json!(""),
            serde_json::json!("??")
        ]
    )
    .is_err());
}

#[test]
fn expression_api_version_is_stable() {
    // 需求 6.29：表达式子系统 API 版本独立于前端语言版本。
    assert_eq!(
        cloudflow_runtime::expression::API_VERSION,
        "expr.cloudflow.io/v1"
    );
}

#[test]
fn dsl_sync_null_literal() {
    // [EXPR-NULL] DSL grammar 定位器也能切出 `null` 字面量（双文件同步回归）。
    let wf = parse_dsl(
        r#"
        workflow "sync_null" {
            trigger { manual {} }
            step pick { action file.list {} condition { value != null } }
        }
        "#,
    );
    let cond = wf.steps[0].condition.clone().expect("condition");
    match &cond.kind {
        ExpressionKind::Binary {
            operator: op,
            right,
            ..
        } => {
            assert_eq!(op, "!=");
            assert!(matches!(
                right.kind,
                ExpressionKind::Literal(ValueNode::Null)
            ));
        }
        other => panic!("unexpected {other:?}"),
    }
}

#[test]
fn expression_interpolation_github_actions_double_brace() {
    // 需求 6.14/6.32（对标 GitHub Actions `${{ }}`）：CloudFlow YAML 只定义 `${{ }}`
    // 一种分隔符；表达式子系统唯一插值实现，段可折叠为 $ref / 字面量 / 表达式。
    use cloudflow_runtime::expression::parse_interpolated_value;

    // 纯文本：无插值 → None（调用方保持纯字符串）。
    assert!(parse_interpolated_value("plain text", "src", "t.flow", 0).is_none());

    // `${ }` 单括号不再是 YAML 表达式语法：不经插值（保持纯字符串）。
    assert!(parse_interpolated_value(
        "file_${input.file_id}.txt",
        "v = file_${input.file_id}.txt",
        "t.flow",
        4,
    )
    .is_none());

    // GitHub-Actions 双大括号 `${{ ... }}` 整串与模板。
    let whole =
        parse_interpolated_value("${{ vars.who }}", "v = ${{ vars.who }}", "t.flow", 4).unwrap();
    assert!(matches!(
        whole,
        ValueNode::Template(segments)
            if matches!(segments.as_slice(), [ValueNode::VariableRef(path)] if path == "vars.who")
    ));

    let greeting = parse_interpolated_value(
        "hello ${{ vars.who }}, today is ${{ steps.a.output }}",
        "v = hello ${{ vars.who }}",
        "t.flow",
        4,
    )
    .unwrap();
    match greeting {
        ValueNode::Template(segments) => {
            let refs = segments
                .iter()
                .filter(|segment| matches!(segment, ValueNode::VariableRef(_)))
                .count();
            assert_eq!(refs, 2, "应解析出两个 $ref 段");
        }
        other => panic!("unexpected {other:?}"),
    }
}
