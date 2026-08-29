use cloudflow_runtime::{
    compile_source_named, runtime::RuntimeEngine, semantic::InMemoryCapabilityCatalog,
};

#[test]
fn demo_file_is_compilable_and_runtime_builds_dag() {
    let source = include_str!("../examples/weekly_sales_report.flow");
    let ir = compile_source_named(
        source,
        "weekly_sales_report.flow",
        &InMemoryCapabilityCatalog::default(),
    )
    .expect("Demo CloudFlow must compile");
    let runtime = RuntimeEngine::load("contract-demo", ir).expect("IR must be loadable");
    // [CLOUDFLOW-COVERAGE-001] 示例可在主链后追加控制流；契约仅固定核心步骤的
    // 依赖顺序，不能把测试写成拒绝合法扩展节点的白名单。
    let order = runtime.topological_order().expect("DAG");
    for (before, after) in [
        ("collect_files", "aggregate_data"),
        ("aggregate_data", "generate_report"),
        ("generate_report", "save_report"),
    ] {
        assert!(
            order.iter().position(|value| value == before)
                < order.iter().position(|value| value == after),
            "核心依赖顺序必须保持 {before} -> {after}: {order:?}"
        );
    }
}

#[test]
fn invalid_source_returns_error_design_code() {
    let error = compile_source_named(
        "workflow \"broken\" { step a { action file.list {} depends_on missing } }",
        "broken.flow",
        &InMemoryCapabilityCatalog::default(),
    )
    .expect_err("invalid dependency");
    assert!(error
        .diagnostics
        .iter()
        .any(|diagnostic| diagnostic.code == "CF2002"));
    assert!(error
        .diagnostics
        .iter()
        .all(|diagnostic| diagnostic.location.file == "broken.flow"));
}
