//! 统一执行引擎错误模型（需求 1.21）。
//!
//! `ExecutionError` 是统一调度驱动（`crate::engine::driver`）内部的唯一错误类型：
//! 携带语义化变体 + 稳定错误码。各执行面按自身错误码体系映射：
//! - 生产执行面：`production_code()` / `production_display()` 与历史
//!   `RuntimeExecutionError` 的码表、文案逐字对齐（CF6001/CF1301/CF2101/CF4408/
//!   CF4417/CF4412/CF2203…），保证生产行为不变；
//! - 开发调试面：经 `crate::dev_exec::to_dev_error` 映射为 CFD-81xx 体系包装
//!   （如 CF2101 → CFD-8101 原值直通），保持调试面既有错误码与文案。

use serde_json::Value;

/// 统一驱动执行错误（节点/控制流/状态 I/O 共用）。
#[derive(Debug, Clone, PartialEq)]
pub enum ExecutionError {
    /// 状态存储 I/O 失败（生产：数据库不可用；内存实现理论上不产生）。
    Store(String),
    /// IR 结构/契约/调度问题。
    Ir(String),
    /// 能力（Agent）调用失败；`code` 由 Agent 侧给出（CF3001/CF5001…）。
    Action {
        code: String,
        message: String,
        retryable: bool,
    },
    /// 表达式/变量/引用求值失败。
    Variable(String),
    /// assert 条件不成立（步骤行 CF2202；工作流级码随面映射）。
    AssertFailed,
    /// validate 校验未通过（CF4412）。
    ValidateFailed,
    /// break 跳出循环（内部控制信号；非业务异常）。
    LoopBreak,
    /// continue 进入下次迭代（内部控制信号；非业务异常）。
    LoopContinue,
    /// 提前返回信号，携带返回输出。
    StepReturn(Value),
    /// wait 出现在动态执行体内（CF2203）。
    DynamicWait,
    /// 循环迭代上限被突破（CF2201）。
    LoopLimit {
        /// foreach / while / for。
        kind: &'static str,
        /// foreach 元素数量（while/for 为 None）。
        elements: Option<usize>,
        /// maxIterations 上限。
        max: usize,
    },
    /// 取值/取值类型问题（foreach 集合非数组、range 端点非数字等；
    /// 两执行面同为 CF2101，文案直通）。
    ValueProblem(String),
    /// 缺失必需配置（loopConfig/switchConfig/errorHandler/iterator…；
    /// 生产 CF1301 / 调试面 CFD-8102）。
    MissingConfig(String),
    /// loopConfig.kind 不在支持集合（两执行面文案不同）。
    UnsupportedLoopKind(String),
    /// 内部不变量破坏（如重试循环异常结束）。
    Internal(String),
}

impl ExecutionError {
    /// 是否为循环/返回控制信号（try/catch 不捕获、沿调用栈向上传播，需求 5.9/12.6）。
    pub fn is_control_signal(&self) -> bool {
        matches!(
            self,
            Self::LoopBreak | Self::LoopContinue | Self::StepReturn(_)
        )
    }

    /// 生产执行面错误码（与历史 `RuntimeExecutionError::code()` 逐字对齐）。
    pub fn production_code(&self) -> String {
        match self {
            Self::Store(_) => "CF6001".into(),
            Self::Ir(_) => "CF1301".into(),
            Self::Action { code, .. } => code.clone(),
            Self::Variable(_) => "CF2101".into(),
            Self::AssertFailed => "CF2101".into(),
            Self::ValidateFailed => "CF4412".into(),
            Self::LoopBreak | Self::LoopContinue => "CF4408".into(),
            Self::StepReturn(_) => "CF4417".into(),
            Self::DynamicWait => "CF1301".into(),
            Self::LoopLimit { .. } => "CF1301".into(),
            Self::ValueProblem(_) => "CF2101".into(),
            Self::MissingConfig(_) => "CF1301".into(),
            Self::UnsupportedLoopKind(_) => "CF1301".into(),
            Self::Internal(_) => "CF1301".into(),
        }
    }

    /// 生产执行面 `Display` 文案（与历史 `RuntimeExecutionError` 逐字对齐）。
    pub fn production_display(&self) -> String {
        match self {
            Self::Store(error) => format!("数据库错误：{error}"),
            Self::Ir(message) => format!("IR 错误：{message}"),
            Self::Action { message, .. } => message.clone(),
            Self::Variable(message) => format!("变量错误：{message}"),
            Self::AssertFailed => "变量错误：CF2202: assert 条件不成立".into(),
            Self::ValidateFailed => "validate 校验未通过：validate 表达式求值为 false".into(),
            Self::LoopBreak => "break 跳出循环".into(),
            Self::LoopContinue => "continue 进入下次迭代".into(),
            Self::StepReturn(value) => format!("提前返回：{value:?}"),
            Self::DynamicWait => "IR 错误：CF2203: 动态执行体不支持 WAITING".into(),
            Self::LoopLimit {
                kind,
                elements,
                max,
            } => match *kind {
                "foreach" => format!(
                    "IR 错误：CF2201: foreach 元素数量 {} 超过 maxIterations {max}",
                    elements.expect("foreach 必带元素数量")
                ),
                "while" => {
                    format!("IR 错误：CF2201: while 超过 maxIterations {max}，已中止以避免无限循环")
                }
                _ => format!("IR 错误：CF2201: for 超过 maxIterations {max}"),
            },
            Self::MissingConfig(message) => format!("IR 错误：{message}"),
            Self::UnsupportedLoopKind(kind) => format!("IR 错误：不支持的 loop kind：{kind}"),
            Self::ValueProblem(message) => format!("变量错误：{message}"),
            Self::Internal(message) => format!("IR 错误：{message}"),
        }
    }

