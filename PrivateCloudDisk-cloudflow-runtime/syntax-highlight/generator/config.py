"""CloudFlow 语法高亮分类配置。

这是统一规范生成器的人工知识库：GRAMMAR.pest 以字面量字符串描述关键字，但字面量本身
不带“属于哪一类”的语义。本模块维护 关键字/操作符/类型 的分类表，脚本读取 grammar 后按此归类，
未命中任何分类的 token 会被登记为“未分类”并告警（对应需求 3.7/3.8 的同步检测）。

新增 DSL 语法时只需：1) 在 GRAMMAR.pest 加规则；2) 若关键字语义属于新类别，在下方补充。
"""

# ---------------------------------------------------------------------------
# 语言元信息
# ---------------------------------------------------------------------------
LANGUAGE = {
    "languageId": "cloudflow",
    "languageName": "CloudFlow",
    "version": "1.2.0",
    "scopeName": "source.cloudflow",
    "fileExtensions": [".flow", ".cloudflow"],
    "lineComment": "#",
    "description": "CloudFlow DSL 统一语法高亮规范（唯一事实来源，由 GRAMMAR.pest + AST.rs 生成）",
    "authors": ["PrivateCloudDisk Team"],
}

# ---------------------------------------------------------------------------
# Token 类别定义（每个类别：scope 用 VS Code TextMate 命名风格；color 为默认主题基色）
# ---------------------------------------------------------------------------
TOKEN_CATEGORIES = {
    "control": {
        "scope": "keyword.control.cloudflow",
        "color": "#C586C0",
        "label": "控制流关键字",
    },
    "declaration": {
        "scope": "keyword.declaration.cloudflow",
        "color": "#569CD6",
        "label": "声明/顶层块关键字",
    },
    "type": {
        "scope": "support.type.cloudflow",
        "color": "#4EC9B0",
        "label": "类型名（string/number 等）",
    },
    "modifier": {
        "scope": "storage.modifier.cloudflow",
        "color": "#9CDCFE",
        "label": "限定词（input/output）",
    },
    "literal": {
        "scope": "constant.language.boolean.cloudflow",
        "color": "#569CD6",
        "label": "布尔字面量 true/false",
    },
    "null_literal": {
        "scope": "constant.language.null.cloudflow",
        "color": "#569CD6",
        "label": "空值字面量 null",
    },
    "operator": {
        "scope": "keyword.operator.cloudflow",
        "color": "#D4D4D4",
        "label": "操作符",
    },
    "annotation": {
        "scope": "meta.annotation.cloudflow",
        "color": "#CE9178",
        "label": "注解（audit/tag）",
    },
    "comment": {
        "scope": "comment.line.number-sign.cloudflow",
        "color": "#6A9955",
        "label": "单行注释",
    },
    "string": {
        "scope": "string.quoted.double.cloudflow",
        "color": "#CE9178",
        "label": "双引号字符串",
    },
    "tripleString": {
        "scope": "string.quoted.triple.cloudflow",
        "color": "#CE9178",
        "label": "三双引号多行字符串",
    },
    "interpolation": {
        "scope": "variable.other.embedded.cloudflow",
        "color": "#FFD700",
        "label": "字符串模板插值 ${...}",
    },
    "number": {
        "scope": "constant.numeric.cloudflow",
        "color": "#B5CEA8",
        "label": "数字字面量",
    },
    "duration": {
        "scope": "constant.numeric.duration.cloudflow",
        "color": "#B5CEA8",
        "label": "时长字面量（5m/30s）",
    },
    "stepReference": {
        "scope": "variable.other.steps.cloudflow",
        "color": "#4FC1FF",
        "label": "steps.<id>.output 引用",
    },
    "variableReference": {
        "scope": "variable.other.vars.cloudflow",
        "color": "#9CDCFE",
        "label": "vars.<name> 引用",
    },
    "systemReference": {
        "scope": "variable.other.system.cloudflow",
        "color": "#9CDCFE",
        "label": "workflow.<name> 引用",
    },
    "environmentReference": {
        "scope": "variable.other.env.cloudflow",
        "color": "#9CDCFE",
        "label": "env.<key> 引用",
    },
    "function": {
        "scope": "entity.name.function.cloudflow",
        "color": "#DCDAA8",
        "label": "函数/能力调用",
    },
    "punctation": {
        "scope": "punctuation.separator.cloudflow",
        "color": "#D4D4D4",
        "label": "标点与分隔符",
    },
}

