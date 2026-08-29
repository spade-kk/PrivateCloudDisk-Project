//! CloudFlow LSP method dispatcher.
//!
//! One `LspSession` belongs to one stdio process or one TCP/UDS/WebSocket
//! connection. Documents, auth context, AST cache and cancellation markers are
//! therefore never shared across users in server mode.

use crate::{
    analysis::{self, AnalysisCache, AnalysisResult},
    auth::AuthContext,
    capability::CapabilityProvider,
    document::{
        DidOpenTextDocument, DocumentManager, TextDocumentContentChangeEvent,
        TextDocumentIdentifier, VersionedTextDocumentIdentifier,
    },
    protocol::{error_response, notification, response, JsonRpcError, JsonRpcRequest, Position},
};
use serde::Deserialize;
use serde_json::{json, Value};
use std::{collections::HashSet, sync::Arc};
use tokio::sync::{Mutex, RwLock};

#[derive(Clone)]
pub struct LanguageServer {
    capabilities: CapabilityProvider,
}

impl LanguageServer {
    pub fn new(capabilities: CapabilityProvider) -> Self {
        Self { capabilities }
    }

    pub fn session(&self, local_auth: AuthContext) -> LspSession {
        LspSession {
            server: self.clone(),
            documents: Arc::new(RwLock::new(DocumentManager::default())),
            analyses: AnalysisCache::default(),
            auth: Arc::new(RwLock::new(local_auth)),
            cancelled: Arc::new(Mutex::new(HashSet::new())),
            initialized: Arc::new(Mutex::new(false)),
        }
    }
}

