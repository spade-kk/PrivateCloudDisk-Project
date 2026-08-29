//! 时钟实现（需求 1.9/2.7/6.18）：真实时钟（生产面）与虚拟时钟（调试面）。

use crate::engine::deps::Clock;
use std::time::{Duration, Instant};

/// 真实时钟（生产面）：墙钟时间 + 真实睡眠。
#[derive(Debug, Clone)]
pub struct RealClock {
    start: Instant,
}

impl RealClock {
    pub fn new() -> Self {
        Self {
            start: Instant::now(),
        }
    }
}

impl Default for RealClock {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait::async_trait]
impl Clock for RealClock {
    fn now_ms(&self) -> u64 {
        self.start.elapsed().as_millis() as u64
    }

    async fn sleep_backoff(&self, delay: Duration) {
        tokio::time::sleep(delay).await;
    }

    async fn sleep_delay(&self, ms: u64) {
        tokio::time::sleep(Duration::from_millis(ms)).await;
    }

    fn advance(&self, _ms: u64) {}
}

/// 虚拟时钟（调试面）：墙钟 + 模拟动作延迟累计（需求 4.14：模拟延迟计入
/// 全局执行时间与结果耗时）。`honor_delays=false` 时 delay 节点不睡眠（测试加速）。
#[derive(Debug)]
pub struct VirtualClock {
    start: Instant,
    virtual_ms: std::sync::Mutex<u64>,
    honor_delays: bool,
}

impl VirtualClock {
    pub fn new(honor_delays: bool) -> Self {
        Self {
            start: Instant::now(),
            virtual_ms: std::sync::Mutex::new(0),
            honor_delays,
        }
    }

    /// 模拟动作延迟累计（毫秒）。
    pub fn simulated_ms(&self) -> u64 {
        *self.virtual_ms.lock().expect("虚拟时钟锁中毒")
    }
}

#[async_trait::async_trait]
impl Clock for VirtualClock {
    fn now_ms(&self) -> u64 {
        self.start.elapsed().as_millis() as u64 + self.simulated_ms()
    }

    /// 重试退避：调试面不真实睡眠（与历史行为一致：仅记录计划延迟）。
    async fn sleep_backoff(&self, _delay: Duration) {}

    /// delay 节点：`honor_delays` 时线程睡眠且封顶 5s（测试加速），否则 no-op。
    async fn sleep_delay(&self, ms: u64) {
        if self.honor_delays {
            std::thread::sleep(Duration::from_millis(ms.min(5_000)));
        }
    }

    /// 模拟动作延迟记账（Mock 执行器在模拟延迟路径调用）。
    fn advance(&self, ms: u64) {
        let mut guard = self.virtual_ms.lock().expect("虚拟时钟锁中毒");
        *guard = guard.saturating_add(ms);
    }
}
