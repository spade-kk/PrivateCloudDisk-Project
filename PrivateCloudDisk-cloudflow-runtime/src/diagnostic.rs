//! CloudFlow 统一编译诊断模型。

use miette::miette;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "UPPERCASE")]
pub enum Severity {
    Error,
    Warning,
    Info,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Location {
    pub file: String,
    pub line: usize,
    pub column: usize,
    #[serde(rename = "startOffset")]
    pub start_offset: usize,
    #[serde(rename = "endOffset")]
    pub end_offset: usize,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct SourceContext {
    #[serde(rename = "lineText")]
    pub line_text: String,
    pub pointer: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Diagnostic {
    pub code: String,
    pub severity: Severity,
    pub category: String,
    pub message: String,
    pub location: Location,
    pub source: SourceContext,
    pub suggestions: Vec<String>,
    pub help: Option<String>,
    #[serde(rename = "cliOutput")]
    pub cli_output: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Default)]
pub struct CompileDiagnostics {
    pub diagnostics: Vec<Diagnostic>,
}

impl Diagnostic {
    pub fn new(
        code: &str,
        category: &str,
        message: impl Into<String>,
        source: &str,
        file: &str,
        start: usize,
        end: usize,
        suggestions: Vec<String>,
        help: Option<String>,
    ) -> Self {
        let (line, column) = line_column(source, start);
        let line_text = source
            .lines()
            .nth(line.saturating_sub(1))
            .unwrap_or("")
            .to_string();
        let width = end
            .saturating_sub(start)
            .max(1)
            .min(line_text.chars().count().max(1));
        let pointer = format!(
            "{}{}",
            " ".repeat(column.saturating_sub(1)),
            "^".repeat(width)
        );
        let message = message.into();
        let cli_output = format!(
            "ERROR {code}\n\n{file}:{line}:{column}\n\n{message}\n\n{line_text}\n{pointer}"
        );
        Self {
            code: code.into(),
            severity: Severity::Error,
            category: category.into(),
            message,
            location: Location {
                file: file.into(),
                line,
                column,
                start_offset: start,
                end_offset: end,
            },
            source: SourceContext { line_text, pointer },
            suggestions,
            help,
            cli_output,
        }
    }

    /// 通过 miette 生成 CLI 兼容的 Report；JSON/CLI 的字段仍由本结构统一输出。
    pub fn miette_report(&self) -> miette::Report {
        miette!("{} {}\n{}", self.code, self.message, self.cli_output)
    }
}

pub fn line_column(source: &str, offset: usize) -> (usize, usize) {
    let bounded = offset.min(source.len());
    let before = &source[..bounded];
    let line = before.bytes().filter(|byte| *byte == b'\n').count() + 1;
    let column = before
        .rsplit('\n')
        .next()
        .map(|line| line.chars().count() + 1)
        .unwrap_or(1);
    (line, column)
}
