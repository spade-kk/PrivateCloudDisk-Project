//! CloudFlow 表达式子系统 —— 运行期求值器（IR 值上下文求值，双执行面唯一实现）。
//!
//! 本模块是 `expr.cloudflow.io/v1` 运行期语义的唯一事实来源：
//! - 统一驱动（`crate::engine::driver`）用它做条件判断、参数计算、输出映射；
//! - 生产执行面与开发调试执行面均经本模块求值，**不得**在各自目录重复实现
//!   `$ref`/`$expr`/`$template`/`$pipeline` 的运行时求值逻辑；
//! - 内建函数实现收敛于 [`super::eval::call_builtin`]（白名单见 `builtins`）。
//!
//! 值形态约定：IR 中的表达式值以 `serde_json::Value` 标记对象表示
//! （`$ref` / `$expr` / `$template` / `$pipeline`），求值上下文为
//! `{"vars": {...}, "steps": {"<id>": {"output": ...}}}`（可含行上下文顶层键）。

use serde_json::{Map, Number, Value};
use std::time::Duration;

/// 运行期表达式求值错误（面向用户的诊断文本；执行面按自身错误码体系包装，
/// 如生产 `CF2101` / 调试面 `CFD-8101`）。
#[derive(Debug, Clone, thiserror::Error)]
#[error("{0}")]
pub struct ExpressionEvalError(pub String);

/// 在值上下文中求值一个 IR 值（递归：数组/对象逐项，标记对象按语义分派）。
///
/// 该函数是纯函数：不访问文件系统、网络或全局状态；`now()` 等时间函数
/// 通过内建函数白名单受控提供。
pub fn evaluate_value(value: &Value, context: &Value) -> Result<Value, ExpressionEvalError> {
    if let Some(reference) = value.get("$ref").and_then(Value::as_str) {
        return lookup(context, reference)
            .cloned()
            .ok_or_else(|| ExpressionEvalError(format!("引用不存在：{reference}")));
    }
    if let Some(expression) = value.get("$expr") {
        if let (Some(condition), Some(when_true), Some(when_false)) = (
            expression.get("condition"),
            expression.get("whenTrue"),
            expression.get("whenFalse"),
        ) {
            return if truthy(&evaluate_value(condition, context)?) {
                evaluate_value(when_true, context)
            } else {
                evaluate_value(when_false, context)
            };
        }
        if let Some(operator) = expression.get("operator").and_then(Value::as_str) {
            if let Some(operand) = expression.get("operand") {
                let operand = evaluate_value(operand, context)?;
                return match operator {
                    "!" => Ok(Value::Bool(!truthy(&operand))),
                    "-" => number(&operand).map(|value| json_number(-value)),
                    _ => Err(ExpressionEvalError(format!("未知一元运算符 {operator}"))),
                };
            }
            let left = evaluate_value(expression.get("left").unwrap_or(&Value::Null), context)?;
            let right = evaluate_value(expression.get("right").unwrap_or(&Value::Null), context)?;
            return binary(operator, left, right);
        }
        if let Some(function) = expression.get("function").and_then(Value::as_str) {
            let arguments = expression
                .get("arguments")
                .and_then(Value::as_array)
                .cloned()
                .unwrap_or_default()
                .iter()
                .map(|value| evaluate_value(value, context))
                .collect::<Result<Vec<_>, _>>()?;
            return call(function, &arguments);
        }
    }
    // [V1.2-INTERPOLATION] 字符串模板：逐段求值并拼接。
    if let Some(segments) = value.get("$template").and_then(Value::as_array) {
        let mut out = String::new();
        for segment in segments {
            match evaluate_value(segment, context)? {
                Value::String(text) => out.push_str(&text),
                other => out.push_str(&other.to_string()),
            }
        }
        return Ok(Value::String(out));
    }
    // [V1.2-PIPELINE] 集合处理管道。
    if let Some(pipeline) = value.get("$pipeline").and_then(Value::as_object) {
        let input = pipeline
            .get("input")
            .map(|value| evaluate_value(value, context))
            .transpose()?
            .unwrap_or(Value::Null);
        let op = pipeline.get("op").cloned().unwrap_or_default();
        return apply_pipeline(input, &op, context);
    }
    match value {
        Value::Array(values) => Ok(Value::Array(
            values
                .iter()
                .map(|value| evaluate_value(value, context))
                .collect::<Result<_, _>>()?,
        )),
        Value::Object(values) => Ok(Value::Object(
            values
                .iter()
                .map(|(key, value)| Ok((key.clone(), evaluate_value(value, context)?)))
                .collect::<Result<_, ExpressionEvalError>>()?,
        )),
        _ => Ok(value.clone()),
    }
}

