//! CloudFlow AST 可视化输出（`--emit-ast`）。
//!
//! 本模块把 Parser 生成的 AST 转换为层级树形文本（可着色）或 JSON 序列化结果，
//! 用于语法调试与审计。**不依赖第三方树形库**（项目离线、避免新增网络依赖），
//! 用简单的 `Tree` 结构 + 递归绘制实现，等价于 `termtree` 的能力。
//!
//! 约束：
//! - 只反映 Parser 输出的纯语法 AST，不含语义阶段类型表/IR 阶段信息（需求 5.1/5.3）。
//! - 颜色仅用于终端文本（ANSI 转义），`--no-color` 后为纯文本，写文件默认无色（3.13-3.15）。
//! - AST 变更时 `match` 穷尽所有变体，Rust 编译期即报漏（4.6）。

use crate::ast::*;
use serde_json::{json, Value as JValue};

/// 树形绘制选项。
#[derive(Debug, Clone, Copy, Default)]
pub struct AstPrintOptions {
    /// 是否在文本输出中使用 ANSI 颜色。
    pub color: bool,
}

/// 简单的树形节点，供绘制器渲染。
struct Tree {
    label: String,
    children: Vec<Tree>,
}

impl Tree {
    fn leaf(label: String) -> Self {
        Tree {
            label,
            children: Vec::new(),
        }
    }
    fn with(children: Vec<Tree>) -> Self {
        Tree {
            label: String::new(),
            children,
        }
    }
}

/// 为节点文本着色（使用 ANSI SGR 转义，仅在 `color` 开启时）。
fn paint(text: &str, code: &str, color: bool) -> String {
    if !color || text.is_empty() {
        return text.to_owned();
    }
    format!("\x1b[{code}m{text}\x1b[0m")
}

fn node_paint(text: &str, color: bool) -> String {
    // 节点类型名：加粗默认前景，便于与字段名区分。
    paint(text, "1", color)
}

/// 字段叶子：`field: value`。
fn leaf(field: &str, value: &str, color: bool) -> Tree {
    Tree::leaf(format!("{}: {}", paint(field, "2", color), value))
}

fn opt_leaf(field: &str, value: Option<&str>, color: bool) -> Tree {
    match value {
        Some(v) => leaf(field, v, color),
        None => Tree::leaf(format!("{}: <none>", paint(field, "2", color))),
    }
}

/// 渲染树为带分支字符的文本。
fn render_tree(root: &Tree, color: bool) -> String {
    let mut out = String::new();
    let root_label = if root.label.is_empty() {
        node_paint("(root)", color)
    } else {
        root.label.clone()
    };
    out.push_str(&root_label);
    out.push('\n');
    for (i, child) in root.children.iter().enumerate() {
        render_child(child, "", i == root.children.len() - 1, color, &mut out);
    }
    out
}

fn render_child(tree: &Tree, prefix: &str, is_last: bool, color: bool, out: &mut String) {
    let connector = if is_last { "└── " } else { "├── " };
    out.push_str(prefix);
    out.push_str(connector);
    out.push_str(&tree.label);
    out.push('\n');
    let next_prefix = if is_last {
        format!("{prefix}    ")
    } else {
        format!("{prefix}│   ")
    };
    for (i, child) in tree.children.iter().enumerate() {
        render_child(
            child,
            &next_prefix,
            i == tree.children.len() - 1,
            color,
            out,
        );
    }
}

// ---------------------------------------------------------------------------
// 值 / 表达式 渲染
// ---------------------------------------------------------------------------

/// 值节点的紧凑叶子表示（不递归，作为单行展示）。
fn value_inline(value: &ValueNode) -> String {
    match value {
        ValueNode::String(s) => format!("{s:?}"),
        ValueNode::Template(parts) => {
            let body = parts
                .iter()
                .map(value_inline)
                .collect::<Vec<_>>()
                .join(" + ");
            format!("Template[{body}]")
        }
        ValueNode::Number(n) => n.to_string(),
        ValueNode::Boolean(b) => b.to_string(),
        ValueNode::Null => "null".into(),
        ValueNode::Duration(d) => d.clone(),
        ValueNode::VariableRef(r) => r.clone(),
        ValueNode::Expression(e) => expression_inline(e),
        ValueNode::Array(items) => format!(
            "[{}]",
            items
                .iter()
                .map(value_inline)
                .collect::<Vec<_>>()
                .join(", ")
        ),
        ValueNode::Object(map) => format!(
            "{{{}}}",
            map.iter()
                .map(|(k, v)| format!("{k}: {}", value_inline(v)))
                .collect::<Vec<_>>()
                .join(", ")
        ),
        ValueNode::Call {
            function,
            positional,
            named,
        } => {
            let args = positional
                .iter()
                .map(value_inline)
                .chain(
                    named
                        .iter()
                        .map(|(k, v)| format!("{k}={}", value_inline(v))),
                )
                .collect::<Vec<_>>()
                .join(", ");
            format!("{function}({args})")
        }
        ValueNode::Enum(e) => e.clone(),
    }
}

