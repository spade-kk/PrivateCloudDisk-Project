//! Compiler-backed document analysis, symbol extraction and LSP projections.
//!
//! [CLOUDFLOW-LS-CORE-001] This module deliberately calls the existing compiler
//! frontend (`parse_frontend_detailed` + `semantic::validate_with_rules`) instead
//! of recreating a second lexer, parser, AST, type checker or expression engine.

use crate::{
    auth::AuthContext,
    capability::{Capability, CapabilityProvider, DynamicCapabilityCatalog},
    document::{byte_range_to_lsp, Document},
    protocol::{Position, Range},
};
use cloudflow_runtime::{
    ast::{FlowNode, Span, StepNode, WorkflowNode},
    diagnostic::{Diagnostic, Severity},
    language_of, parse_frontend_detailed, semantic,
};
use serde_json::{json, Value};
use std::{collections::HashMap, sync::Arc};
use tokio::sync::RwLock;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SymbolKind {
    Workflow,
    Variable,
    Step,
    Action,
}

impl SymbolKind {
    fn lsp_kind(&self) -> u32 {
        match self {
            SymbolKind::Workflow => 12, // Function
            SymbolKind::Variable => 13, // Variable
            SymbolKind::Step => 6,      // Method
            SymbolKind::Action => 3,    // Function
        }
    }

    fn completion_kind(&self) -> u32 {
        match self {
            SymbolKind::Variable => 6,
            SymbolKind::Step => 2,
            SymbolKind::Action | SymbolKind::Workflow => 3,
        }
    }
}

#[derive(Debug, Clone)]
pub struct Symbol {
    pub name: String,
    pub detail: String,
    pub kind: SymbolKind,
    pub range: Range,
    pub selection_range: Range,
}

#[derive(Debug, Clone)]
pub struct AnalysisResult {
    pub version: i64,
    pub ast: Option<WorkflowNode>,
    pub diagnostics: Vec<Diagnostic>,
    pub symbols: Vec<Symbol>,
    pub capabilities: Vec<Capability>,
}

#[derive(Clone, Default)]
pub struct AnalysisCache {
    by_uri: Arc<RwLock<HashMap<String, AnalysisResult>>>,
}

impl AnalysisCache {
    pub async fn get(&self, uri: &str, version: i64) -> Option<AnalysisResult> {
        self.by_uri
            .read()
            .await
            .get(uri)
            .filter(|result| result.version == version)
            .cloned()
    }

    pub async fn put(&self, uri: String, result: AnalysisResult) {
        self.by_uri.write().await.insert(uri, result);
    }

    pub async fn remove(&self, uri: &str) {
        self.by_uri.write().await.remove(uri);
    }
}

pub async fn analyze(
    document: &Document,
    capability_provider: &CapabilityProvider,
    auth: &AuthContext,
) -> AnalysisResult {
    // Capability resolution may fail because a local editor is offline or the
    // token expired. Parsing/type/reference checking remains available locally;
    // only an authenticated Hub response turns on strict action availability.
    let (capabilities, catalog) = match capability_provider.capabilities(auth).await {
        Ok(capabilities) => {
            let catalog = DynamicCapabilityCatalog::from_capabilities(&capabilities, true);
            (capabilities, catalog)
        }
        Err(_) => (
            Vec::new(),
            DynamicCapabilityCatalog::from_capabilities(&[], false),
        ),
    };
    let language = language_of(&document.uri);
    match parse_frontend_detailed(&document.text, &document.uri, language) {
        Ok((workflow, mut diagnostics)) => {
            diagnostics.extend(semantic::validate_with_rules(
                &workflow,
                &catalog,
                &document.text,
                &document.uri,
                &[],
            ));
            let symbols = symbols_from_workflow(&document.text, &workflow);
            AnalysisResult {
                version: document.version,
                ast: Some(workflow),
                diagnostics,
                symbols,
                capabilities,
            }
        }
        Err(diagnostic) => AnalysisResult {
            version: document.version,
            ast: None,
            diagnostics: vec![*diagnostic],
            symbols: Vec::new(),
            capabilities,
        },
    }
}

