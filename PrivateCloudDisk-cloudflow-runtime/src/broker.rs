use std::collections::HashSet;

#[derive(Debug, Clone)]
pub struct EventEnvelope {
    pub id: String,
    pub event_type: String,
    pub correlation_id: String,
    pub causation_id: Option<String>,
    pub user_id: String,
    pub space_id: Option<String>,
    pub retry_count: u32,
}

#[derive(Default)]
pub struct IdempotencyStore {
    keys: HashSet<String>,
}

impl IdempotencyStore {
    pub fn claim(&mut self, key: &str) -> bool {
        self.keys.insert(key.to_string())
    }
}
