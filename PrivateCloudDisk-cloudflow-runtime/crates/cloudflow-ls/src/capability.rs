//! Capability Hub adapter used by dynamic CloudFlow LSP intelligence.
//!
//! [CLOUDFLOW-LS-CAPABILITY-001] CloudFlow actions are not language keywords.
//! They come from the platform capability registry and are cached by the opaque
//! authenticated principal hash + tenant + space. The LS never invents a list
//! of user capabilities and never stores the raw token in cache/logs.

use crate::auth::AuthContext;
use cloudflow_runtime::semantic::CapabilityCatalog;
use reqwest::{Client, StatusCode};
use serde::Deserialize;
use std::{
    collections::{HashMap, HashSet},
    sync::Arc,
    time::{Duration, Instant},
};
use tokio::sync::RwLock;

pub const DEFAULT_CAPABILITY_API: &str = "http://127.0.0.1:8080/api/v1";
pub const DEFAULT_CACHE_TTL: Duration = Duration::from_secs(300);

#[derive(Debug, Clone, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Capability {
    pub capability_key: String,
    #[serde(default)]
    pub source_type: String,
    #[serde(default)]
    pub source_id: String,
    #[serde(default)]
    pub source_version: String,
    #[serde(default)]
    pub display_name: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub input_schema_json: String,
    #[serde(default)]
    pub output_schema_json: String,
    #[serde(default)]
    pub required_permissions_json: String,
    #[serde(default)]
    pub availability_policy_json: String,
}

#[derive(Debug, Deserialize)]
struct ApiEnvelope<T> {
    #[allow(dead_code)]
    code: Option<serde_json::Value>,
    data: T,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
struct CacheKey {
    principal: String,
    tenant_id: String,
    space_id: String,
}

#[derive(Debug, Clone)]
struct CachedCapabilities {
    loaded_at: Instant,
    capabilities: Vec<Capability>,
}

#[derive(Debug, Clone)]
pub struct DynamicCapabilityCatalog {
    keys: Arc<HashSet<String>>,
}

impl DynamicCapabilityCatalog {
    pub fn from_capabilities(capabilities: &[Capability], authoritative: bool) -> Self {
        let mut keys = capabilities
            .iter()
            .map(|item| item.capability_key.clone())
            .collect::<HashSet<_>>();
        // The compiler deliberately gates catalog enforcement behind this
        // sentinel. Add it only after a successful authenticated Hub response;
        // an offline LS must not claim all actions are unavailable.
        if authoritative {
            keys.insert("__catalog_enabled__".into());
        }
        Self {
            keys: Arc::new(keys),
        }
    }
}

impl CapabilityCatalog for DynamicCapabilityCatalog {
    fn contains(&self, key: &str) -> bool {
        self.keys.contains(key)
    }
}

#[derive(Clone)]
pub struct CapabilityProvider {
    client: Client,
    api_base: String,
    ttl: Duration,
    cache: Arc<RwLock<HashMap<CacheKey, CachedCapabilities>>>,
}

#[derive(Debug, thiserror::Error)]
pub enum CapabilityError {
    #[error("CloudFlow 平台令牌不存在；LS 已降级为静态语法智能")]
    Unauthenticated,
    #[error("CloudFlow 平台拒绝能力查询（HTTP {0}）")]
    Forbidden(StatusCode),
    #[error("Capability Hub 请求失败：{0}")]
    Request(String),
    #[error("Capability Hub 返回了无效数据：{0}")]
    InvalidResponse(String),
}

impl CapabilityProvider {
    pub fn new(api_base: impl Into<String>, ttl: Duration) -> Result<Self, CapabilityError> {
        let client = Client::builder()
            .timeout(Duration::from_secs(5))
            .build()
            .map_err(|error| CapabilityError::Request(error.to_string()))?;
        Ok(Self {
            client,
            api_base: api_base.into().trim_end_matches('/').into(),
            ttl,
            cache: Arc::new(RwLock::new(HashMap::new())),
        })
    }

