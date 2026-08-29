//! CloudFlow YAML 前端测试（需求 15.x / 29.x / 31.x）。
//!
//! 覆盖：语言识别、YAML → Domain AST、表达式委托与引用规范化、触发器/重试/超时/控制流映射、
//! 顶层 catch/finally、outputs、错误诊断（CFY-*）、DSL↔YAML IR 等价、示例编译循环；
//! 以及 GitHub-Actions 风格能力引用（`action: plugin:<id>:<fn>@<v>`、`${{ }}` 统一表达式插值）。
//! CloudFlow YAML 只定义 `${{ }}` 表达式分隔符；不接受旧版 `automation.pcd/v1` 包装。

use cloudflow_runtime::ast::{FlowNode, TriggerNode};
use cloudflow_runtime::ir::TriggerIr;
use cloudflow_runtime::semantic::InMemoryCapabilityCatalog;
use cloudflow_runtime::yaml::{parse_yaml, parse_yaml_detailed};
use cloudflow_runtime::{
    compile_source_named, compile_source_named_for_language, language_of, parse_ast_for_language,
    Language,
};

fn catalog() -> InMemoryCapabilityCatalog {
    InMemoryCapabilityCatalog::default()
}

fn trigger_type(trigger: &TriggerIr) -> &'static str {
    match trigger {
        TriggerIr::Manual => "manual",
        TriggerIr::Schedule { .. } => "schedule",
        TriggerIr::Event { .. } => "event",
        TriggerIr::Http { .. } => "http",
        TriggerIr::Interval { .. } => "interval",
    }
}

#[test]
fn language_detection_by_extension() {
    // 需求 13.3：.flow → DSL；.yaml/.yml/.flow.yaml/.workflow.yaml → YAML。
    assert_eq!(language_of("a.flow"), Language::Dsl);
    assert_eq!(language_of("a.flow.yaml"), Language::Yaml);
    assert_eq!(language_of("a.workflow.yaml"), Language::Yaml);
    assert_eq!(language_of("a.yaml"), Language::Yaml);
    assert_eq!(language_of("a.YML"), Language::Yaml);
    assert_eq!(language_of("<inline>"), Language::Dsl);
}

#[test]
fn yaml_null_maps_to_value_node_null() {
    // 需求 6.3：YAML `null`（serde_yaml_ng 的 YamlValue::Null）映射到 ValueNode::Null，
    // 而非旧版 Enum("null") 兜底。
    let source = r#"
trigger: { type: manual }
steps:
  - id: probe
    action: file.get
    input: { maybe: null }
"#;
    let workflow = parse_yaml(source, "null.workflow.yaml").expect("yaml parse");
    let probe = workflow
        .steps
        .iter()
        .find(|step| step.id == "probe")
        .expect("probe step");
    let arg = &probe.action.as_ref().expect("action").arguments;
    assert_eq!(
        arg.get("maybe"),
        Some(&cloudflow_runtime::ast::ValueNode::Null)
    );
}

#[test]
fn yaml_parse_produces_domain_ast() {
    // 需求 8.6/8.9：workflow 名、触发器、输入变量都落到共享 Domain AST。
    let source = r#"
workflow: { name: demo, version: 1.0 }
trigger: { event: file.created }
input: { file_id: { type: string, required: true } }
variables: { bucket: user-file }
steps:
  - id: get
    action: file.get
    input: { id: "${{ input.file_id }}" }
"#;
    let workflow = parse_yaml(source, "demo.workflow.yaml").expect("yaml parse");
    assert_eq!(workflow.name, "demo");
    assert!(matches!(
        workflow.trigger,
        TriggerNode::Event { ref name } if name == "file.created"
    ));
    let input_variable = workflow
        .variables
        .iter()
        .find(|variable| variable.name == "file_id")
        .expect("file_id");
    assert_eq!(
        input_variable.source,
        cloudflow_runtime::ast::VariableSource::Input
    );
    assert_eq!(input_variable.type_name, "string");
    assert!(input_variable.required);
    assert!(workflow
        .variables
        .iter()
        .any(|variable| variable.name == "bucket"));
    assert_eq!(workflow.steps[0].id, "get");
}

