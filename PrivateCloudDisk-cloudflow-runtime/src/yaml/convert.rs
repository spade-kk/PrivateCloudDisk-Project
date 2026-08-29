//! CloudFlow YAML 前端 —— YAML → Workflow Domain AST 转换（需求 8.x）。
//!
//! 转换链：YAML 文本 → `serde_yaml_ng`（第三方库）→ 强类型 `YamlDocument`（model.rs）
//! → 本文的领域 AST（`crate::ast::WorkflowNode`）。表达式一律以**字符串**交给
//! `crate::expression` 子系统（需求 6.31/28.56）：本模块只负责把 `${{ ... }}` 切出、
//! 规范引用（`input.x → vars.x`、`steps.<id>.<f> → steps.<id>.output.<f>`），
//! 不自行解析表达式语法。

use crate::ast::{
    ActionNode, EnvironmentDecl, ExpressionKind, ExpressionNode, FlowNode, LoopNode, MetadataNode,
    ParallelNode, RetryNode, Span, StepNode, SwitchCase, SwitchNode, TimeoutConfig, TriggerNode,
    ValueNode, VariableDecl, VariableSource, WorkflowNode,
};
use crate::diagnostic::Diagnostic;
use crate::yaml::locator::{yaml_parse_error, yaml_schema_error, Locator};
use crate::yaml::model::YamlDocument;
use serde_yaml_ng::Value as YamlValue;
use std::collections::{BTreeMap, HashSet};

/// [19.9/19.10] YAML 安全上限：防止解析炸弹（超大文件 / 超深嵌套 / 别名展开节点爆炸）。
/// 尺寸上限与 HTTP 编译接口 1 MiB 请求体上限一致（单一事实源见架构文档安全章节）。
pub const MAX_YAML_SOURCE_BYTES: usize = 1024 * 1024;
/// [19.9] 嵌套深度上限（mapping/sequence 层数，标量记 1 层）。
pub const MAX_YAML_DEPTH: usize = 100;
/// [19.10] 解析后节点总数上限：锚点/别名在 libyaml 事件期展开为完整值树，
/// 以节点总数近似约束“锚点 + 别名”数量，拦截别名炸弹（billion laughs 变体）。
pub const MAX_YAML_NODES: usize = 100_000;

/// [19.9/19.10] 资源保护护栏：超限立即返回单一致命诊断（CFY-SCHEMA-1005/1006/1007），
/// 不进入后续 Schema 校验与 AST 转换。护栏本身必须是纯函数（19.27）。
fn yaml_limit_check(source: &str, filename: &str) -> Result<(), Box<Diagnostic>> {
    if source.len() > MAX_YAML_SOURCE_BYTES {
        return Err(Box::new(Diagnostic::new(
            "CFY-SCHEMA-1005",
            "YAML_LIMIT_ERROR",
            format!(
                "YAML 源码大小 {} 字节超过上限 {} 字节（1 MiB）",
                source.len(),
                MAX_YAML_SOURCE_BYTES
            ),
            source,
            filename,
            0,
            1,
            vec![],
            Some("拆分工作流为多个模块（include）或改用 DSL 前端".into()),
        )));
    }
    Ok(())
}

/// 统计解析后值树的深度与节点总数（纯函数，19.27）。
fn yaml_depth_and_nodes(value: &YamlValue) -> (usize, usize) {
    match value {
        YamlValue::Mapping(mapping) => {
            let mut depth = 1usize;
            let mut nodes = 1usize;
            for (key, value) in mapping {
                let (key_depth, key_nodes) = yaml_depth_and_nodes(key);
                let (value_depth, value_nodes) = yaml_depth_and_nodes(value);
                depth = depth.max(1 + key_depth.max(value_depth));
                nodes += 2 + key_nodes + value_nodes;
            }
            (depth, nodes)
        }
        YamlValue::Sequence(sequence) => {
            let mut depth = 1usize;
            let mut nodes = 1usize;
            for item in sequence {
                let (child_depth, child_nodes) = yaml_depth_and_nodes(item);
                depth = depth.max(1 + child_depth);
                nodes += 1 + child_nodes;
            }
            (depth, nodes)
        }
        YamlValue::Null | YamlValue::Bool(_) | YamlValue::Number(_) | YamlValue::String(_) => {
            (1, 1)
        }
        // 自定义 tag 只是标注，不增加结构深度。
        YamlValue::Tagged(tagged) => yaml_depth_and_nodes(&tagged.value),
    }
}

/// 语法解析入口：仅解析 YAML → Domain AST，不执行语义分析（对应 DSL `parse_source_detailed`）。
pub fn parse_yaml_detailed(
    source: &str,
    filename: &str,
) -> Result<(WorkflowNode, Vec<Diagnostic>), Box<Diagnostic>> {
    // [19.9] 先于解析器执行尺寸护栏，超大输入不进入 libyaml。
    yaml_limit_check(source, filename)?;
    let root: YamlValue = serde_yaml_ng::from_str(source)
        .map_err(|error| yaml_parse_error(&error, source, filename))?;
    let normalized = normalize_document(root);
    // [19.9/19.10] 深度/节点护栏：拦截嵌套炸弹与别名展开后的节点爆炸。
    let (depth, nodes) = yaml_depth_and_nodes(&normalized);
    if depth > MAX_YAML_DEPTH {
        return Err(Box::new(Diagnostic::new(
            "CFY-SCHEMA-1006",
            "YAML_LIMIT_ERROR",
            format!("YAML 嵌套深度 {} 超过上限 {} 层", depth, MAX_YAML_DEPTH),
            source,
            filename,
            0,
            1,
            vec![],
            Some("减少嵌套层级；深层结构拆分为步骤或模块".into()),
        )));
    }
    if nodes > MAX_YAML_NODES {
        return Err(Box::new(Diagnostic::new(
            "CFY-SCHEMA-1007",
            "YAML_LIMIT_ERROR",
            format!(
                "YAML 解析后节点数 {} 超过上限 {}（锚点/别名展开保护）",
                nodes, MAX_YAML_NODES
            ),
            source,
            filename,
            0,
            1,
            vec![],
            Some("检查是否使用了放大式的锚点/别名组合；避免生成式内容直接入库".into()),
        )));
    }
    // Schema 形状校验作为 YAML 编译**第一步**（需求 31.2）：必填/类型/未知字段/非法值，
    // 一次收集全部 `CFY-SCHEMA-*` 错误（31.8），随后继续尽力构建 AST 并合并领域诊断。
    let mut diagnostics = crate::yaml::schema::validate_schema(&normalized, source, filename);
    let document = match serde_yaml_ng::from_value(normalized) {
        Ok(document) => Some(document),
        Err(error) => {
            // 形状错误已由 Schema 层给出（如 steps 非列表），不再重复报 CFY-1001；
            // 无形状错误的强类型反序列化失败才按解析错误上报。
            if diagnostics.is_empty() {
                return Err(yaml_parse_error(&error, source, filename));
            }
            None
        }
    };
    let converter = Converter::new(source, filename);
    let (workflow, conversion_diagnostics) = converter.convert(document.unwrap_or_default());
    diagnostics.extend(conversion_diagnostics);
    Ok((workflow, diagnostics))
}

