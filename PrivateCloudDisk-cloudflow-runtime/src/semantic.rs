//! CloudFlow 语义校验：引用、类型、DAG、能力和基础资源上限。

use crate::ast::*;
use crate::diagnostic::Diagnostic;
use std::collections::{HashMap, HashSet};

pub trait CapabilityCatalog {
    fn contains(&self, key: &str) -> bool;
}

#[derive(Default)]
pub struct InMemoryCapabilityCatalog {
    keys: HashSet<String>,
}
impl InMemoryCapabilityCatalog {
    pub fn insert(&mut self, key: &str) {
        self.keys.insert(key.to_string());
    }
}
impl CapabilityCatalog for InMemoryCapabilityCatalog {
    fn contains(&self, key: &str) -> bool {
        self.keys.contains(key)
    }
}

/// [V1.2-RETRY_ON] 内置可重试异常类型白名单。
const RETRYABLE_EXCEPTIONS: &[&str] = &[
    "TimeoutException",
    "NetworkException",
    "PluginException",
    "StorageException",
    "PermissionException",
    "TransientException",
    "ValidationException",
    "WorkerUnavailableException",
    "GenericException",
];
/// [V1.2-TIMEOUT-BLOCK] 允许的 on_timeout 行为取值。
const ON_TIMEOUT_VALUES: &[&str] = &["fail", "continue", "retry"];

