//! IR 契约校验器（需求清单 §3 / §11.4）——本 crate **唯一**的 IR 校验实现。
//!
//! 统一入口策略：
//! - 本模块 `validate_ir_contracts`：完整 IR 契约校验——apiVersion/kind/重复节点 ID、
//!   节点类型合法性、各类型节点必备字段、变量/触发器/运行时配置、表达式结构
//!   （`$ref`/`$expr`/`$template`/`$pipeline`）、`$ref` 引用可解析性与
//!   `children`/`controlParent`/`dependsOn` 引用一致性、边引用与环检测。
//! - [`crate::compiler::validate_ir`]：文本形态薄适配层，内部委托本模块并把
//!   `IrContractIssue` 映射为字符串；`RuntimeEngine::load`（生产执行面加载）、
//!   生产 HTTP `/ir-validate` API、Workflow Service 与其他微服务、开发调试执行入口
//!   全部共用同一校验语义，开发面与生产面行为一致。
//!
//! 设计约束（需求 3.23/3.24/3.25）：
//! - 纯函数：`validate_ir_contracts(ir) -> Vec<IrContractIssue>`，无 I/O、无全局状态、可独立单测；
//! - 一次收集全部问题，不因第一个错误停止；
//! - 每个问题携带错误码（CFI-xxxx 段：IR 契约校验层；与 DSL 编译 CFxxxx、YAML 前端
//!   CFY-xxxx、调试运行面 CFD-xxxx 分层区分，见 `docs/CLOUDFLOW_ERROR_DESIGN.md`）、
//!   JSON 路径与节点 ID；
//! - 校验通过后调用方可直接复用同一 `WorkflowIrV1` 对象进入执行引擎。

use crate::ir::WorkflowIrV1;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::{BTreeMap, BTreeSet, HashSet};

/// 本 crate 当前唯一支持的 IR 契约版本。
pub const IR_API_VERSION: &str = "workflow.cloudflow.io/v1";

/// 合法的节点类型集合（与 compiler 生成、Runtime 执行的 node_type 对齐；
/// `subworkflow` 为预留类型，由 provider=workflow 的 task 节点承载）。
pub const VALID_NODE_TYPES: &[&str] = &[
    "task",
    "plugin",
    "subworkflow",
    "condition",
    "parallel",
    "loop",
    "try",
    "switch",
    "assert",
    "validate",
    "wait",
    "delay",
    "notify",
    "return",
    "break",
    "continue",
];

/// 变量声明允许的 `type` 取值（与 execution 层 `matches_type` 对齐，另加 unknown 声明态）。
const VALID_VARIABLE_TYPES: &[&str] = &[
    "string", "number", "boolean", "array", "object", "file", "user", "space", "unknown",
];

const VALID_RETRY_STRATEGIES: &[&str] = &["fixed", "exponential"];

/// IR 契约校验问题（需求 3.23：包含节点 ID 和原因；3.24：可序列化为诊断输出）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IrContractIssue {
    /// 错误码：CFI-xxxx 段。
    pub code: String,
    /// JSON 路径，如 `spec.graph.nodes[2].loopConfig`。
    pub path: String,
    /// 相关节点 ID（若问题与具体节点相关）。
    #[serde(skip_serializing_if = "Option::is_none")]
    pub node_id: Option<String>,
    /// 人类可读原因。
    pub message: String,
}

impl IrContractIssue {
    pub fn new(
        code: &str,
        path: impl Into<String>,
        node_id: Option<&str>,
        message: impl Into<String>,
    ) -> Self {
        Self {
            code: code.into(),
            path: path.into(),
            node_id: node_id.map(str::to_owned),
            message: message.into(),
        }
    }
}