/// 语法解析入口：任一诊断即整体失败（对应 `lib::parse_ast` 语义）。
pub fn parse_yaml(source: &str, filename: &str) -> Result<WorkflowNode, Box<Diagnostic>> {
    let (workflow, diagnostics) = parse_yaml_detailed(source, filename)?;
    if let Some(diagnostic) = diagnostics.into_iter().next() {
        return Err(Box::new(diagnostic));
    }
    Ok(workflow)
}

/// 把 `workflow: {trigger, inputs, steps, ...}` 嵌套形态提升为扁平形态
/// （metadata 保留在 workflow 下供 `YamlWorkflowMeta` 读取）。CloudFlow YAML 只支持
/// 扁平形态与嵌套形态两种**本地**书写方式；不解析任何旧版 `automation.pcd/v1` 包装
/// （`apiVersion/kind/metadata/spec/limits`、`uses/needs/result`）。
fn normalize_document(root: YamlValue) -> YamlValue {
    let YamlValue::Mapping(mut root) = root else {
        return root;
    };
    let workflow_key = YamlValue::String("workflow".into());
    if let Some(workflow_mapping) = root
        .get(&workflow_key)
        .and_then(YamlValue::as_mapping)
        .cloned()
    {
        for (key, value) in workflow_mapping {
            if !root.contains_key(&key) {
                root.insert(key, value);
            }
        }
    }

    YamlValue::Mapping(root)
}

struct Converter<'a> {
    source: &'a str,
    filename: &'a str,
    locator: Locator,
    diagnostics: Vec<Diagnostic>,
    /// YAML 控制块 `id` 别名 → 展开后的真实步骤 ID 列表（并行/循环/switch）。
    aliases: BTreeMap<String, Vec<String>>,
    used_ids: HashSet<String>,
}

impl<'a> Converter<'a> {
    fn new(source: &'a str, filename: &'a str) -> Self {
        Self {
            source,
            filename,
            locator: Locator::new(),
            diagnostics: Vec::new(),
            aliases: BTreeMap::new(),
            used_ids: HashSet::new(),
        }
    }

    fn convert(mut self, document: YamlDocument) -> (WorkflowNode, Vec<Diagnostic>) {
        let source = self.source.to_owned();
        let filename = self.filename.to_owned();

        let name_offset = self.locate_scalar("workflow");
        let name = document
            .workflow
            .as_ref()
            .and_then(|meta| meta.name.clone())
            .unwrap_or_else(|| "unnamed_workflow".into());
        let flow_span = self.locator.span_at(&source, name_offset.min(source.len()));

        let metadata = MetadataNode {
            display_name: document
                .workflow
                .as_ref()
                .and_then(|meta| meta.display_name.clone()),
            description: document
                .workflow
                .as_ref()
                .and_then(|meta| meta.description.clone()),
            version: document
                .workflow
                .as_ref()
                .and_then(|meta| meta.version.clone())
                .map(|version| scalar(&version)),
            ..Default::default()
        };

        let mut variables = Vec::new();
        let mut seen_variables = HashSet::new();

        // inputs / input：外部输入参数（VariableSource::Input）。
        for declared in self.input_decls(&document) {
            if !seen_variables.insert(declared.name.clone()) {
                self.diagnostics.push(yaml_schema_error(
                    format!("输入参数重复声明：{}", declared.name),
                    &source,
                    &filename,
                    declared.offset,
                    vec![declared.name.clone()],
                ));
                continue;
            }
            let span = self.locator.span_at(&source, declared.offset);
            variables.push(VariableDecl {
                name: declared.name,
                type_name: declared.type_name.unwrap_or_else(|| "string".into()),
                required: declared.required,
                source: VariableSource::Input,
                default: declared.default.map(|value| self.value_from_yaml(&value)),
                span,
            });
        }

        // variables：运行时本地变量（VariableSource::Local / Deferred）。
        if let Some(vars) = document.variables.as_ref() {
            for (raw_name, value) in vars {
                let name = raw_name.trim();
                let offset = self.locate_scalar(raw_name);
                let span = self.locator.span_at(&source, offset);
                let decl = self.parse_decl(value);
                let default = decl
                    .expression
                    .map(|expr| self.expression_as_value(&expr, offset))
                    .or_else(|| decl.default.map(|val| self.value_from_yaml(&val)));
                let source_kind = if default.is_none() {
                    VariableSource::Deferred
                } else {
                    VariableSource::Local
                };
                let type_name = decl
                    .type_name
                    .or_else(|| default.as_ref().map(infer_yaml_value_type))
                    .unwrap_or_else(|| "string".into());
                if !seen_variables.insert(name.to_owned()) {
                    self.diagnostics.push(yaml_schema_error(
                        format!("变量重复声明：{name}"),
                        &source,
                        &filename,
                        offset,
                        vec![name.to_owned()],
                    ));
                    continue;
                }
                variables.push(VariableDecl {
                    name: name.to_owned(),
                    type_name,
                    required: false,
                    source: source_kind,
                    default,
                    span,
                });
            }
        }

        // env：环境变量（工作流级，编译期注入）。
        let environment = document
            .env
            .as_ref()
            .map(|env| {
                env.iter()
                    .map(|(key, value)| {
                        let offset = self.locate_scalar(key);
                        EnvironmentDecl {
                            key: key.clone(),
                            value: self.value_from_yaml(value),
                            span: self.locator.span_at(&source, offset),
                        }
                    })
                    .collect()
            })
            .unwrap_or_default();

        let trigger = self.convert_trigger(document.trigger.as_ref());

        // 顶层内流程（steps）。
        let mut step_offsets = Vec::new();
        let inner_flow: Vec<FlowNode> = document
            .steps
            .as_ref()
            .map(|steps| {
                steps
                    .iter()
                    .filter_map(|step_value| {
                        let (node, offset) = self.convert_step(step_value);
                        if let Some(id) = node.as_ref().and_then(flow_id) {
                            step_offsets.push((id, offset));
                        }
                        node
                    })
                    .collect()
            })
            .unwrap_or_default();

        // catch / finally → 顶层 TryCatch 包裹。
        let catch_nodes: Vec<FlowNode> = document
            .catch
            .as_ref()
            .map(|entries| {
                entries
                    .iter()
                    .filter_map(|entry| self.convert_catch_entry(entry))
                    .collect()
            })
            .unwrap_or_default();
        let finally_nodes: Vec<FlowNode> = document
            .finally
            .as_ref()
            .map(|entries| {
                entries
                    .iter()
                    .filter_map(|entry| self.convert_step(entry).0)
                    .collect()
            })
            .unwrap_or_default();

        let flow = if catch_nodes.is_empty() && finally_nodes.is_empty() {
            inner_flow
        } else {
            let wrap_offset = step_offsets
                .first()
                .map(|(_, offset)| *offset)
                .unwrap_or(name_offset);
            vec![FlowNode::TryCatch(crate::ast::TryCatchNode {
                try_nodes: inner_flow,
                catch_binding: None,
                catch_nodes,
                finally_nodes,
                span: self.locator.span_at(&source, wrap_offset),
            })]
        };

        let runtime = self.convert_runtime(document.runtime.as_ref());

        let mut workflow = WorkflowNode {
            name,
            namespace: None,
            environment,
            audit: None,
            step_groups: vec![],
            module_defaults: BTreeMap::new(),
            includes: vec![],
            metadata,
            variables,
            trigger,
            runtime,
            flow: vec![],
            steps: vec![],
            controls: vec![],
            handlers: vec![],
            outputs: self.convert_outputs(document.outputs.as_ref()),
            span: flow_span,
        };

        for item in flow {
            match &item {
                FlowNode::Step(step) => workflow.steps.push((**step).clone()),
                _ => workflow.controls.push(item.clone()),
            }
            workflow.flow.push(item);
        }
        (workflow, self.diagnostics)
    }

