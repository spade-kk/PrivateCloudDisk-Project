# CloudFlow VS Code 扩展与 Language Server 修复审计

## 结论

本次问题不是单一的语法正则缺失，而是发布产物、Extension Host 激活和 LSP 能力注册三条链路同时存在不一致：

| 检查项 | 原状 | 影响 | 修复结果 |
| --- | --- | --- | --- |
| DSL 静态高亮 | `vscode/syntaxes/cloudflow.tmLanguage.json` 比 `syntax-highlight/build/cloudflow.tmLanguage.json` 旧，缺少 `input.*`、`env.*` 和 `null` 规则 | 打开的 `.flow` 文件无法完整按当前 Compiler 语法着色；安装历史 VSIX 时仍会使用旧规则 | 打包前自动从统一规范生成并复制，扩展 Grammar 与 `build/` 字节级一致 |
| `cloudflow.clearCapabilityCache` | LS 声明 `executeCommandProvider`，`vscode-languageclient` 会自动注册；扩展又手动 `registerCommand` | Extension Host 报 `command already exists`，LanguageClient 初始化失败，后续诊断/补全/语义能力全部不可用 | 删除扩展侧重复注册，保留 `package.json` 命令声明，由 LSP client 单点注册 |
| LS 可执行文件 | VSIX 没有 `bin/`，默认配置只能寻找 PATH 中的 `cloudflow-ls` | 用户必须手工安装和配置 Rust LS，常见结果是 `ENOENT` 后静态降级 | `prepare:extension` 构建 release 版并放入 `bin/<platform>-<arch>/`，默认 `serverPath=bundled` |
| Grammar 发布流程 | README 依赖手工复制，`vsce package` 可直接绕过同步 | 代码、生成产物、VSIX 可能三者不一致 | `npm run vsce:package` 强制先运行准备脚本，并增加契约测试 |

## 审计范围

- `PrivateCloudDisk-cloudflow-runtime/syntax-highlight/vscode/package.json`
- `PrivateCloudDisk-cloudflow-runtime/syntax-highlight/vscode/src/extension.js`
- `PrivateCloudDisk-cloudflow-runtime/syntax-highlight/vscode/syntaxes/cloudflow.tmLanguage.json`
- `PrivateCloudDisk-cloudflow-runtime/syntax-highlight/generator/*`
- `PrivateCloudDisk-cloudflow-runtime/crates/cloudflow-ls/*`
- `PrivateCloudDisk-cloudflow-runtime/docs/language-server/*`

## 根因分析

### 1. 为什么 DSL 高亮完全不显示或显示不完整

VS Code 的静态着色由 `package.json` 的 `contributes.grammars` 直接加载
`./syntaxes/cloudflow.tmLanguage.json`，与 Rust LS 是否启动无关。原扩展包中的 Grammar 时间戳和内容均来自更早的生成结果，实际已经落后于统一规范。这样会造成新增引用和字面量没有 scope；当用户安装的是历史 VSIX 时，工作区中后来更新的 `build/` 文件也不会改变已安装扩展的 Grammar。

当前发布链已经固定为：

```text
src/grammar.pest + crates/cloudflow-engine-core/src/ast.rs
        ↓
build_spec.py
        ↓
build/cloudflow.syntax-highlight.json
        ↓
convert.py --format tmLanguage
        ↓
vscode/syntaxes/cloudflow.tmLanguage.json
        ↓
VSIX
```

`npm run prepare:extension` 会自动执行这条链，不再依赖人工复制。契约测试还会对两个
Grammar 文件做完整文本相等校验，并检查 `input`、`env`、`null` 等当前规则确实存在。

### 2. 为什么 Language Server 初始化失败

`crates/cloudflow-ls/src/server.rs` 返回：

```json
{
  "executeCommandProvider": {
    "commands": ["cloudflow.clearCapabilityCache"]
  }
}
```

`vscode-languageclient` 的 ExecuteCommandFeature 收到该 capability 后，会调用 VS Code
命令注册 API。旧版扩展入口又手动注册了完全相同的命令，第二次注册即触发
`command already exists`，所以连接看似启动，实际在初始化阶段失败。

修复后职责如下：

