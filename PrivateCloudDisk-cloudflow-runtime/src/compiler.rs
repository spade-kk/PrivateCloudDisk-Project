//! AST → Workflow IR v1 生成器。

use crate::ast::*;
use crate::ir::*;
use serde_json::{Map, Value};
use std::collections::{BTreeMap, HashSet};

pub fn compile(workflow: &WorkflowNode) -> WorkflowIrV1 {
    let mut nodes = Vec::new();
    let mut edges = Vec::new();
    if workflow.flow.is_empty() {
        // 兼容由早期 AST 构造器创建的 WorkflowNode；新 Parser 一定填充 flow。
        for step in &workflow.steps {
            compile_step(step, &mut nodes, &mut edges, None);
        }
        for control in &workflow.controls {
            compile_flow(control, &mut nodes, &mut edges, None);
        }
    } else {
        // [V1.2-STEP-GROUP] 步骤组是编译期组合语法：展开为普通步骤后再走顺序屏障，
        // 避免为组生成不存在的幻影节点导致非法 DAG 边。组内步骤 ID 由 semantic CF2001/CF4418 保证唯一。
        let mut expanded = Vec::new();
        for item in &workflow.flow {
            match item {
                FlowNode::StepGroup(group) => {
                    for step in &group.steps {
                        expanded.push(FlowNode::Step(Box::new(step.clone())));
                    }
                }
                other => expanded.push(other.clone()),
            }
        }
        compile_top_level_flow(&expanded, &mut nodes, &mut edges);
    }
    let mut variables = BTreeMap::new();
    for variable in &workflow.variables {
        variables.insert(
            variable.name.clone(),
            VariableIr {
                type_name: variable.type_name.clone(),
                required: variable.required,
                source: match variable.source {
                    VariableSource::Input => "input",
                    VariableSource::Local => "local",
                    VariableSource::Deferred => "deferred",
                }
                .into(),
                // input 的 default 保持历史字段；本地变量用 value，防止 API 调用方将其误覆写。
                default: (variable.source == VariableSource::Input)
                    .then(|| variable.default.as_ref().map(value_ir))
                    .flatten(),
                value: (variable.source != VariableSource::Input)
                    .then(|| variable.default.as_ref().map(value_ir))
                    .flatten(),
            },
        );
    }
    let trigger = match &workflow.trigger {
        TriggerNode::Manual => TriggerIr::Manual,
        TriggerNode::Schedule { cron, timezone } => TriggerIr::Schedule {
            cron: cron.clone(),
            timezone: timezone.clone(),
        },
        TriggerNode::Event { name } => TriggerIr::Event {
            event: name.clone(),
        },
        TriggerNode::Http { path, method } => TriggerIr::Http {
            path: path.clone(),
            method: method.clone(),
        },
        TriggerNode::Interval { raw, .. } => TriggerIr::Interval { every: raw.clone() },
    };
    let mut labels = BTreeMap::new();
    if !workflow.metadata.tags.is_empty() {
        labels.insert("cloudflow.io/tags".into(), workflow.metadata.tags.join(","));
    }
    let mut extensions = BTreeMap::new();
    if !workflow.includes.is_empty() {
        extensions.insert(
            "includes".into(),
            Value::Array(
                workflow
                    .includes
                    .iter()
                    .map(|include| Value::String(include.path.clone()))
                    .collect(),
            ),
        );
        // [V1.2-IMPORT-ALIAS] 保留 import 别名映射，供 IDE/浏览器识别模块引用关系。
        let aliases = workflow
            .includes
            .iter()
            .filter_map(|include| {
                include
                    .alias
                    .as_ref()
                    .map(|alias| (alias.clone(), include.path.clone()))
            })
            .collect::<BTreeMap<_, _>>();
        if !aliases.is_empty() {
            extensions.insert(
                "importAliases".into(),
                serde_json::to_value(aliases).unwrap_or(Value::Object(Map::new())),
            );
        }
    }
    if !workflow.handlers.is_empty() {
        extensions.insert(
            "handlers".into(),
            Value::Array(workflow.handlers.iter().map(handler_ir).collect()),
        );
    }
    WorkflowIrV1 {
        api_version: "workflow.cloudflow.io/v1".into(),
        kind: "Workflow".into(),
        metadata: MetadataIr {
            name: workflow.name.clone(),
            display_name: workflow.metadata.display_name.clone(),
            description: workflow.metadata.description.clone(),
            version: workflow.metadata.version.clone(),
            owner: workflow.metadata.author.clone(),
            labels,
            namespace: workflow.namespace.clone(),
            changelog: workflow.metadata.changelog.clone(),
            tags: workflow.metadata.tags.clone(),
            ..Default::default()
        },
        spec: SpecIr {
            trigger,
            variables,
            graph: GraphIr { nodes, edges },
            outputs: BTreeMap::new(),
            environment: workflow
                .environment
                .iter()
                .map(|entry| (entry.key.clone(), value_ir(&entry.value)))
                .collect(),
            audit: workflow.audit.as_ref().map(|audit| AuditIr {
                level: audit.level.clone(),
                description: audit.description.clone(),
            }),
        },
        runtime: RuntimeIr {
            timeout_seconds: workflow
                .runtime
                .timeout
                .as_ref()
                .map(|value| value.milliseconds.div_ceil(1_000)),
            max_parallel: workflow.runtime.max_parallel,
            retry_policy: workflow.runtime.retry.as_ref().map(retry_ir),
        },
        security: SecurityIr::default(),
        extensions,
    }
}