pub fn validate(
    workflow: &WorkflowNode,
    catalog: &dyn CapabilityCatalog,
    source: &str,
    filename: &str,
) -> Vec<Diagnostic> {
    let mut diagnostics = Vec::new();
    match &workflow.trigger {
        TriggerNode::Schedule { cron, .. } if cron.trim().is_empty() => diagnostics.push(diag(
            "CF1301",
            "AST_ERROR",
            "schedule trigger 缺少 cron",
            source,
            filename,
            workflow.span,
            vec!["cron".into()],
            None,
        )),
        TriggerNode::Event { name } if name.trim().is_empty() => diagnostics.push(diag(
            "CF1301",
            "AST_ERROR",
            "event trigger 缺少 name",
            source,
            filename,
            workflow.span,
            vec!["name".into()],
            None,
        )),
        TriggerNode::Http { path, method } => {
            if path.trim().is_empty() {
                diagnostics.push(diag(
                    "CF1301",
                    "AST_ERROR",
                    "http trigger 缺少 path",
                    source,
                    filename,
                    workflow.span,
                    vec!["path".into()],
                    None,
                ));
            }
            // [V1.2-WEBHOOK] 校验 HTTP 方法白名单。
            if let Some(method) = method {
                const HTTP_METHODS: &[&str] =
                    &["GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"];
                if !HTTP_METHODS.contains(&method.as_str()) {
                    diagnostics.push(diag(
                        "CF4413",
                        "WEBHOOK_ERROR",
                        format!("http trigger 方法 {method} 非法"),
                        source,
                        filename,
                        workflow.span,
                        vec!["POST".into(), "GET".into(), "PUT".into()],
                        Some(
                            "http 触发 method 仅支持 GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS".into(),
                        ),
                    ));
                }
            }
        }
        // [V1.2-INTERVAL-TRIGGER] 周期必须非空且大于 0。
        TriggerNode::Interval { raw, milliseconds }
            if raw.trim().is_empty() || *milliseconds == 0 =>
        {
            diagnostics.push(diag(
                "CF4414",
                "INTERVAL_ERROR",
                "interval trigger 时长必须大于 0",
                source,
                filename,
                workflow.span,
                vec!["5m".into(), "1h".into(), "30s".into()],
                Some("如 trigger { interval = 5m }".into()),
            ));
        }
        _ => {}
    }
    // [V1.2-AUDIT] 审计 level 必须在 low/medium/high 白名单内（缺省视为 low）。
    if let Some(audit) = &workflow.audit {
        const AUDIT_LEVELS: &[&str] = &["low", "medium", "high"];
        let level = audit.level.trim().to_lowercase();
        if !level.is_empty() && !AUDIT_LEVELS.contains(&level.as_str()) {
            diagnostics.push(diag(
                "CF4415",
                "AUDIT_ERROR",
                format!("audit level `{}` 非法", audit.level),
                source,
                filename,
                audit.span,
                vec!["low".into(), "medium".into(), "high".into()],
                Some("audit level 仅支持 low/medium/high".into()),
            ));
        }
    }
    // [V1.2-ENVIRONMENT] 环境变量值必须是字面量（string/number/boolean），
    // 不得引用 vars/表达式，避免编译期无法确定注入值。
    for entry in &workflow.environment {
        match &entry.value {
            ValueNode::VariableRef(_) | ValueNode::Expression(_) | ValueNode::Call { .. } => {
                diagnostics.push(diag(
                    "CF4405",
                    "ENVIRONMENT_ERROR",
                    format!(
                        "environment 变量 {} 必须是字面量（string/number/boolean）",
                        entry.key
                    ),
                    source,
                    filename,
                    entry.span,
                    vec![],
                    Some("环境变量在编译期注入，不能引用运行时变量".into()),
                ));
            }
            _ => {}
        }
    }
    // [V1.2-NAMESPACE] 命名空间必须满足小写点分标识符。
    if let Some(namespace) = &workflow.namespace {
        if namespace.is_empty()
            || !namespace.split('.').all(|part| {
                !part.is_empty() && part.chars().next().is_some_and(|c| c.is_ascii_lowercase())
            })
        {
            diagnostics.push(diag(
                "CF4406",
                "NAMESPACE_ERROR",
                format!("namespace `{namespace}` 不符合规范，需小写点分标识符"),
                source,
                filename,
                workflow.span,
                vec![],
                Some("例如 com.example.workflows".into()),
            ));
        }
    }
    // [V1.2-IMPORT-ALIAS] import 别名必须唯一。
    {
        let mut aliases = HashMap::<&str, &str>::new();
        for include in &workflow.includes {
            if let Some(alias) = &include.alias {
                if let Some(previous) = aliases.insert(alias.as_str(), include.path.as_str()) {
                    diagnostics.push(diag(
                        "CF4407",
                        "IMPORT_ERROR",
                        format!("import 别名 `{alias}` 重复，先前指向 `{previous}`"),
                        source,
                        filename,
                        include.span,
                        vec![],
                        Some("为每个模块使用唯一的别名".into()),
                    ));
                }
            }
        }
    }
    let variable_types = workflow
        .variables
        .iter()
        .map(|variable| (variable.name.as_str(), variable.type_name.as_str()))
        .collect::<HashMap<_, _>>();
    for variable in &workflow.variables {
        if let Some(default) = &variable.default {
            if !value_matches_type(default, &variable.type_name, &variable_types) {
                diagnostics.push(diag(
                    "CF2101",
                    "TYPE_ERROR",
                    format!(
                        "变量 {} 的默认值与类型 {} 不匹配",
                        variable.name, variable.type_name
                    ),
                    source,
                    filename,
                    variable.span,
                    vec![],
                    Some("请调整显式类型、input.<type> 或初始值".into()),
                ));
            }
        }
        if variable.source == VariableSource::Deferred && variable.required {
            diagnostics.push(diag(
                "CF2101",
                "TYPE_ERROR",
                format!("延迟变量 {} 不能声明为 required", variable.name),
                source,
                filename,
                variable.span,
                vec![],
                Some("required 仅适用于 input.<type> 声明".into()),
            ));
        }
    }
    let mut all_steps = workflow.steps.iter().collect::<Vec<_>>();
    collect_control_steps(&workflow.controls, &mut all_steps);
    for handler in &workflow.handlers {
        collect_control_steps(&handler.nodes, &mut all_steps);
    }
    if all_steps.is_empty() {
        diagnostics.push(diag(
            "CF2001",
            "SEMANTIC_ERROR",
            "工作流至少需要一个 step",
            source,
            filename,
            workflow.span,
            vec!["step".into()],
            None,
        ));
    }
    if all_steps.len() > 200 {
        diagnostics.push(diag(
            "CF2101",
            "RESOURCE_LIMIT",
            "步骤数量不能超过 200",
            source,
            filename,
            workflow.span,
            vec![],
            Some("请拆分为子工作流".into()),
        ));
    }
    let variable_names = workflow
        .variables
        .iter()
        .map(|value| value.name.clone())
        .collect::<HashSet<_>>();
    // [CLOUDFLOW-VARIABLE-005] 局部变量不得提升为工作流全局变量。foreach iterator 与
    // catch binding 仅在其控制块的递归校验调用中传入；这样 `item` 在循环体外出现会在编译期
    // 明确失败，不再依赖 Runtime 兜底。
    let root_locals = HashSet::new();
    let mut ids = HashSet::new();
    let mut graph = HashMap::<String, Vec<String>>::new();
    for step in &all_steps {
        if !ids.insert(step.id.clone()) {
            diagnostics.push(diag(
                "CF2001",
                "SEMANTIC_ERROR",
                format!("步骤 ID 重复：{}", step.id),
                source,
                filename,
                step.span,
                vec![],
                Some("为每个 step 使用唯一的小写标识".into()),
            ));
        }
        graph.insert(step.id.clone(), step.depends_on.clone());
    }
    // [CLOUDFLOW-VARIABLE-002] 本地变量的 initializer 同样可能包含 $ref/$expr；
    // 原实现只校验 action 参数，导致 `x = vars.missing` 能穿透编译进入 Runtime。
    for variable in &workflow.variables {
        if let Some(value) = &variable.default {
            validate_value_references(
                value,
                &variable_names,
                &root_locals,
                &ids,
                source,
                filename,
                variable.span,
                &mut diagnostics,
            );
        }
    }
    for step in &all_steps {
        for dependency in &step.depends_on {
            if !graph.contains_key(dependency) {
                diagnostics.push(diag(
                    "CF2002",
                    "REFERENCE_ERROR",
                    format!("步骤 {} 依赖不存在：{dependency}", step.id),
                    source,
                    filename,
                    step.span,
                    nearest_step(dependency, graph.keys().map(String::as_str)),
                    Some("depends_on 必须引用同一 workflow 中的 step".into()),
                ));
            }
        }
    }
    // [V1.2-USE-WITH] 收集已声明 import 别名，供 step `use <alias>` 解析。
    let import_aliases = workflow
        .includes
        .iter()
        .filter_map(|include| include.alias.clone())
        .collect::<HashSet<_>>();
    // [V1.2-STEP-GROUP] 步骤组 ID 必须唯一且不与任何 step ID 冲突（CF4418），
    // 且组不能为空。组内步骤已通过 collect_control_steps 进入 all_steps 参与全局校验。
    // 组内步骤 ID 的全局唯一性已由上面 CF2001 对所有 all_steps（含组内步骤）统一校验；
    // 这里只校验组本身：组名不得与 step ID 或其它组名冲突、组不能为空。
    let mut group_ids = HashSet::new();
    for group in &workflow.step_groups {
        if group.steps.is_empty() {
            diagnostics.push(diag(
                "CF4418",
                "GROUP_ERROR",
                format!("step group `{}` 不能为空", group.id),
                source,
                filename,
                group.span,
                vec!["step a { ... }".into()],
                Some("每组至少包含一个 step".into()),
            ));
            continue;
        }
        if !group_ids.insert(group.id.clone()) {
            diagnostics.push(diag(
                "CF4418",
                "GROUP_ERROR",
                format!("step group ID 重复：{}", group.id),
                source,
                filename,
                group.span,
                vec![],
                Some("step group 名称必须全局唯一".into()),
            ));
        }
        if ids.contains(&group.id) {
            diagnostics.push(diag(
                "CF4418",
                "GROUP_ERROR",
                format!("step group `{}` 与 step ID 冲突", group.id),
                source,
                filename,
                group.span,
                vec![],
                Some("step group 名称不得与任何 step ID 相同".into()),
            ));
        }
    }
    // [V1.2-USE-WITH / V1.2-COND-DEPENDS] 步骤级扩展字段统一在 all_steps 上校验：
    // use_alias 必须指向已声明 import 别名（CF4420），depends_condition 必须可求值为布尔（CF4421）。
    for step in &all_steps {
        if let Some(alias) = &step.use_alias {
            if !import_aliases.contains(alias) {
                diagnostics.push(diag(
                    "CF4420",
                    "USE_ERROR",
                    format!("step `{}` 引用了未声明模块别名 `{alias}`", step.id),
                    source,
                    filename,
                    step.span,
                    vec![],
                    Some("请先 import 'module.flow' as <alias> 声明别名".into()),
                ));
            }
        }
        if let Some(condition) = &step.depends_condition {
            validate_expression(
                condition,
                &variable_names,
                &root_locals,
                &ids,
                source,
                filename,
                &mut diagnostics,
            );
            if let Some(ty) = inferred_expression_type(condition, &variable_types) {
                if ty != "boolean" {
                    diagnostics.push(diag(
                        "CF4421",
                        "COND_DEPENDS_ERROR",
                        format!("step `{}` 的条件依赖必须为布尔表达式", step.id),
                        source,
                        filename,
                        step.span,
                        vec!["vars.flag == true".into(), "steps.a.output.ok".into()],
                        Some("depends_on ... if <cond> 的 <cond> 必须是布尔表达式".into()),
                    ));
                }
            }
        }
    }
    // 仅从顶层 step 进入 action/控制块校验；嵌套 step 由 validate_controls 在其真实词法
    // 作用域递归处理。all_steps 仍用于全局 ID 与 depends_on DAG 校验，不能直接拿来校验裸局部变量。
    for step in &workflow.steps {
        validate_step_body(
            step,
            &variable_names,
            &root_locals,
            &ids,
            catalog,
            source,
            filename,
            &mut diagnostics,
        );
    }
    validate_controls(
        &workflow.controls,
        &variable_names,
        &root_locals,
        &ids,
        catalog,
        source,
        filename,
        &mut diagnostics,
        0,
    );
    if has_cycle(&graph) {
        diagnostics.push(diag(
            "CF2002",
            "REFERENCE_ERROR",
            "工作流依赖存在循环，无法构建 DAG",
            source,
            filename,
            workflow.span,
            vec![],
            Some("删除循环依赖，保证所有边最终指向前置步骤".into()),
        ));
    }
    diagnostics
}

