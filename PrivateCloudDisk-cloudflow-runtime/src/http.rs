//! CloudFlow Runtime 的 Axum HTTP 适配层。
//!
//! [CLOUDFLOW-HTTP-001] HTTP/JSON/请求限制均委托成熟生态库；编译入口保持内部令牌边界。

use axum::{
    extract::rejection::JsonRejection,
    extract::DefaultBodyLimit,
    extract::State,
    http::{
        header::{AUTHORIZATION, CONTENT_TYPE},
        HeaderMap, HeaderValue, Method, StatusCode,
    },
    response::IntoResponse,
    routing::{get, post},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, sync::Arc, time::Duration};
use tokio::sync::RwLock;
use tower::limit::ConcurrencyLimitLayer;
use tower_http::{cors::CorsLayer, timeout::TimeoutLayer, trace::TraceLayer};
use tracing::error;

use crate::execution::ExecutionCoordinator;
use crate::{
    compile_source_named, compiler::validate_ir, diagnostic::Diagnostic, ir::WorkflowIrV1,
    persistence::CreateExecution, semantic::InMemoryCapabilityCatalog,
};

pub const MAX_COMPILE_BODY_BYTES: usize = 1024 * 1024;
pub const COMPILER_VERSION: &str = env!("CARGO_PKG_VERSION");

#[derive(Debug, Clone)]
pub struct HttpConfig {
    pub service_token: String,
    pub capabilities: Vec<String>,
    pub max_concurrency: usize,
    pub allowed_origins: Vec<String>,
}

impl Default for HttpConfig {
    fn default() -> Self {
        Self {
            service_token: String::new(),
            capabilities: vec![],
            max_concurrency: 32,
            allowed_origins: vec![],
        }
    }
}

