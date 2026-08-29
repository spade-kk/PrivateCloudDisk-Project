//! CloudFlow 编译器内部 AST。
//!
//! AST 与网络 IR 有意分离：AST 保留源码 Span 和值类型，IR 只保留稳定的机器契约。

use serde_json::Number;
use std::collections::BTreeMap;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct Span {
    pub start: usize,
    pub end: usize,
    pub line: usize,
    pub column: usize,
    pub end_line: usize,
    pub end_column: usize,
}

#[derive(Debug, Clone, PartialEq)]
pub struct WorkflowNode {
    pub name: String,
    /// [V1.2-NAMESPACE] 工作流命名空间，用于组织/筛选；缺省为 None。
    pub namespace: Option<String>,
    /// [V1.2-ENVIRONMENT] 环境变量声明，与普通 variables 区分。
    pub environment: Vec<EnvironmentDecl>,
    /// [V1.2-AUDIT] 工作流级审计注解（level + description）。
    pub audit: Option<AuditAnnotation>,
    /// [V1.2-STEP-GROUP] 顶层步骤组定义（step group { ... }），与单步步解耦。
    pub step_groups: Vec<StepGroupNode>,
    /// [V1.2-USE-WITH] 模块别名 → 该模块声明的默认变量（use <alias> 注入为步骤参数来源）。
    pub module_defaults: BTreeMap<String, Vec<(String, ValueNode)>>,
    /// include 仅在 Compiler 文件模式解析；HTTP/内联模式不会读取调用方文件系统。
    pub includes: Vec<IncludeNode>,
    pub metadata: MetadataNode,
    pub variables: Vec<VariableDecl>,
    pub trigger: TriggerNode,
    pub runtime: RuntimeConfig,
    /// 顶层流程节点的源码顺序。`steps`/`controls` 仍保留给现有语义分析与兼容调用方，
    /// 但 Compiler 必须优先使用 flow，避免在收集到两个 Vec 后丢失 wait、try 等控制节点
    /// 与前后 step 的相对位置。
    pub flow: Vec<FlowNode>,
    pub steps: Vec<StepNode>,
    pub controls: Vec<FlowNode>,
    pub handlers: Vec<HandlerNode>,
    /// [V1.2-OUTPUTS] 工作流级输出声明（`workflow` 返回给调用方的输出映射）。
    /// DSL 前端暂不产生；YAML 前端 `outputs:` 映射到此，由 Compiler 写入 `spec.outputs`。
    pub outputs: BTreeMap<String, ValueNode>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub struct IncludeNode {
    pub path: String,
    /// [V1.2-IMPORT-ALIAS] import "x.flow" as alias 的别名。
    pub alias: Option<String>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq, Default)]