/// 校验 IR 契约完整性；返回空 Vec 表示通过（需求 3.1/3.24）。
pub fn validate_ir_contracts(ir: &WorkflowIrV1) -> Vec<IrContractIssue> {
    let mut issues: Vec<IrContractIssue> = Vec::new();

    // 3.2 apiVersion。
    if ir.api_version != IR_API_VERSION {
        issues.push(IrContractIssue::new(
            "CFI-7001",
            "apiVersion",
            None,
            format!(
                "apiVersion 必须为 `{IR_API_VERSION}`，实际为 `{}`",
                ir.api_version
            ),
        ));
    }
    // 3.3 kind。
    if ir.kind != "Workflow" {
        issues.push(IrContractIssue::new(
            "CFI-7002",
            "kind",
            None,
            format!("kind 必须为 `Workflow`，实际为 `{}`", ir.kind),
        ));
    }
    // 3.4 metadata.name。
    if ir.metadata.name.trim().is_empty() {
        issues.push(IrContractIssue::new(
            "CFI-7003",
            "metadata.name",
            None,
            "metadata.name 不能为空",
        ));
    }

    let nodes = &ir.spec.graph.nodes;
    // 3.7 nodes 非空。
    if nodes.is_empty() {
        issues.push(IrContractIssue::new(
            "CFI-7004",
            "spec.graph.nodes",
            None,
            "graph.nodes 不能为空：工作流至少需要一个节点",
        ));
        return issues;
    }
    // 3.7 节点 ID 唯一。
    let node_ids: HashSet<&str> = nodes.iter().map(|node| node.id.as_str()).collect();
    if node_ids.len() != nodes.len() {
        let mut seen = HashSet::new();
        for node in nodes {
            if !seen.insert(node.id.as_str()) {
                issues.push(IrContractIssue::new(
                    "CFI-7005",
                    format!("spec.graph.nodes[id={}] ", node.id),
                    Some(&node.id),
                    "graph 包含重复的节点 ID",
                ));
            }
        }
    }

    // 预收集可解析的变量名（静态变量 + 循环节点迭代变量），供 $ref 校验使用（3.21）。
    let declared_vars: BTreeSet<String> = ir.spec.variables.keys().cloned().collect();
    let iterator_vars: BTreeSet<String> = nodes
        .iter()
        .filter_map(|node| node.loop_config.as_ref())
        .filter_map(Value::as_object)
        .filter_map(|config| config.get("iterator").and_then(Value::as_str))
        .map(str::to_owned)
        .collect();
    // try/catch 的 catchBinding 是运行时绑定的局部变量（{code,message}），静态可解析。
    let catch_bindings: BTreeSet<String> = nodes
        .iter()
        .filter_map(|node| node.error_handler.as_ref())
        .filter_map(Value::as_object)
        .filter_map(|handler| handler.get("catchBinding").and_then(Value::as_str))
        .map(str::to_owned)
        .collect();
    let known_names: BTreeSet<String> = declared_vars
        .iter()
        .cloned()
        .chain(iterator_vars.iter().cloned())
        .chain(catch_bindings.iter().cloned())
        .collect();

    // 3.17 变量类型合法。
    for (name, variable) in &ir.spec.variables {
        if !VALID_VARIABLE_TYPES.contains(&variable.type_name.as_str()) {
            issues.push(IrContractIssue::new(
                "CFI-7019",
                format!("spec.variables[{}].type", name),
                None,
                format!(
                    "变量 {name} 的类型 `{}` 非法（允许：{}）",
                    variable.type_name,
                    VALID_VARIABLE_TYPES.join("/")
                ),
            ));
        }
    }

    // 3.18 触发器类型合法。
    match &ir.spec.trigger {
        crate::ir::TriggerIr::Schedule { cron, .. } if cron.trim().is_empty() => {
            issues.push(IrContractIssue::new(
                "CFI-7020",
                "spec.trigger.cron",
                None,
                "schedule 触发器缺少 cron 表达式",
            ));
        }
        crate::ir::TriggerIr::Http { path, .. } if path.trim().is_empty() => {
            issues.push(IrContractIssue::new(
                "CFI-7020",
                "spec.trigger.path",
                None,
                "http 触发器缺少 path",
            ));
        }
        _ => {}
    }

    // 3.19 运行时配置合法。
    if let Some(max_parallel) = ir.runtime.max_parallel {
        if max_parallel == 0 || max_parallel > 32 {
            issues.push(IrContractIssue::new(
                "CFI-7021",
                "runtime.maxParallel",
                None,
                format!("runtime.maxParallel 必须位于 1..=32，实际为 {max_parallel}"),
            ));
        }
    }
    if let Some(policy) = &ir.runtime.retry_policy {
        if policy.max_attempts == 0 {
            issues.push(IrContractIssue::new(
                "CFI-7021",
                "runtime.retryPolicy.maxAttempts",
                None,
                "runtime.retryPolicy.maxAttempts 必须 >= 1",
            ));
        }
        if !VALID_RETRY_STRATEGIES.contains(&policy.strategy.as_str()) {
            issues.push(IrContractIssue::new(
                "CFI-7021",
                "runtime.retryPolicy.strategy",
                None,
                format!(
                    "retryPolicy.strategy 非法：{}（允许 {}）",
                    policy.strategy,
                    VALID_RETRY_STRATEGIES.join("/")
                ),
            ));
        }
    }

    for (index, node) in nodes.iter().enumerate() {
        let node_path = format!("spec.graph.nodes[{index}]");
        let type_path = format!("{node_path}.type");
        // 3.8 节点类型合法。
        if !VALID_NODE_TYPES.contains(&node.node_type.as_str()) {
            issues.push(IrContractIssue::new(
                "CFI-7006",
                type_path,
                Some(&node.id),
                format!(
                    "节点类型 `{}` 非法（允许：{}）",
                    node.node_type,
                    VALID_NODE_TYPES.join("/")
                ),
            ));
            continue;
        }
        validate_node_by_type(
            ir,
            node,
            index,
            &node_path,
            &node_ids,
            &known_names,
            &mut issues,
        );
    }

    // 3.15 edges 引用存在性。
    for (edge_index, edge) in ir.spec.graph.edges.iter().enumerate() {
        let path = format!("spec.graph.edges[{edge_index}]");
        if !node_ids.contains(edge.from.as_str()) {
            issues.push(IrContractIssue::new(
                "CFI-7017",
                format!("{path}.from"),
                None,
                format!("edge from 引用了不存在的节点 `{}`", edge.from),
            ));
        }
        if !node_ids.contains(edge.to.as_str()) {
            issues.push(IrContractIssue::new(
                "CFI-7017",
                format!("{path}.to"),
                None,
                format!("edge to 引用了不存在的节点 `{}`", edge.to),
            ));
        }
    }
    // 3.16 环检测（拓扑排序）。
    if has_cycle(nodes, &ir.spec.graph.edges) {
        issues.push(IrContractIssue::new(
            "CFI-7018",
            "spec.graph.edges",
            None,
            "workflow graph 存在环：edges 不能形成循环依赖",
        ));
    }
    issues
}

