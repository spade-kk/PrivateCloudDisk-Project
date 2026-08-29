//! 受控 Capability Agent 适配器。
//!
//! [CLOUDFLOW-RUNTIME-AGENT-001] Runtime 不直接访问平台数据库/文件系统；builtin、api、plugin
//! 三类 action 均经 gRPC Agent 调用，并在发起调用前取“工作流声明权限 ∩ 当前授权权限”。

use crate::ir::ActionIr;
use async_trait::async_trait;
use serde_json::Value;
use std::{collections::HashSet, time::Duration};
use tonic::{metadata::MetadataValue, transport::Channel, Request};

pub mod proto {
    tonic::include_proto!("pcd.cloudflow.v1");
}

use proto::{
    capability_agent_client::CapabilityAgentClient, capability_agent_server::CapabilityAgent,
    InvokeCapabilityRequest, InvokeCapabilityResponse,
};

#[derive(Debug, Clone)]
pub struct AuthorizationContext {
    pub user_id: String,
    pub space_id: Option<String>,
    pub declared_permissions: HashSet<String>,
    pub granted_permissions: HashSet<String>,
}

impl AuthorizationContext {
    pub fn validate_least_privilege(&self) -> Result<(), AgentError> {
        let missing = self
            .declared_permissions
            .difference(&self.granted_permissions)
            .cloned()
            .collect::<Vec<_>>();
        if missing.is_empty() {
            Ok(())
        } else {
            Err(AgentError {
                code: "CF4002".into(),
                summary: format!("当前用户/空间缺少工作流声明权限：{}", missing.join(", ")),
                retryable: false,
            })
        }
    }
}

#[derive(Debug, Clone)]
pub struct AgentInvocation {
    pub execution_id: String,
    pub step_id: String,
    pub attempt: u32,
    pub action: ActionIr,
    pub input: Value,
    pub authorization: AuthorizationContext,
    pub trace_id: String,
}

#[derive(Debug, Clone)]
pub struct AgentOutput {
    pub value: Value,
}

#[derive(Debug, Clone, thiserror::Error)]
#[error("{code}: {summary}")]
pub struct AgentError {
    pub code: String,
    pub summary: String,
    pub retryable: bool,
}

#[async_trait]
pub trait CapabilityInvoker: Send + Sync {
    async fn invoke(&self, invocation: AgentInvocation) -> Result<AgentOutput, AgentError>;
}

#[derive(Clone)]
pub struct GrpcCapabilityInvoker {
    client: CapabilityAgentClient<Channel>,
    service_token: MetadataValue<tonic::metadata::Ascii>,
    timeout: Duration,
}

impl GrpcCapabilityInvoker {
    pub async fn connect(
        endpoint: &str,
        service_token: &str,
        timeout: Duration,
    ) -> Result<Self, tonic::transport::Error> {
        // connect_lazy 让 Runtime 与同进程启动的 Agent 服务解耦，首次调用仍受 timeout 控制。
        let channel = tonic::transport::Endpoint::new(endpoint.to_owned())?.connect_lazy();
        let client = CapabilityAgentClient::new(channel);
        // 服务令牌来自受控环境变量；非法 ASCII 值不能静默进入元数据。
        let service_token = MetadataValue::try_from(service_token)
            .expect("PCD_INTERNAL_SERVICE_TOKEN 必须是合法 ASCII");
        Ok(Self {
            client,
            service_token,
            timeout,
        })
    }
}

#[async_trait]
impl CapabilityInvoker for GrpcCapabilityInvoker {
    async fn invoke(&self, invocation: AgentInvocation) -> Result<AgentOutput, AgentError> {
        invocation.authorization.validate_least_privilege()?;
        let capability = capability_key(&invocation.action)?;
        let mut request = Request::new(InvokeCapabilityRequest {
            execution_id: invocation.execution_id.clone(),
            step_id: invocation.step_id.clone(),
            attempt: invocation.attempt,
            capability,
            input_json: serde_json::to_string(&invocation.input).map_err(|error| AgentError {
                code: "CF3102".into(),
                summary: format!("能力输入无法序列化：{error}"),
                retryable: false,
            })?,
            user_id: invocation.authorization.user_id,
            space_id: invocation.authorization.space_id.unwrap_or_default(),
            declared_permissions: sorted(invocation.authorization.declared_permissions),
            granted_permissions: sorted(invocation.authorization.granted_permissions),
            idempotency_key: format!(
                "{}:{}:{}",
                invocation.execution_id, invocation.step_id, invocation.attempt
            ),
            trace_id: invocation.trace_id,
        });
        request
            .metadata_mut()
            .insert("x-pcd-service-token", self.service_token.clone());
        let mut client = self.client.clone();
        let response = tokio::time::timeout(self.timeout, client.invoke(request))
            .await
            .map_err(|_| AgentError {
                code: "CF5001".into(),
                summary: "Capability Agent 调用超时".into(),
                retryable: true,
            })?
            .map_err(|error| AgentError {
                code: "CF5002".into(),
                summary: sanitize(&error.to_string()),
                retryable: matches!(
                    error.code(),
                    tonic::Code::Unavailable
                        | tonic::Code::DeadlineExceeded
                        | tonic::Code::ResourceExhausted
                        | tonic::Code::Aborted
                ),
            })?
            .into_inner();
        if !response.success {
            return Err(AgentError {
                code: if response.error_code.is_empty() {
                    "CF5003".into()
                } else {
                    response.error_code
                },
                summary: sanitize(&response.error_summary),
                retryable: response.retryable,
            });
        }
        let value = serde_json::from_str(&response.output_json).map_err(|error| AgentError {
            code: "CF5004".into(),
            summary: format!("Capability Agent 返回了无效 JSON：{error}"),
            retryable: false,
        })?;
        Ok(AgentOutput { value })
    }
}

