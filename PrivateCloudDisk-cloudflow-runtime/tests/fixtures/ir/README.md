# IR 示例夹具（tests/fixtures/ir）

需求清单 §8（8.1-8.25）：开发/调试执行入口与 IR 契约校验器的测试资产。

- 文件格式：`workflow.cloudflow.io/v1` IR（camelCase JSON，与 `compiler::compile` 产物一致）。
- 动作均使用 mock 动作名（builtin/api/workflow），由 `MockActionExecutor` 确定性回显；
  不依赖真实插件或数据库（需求 8.17）。
- 测试通过 `include_str!` 动态加载（需求 8.20）；CLI `cloudflowc dev-execute` 与
  HTTP `/api/dev/execute` 可直接执行（需求 8.21/8.22）。
- 非法示例（13-17）用于 IR 契约校验器反例测试（需求 8.12-8.16），头部场景说明见下表。

## 文件清单与预期结果

| 文件 | 场景 | 预期 |
| --- | --- | --- |
| `01_sequential.json` | 顺序三步（收集→聚合→保存），`$ref` 输出引用 | 校验通过；执行 success；`outputs.report_data` 为 aggregate 的 mock 输出 |
| `02_condition.json` | condition 真/假分支 | `is_big=true` → 执行 `big`、跳过 `small`；`false` 相反 |
| `03_parallel.json` | parallel 三分支 + 汇聚节点 | 4 节点全部 success |
| `04_loop_foreach.json` | foreach 遍历 `vars.files`（迭代变量 `item`） | 每次迭代执行 `process`；空数组 0 次 |
| `05_loop_while.json` | while（条件 false，零次迭代） | success；`tick` skipped |
| `06_loop_for_range.json` | for i in 0..3 | `emit` 迭代 3 次 |
| `07_retry_timeout.json` | task + retry(maxAttempts=3, exponential) + timeout=5s | 配合 `inject_failures` 验证重试；`action_latency_ms` 超 5s 验证 CF5001 |
| `08_exception_try_catch_finally.json` | try/catch/finally（catchBinding=error） | `risky` 失败 → `compensate` 执行（vars.error 绑定）→ `cleanup` 必执行；工作流 success |
| `09_wait_approval.json` | build → wait(approval) → deploy | 状态 waiting；deploy 未执行 |
| `10_subworkflow.json` | provider=workflow 子工作流调用（java.build → k8s.deploy） | success；`workflow:java.build` 动作键 |
| `11_variables_expressions.json` | input/local 变量、`$expr` 算术/比较、`$template`、`$pipeline` filter | success；outputs.sum = a+b |
| `12_complex_combo.json` | switch + condition + assert + parallel + delay + try/catch/finally 组合 | success（ok=true, kind=pdf） |
| `13_invalid_structure.json` | 非法 apiVersion + 未知节点类型 quux + task 缺 action | 校验失败：CFI-7001/CFI-7006/CFI-7026 |
| `14_missing_field.json` | task 缺 action；condition 缺 condition 表达式 | 校验失败：CFI-7026/CFI-7009 |
| `15_cycle.json` | edges 形成 a→b→a 环 | 校验失败：CFI-7018 |
| `16_unknown_ref.json` | `$ref` 指向未声明变量与不存在步骤 | 校验失败：CFI-7023 ×2 |
| `17_unknown_action.json` | 动作 `builtin:file.definitely_missing_capability` | strict Mock 执行器返回 CF5002（执行失败） |
