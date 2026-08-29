# CloudFlow DSL — VS Code 静态高亮 + CloudFlow LS 扩展

为 `.flow` / `.cloudflow` 文件提供 CloudFlow 工作流 DSL 静态语法高亮、Snippet、离线基础补全，
并默认随 VSIX 携带当前平台的 `cloudflow-ls`，提供动态诊断、类型、符号、跳转、重命名和按权限过滤的 Action 智能。

> 该扩展的 `.tmLanguage.json` 由统一规范 `syntax-highlight/build/cloudflow.syntax-highlight.json`
> 生成（生成器 `convert.py --format tmLanguage`）。**请勿手动修改 `syntaxes/cloudflow.tmLanguage.json`**，
> 需修改时请更新 `src/grammar.pest` / `crates/cloudflow-engine-core/src/ast.rs` 并重新生成。

## 两层能力与职责边界

| 层 | 负责 | 不负责 |
| --- | --- | --- |
| `syntax-highlight`（本扩展静态层） | TextMate 高亮、语言配置、Snippet、生成规范驱动的关键字/基础补全 | Parser、AST、类型系统、租户权限、动态插件能力 |
| `cloudflow-ls`（Rust LSP） | 编译器诊断、符号、类型、动态能力 completion/hover/signature help、definition/references/rename | Runtime 执行、能力注册、业务数据库访问 |

LS 成功连接后，静态补全 provider 自动不再返回动态结果，避免重复候选；LS 未安装、离线或 Token 失效时，静态层自动继续工作。

## 静态特性

- 关键字（控制流 / 声明 / 类型 / 字面量）高亮
- 字符串（双引号、三双引号多行、`${...}` 插值）高亮
- 数字、时长字面量高亮
- 变量引用 `vars.` / `steps.` / `workflow.` 高亮
- 操作符、标点、注释（`#`）高亮
- 亮色 / 暗色主题自动适配（TextMate scope 随主题配色）
- **离线基础补全**：关键字 / 顶层块 / 控制流结构 / 内置函数 / 类型 / 触发器 / 片段
  （`src/extension.js` 提供 CompletionItem + SignatureHelp，规则来自
  `syntaxes/cloudflow.completion.json`，由 `grammar.pest` + `AST.rs` + `config.py` 生成）
- 括号 / 引号自动配对、缩进、`#` 注释（`language-configuration.json`）

## CloudFlow Language Server

本扩展默认从 VSIX 内置目录以 stdio 启动 `cloudflow-ls`，不需要用户另行安装语言服务器。当前平台的二进制路径为：

```text
bin/<platform>-<arch>/cloudflow-ls[.exe]
```

例如 Apple Silicon macOS 是 `bin/darwin-arm64/cloudflow-ls`，Windows 是
`bin/win32-x64/cloudflow-ls.exe`。发布流水线应分别为目标平台构建 VSIX；单个 VSIX
只携带它所声明的目标平台二进制，避免把其他平台的可执行文件误当作当前文件。

若需要使用用户自行构建的版本，仍可在设置中覆盖 `cloudflow.lsp.serverPath`：

```bash
cd PrivateCloudDisk-cloudflow-runtime
npm --prefix syntax-highlight/vscode run prepare:extension
# 也可以把 CLOUDFLOW_LS_BIN 指向已经构建好的 cloudflow-ls
```

VS Code 设置：

```json
{
  "cloudflow.lsp.enabled": true,
  "cloudflow.lsp.serverPath": "bundled",
  "cloudflow.lsp.tokenFile": "/Users/me/.cloudflow/token",
  "cloudflow.lsp.tenantId": "optional-tenant",
  "cloudflow.lsp.spaceId": "optional-space",
  "cloudflow.auth.loginUrl": "https://platform.example.com/oauth/authorize"
}
```

`cloudflow.lsp.serverPath` 的默认值 `bundled` 会解析到扩展目录下与当前
`process.platform`/`process.arch` 对应的二进制；填写绝对路径、PATH 中的命令名或以 `~/`
开头的路径即可覆盖。找不到内置文件时会最后回退到 PATH 中的 `cloudflow-ls`，并在状态栏
显示静态降级。扩展不会通过 shell 执行该路径。

`cloudflow.lsp.tokenFile` 必须是 owner-only（Unix `0600`）文件。也可通过命令面板执行：