#[allow(clippy::too_many_arguments)]
fn validate_step_body(
    step: &StepNode,
    variables: &HashSet<String>,
    locals: &HashSet<String>,
    steps: &HashSet<String>,
    catalog: &dyn CapabilityCatalog,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) {
    if let Some(action) = &step.action {
        validate_action(action, step.span, catalog, source, filename, diagnostics);
        for value in action.arguments.values() {
            validate_value_references(
                value,
                variables,
                locals,
                steps,
                source,
                filename,
                step.span,
                diagnostics,
            );
        }
    }
    if let Some(condition) = &step.condition {
        validate_expression(
            condition,
            variables,
            locals,
            steps,
            source,
            filename,
            diagnostics,
        );
    }
    // [V1.2-RETRY_ON] 校验可重试异常类型是否在白名单内。
    for exception in &step.retry_on {
        if !RETRYABLE_EXCEPTIONS.contains(&exception.as_str()) {
            diagnostics.push(diag(
                "CF4402",
                "RETRY_ERROR",
                format!("retry_on 引用了未知异常类型 `{exception}`"),
                source,
                filename,
                step.span,
                RETRYABLE_EXCEPTIONS
                    .iter()
                    .map(|value| format!("- {value}"))
                    .collect(),
                Some("仅支持内置异常类型白名单".into()),
            ));
        }
    }
    // [V1.2-TIMEOUT-BLOCK] on_timeout 取值只允许 fail/continue/retry。
    if let Some(on_timeout) = &step.on_timeout {
        if !ON_TIMEOUT_VALUES.contains(&on_timeout.as_str()) {
            diagnostics.push(diag(
                "CF4403",
                "TIMEOUT_ERROR",
                format!("on_timeout 取值 `{on_timeout}` 不合法"),
                source,
                filename,
                step.span,
                vec!["fail".into(), "continue".into(), "retry".into()],
                Some("on_timeout 仅支持 fail/continue/retry".into()),
            ));
        }
    }
    validate_controls(
        &step.controls,
        variables,
        locals,
        steps,
        catalog,
        source,
        filename,
        diagnostics,
        0,
    );
}

fn value_matches_type(value: &ValueNode, type_name: &str, variables: &HashMap<&str, &str>) -> bool {
    // [CLOUDFLOW-VARIABLE-004] 旧逻辑把所有 Expression 当成合法值，`x: number = true + 1`
    // 会绕过编译检查。能静态推导的表达式必须在语义阶段匹配目标类型；来自步骤输出等
    // 未声明 schema 的值保持 unknown，交由 Runtime/Capability Schema 做最后校验。
    inferred_value_type(value, variables)
        .is_none_or(|actual| actual == type_name || actual == "unknown")
}

fn inferred_value_type(value: &ValueNode, variables: &HashMap<&str, &str>) -> Option<String> {
    match value {
        ValueNode::String(_) => Some("string".into()),
        ValueNode::Number(_) => Some("number".into()),
        ValueNode::Boolean(_) => Some("boolean".into()),
        ValueNode::Null => Some("null".into()),
        ValueNode::Array(_) => Some("array".into()),
        ValueNode::Object(_) => Some("object".into()),
        ValueNode::Duration(_) => Some("duration".into()),
        ValueNode::VariableRef(reference) => {
            reference_type(reference, variables).map(str::to_owned)
        }
        ValueNode::Expression(expression) => inferred_expression_type(expression, variables),
        ValueNode::Template(_) => Some("string".into()),
        // 调用形式目前只用于 input 声明，语义类型由 input.<type> 直接决定；不将其当作
        // 普通本地变量字面量，避免绕过 source/input 的权限边界。
        ValueNode::Call { .. } | ValueNode::Enum(_) => None,
    }
}

fn inferred_expression_type(
    expression: &ExpressionNode,
    variables: &HashMap<&str, &str>,
) -> Option<String> {
    match &expression.kind {
        ExpressionKind::Literal(value) => inferred_value_type(value, variables),
        ExpressionKind::Reference(reference) => {
            reference_type(reference, variables).map(str::to_owned)
        }
        ExpressionKind::Unary { operator, operand } => match operator.as_str() {
            "-" if inferred_expression_type(operand, variables).as_deref() == Some("number") => {
                Some("number".into())
            }
            "!" => Some("boolean".into()),
            _ => None,
        },
        ExpressionKind::Binary {
            operator,
            left,
            right,
        } => match operator.as_str() {
            "+" | "-" | "*" | "/" | "%"
                if inferred_expression_type(left, variables).as_deref() == Some("number")
                    && inferred_expression_type(right, variables).as_deref() == Some("number") =>
            {
                Some("number".into())
            }
            "==" | "!=" | ">" | ">=" | "<" | "<=" | "&&" | "||" => Some("boolean".into()),
            _ => None,
        },
        ExpressionKind::Ternary {
            when_true,
            when_false,
            ..
        } => {
            let true_type = inferred_expression_type(when_true, variables);
            let false_type = inferred_expression_type(when_false, variables);
            match (true_type, false_type) {
                (Some(left), Some(right)) if left == right => Some(left),
                (Some(left), None) => Some(left),
                (None, Some(right)) => Some(right),
                _ => None,
            }
        }
        ExpressionKind::Call { function, .. } => match function.as_str() {
            "len" | "size" | "now" | "abs" | "round" | "floor" | "ceil" => Some("number".into()),
            "contains" | "starts_with" | "ends_with" => Some("boolean".into()),
            "trim" | "to_upper" | "to_lower" => Some("string".into()),
            // GitHub Actions 对齐：toJSON/formatNumber/formatDateTime 返回字符串。
            "to_json" | "format_number" | "format_date_time" => Some("string".into()),
            "range" => Some("array".into()),
            // `get` 的返回类型依赖容器元素，编译期未知。
            _ => None,
        },
        ExpressionKind::Pipe { .. } => None,
    }
}

fn validate_action(
    action: &ActionNode,
    span: Span,
    catalog: &dyn CapabilityCatalog,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) {
    let key = action_key(action);
    if action.provider == "plugin"
        && (action.plugin_id.as_deref().unwrap_or_default().is_empty()
            || action.function.as_deref().unwrap_or_default().is_empty())
    {
        diagnostics.push(diag(
            "CF3001",
            "CAPABILITY_ERROR",
            "plugin action 必须声明非空 id 和 function",
            source,
            filename,
            span,
            vec!["id".into(), "function".into()],
            Some("插件能力格式为 action plugin { id = \"...\" function = \"...\" }".into()),
        ));
    }
    if action.provider != "plugin"
        && (action.service.as_deref().unwrap_or_default().is_empty()
            || action.method.as_deref().unwrap_or_default().is_empty())
    {
        diagnostics.push(diag(
            "CF3001",
            "CAPABILITY_ERROR",
            "action 名称必须使用 service.method 或 provider.service.method",
            source,
            filename,
            span,
            vec!["file.list".into()],
            None,
        ));
    }
    if catalog.contains("__catalog_enabled__") && !catalog.contains(&key) {
        diagnostics.push(diag(
            "CF3001",
            "CAPABILITY_ERROR",
            format!("能力不存在或未启用：{key}"),
            source,
            filename,
            span,
            vec![],
            Some("请在 Capability Hub 注册该能力，或检查插件版本".into()),
        ));
    }
}

/// [V1.2-VALIDATE/FOR] 对可直接推导的字面量返回静态类型；引用/运算类型未知时返回 None，
/// 交由 Runtime 求值。用于 for-range 端点必须为 number、validate 表达式必须为 boolean 的编译期检查。
fn static_type(expression: &ExpressionNode) -> Option<&'static str> {
    match &expression.kind {
        ExpressionKind::Literal(ValueNode::Number(_)) => Some("number"),
        ExpressionKind::Literal(ValueNode::String(_)) => Some("string"),
        ExpressionKind::Literal(ValueNode::Template(_)) => Some("string"),
        ExpressionKind::Literal(ValueNode::Boolean(_)) => Some("boolean"),
        ExpressionKind::Literal(ValueNode::Null) => Some("null"),
        ExpressionKind::Literal(ValueNode::Duration(_)) => Some("duration"),
        _ => None,
    }
}