# ---------------------------------------------------------------------------
# 关键字分类表（grammar 提取的字面量与下表并集后，按类别输出；分类由语义决定）
# ---------------------------------------------------------------------------
KEYWORDS = {
    # 控制流：改变执行顺序
    "control": [
        "if", "else", "foreach", "in", "for", "while", "parallel",
        "try", "catch", "finally", "wait", "assert", "switch", "case",
        "default", "break", "continue", "on_error", "delay", "validate",
        "expect", "notify", "return",
    ],
    # 声明 / 顶层块
    "declaration": [
        "workflow", "metadata", "variables", "trigger", "schedule", "event",
        "http", "manual", "interval", "runtime", "environment", "namespace",
        "tag", "steps", "handlers", "on_failure", "import", "include", "as",
        "audit", "step", "group", "action", "name", "depends_on", "condition",
        "retry", "retry_on", "timeout", "output", "use", "with", "duration",
        "max_concurrency",
        "on_timeout", "max_attempts", "strategy", "backoff", "max_parallel",
        "retry_policy",
    ],
    # 管道操作（filter/map/reduce）：以函数样式出现在 pipeline 管道 `|` 之后
    "function": ["filter", "map", "reduce"],
    # 类型名
    "type": ["string", "number", "boolean", "array", "object", "file", "user", "space"],
    # 字面量
    "literal": ["true", "false"],
    # 空值字面量（表达式文法 `null = { "null" }`，需求 6.3）
    "null_literal": ["null"],
    # 限定词（可变上下文：input 在变量声明中，output 指向输出变量）
    "modifier": ["input", "output"],
}

# 操作符分组（按优先级/语义）
OPERATORS = {
    "arithmetic": ["+", "-", "*", "/", "%"],
    "comparison": ["==", "!=", ">", "<", ">=", "<="],
    "logical": ["&&", "||", "!"],
    "assignment": ["="],
    "pipeline": ["|"],
    "ternary": ["?", ":", "=>"],
}

# 标点（语言结构分隔符，不作为关键字着色）
PUNCTUATION = ["{", "}", "(", ")", "[", "]", ",", ".", ";"]

# 标识符前缀 → 引用类别（scope id）
# 必须与表达式子系统 `crates/cloudflow-engine-core/src/expression/grammar.pest` 的 reference 分解（vars/steps/input/env/
# workflow 前缀）同步：新增引用命名空间时同时更新此处与 COMPLETION_REF_PREFIXES。
REFERENCE_PREFIXES = {
    "vars": "variableReference",
    "steps": "stepReference",
    "input": "variableReference",
    "env": "environmentReference",
    "workflow": "systemReference",
}

# ---------------------------------------------------------------------------
# 正则（用于把 grammar 中的原子规则名映射到 token 类别；供比对，实际匹配取自 grammar）
# ---------------------------------------------------------------------------
NUMERIC_UNIT_KEYWORDS = ["ms", "s", "m", "h", "d"]  # duration 单位


# ---------------------------------------------------------------------------
# 补全规范知识库（供 completion_builder.py 使用，需求 15.x）
# 这些语义分类无法从 GRAMMAR.pest 字面量直接推断（如“哪些函数属于内置白名单”、
# “trigger 有哪些字段提示”），集中维护于此，作为补全规范的唯一人工事实来源。
# 新增 DSL 能力时：1) 在 GRAMMAR.pest / AST.rs 加规则与节点；2) 在下方补充对应分类。
# ---------------------------------------------------------------------------

