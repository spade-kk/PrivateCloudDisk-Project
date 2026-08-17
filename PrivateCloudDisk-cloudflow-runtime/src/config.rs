//! Runtime 配置的安全默认值；生产密钥由环境变量注入，不写入源码。

#[derive(Debug, Clone)]
pub struct RuntimeConfig {
    pub max_source_bytes: usize,
    pub max_steps: usize,
    pub execution_timeout_seconds: u64,
}

impl Default for RuntimeConfig {
    fn default() -> Self {
        Self {
            max_source_bytes: 256 * 1024,
            max_steps: 200,
            execution_timeout_seconds: 120,
        }
    }
}
