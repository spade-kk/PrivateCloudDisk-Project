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
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
#[serde(rename_all = "camelCase")]
pub struct SpecIr {
    pub trigger: TriggerIr,
    pub variables: BTreeMap<String, VariableIr>,
    pub graph: GraphIr,
    pub outputs: BTreeMap<String, Value>,
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
    },
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
    pub default: Option<Value>,
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
    pub timeout: Option<String>,
    pub condition: Option<String>,
    pub loop_config: Option<Value>,
    pub parallel: Option<Value>,
    pub error_handler: Option<Value>,
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