pub fn diagnostics_to_lsp(source: &str, diagnostics: &[Diagnostic]) -> Vec<Value> {
    diagnostics.iter().map(|item| {
        let severity = match item.severity {
            Severity::Error | Severity::Fatal => 1,
            Severity::Warning => 2,
            Severity::Info => 3,
        };
        json!({
            "range": byte_range_to_lsp(source, item.location.start_offset, item.location.end_offset),
            "severity": severity,
            "code": item.code,
            "source": "cloudflow-compiler",
            "message": item.message,
            "relatedInformation": [{
                "location": {"uri": item.location.file, "range": byte_range_to_lsp(source, item.location.start_offset, item.location.end_offset)},
                "message": item.help.clone().unwrap_or_default(),
            }],
        })
    }).collect()
}

pub fn completion_items(
    document: &Document,
    position: Position,
    result: &AnalysisResult,
) -> Vec<Value> {
    let prefix = line_prefix(&document.text, position).unwrap_or_default();
    let mut items = keyword_completions();
    let word = identifier_at(&document.text, position).unwrap_or_default();
    if prefix.trim_end().ends_with("action")
        || prefix.trim_end().ends_with("action ")
        || prefix.contains("action ")
    {
        for capability in &result.capabilities {
            items.push(capability_completion(capability, &word));
        }
    }
    if prefix.contains("vars.") || prefix.trim_end().ends_with("vars") {
        for symbol in result
            .symbols
            .iter()
            .filter(|symbol| symbol.kind == SymbolKind::Variable)
        {
            items.push(json!({
                "label": format!("vars.{}", symbol.name), "kind": symbol.kind.completion_kind(),
                "detail": symbol.detail, "insertText": format!("vars.{}", symbol.name),
            }));
        }
    }
    if prefix.contains("steps.") || prefix.trim_end().ends_with("steps") {
        for symbol in result
            .symbols
            .iter()
            .filter(|symbol| symbol.kind == SymbolKind::Step)
        {
            items.push(json!({
                "label": format!("steps.{}.output", symbol.name), "kind": symbol.kind.completion_kind(),
                "detail": "CloudFlow step output", "insertText": format!("steps.{}.output", symbol.name),
            }));
        }
    }
    items
}

pub fn hover(document: &Document, position: Position, result: &AnalysisResult) -> Option<Value> {
    let word = identifier_at(&document.text, position)?;
    if let Some(capability) = result
        .capabilities
        .iter()
        .find(|item| item.capability_key == word)
    {
        return Some(json!({
            "contents": {"kind": "markdown", "value": capability_markdown(capability)},
            "range": word_range(&document.text, position, &word),
        }));
    }
    result.symbols.iter().find(|symbol| symbol.name == word).map(|symbol| json!({
        "contents": {"kind": "markdown", "value": format!("`{}` **{}**\\n\\n{}", symbol.name, symbol_kind_label(&symbol.kind), symbol.detail)},
        "range": symbol.selection_range,
    }))
}

pub fn signature_help(
    document: &Document,
    position: Position,
    result: &AnalysisResult,
) -> Option<Value> {
    let prefix = line_prefix(&document.text, position)?;
    let action = prefix
        .rsplit_once("action ")?
        .1
        .trim()
        .trim_end_matches('(')
        .trim();
    let capability = result
        .capabilities
        .iter()
        .find(|item| item.capability_key == action)?;
    let parameters = schema_parameters(capability);
    Some(json!({
        "signatures": [{
            "label": format!("{}({})", capability.capability_key, parameters.iter().map(|item| item.get("label").and_then(Value::as_str).unwrap_or("")).collect::<Vec<_>>().join(", ")),
            "documentation": {"kind": "markdown", "value": capability_markdown(capability)},
            "parameters": parameters,
        }],
        "activeSignature": 0,
        "activeParameter": 0,
    }))
}

pub fn definition(document: &Document, position: Position, result: &AnalysisResult) -> Vec<Value> {
    let Some(word) = identifier_at(&document.text, position) else {
        return Vec::new();
    };
    result
        .symbols
        .iter()
        .filter(|symbol| symbol.name == word)
        .map(|symbol| {
            json!({
                "uri": document.uri, "range": symbol.selection_range,
            })
        })
        .collect()
}

