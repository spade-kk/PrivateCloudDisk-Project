//! Pest PEG 解析器：只做语法结构转换，不在此处执行能力或权限判断。

use crate::ast::*;
use crate::diagnostic::Diagnostic;
use pest::error::InputLocation;
use pest::iterators::Pair;
use pest::Parser;
use pest_derive::Parser;
use serde_json::{Map, Value};

#[derive(Parser)]
#[grammar = "grammar.pest"]
pub struct CloudFlowParser;

pub fn parse_source(source: &str, filename: &str) -> Result<WorkflowNode, Diagnostic> {
    if source.len() > 256 * 1024 {
        return Err(Diagnostic::new(
            "CF1104",
            "LEXICAL_ERROR",
            "CloudFlow 源码不能超过 256 KiB",
            source,
            filename,
            0,
            source.len(),
            vec![],
            Some("请拆分工作流或减少内嵌资源".into()),
        ));
    }
    let mut pairs = CloudFlowParser::parse(Rule::file, source).map_err(|error| {
        let offset = match error.location {
            InputLocation::Pos(position) => position,
            InputLocation::Span((position, _)) => position,
        };
        Diagnostic::new(
            "CF1201",
            "SYNTAX_ERROR",
            format!("无法解析 CloudFlow：{error}"),
            source,
            filename,
            offset,
            offset + 1,
            vec![],
            Some("请参考 CLOUDFLOW_DEMO_DESIGN.md 的块结构语法".into()),
        )
    })?;
    let file = pairs.next().expect("Pest file rule always has a child");
    let workflow = file.into_inner().next().expect("workflow is required");
    parse_workflow(workflow, source, filename)
}

fn parse_workflow(
    pair: Pair<'_, Rule>,
    source: &str,
    _filename: &str,
) -> Result<WorkflowNode, Diagnostic> {
    let span = span_of(&pair, source);
    let mut inner = pair.into_inner();
    let name = string_text(inner.next().expect("workflow name"));
    let mut node = WorkflowNode {
        name,
        metadata: MetadataNode::default(),
        variables: vec![],
        trigger: TriggerNode::Manual,
        runtime: RuntimeConfig::default(),
        steps: vec![],
        handlers: vec![],
        span,
    };
    let body = inner.next().expect("workflow body");
    for item in body.into_inner() {
        match item.as_rule() {
            Rule::metadata_block => node.metadata = parse_metadata(item, source),
            Rule::variables_block => node.variables = parse_variables(item, source),
            Rule::trigger_block => node.trigger = parse_trigger(item, source),
            Rule::runtime_block => node.runtime = parse_runtime(item, source),
            Rule::step_decl => node.steps.push(parse_step(item, source)?),
            Rule::on_failure_block => node.handlers.push(parse_handler(item, source)),
            // 控制流节点在 V1 AST 中保留为扩展块；其内容仍被 Pest 校验，后续 IR 版本扩展不会破坏语法。
            Rule::control_block | Rule::assignment => {}
            Rule::named_block if item.as_str().trim_start().starts_with("on_failure") => {
                node.handlers.push(parse_named_handler(item, source));
            }
            Rule::named_block => {}
            _ => {}
        }
    }
    Ok(node)
}

fn parse_metadata(pair: Pair<'_, Rule>, source: &str) -> MetadataNode {
    let mut metadata = MetadataNode {
        span: span_of(&pair, source),
        ..Default::default()
    };
    let map = parse_block_map(pair.into_inner().next().expect("metadata body"));
    metadata.display_name = map
        .get("display_name")
        .and_then(Value::as_str)
        .map(str::to_owned);
    metadata.description = map
        .get("description")
        .and_then(Value::as_str)
        .map(str::to_owned);
    metadata.version = map
        .get("version")
        .and_then(Value::as_str)
        .map(str::to_owned);
    metadata.author = map.get("author").and_then(Value::as_str).map(str::to_owned);
    metadata.tags = map
        .get("tags")
        .and_then(Value::as_array)
        .map(|values| {
            values
                .iter()
                .filter_map(Value::as_str)
                .map(str::to_owned)
                .collect()
        })
        .unwrap_or_default();
    metadata
}

fn parse_variables(pair: Pair<'_, Rule>, source: &str) -> Vec<VariableDecl> {
    let Some(body) = pair.into_inner().next() else {
        return vec![];
    };
    body.into_inner()
        .filter_map(|entry| {
            if entry.as_rule() != Rule::assignment {
                return None;
            }
            let mut inner = entry.clone().into_inner();
            let name = inner.next()?.as_str().to_owned();
            let raw = inner.next()?;
            let type_name = raw
                .as_str()
                .split('.')
                .next()
                .unwrap_or("string")
                .to_owned();
            let args = raw
                .into_inner()
                .find(|p| p.as_rule() == Rule::argument_list);
            let required = args
                .map(|a| a.as_str().contains("required") && a.as_str().contains("true"))
                .unwrap_or(false);
            Some(VariableDecl {
                name,
                type_name,
                required,
                default: None,
                span: span_of(&entry, source),
            })
        })
        .collect()
}