#[test]
fn trigger_kinds_map_to_ast() {
    // 需求 22.x：event / cron / webhook(http) / interval / manual。
    let base = "workflow: { name: triggers }\nsteps:\n  - id: a\n    action: file.get\n";
    let cases = [
        (
            "trigger: { cron: \"0 2 * * *\" }\n",
            TriggerNode::Schedule {
                cron: "0 2 * * *".into(),
                timezone: None,
            },
        ),
        (
            "trigger: { webhook: { path: /deploy, method: POST } }\n",
            TriggerNode::Http {
                path: "/deploy".into(),
                method: Some("POST".into()),
            },
        ),
        (
            "trigger: { interval: 5m }\n",
            TriggerNode::Interval {
                raw: "5m".into(),
                milliseconds: 300_000,
            },
        ),
        ("trigger: manual\n", TriggerNode::Manual),
    ];
    for (index, (trigger, expected)) in cases.into_iter().enumerate() {
        let text = format!("{trigger}{base}");
        let workflow = parse_yaml(&text, "t.yaml")
            .unwrap_or_else(|err| panic!("case {index}: {}", err.message));
        assert_eq!(workflow.trigger, expected, "trigger case {index}");
    }
}

#[test]
fn expression_delegation_and_reference_normalization() {
    // 需求 6.31/28.53/28.59：${{ ... }} 整串交给表达式子系统；input.x → vars.x；
    // steps.<id>.<f> → steps.<id>.output.<f>。
    let source = r#"
workflow: { name: refs }
input: { file_id: string }
steps:
  - id: parse
    action: file.parse
    output: result
  - id: use_ref
    action: file.push
    input:
      id: ${{ input.file_id }}
      whole: ${{ steps.parse.output }}
      short: ${{ steps.parse.result }}
"#;
    let workflow = parse_yaml(source, "refs.workflow.yaml").expect("parse");
    let use_ref = workflow
        .steps
        .iter()
        .find(|step| step.id == "use_ref")
        .expect("step");
    let args = use_ref.action.as_ref().expect("action").arguments.clone();
    fn variable_ref(value: &cloudflow_runtime::ast::ValueNode) -> &str {
        match value {
            cloudflow_runtime::ast::ValueNode::VariableRef(name) => name.as_str(),
            _ => "",
        }
    }
    assert_eq!(variable_ref(args.get("id").unwrap()), "vars.file_id");
    assert_eq!(
        variable_ref(args.get("whole").unwrap()),
        "steps.parse.output"
    );
    assert_eq!(
        variable_ref(args.get("short").unwrap()),
        "steps.parse.output.result"
    );
}

#[test]
fn retry_timeout_and_on_error_map() {
    // 需求 14.x：retry/timeout/on_error。
    let source = r#"
workflow: { name: rt }
steps:
  - id: call
    action: ai.chat
    retry: { count: 3, strategy: exponential, interval: 5s }
    timeout: { duration: 30s, on_timeout: fail }
    on_error: { retry: 2, fallback: notify.admin }
"#;
    let workflow = parse_yaml(source, "rt.workflow.yaml").expect("parse");
    let step = &workflow.steps[0];
    let retry = step.retry.as_ref().expect("retry");
    assert_eq!(retry.max_attempts, 3);
    assert_eq!(retry.strategy, "exponential");
    assert_eq!(step.timeout.as_ref().map(|t| t.raw.as_str()), Some("30s"));
    assert_eq!(step.on_timeout.as_deref(), Some("fail"));
    assert!(matches!(
        step.on_error.first(),
        Some(FlowNode::Step(fallback)) if fallback.action.as_ref().map(|a| a.method.as_deref()) == Some(Some("admin"))
    ));
}

#[test]
fn parallel_foreach_switch_approval_map() {
    // 需求 8.10/4.8/4.16/14.x：parallel、foreach、switch、approval。
    let source = r#"
workflow: { name: controls }
input: { files: array }
steps:
  - id: analyze
    parallel:
      - id: ocr
        action: ai.ocr
      - id: tag
        action: ai.tag
  - id: loop
    foreach:
      item: ${{ input.files }}
      do:
        action: file.process
  - id: pick
    switch:
      expression: ${{ input.files != "" }}
      cases:
        pdf:
          action: parser.pdf
      default:
        action: parser.default
  - id: aprv
    approval: { users: [admin] }
  - id: done
    action: metadata.save
    depends: [analyze, loop, pick, aprv]
"#;
    let workflow = parse_yaml(source, "controls.workflow.yaml").expect("parse");
    assert!(workflow
        .controls
        .iter()
        .any(|node| matches!(node, FlowNode::Parallel(_))));
    assert!(workflow
        .controls
        .iter()
        .any(|node| matches!(node, FlowNode::Loop(_))));
    assert!(workflow
        .controls
        .iter()
        .any(|node| matches!(node, FlowNode::Switch(_))));
    // approval → 真实步骤（approval.request），参与 DAG。
    assert!(workflow.steps.iter().any(|step| step.id == "aprv"
        && step.action.as_ref().map(|a| a.service.as_deref()) == Some(Some("approval"))));
    // depends 别名展开：analyze → ocr/tag；foreach → loop_item；switch → pick_case_pdf。
    let done = workflow
        .steps
        .iter()
        .find(|step| step.id == "done")
        .expect("done");
    assert!(done.depends_on.contains(&"ocr".to_string()));
    assert!(done.depends_on.contains(&"tag".to_string()));
}