    fn convert_outputs(
        &mut self,
        outputs: Option<&BTreeMap<String, YamlValue>>,
    ) -> BTreeMap<String, ValueNode> {
        let mut result = BTreeMap::new();
        if let Some(outputs) = outputs {
            for (key, value) in outputs {
                let declared = self.value_from_yaml(value);
                // 类型说明形式 `{name: {type: X}}`：输出值为对步骤输出的引用由调用方提供，
                // 这里保留显式值；简单 `{name: <expr>}` 直接作为输出表达式。
                result.insert(key.clone(), declared);
            }
        }
        result
    }

    fn convert_trigger(&mut self, trigger: Option<&YamlValue>) -> TriggerNode {
        let Some(trigger) = trigger else {
            return TriggerNode::Manual;
        };
        let _offset = self.locate_yaml_value(trigger);
        match trigger {
            YamlValue::Null => TriggerNode::Manual,
            YamlValue::String(_) => {
                // 字符串（仅 manual 合法）由 schema.rs 校验（CFY-SCHEMA-1004），此处回退 Manual。
                TriggerNode::Manual
            }
            YamlValue::Mapping(map) => {
                let get_any = |keys: &[&str]| {
                    keys.iter()
                        .find_map(|key| map.get(YamlValue::String((*key).into())))
                };
                if let Some(type_value) = get_any(&["type"]) {
                    let type_text = scalar(type_value).to_ascii_lowercase();
                    let sub = |keys: &[&str]| get_any(keys).map(scalar);
                    return match type_text.as_str() {
                        "manual" => TriggerNode::Manual,
                        "event" => TriggerNode::Event {
                            name: sub(&["topic", "name", "event"]).unwrap_or_default(),
                        },
                        "cron" | "schedule" => TriggerNode::Schedule {
                            cron: sub(&["expression", "cron"]).unwrap_or_default(),
                            timezone: sub(&["timezone"]),
                        },
                        "http" | "webhook" => TriggerNode::Http {
                            path: sub(&["path"]).unwrap_or_default(),
                            method: sub(&["method"]).map(|v| v.to_uppercase()),
                        },
                        "interval" => {
                            let raw = sub(&["interval", "expression", "every"]).unwrap_or_default();
                            let milliseconds = parse_duration_ms(&raw);
                            TriggerNode::Interval { raw, milliseconds }
                        }
                        // 不支持的 trigger type 由 schema.rs 校验（CFY-SCHEMA-1004）。
                        _other => TriggerNode::Manual,
                    };
                }
                if let Some(value) = get_any(&["event"]) {
                    let name = match value {
                        YamlValue::Mapping(inner) => inner
                            .get(YamlValue::String("topic".into()))
                            .map(scalar)
                            .unwrap_or_default(),
                        other => scalar(other),
                    };
                    return TriggerNode::Event { name };
                }
                if let Some(value) = get_any(&["cron"]) {
                    let (cron, timezone) = match value {
                        YamlValue::Mapping(inner) => (
                            inner
                                .get(YamlValue::String("expression".into()))
                                .or_else(|| inner.get(YamlValue::String("cron".into())))
                                .map(scalar)
                                .unwrap_or_default(),
                            inner.get(YamlValue::String("timezone".into())).map(scalar),
                        ),
                        other => (scalar(other), None),
                    };
                    return TriggerNode::Schedule { cron, timezone };
                }
                if let Some(value) = get_any(&["webhook", "http"]) {
                    let (path, method) = match value {
                        YamlValue::Mapping(inner) => (
                            inner
                                .get(YamlValue::String("path".into()))
                                .map(scalar)
                                .unwrap_or_default(),
                            inner
                                .get(YamlValue::String("method".into()))
                                .map(|v| scalar(v).to_uppercase()),
                        ),
                        other => (scalar(other), None),
                    };
                    return TriggerNode::Http { path, method };
                }
                if let Some(value) = get_any(&["interval"]) {
                    let raw = match value {
                        YamlValue::Mapping(inner) => inner
                            .get(YamlValue::String("every".into()))
                            .map(scalar)
                            .unwrap_or_default(),
                        other => scalar(other),
                    };
                    let milliseconds = parse_duration_ms(&raw);
                    return TriggerNode::Interval { raw, milliseconds };
                }
                if let Some(value) = get_any(&["schedule"]) {
                    let (cron, timezone) = match value {
                        YamlValue::Mapping(inner) => (
                            inner
                                .get(YamlValue::String("expression".into()))
                                .or_else(|| inner.get(YamlValue::String("cron".into())))
                                .map(scalar)
                                .unwrap_or_default(),
                            inner.get(YamlValue::String("timezone".into())).map(scalar),
                        ),
                        other => (scalar(other), None),
                    };
                    return TriggerNode::Schedule { cron, timezone };
                }
                // 无法识别的 trigger 配置由 schema.rs 校验（CFY-SCHEMA-1003/1004）。
                TriggerNode::Manual
            }
            _ => TriggerNode::Manual,
        }
    }

    /// 运行时配置：`runtime: {timeout, max_parallel, retry}`，
    /// 对应 DSL 的 `runtime { ... }` 块（同一 Rust 类型）。
    fn convert_runtime(&mut self, runtime: Option<&YamlValue>) -> crate::ast::RuntimeConfig {
        let mut config = crate::ast::RuntimeConfig::default();
        let Some(value) = runtime else {
            return config;
        };
        let offset = self.locate_yaml_value(value);
        let Some(map) = value.as_mapping() else {
            // runtime 形状由 schema.rs 校验（CFY-SCHEMA-1002），此处回退默认配置。
            return config;
        };
        let get = |keys: &[&str]| {
            keys.iter()
                .find_map(|key| map.get(YamlValue::String((*key).into())))
        };
        if let Some(timeout) = get(&["timeout"]) {
            config.timeout = Some(match timeout {
                YamlValue::String(raw) => TimeoutConfig {
                    raw: raw.clone(),
                    milliseconds: parse_duration_ms(raw),
                    span: self.locator.span_at(self.source, offset),
                },
                YamlValue::Mapping(inner) => {
                    let raw = inner
                        .get(YamlValue::String("duration".into()))
                        .map(scalar)
                        .unwrap_or_default();
                    TimeoutConfig {
                        raw: raw.clone(),
                        milliseconds: parse_duration_ms(&raw),
                        span: self.locator.span_at(self.source, offset),
                    }
                }
                // runtime.timeout 形状由 schema.rs 校验（CFY-SCHEMA-1002/1004）。
                _ => TimeoutConfig {
                    raw: String::new(),
                    milliseconds: 0,
                    span: self.locator.span_at(self.source, offset),
                },
            });
        }
        if let Some(max_parallel) = get(&["max_parallel"]) {
            config.max_parallel = max_parallel.as_u64().map(|count| count.max(1) as u32);
        }
        if let Some(retry) = get(&["retry"]) {
            config.retry = self.convert_retry(retry);
        }
        config
    }