// 语义诊断需要同时携带全局变量、词法局部变量、步骤表和源码位置；为保留无隐式全局状态的
// 可测试接口，显式参数比引入可变单例更安全。
/// [V1.2-STEP-GROUP] 嵌套在控制流或步骤组中的 step 使用同一 Action 契约校验。
/// 旧实现仅检查 condition，导致嵌套 action 的能力名称、变量引用可绕过语义校验并在
/// Runtime 才失败；这里统一走该入口。
#[allow(clippy::too_many_arguments)]
fn validate_nested_step(
    value: &StepNode,
    variables: &HashSet<String>,
    locals: &HashSet<String>,
    steps: &HashSet<String>,
    catalog: &dyn CapabilityCatalog,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
    loop_depth: usize,
) {
    if let Some(action) = &value.action {
        validate_action(action, value.span, catalog, source, filename, diagnostics);
        for argument in action.arguments.values() {
            validate_value_references(
                argument,
                variables,
                locals,
                steps,
                source,
                filename,
                value.span,
                diagnostics,
            );
        }
    }
    if let Some(expression) = &value.condition {
        validate_expression(
            expression,
            variables,
            locals,
            steps,
            source,
            filename,
            diagnostics,
        );
    }
    validate_controls(
        &value.controls,
        variables,
        locals,
        steps,
        catalog,
        source,
        filename,
        diagnostics,
        loop_depth,
    );
    // [V1.2-ON_ERROR] 步骤级错误处理节点在同一词法作用域校验。
    validate_controls(
        &value.on_error,
        variables,
        locals,
        steps,
        catalog,
        source,
        filename,
        diagnostics,
        loop_depth,
    );
}

#[allow(clippy::too_many_arguments)]
fn validate_controls(
    controls: &[FlowNode],
    variables: &HashSet<String>,
    locals: &HashSet<String>,
    steps: &HashSet<String>,
    catalog: &dyn CapabilityCatalog,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
    loop_depth: usize,
) {
    for control in controls {
        match control {
            FlowNode::Condition(value) => {
                validate_expression(
                    &value.expression,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    diagnostics,
                );
                validate_controls(
                    &value.true_branch,
                    variables,
                    locals,
                    steps,
                    catalog,
                    source,
                    filename,
                    diagnostics,
                    loop_depth,
                );
                validate_controls(
                    &value.false_branch,
                    variables,
                    locals,
                    steps,
                    catalog,
                    source,
                    filename,
                    diagnostics,
                    loop_depth,
                );
            }
            FlowNode::Loop(value) => {
                validate_expression(
                    &value.collection,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    diagnostics,
                );
                let mut loop_locals = locals.clone();
                loop_locals.insert(value.iterator.clone());
                validate_controls(
                    &value.body,
                    variables,
                    &loop_locals,
                    steps,
                    catalog,
                    source,
                    filename,
                    diagnostics,
                    loop_depth,
                );
            }
            FlowNode::For(value) => {
                if let Some(from) = &value.range_from {
                    if let Some(ty) = static_type(from) {
                        if ty != "number" {
                            diagnostics.push(diag(
                                "CF4410",
                                "FOR_ERROR",
                                "for range 起点必须是 number",
                                source,
                                filename,
                                from.span,
                                vec!["0".into(), "vars.start".into()],
                                Some("range(from,to) 的 from/to 必须可求值为数字".into()),
                            ));
                        }
                    }
                    validate_expression(
                        from,
                        variables,
                        locals,
                        steps,
                        source,
                        filename,
                        diagnostics,
                    );
                }
                if let Some(to) = &value.range_to {
                    if let Some(ty) = static_type(to) {
                        if ty != "number" {
                            diagnostics.push(diag(
                                "CF4410",
                                "FOR_ERROR",
                                "for range 终点必须是 number",
                                source,
                                filename,
                                to.span,
                                vec!["10".into(), "vars.max".into()],
                                Some("range(from,to) 的 from/to 必须可求值为数字".into()),
                            ));
                        }
                    }
                    validate_expression(
                        to,
                        variables,
                        locals,
                        steps,
                        source,
                        filename,
                        diagnostics,
                    );
                }
                if let Some(collection) = &value.collection {
                    validate_expression(
                        collection,
                        variables,
                        locals,
                        steps,
                        source,
                        filename,
                        diagnostics,
                    );
                }
                let mut loop_locals = locals.clone();
                loop_locals.insert(value.iterator.clone());
                validate_controls(
                    &value.body,
                    variables,
                    &loop_locals,
                    steps,
                    catalog,
                    source,
                    filename,
                    diagnostics,
                    loop_depth + 1,
                );
            }
            FlowNode::While(value) => {
                validate_expression(
                    &value.condition,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    diagnostics,
                );
                validate_controls(
                    &value.body,
                    variables,
                    locals,
                    steps,
                    catalog,
                    source,
                    filename,
                    diagnostics,
                    loop_depth + 1,
                );
            }
            FlowNode::Parallel(value) => {
                if value.max_concurrency.is_some_and(|value| value == 0) {
                    diagnostics.push(diag(
                        "CF4411",
                        "PARALLEL_ERROR",
                        "parallel max_concurrency 必须为正整数",
                        source,
                        filename,
                        value.span,
                        vec!["1".into(), "3".into(), "8".into()],
                        Some("如 parallel(max_concurrency=3)".into()),
                    ));
                }
                validate_controls(
                    &value.branches,
                    variables,
                    locals,
                    steps,
                    catalog,
                    source,
                    filename,
                    diagnostics,
                    loop_depth,
                );
            }
            FlowNode::TryCatch(value) => {
                validate_controls(
                    &value.try_nodes,
                    variables,
                    locals,
                    steps,
                    catalog,
                    source,
                    filename,
                    diagnostics,
                    loop_depth,
                );
                let mut catch_locals = locals.clone();
                if let Some(binding) = &value.catch_binding {
                    catch_locals.insert(binding.clone());
                }
                validate_controls(
                    &value.catch_nodes,
                    variables,
                    &catch_locals,
                    steps,
                    catalog,
                    source,
                    filename,
                    diagnostics,
                    loop_depth,
                );
                validate_controls(
                    &value.finally_nodes,
                    variables,
                    locals,
                    steps,
                    catalog,
                    source,
                    filename,
                    diagnostics,
                    loop_depth,
                );
            }
            FlowNode::Step(value) => validate_nested_step(
                value,
                variables,
                locals,
                steps,
                catalog,
                source,
                filename,
                diagnostics,
                loop_depth,
            ),
            // [V1.2-STEP-GROUP] 组内步骤以嵌套 step 契约校验（action 能力、参数引用、
            // condition、controls、on_error）；组 ID 冲突由语义顶层 CF4418 校验。
            FlowNode::StepGroup(value) => {
                for step in &value.steps {
                    validate_nested_step(
                        step,
                        variables,
                        locals,
                        steps,
                        catalog,
                        source,
                        filename,
                        diagnostics,
                        loop_depth,
                    );
                }
            }
            FlowNode::Wait(_) => {}
            FlowNode::Assert(value) => validate_expression(
                &value.condition,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            ),
            FlowNode::Validate(value) => {
                if let Some(ty) = static_type(&value.condition) {
                    if ty != "boolean" {
                        diagnostics.push(diag(
                            "CF4409",
                            "VALIDATE_ERROR",
                            "validate 表达式必须是 boolean",
                            source,
                            filename,
                            value.span,
                            vec!["vars.result > 0".into(), "item.enabled == true".into()],
                            Some("validate { ... } 内必须为布尔表达式".into()),
                        ));
                    }
                }
                validate_expression(
                    &value.condition,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    diagnostics,
                );
            }
            // [V1.2-NOTIFY] 校验通知渠道白名单与 to/message 中的变量引用。
            FlowNode::Notify(value) => {
                const NOTIFY_CHANNELS: &[&str] = &[
                    "email", "sms", "webhook", "slack", "dingtalk", "wecom", "system",
                ];
                if !value.channel.trim().is_empty()
                    && !NOTIFY_CHANNELS.contains(&value.channel.to_lowercase().as_str())
                {
                    diagnostics.push(diag(
                        "CF4416",
                        "NOTIFY_ERROR",
                        format!("通知渠道 `{}` 非法", value.channel),
                        source,
                        filename,
                        value.span,
                        vec!["email".into(), "sms".into(), "webhook".into()],
                        Some(
                            "notify 渠道仅支持 email/sms/webhook/slack/dingtalk/wecom/system"
                                .into(),
                        ),
                    ));
                }
                for payload in [&value.recipient, &value.message] {
                    if let Some(payload) = payload {
                        validate_value_references(
                            payload,
                            variables,
                            locals,
                            steps,
                            source,
                            filename,
                            value.span,
                            diagnostics,
                        );
                    }
                }
            }
            // [V1.2-RETURN] 校验返回输出表达式中的引用。
            FlowNode::Return(value) => {
                if let Some(output) = &value.output {
                    validate_expression(
                        output,
                        variables,
                        locals,
                        steps,
                        source,
                        filename,
                        diagnostics,
                    );
                }
            }
            FlowNode::Switch(value) => {
                validate_expression(
                    &value.subject,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    diagnostics,
                );
                for case in &value.cases {
                    // case 值本身为字面量，不参与引用校验；仅递归其分支体。
                    validate_controls(
                        &case.body,
                        variables,
                        locals,
                        steps,
                        catalog,
                        source,
                        filename,
                        diagnostics,
                        loop_depth,
                    );
                }
                validate_controls(
                    &value.default_branch,
                    variables,
                    locals,
                    steps,
                    catalog,
                    source,
                    filename,
                    diagnostics,
                    loop_depth,
                );
            }
            FlowNode::Delay(value) => {
                if value.milliseconds == 0 {
                    diagnostics.push(diag(
                        "CF4404",
                        "DELAY_ERROR",
                        "delay 时长必须大于 0",
                        source,
                        filename,
                        value.span,
                        vec!["1s".into(), "500ms".into(), "2m".into()],
                        Some("如 1s、500ms、2m".into()),
                    ));
                }
            }
            FlowNode::Break(value) => {
                if loop_depth == 0 {
                    diagnostics.push(diag(
                        "CF4408",
                        "CONTROL_ERROR",
                        "break 只能出现在 for/while 循环体内",
                        source,
                        filename,
                        value.span,
                        vec!["将 break 移入 for/while 循环体".into()],
                        Some("break 用于跳出最近的 for/while 循环".into()),
                    ));
                }
            }
            FlowNode::Continue(value) => {
                if loop_depth == 0 {
                    diagnostics.push(diag(
                        "CF4408",
                        "CONTROL_ERROR",
                        "continue 只能出现在 for/while 循环体内",
                        source,
                        filename,
                        value.span,
                        vec!["将 continue 移入 for/while 循环体".into()],
                        Some("continue 用于跳过本次迭代剩余部分".into()),
                    ));
                }
            }
        }
    }
}