fn expression_inline(expression: &ExpressionNode) -> String {
    match &expression.kind {
        ExpressionKind::Literal(v) => value_inline(v),
        ExpressionKind::Reference(r) => r.clone(),
        ExpressionKind::Unary { operator, operand } => {
            format!("{operator}{}", expression_inline(operand))
        }
        ExpressionKind::Binary {
            operator,
            left,
            right,
        } => {
            format!(
                "{} {operator} {}",
                expression_inline(left),
                expression_inline(right)
            )
        }
        ExpressionKind::Ternary {
            condition,
            when_true,
            when_false,
        } => format!(
            "{} ? {} : {}",
            expression_inline(condition),
            expression_inline(when_true),
            expression_inline(when_false)
        ),
        ExpressionKind::Call {
            function,
            arguments,
        } => format!(
            "{function}({})",
            arguments
                .iter()
                .map(expression_inline)
                .collect::<Vec<_>>()
                .join(", ")
        ),
        ExpressionKind::Pipe { input, op } => {
            let op_txt = match op {
                PipeOp::Filter(p) => format!("filter({})", expression_inline(p)),
                PipeOp::Map(Some(f)) => format!("map({f})"),
                PipeOp::Map(None) => "map()".into(),
                PipeOp::Reduce(f) => format!("reduce({f})"),
            };
            format!("{} | {op_txt}", expression_inline(input))
        }
    }
}

/// 表达式树（递归展开，保留结构）。
fn expression_tree(expression: &ExpressionNode, color: bool) -> Tree {
    match &expression.kind {
        ExpressionKind::Literal(v) => Tree::leaf(format!("Literal: {}", value_inline(v))),
        ExpressionKind::Reference(r) => Tree::leaf(format!("Reference: {r}")),
        ExpressionKind::Unary { operator, operand } => Tree {
            label: format!("Unary: {operator}"),
            children: vec![expression_tree(operand, color)],
        },
        ExpressionKind::Binary {
            operator,
            left,
            right,
        } => Tree {
            label: format!("Binary: {operator}"),
            children: vec![expression_tree(left, color), expression_tree(right, color)],
        },
        ExpressionKind::Ternary {
            condition,
            when_true,
            when_false,
        } => Tree {
            label: "Ternary".into(),
            children: vec![
                Tree::with(vec![
                    Tree::leaf("condition".into()),
                    expression_tree(condition, color),
                ]),
                Tree::with(vec![
                    Tree::leaf("when_true".into()),
                    expression_tree(when_true, color),
                ]),
                Tree::with(vec![
                    Tree::leaf("when_false".into()),
                    expression_tree(when_false, color),
                ]),
            ],
        },
        ExpressionKind::Call {
            function,
            arguments,
        } => Tree {
            label: format!("Call: {function}"),
            children: arguments
                .iter()
                .map(|a| expression_tree(a, color))
                .collect(),
        },
        ExpressionKind::Pipe { input, op } => Tree {
            label: "Pipe".into(),
            children: vec![
                Tree::with(vec![
                    Tree::leaf("input".into()),
                    expression_tree(input, color),
                ]),
                Tree::with(vec![
                    Tree::leaf(pipeline_op_label(op).into()),
                    pipeline_op_tree(op, color),
                ]),
            ],
        },
    }
}

fn pipeline_op_label(op: &PipeOp) -> String {
    match op {
        PipeOp::Filter(_) => "filter".into(),
        PipeOp::Map(_) => "map".into(),
        PipeOp::Reduce(_) => "reduce".into(),
    }
}

fn pipeline_op_tree(op: &PipeOp, color: bool) -> Tree {
    match op {
        PipeOp::Filter(pred) => Tree::with(vec![expression_tree(pred, color)]),
        PipeOp::Map(field) => Tree::with(vec![Tree::leaf(format!(
            "field: {}",
            field.as_deref().unwrap_or("<none>")
        ))]),
        PipeOp::Reduce(fn_name) => Tree::with(vec![Tree::leaf(format!("function: {fn_name}"))]),
    }
}

