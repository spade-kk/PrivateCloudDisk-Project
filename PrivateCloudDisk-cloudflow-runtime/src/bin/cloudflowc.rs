//! CloudFlow DSL Compiler CLI。

use clap::{Parser, Subcommand, ValueEnum};
use cloudflow_runtime::{
    ast_printer::{self, AstPrintOptions},
    compile_source_named_for_language, dev_execute_sync,
    diagnostic::Diagnostic,
    parse_ast_for_language,
    semantic::InMemoryCapabilityCatalog,
    DevConfig, DevEntryError, DevLogLevel, DevTaskStatus, DevWorkflowStatus, Language,
    MockActionExecutor,
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
    /// 层级树形/人类可读文本（`text` 为别名，需求 13.8）。
    #[value(alias = "text")]
    Human,
    Json,
}

#[derive(Debug, Clone, Copy, ValueEnum)]
enum FrontendLang {
    Dsl,
    Yaml,
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
        #[arg(long, short = 'A', alias = "emit-domain-ast")]
        emit_ast: bool,
        /// 显式指定前端语言：dsl | yaml（缺省按文件扩展名识别；-i/stdin 需配合使用）。
        #[arg(long = "lang")]
        language: Option<FrontendLang>,
    },
    /// 开发调试执行入口（需求 9.1-9.10）：直接执行 IR JSON，纯内存、不写数据库。
    DevExecute {
        /// IR 文件（workflow.cloudflow.io/v1 JSON）；缺省读 stdin。
        #[arg(value_name = "IR_FILE")]
        input: Option<PathBuf>,
        /// 直接传入 IR JSON 字符串；与 IR_FILE 互斥。
        #[arg(short = 'i', long, conflicts_with = "input")]
        source: Option<String>,
        /// 初始变量覆盖：key=value（value 先按 JSON 解析，失败按字符串）。可重复（需求 9.2）。
        #[arg(long = "var", value_name = "KEY=VALUE")]
        vars: Vec<String>,
        /// 启用 mock 动作执行（本入口动作执行器始终为内存 Mock；保留参数用于语义兼容，需求 9.3）。
        #[arg(long)]
        mock: bool,
        /// 跳过 IR 契约校验（需求 9.4/4.11，用于测试校验器本身）。
        #[arg(long)]
        no_validate: bool,
        /// 执行超时，如 30s / 5m（需求 9.6）。
        #[arg(long, value_name = "DURATION")]
        timeout: Option<String>,
        /// 输出详细执行日志（需求 9.7）。
        #[arg(long)]
        verbose: bool,
        /// 在指定节点执行前暂停（需求 9.8）。
        #[arg(long, value_name = "NODE_ID")]
        breakpoint: Option<String>,
        /// 单步执行：每个顶层节点完成后暂停。
        #[arg(long)]
        single_step: bool,
        /// 跳过的节点 ID（逗号分隔）。
        #[arg(long, value_name = "NODE_IDS")]
        skip_nodes: Option<String>,
        /// 日志级别：debug | info | warn | error。
        #[arg(long, value_name = "LEVEL")]
        level: Option<String>,
        /// 导出 Markdown 执行报告（需求 10.15）。
        #[arg(long, value_name = "FILE")]
        report: Option<PathBuf>,
        /// 导出 JSON 执行报告（需求 10.14）。
        #[arg(long, value_name = "FILE")]
        report_json: Option<PathBuf>,
        #[arg(long, value_enum, default_value_t = OutputFormat::Human)]
        output_format: OutputFormat,
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
            language,
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
                language,
            )
        }
        Command::DevExecute {
            input,
            source,
            vars,
            mock,
            no_validate,
            timeout,
            verbose,
            breakpoint,
            single_step,
            skip_nodes,
            level,
            report,
            report_json,
            output_format,
        } => dev_execute(DevExecuteArgs {
            input,
            source,
            vars,
            mock,
            no_validate,
            timeout,
            verbose,
            breakpoint,
            single_step,
            skip_nodes,
            level,
            report,
            report_json,
            output_format,
        }),
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
    language: Option<FrontendLang>,
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
    let language = resolve_language(language, &filename);
    if emit_ast && !check_only {
        return emit_ast_output(
            &text,
            &filename,
            output,
            output_format,
            no_color,
            explain,
            language,
        );
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
    match compile_source_named_for_language(
        &text,
        &filename,
        language,
        &InMemoryCapabilityCatalog::default(),
    ) {
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
    language: Language,
) -> ExitCode {
    match parse_ast_for_language(text, filename, language) {
        Ok(workflow) => {
            let body = match output_format {
                OutputFormat::Json => ast_printer::render_json(&workflow),
                OutputFormat::Human => {
                    // [AST-VIS-004] 写文件时默认无色；stdout 时 `--no-color` 关闭 ANSI。
                    let color = !no_color && output.is_none();
                    let tree = ast_printer::render(&workflow, &AstPrintOptions { color });
                    let mut text = String::new();
                    text.push_str(&tree);
                    if explain {
                        // [AST-VIS-005] `--explain` 在树后附一句说明，不改变树结构。
                        text.push('\n');
                        text.push_str(
                            "// AST 仅反映语法解析结果，不代表语义/IR 合法（需求 5.19）。\n",
                        );
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

/// 解析前端语言：`--lang` 显式时优先；否则按文件扩展名识别（.yaml/.yml → YAML，.flow → DSL）。
fn resolve_language(lang: Option<FrontendLang>, filename: &str) -> Language {
    match lang {
        Some(FrontendLang::Dsl) => Language::Dsl,
        Some(FrontendLang::Yaml) => Language::Yaml,
        None => cloudflow_runtime::language_of(filename),
    }
}

/// `dev-execute` 子命令参数（14 个 CLI 标志聚合为结构体，避免过长参数列表）。
struct DevExecuteArgs {
    input: Option<PathBuf>,
    source: Option<String>,
    vars: Vec<String>,
    mock: bool,
    no_validate: bool,
    timeout: Option<String>,
    verbose: bool,
    breakpoint: Option<String>,
    single_step: bool,
    skip_nodes: Option<String>,
    level: Option<String>,
    report: Option<PathBuf>,
    report_json: Option<PathBuf>,
    output_format: OutputFormat,
}

/// `dev-execute` 子命令（需求 9.1-9.10）：读取 IR → 校验 → 纯内存执行 → stdout 结果。
fn dev_execute(args: DevExecuteArgs) -> ExitCode {
    let DevExecuteArgs {
        input,
        source,
        vars,
        mock,
        no_validate,
        timeout,
        verbose,
        breakpoint,
        single_step,
        skip_nodes,
        level,
        report,
        report_json,
        output_format,
    } = args;
    let ir_text = match read_source(source, input) {
        Ok((value, _)) => value,
        Err(message) => {
            eprintln!("CFD-8101: {message}");
            return ExitCode::from(2);
        }
    };
    let mut supplied = serde_json::Map::new();
    for item in &vars {
        match item.split_once('=') {
            Some((key, value)) => {
                supplied.insert(
                    key.to_owned(),
                    serde_json::from_str(value)
                        .unwrap_or(serde_json::Value::String(value.to_owned())),
                );
            }
            None => {
                eprintln!("CFD-8101: --var 需要 key=value 形式：{item}");
                return ExitCode::from(2);
            }
        }
    }
    let mut config = DevConfig {
        skip_validation: no_validate,
        breakpoint,
        single_step,
        mock,
        ..DevConfig::default()
    };
    if let Some(items) = skip_nodes {
        config.skip_nodes = items
            .split(',')
            .map(|value| value.trim().to_owned())
            .filter(|value| !value.is_empty())
            .collect();
    }
    config.log_level = if verbose {
        DevLogLevel::Debug
    } else {
        match level.as_deref() {
            Some("debug") => DevLogLevel::Debug,
            Some("warn") => DevLogLevel::Warn,
            Some("error") => DevLogLevel::Error,
            _ => DevLogLevel::Info,
        }
    };
    if let Some(duration) = &timeout {
        match parse_duration_arg(duration) {
            Some(millis) => config.overall_timeout_ms = Some(millis),
            None => {
                eprintln!("CFD-8101: 无法解析 --timeout {duration}（支持 30s/5m/100ms 等）");
                return ExitCode::from(2);
            }
        }
    }
    let executor = std::sync::Arc::new(MockActionExecutor::new());
    let result = match serde_json::from_str::<cloudflow_runtime::ir::WorkflowIrV1>(&ir_text) {
        Ok(ir) => dev_execute_sync(&ir, serde_json::Value::Object(supplied), &config, executor),
        Err(error) => {
            match output_format {
                OutputFormat::Json => {
                    eprintln!(
                        "{}",
                        serde_json::to_string_pretty(&serde_json::json!({
                            "valid": false,
                            "error": format!("IR JSON 解析失败：{error}")
                        }))
                        .expect("json")
                    );
                }
                OutputFormat::Human => eprintln!("CFD-8101: IR JSON 解析失败：{error}"),
            }
            return ExitCode::from(2);
        }
    };
    match result {
        Ok(execution) => run_dev_execute_output(
            &ir_text,
            &execution,
            &config,
            report,
            report_json,
            output_format,
            verbose,
        ),
        Err(error) => {
            match error {
                DevEntryError::Validation(issues) => {
                    match output_format {
                        OutputFormat::Json => {
                            eprintln!(
                                "{}",
                                serde_json::to_string_pretty(&serde_json::json!({
                                    "valid": false,
                                    "status": "validationFailed",
                                    "issues": issues,
                                }))
                                .expect("json")
                            );
                        }
                        OutputFormat::Human => {
                            eprintln!("IR 契约校验未通过（{} 项问题）：", issues.len());
                            for issue in &issues {
                                let node = issue
                                    .node_id
                                    .as_ref()
                                    .map(|id| format!(" @ {id}"))
                                    .unwrap_or_default();
                                eprintln!(
                                    "  {} {}{}：{}",
                                    issue.code, issue.path, node, issue.message
                                );
                            }
                        }
                    }
                    // 输入 IR 不合法属于调用方参数错误（需求 9.10：失败返回非 0）。
                    ExitCode::from(2)
                }
                DevEntryError::InvalidJson(message) => {
                    match output_format {
                        OutputFormat::Json => {
                            eprintln!(
                                "{}",
                                serde_json::to_string_pretty(&serde_json::json!({
                                    "valid": false,
                                    "error": format!("IR JSON 解析失败：{message}"),
                                }))
                                .expect("json")
                            );
                        }
                        OutputFormat::Human => eprintln!("CFD-8101: IR JSON 解析失败：{message}"),
                    }
                    ExitCode::from(2)
                }
                DevEntryError::Internal(message) => {
                    match output_format {
                        OutputFormat::Json => {
                            eprintln!(
                                "{}",
                                serde_json::to_string_pretty(&serde_json::json!({
                                    "valid": false,
                                    "error": format!("开发执行引擎内部错误：{message}"),
                                }))
                                .expect("json")
                            );
                        }
                        OutputFormat::Human => {
                            eprintln!("CFD-8102: 开发执行引擎内部错误：{message}")
                        }
                    }
                    ExitCode::from(1)
                }
            }
        }
    }
}

fn parse_duration_arg(value: &str) -> Option<u64> {
    let split = value.find(|character: char| !character.is_ascii_digit())?;
    let amount = value[..split].parse::<u64>().ok()?;
    match &value[split..] {
        "ms" => Some(amount),
        "s" => Some(amount.saturating_mul(1_000)),
        "m" => Some(amount.saturating_mul(60_000)),
        "h" => Some(amount.saturating_mul(3_600_000)),
        "d" => Some(amount.saturating_mul(86_400_000)),
        _ => None,
    }
}

fn run_dev_execute_output(
    ir_text: &str,
    execution: &cloudflow_runtime::DevExecutionResult,
    config: &DevConfig,
    report: Option<PathBuf>,
    report_json: Option<PathBuf>,
    output_format: OutputFormat,
    verbose: bool,
) -> ExitCode {
    let ir = serde_json::from_str::<cloudflow_runtime::ir::WorkflowIrV1>(ir_text).ok();
    // 报告导出（需求 10.14/10.15）。
    if let Some(path) = &report_json {
        if let Err(error) = fs::write(
            path,
            serde_json::to_string_pretty(execution).unwrap_or_default(),
        ) {
            eprintln!("CFD-8101: 无法写出 JSON 报告 {}：{error}", path.display());
            return ExitCode::from(2);
        }
    }
    if let (Some(path), Some(ir)) = (&report, ir.as_ref()) {
        if let Err(error) = fs::write(path, execution.render_markdown(ir)) {
            eprintln!(
                "CFD-8101: 无法写出 Markdown 报告 {}：{error}",
                path.display()
            );
            return ExitCode::from(2);
        }
    }
    match output_format {
        OutputFormat::Json => {
            println!(
                "{}",
                serde_json::to_string_pretty(execution).expect("result serialize")
            );
        }
        OutputFormat::Human => {
            let summary = execution.summary();
            println!(
                "状态：{}    耗时：{}ms    节点：{}（成功 {} / 失败 {} / 跳过 {}）",
                execution.status,
                summary["durationMs"],
                summary["nodes"],
                summary["success"],
                summary["failed"],
                summary["skipped"],
            );
            if let Some(ir) = &ir {
                for node in &ir.spec.graph.nodes {
                    if let Some(result) = execution.node_results.get(&node.id) {
                        let marker = match result.status {
                            DevTaskStatus::Success => "✓",
                            DevTaskStatus::Failed => "✗",
                            DevTaskStatus::Skipped => "↷",
                            DevTaskStatus::Waiting => "⏸",
                            _ => "·",
                        };
                        println!(
                            "  {marker} {} [{}] {}ms",
                            result.node_id, result.status, result.duration_ms
                        );
                    }
                }
            }
            if !execution.errors.is_empty() {
                println!("错误：");
                for error in &execution.errors {
                    println!(
                        "  - {} {}{}",
                        error.code,
                        error.message,
                        error
                            .node_id
                            .as_ref()
                            .map(|id| format!("（{id}）"))
                            .unwrap_or_default()
                    );
                }
            }
            if verbose || config.log_level == DevLogLevel::Debug {
                println!("日志：");
                for entry in &execution.logs {
                    println!(
                        "  [{:?}]{} {}",
                        entry.level,
                        entry
                            .node_id
                            .as_ref()
                            .map(|id| format!(" {id}: "))
                            .unwrap_or_default(),
                        entry.message
                    );
                }
            }
        }
    }
    // 退出码（需求 9.10）：成功/等待/断点=0；失败/超时=1。
    match execution.status {
        DevWorkflowStatus::Success | DevWorkflowStatus::Waiting | DevWorkflowStatus::Breakpoint => {
            ExitCode::SUCCESS
        }
        DevWorkflowStatus::Failed | DevWorkflowStatus::Timeout => ExitCode::from(1),
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
