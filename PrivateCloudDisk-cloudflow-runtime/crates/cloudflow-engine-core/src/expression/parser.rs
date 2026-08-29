//! CloudFlow 表达式子系统 —— Pest Parser（需求 6.15/6.16/6.31）。
//!
//! 本文件是**所有前端语言（CloudFlow DSL / YAML / 未来 JSON）表达式解析的唯一实现**：
//! 表达式词法由 `grammar.pest`（pest 第三方库）完成，不手工维护 tokenizer/正则；
//! 解析逻辑从 DSL `parser.rs` 完整抽取（等价移植，只增不减），
//! 输出 Workflow Domain AST 的 `crate::ast::ExpressionNode`（表达式 AST 不另行定义，见 6.17）。
//!
//! 前端只把表达式**字符串**交给 `parse_expression_string` / `parse_value_string`
//! （含 SOI/EOI 完整消费检查），span 以 `base` 对齐到所在源码的绝对坐标。

use crate::ast::*;
use crate::diagnostic::Diagnostic;
use pest::error::Error as PestError;
use pest::iterators::Pair;
use pest::Parser;
use pest_derive::Parser;
use serde_json::Number;
use std::collections::{BTreeMap, HashMap};
use std::sync::{Arc, LazyLock, Mutex};

#[derive(Parser)]
#[grammar = "expression/grammar.pest"]
struct CloudFlowExpressionParser;

fn expr_diagnostic(
    error: PestError<Rule>,
    text: &str,
    source: &str,
    filename: &str,
    base: usize,
) -> Box<Diagnostic> {
    let position = match error.line_col {
        pest::error::LineColLocation::Pos((line, column)) => {
            line_column_offset(source, base + char_offset(text, line, column))
        }
        pest::error::LineColLocation::Span((line, column), (_, _)) => {
            line_column_offset(source, base + char_offset(text, line, column))
        }
    };
    Box::new(Diagnostic::new(
        "CFY-EXPR-102",
        "EXPRESSION_ERROR",
        format!("表达式解析失败：{}", error.variant.message()),
        source,
        filename,
        position,
        position + 1,
        vec![],
        Some(
            "请使用统一表达式语法：vars./steps./input./env. 引用、属性/索引访问、布尔/比较/算术/三元/管道、白名单函数、KB/MB/GB 常量"
                .into(),
        ),
    ))
}

/// 把 pest 报告的 (line, column) 转换为字符串内的字符偏移，再换算字节偏移。
fn char_offset(text: &str, line: usize, column: usize) -> usize {
    let mut current_line = 1usize;
    let mut byte = 0usize;
    for (index, ch) in text.char_indices() {
        if current_line >= line {
            let col = text[index..].chars().count();
            return if col <= column { index } else { byte };
        }
        if ch == '\n' {
            current_line += 1;
            byte = index + 1;
        }
    }
    text.len().min(byte)
}

/// 字节偏移向上对齐到合法 UTF-8 字符边界（char_indices 只报告字符起点）。
fn line_column_offset(source: &str, offset: usize) -> usize {
    let mut candidate = source.len().min(offset);
    while !source.is_char_boundary(candidate) {
        candidate = candidate.saturating_add(1);
    }
    candidate.min(source.len())
}

/// [19.16] 表达式长度防线：防止病病超长表达式消耗堆树/内存（递归降解）。
/// 16K 字符对互联网工作流表达式充表余量；插值模板的长度在每个
/// 插值段内分别计算，不受整体模板影响。
pub const MAX_EXPRESSION_CHARS: usize = 16_384;

fn expression_length_check(
    text: &str,
    source: &str,
    filename: &str,
    base: usize,
) -> Result<(), Box<Diagnostic>> {
    let chars = text.chars().count();
    if chars > MAX_EXPRESSION_CHARS {
        return Err(Box::new(Diagnostic::new(
            "CFY-EXPR-103",
            "EXPRESSION_ERROR",
            format!(
                "表达式长度 {} 字符超过上限 {} 字符",
                chars, MAX_EXPRESSION_CHARS
            ),
            source,
            filename,
            base,
            base + 1,
            vec![],
            Some("拆分长表达式：子结果提取到变量，或拆成多个步骤".into()),
        )));
    }
    Ok(())
}

