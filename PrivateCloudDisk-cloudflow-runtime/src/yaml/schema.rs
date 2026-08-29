//! CloudFlow YAML 前端 —— Workflow Schema 校验层（需求 5.22/5.27/31.x/35.x）。
//!
//! 本层是 YAML 编译管线的**第一步**（需求 31.2）：在 `serde_yaml_ng` 解析产物
//! （`normalize_document` 提升后的统一形态）上做**形状校验**（必填字段 / 字段类型 / 未知字段 /
//! 非法值），一次收集多个错误（31.8），错误信息携带 YAML **字段路径**（`steps[2].retry.count`）
//! 与行号、修复建议（31.7/31.22），错误码统一为 `CFY-SCHEMA-1001..1004`（31.9）。
//!
//! 职责边界（分层约定，需求 3.2/3.5/6.31）：
//! - 本层只做**形状**校验，不构建 AST、不解读表达式；`${{ ... }}` 仍是字符串，交由表达式子系统。
//! - 领域语义（重复声明、依赖、能力存在性、类型推导）由 `convert.rs` / 统一语义层负责。
//! - `convert.rs` 不再重复报形状错误：形状信息收敛为本层唯一事实源，避免重复诊断。
//! - 解析仍由 `serde_yaml_ng` 承担（需求 7.x），本层不是解析器，只是 Schema 校验器。
//!
//! 位置信息：`serde_yaml_ng` 反序列化不保留逐值位置（需求 7.6/7.7 取舍），本层复用
//! `Locator` 的**文档序**扫描近似回填行/列（与 `convert.rs` 同一机制，见 `locator.rs`）。

use crate::diagnostic::Diagnostic;
use crate::yaml::convert::scalar;
use crate::yaml::locator::Locator;
use serde_yaml_ng::Value as YamlValue;

/// 顶层允许字段（含嵌套形态 `workflow:` 下被 `normalize_document` 提升的元数据键）。
const ROOT_KEYS: &[&str] = &[
    "workflow",
    "name",
    "version",
    "description",
    "display_name",
    "trigger",
    "input",
    "inputs",
    "variables",
    "env",
    "runtime",
    "steps",
    "catch",
    "finally",
    "outputs",
    "policies",
];

/// `workflow:` 元数据允许字段。
const WORKFLOW_META_KEYS: &[&str] = &["name", "version", "description", "display_name"];

/// step 允许字段（GitHub-Actions 对齐 + CloudFlow 本地字段，需求 5.9）。
const STEP_KEYS: &[&str] = &[
    "id",
    "name",
    "display_name",
    "action",
    "workflow",
    "input",
    "with",
    "output",
    "when",
    "condition",
    "if",
    "depends",
    "depends_on",
    "retry",
    "timeout",
    "on_error",
    "approval",
    "parallel",
    "switch",
    "foreach",
];

/// action 对象形态（`convert_action` Mapping 分支）允许字段（需求 5.10）。
const ACTION_KEYS: &[&str] = &[
    "provider",
    "service",
    "method",
    "id",
    "function",
    "version",
    "arguments",
    "with",
    "input",
];

/// action `provider` 值白名单（需求 5.10）。
const ACTION_PROVIDERS: &[&str] = &["builtin", "plugin", "api"];

/// retry 允许字段（需求 5.13/31.22）。
const RETRY_KEYS: &[&str] = &[
    "count",
    "max",
    "max_attempts",
    "attempts",
    "strategy",
    "interval",
];

/// retry 退避策略白名单（与 `convert_retry`/DSL `RetryNode.strategy` 对齐）。
const RETRY_STRATEGIES: &[&str] = &["fixed", "linear", "exponential"];

/// timeout 允许字段（需求 5.14）。
const TIMEOUT_KEYS: &[&str] = &["duration", "on_timeout"];

/// on_error 允许字段。
const ON_ERROR_KEYS: &[&str] = &["retry", "fallback"];

/// parallel 对象形态允许字段（需求 5.15）。
const PARALLEL_KEYS: &[&str] = &["tasks", "max_concurrency"];

/// switch 允许字段。
const SWITCH_KEYS: &[&str] = &["expression", "cases", "default"];

/// foreach 允许字段（需求 5.15）。
const FOREACH_KEYS: &[&str] = &["item", "collection", "in", "do"];

/// catch 条目允许字段。
const CATCH_KEYS: &[&str] = &["error", "action", "workflow", "input"];

/// 变量/输入声明允许字段（`parse_decl`）。
const DECL_KEYS: &[&str] = &["type", "required", "default", "expression"];

/// 触发器允许字段（各形态并集，需求 5.6/22.x）。
const TRIGGER_KEYS: &[&str] = &[
    "type",
    "event",
    "cron",
    "schedule",
    "webhook",
    "http",
    "interval",
    "topic",
    "name",
    "expression",
    "timezone",
    "path",
    "method",
    "every",
];