/// [CLOUDFLOW-ORDER-001] 控制流是有副作用的顺序边界：源码中紧邻控制节点前后的顶层节点
/// 必须等待该边界完成。例如 `wait approval {}` 后的 step 不能在审批前执行。普通相邻 step
/// 仍遵从显式 `depends_on`，保留 DAG 并行能力。
fn compile_top_level_flow(flows: &[FlowNode], nodes: &mut Vec<NodeIr>, edges: &mut Vec<EdgeIr>) {
    let mut previous: Option<(String, bool)> = None;
    for flow in flows {
        let id = flow_id(flow);
        let control = !matches!(flow, FlowNode::Step(_));
        compile_flow(flow, nodes, edges, None);
        if let Some((previous_id, previous_is_control)) = previous {
            if control || previous_is_control {
                edges.push(EdgeIr {
                    from: previous_id,
                    to: id.clone(),
                });
            }
        }
        previous = Some((id, control));
    }
}

fn step_ir(step: &StepNode) -> NodeIr {
    let mut outputs = BTreeMap::new();
    if let Some(output) = &step.output {
        outputs.insert(
            output.clone(),
            serde_json::json!({"$ref": format!("steps.{}.output", step.id)}),
        );
    }
    let node = NodeIr {
        id: step.id.clone(),
        node_type: if step.action.as_ref().map(|action| action.provider.as_str()) == Some("plugin")
        {
            "plugin".into()
        } else {
            "task".into()
        },
        name: step.name.clone(),
        action: step.action.as_ref().map(action_ir),
        inputs: BTreeMap::new(),
        outputs,
        depends_on: step.depends_on.clone(),
        retry: step.retry.as_ref().map(retry_ir),
        retry_on: step.retry_on.clone(),
        timeout: step.timeout.as_ref().map(|value| value.raw.clone()),
        on_timeout: step.on_timeout.clone(),
        condition: step.condition.as_ref().map(expression_ir),
        // [V1.2-COND-DEPENDS] 条件依赖：求值为 false 时该节点无需等待静态依赖完成。
        depends_condition: step.depends_condition.as_ref().map(expression_ir),
        loop_config: None,
        parallel: None,
        error_handler: None,
        switch_config: None,
        delay_ms: None,
        notify_config: None,
        on_error: if step.on_error.is_empty() {
            None
        } else {
            Some(serde_json::json!({"nodes": flow_ids(&step.on_error)}))
        },
        control_parent: None,
        control_branch: None,
        children: flow_ids(&step.controls),
    };
    node
}