```text
LS executeCommandProvider
        ↓
vscode-languageclient ExecuteCommandFeature
        ↓
cloudflow.clearCapabilityCache
        ↓ workspace/executeCommand
cloudflow-ls 当前会话能力缓存失效
```

扩展仍然在 `contributes.commands` 中声明该命令，使其出现在命令面板；但不再在
`extension.js` 中重复 `registerCommand`。登录、登出、状态展示和重启仍由扩展自身注册。

### 3. 为什么之前用户需要手工安装 LS

原 `vsce:package` 只执行 `vsce package`，仓库中没有打包前的 Rust 构建步骤，且原 VSIX
没有 `extension/bin/`。因此 `serverPath` 默认值 `cloudflow-ls` 只能依赖系统 PATH。

现在 `scripts/prepare-extension.mjs`：

1. 从 Compiler/AST 重新生成静态 Grammar 和补全产物。
2. 执行 `cargo build --release --locked --package cloudflow-ls`。
3. 将二进制复制到 `bin/<platform>-<arch>/cloudflow-ls[.exe]`。
4. 保留 `CLOUDFLOW_LS_BIN`，允许发布系统使用已有产物；保留 `CLOUDFLOW_LS_TARGET` 和 `CLOUDFLOW_LS_PLATFORM` 支持跨平台构建。
5. `.vscodeignore` 不忽略 `bin/`，因此 `vsce` 会把它写入最终 VSIX。

Extension Host 查找顺序为：当前平台目录 → `bin/cloudflow-ls[.exe]` 兼容回退 → PATH 中的
`cloudflow-ls`。用户设置的非 `bundled` 值仍然覆盖默认路径，并通过 `shell: false` 启动。

## 已实现的变更

- `cloudflow.lsp.serverPath` 默认值改为 `bundled`。
- 新增 `npm run prepare:extension`、`npm run test:extension`。
- `npm run vsce:package` 自动生成静态产物、构建 LS 并打包。
- 删除重复的 `cloudflow.clearCapabilityCache` 手动注册。
- 增加用户目录 `~`/`%USERPROFILE%` 路径展开兼容。
- 支持根据 Rust target 判断 Windows `.exe`，支持跨目标产物路径。
- 扩展 Grammar 已与生成 Grammar 同步。
- VS Code README、Language Server README、验证记录和统一扩展开发指南已同步。

## 验收命令

在 `PrivateCloudDisk-cloudflow-runtime/syntax-highlight/vscode` 执行：

```bash
npm run test:extension
npm run prepare:extension
node --check src/extension.js
node --check scripts/prepare-extension.mjs
```

标准 LSP 握手：

```bash
body='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"capabilities":{},"initializationOptions":{}}}'
printf 'Content-Length: %s\r\n\r\n%s' "${#body}" "$body" \
  | bin/darwin-arm64/cloudflow-ls --stdio
```

通过标准为：收到 JSON-RPC `id=1` 响应，返回 `CloudFlow Language Server`、LSP 能力集合
和 `executeCommandProvider`，进程不因能力查询失败而在初始化阶段退出。

检查 VSIX：

```bash
npm run vsce:package
unzip -l cloudflow-language-1.3.1.vsix \
  | grep -E 'extension/(bin/.*/cloudflow-ls|syntaxes/cloudflow.tmLanguage.json)'
```

真实 VS Code Extension Development Host 仍应补做打开 `.flow`、切换 Light/Dark 主题、
执行登录/登出、修改文档触发诊断、运行 `CloudFlow: 清理能力缓存` 等 UI 验收；离线工作树
可以验证协议和包内容，但不能替代真实 VS Code 版本、Gateway、OAuth2 和 Capability Hub
环境。

## 交付边界与风险

- 当前工作机生成的是 `darwin-arm64` 二进制；Linux、Windows、Intel macOS 应在对应构建机
  或 Rust target 工具链上分别执行准备脚本并发布对应 VSIX。
- VSIX 内置二进制只解决分发和启动，不绕过 Capability Hub 的用户/租户/空间权限；Token
  仍通过 VS Code Secret Storage、环境变量或 `0600` Token 文件进入 LS。
- LS 继续只依赖 `compiler-api`，不执行工作流、不访问业务数据库；静态 `syntax-highlight`
  与动态 `cloudflow-ls` 的职责边界没有改变。