    /// 把单个 YAML step 值转换为（FlowNode, 锚点偏移）。
    fn convert_step(&mut self, step_value: &YamlValue) -> (Option<FlowNode>, usize) {
        let Some(map) = step_value.as_mapping() else {
            // 非对象步骤由 schema.rs 校验（CFY-SCHEMA-1002），此处尽力丢弃该条。
            let _ = self.locate_yaml_value(step_value);
            return (None, 0);
        };
        let id_offset = self.locate_key(map, "id");
        let id = self.mapping_scalar(map, "id").unwrap_or_default();

        // 控制类字段：parallel / switch / foreach。
        if let Some(parallel) = map.get(YamlValue::String("parallel".into())) {
            return (self.convert_parallel(&id, parallel), id_offset);
        }
        if let Some(switch) = map.get(YamlValue::String("switch".into())) {
            return (self.convert_switch(&id, switch), id_offset);
        }
        if let Some(foreach) = map.get(YamlValue::String("foreach".into())) {
            return (self.convert_foreach(&id, foreach), id_offset);
        }

        // 普通 step。
        let step_id = if id.is_empty() {
            self.synthesize_id("yaml_step")
        } else {
            id.clone()
        };
        let mut step = StepNode {
            id: step_id.clone(),
            span: self.locator.span_at(self.source, id_offset),
            ..Default::default()
        };
        self.mark_used(step_id.clone());

        step.name = self.mapping_scalar(map, "name");

        let action_value = map.get(YamlValue::String("action".into()));
        if let Some(action_value) = action_value {
            step.action = Some(self.convert_action(action_value));
        } else if let Some(workflow_value) = map.get(YamlValue::String("workflow".into())) {
            // 子工作流引用（预留语义）：编译为 provider=workflow 的任务节点。
            let action_offset = self.locate_yaml_value(workflow_value);
            let header = scalar(workflow_value);
            let parts = header.split('.').collect::<Vec<_>>();
            step.action = Some(ActionNode {
                provider: "workflow".into(),
                service: parts.first().map(|part| (*part).to_string()),
                method: if parts.len() > 1 {
                    Some(parts[1..].join("."))
                } else {
                    None
                },
                span: self.locator.span_at(self.source, action_offset),
                ..Default::default()
            });
        } else if map.get(YamlValue::String("approval".into())).is_none() {
            // approval 步骤允许缺省 action（下方补 approval.request）。
            // 缺少 action 由 schema.rs 校验（CFY-SCHEMA-1001），此处丢弃该步骤保住尽力构建。
            return (None, id_offset);
        }

        // 参数（input: / with:）。
        for arg_key in ["input", "with"] {
            if let Some(args) = map.get(YamlValue::String(arg_key.into())) {
                // input/with 形状由 schema.rs 校验（CFY-SCHEMA-1002），此处跳过参数。
                if let Some(args_map) = args.as_mapping() {
                    for (key, value) in args_map {
                        if let Some(action) = step.action.as_mut() {
                            action
                                .arguments
                                .insert(scalar(key), self.value_from_yaml(value));
                        }
                    }
                }
                break;
            }
        }

        let output_value = map.get(YamlValue::String("output".into()));
        if let Some(output) = output_value {
            step.output = self.convert_output_decl(output).map(|(name, _)| name);
        }

        // when / condition / if（GitHub-Actions 旧版别名）。
        if let Some(condition_value) = map
            .get(YamlValue::String("when".into()))
            .or_else(|| map.get(YamlValue::String("condition".into())))
            .or_else(|| map.get(YamlValue::String("if".into())))
        {
            let text = scalar(condition_value);
            if !text.trim().is_empty() {
                let offset = self.locate_yaml_value(condition_value);
                step.condition = self.expression_from_text(&text, offset).ok();
            }
        }

        // depends / depends_on（支持字符串或数组）。
        if let Some(depends) = map
            .get(YamlValue::String("depends".into()))
            .or_else(|| map.get(YamlValue::String("depends_on".into())))
        {
            let deps = self.string_list(depends);
            for dep in deps {
                if let Some(expanded) = self.aliases.get(&dep) {
                    step.depends_on.extend(expanded.iter().cloned());
                } else {
                    step.depends_on.push(dep);
                }
            }
        }

        // retry。
        if let Some(retry) = map.get(YamlValue::String("retry".into())) {
            step.retry = self.convert_retry(retry);
        }

        // timeout / on_timeout。
        if let Some(timeout) = map.get(YamlValue::String("timeout".into())) {
            let timeout_offset = self.locate_yaml_value(timeout);
            match timeout {
                YamlValue::Mapping(inner) => {
                    let duration = inner.get(YamlValue::String("duration".into()));
                    if let Some(duration_value) = duration {
                        let raw = scalar(duration_value);
                        step.timeout = Some(TimeoutConfig {
                            raw: raw.clone(),
                            milliseconds: parse_duration_ms(&raw),
                            span: self.locator.span_at(self.source, timeout_offset),
                        });
                    }
                    if let Some(on_timeout) = inner.get(YamlValue::String("on_timeout".into())) {
                        step.on_timeout = Some(scalar(on_timeout));
                    }
                }
                YamlValue::String(raw) => {
                    step.timeout = Some(TimeoutConfig {
                        raw: raw.clone(),
                        milliseconds: parse_duration_ms(raw),
                        span: self.locator.span_at(self.source, timeout_offset),
                    });
                }
                // timeout 形状由 schema.rs 校验（CFY-SCHEMA-1002/1004）。
                _ => {}
            }
        }

        // on_error。
        if let Some(on_error) = map.get(YamlValue::String("on_error".into())) {
            self.convert_on_error(&mut step, on_error);
        }

        // approval（审批）→ approval.request 动作步骤（与 DSL file_approval 对齐）。
        if let Some(approval) = map.get(YamlValue::String("approval".into())) {
            if let Some(inner) = approval.as_mapping() {
                for (key, value) in inner {
                    if let Some(action) = step.action.as_mut() {
                        action
                            .arguments
                            .insert(scalar(key), self.value_from_yaml(value));
                    }
                }
            } else if step.action.is_none() {
                // approval 形状由 schema.rs 校验（CFY-SCHEMA-1002）。
            }
            if step.action.is_none() {
                step.action = Some(ActionNode {
                    provider: "builtin".into(),
                    service: Some("approval".into()),
                    method: Some("request".into()),
                    span: step.span,
                    ..Default::default()
                });
            }
            if step.output.is_none() {
                step.output = Some("approval".into());
            }
        }

        (Some(FlowNode::Step(Box::new(step))), id_offset)
    }