fn action_ir(action: &ActionNode) -> ActionIr {
    ActionIr {
        provider: action.provider.clone(),
        service: action.service.clone(),
        method: action.method.clone(),
        plugin_id: action.plugin_id.clone(),
        function: action.function.clone(),
        version: action.version.clone(),
        arguments: Value::Object(
            action
                .arguments
                .iter()
                .map(|(key, value)| (key.clone(), value_ir(value)))
                .collect(),
        ),
    }
}

fn retry_ir(value: &RetryNode) -> RetryIr {
    RetryIr {
        max_attempts: value.max_attempts.max(1),
        strategy: value.strategy.clone(),
    }
}

fn dependency_edges(steps: &[StepNode]) -> Vec<EdgeIr> {
    steps
        .iter()
        .flat_map(|step| {
            step.depends_on.iter().map(move |from| EdgeIr {
                from: from.clone(),
                to: step.id.clone(),
            })
        })
        .collect()
}

fn compile_flow(
    flow: &FlowNode,
    nodes: &mut Vec<NodeIr>,
    edges: &mut Vec<EdgeIr>,
    parent: Option<&str>,
) {
    match flow {
        FlowNode::Step(step) => compile_step(step, nodes, edges, parent),
        // [V1.2-STEP-GROUP] 步骤组在编译期扁平化为普通步骤节点；组内步骤 ID 由
        // semantic CF4418 保证全局唯一，组作为顺序边界参与调度（compile_top_level_flow）。
        FlowNode::StepGroup(value) => {
            for step in &value.steps {
                compile_step(step, nodes, edges, parent);
            }
        }
        FlowNode::Condition(value) => {
            let id = generated_id("condition", value.span);
            let true_ids = flow_ids(&value.true_branch);
            let false_ids = flow_ids(&value.false_branch);
            let mut node = control_node(
                &id,
                "condition",
                Some(expression_ir(&value.expression)),
                None,
                None,
                Some(serde_json::json!({"trueBranch": true_ids, "falseBranch": false_ids})),
            );
            node.children = true_ids.iter().chain(false_ids.iter()).cloned().collect();
            nodes.push(node);
            link_parent(parent, &id, edges);
            for child in value.true_branch.iter().chain(value.false_branch.iter()) {
                compile_flow(child, nodes, edges, Some(&id));
            }
        }
        FlowNode::Loop(value) => {
            let id = generated_id("loop", value.span);
            let mut node = control_node(
                &id,
                "loop",
                None,
                Some(
                    serde_json::json!({"kind":"foreach", "iterator": value.iterator, "collection": expression_ir(&value.collection), "body": flow_ids(&value.body), "maxIterations": 1000}),
                ),
                None,
                None,
            );
            node.children = flow_ids(&value.body);
            nodes.push(node);
            link_parent(parent, &id, edges);
            for child in &value.body {
                compile_flow_controlled(child, nodes, edges, Some(&id), &id, "loop");
            }
        }
        FlowNode::While(value) => {
            let id = generated_id("while", value.span);
            let mut node = control_node(
                &id,
                "loop",
                None,
                Some(serde_json::json!({
                    "kind":"while",
                    "condition": expression_ir(&value.condition),
                    "body": flow_ids(&value.body),
                    "maxIterations": 1000
                })),
                None,
                None,
            );
            node.children = flow_ids(&value.body);
            nodes.push(node);
            link_parent(parent, &id, edges);
            for child in &value.body {
                compile_flow_controlled(child, nodes, edges, Some(&id), &id, "while");
            }
        }
        FlowNode::Parallel(value) => {
            let id = generated_id("parallel", value.span);
            // [V1.2-PARALLEL] 分支级并发限制写入 parallel 配置；未设置时 Runtime 沿用全局并发。
            let parallel_config = match value.max_concurrency {
                Some(max_concurrency) => serde_json::json!({
                    "branches": flow_ids(&value.branches),
                    "maxConcurrency": max_concurrency
                }),
                None => serde_json::json!({"branches": flow_ids(&value.branches)}),
            };
            let mut node = control_node(&id, "parallel", None, None, Some(parallel_config), None);
            node.children = flow_ids(&value.branches);
            nodes.push(node);
            link_parent(parent, &id, edges);
            for child in &value.branches {
                compile_flow(child, nodes, edges, Some(&id));
            }
        }
        FlowNode::TryCatch(value) => {
            let id = generated_id("try", value.span);
            let mut node = control_node(
                &id,
                "try",
                None,
                None,
                None,
                Some(serde_json::json!({
                    "catchBinding": value.catch_binding,
                    "try": flow_ids(&value.try_nodes),
                    "catch": flow_ids(&value.catch_nodes),
                    "finally": flow_ids(&value.finally_nodes)
                })),
            );
            node.children = value
                .try_nodes
                .iter()
                .chain(value.catch_nodes.iter())
                .chain(value.finally_nodes.iter())
                .map(flow_id)
                .collect();
            nodes.push(node);
            link_parent(parent, &id, edges);
            for child in &value.try_nodes {
                compile_flow_controlled(child, nodes, edges, Some(&id), &id, "try");
            }
            for child in &value.catch_nodes {
                compile_flow_controlled(child, nodes, edges, Some(&id), &id, "catch");
            }
            for child in &value.finally_nodes {
                compile_flow_controlled(child, nodes, edges, Some(&id), &id, "finally");
            }
        }
        FlowNode::Wait(value) => {
            let id = generated_id("wait", value.span);
            let mut node = control_node(
                &id,
                "wait",
                None,
                None,
                None,
                Some(serde_json::json!({"waitType": value.wait_type})),
            );
            node.timeout = value.timeout.as_ref().map(|timeout| timeout.raw.clone());
            nodes.push(node);
            link_parent(parent, &id, edges);
        }
        FlowNode::Assert(value) => {
            let id = generated_id("assert", value.span);
            let node = control_node(
                &id,
                "assert",
                Some(expression_ir(&value.condition)),
                None,
                None,
                None,
            );
            nodes.push(node);
            link_parent(parent, &id, edges);
        }
        FlowNode::Switch(value) => {
            let id = generated_id("switch", value.span);
            let cases = value
                .cases
                .iter()
                .map(|case| {
                    serde_json::json!({
                        "value": value_ir(&case.value),
                        "body": flow_ids(&case.body)
                    })
                })
                .collect::<Vec<_>>();
            let mut node = control_node(&id, "switch", None, None, None, None);
            node.switch_config = Some(serde_json::json!({
                "subject": expression_ir(&value.subject),
                "cases": cases,
                "default": flow_ids(&value.default_branch)
            }));
            node.children = value
                .cases
                .iter()
                .flat_map(|case| flow_ids(&case.body))
                .chain(flow_ids(&value.default_branch))
                .collect();
            nodes.push(node);
            link_parent(parent, &id, edges);
            for case in &value.cases {
                for child in &case.body {
                    compile_flow_controlled(child, nodes, edges, Some(&id), &id, "switch-case");
                }
            }
            for child in &value.default_branch {
                compile_flow_controlled(child, nodes, edges, Some(&id), &id, "switch-default");
            }
        }
        FlowNode::Delay(value) => {
            let id = generated_id("delay", value.span);
            let mut node = control_node(&id, "delay", None, None, None, None);
            node.delay_ms = Some(value.milliseconds);
            nodes.push(node);
            link_parent(parent, &id, edges);
        }
        // [V1.2-FOR] 索引/集合循环：与 foreach/while 共用 loop 节点，分别以
        // loop_config.kind = for-range / for 区分。范围端点或集合写入 config，由 Runtime 求值。
        FlowNode::For(value) => {
            let id = generated_id("for", value.span);
            let loop_config = if value.range_from.is_some() && value.range_to.is_some() {
                serde_json::json!({
                    "kind": "for-range",
                    "iterator": value.iterator,
                    "from": expression_ir(value.range_from.as_ref().expect("for-range 起点")),
                    "to": expression_ir(value.range_to.as_ref().expect("for-range 终点")),
                    "body": flow_ids(&value.body),
                    "maxIterations": 1000
                })
            } else {
                serde_json::json!({
                    "kind": "for",
                    "iterator": value.iterator,
                    "collection": expression_ir(
                        value.collection.as_ref().expect("for 需要 range 或集合")
                    ),
                    "body": flow_ids(&value.body),
                    "maxIterations": 1000
                })
            };
            let mut node = control_node(&id, "loop", None, Some(loop_config), None, None);
            node.children = flow_ids(&value.body);
            nodes.push(node);
            link_parent(parent, &id, edges);
            for child in &value.body {
                compile_flow_controlled(child, nodes, edges, Some(&id), &id, "for");
            }
        }
        // [V1.2-VALIDATE] 校验节点：condition 保存布尔表达式，Runtime 求值为 false 时报错。
        FlowNode::Validate(value) => {
            let id = generated_id("validate", value.span);
            let node = control_node(
                &id,
                "validate",
                Some(expression_ir(&value.condition)),
                None,
                None,
                None,
            );
            nodes.push(node);
            link_parent(parent, &id, edges);
        }
        // [V1.2-NOTIFY] 内建通知：notifyConfig 携带 channel/to/message。
        FlowNode::Notify(value) => {
            let id = generated_id("notify", value.span);
            let mut node = control_node(&id, "notify", None, None, None, None);
            node.notify_config = Some(serde_json::json!({
                "channel": value.channel,
                "to": value.recipient.as_ref().map(value_ir),
                "message": value.message.as_ref().map(value_ir)
            }));
            nodes.push(node);
            link_parent(parent, &id, edges);
        }
        // [V1.2-RETURN] 步骤级提前返回：以 returnConfig 承载可选输出表达式。
        FlowNode::Return(value) => {
            let id = generated_id("return", value.span);
            let mut node = control_node(&id, "return", None, None, None, None);
            if let Some(output) = &value.output {
                node.inputs.insert("output".into(), expression_ir(output));
            }
            nodes.push(node);
            link_parent(parent, &id, edges);
        }
        // [V1.2-BREAK-CONTINUE] 循环控制节点，无子节点；Runtime 通过控制信号跳出/继续循环。
        FlowNode::Break(value) => {
            let id = generated_id("break", value.span);
            let node = control_node(&id, "break", None, None, None, None);
            nodes.push(node);
            link_parent(parent, &id, edges);
        }
        FlowNode::Continue(value) => {
            let id = generated_id("continue", value.span);
            let node = control_node(&id, "continue", None, None, None, None);
            nodes.push(node);
            link_parent(parent, &id, edges);
        }
    }
}