# 内置表达式函数白名单（唯一事实来源统一在表达式子系统 `crates/cloudflow-engine-core/src/expression/builtins.rs`；
# semantics 通过 builtins::is_builtin_function 校验，execution.rs 的 call() 运行时实现
# 与之保持一致）。filter/map/reduce 为管道操作符，单独列在 PIPELINE_OPERATORS，
# 不作为普通函数补全。
BUILTIN_FUNCTIONS = {
    "size": {
        "signature": "size(value) -> number",
        "parameters": [{"label": "value", "type": "array|object|string", "doc": "要测量长度的集合或字符串"}],
        "returnType": "number",
        "doc": "返回数组长度、对象键数或字符串长度（与 len 等价）。",
    },
    "len": {
        "signature": "len(value) -> number",
        "parameters": [{"label": "value", "type": "array|object|string", "doc": "要测量长度的集合或字符串"}],
        "returnType": "number",
        "doc": "返回数组长度、对象键数或字符串长度（与 size 等价）。",
    },
    "contains": {
        "signature": "contains(container, needle) -> boolean",
        "parameters": [
            {"label": "container", "type": "string|array|object", "doc": "被搜索的字符串/数组/对象"},
            {"label": "needle", "type": "any", "doc": "要查找的目标"},
        ],
        "returnType": "boolean",
        "doc": "字符串是否包含子串、数组是否包含元素、对象是否包含键。",
    },
    "starts_with": {
        "signature": "starts_with(text, prefix) -> boolean",
        "parameters": [
            {"label": "text", "type": "string", "doc": "源字符串"},
            {"label": "prefix", "type": "string", "doc": "前缀"},
        ],
        "returnType": "boolean",
        "doc": "判断字符串是否以指定前缀开头。",
    },
    "ends_with": {
        "signature": "ends_with(text, suffix) -> boolean",
        "parameters": [
            {"label": "text", "type": "string", "doc": "源字符串"},
            {"label": "suffix", "type": "string", "doc": "后缀"},
        ],
        "returnType": "boolean",
        "doc": "判断字符串是否以指定后缀结尾。",
    },
    "now": {
        "signature": "now() -> number",
        "parameters": [],
        "returnType": "number",
        "doc": "返回当前 Unix 时间戳（秒）；与 GitHub Actions 的 now 对齐。",
    },
    "get": {
        "signature": "get(container, keyOrIndex) -> any",
        "parameters": [
            {"label": "container", "type": "array|object", "doc": "数组或对象"},
            {"label": "keyOrIndex", "type": "number|string", "doc": "数组索引或对象键"},
        ],
        "returnType": "any",
        "doc": "按索引/键取容器元素；不存在返回 null。",
    },
    "trim": {
        "signature": "trim(text) -> string",
        "parameters": [{"label": "text", "type": "string", "doc": "源字符串"}],
        "returnType": "string",
        "doc": "去除字符串首尾空白。",
    },
    "to_upper": {
        "signature": "to_upper(text) -> string",
        "parameters": [{"label": "text", "type": "string", "doc": "源字符串"}],
        "returnType": "string",
        "doc": "字符串转大写（成员方法调用 toUpperCase() 的等价函数形式）。",
    },
    "to_lower": {
        "signature": "to_lower(text) -> string",
        "parameters": [{"label": "text", "type": "string", "doc": "源字符串"}],
        "returnType": "string",
        "doc": "字符串转小写（成员方法调用 toLowerCase() 的等价函数形式）。",
    },
    "range": {
        "signature": "range([start], stop, [step]) -> array",
        "parameters": [
            {"label": "start", "type": "number", "doc": "起始（含，缺省 0）"},
            {"label": "stop", "type": "number", "doc": "终止（不含）"},
            {"label": "step", "type": "number", "doc": "步长（缺省 1）"},
        ],
        "returnType": "array",
        "doc": "生成数字数组（range(3) → [0,1,2]）。",
    },
    "abs": {
        "signature": "abs(x) -> number",
        "parameters": [{"label": "x", "type": "number", "doc": "数值"}],
        "returnType": "number",
        "doc": "绝对值。",
    },
    "round": {
        "signature": "round(x) -> number",
        "parameters": [{"label": "x", "type": "number", "doc": "数值"}],
        "returnType": "number",
        "doc": "四舍五入。",
    },
    "floor": {
        "signature": "floor(x) -> number",
        "parameters": [{"label": "x", "type": "number", "doc": "数值"}],
        "returnType": "number",
        "doc": "向下取整。",
    },
    "ceil": {
        "signature": "ceil(x) -> number",
        "parameters": [{"label": "x", "type": "number", "doc": "数值"}],
        "returnType": "number",
        "doc": "向上取整。",
    },
    # ── GitHub Actions Expressions 对齐（需求 6.32）──
    "to_json": {
        "signature": "to_json(value) -> string",
        "parameters": [{"label": "value", "type": "any", "doc": "对象/数组/标量"}],
        "returnType": "string",
        "doc": "将值序列化为 JSON 字符串（GitHub toJSON）。",
    },
    "from_json": {
        "signature": "from_json(text) -> any",
        "parameters": [{"label": "text", "type": "string", "doc": "符合 JSON 语法的字符串"}],
        "returnType": "any",
        "doc": "把 JSON 字符串解析为值（GitHub fromJSON）；非字符串原样返回。",
    },
    "format_number": {
        "signature": "format_number(number, [format]) -> string",
        "parameters": [
            {"label": "number", "type": "number", "doc": "要格式化的数值"},
            {"label": "format", "type": "string", "doc": "可选，如 0.00 或 #,##0.00（含逗号启用千分位）"},
        ],
        "returnType": "string",
        "doc": "按小数位与千分位格式化数值（GitHub formatNumber）。",
    },
    "format_date_time": {
        "signature": "format_date_time(value, [format], [timezone]) -> string",
        "parameters": [
            {"label": "value", "type": "number|string", "doc": "Unix 秒/毫秒或 ISO 日期字符串"},
            {"label": "format", "type": "string", "doc": "可选，.NET token：yyyy MM dd HH mm ss；缺省输出 RFC3339"},
            {"label": "timezone", "type": "string", "doc": "可选，UTC 偏移（+08:00）或常见 IANA 时区（标准偏移，不含 DST）"},
        ],
        "returnType": "string",
        "doc": "把日期/时间值按格式与时区输出（GitHub formatDateTime）。",
    },
}

