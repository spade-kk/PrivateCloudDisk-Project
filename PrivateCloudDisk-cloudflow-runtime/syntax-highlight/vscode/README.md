# CloudFlow DSL — VS Code 语法高亮 + 代码补全扩展

为 `.flow` / `.cloudflow` 文件提供 CloudFlow 工作流 DSL 语法高亮与代码补全。

> 该扩展的 `.tmLanguage.json` 由统一规范 `syntax-highlight/build/cloudflow.syntax-highlight.json`
> 生成（生成器 `convert.py --format tmLanguage`）。**请勿手动修改 `syntaxes/cloudflow.tmLanguage.json`**，
> 需修改时请更新 `src/grammar.pest` / `src/ast.rs` 并重新生成。

## 特性

- 关键字（控制流 / 声明 / 类型 / 字面量）高亮
- 字符串（双引号、三双引号多行、`${...}` 插值）高亮
- 数字、时长字面量高亮
- 变量引用 `vars.` / `steps.` / `workflow.` 高亮
- 操作符、标点、注释（`#`）高亮
- 亮色 / 暗色主题自动适配（TextMate scope 随主题配色）
- **代码补全**：关键字 / 顶层块 / 控制流结构 / 内置函数 / 类型 / 触发器 / 片段
  （`src/extension.js` 提供 CompletionItem + SignatureHelp，规则来自
  `syntaxes/cloudflow.completion.json`，由 `grammar.pest` + `AST.rs` + `config.py` 生成）
- 括号 / 引号自动配对、缩进、`#` 注释（`language-configuration.json`）

## 安装

本仓库提供了两种方式：

### 方式一：安装 `.vsix`（推荐）
```bash
cd syntax-highlight/vscode
npm install -g @vscode/vsce   # 仅打包时需要
vsce package                  # 生成 cloudflow-language-*.vsix
code --install-extension cloudflow-language-1.2.0.vsix
```

### 方式二：本地开发目录（F5 调试）
1. 用 VS Code 打开 `syntax-highlight/vscode` 目录。
2. 按 `F5` 启动扩展开发宿主（Extension Development Host）。
3. 打开任意 `.flow` 文件即可看到高亮。

## 更新高亮规则

修改 `src/grammar.pest` / `src/ast.rs` 后：
```bash
python3 syntax-highlight/generator/build_spec.py --force
python3 syntax-highlight/generator/convert.py --format tmLanguage
cp syntax-highlight/build/cloudflow.tmLanguage.json syntax-highlight/vscode/syntaxes/cloudflow.tmLanguage.json
```

## 更新代码补全规则

修改 `src/grammar.pest` / `src/ast.rs`  `syntax-highlight/generator/config.py` 后：
```bash
python3 syntax-highlight/generator/completion_builder.py --force         # 代码补全规范
# VS Code 片段 + Web 拷贝
python3 syntax-highlight/generator/completion_convert.py --web <web-dir> # 生成 cloudflow.code-snippets 派发到 syntax-highlight/vscode/snippets目录
```

## 语言配置

- 注释：`#`（单行）
- 括号匹配 / 自动闭合：`{}` `[]` `()` `""` `""""`
- 折叠标记：`# region` / `# endregion`