/// `trigger.type` 值白名单。
const TRIGGER_TYPE_VALUES: &[&str] = &[
    "manual", "event", "cron", "schedule", "http", "webhook", "interval",
];

/// runtime 允许字段。
const RUNTIME_KEYS: &[&str] = &["timeout", "max_parallel", "retry"];

/// 顶层入口：对规范化后的 YAML 值树做形状校验，一次性收集全部 `CFY-SCHEMA-*` 诊断。
pub(crate) fn validate_schema(root: &YamlValue, source: &str, filename: &str) -> Vec<Diagnostic> {
    let mut validator = SchemaValidator {
        source,
        filename,
        locator: Locator::new(),
        diagnostics: Vec::new(),
    };
    validator.validate_root(root);
    validator.diagnostics
}

/// 从统一定义生成 JSON Schema（需求 31.10/31.18/31.23：单一事实来源，供前端 IDE / API 使用）。
pub(crate) fn emit_json_schema() -> serde_json::Value {
    let string_list = |values: &[&str]| {
        serde_json::Value::Array(
            values
                .iter()
                .map(|value| serde_json::Value::String((*value).to_string()))
                .collect(),
        )
    };
    serde_json::json!({
        "$schema": "http://json-schema.org/draft-07/schema#",
        "$id": "https://cloudflow.local/schemas/yaml-workflow.schema.json",
        "title": "CloudFlow YAML Workflow (yaml.cloudflow.io/v1)",
        "description": "CloudFlow Workflow 的 YAML 前端（workflow.yaml / flow.yaml）。解析由 serde_yaml_ng 承担，本 Schema 仅供 IDE 校验与补全（需求 31.10-31.11）。",
        "type": "object",
        "additionalProperties": false,
        "properties": {
            "workflow": {
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "version": {},
                    "description": { "type": "string" },
                    "display_name": { "type": "string" }
                },
                "additionalProperties": false
            },
            "name": { "type": "string" },
            "version": {},
            "description": { "type": "string" },
            "display_name": { "type": "string" },
            "trigger": {
                "oneOf": [
                    { "type": "string", "enum": ["manual"] },
                    {
                        "type": "object",
                        "properties": {
                            "type": { "enum": string_list(TRIGGER_TYPE_VALUES) },
                            "event": {},
                            "cron": {},
                            "schedule": {},
                            "webhook": {},
                            "http": {},
                            "interval": {},
                            "topic": { "type": "string" },
                            "name": { "type": "string" },
                            "expression": { "type": "string" },
                            "timezone": { "type": "string" },
                            "path": { "type": "string" },
                            "method": { "type": "string" },
                            "every": { "type": "string" }
                        },
                        "additionalProperties": false
                    }
                ]
            },
            "input": { "type": "object" },
            "inputs": { "type": "object" },
            "variables": { "type": "object" },
            "env": { "type": "object" },
            "runtime": {
                "type": "object",
                "properties": {
                    "timeout": {},
                    "max_parallel": { "type": "number" },
                    "retry": {}
                },
                "additionalProperties": false
            },
            "steps": {
                "type": "array",
                "items": { "type": "object" }
            },
            "catch": { "type": "array", "items": { "type": "object" } },
            "finally": { "type": "array", "items": { "type": "object" } },
            "outputs": { "type": "object" },
            "policies": { "type": "object" }
        },
        "required": ["steps"]
    })
}

struct SchemaValidator<'a> {
    source: &'a str,
    filename: &'a str,
    /// 文档序扫描游标：与 `convert.rs` 同一机制，保证重复键按出现先后命中各自位置。
    locator: Locator,
    diagnostics: Vec<Diagnostic>,
}

impl<'a> SchemaValidator<'a> {
    // ---- 文档序遍历（单遍，按文档顺序推进共享游标）----

    fn locate(&mut self, needle: &str) -> usize {
        self.locator.locate(self.source, needle)
    }

    /// 定位 key（返回 key 文本在源码中的偏移，并推进游标）。
    fn loc_key(&mut self, map: &serde_yaml_ng::Mapping, key: &str) -> usize {
        match map.keys().find(|candidate| scalar(candidate) == key) {
            Some(key_value) => self.loc_value(key_value),
            None => self.locate(key),
        }
    }

    /// 定位 value 内首个标量（镜像 `convert.rs::locate_yaml_value`）。
    fn loc_value(&mut self, value: &YamlValue) -> usize {
        match value {
            YamlValue::String(text) => self.locate(text),
            YamlValue::Number(number) => self.locate(&number.to_string()),
            YamlValue::Bool(v) => self.locate(if *v { "true" } else { "false" }),
            YamlValue::Sequence(items) => {
                items.first().map(|item| self.loc_value(item)).unwrap_or(0)
            }
            YamlValue::Mapping(map) => map
                .keys()
                .next()
                .map(|key| self.loc_value(key))
                .unwrap_or(0),
            _ => 0,
        }
    }