#[test]
fn top_level_catch_finally_wraps_try() {
    // 需求 8.11：顶层 catch/finally → TryCatch 包裹。
    let source = r#"
workflow: { name: tcf }
steps:
  - id: upload
    action: file.upload
catch:
  - error: "*"
    action: notify.admin
finally:
  - id: cleanup
    action: file.cleanup
"#;
    let workflow = parse_yaml(source, "tcf.workflow.yaml").expect("parse");
    assert!(matches!(workflow.flow.first(), Some(FlowNode::TryCatch(_))));
}

#[test]
fn yaml_compiles_to_ir_end_to_end() {
    // 需求 29.x：YAML 全流程编译（解析 → 语义 → IR）。
    let source = r#"
workflow: { name: e2e, version: 1 }
trigger: { event: file.created }
input: { file_id: string }
steps:
  - id: get
    action: file.get
    input: { id: "${{ input.file_id }}" }
  - id: save
    action: metadata.save
    depends: [get]
"#;
    let ir =
        compile_source_named_for_language(source, "e2e.workflow.yaml", Language::Yaml, &catalog())
            .expect("compile");
    assert_eq!(ir.metadata.name, "e2e");
    assert!(ir.spec.variables.contains_key("file_id"));
    assert_eq!(trigger_type(&ir.spec.trigger), "event");
}

#[test]
fn dsl_and_yaml_compile_to_equivalent_ir() {
    // 需求 8.26/15.10/18.14：同语义 DSL 与 YAML 生成一致 IR。
    let dsl = r#"workflow "equiv" {
    trigger { event { name = "file.created" } }
    variables {
        file_id = input.string(required = true)
    }
    step get_file { action file.get { id = vars.file_id } }
    step save { depends_on get_file action metadata.save { summary = steps.get_file.output } }
}"#;
    let yaml = r#"
workflow: { name: equiv }
trigger: { event: file.created }
input: { file_id: { type: string, required: true } }
steps:
  - id: get_file
    action: file.get
    input: { id: "${{ input.file_id }}" }
  - id: save
    action: metadata.save
    depends: [get_file]
    input: { summary: "${{ steps.get_file.output }}" }
"#;
    let dsl_ir = compile_source_named(dsl, "equiv.flow", &catalog()).expect("dsl");
    let yaml_ir =
        compile_source_named_for_language(yaml, "equiv.workflow.yaml", Language::Yaml, &catalog())
            .expect("yaml");
    let dsl_json = serde_json::to_string_pretty(&dsl_ir).unwrap();
    let yaml_json = serde_json::to_string_pretty(&yaml_ir).unwrap();
    assert_eq!(dsl_json, yaml_json, "DSL 与 YAML 应生成一致 IR");
}

#[test]
fn yaml_emit_ast_via_language_api() {
    // 需求 5.12/13.7：`--emit-ast` 对 YAML 同样输出共享 Domain AST。
    let source = "workflow: { name: ast_yaml }\nsteps:\n  - id: a\n    action: file.get\n";
    let workflow =
        parse_ast_for_language(source, "ast.workflow.yaml", Language::Yaml).expect("parse ast");
    assert_eq!(workflow.name, "ast_yaml");
}