fn lookup<'a>(context: &'a Value, reference: &str) -> Option<&'a Value> {
    let normalized = if let Some(step) = reference.strip_prefix("steps.") {
        format!("steps.{step}")
    } else if let Some((step, rest)) = reference.split_once('.') {
        // 不可用 `?` 提前返回：当上下文缺少 steps 键时，非 steps 的点分引用
        // （如 vars.name、行字段 size）也必须能继续解析。
        let is_step = context.get("steps").and_then(|s| s.get(step)).is_some();
        if is_step {
            format!("steps.{step}.{rest}")
        } else {
            reference.to_owned()
        }
    } else {
        reference.to_owned()
    };
    // 无点分引用是局部变量（vars 命名空间，含 foreach 迭代变量）：支持 `items[0]` 索引。
    if !normalized.contains('.') {
        let (name, indices) = split_index_tokens(&normalized);
        if let Some(value) = context.get("vars").and_then(|vars| vars.get(name)) {
            return apply_index_tokens(Some(value), &indices);
        }
    }
    deref_path(context, &normalized)
}

/// 按 `.` 与 `[n]` 拆分路径并逐级下钻（需求 6.6 运行时索引求值；`steps.<id>.output[0]`、
/// `vars.list[1].name`）。
fn deref_path<'a>(mut current: &'a Value, path: &str) -> Option<&'a Value> {
    for raw in path.split('.') {
        let (name, indices) = split_index_tokens(raw);
        if !name.is_empty() {
            current = current.get(name)?;
        }
        current = apply_index_tokens(Some(current), &indices)?;
    }
    Some(current)
}

/// 应用一组合法索引 `[n]`；任一越界即返回 None。
fn apply_index_tokens<'a>(value: Option<&'a Value>, indices: &[usize]) -> Option<&'a Value> {
    let mut current = value?;
    for index in indices {
        current = current.as_array()?.get(*index)?;
    }
    Some(current)
}

/// 把段 `list[0]` / `output[2]` 拆为 `(名字, [索引])`；无索引时索引表为空。
fn split_index_tokens(segment: &str) -> (&str, Vec<usize>) {
    let end = segment.find('[').unwrap_or(segment.len());
    let mut indices = Vec::new();
    let mut rest = &segment[end..];
    while let Some(open) = rest.find('[') {
        let tail = &rest[open + 1..];
        match tail.find(']') {
            Some(close) => {
                if let Ok(index) = tail[..close].parse::<usize>() {
                    indices.push(index);
                }
                rest = &tail[close + 1..];
            }
            None => break,
        }
    }
    (&segment[..end], indices)
}

fn binary(operator: &str, left: Value, right: Value) -> Result<Value, ExpressionEvalError> {
    Ok(match operator {
        "==" => Value::Bool(left == right),
        "!=" => Value::Bool(left != right),
        "&&" => Value::Bool(truthy(&left) && truthy(&right)),
        "||" => Value::Bool(truthy(&left) || truthy(&right)),
        ">" => Value::Bool(number(&left)? > number(&right)?),
        ">=" => Value::Bool(number(&left)? >= number(&right)?),
        "<" => Value::Bool(number(&left)? < number(&right)?),
        "<=" => Value::Bool(number(&left)? <= number(&right)?),
        "+" => json_number(number(&left)? + number(&right)?),
        "-" => json_number(number(&left)? - number(&right)?),
        "*" => json_number(number(&left)? * number(&right)?),
        "/" => {
            let divisor = number(&right)?;
            if divisor == 0.0 {
                return Err(ExpressionEvalError("表达式除零".into()));
            }
            json_number(number(&left)? / divisor)
        }
        "%" => {
            let divisor = number(&right)?;
            if divisor == 0.0 {
                return Err(ExpressionEvalError("表达式取模除零".into()));
            }
            json_number(number(&left)? % divisor)
        }
        _ => return Err(ExpressionEvalError(format!("未知运算符 {operator}"))),
    })
}

fn call(function: &str, arguments: &[Value]) -> Result<Value, ExpressionEvalError> {
    // 内建函数实现唯一收敛于表达式子系统（需求 6.22/6.25/6.27）：执行核心不重复实现。
    super::eval::call_builtin(function, arguments).map_err(ExpressionEvalError)
}