# 管道操作符（集合流水线，出现在 `|` 之后）
PIPELINE_OPERATORS = {
    "filter": {"signature": "filter(\u27a4 expr)", "doc": "按谓词过滤数组元素。"},
    "map": {"signature": "map(field)", "doc": "投影每个元素的一个字段。"},
    "reduce": {"signature": "reduce(fn)", "doc": "沿数组归约为单值（如 sum）。"},
}

# 可重试异常类型白名单（与 docs/CLOUDFLOW_DESIGN.md V1.2 一节一致）
RETRY_EXCEPTIONS = [
    "TimeoutException", "NetworkException", "PluginException", "StorageException",
    "PermissionException", "TransientException", "ValidationException",
    "WorkerUnavailableException", "GenericException",
]

# 类型名（变量声明/类型标注处补全；type 关键字已存在于 KEYWORDS，此处补充归属）
COMPLETION_TYPES = ["string", "number", "boolean", "array", "object", "file", "user", "space", "credential", "input"]

# 触发器类型 → 补全模板与字段提示（需求 15.20）
TRIGGER_TYPES = {
    "manual": {
        "label": "manual",
        "template": "trigger {\n    manual {}\n}",
        "fields": [],
        "doc": "手动触发：仅通过启动 API 人工触发。",
    },
    "schedule": {
        "label": "schedule",
        "template": "trigger {\n    schedule {\n        cron = \"${1:0 0 * * *}\"\n        timezone = \"${2:Asia/Shanghai}\"\n    }\n}",
        "fields": ["cron", "timezone"],
        "doc": "定时触发：cron 表达式 + 可选时区。",
    },
    "event": {
        "label": "event",
        "template": "trigger {\n    event {\n        name = \"${1:event_name}\"\n    }\n}",
        "fields": ["name"],
        "doc": "事件触发：监听平台事件。",
    },
    "http": {
        "label": "http",
        "template": "trigger {\n    http {\n        path = \"${1:/webhooks/start}\"\n        method = \"${2:POST}\"\n    }\n}",
        "fields": ["path", "method"],
        "doc": "Webhook 触发：注册 HTTP 端点（path + method）。",
    },
    "interval": {
        "label": "interval",
        "template": "trigger {\n    interval = ${1:5m}\n}",
        "fields": [],
        "doc": "周期触发：interval = <duration>，如 5m / 1h。",
    },
    "webhook": {
        "label": "webhook",
        "template": "trigger {\n    http {\n        path = \"${1:/webhook/start}\"\n        method = \"${2:POST}\"\n    }\n}",
        "fields": ["path", "method"],
        "doc": "Webhook 详配别名：等效于 http 块（path + method）。",
    },
}