fn parse_trigger(pair: Pair<'_, Rule>, _source: &str) -> TriggerNode {
    let Some(body) = pair.into_inner().next() else {
        return TriggerNode::Manual;
    };
    let Some(item) = body.into_inner().next() else {
        return TriggerNode::Manual;
    };
    let kind = item.as_str().split('{').next().unwrap_or("").trim();
    let map = item
        .clone()
        .into_inner()
        .find(|p| p.as_rule() == Rule::block)
        .map(parse_block_map)
        .unwrap_or_default();
    if kind.starts_with("schedule") {
        return TriggerNode::Schedule {
            cron: map
                .get("cron")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .into(),
            timezone: map
                .get("timezone")
                .and_then(Value::as_str)
                .map(str::to_owned),
        };
    }
    if kind.starts_with("event") {
        return TriggerNode::Event {
            name: map
                .get("name")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .into(),
        };
    }
    if kind.starts_with("http") {
        return TriggerNode::Http {
            path: map
                .get("path")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .into(),
        };
    }
    TriggerNode::Manual
}

fn parse_runtime(pair: Pair<'_, Rule>, _source: &str) -> RuntimeConfig {
    let Some(body) = pair.into_inner().next() else {
        return RuntimeConfig::default();
    };
    let map = parse_block_map(body.clone());
    let retry = body
        .into_inner()
        .find(|p| {
            p.as_rule() == Rule::named_block && p.as_str().trim_start().starts_with("retry_policy")
        })
        .map(|p| {
            parse_block_map(
                p.into_inner()
                    .nth(1)
                    .unwrap_or_else(|| panic!("retry_policy body")),
            )
        })
        .map(|m| RetryNode {
            max_attempts: m.get("max_attempts").and_then(Value::as_u64).unwrap_or(1) as u32,
            strategy: m
                .get("strategy")
                .and_then(Value::as_str)
                .unwrap_or("fixed")
                .into(),
        });
    RuntimeConfig {
        timeout: map
            .get("timeout")
            .and_then(Value::as_str)
            .map(str::to_owned),
        max_parallel: map
            .get("max_parallel")
            .and_then(Value::as_u64)
            .map(|v| v as u32),
        retry,
    }
}

fn parse_step(pair: Pair<'_, Rule>, source: &str) -> Result<StepNode, Diagnostic> {
    let span = span_of(&pair, source);
    let mut inner = pair.into_inner();
    let id = inner.next().expect("step id").as_str().to_owned();
    let body = inner.next().expect("step body");
    let mut step = StepNode {
        id,
        span,
        ..Default::default()
    };
    for item in body.into_inner() {
        match item.as_rule() {
            Rule::action_decl => step.action = Some(parse_action(item, source)),
            Rule::depends_decl => step.depends_on = parse_dependencies(item),
            Rule::condition_decl => step.condition = Some(block_text(&item)),
            Rule::retry_decl => step.retry = Some(parse_retry(item)),
            Rule::output_decl => {
                step.output = item.into_inner().next().map(|p| p.as_str().to_owned())
            }
            Rule::timeout_decl => {
                step.timeout = item
                    .as_str()
                    .split('=')
                    .nth(1)
                    .map(|v| v.trim().trim_end_matches(';').to_owned())
            }
            Rule::assignment => {
                let mut p = item.clone().into_inner();
                if let (Some(k), Some(v)) = (p.next(), p.next()) {
                    if k.as_str() == "name" {
                        step.name = Some(value_text(v));
                    }
                }
            }
            _ => {}
        }
    }
    if step.action.is_none() {
        return Err(Diagnostic::new(
            "CF1301",
            "AST_ERROR",
            format!("步骤 {} 缺少 action", step.id),
            source,
            "workflow.flow",
            span.start,
            span.end,
            vec![],
            None,
        ));
    }
    Ok(step)
}

fn parse_handler(pair: Pair<'_, Rule>, source: &str) -> HandlerNode {
    let span = span_of(&pair, source);
    let steps = pair
        .into_inner()
        .next()
        .map(|body| {
            body.into_inner()
                .filter_map(|p| {
                    (p.as_rule() == Rule::step_decl)
                        .then(|| parse_step(p, source).ok())
                        .flatten()
                })
                .collect()
        })
        .unwrap_or_default();
    HandlerNode {
        id: "on_failure".into(),
        steps,
        span,
    }
}