pub fn references(
    document: &Document,
    position: Position,
    include_declaration: bool,
) -> Vec<Value> {
    let Some(word) = identifier_at(&document.text, position) else {
        return Vec::new();
    };
    word_occurrences(&document.text, &word)
        .into_iter()
        .filter_map(|range| {
            let is_declaration =
                range.start.line == position.line && range.start.character == position.character;
            (include_declaration || !is_declaration)
                .then(|| json!({"uri": document.uri, "range": range}))
        })
        .collect()
}

pub fn prepare_rename(document: &Document, position: Position) -> Option<Range> {
    let word = identifier_at(&document.text, position)?;
    word_range(&document.text, position, &word)
}

pub fn rename(document: &Document, position: Position, new_name: &str) -> Result<Value, String> {
    if !is_identifier(new_name) {
        return Err("CloudFlow 符号只能使用字母、数字、_ 或 -，且不能以数字开头".into());
    }
    let old_name = identifier_at(&document.text, position)
        .ok_or_else(|| "光标处没有可重命名符号".to_string())?;
    let edits = word_occurrences(&document.text, &old_name)
        .into_iter()
        .map(|range| json!({"range": range, "newText": new_name}))
        .collect::<Vec<_>>();
    Ok(json!({"changes": {document.uri.clone(): edits}}))
}

pub fn document_symbols(result: &AnalysisResult) -> Vec<Value> {
    result
        .symbols
        .iter()
        .map(|symbol| {
            json!({
                "name": symbol.name, "detail": symbol.detail, "kind": symbol.kind.lsp_kind(),
                "range": symbol.range, "selectionRange": symbol.selection_range,
            })
        })
        .collect()
}

pub fn folding_ranges(source: &str) -> Vec<Value> {
    let mut stack = Vec::new();
    let mut result = Vec::new();
    for (line, text) in source.lines().enumerate() {
        for _ in text.match_indices('{') {
            stack.push(line as u32);
        }
        for _ in text.match_indices('}') {
            if let Some(start) = stack.pop().filter(|start| *start < line as u32) {
                result.push(json!({"startLine": start, "endLine": line, "kind": "region"}));
            }
        }
    }
    result
}

pub fn semantic_tokens(source: &str, result: &AnalysisResult) -> Vec<u32> {
    // LSP delta encoding: keyword/action/variable/step classifications only.
    let mut tokens: Vec<(Position, u32, u32)> = Vec::new();
    for keyword in [
        "workflow",
        "variables",
        "trigger",
        "steps",
        "step",
        "action",
        "if",
        "else",
        "foreach",
        "parallel",
        "try",
        "catch",
        "return",
    ] {
        for range in word_occurrences(source, keyword) {
            tokens.push((range.start, keyword.encode_utf16().count() as u32, 0));
        }
    }
    for symbol in &result.symbols {
        let token_type = match symbol.kind {
            SymbolKind::Variable => 1,
            SymbolKind::Step => 2,
            SymbolKind::Action => 3,
            SymbolKind::Workflow => 4,
        };
        tokens.push((
            symbol.selection_range.start,
            symbol.name.encode_utf16().count() as u32,
            token_type,
        ));
    }
    tokens.sort_by_key(|(position, _, _)| (position.line, position.character));
    let mut previous = Position::default();
    let mut encoded = Vec::with_capacity(tokens.len() * 5);
    for (position, length, kind) in tokens {
        let delta_line = position.line - previous.line;
        let delta_start = if delta_line == 0 {
            position.character.saturating_sub(previous.character)
        } else {
            position.character
        };
        encoded.extend([delta_line, delta_start, length, kind, 0]);
        previous = position;
    }
    encoded
}

fn symbols_from_workflow(source: &str, workflow: &WorkflowNode) -> Vec<Symbol> {
    let mut symbols = vec![symbol_from_span(
        source,
        &workflow.name,
        "CloudFlow workflow",
        SymbolKind::Workflow,
        workflow.span,
    )];
    symbols.extend(workflow.variables.iter().map(|variable| {
        symbol_from_span(
            source,
            &variable.name,
            format!("{} variable", variable.type_name),
            SymbolKind::Variable,
            variable.span,
        )
    }));
    collect_steps(source, &workflow.flow, &mut symbols);
    for handler in &workflow.handlers {
        collect_steps(source, &handler.nodes, &mut symbols);
    }
    symbols
}