    /// 调试面错误码（CFD-81xx 体系；业务码原值直通）。
    pub fn dev_code(&self) -> String {
        match self {
            Self::Store(_) => "CFD-8101".into(),
            Self::Ir(_) => "CFD-8102".into(),
            Self::Action { code, .. } => code.clone(),
            Self::Variable(_) => "CFD-8101".into(),
            Self::AssertFailed => "CF2202".into(),
            Self::ValidateFailed => "CF4412".into(),
            Self::LoopBreak | Self::LoopContinue => "CF4408".into(),
            Self::StepReturn(_) => "CF4417".into(),
            Self::DynamicWait => "CF2203".into(),
            Self::LoopLimit { .. } => "CF2201".into(),
            Self::ValueProblem(_) => "CF2101".into(),
            Self::MissingConfig(_) => "CFD-8102".into(),
            Self::UnsupportedLoopKind(_) => "CFD-8102".into(),
            Self::Internal(message) => {
                // 与历史调试面一致：重试循环异常结束走 CFD-8108，其余不变量走 CFD-8102。
                if message == "重试循环异常结束" {
                    "CFD-8108".into()
                } else {
                    "CFD-8102".into()
                }
            }
        }
    }

    /// 步骤行/catch 绑定用的失败文案（两执行面各自的历史口径）：
    /// - 生产面（`inline=false`）：与历史 `RuntimeExecutionError::public_message` 逐字对齐
    ///   （无 "IR 错误：" / "变量错误：" 前缀；Agent 用摘要；DB 用固定文案）；
    /// - 调试面（`inline=true`）：与历史 `DevError.message` 逐字对齐（= `dev_message`）。
    pub fn public_message_for(&self, inline: bool) -> String {
        if inline {
            return self.dev_message();
        }
        match self {
            Self::Store(_) => "CloudFlow Runtime 持久化服务暂时不可用".into(),
            Self::Ir(message)
            | Self::Variable(message)
            | Self::Action { message, .. }
            | Self::ValueProblem(message)
            | Self::MissingConfig(message)
            | Self::Internal(message) => message.clone(),
            Self::AssertFailed => "CF2202: assert 条件不成立".into(),
            Self::ValidateFailed => "validate 校验未通过：validate 表达式求值为 false".into(),
            Self::LoopBreak => "break 跳出循环".into(),
            Self::LoopContinue => "continue 进入下次迭代".into(),
            Self::StepReturn(value) => format!("提前返回输出 {value}"),
            Self::DynamicWait => "CF2203: 动态执行体不支持 WAITING".into(),
            Self::LoopLimit {
                kind,
                elements,
                max,
            } => match *kind {
                "foreach" => format!(
                    "CF2201: foreach 元素数量 {} 超过 maxIterations {max}",
                    elements.expect("foreach 必带元素数量")
                ),
                "while" => format!("CF2201: while 超过 maxIterations {max}，已中止以避免无限循环"),
                _ => format!("CF2201: for 超过 maxIterations {max}"),
            },
            Self::UnsupportedLoopKind(kind) => format!("不支持的 loop kind：{kind}"),
        }
    }

    /// 调试面错误文案（与历史 `DevExecError`/`DevError.message` 逐字对齐）。
    pub fn dev_message(&self) -> String {
        match self {
            Self::Store(message) => message.clone(),
            Self::Ir(message) => message.clone(),
            Self::Action { message, .. } => message.clone(),
            Self::Variable(message) => message.clone(),
            Self::AssertFailed => "assert 条件不成立".into(),
            Self::ValidateFailed => "validate 校验未通过".into(),
            Self::LoopBreak => "break 跳出循环".into(),
            Self::LoopContinue => "continue 进入下次迭代".into(),
            Self::StepReturn(value) => format!("提前返回输出 {value}"),
            Self::DynamicWait => "wait 不允许位于 foreach/while/try 的动态执行体内".into(),
            Self::LoopLimit {
                kind,
                elements,
                max,
            } => match *kind {
                "foreach" => format!(
                    "foreach 元素数量 {} 超过 maxIterations {max}",
                    elements.expect("foreach 必带元素数量")
                ),
                "while" => format!("while 超过 maxIterations {max}，已中止以避免无限循环"),
                _ => format!("for 超过 maxIterations {max}"),
            },
            Self::ValueProblem(message) => message.clone(),
            Self::MissingConfig(message) => message.clone(),
            Self::UnsupportedLoopKind(kind) => format!("未知 loopConfig.kind：{kind}"),
            Self::Internal(message) => message.clone(),
        }
    }
}

impl std::fmt::Display for ExecutionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        // 面向用户默认给调试面文案（统一驱动以调试面为默认受众）；
        // 生产面显式使用 `production_display`。
        write!(f, "{}：{}", self.dev_code(), self.dev_message())
    }
}

impl std::error::Error for ExecutionError {}
