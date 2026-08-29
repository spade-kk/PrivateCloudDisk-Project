//! Runtime 配置的安全默认值；生产密钥只从环境变量注入，不写入源码或日志。

use std::{env, time::Duration};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RuntimeMode {
    /// 仅提供编译/IR 校验，适用于 IDE 本地开发。
    Compiler,
    /// 启用 MySQL、RabbitMQ、gRPC Agent 和持久化执行 Worker。
    Production,
}

#[derive(Debug, Clone)]
pub struct RuntimeConfig {
    pub mode: RuntimeMode,
    pub database_url: Option<String>,
    pub database_max_connections: u32,
    pub rabbitmq_url: Option<String>,
    pub capability_agent_url: Option<String>,
    pub capability_agent_listen_address: Option<String>,
    pub workflow_capability_url: Option<String>,
    pub worker_concurrency: usize,
    pub stale_seconds: u64,
    pub poll_interval: Duration,
    pub action_timeout: Duration,
}

impl RuntimeConfig {
    pub fn from_env() -> Result<Self, String> {
        let mode = match env::var("CLOUDFLOW_RUNTIME_MODE")
            .unwrap_or_else(|_| "compiler".into())
            .to_ascii_lowercase()
            .as_str()
        {
            "compiler" | "compile" => RuntimeMode::Compiler,
            "production" | "persistent" => RuntimeMode::Production,
            value => return Err(format!("CLOUDFLOW_RUNTIME_MODE 不支持：{value}")),
        };
        let config = Self {
            mode,
            database_url: non_empty("CLOUDFLOW_DATABASE_URL")
                .or_else(|| non_empty("CLOUDFLOW_DATASOURCE_URL")),
            database_max_connections: number("CLOUDFLOW_DB_MAX_CONNECTIONS", 16),
            rabbitmq_url: non_empty("CLOUDFLOW_RABBITMQ_URL"),
            capability_agent_url: non_empty("CLOUDFLOW_CAPABILITY_AGENT_GRPC_URL"),
            capability_agent_listen_address: non_empty("CLOUDFLOW_AGENT_LISTEN_ADDRESS"),
            workflow_capability_url: non_empty("CLOUDFLOW_WORKFLOW_CAPABILITY_URL"),
            worker_concurrency: number("CLOUDFLOW_WORKER_CONCURRENCY", 8),
            stale_seconds: number("CLOUDFLOW_STALE_SECONDS", 180),
            poll_interval: Duration::from_millis(number("CLOUDFLOW_POLL_INTERVAL_MS", 250)),
            action_timeout: Duration::from_secs(number("CLOUDFLOW_ACTION_TIMEOUT_SECONDS", 120)),
        };
        if mode == RuntimeMode::Production {
            for (name, value) in [
                ("CLOUDFLOW_DATABASE_URL", config.database_url.as_ref()),
                ("CLOUDFLOW_RABBITMQ_URL", config.rabbitmq_url.as_ref()),
                (
                    "CLOUDFLOW_CAPABILITY_AGENT_GRPC_URL",
                    config.capability_agent_url.as_ref(),
                ),
                (
                    "CLOUDFLOW_AGENT_LISTEN_ADDRESS",
                    config.capability_agent_listen_address.as_ref(),
                ),
                (
                    "CLOUDFLOW_WORKFLOW_CAPABILITY_URL",
                    config.workflow_capability_url.as_ref(),
                ),
            ] {
                if value.is_none() {
                    return Err(format!("生产执行面缺少 {name}"));
                }
            }
        }
        Ok(config)
    }
}

impl Default for RuntimeConfig {
    fn default() -> Self {
        Self {
            mode: RuntimeMode::Compiler,
            database_url: None,
            database_max_connections: 16,
            rabbitmq_url: None,
            capability_agent_url: None,
            capability_agent_listen_address: None,
            workflow_capability_url: None,
            worker_concurrency: 8,
            stale_seconds: 180,
            poll_interval: Duration::from_millis(250),
            action_timeout: Duration::from_secs(120),
        }
    }
}

fn non_empty(name: &str) -> Option<String> {
    env::var(name).ok().filter(|value| !value.trim().is_empty())
}

fn number<T>(name: &str, default: T) -> T
where
    T: std::str::FromStr + Copy,
{
    env::var(name)
        .ok()
        .and_then(|value| value.parse().ok())
        .unwrap_or(default)
}