    pub async fn capabilities(
        &self,
        auth: &AuthContext,
    ) -> Result<Vec<Capability>, CapabilityError> {
        if !auth.is_authenticated() {
            return Err(CapabilityError::Unauthenticated);
        }
        let key = self.cache_key(auth);
        if let Some(cached) = self
            .cache
            .read()
            .await
            .get(&key)
            .filter(|entry| entry.loaded_at.elapsed() < self.ttl)
            .cloned()
        {
            return Ok(cached.capabilities);
        }
        let fetched = self.fetch(auth).await?;
        self.cache.write().await.insert(
            key,
            CachedCapabilities {
                loaded_at: Instant::now(),
                capabilities: fetched.clone(),
            },
        );
        Ok(fetched)
    }

    /// Retain no raw identity in keys. A platform permission/plugin change can invoke
    /// `workspace/executeCommand cloudflow.clearCapabilityCache` or call this hook.
    pub async fn invalidate(&self, auth: Option<&AuthContext>) {
        let mut cache = self.cache.write().await;
        if let Some(auth) = auth {
            cache.remove(&self.cache_key(auth));
        } else {
            cache.clear();
        }
    }

    async fn fetch(&self, auth: &AuthContext) -> Result<Vec<Capability>, CapabilityError> {
        let url = format!("{}/capabilities", self.api_base);
        let mut request = self
            .client
            .get(url)
            .bearer_auth(auth.access_token.as_deref().unwrap_or_default())
            .query(&[("page", "1"), ("size", "100")]);
        if let Some(space_id) = auth
            .space_id
            .as_deref()
            .filter(|value| !value.trim().is_empty())
        {
            request = request.header("X-Space-Id", space_id);
        }
        if let Some(tenant_id) = auth
            .tenant_id
            .as_deref()
            .filter(|value| !value.trim().is_empty())
        {
            request = request.header("X-Tenant-Id", tenant_id);
        }
        let response = request
            .send()
            .await
            .map_err(|error| CapabilityError::Request(error.to_string()))?;
        if response.status() == StatusCode::UNAUTHORIZED
            || response.status() == StatusCode::FORBIDDEN
        {
            return Err(CapabilityError::Forbidden(response.status()));
        }
        if !response.status().is_success() {
            return Err(CapabilityError::Request(format!(
                "HTTP {}",
                response.status()
            )));
        }
        response
            .json::<ApiEnvelope<Vec<Capability>>>()
            .await
            .map(|body| body.data)
            .map_err(|error| CapabilityError::InvalidResponse(error.to_string()))
    }

    fn cache_key(&self, auth: &AuthContext) -> CacheKey {
        CacheKey {
            principal: auth.cache_identity(),
            tenant_id: auth.tenant_id.clone().unwrap_or_default(),
            space_id: auth.space_id.clone().unwrap_or_default(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn catalog_only_contains_returned_capabilities() {
        let catalog = DynamicCapabilityCatalog::from_capabilities(
            &[Capability {
                capability_key: "api:file.upload".into(),
                source_type: "API".into(),
                source_id: String::new(),
                source_version: String::new(),
                display_name: String::new(),
                description: String::new(),
                input_schema_json: "{}".into(),
                output_schema_json: "{}".into(),
                required_permissions_json: "[]".into(),
                availability_policy_json: "{}".into(),
            }],
            true,
        );
        assert!(catalog.contains("api:file.upload"));
        assert!(!catalog.contains("plugin:unlicensed.action"));
    }

    #[tokio::test]
    async fn cache_is_scoped_by_tenant_and_space() {
        let provider = CapabilityProvider::new("http://127.0.0.1:1", DEFAULT_CACHE_TTL).unwrap();
        let a = AuthContext {
            access_token: Some("same".into()),
            tenant_id: Some("tenant-a".into()),
            space_id: Some("space-a".into()),
        };
        let b = AuthContext {
            access_token: Some("same".into()),
            tenant_id: Some("tenant-b".into()),
            space_id: Some("space-a".into()),
        };
        assert_ne!(provider.cache_key(&a), provider.cache_key(&b));
    }
}
