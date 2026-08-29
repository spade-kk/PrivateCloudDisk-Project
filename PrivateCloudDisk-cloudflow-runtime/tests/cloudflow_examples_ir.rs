//! 示例 IR（`examples/ir/`）回归测试（需求 7.21/9.8）：
//! 每个样例必须通过 IR 契约校验，并能在开发调试入口（纯内存 Mock）完整执行成功。
//! 与 `tests/fixtures/ir/`（测试资产，含反例）分离。

use cloudflow_runtime::{
    dev_execute_sync, ir_validate::validate_ir_contracts, DevConfig, MockActionExecutor,
    WorkflowIrV1,
};
use serde_json::{json, Value};
use std::path::Path;

fn examples_dir() -> std::path::PathBuf {
    Path::new("examples/ir").to_path_buf()
}

fn ir_files() -> Vec<std::path::PathBuf> {
    let mut files: Vec<std::path::PathBuf> = std::fs::read_dir(examples_dir())
        .expect("examples/ir 目录存在")
        .map(|entry| entry.expect("目录项可读").path())
        .filter(|path| path.extension().is_some_and(|ext| ext == "json"))
        .collect();
    files.sort();
    files
}

#[test]
fn example_ir_files_exist_and_are_well_formed() {
    let files = ir_files();
    assert!(
        files.len() >= 3,
        "examples/ir 至少应有 3 个样例，实际 {len}",
        len = files.len()
    );
}

#[test]
fn all_example_ir_pass_contracts_and_execute_in_memory() {
    let executor = std::sync::Arc::new(MockActionExecutor::new());
    for path in ir_files() {
        let raw = std::fs::read_to_string(&path).expect("样例可读");
        let ir: WorkflowIrV1 =
            serde_json::from_str(&raw).expect(&format!("{} 可反序列化", path.display()));
        let issues = validate_ir_contracts(&ir);
        assert!(
            issues.is_empty(),
            "{} 应通过 IR 契约校验：{issues:?}",
            path.display()
        );
        // 变量提供：按 IR 声明的 input 变量给占位值（表达式求值可解析）。
        let mut vars = serde_json::Map::new();
        for (key, variable) in &ir.spec.variables {
            if variable.source == "input" {
                let placeholder = match variable.type_name.as_str() {
                    "array" => json!(["sample.xlsx"]),
                    "object" => json!({"sample": true}),
                    "number" | "integer" => json!(1),
                    "boolean" => json!(true),
                    _ => json!("sample"),
                };
                vars.insert(key.clone(), placeholder);
            }
        }
        let result = dev_execute_sync(
            &ir,
            Value::Object(vars),
            &DevConfig::default(),
            executor.clone(),
        )
        .expect(&format!("{} 应可在内存调试入口执行", path.display()));
        assert_eq!(
            result.status,
            cloudflow_runtime::DevWorkflowStatus::Success,
            "{} 执行应成功：{:?}",
            path.display(),
            result.errors
        );
    }
}
