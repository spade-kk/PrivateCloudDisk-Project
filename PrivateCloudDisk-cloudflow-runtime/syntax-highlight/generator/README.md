# CloudFlow 统一语法高亮生成器

从 `src/grammar.pest` 与 `src/ast.rs` 自动提取 CloudFlow DSL 语法元素，生成四种文件：

| 产物 | 路径 | 用途 |
| --- | --- | --- |
| 统一规范（唯一事实来源） | `syntax-highlight/build/cloudflow.syntax-highlight.json` | 各平台转换器的输入 |
| VS Code TextMate | `syntax-highlight/build/cloudflow.tmLanguage.json` | `.tmLanguage.json` |
| Monaco Monarch | `syntax-highlight/build/cloudflow.monarch.json` / `.ts` | 前端 Monaco 引入 |
| Highlight.js | `syntax-highlight/build/cloudflow.hljs.js` | 网页代码高亮（UMD） |

> 生成产物自动生成，**请勿手动修改**；改 DSL 语法应先改 `GRAMMAR.pest` / `AST.rs`，再重新生成（需求 14.27）。

## 快速开始

```bash
# 一键生成全部产物：语法高亮规范 + 补全规范 + 三格式 + VS Code 片段/Web 分发
python3 syntax-highlight/generator/generate.py --verbose

# 分步执行等价：
python3 syntax-highlight/generator/build_spec.py --force --verbose          # 语法高亮规范
python3 syntax-highlight/generator/completion_builder.py --force --verbose  # 代码补全规范
python3 syntax-highlight/generator/convert.py --format all --verbose        # tmLanguage/monarch/hljs
python3 syntax-highlight/generator/completion_convert.py --web <web-dir>    # VS Code 片段 + Web 拷贝

# 运行测试套件（语法高亮 + 补全，现 55 项）
python3 -m unittest discover -s syntax-highlight/generator/tests -p "test_*.py"
```

## 命令行

### build_spec.py

| 参数 | 说明 | 需求 |
| --- | --- | --- |
| `--force` | 忽略增量检查，全量重新生成 | 14.13 |
| `--watch` | 监听 GRAMMAR.pest / AST.rs 变更自动重生成 | 14.28 |
| `--verbose` | 打印关键字/操作符/AST 节点统计与告警 | 14.23 |

错误处理（需求 14.22）：源文件缺失/损坏时输出清晰错误并以非零码退出，不生成损坏产物。
产物含版本与生成时间戳（需求 14.21），供追踪同步状态。

### convert.py

| 参数 | 说明 |
| --- | --- |
| `--format tmLanguage|monarch|hljs|all` | 输出格式（默认 all） |
| `--verbose` | 打印每个输出文件 |
| `--force` | 强制读规范并转换 |

## 目录结构

```
syntax-highlight/
├── generator/
│   ├── generate.py            # 一键生成器（高亮 + 补全 + 三格式 + 分发）
│   ├── build_spec.py          # 统一高亮解析脚本（主入口）
│   ├── completion_builder.py  # 统一补全规范生成器
│   ├── completion_convert.py  # 补全 → VS Code 片段 + Web 分发
│   ├── config.py              # 人工知识库：高亮分类 + 补全（函数/触发器/异常/模板/片段/错误码）
│   ├── grammar_scraper.py     # 从 GRAMMAR.pest 提取 token
│   ├── ast_scraper.py         # 从 AST.rs 提取节点 → meta scope
│   ├── convert.py             # 规范 → 多格式转换器入口
│   ├── converters/
│   │   ├── _common.py
│   │   ├── textmate.py        # generate(spec) -> tmLanguage
│   │   ├── monarch.py         # generate(spec) -> Monarch (JSON + ts)
│   │   └── hljs.py            # generate(spec) -> hljs 语言定义（UMD）
│   └── tests/
│       ├── test_syntax_highlight.py
│       ├── test_completion.py
│       └── hljs_runner.mjs
├── schema/
│   ├── cloudflow.syntax-highlight.schema.json
│   └── cloudflow.completion.schema.json
├── build/                     # 生成产物（勿手改）
├── vscode/                    # VS Code 扩展（高亮 + 补全 provider + 片段）
├── samples/                   # 示例 .flow（高亮回归样本）
└── demo/highlight-demo.html   # Highlight.js 网页演示
```

## 架构与扩展

### 数据流

```
GRAMMAR.pest ──┐
               ├─ grammar_scraper ─┐
AST.rs ────────┤                  ├─ build_spec.py ─→ cloudflow.syntax-highlight.json
               └─ ast_scraper ────┘                        │
                                               ┌──────────┼───────────┐
                                        textmate.py   monarch.py   hljs.py
                                               │            │           │
                                       .tmLanguage.json  .monarch.ts  .hljs.js

# 补全（需求 15.x）：GRAMMAR.pest + AST.rs + config.py 补全知识库
GRAMMAR.pest ─┐
AST.rs ───────┤─ completion_builder.py ─→ cloudflow.completion.json ──┬→ VS Code 片段/extension.js
config.py ────┘                                                        └→ Web Monaco cloudflowCompletion.ts
```

### 转换器接口（需求 14.15）

每个转换器模块实现 `generate(spec: dict, options: dict | None) -> str`，
输入统一规范，输出对应格式的文本。在 `convert.py` 的 `FORMATS` 表登记即可被调用。

新增目标格式（Prism.js / CodeMirror 等，需求 8.13 / 14.14）：
1. 在 `converters/` 新增 `xxx.py`，实现 `generate(spec)`。
2. 在 `convert.py` 的 `FORMATS` 登记模块名与输出文件。
3. 无需修改核心 `build_spec.py`（核心解析与输出解耦）。

### 配置（config.py）

`TOKEN_CATEGORIES` / `KEYWORDS` / `OPERATORS` / `PUNCTUATION` / `REFERENCE_PREFIXES`
是人工知识库：GRAMMAR.pest 的字面量本身不携带语义类别，脚本按此归类；
未命中任何类别的 token 会登记为未分类并告警（需求 3.7 / 3.8）。

补全知识库同样位于 `config.py`：`BUILTIN_FUNCTIONS`（内置函数白名单）、`PIPELINE_OPERATORS`
（filter/map/reduce）、`RETRY_EXCEPTIONS`（可重试异常）、`TRIGGER_TYPES`（触发器类型与字段）、
`WORKFLOW_BLOCKS`（顶层块）、`STRUCTURE_TEMPLATES`（控制流/结构模板）、`SNIPPETS`（常用片段）、
`PAIR_RULES`（括号/引号配对与缩进）、`ERROR_CODES`（错误码速查）、`COMPLETION_REF_PREFIXES`
（ref 前缀）。这些语义无法从字面量自动推断，故集中维护为补全规范的唯一人工事实来源。

新增 DSL 语法的标准流程（需求 14.26）：
1. 在 `GRAMMAR.pest` 加规则、必要时在 `AST.rs` 加节点。
2. 若新关键字属于既有类别，脚本自动识别；若属于新类别，在 `config.py` 补充。
3. 运行 `build_spec.py --force` → `convert.py --format all`。
4. 检查生成产物 diff 并跑测试，提交同步后的高亮文件。
