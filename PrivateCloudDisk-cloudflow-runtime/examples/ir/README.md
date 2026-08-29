# CloudFlow 示例 IR（`workflow.cloudflow.io/v1`）

本目录存放**手工维护的 IR 参考样例**，用于：

- 开发调试执行入口（CLI `cloudflowc dev-execute` 与 HTTP `POST /api/dev/execute`）的演示输入；
- 前端 IDE / 微服务消费 IR 结构时的人类可读参照（`tests/fixtures/ir/` 是测试资产，与本目录分离）。

## 样例清单

| 文件 | 覆盖特性 |
|---|---|
| `01_sequential_flow.ir.json` | 顺序三段式（task 依赖链 + `steps.<id>.output` 数据传递 + `spec.outputs`） |
| `02_condition_branch.ir.json` | `condition` 节点 + `trueBranch/falseBranch` + 未选中分支跳过 + 汇合节点 |
| `03_plugin_retry.ir.json` | 插件能力（`plugin:8ae47c8d:generate_report`）+ 节点级重试（exponential）+ 节点超时 + 字符串模板 |

## 与测试资产的区别

- 本目录（`examples/ir/`）：可展示、可复制、可手工编辑；**不**包含反例（invalid）。
- `tests/fixtures/ir/`：测试夹具，含正例与反例（`13_invalid_structure`、`15_cycle` 等），
  由 `tests/cloudflow_dev_exec.rs` 的 `e2e_all_valid_fixtures_compile_shape_and_execute` 与
  契约校验测试直接消费。

## 如何运行

```bash
# CLI（纯内存 Mock 动作执行，不写数据库）
cargo run -p pcd-cloudflow-runtime --bin cloudflowc -- dev-execute examples/ir/01_sequential_flow.ir.json --var files='["a.xlsx"]' --verbose

# HTTP 调试入口（需 CLOUDFLOW_ENABLE_DEBUG_EXECUTE=true 与 X-PCD-Service-Token）
curl -s http://127.0.0.1:8080/api/dev/execute \
  -H 'Content-Type: application/json' -H 'X-PCD-Service-Token: <token>' \
  -d @<(python3 -c 'import json,sys; ir=json.load(open("examples/ir/01_sequential_flow.ir.json")); print(json.dumps({"ir": ir, "variables": {"files": ["a.xlsx"]}}))')
```

## 约定

- 文件头注释见各文件内 `metadata.description`（JSON 无注释语法）；
- 更新 IR 结构（`ir.rs`）后须同步更新本目录样例，并由 `tests/cloudflow_examples_ir.rs`
  保证“样例必须可校验 + 可执行”；
- 示例中不得包含真实密钥 / 真实用户 ID；插件 ID 使用文档占位值。