fn validate_node_by_type(
    _ir: &WorkflowIrV1,
    node: &crate::ir::NodeIr,
    _index: usize,
    node_path: &str,
    node_ids: &HashSet<&str>,
    known_names: &BTreeSet<String>,
    issues: &mut Vec<IrContractIssue>,
) {
    // 依赖/子节点/控制归属引用一致性（3.22）。
    for dep in &node.depends_on {
        if !node_ids.contains(dep.as_str()) {
            issues.push(IrContractIssue::new(
                "CFI-7024",
                format!("{node_path}.dependsOn"),
                Some(&node.id),
                format!("dependsOn 引用了不存在的节点 `{dep}`"),
            ));
        }
    }
    for child in &node.children {
        if !node_ids.contains(child.as_str()) {
            issues.push(IrContractIssue::new(
                "CFI-7024",
                format!("{node_path}.children"),
                Some(&node.id),
                format!("children 引用了不存在的节点 `{child}`"),
            ));
        }
    }
    if let Some(parent) = &node.control_parent {
        if !node_ids.contains(parent.as_str()) {
            issues.push(IrContractIssue::new(
                "CFI-7024",
                format!("{node_path}.controlParent"),
                Some(&node.id),
                format!("controlParent 引用了不存在的节点 `{parent}`"),
            ));
        }
    }

    // 节点级 retry 策略。
    if let Some(retry) = &node.retry {
        if retry.max_attempts == 0 {
            issues.push(IrContractIssue::new(
                "CFI-7028",
                format!("{node_path}.retry.maxAttempts"),
                Some(&node.id),
                "retry.maxAttempts 必须 >= 1",
            ));
        }
        if !VALID_RETRY_STRATEGIES.contains(&retry.strategy.as_str()) {
            issues.push(IrContractIssue::new(
                "CFI-7028",
                format!("{node_path}.retry.strategy"),
                Some(&node.id),
                format!(
                    "retry.strategy 非法：{}（允许 {}）",
                    retry.strategy,
                    VALID_RETRY_STRATEGIES.join("/")
                ),
            ));
        }
    }
    if let Some(on_timeout) = &node.on_timeout {
        if !matches!(on_timeout.as_str(), "fail" | "continue" | "retry") {
            issues.push(IrContractIssue::new(
                "CFI-7027",
                format!("{node_path}.onTimeout"),
                Some(&node.id),
                format!("onTimeout 非法：{on_timeout}（允许 fail/continue/retry）"),
            ));
        }
    }

    match node.node_type.as_str() {
        "task" | "subworkflow" => {
            let Some(action) = node.action.as_ref() else {
                issues.push(IrContractIssue::new(
                    "CFI-7026",
                    format!("{node_path}.action"),
                    Some(&node.id),
                    format!("{} 节点缺少 action", node.node_type),
                ));
                return;
            };
            if action.provider.is_empty() {
                issues.push(IrContractIssue::new(
                    "CFI-7007",
                    format!("{node_path}.action.provider"),
                    Some(&node.id),
                    "task 节点 action.provider 不能为空",
                ));
            }
            // 3.9 task 的 action 必须包含 provider/service/method（plugin 走 CFI-7008 分支校验）。
            if node.node_type == "task" && (action.service.is_none() || action.method.is_none()) {
                issues.push(IrContractIssue::new(
                    "CFI-7007",
                    format!("{node_path}.action"),
                    Some(&node.id),
                    "task 节点 action 必须包含 provider、service、method",
                ));
            }
        }
        "plugin" => {
            let Some(action) = node.action.as_ref() else {
                issues.push(IrContractIssue::new(
                    "CFI-7026",
                    format!("{node_path}.action"),
                    Some(&node.id),
                    "plugin 节点缺少 action",
                ));
                return;
            };
            // 3.10 plugin 的 action 必须包含 pluginId/function。
            if action.plugin_id.is_none() || action.function.is_none() {
                issues.push(IrContractIssue::new(
                    "CFI-7008",
                    format!("{node_path}.action"),
                    Some(&node.id),
                    "plugin 节点 action 必须包含 pluginId、function",
                ));
            }
        }
        "condition" => {
            // 3.11 condition 必须包含表达式与分支。
            if node.condition.is_none() {
                issues.push(IrContractIssue::new(
                    "CFI-7009",
                    format!("{node_path}.condition"),
                    Some(&node.id),
                    "condition 节点缺少 condition 表达式",
                ));
            }
            let branches = node.error_handler.as_ref().and_then(Value::as_object);
            if branches.is_none()
                || !branches
                    .unwrap()
                    .get("trueBranch")
                    .is_some_and(Value::is_array)
                || !branches
                    .unwrap()
                    .get("falseBranch")
                    .is_some_and(Value::is_array)
            {
                issues.push(IrContractIssue::new(
                    "CFI-7009",
                    format!("{node_path}.errorHandler"),
                    Some(&node.id),
                    "condition 节点 errorHandler 必须包含 trueBranch 与 falseBranch（数组）",
                ));
            }
        }
        "parallel" => {
            // 3.12 parallel 必须包含 branches。
            let configured = node
                .parallel
                .as_ref()
                .and_then(Value::as_object)
                .and_then(|config| config.get("branches"))
                .and_then(Value::as_array);
            if configured.is_none() && node.children.is_empty() {
                issues.push(IrContractIssue::new(
                    "CFI-7010",
                    format!("{node_path}.parallel"),
                    Some(&node.id),
                    "parallel 节点必须包含 branches（parallel.branches 或 children）",
                ));
            }
        }
        "loop" => {
            let Some(config) = node.loop_config.as_ref().and_then(Value::as_object) else {
                issues.push(IrContractIssue::new(
                    "CFI-7011",
                    format!("{node_path}.loopConfig"),
                    Some(&node.id),
                    "loop 节点缺少 loopConfig（需包含 kind/iterator/body）",
                ));
                return;
            };
            let kind = config
                .get("kind")
                .and_then(Value::as_str)
                .unwrap_or("foreach");
            let has_body =
                config.get("body").is_some_and(Value::is_array) || !node.children.is_empty();
            let has_iterator = config.get("iterator").is_some_and(Value::is_string);
            match kind {
                "foreach" | "for" => {
                    if !has_iterator {
                        issues.push(IrContractIssue::new(
                            "CFI-7011",
                            format!("{node_path}.loopConfig.iterator"),
                            Some(&node.id),
                            format!("{kind} 循环缺少 iterator"),
                        ));
                    }
                    if config.get("collection").is_none() {
                        issues.push(IrContractIssue::new(
                            "CFI-7011",
                            format!("{node_path}.loopConfig.collection"),
                            Some(&node.id),
                            format!("{kind} 循环缺少 collection"),
                        ));
                    }
                    if !has_body {
                        issues.push(IrContractIssue::new(
                            "CFI-7011",
                            format!("{node_path}.loopConfig.body"),
                            Some(&node.id),
                            format!("{kind} 循环缺少 body"),
                        ));
                    }
                }
                "while" => {
                    if config.get("condition").is_none() {
                        issues.push(IrContractIssue::new(
                            "CFI-7011",
                            format!("{node_path}.loopConfig.condition"),
                            Some(&node.id),
                            "while 循环缺少 condition",
                        ));
                    }
                    if !has_body {
                        issues.push(IrContractIssue::new(
                            "CFI-7011",
                            format!("{node_path}.loopConfig.body"),
                            Some(&node.id),
                            "while 循环缺少 body",
                        ));
                    }
                }
                "for-range" => {
                    if !has_iterator {
                        issues.push(IrContractIssue::new(
                            "CFI-7011",
                            format!("{node_path}.loopConfig.iterator"),
                            Some(&node.id),
                            "for-range 循环缺少 iterator",
                        ));
                    }
                    if config.get("from").is_none() || config.get("to").is_none() {
                        issues.push(IrContractIssue::new(
                            "CFI-7011",
                            format!("{node_path}.loopConfig"),
                            Some(&node.id),
                            "for-range 循环缺少 from/to",
                        ));
                    }
                    if !has_body {
                        issues.push(IrContractIssue::new(
                            "CFI-7011",
                            format!("{node_path}.loopConfig.body"),
                            Some(&node.id),
                            "for-range 循环缺少 body",
                        ));
                    }
                }
                other => {
                    issues.push(IrContractIssue::new(
                        "CFI-7011",
                        format!("{node_path}.loopConfig.kind"),
                        Some(&node.id),
                        format!(
                            "loopConfig.kind 非法：{other}（允许 foreach/while/for/for-range）"
                        ),
                    ));
                }
            }
        }
        "try" => {
            let handler = node.error_handler.as_ref().and_then(Value::as_object);
            if handler.is_none() {
                issues.push(IrContractIssue::new(
                    "CFI-7013",
                    format!("{node_path}.errorHandler"),
                    Some(&node.id),
                    "try 节点缺少 errorHandler（需包含 try/catch/finally 或 catchBinding）",
                ));
            }
        }
        "switch" => {
            let config = node.switch_config.as_ref().and_then(Value::as_object);
            match config {
                Some(config) if config.get("subject").is_some() => {}
                Some(_) => {
                    issues.push(IrContractIssue::new(
                        "CFI-7014",
                        format!("{node_path}.switchConfig.subject"),
                        Some(&node.id),
                        "switch 节点 switchConfig 缺少 subject",
                    ));
                }
                None => {
                    issues.push(IrContractIssue::new(
                        "CFI-7014",
                        format!("{node_path}.switchConfig"),
                        Some(&node.id),
                        "switch 节点缺少 switchConfig（需包含 subject/cases）",
                    ));
                }
            }
        }
        "assert" | "validate" => {
            if node.condition.is_none() {
                issues.push(IrContractIssue::new(
                    "CFI-7015",
                    format!("{node_path}.condition"),
                    Some(&node.id),
                    format!("{} 节点缺少 condition 表达式", node.node_type),
                ));
            }
        }
        "wait" => {
            // 3.14 wait 必须包含等待配置。
            let wait_type = node
                .error_handler
                .as_ref()
                .and_then(|value| value.get("waitType"))
                .and_then(Value::as_str);
            if wait_type.is_none() {
                issues.push(IrContractIssue::new(
                    "CFI-7012",
                    format!("{node_path}.errorHandler.waitType"),
                    Some(&node.id),
                    "wait 节点缺少等待配置（errorHandler.waitType）",
                ));
            }
        }
        "delay" => {
            if node.delay_ms.is_none() {
                issues.push(IrContractIssue::new(
                    "CFI-7016",
                    format!("{node_path}.delayMs"),
                    Some(&node.id),
                    "delay 节点缺少 delayMs",
                ));
            }
        }
        _ => {}
    }

    // 3.20/3.21/3.22 表达式结构与 $ref 引用：遍历节点全部 Value 载荷。
    let id = node.id.as_str();
    let mut check = |value: &Value, path: &str| {
        check_expression_tree(value, path, id, known_names, node_ids, false, issues)
    };
    if let Some(condition) = &node.condition {
        check(condition, &format!("{node_path}.condition"));
    }
    if let Some(depends_condition) = &node.depends_condition {
        check(depends_condition, &format!("{node_path}.dependsCondition"));
    }
    for (key, value) in &node.inputs {
        check(value, &format!("{node_path}.inputs[{key}]"));
    }
    for (key, value) in &node.outputs {
        check(value, &format!("{node_path}.outputs[{key}]"));
    }
    if let Some(action) = &node.action {
        check(&action.arguments, &format!("{node_path}.action.arguments"));
    }
    for (key, value) in [
        ("loopConfig", node.loop_config.as_ref()),
        ("switchConfig", node.switch_config.as_ref()),
        ("parallel", node.parallel.as_ref()),
        ("errorHandler", node.error_handler.as_ref()),
        ("notifyConfig", node.notify_config.as_ref()),
        ("onError", node.on_error.as_ref()),
    ] {
        if let Some(value) = value {
            check(value, &format!("{node_path}.{key}"));
        }
    }
}

