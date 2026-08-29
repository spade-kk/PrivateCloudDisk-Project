//! `workflow.cloudflow.io/v1` 稳定 Workflow IR。

use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::BTreeMap;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct WorkflowIrV1 {
    #[serde(rename = "apiVersion")]
    pub api_version: String,
    pub kind: String,
    pub metadata: MetadataIr,
    pub spec: SpecIr,
    pub runtime: RuntimeIr,
    pub security: SecurityIr,
    pub extensions: BTreeMap<String, Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
#[serde(rename_all = "camelCase")]
pub struct MetadataIr {
    pub id: Option<String>,
    pub name: String,
    pub display_name: Option<String>,
    pub description: Option<String>,
    pub version: Option<String>,
    pub owner: Option<String>,
    pub labels: BTreeMap<String, String>,
    /// [V1.2-NAMESPACE] 工作流命名空间。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub namespace: Option<String>,
    /// [V1.2-METADATA] 版本变更记录。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub changelog: Option<String>,
    /// [V1.2-TAG] 工作流分类标签。
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub tags: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
#[serde(rename_all = "camelCase")]
pub struct SpecIr {
    pub trigger: TriggerIr,
    pub variables: BTreeMap<String, VariableIr>,
    pub graph: GraphIr,
    pub outputs: BTreeMap<String, Value>,
    /// [V1.2-ENVIRONMENT] 环境变量声明，与 variables 相互独立。
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub environment: BTreeMap<String, Value>,
    /// [V1.2-AUDIT] 工作流级审计注解。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub audit: Option<AuditIr>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum TriggerIr {
    Manual,
    Schedule {
        cron: String,
        timezone: Option<String>,
    },
    Event {
        event: String,
    },
    Http {
        path: String,
        /// [V1.2-WEBHOOK] HTTP 触发允许的请求方法；缺省为 POST。
        #[serde(default, skip_serializing_if = "Option::is_none")]
        method: Option<String>,
    },
    Interval {
        every: String,
    },
}

/// [V1.2-AUDIT] 审计注解 IR。
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct AuditIr {
    pub level: String,
    pub description: Option<String>,
}

impl Default for TriggerIr {
    fn default() -> Self {
        Self::Manual
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
#[serde(rename_all = "camelCase")]
pub struct VariableIr {
    #[serde(rename = "type")]
    pub type_name: String,
    pub required: bool,
    /// input/local/deferred；Runtime 据此决定调用方是否可以提供覆盖值。
    #[serde(default = "default_variable_source")]
    pub source: String,
    pub default: Option<Value>,
    /// 本地初始化值或可恢复的受控变量初始值；保留 JSON 原生类型、$ref/$expr。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub value: Option<Value>,
}

fn default_variable_source() -> String {
    // 兼容已经持久化的 V1 IR：旧变量一律按 input/default 行为解析。
    "input".into()
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
#[serde(rename_all = "camelCase")]
pub struct GraphIr {
    pub nodes: Vec<NodeIr>,
    pub edges: Vec<EdgeIr>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct EdgeIr {
    pub from: String,
    pub to: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
#[serde(rename_all = "camelCase")]
pub struct NodeIr {
    pub id: String,
    #[serde(rename = "type")]
    pub node_type: String,
    pub name: Option<String>,
    pub action: Option<ActionIr>,
    pub inputs: BTreeMap<String, Value>,
    pub outputs: BTreeMap<String, Value>,
    pub depends_on: Vec<String>,
    pub retry: Option<RetryIr>,
    /// [V1.2-RETRY_ON] 可重试异常类型白名单。
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub retry_on: Vec<String>,
    pub timeout: Option<String>,
    /// [V1.2-TIMEOUT-BLOCK] 超时后行为（fail/continue/retry）。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub on_timeout: Option<String>,
    pub condition: Option<Value>,
    /// [V1.2-COND-DEPENDS] 条件依赖表达式：求值为 false 时节点无需等待静态依赖完成。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub depends_condition: Option<Value>,
    /// [V1.2-SWITCH] switch 控制配置（subject + cases + default）。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub switch_config: Option<Value>,
    /// [V1.2-DELAY] 固定延迟毫秒数。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub delay_ms: Option<u64>,
    /// [V1.2-NOTIFY] 内建通知配置（channel/recipient/message）。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub notify_config: Option<Value>,
    /// [V1.2-ON_ERROR] 步骤级错误处理子节点 ID 列表。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub on_error: Option<Value>,
    pub loop_config: Option<Value>,
    pub parallel: Option<Value>,
    pub error_handler: Option<Value>,
    /// 控制节点所属关系。静态调度器据此避免将 foreach/try 的内部节点当作普通 DAG 节点重复执行。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub control_parent: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub control_branch: Option<String>,
    /// 控制节点/step 内嵌节点的直接子节点；用于 Runtime 分支跳过和恢复，不改变 edges 契约。
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub children: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
#[serde(rename_all = "camelCase")]
pub struct ActionIr {
    pub provider: String,
    pub service: Option<String>,
    pub method: Option<String>,
    pub plugin_id: Option<String>,
    pub function: Option<String>,
    pub version: Option<String>,
    pub arguments: Value,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct RetryIr {
    pub max_attempts: u32,
    pub strategy: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeIr {
    pub timeout_seconds: Option<u64>,
    pub max_parallel: Option<u32>,
    pub retry_policy: Option<RetryIr>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
#[serde(rename_all = "camelCase")]
pub struct SecurityIr {
    pub permissions: Vec<String>,
    pub resource_limits: BTreeMap<String, Value>,
}