#[derive(Clone)]
struct AppState {
    token: String,
    capabilities: Vec<String>,
    executions: Arc<RwLock<HashMap<String, ExecutionRecord>>>,
    coordinator: Option<ExecutionCoordinator>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileRequest {
    pub source: String,
    #[serde(default)]
    pub filename: String,
    #[serde(default, alias = "target_ir_version")]
    pub target_ir_version: Option<String>,
    #[serde(default, rename = "userId")]
    _user_id: Option<String>,
    #[serde(default, rename = "spaceId")]
    _space_id: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileResponse {
    pub valid: bool,
    pub ir: Option<WorkflowIrV1>,
    pub diagnostics: Vec<Diagnostic>,
    pub compiler_version: &'static str,
    pub target_ir_version: &'static str,
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
    logs: Vec<ExecutionLog>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct ExecutionLog {
    sequence: u64,
    level: String,
    message: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct StartExecutionRequest {
    ir: WorkflowIrV1,
    #[serde(default)]
    execution_id: Option<String>,
    #[serde(default)]
    variables: serde_json::Value,
    #[serde(default)]
    workflow_id: String,
    #[serde(default)]
    user_id: String,
    #[serde(default)]
    space_id: Option<String>,
    #[serde(default)]
    declared_permissions: Vec<String>,
    #[serde(default)]
    granted_permissions: Vec<String>,
    #[serde(default)]
    trace_id: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct StartExecutionResponse {
    execution_id: String,
    status: ExecutionStatus,
}

/// [CLOUDFLOW-WAIT-001] resume 只接受 JSON 审批结果；服务令牌、空间授权仍在 Runtime
/// 上游控制面完成，HTTP 层不得接收任意代码或回调 URL。
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ResumeExecutionRequest {
    #[serde(default)]
    approval: serde_json::Value,
}

pub fn build_router(config: HttpConfig) -> Router {
    build_router_with_coordinator(config, None)
}

/// 生产入口注入持久化协调器；单元测试和 IDE 编译模式继续使用内存控制适配。
pub fn build_router_with_coordinator(
    config: HttpConfig,
    coordinator: Option<ExecutionCoordinator>,
) -> Router {
    let state = AppState {
        token: config.service_token,
        capabilities: config.capabilities,
        executions: Arc::new(RwLock::new(HashMap::new())),
        coordinator,
    };
    let mut app = Router::new()
        .route("/health", get(health))
        .route("/health/live", get(health))
        .route("/health/ready", get(health_ready))
        .route("/api/v1/compile", post(compile_handler))
        .route("/api/v1/executions", post(start_execution))
        .route("/api/v1/executions/:execution_id", get(get_execution))
        .route(
            "/api/v1/executions/:execution_id/pause",
            post(pause_execution),
        )
        .route(
            "/api/v1/executions/:execution_id/retry",
            post(retry_execution),
        )
        .route(
            "/api/v1/executions/:execution_id/resume",
            post(resume_execution),
        )
        .route(
            "/api/v1/executions/:execution_id/cancel",
            post(cancel_execution),
        )
        .route(
            "/api/v1/executions/:execution_id/logs",
            get(get_execution_logs),
        )
        // 旧路径保留为向后兼容别名；新 Workflow Service 只调用 /api/v1/compile。
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
        .route(
            "/internal/v1/cloudflow/executions/:execution_id/pause",
            post(pause_execution),
        )
        .route(
            "/internal/v1/cloudflow/executions/:execution_id/retry",
            post(retry_execution),
        )
        .route(
            "/internal/v1/cloudflow/executions/:execution_id/resume",
            post(resume_execution),
        )
        .route(
            "/internal/v1/cloudflow/executions/:execution_id/logs",
            get(get_execution_logs),
        )
        .with_state(state)
        // AUDIT FIX [6.8]：在反序列化前限制请求体并限制并发，避免超大 DSL 或并发洪峰耗尽 Runtime。
        // 使用 Axum extractor 级大小限制，超限时 JsonRejection 仍能返回 CF1104 结构化诊断。
        .layer(DefaultBodyLimit::max(MAX_COMPILE_BODY_BYTES))
        .layer(TimeoutLayer::with_status_code(
            StatusCode::REQUEST_TIMEOUT,
            Duration::from_secs(30),
        ))
        .layer(ConcurrencyLimitLayer::new(config.max_concurrency.max(1)))
        // TraceLayer 不记录 Header/Body，避免服务令牌或用户源码泄露到日志。
        .layer(TraceLayer::new_for_http());
    if !config.allowed_origins.is_empty() {
        let origins = config
            .allowed_origins
            .iter()
            .filter_map(|value| value.parse::<HeaderValue>().ok())
            .collect::<Vec<_>>();
        if !origins.is_empty() {
            app = app.layer(
                CorsLayer::new()
                    .allow_origin(origins)
                    .allow_methods([Method::POST, Method::GET])
                    .allow_headers([
                        CONTENT_TYPE,
                        AUTHORIZATION,
                        "x-pcd-service-token".parse().expect("static header"),
                    ]),
            );
        }
    }
    app
}

async fn health() -> Json<serde_json::Value> {
    Json(serde_json::json!({"status":"UP"}))
}
async fn health_ready(State(state): State<AppState>) -> impl IntoResponse {
    if let Some(coordinator) = &state.coordinator {
        if let Err(error) = coordinator.store().ping().await {
            error!(%error, "CloudFlow readiness 数据库探测失败");
            return (
                StatusCode::SERVICE_UNAVAILABLE,
                Json(serde_json::json!({
                    "status":"DOWN",
                    "component":"mysql"
                })),
            );
        }
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({
            "status":"UP",
            "compilerVersion":COMPILER_VERSION,
            "executionMode": if state.coordinator.is_some() { "persistent" } else { "compiler" }
        })),
    )
}

fn authorized(headers: &HeaderMap, state: &AppState) -> bool {
    !state.token.is_empty()
        && headers
            .get("X-PCD-Service-Token")
            .and_then(|value| value.to_str().ok())
            .is_some_and(|value| constant_time_eq(value.as_bytes(), state.token.as_bytes()))
}

fn constant_time_eq(left: &[u8], right: &[u8]) -> bool {
    if left.len() != right.len() {
        return false;
    }
    // AUDIT FIX [6.8]：内部服务凭证使用定长循环比较，避免直接字符串早停比较。
    left.iter()
        .zip(right)
        .fold(0_u8, |difference, (a, b)| difference | (a ^ b))
        == 0
}

async fn compile_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
    payload: Result<Json<CompileRequest>, JsonRejection>,
) -> impl IntoResponse {
    if !authorized(&headers, &state) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(error_response(vec![Diagnostic::new(
                "CF4001",
                "PERMISSION_ERROR",
                "缺少或无效的内部服务令牌",
                "",
                "<http>",
                0,
                1,
                vec![],
                Some("Workflow Service 必须使用 X-PCD-Service-Token 调用".into()),
            )])),
        );
    }
    let request = match payload {
        Ok(Json(request)) => request,
        Err(rejection) => {
            let status = rejection.status();
            let too_large = status == StatusCode::PAYLOAD_TOO_LARGE;
            return (
                if too_large {
                    StatusCode::PAYLOAD_TOO_LARGE
                } else {
                    StatusCode::BAD_REQUEST
                },
                Json(error_response(vec![Diagnostic::new(
                    if too_large { "CF1104" } else { "CF1101" },
                    "REQUEST_ERROR",
                    if too_large {
                        "编译请求体超过 1 MiB 上限"
                    } else {
                        "编译请求 JSON 无法解析"
                    },
                    "",
                    "<http>",
                    0,
                    1,
                    vec!["检查 Content-Type 和 JSON 转义".into()],
                    Some(rejection.body_text()),
                )])),
            );
        }
    };
    if request
        .target_ir_version
        .as_deref()
        .is_some_and(|value| !matches!(value, "v1" | "workflow.cloudflow.io/v1"))
    {
        return (
            StatusCode::UNPROCESSABLE_ENTITY,
            Json(error_response(vec![Diagnostic::new(
                "CF1301",
                "IR_VERSION_ERROR",
                format!(
                    "不支持的 IR target：{}",
                    request.target_ir_version.as_deref().unwrap_or_default()
                ),
                &request.source,
                filename(&request),
                0,
                1,
                vec!["v1".into()],
                None,
            )])),
        );
    }
    let mut catalog = InMemoryCapabilityCatalog::default();
    if !state.capabilities.is_empty() {
        catalog.insert("__catalog_enabled__");
        for capability in &state.capabilities {
            catalog.insert(capability);
        }
    }
    match compile_source_named(&request.source, filename(&request), &catalog) {
        Ok(ir) => (
            StatusCode::OK,
            Json(CompileResponse {
                valid: true,
                ir: Some(ir),
                diagnostics: vec![],
                compiler_version: COMPILER_VERSION,
                target_ir_version: "workflow.cloudflow.io/v1",
            }),
        ),
        Err(error) => (
            StatusCode::UNPROCESSABLE_ENTITY,
            Json(error_response(error.diagnostics)),
        ),
    }
}

fn filename(request: &CompileRequest) -> &str {
    if request.filename.is_empty() {
        "<request>"
    } else {
        &request.filename
    }
}
fn error_response(diagnostics: Vec<Diagnostic>) -> CompileResponse {
    CompileResponse {
        valid: false,
        ir: None,
        diagnostics,
        compiler_version: COMPILER_VERSION,
        target_ir_version: "workflow.cloudflow.io/v1",
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
    let execution_id = request
        .execution_id
        .filter(|value| valid_execution_id(value))
        .unwrap_or_else(|| unique_execution_id(&request.ir));
    if let Some(coordinator) = &state.coordinator {
        if request.user_id.trim().is_empty() {
            return (
                StatusCode::BAD_REQUEST,
                Json(serde_json::json!({"code":"CF4001","message":"生产执行必须携带 userId"})),
            );
        }
        let command = CreateExecution {
            execution_id: execution_id.clone(),
            workflow_id: if request.workflow_id.is_empty() {
                request
                    .ir
                    .metadata
                    .id
                    .clone()
                    .unwrap_or_else(|| request.ir.metadata.name.clone())
            } else {
                request.workflow_id
            },
            user_id: request.user_id,
            space_id: request.space_id,
            ir: request.ir,
            variables: request.variables,
            declared_permissions: request.declared_permissions,
            granted_permissions: request.granted_permissions,
            trace_id: if request.trace_id.is_empty() {
                uuid::Uuid::new_v4().simple().to_string()
            } else {
                request.trace_id
            },
        };
        return match coordinator.submit(command).await {
            Ok(true) => (
                StatusCode::ACCEPTED,
                Json(serde_json::json!({"executionId":execution_id,"status":"READY"})),
            ),
            Ok(false) => (
                StatusCode::OK,
                Json(serde_json::json!({"executionId":execution_id,"status":"DUPLICATE"})),
            ),
            Err(error) => {
                error!(%error, execution_id=%execution_id, "CloudFlow 执行提交失败");
                let (status, code, message) = match &error {
                    crate::execution::RuntimeExecutionError::Variable(_) => (
                        StatusCode::UNPROCESSABLE_ENTITY,
                        "CF2101",
                        error.public_message(),
                    ),
                    _ => (
                        StatusCode::SERVICE_UNAVAILABLE,
                        "CF-RUNTIME-SUBMIT",
                        "CloudFlow 执行提交暂时不可用".to_owned(),
                    ),
                };
                (
                    status,
                    Json(serde_json::json!({"code":code,"message":message})),
                )
            }
        };
    }
    let record = ExecutionRecord {
        execution_id: execution_id.clone(),
        status: ExecutionStatus::Ready,
        error: None,
        logs: vec![ExecutionLog {
            sequence: 1,
            level: "INFO".into(),
            message: "Workflow IR 已通过校验，执行实例进入 READY".into(),
        }],
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
    if let Some(coordinator) = &state.coordinator {
        return match coordinator.store().get_execution(&execution_id).await {
            Ok(Some(record)) => (StatusCode::OK, Json(serde_json::json!(record))),
            Ok(None) => (
                StatusCode::NOT_FOUND,
                Json(serde_json::json!({"code":"CF4040"})),
            ),
            Err(error) => {
                error!(%error, %execution_id, "CloudFlow 执行状态查询失败");
                service_unavailable()
            }
        };
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
    if let Some(coordinator) = &state.coordinator {
        return persistent_transition(coordinator.store().request_cancel(&execution_id).await);
    }
    let mut executions = state.executions.write().await;
    if let Some(record) = executions.get_mut(&execution_id) {
        record.status = ExecutionStatus::Cancelled;
        append_log(record, "INFO", "执行已取消");
        return (StatusCode::OK, Json(serde_json::json!(record)));
    }
    (
        StatusCode::NOT_FOUND,
        Json(serde_json::json!({"code":"CF4040"})),
    )
}

async fn pause_execution(
    State(state): State<AppState>,
    headers: HeaderMap,
    axum::extract::Path(execution_id): axum::extract::Path<String>,
) -> impl IntoResponse {
    transition_execution(
        state,
        headers,
        execution_id,
        ExecutionStatus::Waiting,
        "执行已暂停并进入 WAITING",
        |status| matches!(status, ExecutionStatus::Ready | ExecutionStatus::Running),
    )
    .await
}

async fn retry_execution(
    State(state): State<AppState>,
    headers: HeaderMap,
    axum::extract::Path(execution_id): axum::extract::Path<String>,
) -> impl IntoResponse {
    transition_execution(
        state,
        headers,
        execution_id,
        ExecutionStatus::Ready,
        "失败或取消实例已请求重试",
        |status| matches!(status, ExecutionStatus::Failed | ExecutionStatus::Cancelled),
    )
    .await
}

async fn resume_execution(
    State(state): State<AppState>,
    headers: HeaderMap,
    axum::extract::Path(execution_id): axum::extract::Path<String>,
    Json(request): Json<ResumeExecutionRequest>,
) -> impl IntoResponse {
    if !authorized(&headers, &state) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({"code":"AUTH-UNAUTHENTICATED"})),
        );
    }
    if let Some(coordinator) = &state.coordinator {
        return persistent_transition(
            coordinator
                .store()
                .resume_execution(&execution_id, &request.approval)
                .await,
        );
    }
    let mut executions = state.executions.write().await;
    let Some(record) = executions.get_mut(&execution_id) else {
        return (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({"code":"CF4040"})),
        );
    };
    if !matches!(record.status, ExecutionStatus::Waiting) {
        return (
            StatusCode::CONFLICT,
            Json(serde_json::json!({"code":"CF4100","message":"实例不在 WAITING_APPROVAL 状态"})),
        );
    }
    record.status = ExecutionStatus::Ready;
    append_log(
        record,
        "INFO",
        "已收到外部恢复信号，实例从 WAITING_APPROVAL 返回 READY",
    );
    (StatusCode::OK, Json(serde_json::json!(record)))
}