    /// 独立游标定位（用于“必填缺失”等不参与文档序遍历的错误，不扰动共享游标）。
    fn fresh_locate(&self, needle: &str) -> usize {
        Locator::new().locate(self.source, needle)
    }

    // ---- 诊断构造 ----

    // 与 crate::diagnostic::Diagnostic::new 相同的显式参数约定（保持调用侧不遗漏定位）。
    #[allow(clippy::too_many_arguments)]
    fn emit(
        &mut self,
        code: &str,
        category: &str,
        _message_path: &str,
        message: impl Into<String>,
        offset: usize,
        suggestions: Vec<String>,
        help: &str,
    ) {
        let offset = offset.min(self.source.len());
        self.diagnostics.push(Diagnostic::new(
            code,
            category,
            message,
            self.source,
            self.filename,
            offset,
            offset + 1,
            suggestions,
            Some(help.to_string()),
        ));
    }

    /// CFY-SCHEMA-1001 必填字段缺失（偏移指向容器所在行，见 `anchor_offset`）。
    fn error_required(&mut self, path: &str, field: &str, example: &str, offset: usize) {
        self.emit(
            "CFY-SCHEMA-1001",
            "REQUIRED_FIELD",
            path,
            format!("{path} 缺少必填字段 `{field}`"),
            offset,
            vec![example.to_string()],
            "必填字段缺一不可：`steps`、step 的 `id`/`action`；见 docs/CLOUDFLOW_YAML_DESIGN.md",
        );
    }

    /// 容器锚点：映射首个键在源码中的偏移（用独立游标定位，不扰动文档序共享游标）。
    fn anchor_offset(&self, map: &serde_yaml_ng::Mapping) -> usize {
        match map.keys().next() {
            Some(first) => self.fresh_locate(&scalar(first)),
            None => 0,
        }
    }

    /// CFY-SCHEMA-1002 类型不匹配。
    fn error_type(&mut self, path: &str, expected: &str, value: &YamlValue) {
        let offset = self.loc_value(value);
        self.emit(
            "CFY-SCHEMA-1002",
            "TYPE_MISMATCH",
            path,
            format!(
                "{path} 类型错误：期望 {expected}，得到 {}（详见字段路径与行号）",
                yaml_type_name(value)
            ),
            offset,
            vec![format!("{path}: <{}>", expected_to_yaml(expected))],
            "字段类型与参考规范不一致：docs/CLOUDFLOW_YAML_DESIGN.md",
        );
    }

    /// CFY-SCHEMA-1003 未知字段（含 31.22 的“是否想用 …”建议）。
    fn error_unknown(&mut self, path: &str, field: &str, allowed: &[&str], offset: usize) {
        let suggestion = did_you_mean(field, allowed)
            .map(|closest| format!("；是否想使用 `{closest}` 而不是 `{field}`？"))
            .unwrap_or_default();
        self.emit(
            "CFY-SCHEMA-1003",
            "UNKNOWN_FIELD",
            path,
            format!("{path} 存在未知字段 `{field}`{suggestion}"),
            offset,
            vec![format!(
                "允许字段：{}",
                allowed
                    .iter()
                    .map(|key| format!("`{key}`"))
                    .collect::<Vec<_>>()
                    .join("、")
            )],
            "字段拼写或层级不合法；见 docs/CLOUDFLOW_YAML_DESIGN.md",
        );
    }

    /// CFY-SCHEMA-1004 非法值。
    fn error_invalid(&mut self, path: &str, detail: &str, example: &str, offset: usize) {
        self.emit(
            "CFY-SCHEMA-1004",
            "INVALID_VALUE",
            path,
            format!("{path} 值非法：{detail}"),
            offset,
            vec![example.to_string()],
            "非法值与参考规范不一致：docs/CLOUDFLOW_YAML_DESIGN.md",
        );
    }

    // ---- 校验规则 ----