fn compile_step(
    step: &StepNode,
    nodes: &mut Vec<NodeIr>,
    edges: &mut Vec<EdgeIr>,
    parent: Option<&str>,
) {
    nodes.push(step_ir(step));
    edges.extend(dependency_edges(std::slice::from_ref(step)));
    link_parent(parent, &step.id, edges);
    // [CLOUDFLOW-IR-CONTROL-001] 原实现只把 step 内控制流写成摘要，Runtime 看不到可调度节点；
    // 现在递归生成完整节点和父子边，保证 AST 中每个控制节点都进入机器可读 IR。
    for control in &step.controls {
        compile_flow(control, nodes, edges, Some(&step.id));
    }
    // [V1.2-ON_ERROR] 步骤级错误处理节点作为 step 的控制子节点编译（控制面跳过，失败时派发）。
    for control in &step.on_error {
        compile_flow_controlled(control, nodes, edges, Some(&step.id), &step.id, "on_error");
    }
}

/// 为 foreach/while/try 的内部节点写入归属标记。它们仍保留在主图，供 IDE 和审计读取，
/// 但静态调度器必须跳过，由控制节点按运行时上下文动态执行，避免重复副作用。
fn compile_flow_controlled(
    flow: &FlowNode,
    nodes: &mut Vec<NodeIr>,
    edges: &mut Vec<EdgeIr>,
    parent: Option<&str>,
    control_parent: &str,
    control_branch: &str,
) {
    let before = nodes.len();
    compile_flow(flow, nodes, edges, parent);
    for node in &mut nodes[before..] {
        node.control_parent = Some(control_parent.into());
        node.control_branch = Some(control_branch.into());
    }
}