#[test]
fn yaml_parse_errors_use_cfy_codes() {
    // 需求 14.2/31.x：YAML 错误带 CFY-* 前缀与行定位。
    // serde_yaml_ng 语法错误 → CFY-1001（不经过 Schema 层）。
    let bad_yaml = "workflow: { name: x\n steps:\n   - id: a\n      action: file.get\n";
    let err = parse_yaml(bad_yaml, "bad.yaml").unwrap_err();
    assert_eq!(err.code.as_str(), "CFY-1001");

    // 形状错误经 Schema 层（需求 31.9）：缺 action → CFY-SCHEMA-1001。
    let no_action = "workflow: { name: x }\nsteps:\n  - id: a\n    input: {}\n";
    let err = parse_yaml(no_action, "noact.workflow.yaml").unwrap_err();
    assert_eq!(err.code.as_str(), "CFY-SCHEMA-1001");
    assert!(err.location.line >= 2);

    // 非法 trigger type → CFY-SCHEMA-1004（原 CFY-1003 已由 CFY-SCHEMA-1004 取代）。
    let bad_trigger =
        "workflow: { name: x }\ntrigger: { type: bogus }\nsteps:\n  - id: a\n    action: file.get\n";
    let err = parse_yaml(bad_trigger, "trig.workflow.yaml").unwrap_err();
    assert_eq!(err.code.as_str(), "CFY-SCHEMA-1004");
}

#[test]
fn yaml_schema_collects_multi_errors_with_paths_and_lines() {
    // 需求 31.7/31.8/31.9：Schema 校验一次收集多条 CFY-SCHEMA-* 错误，
    // 每条错误携带字段路径（steps[0].retry.count）与行号。
    let source = r#"
workflow: { name: multi }
steps:
  - retry: { count: -1, strategy: bogus }
    timeout: "not-a-duration"
    foo: 1
  - id: ok
    action: file.save
"#;
    let (_workflow, diagnostics) =
        parse_yaml_detailed(source, "multi.workflow.yaml").expect("detailed 解析不因形状错误失败");
    assert!(
        diagnostics.len() >= 6,
        "应一次收集多条形状错误：{diagnostics:?}"
    );
    let messages = diagnostics
        .iter()
        .map(|d| (d.code.as_str(), d.message.as_str()))
        .collect::<Vec<_>>();
    // 必填缺失（id、action，需求 31.3）。
    assert!(messages
        .iter()
        .any(|(_, m)| m.contains("steps[0] 缺少必填字段 `id`")));
    assert!(messages
        .iter()
        .any(|(_, m)| m.contains("steps[0] 缺少必填字段 `action`")));
    // 非法值（31.4 类型 / 31.6 负数 / 策略）与字段路径（31.7）。
    assert!(messages
        .iter()
        .any(|(code, m)| { *code == "CFY-SCHEMA-1004" && m.contains("steps[0].retry.count") }));
    assert!(messages
        .iter()
        .any(|(code, m)| { *code == "CFY-SCHEMA-1004" && m.contains("steps[0].retry.strategy") }));
    assert!(messages
        .iter()
        .any(|(code, m)| *code == "CFY-SCHEMA-1004" && m.contains("steps[0].timeout")));
    // 未知字段（31.5 报错 + 31.22 修复建议）。
    assert!(messages.iter().any(|(code, m)| {
        *code == "CFY-SCHEMA-1003" && m.contains("steps[0] 存在未知字段 `foo`")
    }));
    // 行号都定位到 steps[0] 附近（>= 4 行，全部字段都在该步骤区域内）。
    let step0_line = diagnostics
        .iter()
        .filter(|d| d.message.contains("steps[0]"))
        .map(|d| d.location.line)
        .min()
        .unwrap();
    assert!(
        step0_line >= 4,
        "steps[0] 错误行号应落在步骤内：{step0_line}"
    );
}

#[test]
fn yaml_schema_unknown_field_suggestion() {
    // 需求 31.22：未知字段给出“是否想使用 X 而不是 Y？”建议。
    let source =
        "workflow: { name: s }\nsteps:\n  - id: a\n    action: file.get\n    retry_count: 3\n";
    let (_workflow, diagnostics) = parse_yaml_detailed(source, "s.workflow.yaml").unwrap();
    let message = diagnostics
        .iter()
        .find(|d| d.message.contains("retry_count"))
        .map(|d| d.message.clone())
        .expect("应报出 retry_count 未知字段");
    assert!(
        message.contains("是否想使用 `retry` 而不是 `retry_count`？"),
        "建议应命中 retry：{message}"
    );
}