#[allow(clippy::too_many_arguments)]
fn validate_value_references(
    value: &ValueNode,
    variables: &HashSet<String>,
    locals: &HashSet<String>,
    steps: &HashSet<String>,
    source: &str,
    filename: &str,
    span: Span,
    diagnostics: &mut Vec<Diagnostic>,
) {
    match value {
        ValueNode::VariableRef(reference) => validate_reference(
            reference,
            variables,
            locals,
            steps,
            source,
            filename,
            span,
            diagnostics,
        ),
        // [V1.2-INTERPOLATION] 模板中各 $ref 段独立校验。
        ValueNode::Template(segments) => {
            for segment in segments {
                if let ValueNode::VariableRef(reference) = segment {
                    validate_reference(
                        reference,
                        variables,
                        locals,
                        steps,
                        source,
                        filename,
                        span,
                        diagnostics,
                    );
                }
            }
        }
        ValueNode::Expression(expression) => validate_expression(
            expression,
            variables,
            locals,
            steps,
            source,
            filename,
            diagnostics,
        ),
        ValueNode::Array(values) => {
            for value in values {
                validate_value_references(
                    value,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    span,
                    diagnostics,
                );
            }
        }
        ValueNode::Object(values) => {
            for value in values.values() {
                validate_value_references(
                    value,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    span,
                    diagnostics,
                );
            }
        }
        ValueNode::Call {
            positional, named, ..
        } => {
            for value in positional {
                validate_value_references(
                    value,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    span,
                    diagnostics,
                );
            }
            for value in named.values() {
                validate_value_references(
                    value,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    span,
                    diagnostics,
                );
            }
        }
        _ => {}
    }
}

