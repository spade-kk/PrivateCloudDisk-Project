use std::collections::HashMap;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExecutionStatus {
    Pending,
    Running,
    RetryWaiting,
    Success,
    Failed,
    TimedOut,
    Cancelled,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExecutionState {
    pub execution_id: String,
    pub status: ExecutionStatus,
    pub current_step: Option<String>,
    pub row_version: u64,
}

#[derive(Default)]
pub struct ExecutionStateStore {
    states: HashMap<String, ExecutionState>,
}

impl ExecutionStateStore {
    pub fn create(&mut self, execution_id: impl Into<String>) -> ExecutionState {
        let state = ExecutionState {
            execution_id: execution_id.into(),
            status: ExecutionStatus::Pending,
            current_step: None,
            row_version: 0,
        };
        self.states
            .insert(state.execution_id.clone(), state.clone());
        state
    }
    pub fn transition(
        &mut self,
        execution_id: &str,
        status: ExecutionStatus,
        step: Option<String>,
    ) -> Option<ExecutionState> {
        let state = self.states.get_mut(execution_id)?;
        state.status = status;
        state.current_step = step;
        state.row_version += 1;
        Some(state.clone())
    }
    pub fn get(&self, execution_id: &str) -> Option<&ExecutionState> {
        self.states.get(execution_id)
    }
}

pub fn exponential_backoff_ms(attempt: u8, base_ms: u64, max_ms: u64) -> u64 {
    let exponent = u32::from(attempt.min(16));
    base_ms
        .saturating_mul(2_u64.saturating_pow(exponent))
        .min(max_ms)
}