fn collect_steps(source: &str, nodes: &[FlowNode], output: &mut Vec<Symbol>) {
    for node in nodes {
        match node {
            FlowNode::Step(step) => collect_step(source, step, output),
            FlowNode::StepGroup(group) => {
                for step in &group.steps {
                    collect_step(source, step, output);
                }
            }
            FlowNode::Condition(value) => {
                collect_steps(source, &value.true_branch, output);
                collect_steps(source, &value.false_branch, output);
            }
            FlowNode::Loop(value) => collect_steps(source, &value.body, output),
            FlowNode::For(value) => collect_steps(source, &value.body, output),
            FlowNode::While(value) => collect_steps(source, &value.body, output),
            FlowNode::Parallel(value) => collect_steps(source, &value.branches, output),
            FlowNode::TryCatch(value) => {
                collect_steps(source, &value.try_nodes, output);
                collect_steps(source, &value.catch_nodes, output);
                collect_steps(source, &value.finally_nodes, output);
            }
            FlowNode::Switch(value) => {
                for case in &value.cases {
                    collect_steps(source, &case.body, output);
                }
                collect_steps(source, &value.default_branch, output);
            }
            _ => {}
        }
    }
}

fn collect_step(source: &str, step: &StepNode, output: &mut Vec<Symbol>) {
    output.push(symbol_from_span(
        source,
        &step.id,
        "CloudFlow step",
        SymbolKind::Step,
        step.span,
    ));
    if let Some(action) = &step.action {
        let key = semantic::action_key(action);
        output.push(symbol_from_span(
            source,
            &key,
            "Capability action",
            SymbolKind::Action,
            action.span,
        ));
    }
    collect_steps(source, &step.controls, output);
    collect_steps(source, &step.on_error, output);
}

fn symbol_from_span(
    source: &str,
    name: &str,
    detail: impl Into<String>,
    kind: SymbolKind,
    span: Span,
) -> Symbol {
    let range = byte_range_to_lsp(source, span.start, span.end);
    let selection_range = find_name_in_span(source, name, span).unwrap_or(range);
    Symbol {
        name: name.into(),
        detail: detail.into(),
        kind,
        range,
        selection_range,
    }
}

fn find_name_in_span(source: &str, name: &str, span: Span) -> Option<Range> {
    let slice = source.get(span.start.min(source.len())..span.end.min(source.len()))?;
    let local = slice.find(name)?;
    Some(byte_range_to_lsp(
        source,
        span.start + local,
        span.start + local + name.len(),
    ))
}

fn keyword_completions() -> Vec<Value> {
    [
        ("workflow", "workflow \"${1:name}\" {\\n  ${0}\\n}", "Workflow declaration"),
        ("step", "step ${1:id} {\\n  action ${2:api:service.method}\\n}", "Workflow step"),
        ("action", "action ", "Capability action (dynamic catalog follows)"),
        ("variables", "variables {\\n  ${1:name}: ${2:string}\\n}", "Typed variables"),
        ("trigger", "trigger {\\n  manual\\n}", "Workflow trigger"),
        ("if", "if ${1:condition} {\\n  ${0}\\n}", "Conditional branch"),
        ("foreach", "foreach ${1:item} in ${2:items} {\\n  ${0}\\n}", "Iteration"),
        ("parallel", "parallel {\\n  ${0}\\n}", "Parallel branch"),
    ].into_iter().map(|(label, insert_text, detail)| json!({"label": label, "kind": 14, "detail": detail, "insertText": insert_text, "insertTextFormat": 2})).collect()
}

fn capability_completion(capability: &Capability, _word: &str) -> Value {
    json!({
        "label": capability.capability_key, "kind": 3, "detail": capability.display_name,
        "documentation": {"kind": "markdown", "value": capability_markdown(capability)},
        "insertText": capability.capability_key,
    })
}

fn capability_markdown(capability: &Capability) -> String {
    let mut text = format!(
        "### `{}`\\n\\n{}",
        capability.capability_key,
        if capability.description.is_empty() {
            &capability.display_name
        } else {
            &capability.description
        }
    );
    let parameters = schema_parameters(capability);
    if !parameters.is_empty() {
        text.push_str("\\n\\n**参数**\\n");
        for parameter in parameters {
            text.push_str(&format!(
                "- `{}`\\n",
                parameter
                    .get("label")
                    .and_then(Value::as_str)
                    .unwrap_or("value")
            ));
        }
    }
    text
}

