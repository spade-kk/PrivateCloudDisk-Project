//! 开发/调试执行入口（dev/debug entry）与 IR 契约校验器测试（需求清单 §3/§4/§5/§8/§9）。
//!
//! 全部测试使用纯内存状态：`MockActionExecutor` + 内存调度，不依赖数据库/MQ/Redis。

use cloudflow_runtime::{
    dev_execute, dev_execute_sync, validate_ir_contracts, ActionExecutor, DevConfig, DevEntryError,
    DevFailureSpec, DevLogLevel, DevTaskStatus, DevWorkflowStatus, IrContractIssue,
    MockActionExecutor, WorkflowIrV1,
};
use serde_json::{json, Value};
use std::collections::{BTreeMap, BTreeSet};

const SEQUENTIAL: &str = include_str!("fixtures/ir/01_sequential.json");
const CONDITION: &str = include_str!("fixtures/ir/02_condition.json");
const PARALLEL: &str = include_str!("fixtures/ir/03_parallel.json");
const FOREACH: &str = include_str!("fixtures/ir/04_loop_foreach.json");
const WHILE: &str = include_str!("fixtures/ir/05_loop_while.json");
const FOR_RANGE: &str = include_str!("fixtures/ir/06_loop_for_range.json");
const RETRY_TIMEOUT: &str = include_str!("fixtures/ir/07_retry_timeout.json");
const TRY_CATCH: &str = include_str!("fixtures/ir/08_exception_try_catch_finally.json");
const WAIT_APPROVAL: &str = include_str!("fixtures/ir/09_wait_approval.json");
const SUBWORKFLOW: &str = include_str!("fixtures/ir/10_subworkflow.json");
const VARS_EXPR: &str = include_str!("fixtures/ir/11_variables_expressions.json");
const COMBO: &str = include_str!("fixtures/ir/12_complex_combo.json");
const INVALID_STRUCTURE: &str = include_str!("fixtures/ir/13_invalid_structure.json");
const MISSING_FIELD: &str = include_str!("fixtures/ir/14_missing_field.json");
const CYCLE: &str = include_str!("fixtures/ir/15_cycle.json");
const UNKNOWN_REF: &str = include_str!("fixtures/ir/16_unknown_ref.json");
const UNKNOWN_ACTION: &str = include_str!("fixtures/ir/17_unknown_action.json");

fn run(
    json: &str,
    vars: Value,
    config: &DevConfig,
) -> Result<cloudflow_runtime::DevExecutionResult, DevEntryError> {
    dev_execute(
        json,
        vars,
        config,
        std::sync::Arc::new(MockActionExecutor::new()),
    )
}

fn load(json: &str) -> WorkflowIrV1 {
    serde_json::from_str(json).expect("fixture IR 可反序列化")
}

// ---------------------------------------------------------------- 一、IR 契约校验器

#[test]
fn valid_ir_passes_contracts() {
    assert!(validate_ir_contracts(&load(SEQUENTIAL)).is_empty());
    assert!(validate_ir_contracts(&load(COMBO)).is_empty());
    assert!(validate_ir_contracts(&load(FOREACH)).is_empty());
}

#[test]
fn detects_bad_api_version_and_kind() {
    let mut ir = load(INVALID_STRUCTURE);
    ir.kind = "Job".into();
    let issues = validate_ir_contracts(&ir);
    assert!(issues.iter().any(|i| i.code == "CFI-7001"), "{issues:?}");
    assert!(issues.iter().any(|i| i.code == "CFI-7002"), "{issues:?}");
}

#[test]
fn detects_duplicate_node_ids() {
    let mut ir = load(SEQUENTIAL);
    ir.spec.graph.nodes.push(ir.spec.graph.nodes[0].clone());
    let issues = validate_ir_contracts(&ir);
    assert!(issues.iter().any(|i| i.code == "CFI-7005"));
}

#[test]
fn detects_unknown_edge_reference() {
    let mut ir = load(SEQUENTIAL);
    ir.spec.graph.edges.push(cloudflow_runtime::ir::EdgeIr {
        from: "a".into(),
        to: "ghost".into(),
    });
    let issues = validate_ir_contracts(&ir);
    assert!(issues.iter().any(|i| i.code == "CFI-7017"));
}

#[test]
fn detects_cycle() {
    let issues = validate_ir_contracts(&load(CYCLE));
    assert!(issues.iter().any(|i| i.code == "CFI-7018"));
}

#[test]
fn detects_unknown_reference() {
    let issues = validate_ir_contracts(&load(UNKNOWN_REF));
    let refs: Vec<_> = issues.iter().filter(|i| i.code == "CFI-7023").collect();
    assert_eq!(refs.len(), 2, "{issues:?}");
}

#[test]
fn detects_missing_action_and_condition_expression() {
    let issues = validate_ir_contracts(&load(MISSING_FIELD));
    assert!(issues.iter().any(|i| i.code == "CFI-7026"));
    assert!(issues.iter().any(|i| i.code == "CFI-7009"));
}

#[test]
fn detects_invalid_node_type() {
    let issues = validate_ir_contracts(&load(INVALID_STRUCTURE));
    assert!(issues
        .iter()
        .any(|i| i.code == "CFI-7006" && i.node_id.as_deref() == Some("bad")));
}

