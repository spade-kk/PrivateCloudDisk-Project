//! CloudFlow 统一编译诊断模型。

use miette::{LabeledSpan, MietteDiagnostic, NamedSource};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "UPPERCASE")]
pub enum Severity {
    Error,
    Warning,
    Info,
    Fatal,
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
    #[serde(rename = "documentationUrl")]
    pub documentation_url: String,
    #[serde(rename = "cliOutput")]
    pub cli_output: String,
    /// 仅用于 CLI 的 miette 源码渲染，禁止通过 HTTP/JSON 暴露完整源码副本。
    #[serde(skip)]
    pub full_source: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Default)]
pub struct CompileDiagnostics {
    pub diagnostics: Vec<Diagnostic>,
}

impl Diagnostic {
    // 诊断构造器的参数与 CLOUDFLOW_ERROR_DESIGN.md 的固定字段逐项对应，
    // 保持显式参数可以避免调用侧遗漏源码位置或修复建议。
    #[allow(clippy::too_many_arguments)]
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
        // offset 使用 UTF-8 byte，终端指针使用字符列；不能直接拿 byte 长度绘制中文源码 span。
        let width = source
            .get(start.min(source.len())..end.min(source.len()))
            .map(|value| value.lines().next().unwrap_or(value).chars().count())
            .unwrap_or(1)
            .max(1)
            .min(line_text.chars().count().max(1));
        let pointer = format!(
            "{}{}",
            " ".repeat(column.saturating_sub(1)),
            "^".repeat(width)
        );
        let message = message.into();
        let suggestion_text = if suggestions.is_empty() {
            String::new()
        } else {
            format!(
                "\n\n建议：\n{}",
                suggestions
                    .iter()
                    .map(|value| format!("- {value}"))
                    .collect::<Vec<_>>()
                    .join("\n")
            )
        };
        let help_text = help
            .as_ref()
            .map(|value| format!("\n\n帮助：{value}"))
            .unwrap_or_default();
        let cli_output = format!(
            "ERROR {code}\n\n{file}:{line}:{column}\n\n{message}\n\n{line} | {line_text}\n  | {pointer}{suggestion_text}{help_text}"
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
            documentation_url: format!("/docs/cloudflow/errors/{code}"),
            cli_output,
            full_source: source.to_owned(),
        }
    }

    /// 通过 miette 生成带源码标注的 CLI Report；JSON/CLI 字段仍由本结构统一输出。
    pub fn miette_report(&self) -> miette::Report {
        let label = LabeledSpan::new_with_span(
            Some(self.message.clone()),
            self.location.start_offset
                ..self.location.end_offset.max(self.location.start_offset + 1),
        );
        let mut diagnostic = MietteDiagnostic::new(self.message.clone())
            .with_code(self.code.clone())
            .with_labels([label])
            .with_url(self.documentation_url.clone());
        if let Some(help) = &self.help {
            diagnostic = diagnostic.with_help(help.clone());
        }
        miette::Report::new(diagnostic).with_source_code(NamedSource::new(
            self.location.file.clone(),
            self.full_source.clone(),
        ))
    }
}

pub fn line_column(source: &str, offset: usize) -> (usize, usize) {
    let bounded = char_boundary(source.as_bytes(), offset.min(source.len()));
    let before = &source[..bounded];
    let line = before.bytes().filter(|byte| *byte == b'\n').count() + 1;
    let column = before
        .rsplit('\n')
        .next()
        .map(|line| line.chars().count() + 1)
        .unwrap_or(1);
    (line, column)
}

/// 把字节下标对齐到最近的 UTF-8 字符起点（跳过连续的续延字节）。
/// 供 span/诊断定位使用：YAML 定位器可能给出落在多字节字符内部的近似偏移。
pub(crate) fn char_boundary(bytes: &[u8], mut index: usize) -> usize {
    index = index.min(bytes.len());
    while index < bytes.len() && (0x80..0xC0).contains(&bytes[index]) {
        index += 1;
    }
    index
}