    fn convert_parallel(&mut self, id: &str, parallel: &YamlValue) -> Option<FlowNode> {
        let offset = self.locate_yaml_value(parallel);
        let branches_value = match parallel {
            YamlValue::Sequence(_) => parallel.clone(),
            YamlValue::Mapping(inner) => match inner.get(YamlValue::String("tasks".into())) {
                Some(tasks) => tasks.clone(),
                // parallel 缺 tasks 由 schema.rs 校验（CFY-SCHEMA-1001），此处尽力丢弃。
                None => return None,
            },
            // parallel 形状由 schema.rs 校验（CFY-SCHEMA-1002），此处尽力丢弃。
            _ => return None,
        };
        let max_concurrency = if let YamlValue::Mapping(inner) = parallel {
            inner
                .get(YamlValue::String("max_concurrency".into()))
                .and_then(|v| v.as_u64())
        } else {
            None
        };
        let mut branches = Vec::new();
        let mut branch_ids = Vec::new();
        if let YamlValue::Sequence(items) = branches_value {
            for item in items {
                if let (Some(branch), _) = self.convert_step(&item) {
                    if let Some(id) = flow_id(&branch) {
                        branch_ids.push(id);
                    }
                    branches.push(branch);
                }
            }
        }
        if !id.is_empty() {
            self.aliases.insert(id.to_string(), branch_ids.clone());
        }
        Some(FlowNode::Parallel(ParallelNode {
            branches,
            max_concurrency: max_concurrency.map(|value| value as u32),
            span: self.locator.span_at(self.source, offset),
        }))
    }

    fn convert_foreach(&mut self, id: &str, foreach: &YamlValue) -> Option<FlowNode> {
        let offset = self.locate_yaml_value(foreach);
        let Some(map) = foreach.as_mapping() else {
            // foreach 形状由 schema.rs 校验（CFY-SCHEMA-1002），此处尽力丢弃。
            return None;
        };
        let iterator = map
            .get(YamlValue::String("item".into()))
            .map(scalar)
            .unwrap_or_else(|| "item".into());
        let collection_text = map
            .get(YamlValue::String("collection".into()))
            .or_else(|| map.get(YamlValue::String("in".into())))
            .map(scalar)
            .unwrap_or_default();
        let collection = self
            .expression_from_text(&collection_text, offset)
            .unwrap_or_else(|_| placeholder_expression(self.source, offset));
        let do_value = map.get(YamlValue::String("do".into()));
        let mut body = Vec::new();
        if let Some(do_map) = do_value {
            // foreach.do 形状由 schema.rs 校验（CFY-SCHEMA-1002），此处忽略非法形状。
            if let (Some(FlowNode::Step(mut step)), _) = self.convert_step(do_map) {
                if step.id.is_empty() || (!id.is_empty() && step.id.starts_with("yaml_step_auto")) {
                    step.id = self
                        .unique_id(&format!("{}_item", if id.is_empty() { "loop" } else { id }));
                } else {
                    self.mark_used(step.id.clone());
                }
                let body_id = step.id.clone();
                self.aliases.insert(
                    if id.is_empty() {
                        "loop".into()
                    } else {
                        id.to_string()
                    },
                    vec![body_id],
                );
                body.push(FlowNode::Step(step));
            }
        } else {
            // foreach 缺 do 由 schema.rs 校验（CFY-SCHEMA-1001）。
        }
        Some(FlowNode::Loop(LoopNode {
            iterator: iterator.to_string(),
            collection,
            body,
            span: self.locator.span_at(self.source, offset),
        }))
    }

    fn convert_switch(&mut self, id: &str, switch: &YamlValue) -> Option<FlowNode> {
        let offset = self.locate_yaml_value(switch);
        let Some(map) = switch.as_mapping() else {
            // switch 形状由 schema.rs 校验（CFY-SCHEMA-1002），此处尽力丢弃。
            return None;
        };
        let subject_text = map
            .get(YamlValue::String("expression".into()))
            .map(scalar)
            .unwrap_or_default();
        let subject = self
            .expression_from_text(&subject_text, offset)
            .unwrap_or_else(|_| placeholder_expression(self.source, offset));
        let mut cases = Vec::new();
        let mut case_ids = Vec::new();
        if let Some(cases_map) = map
            .get(YamlValue::String("cases".into()))
            .and_then(YamlValue::as_mapping)
        {
            for (case_key, case_value) in cases_map {
                let key_text = scalar(case_key);
                let case_offset = self.locate_yaml_value(case_value);
                let case_id = self.unique_id(&format!(
                    "{}_case_{}",
                    if id.is_empty() { "switch" } else { id },
                    sanitize_id(&key_text)
                ));
                if let (Some(FlowNode::Step(mut step)), _) = self.convert_step(case_value) {
                    if step.id.is_empty() || step.id.starts_with("yaml_step_auto") {
                        step.id = case_id.clone();
                    } else {
                        self.mark_used(step.id.clone());
                    }
                    case_ids.push(step.id.clone());
                    cases.push(SwitchCase {
                        value: ValueNode::Enum(key_text.clone()),
                        body: vec![FlowNode::Step(step)],
                        span: self.locator.span_at(self.source, case_offset),
                    });
                }
            }
        }
        let mut default_branch = Vec::new();
        if let Some(default_value) = map.get(YamlValue::String("default".into())) {
            if let (Some(FlowNode::Step(mut step)), _) = self.convert_step(default_value) {
                if step.id.is_empty() || step.id.starts_with("yaml_step_auto") {
                    step.id = self.unique_id(&format!(
                        "{}_default",
                        if id.is_empty() { "switch" } else { id }
                    ));
                } else {
                    self.mark_used(step.id.clone());
                }
                case_ids.push(step.id.clone());
                default_branch.push(FlowNode::Step(step));
            }
        }
        if !id.is_empty() {
            self.aliases.insert(id.to_string(), case_ids);
        }
        Some(FlowNode::Switch(SwitchNode {
            subject,
            cases,
            default_branch,
            span: self.locator.span_at(self.source, offset),
        }))
    }

    fn convert_catch_entry(&mut self, entry: &YamlValue) -> Option<FlowNode> {
        let Some(map) = entry.as_mapping() else {
            // catch 条目形状由 schema.rs 校验（CFY-SCHEMA-1002），此处尽力丢弃。
            let _ = self.locate_yaml_value(entry);
            return None;
        };
        let action = map
            .get(YamlValue::String("action".into()))
            .or_else(|| map.get(YamlValue::String("workflow".into())));
        let Some(action) = action else {
            // catch 缺 action 由 schema.rs 校验（CFY-SCHEMA-1001），此处尽力丢弃。
            let _ = self.locate_yaml_value(entry);
            return None;
        };
        let action_offset = self.locate_yaml_value(action);
        let id = self.unique_id(&format!("catch_{}", self.used_ids.len()));
        let mut step = StepNode {
            id,
            action: Some(self.convert_action(action)),
            span: self.locator.span_at(self.source, action_offset),
            ..Default::default()
        };
        if let Some(args) = map.get(YamlValue::String("input".into())) {
            if let Some(args_map) = args.as_mapping() {
                for (key, value) in args_map {
                    if let Some(action_node) = step.action.as_mut() {
                        action_node
                            .arguments
                            .insert(scalar(key), self.value_from_yaml(value));
                    }
                }
            }
        }
        Some(FlowNode::Step(Box::new(step)))
    }