// ---------------------------------------------------------------------------
// 值树（ValueNode 递归）
// ---------------------------------------------------------------------------

fn value_tree(value: &ValueNode, color: bool) -> Tree {
    match value {
        ValueNode::Array(items) => Tree {
            label: node_paint("Array", color),
            children: items.iter().map(|v| value_tree(v, color)).collect(),
        },
        ValueNode::Object(map) => Tree {
            label: node_paint("Object", color),
            children: map
                .iter()
                .map(|(k, v)| Tree {
                    label: leaf("key", k, color).label,
                    children: vec![value_tree(v, color)],
                })
                .collect(),
        },
        ValueNode::Expression(expr) => expression_tree(expr, color),
        ValueNode::Template(parts) => Tree {
            label: node_paint("Template", color),
            children: parts.iter().map(|p| value_tree(p, color)).collect(),
        },
        other => Tree::leaf(value_inline(other)),
    }
}

// ---------------------------------------------------------------------------
// Flow 节点
// ---------------------------------------------------------------------------

fn flow_section(title: &str, nodes: &[FlowNode], color: bool) -> Tree {
    Tree {
        label: node_paint(title, color),
        children: nodes.iter().map(|n| flow_tree(n, color)).collect(),
    }
}

fn controls_section(nodes: &[FlowNode], color: bool) -> Tree {
    flow_section("controls", nodes, color)
}

fn step_tree(step: &StepNode, color: bool) -> Tree {
    let mut children = Vec::new();
    children.push(leaf("id", &step.id, color));
    if let Some(name) = &step.name {
        children.push(leaf("name", name, color));
    }
    if let Some(action) = &step.action {
        children.push(action_tree(action, color));
    }
    if !step.depends_on.is_empty() {
        children.push(Tree::leaf(format!(
            "depends_on: {}",
            step.depends_on.join(", ")
        )));
    }
    if step.depends_condition.is_some() {
        children.push(Tree {
            label: "depends_condition".into(),
            children: vec![expression_tree(
                step.depends_condition.as_ref().unwrap(),
                color,
            )],
        });
    }
    if let Some(condition) = &step.condition {
        children.push(Tree {
            label: "condition".into(),
            children: vec![expression_tree(condition, color)],
        });
    }
    if let Some(retry) = &step.retry {
        children.push(Tree {
            label: node_paint("Retry", color),
            children: vec![
                leaf("max_attempts", &retry.max_attempts.to_string(), color),
                leaf("strategy", &retry.strategy, color),
            ],
        });
    }
    if !step.retry_on.is_empty() {
        children.push(Tree::leaf(format!(
            "retry_on: [{}]",
            step.retry_on.join(", ")
        )));
    }
    if let Some(timeout) = &step.timeout {
        children.push(Tree {
            label: node_paint("Timeout", color),
            children: vec![
                leaf("raw", &timeout.raw, color),
                leaf("milliseconds", &timeout.milliseconds.to_string(), color),
            ],
        });
    }
    if let Some(on_timeout) = &step.on_timeout {
        children.push(leaf("on_timeout", on_timeout, color));
    }
    if !step.on_error.is_empty() {
        children.push(flow_section("on_error", &step.on_error, color));
    }
    if let Some(output) = &step.output {
        children.push(leaf("output", output, color));
    }
    if let Some(alias) = &step.use_alias {
        children.push(leaf("use_alias", alias, color));
    }
    if !step.controls.is_empty() {
        children.push(controls_section(&step.controls, color));
    }
    Tree {
        label: node_paint("Step", color),
        children,
    }
}

fn action_tree(action: &ActionNode, color: bool) -> Tree {
    let mut children = Vec::new();
    children.push(leaf("provider", &action.provider, color));
    if let Some(service) = &action.service {
        children.push(leaf("service", service, color));
    }
    if let Some(method) = &action.method {
        children.push(leaf("method", method, color));
    }
    if let Some(id) = &action.plugin_id {
        children.push(leaf("plugin_id", id, color));
    }
    if let Some(fn_name) = &action.function {
        children.push(leaf("function", fn_name, color));
    }
    if let Some(version) = &action.version {
        children.push(leaf("version", version, color));
    }
    if !action.arguments.is_empty() {
        children.push(Tree {
            label: node_paint("Arguments", color),
            children: action
                .arguments
                .iter()
                .map(|(k, v)| Tree {
                    label: leaf("arg", k, color).label,
                    children: vec![value_tree(v, color)],
                })
                .collect(),
        });
    }
    Tree {
        label: node_paint("Action", color),
        children,
    }
}

