//! CloudFlow Runtime HTTP 适配与最小执行状态 API。
//!
//! HTTP、JSON、请求体上限和超时均交给 Tokio/Axum/serde/tower-http，避免手写协议解析。

use axum::{
    extract::State,
    http::{HeaderMap, StatusCode},
    response::IntoResponse,
    routing::{get, post},
    Json, Router,
};
use cloudflow_runtime::{
    compile_source_named, compiler::validate_ir, diagnostic::Diagnostic, ir::WorkflowIrV1,
    semantic::InMemoryCapabilityCatalog,
};
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, env, sync::Arc};
use tokio::sync::RwLock;
use tower_http::{limit::RequestBodyLimitLayer, timeout::TimeoutLayer, trace::TraceLayer};

const MAX_BODY: usize = 2 * 1024 * 1024;

#[derive(Clone)]
struct AppState {
    token: String,
    capabilities: Vec<String>,
    executions: Arc<RwLock<HashMap<String, ExecutionRecord>>>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CompileRequest {
    source: String,
    #[serde(default)]
    filename: String,
    #[serde(default)]
    #[serde(rename = "userId")]
    _user_id: Option<String>,
    #[serde(default)]
    #[serde(rename = "spaceId")]
    _space_id: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct CompileResponse {
    valid: bool,
    ir: Option<WorkflowIrV1>,
    diagnostics: Vec<Diagnostic>,
    compiler_version: &'static str,
}

#[derive(Debug, Deserialize)]
struct IrRequest {
    ir: WorkflowIrV1,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
enum ExecutionStatus {
    Created,
    Ready,
    Running,
    Waiting,
    Success,
    Failed,
    Cancelled,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct ExecutionRecord {
    execution_id: String,
    status: ExecutionStatus,
    error: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct StartExecutionRequest {
    ir: WorkflowIrV1,
    #[serde(default)]
    #[serde(rename = "variables")]
    _variables: serde_json::Value,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct StartExecutionResponse {
    execution_id: String,
    status: ExecutionStatus,
}

#[tokio::main]
async fn main() {
    if env::args().any(|arg| arg == "--healthcheck") {
        println!("ok");
        return;
    }
    let address = env::var("CLOUDFLOW_LISTEN_ADDRESS").unwrap_or_else(|_| "127.0.0.1:8091".into());
    let token = env::var("PCD_INTERNAL_SERVICE_TOKEN").unwrap_or_default();
    let capabilities = env::var("CLOUDFLOW_CAPABILITIES")
        .unwrap_or_default()
        .split(',')
        .map(str::trim)
        .filter(|v| !v.is_empty())
        .map(str::to_owned)
        .collect();
    let state = AppState {
        token,
        capabilities,
        executions: Arc::new(RwLock::new(HashMap::new())),
    };
    let app = Router::new()
        .route(
            "/health/live",
            get(|| async { Json(serde_json::json!({"status":"UP"})) }),
        )
        .route(
            "/health/ready",
            get(|| async { Json(serde_json::json!({"status":"UP","compilerVersion":"0.2.0"})) }),
        )
        .route("/internal/v1/cloudflow/compile", post(compile_handler))
        .route("/internal/v1/compile", post(compile_handler))
        .route(
            "/internal/v1/cloudflow/validate-ir",
            post(validate_ir_handler),
        )
        .route("/internal/v1/cloudflow/executions", post(start_execution))
        .route(
            "/internal/v1/cloudflow/executions/:execution_id",
            get(get_execution),
        )
        .route(
            "/internal/v1/cloudflow/executions/:execution_id/cancel",
            post(cancel_execution),
        )
        .with_state(state)
        .layer(RequestBodyLimitLayer::new(MAX_BODY))
        .layer(TimeoutLayer::with_status_code(
            StatusCode::REQUEST_TIMEOUT,
            std::time::Duration::from_secs(30),
        ))
        // TraceLayer 不记录 Header/Body，避免服务令牌或用户源码泄露到日志。
        .layer(TraceLayer::new_for_http());
    let listener = match tokio::net::TcpListener::bind(&address).await {
        Ok(value) => value,
        Err(error) => {
            eprintln!("CloudFlow Runtime 无法绑定监听地址: {error}");
            return;
        }
    };
    println!("pcd-cloudflow-runtime listening on {address}");
    if let Err(error) = axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
    {
        eprintln!("CloudFlow Runtime 已停止: {error}");
    }
}

async fn shutdown_signal() {
    let _ = tokio::signal::ctrl_c().await;
}

fn authorized(headers: &HeaderMap, state: &AppState) -> bool {
    if state.token.is_empty() {
        return false;
    }
    headers
        .get("X-PCD-Service-Token")
        .and_then(|v| v.to_str().ok())
        .map(|v| v == state.token)
        .unwrap_or(false)
}

async fn compile_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(request): Json<CompileRequest>,
) -> impl IntoResponse {
    if !authorized(&headers, &state) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(CompileResponse {
                valid: false,
                ir: None,
                diagnostics: vec![],
                compiler_version: "0.2.0",
            }),
        );
    }
    let filename = if request.filename.is_empty() {
        "<request>"
    } else {
        request.filename.as_str()
    };
    let mut catalog = InMemoryCapabilityCatalog::default();
    // 只有显式配置能力目录时才启用强校验，避免 IDE 在尚未同步 Capability Hub 时误拒绝。
    if !state.capabilities.is_empty() {
        catalog.insert("__catalog_enabled__");
        for capability in &state.capabilities {
            catalog.insert(capability);
        }
    }
    match compile_source_named(&request.source, filename, &catalog) {
        Ok(ir) => (
            StatusCode::OK,
            Json(CompileResponse {
                valid: true,
                ir: Some(ir),
                diagnostics: vec![],
                compiler_version: "0.2.0",
            }),
        ),
        Err(error) => (
            StatusCode::UNPROCESSABLE_ENTITY,
            Json(CompileResponse {
                valid: false,
                ir: None,
                diagnostics: error.diagnostics,
                compiler_version: "0.2.0",
            }),
        ),
    }
}

async fn validate_ir_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(request): Json<IrRequest>,
) -> impl IntoResponse {
    if !authorized(&headers, &state) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({"valid": false, "diagnostics": []})),
        );
    }
    let errors = validate_ir(&request.ir);
    let status = if errors.is_empty() {
        StatusCode::OK
    } else {
        StatusCode::UNPROCESSABLE_ENTITY
    };
    (
        status,
        Json(serde_json::json!({"valid": errors.is_empty(), "errors": errors})),
    )
}