fn schema_parameters(capability: &Capability) -> Vec<Value> {
    let Ok(schema) = serde_json::from_str::<Value>(&capability.input_schema_json) else {
        return Vec::new();
    };
    let required = schema
        .get("required")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default()
        .into_iter()
        .filter_map(|value| value.as_str().map(str::to_owned))
        .collect::<Vec<_>>();
    schema.get("properties").and_then(Value::as_object).map(|properties| properties.iter().map(|(name, definition)| {
        let typ = definition.get("type").and_then(Value::as_str).unwrap_or("unknown");
        let optional = if required.iter().any(|required| required == name) { "" } else { "?" };
        json!({"label": format!("{}{}: {}", name, optional, typ), "documentation": definition.get("description").and_then(Value::as_str).unwrap_or("")})
    }).collect()).unwrap_or_default()
}

fn identifier_at(source: &str, position: Position) -> Option<String> {
    let offset = crate::document::position_to_byte_offset(source, position).ok()?;
    let bytes = source.as_bytes();
    let mut start = offset.min(bytes.len());
    while start > 0 && is_identifier_byte(bytes[start - 1]) {
        start -= 1;
    }
    let mut end = offset.min(bytes.len());
    while end < bytes.len() && is_identifier_byte(bytes[end]) {
        end += 1;
    }
    (start < end).then(|| source[start..end].to_string())
}

fn word_range(source: &str, position: Position, word: &str) -> Option<Range> {
    let offset = crate::document::position_to_byte_offset(source, position).ok()?;
    let start = offset.saturating_sub(word.len());
    let exact = source
        .get(start..offset)
        .filter(|candidate| *candidate == word)
        .map(|_| start)
        .or_else(|| source[..offset].rfind(word));
    exact.map(|start| byte_range_to_lsp(source, start, start + word.len()))
}

fn word_occurrences(source: &str, word: &str) -> Vec<Range> {
    let mut result = Vec::new();
    let mut cursor = 0;
    while let Some(found) = source[cursor..].find(word) {
        let start = cursor + found;
        let end = start + word.len();
        let before = start == 0 || !is_identifier_byte(source.as_bytes()[start - 1]);
        let after = end == source.len() || !is_identifier_byte(source.as_bytes()[end]);
        if before && after {
            result.push(byte_range_to_lsp(source, start, end));
        }
        cursor = end;
    }
    result
}

fn line_prefix(source: &str, position: Position) -> Option<String> {
    let offset = crate::document::position_to_byte_offset(source, position).ok()?;
    Some(
        source[..offset]
            .rsplit('\n')
            .next()
            .unwrap_or_default()
            .to_string(),
    )
}

fn is_identifier(value: &str) -> bool {
    value
        .chars()
        .next()
        .is_some_and(|item| item.is_ascii_alphabetic() || item == '_')
        && value
            .chars()
            .all(|item| item.is_ascii_alphanumeric() || item == '_' || item == '-')
}

fn is_identifier_byte(byte: u8) -> bool {
    byte.is_ascii_alphanumeric() || byte == b'_' || byte == b'-' || byte == b'.' || byte == b':'
}

fn symbol_kind_label(kind: &SymbolKind) -> &'static str {
    match kind {
        SymbolKind::Workflow => "workflow",
        SymbolKind::Variable => "variable",
        SymbolKind::Step => "step",
        SymbolKind::Action => "capability",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn parser_and_semantic_rules_are_reused_for_diagnostics() {
        let doc = Document {
            uri: "file:///sample.flow".into(),
            language_id: "cloudflow".into(),
            version: 1,
            text: "workflow \"sample\" { trigger { manual {} } }".into(),
            dirty_lines: None,
        };
        let provider =
            CapabilityProvider::new("http://127.0.0.1:1", std::time::Duration::from_secs(1))
                .unwrap();
        let result = analyze(&doc, &provider, &AuthContext::default()).await;
        assert!(result
            .diagnostics
            .iter()
            .any(|diagnostic| diagnostic.code == "CF2001"));
    }
}