fn validate_expression(
    expression: &ExpressionNode,
    variables: &HashSet<String>,
    locals: &HashSet<String>,
    steps: &HashSet<String>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) {
    validate_expression_static_types(expression, source, filename, diagnostics);
    match &expression.kind {
        ExpressionKind::Reference(reference) => validate_reference(
            reference,
            variables,
            locals,
            steps,
            source,
            filename,
            expression.span,
            diagnostics,
        ),
        ExpressionKind::Unary { operand, .. } => validate_expression(
            operand,
            variables,
            locals,
            steps,
            source,
            filename,
            diagnostics,
        ),
        ExpressionKind::Binary { left, right, .. } => {
            validate_expression(
                left,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
            validate_expression(
                right,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
        }
        ExpressionKind::Ternary {
            condition,
            when_true,
            when_false,
        } => {
            validate_expression(
                condition,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
            validate_expression(
                when_true,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
            validate_expression(
                when_false,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
        }
        ExpressionKind::Call {
            function,
            arguments,
        } => {
            // 白名单唯一事实来源：表达式子系统 `builtins::is_builtin_function`
            // （与执行端求值、补全规范 `config.py::BUILTIN_FUNCTIONS` 保持一致）。
            if !crate::expression::builtins::is_builtin_function(function) {
                diagnostics.push(diag(
                    "CF2101",
                    "TYPE_ERROR",
                    format!("表达式函数未注册：{function}"),
                    source,
                    filename,
                    expression.span,
                    vec!["size".into(), "len".into()],
                    Some("表达式只能调用 Runtime 白名单纯函数".into()),
                ));
            }
            for argument in arguments {
                validate_expression(
                    argument,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    diagnostics,
                );
            }
        }
        // [V1.2-PIPELINE] 校验管道输入与 filter 谓词。
        ExpressionKind::Pipe { input, op } => {
            validate_expression(
                input,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
            if let PipeOp::Filter(predicate) = op {
                validate_filter_predicate(
                    predicate,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    diagnostics,
                );
            }
        }
        ExpressionKind::Literal(_) => {}
    }
}

/// [V1.2-PIPELINE] 校验 filter 谓词表达式；谓词内的裸标识符视为当前数组元素的字段
/// （行上下文），因此允许 `size > 100` 这类引用，而不要求 `vars.`/`steps.` 前缀。
/// 全局引用（vars./steps./workflow.）仍按严格规则校验，静态类型检查保持生效。
fn validate_filter_predicate(
    expression: &ExpressionNode,
    variables: &HashSet<String>,
    locals: &HashSet<String>,
    steps: &HashSet<String>,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) {
    validate_expression_static_types(expression, source, filename, diagnostics);
    match &expression.kind {
        ExpressionKind::Reference(reference) => {
            let global = reference.starts_with("vars.")
                || reference.starts_with("steps.")
                || reference.starts_with("workflow.");
            if global {
                validate_reference(
                    reference,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    expression.span,
                    diagnostics,
                );
            }
            // 裸标识符为行字段（元素属性），无 schema 可验，静默放行。
        }
        ExpressionKind::Unary { operand, .. } => validate_filter_predicate(
            operand,
            variables,
            locals,
            steps,
            source,
            filename,
            diagnostics,
        ),
        ExpressionKind::Binary { left, right, .. } => {
            validate_filter_predicate(
                left,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
            validate_filter_predicate(
                right,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
        }
        ExpressionKind::Ternary {
            condition,
            when_true,
            when_false,
        } => {
            validate_filter_predicate(
                condition,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
            validate_filter_predicate(
                when_true,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
            validate_filter_predicate(
                when_false,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
        }
        ExpressionKind::Call { arguments, .. } => {
            for argument in arguments {
                validate_filter_predicate(
                    argument,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    diagnostics,
                );
            }
        }
        ExpressionKind::Pipe { input, op } => {
            validate_expression(
                input,
                variables,
                locals,
                steps,
                source,
                filename,
                diagnostics,
            );
            if let PipeOp::Filter(predicate) = op {
                validate_filter_predicate(
                    predicate,
                    variables,
                    locals,
                    steps,
                    source,
                    filename,
                    diagnostics,
                );
            }
        }
        ExpressionKind::Literal(_) => {}
    }
}

/// 对不依赖变量 schema 的字面量表达式先做确定性类型拒绝。引用步骤输出时类型可能未知，
/// 不能误报；但 `true + 1`、`"a" % 2` 这类在编译期即可证明错误的表达式绝不能留给 Runtime。
fn validate_expression_static_types(
    expression: &ExpressionNode,
    source: &str,
    filename: &str,
    diagnostics: &mut Vec<Diagnostic>,
) {
    let empty = HashMap::new();
    let type_of = |value: &ExpressionNode| inferred_expression_type(value, &empty);
    match &expression.kind {
        ExpressionKind::Unary { operator, operand } if operator == "-" => {
            if let Some(actual) = type_of(operand).filter(|actual| actual != "number") {
                diagnostics.push(diag(
                    "CF2101",
                    "TYPE_ERROR",
                    format!("一元 - 需要 number，实际为 {actual}"),
                    source,
                    filename,
                    expression.span,
                    vec![],
                    Some("请使用数值变量或数值字面量".into()),
                ));
            }
        }
        ExpressionKind::Binary {
            operator,
            left,
            right,
        } if matches!(operator.as_str(), "+" | "-" | "*" | "/" | "%") => {
            for actual in [type_of(left), type_of(right)].into_iter().flatten() {
                if actual != "number" {
                    diagnostics.push(diag(
                        "CF2101",
                        "TYPE_ERROR",
                        format!("运算符 {operator} 需要 number，实际为 {actual}"),
                        source,
                        filename,
                        expression.span,
                        vec![],
                        Some("请转换为数值，或修正变量声明类型".into()),
                    ));
                }
            }
        }
        ExpressionKind::Binary {
            operator,
            left,
            right,
        } if matches!(operator.as_str(), "&&" | "||") => {
            for actual in [type_of(left), type_of(right)].into_iter().flatten() {
                if actual != "boolean" {
                    diagnostics.push(diag(
                        "CF2101",
                        "TYPE_ERROR",
                        format!("运算符 {operator} 需要 boolean，实际为 {actual}"),
                        source,
                        filename,
                        expression.span,
                        vec![],
                        Some("请使用布尔表达式或比较表达式".into()),
                    ));
                }
            }
        }
        ExpressionKind::Ternary {
            condition,
            when_true,
            when_false,
        } => {
            if let Some(actual) = type_of(condition).filter(|actual| actual != "boolean") {
                diagnostics.push(diag(
                    "CF2101",
                    "TYPE_ERROR",
                    format!("三元表达式条件需要 boolean，实际为 {actual}"),
                    source,
                    filename,
                    expression.span,
                    vec![],
                    Some("使用比较表达式作为 condition".into()),
                ));
            }
            if let (Some(left), Some(right)) = (type_of(when_true), type_of(when_false)) {
                if left != right {
                    diagnostics.push(diag(
                        "CF2101",
                        "TYPE_ERROR",
                        format!("三元表达式两个分支类型不一致：{left} 与 {right}"),
                        source,
                        filename,
                        expression.span,
                        vec![],
                        Some("两个分支应返回同一类型".into()),
                    ));
                }
            }
        }
        _ => {}
    }
}

#[allow(clippy::too_many_arguments)]
/// 取路径首个标识符基名：`vars.files[0].name` → `files`；`items[0]` → `items`；`file.size` → `file`。
fn first_segment(path: &str) -> &str {
    path.split(['.', '[']).next().unwrap_or(path)
}

fn validate_reference(
    reference: &str,
    variables: &HashSet<String>,
    locals: &HashSet<String>,
    steps: &HashSet<String>,
    source: &str,
    filename: &str,
    span: Span,
    diagnostics: &mut Vec<Diagnostic>,
) {
    // 取路径首个标识符（`.` 或 `[` 前的基名），使 `vars.files[0].name` / `items[0]` 等索引
    // 引用能按基名校验（需求 6.6）。
    if let Some(variable) = reference.strip_prefix("vars.").map(first_segment) {
        if !variables.contains(variable) {
            diagnostics.push(diag(
                "CF2002",
                "REFERENCE_ERROR",
                format!("变量未声明：vars.{variable}"),
                source,
                filename,
                span,
                vec![],
                Some("请先在 variables 块声明变量".into()),
            ));
        }
        return;
    }
    if let Some(step) = reference.strip_prefix("steps.").map(first_segment) {
        if !steps.contains(step) {
            diagnostics.push(diag(
                "CF2002",
                "REFERENCE_ERROR",
                format!("步骤输出引用不存在：{reference}"),
                source,
                filename,
                span,
                nearest_step(step, steps.iter().map(String::as_str)),
                Some("步骤输出引用格式为 steps.<step_id>.output".into()),
            ));
        }
        return;
    }
    // [YAML-NAMESPACE] `env.<key>` 与 `input.<name>` 是 YAML 前端引入的一等引用命名空间：
    // - YAML 前端在委托表达式子系统前已把 `input.<name>` 规范化为 `vars.<name>`（与 DSL 输入
    //   变量表示一致，保证 DSL/YAML 的 IR 等价），因此这里即便出现 `input.` 也直接放行；
    // - `env.<key>` 引用编译期注入的环境变量，声明侧已由 CF4405 约定为字面量；引用拼写
    //   由运行时按工作流注入环境校验，编译期不再收紧（与 `workflow.` 同理）。
    if reference.starts_with("workflow.")
        || reference.starts_with("env.")
        || reference.starts_with("input.")
    {
        return;
    }
    let local = first_segment(reference);
    if !local.contains('.') && locals.contains(local) {
        return;
    }
    diagnostics.push(diag(
        "CF2002",
        "REFERENCE_ERROR",
        format!("非法引用：{reference}"),
        source,
        filename,
        span,
        vec!["vars.<name>".into(), "steps.<id>.output".into()],
        None,
    ));
}

pub fn action_key(action: &ActionNode) -> String {
    if action.provider == "plugin" {
        format!(
            "plugin:{}:{}",
            action.plugin_id.as_deref().unwrap_or_default(),
            action.function.as_deref().unwrap_or_default()
        )
    } else if let (Some(service), Some(method)) = (&action.service, &action.method) {
        format!("{}:{}.{}", action.provider, service, method)
    } else {
        action.provider.clone()
    }
}

fn collect_control_steps<'a>(nodes: &'a [FlowNode], output: &mut Vec<&'a StepNode>) {
    for node in nodes {
        match node {
            FlowNode::Step(step) => {
                output.push(step.as_ref());
                collect_control_steps(&step.controls, output);
                collect_control_steps(&step.on_error, output);
            }
            FlowNode::StepGroup(value) => {
                for step in &value.steps {
                    output.push(step);
                    collect_control_steps(&step.controls, output);
                    collect_control_steps(&step.on_error, output);
                }
            }
            FlowNode::Condition(value) => {
                collect_control_steps(&value.true_branch, output);
                collect_control_steps(&value.false_branch, output);
            }
            FlowNode::Loop(value) => collect_control_steps(&value.body, output),
            FlowNode::For(value) => collect_control_steps(&value.body, output),
            FlowNode::While(value) => collect_control_steps(&value.body, output),
            FlowNode::Parallel(value) => collect_control_steps(&value.branches, output),
            FlowNode::TryCatch(value) => {
                collect_control_steps(&value.try_nodes, output);
                collect_control_steps(&value.catch_nodes, output);
                collect_control_steps(&value.finally_nodes, output);
            }
            FlowNode::Wait(_) => {}
            FlowNode::Assert(_) => {}
            FlowNode::Validate(_) => {}
            FlowNode::Notify(_) => {}
            FlowNode::Return(_) => {}
            FlowNode::Switch(value) => {
                for case in &value.cases {
                    collect_control_steps(&case.body, output);
                }
                collect_control_steps(&value.default_branch, output);
            }
            FlowNode::Delay(_) => {}
            FlowNode::Break(_) => {}
            FlowNode::Continue(_) => {}
        }
    }
}

fn reference_type<'a>(reference: &str, variables: &HashMap<&'a str, &'a str>) -> Option<&'a str> {
    reference
        .strip_prefix("vars.")
        .and_then(|value| value.split('.').next())
        .and_then(|name| variables.get(name).copied())
}

fn has_cycle(graph: &HashMap<String, Vec<String>>) -> bool {
    fn visit(
        id: &str,
        graph: &HashMap<String, Vec<String>>,
        state: &mut HashMap<String, u8>,
    ) -> bool {
        match state.get(id).copied().unwrap_or(0) {
            1 => return true,
            2 => return false,
            _ => {}
        }
        state.insert(id.into(), 1);
        if graph
            .get(id)
            .into_iter()
            .flatten()
            .any(|dependency| graph.contains_key(dependency) && visit(dependency, graph, state))
        {
            return true;
        }
        state.insert(id.into(), 2);
        false
    }
    let mut state = HashMap::new();
    graph.keys().any(|id| visit(id, graph, &mut state))
}

fn nearest_step<'a>(value: &str, candidates: impl Iterator<Item = &'a str>) -> Vec<String> {
    candidates
        .filter(|candidate| candidate.starts_with(value.chars().next().unwrap_or('_')))
        .take(3)
        .map(str::to_owned)
        .collect()
}

// 语义分析所有分支统一经过此入口，参数对应结构化诊断契约的必填上下文。
#[allow(clippy::too_many_arguments)]
fn diag(
    code: &str,
    category: &str,
    message: impl Into<String>,
    source: &str,
    filename: &str,
    span: Span,
    suggestions: Vec<String>,
    help: Option<String>,
) -> Diagnostic {
    Diagnostic::new(
        code,
        category,
        message,
        source,
        filename,
        span.start,
        span.end,
        suggestions,
        help,
    )
}

// ============================================================================
// [V1.3-RULE] 语义规则插件接口（需求 10.27）：
// 统一语义层支持在不改动编译管线的前提下注册新的检查。内置单体检查保持既有
// 行为与诊断顺序不变；新增检查统一以规则实现注册，在内置检查之后运行
// （新诊断追加在既有诊断之后）。
//
// 已注册内置规则：
// - DuplicateVariableRule（10.3 变量重复声明，CF2003）
// - RetryConfigRule（10.12 retry 配置合法性，CF4423）
// - TimeoutConfigRule（10.12 步骤/运行时 timeout 必须 > 0，CF4424）
// - WaitConfigRule（10.13 wait 审批配置检查，CF4419）
// - MetadataRule（10.19 标签注解检查，CF4425）
//
// 权限/资源声明检查（10.17/10.18）设计说明：Domain AST 当前没有权限/资源
// 声明节点，能力级授权由 Agent 层在执行期统一强制（能力存在性在 CF3001 已
// 校验）；`SecurityIr` 作为 IR 契约字段保留默认值，未来前端引入权限声明
// 语法时通过新增规则接入，无需改动管线。
// ============================================================================

/// 规则执行上下文：只读的 Domain AST、能力目录与源码坐标。
pub struct RuleContext<'a> {
    pub workflow: &'a WorkflowNode,
    pub catalog: &'a dyn CapabilityCatalog,
    pub source: &'a str,
    pub filename: &'a str,
}

/// [10.27] 语义规则插件接口：实现该 trait 并通过 `validate_with_rules` 注册即可
/// 扩展新的统一语义检查（供未来 IDE 侧规则、组织级规范检查复用）。
pub trait SemanticRule: Send {
    /// 规则名（用于诊断溯源与测试定位）。
    fn name(&self) -> &'static str;
    /// 执行检查，把新增诊断追加到 `diagnostics`。
    fn check(&self, ctx: &RuleContext, diagnostics: &mut Vec<Diagnostic>);
}

/// [10.12] 执行引擎支持的重试策略白名单（与 `execution_core::retry_strategy` 对齐）。
const RETRY_STRATEGIES: &[&str] = &["fixed", "exponential"];

/// 收集规则可见的全部步骤：以 `flow`（源码顺序主视图）为事实源，递归进入
/// 控制块与 `step.controls`/`step.on_error`，再并入 handlers。
fn collect_rule_steps<'a>(workflow: &'a WorkflowNode) -> Vec<&'a StepNode> {
    let mut steps = Vec::new();
    collect_control_steps(&workflow.flow, &mut steps);
    for handler in &workflow.handlers {
        collect_control_steps(&handler.nodes, &mut steps);
    }
    steps
}

/// 递归收集全部 wait 节点（与 `collect_control_steps` 同构，覆盖嵌套控制块）。
fn collect_flow_waits<'a>(nodes: &'a [FlowNode], output: &mut Vec<&'a WaitNode>) {
    for node in nodes {
        match node {
            FlowNode::Step(step) => {
                collect_flow_waits(&step.controls, output);
                collect_flow_waits(&step.on_error, output);
            }
            FlowNode::StepGroup(value) => {
                for step in &value.steps {
                    collect_flow_waits(&step.controls, output);
                    collect_flow_waits(&step.on_error, output);
                }
            }
            FlowNode::Condition(value) => {
                collect_flow_waits(&value.true_branch, output);
                collect_flow_waits(&value.false_branch, output);
            }
            FlowNode::Loop(value) => collect_flow_waits(&value.body, output),
            FlowNode::For(value) => collect_flow_waits(&value.body, output),
            FlowNode::While(value) => collect_flow_waits(&value.body, output),
            FlowNode::Parallel(value) => collect_flow_waits(&value.branches, output),
            FlowNode::TryCatch(value) => {
                collect_flow_waits(&value.try_nodes, output);
                collect_flow_waits(&value.catch_nodes, output);
                collect_flow_waits(&value.finally_nodes, output);
            }
            FlowNode::Wait(value) => output.push(value),
            FlowNode::Switch(value) => {
                for case in &value.cases {
                    collect_flow_waits(&case.body, output);
                }
                collect_flow_waits(&value.default_branch, output);
            }
            FlowNode::Assert(_)
            | FlowNode::Validate(_)
            | FlowNode::Notify(_)
            | FlowNode::Return(_)
            | FlowNode::Delay(_)
            | FlowNode::Break(_)
            | FlowNode::Continue(_) => {}
        }
    }
}

/// [10.3] 变量重复声明检查（CF2003）。
pub struct DuplicateVariableRule;
impl SemanticRule for DuplicateVariableRule {
    fn name(&self) -> &'static str {
        "duplicate-variable"
    }
    fn check(&self, ctx: &RuleContext, diagnostics: &mut Vec<Diagnostic>) {
        let mut seen = HashSet::new();
        for variable in &ctx.workflow.variables {
            if !seen.insert(variable.name.as_str()) {
                diagnostics.push(diag(
                    "CF2003",
                    "VAR_ERROR",
                    format!("变量 `{}` 重复声明", variable.name),
                    ctx.source,
                    ctx.filename,
                    variable.span,
                    vec![],
                    Some("每个变量名只能声明一次；需要不同来源时请重命名".into()),
                ));
            }
        }
    }
}

/// [10.12] retry 配置合法性检查（CF4423）：max_attempts 必须为正，
/// strategy 只允许 `fixed` / `exponential`（与执行引擎退避策略白名单一致）。
pub struct RetryConfigRule;
impl SemanticRule for RetryConfigRule {
    fn name(&self) -> &'static str {
        "retry-config"
    }
    fn check(&self, ctx: &RuleContext, diagnostics: &mut Vec<Diagnostic>) {
        let mut check_retry = |retry: &RetryNode| {
            if retry.max_attempts == 0 {
                diagnostics.push(diag(
                    "CF4423",
                    "RETRY_ERROR",
                    "retry max_attempts 必须为正整数",
                    ctx.source,
                    ctx.filename,
                    retry.span,
                    vec!["max_attempts = 2".into()],
                    None,
                ));
            }
            if !RETRY_STRATEGIES.contains(&retry.strategy.as_str()) {
                diagnostics.push(diag(
                    "CF4423",
                    "RETRY_ERROR",
                    format!(
                        "retry 策略 `{}` 非法，仅支持 fixed / exponential",
                        retry.strategy
                    ),
                    ctx.source,
                    ctx.filename,
                    retry.span,
                    RETRY_STRATEGIES
                        .iter()
                        .map(|value| value.to_string())
                        .collect(),
                    None,
                ));
            }
        };
        if let Some(retry) = &ctx.workflow.runtime.retry {
            check_retry(retry);
        }
        for step in collect_rule_steps(ctx.workflow) {
            if let Some(retry) = &step.retry {
                check_retry(retry);
            }
        }
    }
}

/// [10.12] 步骤/运行时 timeout 合法性检查（CF4424）：timeout 必须大于 0。
pub struct TimeoutConfigRule;
impl SemanticRule for TimeoutConfigRule {
    fn name(&self) -> &'static str {
        "timeout-config"
    }
    fn check(&self, ctx: &RuleContext, diagnostics: &mut Vec<Diagnostic>) {
        if let Some(timeout) = &ctx.workflow.runtime.timeout {
            if timeout.milliseconds == 0 {
                diagnostics.push(diag(
                    "CF4424",
                    "TIMEOUT_ERROR",
                    "runtime timeout 必须大于 0",
                    ctx.source,
                    ctx.filename,
                    timeout.span,
                    vec!["timeout = 30s".into()],
                    None,
                ));
            }
        }
        for step in collect_rule_steps(ctx.workflow) {
            if let Some(timeout) = &step.timeout {
                if timeout.milliseconds == 0 {
                    diagnostics.push(diag(
                        "CF4424",
                        "TIMEOUT_ERROR",
                        format!("步骤 {} 的 timeout 必须大于 0", step.id),
                        ctx.source,
                        ctx.filename,
                        timeout.span,
                        vec!["timeout = 60s".into()],
                        None,
                    ));
                }
            }
        }
    }
}

/// [10.13] wait 审批配置检查（CF4419）：wait 节点携带 timeout 时必须大于 0。
///
/// `wait_type` 为审批标签：`approval` 为首等语义（Runtime 统一进入
/// WAITING_APPROVAL 挂起点，等待审批/恢复接口）；其他取值（如 `cleanup`）
/// 作为用户侧标签透传，不产生编译错误，避免破坏既有工作流。
pub struct WaitConfigRule;
impl SemanticRule for WaitConfigRule {
    fn name(&self) -> &'static str {
        "wait-config"
    }
    fn check(&self, ctx: &RuleContext, diagnostics: &mut Vec<Diagnostic>) {
        let mut waits = Vec::new();
        collect_flow_waits(&ctx.workflow.flow, &mut waits);
        for handler in &ctx.workflow.handlers {
            collect_flow_waits(&handler.nodes, &mut waits);
        }
        for wait in waits {
            if let Some(timeout) = &wait.timeout {
                if timeout.milliseconds == 0 {
                    diagnostics.push(diag(
                        "CF4419",
                        "WAIT_ERROR",
                        format!("wait {} 的 timeout 必须大于 0", wait.wait_type),
                        ctx.source,
                        ctx.filename,
                        timeout.span,
                        vec!["timeout = 24h".into()],
                        Some("不带 timeout 的 wait 将无限期挂起，请确认审批语义".into()),
                    ));
                }
            }
        }
    }
}

/// [10.19] 标签注解检查（CF4425）：metadata.tags 不允许空白标签。
pub struct MetadataRule;
impl SemanticRule for MetadataRule {
    fn name(&self) -> &'static str {
        "metadata-tags"
    }
    fn check(&self, ctx: &RuleContext, diagnostics: &mut Vec<Diagnostic>) {
        for tag in &ctx.workflow.metadata.tags {
            if tag.trim().is_empty() {
                diagnostics.push(diag(
                    "CF4425",
                    "METADATA_ERROR",
                    "tags 不能包含空白标签",
                    ctx.source,
                    ctx.filename,
                    ctx.workflow.span,
                    vec!["tags = [sales, weekly]".into()],
                    None,
                ));
            }
        }
    }
}

