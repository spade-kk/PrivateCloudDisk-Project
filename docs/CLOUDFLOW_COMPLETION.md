# CloudFlow 统一代码补全系统

CloudFlow 的代码补全/结构提示以 `syntax-highlight/build/cloudflow.completion.json` 为**唯一事实来源**，
与语法高亮规范平行，由 `GRAMMAR.pest` + `AST.rs` 自动生成，并在 VS Code 扩展与前端 Monaco 两处复用。
**前端与编辑器插件不得硬编码任何 CloudFlow 补全/结构提示规则**（需求 15：统一补全规范、禁止硬编码）。

## 1. 架构与数据流

```
GRAMMAR.pest ─────────┐
                      ├─ grammar_scraper  (关键字/操作符/引用前缀)
                      ├─ ast_scraper      (AST 节点 → 结构模板对账)
config.py（人工知识库）─┤
  BUILTIN_FUNCTIONS    │
  TRIGGER_TYPES        │
  RETRY_EXCEPTIONS  ──┴─ completion_builder.py → cloudflow.completion.json（唯一事实来源）
  STRUCTURE_TEMPLATES                             │
  SNIPPETS / ERROR_CODES                          ├─ convert.py（语法高亮，并行）
                                                  └─ completion_convert.py
                                     ┌────────────┼──────────────┐
                              vscode/snippets    VS Code        前端 Monaco
                              cloudflow.code-   extension.js    cloudflowCompletion.ts
                              snippets          （补全+签名）    （补全+签名）
```

补全规范含以下分类：

- `keywords`：关键字（含 `topLevelKeywords`）。
- `blocks`：顶层块/声明模板（workflow/metadata/variables/trigger/runtime/steps/handlers/environment/namespace/audit/tag/import/include）。
- `structureTemplates`：控制流/结构模板（if/foreach/for/while/parallel/try-catch-finally/wait/assert/switch/delay/notify/validate/return/break/continue/step_group/retry/retry_on/depends_on/timeout）。
- `builtinFunctions`：内置函数白名单（现 19 个：size/len/contains/starts_with/ends_with/now/get/
  trim/to_upper/to_lower/range/abs/round/floor/ceil）+ 签名帮助参数。
- `pipelineOperators`：filter/map/reduce 管道操作符。
- `types`：类型名补全（string/number/boolean/array/object/file/user/space/credential/input）。
- `triggers`：触发器类型与字段提示（manual/schedule/event/http/interval/webhook）。
- `retryExceptions`：可重试异常白名单。
- `referencePrefixes`：`vars.` / `steps.<id>.output` / `workflow.` 引用前缀。
- `capabilities`：能力补全（运行时动态来源，前端经 `props.capabilities` 注入）。
- `errorCodes`：错误码速查（对应 `CLOUDFLOW_ERROR_DESIGN.md`，供 Monaco markers/quick-fix）。
- `pairs`：括号/引号自动配对、缩进、注释规则。
- `snippets`：常用代码片段。
- `astNodes`：AST 节点/FlowNode 变体快照，用于结构模板与代码结构提示对账。

## 2. 使用方式

### 2.1 生成

```bash
cd PrivateCloudDisk-cloudflow-runtime

# 一键生成语法高亮 + 补全全部产物（含 Web 前端分发拷贝）
python3 syntax-highlight/generator/generate.py --verbose

# 或分步执行
python3 syntax-highlight/generator/completion_builder.py --force   # 生成 cloudflow.completion.json
python3 syntax-highlight/generator/completion_convert.py --web \
    ../PrivateCloudDisk-web/src/languages                          # VS Code 片段 + Web 拷贝
```

### 2.2 验证

```bash
node --check syntax-highlight/vscode/src/extension.js
python3 -m unittest discover -s syntax-highlight/generator/tests -p "test_completion.py"
python3 -m unittest discover -s syntax-highlight/generator/tests -p "test_*.py"   # 全量（含高亮回归）
cd syntax-highlight/vscode && vsce package --out cloudflow-language-1.2.1.vsix
```

### 2.3 前端 Monaco

把 `syntax-highlight/build/cloudflow.completion.json` 复制到
`PrivateCloudDisk-web/src/languages/cloudflow.completion.json`（`generate.py --web` 会代做）。
`src/languages/cloudflowCompletion.ts` 读取该规范并注册：
- `registerCloudFlowCompletion(monaco, { capabilities })`：CompletionItem + SignatureHelp provider；
- 变量（`vars.`）、步骤输出（`steps.<id>.output`）、能力（`action ` / `action plugin.`）为运行时动态来源；
- 作用域变量（`foreach item` / `catch error`）由调用方通过 `options.variables` 注入。

`PluginMonacoEditor.vue` 不再硬编码 CloudFlow 补全，而是调用 `registerCloudFlowCompletion`。

### 2.4 VS Code 扩展

`vscode/` 扩展包含：

- `syntaxes/cloudflow.tmLanguage.json` + `cloudflow.completion.json`：语法与补全规范资源；
- `snippets/cloudflow.code-snippets`：结构模板与常用片段；
- `src/extension.js`：CompletionItemProvider + SignatureHelpProvider，`main` 指向该入口；
- `language-configuration.json`：括号/引号自动配对、缩进、注释。

纯 JS、无编译、无后端服务，`vsce package` 直接打包（`main` 已声明，不再报
“Manifest needs either a 'main' or 'browser' property”）。

## 3. 新增语法时的同步流程

```text
1) 在 src/grammar.pest 增加规则；
2) 若关键字/结构属于新补全类别，在 syntax-highlight/generator/config.py 补充
   BUILTIN_FUNCTIONS / TRIGGER_TYPES / STRUCTURE_TEMPLATES / SNIPPETS / ERROR_CODES 等；
3) 运行 generate.py 重新生成；
4) 检查 build/ 与 vscode/、web 分发产物的 diff 后提交；
5) 补全测试与语法高亮测试全绿。
```

约束：产物（`build/`、`vscode/snippets/`、`vscode/syntaxes/cloudflow.completion.json`、
`src/languages/cloudflow.completion.json`）由脚本自动生成，勿手动修改。若补全类别/函数/类型有误，
应改 `config.py` 或源码规则后重新生成。