/// [19.16] 嵌套深度防线：PEG 递归解析每层嵌套都需要调用栈，
/// 极端嵌套（如 5000 层括号）会直接栈溢出（pest 无内置嵌套限制）。
/// 长度防线拦住不了“高密度嵌套”罪形，本函数以 O(n) 预扫（跳过
/// 字符串字面量）约束语法中两类递归结构：
/// - 平衡括号最大深度（函数调用 / 括号 / filter 参数）；
/// - 三元运符 `?` 总数（每个 `?` 在右嵌套时最多增加一层解析递归）。
///
/// 限制 512：512 层 pest 递归堆消耗约 1MB，在默认 2–8MB 线程堆上保留边际。
pub const MAX_EXPRESSION_NESTING: usize = 512;

fn expression_nesting_check(
    text: &str,
    source: &str,
    filename: &str,
    base: usize,
) -> Result<(), Box<Diagnostic>> {
    let mut paren_depth: i32 = 0;
    let mut max_paren_depth = 0usize;
    let mut question_marks = 0usize;
    let mut in_single = false;
    let mut in_double = false;
    let mut escaped = false;
    for ch in text.chars() {
        if escaped {
            escaped = false;
            continue;
        }
        match ch {
            '\\' if in_double => escaped = true,
            '\'' if !in_double => in_single = !in_single,
            '"' if !in_single => in_double = !in_double,
            '(' if !in_single && !in_double => {
                paren_depth += 1;
                max_paren_depth = max_paren_depth.max(paren_depth as usize);
            }
            ')' if !in_single && !in_double => {
                paren_depth = paren_depth.saturating_sub(1);
            }
            '?' if !in_single && !in_double => question_marks += 1,
            _ => {}
        }
    }
    if max_paren_depth > MAX_EXPRESSION_NESTING || question_marks > MAX_EXPRESSION_NESTING {
        return Err(Box::new(Diagnostic::new(
            "CFY-EXPR-104",
            "EXPRESSION_ERROR",
            format!(
                "表达式嵌套过深（括号最大深度 {max_paren_depth} / 三元符 {question_marks}，上限 {MAX_EXPRESSION_NESTING}）"
            ),
            source,
            filename,
            base,
            base + 1,
            vec![],
            Some("拆分嵌套表达式：提取中间结果到变量，或拆成多个步骤".into()),
        )));
    }
    Ok(())
}

/// [19.3] 表达式解析缓存：缓存 **rebase 前** 的相对坐标解析结果，
/// pest 词法分析与节点构造（重量级）只执行一次；轻量级的
/// `rebase_*` 坐标对齐仍每次执行，结果与无缓存时逐节点等价（19.27 纯函数义务）。
/// 全局缓存限容（超容整体清空，粗粒度策略避免引入额外依赖），
/// Mutex 保证线程安全（19.14）。
const EXPR_CACHE_CAP: usize = 1024;
static EXPR_CACHE: LazyLock<Mutex<HashMap<String, Arc<ExpressionNode>>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

const VALUE_CACHE_CAP: usize = 1024;
static VALUE_CACHE: LazyLock<Mutex<HashMap<String, Arc<ValueNode>>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

fn cache_lock<T>(
    cache: &Mutex<HashMap<String, T>>,
) -> std::sync::MutexGuard<'_, HashMap<String, T>> {
    cache
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// [19.8/19.23] 缓存可观测性：(当前缓存项数, 容量上限)。
pub fn expression_cache_stats() -> (usize, usize) {
    (cache_lock(&EXPR_CACHE).len(), EXPR_CACHE_CAP)
}
/// [19.8/19.23] 值上下文解析缓存可观测性。
pub fn value_cache_stats() -> (usize, usize) {
    (cache_lock(&VALUE_CACHE).len(), VALUE_CACHE_CAP)
}
/// 清空解析缓存（测试隔离 / 内存预算调整）。
pub fn clear_parse_caches() {
    cache_lock(&EXPR_CACHE).clear();
    cache_lock(&VALUE_CACHE).clear();
}