    fn convert_action(&mut self, value: &YamlValue) -> ActionNode {
        let action_offset = self.locate_yaml_value(value);
        let span = self.locator.span_at(self.source, action_offset);
        match value {
            YamlValue::String(header) => {
                let mut action = ActionNode {
                    provider: "builtin".into(),
                    span,
                    ..Default::default()
                };
                // 能力引用冒号形式（GitHub-Actions 风格，对接能力 Hub）：
                //   plugin:<plugin_id>:<function>[@<version>]
                //   builtin:<service>.<method>   /   api:<service>.<method>
                if let Some((provider, rest)) = header.split_once(':') {
                    match provider {
                        "plugin" => {
                            action.provider = "plugin".into();
                            let mut segments = rest.split(':');
                            if let (Some(id), Some(function_with_version)) =
                                (segments.next(), segments.next())
                            {
                                action.plugin_id = Some(id.trim().to_owned());
                                let (function, version) =
                                    split_action_version(function_with_version);
                                action.function = Some(function);
                                action.version = version;
                            } else {
                                action.function = Some(rest.trim().to_owned());
                            }
                            return action;
                        }
                        "builtin" | "api" => {
                            action.provider = provider.into();
                            let mut dotted = rest.split('.');
                            if let Some(service) = dotted.next() {
                                action.service = Some(service.trim().to_owned());
                            }
                            let method = dotted.collect::<Vec<_>>().join(".");
                            if !method.is_empty() {
                                action.method = Some(method);
                            }
                            return action;
                        }
                        _ => {}
                    }
                }
                let parts = header.split('.').collect::<Vec<_>>();
                if header.starts_with("plugin.") && parts.len() >= 2 {
                    action.provider = "plugin".into();
                    action.plugin_id = Some(parts[1].into());
                    action.function = Some(parts[2..].join("."));
                } else if parts.len() >= 3 && matches!(parts[0], "builtin" | "api") {
                    action.provider = parts[0].into();
                    action.service = Some(parts[1].into());
                    action.method = Some(parts[2..].join("."));
                } else if parts.len() >= 2 {
                    action.provider = "builtin".into();
                    action.service = Some(parts[0].into());
                    action.method = Some(parts[1..].join("."));
                } else {
                    action.provider = header.clone();
                }
                action
            }
            YamlValue::Mapping(map) => {
                let provider = map
                    .get(YamlValue::String("provider".into()))
                    .map(scalar)
                    .unwrap_or_else(|| "builtin".into());
                let mut action = ActionNode {
                    provider,
                    span,
                    ..Default::default()
                };
                for (field, slot) in [
                    ("service", &mut action.service),
                    ("method", &mut action.method),
                    ("id", &mut action.plugin_id),
                    ("function", &mut action.function),
                    ("version", &mut action.version),
                ] {
                    if let Some(value) = map.get(YamlValue::String(field.into())) {
                        *slot = Some(scalar(value));
                    }
                }
                if let Some(args) = map
                    .get(YamlValue::String("arguments".into()))
                    .or_else(|| map.get(YamlValue::String("with".into())))
                    .or_else(|| map.get(YamlValue::String("input".into())))
                {
                    if let Some(args_map) = args.as_mapping() {
                        for (key, value) in args_map {
                            action
                                .arguments
                                .insert(scalar(key), self.value_from_yaml(value));
                        }
                    }
                }
                action
            }
            _ => ActionNode {
                provider: "builtin".into(),
                span,
                ..Default::default()
            },
        }
    }

    fn convert_retry(&mut self, retry: &YamlValue) -> Option<RetryNode> {
        let retry_offset = self.locate_yaml_value(retry);
        let span = self.locator.span_at(self.source, retry_offset);
        let mut max_attempts = 1u32;
        let mut strategy = "fixed".to_string();
        match retry {
            YamlValue::Number(number) => {
                max_attempts = number.as_u64().unwrap_or(1).max(1) as u32;
            }
            YamlValue::String(value) => {
                if let Ok(parsed) = value.parse::<u32>() {
                    max_attempts = parsed.max(1);
                } else {
                    // retry 字符串非法由 schema.rs 校验（CFY-SCHEMA-1004），此处尽力丢弃。
                    let _ = self.locate_yaml_value(retry);
                    return None;
                }
            }
            YamlValue::Mapping(map) => {
                for count_key in ["count", "max", "max_attempts", "attempts"] {
                    if let Some(value) = map.get(YamlValue::String(count_key.into())) {
                        max_attempts = value.as_u64().unwrap_or(1).max(1) as u32;
                        break;
                    }
                }
                let strategy_value = map.get(YamlValue::String("strategy".into()));
                if let Some(value) = strategy_value {
                    strategy = match scalar(value).to_ascii_lowercase().as_str() {
                        "exponential" => "exponential".into(),
                        "linear" => "linear".into(),
                        _ => "fixed".into(),
                    };
                }
            }
            // retry 形状由 schema.rs 校验（CFY-SCHEMA-1002），此处尽力丢弃。
            _ => {
                let _ = self.locate_yaml_value(retry);
                return None;
            }
        }
        Some(RetryNode {
            max_attempts,
            strategy,
            span,
        })
    }

    fn convert_on_error(&mut self, step: &mut StepNode, on_error: &YamlValue) {
        let Some(map) = on_error.as_mapping() else {
            // on_error 形状由 schema.rs 校验（CFY-SCHEMA-1002），此处忽略。
            let _ = self.locate_yaml_value(on_error);
            return;
        };
        if let Some(retry) = map.get(YamlValue::String("retry".into())) {
            if step.retry.is_none() {
                step.retry = self.convert_retry(retry);
            }
        }
        if let Some(fallback) = map.get(YamlValue::String("fallback".into())) {
            let fallback_offset = self.locate_yaml_value(fallback);
            let fb_span = self.locator.span_at(self.source, fallback_offset);
            let fallback_action = match fallback {
                YamlValue::String(header) => {
                    let mut action = ActionNode {
                        provider: "builtin".into(),
                        span: fb_span,
                        ..Default::default()
                    };
                    let parts = header.split('.').collect::<Vec<_>>();
                    if parts.len() >= 2 {
                        action.service = Some(parts[0].into());
                        action.method = Some(parts[1..].join("."));
                    } else {
                        action.provider = header.clone();
                    }
                    action
                }
                YamlValue::Mapping(inner) => {
                    // 形如 {action: notify.admin, input: {...}} 的完整 fallback 步骤。
                    if let Some(action_value) = inner.get(YamlValue::String("action".into())) {
                        self.convert_action(action_value)
                    } else {
                        ActionNode {
                            provider: "builtin".into(),
                            service: Some("notify".into()),
                            method: Some("admin".into()),
                            span: fb_span,
                            ..Default::default()
                        }
                    }
                }
                _ => return,
            };
            let fb_step = StepNode {
                id: self.unique_id(&format!("{}_on_error", step.id)),
                action: Some(fallback_action),
                span: fb_span,
                ..Default::default()
            };
            step.on_error.push(FlowNode::Step(Box::new(fb_step)));
        }
    }