    fn validate_root(&mut self, root: &YamlValue) {
        let Some(map) = root.as_mapping() else {
            let offset = 0;
            self.emit(
                "CFY-SCHEMA-1002",
                "TYPE_MISMATCH",
                "workflow",
                "workflow 文档顶层必须是对象（Mapping，YAML 键值对映射）",
                offset,
                vec![
                    "workflow:\n  name: demo\n  steps:\n    - id: a\n      action: file.get".into(),
                ],
                "顶层结构见 docs/CLOUDFLOW_YAML_DESIGN.md",
            );
            return;
        };

        // 31.3：`steps` 为必填顶层字段（空列表合法）；偏移指向文档首个键所在行。
        if !map.contains_key(ykey("steps")) {
            let steps_anchor = self.anchor_offset(map);
            self.error_required(
                "workflow",
                "steps",
                "steps:\n  - id: a\n    action: file.get",
                steps_anchor,
            );
        }

        for (key, value) in map {
            let key_text = scalar(key);
            let key_path = key_text.clone();
            let key_offset = self.loc_key(map, &key_text);
            match key_text.as_str() {
                "workflow" => self.validate_workflow_meta(value),
                // 嵌套形态被 `normalize_document` 提升的元数据键：允许且无需额外校验
                // （`workflow:` 内部字段在 validate_workflow_meta 中校验）。
                "name" | "version" | "description" | "display_name" => {}
                "trigger" => self.validate_trigger(value, &key_path),
                "input" | "inputs" => self.validate_decls(value, &key_path),
                "variables" => self.validate_decls(value, &key_path),
                "env" => self.require_mapping(value, &key_path, &[]),
                "runtime" => self.validate_runtime(value, &key_path),
                "steps" => self.validate_steps_list(value, &key_path, true),
                "catch" => self.validate_catch_entries(value, &key_path),
                "finally" => self.validate_steps_list(value, &key_path, true),
                "outputs" => self.validate_decls(value, &key_path),
                "policies" => self.require_mapping(value, &key_path, &[]),
                other => self.error_unknown("workflow", other, ROOT_KEYS, key_offset),
            }
        }
    }

    fn validate_workflow_meta(&mut self, value: &YamlValue) {
        let Some(map) = value.as_mapping() else {
            self.emit(
                "CFY-SCHEMA-1002",
                "TYPE_MISMATCH",
                "workflow",
                "workflow 元数据必须是对象（{name, version, description, display_name}）",
                self.fresh_locate("workflow"),
                vec!["workflow: { name: demo, version: 1.0 }".into()],
                "workflow 元数据见 docs/CLOUDFLOW_YAML_DESIGN.md",
            );
            return;
        };
        for (key, val) in map {
            let key_text = scalar(key);
            if !WORKFLOW_META_KEYS.contains(&key_text.as_str()) {
                // 未知元数据键在 normalize 提升后会在根层以同名键出现，由根层统一报（避免重复）。
                continue;
            }
            match key_text.as_str() {
                // name/description/display_name 应为字符串；version 允许字符串或数字。
                "name" | "description" | "display_name" => {
                    if !val.is_string() {
                        self.emit(
                            "CFY-SCHEMA-1002",
                            "TYPE_MISMATCH",
                            &format!("workflow.{key_text}"),
                            format!(
                                "workflow.{key_text} 类型错误：期望 string，得到 {}",
                                yaml_type_name(val)
                            ),
                            self.fresh_locate(&key_text),
                            vec![format!("workflow.{key_text}: ...")],
                            "字段类型见 docs/CLOUDFLOW_YAML_DESIGN.md",
                        );
                    }
                }
                "version" => {}
                _ => {}
            }
        }
    }

    /// 变量/输入/输出声明表：值为标量或 `{type, required, default, expression}`。
    fn validate_decls(&mut self, value: &YamlValue, path: &str) {
        let Some(map) = value.as_mapping() else {
            self.error_type(path, "object（声明映射）", value);
            return;
        };
        for (key, val) in map {
            let decl_path = format!("{path}.{}", scalar(key));
            let Some(decl_map) = val.as_mapping() else {
                continue; // 标量声明（如 `batch_size: 100`）合法。
            };
            for (decl_key, decl_value) in decl_map {
                let decl_key_text = scalar(decl_key);
                let decl_key_offset = self.loc_key(decl_map, &decl_key_text);
                if !DECL_KEYS.contains(&decl_key_text.as_str()) {
                    self.error_unknown(&decl_path, &decl_key_text, DECL_KEYS, decl_key_offset);
                }
                if decl_key_text == "required" && !decl_value.is_bool() {
                    let value_offset = self.loc_value(decl_value);
                    self.emit(
                        "CFY-SCHEMA-1002",
                        "TYPE_MISMATCH",
                        &format!("{decl_path}.required"),
                        format!(
                            "{decl_path}.required 类型错误：期望 boolean，得到 {}",
                            yaml_type_name(decl_value)
                        ),
                        value_offset,
                        vec!["required: true".into()],
                        "声明字段类型见 docs/CLOUDFLOW_YAML_DESIGN.md",
                    );
                }
            }
        }
    }

    fn require_mapping(&mut self, value: &YamlValue, path: &str, allowed: &[&str]) {
        let Some(map) = value.as_mapping() else {
            self.error_type(path, "object", value);
            return;
        };
        if allowed.is_empty() {
            return;
        }
        for (key, _) in map {
            let key_text = scalar(key);
            if !allowed.contains(&key_text.as_str()) {
                let key_offset = self.loc_key(map, &key_text);
                self.error_unknown(path, &key_text, allowed, key_offset);
            }
        }
    }

    fn validate_steps_list(&mut self, value: &YamlValue, path: &str, require_id: bool) {
        let Some(items) = value.as_sequence() else {
            self.error_type(path, "array（steps 列表）", value);
            return;
        };
        for (index, item) in items.iter().enumerate() {
            let item_path = format!("{path}[{index}]");
            self.validate_step(item, &item_path, require_id);
        }
    }

