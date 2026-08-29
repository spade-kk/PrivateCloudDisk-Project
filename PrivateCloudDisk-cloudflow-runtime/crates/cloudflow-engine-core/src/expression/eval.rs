//! CloudFlow 表达式子系统 —— 求值器与内建函数实现（需求 6.18/6.22/6.25/6.27/6.31）。
//!
//! 内建函数的**实现**唯一收敛于此：名称白名单在 `builtins::BUILTIN_FUNCTIONS`（与语义层、
//! 补全规范一致），可执行逻辑在本模块。生产 Runtime（`src/execution.rs` 的 `call`）委托
//! `call_builtin`，避免多处重复实现；语义层只通过 `builtins::is_builtin_function` 校验名称。
//!
//! 安全（需求 6.27/19.11/19.12）：只允许本文件内白名单纯函数，禁止任意代码执行；
//! 新增函数必须：1) 在本文件实现 → 2) 登记 `builtins::BUILTIN_FUNCTIONS` → 3) 同步
//! `src/semantic.rs` 的返回类型与 `syntax-highlight/generator/config.py`。

use serde_json::{Map, Number, Value};
use std::time::{SystemTime, UNIX_EPOCH};

/// 表达式子系统 API 版本（需求 6.29）：独立于前端语言版本演进。
pub const API_VERSION: &str = "expr.cloudflow.io/v1";

/// 调用白名单内建函数；未注册函数返回错误（安全设计，需求 6.22/6.27）。
///
/// 错误为面向用户的诊断文本，由调用方（如 `RuntimeExecutionError::Variable`）包装。
/// 与旧的 Runtime 内联实现保持同款错误消息（`未知内置函数 {function}`），行为向后兼容。
pub fn call_builtin(function: &str, arguments: &[Value]) -> Result<Value, String> {
    let first = arguments.first().unwrap_or(&Value::Null);
    match function {
        "len" | "size" => Ok(Value::Number(Number::from(
            first
                .as_array()
                .map(Vec::len)
                .or_else(|| first.as_object().map(Map::len))
                .or_else(|| first.as_str().map(str::len))
                .unwrap_or(0) as u64,
        ))),
        "contains" => {
            let needle = arguments.get(1).unwrap_or(&Value::Null);
            Ok(Value::Bool(match first {
                Value::String(value) => needle.as_str().is_some_and(|n| value.contains(n)),
                Value::Array(values) => values.iter().any(|item| item == needle),
                Value::Object(values) => {
                    needle.as_str().is_some_and(|key| values.contains_key(key))
                }
                _ => false,
            }))
        }
        "starts_with" => Ok(Value::Bool(
            first
                .as_str()
                .zip(arguments.get(1).and_then(Value::as_str))
                .is_some_and(|(value, prefix)| value.starts_with(prefix)),
        )),
        "ends_with" => Ok(Value::Bool(
            first
                .as_str()
                .zip(arguments.get(1).and_then(Value::as_str))
                .is_some_and(|(value, suffix)| value.ends_with(suffix)),
        )),
        // [EXPR-NOW] 当前 Unix 秒（需求 6.11 示例函数；与 GitHub Actions `now` 对齐）。
        "now" => Ok(Value::Number(Number::from(now_unix_secs()))),
        // [EXPR-GET] 容器取值：get(array|object, keyOrIndex)。
        "get" => {
            let container = first;
            let key = arguments.get(1).unwrap_or(&Value::Null);
            Ok(match container {
                Value::Array(values) => key
                    .as_u64()
                    .and_then(|index| values.get(index as usize))
                    .cloned()
                    .unwrap_or(Value::Null),
                Value::Object(values) => key
                    .as_str()
                    .and_then(|key| values.get(key))
                    .cloned()
                    .unwrap_or(Value::Null),
                _ => Value::Null,
            })
        }
        "trim" => Ok(Value::String(
            first.as_str().unwrap_or_default().trim().to_owned(),
        )),
        "to_upper" | "to_uppercase" => Ok(Value::String(
            first.as_str().unwrap_or_default().to_uppercase(),
        )),
        "to_lower" | "to_lowercase" => Ok(Value::String(
            first.as_str().unwrap_or_default().to_lowercase(),
        )),
        // [EXPR-RANGE] range(stop) / range(start, stop) / range(start, stop, step)。
        "range" => {
            let start = arguments.first().and_then(Value::as_f64).unwrap_or(0.0) as i64;
            let (stop, step) = match arguments.len() {
                1 => (start, 1),
                3 => (
                    arguments.get(1).and_then(Value::as_f64).unwrap_or(0.0) as i64,
                    arguments.get(2).and_then(Value::as_f64).unwrap_or(1.0) as i64,
                ),
                _ => (
                    arguments.get(1).and_then(Value::as_f64).unwrap_or(0.0) as i64,
                    1,
                ),
            };
            let (start, stop) = if arguments.len() == 1 {
                (0, stop)
            } else {
                (start, stop)
            };
            if step == 0 {
                return Err("range 的 step 不能为 0".into());
            }
            let mut values = Vec::new();
            let mut cursor = start;
            while (step > 0 && cursor < stop) || (step < 0 && cursor > stop) {
                values.push(Value::Number(Number::from(cursor)));
                cursor += step;
            }
            Ok(Value::Array(values))
        }
        "abs" => numeric_unary(first, f64::abs),
        "round" => numeric_unary(first, f64::round),
        "floor" => numeric_unary(first, f64::floor),
        "ceil" => numeric_unary(first, f64::ceil),
        // ── GitHub Actions Expressions 对齐函数（需求 6.11/6.32）──
        // to_json / from_json / format_number / format_date_time，与 GitHub 的
        // toJSON / fromJSON / formatNumber / formatDateTime 同名对齐。
        "to_json" => serde_json::to_string(first)
            .map(Value::String)
            .map_err(|error| format!("toJSON 序列化失败：{error}")),
        "from_json" => {
            let text = match first {
                Value::String(text) => text.clone(),
                other => return Ok(other.clone()),
            };
            serde_json::from_str::<Value>(&text)
                .map_err(|error| format!("fromJSON 解析失败：{error}"))
        }
        "format_number" => {
            let format = arguments.get(1).and_then(Value::as_str).unwrap_or("");
            format_number_value(first, format)
        }
        "format_date_time" => {
            let format = arguments.get(1).and_then(Value::as_str).unwrap_or("");
            let timezone = arguments.get(2).and_then(Value::as_str).unwrap_or("");
            format_date_time_value(first, format, timezone)
        }
        _ => Err(format!("未知内置函数 {function}")),
    }
}