fn control_node(
    id: &str,
    node_type: &str,
    condition: Option<Value>,
    loop_config: Option<Value>,
    parallel: Option<Value>,
    error_handler: Option<Value>,
) -> NodeIr {
    NodeIr {
        id: id.into(),
        node_type: node_type.into(),
        condition,
        loop_config,
        parallel,
        error_handler,
        children: Vec::new(),
        ..Default::default()
    }
}

fn link_parent(parent: Option<&str>, child: &str, edges: &mut Vec<EdgeIr>) {
    if let Some(parent) = parent {
        edges.push(EdgeIr {
            from: parent.into(),
            to: child.into(),
        });
    }
}

fn flow_ids(nodes: &[FlowNode]) -> Vec<String> {
    nodes.iter().map(flow_id).collect()
}

fn flow_id(node: &FlowNode) -> String {
    match node {
        FlowNode::Step(value) => value.id.clone(),
        FlowNode::Condition(value) => generated_id("condition", value.span),
        FlowNode::Loop(value) => generated_id("loop", value.span),
        FlowNode::While(value) => generated_id("while", value.span),
        FlowNode::Parallel(value) => generated_id("parallel", value.span),
        FlowNode::TryCatch(value) => generated_id("try", value.span),
        FlowNode::Wait(value) => generated_id("wait", value.span),
        FlowNode::Assert(value) => generated_id("assert", value.span),
        FlowNode::Switch(value) => generated_id("switch", value.span),
        FlowNode::Delay(value) => generated_id("delay", value.span),
        FlowNode::For(value) => generated_id("for", value.span),
        FlowNode::Validate(value) => generated_id("validate", value.span),
        FlowNode::Notify(value) => generated_id("notify", value.span),
        FlowNode::Return(value) => generated_id("return", value.span),
        FlowNode::Break(value) => generated_id("break", value.span),
        FlowNode::Continue(value) => generated_id("continue", value.span),
        FlowNode::StepGroup(value) => value.id.clone(),
    }
}