fn flow_tree(node: &FlowNode, color: bool) -> Tree {
    match node {
        FlowNode::Step(step) => step_tree(step, color),
        FlowNode::Condition(node) => Tree {
            label: node_paint("Condition", color),
            children: vec![
                Tree {
                    label: "expression".into(),
                    children: vec![expression_tree(&node.expression, color)],
                },
                flow_section("true_branch", &node.true_branch, color),
                flow_section("false_branch", &node.false_branch, color),
            ],
        },
        FlowNode::Loop(node) => Tree {
            label: node_paint("Foreach", color),
            children: vec![
                leaf("iterator", &node.iterator, color),
                Tree {
                    label: "collection".into(),
                    children: vec![expression_tree(&node.collection, color)],
                },
                flow_section("body", &node.body, color),
            ],
        },
        FlowNode::For(node) => {
            let mut children = Vec::new();
            children.push(leaf("iterator", &node.iterator, color));
            if let Some(from) = &node.range_from {
                children.push(Tree {
                    label: "range_from".into(),
                    children: vec![expression_tree(from, color)],
                });
            }
            if let Some(to) = &node.range_to {
                children.push(Tree {
                    label: "range_to".into(),
                    children: vec![expression_tree(to, color)],
                });
            }
            if let Some(collection) = &node.collection {
                children.push(Tree {
                    label: "collection".into(),
                    children: vec![expression_tree(collection, color)],
                });
            }
            children.push(flow_section("body", &node.body, color));
            Tree {
                label: node_paint("For", color),
                children,
            }
        }
        FlowNode::While(node) => Tree {
            label: node_paint("While", color),
            children: vec![
                Tree {
                    label: "condition".into(),
                    children: vec![expression_tree(&node.condition, color)],
                },
                flow_section("body", &node.body, color),
            ],
        },
        FlowNode::Parallel(node) => {
            let mut children = Vec::new();
            if let Some(max) = node.max_concurrency {
                children.push(leaf("max_concurrency", &max.to_string(), color));
            }
            children.push(flow_section("branches", &node.branches, color));
            Tree {
                label: node_paint("Parallel", color),
                children,
            }
        }
        FlowNode::TryCatch(node) => Tree {
            label: node_paint("TryCatch", color),
            children: vec![
                flow_section("try", &node.try_nodes, color),
                Tree {
                    label: format!(
                        "catch: {}",
                        node.catch_binding.as_deref().unwrap_or("<none>")
                    ),
                    children: node
                        .catch_nodes
                        .iter()
                        .map(|n| flow_tree(n, color))
                        .collect(),
                },
                flow_section("finally", &node.finally_nodes, color),
            ],
        },
        FlowNode::Wait(node) => {
            let mut children = vec![leaf("wait_type", &node.wait_type, color)];
            if let Some(timeout) = &node.timeout {
                children.push(Tree {
                    label: node_paint("Timeout", color),
                    children: vec![
                        leaf("raw", &timeout.raw, color),
                        leaf("milliseconds", &timeout.milliseconds.to_string(), color),
                    ],
                });
            }
            Tree {
                label: node_paint("Wait", color),
                children,
            }
        }
        FlowNode::Assert(node) => Tree {
            label: node_paint("Assert", color),
            children: vec![expression_tree(&node.condition, color)],
        },
        FlowNode::Switch(node) => {
            let mut children = vec![Tree {
                label: "subject".into(),
                children: vec![expression_tree(&node.subject, color)],
            }];
            for case in &node.cases {
                children.push(Tree {
                    label: format!("Case: {}", value_inline(&case.value)),
                    children: case.body.iter().map(|n| flow_tree(n, color)).collect(),
                });
            }
            if !node.default_branch.is_empty() {
                children.push(flow_section("default", &node.default_branch, color));
            }
            Tree {
                label: node_paint("Switch", color),
                children,
            }
        }
        FlowNode::Delay(node) => Tree {
            label: node_paint("Delay", color),
            children: vec![
                leaf("raw", &node.raw, color),
                leaf("milliseconds", &node.milliseconds.to_string(), color),
            ],
        },
        FlowNode::Notify(node) => Tree {
            label: node_paint("Notify", color),
            children: vec![
                leaf("channel", &node.channel, color),
                opt_leaf(
                    "recipient",
                    node.recipient.as_ref().map(value_inline).as_deref(),
                    color,
                ),
                opt_leaf(
                    "message",
                    node.message.as_ref().map(value_inline).as_deref(),
                    color,
                ),
            ],
        },
        FlowNode::Return(node) => {
            let mut children = Vec::new();
            if let Some(output) = &node.output {
                children.push(Tree {
                    label: "output".into(),
                    children: vec![expression_tree(output, color)],
                });
            }
            Tree {
                label: node_paint("Return", color),
                children,
            }
        }
        FlowNode::Break(_) => Tree::leaf(node_paint("Break", color)),
        FlowNode::Continue(_) => Tree::leaf(node_paint("Continue", color)),
        FlowNode::Validate(node) => Tree {
            label: node_paint("Validate", color),
            children: vec![expression_tree(&node.condition, color)],
        },
        FlowNode::StepGroup(node) => Tree {
            label: node_paint("StepGroup", color),
            children: std::iter::once(Tree::leaf(format!("id: {}", node.id)))
                .chain(node.steps.iter().map(|s| step_tree(s, color)))
                .collect(),
        },
    }
}