# 顶层块 / 声明（需求 15.3 keywords 之外的结构补全）
WORKFLOW_BLOCKS = {
    "workflow": {"template": 'workflow "${1:name}" {\n    $0\n}', "doc": "定义 CloudFlow 工作流。"},
    "metadata": {"template": "metadata {\n    display_name = \"${1:名称}\"\n    version = \"${2:1.0.0}\"\n    changelog = \"${3:...}\"\n}", "doc": "元数据声明（版本/changelog 等）。"},
    "variables": {"template": "variables {\n    ${1:name} = ${2:value}\n}", "doc": "变量声明块（支持 input.<type> 与类型标注）。"},
    "variable": {"template": "${1:name}: ${2:type} = ${3:value}", "doc": "变量声明：显式类型 + 初始值。"},
    "trigger": {"template": "trigger {\n    ${1:manual} {}\n}", "doc": "触发器块。"},
    "runtime": {"template": "runtime {\n    timeout = ${1:5m}\n    max_parallel = ${2:2}\n}", "doc": "运行时配置块。"},
    "steps": {"template": "steps {\n    $0\n}", "doc": "步骤/控制流区块。"},
    "handlers": {"template": "handlers {\n    on_failure {\n        notify { channel = \"email\"; to = vars.owner; message = \"失败：${1:...}\" }\n    }\n}", "doc": "全局失败处理块。"},
    "environment": {"template": 'environment {\n    NODE_ENV = "production"\n}', "doc": "环境变量声明（仅供字面量，区别于 variables）。"},
    "namespace": {"template": "namespace ${1:com.example.workflows}", "doc": "工作流命名空间声明。"},
    "audit": {"template": 'audit {\n    level = "high"\n    description = "${1:...}"\n}', "doc": "工作流级审计注解。"},
    "tag": {"template": 'tag "${1:finance}"', "doc": "工作流分类标签。"},
    "import": {"template": 'import "${1:common.flow}" as ${2:common}', "doc": "带别名的模块导入。"},
    "include": {"template": 'include "${1:common.flow}"', "doc": "受限模块复用（CLI 文件模式）。"},
}

