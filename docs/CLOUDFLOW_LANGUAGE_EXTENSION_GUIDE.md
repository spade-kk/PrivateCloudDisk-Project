# CloudFlow 新语法扩展指南

> 需求关联：CloudFlow 语法覆盖测试、扩展与 Runtime 执行增强。本文定义新增关键字的唯一工程流程，
> 防止出现“Parser 接受、AST/IR/Runtime 丢失语义”的半实现状态。

## 1. 先写规范，再写语法

1. 在 `CLOUDFLOW_DESIGN.md` 定义关键字、作用域、错误码、资源上限和可恢复语义。
2. 在 `CLOUDFLOW_IR_DESIGN.md` 定义稳定 JSON 映射；不得把可执行表达式降级为字符串。
3. 明确 V1 行为：若 Runtime 不支持，Parser 必须拒绝，而不是接受后静默跳过。
4. 评估是否会引入跨文件读取、网络、插件权限或动态代码执行；这些能力必须先经过安全评审。

## 2. 编译器改动清单

| 层次 | 必须改动 | 验收 |
|---|---|---|
| PEG | `src/grammar.pest` 添加**白名单**规则 | 未知关键字仍输出 `CF1202` |
| AST | `src/ast.rs` 增加带 `Span` 的节点/值枚举 | AST 单测保留行列位置 |
| Parser | `src/parser.rs` 只转换结构，不做业务调用 | 字面量、`$ref`、`$expr` 不混淆 |
| Semantic | `src/semantic.rs` 加类型、作用域、DAG、资源限制检查 | 可聚合多个结构化诊断 |
| IR | `src/compiler.rs` 与 `src/ir.rs` 完整映射 | `validate_ir` 与 Schema 都通过 |
| Runtime | `src/execution.rs` 实现持久化执行/恢复 | 不重复副作用，失败路径可审计 |
| AST 可视化 | `src/ast_printer.rs` 的 `flow_tree`/`step_tree`/`render_json` 为新增节点穷尽匹配 | `--emit-ast` 树/JSON 无遗漏，单测覆盖新节点（Rust 编译期 `match` 穷尽保证不漏） |

## 3. 测试与样例

- 在 `examples/coverage/` 增加一个聚焦 `.flow`，文件头注释说明目的和 IR 预期。
- 在 `tests/cloudflow_coverage.rs` 验证节点类型、边、`$ref/$expr` 与控制流字段。
- 执行 `./scripts/verify_coverage.sh`；它使用真实 `cloudflowc` 生成 IR，再使用
  `schemas/workflow-ir-v1.schema.json` 的离线契约子集验证。
- 有持久化/异步行为时，在 MySQL/RabbitMQ 集成测试中覆盖成功、失败、超时、重复投递与恢复。

## 4. 禁止事项

- 不得增加 `ident { ... }` 形式的通配语言块。
- 不得为方便解析而使用 `eval`、动态 Rust/Python/JavaScript 执行或未受限文件读取。
- 不得修改旧 IR 字段含义；新增字段必须 `serde(default)` 并给出迁移/兼容说明。
- 不得只修改前端高亮而未在 Compiler、Runtime 和文档中落地。

## 5. 当前预留项

`match/case` 预留已由 V1.2 `switch/case/default` 落地（不与 if/else 混淆）。V1.2 现含三批扩展
（switch/retry_on/timeout/delay/environment/namespace/import-as/tag/changelog；for/break/continue/
parallel 并发/validate；interval/webhook 触发器详配/on_error/notify/管道/字符串模板/audit/step group/
use/条件 depends_on/return），每批均按本流程贯通 grammar→AST→IR→语义→Runtime→错误码→测试→文档，
详见 `docs/CLOUDFLOW_V1.2_DSL_EXTENSION.md`。`include` 已实现第一阶段：
相对 `.flow` 模块、入口目录沙箱、循环检测、深度上限和不覆盖 trigger/runtime 的合并规则。后续仍需补齐
依赖哈希、签名锁定、远程/市场模块审核与 IDE 受信任模块选择器，届时不得放松现有路径安全边界。

## 6. 语法高亮同步（统一规范生成）

CloudFlow 的语法高亮以 `syntax-highlight/build/cloudflow.syntax-highlight.json` 为**唯一事实来源**，
由 `GRAMMAR.pest` + `AST.rs` 自动生成，并在前端 Monaco、VS Code TextMate、网页 Highlight.js 三处复用。
**前端与编辑器插件不得硬编码任何 CloudFlow 高亮正则**（需求：统一规范、禁止硬编码）。

### 6.1 数据流