/// 内置规则注册表：编译管线在单体检查之后按固定顺序运行。
pub fn builtin_rules() -> Vec<Box<dyn SemanticRule>> {
    vec![
        Box::new(DuplicateVariableRule),
        Box::new(RetryConfigRule),
        Box::new(TimeoutConfigRule),
        Box::new(WaitConfigRule),
        Box::new(MetadataRule),
    ]
}

/// 统一语义分析的可扩展入口（需求 10.22/10.27）：先运行内置单体检查，
/// 再运行内置规则注册表与调用方注入的 `extra_rules`（组织级/IDE 侧规则）。
/// `validate` 保持既有签名不变；编译管线（`compile_source_named_for_language`）
/// 调用本入口，保证 IR 生成前强制完成全部统一语义检查（10.29）。
pub fn validate_with_rules(
    workflow: &WorkflowNode,
    catalog: &dyn CapabilityCatalog,
    source: &str,
    filename: &str,
    extra_rules: &[Box<dyn SemanticRule>],
) -> Vec<Diagnostic> {
    let mut diagnostics = validate(workflow, catalog, source, filename);
    let context = RuleContext {
        workflow,
        catalog,
        source,
        filename,
    };
    for rule in builtin_rules() {
        rule.check(&context, &mut diagnostics);
    }
    for rule in extra_rules {
        rule.check(&context, &mut diagnostics);
    }
    diagnostics
}
