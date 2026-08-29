//! CloudFlow 表达式子系统 —— 内建常量与函数白名单（需求 6.11/6.22/6.27/6.30）。
//!
//! 所有前端语言共用的表达式都在本子系统中解析；**可调用函数必须在本白名单内**，禁止任意代码
//! 执行（沙箱/安全设计见需求 19.11/19.12）。
//!
//! ⚠️ 白名单是**唯一事实来源**：语义层校验（`src/semantic.rs`）与补全规范
//! （`syntax-highlight/generator/config.py` 的 `BUILTIN_FUNCTIONS`）都必须与本表保持一致。
//! 新增内建能力 → 1) 在 `grammar.pest` / 求值器支持；2) 登记到本表；3) 同步上述消费方。

/// 表达式期常量（数字字面量折叠，需求 6.22）：KB / MB / GB。解析器在 `local_ref` 处查此表。
pub const CONSTANTS: &[(&str, f64)] = &[
    ("KB", 1024.0),
    ("MB", 1024.0 * 1024.0),
    ("GB", 1024.0 * 1024.0 * 1024.0),
];

/// 已实现的运行时白名单函数（与语义校验、执行端求值、补全规范一致）。
/// 实现唯一收敛于本子系统 `eval.rs::call_builtin`（需求 6.22/6.25/6.27）。
pub const BUILTIN_FUNCTIONS: &[&str] = &[
    "size",        // 数组/对象/字符串长度
    "len",         // 与 size 等价
    "contains",    // 容器包含判断
    "starts_with", // 字符串前缀判断
    "ends_with",   // 字符串后缀判断
    "now",         // 当前 Unix 秒（需求 6.11）
    "get",         // 数组/对象按索引/键取值
    "trim",        // 字符串去首尾空白
    "to_upper",    // 字符串转大写
    "to_lower",    // 字符串转小写
    "range",       // range(stop)/range(start, stop, step) → 数字数组
    "abs",         // 绝对值
    "round",       // 四舍五入
    "floor",       // 向下取整
    "ceil",        // 向上取整
    // ── GitHub Actions Expressions 对齐（需求 6.32）：toJSON/fromJSON/formatNumber/formatDateTime
    "to_json",          // 对象/数组/标量 → JSON 字符串（GitHub toJSON）
    "from_json",        // JSON 字符串 → 值（GitHub fromJSON）
    "format_number",    // 数值格式化：小数位 + 千分位（GitHub formatNumber）
    "format_date_time", // 日期/时间格式化：.NET token + 时区（GitHub formatDateTime）
];

/// 管道操作符（`<expr> | filter(...)`，需求 6.13）。不是普通函数，单独分类。
pub const PIPELINE_OPERATORS: &[&str] = &["filter", "map", "reduce"];

/// 查询常量：`constant("MB") == Some(1048576.0)`。
pub fn constant(name: &str) -> Option<f64> {
    CONSTANTS
        .iter()
        .find(|(candidate, _)| *candidate == name)
        .map(|(_, value)| *value)
}

/// 查询白名单内建函数：`is_builtin_function("size") == true`。
///
/// 只有已实现并登记的函数才返回 `true`；未来函数在求值器实现并登记到 `BUILTIN_FUNCTIONS`
/// 之前，一律判定为未注册（不虚报能力）。新增函数按 README 扩展流程登记。
pub fn is_builtin_function(name: &str) -> bool {
    BUILTIN_FUNCTIONS.contains(&name)
}

/// 查询管道操作符（供语义/补全判断 `|` 后关键字是否为合法管段）。
pub fn is_pipeline_operator(name: &str) -> bool {
    PIPELINE_OPERATORS.contains(&name)
}