- `CloudFlow: 登录平台`：打开配置的 OAuth2 授权页，随后将短期 Access Token 存入 VS Code Secret Storage；
- `CloudFlow: 登出平台`：删除 Secret Storage Token 并重启 LS；
- `CloudFlow: 清理能力缓存`：让 LS 对当前用户/租户/空间重新查询 Capability Hub；
- `CloudFlow: 重启语言服务`：重新启动 `cloudflow-ls --stdio`。

生产 OAuth2 推荐 Platform callback 或 Device Flow。扩展故意不硬编码任意部署的 OAuth URL，也不把 Token 写入工作区设置、日志或补全缓存。仓库开发环境中的完整协议、Web Studio 与多 IDE 部署说明位于 `docs/language-server/README.md`。

## 安装

本仓库提供了两种方式：

### 方式一：安装 `.vsix`（推荐）
```bash
cd syntax-highlight/vscode
npm ci                              # 安装 vscode-languageclient
npm run test:extension              # 校验命令注册、Grammar 同步和打包契约
npm install -g @vscode/vsce         # 仅打包时需要
npm run vsce:package                # 生成并打包静态规则 + 当前平台 cloudflow-ls
code --install-extension cloudflow-language-1.3.1.vsix --force
```

`npm run vsce:package` 会按固定顺序执行：从 `GRAMMAR.pest` 与 Compiler AST 生成统一
规范、生成 TextMate Grammar/补全文件、同步扩展内置 Grammar、构建 release 版
`cloudflow-ls`，最后由 `vsce` 将 `bin/` 一并写入 VSIX。不要直接复制旧 VSIX，也不要
只运行 `vsce package` 绕过准备步骤。

跨平台发布示例（在对应 Rust target 工具链/构建机上执行）：

```bash
# Linux x64
CLOUDFLOW_LS_TARGET=x86_64-unknown-linux-gnu \
CLOUDFLOW_LS_PLATFORM=linux-x64 \
npm run vsce:package

# 使用已有产物，不重复编译
CLOUDFLOW_LS_BIN=/absolute/path/to/cloudflow-ls \
CLOUDFLOW_LS_PLATFORM=darwin-arm64 \
npm run vsce:package
```

### 方式二：本地开发目录（F5 调试）
1. 用 VS Code 打开 `syntax-highlight/vscode` 目录。
2. 按 `F5` 启动扩展开发宿主（Extension Development Host）。
3. 打开任意 `.flow` 文件即可看到高亮。

即使 LS 启动失败，扩展也会继续提供 TextMate、Snippet 和基础补全；但安装正常平台 VSIX
后，状态栏应显示“CloudFlow LS：已连接”。如果看到“静态降级”，先执行命令面板中的
`CloudFlow: 重启语言服务`，再检查 `CloudFlow Language Server` 输出面板和
`cloudflow.lsp.serverPath`。如果曾手动安装旧版本 VSIX，请用 `--force` 安装新包，避免
继续运行旧的无二进制版本。

如果日志出现 `command 'cloudflow.clearCapabilityCache' already exists`，说明运行的仍是
旧扩展或旧扩展代码同时手动注册了该命令。当前实现只在 `package.json` 声明命令，由
`vscode-languageclient` 根据 LS 的 `executeCommandProvider` 注册一次；重新安装当前 VSIX
并重启 Extension Host 后即可消除冲突。

## 更新高亮规则

修改 `src/grammar.pest` / `crates/cloudflow-engine-core/src/ast.rs` 后：
```bash
cd syntax-highlight/vscode
npm run prepare:extension
npm run test:extension
```

`prepare:extension` 是唯一推荐的扩展同步入口；它会自动复制生成的
`build/cloudflow.tmLanguage.json`，从而避免 VSIX 使用过期 Grammar。若只想生成静态产物
而不编译 LS，可单独运行 generator，但打包前仍必须运行 `npm run prepare:extension`。

## 更新代码补全规则

修改 `src/grammar.pest` / `crates/cloudflow-engine-core/src/ast.rs`  `syntax-highlight/generator/config.py` 后：
```bash
python3 syntax-highlight/generator/completion_builder.py --force         # 代码补全规范
# VS Code 片段 + Web 拷贝
python3 syntax-highlight/generator/completion_convert.py --web <web-dir> # 生成 cloudflow.code-snippets 派发到 syntax-highlight/vscode/snippets目录
```

## 语言配置

- 注释：`#`（单行）
- 括号匹配 / 自动闭合：`{}` `[]` `()` `""` `""""`
- 折叠标记：`# region` / `# endregion`