/// [V1.2-PIPELINE] 应用一个管道操作。
fn apply_pipeline(input: Value, op: &Value, context: &Value) -> Result<Value, ExpressionEvalError> {
    let elements = input
        .as_array()
        .cloned()
        .ok_or_else(|| ExpressionEvalError("管道输入必须是 array".into()))?;
    match op.get("op").and_then(Value::as_str).unwrap_or("map") {
        "filter" => {
            let predicate = op.get("predicate").unwrap_or(&Value::Null);
            let mut kept = Vec::new();
            for element in elements {
                // 行上下文：把当前元素字段提升为顶层键，使谓词中的裸标识符（如 size）可解析。
                let mut row = context.clone();
                if let Some(fields) = element.as_object() {
                    for (key, value) in fields {
                        if key != "vars" && key != "steps" {
                            row[key.clone()] = value.clone();
                        }
                    }
                }
                if truthy(&evaluate_value(predicate, &row)?) {
                    kept.push(element);
                }
            }
            Ok(Value::Array(kept))
        }
        "map" => {
            let field = op.get("field").and_then(Value::as_str);
            let out = elements
                .into_iter()
                .map(|element| {
                    field
                        .and_then(|field| element.get(field))
                        .cloned()
                        .unwrap_or(Value::Null)
                })
                .collect();
            Ok(Value::Array(out))
        }
        "reduce" => {
            let function = op
                .get("function")
                .and_then(Value::as_str)
                .unwrap_or("count");
            reduce_values(elements, function)
        }
        other => Err(ExpressionEvalError(format!("未知管道操作 {other}"))),
    }
}

/// [V1.2-PIPELINE] 聚合函数：count/sum/avg/min/max/join。
fn reduce_values(elements: Vec<Value>, function: &str) -> Result<Value, ExpressionEvalError> {
    match function {
        "count" => Ok(Value::Number(Number::from(elements.len() as u64))),
        "sum" => {
            let mut total = 0.0;
            for element in &elements {
                total += number(element)?;
            }
            Ok(json_number(total))
        }
        "avg" => {
            if elements.is_empty() {
                return Ok(Value::Null);
            }
            let mut total = 0.0;
            for element in &elements {
                total += number(element)?;
            }
            Ok(json_number(total / elements.len() as f64))
        }
        "min" => {
            let mut it = elements.iter();
            let Some(first) = it.next() else {
                return Ok(Value::Null);
            };
            let mut min = number(first)?;
            for element in it {
                min = min.min(number(element)?);
            }
            Ok(json_number(min))
        }
        "max" => {
            let mut it = elements.iter();
            let Some(first) = it.next() else {
                return Ok(Value::Null);
            };
            let mut max = number(first)?;
            for element in it {
                max = max.max(number(element)?);
            }
            Ok(json_number(max))
        }
        "join" => {
            let parts = elements
                .iter()
                .map(|element| match element {
                    Value::String(value) => value.clone(),
                    other => other.to_string(),
                })
                .collect::<Vec<_>>()
                .join(",");
            Ok(Value::String(parts))
        }
        _ => Err(ExpressionEvalError(format!("未知聚合函数 {function}"))),
    }
}

fn number(value: &Value) -> Result<f64, ExpressionEvalError> {
    value
        .as_f64()
        .ok_or_else(|| ExpressionEvalError("表达式需要 number".into()))
}

fn json_number(value: f64) -> Value {
    // 整数结果保留 integer 数字类型（对齐 GitHub Actions 表达式：整数运算不产生浮点），
    // 非整数/非有限值按浮点处理。
    if value.is_finite() && value.fract() == 0.0 && value.abs() <= 9_007_199_254_740_992.0 {
        Value::Number(Number::from(value as i64))
    } else {
        Number::from_f64(value)
            .map(Value::Number)
            .unwrap_or(Value::Null)
    }
}

/// 真值判定（GitHub Actions 语义：null/0/空串/空容器为假）。
pub fn truthy(value: &Value) -> bool {
    match value {
        Value::Bool(value) => *value,
        Value::Null => false,
        Value::Number(value) => value.as_f64().is_some_and(|value| value != 0.0),
        Value::String(value) => !value.is_empty(),
        Value::Array(value) => !value.is_empty(),
        Value::Object(value) => !value.is_empty(),
    }
}

/// 解析 IR 时长字符串（`500ms` / `30s` / `5m` / `2h` / `1d`）。
pub fn parse_duration(value: Option<&str>) -> Option<Duration> {
    let value = value?;
    let split = value.find(|character: char| !character.is_ascii_digit())?;
    let amount = value[..split].parse::<u64>().ok()?;
    match &value[split..] {
        "ms" => Some(Duration::from_millis(amount)),
        "s" => Some(Duration::from_secs(amount)),
        "m" => Some(Duration::from_secs(amount.saturating_mul(60))),
        "h" => Some(Duration::from_secs(amount.saturating_mul(3600))),
        "d" => Some(Duration::from_secs(amount.saturating_mul(86_400))),
        _ => None,
    }
}

