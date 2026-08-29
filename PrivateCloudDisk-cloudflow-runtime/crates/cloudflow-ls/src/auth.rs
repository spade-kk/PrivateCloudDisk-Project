//! Token discovery for local Language Server mode.
//!
//! Browser/server clients pass their access token in LSP initializationOptions;
//! stdio can read the same token from environment or a 0600 local token file.

use sha2::{Digest, Sha256};
use std::{
    env, fs,
    path::{Path, PathBuf},
};

#[derive(Debug, Clone, Default)]
pub struct AuthContext {
    pub access_token: Option<String>,
    pub tenant_id: Option<String>,
    pub space_id: Option<String>,
}

impl AuthContext {
    pub fn cache_identity(&self) -> String {
        let Some(token) = &self.access_token else {
            return "offline".into();
        };
        let mut hash = Sha256::new();
        hash.update(token.as_bytes());
        format!("{:x}", hash.finalize())
    }

    pub fn is_authenticated(&self) -> bool {
        self.access_token
            .as_ref()
            .is_some_and(|value| !value.trim().is_empty())
    }
}

pub fn load_local_token(explicit_file: Option<&Path>) -> Result<Option<String>, String> {
    if let Ok(token) = env::var("CLOUDFLOW_TOKEN") {
        let token = token.trim().to_owned();
        return Ok((!token.is_empty()).then_some(token));
    }
    let configured = explicit_file
        .map(PathBuf::from)
        .or_else(|| env::var_os("CLOUDFLOW_TOKEN_FILE").map(PathBuf::from))
        .or_else(default_token_file);
    let Some(path) = configured else {
        return Ok(None);
    };
    if !path.exists() {
        return Ok(None);
    }
    ensure_owner_only(&path)?;
    let token = fs::read_to_string(&path)
        .map_err(|error| format!("无法读取 CloudFlow Token 文件 {}：{error}", path.display()))?;
    let token = token.trim().to_owned();
    Ok((!token.is_empty()).then_some(token))
}

fn default_token_file() -> Option<PathBuf> {
    env::var_os("HOME")
        .map(PathBuf::from)
        .map(|home| home.join(".cloudflow/token"))
}

#[cfg(unix)]
fn ensure_owner_only(path: &Path) -> Result<(), String> {
    use std::os::unix::fs::PermissionsExt;
    let mode = fs::metadata(path)
        .map_err(|error| format!("无法读取 Token 文件权限：{error}"))?
        .permissions()
        .mode();
    if mode & 0o077 != 0 {
        return Err(format!(
            "Token 文件 {} 权限必须为 0600（当前 {:o}）",
            path.display(),
            mode & 0o777
        ));
    }
    Ok(())
}

#[cfg(not(unix))]
fn ensure_owner_only(_path: &Path) -> Result<(), String> {
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cache_identity_never_contains_token() {
        let context = AuthContext {
            access_token: Some("secret-token".into()),
            ..Default::default()
        };
        assert_ne!(context.cache_identity(), "secret-token");
        assert_eq!(context.cache_identity().len(), 64);
    }
}