    fn validate_step(&mut self, value: &YamlValue, path: &str, require_id: bool) {
        let Some(map) = value.as_mapping() else {
            self.error_type(path, "object（步骤）", value);
            return;
        };

        // 必填：id（列表条目，需求 31.3）与 action/workflow/approval/控制字段。
        // 偏移指向步骤所在行（首个键），保证缺失字段也能定位到该步骤。
        let step_anchor = self.anchor_offset(map);
        if require_id && !map.contains_key(ykey("id")) {
            self.error_required(path, "id", "id: <step_id>", step_anchor);
        }
        let has_action = map.contains_key(ykey("action")) || map.contains_key(ykey("workflow"));
        let has_approval = map.contains_key(ykey("approval"));
        let has_control = map.contains_key(ykey("parallel"))
            || map.contains_key(ykey("switch"))
            || map.contains_key(ykey("foreach"));
        if !has_action && !has_approval && !has_control {
            self.error_required(
                path,
                "action",
                "action: file.parse\n# 或 workflow: module.call\n# 或 approval: {users: [admin]}",
                step_anchor,
            );
        }

        for (key, val) in map {
            let key_text = scalar(key);
            let key_path = format!("{path}.{}", key_text);
            let key_offset = self.loc_key(map, &key_text);
            if !STEP_KEYS.contains(&key_text.as_str()) {
                self.error_unknown(path, &key_text, STEP_KEYS, key_offset);
                continue;
            }
            match key_text.as_str() {
                "input" | "with" => self.require_mapping(val, &key_path, &[]),
                "action" => self.validate_action(val, &key_path),
                // workflow：子工作流引用（字符串 `module.call`）或对象形态。
                "workflow" => {}
                "retry" => self.validate_retry(val, &key_path),
                "timeout" => self.validate_timeout(val, &key_path),
                "on_error" => self.validate_on_error(val, &key_path),
                "approval" => self.require_mapping(val, &key_path, &[]),
                "depends" | "depends_on" => self.validate_depends(val, &key_path),
                "parallel" => self.validate_parallel(val, &key_path),
                "switch" => self.validate_switch(val, &key_path),
                "foreach" => self.validate_foreach(val, &key_path),
                // 标量字段：id/name/display_name/output/when/condition/if。
                _ => {}
            }
        }
    }

    fn validate_action(&mut self, value: &YamlValue, path: &str) {
        match value {
            YamlValue::String(_) => {} // `service.method` / `provider:service.method` / `plugin:<id>:<fn>@<v>`
            YamlValue::Mapping(map) => {
                for (key, _) in map {
                    let key_text = scalar(key);
                    if !ACTION_KEYS.contains(&key_text.as_str()) {
                        let key_offset = self.loc_key(map, &key_text);
                        self.error_unknown(path, &key_text, ACTION_KEYS, key_offset);
                    }
                }
                if let Some(provider) = map.get(ykey("provider")) {
                    let provider_text = scalar(provider).to_ascii_lowercase();
                    if !ACTION_PROVIDERS.contains(&provider_text.as_str()) {
                        let value_offset = self.loc_value(provider);
                        self.error_invalid(
                            path,
                            &format!(
                                "`provider` 仅支持 {}，得到 `{provider_text}`",
                                ACTION_PROVIDERS.join("/")
                            ),
                            "action:\n  provider: builtin\n  service: file\n  method: get",
                            value_offset,
                        );
                    }
                }
            }
            other => self.error_type(path, "string 或 object（action 引用）", other),
        }
    }

    fn validate_depends(&mut self, value: &YamlValue, path: &str) {
        match value {
            YamlValue::String(_) => {}
            YamlValue::Sequence(items) => {
                for (index, item) in items.iter().enumerate() {
                    if !item.is_string() {
                        self.error_type(&format!("{path}[{index}]"), "string（步骤 ID）", item);
                    }
                }
            }
            other => self.error_type(path, "string 或 array（步骤 ID 列表）", other),
        }
    }

