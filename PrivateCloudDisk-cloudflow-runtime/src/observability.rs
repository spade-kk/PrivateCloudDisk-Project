use std::sync::atomic::{AtomicU64, Ordering};

#[derive(Default)]
pub struct RuntimeMetrics {
    pub executions_total: AtomicU64,
    pub succeeded_total: AtomicU64,
    pub failed_total: AtomicU64,
    pub retries_total: AtomicU64,
}

impl RuntimeMetrics {
    pub fn record_success(&self) {
        self.executions_total.fetch_add(1, Ordering::Relaxed);
        self.succeeded_total.fetch_add(1, Ordering::Relaxed);
    }
    pub fn record_failure(&self) {
        self.executions_total.fetch_add(1, Ordering::Relaxed);
        self.failed_total.fetch_add(1, Ordering::Relaxed);
    }
    pub fn record_retry(&self) {
        self.retries_total.fetch_add(1, Ordering::Relaxed);
    }
}
