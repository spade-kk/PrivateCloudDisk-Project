//! CloudFlow `--emit-ast` 可视化输出测试（需求 2.14/2.15/3.24/3.25/6.x）。
//!
//! 覆盖：
//! - CLI 参数解析与优先级（--check-only 优先、--output-format json、-o 文件、--no-color、--explain）
//! - 树形文本格式（根节点、分支字符、字段、颜色开关）
//! - JSON 序列化结果合法
//! - 解析失败退出码与 include/语义跳过行为
//! - AST printer 单元断言（各节点类型、表达式/值渲染）
use cloudflow_runtime::{
    ast_printer::{self, AstPrintOptions},
    parse_ast,
};
use std::process::Command;

const BIN: &str = env!("CARGO_BIN_EXE_cloudflowc");

fn run(args: &[&str]) -> (std::process::ExitStatus, String, String) {
    let out = Command::new(BIN)
        .args(args)
        .output()
        .expect("run cloudflowc");
    (
        out.status,
        String::from_utf8_lossy(&out.stdout).into_owned(),
        String::from_utf8_lossy(&out.stderr).into_owned(),
    )
}

const SIMPLE: &str =
    "workflow \"cli\" { trigger { manual {} } steps { step run { action file.list {} } } }";

// ---------------------------------------------------------------------------
// CLI 行为
// ---------------------------------------------------------------------------

#[test]
fn emit_ast_text_output_contains_structure() {
    let (status, stdout, _) = run(&["compile", "-i", SIMPLE, "--emit-ast", "--no-color"]);
    assert!(status.success());
    assert!(stdout.contains("Workflow"));
    assert!(stdout.contains("name: cli"));
    assert!(stdout.contains("Step"));
    assert!(stdout.contains("id: run"));
    assert!(stdout.contains("Action"));
    assert!(stdout.contains("provider: builtin"));
}

#[test]
fn emit_ast_no_color_has_no_ansi() {
    let (_, stdout, _) = run(&["compile", "-i", SIMPLE, "--emit-ast", "--no-color"]);
    assert!(!stdout.contains("\x1b["));
}

#[test]
fn emit_ast_color_has_ansi() {
    let (_, stdout, _) = run(&["compile", "-i", SIMPLE, "--emit-ast"]);
    // stdout 非 TTY 时我们仍下发颜色（由调用方 --no-color 控制），因此默认含 ANSI。
    assert!(stdout.contains("\x1b["));
}

#[test]
fn emit_ast_json_valid() {
    let (status, stdout, _) = run(&[
        "compile",
        "-i",
        SIMPLE,
        "--emit-ast",
        "--output-format",
        "json",
        "--no-color",
    ]);
    assert!(status.success());
    let value: serde_json::Value = serde_json::from_str(&stdout).expect("AST JSON must parse");
    assert_eq!(value["ast"]["name"], "cli");
    assert_eq!(value["ast"]["steps"][0]["type"], "Step");
    assert_eq!(value["ast"]["steps"][0]["id"], "run");
}

#[test]
fn emit_ast_write_file_no_color() {
    let dir = std::env::temp_dir();
    let path = dir.join("cloudflow-ast-test.txt");
    let (status, stdout, _) = run(&[
        "compile",
        "-i",
        SIMPLE,
        "--emit-ast",
        "-o",
        path.to_str().unwrap(),
    ]);
    assert!(status.success());
    assert!(stdout.is_empty(), "写文件时 stdout 应为空");
    let content = std::fs::read_to_string(&path).expect("AST file written");
    assert!(content.contains("Workflow"));
    assert!(!content.contains("\x1b["), "写文件默认不含颜色");
    let _ = std::fs::remove_file(&path);
}

#[test]
fn check_only_takes_priority_over_emit_ast() {
    let (status, stdout, _) = run(&[
        "compile",
        "-i",
        SIMPLE,
        "--emit-ast",
        "--check-only",
        "--no-color",
    ]);
    assert!(status.success());
    assert!(stdout.contains("CloudFlow OK"));
    assert!(
        !stdout.contains("└──") && !stdout.contains("Workflow\n"),
        "--check-only 优先，不应输出 AST 树"
    );
}

#[test]
fn emit_ast_explain_appends_note() {
    let (_, stdout, _) = run(&[
        "compile",
        "-i",
        SIMPLE,
        "--emit-ast",
        "--no-color",
        "--explain",
    ]);
    assert!(stdout.contains("仅反映语法解析结果"));
}

#[test]
fn emit_ast_parse_failure_exits_nonzero() {
    let (status, _, stderr) = run(&[
        "compile",
        "-i",
        "workflow \"bad\" {",
        "--emit-ast",
        "--no-color",
    ]);
    assert!(!status.success());
    assert!(stderr.contains("CF1201") || stderr.contains("CF1202"));
}

#[test]
fn emit_ast_control_flow_nodes_rendered() {
    let src = concat!(
        "workflow \"ctl\" { trigger { manual {} } steps { ",
        "switch vars.status { case \"a\" => { step x { action file.list {} } } default => { step y { action file.list {} } } } ",
        "parallel { step p { action file.list {} } } ",
        "for i in range(0, vars.n) { step f { action file.list {} } } ",
        "} }",
    );
    let (status, stdout, _) = run(&["compile", "-i", src, "--emit-ast", "--no-color"]);
    assert!(status.success());
    for expected in ["Switch", "Case", "Parallel", "For", "Step"] {
        assert!(stdout.contains(expected), "应包含节点类型 {expected}");
    }
}

// ---------------------------------------------------------------------------
// Library-level（AST printer 单元断言）
// ---------------------------------------------------------------------------

#[test]
fn parse_ast_returns_pure_syntax_ast() {
    let wf = parse_ast(SIMPLE, "cli.flow").expect("SIMPLE must parse");
    assert_eq!(wf.name, "cli");
    assert_eq!(wf.flow.len(), 1);
}

#[test]
fn render_text_preserves_value_and_variable_nodes() {
    let src = concat!(
        "workflow \"v\" { variables { max: number = 3 } trigger { manual {} } ",
        "steps { step s { action file.list { path = vars.max } } } }",
    );
    let wf = parse_ast(src, "v.flow").expect("parse");
    let text = ast_printer::render(&wf, &AstPrintOptions { color: false });
    assert!(text.contains("Variable"));
    assert!(text.contains("type: number"));
    assert!(text.contains("vars.max"), "变量引用应内联显示: {text}");
}

#[test]
fn render_json_is_complete_and_lossless_for_steps() {
    let wf = parse_ast(SIMPLE, "cli.flow").expect("parse");
    let json = ast_printer::render_json(&wf);
    let value: serde_json::Value = serde_json::from_str(&json).expect("json");
    assert_eq!(value["ast"]["steps"][0]["action"]["provider"], "builtin");
    assert_eq!(value["ast"]["steps"][0]["action"]["service"], "file");
}

#[test]
fn render_include_field_shown() {
    // include 保留在 AST（未展开），应显示 Includes 节点。
    let src = "workflow \"w\" { include \"common.flow\" trigger { manual {} } steps { } }";
    let wf = parse_ast(src, "w.flow").expect("parse include decl");
    let text = ast_printer::render(&wf, &AstPrintOptions { color: false });
    assert!(text.contains("Includes"));
    assert!(text.contains("common.flow"));
}