    fn validate_retry(&mut self, value: &YamlValue, path: &str) {
        match value {
            YamlValue::Number(number) => {
                if number.as_i64().unwrap_or(1) < 1 {
                    let value_offset = self.loc_value(value);
                    self.error_invalid(
                        path,
                        "次数必须 ≥ 1（0 或负数不允许）",
                        "retry: 3",
                        value_offset,
                    );
                }
            }
            YamlValue::String(text) => {
                if text.trim().parse::<u64>().is_err() {
                    let value_offset = self.loc_value(value);
                    self.error_invalid(
                        path,
                        &format!("字符串必须是次数数字，得到 `{text}`"),
                        "retry: 3\n# 或 retry:\n#   count: 3\n#   strategy: exponential",
                        value_offset,
                    );
                }
            }
            YamlValue::Mapping(map) => {
                for (key, val) in map {
                    let key_text = scalar(key);
                    let key_path = format!("{path}.{}", key_text);
                    if !RETRY_KEYS.contains(&key_text.as_str()) {
                        let key_offset = self.loc_key(map, &key_text);
                        self.error_unknown(path, &key_text, RETRY_KEYS, key_offset);
                        continue;
                    }
                    if key_text == "strategy" {
                        let strategy = scalar(val).to_ascii_lowercase();
                        if !RETRY_STRATEGIES.contains(&strategy.as_str()) {
                            let value_offset = self.loc_value(val);
                            self.error_invalid(
                                &key_path,
                                &format!(
                                    "`strategy` 仅支持 {}，得到 `{}`",
                                    RETRY_STRATEGIES.join("/"),
                                    strategy
                                ),
                                "strategy: exponential",
                                value_offset,
                            );
                        }
                        continue;
                    }
                    if key_text == "interval" {
                        // interval 是退避间隔时长字符串（如 5s/1m），非次数。
                        let text = scalar(val);
                        if !text.is_empty() && !is_valid_duration(&text) {
                            let value_offset = self.loc_value(val);
                            self.error_invalid(
                                &key_path,
                                &format!("退避间隔时长格式不合法，得到 `{text}`"),
                                "interval: 5s",
                                value_offset,
                            );
                        }
                        continue;
                    }
                    // count/max/max_attempts/attempts：数字且 ≥ 1。
                    match val {
                        YamlValue::Number(number) => {
                            if number.as_i64().unwrap_or(1) < 1 {
                                let value_offset = self.loc_value(val);
                                self.error_invalid(
                                    &key_path,
                                    "次数必须 ≥ 1（0 或负数不允许）",
                                    "count: 3",
                                    value_offset,
                                );
                            }
                        }
                        other => self.error_type(&key_path, "number（重试次数）", other),
                    }
                }
            }
            other => self.error_type(path, "number、string 或 object（retry）", other),
        }
    }

    fn validate_timeout(&mut self, value: &YamlValue, path: &str) {
        match value {
            YamlValue::String(text) => {
                if !is_valid_duration(text) {
                    let value_offset = self.loc_value(value);
                    self.error_invalid(
                        path,
                        &format!("时长格式不合法，得到 `{text}`"),
                        "timeout: 30s\n# 支持 ms / s / m / h / d 单位，如 30s、5m、1h",
                        value_offset,
                    );
                }
            }
            YamlValue::Mapping(map) => {
                for (key, val) in map {
                    let key_text = scalar(key);
                    if !TIMEOUT_KEYS.contains(&key_text.as_str()) {
                        let key_offset = self.loc_key(map, &key_text);
                        self.error_unknown(path, &key_text, TIMEOUT_KEYS, key_offset);
                        continue;
                    }
                    if key_text == "duration" {
                        let text = scalar(val);
                        if !is_valid_duration(&text) {
                            let value_offset = self.loc_value(val);
                            self.error_invalid(
                                &format!("{path}.duration"),
                                &format!("时长格式不合法，得到 `{text}`"),
                                "duration: 30s",
                                value_offset,
                            );
                        }
                    }
                }
            }
            other => self.error_type(path, "string 或 object（{duration, on_timeout}）", other),
        }
    }

    fn validate_on_error(&mut self, value: &YamlValue, path: &str) {
        let Some(map) = value.as_mapping() else {
            self.error_type(path, "object（{retry, fallback}）", value);
            return;
        };
        for (key, val) in map {
            let key_text = scalar(key);
            let key_path = format!("{path}.{}", key_text);
            if !ON_ERROR_KEYS.contains(&key_text.as_str()) {
                let key_offset = self.loc_key(map, &key_text);
                self.error_unknown(path, &key_text, ON_ERROR_KEYS, key_offset);
                continue;
            }
            match key_text.as_str() {
                "retry" => self.validate_retry(val, &key_path),
                "fallback" if !val.is_string() && val.as_mapping().is_none() => {
                    self.error_type(&key_path, "string 或 object（fallback 动作）", val)
                }
                _ => {}
            }
        }
    }

    fn validate_parallel(&mut self, value: &YamlValue, path: &str) {
        match value {
            YamlValue::Sequence(items) => {
                for (index, item) in items.iter().enumerate() {
                    let item_path = format!("{path}[{index}]");
                    self.validate_step(item, &item_path, false);
                }
            }
            YamlValue::Mapping(map) => {
                if !map.contains_key(ykey("tasks")) {
                    let tasks_anchor = self.anchor_offset(map);
                    self.error_required(
                        path,
                        "tasks",
                        "tasks:\n  - id: ocr\n    action: ai.ocr",
                        tasks_anchor,
                    );
                }
                for (key, val) in map {
                    let key_text = scalar(key);
                    let key_path = format!("{path}.{}", key_text);
                    if !PARALLEL_KEYS.contains(&key_text.as_str()) {
                        let key_offset = self.loc_key(map, &key_text);
                        self.error_unknown(path, &key_text, PARALLEL_KEYS, key_offset);
                        continue;
                    }
                    match key_text.as_str() {
                        "tasks" => self.validate_steps_list(val, &key_path, false),
                        "max_concurrency" => {
                            if !val.is_number() {
                                self.error_type(&key_path, "number（最大并发数）", val);
                            }
                        }
                        _ => {}
                    }
                }
            }
            other => self.error_type(path, "array 或 object（{tasks, max_concurrency}）", other),
        }
    }