# 控制流/结构片段模板（需求 15.6-15.7；placeholders 同时兼容 Monaco 与 VS Code 片段语法）
STRUCTURE_TEMPLATES = {
    "step": {"template": "step ${1:step_id} {\n    action ${2:file.list} {\n        ${3}\n    }\n}", "doc": "定义步骤并调用 Capability Hub 能力。"},
    "if": {"template": "if ${1:vars.flag == true} {\n    $2\n} else {\n    $3\n}", "doc": "条件分支。"},
    "foreach": {"template": "foreach ${1:item} in ${2:vars.items} {\n    $3\n}", "doc": "遍历集合（item 仅在循环体内可见）。"},
    "for": {"template": "for ${1:i} in range(${2:0}, ${3:vars.max}) {\n    $4\n}", "doc": "索引循环；循环体内可用 break/continue。"},
    "while": {"template": "while ${1:vars.count > 0} {\n    $2\n}", "doc": "条件循环（受 maxIterations 保护）。"},
    "parallel": {"template": "parallel {\n    step ${1:a} {}\n    step ${2:b} {}\n}", "doc": "并行分支。"},
    "parallel_max": {"template": "parallel(max_concurrency=${1:3}) {\n    step ${2:a} {}\n    step ${3:b} {}\n}", "doc": "并行分支（带分支级并发上限）。"},
    "try_catch_finally": {"template": "try {\n    ${1}\n} catch ${2:error} {\n    ${3}\n} finally {\n    ${4}\n}", "doc": "异常处理（catch 变量仅在 catch 体内可见）。"},
    "wait": {"template": "wait ${1:approval} {\n    timeout = ${2:24h}\n}", "doc": "等待人工审批或外部信号（可持久化/resume）。"},
    "assert": {"template": "assert { ${1:vars.result == 0} }", "doc": "断言：失败产生 CF2202。"},
    "switch": {"template": "switch ${1:vars.status} {\n    case \"${2:active}\" => {\n        $3\n    }\n    default => {\n        $4\n    }\n}", "doc": "多分支选择。"},
    "delay": {"template": "delay ${1:5s}", "doc": "固定延迟步骤。"},
    "notify": {"template": 'notify {\n    channel = "email"\n    to = vars.${1:user_id}\n    message = "${2:...}"\n}', "doc": "内建通知。"},
    "validate": {"template": "validate { ${1:vars.result > 0} }", "doc": "校验：求值 false 时运行时报错。"},
    "expect": {"template": "expect { ${1:vars.result > 0} }", "doc": "校验别名（同 validate）。"},
    "return": {"template": "return ${1:vars.result}", "doc": "步骤级提前返回。"},
    "break": {"template": "break", "doc": "跳出 for/while 循环。"},
    "continue": {"template": "continue", "doc": "跳过本轮循环。"},
    "step_group": {"template": "step group ${1:group_id} {\n    step ${2:a} {}\n    step ${3:b} {}\n}", "doc": "步骤组（编译期扁平化）。"},
    "retry": {"template": "retry {\n    max_attempts = ${1:3}\n    strategy = \"${2:exponential}\"\n    backoff = \"${3:5s}\"\n}", "doc": "重试策略。"},
    "retry_on": {"template": "retry_on [${1:TimeoutException}]", "doc": "可重试异常白名单。"},
    "depends_on": {"template": "depends_on ${1:step_a} if ${2:vars.flag == true}", "doc": "条件依赖。"},
    "timeout": {"template": "timeout {\n    duration = ${1:30s}\n    on_timeout = \"${2:fail}\"\n}", "doc": "超时块（on_timeout: fail/continue/retry）。"},
}

# 常用代码片段（需求 15.23）
SNIPPETS = {
    "full_workflow": {
        "prefix": "workflow",
        "template": 'workflow "${1:name}" {\n    metadata {\n        display_name = "${2:名称}"\n        version = "${3:1.0.0}"\n    }\n    variables {\n        ${4:name} = ${5:value}\n    }\n    trigger { manual {} }\n    runtime {\n        timeout = 5m\n        max_parallel = 2\n    }\n    steps {\n        step ${6:first} {\n            action ${7:file.list} {\n                ${8}\n            }\n        }\n    }\n}',
        "doc": "完整工作流模板。",
    },
    "add_step": {
        "prefix": "step",
        "template": "step ${1:step_id} {\n    action ${2:file.list} {\n        ${3}\n    }\n}",
        "doc": "添加一个步骤。",
    },
    "create_trigger": {
        "prefix": "trigger",
        "template": "trigger {\n    ${1:manual} {}\n}",
        "doc": "创建触发器块。",
    },
    "create_variable": {
        "prefix": "variable",
        "template": "${1:name}: ${2:type} = ${3:value}",
        "doc": "声明变量。",
    },
    "foreach_loop": {
        "prefix": "foreach",
        "template": "foreach ${1:item} in ${2:vars.items} {\n    ${3}\n}",
        "doc": "遍历集合。",
    },
}

