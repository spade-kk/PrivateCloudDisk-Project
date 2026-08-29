//! CloudFlow DSL Compiler CLI。

use clap::{Parser, Subcommand, ValueEnum};
use cloudflow_runtime::{
    ast_printer::{self, AstPrintOptions},
    compile_source_named, diagnostic::Diagnostic, parse_ast, semantic::InMemoryCapabilityCatalog,
};
use miette::MietteHandlerOpts;
use serde::Serialize;
use std::{
    fs,
    io::{self, Read},
    path::PathBuf,
    process::ExitCode,
};

#[derive(Debug, Parser)]
#[command(name = "cloudflowc", version, about = "CloudFlow DSL Compiler")]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Debug, Clone, Copy, ValueEnum)]
enum OutputFormat {
    Human,
    Json,
}

#[derive(Debug, Subcommand)]
enum Command {
    /// 将 .flow 源码编译为 workflow.cloudflow.io/v1 IR。
    Compile {
        #[arg(value_name = "FILE", conflicts_with = "source")]
        input: Option<PathBuf>,
        /// 直接传入 DSL 源码；与 FILE 互斥。
        #[arg(short = 'i', long, conflicts_with = "input")]
        source: Option<String>,
        #[arg(short, long)]
        output: Option<PathBuf>,
        #[arg(long, default_value = "v1")]
        target: String,
        #[arg(long)]
        check_only: bool,
        #[arg(long)]
        explain: bool,
        #[arg(long, value_enum, default_value_t = OutputFormat::Human)]
        output_format: OutputFormat,
        #[arg(long)]
        no_color: bool,
        /// 输出紧凑 IR JSON；默认使用便于审阅的格式化 JSON。
        #[arg(long)]
        compact: bool,
        /// 输出编译生成的 AST 语法树（可视化文本或 JSON），用于调试和语法审计。
        /// 与 `--check-only` 同时出现时 `--check-only` 优先；替换 `--output-format json`
        /// 会输出 AST 的 JSON 序列化，否则输出层级树形文本。
        #[arg(long, short = 'A')]
        emit_ast: bool,
    },
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct JsonDiagnosticResponse<'a> {
    valid: bool,
    target_ir_version: &'static str,
    diagnostics: &'a [Diagnostic],
}

fn main() -> ExitCode {
    let cli = Cli::parse();
    match cli.command {
        Command::Compile {
            input,
            source,
            output,
            target,
            check_only,
            explain,
            output_format,
            no_color,
            compact,
            emit_ast,
        } => {
            configure_miette(no_color);
            compile(
                input,
                source,
                output,
                target,
                check_only,
                explain,
                output_format,
                compact,
                no_color,
                emit_ast,
            )
        }
    }
}

#[allow(clippy::too_many_arguments)]
fn compile(
    input: Option<PathBuf>,
    source: Option<String>,
    output: Option<PathBuf>,
    target: String,
    check_only: bool,
    explain: bool,
    output_format: OutputFormat,
    compact: bool,
    no_color: bool,
    emit_ast: bool,
) -> ExitCode {
    // 先读源码（emit_ast 与正常编译都需要）。
    let (text, filename) = match read_source(source, input) {
        Ok(value) => value,
        Err(message) => {
            let diagnostic = Diagnostic::new(
                "CF1101",
                "IO_ERROR",
                message,
                "",
                "<cli>",
                0,
                1,
                vec![],
                None,
            );
            print_errors(std::slice::from_ref(&diagnostic), output_format, explain);
            return ExitCode::from(2);
        }
    };
    // [AST-VIS-001] `--emit-ast` 只做解析，输出 AST，不生成 IR。
    // `--check-only` 优先（需求 2.22）：同时指定时走完整校验，不输出 AST。
    // `--target` 在 emit_ast 下无意义（不生成 IR），按“忽略”处理（需求 2.6）。
    if emit_ast && !check_only {
        return emit_ast_output(&text, &filename, output, output_format, no_color, explain);
    }
    let _ = target;
    if !matches!(target.as_str(), "v1" | "workflow.cloudflow.io/v1") {
        let diagnostic = Diagnostic::new(
            "CF1301",
            "IR_VERSION_ERROR",
            format!("不支持的 IR target：{target}"),
            "",
            "<cli>",
            0,
            1,
            vec!["v1".into(), "workflow.cloudflow.io/v1".into()],
            Some("CloudFlow V1 当前只生成 workflow.cloudflow.io/v1".into()),
        );
        print_errors(std::slice::from_ref(&diagnostic), output_format, explain);
        return ExitCode::from(2);
    }
    match compile_source_named(&text, &filename, &InMemoryCapabilityCatalog::default()) {
        Ok(ir) => {
            if check_only {
                match output_format {
                    OutputFormat::Human => {
                        println!("CloudFlow OK: {} ({})", ir.metadata.name, ir.api_version)
                    }
                    OutputFormat::Json => println!(
                        "{}",
                        serde_json::json!({"valid": true, "targetIrVersion": ir.api_version, "diagnostics": []})
                    ),
                }
                return ExitCode::SUCCESS;
            }
            let json = if compact {
                serde_json::to_string(&ir)
            } else {
                serde_json::to_string_pretty(&ir)
            }
            .expect("IR must serialize");
            if let Some(path) = output {
                if let Err(error) = fs::write(&path, json) {
                    eprintln!("CF1101: 无法写出 {}：{error}", path.display());
                    return ExitCode::from(2);
                }
            } else {
                println!("{json}");
            }
            ExitCode::SUCCESS
        }
        Err(error) => {
            print_errors(&error.diagnostics, output_format, explain);
            ExitCode::from(1)
        }
    }
}