fn generated_id(prefix: &str, span: Span) -> String {
    format!("__{prefix}_{}_{}", span.line, span.column)
}

fn handler_ir(handler: &HandlerNode) -> Value {
    let mut nodes = Vec::new();
    let mut edges = Vec::new();
    for node in &handler.nodes {
        compile_flow(node, &mut nodes, &mut edges, None);
    }
    // 失败处理图与主执行图隔离，避免调度器在正常路径把 handler 节点当作无依赖任务提前执行。
    serde_json::json!({"id": handler.id, "graph": {"nodes": nodes, "edges": edges}})
}

pub fn value_ir(value: &ValueNode) -> Value {
    match value {
        ValueNode::String(value) | ValueNode::Duration(value) | ValueNode::Enum(value) => {
            Value::String(value.clone())
        }
        ValueNode::Number(value) => Value::Number(value.clone()),
        ValueNode::Boolean(value) => Value::Bool(*value),
        ValueNode::VariableRef(value) => serde_json::json!({"$ref": value}),
        // [V1.2-INTERPOLATION] 字符串模板：段序列（String | {"$ref": ...}）。
        ValueNode::Template(segments) => serde_json::json!({
            "$template": segments.iter().map(value_ir).collect::<Vec<_>>()
        }),
        ValueNode::Expression(value) => expression_ir(value),
        ValueNode::Array(values) => Value::Array(values.iter().map(value_ir).collect()),
        ValueNode::Object(values) => Value::Object(
            values
                .iter()
                .map(|(key, value)| (key.clone(), value_ir(value)))
                .collect(),
        ),
        ValueNode::Call {
            function,
            positional,
            named,
        } => serde_json::json!({
            "$call": {"function": function, "arguments": positional.iter().map(value_ir).collect::<Vec<_>>(), "namedArguments": named.iter().map(|(key, value)| (key.clone(), value_ir(value))).collect::<Map<_, _>>()}
        }),
    }
}