    fn validate_switch(&mut self, value: &YamlValue, path: &str) {
        let Some(map) = value.as_mapping() else {
            self.error_type(path, "object（{expression, cases, default}）", value);
            return;
        };
        for (key, val) in map {
            let key_text = scalar(key);
            let key_path = format!("{path}.{}", key_text);
            if !SWITCH_KEYS.contains(&key_text.as_str()) {
                let key_offset = self.loc_key(map, &key_text);
                self.error_unknown(path, &key_text, SWITCH_KEYS, key_offset);
                continue;
            }
            match key_text.as_str() {
                "cases" => {
                    let Some(cases) = val.as_mapping() else {
                        self.error_type(&key_path, "object（case → 步骤）", val);
                        continue;
                    };
                    for (case_key, case_value) in cases {
                        let case_path = format!("{key_path}.{}", scalar(case_key));
                        self.validate_step(case_value, &case_path, false);
                    }
                }
                "default" => self.validate_step(val, &key_path, false),
                _ => {}
            }
        }
    }

    fn validate_foreach(&mut self, value: &YamlValue, path: &str) {
        let Some(map) = value.as_mapping() else {
            self.error_type(path, "object（{item, collection, do}）", value);
            return;
        };
        if !map.contains_key(ykey("do")) {
            let do_anchor = self.anchor_offset(map);
            self.error_required(
                path,
                "do",
                "do:\n  action: file.process\n  input: {...}",
                do_anchor,
            );
        }
        for (key, val) in map {
            let key_text = scalar(key);
            let key_path = format!("{path}.{}", key_text);
            if !FOREACH_KEYS.contains(&key_text.as_str()) {
                let key_offset = self.loc_key(map, &key_text);
                self.error_unknown(path, &key_text, FOREACH_KEYS, key_offset);
                continue;
            }
            if key_text == "do" {
                self.validate_step(val, &key_path, false);
            }
            // item/collection/in：表达式字符串（`${{ ... }}`），无需额外形状校验。
        }
    }

    fn validate_catch_entries(&mut self, value: &YamlValue, path: &str) {
        let Some(items) = value.as_sequence() else {
            self.error_type(path, "array（catch 条目列表）", value);
            return;
        };
        for (index, item) in items.iter().enumerate() {
            let item_path = format!("{path}[{index}]");
            let Some(map) = item.as_mapping() else {
                self.error_type(&item_path, "object（catch 条目）", item);
                continue;
            };
            if !map.contains_key(ykey("action")) && !map.contains_key(ykey("workflow")) {
                let catch_anchor = self.anchor_offset(map);
                self.error_required(&item_path, "action", "action: notify.admin", catch_anchor);
            }
            for (key, _) in map {
                let key_text = scalar(key);
                if !CATCH_KEYS.contains(&key_text.as_str()) {
                    let key_offset = self.loc_key(map, &key_text);
                    self.error_unknown(&item_path, &key_text, CATCH_KEYS, key_offset);
                }
            }
        }
    }

    fn validate_trigger(&mut self, value: &YamlValue, path: &str) {
        match value {
            YamlValue::String(text) => {
                if !text.trim().eq_ignore_ascii_case("manual") {
                    let value_offset = self.loc_value(value);
                    self.error_invalid(
                        path,
                        &format!("trigger 字符串只能是 manual，得到 `{text}`"),
                        "trigger: manual",
                        value_offset,
                    );
                }
            }
            YamlValue::Mapping(map) => {
                for (key, val) in map {
                    let key_text = scalar(key);
                    let key_path = format!("{path}.{}", key_text);
                    if !TRIGGER_KEYS.contains(&key_text.as_str()) {
                        let key_offset = self.loc_key(map, &key_text);
                        self.error_unknown(path, &key_text, TRIGGER_KEYS, key_offset);
                        continue;
                    }
                    if key_text == "type" {
                        let type_text = scalar(val).to_ascii_lowercase();
                        if !TRIGGER_TYPE_VALUES.contains(&type_text.as_str()) {
                            let value_offset = self.loc_value(val);
                            self.error_invalid(
                                &key_path,
                                &format!(
                                    "`type` 仅支持 {}，得到 `{type_text}`",
                                    TRIGGER_TYPE_VALUES.join("/")
                                ),
                                "type: schedule",
                                value_offset,
                            );
                        }
                        continue;
                    }
                    // event/cron/schedule/webhook/http/interval：标量或对象形态。
                    if !val.is_string()
                        && val.as_mapping().is_none()
                        && !val.is_number()
                        && !val.is_bool()
                        && !val.is_null()
                    {
                        self.error_type(&key_path, "string 或 object（触发器详情）", val);
                    }
                }
            }
            other => self.error_type(path, "string 或 object（trigger）", other),
        }
    }