async fn start_execution(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(request): Json<StartExecutionRequest>,
) -> impl IntoResponse {
    if !authorized(&headers, &state) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({"code":"AUTH-UNAUTHENTICATED"})),
        );
    }
    let errors = validate_ir(&request.ir);
    if !errors.is_empty() {
        return (
            StatusCode::UNPROCESSABLE_ENTITY,
            Json(serde_json::json!({"code":"CF1301","errors":errors})),
        );
    }
    let execution_id = format!("cf-{}", uuid_like_id(&request.ir));
    let record = ExecutionRecord {
        execution_id: execution_id.clone(),
        status: ExecutionStatus::Ready,
        error: None,
    };
    state
        .executions
        .write()
        .await
        .insert(execution_id.clone(), record.clone());
    (
        StatusCode::ACCEPTED,
        Json(serde_json::json!(StartExecutionResponse {
            execution_id,
            status: record.status
        })),
    )
}

async fn get_execution(
    State(state): State<AppState>,
    headers: HeaderMap,
    axum::extract::Path(execution_id): axum::extract::Path<String>,
) -> impl IntoResponse {
    if !authorized(&headers, &state) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({"code":"AUTH-UNAUTHENTICATED"})),
        );
    }
    match state.executions.read().await.get(&execution_id).cloned() {
        Some(record) => (StatusCode::OK, Json(serde_json::json!(record))),
        None => (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({"code":"CF4040"})),
        ),
    }
}

async fn cancel_execution(
    State(state): State<AppState>,
    headers: HeaderMap,
    axum::extract::Path(execution_id): axum::extract::Path<String>,
) -> impl IntoResponse {
    if !authorized(&headers, &state) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({"code":"AUTH-UNAUTHENTICATED"})),
        );
    }
    let mut executions = state.executions.write().await;
    if let Some(record) = executions.get_mut(&execution_id) {
        record.status = ExecutionStatus::Cancelled;
        return (StatusCode::OK, Json(serde_json::json!(record)));
    }
    (
        StatusCode::NOT_FOUND,
        Json(serde_json::json!({"code":"CF4040"})),
    )
}

fn uuid_like_id(ir: &WorkflowIrV1) -> String {
    use sha2::{Digest, Sha256};
    let bytes = serde_json::to_vec(ir).unwrap_or_default();
    let mut hash = Sha256::new();
    hash.update(bytes);
    format!("{:x}", hash.finalize())[..24].to_owned()
}