// ---------------------------------------------------------------------------
// 顶层节点：WorkflowNode
// ---------------------------------------------------------------------------

fn metadata_tree(metadata: &MetadataNode, color: bool) -> Tree {
    Tree {
        label: node_paint("Metadata", color),
        children: vec![
            opt_leaf("display_name", metadata.display_name.as_deref(), color),
            opt_leaf("description", metadata.description.as_deref(), color),
            opt_leaf("version", metadata.version.as_deref(), color),
            opt_leaf("author", metadata.author.as_deref(), color),
            if metadata.tags.is_empty() {
                Tree::leaf("tags: []".into())
            } else {
                Tree::leaf(format!("tags: [{}]", metadata.tags.join(", ")))
            },
            opt_leaf("changelog", metadata.changelog.as_deref(), color),
        ],
    }
}

fn trigger_inline(trigger: &TriggerNode) -> String {
    match trigger {
        TriggerNode::Manual => "manual".into(),
        TriggerNode::Schedule { cron, timezone } => format!(
            "schedule(cron={cron}, timezone={})",
            timezone.as_deref().unwrap_or("<none>")
        ),
        TriggerNode::Event { name } => format!("event({name})"),
        TriggerNode::Http { path, method } => format!(
            "http(path={path}, method={})",
            method.as_deref().unwrap_or("POST")
        ),
        TriggerNode::Interval { raw, milliseconds } => {
            format!("interval({raw}, {milliseconds}ms)")
        }
    }
}

fn variable_tree(decl: &VariableDecl, color: bool) -> Tree {
    let mut children = vec![
        leaf("name", &decl.name, color),
        leaf("type", &decl.type_name, color),
        leaf(
            "source",
            &format!("{:?}", decl.source).to_lowercase(),
            color,
        ),
        leaf("required", &decl.required.to_string(), color),
    ];
    if let Some(default) = &decl.default {
        children.push(Tree {
            label: "default".into(),
            children: vec![value_tree(default, color)],
        });
    }
    Tree {
        label: node_paint("Variable", color),
        children,
    }
}

fn runtime_tree(runtime: &RuntimeConfig, color: bool) -> Tree {
    let mut children = Vec::new();
    if let Some(timeout) = &runtime.timeout {
        children.push(Tree {
            label: node_paint("Timeout", color),
            children: vec![
                leaf("raw", &timeout.raw, color),
                leaf("milliseconds", &timeout.milliseconds.to_string(), color),
            ],
        });
    }
    if let Some(max) = runtime.max_parallel {
        children.push(Tree::leaf(format!("max_parallel: {max}")));
    }
    if let Some(retry) = &runtime.retry {
        children.push(Tree {
            label: node_paint("Retry", color),
            children: vec![
                leaf("max_attempts", &retry.max_attempts.to_string(), color),
                leaf("strategy", &retry.strategy, color),
            ],
        });
    }
    Tree {
        label: node_paint("Runtime", color),
        children,
    }
}

fn handler_tree(handler: &HandlerNode, color: bool) -> Tree {
    Tree {
        label: node_paint("Handler", color),
        children: std::iter::once(Tree::leaf(format!("id: {}", handler.id)))
            .chain(handler.nodes.iter().map(|n| flow_tree(n, color)))
            .collect(),
    }
}

