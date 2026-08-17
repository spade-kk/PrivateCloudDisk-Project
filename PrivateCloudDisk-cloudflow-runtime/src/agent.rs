use std::collections::HashSet;

#[derive(Debug, Clone)]
pub struct AuthorizationContext {
    pub user_id: String,
    pub space_id: Option<String>,
    pub declared_permissions: HashSet<String>,
    pub granted_permissions: HashSet<String>,
}

impl AuthorizationContext {
    pub fn allows(&self, permission: &str) -> bool {
        self.declared_permissions.contains(permission)
            && self.granted_permissions.contains(permission)
    }
}

pub trait CapabilityInvoker {
    fn invoke(&self, capability: &str, context: &AuthorizationContext) -> Result<(), String>;
}
