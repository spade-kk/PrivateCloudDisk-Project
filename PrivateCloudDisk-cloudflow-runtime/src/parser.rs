//! Pest PEG 解析器：只做语法结构转换，不在此处执行能力或权限判断。
//!
//! [CLOUDFLOW-STRICT-001] 未知块通过专用恢复规则聚合为诊断，绝不静默进入 AST/IR。

use crate::ast::*;
use crate::diagnostic::Diagnostic;
use pest::error::InputLocation;
use pest::iterators::Pair;
use pest::Parser;
use pest_derive::Parser;
use serde_json::Number;
use std::collections::{BTreeMap, HashSet};

#[derive(Parser)]
#[grammar = "grammar.pest"]
pub struct CloudFlowParser;

pub fn parse_source(source: &str, filename: &str) -> Result<WorkflowNode, Box<Diagnostic>> {
    let (workflow, diagnostics) = parse_source_detailed(source, filename)?;
    diagnostics
        .into_iter()
        .next()
        .map_or(Ok(workflow), |diagnostic| Err(Box::new(diagnostic)))
}

pub fn parse_source_detailed(
    source: &str,
    filename: &str,
) -> Result<(WorkflowNode, Vec<Diagnostic>), Box<Diagnostic>> {
    if source.len() > 256 * 1024 {
        return Err(Box::new(Diagnostic::new(
            "CF1104",
            "LEXICAL_ERROR",
            "CloudFlow 源码不能超过 256 KiB",
            source,
            filename,
            0,
            source.len(),
            vec![],
            Some("请拆分工作流或减少内嵌资源".into()),
        )));
    }
    let mut pairs = CloudFlowParser::parse(Rule::file, source).map_err(|error| {
        let (start, end) = match error.location {
            InputLocation::Pos(position) => (position, position.saturating_add(1)),
            InputLocation::Span((start, end)) => (start, end.max(start + 1)),
        };
        let message = classify_pest_error(source, start, &error.to_string());
        Box::new(Diagnostic::new(
            message.0,
            "SYNTAX_ERROR",
            message.1,
            source,
            filename,
            start,
            end,
            message.2,
            Some("请使用小写 CloudFlow 关键字，并检查双引号、大括号和表达式".into()),
        ))
    })?;
    let file = pairs.next().expect("Pest file rule always has a child");
    let workflow = file.into_inner().next().expect("workflow is required");
    Ok(parse_workflow(workflow, source, filename))
}

fn parse_workflow(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
) -> (WorkflowNode, Vec<Diagnostic>) {
    let span = span_of(&pair, source);
    let mut inner = pair.into_inner();
    let name = string_text(inner.next().expect("workflow name"));
    let body = inner.next().expect("workflow body");
    let mut diagnostics = Vec::new();
    let mut node = WorkflowNode {
        name,
        namespace: None,
        environment: vec![],
        audit: None,
        step_groups: vec![],
        module_defaults: BTreeMap::new(),
        includes: vec![],
        metadata: MetadataNode::default(),
        variables: vec![],
        trigger: TriggerNode::Manual,
        runtime: RuntimeConfig::default(),
        flow: vec![],
        steps: vec![],
        controls: vec![],
        handlers: vec![],
        span,
    };
    for item in body.into_inner() {
        match item.as_rule() {
            Rule::include_decl => {
                let span = span_of(&item, source);
                if let Some(path) = item.into_inner().next().map(string_text) {
                    node.includes.push(IncludeNode {
                        path,
                        alias: None,
                        span,
                    });
                }
            }
            Rule::import_decl => node.includes.push(parse_import_decl(&item, source)),
            Rule::environment_block => {
                node.environment = parse_environment(item, source, filename, &mut diagnostics);
            }
            Rule::namespace_decl => {
                node.namespace = item.into_inner().next().map(|p| p.as_str().to_owned());
            }
            Rule::tag_decl => {
                if let Some(tag) = item.into_inner().next().map(string_text) {
                    node.metadata.tags.push(tag);
                }
            }
            Rule::metadata_block => {
                node.metadata = parse_metadata(item, source, filename, &mut diagnostics)
            }
            Rule::audit_block => node.audit = parse_audit(item, source, filename, &mut diagnostics),
            Rule::variables_block => {
                node.variables = parse_variables(item, source, filename, &mut diagnostics)
            }
            Rule::trigger_block => {
                node.trigger = parse_trigger(item, source, filename, &mut diagnostics)
            }
            Rule::runtime_block => {
                node.runtime = parse_runtime(item, source, filename, &mut diagnostics)
            }
            Rule::steps_block => {
                let mut flow = Vec::new();
                parse_nodes_into(
                    item.into_inner().next().expect("steps body"),
                    source,
                    filename,
                    &mut diagnostics,
                    &mut flow,
                );
                for value in flow {
                    append_top_level_flow(&mut node, value);
                }
            }
            Rule::handlers_block => {
                for handler in item.into_inner() {
                    node.handlers
                        .push(parse_handler(handler, source, filename, &mut diagnostics));
                }
            }
            Rule::step_group_decl => {
                let group = parse_step_group(item, source, filename, &mut diagnostics);
                node.step_groups.push(group.clone());
                append_top_level_flow(&mut node, FlowNode::StepGroup(group));
            }
            Rule::step_decl => append_top_level_flow(
                &mut node,
                FlowNode::Step(Box::new(parse_step(
                    item,
                    source,
                    filename,
                    &mut diagnostics,
                ))),
            ),
            Rule::on_failure_block => {
                node.handlers
                    .push(parse_handler(item, source, filename, &mut diagnostics))
            }
            Rule::if_decl
            | Rule::foreach_decl
            | Rule::for_decl
            | Rule::while_decl
            | Rule::parallel_decl
            | Rule::try_decl
            | Rule::wait_decl
            | Rule::assert_decl
            | Rule::switch_decl
            | Rule::delay_decl
            | Rule::validate_decl
            | Rule::expect_decl
            | Rule::notify_decl
            | Rule::return_decl
            | Rule::break_decl
            | Rule::continue_decl => {
                if let Some(control) = parse_flow_node(item, source, filename, &mut diagnostics) {
                    append_top_level_flow(&mut node, control);
                }
            }
            Rule::unknown_top_block => diagnostics.push(unknown_keyword(
                &item,
                source,
                filename,
                &[
                    "metadata",
                    "include",
                    "variables",
                    "trigger",
                    "runtime",
                    "steps",
                    "handlers",
                    "step",
                    "on_failure",
                ],
                "工作流顶层块",
            )),
            _ => {}
        }
    }
    (node, diagnostics)
}

fn parse_nodes_into(
    body: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
    flow: &mut Vec<FlowNode>,
) {
    for item in body.into_inner() {
        match item.as_rule() {
            Rule::step_decl => flow.push(FlowNode::Step(Box::new(parse_step(
                item,
                source,
                filename,
                diagnostics,
            )))),
            Rule::unknown_node_block => diagnostics.push(unknown_keyword(
                &item,
                source,
                filename,
                &[
                    "step", "if", "foreach", "while", "parallel", "try", "wait", "assert",
                ],
                "流程节点",
            )),
            _ => {
                if let Some(control) = parse_flow_node(item, source, filename, diagnostics) {
                    flow.push(control);
                }
            }
        }
    }
}