/// 解析整段表达式字符串为 Domain AST（前端共用入口，需求 6.21/6.31）。
///
/// `base` 为表达式文本在 `source` 中的字节偏移；返回节点 span 对齐到源码绝对坐标。
pub fn parse_expression_string(
    text: &str,
    source: &str,
    filename: &str,
    base: usize,
) -> Result<ExpressionNode, Box<Diagnostic>> {
    expression_length_check(text, source, filename, base)?;
    expression_nesting_check(text, source, filename, base)?;
    // 注意：get 的锁守卫必须限定在块内释放，miss 分支的写锁才不会自锁
    // （std Mutex 不可重入，守卫跨 match 存活会在二次加锁时死锁）。
    let cached: Option<Arc<ExpressionNode>> = cache_lock(&EXPR_CACHE).get(text).cloned();
    let relative = match cached {
        Some(cached) => cached,
        None => {
            let node = parse_expression_relative(text, source, filename, base)?;
            let cached = Arc::new(node);
            let mut cache = cache_lock(&EXPR_CACHE);
            if cache.len() >= EXPR_CACHE_CAP {
                cache.clear();
            }
            cache.insert(text.to_string(), cached.clone());
            cached
        }
    };
    let mut node = (*relative).clone();
    rebase_expression(&mut node, base, source);
    Ok(node)
}

/// 纯解析：pest 词法 + 节点构造（不含 rebase 坐标对齐）。
fn parse_expression_relative(
    text: &str,
    source: &str,
    filename: &str,
    base: usize,
) -> Result<ExpressionNode, Box<Diagnostic>> {
    let mut pairs = CloudFlowExpressionParser::parse(Rule::expr_entry, text)
        .map_err(|error| expr_diagnostic(error, text, source, filename, base))?;
    let entry = pairs
        .next()
        .ok_or_else(|| expr_error(text, source, filename, base, "空表达式"))?;
    let expression = entry
        .into_inner()
        .next()
        .ok_or_else(|| expr_error(text, source, filename, base, "表达式结构为空"))?;
    Ok(parse_expression(expression, text))
}

/// 解析整段值字符串为 `ValueNode`（值上下文，如 action 参数/trigger 字段）。
pub fn parse_value_string(
    text: &str,
    source: &str,
    filename: &str,
    base: usize,
) -> Result<ValueNode, Box<Diagnostic>> {
    expression_length_check(text, source, filename, base)?;
    expression_nesting_check(text, source, filename, base)?;
    // [19.3] 值上下文同样复用解析缓存（限长防线与表达式共同）。
    // get 锁守卫目前为暂时对象且不跨命令，门卫释放后 miss 分支才可写锁。
    let cached: Option<Arc<ValueNode>> = cache_lock(&VALUE_CACHE).get(text).cloned();
    let relative = match cached {
        Some(cached) => cached,
        None => {
            let node = parse_value_relative(text, source, filename, base)?;
            let cached = Arc::new(node);
            let mut cache = cache_lock(&VALUE_CACHE);
            if cache.len() >= VALUE_CACHE_CAP {
                cache.clear();
            }
            cache.insert(text.to_string(), cached.clone());
            cached
        }
    };
    let mut node = (*relative).clone();
    rebase_value(&mut node, base, source);
    Ok(node)
}

/// 纯解析：值上下文 pest 词法 + 节点构造（不含 rebase 坐标对齐）。
fn parse_value_relative(
    text: &str,
    source: &str,
    filename: &str,
    base: usize,
) -> Result<ValueNode, Box<Diagnostic>> {
    let mut pairs = CloudFlowExpressionParser::parse(Rule::value_entry, text)
        .map_err(|error| expr_diagnostic(error, text, source, filename, base))?;
    let entry = pairs
        .next()
        .ok_or_else(|| expr_error(text, source, filename, base, "空值"))?;
    let value = entry
        .into_inner()
        .next()
        .ok_or_else(|| expr_error(text, source, filename, base, "值结构为空"))?;
    Ok(parse_value(value, text))
}