```
GRAMMAR.pest ─┐
               ├─ grammar_scraper (关键字/操作符/字符串/数字/引用/注释)
               ├─ ast_scraper     (AST 节点 → meta scope)
               └─ build_spec.py ─→ cloudflow.syntax-highlight.json（唯一事实来源）
                                          │
                     ┌────────────────────┼─────────────────────┐
              textmate.py           monarch.py             hljs.py
       cloudflow.tmLanguage.json   cloudflow.monarch.ts   cloudflow.hljs.js
       （VS Code 扩展）            （前端 Monaco）         （文档站/网页）
```

### 6.2 更新高亮规则

新增或修改 DSL 语法时（与 Compiler/Runtime/文档流程并行）：

```bash
cd PrivateCloudDisk-cloudflow-runtime
python3 syntax-highlight/generator/generate.py --verbose   # 一键：规范+补全+三格式+Web 分发
# 分步等价：
# python3 syntax-highlight/generator/build_spec.py --force
# python3 syntax-highlight/generator/completion_builder.py --force
# python3 syntax-highlight/generator/convert.py --format all
python3 -m unittest discover -s syntax-highlight/generator/tests -p "test_*.py"
```

如需即时生效多次改语法，可用 `build_spec.py --watch` 监听 `GRAMMAR.pest` / `AST.rs` 变更。

- 前端 Monaco：把 `syntax-highlight/build/cloudflow.monarch.json` 复制到
  `PrivateCloudDisk-web/src/languages/cloudflow.monarch.json`（`src/languages/cloudflow.ts` 负责注册）。
- VS Code 扩展：复制新 `cloudflow.tmLanguage.json` 到 `syntax-highlight/vscode/syntaxes/`，
  或用 `vsce package` 重新打包。
- 网页 Highlight.js：直接引用 `syntax-highlight/build/cloudflow.hljs.js`（UMD，ESM/CJS 均可）。

### 6.3 约束与校验

- 新增关键字若属于既有类别，脚本自动识别并归类；若属于新语义类别，在
  `syntax-highlight/generator/config.py` 补充类别/颜色/scope。
- 生成产物含版本与时间戳；`unclassifiedTokens` 非空时 `build_spec.py` 会告警（防遗漏）。
- 测试套件校验统一规范合法、三格式可加载、关键字/字符串/数字/注释/引用/模板高亮覆盖、
  样本 `.flow` 覆盖，以及三格式 scope 一致性。
- 若新增语法只改前端高亮而未在 Compiler/Runtime/文档落地，视为未完成（沿用第 4 节禁止事项）。


## 7. 代码补全同步（统一补全规范）

与语法高亮平行，CloudFlow 代码补全/结构提示以 `syntax-highlight/build/cloudflow.completion.json`
为唯一事实来源，由 `GRAMMAR.pest` + `AST.rs` + `config.py`（人工知识库）自动生成
（详见 [CloudFlow 统一代码补全](CLOUDFLOW_COMPLETION.md)）。

- 补全规范由 `syntax-highlight/generator/completion_builder.py` 生成，
  `syntax-highlight/generator/completion_convert.py` 分发到 VS Code 片段/前端 Monaco。
- 内置函数白名单（`size/len/contains/starts_with/ends_with`）、触发器类型、可重试异常、
  结构模板、片段与错误码在 `config.py` 集中维护。
- VS Code 扩展新增 `src/extension.js`（CompletionItem + SignatureHelp provider）与
  `snippets/cloudflow.code-snippets`；`main` 已声明，`vsce package` 可正常打包。
- 前端 Monaco 由 `PrivateCloudDisk-web/src/languages/cloudflowCompletion.ts` 注册补全，
  运行时能力表经 `props.capabilities` 注入；`PluginMonacoEditor.vue` 不再硬编码 CloudFlow 补全。
- 新增补全类别/函数/类型时：改 `config.py` → 运行 `generate.py` → 重新打包/拷贝。

## 6. AST 可视化（`--emit-ast`）

新增 AST 节点时，同时更新 `src/ast_printer.rs`：
- `flow_tree()`：为新的 `FlowNode` 变体返回 `Tree`（标签 + 子节点）；
- `render_json()` 的 `flow_json()`：为新变体产出对等 JSON；
- 结构体级节点（如新声明）在 `workflow_tree_colored()` / `render_json()` 顶层分支补字段。
Rust 的 `match` 穷尽性会强制补齐，避免 `--emit-ast` 漏输出。示例命令：
```bash
cargo run --bin cloudflowc -- compile examples/coverage/<file>.flow --emit-ast --no-color
```