/// 数值一元函数：非数值返回错误。
fn numeric_unary(value: &Value, f: fn(f64) -> f64) -> Result<Value, String> {
    value
        .as_f64()
        .map(|n| {
            Number::from_f64(f(n))
                .map(Value::Number)
                .unwrap_or(Value::Null)
        })
        .ok_or_else(|| "表达式需要 number".into())
}

/// `format_number(number, [format])`：GitHub `formatNumber` 对齐（需求 6.32）。
/// 支持十进制小数位（`"0.00"`）与千分位分隔（含 `,`，如 `"#,##0.00"`）。
fn format_number_value(value: &Value, format: &str) -> Result<Value, String> {
    let number = value
        .as_f64()
        .ok_or_else(|| "formatNumber 需要 number".to_string())?;
    let decimals = format
        .rsplit('.')
        .next()
        .map(|after| after.chars().take_while(|ch| ch.is_ascii_digit()).count())
        .filter(|count| *count > 0)
        .unwrap_or(0);
    let raw = format!("{:.*}", decimals, number);
    let rendered = if format.contains(',') {
        group_thousands(&raw)
    } else {
        raw
    };
    Ok(Value::String(rendered))
}

/// 十进制数字串按千分位插入逗号（保留负号与小数部分）。
fn group_thousands(text: &str) -> String {
    let negative = text.starts_with('-');
    let body = text.strip_prefix('-').unwrap_or(text);
    let (integer, fraction) = match body.find('.') {
        Some(pos) => (&body[..pos], &body[pos..]),
        None => (body, ""),
    };
    let length = integer.len();
    let mut grouped = String::new();
    for (index, ch) in integer.chars().enumerate() {
        if index > 0 && (length - index) % 3 == 0 {
            grouped.push(',');
        }
        grouped.push(ch);
    }
    let mut rendered = String::new();
    if negative {
        rendered.push('-');
    }
    rendered.push_str(&grouped);
    rendered.push_str(fraction);
    rendered
}

/// `format_date_time(value, [format], [timezone])`：GitHub `formatDateTime` 对齐（需求 6.32）。
/// value 支持 Unix 秒/毫秒或 ISO 字符串；format 支持 .NET 风格 token（yyyy MM dd HH mm ss），
/// 缺省输出 RFC3339；timezone 支持 UTC 偏移（`+08:00`）与少量常见 IANA 时区（标准偏移，不含 DST）。
fn format_date_time_value(value: &Value, format: &str, timezone: &str) -> Result<Value, String> {
    let offset_secs = resolve_timezone_offset_secs(timezone).ok_or_else(|| {
        format!("formatDateTime 无法识别时区：{timezone}（支持 +08:00 之类偏移或常见 IANA 时区）")
    })?;
    let offset = chrono::FixedOffset::east_opt(offset_secs)
        .unwrap_or_else(|| chrono::FixedOffset::east_opt(0).unwrap());
    let moment = match value {
        Value::Number(number) => {
            let seconds = number.as_f64().unwrap_or(0.0);
            let seconds = if seconds > 1e12 {
                seconds / 1000.0
            } else {
                seconds
            };
            chrono::DateTime::from_timestamp(seconds as i64, 0)
                .ok_or_else(|| "formatDateTime 时间戳越界".to_string())?
        }
        Value::String(text) => parse_datetime_utc(text)
            .ok_or_else(|| format!("formatDateTime 无法解析日期：{text}"))?,
        _ => return Err("formatDateTime 需要 number 或 string".to_string()),
    };
    let local = moment.with_timezone(&offset);
    if format.is_empty() {
        return Ok(Value::String(local.to_rfc3339()));
    }
    Ok(Value::String(
        local.format(&net_date_tokens_to_chrono(format)).to_string(),
    ))
}

