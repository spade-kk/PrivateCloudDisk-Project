# CloudFlow 统一语法高亮系统

> 需求关联：CloudFlow 统一语法高亮规则生成。
> 目标：以 `GRAMMAR.pest` + `AST.rs` 为**唯一事实来源**，生成统一规范，转换到
> VS Code TextMate、Monaco Monarch、Highlight.js 三种格式；**前端与编辑器插件不硬编码
> 任何 CloudFlow 高亮正则**。

## 1. 架构与数据流

```
GRAMMAR.pest ──┐
               ├─ grammar_scraper ─┐
               │                   ├─ build_spec.py ─→ cloudflow.syntax-highlight.json
AST.rs ────────┼─ ast_scraper ─────┘        （唯一事实来源，含版本与时间戳）
               │
               └─ config.py（人工知识库：类别/关键字/操作符/引用前缀 分类）
                                          │
                     ┌────────────────────┼─────────────────────┐
              textmate.py           monarch.py             hljs.py
       cloudflow.tmLanguage.json   cloudflow.monarch.json  cloudflow.hljs.js
       （VS Code 扩展）            （前端 Monaco）          （网页/文档站）
```

代码位置：`PrivateCloudDisk-cloudflow-runtime/syntax-highlight/`。

## 2. Token 类别与配色

| 类别 | TextMate scope | 说明 |
| --- | --- | --- |
| 控制流 | `keyword.control.cloudflow` | if/else/for/while/switch/case/break/… |
| 声明 | `keyword.declaration.cloudflow` | workflow/metadata/variables/trigger/… |
| 类型 | `support.type.cloudflow` | string/number/boolean/… |
| 限定词 | `storage.modifier.cloudflow` | input/output |
| 布尔 | `constant.language.boolean.cloudflow` | true/false |
| 函数 | `entity.name.function.cloudflow` | filter/map/reduce 等管道操作 |
| 操作符 | `keyword.operator.cloudflow` | 算术/比较/逻辑/管道/三元 |
| 标点 | `punctuation.separator.cloudflow` | {}()[],.; |
| 注释 | `comment.line.number-sign.cloudflow` | `#` 单行 |
| 字符串 | `string.quoted.double/triple.cloudflow` | `"…"` 与 `"""…"""` |
| 插值 | `variable.other.embedded.cloudflow` | `${…}` |
| 数字/时长 | `constant.numeric.(.duration).cloudflow` | 整数/小数/科学计数/5m/30s |
| 引用 | `variable.other.vars/steps/system.cloudflow` | `vars.`/`steps.`/`workflow.` |
| AST meta | `meta.block.<kw>.cloudflow` | 语义块作用域 |


> 关于 `meta.*`（AST 语义作用域）的说明：`cloudflow.syntax-highlight.json` 的
> `ast.nodeScopes` / `ast.flowVariantScopes` 是**语义层映射**（供 LSP、语义 token Provider 等
> 结构化语义工具消费），而 TextMate / Monarch / Highlight.js 三种是**词法 token 渲染器**，按
> `categories` 的 token 级类别着色。因此三平台均不把 `meta.*` 当作可着色的词法 token——
> 这是有意设计，并非遗漏；词法层面每个类别（keyword/type/literal/operator/…）都已完整区分。

## 3. 使用方式

### 3.1 生成

```bash
cd PrivateCloudDisk-cloudflow-runtime
python3 syntax-highlight/generator/build_spec.py --force --verbose   # 统一规范
python3 syntax-highlight/generator/convert.py --format all --verbose  # 三种格式
```

### 3.2 Monaco（前端工作流/插件 IDE）

1. 将 `build/cloudflow.monarch.json` 复制到 `PrivateCloudDisk-web/src/languages/cloudflow.monarch.json`。
2. `PrivateCloudDisk-web/src/languages/cloudflow.ts` 调用
   `monaco.languages.register({id:'cloudflow'})` 与 `setMonarchTokensProvider`。
3. 编辑器 `language="cloudflow"` 即可高亮；不再硬编码正则。

### 3.3 VS Code

- 扩展目录 `syntax-highlight/vscode/`（含 grammar + language-configuration + package.json）。
- 打开 `.flow`/`.cloudflow` 即高亮；`vsce package` 生成 `.vsix`。

### 3.4 网页 Highlight.js

- 直接引用 `build/cloudflow.hljs.js`（UMD，ESM/CJS 均可，自带 `registerLanguage('cloudflow')`）。
- 预演：浏览器打开 `demo/highlight-demo.html`，或文档站 Markdown 使用 ```cloudflow 语言标记。

## 4. 更新流程与校验

新增/修改 DSL 语法：

```bash
# 1) 改 GRAMMAR.pest / AST.rs；新增语义类别时同步 config.py
# 2) 重新生成
python3 syntax-highlight/generator/build_spec.py --force
python3 syntax-highlight/generator/convert.py --format all
# 3) 测试
python3 -m unittest discover -s syntax-highlight/generator/tests -p "test_*.py"
```

- 产物含版本与生成时间戳；`unclassifiedTokens` 非空会告警（防遗漏）。
- 测试套件覆盖：统一规范 schema、三格式可加载、关键字/字符串/数字/注释/引用/模板高亮、
  三格式 scope 一致性、样本 `.flow` 覆盖与新增语法回归。
- 生产命令：`node --check syntax-highlight/build/cloudflow.hljs.js` 校验 HLJS JS 语法。

## 5. 平台一致性说明

三平台允许主题配色差异，但 **token 类别与 scope 一致**（需求 12.12）：
Monaco 与 Highlight.js 通过 category 语义名称对颜色做主题适配，TextMate 通过 scope 交给主题。

## 6. 交付物

- 统一规范 `cloudflow.syntax-highlight.json` 与 JSON Schema
- TextMate / Monarch(JSON+TS) / Highlight.js(UMD) 三份产物
- 解析脚本（`grammar_scraper.py`、`ast_scraper.py`）、转换工具（`convert.py`）、配置（`config.py`）
- VS Code 扩展、前端 Monaco 模块、Highlight.js 演示页、样例 `.flow`
- 测试套件与示例高亮演示