fn workflow_tree_colored(workflow: &WorkflowNode, color: bool) -> Tree {
    let mut children = Vec::new();
    children.push(leaf("name", &workflow.name, color));
    if let Some(namespace) = &workflow.namespace {
        children.push(leaf("namespace", namespace, color));
    }
    if !workflow.includes.is_empty() {
        children.push(Tree {
            label: node_paint("Includes", color),
            children: workflow
                .includes
                .iter()
                .map(|inc| {
                    let alias = inc
                        .alias
                        .as_deref()
                        .map(|a| format!(" as {a}"))
                        .unwrap_or_default();
                    Tree::leaf(format!("{} (alias:{})", inc.path, alias))
                })
                .collect(),
        });
    }
    children.push(metadata_tree(&workflow.metadata, color));
    if !workflow.environment.is_empty() {
        children.push(Tree {
            label: node_paint("Environment", color),
            children: workflow
                .environment
                .iter()
                .map(|e| Tree::leaf(format!("{}: {}", e.key, value_inline(&e.value))))
                .collect(),
        });
    }
    if let Some(audit) = &workflow.audit {
        children.push(Tree {
            label: node_paint("Audit", color),
            children: vec![
                leaf("level", &audit.level, color),
                opt_leaf("description", audit.description.as_deref(), color),
            ],
        });
    }
    children.push(Tree {
        label: node_paint("Variables", color),
        children: workflow
            .variables
            .iter()
            .map(|v| variable_tree(v, color))
            .collect(),
    });
    children.push(Tree {
        label: node_paint("Trigger", color),
        children: vec![Tree::leaf(trigger_inline(&workflow.trigger))],
    });
    children.push(runtime_tree(&workflow.runtime, color));
    if !workflow.step_groups.is_empty() {
        children.push(Tree {
            label: node_paint("StepGroups", color),
            children: workflow
                .step_groups
                .iter()
                .map(|g| Tree {
                    label: node_paint("StepGroup", color),
                    children: std::iter::once(Tree::leaf(format!("id: {}", g.id)))
                        .chain(g.steps.iter().map(|s| step_tree(s, color)))
                        .collect(),
                })
                .collect(),
        });
    }
    if !workflow.outputs.is_empty() {
        children.push(Tree {
            label: node_paint("Outputs", color),
            children: workflow
                .outputs
                .iter()
                .map(|(key, value)| Tree::leaf(format!("{}: {}", key, value_inline(value))))
                .collect(),
        });
    }
    children.push(flow_section("steps", &workflow.flow, color));
    children.push(Tree {
        label: node_paint("Handlers", color),
        children: workflow
            .handlers
            .iter()
            .map(|h| handler_tree(h, color))
            .collect(),
    });
    Tree {
        label: node_paint("Workflow", color),
        children,
    }
}

/// 生成 AST 树形文本。
pub fn render(workflow: &WorkflowNode, options: &AstPrintOptions) -> String {
    render_tree(
        &workflow_tree_colored(workflow, options.color),
        options.color,
    )
}

// ---------------------------------------------------------------------------
// JSON 序列化（`--emit-ast --output-format json`）
// ---------------------------------------------------------------------------

fn value_json(value: &ValueNode) -> JValue {
    match value {
        ValueNode::String(s) => json!({ "string": s }),
        ValueNode::Template(parts) => {
            json!({ "template": parts.iter().map(value_json).collect::<Vec<_>>() })
        }
        ValueNode::Number(n) => json!({ "number": n }),
        ValueNode::Boolean(b) => json!({ "boolean": b }),
        ValueNode::Null => json!(null),
        ValueNode::Duration(d) => json!({ "duration": d }),
        ValueNode::VariableRef(r) => json!({ "ref": r }),
        ValueNode::Expression(e) => expression_json(e),
        ValueNode::Array(items) => {
            json!({ "array": items.iter().map(value_json).collect::<Vec<_>>() })
        }
        ValueNode::Object(map) => json!({
            "object": map.iter().map(|(k, v)| json!({"key": k, "value": value_json(v)})).collect::<Vec<_>>()
        }),
        ValueNode::Call {
            function,
            positional,
            named,
        } => json!({
            "call": {
                "function": function,
                "positional": positional.iter().map(value_json).collect::<Vec<_>>(),
                "named": named.iter().map(|(k, v)| json!({"key": k, "value": value_json(v)})).collect::<Vec<_>>(),
            }
        }),
        ValueNode::Enum(e) => json!({ "enum": e }),
    }
}