/// 字符串插值（需求 6.14/6.32，对标 GitHub Actions `${{ }}`）。
///
/// 把含 `${{ ... }}` 的文本拆为模板段，每段为：
/// - `ValueNode::String`：普通文本段；
/// - `ValueNode::VariableRef`：简单引用（如 `vars.x` / `steps.a.output`）；
/// - `ValueNode::Expression`：复杂表达式（如 `steps.a.output.n > 0`）。
///
/// 这是表达式子系统**唯一**的插值实现，供 YAML 前端使用（需求 6.31 只切字符串）。
/// CloudFlow YAML 只定义 `${{ }}` 一种分隔符（需求 6.32），不再接受 `${ }`；
/// DSL 侧 `${...}` 插值由 `parse_value_string`（`parse_string_value`）单独处理。
/// 不含任何插值时返回 `None`（调用方保持纯字符串语义）；含插值但内层表达式解析失败时，
/// 该占位符保留为文本段，不产生吞噬性错误（与 DSL `parse_string_value` 一致）。
pub fn parse_interpolated_value(
    text: &str,
    source: &str,
    filename: &str,
    base: usize,
) -> Option<ValueNode> {
    let bytes = text.as_bytes();
    let mut segments: Vec<ValueNode> = Vec::new();
    let mut plain = String::new();
    let mut cursor = 0usize;
    let mut found = false;
    while cursor < bytes.len() {
        // GitHub-Actions 双大括号 `${{ ... }}`（必须先于 `${` 判断）。
        if bytes[cursor..].starts_with(b"${{") {
            let after = &text[cursor + 3..];
            match after.find("}}") {
                Some(close) => {
                    let inner = after[..close].trim();
                    let expr_base = base.saturating_add(cursor + 3);
                    cursor += 3 + close + 2;
                    if inner.is_empty() {
                        plain.push_str("${{}}");
                        continue;
                    }
                    found = true;
                    if !plain.is_empty() {
                        segments.push(ValueNode::String(std::mem::take(&mut plain)));
                    }
                    segments.push(interpolated_segment(inner, source, filename, expr_base));
                }
                None => {
                    plain.push('$');
                    cursor += 1;
                }
            }
            continue;
        }
        let character = text[cursor..].chars().next().unwrap_or('\0');
        plain.push(character);
        cursor += character.len_utf8();
    }
    if !found {
        return None;
    }
    if !plain.is_empty() {
        segments.push(ValueNode::String(plain));
    }
    Some(ValueNode::Template(segments))
}

/// 单个插值段：交给表达式子系统解析并折叠为 `$ref` / 字面量 / 表达式。
fn interpolated_segment(inner: &str, source: &str, filename: &str, base: usize) -> ValueNode {
    match parse_expression_string(inner, source, filename, base) {
        Ok(expression) => value_from_expression(expression),
        Err(_) => ValueNode::String(format!("${{ {inner} }}")),
    }
}

fn expr_error(
    text: &str,
    source: &str,
    filename: &str,
    base: usize,
    message: &str,
) -> Box<Diagnostic> {
    Box::new(Diagnostic::new(
        "CFY-EXPR-102",
        "EXPRESSION_ERROR",
        message.to_owned(),
        source,
        filename,
        base,
        base + text.len().max(1),
        vec![],
        None,
    ))
}