/// [AST-VIS-003] 输出 AST：解析入口文件（不展开 include、不执行语义分析、不生成 IR）。
/// - `--output-format json` → AST 的 JSON 序列化（需求 2.4/3.17）；
/// - 否则输出层级树形文本；写文件默认无色（需求 3.15），stdout 尊重 `--no-color`。
/// - 解析失败（语法错误/超限）返回非零退出码（需求 6.20）。
fn emit_ast_output(
    text: &str,
    filename: &str,
    output: Option<PathBuf>,
    output_format: OutputFormat,
    no_color: bool,
    explain: bool,
) -> ExitCode {
    match parse_ast(text, filename) {
        Ok(workflow) => {
            let body = match output_format {
                OutputFormat::Json => ast_printer::render_json(&workflow),
                OutputFormat::Human => {
                    // [AST-VIS-004] 写文件时默认无色；stdout 时 `--no-color` 关闭 ANSI。
                    let color = !no_color && output.is_none();
                    let tree = ast_printer::render(
                        &workflow,
                        &AstPrintOptions { color },
                    );
                    let mut text = String::new();
                    text.push_str(&tree);
                    if explain {
                        // [AST-VIS-005] `--explain` 在树后附一句说明，不改变树结构。
                        text.push_str("\n");
                        text.push_str("// AST 仅反映语法解析结果，不代表语义/IR 合法（需求 5.19）。\n");
                    }
                    text
                }
            };
            if let Some(path) = output {
                if let Err(error) = fs::write(&path, body) {
                    eprintln!("CF1101: 无法写出 AST：{} {}", path.display(), error);
                    return ExitCode::from(2);
                }
            } else {
                println!("{body}");
            }
            ExitCode::SUCCESS
        }
        Err(error) => {
            print_errors(&error.diagnostics, output_format, explain);
            ExitCode::from(1)
        }
    }
}

fn read_source(source: Option<String>, input: Option<PathBuf>) -> Result<(String, String), String> {
    match (source, input) {
        (Some(value), None) => Ok((value, "<inline>".into())),
        (None, Some(path)) => fs::read_to_string(&path)
            .map(|value| (value, path.display().to_string()))
            .map_err(|error| format!("无法读取 {}：{error}", path.display())),
        (None, None) => {
            let mut value = String::new();
            io::stdin()
                .read_to_string(&mut value)
                .map_err(|error| format!("无法读取 stdin：{error}"))?;
            Ok((value, "<stdin>".into()))
        }
        (Some(_), Some(_)) => Err("FILE 与 -i 不能同时使用".into()),
    }
}

fn configure_miette(no_color: bool) {
    let _ = miette::set_hook(Box::new(move |_| {
        Box::new(
            MietteHandlerOpts::new()
                .force_graphical(true)
                .color(!no_color)
                .context_lines(2)
                .build(),
        )
    }));
}

fn print_errors(diagnostics: &[Diagnostic], format: OutputFormat, explain: bool) {
    match format {
        OutputFormat::Json => {
            let response = JsonDiagnosticResponse {
                valid: false,
                target_ir_version: "workflow.cloudflow.io/v1",
                diagnostics,
            };
            eprintln!(
                "{}",
                serde_json::to_string_pretty(&response).expect("diagnostics serialize")
            );
        }
        OutputFormat::Human => {
            for diagnostic in diagnostics {
                eprintln!("{:?}", diagnostic.miette_report());
                if explain {
                    if !diagnostic.suggestions.is_empty() {
                        eprintln!("建议：{}", diagnostic.suggestions.join("、"));
                    }
                    eprintln!("文档：{}", diagnostic.documentation_url);
                }
            }
        }
    }
}