/// gRPC Agent 服务端：把受控调用转交 Workflow Service Capability Hub 内部接口。
#[derive(Clone)]
pub struct CapabilityAgentProxy {
    client: reqwest::Client,
    workflow_capability_url: String,
    service_token: String,
}

impl CapabilityAgentProxy {
    pub fn new(workflow_capability_url: String, service_token: String, timeout: Duration) -> Self {
        let client = reqwest::Client::builder()
            .timeout(timeout)
            .pool_max_idle_per_host(16)
            .build()
            .expect("reqwest client configuration");
        Self {
            client,
            workflow_capability_url,
            service_token,
        }
    }
}

#[tonic::async_trait]
impl CapabilityAgent for CapabilityAgentProxy {
    async fn invoke(
        &self,
        request: Request<InvokeCapabilityRequest>,
    ) -> Result<tonic::Response<InvokeCapabilityResponse>, tonic::Status> {
        let presented = request
            .metadata()
            .get("x-pcd-service-token")
            .and_then(|value| value.to_str().ok())
            .unwrap_or_default();
        if !constant_time_eq(presented.as_bytes(), self.service_token.as_bytes()) {
            return Err(tonic::Status::unauthenticated("内部服务认证失败"));
        }
        let command = request.into_inner();
        let input = serde_json::from_str::<Value>(&command.input_json)
            .map_err(|_| tonic::Status::invalid_argument("input_json 无效"))?;
        let response = self
            .client
            .post(&self.workflow_capability_url)
            .header("X-PCD-Service-Token", &self.service_token)
            .json(&serde_json::json!({
                "capabilityKey": command.capability,
                "executionId": command.execution_id,
                "stepId": command.step_id,
                "attempt": command.attempt,
                "userId": command.user_id,
                "spaceId": if command.space_id.is_empty() { Value::Null } else { Value::String(command.space_id) },
                "input": input,
                "declaredPermissions": command.declared_permissions,
                "grantedPermissions": command.granted_permissions,
                "traceId": command.trace_id,
                "idempotencyKey": command.idempotency_key
            }))
            .send()
            .await
            .map_err(|error| tonic::Status::unavailable(sanitize(&error.to_string())))?;
        let status = response.status();
        let body = response
            .json::<Value>()
            .await
            .map_err(|_| tonic::Status::internal("Capability Hub 返回了无效 JSON"))?;
        if !status.is_success() {
            return Err(match status.as_u16() {
                401 => tonic::Status::unauthenticated("Capability Hub 内部认证失败"),
                403 => tonic::Status::permission_denied("Capability Hub 拒绝权限"),
                429 => tonic::Status::resource_exhausted("Capability Hub 限流"),
                _ if status.is_server_error() => {
                    tonic::Status::unavailable("Capability Hub 暂不可用")
                }
                _ => tonic::Status::failed_precondition("Capability Hub 拒绝调用"),
            });
        }
        let data = body.get("data").cloned().unwrap_or(Value::Null);
        let success = data
            .get("success")
            .and_then(Value::as_bool)
            .unwrap_or(false);
        let output = data
            .get("output")
            .cloned()
            .unwrap_or_else(|| serde_json::json!({}));
        Ok(tonic::Response::new(InvokeCapabilityResponse {
            success,
            output_json: serde_json::to_string(&output).unwrap_or_else(|_| "{}".into()),
            error_code: data
                .get("errorCode")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_owned(),
            error_summary: data
                .get("errorSummary")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_owned(),
            retryable: data
                .get("retryable")
                .and_then(Value::as_bool)
                .unwrap_or(false),
        }))
    }
}

pub fn capability_key(action: &ActionIr) -> Result<String, AgentError> {
    match action.provider.as_str() {
        "plugin" => {
            let plugin_id = action.plugin_id.as_deref().unwrap_or_default();
            let function = action.function.as_deref().unwrap_or_default();
            if plugin_id.is_empty() || function.is_empty() {
                return Err(invalid_action("plugin action 缺少 pluginId/function"));
            }
            Ok(format!("plugin:{plugin_id}:{function}"))
        }
        "builtin" | "api" => {
            let service = action.service.as_deref().unwrap_or_default();
            let method = action.method.as_deref().unwrap_or_default();
            if service.is_empty() || method.is_empty() {
                return Err(invalid_action("action 缺少 service/method"));
            }
            Ok(format!("{}:{service}.{method}", action.provider))
        }
        provider => Err(invalid_action(&format!("未知 action provider：{provider}"))),
    }
}

fn invalid_action(summary: &str) -> AgentError {
    AgentError {
        code: "CF3001".into(),
        summary: summary.into(),
        retryable: false,
    }
}

fn sorted(values: HashSet<String>) -> Vec<String> {
    let mut values = values.into_iter().collect::<Vec<_>>();
    values.sort();
    values
}

fn sanitize(value: &str) -> String {
    value
        .replace(['\r', '\n'], " ")
        .chars()
        .take(2000)
        .collect()
}

fn constant_time_eq(left: &[u8], right: &[u8]) -> bool {
    if left.len() != right.len() {
        return false;
    }
    left.iter()
        .zip(right)
        .fold(0_u8, |difference, (a, b)| difference | (a ^ b))
        == 0
}
