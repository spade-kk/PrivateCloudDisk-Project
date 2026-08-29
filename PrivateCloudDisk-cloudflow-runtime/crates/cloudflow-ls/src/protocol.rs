//! JSON-RPC 2.0 / LSP wire primitives.
//!
//! [CLOUDFLOW-LS-PROTOCOL-001] We intentionally keep the transport independent
//! from the LSP methods. stdio/TCP/UDS use Content-Length frames while WebSocket
//! carries the exact same JSON values as text frames.

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

pub const JSONRPC_VERSION: &str = "2.0";
pub const MAX_MESSAGE_BYTES: usize = 1_048_576;

#[derive(Debug, Clone, Copy, Default, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Position {
    pub line: u32,
    pub character: u32,
}

#[derive(Debug, Clone, Copy, Default, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Range {
    pub start: Position,
    pub end: Position,
}

#[derive(Debug, Clone, Deserialize)]
pub struct JsonRpcRequest {
    pub jsonrpc: Option<String>,
    pub id: Option<Value>,
    pub method: String,
    #[serde(default)]
    pub params: Value,
}

impl JsonRpcRequest {
    pub fn is_notification(&self) -> bool {
        self.id.is_none()
    }

    pub fn validate(&self) -> Result<(), JsonRpcError> {
        if self.jsonrpc.as_deref() != Some(JSONRPC_VERSION) {
            return Err(JsonRpcError::invalid_request("jsonrpc 必须为 2.0"));
        }
        if self.method.trim().is_empty() {
            return Err(JsonRpcError::invalid_request("method 不能为空"));
        }
        Ok(())
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct JsonRpcError {
    pub code: i64,
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<Value>,
}

impl JsonRpcError {
    pub fn parse(message: impl Into<String>) -> Self {
        Self {
            code: -32700,
            message: message.into(),
            data: None,
        }
    }

    pub fn invalid_request(message: impl Into<String>) -> Self {
        Self {
            code: -32600,
            message: message.into(),
            data: None,
        }
    }

    pub fn method_not_found(method: &str) -> Self {
        Self {
            code: -32601,
            message: format!("不支持的 LSP 方法：{method}"),
            data: None,
        }
    }

    pub fn invalid_params(message: impl Into<String>) -> Self {
        Self {
            code: -32602,
            message: message.into(),
            data: None,
        }
    }

    pub fn internal(message: impl Into<String>) -> Self {
        Self {
            code: -32603,
            message: message.into(),
            data: None,
        }
    }

    pub fn cancelled() -> Self {
        Self {
            code: -32800,
            message: "请求已取消".into(),
            data: None,
        }
    }
}

pub fn response(id: Value, result: Value) -> Value {
    json!({ "jsonrpc": JSONRPC_VERSION, "id": id, "result": result })
}

pub fn error_response(id: Option<Value>, error: JsonRpcError) -> Value {
    json!({ "jsonrpc": JSONRPC_VERSION, "id": id.unwrap_or(Value::Null), "error": error })
}

pub fn notification(method: &str, params: Value) -> Value {
    json!({ "jsonrpc": JSONRPC_VERSION, "method": method, "params": params })
}

pub fn parse_request(input: &[u8]) -> Result<JsonRpcRequest, JsonRpcError> {
    if input.len() > MAX_MESSAGE_BYTES {
        return Err(JsonRpcError::invalid_request("LSP 消息超过 1 MiB 限制"));
    }
    let request: JsonRpcRequest = serde_json::from_slice(input)
        .map_err(|error| JsonRpcError::parse(format!("JSON-RPC 解析失败：{error}")))?;
    request.validate()?;
    Ok(request)
}
