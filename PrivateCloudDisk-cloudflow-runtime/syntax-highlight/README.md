# CloudFlow 统一语法高亮

本目录承载 CloudFlow DSL 的**统一语法高亮与代码补全系统**：以 `GRAMMAR.pest` + `AST.rs` 为唯一事实来源，
自动生成高亮规范（VS Code、Monaco、Highlight.js 三处）与补全规范（VS Code 扩展、前端 Monaco 两处）。
**前端与编辑器插件不硬编码任何 CloudFlow 高亮正则或补全规则**，改 DSL 语法后重新生成即可全平台同步。

```
syntax-highlight/
├── generator/                 # 解析与转换脚本（见 generator/README.md）
├── schema/                    # 统一规范 JSON Schema
├── build/                     # ★ 生成产物（勿手改）：
│   ├── cloudflow.syntax-highlight.json   # 唯一事实来源
│   ├── cloudflow.tmLanguage.json         # VS Code TextMate
│   ├── cloudflow.monarch.json / .ts      # Monaco Monarch
│   └── cloudflow.hljs.js                 # Highlight.js（UMD）
├── vscode/                    # VS Code 扩展（package.json / language-configuration / tmLanguage）
├── samples/                   # 示例 .flow（高亮回归样本）
└── demo/highlight-demo.html   # Highlight.js 网页演示
```

## 使用

```bash
# 一键生成全部产物（高亮 + 补全 + 三格式 + Web 分发）
python3 generator/generate.py --verbose
# 分步：build_spec.py（高亮）/ completion_builder.py（补全）/ convert.py（三格式）
# 测试（语法高亮 + 补全，现 55 项）
python3 -m unittest discover -s generator/tests -p "test_*.py"
```

- **前端 Monaco**：`PrivateCloudDisk-web/src/languages/cloudflow.ts` 引用
  `build/cloudflow.monarch.json`（需复制产物到 `src/languages/cloudflow.monarch.json`）；
  `cloudflowCompletion.ts` 引用 `build/cloudflow.completion.json` 注册补全/签名帮助。
- **VS Code 扩展**：`vscode/` 目录，`vsce package` 打包 `.vsix`。
- **网页 Highlight.js**：`build/cloudflow.hljs.js`（UMD，ESM/CJS 均可），或打开 `demo/highlight-demo.html` 预览。

详细说明见 `generator/README.md`、`../docs/CLOUDFLOW_LANGUAGE_EXTENSION_GUIDE.md` 与 `../docs/CLOUDFLOW_COMPLETION.md`。