/// 解析 UTC 时间：RFC3339 优先，其次常见 ISO 形态。
fn parse_datetime_utc(text: &str) -> Option<chrono::DateTime<chrono::Utc>> {
    if let Ok(parsed) = chrono::DateTime::parse_from_rfc3339(text) {
        return Some(parsed.with_timezone(&chrono::Utc));
    }
    for spec in [
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%d %H:%M",
        "%Y-%m-%d",
        "%Y/%m/%d %H:%M:%S",
        "%Y/%m/%d",
    ] {
        if let Ok(parsed) = chrono::NaiveDateTime::parse_from_str(text, spec) {
            return Some(chrono::DateTime::from_naive_utc_and_offset(
                parsed,
                chrono::Utc,
            ));
        }
    }
    None
}

/// 时区 → UTC 秒偏移。支持数字偏移（`+HH:MM`/`+HHMM`/`+HH`）与少量常见 IANA 时区（标准偏移，不含 DST）。
fn resolve_timezone_offset_secs(timezone: &str) -> Option<i32> {
    let tz = timezone.trim();
    if tz.is_empty()
        || tz.eq_ignore_ascii_case("utc")
        || tz == "Z"
        || tz.eq_ignore_ascii_case("gmt")
    {
        return Some(0);
    }
    if let Some(sign_char) = tz.chars().next() {
        if sign_char == '+' || sign_char == '-' {
            let body = tz.trim_start_matches(['+', '-']);
            let sign: i32 = if sign_char == '+' { 1 } else { -1 };
            let (hours, minutes) = if let Some((hh, mm)) = body.split_once(':') {
                (hh.parse::<i32>().ok(), mm.parse::<i32>().ok())
            } else if body.len() == 4 {
                (body[..2].parse::<i32>().ok(), body[2..].parse::<i32>().ok())
            } else {
                (body.parse::<i32>().ok(), Some(0))
            };
            if let (Some(hours), Some(minutes)) = (hours, minutes) {
                if hours.abs() <= 14 && minutes < 60 {
                    return Some(sign * (hours * 3600 + minutes * 60));
                }
            }
        }
    }
    const IANA: &[(&str, i32)] = &[
        ("Asia/Shanghai", 8 * 3600),
        ("Asia/Hong_Kong", 8 * 3600),
        ("Asia/Chongqing", 8 * 3600),
        ("Asia/Singapore", 8 * 3600),
        ("Asia/Tokyo", 9 * 3600),
        ("Asia/Seoul", 9 * 3600),
        ("Europe/London", 0),
        ("America/New_York", -5 * 3600),
        ("America/Los_Angeles", -8 * 3600),
    ];
    IANA.iter()
        .find(|(name, _)| tz.eq_ignore_ascii_case(name))
        .map(|(_, secs)| *secs)
}

/// .NET 风格日期 token → chrono 格式化指令（yyyy MM dd HH mm ss）；其余字符按字面保留。
/// 命中 token 时整体跳过该 token，避免逐字符消费产生残留（如 yyyy → 残留 yyy）。
fn net_date_tokens_to_chrono(format: &str) -> String {
    const TOKENS: &[(&str, &str)] = &[
        ("yyyy", "%Y"),
        ("MM", "%m"),
        ("dd", "%d"),
        ("HH", "%H"),
        ("mm", "%M"),
        ("ss", "%S"),
    ];
    let chars: Vec<char> = format.chars().collect();
    let mut out = String::new();
    let mut index = 0usize;
    while index < chars.len() {
        let rest: String = chars[index..].iter().collect();
        match TOKENS.iter().find(|(token, _)| rest.starts_with(token)) {
            Some((token, chrono_directive)) => {
                out.push_str(chrono_directive);
                index += token.len();
            }
            None => {
                out.push(chars[index]);
                index += 1;
            }
        }
    }
    out
}

/// 当前 Unix 秒（UTC）——内建 `now()` 的时钟来源（可注入以做确定性测试）。
fn now_unix_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}