    fn input_decls(&mut self, document: &YamlDocument) -> Vec<NamedDecl> {
        let source_map = document.inputs.as_ref().or(document.input.as_ref());
        let mut result = Vec::new();
        if let Some(map) = source_map {
            for (name, value) in map {
                let offset = self.locate_scalar(name);
                let decl = self.parse_decl(value);
                result.push(NamedDecl {
                    name: name.clone(),
                    type_name: decl.type_name.or_else(|| match value {
                        YamlValue::String(text) if !text.is_empty() && shorthand_is_type(text) => {
                            Some(text.clone())
                        }
                        _ => None,
                    }),
                    required: decl.required,
                    default: decl.default,
                    offset,
                });
            }
        }
        result
    }

    fn parse_decl(&mut self, value: &YamlValue) -> DeclParts {
        let mut decl = DeclParts::default();
        if let YamlValue::Mapping(map) = value {
            if let Some(v) = map.get(YamlValue::String("type".into())) {
                decl.type_name = Some(scalar(v));
            }
            if let Some(v) = map.get(YamlValue::String("required".into())) {
                decl.required = v.as_bool().unwrap_or(false);
            }
            if let Some(v) = map.get(YamlValue::String("default".into())) {
                decl.default = Some(v.clone());
            }
            if let Some(v) = map.get(YamlValue::String("expression".into())) {
                decl.expression = Some(v.clone());
            }
        }
        decl
    }

    fn value_from_yaml(&mut self, value: &YamlValue) -> ValueNode {
        match value {
            YamlValue::Null => ValueNode::Null,
            YamlValue::Bool(v) => ValueNode::Boolean(*v),
            YamlValue::Number(v) => {
                let text = v.to_string();
                let number = serde_json::from_str::<serde_json::Number>(&text)
                    .unwrap_or_else(|_| serde_json::Number::from(0));
                ValueNode::Number(number)
            }
            YamlValue::String(text) => {
                let offset = self.locate_scalar(text);
                self.string_to_value(text, offset)
            }
            YamlValue::Sequence(items) => ValueNode::Array(
                items
                    .iter()
                    .map(|item| self.value_from_yaml(item))
                    .collect(),
            ),
            YamlValue::Mapping(map) => {
                let mut result = BTreeMap::new();
                for (key, item) in map {
                    result.insert(scalar(key), self.value_from_yaml(item));
                }
                ValueNode::Object(result)
            }
            _ => ValueNode::String(String::new()),
        }
    }

    /// 字符串标量 → 领域值：`${{ ... }}` 整串交给表达式子系统；含插值走字符串模板
    /// （`parse_interpolated_value`）；裸引用（vars./steps./input./env./workflow. 或点分路径）
    /// 解析为 $ref；其余为字符串。
    fn string_to_value(&mut self, text: &str, offset: usize) -> ValueNode {
        let trimmed = text.trim();
        if let Some((inner, opens)) = whole_expression_index(trimmed) {
            let inner_offset = offset.saturating_add(opens);
            if let Ok(expression) = self.expression_from_inner(inner, inner_offset) {
                return crate::expression::value_from_expression(expression);
            }
            return ValueNode::Enum(trimmed.into());
        }
        if text.contains("${{") {
            let normalized = normalize_refs(text);
            // 表达式子系统唯一插值实现：`${{ ... }}`（GitHub-Actions 风格）。
            if let Some(value) = crate::expression::parse_interpolated_value(
                &normalized,
                self.source,
                self.filename,
                offset,
            ) {
                return value;
            }
            return match crate::expression::parse_value_string(
                &normalized,
                self.source,
                self.filename,
                offset,
            ) {
                Ok(value) => value,
                Err(diagnostic) => {
                    self.diagnostics.push(*diagnostic);
                    ValueNode::String(text.into())
                }
            };
        }
        if looks_like_reference(text) {
            let normalized = normalize_refs(text);
            if let Ok(value) = crate::expression::parse_value_string(
                &normalized,
                self.source,
                self.filename,
                offset,
            ) {
                return value;
            }
        }
        ValueNode::String(text.into())
    }

    /// 表达式上下文：条件/when/foreach 集合/switch subject。
    /// 传入的文本可以是 `${{ ... }}` 包裹或裸表达式；先切出再交给子系统。
    fn expression_from_text(&mut self, text: &str, offset: usize) -> Result<ExpressionNode, ()> {
        let trimmed = text.trim();
        let (inner, base) = match whole_expression_index(trimmed) {
            Some((inner, opens)) => (inner, offset.saturating_add(opens)),
            None => (trimmed, offset),
        };
        self.expression_from_inner(inner, base)
    }

    /// 已切出的表达式文本 → 表达式子系统（供 whole-expression / 裸表达式 / 插值段共用）。
    fn expression_from_inner(&mut self, inner: &str, base: usize) -> Result<ExpressionNode, ()> {
        let inner = inner.trim();
        if inner.is_empty() {
            return Err(());
        }
        let normalized = normalize_refs(inner);
        match crate::expression::parse_expression_string(
            &normalized,
            self.source,
            self.filename,
            base,
        ) {
            Ok(expression) => Ok(expression),
            Err(diagnostic) => {
                self.diagnostics.push(*diagnostic);
                Err(())
            }
        }
    }

    fn expression_as_value(&mut self, value: &YamlValue, offset: usize) -> ValueNode {
        let text = scalar(value);
        if let Ok(expression) = self.expression_from_text(&text, offset) {
            crate::expression::value_from_expression(expression)
        } else {
            ValueNode::String(text)
        }
    }

    fn convert_output_decl(&mut self, output: &YamlValue) -> Option<(String, Option<ValueNode>)> {
        match output {
            YamlValue::String(name) => Some((name.clone(), None)),
            YamlValue::Mapping(map) => {
                // {result: {type: object}} / {result: <expr>}：取首个键作为输出名。
                let (key, value) = map.iter().next()?;
                Some((scalar(key), Some(self.value_from_yaml(value))))
            }
            _ => None,
        }
    }

    fn mapping_scalar(&self, map: &serde_yaml_ng::Mapping, key: &str) -> Option<String> {
        map.get(YamlValue::String(key.into()))
            .map(scalar)
            .filter(|value| !value.is_empty())
    }

    fn string_list(&mut self, value: &YamlValue) -> Vec<String> {
        match value {
            YamlValue::String(text) => text
                .split(|c: char| c == ',' || c.is_whitespace())
                .filter(|s| !s.is_empty())
                .map(str::to_owned)
                .collect(),
            YamlValue::Sequence(items) => items.iter().map(scalar).collect(),
            _ => vec![],
        }
    }

    fn locate_scalar(&mut self, needle: &str) -> usize {
        self.locator.locate(self.source, needle)
    }

    fn locate_yaml_value(&mut self, value: &YamlValue) -> usize {
        match value {
            YamlValue::String(text) => self.locate_scalar(text),
            YamlValue::Number(number) => self.locate_scalar(&number.to_string()),
            YamlValue::Bool(v) => self.locate_scalar(if *v { "true" } else { "false" }),
            YamlValue::Sequence(items) => items
                .first()
                .map(|item| self.locate_yaml_value(item))
                .unwrap_or(0),
            YamlValue::Mapping(map) => map
                .iter()
                .next()
                .map(|(key, _)| self.locate_yaml_value(key))
                .unwrap_or(0),
            _ => 0,
        }
    }

    fn locate_key(&mut self, map: &serde_yaml_ng::Mapping, key: &str) -> usize {
        match map.keys().find(|candidate| scalar(candidate) == key) {
            Some(key_value) => self.locate_yaml_value(key_value),
            None => self.locate_scalar(key),
        }
    }