/// [CLOUDFLOW-ORDER-001] 语义分析尚使用历史 `steps`/`controls` 聚合视图；这里在不破坏
/// 既有分析代码的前提下同步保留源码顺序视图。后续所有编译路径必须使用 `flow`。
fn append_top_level_flow(workflow: &mut WorkflowNode, flow: FlowNode) {
    match &flow {
        FlowNode::Step(step) => workflow.steps.push((**step).clone()),
        _ => workflow.controls.push(flow.clone()),
    }
    workflow.flow.push(flow);
}

fn parse_metadata(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> MetadataNode {
    let mut metadata = MetadataNode {
        span: span_of(&pair, source),
        ..Default::default()
    };
    let mut seen = HashSet::new();
    for entry in pair.into_inner() {
        let mut fields = entry.clone().into_inner();
        let Some(key) = fields.next() else { continue };
        let Some(value) = fields.next() else { continue };
        if !seen.insert(key.as_str().to_owned()) {
            diagnostics.push(duplicate_field(
                key.as_str(),
                &entry,
                source,
                filename,
                "metadata",
            ));
            continue;
        }
        match key.as_str() {
            "display_name" => metadata.display_name = value_as_string(&value),
            "description" => metadata.description = value_as_string(&value),
            "version" => metadata.version = value_as_string(&value),
            "author" => metadata.author = value_as_string(&value),
            "tags" => metadata.tags = value_as_string_array(value),
            "changelog" => metadata.changelog = value_as_string(&value),
            unknown => diagnostics.push(unknown_field(
                unknown,
                &entry,
                source,
                filename,
                &[
                    "display_name",
                    "description",
                    "version",
                    "author",
                    "tags",
                    "changelog",
                ],
                "metadata",
            )),
        }
    }
    metadata
}

fn parse_variables(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> Vec<VariableDecl> {
    let mut names = HashSet::new();
    pair.into_inner()
        .filter_map(|entry| {
            let span = span_of(&entry, source);
            let mut fields = entry.into_inner();
            let name = fields.next()?.as_str().to_owned();
            if !names.insert(name.clone()) {
                diagnostics.push(Diagnostic::new(
                    "CF2001",
                    "SEMANTIC_ERROR",
                    format!("变量重复声明：{name}"),
                    source,
                    filename,
                    span.start,
                    span.end,
                    vec![],
                    Some("variables 块中的变量名必须唯一".into()),
                ));
            }
            let mut explicit_type = None;
            let mut initializer = None;
            for field in fields {
                match field.as_rule() {
                    Rule::variable_type_annotation => {
                        explicit_type = field
                            .into_inner()
                            .next()
                            .map(|value| value.as_str().to_owned());
                    }
                    Rule::variable_initializer => initializer = Some(field),
                    _ => {}
                }
            }
            let mut required = false;
            let mut default = None;
            let (source_kind, type_name) = if let Some(initializer) = initializer {
                let value = initializer.into_inner().next()?;
                if value.as_rule() == Rule::input_decl {
                    let mut input_fields = value.into_inner();
                    let inferred = input_fields.next()?.as_str().to_owned();
                    let mut argument_names = HashSet::new();
                    if let Some(args) = input_fields.next() {
                        for arg in args.into_inner() {
                            let argument_span = span_of(&arg, source);
                            let mut arg_fields = arg.clone().into_inner();
                            let Some(key) = arg_fields.next() else {
                                continue;
                            };
                            let Some(value) = arg_fields.next() else {
                                continue;
                            };
                            if !argument_names.insert(key.as_str().to_owned()) {
                                diagnostics.push(Diagnostic::new(
                                    "CF1206",
                                    "SYNTAX_ERROR",
                                    format!("input.{} 参数重复：{}", inferred, key.as_str()),
                                    source,
                                    filename,
                                    argument_span.start,
                                    argument_span.end,
                                    vec![],
                                    Some("每个输入参数只能声明一次".into()),
                                ));
                                continue;
                            }
                            match key.as_str() {
                                "required" => {
                                    let parsed =
                                        value_from_expression(parse_expression(value, source));
                                    if !matches!(parsed, ValueNode::Boolean(_)) {
                                        diagnostics.push(Diagnostic::new(
                                            "CF2101",
                                            "TYPE_ERROR",
                                            "required 只能使用 boolean 字面量",
                                            source,
                                            filename,
                                            argument_span.start,
                                            argument_span.end,
                                            vec![
                                                "required = true".into(),
                                                "required = false".into(),
                                            ],
                                            None,
                                        ));
                                    }
                                    required = matches!(parsed, ValueNode::Boolean(true));
                                }
                                "default" => {
                                    default =
                                        Some(value_from_expression(parse_expression(value, source)))
                                }
                                unknown => diagnostics.push(unknown_field(
                                    unknown,
                                    &arg,
                                    source,
                                    filename,
                                    &["required", "default"],
                                    "input",
                                )),
                            }
                        }
                    }
                    if let Some(explicit) = explicit_type.as_ref() {
                        if explicit != &inferred {
                            diagnostics.push(Diagnostic::new(
                                "CF2101",
                                "TYPE_ERROR",
                                format!(
                                    "变量 {name} 的显式类型 {explicit} 与 input.{inferred} 不一致"
                                ),
                                source,
                                filename,
                                span.start,
                                span.end,
                                vec![],
                                Some("input 的类型必须与变量显式类型一致".into()),
                            ));
                        }
                    }
                    (VariableSource::Input, inferred)
                } else {
                    let value = value_from_expression(parse_expression(value, source));
                    let inferred = explicit_type.unwrap_or_else(|| infer_value_type(&value));
                    default = Some(value);
                    (VariableSource::Local, inferred)
                }
            } else if let Some(explicit) = explicit_type {
                (VariableSource::Deferred, explicit)
            } else {
                diagnostics.push(Diagnostic::new(
                    "CF1301",
                    "AST_ERROR",
                    format!("变量 {name} 缺少类型或初始值"),
                    source,
                    filename,
                    span.start,
                    span.end,
                    vec!["name: string".into(), "name = \"value\"".into()],
                    Some("未初始化变量必须使用显式类型；推断变量必须提供初始值".into()),
                ));
                (VariableSource::Deferred, "unknown".into())
            };
            Some(VariableDecl {
                name,
                type_name,
                required,
                source: source_kind,
                default,
                span,
            })
        })
        .collect()
}

fn parse_trigger(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> TriggerNode {
    let Some(item) = pair.into_inner().next() else {
        return TriggerNode::Manual;
    };
    match item.as_rule() {
        Rule::manual_trigger => TriggerNode::Manual,
        Rule::schedule_trigger => {
            let values = parse_checked_assignments(
                item,
                source,
                filename,
                diagnostics,
                &["cron", "timezone"],
                "schedule",
            );
            TriggerNode::Schedule {
                cron: values
                    .get("cron")
                    .and_then(value_node_string)
                    .unwrap_or_default(),
                timezone: values.get("timezone").and_then(value_node_string),
            }
        }
        Rule::event_trigger => {
            let values =
                parse_checked_assignments(item, source, filename, diagnostics, &["name"], "event");
            TriggerNode::Event {
                name: values
                    .get("name")
                    .and_then(value_node_string)
                    .unwrap_or_default(),
            }
        }
        Rule::http_trigger => {
            // [V1.2-WEBHOOK] http 触发支持 path 与 method 详配。
            let values = parse_checked_assignments(
                item,
                source,
                filename,
                diagnostics,
                &["path", "method"],
                "http",
            );
            TriggerNode::Http {
                path: values
                    .get("path")
                    .and_then(value_node_string)
                    .unwrap_or_default(),
                method: values
                    .get("method")
                    .and_then(value_node_string)
                    .map(|value| value.to_uppercase()),
            }
        }
        // [V1.2-INTERVAL-TRIGGER] trigger { interval = 5m }。
        Rule::interval_trigger => {
            let raw = item.into_inner().next().map(|v| v.as_str().to_owned());
            TriggerNode::Interval {
                raw: raw.clone().unwrap_or_default(),
                milliseconds: raw.as_deref().map(duration_millis).unwrap_or(0),
            }
        }
        Rule::unknown_trigger_block => {
            diagnostics.push(unknown_keyword(
                &item,
                source,
                filename,
                &["schedule", "event", "http", "manual", "interval"],
                "trigger",
            ));
            TriggerNode::Manual
        }
        _ => TriggerNode::Manual,
    }
}

/// [V1.2-AUDIT] 解析工作流审计注解 audit { level = "high"; description = "..." }。
fn parse_audit(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> Option<AuditAnnotation> {
    let span = span_of(&pair, source);
    let mut level = String::new();
    let mut description = None;
    for item in pair.into_inner() {
        let mut fields = item.clone().into_inner();
        if let (Some(key), Some(value)) = (fields.next(), fields.next()) {
            match key.as_str() {
                "level" => {
                    level = value_node_string(&parse_value(value, source)).unwrap_or_default()
                }
                "description" => description = value_node_string(&parse_value(value, source)),
                other => diagnostics.push(unknown_field(
                    other,
                    &item,
                    source,
                    filename,
                    &["level", "description"],
                    "audit",
                )),
            }
        }
    }
    Some(AuditAnnotation {
        level,
        description,
        span,
    })
}

fn parse_runtime(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> RuntimeConfig {
    let mut runtime = RuntimeConfig::default();
    let mut seen = HashSet::new();
    for item in pair.into_inner() {
        let field = match item.as_rule() {
            Rule::runtime_timeout => Some("timeout"),
            Rule::max_parallel_assignment => Some("max_parallel"),
            Rule::retry_policy_block => Some("retry_policy"),
            _ => None,
        };
        if field.is_some_and(|value| !seen.insert(value)) {
            diagnostics.push(duplicate_field(
                field.unwrap_or_default(),
                &item,
                source,
                filename,
                "runtime",
            ));
            continue;
        }
        match item.as_rule() {
            Rule::runtime_timeout => {
                runtime.timeout = item.into_inner().next().map(|v| parse_timeout(v, source))
            }
            Rule::max_parallel_assignment => {
                runtime.max_parallel = item
                    .into_inner()
                    .next()
                    .and_then(|v| v.as_str().parse().ok())
            }
            Rule::retry_policy_block => {
                runtime.retry = item
                    .into_inner()
                    .next()
                    .map(|body| parse_retry_body(body, source, filename, diagnostics))
            }
            Rule::unknown_runtime_block | Rule::unknown_runtime_assignment => {
                diagnostics.push(unknown_keyword(
                    &item,
                    source,
                    filename,
                    &["timeout", "max_parallel", "retry_policy"],
                    "runtime",
                ))
            }
            _ => {}
        }
    }
    runtime
}

/// [V1.2-STEP-GROUP] 解析 `step group <id> { step ... }`；组内只允许嵌套 step_decl。
fn parse_step_group(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> StepGroupNode {
    let span = span_of(&pair, source);
    let mut inner = pair.into_inner();
    let id = inner
        .next()
        .map(|p| p.as_str().to_owned())
        .unwrap_or_default();
    let body = inner.next();
    let mut steps = Vec::new();
    if let Some(body) = body {
        for item in body.into_inner() {
            if item.as_rule() == Rule::step_decl {
                steps.push(parse_step(item, source, filename, diagnostics));
            }
        }
    }
    StepGroupNode { id, steps, span }
}

fn parse_step(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> StepNode {
    let span = span_of(&pair, source);
    let mut inner = pair.into_inner();
    let id = inner.next().expect("step id").as_str().to_owned();
    let body = inner.next().expect("step body");
    let mut step = StepNode {
        id,
        span,
        ..Default::default()
    };
    let mut seen = HashSet::new();
    for item in body.into_inner() {
        let unique_field = match item.as_rule() {
            Rule::step_name => Some("name"),
            Rule::action_decl => Some("action"),
            Rule::depends_decl => Some("depends_on"),
            Rule::condition_decl => Some("condition"),
            Rule::retry_decl => Some("retry"),
            Rule::retry_on_decl => Some("retry_on"),
            Rule::on_error_decl => Some("on_error"),
            Rule::output_decl => Some("output"),
            Rule::timeout_decl => Some("timeout"),
            Rule::timeout_block => Some("timeout"),
            Rule::use_decl => Some("use"),
            _ => None,
        };
        if unique_field.is_some_and(|value| !seen.insert(value)) {
            diagnostics.push(duplicate_field(
                unique_field.unwrap_or_default(),
                &item,
                source,
                filename,
                "step",
            ));
            continue;
        }
        match item.as_rule() {
            Rule::step_name => step.name = item.into_inner().next().map(string_text),
            Rule::action_decl => step.action = Some(parse_action(item, source)),
            Rule::depends_decl => {
                // [V1.2-COND-DEPENDS] depends_on 可选 `if <bool 表达式>`；表达式对应当前
                // 依赖的条件依赖配置，ident 则加入静态依赖列表。
                for p in item.into_inner() {
                    if p.as_rule() == Rule::ident {
                        step.depends_on.push(p.as_str().to_owned());
                    } else if p.as_rule() == Rule::expression {
                        step.depends_condition = Some(parse_expression(p, source));
                    }
                }
            }
            Rule::condition_decl => {
                if let Some(expr) = item
                    .into_inner()
                    .next()
                    .and_then(|body| body.into_inner().next())
                {
                    step.condition = Some(parse_expression(expr, source));
                }
            }
            Rule::retry_decl => {
                step.retry = item
                    .into_inner()
                    .next()
                    .map(|body| parse_retry_body(body, source, filename, diagnostics))
            }
            Rule::output_decl => {
                step.output = item.into_inner().next().map(|p| p.as_str().to_owned())
            }
            Rule::timeout_decl => {
                step.timeout = item.into_inner().next().map(|v| parse_timeout(v, source))
            }
            Rule::retry_on_decl => {
                step.retry_on = item
                    .into_inner()
                    .map(|v| {
                        if v.as_rule() == Rule::string_value {
                            string_text(v)
                        } else {
                            v.as_str().to_owned()
                        }
                    })
                    .collect();
            }
            Rule::timeout_block => {
                let (timeout, on_timeout) =
                    parse_timeout_block(item, source, filename, diagnostics);
                step.timeout = timeout;
                step.on_timeout = on_timeout;
            }
            // [V1.2-ON_ERROR] 步骤级错误处理块，解析为独立节点列表。
            Rule::on_error_decl => {
                if let Some(body) = item.into_inner().next() {
                    step.on_error = parse_flow_nodes(body, source, filename, diagnostics);
                }
            }
            // [V1.2-USE-WITH] use/with <alias>：模块默认参数注入来源别名。
            Rule::use_decl => {
                step.use_alias = item.into_inner().next().map(|p| p.as_str().to_owned());
            }
            Rule::unknown_step_block | Rule::unknown_step_assignment => {
                diagnostics.push(unknown_keyword(
                    &item,
                    source,
                    filename,
                    &[
                        "name",
                        "action",
                        "depends_on",
                        "condition",
                        "retry",
                        "output",
                        "timeout",
                        "on_error",
                        "if",
                        "foreach",
                        "while",
                        "parallel",
                        "try",
                        "wait",
                        "assert",
                    ],
                    "step",
                ))
            }
            _ => {
                if let Some(control) = parse_flow_node(item, source, filename, diagnostics) {
                    step.controls.push(control);
                }
            }
        }
    }
    if step.action.is_none() && step.controls.is_empty() {
        diagnostics.push(Diagnostic::new(
            "CF1301",
            "AST_ERROR",
            format!("步骤 {} 缺少 action 或控制流节点", step.id),
            source,
            filename,
            span.start,
            span.end,
            vec!["action".into()],
            Some("普通步骤必须声明 action；编排步骤可以包含显式控制流节点".into()),
        ));
    }
    step
}

fn parse_action(pair: Pair<'_, Rule>, source: &str) -> ActionNode {
    let span = span_of(&pair, source);
    let mut inner = pair.into_inner();
    let header = inner
        .next()
        .map(|p| p.as_str().to_owned())
        .unwrap_or_default();
    let mut action = ActionNode {
        provider: "builtin".into(),
        span,
        ..Default::default()
    };
    if header == "plugin" {
        action.provider = "plugin".into();
    } else if let Some(plugin) = header.strip_prefix("plugin.") {
        let parts = plugin.split('.').collect::<Vec<_>>();
        if parts.len() >= 2 {
            action.provider = "plugin".into();
            action.plugin_id = Some(parts[0].into());
            action.function = Some(parts[1..].join("."));
        } else {
            action.provider = header;
        }
    } else {
        let parts = header.split('.').collect::<Vec<_>>();
        if parts.len() >= 3 && matches!(parts[0], "builtin" | "api") {
            action.provider = parts[0].into();
            action.service = Some(parts[1].into());
            action.method = Some(parts[2..].join("."));
        } else if parts.len() >= 2 {
            action.service = Some(parts[0].into());
            action.method = Some(parts[1..].join("."));
        } else {
            action.provider = header;
        }
    }
    if let Some(body) = inner.next() {
        action.arguments = parse_action_body(body, source);
    }
    if action.provider == "plugin" {
        action.plugin_id = action
            .arguments
            .get("id")
            .and_then(value_node_string)
            .or(action.plugin_id);
        action.function = action
            .arguments
            .get("function")
            .and_then(value_node_string)
            .or(action.function);
        action.version = action.arguments.get("version").and_then(value_node_string);
    }
    action
}

fn parse_action_body(pair: Pair<'_, Rule>, source: &str) -> BTreeMap<String, ValueNode> {
    let mut map = BTreeMap::new();
    for item in pair.into_inner() {
        match item.as_rule() {
            Rule::action_assignment => {
                let mut fields = item.into_inner();
                if let (Some(key), Some(value)) = (fields.next(), fields.next()) {
                    map.insert(
                        key.as_str().to_owned(),
                        value_from_expression(parse_expression(value, source)),
                    );
                }
            }
            Rule::action_object => {
                let mut fields = item.into_inner();
                if let (Some(key), Some(body)) = (fields.next(), fields.next()) {
                    map.insert(
                        key.as_str().to_owned(),
                        ValueNode::Object(parse_action_body(body, source)),
                    );
                }
            }
            Rule::call_stmt => {
                let value = parse_call(item.clone(), source);
                map.insert(format!("$call:{}.{}", map.len(), item.as_str()), value);
            }
            _ => {}
        }
    }
    map
}

fn parse_retry_body(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> RetryNode {
    let span = span_of(&pair, source);
    let mut max_attempts = 1;
    let mut strategy = "fixed".to_owned();
    let mut seen = HashSet::new();
    for item in pair.into_inner() {
        let field = match item.as_rule() {
            Rule::retry_max_attempts => Some("max_attempts"),
            Rule::retry_strategy | Rule::retry_backoff => Some("strategy"),
            _ => None,
        };
        if field.is_some_and(|value| !seen.insert(value)) {
            diagnostics.push(duplicate_field(
                field.unwrap_or_default(),
                &item,
                source,
                filename,
                "retry",
            ));
            continue;
        }
        match item.as_rule() {
            Rule::retry_max_attempts => {
                max_attempts = item
                    .into_inner()
                    .next()
                    .and_then(|p| p.as_str().parse().ok())
                    .unwrap_or(1)
            }
            Rule::retry_strategy | Rule::retry_backoff => {
                strategy = item
                    .into_inner()
                    .next()
                    .map(value_text)
                    .unwrap_or_else(|| "fixed".into())
            }
            Rule::unknown_retry_assignment => diagnostics.push(unknown_keyword(
                &item,
                source,
                filename,
                &["max_attempts", "strategy", "backoff"],
                "retry",
            )),
            _ => {}
        }
    }
    RetryNode {
        max_attempts,
        strategy,
        span,
    }
}

fn parse_handler(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> HandlerNode {
    let span = span_of(&pair, source);
    let body = pair.into_inner().next();
    let nodes = body
        .map(|value| parse_flow_nodes(value, source, filename, diagnostics))
        .unwrap_or_default();
    HandlerNode {
        id: "on_failure".into(),
        nodes,
        span,
    }
}

fn parse_flow_nodes(
    body: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> Vec<FlowNode> {
    let mut nodes = Vec::new();
    for item in body.into_inner() {
        if item.as_rule() == Rule::unknown_node_block {
            diagnostics.push(unknown_keyword(
                &item,
                source,
                filename,
                &[
                    "step", "if", "foreach", "while", "parallel", "try", "wait", "assert",
                ],
                "流程节点",
            ));
        } else if let Some(node) = parse_flow_node(item, source, filename, diagnostics) {
            nodes.push(node);
        }
    }
    nodes
}

fn parse_flow_node(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> Option<FlowNode> {
    let span = span_of(&pair, source);
    match pair.as_rule() {
        Rule::step_decl => Some(FlowNode::Step(Box::new(parse_step(
            pair,
            source,
            filename,
            diagnostics,
        )))),
        Rule::if_decl => {
            let mut fields = pair.into_inner();
            let expression = fields
                .next()?
                .into_inner()
                .next()
                .map(|v| parse_expression(v, source))?;
            let true_branch = parse_flow_nodes(fields.next()?, source, filename, diagnostics);
            let false_branch = fields
                .next()
                .map(|v| parse_flow_nodes(v, source, filename, diagnostics))
                .unwrap_or_default();
            Some(FlowNode::Condition(ConditionNode {
                expression,
                true_branch,
                false_branch,
                span,
            }))
        }
        Rule::foreach_decl => {
            let mut fields = pair.into_inner();
            let iterator = fields.next()?.as_str().to_owned();
            let collection = parse_expression(fields.next()?, source);
            let body = parse_flow_nodes(fields.next()?, source, filename, diagnostics);
            Some(FlowNode::Loop(LoopNode {
                iterator,
                collection,
                body,
                span,
            }))
        }
        // [V1.2-FOR] 识别 range(from,to) 为索引循环；其余表达式按集合元素迭代。
        Rule::for_decl => {
            let mut fields = pair.into_inner();
            let iterator = fields.next()?.as_str().to_owned();
            let collection = parse_expression(fields.next()?, source);
            let body = parse_flow_nodes(fields.next()?, source, filename, diagnostics);
            let mut range_from = None;
            let mut range_to = None;
            let mut collection = Some(collection);
            if let ExpressionKind::Call {
                function,
                arguments,
            } = &collection.as_ref().unwrap().kind
            {
                if function == "range" && arguments.len() == 2 {
                    range_from = Some(arguments[0].clone());
                    range_to = Some(arguments[1].clone());
                    collection = None;
                }
            }
            Some(FlowNode::For(ForNode {
                iterator,
                range_from,
                range_to,
                collection,
                body,
                span,
            }))
        }
        // [V1.2-BREAK-CONTINUE] 循环控制语句；语义层负责校验其位于循环体内。
        Rule::break_decl => Some(FlowNode::Break(BreakNode { span })),
        Rule::continue_decl => Some(FlowNode::Continue(ContinueNode { span })),
        Rule::while_decl => {
            let mut fields = pair.into_inner();
            let condition = fields
                .next()?
                .into_inner()
                .next()
                .map(|value| parse_expression(value, source))?;
            let body = parse_flow_nodes(fields.next()?, source, filename, diagnostics);
            Some(FlowNode::While(WhileNode {
                condition,
                body,
                span,
            }))
        }
        Rule::parallel_decl => {
            // [V1.2-PARALLEL] 可选分支级并发限制 parallel(max_concurrency=3) { ... }。
            let mut max_concurrency = None;
            let mut branches = Vec::new();
            for field in pair.into_inner() {
                match field.as_rule() {
                    Rule::parallel_opt => {
                        for option in field.into_inner() {
                            // parallel_option = { "max_concurrency" ~ "=" ~ number }，
                            // 仅捕获 number 一个 token。
                            if let Some(number) = option.into_inner().next() {
                                max_concurrency = number.as_str().parse::<u32>().ok();
                            }
                        }
                    }
                    _ => branches = parse_flow_nodes(field, source, filename, diagnostics),
                }
            }
            Some(FlowNode::Parallel(ParallelNode {
                branches,
                max_concurrency,
                span,
            }))
        }
        Rule::try_decl => {
            let mut fields = pair.into_inner();
            let try_nodes = parse_flow_nodes(fields.next()?, source, filename, diagnostics);
            let mut catch_binding = None;
            let mut catch_nodes = Vec::new();
            let mut finally_nodes = Vec::new();
            for clause in fields {
                match clause.as_rule() {
                    Rule::catch_clause => {
                        let mut catch_fields = clause.into_inner();
                        let first = catch_fields.next();
                        let body = if first
                            .as_ref()
                            .is_some_and(|value| value.as_rule() == Rule::ident)
                        {
                            catch_binding = first.map(|value| value.as_str().to_owned());
                            catch_fields.next()
                        } else {
                            first
                        };
                        if let Some(body) = body {
                            catch_nodes = parse_flow_nodes(body, source, filename, diagnostics);
                        }
                    }
                    Rule::finally_clause => {
                        if let Some(body) = clause.into_inner().next() {
                            finally_nodes = parse_flow_nodes(body, source, filename, diagnostics);
                        }
                    }
                    _ => {}
                }
            }
            Some(FlowNode::TryCatch(TryCatchNode {
                try_nodes,
                catch_binding,
                catch_nodes,
                finally_nodes,
                span,
            }))
        }
        Rule::wait_decl => {
            let mut fields = pair.into_inner();
            let wait_type = fields.next()?.as_str().to_owned();
            let timeout = fields
                .next()
                .and_then(|decl| decl.into_inner().next())
                .map(|v| parse_timeout(v, source));
            Some(FlowNode::Wait(WaitNode {
                wait_type,
                timeout,
                span,
            }))
        }
        Rule::assert_decl => {
            let expression = pair
                .into_inner()
                .next()?
                .into_inner()
                .next()
                .map(|value| parse_expression(value, source))?;
            Some(FlowNode::Assert(AssertNode {
                condition: expression,
                span,
            }))
        }
        // [V1.2-VALIDATE] validate/expect { <bool 表达式> }：与 assert 一致的结构化表达式块。
        Rule::validate_decl | Rule::expect_decl => {
            let condition = pair
                .into_inner()
                .next()?
                .into_inner()
                .next()
                .map(|value| parse_expression(value, source))?;
            Some(FlowNode::Validate(ValidateNode { condition, span }))
        }
        // [V1.2-NOTIFY] notify { ... } 内建通知。
        Rule::notify_decl => {
            let mut channel = String::new();
            let mut recipient = None;
            let mut message = None;
            for item in pair.into_inner() {
                let mut fields = item.into_inner();
                if let (Some(key), Some(value)) = (fields.next(), fields.next()) {
                    let value = parse_value(value, source);
                    match key.as_str() {
                        "channel" => channel = value_node_string(&value).unwrap_or_default(),
                        "to" => recipient = Some(value),
                        "message" => message = Some(value),
                        _ => {}
                    }
                }
            }
            Some(FlowNode::Notify(NotifyNode {
                channel,
                recipient,
                message,
                span,
            }))
        }
        // [V1.2-RETURN] 步骤级提前返回：return <expr>?。
        Rule::return_decl => {
            let output = pair
                .into_inner()
                .next()
                .map(|value| parse_expression(value, source));
            Some(FlowNode::Return(ReturnNode { output, span }))
        }
        Rule::switch_decl => {
            let mut fields = pair.into_inner();
            let subject = parse_expression(fields.next()?, source);
            let switch_body = fields.next()?;
            let mut cases = Vec::new();
            let mut default_branch = Vec::new();
            let mut seen_default = false;
            for clause in switch_body.into_inner() {
                match clause.as_rule() {
                    Rule::switch_case => {
                        let case_span = span_of(&clause, source);
                        let mut case_fields = clause.into_inner();
                        let value = parse_value(case_fields.next()?, source);
                        let body =
                            parse_flow_nodes(case_fields.next()?, source, filename, diagnostics);
                        cases.push(SwitchCase {
                            value,
                            body,
                            span: case_span,
                        });
                    }
                    Rule::switch_default => {
                        if seen_default {
                            diagnostics.push(Diagnostic::new(
                                "CF4401",
                                "SYNTAX_ERROR",
                                "switch 只允许一个 default 分支",
                                source,
                                filename,
                                clause.as_span().start(),
                                clause.as_span().start() + "default".len(),
                                vec!["删除多余的 default 分支".into()],
                                Some("switch 结构可选 default，但至多一个".into()),
                            ));
                        }
                        seen_default = true;
                        if let Some(body) = clause.into_inner().next() {
                            default_branch = parse_flow_nodes(body, source, filename, diagnostics);
                        }
                    }
                    _ => {}
                }
            }
            Some(FlowNode::Switch(SwitchNode {
                subject,
                cases,
                default_branch,
                span,
            }))
        }
        Rule::delay_decl => {
            let raw = pair.into_inner().next()?.as_str().to_owned();
            Some(FlowNode::Delay(DelayNode {
                milliseconds: duration_millis(&raw),
                raw,
                span,
            }))
        }
        _ => None,
    }
}

fn parse_value(pair: Pair<'_, Rule>, source: &str) -> ValueNode {
    match pair.as_rule() {
        // [V1.2-INTERPOLATION] 含 ${...} 的字符串解析为字符串模板。
        Rule::string_value | Rule::triple_string => parse_string_value(pair),
        Rule::boolean => ValueNode::Boolean(pair.as_str() == "true"),
        Rule::number => ValueNode::Number(
            pair.as_str()
                .parse::<Number>()
                .unwrap_or_else(|_| Number::from(0)),
        ),
        Rule::duration => ValueNode::Duration(pair.as_str().to_owned()),
        Rule::reference => ValueNode::VariableRef(normalize_reference(pair.as_str())),
        Rule::enum_value => ValueNode::Enum(pair.as_str().to_owned()),
        Rule::array => {
            let values = pair
                .into_inner()
                .next()
                .map(|list| {
                    list.into_inner()
                        .map(|value| value_from_expression(parse_expression(value, source)))
                        .collect()
                })
                .unwrap_or_default();
            ValueNode::Array(values)
        }
        Rule::object => {
            let mut values = BTreeMap::new();
            for entry in pair.into_inner() {
                let mut fields = entry.into_inner();
                let Some(key) = fields.next() else { continue };
                let Some(value) = fields.next() else { continue };
                values.insert(
                    if key.as_rule() == Rule::string_value {
                        string_text(key)
                    } else {
                        key.as_str().to_owned()
                    },
                    value_from_expression(parse_expression(value, source)),
                );
            }
            ValueNode::Object(values)
        }
        Rule::call_value => parse_call(pair, source),
        _ => ValueNode::Enum(pair.as_str().to_owned()),
    }
}

fn parse_call(pair: Pair<'_, Rule>, source: &str) -> ValueNode {
    let mut fields = pair.into_inner();
    let function = fields
        .next()
        .map(|p| p.as_str().to_owned())
        .unwrap_or_default();
    let mut positional = Vec::new();
    let mut named = BTreeMap::new();
    if let Some(args) = fields.next() {
        match args.as_rule() {
            Rule::named_argument_list => {
                for arg in args.into_inner() {
                    let mut values = arg.into_inner();
                    if let (Some(key), Some(value)) = (values.next(), values.next()) {
                        named.insert(key.as_str().to_owned(), parse_value(value, source));
                    }
                }
            }
            Rule::value_list => positional.extend(
                args.into_inner()
                    .map(|value| value_from_expression(parse_expression(value, source))),
            ),
            _ => {}
        }
    }
    ValueNode::Call {
        function,
        positional,
        named,
    }
}

/// [V1.2-PIPELINE] 解析单个管道操作：filter(pred) / map(field) / reduce(func)。
fn parse_pipeline_op(pair: &Pair<'_, Rule>, source: &str) -> PipeOp {
    let Some(op) = pair.clone().into_inner().next() else {
        return PipeOp::Reduce("count".into());
    };
    let span = span_of(&op, source);
    match op.as_rule() {
        Rule::pipeline_filter => PipeOp::Filter(Box::new(
            op.into_inner()
                .next()
                .map(|p| parse_expression(p, source))
                .unwrap_or_else(|| literal_expression(ValueNode::Boolean(false), span)),
        )),
        Rule::pipeline_map => PipeOp::Map(op.into_inner().next().map(|p| {
            if p.as_rule() == Rule::string_value {
                string_text(p)
            } else {
                p.as_str().to_owned()
            }
        })),
        Rule::pipeline_reduce => PipeOp::Reduce(
            op.into_inner()
                .next()
                .map(|p| p.as_str().to_owned())
                .unwrap_or_default(),
        ),
        _ => PipeOp::Reduce("count".into()),
    }
}

/// [V1.2-INTERPOLATION] 解析字符串模板；仅将 `${<简单引用>}` 提升为 $ref 段，
/// 复杂占位符按普通文本保留（由调用方决定是否使用）。
fn parse_string_value(pair: Pair<'_, Rule>) -> ValueNode {
    let raw = string_text(pair);
    if !raw.contains("${") {
        return ValueNode::String(raw);
    }
    let mut segments = Vec::new();
    let mut rest = raw.as_str();
    let mut text = String::new();
    while let Some(start) = rest.find("${") {
        text.push_str(&rest[..start]);
        let after = &rest[start + 2..];
        if let Some(end) = after.find('}') {
            let inner = &after[..end];
            if !text.is_empty() {
                segments.push(ValueNode::String(std::mem::take(&mut text)));
            }
            if is_simple_reference(inner) {
                segments.push(ValueNode::VariableRef(inner.to_owned()));
            } else {
                text.push_str("${");
                text.push_str(inner);
                text.push('}');
            }
            rest = &after[end + 1..];
        } else {
            text.push_str(&rest[start..]);
            rest = "";
            break;
        }
    }
    text.push_str(rest);
    if !text.is_empty() {
        segments.push(ValueNode::String(text));
    }
    if segments.is_empty() {
        return ValueNode::String(raw);
    }
    ValueNode::Template(segments)
}

/// 判断 `${...}` 内是否为简单引用（小写点分/下划线/连字符路径）。
fn is_simple_reference(value: &str) -> bool {
    let mut chars = value.chars();
    let first = chars.next().unwrap_or('\0');
    (first.is_ascii_alphabetic() || first == '_')
        && value
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-'))
}

fn parse_expression(pair: Pair<'_, Rule>, source: &str) -> ExpressionNode {
    let span = span_of(&pair, source);
    match pair.as_rule() {
        // [V1.2-PIPELINE] 左折叠管道：<input> | op1 | op2 …。
        Rule::expression => {
            let mut fields = pair.into_inner();
            let first = fields
                .next()
                .map(|p| parse_expression(p, source))
                .unwrap_or_else(|| literal_expression(ValueNode::Boolean(false), span));
            let mut result = first;
            for stage in fields {
                let op = parse_pipeline_op(&stage, source);
                result = ExpressionNode {
                    kind: ExpressionKind::Pipe {
                        input: Box::new(result),
                        op,
                    },
                    span,
                };
            }
            result
        }
        Rule::ternary
        | Rule::logical_or
        | Rule::logical_and
        | Rule::equality
        | Rule::comparison
        | Rule::additive
        | Rule::multiplicative => {
            let is_ternary = pair.as_rule() == Rule::ternary;
            let mut fields = pair.into_inner();
            let first = fields
                .next()
                .map(|p| parse_expression(p, source))
                .unwrap_or_else(|| literal_expression(ValueNode::Boolean(false), span));
            let mut result = first;
            // ternary 的第二、三个元素不带独立 operator pair，单独构建表达式节点。
            if is_ternary {
                if let (Some(when_true), Some(when_false)) = (fields.next(), fields.next()) {
                    return ExpressionNode {
                        kind: ExpressionKind::Ternary {
                            condition: Box::new(result),
                            when_true: Box::new(parse_expression(when_true, source)),
                            when_false: Box::new(parse_expression(when_false, source)),
                        },
                        span,
                    };
                }
            }
            while let (Some(op), Some(right)) = (fields.next(), fields.next()) {
                result = ExpressionNode {
                    kind: ExpressionKind::Binary {
                        operator: op.as_str().to_owned(),
                        left: Box::new(result),
                        right: Box::new(parse_expression(right, source)),
                    },
                    span,
                };
            }
            result
        }
        Rule::unary => {
            let mut fields = pair.into_inner().collect::<Vec<_>>();
            let primary = fields
                .pop()
                .map(|p| parse_expression(p, source))
                .unwrap_or_else(|| literal_expression(ValueNode::Boolean(false), span));
            fields
                .into_iter()
                .rev()
                .fold(primary, |operand, operator| ExpressionNode {
                    kind: ExpressionKind::Unary {
                        operator: operator.as_str().to_owned(),
                        operand: Box::new(operand),
                    },
                    span,
                })
        }
        Rule::primary => pair
            .into_inner()
            .next()
            .map(|p| parse_expression(p, source))
            .unwrap_or_else(|| literal_expression(ValueNode::Boolean(false), span)),
        Rule::function_call => {
            let mut fields = pair.into_inner();
            let function = fields
                .next()
                .map(|p| p.as_str().to_owned())
                .unwrap_or_default();
            let arguments = fields
                .next()
                .map(|list| {
                    list.into_inner()
                        .map(|p| parse_expression(p, source))
                        .collect()
                })
                .unwrap_or_default();
            ExpressionNode {
                kind: ExpressionKind::Call {
                    function,
                    arguments,
                },
                span,
            }
        }
        Rule::reference | Rule::local_ref => ExpressionNode {
            kind: ExpressionKind::Reference(if pair.as_rule() == Rule::local_ref {
                pair.as_str().to_owned()
            } else {
                normalize_reference(pair.as_str())
            }),
            span,
        },
        Rule::string_value
        | Rule::triple_string
        | Rule::boolean
        | Rule::duration
        | Rule::number
        | Rule::array
        | Rule::object => literal_expression(parse_value(pair, source), span),
        _ => {
            let raw = pair.as_str().to_owned();
            pair.into_inner()
                .next()
                .map(|p| parse_expression(p, source))
                .unwrap_or_else(|| literal_expression(ValueNode::Enum(raw), span))
        }
    }
}

/// 仅由一个字面量/引用构成的表达式在 AST 中保留为值；其余表达式显式标记，
/// 从而避免把 `vars.a + 1` 降级为无法执行的字符串。
fn value_from_expression(expression: ExpressionNode) -> ValueNode {
    match expression.kind {
        ExpressionKind::Literal(value) => value,
        ExpressionKind::Reference(reference) => ValueNode::VariableRef(reference),
        ExpressionKind::Pipe { .. } => ValueNode::Expression(Box::new(expression)),
        _ => ValueNode::Expression(Box::new(expression)),
    }
}

fn infer_value_type(value: &ValueNode) -> String {
    match value {
        ValueNode::String(_) | ValueNode::Duration(_) | ValueNode::Enum(_) => "string",
        ValueNode::Template(_) => "string",
        ValueNode::Number(_) => "number",
        ValueNode::Boolean(_) => "boolean",
        ValueNode::Array(_) => "array",
        ValueNode::Object(_) => "object",
        ValueNode::VariableRef(_) | ValueNode::Expression(_) | ValueNode::Call { .. } => "unknown",
    }
    .into()
}

fn literal_expression(value: ValueNode, span: Span) -> ExpressionNode {
    ExpressionNode {
        kind: ExpressionKind::Literal(value),
        span,
    }
}

fn parse_timeout(pair: Pair<'_, Rule>, source: &str) -> TimeoutConfig {
    let raw = pair.as_str().to_owned();
    TimeoutConfig {
        milliseconds: duration_millis(&raw),
        raw,
        span: span_of(&pair, source),
    }
}

fn duration_millis(value: &str) -> u64 {
    let unit_len = if value.ends_with("ms") { 2 } else { 1 };
    let (number, unit) = value.split_at(value.len().saturating_sub(unit_len));
    let base = number.parse::<u64>().unwrap_or_default();
    match unit {
        "ms" => base,
        "s" => base * 1_000,
        "m" => base * 60_000,
        "h" => base * 3_600_000,
        "d" => base * 86_400_000,
        _ => 0,
    }
}

fn parse_import_decl(pair: &Pair<'_, Rule>, source: &str) -> IncludeNode {
    let span = span_of(pair, source);
    let mut fields = pair.clone().into_inner();
    let path = fields.next().map(string_text).unwrap_or_default();
    let alias = fields.next().map(|p| p.as_str().to_owned());
    IncludeNode { path, alias, span }
}

/// [V1.2-ENVIRONMENT] 解析 `environment { KEY = value; ... }` 声明。
fn parse_environment(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> Vec<EnvironmentDecl> {
    let mut env = Vec::new();
    let mut seen = HashSet::new();
    for entry in pair.into_inner() {
        let mut fields = entry.clone().into_inner();
        let Some(key) = fields.next() else { continue };
        let Some(value) = fields.next() else { continue };
        let key_name = key.as_str().to_owned();
        if !seen.insert(key_name.clone()) {
            diagnostics.push(duplicate_field(
                &key_name,
                &entry,
                source,
                filename,
                "environment",
            ));
            continue;
        }
        env.push(EnvironmentDecl {
            key: key_name,
            value: parse_value(value, source),
            span: span_of(&entry, source),
        });
    }
    env
}

/// [V1.2-TIMEOUT-BLOCK] 解析 `timeout { duration = X; on_timeout = Y }`。
fn parse_timeout_block(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) -> (Option<TimeoutConfig>, Option<String>) {
    let mut timeout = None;
    let mut on_timeout = None;
    for item in pair.into_inner() {
        match item.as_rule() {
            Rule::timeout_duration => {
                if let Some(duration) = item.into_inner().next() {
                    timeout = Some(parse_timeout(duration, source));
                }
            }
            Rule::timeout_on_timeout => {
                on_timeout = item.into_inner().next().map(value_text);
            }
            Rule::unknown_timeout_assignment => diagnostics.push(unknown_field(
                first_word(item.as_str()),
                &item,
                source,
                filename,
                &["duration", "on_timeout"],
                "timeout 块",
            )),
            _ => {}
        }
    }
    (timeout, on_timeout)
}

fn parse_checked_assignments(
    pair: Pair<'_, Rule>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
    allowed: &[&str],
    context: &str,
) -> BTreeMap<String, ValueNode> {
    let mut values = BTreeMap::new();
    let mut seen = HashSet::new();
    for item in pair.into_inner() {
        let mut fields = item.clone().into_inner();
        if let (Some(key), Some(value)) = (fields.next(), fields.next()) {
            if !seen.insert(key.as_str().to_owned()) {
                diagnostics.push(duplicate_field(
                    key.as_str(),
                    &item,
                    source,
                    filename,
                    context,
                ));
            } else if !allowed.contains(&key.as_str()) {
                diagnostics.push(unknown_field(
                    key.as_str(),
                    &item,
                    source,
                    filename,
                    allowed,
                    context,
                ));
            } else {
                values.insert(key.as_str().to_owned(), parse_value(value, source));
            }
        }
    }
    values
}

fn duplicate_field(
    field: &str,
    pair: &Pair<'_, Rule>,
    source: &str,
    filename: &str,
    context: &str,
) -> Diagnostic {
    let span = pair.as_span();
    Diagnostic::new(
        "CF1206",
        "SYNTAX_ERROR",
        format!("{context} 中字段 `{field}` 重复定义"),
        source,
        filename,
        span.start(),
        span.start() + field.len().max(1),
        vec![],
        Some("删除重复字段，CloudFlow 不采用后值覆盖前值的隐式行为".into()),
    )
}

fn unknown_keyword(
    pair: &Pair<'_, Rule>,
    source: &str,
    filename: &str,
    allowed: &[&str],
    context: &str,
) -> Diagnostic {
    let keyword = first_word(pair.as_str());
    unknown_field(keyword, pair, source, filename, allowed, context)
}

fn unknown_field(
    keyword: &str,
    pair: &Pair<'_, Rule>,
    source: &str,
    filename: &str,
    allowed: &[&str],
    context: &str,
) -> Diagnostic {
    let span = pair.as_span();
    let suggestions = nearest(keyword, allowed)
        .into_iter()
        .map(str::to_owned)
        .collect::<Vec<_>>();
    Diagnostic::new(
        "CF1202",
        "SYNTAX_ERROR",
        format!("未知的 {context} 关键字 `{keyword}`"),
        source,
        filename,
        span.start(),
        span.start() + keyword.len().max(1),
        suggestions,
        Some(format!("可用关键字：{}", allowed.join("、"))),
    )
}

fn nearest<'a>(value: &str, allowed: &'a [&str]) -> Vec<&'a str> {
    let lower = value.to_ascii_lowercase();
    let mut ranked = allowed
        .iter()
        .map(|candidate| (*candidate, edit_distance(&lower, candidate)))
        .collect::<Vec<_>>();
    ranked.sort_by_key(|(_, distance)| *distance);
    ranked
        .into_iter()
        .take(2)
        .filter(|(_, distance)| *distance <= 4)
        .map(|(value, _)| value)
        .collect()
}

fn edit_distance(left: &str, right: &str) -> usize {
    let mut previous = (0..=right.len()).collect::<Vec<_>>();
    for (i, a) in left.bytes().enumerate() {
        let mut current = vec![i + 1];
        for (j, b) in right.bytes().enumerate() {
            current.push(
                (previous[j + 1] + 1)
                    .min(current[j] + 1)
                    .min(previous[j] + usize::from(a != b)),
            );
        }
        previous = current;
    }
    previous[right.len()]
}

fn classify_pest_error(
    source: &str,
    offset: usize,
    raw: &str,
) -> (&'static str, String, Vec<String>) {
    let token = source
        .get(offset..)
        .and_then(|tail| tail.split_whitespace().next())
        .unwrap_or("");
    if token.chars().next().is_some_and(char::is_uppercase) {
        return (
            "CF1202",
            format!("CloudFlow 关键字大小写错误：`{token}`"),
            vec![token.to_ascii_lowercase()],
        );
    }
    if raw.contains("string_value") || raw.contains("triple_string") {
        return (
            "CF1102",
            "字符串必须使用配对的双引号或三引号".into(),
            vec!["\"text\"".into()],
        );
    }
    if source[offset.min(source.len())..].contains('{')
        && !source[offset.min(source.len())..].contains('}')
    {
        return ("CF1203", "块的大括号未闭合".into(), vec!["}".into()]);
    }
    (
        "CF1201",
        "CloudFlow 语法不完整或存在非法 token".into(),
        vec![],
    )
}

fn normalize_reference(value: &str) -> String {
    if value.starts_with("vars.") || value.starts_with("steps.") || value.starts_with("workflow.") {
        value.to_owned()
    } else {
        format!("steps.{value}")
    }
}

fn first_word(value: &str) -> &str {
    value
        .trim_start()
        .split(|character: char| character.is_whitespace() || character == '{' || character == '=')
        .next()
        .unwrap_or("")
}

fn value_as_string(pair: &Pair<'_, Rule>) -> Option<String> {
    matches!(pair.as_rule(), Rule::string_value | Rule::triple_string)
        .then(|| string_text(pair.clone()))
}

fn value_as_string_array(pair: Pair<'_, Rule>) -> Vec<String> {
    if pair.as_rule() != Rule::array {
        return vec![];
    }
    pair.into_inner()
        .next()
        .map(|list| {
            list.into_inner()
                .filter_map(|value| value_as_string(&value))
                .collect()
        })
        .unwrap_or_default()
}

fn value_node_string(value: &ValueNode) -> Option<String> {
    match value {
        ValueNode::String(value) | ValueNode::Enum(value) | ValueNode::Duration(value) => {
            Some(value.clone())
        }
        _ => None,
    }
}

fn value_text(pair: Pair<'_, Rule>) -> String {
    match pair.as_rule() {
        Rule::string_value | Rule::triple_string => string_text(pair),
        _ => pair.as_str().to_owned(),
    }
}

fn string_text(pair: Pair<'_, Rule>) -> String {
    let raw = pair.as_str();
    if raw.starts_with("\"\"\"") {
        raw.trim_start_matches("\"\"\"")
            .trim_end_matches("\"\"\"")
            .trim()
            .into()
    } else {
        serde_json::from_str(raw).unwrap_or_else(|_| raw.trim_matches('"').to_owned())
    }
}

fn span_of(pair: &Pair<'_, Rule>, source: &str) -> Span {
    let span = pair.as_span();
    let (line, column) = crate::diagnostic::line_column(source, span.start());
    let (end_line, end_column) = crate::diagnostic::line_column(source, span.end());
    Span {
        start: span.start(),
        end: span.end(),
        line,
        column,
        end_line,
        end_column,
    }
}