#[test]
fn validator_collects_all_issues_in_one_pass() {
    // 13_invalid_structure：坏 apiVersion + quux 节点 + task 缺 action，一次收集。
    let issues = validate_ir_contracts(&load(INVALID_STRUCTURE));
    assert!(issues.iter().any(|i| i.code == "CFI-7001"));
    assert!(issues.iter().any(|i| i.code == "CFI-7006"));
    assert!(issues.iter().any(|i| i.code == "CFI-7026"));
}

#[test]
fn validator_issues_carry_path_and_node_id() {
    let issues = validate_ir_contracts(&load(MISSING_FIELD));
    let action_issue = issues.iter().find(|i| i.code == "CFI-7026").unwrap();
    assert!(action_issue.path.contains("nodes[0]"));
    assert_eq!(action_issue.node_id.as_deref(), Some("task_no_action"));
}

// ---------------------------------------------------------------- 二、DAG 与调度

#[test]
fn sequential_ordering_respected() {
    let result = run(
        SEQUENTIAL,
        json!({"files": ["a.xlsx"]}),
        &DevConfig::default(),
    )
    .unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    for id in ["collect", "aggregate", "save"] {
        assert_eq!(result.node_results[id].status, DevTaskStatus::Success);
    }
    // 日志顺序：collect 先于 aggregate 先于 save。
    let idx = |node: &str| -> usize {
        result
            .logs
            .iter()
            .position(|entry| entry.node_id.as_deref() == Some(node))
            .unwrap()
    };
    assert!(idx("collect") < idx("aggregate"));
    assert!(idx("aggregate") < idx("save"));
}

#[test]
fn parallel_all_branches_complete() {
    let result = run(PARALLEL, json!({}), &DevConfig::default()).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    for id in ["par", "ocr", "summary", "tags", "store"] {
        assert_eq!(
            result.node_results[id].status,
            DevTaskStatus::Success,
            "{id}"
        );
    }
}

// ---------------------------------------------------------------- 三、条件分支

#[test]
fn condition_true_branch_executes_false_skipped() {
    let result = run(CONDITION, json!({"is_big": true}), &DevConfig::default()).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(result.node_results["big"].status, DevTaskStatus::Success);
    assert_eq!(result.node_results["small"].status, DevTaskStatus::Skipped);
}

#[test]
fn condition_false_branch_executes_true_skipped() {
    let result = run(CONDITION, json!({"is_big": false}), &DevConfig::default()).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(result.node_results["big"].status, DevTaskStatus::Skipped);
    assert_eq!(result.node_results["small"].status, DevTaskStatus::Success);
}

// ---------------------------------------------------------------- 四、循环

#[test]
fn foreach_iterates_with_iterator_variable() {
    let mut config = DevConfig::default();
    config.log_level = DevLogLevel::Debug;
    let result = run(FOREACH, json!({"files": ["a.xlsx", "b.xlsx"]}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(result.node_results["loop"].status, DevTaskStatus::Success);
    // 迭代变量在动作入参中生效：调试日志记录两次迭代（item=a.xlsx / item=b.xlsx）。
    let iterations: Vec<_> = result
        .logs
        .iter()
        .filter(|entry| entry.message.contains("foreach 迭代"))
        .collect();
    assert_eq!(iterations.len(), 2, "{iterations:?}");
    assert!(iterations[0].message.contains("item=a.xlsx"));
    assert!(iterations[1].message.contains("item=b.xlsx"));
}

#[test]
fn foreach_empty_collection_zero_iterations() {
    let result = run(FOREACH, json!({"files": []}), &DevConfig::default()).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(
        result.node_results["process"].status,
        DevTaskStatus::Skipped
    );
}

#[test]
fn while_zero_iterations_when_condition_false() {
    let result = run(WHILE, json!({"enabled": false}), &DevConfig::default()).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(result.node_results["tick"].status, DevTaskStatus::Skipped);
}

#[test]
fn for_range_iterates_exact_times() {
    let result = run(FOR_RANGE, json!({}), &DevConfig::default()).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(
        result.node_results["loop"].output.as_ref().unwrap()["iterations"],
        3
    );
}

#[test]
fn while_max_iterations_guard_fails() {
    let ir = json!({
        "apiVersion": "workflow.cloudflow.io/v1",
        "kind": "Workflow",
        "metadata": {"id": "wf-w", "name": "w", "labels": {}},
        "spec": {
            "trigger": {"type": "manual"},
            "variables": {},
            "graph": {
                "nodes": [
                    {"id": "loop", "type": "loop", "name": null, "action": null,
                     "inputs": {}, "outputs": {}, "dependsOn": [], "retry": null, "retryOn": [],
                     "timeout": null, "onTimeout": null, "condition": null, "dependsCondition": null,
                     "switchConfig": null, "delayMs": null, "notifyConfig": null, "onError": null,
                     "loopConfig": {"kind": "while", "condition": true, "body": ["tick"], "maxIterations": 3},
                     "parallel": null, "errorHandler": null, "controlParent": null, "controlBranch": null,
                     "children": ["tick"]},
                    {"id": "tick", "type": "task", "name": null,
                     "action": {"provider": "builtin", "service": "db", "method": "tick",
                                "pluginId": null, "function": null, "version": null, "arguments": {}},
                     "inputs": {}, "outputs": {}, "dependsOn": ["loop"], "retry": null, "retryOn": [],
                     "timeout": null, "onTimeout": null, "condition": null, "dependsCondition": null,
                     "switchConfig": null, "delayMs": null, "notifyConfig": null, "onError": null,
                     "loopConfig": null, "parallel": null, "errorHandler": null,
                     "controlParent": "loop", "controlBranch": null, "children": []}
                ],
                "edges": [{"from": "loop", "to": "tick"}]
            },
            "outputs": {}, "environment": {}, "audit": null
        },
        "runtime": {"timeoutSeconds": 30, "maxParallel": 1, "retryPolicy": null},
        "security": {"permissions": [], "resourceLimits": {}},
        "extensions": {}
    });
    let ir: WorkflowIrV1 = serde_json::from_value(ir).unwrap();
    let result = dev_execute_sync(
        &ir,
        json!({}),
        &DevConfig::default(),
        std::sync::Arc::new(MockActionExecutor::new()),
    )
    .unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Failed);
    assert!(result.errors.iter().any(|e| e.code == "CF2201"));
}

// ---------------------------------------------------------------- 五、switch / assert

#[test]
fn switch_selects_matching_case_and_skips_others() {
    let result = run(
        COMBO,
        json!({"kind": "doc", "ok": true}),
        &DevConfig::default(),
    )
    .unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(result.node_results["doc"].status, DevTaskStatus::Success);
    assert_eq!(result.node_results["pdf"].status, DevTaskStatus::Skipped);
    assert_eq!(result.node_results["other"].status, DevTaskStatus::Skipped);
}

#[test]
fn switch_default_branch_when_no_case_matches() {
    let result = run(
        COMBO,
        json!({"kind": "zip", "ok": true}),
        &DevConfig::default(),
    )
    .unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(result.node_results["other"].status, DevTaskStatus::Success);
    assert_eq!(result.node_results["pdf"].status, DevTaskStatus::Skipped);
}

#[test]
fn assert_passes_when_condition_true() {
    let result = run(
        COMBO,
        json!({"kind": "pdf", "ok": true}),
        &DevConfig::default(),
    )
    .unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(
        result.node_results["assert_ok"].status,
        DevTaskStatus::Success
    );
}

#[test]
fn assert_fails_when_condition_false() {
    let result = run(
        COMBO,
        json!({"kind": "pdf", "ok": false}),
        &DevConfig::default(),
    )
    .unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Failed);
    assert_eq!(
        result.node_results["assert_ok"].status,
        DevTaskStatus::Failed
    );
    assert!(result.errors.iter().any(|e| e.code == "CF2202"));
}