    fn validate_runtime(&mut self, value: &YamlValue, path: &str) {
        let Some(map) = value.as_mapping() else {
            self.error_type(path, "object（{timeout, max_parallel, retry}）", value);
            return;
        };
        for (key, val) in map {
            let key_text = scalar(key);
            let key_path = format!("{path}.{}", key_text);
            if !RUNTIME_KEYS.contains(&key_text.as_str()) {
                let key_offset = self.loc_key(map, &key_text);
                self.error_unknown(path, &key_text, RUNTIME_KEYS, key_offset);
                continue;
            }
            match key_text.as_str() {
                "timeout" => self.validate_timeout(val, &key_path),
                "max_parallel" => {
                    if !val.is_number() {
                        self.error_type(&key_path, "number（最大并行度）", val);
                    }
                }
                "retry" => self.validate_retry(val, &key_path),
                _ => {}
            }
        }
    }
}

fn ykey(text: &str) -> YamlValue {
    YamlValue::String(text.into())
}

fn yaml_type_name(value: &YamlValue) -> &'static str {
    match value {
        YamlValue::Null => "null",
        YamlValue::Bool(_) => "boolean",
        YamlValue::Number(_) => "number",
        YamlValue::String(_) => "string",
        YamlValue::Sequence(_) => "array",
        YamlValue::Mapping(_) => "object",
        YamlValue::Tagged(_) => "tagged",
    }
}

fn expected_to_yaml(expected: &str) -> String {
    match expected {
        "object" => "{...}".to_string(),
        "array" => "[...]".to_string(),
        other => other.to_string(),
    }
}

/// 时长格式：`<数字>(ms|s|m|h|d)`（与 `parse_duration_ms` 支持单位一致，但更严格区分非法文本）。
fn is_valid_duration(text: &str) -> bool {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return false;
    }
    let unit_len = if trimmed.ends_with("ms") { 2 } else { 1 };
    if trimmed.len() <= unit_len {
        return false;
    }
    let split = trimmed.len() - unit_len;
    let (number, unit) = trimmed.split_at(split);
    number.chars().all(|c| c.is_ascii_digit()) && matches!(unit, "ms" | "s" | "m" | "h" | "d")
}

/// 近邻匹配（31.22 修复建议，如 `是否想使用 retry 而不是 retry_count？`）。
///
/// 优先级：编辑距离 ≤ 2 的最接近候选 → 输入以候选为**前缀**的最长候选 → 输入**包含**候选的
/// 最长候选。后两者保证 `retry_count → retry`、`dependss → depends` 这类拼写能给出建议。
fn did_you_mean(input: &str, candidates: &[&str]) -> Option<String> {
    let lower = input.to_ascii_lowercase();
    let fuzzy = candidates
        .iter()
        .filter_map(|candidate| {
            let distance = levenshtein(&lower, &candidate.to_ascii_lowercase());
            (distance <= 2).then_some((distance, *candidate))
        })
        .min_by_key(|(distance, _)| *distance)
        .map(|(_, candidate)| candidate.to_string());
    if fuzzy.is_some() {
        return fuzzy;
    }
    // 前缀优先（`retry_count` → `retry`）。
    let prefix = candidates
        .iter()
        .filter(|candidate| lower.starts_with(&candidate.to_ascii_lowercase()))
        .max_by_key(|candidate| candidate.len());
    if let Some(candidate) = prefix {
        return Some((*candidate).to_string());
    }
    // 包含匹配（`maxAttempts` → `max_attempts` 的场景尽量取最长）。
    candidates
        .iter()
        .filter(|candidate| {
            !candidate.to_ascii_lowercase().is_empty()
                && lower.contains(&candidate.to_ascii_lowercase())
        })
        .max_by_key(|candidate| candidate.len())
        .map(|candidate| (*candidate).to_string())
}

fn levenshtein(a: &str, b: &str) -> usize {
    let a_chars: Vec<char> = a.chars().collect();
    let b_chars: Vec<char> = b.chars().collect();
    let mut previous: Vec<usize> = (0..=b_chars.len()).collect();
    let mut current = vec![0usize; b_chars.len() + 1];
    for (i, ca) in a_chars.iter().enumerate() {
        current[0] = i + 1;
        for (j, cb) in b_chars.iter().enumerate() {
            current[j + 1] = if ca == cb {
                previous[j]
            } else {
                1 + previous[j].min(previous[j + 1]).min(current[j])
            };
        }
        std::mem::swap(&mut previous, &mut current);
    }
    previous[b_chars.len()]
}