fn parse_named_handler(pair: Pair<'_, Rule>, source: &str) -> HandlerNode {
    let span = span_of(&pair, source);
    let mut inner = pair.into_inner();
    let id = inner
        .next()
        .map(|value| value.as_str().to_owned())
        .unwrap_or_else(|| "on_failure".into());
    let steps = inner
        .next()
        .map(|body| {
            body.into_inner()
                .filter_map(|p| {
                    (p.as_rule() == Rule::step_decl)
                        .then(|| parse_step(p, source).ok())
                        .flatten()
                })
                .collect()
        })
        .unwrap_or_default();
    HandlerNode { id, steps, span }
}

fn parse_action(pair: Pair<'_, Rule>, _source: &str) -> ActionNode {
    let raw = pair.as_str();
    let header = raw
        .split('{')
        .next()
        .unwrap_or(raw)
        .trim()
        .strip_prefix("action")
        .unwrap_or("")
        .trim();
    let mut action = ActionNode {
        provider: "builtin".into(),
        arguments: Value::Object(Map::new()),
        ..Default::default()
    };
    if header == "plugin" {
        action.provider = "plugin".into();
    } else if let Some((service, method)) = header.split_once('.') {
        action.service = Some(service.into());
        action.method = Some(method.into());
    } else {
        action.provider = header.into();
    }
    if let Some(body) = pair.into_inner().find(|p| p.as_rule() == Rule::block) {
        action.arguments = Value::Object(parse_block_map(body));
    }
    if action.provider == "plugin" {
        if let Value::Object(map) = &action.arguments {
            action.plugin_id = map.get("id").and_then(Value::as_str).map(str::to_owned);
            action.function = map
                .get("function")
                .and_then(Value::as_str)
                .map(str::to_owned);
            action.version = map
                .get("version")
                .and_then(Value::as_str)
                .map(str::to_owned);
        }
    }
    action
}

fn parse_retry(pair: Pair<'_, Rule>) -> RetryNode {
    let map = pair
        .into_inner()
        .next()
        .map(parse_block_map)
        .unwrap_or_default();
    RetryNode {
        max_attempts: map.get("max_attempts").and_then(Value::as_u64).unwrap_or(1) as u32,
        strategy: map
            .get("strategy")
            .or_else(|| map.get("backoff"))
            .and_then(Value::as_str)
            .unwrap_or("fixed")
            .into(),
    }
}

fn parse_dependencies(pair: Pair<'_, Rule>) -> Vec<String> {
    pair.as_str()
        .trim_start_matches("depends_on")
        .trim()
        .trim_end_matches(';')
        .split(',')
        .map(str::trim)
        .filter(|v| !v.is_empty())
        .map(str::to_owned)
        .collect()
}

fn parse_block_map(pair: Pair<'_, Rule>) -> Map<String, Value> {
    let mut map = Map::new();
    for entry in pair.into_inner() {
        match entry.as_rule() {
            Rule::assignment => {
                let mut i = entry.into_inner();
                if let (Some(k), Some(v)) = (i.next(), i.next()) {
                    map.insert(k.as_str().to_owned(), value_pair(v));
                }
            }
            Rule::named_block => {
                let mut i = entry.into_inner();
                if let Some(k) = i.next() {
                    if let Some(body) = i.next() {
                        map.insert(k.as_str().to_owned(), Value::Object(parse_block_map(body)));
                    }
                }
            }
            Rule::call_stmt => {
                map.insert(
                    entry.as_str().to_owned(),
                    Value::String(entry.as_str().to_owned()),
                );
            }
            _ => {}
        }
    }
    map
}

fn value_pair(pair: Pair<'_, Rule>) -> Value {
    match pair.as_rule() {
        Rule::string_value | Rule::triple_string => Value::String(string_text(pair)),
        Rule::boolean => Value::Bool(pair.as_str() == "true"),
        Rule::number => pair
            .as_str()
            .parse::<f64>()
            .ok()
            .map(|v| {
                serde_json::Number::from_f64(v)
                    .map(Value::Number)
                    .unwrap_or(Value::Null)
            })
            .unwrap_or(Value::Null),
        Rule::duration | Rule::qualified => Value::String(pair.as_str().to_owned()),
        Rule::array => Value::Array(
            pair.into_inner()
                .next()
                .map(|list| list.into_inner().map(value_pair).collect())
                .unwrap_or_default(),
        ),
        Rule::call_expr => Value::String(pair.as_str().to_owned()),
        _ => Value::String(pair.as_str().to_owned()),
    }
}

fn value_text(pair: Pair<'_, Rule>) -> String {
    let raw = pair.as_str().to_owned();
    value_pair(pair).as_str().map(str::to_owned).unwrap_or(raw)
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
fn block_text(pair: &Pair<'_, Rule>) -> String {
    let raw = pair.as_str();
    raw.find('{')
        .and_then(|s| raw.rfind('}').map(|e| raw[s + 1..e].trim().to_owned()))
        .unwrap_or_default()
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