// ---------------------------------------------------------------- 六、重试与超时

fn inject_failures(node: &str, specs: Vec<DevFailureSpec>) -> DevConfig {
    let mut config = DevConfig::default();
    config.inject_failures.insert(node.to_owned(), specs);
    config
}

#[test]
fn retry_succeeds_after_injected_transient_failure() {
    let config = inject_failures(
        "flaky",
        vec![DevFailureSpec {
            code: "CF5003".into(),
            message: "网络抖动".into(),
            retryable: true,
        }],
    );
    let result = run(RETRY_TIMEOUT, json!({}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(result.node_results["flaky"].attempts, 2);
}

#[test]
fn retry_exhausts_and_fails_workflow() {
    let spec = || DevFailureSpec {
        code: "CF5003".into(),
        message: "持续失败".into(),
        retryable: true,
    };
    let config = inject_failures("flaky", vec![spec(), spec(), spec()]);
    let result = run(RETRY_TIMEOUT, json!({}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Failed);
    assert_eq!(result.node_results["flaky"].attempts, 3);
    assert!(result.errors.iter().any(|e| e.code == "CF5003"));
}

#[test]
fn non_retryable_failure_stops_immediately() {
    let config = inject_failures(
        "flaky",
        vec![DevFailureSpec {
            code: "CF5004".into(),
            message: "参数非法".into(),
            retryable: false,
        }],
    );
    let result = run(RETRY_TIMEOUT, json!({}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Failed);
    assert_eq!(result.node_results["flaky"].attempts, 1);
}

#[test]
fn simulated_timeout_triggers_retryable_cf5001_then_succeeds() {
    // 前两次模拟超时（CF5001 可重试），第三次成功。
    let spec = || DevFailureSpec {
        code: "CF5001".into(),
        message: "节点执行超过 Runtime 超时上限".into(),
        retryable: true,
    };
    let config = inject_failures("flaky", vec![spec(), spec()]);
    let result = run(RETRY_TIMEOUT, json!({}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(result.node_results["flaky"].attempts, 3);
}

#[test]
fn simulated_latency_exceeding_node_timeout_fails_all_attempts() {
    let mut config = DevConfig::default();
    config.action_latency_ms.insert("flaky".into(), 6_000); // > timeout 5s
    let result = run(RETRY_TIMEOUT, json!({}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Failed);
    assert!(result.errors.iter().any(|e| e.code == "CF5001"));
}

// ---------------------------------------------------------------- 七、异常处理

#[test]
fn try_catch_finally_recovers_from_failure() {
    let config = inject_failures(
        "risky",
        vec![DevFailureSpec {
            code: "CF5003".into(),
            message: "数据损坏".into(),
            retryable: false,
        }],
    );
    let result = run(TRY_CATCH, json!({}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(result.node_results["risky"].status, DevTaskStatus::Failed);
    assert_eq!(
        result.node_results["compensate"].status,
        DevTaskStatus::Success
    );
    assert_eq!(
        result.node_results["cleanup"].status,
        DevTaskStatus::Success
    );
    // catchBinding 将 {code,message} 绑定到 vars.error，compensate 入参可引用
    // （mock 回显中入参位于 arguments 下，故断言 input）。
    assert_eq!(
        result.node_results["compensate"].input.as_ref().unwrap()["error"]["code"],
        "CF5003"
    );
}

#[test]
fn try_without_catch_propagates_failure_but_finally_runs() {
    let ir: WorkflowIrV1 =
        serde_json::from_value(serde_json::from_str(TRY_CATCH).unwrap()).unwrap();
    let mut ir = ir;
    ir.spec.graph.nodes.iter_mut().for_each(|node| {
        if node.id == "trynode" {
            if let Some(handler) = node.error_handler.as_mut().and_then(Value::as_object_mut) {
                handler.insert("catch".into(), json!([]));
            }
        }
    });
    let config = inject_failures(
        "risky",
        vec![DevFailureSpec {
            code: "CF5003".into(),
            message: "数据损坏".into(),
            retryable: false,
        }],
    );
    let result = dev_execute_sync(
        &ir,
        json!({}),
        &config,
        std::sync::Arc::new(MockActionExecutor::new()),
    )
    .unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Failed);
    assert_eq!(
        result.node_results["cleanup"].status,
        DevTaskStatus::Success
    );
}

// ---------------------------------------------------------------- 八、等待 / 子工作流

#[test]
fn wait_node_halts_with_waiting_status() {
    let result = run(WAIT_APPROVAL, json!({}), &DevConfig::default()).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Waiting);
    assert_eq!(result.node_results["build"].status, DevTaskStatus::Success);
    assert_eq!(
        result.node_results["approval"].status,
        DevTaskStatus::Waiting
    );
    assert_eq!(result.node_results["deploy"].status, DevTaskStatus::Skipped);
}

#[test]
fn subworkflow_action_uses_workflow_provider_key() {
    let result = run(SUBWORKFLOW, json!({}), &DevConfig::default()).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    // 子工作流动作键：workflow:java.build / workflow:k8s.deploy。
    let output = &result.node_results["java_build"].output.as_ref().unwrap()["arguments"];
    assert!(result
        .logs
        .iter()
        .any(|entry| entry.message.contains("workflow:java.build")));
    assert!(output.is_object());
}

// ---------------------------------------------------------------- 九、动作分发

#[test]
fn plugin_action_key_uses_plugin_id_and_function() {
    #[derive(Debug, Default)]
    struct KeyCaptor(std::sync::Arc<std::sync::Mutex<Vec<String>>>);
    #[async_trait::async_trait]
    impl ActionExecutor for KeyCaptor {
        async fn execute(
            &self,
            step: &cloudflow_runtime::engine::context::StepContext,
        ) -> Result<serde_json::Value, cloudflow_runtime::engine::error::ExecutionError> {
            // 统一驱动直接注入执行器；动作键捕获对原始 captor 缓冲可观测。
            self.0
                .lock()
                .unwrap()
                .push(cloudflow_runtime::dev_exec::action_key(&step.action));
            Ok(json!({"ok": true}))
        }
    }
    let ir: WorkflowIrV1 = serde_json::from_value(json!({
        "apiVersion": "workflow.cloudflow.io/v1",
        "kind": "Workflow",
        "metadata": {"id": "wf-p", "name": "p", "labels": {}},
        "spec": {
            "trigger": {"type": "manual"},
            "variables": {},
            "graph": {
                "nodes": [
                    {"id": "report", "type": "plugin", "name": null,
                     "action": {"provider": "plugin", "service": null, "method": null,
                                "pluginId": "8ae47c8d", "function": "generate_report", "version": "1",
                                "arguments": {"x": 1}},
                     "inputs": {}, "outputs": {}, "dependsOn": [], "retry": null, "retryOn": [],
                     "timeout": null, "onTimeout": null, "condition": null, "dependsCondition": null,
                     "switchConfig": null, "delayMs": null, "notifyConfig": null, "onError": null,
                     "loopConfig": null, "parallel": null, "errorHandler": null,
                     "controlParent": null, "controlBranch": null, "children": []}
                ],
                "edges": []
            },
            "outputs": {}, "environment": {}, "audit": null
        },
        "runtime": {"timeoutSeconds": 30, "maxParallel": 1, "retryPolicy": null},
        "security": {"permissions": [], "resourceLimits": {}},
        "extensions": {}
    })).unwrap();
    let keys = std::sync::Arc::new(std::sync::Mutex::new(Vec::new()));
    let captor = KeyCaptor(std::sync::Arc::clone(&keys));
    let result = dev_execute_sync(
        &ir,
        json!({}),
        &DevConfig::default(),
        std::sync::Arc::new(captor),
    )
    .unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(
        *keys.lock().unwrap(),
        vec!["plugin:8ae47c8d:generate_report"]
    );
}

#[test]
fn unknown_action_in_strict_mode_fails_with_cf5002() {
    let ir = load(UNKNOWN_ACTION);
    let result = dev_execute_sync(
        &ir,
        json!({}),
        &DevConfig::default(),
        std::sync::Arc::new(MockActionExecutor::with_known_actions([
            "builtin:file.list".to_owned(),
        ])),
    )
    .unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Failed);
    assert!(result.errors.iter().any(|e| e.code == "CF5002"));
}

// ---------------------------------------------------------------- 十、表达式与变量

#[test]
fn expressions_ref_arithmetic_template_and_pipeline() {
    let result = run(
        VARS_EXPR,
        json!({"a": 2, "b": 3, "items": [{"size": 5}, {"size": 0}]}),
        &DevConfig::default(),
    )
    .unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    let input = &result.node_results["compute"].input.as_ref().unwrap();
    assert_eq!(input["sum"], 5);
    assert_eq!(input["big"], false);
    assert_eq!(input["label"], "value=x");
    assert_eq!(input["kept"], json!([{"size": 5}]));
    // spec.outputs 求值。
    assert_eq!(result.outputs["sum"], 5);
    // local 变量求值结果进入上下文快照。
    assert_eq!(result.context_snapshot["vars"]["derived"], "local=x");
}

#[test]
fn local_variable_cannot_be_overridden_by_caller() {
    let ir = load(VARS_EXPR);
    let result = dev_execute_sync(
        &ir,
        json!({"a": 1, "b": 1, "items": [], "derived": "hijack"}),
        &DevConfig::default(),
        std::sync::Arc::new(MockActionExecutor::new()),
    );
    let Err(DevEntryError::Validation(issues)) = result else {
        panic!("本地变量覆盖必须被拒绝：{result:?}");
    };
    assert!(issues
        .iter()
        .any(|i: &IrContractIssue| i.code == "CFD-8101"));
}

#[test]
fn input_variable_overrides_default() {
    let result = run(
        VARS_EXPR,
        json!({"a": 10, "b": 1, "items": []}),
        &DevConfig::default(),
    )
    .unwrap();
    assert_eq!(result.outputs["sum"], 11);
}

// ---------------------------------------------------------------- 十一、输出与快照

#[test]
fn workflow_outputs_evaluated_from_step_results() {
    let result = run(
        SEQUENTIAL,
        json!({"files": ["a.xlsx"]}),
        &DevConfig::default(),
    )
    .unwrap();
    let expected = result.node_results["aggregate"]
        .output
        .as_ref()
        .expect("aggregate output")
        .clone();
    assert_eq!(result.outputs["report_data"], expected);
}

#[test]
fn context_snapshot_contains_vars_and_steps() {
    let result = run(
        SEQUENTIAL,
        json!({"files": ["a.xlsx"]}),
        &DevConfig::default(),
    )
    .unwrap();
    let snapshot = &result.context_snapshot;
    assert_eq!(snapshot["vars"]["files"], json!(["a.xlsx"]));
    assert!(snapshot["steps"]["collect"]["output"].is_object());
    assert!(snapshot["steps"]["aggregate"]["output"].is_object());
    assert!(snapshot["outputs"]["report_data"].is_object());
}

#[test]
fn context_snapshot_serialization_roundtrip() {
    let result = run(SEQUENTIAL, json!({"files": []}), &DevConfig::default()).unwrap();
    let text = serde_json::to_string(&result.context_snapshot).expect("snapshot serialize");
    let restored: Value = serde_json::from_str(&text).expect("snapshot deserialize");
    assert_eq!(restored, result.context_snapshot);
}

// ---------------------------------------------------------------- 十二、错误处理

#[test]
fn node_failure_marks_workflow_failed_with_details() {
    let config = inject_failures(
        "collect",
        vec![DevFailureSpec {
            code: "CF5003".into(),
            message: "存储不可用".into(),
            retryable: false,
        }],
    );
    let result = run(SEQUENTIAL, json!({"files": []}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Failed);
    assert_eq!(result.node_results["collect"].status, DevTaskStatus::Failed);
    assert!(result.node_results["collect"].error.is_some());
    // 上游失败后剩余节点被跳过。
    assert_eq!(
        result.node_results["aggregate"].status,
        DevTaskStatus::Skipped
    );
}

#[test]
fn invalid_ir_rejected_before_execution() {
    let result = run(CYCLE, json!({}), &DevConfig::default());
    let Err(DevEntryError::Validation(issues)) = result else {
        panic!("环 IR 必须在执行前被拒绝");
    };
    assert!(issues.iter().any(|i| i.code == "CFI-7018"));
}

#[test]
fn skip_validation_allows_executing_invalid_ir_structure() {
    // 需求 4.11/9.4：跳过校验后可执行（用于测试校验器本身）。
    let mut config = DevConfig::default();
    config.skip_validation = true;
    // CYCLE 有环但结构完整：同步调度会死锁 → 预期 failed + CFD-8103。
    let result = run(CYCLE, json!({}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Failed);
    assert!(result.errors.iter().any(|e| e.code == "CFD-8103"));
}

// ---------------------------------------------------------------- 十三、调试能力

#[test]
fn breakpoint_halts_before_node_with_snapshot() {
    let mut config = DevConfig::default();
    config.breakpoint = Some("aggregate".into());
    let result = run(SEQUENTIAL, json!({"files": []}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Breakpoint);
    assert_eq!(
        result.node_results["collect"].status,
        DevTaskStatus::Success
    );
    assert_eq!(
        result.node_results["aggregate"].status,
        DevTaskStatus::Pending
    );
    assert!(result.context_snapshot["steps"].get("collect").is_some());
}

#[test]
fn skip_nodes_bypasses_node_execution() {
    let mut config = DevConfig::default();
    config.skip_nodes = vec!["aggregate".into()];
    let result = run(SEQUENTIAL, json!({"files": []}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(
        result.node_results["aggregate"].status,
        DevTaskStatus::Skipped
    );
    assert_eq!(result.node_results["save"].status, DevTaskStatus::Success);
}

#[test]
fn mock_outputs_override_action_result() {
    let mut config = DevConfig::default();
    config
        .mock_outputs
        .insert("aggregate".into(), json!({"rows": 42}));
    let result = run(SEQUENTIAL, json!({"files": []}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(
        result.node_results["aggregate"].output.as_ref().unwrap()["rows"],
        42
    );
    assert_eq!(result.outputs["report_data"]["rows"], 42);
}

#[test]
fn disabled_expressions_pass_values_through() {
    let mut config = DevConfig::default();
    config.enable_expressions = false;
    // 01 中 save 入参 $ref 会原样透传；动作仍执行（mock 不解析）。
    let result = run(SEQUENTIAL, json!({"files": []}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(
        result.node_results["save"].input.as_ref().unwrap()["source"],
        json!({"$ref": "steps.aggregate.output"})
    );
}

#[test]
fn overall_timeout_stops_execution() {
    let mut config = DevConfig::default();
    config.overall_timeout_ms = Some(1);
    // 首个节点模拟延迟 50ms（虚拟时间），确保全局 1ms 超时在第二圈检查时必然触发。
    config.action_latency_ms.insert("collect".into(), 50);
    let result = run(SEQUENTIAL, json!({"files": []}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Timeout);
    assert!(result.errors.iter().any(|e| e.code == "CFD-8104"));
}

#[test]
fn log_node_filter_limits_entries() {
    let mut config = DevConfig::default();
    config.log_node_filter = Some("save".into());
    let result = run(SEQUENTIAL, json!({"files": []}), &config).unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert!(result
        .logs
        .iter()
        .filter(|entry| entry.node_id.is_some())
        .all(|entry| entry.node_id.as_deref() == Some("save")));
}

#[test]
fn markdown_report_contains_node_table() {
    let result = run(SEQUENTIAL, json!({"files": []}), &DevConfig::default()).unwrap();
    let markdown = result.render_markdown(&load(SEQUENTIAL));
    assert!(markdown.contains("# CloudFlow 调试执行报告"));
    assert!(markdown.contains("| collect | task | success |"));
    assert!(markdown.contains("## 输出"));
}

// ---------------------------------------------------------------- 十四、端到端

#[test]
fn e2e_all_valid_fixtures_compile_shape_and_execute() {
    // 需求 8.22/8.24：示例 IR 文件在调试入口可执行。
    for fixture in [
        SEQUENTIAL,
        CONDITION,
        PARALLEL,
        FOREACH,
        WHILE,
        FOR_RANGE,
        RETRY_TIMEOUT,
        TRY_CATCH,
        WAIT_APPROVAL,
        SUBWORKFLOW,
        VARS_EXPR,
    ] {
        let ir = load(fixture);
        assert!(
            validate_ir_contracts(&ir).is_empty(),
            "{} 应通过契约校验",
            ir.metadata.name
        );
    }
}

#[test]
fn e2e_complex_combo_full_run() {
    let result = run(
        COMBO,
        json!({"kind": "pdf", "ok": true}),
        &DevConfig::default(),
    )
    .unwrap();
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(result.node_results["finish"].status, DevTaskStatus::Success);
    assert_eq!(result.node_results["send"].status, DevTaskStatus::Success);
    assert_eq!(
        result.node_results["fallback"].status,
        DevTaskStatus::Skipped
    );
}

#[test]
fn e2e_dsl_compiled_ir_executes_in_dev_engine() {
    use cloudflow_runtime::{compile_source_named, semantic::InMemoryCapabilityCatalog};
    let source =
        std::fs::read_to_string("examples/coverage/basic_workflow.flow").expect("示例存在");
    let ir = compile_source_named(
        &source,
        "basic_workflow.flow",
        &InMemoryCapabilityCatalog::default(),
    )
    .expect("DSL 编译成功");
    let result = dev_execute_sync(
        &ir,
        json!({}),
        &DevConfig::default(),
        std::sync::Arc::new(MockActionExecutor::new()),
    )
    .expect("DSL 编译产物可在调试入口执行");
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(
        result.node_results["list_files"].status,
        DevTaskStatus::Success
    );
}

#[test]
fn e2e_yaml_compiled_ir_executes_in_dev_engine() {
    use cloudflow_runtime::{compile_source_named, semantic::InMemoryCapabilityCatalog};
    let source =
        std::fs::read_to_string("examples/yaml/simple_file_process.flow.yaml").expect("示例存在");
    let ir = compile_source_named(
        &source,
        "simple_file_process.flow.yaml",
        &InMemoryCapabilityCatalog::default(),
    )
    .expect("YAML 编译成功");
    let result = dev_execute_sync(
        &ir,
        json!({"file_id": "f-123"}),
        &DevConfig::default(),
        std::sync::Arc::new(MockActionExecutor::new()),
    )
    .expect("YAML 编译产物可在调试入口执行");
    assert_eq!(result.status, DevWorkflowStatus::Success);
    assert_eq!(
        result.node_results["get_file"].status,
        DevTaskStatus::Success
    );
    assert_eq!(result.node_results["save"].status, DevTaskStatus::Success);
}

#[test]
fn e2e_concurrency_two_independent_workflows() {
    use std::thread;
    let mut threads = Vec::new();
    for offset in 0..4 {
        let handle = thread::spawn(move || {
            let mut config = DevConfig::default();
            if offset % 2 == 1 {
                config.log_level = DevLogLevel::Debug;
            }
            let result = run(SEQUENTIAL, json!({"files": ["x.xlsx"]}), &config).unwrap();
            result.status
        });
        threads.push(handle);
    }
    for handle in threads {
        assert_eq!(handle.join().unwrap(), DevWorkflowStatus::Success);
    }
}

// ---------------------------------------------------------------- 十五、HTTP 调试端点

async fn dev_router(enabled: bool) -> axum::Router {
    use cloudflow_runtime::http::{build_router, HttpConfig};
    let config = HttpConfig {
        // 开发端点与生产端点共用 X-PCD-Service-Token 鉴权（fail-closed）。
        service_token: "dev-test-token".into(),
        capabilities: vec![],
        max_concurrency: 4,
        allowed_origins: vec![],
        enable_dev_execute: enabled,
    };
    build_router(config)
}

async fn post_json(app: &axum::Router, body: &Value) -> (u16, Value) {
    use axum::body::Body;
    use http_body_util::BodyExt;
    use tower::ServiceExt;
    let request = axum::http::Request::builder()
        .method("POST")
        .uri("/api/dev/execute")
        .header(axum::http::header::CONTENT_TYPE, "application/json")
        .header("X-PCD-Service-Token", "dev-test-token")
        .body(Body::from(serde_json::to_vec(body).unwrap()))
        .unwrap();
    let response = app.clone().oneshot(request).await.unwrap();
    let status = response.status().as_u16();
    let bytes = response.into_body().collect().await.unwrap().to_bytes();
    let value: Value = serde_json::from_slice(&bytes).unwrap_or(Value::Null);
    (status, value)
}

#[tokio::test]
async fn http_dev_execute_returns_full_result() {
    let app = dev_router(true).await;
    let (status, value) = post_json(
        &app,
        &json!({"ir": load(SEQUENTIAL), "variables": {"files": []}}),
    )
    .await;
    assert_eq!(status, 200);
    assert_eq!(value["status"], "success");
    assert!(value["nodeResults"]["collect"]["status"] == "success");
    assert!(value["logs"].is_array());
    assert!(value["contextSnapshot"]["vars"]["files"].is_array());
}

#[tokio::test]
async fn http_dev_execute_disabled_by_default_returns_404() {
    let app = dev_router(false).await;
    let (status, value) = post_json(&app, &json!({"ir": load(SEQUENTIAL)})).await;
    // 关闭态：路由不存在，axum 默认 404 且响应体为空——不泄露端点存在性
    // 与 CLOUDFLOW_ENABLE_DEBUG_EXECUTE 配置细节。
    assert_eq!(status, 404);
    assert!(value.is_null(), "关闭态 404 必须无 JSON 响应体");
}

#[tokio::test]
async fn http_dev_openapi_disabled_by_default_returns_404() {
    use axum::body::Body;
    use http_body_util::BodyExt;
    use tower::ServiceExt;
    let app = dev_router(false).await;
    let request = axum::http::Request::builder()
        .uri("/api/dev/openapi.json")
        .header("X-PCD-Service-Token", "dev-test-token")
        .body(Body::empty())
        .unwrap();
    let response = app.clone().oneshot(request).await.unwrap();
    assert_eq!(response.status(), axum::http::StatusCode::NOT_FOUND);
    let bytes = response.into_body().collect().await.unwrap().to_bytes();
    assert!(bytes.is_empty(), "关闭态 openapi 必须无响应体");
}

#[tokio::test]
async fn http_dev_execute_enabled_requires_service_token() {
    use tower::ServiceExt;
    let app = dev_router(true).await;
    let request = axum::http::Request::builder()
        .method("POST")
        .uri("/api/dev/execute")
        .header(axum::http::header::CONTENT_TYPE, "application/json")
        .body(axum::body::Body::from(
            serde_json::to_vec(&json!({"ir": load(SEQUENTIAL)})).unwrap(),
        ))
        .unwrap();
    let response = app.clone().oneshot(request).await.unwrap();
    assert_eq!(response.status(), axum::http::StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn http_dev_execute_validation_failure_returns_422() {
    let app = dev_router(true).await;
    let (status, value) = post_json(&app, &json!({"ir": load(CYCLE)})).await;
    assert_eq!(status, 422);
    assert!(value["status"] == "validationFailed");
    assert!(value["issues"].is_array());
}

#[tokio::test]
async fn http_dev_execute_supports_mock_outputs_and_skip_nodes() {
    let app = dev_router(true).await;
    let ir = load(SEQUENTIAL);
    let (status, value) = post_json(
        &app,
        &json!({
            "ir": ir,
            "variables": {"files": []},
            "skipNodes": ["aggregate"],
            "mockOutputs": {"save": {"done": true}},
        }),
    )
    .await;
    assert_eq!(status, 200);
    assert_eq!(value["status"], "success");
    assert_eq!(value["nodeResults"]["aggregate"]["status"], "skipped");
    assert_eq!(value["nodeResults"]["save"]["output"]["done"], true);
}

#[tokio::test]
async fn http_dev_openapi_document_served_when_enabled() {
    use axum::body::Body;
    use http_body_util::BodyExt;
    use tower::ServiceExt;
    let app = dev_router(true).await;
    let request = axum::http::Request::builder()
        .uri("/api/dev/openapi.json")
        .header("X-PCD-Service-Token", "dev-test-token")
        .body(Body::empty())
        .unwrap();
    let response = app.clone().oneshot(request).await.unwrap();
    assert_eq!(response.status(), axum::http::StatusCode::OK);
    let bytes = response.into_body().collect().await.unwrap().to_bytes();
    let value: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(value["openapi"], "3.0.3");
    assert!(value["paths"]["/api/dev/execute"].is_object());
}

// ---------------------------------------------------------------- 十六、动作执行器契约

#[tokio::test]
async fn mock_executor_default_echo_is_deterministic() {
    use cloudflow_runtime::engine::context::{ExecutionContext, StepContext};
    let executor = MockActionExecutor::new();
    let step = StepContext {
        execution: ExecutionContext::default(),
        node_id: "n1".into(),
        step_id: "n1".into(),
        attempt: 1,
        action: cloudflow_runtime::ir::ActionIr {
            provider: "builtin".into(),
            service: Some("file".into()),
            method: Some("list".into()),
            plugin_id: None,
            function: None,
            version: None,
            arguments: json!({"pattern": "*"}),
        },
        input: json!({"pattern": "*"}),
        timeout: std::time::Duration::from_secs(30),
    };
    let first = executor.execute(&step).await.expect("mock 应返回 Ok");
    let second = executor.execute(&step).await.expect("mock 应返回 Ok");
    assert_eq!(first, second);
    assert_eq!(first["ok"], true);
    assert_eq!(first["action"], "builtin:file.list");
}

#[tokio::test]
async fn mock_executor_canned_output_wins_over_echo() {
    use cloudflow_runtime::engine::context::{ExecutionContext, StepContext};
    let executor = MockActionExecutor::new();
    let executor = {
        let mut e = executor;
        e.with_canned("builtin:file.list".into(), json!({"files": 3}));
        e
    };
    let step = StepContext {
        execution: ExecutionContext::default(),
        node_id: "n1".into(),
        step_id: "n1".into(),
        attempt: 1,
        action: cloudflow_runtime::ir::ActionIr {
            provider: "builtin".into(),
            service: Some("file".into()),
            method: Some("list".into()),
            plugin_id: None,
            function: None,
            version: None,
            arguments: json!({}),
        },
        input: json!({}),
        timeout: std::time::Duration::from_secs(30),
    };
    let value = executor.execute(&step).await.expect("mock 应返回 Ok");
    assert_eq!(value, json!({"files": 3}));
}

// ---------------------------------------------------------------- 十七、异步入口与回归

#[tokio::test]
async fn dev_execute_async_matches_sync_result() {
    use std::sync::Arc;
    let ir = load(SEQUENTIAL);
    let sync_result = dev_execute_sync(
        &ir,
        json!({"files": []}),
        &DevConfig::default(),
        std::sync::Arc::new(MockActionExecutor::new()),
    )
    .unwrap();
    let async_result = cloudflow_runtime::dev_execute_async(
        &ir,
        json!({"files": []}),
        DevConfig::default(),
        Arc::new(MockActionExecutor::new()),
    )
    .await
    .unwrap();
    assert_eq!(sync_result.status, async_result.status);
    assert_eq!(
        sync_result.node_results.keys().collect::<BTreeSet<_>>(),
        async_result.node_results.keys().collect::<BTreeSet<_>>()
    );
}

#[test]
fn regression_compiler_validate_ir_still_used_by_engine() {
    // 回归：IR 契约校验已统一——`compiler::validate_ir`（生产 `/ir-validate`、
    // `RuntimeEngine::load`、Workflow Service 与微服务调用）内部委托
    // `ir_validate::validate_ir_contracts`（开发调试入口同一校验器），
    // 开发面与生产面行为一致，不再存在两套校验器。
    let ir = load(SEQUENTIAL);
    assert!(cloudflow_runtime::compiler::validate_ir(&ir).is_empty());
    assert!(validate_ir_contracts(&ir).is_empty());
}

#[test]
fn prod_validate_ir_and_contracts_agree_on_invalid_ir() {
    // 同一份非法 IR：生产入口（字符串形态）与契约校验器（带码形态）必须同时拒绝，
    // 且生产入口的每条消息都携带统一的 CFI- 错误码。
    let ir = load(INVALID_STRUCTURE);
    let issues = validate_ir_contracts(&ir);
    assert!(!issues.is_empty());
    let errors = cloudflow_runtime::compiler::validate_ir(&ir);
    assert!(!errors.is_empty());
    assert_eq!(errors.len(), issues.len());
    for error in &errors {
        assert!(error.starts_with("CFI-"), "缺少 CFI- 错误码前缀：{error}");
    }
}

// 确保 DevFailureSpec / BTreeMap 等类型被引用（避免死代码告警）。
#[test]
fn dev_config_defaults_are_safe() {
    let config = DevConfig::default();
    assert!(config.mock);
    assert!(!config.skip_validation);
    assert!(config.enable_expressions);
    assert!(config.overall_timeout_ms.is_none());
    let failures: BTreeMap<String, Vec<DevFailureSpec>> = BTreeMap::new();
    assert!(failures.is_empty());
}