#[test]
fn yaml_schema_errors_block_compile_with_paths() {
    // 需求 10.29/31.2：形状错误在语义/IR 前被 Schema 层拦截，编译整体失败并带路径。
    let source = "workflow: { name: bad }\nsteps:\n  - id: a\n    action: file.get\n    retry:\n      count: -1\n      strategy: extended\n";
    let error =
        compile_source_named_for_language(source, "bad.workflow.yaml", Language::Yaml, &catalog())
            .expect_err("形状错误应阻断编译");
    assert!(error
        .diagnostics
        .iter()
        .any(|d| d.code.as_str() == "CFY-SCHEMA-1004"));
    assert!(error
        .diagnostics
        .iter()
        .any(|d| d.message.contains("steps[0].retry.count")));
}

#[test]
fn yaml_json_schema_matches_ondisk() {
    // 需求 31.10/31.18：JSON Schema 由 schema.rs 统一定义生成，与仓库文件一致（避免漂移）。
    // 需要重新生成时：UPDATE_YAML_SCHEMA=1 cargo test --test cloudflow_yaml yaml_json_schema_regenerate_with_env
    let generated = cloudflow_runtime::emit_yaml_json_schema();
    let path =
        std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("schemas/yaml-workflow.schema.json");
    let disk: serde_json::Value = serde_json::from_str(
        &std::fs::read_to_string(&path).expect("schemas/yaml-workflow.schema.json 必须存在"),
    )
    .expect("schemas/yaml-workflow.schema.json 必须是合法 JSON");
    assert_eq!(generated, disk, "JSON Schema 与生成器漂移，需重新生成");
}

#[test]
fn yaml_json_schema_regenerate_with_env() {
    // 需求 31.18 开发辅助：`UPDATE_YAML_SCHEMA=1` 时把生成器输出落盘（单一事实来源）。
    if std::env::var("UPDATE_YAML_SCHEMA").as_deref() != Ok("1") {
        return;
    }
    let generated = cloudflow_runtime::emit_yaml_json_schema();
    let path =
        std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("schemas/yaml-workflow.schema.json");
    std::fs::write(
        &path,
        serde_json::to_string_pretty(&generated).unwrap()
            + "
",
    )
    .unwrap();
    panic!("已重新生成 {}", path.display());
}

#[test]
fn all_yaml_examples_compile() {
    // 需求 29.21-29.22/32.x：examples/yaml/*.flow.yaml 全部通过全流程编译。
    let dir = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("examples/yaml");
    let mut count = 0;
    for entry in std::fs::read_dir(&dir).unwrap() {
        let entry = entry.unwrap();
        let path = entry.path();
        if path.extension().and_then(|ext| ext.to_str()) == Some("yaml") {
            // 留档文件（*.legacy.workflow.yaml，旧版 automation.pcd/v1 参考）不参与编译，
            // 见 examples/yaml/weekly_sales_report.legacy.workflow.yaml 头部说明。
            if path
                .file_name()
                .and_then(|name| name.to_str())
                .is_some_and(|name| name.contains(".legacy."))
            {
                continue;
            }
            let source = std::fs::read_to_string(&path).unwrap();
            let ir = compile_source_named_for_language(
                &source,
                &path.display().to_string(),
                Language::Yaml,
                &catalog(),
            )
            .unwrap_or_else(|error| {
                panic!(
                    "{} 编译失败：{}",
                    path.display(),
                    error.diagnostics[0].message
                )
            });
            assert!(!ir.spec.graph.nodes.is_empty());
            count += 1;
        }
    }
    assert!(count >= 8, "至少 8 个 YAML 示例（当前 {count}）");
}

