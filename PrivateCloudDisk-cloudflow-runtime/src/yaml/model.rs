//! CloudFlow YAML 前端 —— 强类型模型（需求 8.3/8.4）。
//!
//! 顶层使用 serde derive 定义 `YamlDocument`；其中结构多变的部分（trigger、step、变量、env）
//! 保留为 `serde_yaml_ng::Value`，由 `convert.rs` 按规范解释，避免把 YAML 宽松形式压死成
//! 单一枚举。YAML 词法/解析由第三方库 `serde_yaml_ng` 完成（需求 7.x），本模块不实现解析器。

use serde::Deserialize;
use serde_yaml_ng::Value as YamlValue;
use std::collections::BTreeMap;

/// 一个 YAML 工作流文档（支持两种顶层形态）：
/// - 扁平形态（CLOUDFLOW_YAML_DEMO_DESIGN.md）：`trigger/input(s)/variables/env/steps/catch/finally`
///   与 `workflow: {name, version}` 平铺；
/// - 嵌套形态（CLOUDFLOW_YAML_DESIGN.md）：`workflow: {name, version, description, trigger,
///   inputs, variables, steps, outputs, policies}`。
///   `normalize_document` 会在反序列化前把嵌套形态的字段提升到顶层，统一为扁平模型。
///
/// CloudFlow YAML 只接受上述两种**本地**形态；旧版 `automation.pcd/v1` 包装
/// （`apiVersion/kind/metadata/spec/limits`、`uses/needs/result`）不再解析。
#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "lowercase")]
pub struct YamlDocument {
    pub workflow: Option<YamlWorkflowMeta>,
    pub trigger: Option<YamlValue>,
    /// `input:`（DEMO）与 `inputs:`（DESIGN）两种拼写都支持。
    pub input: Option<BTreeMap<String, YamlValue>>,
    pub inputs: Option<BTreeMap<String, YamlValue>>,
    pub variables: Option<BTreeMap<String, YamlValue>>,
    pub env: Option<BTreeMap<String, YamlValue>>,
    /// 运行时配置 `runtime: {timeout, max_parallel, retry}`。
    pub runtime: Option<YamlValue>,
    pub steps: Option<Vec<YamlValue>>,
    pub catch: Option<Vec<YamlValue>>,
    pub finally: Option<Vec<YamlValue>>,
    /// 工作流级输出声明（编译为 `spec.outputs`；当前 DSL 亦未使用，属共享扩展）。
    pub outputs: Option<BTreeMap<String, YamlValue>>,
    /// 运行时策略（安全/隔离/配额）；当前版本解析并留档，不作为已实现能力（预留）。
    #[allow(dead_code)]
    // 预留字段：仅反序列化留档，暂不消费（见 docs/CLOUDFLOW_YAML_GITHUB_ACTIONS_ALIGN.md）
    pub policies: Option<YamlValue>,
}

/// `workflow:` 元数据。
#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "lowercase")]
pub struct YamlWorkflowMeta {
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub version: Option<YamlValue>,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub display_name: Option<String>,
}
