//! CloudFlow YAML 前端 —— 定位器与错误转换（需求 7.22/14.11）。
//!
//! `serde_yaml_ng` 反序列化到强类型结构后不携带每个字段的行/列（需求 7.6 可选手段），
//! 这里用**文档序扫描**把 YAML 标量原文映射回源码字节偏移：转换器按文档顺序逐项调用
//! `locate_scalar`，游标只前移，因此同一标量多次出现时也会命中各自的位置。
//! 该折衷在 `docs/CLOUDFLOW_YAML_DESIGN.md` 中明确记录（需求 7.7 备选方案）。
//!
//! 错误码分工（需求 31.9 起）：解析错误 `CFY-1001`、转换期结构/语义错误 `CFY-1002` 由本模块
//! 构造；**形状校验**（必填/类型/未知字段/非法值）收敛于 `schema.rs`，统一前缀
//! `CFY-SCHEMA-1001..1004`（原 `CFY-1003` 由 `CFY-SCHEMA-1003 UNKNOWN_FIELD` 取代）。

use crate::ast::Span;
use crate::diagnostic::{line_column, Diagnostic};
use serde_yaml_ng::Error as YamlError;

/// 解析错误 → CloudFlow 诊断（CFY-1001，行/列来自 libyaml 标记）。
pub(crate) fn yaml_parse_error(error: &YamlError, source: &str, filename: &str) -> Box<Diagnostic> {
    let (start, end) = match error.location() {
        Some(location) => (location.index(), location.index() + 1),
        None => (0, source.len().max(1)),
    };
    Box::new(Diagnostic::new(
        "CFY-1001",
        "YAML_PARSE_ERROR",
        format!("YAML 解析失败：{}", error),
        source,
        filename,
        start.min(source.len()),
        end.min(source.len()),
        vec!["检查 YAML 缩进、引号、锚点与别名是否合法".into()],
        Some("CloudFlow YAML 使用 libyaml 语法；字段参考 docs/CLOUDFLOW_YAML_DESIGN.md".into()),
    ))
}

/// 结构性问题（缺字段 / 形状非法）→ CFY-1002。
pub(crate) fn yaml_schema_error(
    message: impl Into<String>,
    source: &str,
    filename: &str,
    offset: usize,
    suggestions: Vec<String>,
) -> Diagnostic {
    let offset = offset.min(source.len());
    Diagnostic::new(
        "CFY-1002",
        "YAML_SCHEMA_ERROR",
        message,
        source,
        filename,
        offset,
        offset + 1,
        suggestions,
        Some("字段结构与参考规范不一致：docs/CLOUDFLOW_YAML_DESIGN.md".into()),
    )
}

/// 文档序标量定位器：把 YAML 标量文本映射回源码字节偏移。
#[derive(Debug, Default)]
pub(crate) struct Locator {
    cursor: usize,
}

impl Locator {
    pub(crate) fn new() -> Self {
        Self { cursor: 0 }
    }

    /// 在源码剩余区间查找 `needle`（尽量使用带引号的渲染形式以减少误命中），
    /// 返回字节偏移；找不到时退化到游标位置。
    pub(crate) fn locate(&mut self, source: &str, needle: &str) -> usize {
        // 光标可能落在多字节字符内部（前一次“未命中”按 needle 字节长推进所致），
        // 先对齐到最近的 UTF-8 字符起点，避免 `source[from..]` 切片 panic（中文内容）。
        let from = char_boundary(source.as_bytes(), self.cursor.min(source.len()));
        let candidates = candidate_renders(needle);
        let mut hit = None;
        for candidate in &candidates {
            if candidate.is_empty() {
                continue;
            }
            if let Some(relative) = source[from..].find(candidate.as_str()) {
                hit = Some((from + relative, candidate.len()));
                break;
            }
        }
        match hit {
            Some((offset, length)) => {
                let end = char_boundary(source.as_bytes(), offset + length);
                self.cursor = end;
                offset
            }
            None => {
                self.cursor = char_boundary(source.as_bytes(), from + needle.len());
                from
            }
        }
    }

    /// 生成只含单字符宽度的 Span（用于诊断定位）。
    pub(crate) fn span_at(&self, source: &str, offset: usize) -> Span {
        let offset = char_boundary(source.as_bytes(), offset.min(source.len()));
        let (line, column) = line_column(source, offset);
        Span {
            start: offset,
            end: offset + 1,
            line,
            column,
            end_line: line,
            end_column: column + 1,
        }
    }
}

/// 把字节下标对齐到最近的 UTF-8 字符起点（跳过连续的续延字节）。
fn char_boundary(bytes: &[u8], mut index: usize) -> usize {
    index = index.min(bytes.len());
    while index < bytes.len() && (0x80..0xC0).contains(&bytes[index]) {
        index += 1;
    }
    index
}

/// 为标量生成候选渲染形式：先双引号形式，再原文。
fn candidate_renders(value: &str) -> Vec<String> {
    let mut renders = Vec::new();
    if value.contains(' ') || value.contains('#') || value.starts_with('{') {
        renders.push(format!("\"{}\"", value.replace('\"', "\\\"")));
    } else {
        renders.push(format!("\"{}\"", value));
    }
    if !value.is_empty() {
        renders.push(value.to_owned());
    }
    renders
}