pub struct MetadataNode {
    pub display_name: Option<String>,
    pub description: Option<String>,
    pub version: Option<String>,
    pub author: Option<String>,
    pub tags: Vec<String>,
    /// [V1.2-METADATA] 版本变更记录。
    pub changelog: Option<String>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub enum TriggerNode {
    Manual,
    Schedule {
        cron: String,
        timezone: Option<String>,
    },
    Event {
        name: String,
    },
    Http {
        path: String,
        /// [V1.2-WEBHOOK] HTTP 触发允许的请求方法（GET/POST/…），缺省为 POST。
        method: Option<String>,
    },
    /// [V1.2-INTERVAL-TRIGGER] 周期触发：interval = 5m。
    Interval {
        raw: String,
        milliseconds: u64,
    },
}

impl Default for TriggerNode {
    fn default() -> Self {
        Self::Manual
    }
}

#[derive(Debug, Clone, PartialEq, Default)]
pub struct RuntimeConfig {
    pub timeout: Option<TimeoutConfig>,
    pub max_parallel: Option<u32>,
    pub retry: Option<RetryNode>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TimeoutConfig {
    pub raw: String,
    pub milliseconds: u64,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub struct VariableDecl {
    pub name: String,
    pub type_name: String,
    pub required: bool,
    /// `input` 表示调用方可提供，`local` 表示编译期本地变量，`deferred` 表示受控运行时注入。
    pub source: VariableSource,
    pub default: Option<ValueNode>,
    pub span: Span,
}

/// [CLOUDFLOW-VARIABLE-001] 变量来源属于 AST 契约，不能再仅靠 `default` 是否为空猜测，
/// 否则调用方可越过声明覆写本地常量，且 Runtime 无法区分未初始化变量。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VariableSource {
    Input,
    Local,
    Deferred,
}

#[derive(Debug, Clone, PartialEq)]
pub enum ValueNode {
    String(String),
    /// [V1.2-INTERPOLATION] 字符串模板：由文本段与 `$ref` 段交替组成。
    Template(Vec<ValueNode>),
    Number(Number),
    Boolean(bool),
    /// [EXPR-NULL] `null` 字面量（需求 6.3）：显式空值，求值/等于/真值按 JSON null 语义。
    Null,
    Duration(String),
    VariableRef(String),
    Expression(Box<ExpressionNode>),
    Array(Vec<ValueNode>),
    Object(BTreeMap<String, ValueNode>),
    Call {
        function: String,
        positional: Vec<ValueNode>,
        named: BTreeMap<String, ValueNode>,
    },
    Enum(String),
}

#[derive(Debug, Clone, PartialEq)]
pub struct ExpressionNode {
    pub kind: ExpressionKind,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub enum ExpressionKind {
    Literal(ValueNode),
    Reference(String),
    Unary {
        operator: String,
        operand: Box<ExpressionNode>,
    },
    Binary {
        operator: String,
        left: Box<ExpressionNode>,
        right: Box<ExpressionNode>,
    },
    Ternary {
        condition: Box<ExpressionNode>,
        when_true: Box<ExpressionNode>,
        when_false: Box<ExpressionNode>,
    },
    Call {
        function: String,
        arguments: Vec<ExpressionNode>,
    },
    /// [V1.2-PIPELINE] 集合处理管道：input | op。
    Pipe {
        input: Box<ExpressionNode>,
        op: PipeOp,
    },
}

/// [V1.2-PIPELINE] 管道操作符：filter 保留谓词、map 投影字段、reduce 聚合。
#[derive(Debug, Clone, PartialEq)]
pub enum PipeOp {
    Filter(Box<ExpressionNode>),
    Map(Option<String>),
    Reduce(String),
}

#[derive(Debug, Clone, PartialEq, Default)]
pub struct StepNode {
    pub id: String,
    pub name: Option<String>,
    pub action: Option<ActionNode>,
    pub depends_on: Vec<String>,
    pub condition: Option<ExpressionNode>,
    pub retry: Option<RetryNode>,
    /// [V1.2-RETRY_ON] 可重试异常类型白名单列表。
    pub retry_on: Vec<String>,
    pub timeout: Option<TimeoutConfig>,
    /// [V1.2-TIMEOUT-BLOCK] 超时后行为（fail/continue/retry）。
    pub on_timeout: Option<String>,
    /// [V1.2-ON_ERROR] 步骤级错误处理节点（区别于 try/catch）。
    pub on_error: Vec<FlowNode>,
    pub output: Option<String>,
    /// [V1.2-USE-WITH] `use <alias>` / `with <alias>`：step 复用的模块参数别名。
    pub use_alias: Option<String>,
    /// [V1.2-COND-DEPENDS] 条件依赖：depends_on A if <bool 表达式>。
    pub depends_condition: Option<ExpressionNode>,
    pub controls: Vec<FlowNode>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq, Default)]
pub struct ActionNode {
    pub provider: String,
    pub service: Option<String>,
    pub method: Option<String>,
    pub plugin_id: Option<String>,
    pub function: Option<String>,
    pub version: Option<String>,
    pub arguments: BTreeMap<String, ValueNode>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub struct RetryNode {
    pub max_attempts: u32,
    pub strategy: String,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub enum FlowNode {
    Step(Box<StepNode>),
    Condition(ConditionNode),
    Loop(LoopNode),
    /// [V1.2-FOR] 索引/集合循环（区别于 foreach）：for i in range(0, n) 或 for x in <array>。
    For(ForNode),
    While(WhileNode),
    Parallel(ParallelNode),
    TryCatch(TryCatchNode),
    Wait(WaitNode),
    Assert(AssertNode),
    Switch(SwitchNode),
    Delay(DelayNode),
    /// [V1.2-NOTIFY] 内建通知节点。
    Notify(NotifyNode),
    /// [V1.2-RETURN] 步骤级提前返回节点。
    Return(ReturnNode),
    /// [V1.2-BREAK-CONTINUE] 循环控制语句，仅允许在 for/while 循环体内。
    Break(BreakNode),
    Continue(ContinueNode),
    /// [V1.2-VALIDATE] 块级校验节点：对布尔表达式求值，false 时运行时报错。
    Validate(ValidateNode),
    /// [V1.2-STEP-GROUP] 步骤组：把多个步骤组合为一个逻辑单元（编译期扁平化）。
    StepGroup(StepGroupNode),
}

#[derive(Debug, Clone, PartialEq)]
pub struct ConditionNode {
    pub expression: ExpressionNode,
    pub true_branch: Vec<FlowNode>,
    pub false_branch: Vec<FlowNode>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub struct LoopNode {
    pub iterator: String,
    pub collection: ExpressionNode,
    pub body: Vec<FlowNode>,
    pub span: Span,
}

/// [V1.2-FOR] 循环节点。`range(0, n)` 形态使用 range_from/range_to（索引迭代），
/// 其余表达式使用 collection（元素迭代，类似 foreach 但支持 break/continue）。
#[derive(Debug, Clone, PartialEq)]
pub struct ForNode {
    pub iterator: String,
    pub range_from: Option<ExpressionNode>,
    pub range_to: Option<ExpressionNode>,
    pub collection: Option<ExpressionNode>,
    pub body: Vec<FlowNode>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub struct WhileNode {
    pub condition: ExpressionNode,
    pub body: Vec<FlowNode>,
    pub span: Span,
}

/// [V1.2-BREAK-CONTINUE] 循环跳出节点。
#[derive(Debug, Clone, PartialEq)]
pub struct BreakNode {
    pub span: Span,
}

/// [V1.2-BREAK-CONTINUE] 循环继续节点。
#[derive(Debug, Clone, PartialEq)]
pub struct ContinueNode {
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ParallelNode {
    pub branches: Vec<FlowNode>,
    /// [V1.2-PARALLEL] 分支级并发数上限（缺省沿用 runtime.max_parallel）。
    pub max_concurrency: Option<u32>,
    pub span: Span,
}

/// [V1.2-VALIDATE] 校验节点。
#[derive(Debug, Clone, PartialEq)]
pub struct ValidateNode {
    pub condition: ExpressionNode,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub struct TryCatchNode {
    pub try_nodes: Vec<FlowNode>,
    pub catch_binding: Option<String>,
    pub catch_nodes: Vec<FlowNode>,
    pub finally_nodes: Vec<FlowNode>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub struct WaitNode {
    pub wait_type: String,
    pub timeout: Option<TimeoutConfig>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AssertNode {
    pub condition: ExpressionNode,
    pub span: Span,
}

/// [V1.2-SWITCH] 多分支选择节点：subject 表达式 + case 分支 + 可选 default。
#[derive(Debug, Clone, PartialEq)]
pub struct SwitchNode {
    pub subject: ExpressionNode,
    pub cases: Vec<SwitchCase>,
    pub default_branch: Vec<FlowNode>,
    pub span: Span,
}

/// [V1.2-SWITCH] 单个 case 分支：匹配值 + 分支体。
#[derive(Debug, Clone, PartialEq)]
pub struct SwitchCase {
    pub value: ValueNode,
    pub body: Vec<FlowNode>,
    pub span: Span,
}

/// [V1.2-DELAY] 固定延迟步骤。
#[derive(Debug, Clone, PartialEq)]
pub struct DelayNode {
    pub raw: String,
    pub milliseconds: u64,
    pub span: Span,
}

/// [V1.2-NOTIFY] 内建通知节点。
#[derive(Debug, Clone, PartialEq)]
pub struct NotifyNode {
    pub channel: String,
    pub recipient: Option<ValueNode>,
    pub message: Option<ValueNode>,
    pub span: Span,
}

/// [V1.2-RETURN] 步骤级提前返回节点：携带可选输出表达式。
#[derive(Debug, Clone, PartialEq)]
pub struct ReturnNode {
    pub output: Option<ExpressionNode>,
    pub span: Span,
}

/// [V1.2-ENVIRONMENT] 环境变量声明项。
#[derive(Debug, Clone, PartialEq)]
pub struct EnvironmentDecl {
    pub key: String,
    pub value: ValueNode,
    pub span: Span,
}

/// [V1.2-AUDIT] 工作流审计注解。
#[derive(Debug, Clone, PartialEq)]
pub struct AuditAnnotation {
    pub level: String,
    pub description: Option<String>,
    pub span: Span,
}

/// [V1.2-STEP-GROUP] 步骤组：把多个步骤组合为一个逻辑单元执行。
#[derive(Debug, Clone, PartialEq)]
pub struct StepGroupNode {
    pub id: String,
    pub steps: Vec<StepNode>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq, Default)]
pub struct HandlerNode {
    pub id: String,
    pub nodes: Vec<FlowNode>,
    pub span: Span,
}