/// 校验表达式树：`$ref`/`$expr`/`$template`/`$pipeline` 结构合法且引用可解析（3.20/3.21）。
/// `pipeline_row_context` 为 true 时表示处于管道谓词的“行上下文”：裸标识符可能是元素字段，
/// 静态不可解析，跳过裸标识符的引用检查。
fn check_expression_tree(
    value: &Value,
    path: &str,
    node_id: &str,
    known_names: &BTreeSet<String>,
    node_ids: &HashSet<&str>,
    pipeline_row_context: bool,
    issues: &mut Vec<IrContractIssue>,
) {
    if let Some(reference) = value.get("$ref") {
        if !reference.is_string() {
            issues.push(IrContractIssue::new(
                "CFI-7022",
                String::from(path),
                Some(node_id),
                "$ref 必须是字符串",
            ));
            return;
        }
        let reference = reference.as_str().unwrap();
        check_reference(
            reference,
            path,
            node_id,
            known_names,
            node_ids,
            pipeline_row_context,
            issues,
        );
        return;
    }
    if let Some(expression) = value.get("$expr") {
        let object = match expression.as_object() {
            Some(object) => object,
            None => {
                issues.push(IrContractIssue::new(
                    "CFI-7022",
                    String::from(path),
                    Some(node_id),
                    "$expr 必须是对象",
                ));
                return;
            }
        };
        let is_ternary = object.get("condition").is_some()
            && object.get("whenTrue").is_some()
            && object.get("whenFalse").is_some();
        let is_unary = object.get("operator").is_some() && object.get("operand").is_some();
        let is_binary = object.get("operator").is_some()
            && object.get("left").is_some()
            && object.get("right").is_some();
        let is_function = object.get("function").is_some();
        if !(is_ternary || is_unary || is_binary || is_function) {
            issues.push(IrContractIssue::new(
                "CFI-7022",
                String::from(path),
                Some(node_id),
                "$expr 结构非法：需为 condition/whenTrue/whenFalse、operator/operand、operator/left/right 或 function/arguments 之一",
            ));
            return;
        }
        let child_ctx = pipeline_row_context;
        for (key, child) in object {
            if key != "operator" && key != "function" {
                check_expression_tree(
                    child,
                    &format!("{path}.{key}"),
                    node_id,
                    known_names,
                    node_ids,
                    child_ctx,
                    issues,
                );
            }
        }
        return;
    }
    if let Some(segments) = value.get("$template") {
        if !segments.is_array() {
            issues.push(IrContractIssue::new(
                "CFI-7022",
                String::from(path),
                Some(node_id),
                "$template 必须是数组（字符串段与表达式段交替）",
            ));
            return;
        }
        for (segment_index, segment) in segments.as_array().unwrap().iter().enumerate() {
            if !segment.is_string() {
                check_expression_tree(
                    segment,
                    &format!("{path} segments[{segment_index}]"),
                    node_id,
                    known_names,
                    node_ids,
                    pipeline_row_context,
                    issues,
                );
            }
        }
        return;
    }
    if let Some(pipeline) = value.get("$pipeline") {
        let object = match pipeline.as_object() {
            Some(object) => object,
            None => {
                issues.push(IrContractIssue::new(
                    "CFI-7022",
                    String::from(path),
                    Some(node_id),
                    "$pipeline 必须是对象（input + op）",
                ));
                return;
            }
        };
        if object.get("input").is_none() || object.get("op").is_none() {
            issues.push(IrContractIssue::new(
                "CFI-7022",
                String::from(path),
                Some(node_id),
                "$pipeline 缺少 input 或 op",
            ));
            return;
        }
        check_expression_tree(
            object.get("input").unwrap(),
            &format!("{path}.input"),
            node_id,
            known_names,
            node_ids,
            false,
            issues,
        );
        // filter 谓词处于行上下文：裸标识符可能是元素字段（如 size），跳过裸标识符引用检查。
        if let Some(op) = object.get("op").and_then(Value::as_object) {
            if op.get("op").and_then(Value::as_str) == Some("filter") {
                if let Some(predicate) = op.get("predicate") {
                    check_expression_tree(
                        predicate,
                        &format!("{path}.op.predicate"),
                        node_id,
                        known_names,
                        node_ids,
                        true,
                        issues,
                    );
                }
            }
        }
        return;
    }
    match value {
        Value::Array(values) => {
            for (child_index, child) in values.iter().enumerate() {
                check_expression_tree(
                    child,
                    &format!("{path}[{child_index}]"),
                    node_id,
                    known_names,
                    node_ids,
                    pipeline_row_context,
                    issues,
                );
            }
        }
        Value::Object(map) => {
            for (key, child) in map {
                check_expression_tree(
                    child,
                    &format!("{path}.{key}"),
                    node_id,
                    known_names,
                    node_ids,
                    pipeline_row_context,
                    issues,
                );
            }
        }
        Value::Null | Value::Bool(_) | Value::Number(_) | Value::String(_) => {}
    }
}