#[derive(Clone)]
pub struct LspSession {
    server: LanguageServer,
    documents: Arc<RwLock<DocumentManager>>,
    analyses: AnalysisCache,
    auth: Arc<RwLock<AuthContext>>,
    cancelled: Arc<Mutex<HashSet<String>>>,
    initialized: Arc<Mutex<bool>>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct InitializeParams {
    #[serde(default)]
    initialization_options: Option<InitializeOptions>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
struct InitializeOptions {
    #[serde(default)]
    access_token: Option<String>,
    #[serde(default)]
    tenant_id: Option<String>,
    #[serde(default)]
    space_id: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DidOpenParams {
    text_document: DidOpenTextDocument,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DidChangeParams {
    text_document: VersionedTextDocumentIdentifier,
    content_changes: Vec<TextDocumentContentChangeEvent>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DidCloseParams {
    text_document: TextDocumentIdentifier,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct TextDocumentPositionParams {
    text_document: TextDocumentIdentifier,
    position: Position,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ReferenceParams {
    #[serde(flatten)]
    position: TextDocumentPositionParams,
    #[serde(default)]
    context: ReferenceContext,
}

#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ReferenceContext {
    #[serde(default)]
    include_declaration: bool,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RenameParams {
    #[serde(flatten)]
    position: TextDocumentPositionParams,
    new_name: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DocumentOnlyParams {
    text_document: TextDocumentIdentifier,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ExecuteCommandParams {
    command: String,
}

impl LspSession {
    /// Returns outgoing JSON-RPC messages. Notifications can produce compiler
    /// diagnostics, while requests append a correlated result/error response.
    pub async fn handle(&self, request: JsonRpcRequest) -> Vec<Value> {
        let method = request.method.clone();
        if method == "$/cancelRequest" {
            if let Some(id) = request.params.get("id") {
                self.cancelled.lock().await.insert(id.to_string());
            }
            return Vec::new();
        }
        let result = self.handle_method(&request).await;
        if request.is_notification() {
            return result.unwrap_or_else(|_| Vec::new());
        }
        let id = request.id.clone().expect("request id checked above");
        match result {
            Ok(mut messages) => {
                if self.is_cancelled(&id).await {
                    messages.push(error_response(Some(id), JsonRpcError::cancelled()));
                } else {
                    let result = messages.pop().unwrap_or(Value::Null);
                    messages.push(response(id, result));
                }
                messages
            }
            Err(error) => vec![error_response(Some(id), error)],
        }
    }

    async fn handle_method(&self, request: &JsonRpcRequest) -> Result<Vec<Value>, JsonRpcError> {
        match request.method.as_str() {
            "initialize" => self
                .initialize(&request.params)
                .await
                .map(|result| vec![result]),
            "initialized" => Ok(Vec::new()),
            "shutdown" => Ok(vec![Value::Null]),
            "exit" => Ok(Vec::new()),
            "textDocument/didOpen" => self.did_open(&request.params).await,
            "textDocument/didChange" => self.did_change(&request.params).await,
            "textDocument/didClose" => self.did_close(&request.params).await,
            "textDocument/completion" => {
                self.with_position(&request.params, |document, result, position| {
                    Value::Array(analysis::completion_items(document, position, result))
                })
                .await
            }
            "textDocument/hover" => {
                self.with_position(&request.params, |document, result, position| {
                    analysis::hover(document, position, result).unwrap_or(Value::Null)
                })
                .await
            }
            "textDocument/signatureHelp" => {
                self.with_position(&request.params, |document, result, position| {
                    analysis::signature_help(document, position, result).unwrap_or(Value::Null)
                })
                .await
            }
            "textDocument/definition" => {
                self.with_position(&request.params, |document, result, position| {
                    Value::Array(analysis::definition(document, position, result))
                })
                .await
            }
            "textDocument/references" => self.references(&request.params).await,
            "textDocument/prepareRename" => {
                self.with_position(&request.params, |document, _result, position| {
                    serde_json::to_value(analysis::prepare_rename(document, position))
                        .unwrap_or(Value::Null)
                })
                .await
            }
            "textDocument/rename" => self.rename(&request.params).await,
            "textDocument/documentSymbol" => self.document_symbols(&request.params).await,
            "textDocument/semanticTokens/full" => self.semantic_tokens(&request.params).await,
            "textDocument/foldingRange" => self.folding_ranges(&request.params).await,
            "textDocument/formatting" => self.formatting(&request.params).await,
            // [CLOUDFLOW-LS-CAPABILITY-REFRESH-001] 插件安装/下架、权限和租户策略变化
            // 由 IDE/Web Studio 的平台事件桥接为此通知。刷新仅作用于当前 LSP 会话的
            // token + tenant + space 缓存，绝不清除其他用户连接的能力结果。
            "cloudflow/capabilitiesChanged" => self.capabilities_changed().await,
            "workspace/executeCommand" => self.execute_command(&request.params).await,
            _ => Err(JsonRpcError::method_not_found(&request.method)),
        }
    }

    async fn initialize(&self, params: &Value) -> Result<Value, JsonRpcError> {
        let params: InitializeParams = serde_json::from_value(params.clone()).map_err(|error| {
            JsonRpcError::invalid_params(format!("initialize 参数无效：{error}"))
        })?;
        if let Some(options) = params.initialization_options {
            let mut auth = self.auth.write().await;
            if options.access_token.is_some() {
                auth.access_token = options.access_token;
            }
            if options.tenant_id.is_some() {
                auth.tenant_id = options.tenant_id;
            }
            if options.space_id.is_some() {
                auth.space_id = options.space_id;
            }
        }
        *self.initialized.lock().await = true;
        Ok(json!({
            "capabilities": {
                "positionEncoding": "utf-16",
                "textDocumentSync": {"openClose": true, "change": 2, "save": {"includeText": false}},
                "completionProvider": {"triggerCharacters": [" ", ".", ":", "(", ","], "resolveProvider": false},
                "hoverProvider": true,
                "signatureHelpProvider": {"triggerCharacters": ["(", ","]},
                "definitionProvider": true,
                "referencesProvider": true,
                "renameProvider": {"prepareProvider": true},
                "documentSymbolProvider": true,
                "documentFormattingProvider": true,
                "foldingRangeProvider": true,
                "semanticTokensProvider": {"legend": {"tokenTypes": ["keyword", "variable", "method", "function", "namespace"], "tokenModifiers": []}, "full": true},
                "executeCommandProvider": {"commands": ["cloudflow.clearCapabilityCache"]},
                "experimental": {"cloudflowCapabilityRefresh": true}
            },
            "serverInfo": {"name": "CloudFlow Language Server", "version": env!("CARGO_PKG_VERSION")}
        }))
    }

    async fn did_open(&self, params: &Value) -> Result<Vec<Value>, JsonRpcError> {
        self.require_initialized().await?;
        let params: DidOpenParams = deserialize(params, "didOpen")?;
        let document = self
            .documents
            .write()
            .await
            .open(params.text_document)
            .map_err(JsonRpcError::invalid_params)?;
        Ok(vec![self.reanalyze_notification(&document).await])
    }

    async fn did_change(&self, params: &Value) -> Result<Vec<Value>, JsonRpcError> {
        self.require_initialized().await?;
        let params: DidChangeParams = deserialize(params, "didChange")?;
        let document = self
            .documents
            .write()
            .await
            .change(params.text_document, params.content_changes)
            .map_err(JsonRpcError::invalid_params)?;
        Ok(vec![self.reanalyze_notification(&document).await])
    }

    async fn did_close(&self, params: &Value) -> Result<Vec<Value>, JsonRpcError> {
        let params: DidCloseParams = deserialize(params, "didClose")?;
        self.documents
            .write()
            .await
            .close(&params.text_document.uri);
        self.analyses.remove(&params.text_document.uri).await;
        Ok(vec![notification(
            "textDocument/publishDiagnostics",
            json!({"uri": params.text_document.uri, "diagnostics": []}),
        )])
    }

    async fn with_position<F>(
        &self,
        params: &Value,
        callback: F,
    ) -> Result<Vec<Value>, JsonRpcError>
    where
        F: FnOnce(&crate::document::Document, &AnalysisResult, Position) -> Value,
    {
        let params: TextDocumentPositionParams = deserialize(params, "textDocument position")?;
        let document = self.document(&params.text_document.uri).await?;
        let result = self.analysis(&document).await;
        Ok(vec![callback(&document, &result, params.position)])
    }

    async fn references(&self, params: &Value) -> Result<Vec<Value>, JsonRpcError> {
        let params: ReferenceParams = deserialize(params, "references")?;
        let document = self.document(&params.position.text_document.uri).await?;
        Ok(vec![Value::Array(analysis::references(
            &document,
            params.position.position,
            params.context.include_declaration,
        ))])
    }

    async fn rename(&self, params: &Value) -> Result<Vec<Value>, JsonRpcError> {
        let params: RenameParams = deserialize(params, "rename")?;
        let document = self.document(&params.position.text_document.uri).await?;
        Ok(vec![analysis::rename(
            &document,
            params.position.position,
            &params.new_name,
        )
        .map_err(JsonRpcError::invalid_params)?])
    }

    async fn document_symbols(&self, params: &Value) -> Result<Vec<Value>, JsonRpcError> {
        let params: DocumentOnlyParams = deserialize(params, "documentSymbol")?;
        let document = self.document(&params.text_document.uri).await?;
        let result = self.analysis(&document).await;
        Ok(vec![Value::Array(analysis::document_symbols(&result))])
    }

    async fn semantic_tokens(&self, params: &Value) -> Result<Vec<Value>, JsonRpcError> {
        let params: DocumentOnlyParams = deserialize(params, "semanticTokens")?;
        let document = self.document(&params.text_document.uri).await?;
        let result = self.analysis(&document).await;
        Ok(vec![
            json!({"data": analysis::semantic_tokens(&document.text, &result)}),
        ])
    }

    async fn folding_ranges(&self, params: &Value) -> Result<Vec<Value>, JsonRpcError> {
        let params: DocumentOnlyParams = deserialize(params, "foldingRange")?;
        let document = self.document(&params.text_document.uri).await?;
        Ok(vec![Value::Array(analysis::folding_ranges(&document.text))])
    }

    async fn formatting(&self, params: &Value) -> Result<Vec<Value>, JsonRpcError> {
        let params: DocumentOnlyParams = deserialize(params, "formatting")?;
        let document = self.document(&params.text_document.uri).await?;
        let formatted = format_cloudflow(&document.text);
        if formatted == document.text {
            return Ok(vec![Value::Array(Vec::new())]);
        }
        let last_line = document.text.lines().count().saturating_sub(1) as u32;
        let last_character = document
            .text
            .lines()
            .last()
            .map(|line| line.encode_utf16().count() as u32)
            .unwrap_or(0);
        Ok(vec![
            json!([{"range": {"start": {"line": 0, "character": 0}, "end": {"line": last_line, "character": last_character}}, "newText": formatted}]),
        ])
    }

    async fn capabilities_changed(&self) -> Result<Vec<Value>, JsonRpcError> {
        let auth = self.auth.read().await.clone();
        self.server.capabilities.invalidate(Some(&auth)).await;
        let documents = self
            .documents
            .read()
            .await
            .all()
            .cloned()
            .collect::<Vec<_>>();
        let mut notifications = Vec::with_capacity(documents.len());
        for document in documents {
            // 版本未变不代表能力可见性不变；只失效本会话对应文档的分析缓存。
            self.analyses.remove(&document.uri).await;
            notifications.push(self.reanalyze_notification(&document).await);
        }
        Ok(notifications)
    }

    async fn execute_command(&self, params: &Value) -> Result<Vec<Value>, JsonRpcError> {
        let params: ExecuteCommandParams = deserialize(params, "executeCommand")?;
        if params.command != "cloudflow.clearCapabilityCache" {
            return Err(JsonRpcError::invalid_params("不支持的 CloudFlow 命令"));
        }
        let auth = self.auth.read().await.clone();
        self.server.capabilities.invalidate(Some(&auth)).await;
        Ok(vec![Value::Null])
    }

    async fn document(&self, uri: &str) -> Result<crate::document::Document, JsonRpcError> {
        self.documents
            .read()
            .await
            .get(uri)
            .ok_or_else(|| JsonRpcError::invalid_params(format!("文档尚未 didOpen：{uri}")))
    }

    async fn analysis(&self, document: &crate::document::Document) -> AnalysisResult {
        if let Some(result) = self.analyses.get(&document.uri, document.version).await {
            return result;
        }
        let auth = self.auth.read().await.clone();
        let result = analysis::analyze(document, &self.server.capabilities, &auth).await;
        self.analyses
            .put(document.uri.clone(), result.clone())
            .await;
        result
    }

    async fn reanalyze_notification(&self, document: &crate::document::Document) -> Value {
        let result = self.analysis(document).await;
        notification(
            "textDocument/publishDiagnostics",
            json!({
                "uri": document.uri, "version": document.version,
                "diagnostics": analysis::diagnostics_to_lsp(&document.text, &result.diagnostics),
            }),
        )
    }

    async fn require_initialized(&self) -> Result<(), JsonRpcError> {
        if *self.initialized.lock().await {
            Ok(())
        } else {
            Err(JsonRpcError::invalid_request("必须先调用 initialize"))
        }
    }

    async fn is_cancelled(&self, id: &Value) -> bool {
        self.cancelled.lock().await.remove(&id.to_string())
    }
}

fn deserialize<T: for<'a> Deserialize<'a>>(params: &Value, label: &str) -> Result<T, JsonRpcError> {
    serde_json::from_value(params.clone())
        .map_err(|error| JsonRpcError::invalid_params(format!("{label} 参数无效：{error}")))
}

/// A deterministic editor formatter. It only adjusts whitespace around braces and
/// never changes tokens, expressions or semantics; compiler parsing remains the
/// source of truth for every semantic conclusion.
fn format_cloudflow(source: &str) -> String {
    let mut depth = 0usize;
    source
        .lines()
        .map(|raw| {
            let line = raw.trim();
            if line.starts_with('}') {
                depth = depth.saturating_sub(1);
            }
            let formatted = if line.is_empty() {
                String::new()
            } else {
                format!("{}{}", "  ".repeat(depth), line)
            };
            if line.ends_with('{') {
                depth += 1;
            }
            formatted
        })
        .collect::<Vec<_>>()
        .join("\n")
        + if source.ends_with('\n') { "\n" } else { "" }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::capability::{CapabilityProvider, DEFAULT_CACHE_TTL};

    #[tokio::test]
    async fn initialize_and_open_publish_compiler_diagnostics() {
        let provider = CapabilityProvider::new("http://127.0.0.1:1", DEFAULT_CACHE_TTL).unwrap();
        let session = LanguageServer::new(provider).session(AuthContext::default());
        let initialize = JsonRpcRequest {
            jsonrpc: Some("2.0".into()),
            id: Some(json!(1)),
            method: "initialize".into(),
            params: json!({}),
        };
        assert!(session
            .handle(initialize)
            .await
            .last()
            .unwrap()
            .get("result")
            .is_some());
        let opened = JsonRpcRequest {
            jsonrpc: Some("2.0".into()),
            id: None,
            method: "textDocument/didOpen".into(),
            params: json!({"textDocument":{"uri":"file:///bad.flow","languageId":"cloudflow","version":1,"text":"workflow \"bad\" {"}}),
        };
        let output = session.handle(opened).await;
        assert_eq!(output[0]["method"], "textDocument/publishDiagnostics");
    }

    #[tokio::test]
    async fn capability_change_reanalyzes_only_open_documents_in_the_session() {
        let provider = CapabilityProvider::new("http://127.0.0.1:1", DEFAULT_CACHE_TTL).unwrap();
        let session = LanguageServer::new(provider).session(AuthContext::default());
        let _ = session
            .handle(JsonRpcRequest {
                jsonrpc: Some("2.0".into()),
                id: Some(json!(1)),
                method: "initialize".into(),
                params: json!({}),
            })
            .await;
        let _ = session
            .handle(JsonRpcRequest {
                jsonrpc: Some("2.0".into()),
                id: None,
                method: "textDocument/didOpen".into(),
                params: json!({"textDocument":{"uri":"file:///refresh.flow","languageId":"cloudflow","version":1,"text":"workflow \"refresh\" {"}}),
            })
            .await;

        let output = session
            .handle(JsonRpcRequest {
                jsonrpc: Some("2.0".into()),
                id: None,
                method: "cloudflow/capabilitiesChanged".into(),
                params: json!({}),
            })
            .await;

        assert_eq!(output.len(), 1);
        assert_eq!(output[0]["method"], "textDocument/publishDiagnostics");
        assert_eq!(output[0]["params"]["uri"], "file:///refresh.flow");
    }
}
