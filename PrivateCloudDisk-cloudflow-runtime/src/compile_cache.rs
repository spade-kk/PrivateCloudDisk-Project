//! [19.17] 编译产物进程内缓存（HTTP 编译接口专用）。
//!
//! 定位与边界：
//! - 缓存粒度为 **完整编译结果**（成功 IR 或失败诊断列表），键为请求指纹
//!   （源码 SHA-256 + 语言 + 目标 IR 版本 + 能力目录指纹 + 文件名）；
//! - 仅当 `filename` 缺省（`<request>`）时启用：此情形 include 解析必然失败
//!   （无物理根目录，CF3103），结果完全由请求内容决定，缓存无陈旧化风险；
//! - 用户显式携带 `.flow` 路径文件名时**禁用缓存**（include 可能读取本地
//!   模块文件，文件变更不得被旧缓存遮蔽）；
//! - 不跨进程、不落盘：进程重启即失效，无持久化一致性问题；
//! - 容量上限（默认 256 条）+ 超限整体清空（粗粒度策略，避免引入额外依赖），
//!   命中/未命中计数供可观测性使用（19.8/19.23）。
//!
//! 确定性前提：`compile_source_named_for_language` 对固定输入是纯计算
//! （解析 → include 拒绝 → use 默认参数注入 → 统一语义 → IR 生成），
//! 无时间戳/随机数/全局可变状态影响结果。

use crate::diagnostic::Diagnostic;
use crate::ir::WorkflowIrV1;
use crate::Language;
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};

/// 缓存键：源码双 64 位哈希 + 长度 + 请求维度指纹。
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct CacheKey {
    source_hash: [u64; 2],
    source_len: usize,
    filename: String,
    language: Language,
    target_ir_version: Option<String>,
    catalog_hash: u64,
}

#[derive(Debug, Clone)]
pub struct CacheEntry {
    pub valid: bool,
    pub ir: Option<WorkflowIrV1>,
    pub diagnostics: Vec<Diagnostic>,
}

fn sha256_parts(input: &[u8]) -> [u64; 2] {
    let digest = Sha256::digest(input);
    let mut hi = [0u8; 8];
    let mut lo = [0u8; 8];
    hi.copy_from_slice(&digest[..8]);
    lo.copy_from_slice(&digest[8..16]);
    [u64::from_be_bytes(hi), u64::from_be_bytes(lo)]
}

/// 能力目录指纹：能力集合（含 `__catalog_enabled__` 标记语义）决定 CF3001 结果，
/// 必须参与缓存键。排序后哈希保证与注入顺序无关。
fn catalog_fingerprint(capabilities: &[String]) -> u64 {
    let mut sorted = capabilities.to_vec();
    sorted.sort();
    let joined: String = sorted.join("\u{0}");
    sha256_parts(joined.as_bytes())[1]
}

pub struct CompileCache {
    entries: Mutex<HashMap<CacheKey, Arc<CacheEntry>>>,
    capacity: usize,
    hits: AtomicU64,
    misses: AtomicU64,
}

impl CompileCache {
    fn new(capacity: usize) -> Self {
        Self {
            entries: Mutex::new(HashMap::new()),
            capacity,
            hits: AtomicU64::new(0),
            misses: AtomicU64::new(0),
        }
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, HashMap<CacheKey, Arc<CacheEntry>>> {
        self.entries
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    /// 查询缓存；未命中时记录 miss。
    pub fn get(&self, key: &CacheKey) -> Option<Arc<CacheEntry>> {
        match self.lock().get(key).cloned() {
            Some(entry) => {
                self.hits.fetch_add(1, Ordering::Relaxed);
                Some(entry)
            }
            None => {
                self.misses.fetch_add(1, Ordering::Relaxed);
                None
            }
        }
    }

    /// 写入缓存；超过容量上限时整体清空后写入（粗粒度 LRU 近似）。
    pub fn insert(&self, key: CacheKey, entry: CacheEntry) {
        let mut cache = self.lock();
        if cache.len() >= self.capacity {
            cache.clear();
        }
        cache.insert(key, Arc::new(entry));
    }

    pub fn stats(&self) -> (usize, usize, u64, u64) {
        (
            self.lock().len(),
            self.capacity,
            self.hits.load(Ordering::Relaxed),
            self.misses.load(Ordering::Relaxed),
        )
    }

    pub fn clear(&self) {
        self.lock().clear();
    }
}

impl Default for CompileCache {
    fn default() -> Self {
        Self::new(256)
    }
}

/// 构造缓存键（HTTP 编译接口使用）。
pub fn compile_cache_key(
    source: &str,
    filename: &str,
    language: Language,
    target_ir_version: Option<String>,
    capabilities: &[String],
) -> CacheKey {
    CacheKey {
        source_hash: sha256_parts(source.as_bytes()),
        source_len: source.len(),
        filename: filename.to_owned(),
        language,
        target_ir_version,
        catalog_hash: catalog_fingerprint(capabilities),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn key(source: &str) -> CacheKey {
        compile_cache_key(
            source,
            "<request>",
            Language::Dsl,
            None,
            &vec!["file.list".to_string()],
        )
    }

    #[test]
    fn same_source_hits_cache_different_source_misses() {
        let cache = CompileCache::default();
        let entry = CacheEntry {
            valid: true,
            ir: None,
            diagnostics: vec![],
        };
        cache.insert(key("a"), entry.clone());
        assert!(cache.get(&key("a")).is_some());
        assert!(cache.get(&key("b")).is_none());
        let (entries, capacity, hits, misses) = cache.stats();
        assert_eq!(entries, 1);
        assert_eq!(capacity, 256);
        assert_eq!(hits, 1);
        assert_eq!(misses, 1);
    }

    #[test]
    fn capacity_overflow_clears_and_inserts() {
        let cache = CompileCache::new(2);
        for index in 0..2 {
            cache.insert(
                key(&format!("s{index}")),
                CacheEntry {
                    valid: false,
                    ir: None,
                    diagnostics: vec![],
                },
            );
        }
        cache.insert(
            key("overflow"),
            CacheEntry {
                valid: false,
                ir: None,
                diagnostics: vec![],
            },
        );
        assert!(cache.get(&key("overflow")).is_some());
        assert!(cache.get(&key("s0")).is_none());
    }

    #[test]
    fn catalog_fingerprint_is_order_independent() {
        let a = catalog_fingerprint(&["b".into(), "a".into()]);
        let b = catalog_fingerprint(&["a".into(), "b".into()]);
        assert_eq!(a, b);
        let c = catalog_fingerprint(&["a".into()]);
        assert_ne!(a, c);
    }
}