/// 校验单个 $ref 字符串是否指向存在的变量或步骤输出（3.21）。
/// `pipeline_row_context` 为 true 时（管道 filter 谓词行上下文），裸标识符视为元素字段，
/// 静态阶段跳过引用检查；带命名空间前缀（vars./steps.）的引用仍正常校验。
fn check_reference(
    reference: &str,
    path: &str,
    node_id: &str,
    known_names: &BTreeSet<String>,
    node_ids: &HashSet<&str>,
    pipeline_row_context: bool,
    issues: &mut Vec<IrContractIssue>,
) {
    if let Some(name) = reference.strip_prefix("vars.") {
        let name = name.split('.').next().unwrap_or(name);
        if !name.is_empty() && !known_names.contains(name) {
            issues.push(IrContractIssue::new(
                "CFI-7023",
                String::from(path),
                Some(node_id),
                format!("$ref `vars.{name}` 指向未声明的变量（未知变量引用）"),
            ));
        }
        return;
    }
    if let Some(rest) = reference.strip_prefix("steps.") {
        let step_id = rest.split('.').next().unwrap_or(rest);
        if step_id.is_empty() {
            issues.push(IrContractIssue::new(
                "CFI-7023",
                String::from(path),
                Some(node_id),
                format!("$ref `{reference}` 缺少步骤 ID"),
            ));
        } else if !node_ids.contains(step_id) {
            issues.push(IrContractIssue::new(
                "CFI-7023",
                String::from(path),
                Some(node_id),
                format!("$ref `{reference}` 指向不存在的步骤 `{step_id}`"),
            ));
        }
        return;
    }
    // env./input. 由运行时注入，静态阶段不校验。
    if reference.starts_with("env.") || reference.starts_with("input.") {
        return;
    }
    // 裸标识符：局部变量/循环迭代变量/管道行上下文；前两者静态可查，
    // 行上下文的裸标识符是元素字段，静态不可解析，跳过。
    if !reference.contains('.') && !known_names.contains(reference) && !pipeline_row_context {
        issues.push(IrContractIssue::new(
            "CFI-7023",
            String::from(path),
            Some(node_id),
            format!("$ref `{reference}` 既不是已声明变量也不是循环迭代变量"),
        ));
    }
}

