//! Versioned in-memory LSP documents and UTF-16 coordinate conversion.
//!
//! The compiler uses UTF-8 byte spans while LSP uses UTF-16 code units. Keeping
//! the conversion at this boundary prevents Chinese/emoji source from producing
//! shifted diagnostics or edits.

use crate::protocol::{Position, Range};
use serde::Deserialize;
use std::collections::HashMap;

pub const MAX_DOCUMENT_BYTES: usize = 256 * 1024;

#[derive(Debug, Clone)]
pub struct Document {
    pub uri: String,
    pub language_id: String,
    pub version: i64,
    pub text: String,
    /// Modified line interval (0-based, inclusive) supplied to the incremental
    /// analysis scheduler. The current compiler parser is whole-document; this
    /// still lets unchanged versions reuse their AST/semantic cache.
    pub dirty_lines: Option<(u32, u32)>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TextDocumentIdentifier {
    pub uri: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionedTextDocumentIdentifier {
    pub uri: String,
    pub version: i64,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DidOpenTextDocument {
    pub uri: String,
    pub language_id: String,
    pub version: i64,
    pub text: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TextDocumentContentChangeEvent {
    #[serde(default)]
    pub range: Option<Range>,
    pub text: String,
}

#[derive(Debug, Default)]
pub struct DocumentManager {
    documents: HashMap<String, Document>,
}

impl DocumentManager {
    pub fn open(&mut self, item: DidOpenTextDocument) -> Result<Document, String> {
        ensure_size(&item.text)?;
        let document = Document {
            uri: item.uri.clone(),
            language_id: item.language_id,
            version: item.version,
            text: item.text,
            dirty_lines: Some((0, u32::MAX)),
        };
        self.documents.insert(item.uri, document.clone());
        Ok(document)
    }

    pub fn change(
        &mut self,
        identifier: VersionedTextDocumentIdentifier,
        changes: Vec<TextDocumentContentChangeEvent>,
    ) -> Result<Document, String> {
        let document = self
            .documents
            .get_mut(&identifier.uri)
            .ok_or_else(|| format!("未打开文档：{}", identifier.uri))?;
        if identifier.version <= document.version {
            return Err(format!(
                "文档版本冲突：收到 {}，当前为 {}",
                identifier.version, document.version
            ));
        }
        let mut first = u32::MAX;
        let mut last = 0;
        for change in changes {
            let changed = apply_change(&mut document.text, &change)?;
            first = first.min(changed.0);
            last = last.max(changed.1);
        }
        ensure_size(&document.text)?;
        document.version = identifier.version;
        document.dirty_lines = Some((first.min(last), last));
        Ok(document.clone())
    }

    pub fn close(&mut self, uri: &str) {
        self.documents.remove(uri);
    }

    pub fn get(&self, uri: &str) -> Option<Document> {
        self.documents.get(uri).cloned()
    }

    pub fn all(&self) -> impl Iterator<Item = &Document> {
        self.documents.values()
    }
}

fn ensure_size(text: &str) -> Result<(), String> {
    if text.len() > MAX_DOCUMENT_BYTES {
        Err("CloudFlow LS 为保护编辑器稳定性，拒绝解析超过 256 KiB 的文档".into())
    } else {
        Ok(())
    }
}

fn apply_change(
    text: &mut String,
    change: &TextDocumentContentChangeEvent,
) -> Result<(u32, u32), String> {
    let Some(range) = change.range else {
        let end = change.text.lines().count().saturating_sub(1) as u32;
        *text = change.text.clone();
        return Ok((0, end));
    };
    let start = position_to_byte_offset(text, range.start)?;
    let end = position_to_byte_offset(text, range.end)?;
    if start > end {
        return Err("didChange 的 range 起点不能位于终点之后".into());
    }
    text.replace_range(start..end, &change.text);
    Ok((
        range.start.line,
        range.start.line + change.text.lines().count().saturating_sub(1) as u32,
    ))
}

pub fn position_to_byte_offset(text: &str, position: Position) -> Result<usize, String> {
    let mut offset = 0usize;
    let mut line = 0u32;
    for segment in text.split_inclusive('\n') {
        let body = segment.strip_suffix('\n').unwrap_or(segment);
        if line == position.line {
            return utf16_column_to_byte(body, position.character).map(|column| offset + column);
        }
        offset += segment.len();
        line += 1;
    }
    // Allow the end position after the final line but reject gaps past it.
    if line == position.line {
        return utf16_column_to_byte("", position.character).map(|column| offset + column);
    }
    Err(format!("LSP position 行号越界：{}", position.line))
}

pub fn byte_offset_to_position(text: &str, raw_offset: usize) -> Position {
    let offset = raw_offset.min(text.len());
    let mut consumed = 0usize;
    let mut line = 0u32;
    for segment in text.split_inclusive('\n') {
        let line_end = consumed + segment.len();
        if offset <= line_end {
            let body = segment.strip_suffix('\n').unwrap_or(segment);
            let local = offset.saturating_sub(consumed).min(body.len());
            let safe = floor_char_boundary(body, local);
            let character = body[..safe].encode_utf16().count() as u32;
            return Position { line, character };
        }
        consumed = line_end;
        line += 1;
    }
    Position { line, character: 0 }
}

pub fn byte_range_to_lsp(text: &str, start: usize, end: usize) -> Range {
    Range {
        start: byte_offset_to_position(text, start),
        end: byte_offset_to_position(text, end),
    }
}

fn utf16_column_to_byte(line: &str, target: u32) -> Result<usize, String> {
    let mut units = 0u32;
    for (byte, character) in line.char_indices() {
        if units == target {
            return Ok(byte);
        }
        units += character.len_utf16() as u32;
        if units > target {
            return Err("LSP position 落在 UTF-16 surrogate pair 中间".into());
        }
    }
    if units == target {
        Ok(line.len())
    } else {
        Err(format!("LSP character 越界：{target}"))
    }
}

fn floor_char_boundary(input: &str, mut offset: usize) -> usize {
    offset = offset.min(input.len());
    while offset > 0 && !input.is_char_boundary(offset) {
        offset -= 1;
    }
    offset
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lsp_positions_round_trip_utf16() {
        let text = "变量😀\nnext";
        let position = Position {
            line: 0,
            character: 4,
        };
        let byte = position_to_byte_offset(text, position).unwrap();
        assert_eq!(&text[byte..], "\nnext");
        assert_eq!(byte_offset_to_position(text, byte), position);
    }

    #[test]
    fn ranged_change_respects_versions() {
        let mut manager = DocumentManager::default();
        manager
            .open(DidOpenTextDocument {
                uri: "file:///a.flow".into(),
                language_id: "cloudflow".into(),
                version: 1,
                text: "abc".into(),
            })
            .unwrap();
        let changed = manager
            .change(
                VersionedTextDocumentIdentifier {
                    uri: "file:///a.flow".into(),
                    version: 2,
                },
                vec![TextDocumentContentChangeEvent {
                    range: Some(Range {
                        start: Position {
                            line: 0,
                            character: 1,
                        },
                        end: Position {
                            line: 0,
                            character: 2,
                        },
                    }),
                    text: "Z".into(),
                }],
            )
            .unwrap();
        assert_eq!(changed.text, "aZc");
    }
}