async fn transition_execution(
    state: AppState,
    headers: HeaderMap,
    execution_id: String,
    target: ExecutionStatus,
    message: &str,
    allowed: fn(&ExecutionStatus) -> bool,
) -> (StatusCode, Json<serde_json::Value>) {
    if !authorized(&headers, &state) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({"code":"AUTH-UNAUTHENTICATED"})),
        );
    }
    if let Some(coordinator) = &state.coordinator {
        let changed = match target {
            ExecutionStatus::Waiting => coordinator.store().request_pause(&execution_id).await,
            ExecutionStatus::Ready => coordinator.store().retry_execution(&execution_id).await,
            _ => Ok(false),
        };
        return persistent_transition(changed);
    }
    let mut executions = state.executions.write().await;
    let Some(record) = executions.get_mut(&execution_id) else {
        return (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({"code":"CF4040"})),
        );
    };
    if !allowed(&record.status) {
        return (
            StatusCode::CONFLICT,
            Json(serde_json::json!({
                "code":"CF4100",
                "message":"当前执行状态不允许该操作",
                "status":record.status
            })),
        );
    }
    record.status = target;
    append_log(record, "INFO", message);
    (StatusCode::OK, Json(serde_json::json!(record)))
}

async fn get_execution_logs(
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
    if let Some(coordinator) = &state.coordinator {
        return match coordinator.store().logs(&execution_id, 1000).await {
            Ok(logs) if logs.is_empty() => (
                StatusCode::NOT_FOUND,
                Json(serde_json::json!({"code":"CF4040"})),
            ),
            Ok(logs) => (
                StatusCode::OK,
                Json(serde_json::json!({"executionId": execution_id, "logs": logs})),
            ),
            Err(error) => {
                error!(%error, %execution_id, "CloudFlow 执行日志查询失败");
                service_unavailable()
            }
        };
    }
    match state.executions.read().await.get(&execution_id) {
        Some(record) => (
            StatusCode::OK,
            Json(serde_json::json!({"executionId": execution_id, "logs": record.logs})),
        ),
        None => (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({"code":"CF4040"})),
        ),
    }
}