#[test]
fn yaml_plugin_action_matches_dsl_hub_key() {
    // 需求 21.x：`plugin:<id>:<function>@<v>`（GitHub-Actions 风格）与 DSL `action plugin { id function version }`
    // 产出同一 ActionNode（provider=plugin），语义 action_key 一致（能力中心键）。
    use cloudflow_runtime::semantic::action_key;
    let yaml_source = r#"
workflow: { name: hub }
steps:
  - id: gen
    action: "plugin:8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7:generate_report@1"
    input: { data: "${{ steps.a.output }}" }
    output: report
"#;
    let workflow = parse_yaml(yaml_source, "hub.workflow.yaml").expect("yaml 解析");
    let yaml_action = workflow
        .steps
        .iter()
        .find(|step| step.id == "gen")
        .unwrap()
        .action
        .clone()
        .unwrap();
    assert_eq!(yaml_action.provider, "plugin");
    assert_eq!(
        yaml_action.plugin_id.as_deref(),
        Some("8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7")
    );
    assert_eq!(yaml_action.function.as_deref(), Some("generate_report"));
    assert_eq!(yaml_action.version.as_deref(), Some("1"));

    let dsl = r#"workflow "hub" {
    step gen {
        depends_on a
        action plugin {
            id = "8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7"
            function = "generate_report"
            version = "1"
            input { data = steps.a.output }
        }
    }
}"#;
    let dsl_workflow = parse_ast_for_language(dsl, "hub.flow", Language::Dsl).expect("dsl 解析");
    let dsl_action = dsl_workflow
        .steps
        .iter()
        .find(|step| step.id == "gen")
        .unwrap()
        .action
        .clone()
        .unwrap();

    assert_eq!(action_key(&yaml_action), action_key(&dsl_action));
    assert_eq!(yaml_action.provider, dsl_action.provider);
    assert_eq!(yaml_action.plugin_id, dsl_action.plugin_id);
    assert_eq!(yaml_action.function, dsl_action.function);
    assert_eq!(yaml_action.version, dsl_action.version);
}

#[test]
fn yaml_double_brace_expression_and_template() {
    // 需求 6.14/6.32/28.56-59（对标 GitHub Actions `${{ }}`）：
    // 整串 `${{ ... }}` 切为表达式；字符串内 `${{ }}` 插值为 Template 段。
    use cloudflow_runtime::ast::ValueNode;
    let source = r#"
workflow: { name: ga }
input: { who: { type: string } }
steps:
  - id: echo
    action: builtin:echo
    input:
      full: "${{ vars.who }}"
      whole: "${{ steps.a.output.count > 0 }}"
      greeting: "hello ${{ vars.who }}, today is ${{ steps.a.output }}"
"#;
    let workflow = parse_yaml(source, "ga.workflow.yaml").expect("解析");
    let echo = workflow
        .steps
        .iter()
        .find(|step| step.id == "echo")
        .unwrap();
    let args = &echo.action.as_ref().unwrap().arguments;
    assert!(matches!(
        args.get("full"),
        Some(ValueNode::VariableRef(path)) if path == "vars.who"
    ));
    assert!(matches!(args.get("whole"), Some(ValueNode::Expression(_))));
    let ref_segments = match args.get("greeting") {
        Some(ValueNode::Template(segments)) => segments
            .iter()
            .filter(|segment| matches!(segment, ValueNode::VariableRef(_)))
            .count(),
        _ => 0,
    };
    assert_eq!(ref_segments, 2, "greeting 应含两个 $ref 段");
}

#[test]
fn invalid_yaml_examples_fail_with_expected_code() {
    // 需求 32.27/32.28：examples/yaml/invalid/ 反例集。每个文件头部用 `# expected: <CODE>`
    // 标注期望错误码；编译必须失败，且诊断集中包含该码（多错误收集，不因首错停止）。
    let dir = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("examples/yaml/invalid");
    assert!(dir.is_dir(), "缺少反例目录 examples/yaml/invalid");
    let mut count = 0;
    for entry in std::fs::read_dir(&dir).unwrap() {
        let entry = entry.unwrap();
        let path = entry.path();
        if path.extension().and_then(|ext| ext.to_str()) != Some("yaml") {
            continue;
        }
        let source = std::fs::read_to_string(&path).unwrap();
        let expected = source
            .lines()
            .find_map(|line| line.trim().strip_prefix("# expected:"))
            .unwrap_or_else(|| panic!("{} 缺少 `# expected: <CODE>` 标注", path.display()))
            .trim();
        let result = compile_source_named_for_language(
            &source,
            &path.display().to_string(),
            Language::Yaml,
            &catalog(),
        );
        match result {
            Ok(ir) => panic!(
                "{} 应编译失败，却成功（节点数 {}）",
                path.display(),
                ir.spec.graph.nodes.len()
            ),
            Err(error) => {
                let codes: Vec<&str> = error
                    .diagnostics
                    .iter()
                    .map(|diagnostic| diagnostic.code.as_str())
                    .collect();
                assert!(
                    codes.iter().any(|code| *code == expected),
                    "{} 期望错误码 {expected}，实际：{codes:?}",
                    path.display()
                );
                count += 1;
            }
        }
    }
    assert!(count >= 10, "至少 10 个反例（当前 {count}）");
}
