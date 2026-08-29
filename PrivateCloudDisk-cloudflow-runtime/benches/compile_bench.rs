//! [19.18/19.22] 编译性能基准（发布前性能基线）。
//!
//! 运行：`cargo bench --bench compile_bench`
//! 说明：无 criterion 依赖的轻量基准（稳定版即可运行），对固定输入重复编译
//! N 次并输出中位/平均耗时。覆盖三条关键路径：
//! - DSL 编译（含表达式解析，验证 19.1 不劣化）；
//! - YAML 编译（含 serde_yaml_ng 解析 + Schema 校验 + 表达式注入，验证 19.2）；
//! - 表达式子系统单独解析（含 19.3 缓存命中路径）。

use std::time::{Duration, Instant};

use cloudflow_runtime::{compile_source_named, expression, semantic::InMemoryCapabilityCatalog};

const WARMUP: usize = 3;
const ITERATIONS: usize = 50;

fn time_repetitive<F: FnMut()>(mut work: F, label: &str) -> f64 {
    for _ in 0..WARMUP {
        work();
    }
    let mut samples: Vec<Duration> = Vec::with_capacity(ITERATIONS);
    for _ in 0..ITERATIONS {
        let start = Instant::now();
        work();
        samples.push(start.elapsed());
    }
    samples.sort();
    let median = samples[samples.len() / 2];
    let average: Duration = samples.iter().sum::<Duration>() / samples.len() as u32;
    println!("{label}: median {median:.3?} avg {average:.3?} (n={ITERATIONS})");
    median.as_secs_f64() * 1e6
}

fn main() {
    let catalog = InMemoryCapabilityCatalog::default();

    let dsl_source = include_str!("../examples/weekly_sales_report.flow");
    let median_us = time_repetitive(
        || {
            let _ = compile_source_named(dsl_source, "weekly_sales_report.flow", &catalog);
        },
        "DSL compile (weekly_sales_report.flow)",
    );
    assert!(median_us < 1_000_000.0, "DSL compile regression: too slow");

    let yaml_source = include_str!("../examples/yaml/weekly_sales_report.flow.yaml");
    time_repetitive(
        || {
            let _ = compile_source_named(yaml_source, "weekly_sales_report.flow.yaml", &catalog);
        },
        "YAML compile (weekly_sales_report.flow.yaml)",
    );

    // 表达式子系统：重复解析同一表达式（首次冷启动 + 后续缓存命中）。
    expression::clear_parse_caches();
    time_repetitive(
        || {
            let _ = expression::parse_expression_string(
                "len(vars.dir) > 0 && vars.n == 1 ? vars.dir : \"x\"",
                "bench",
                "bench.expr",
                0,
            );
        },
        "Expression parse (cold + warm cache mix)",
    );
    let (entries, capacity) = expression::expression_cache_stats();
    let (_, value_capacity) = expression::value_cache_stats();
    println!("cache state: expression {entries}/{capacity}, value capacity {value_capacity}");
    expression::clear_parse_caches();
    let (after, _) = expression::expression_cache_stats();
    assert_eq!(after, 0, "clear_parse_caches must reset entries");
}