/// 环检测（与 compiler::ir_has_cycle 同构，但归属本校验器，需求 3.16/3.24 纯函数）。
fn has_cycle(nodes: &[crate::ir::NodeIr], edges: &[crate::ir::EdgeIr]) -> bool {
    let mut incoming: BTreeMap<&str, usize> = nodes
        .iter()
        .map(|node| (node.id.as_str(), 0usize))
        .collect();
    for edge in edges {
        if let Some(value) = incoming.get_mut(edge.to.as_str()) {
            *value += 1;
        }
    }
    let mut ready: Vec<&str> = incoming
        .iter()
        .filter_map(|(id, count)| (*count == 0).then_some(*id))
        .collect();
    let mut visited = 0usize;
    while let Some(id) = ready.pop() {
        visited += 1;
        for edge in edges.iter().filter(|edge| edge.from == id) {
            if let Some(count) = incoming.get_mut(edge.to.as_str()) {
                *count -= 1;
                if *count == 0 {
                    ready.push(edge.to.as_str());
                }
            }
        }
    }
    visited != incoming.len()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ir::{
        ActionIr, GraphIr, MetadataIr, NodeIr, RuntimeIr, SecurityIr, SpecIr, TriggerIr,
        VariableIr, WorkflowIrV1,
    };
    use serde_json::json;
    use std::collections::BTreeMap;

    fn task_node(id: &str, service: &str, method: &str) -> NodeIr {
        NodeIr {
            id: id.into(),
            node_type: "task".into(),
            name: None,
            action: Some(ActionIr {
                provider: "builtin".into(),
                service: Some(service.into()),
                method: Some(method.into()),
                plugin_id: None,
                function: None,
                version: None,
                arguments: json!({}),
            }),
            inputs: BTreeMap::new(),
            outputs: BTreeMap::new(),
            depends_on: Vec::new(),
            retry: None,
            retry_on: Vec::new(),
            timeout: None,
            on_timeout: None,
            condition: None,
            depends_condition: None,
            switch_config: None,
            delay_ms: None,
            notify_config: None,
            on_error: None,
            loop_config: None,
            parallel: None,
            error_handler: None,
            control_parent: None,
            control_branch: None,
            children: Vec::new(),
        }
    }

    fn minimal_ir(nodes: Vec<NodeIr>, edges: Vec<crate::ir::EdgeIr>) -> WorkflowIrV1 {
        WorkflowIrV1 {
            api_version: IR_API_VERSION.into(),
            kind: "Workflow".into(),
            metadata: MetadataIr {
                id: Some("wf-test".into()),
                name: "wf-test".into(),
                ..Default::default()
            },
            spec: SpecIr {
                trigger: TriggerIr::Manual,
                variables: BTreeMap::new(),
                graph: GraphIr { nodes, edges },
                outputs: BTreeMap::new(),
                environment: BTreeMap::new(),
                audit: None,
            },
            runtime: RuntimeIr::default(),
            security: SecurityIr::default(),
            extensions: BTreeMap::new(),
        }
    }

    #[test]
    fn minimal_valid_ir_passes() {
        let ir = minimal_ir(
            vec![
                task_node("a", "file", "list"),
                task_node("b", "file", "save"),
            ],
            vec![crate::ir::EdgeIr {
                from: "a".into(),
                to: "b".into(),
            }],
        );
        assert!(validate_ir_contracts(&ir).is_empty());
    }

    #[test]
    fn detects_bad_api_version_and_kind() {
        let mut ir = minimal_ir(vec![task_node("a", "file", "list")], Vec::new());
        ir.api_version = "workflow.other.io/v9".into();
        ir.kind = "Job".into();
        let issues = validate_ir_contracts(&ir);
        assert!(issues.iter().any(|issue| issue.code == "CFI-7001"));
        assert!(issues.iter().any(|issue| issue.code == "CFI-7002"));
    }

    #[test]
    fn detects_duplicate_ids_edges_cycle_and_unknown_edge() {
        let mut ir = minimal_ir(
            vec![
                task_node("a", "f", "m"),
                task_node("a", "f", "m"),
                task_node("b", "f", "m"),
                task_node("c", "f", "m"),
                task_node("d", "f", "m"),
            ],
            vec![
                crate::ir::EdgeIr {
                    from: "a".into(),
                    to: "b".into(),
                },
                crate::ir::EdgeIr {
                    from: "b".into(),
                    to: "a".into(),
                },
                crate::ir::EdgeIr {
                    from: "d".into(),
                    to: "ghost".into(),
                },
            ],
        );
        ir.spec.variables.insert(
            "unused".into(),
            VariableIr {
                type_name: "quux".into(),
                required: false,
                source: "input".into(),
                default: None,
                value: None,
            },
        );
        let issues = validate_ir_contracts(&ir);
        assert!(issues.iter().any(|issue| issue.code == "CFI-7005"));
        assert!(issues.iter().any(|issue| issue.code == "CFI-7017"));
        assert!(issues.iter().any(|issue| issue.code == "CFI-7018"));
        assert!(issues.iter().any(|issue| issue.code == "CFI-7019"));
    }

    #[test]
    fn detects_task_missing_service_and_plugin_missing_function() {
        let mut task = task_node("t", "file", "list");
        task.action.as_mut().unwrap().service = None;
        let mut plugin = task_node("p", "file", "list");
        plugin.node_type = "plugin".into();
        plugin.action.as_mut().unwrap().plugin_id = Some("abc".into());
        plugin.action.as_mut().unwrap().function = None;
        let ir = minimal_ir(vec![task, plugin], Vec::new());
        let issues = validate_ir_contracts(&ir);
        assert!(issues.iter().any(|issue| issue.code == "CFI-7007"));
        assert!(issues.iter().any(|issue| issue.code == "CFI-7008"));
    }

    #[test]
    fn detects_condition_loop_wait_missing_fields() {
        let mut condition = task_node("c", "f", "m");
        condition.node_type = "condition".into();
        condition.action = None;
        let mut loop_node = task_node("l", "f", "m");
        loop_node.node_type = "loop".into();
        loop_node.action = None;
        let mut wait = task_node("w", "f", "m");
        wait.node_type = "wait".into();
        wait.action = None;
        let ir = minimal_ir(vec![condition, loop_node, wait], Vec::new());
        let issues = validate_ir_contracts(&ir);
        assert!(issues.iter().any(|issue| issue.code == "CFI-7009"));
        assert!(issues.iter().any(|issue| issue.code == "CFI-7011"));
        assert!(issues.iter().any(|issue| issue.code == "CFI-7012"));
    }

    #[test]
    fn detects_unknown_reference() {
        let mut ir = minimal_ir(vec![task_node("a", "file", "list")], Vec::new());
        ir.spec.graph.nodes[0]
            .inputs
            .insert("x".into(), json!({"$ref": "vars.nobody"}));
        let issues = validate_ir_contracts(&ir);
        assert!(issues
            .iter()
            .any(|issue| issue.code == "CFI-7023" && issue.node_id.as_deref() == Some("a")));
    }

    #[test]
    fn detects_bad_expr_structure() {
        let mut ir = minimal_ir(vec![task_node("a", "file", "list")], Vec::new());
        ir.spec.graph.nodes[0]
            .inputs
            .insert("x".into(), json!({"$expr": {"operator": "+"}}));
        let issues = validate_ir_contracts(&ir);
        assert!(issues.iter().any(|issue| issue.code == "CFI-7022"));
    }

    #[test]
    fn iterator_variables_are_resolvable() {
        let mut body = task_node("body", "file", "process");
        let mut loop_node = task_node("l", "f", "m");
        loop_node.node_type = "loop".into();
        loop_node.action = None;
        loop_node.loop_config = Some(json!({
            "kind": "foreach",
            "iterator": "item",
            "collection": {"$ref": "vars.files"},
            "body": ["body"],
            "maxIterations": 100
        }));
        loop_node.children = vec!["body".into()];
        body.depends_on = vec!["l".into()];
        body.control_parent = Some("l".into());
        body.inputs.insert("v".into(), json!({"$ref": "item"}));
        let mut ir = minimal_ir(
            vec![loop_node, body],
            vec![crate::ir::EdgeIr {
                from: "l".into(),
                to: "body".into(),
            }],
        );
        ir.spec.variables.insert(
            "files".into(),
            VariableIr {
                type_name: "array".into(),
                required: true,
                source: "input".into(),
                default: None,
                value: None,
            },
        );
        assert!(validate_ir_contracts(&ir).is_empty());
    }
}