fn expression_json(expression: &ExpressionNode) -> JValue {
    match &expression.kind {
        ExpressionKind::Literal(v) => json!({ "literal": value_json(v) }),
        ExpressionKind::Reference(r) => json!({ "reference": r }),
        ExpressionKind::Unary { operator, operand } => {
            json!({ "unary": { "operator": operator, "operand": expression_json(operand) } })
        }
        ExpressionKind::Binary {
            operator,
            left,
            right,
        } => json!({
            "binary": { "operator": operator, "left": expression_json(left), "right": expression_json(right) }
        }),
        ExpressionKind::Ternary {
            condition,
            when_true,
            when_false,
        } => json!({
            "ternary": {
                "condition": expression_json(condition),
                "when_true": expression_json(when_true),
                "when_false": expression_json(when_false),
            }
        }),
        ExpressionKind::Call {
            function,
            arguments,
        } => json!({
            "call": { "function": function, "arguments": arguments.iter().map(expression_json).collect::<Vec<_>>() }
        }),
        ExpressionKind::Pipe { input, op } => {
            let op_json = match op {
                PipeOp::Filter(p) => json!({ "filter": expression_json(p) }),
                PipeOp::Map(field) => json!({ "map": field }),
                PipeOp::Reduce(f) => json!({ "reduce": f }),
            };
            json!({ "pipe": { "input": expression_json(input), "op": op_json } })
        }
    }
}

fn step_json(step: &StepNode) -> JValue {
    json!({
        "type": "Step",
        "id": step.id,
        "name": step.name,
        "action": step.action.as_ref().map(action_json),
        "depends_on": step.depends_on,
        "depends_condition": step.depends_condition.as_ref().map(expression_json),
        "condition": step.condition.as_ref().map(expression_json),
        "retry": step.retry.as_ref().map(|r| json!({
            "max_attempts": r.max_attempts, "strategy": r.strategy
        })),
        "retry_on": step.retry_on,
        "timeout": step.timeout.as_ref().map(timeout_json),
        "on_timeout": step.on_timeout,
        "on_error": step.on_error.iter().map(flow_json).collect::<Vec<_>>(),
        "output": step.output,
        "use_alias": step.use_alias,
        "controls": step.controls.iter().map(flow_json).collect::<Vec<_>>(),
    })
}

fn action_json(action: &ActionNode) -> JValue {
    json!({
        "provider": action.provider,
        "service": action.service,
        "method": action.method,
        "plugin_id": action.plugin_id,
        "function": action.function,
        "version": action.version,
        "arguments": action.arguments.iter().map(|(k, v)| json!({"key": k, "value": value_json(v)})).collect::<Vec<_>>(),
    })
}

fn timeout_json(timeout: &TimeoutConfig) -> JValue {
    json!({ "raw": timeout.raw, "milliseconds": timeout.milliseconds })
}

fn flow_json(node: &FlowNode) -> JValue {
    match node {
        FlowNode::Step(s) => step_json(s),
        FlowNode::Condition(c) => json!({
            "type": "Condition",
            "expression": expression_json(&c.expression),
            "true_branch": c.true_branch.iter().map(flow_json).collect::<Vec<_>>(),
            "false_branch": c.false_branch.iter().map(flow_json).collect::<Vec<_>>(),
        }),
        FlowNode::Loop(l) => json!({
            "type": "Foreach",
            "iterator": l.iterator,
            "collection": expression_json(&l.collection),
            "body": l.body.iter().map(flow_json).collect::<Vec<_>>(),
        }),
        FlowNode::For(f) => json!({
            "type": "For",
            "iterator": f.iterator,
            "range_from": f.range_from.as_ref().map(expression_json),
            "range_to": f.range_to.as_ref().map(expression_json),
            "collection": f.collection.as_ref().map(expression_json),
            "body": f.body.iter().map(flow_json).collect::<Vec<_>>(),
        }),
        FlowNode::While(w) => json!({
            "type": "While",
            "condition": expression_json(&w.condition),
            "body": w.body.iter().map(flow_json).collect::<Vec<_>>(),
        }),
        FlowNode::Parallel(p) => json!({
            "type": "Parallel",
            "max_concurrency": p.max_concurrency,
            "branches": p.branches.iter().map(flow_json).collect::<Vec<_>>(),
        }),
        FlowNode::TryCatch(t) => json!({
            "type": "TryCatch",
            "try_nodes": t.try_nodes.iter().map(flow_json).collect::<Vec<_>>(),
            "catch_binding": t.catch_binding,
            "catch_nodes": t.catch_nodes.iter().map(flow_json).collect::<Vec<_>>(),
            "finally_nodes": t.finally_nodes.iter().map(flow_json).collect::<Vec<_>>(),
        }),
        FlowNode::Wait(w) => json!({
            "type": "Wait",
            "wait_type": w.wait_type,
            "timeout": w.timeout.as_ref().map(timeout_json),
        }),
        FlowNode::Assert(a) => json!({
            "type": "Assert",
            "condition": expression_json(&a.condition),
        }),
        FlowNode::Switch(s) => json!({
            "type": "Switch",
            "subject": expression_json(&s.subject),
            "cases": s.cases.iter().map(|case| json!({
                "value": value_json(&case.value),
                "body": case.body.iter().map(flow_json).collect::<Vec<_>>(),
            })).collect::<Vec<_>>(),
            "default_branch": s.default_branch.iter().map(flow_json).collect::<Vec<_>>(),
        }),
        FlowNode::Delay(d) => json!({
            "type": "Delay",
            "raw": d.raw,
            "milliseconds": d.milliseconds,
        }),
        FlowNode::Notify(n) => json!({
            "type": "Notify",
            "channel": n.channel,
            "recipient": n.recipient.as_ref().map(value_json),
            "message": n.message.as_ref().map(value_json),
        }),
        FlowNode::Return(r) => json!({
            "type": "Return",
            "output": r.output.as_ref().map(expression_json),
        }),
        FlowNode::Break(_) => json!({ "type": "Break" }),
        FlowNode::Continue(_) => json!({ "type": "Continue" }),
        FlowNode::Validate(v) => json!({
            "type": "Validate",
            "condition": expression_json(&v.condition),
        }),
        FlowNode::StepGroup(g) => json!({
            "type": "StepGroup",
            "id": g.id,
            "steps": g.steps.iter().map(step_json).collect::<Vec<_>>(),
        }),
    }
}