/// —— 以下为从 DSL `src/parser.rs` 完整抽取的表达式/值构建逻辑（等价移植，只增不减）。
/// 只增不减：可以扩展表达式语法与功能，但不得删减既有能力。
fn parse_expression(pair: Pair<'_, Rule>, text: &str) -> ExpressionNode {
    let span = span_of(&pair, text);
    match pair.as_rule() {
        // [V1.2-PIPELINE] 左折叠管道：<input> | op1 | op2 …。
        Rule::expression => {
            let mut fields = pair.into_inner();
            let first = fields
                .next()
                .map(|p| parse_expression(p, text))
                .unwrap_or_else(|| literal_expression(ValueNode::Boolean(false), span));
            let mut result = first;
            for stage in fields {
                let op = parse_pipeline_op(&stage, text);
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
                .map(|p| parse_expression(p, text))
                .unwrap_or_else(|| literal_expression(ValueNode::Boolean(false), span));
            let mut result = first;
            // ternary 的第二、三个元素不带独立 operator pair，单独构建表达式节点。
            if is_ternary {
                if let (Some(when_true), Some(when_false)) = (fields.next(), fields.next()) {
                    return ExpressionNode {
                        kind: ExpressionKind::Ternary {
                            condition: Box::new(result),
                            when_true: Box::new(parse_expression(when_true, text)),
                            when_false: Box::new(parse_expression(when_false, text)),
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
                        right: Box::new(parse_expression(right, text)),
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
                .map(|p| parse_expression(p, text))
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
            .map(|p| parse_expression(p, text))
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
                        .map(|p| parse_expression(p, text))
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
        // 扩展：通用属性/索引路径折叠为点分/索引引用字符串（需求 6.5/6.6）。
        Rule::runtime_path => ExpressionNode {
            kind: ExpressionKind::Reference(property_path_string(&pair)),
            span,
        },
        Rule::index_access => ExpressionNode {
            kind: ExpressionKind::Reference(index_path_string(&pair)),
            span,
        },
        Rule::reference => ExpressionNode {
            kind: ExpressionKind::Reference(normalize_reference(pair.as_str())),
            span,
        },
        Rule::local_ref => {
            let name = pair.as_str();
            // 扩展：KB/MB/GB 常量在解析期为数字字面量（需求 6.22）。
            if let Some(value) = crate::expression::builtins::constant(name) {
                let number = Number::from_f64(value).unwrap_or_else(|| Number::from(0));
                return literal_expression(ValueNode::Number(number), span);
            }
            ExpressionNode {
                kind: ExpressionKind::Reference(name.to_owned()),
                span,
            }
        }
        Rule::string_value
        | Rule::triple_string
        | Rule::boolean
        | Rule::null
        | Rule::duration
        | Rule::number
        | Rule::array
        | Rule::object => literal_expression(parse_value(pair, text), span),
        _ => {
            let raw = pair.as_str().to_owned();
            pair.into_inner()
                .next()
                .map(|p| parse_expression(p, text))
                .unwrap_or_else(|| literal_expression(ValueNode::Enum(raw), span))
        }
    }
}

/// 扩展：`runtime_path`（如 `file.size`）折叠为点分引用字符串。
fn property_path_string(pair: &Pair<'_, Rule>) -> String {
    pair.clone()
        .into_inner()
        .map(|p| p.as_str().to_owned())
        .collect::<Vec<_>>()
        .join(".")
}

/// 扩展：`index_access`（如 `list[0]`、`vars.a[0]`）折叠为带索引的引用字符串。
fn index_path_string(pair: &Pair<'_, Rule>) -> String {
    let mut fields = pair.clone().into_inner();
    let base = fields
        .next()
        .map(|p| p.as_str().to_owned())
        .unwrap_or_default();
    let mut out = base;
    for child in fields {
        out.push('[');
        out.push_str(child.as_str());
        out.push(']');
    }
    out
}

pub fn value_from_expression(expression: ExpressionNode) -> ValueNode {
    match expression.kind {
        ExpressionKind::Literal(value) => value,
        ExpressionKind::Reference(reference) => ValueNode::VariableRef(reference),
        _ => ValueNode::Expression(Box::new(expression)),
    }
}

fn literal_expression(value: ValueNode, span: Span) -> ExpressionNode {
    ExpressionNode {
        kind: ExpressionKind::Literal(value),
        span,
    }
}

fn parse_pipeline_op(pair: &Pair<'_, Rule>, text: &str) -> PipeOp {
    let Some(op) = pair.clone().into_inner().next() else {
        return PipeOp::Reduce("count".into());
    };
    let span = span_of(&op, text);
    match op.as_rule() {
        Rule::pipeline_filter => PipeOp::Filter(Box::new(
            op.into_inner()
                .next()
                .map(|p| parse_expression(p, text))
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

fn parse_value(pair: Pair<'_, Rule>, text: &str) -> ValueNode {
    match pair.as_rule() {
        // [V1.2-INTERPOLATION] 含 ${...} 的字符串解析为字符串模板。
        Rule::string_value | Rule::triple_string => parse_string_value(pair),
        Rule::boolean => ValueNode::Boolean(pair.as_str() == "true"),
        Rule::null => ValueNode::Null,
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
                        .map(|value| value_from_expression(parse_expression(value, text)))
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
                    value_from_expression(parse_expression(value, text)),
                );
            }
            ValueNode::Object(values)
        }
        Rule::call_value => parse_call(pair, text),
        _ => ValueNode::Enum(pair.as_str().to_owned()),
    }
}

fn parse_call(pair: Pair<'_, Rule>, text: &str) -> ValueNode {
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
                        named.insert(key.as_str().to_owned(), parse_value(value, text));
                    }
                }
            }
            Rule::value_list => positional.extend(
                args.into_inner()
                    .map(|value| value_from_expression(parse_expression(value, text))),
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

/// 引用保留既有命名空间前缀；其余（如 `foo.output`）归一为 `steps.<value>`。
fn normalize_reference(value: &str) -> String {
    if value.starts_with("vars.")
        || value.starts_with("steps.")
        || value.starts_with("workflow.")
        || value.starts_with("input.")
        || value.starts_with("env.")
    {
        value.to_owned()
    } else if value.contains('.') && !value.starts_with("steps.") && !value.contains(".output") {
        // 扩展：输入/IN/运行时对象路径（如 `file.size`、`item.name`）保持原样，
        // 供语义层「运行时裸路径放行」与执行端 lookup 下钻。
        value.to_owned()
    } else {
        format!("steps.{value}")
    }
}

fn span_of(pair: &Pair<'_, Rule>, text: &str) -> Span {
    let span = pair.as_span();
    let (line, column) = crate::diagnostic::line_column(text, span.start());
    let (end_line, end_column) = crate::diagnostic::line_column(text, span.end());
    Span {
        start: span.start(),
        end: span.end(),
        line,
        column,
        end_line,
        end_column,
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

/// 把表达式子树全部 span 从（相对表达式的）偏移重排为源码绝对坐标（多字节安全）。
pub(crate) fn rebase_expression(node: &mut ExpressionNode, base: usize, source: &str) {
    // YAML 定位器可能给出落在多字节字符内部的近似 base；先对齐到字符起点，
    // 避免 `&source[..start]` 切片 panic（中文内容）。
    let start = crate::diagnostic::char_boundary(source.as_bytes(), base + node.span.start);
    let end = crate::diagnostic::char_boundary(source.as_bytes(), base + node.span.end);
    let (line, column) = crate::diagnostic::line_column(source, start);
    let (end_line, end_column) = crate::diagnostic::line_column(source, end);
    node.span = Span {
        start,
        end,
        line,
        column,
        end_line,
        end_column,
    };
    match &mut node.kind {
        ExpressionKind::Literal(_) | ExpressionKind::Reference(_) => {}
        ExpressionKind::Unary { operand, .. } => rebase_expression(operand, base, source),
        ExpressionKind::Binary { left, right, .. } => {
            rebase_expression(left, base, source);
            rebase_expression(right, base, source);
        }
        ExpressionKind::Ternary {
            condition,
            when_true,
            when_false,
        } => {
            rebase_expression(condition, base, source);
            rebase_expression(when_true, base, source);
            rebase_expression(when_false, base, source);
        }
        ExpressionKind::Call { arguments, .. } => {
            for argument in arguments {
                rebase_expression(argument, base, source);
            }
        }
        ExpressionKind::Pipe { input, op } => {
            rebase_expression(input, base, source);
            if let PipeOp::Filter(expression) = op {
                rebase_expression(expression, base, source);
            }
        }
    }
}

/// 递归重排 `ValueNode` 内嵌表达式节点的 span（ValueNode 本身无 span）。
pub(crate) fn rebase_value(value: &mut ValueNode, base: usize, source: &str) {
    match value {
        ValueNode::Expression(expression) => rebase_expression(expression, base, source),
        ValueNode::Template(segments) => {
            for segment in segments {
                rebase_value(segment, base, source);
            }
        }
        ValueNode::Array(items) => {
            for item in items {
                rebase_value(item, base, source);
            }
        }
        ValueNode::Object(map) => {
            for value in map.values_mut() {
                rebase_value(value, base, source);
            }
        }
        ValueNode::Call {
            positional, named, ..
        } => {
            for value in positional {
                rebase_value(value, base, source);
            }
            for value in named.values_mut() {
                rebase_value(value, base, source);
            }
        }
        _ => {}
    }
}