pub fn expression_ir(expression: &ExpressionNode) -> Value {
    match &expression.kind {
        ExpressionKind::Literal(value) => value_ir(value),
        ExpressionKind::Reference(value) => serde_json::json!({"$ref": value}),
        ExpressionKind::Unary { operator, operand } => {
            serde_json::json!({"$expr": {"operator": operator, "operand": expression_ir(operand)}})
        }
        ExpressionKind::Binary {
            operator,
            left,
            right,
        } => {
            serde_json::json!({"$expr": {"operator": operator, "left": expression_ir(left), "right": expression_ir(right)}})
        }
        ExpressionKind::Ternary {
            condition,
            when_true,
            when_false,
        } => serde_json::json!({"$expr": {
            "condition": expression_ir(condition),
            "whenTrue": expression_ir(when_true),
            "whenFalse": expression_ir(when_false)
        }}),
        ExpressionKind::Call {
            function,
            arguments,
        } => {
            serde_json::json!({"$expr": {"function": function, "arguments": arguments.iter().map(expression_ir).collect::<Vec<_>>()}})
        }
        // [V1.2-PIPELINE] 集合处理管道：input + stages。
        ExpressionKind::Pipe { input, op } => serde_json::json!({
            "$pipeline": {
                "input": expression_ir(input),
                "op": pipeline_op_ir(op)
            }
        }),
    }
}

/// [V1.2-PIPELINE] 管道操作 IR。
fn pipeline_op_ir(op: &PipeOp) -> Value {
    match op {
        PipeOp::Filter(expr) => {
            serde_json::json!({"op": "filter", "predicate": expression_ir(expr)})
        }
        PipeOp::Map(field) => {
            serde_json::json!({"op": "map", "field": field})
        }
        PipeOp::Reduce(function) => serde_json::json!({"op": "reduce", "function": function}),
    }
}

/// 对 IR 做结构和 DAG 校验，Runtime 和 Workflow Service 共用。
pub fn validate_ir(ir: &WorkflowIrV1) -> Vec<String> {
    let mut errors = Vec::new();
    if ir.api_version != "workflow.cloudflow.io/v1" {
        errors.push("unsupported apiVersion".into());
    }
    if ir.kind != "Workflow" {
        errors.push("kind must be Workflow".into());
    }
    let ids = ir
        .spec
        .graph
        .nodes
        .iter()
        .map(|node| node.id.as_str())
        .collect::<HashSet<_>>();
    if ids.len() != ir.spec.graph.nodes.len() {
        errors.push("graph contains duplicate node ids".into());
    }
    for edge in &ir.spec.graph.edges {
        if !ids.contains(edge.from.as_str()) || !ids.contains(edge.to.as_str()) {
            errors.push(format!(
                "edge references unknown node {} -> {}",
                edge.from, edge.to
            ));
        }
    }
    if ir_has_cycle(ir) {
        errors.push("workflow graph contains a cycle".into());
    }
    errors
}

fn ir_has_cycle(ir: &WorkflowIrV1) -> bool {
    let mut incoming = ir
        .spec
        .graph
        .nodes
        .iter()
        .map(|node| (node.id.as_str(), 0usize))
        .collect::<BTreeMap<_, _>>();
    for edge in &ir.spec.graph.edges {
        if let Some(value) = incoming.get_mut(edge.to.as_str()) {
            *value += 1;
        }
    }
    let mut ready = incoming
        .iter()
        .filter_map(|(id, count)| (*count == 0).then_some(*id))
        .collect::<Vec<_>>();
    let mut visited = 0;
    while let Some(id) = ready.pop() {
        visited += 1;
        for edge in ir.spec.graph.edges.iter().filter(|edge| edge.from == id) {
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