fn append_log(record: &mut ExecutionRecord, level: &str, message: &str) {
    record.logs.push(ExecutionLog {
        sequence: record.logs.len() as u64 + 1,
        level: level.into(),
        message: message.into(),
    });
}

fn valid_execution_id(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 128
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_'))
}

fn unique_execution_id(ir: &WorkflowIrV1) -> String {
    let _ = ir;
    uuid::Uuid::new_v4().to_string()
}

fn persistent_transition(
    changed: Result<bool, sqlx::Error>,
) -> (StatusCode, Json<serde_json::Value>) {
    match changed {
        Ok(true) => (
            StatusCode::OK,
            Json(serde_json::json!({"status":"ACCEPTED"})),
        ),
        Ok(false) => (
            StatusCode::CONFLICT,
            Json(serde_json::json!({"code":"CF4100"})),
        ),
        Err(error) => {
            error!(%error, "CloudFlow 执行状态变更失败");
            service_unavailable()
        }
    }
}

fn service_unavailable() -> (StatusCode, Json<serde_json::Value>) {
    // AUDIT FIX [2.9]：数据库/驱动错误仅进入结构化服务日志，内部路径、表名和连接信息不回传调用方。
    (
        StatusCode::SERVICE_UNAVAILABLE,
        Json(serde_json::json!({
            "code":"CF6001",
            "message":"CloudFlow Runtime 暂时不可用，请稍后重试"
        })),
    )
}