    fn synthesize_id(&mut self, prefix: &str) -> String {
        self.unique_id(&format!("{prefix}_auto_{}", self.used_ids.len()))
    }

    fn unique_id(&mut self, base: &str) -> String {
        if base.is_empty() {
            return self.synthesize_id("yaml_step");
        }
        if !self.used_ids.contains(base) {
            self.used_ids.insert(base.to_owned());
            return base.to_owned();
        }
        let mut index = 2;
        loop {
            let candidate = format!("{base}_{index}");
            if !self.used_ids.contains(&candidate) {
                self.used_ids.insert(candidate.clone());
                return candidate;
            }
            index += 1;
        }
    }

    fn mark_used(&mut self, id: String) {
        self.used_ids.insert(id);
    }
}

#[derive(Default, Clone)]
struct DeclParts {
    type_name: Option<String>,
    required: bool,
    default: Option<YamlValue>,
    expression: Option<YamlValue>,
}

struct NamedDecl {
    name: String,
    type_name: Option<String>,
    required: bool,
    default: Option<YamlValue>,
    offset: usize,
}

fn sanitize_id(value: &str) -> String {
    value
        .chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() || c == '_' {
                c
            } else {
                '_'
            }
        })
        .collect()
}

fn flow_id(node: &FlowNode) -> Option<String> {
    match node {
        FlowNode::Step(step) => Some(step.id.clone()),
        _ => None,
    }
}

/// 标量文本（构建字符串，兼容数字/布尔/字符串）。
pub(crate) fn scalar(value: &YamlValue) -> String {
    match value {
        YamlValue::String(text) => text.clone(),
        YamlValue::Number(number) => number.to_string(),
        YamlValue::Bool(true) => "true".into(),
        YamlValue::Bool(false) => "false".into(),
        _ => String::new(),
    }
}

/// 把动作串 `function@version` 拆为 `(function, version)`（旧版插件调用，
/// 如 `plugin:<id>:generate_report@1` → function=generate_report, version=1）。
fn split_action_version(text: &str) -> (String, Option<String>) {
    match text.split_once('@') {
        Some((function, version)) => (function.to_owned(), Some(version.to_owned())),
        None => (text.to_owned(), None),
    }
}

/// 整串表达式包裹匹配：GitHub-Actions 风格 `${{ expr }}`（开括 3）。
/// 返回 `(内层表达式, 开括长度)`；不匹配返回 None。CloudFlow YAML 只定义 `${{ }}`
/// 一种表达式分隔符（需求 6.32），不再接受 `${ }`。
fn whole_expression_index(text: &str) -> Option<(&str, usize)> {
    let trimmed = text.trim();
    if trimmed.starts_with("${{") {
        let tail = trimmed.strip_prefix("${{")?;
        if let Some(inner) = tail.strip_suffix("}}") {
            let inner = inner.trim();
            if !inner.is_empty() && !inner.contains("${{") {
                return Some((inner, 3));
            }
        }
        return None;
    }
    None
}

/// 把 `steps.<id>.<field>`（field 不是 output）规范化为 `steps.<id>.output.<field>`，
/// 并把 `input.<name>` 规范化为 `vars.<name>`（与 DSL 输入变量表示一致，保证 IR 等价）。
fn normalize_refs(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    let bytes = text.as_bytes();
    let mut index = 0;
    while index < bytes.len() {
        if bytes[index..].starts_with(b"input.") && (index == 0 || !is_ident_char(bytes[index - 1]))
        {
            out.push_str("vars.");
            index += b"input.".len();
            continue;
        }
        if bytes[index..].starts_with(b"steps.") && (index == 0 || !is_ident_char(bytes[index - 1]))
        {
            let prefix_end = index + b"steps.".len();
            let rest = &text[prefix_end..];
            let segments = rest.split('.').collect::<Vec<_>>();
            if segments.len() >= 2 && segments[1] != "output" {
                out.push_str("steps.");
                out.push_str(segments[0]);
                out.push_str(".output.");
                out.push_str(&segments[1..].join("."));
                break;
            }
            out.push_str("steps.");
            index = prefix_end;
            continue;
        }
        let character = text[index..].chars().next().unwrap_or('\0');
        out.push(character);
        index += character.len_utf8();
    }
    out
}

fn is_ident_char(byte: u8) -> bool {
    byte.is_ascii_alphanumeric() || byte == b'_' || byte == b'-'
}

/// 看起来像引用的字符串（裸引用/点分路径），而不是普通文本。
fn looks_like_reference(text: &str) -> bool {
    let trimmed = text.trim();
    if trimmed.is_empty()
        || trimmed.contains(' ')
        || trimmed == "true"
        || trimmed == "false"
        || trimmed == "null"
    {
        return false;
    }
    if trimmed
        .bytes()
        .all(|byte| byte.is_ascii_digit() || byte == b'.')
    {
        return false;
    }
    let first = trimmed.as_bytes()[0];
    if !(first.is_ascii_alphabetic() || first == b'_') {
        return false;
    }
    trimmed
        .bytes()
        .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'-' | b'.'))
}

/// 输入简写是否为类型名（file/string/number/boolean/array/object/long/…）。
fn shorthand_is_type(text: &str) -> bool {
    matches!(
        text,
        "string"
            | "number"
            | "boolean"
            | "bool"
            | "array"
            | "object"
            | "file"
            | "long"
            | "int"
            | "json"
    )
}

/// 根据 YAML 产生值的领域类型（供本地变量缺省类型推导）。
fn infer_yaml_value_type(value: &ValueNode) -> String {
    match value {
        ValueNode::String(_)
        | ValueNode::Duration(_)
        | ValueNode::Enum(_)
        | ValueNode::Template(_) => "string".into(),
        ValueNode::Number(_) => "number".into(),
        ValueNode::Boolean(_) => "boolean".into(),
        ValueNode::Null => "null".into(),
        ValueNode::Array(_) => "array".into(),
        ValueNode::Object(_) => "object".into(),
        ValueNode::VariableRef(_) | ValueNode::Expression(_) | ValueNode::Call { .. } => {
            "string".into()
        }
    }
}

fn placeholder_expression(source: &str, offset: usize) -> ExpressionNode {
    let span = crate::diagnostic::line_column(source, offset.min(source.len()));
    ExpressionNode {
        kind: ExpressionKind::Literal(ValueNode::Boolean(false)),
        span: Span {
            start: offset.min(source.len()),
            end: offset.min(source.len()) + 1,
            line: span.0,
            column: span.1,
            end_line: span.0,
            end_column: span.1 + 1,
        },
    }
}

pub(crate) fn parse_duration_ms(value: &str) -> u64 {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return 0;
    }
    let unit_len = if trimmed.ends_with("ms") { 2 } else { 1 };
    let split = trimmed.len().saturating_sub(unit_len);
    let (number, unit) = trimmed.split_at(split);
    let base = number.parse::<u64>().unwrap_or(0);
    match unit {
        "ms" => base,
        "s" => base * 1000,
        "m" => base * 60_000,
        "h" => base * 3_600_000,
        "d" => base * 86_400_000,
        _ => 0,
    }
}