# 括号/引号配对与缩进（需求 15.8-15.10；对应 vscode/language-configuration.json 与 Monaco 语言配置）
PAIR_RULES = {
    "brackets": [["{", "}"], ["(", ")"], ["[", "]"]],
    "autoClosingPairs": [
        {"open": "{", "close": "}"},
        {"open": "(", "close": ")"},
        {"open": "[", "close": "]"},
        {"open": "\"", "close": "\"", "notIn": ["string"]},
        {"open": "\"\"\"", "close": "\"\"\"", "notIn": ["string"]},
    ],
    "surroundingPairs": [["{", "}"], ["(", ")"], ["[", "]"], ["\"", "\""]],
    "indentation": {
        "increaseIndentPattern": r"\{[^}]*$",
        "decreaseIndentPattern": r"^\s*\}",
        "indentSize": 4,
    },
    "comment": {"line": "#"},
}

# 错误码速查（来自 docs/CLOUDFLOW_ERROR_DESIGN.md 的分类表；供 Monaco markers 诊断）
# code -> {kind, title, fix}。仅收录高频/分类表片段，完整错误码见 CLOUDFLOW_ERROR_DESIGN.md。
ERROR_CODES = {
    "CF1101": "非法字符", "CF1102": "字符串未闭合", "CF1201": "缺少 token / 语法结构错误",
    "CF1202": "未知关键字", "CF2001": "语义告警（重复定义等）", "CF2002": "未定义引用/作用域外变量",
    "CF2101": "表达式函数未注册（应使用白名单函数）", "CF2202": "断言失败（运行时）",
    "CF4401": "switch 只允许一个 default 分支", "CF4402": "retry_on 引用了未知异常类型",
    "CF4403": "on_timeout 取值非法", "CF4404": "delay 时长必须大于 0",
    "CF4405": "environment 值必须是字面量", "CF4406": "namespace 不符合小写点分标识符",
    "CF4407": "import 别名重复", "CF4408": "break/continue 只能出现在 for/while 循环体内",
    "CF4409": "validate 表达式必须是 boolean", "CF4410": "for range 端点必须是 number",
    "CF4411": "parallel max_concurrency 必须为正整数", "CF4412": "validate 校验未通过（运行时）",
    "CF4413": "http 触发 method 非法", "CF4414": "interval 触发时长必须大于 0",
    "CF4415": "audit level 非法", "CF4416": "notify 渠道非法",
    "CF4417": "步骤级提前返回（运行期信号）", "CF4418": "step group 冲突 / 空组",
    "CF4420": "use/with 引用了未声明模块别名", "CF4421": "条件依赖必须是布尔表达式",
}

# 变量引用前缀（需求 15.11/15.12：vars. / steps.<id>.output / workflow.）
COMPLETION_REF_PREFIXES = {
    "vars": {"doc": "当前作用域变量引用：vars.<name>.", "nextSegment": "<name>"},
    "steps": {"doc": "步骤输出引用：steps.<step_id>.output.", "nextSegment": "<step_id>.output"},
    "input": {"doc": "工作流输入引用：input.<name>.", "nextSegment": "<name>"},
    "env": {"doc": "环境变量引用：env.<key>.", "nextSegment": "<key>"},
    "workflow": {"doc": "工作流系统信息引用：workflow.<name>.", "nextSegment": "<name>"},
}