/// 变量类型匹配（与 IR 契约校验 `ir_validate` 的 type 取值对齐；`unknown` 由声明态放行）。
pub fn matches_type(value: &Value, type_name: &str) -> bool {
    match type_name {
        "string" | "file" | "user" | "space" => value.is_string(),
        "number" => value.is_number(),
        "boolean" => value.is_boolean(),
        "array" => value.is_array(),
        "object" => value.is_object(),
        _ => false,
    }
}

/// 变量规范化（双执行面同一 input/local/deferred 语义，需求 2.23 同构）：
/// 第一遍落实 input/deferred（保持 BTreeMap 顺序），第二遍按声明顺序求值 local。
pub fn normalize_variables(
    ir: &crate::ir::WorkflowIrV1,
    supplied: Value,
) -> Result<Value, ExpressionEvalError> {
    let supplied = supplied.as_object().cloned().unwrap_or_default();
    let mut result = Map::new();
    // 第一遍：先落实 input/deferred 变量（保持 BTreeMap 顺序），
    // 使 local 变量初始值表达式可以引用全部 input 变量。
    for (name, declaration) in &ir.spec.variables {
        match declaration.source.as_str() {
            "input" => {
                if let Some(value) = supplied.get(name) {
                    if !matches_type(value, &declaration.type_name) {
                        return Err(ExpressionEvalError(format!(
                            "变量 {name} 不符合 {} 类型",
                            declaration.type_name
                        )));
                    }
                    result.insert(name.clone(), value.clone());
                } else if let Some(default) = &declaration.default {
                    result.insert(name.clone(), default.clone());
                } else if declaration.required {
                    return Err(ExpressionEvalError(format!("缺少必填变量 {name}")));
                }
            }
            "deferred" => {
                if supplied.contains_key(name) {
                    return Err(ExpressionEvalError(format!(
                        "延迟变量 {name} 只能由受控 Runtime 写入"
                    )));
                }
            }
            "local" => {}
            source => {
                return Err(ExpressionEvalError(format!(
                    "变量 {name} 使用未知来源 {source}"
                )));
            }
        }
    }
    // 第二遍：local 变量按声明顺序求值，可引用 input 变量与其前 local 变量。
    for (name, declaration) in &ir.spec.variables {
        if declaration.source.as_str() != "local" {
            continue;
        }
        if supplied.contains_key(name) {
            return Err(ExpressionEvalError(format!(
                "本地变量 {name} 不允许由启动请求覆盖"
            )));
        }
        let Some(value) = &declaration.value else {
            return Err(ExpressionEvalError(format!("本地变量 {name} 缺少初始值")));
        };
        let context = serde_json::json!({"vars": Value::Object(result.clone()), "steps": {}});
        let evaluated = evaluate_value(value, &context)?;
        if !matches_type(&evaluated, &declaration.type_name) && declaration.type_name != "unknown" {
            return Err(ExpressionEvalError(format!(
                "本地变量 {name} 不符合 {} 类型",
                declaration.type_name
            )));
        }
        result.insert(name.clone(), evaluated);
    }
    Ok(Value::Object(result))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolves_refs_and_expressions_without_string_ambiguity() {
        let context = serde_json::json!({
            "vars": {"threshold": 2},
            "steps": {"collect": {"output": {"count": 3}}}
        });
        let expression = serde_json::json!({"$expr": {
            "left": {"$ref": "steps.collect.output.count"},
            "operator": ">",
            "right": {"$ref": "vars.threshold"}
        }});
        assert_eq!(
            evaluate_value(&expression, &context).unwrap(),
            Value::Bool(true)
        );
    }

    // [V1.2-INTERPOLATION] 字符串模板运行期拼接：${vars.name} 替换为实际值。
    #[test]
    fn evaluates_string_template_with_variable_replacement() {
        let context = serde_json::json!({"vars": {"name": "CloudFlow"}});
        let template = serde_json::json!({
            "$template": ["hello ", {"$ref": "vars.name"}, ", welcome"]
        });
        assert_eq!(
            evaluate_value(&template, &context).unwrap(),
            Value::String("hello CloudFlow, welcome".into())
        );
    }

    // [V1.2-PIPELINE] map/filter/reduce 管道运行期：filter 保留谓词命中的行、
    // map 投影字段、reduce 聚合。
    #[test]
    fn evaluates_pipeline_filter_map_reduce() {
        let context = serde_json::json!({"vars": {}});
        let pipeline = serde_json::json!({
            "$pipeline": {
                "input": {
                    "$pipeline": {
                        "input": {
                            "$pipeline": {
                                "input": {"$ref": "vars.files"},
                                "op": {"op": "filter", "predicate": {"$expr": {
                                    "left": {"$ref": "size"}, "operator": ">", "right": 100
                                }}}
                            }
                        },
                        "op": {"op": "map", "field": "size"}
                    }
                },
                "op": {"op": "reduce", "function": "sum"}
            }
        });
        // vars.files 放在上下文里，但 apply_pipeline 以输入数组为准。
        let mut ctx = context.clone();
        ctx["vars"]["files"] = serde_json::json!([
            {"name": "a", "size": 50},
            {"name": "b", "size": 200},
            {"name": "c", "size": 120}
        ]);
        let result = evaluate_value(&pipeline, &ctx).unwrap();
        // filter(size>100) 保留 200、120；map(size) -> [200,120]；reduce(sum) -> 320。
        assert_eq!(result.as_f64(), Some(320.0));
    }

    // [EXPR-INDEX] 索引访问运行期求值：vars.<list>[i].field 与 steps.<id>.output[i]（需求 6.6）。
    #[test]
    fn evaluates_index_access_references() {
        let context = serde_json::json!({
            "vars": {"files": [{"name": "a"}, {"name": "b"}]},
            "steps": {"parse": {"output": [10, 20, 30]}}
        });
        assert_eq!(
            evaluate_value(&serde_json::json!({"$ref": "vars.files[1].name"}), &context).unwrap(),
            serde_json::json!("b")
        );
        assert_eq!(
            evaluate_value(
                &serde_json::json!({"$ref": "steps.parse.output[2]"}),
                &context
            )
            .unwrap(),
            serde_json::json!(30)
        );
        // 越界索引 → 变量不存在错误。
        assert!(matches!(
            evaluate_value(&serde_json::json!({"$ref": "vars.files[9].name"}), &context),
            Err(ExpressionEvalError(_))
        ));
    }

    // [EXPR-FUNCS] 扩展白名单函数运行期求值（需求 6.11/6.18/6.25）：
    // now/get/trim/to_upper/to_lower/range/abs/round/floor/ceil。
    #[test]
    fn evaluates_extended_builtin_functions() {
        let context = serde_json::json!({});
        let call = |name: &str, args: serde_json::Value| {
            evaluate_value(
                &serde_json::json!({"$expr": {"function": name, "arguments": args}}),
                &context,
            )
        };
        assert_eq!(
            call("to_upper", serde_json::json!(["ab"])).unwrap(),
            serde_json::json!("AB")
        );
        assert_eq!(
            call("trim", serde_json::json!(["  x  "])).unwrap(),
            serde_json::json!("x")
        );
        assert_eq!(
            call("get", serde_json::json!([["a", "b"], 1])).unwrap(),
            serde_json::json!("b")
        );
        assert_eq!(
            call("range", serde_json::json!([3.0])).unwrap(),
            serde_json::json!([0, 1, 2])
        );
        assert_eq!(
            call("abs", serde_json::json!([-4.0])).unwrap(),
            serde_json::json!(4.0)
        );
        let now = call("now", serde_json::json!([])).unwrap();
        assert!(now.as_u64().unwrap() >= 1_600_000_000);
        // 未注册函数仍被拒绝（安全：白名单唯一事实来源）。
        assert!(matches!(
            call("eval", serde_json::json!([])),
            Err(ExpressionEvalError(_))
        ));
    }

    // [V1.2-PIPELINE] 管道输入必须是数组，否则报错。
    #[test]
    fn pipeline_rejects_non_array_input() {
        let context = serde_json::json!({"vars": {"files": "not-an-array"}});
        let pipeline = serde_json::json!({
            "$pipeline": {
                "input": {"$ref": "vars.files"},
                "op": {"op": "map", "field": "name"}
            }
        });
        assert!(evaluate_value(&pipeline, &context).is_err());
    }

    #[test]
    fn normalize_variables_rejects_unknown_source() {
        let ir: crate::ir::WorkflowIrV1 = serde_json::from_value(serde_json::json!({
            "apiVersion": "workflow.cloudflow.io/v1",
            "kind": "Workflow",
            "metadata": {"name": "v"},
            "spec": {
                "trigger": {"type": "manual"},
                "variables": {"a": {"type": "string", "required": false, "source": "weird"}},
                "graph": {"nodes": [], "edges": []}
            },
            "runtime": {},
            "security": {}
        }))
        .unwrap();
        let error = normalize_variables(&ir, serde_json::json!({})).unwrap_err();
        assert!(error.0.contains("未知来源"));
    }
}