/// 生成 AST 的 JSON 序列化结果（`--output-format json` 配合 `--emit-ast`）。
pub fn render_json(workflow: &WorkflowNode) -> String {
    let value = json!({
        "ast": {
            "name": workflow.name,
            "namespace": workflow.namespace,
            "metadata": json!({
                "display_name": workflow.metadata.display_name,
                "description": workflow.metadata.description,
                "version": workflow.metadata.version,
                "author": workflow.metadata.author,
                "tags": workflow.metadata.tags,
                "changelog": workflow.metadata.changelog,
            }),
            "environment": workflow.environment.iter().map(|e| json!({
                "key": e.key, "value": value_json(&e.value)
            })).collect::<Vec<_>>(),
            "audit": workflow.audit.as_ref().map(|a| json!({
                "level": a.level, "description": a.description
            })),
            "includes": workflow.includes.iter().map(|i| json!({
                "path": i.path, "alias": i.alias
            })).collect::<Vec<_>>(),
            "variables": workflow.variables.iter().map(|v| json!({
                "name": v.name,
                "type": v.type_name,
                "source": format!("{:?}", v.source).to_lowercase(),
                "required": v.required,
                "default": v.default.as_ref().map(value_json),
            })).collect::<Vec<_>>(),
            "trigger": match &workflow.trigger {
                TriggerNode::Manual => json!({ "manual": {} }),
                TriggerNode::Schedule { cron, timezone } => json!({ "schedule": { "cron": cron, "timezone": timezone } }),
                TriggerNode::Event { name } => json!({ "event": { "name": name } }),
                TriggerNode::Http { path, method } => json!({ "http": { "path": path, "method": method } }),
                TriggerNode::Interval { raw, milliseconds } => json!({ "interval": { "raw": raw, "milliseconds": milliseconds } }),
            },
            "runtime": json!({
                "timeout": workflow.runtime.timeout.as_ref().map(timeout_json),
                "max_parallel": workflow.runtime.max_parallel,
                "retry": workflow.runtime.retry.as_ref().map(|r| json!({
                    "max_attempts": r.max_attempts, "strategy": r.strategy
                })),
            }),
            "step_groups": workflow.step_groups.iter().map(|g| json!({
                "id": g.id,
                "steps": g.steps.iter().map(step_json).collect::<Vec<_>>(),
            })).collect::<Vec<_>>(),
            "steps": workflow.flow.iter().map(flow_json).collect::<Vec<_>>(),
            "outputs": workflow.outputs.iter().map(|(k, v)| json!({ "name": k, "value": value_json(v) })).collect::<Vec<_>>(),
            "handlers": workflow.handlers.iter().map(|h| json!({
                "id": h.id,
                "nodes": h.nodes.iter().map(flow_json).collect::<Vec<_>>(),
            })).collect::<Vec<_>>(),
        }
    });
    serde_json::to_string_pretty(&value).expect("AST JSON must serialize")
}
