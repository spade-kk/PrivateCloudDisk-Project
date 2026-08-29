# CloudFlow Language Server（CloudFlow LS）

`cloudflow-ls` 是 CloudFlow DSL/YAML 的 Language Server Protocol（LSP）实现：它在编辑期连接 IDE 与现有 CloudFlow 编译器前端，为补全、诊断、Hover、跳转、引用、重命名、格式化、语义 Token 与折叠提供统一服务。

它不是 CloudFlow Runtime，不执行工作流，也不实现第二套 Parser/AST/表达式/类型或语义规则。

可编辑架构图：[`../architecture/cloudflow-language-server.drawio`](../architecture/cloudflow-language-server.drawio)。图的 C4 源模型位于同目录的 `cloudflow-language-server.c4.json`；打开 `.drawio` 可查看“上下文 → IDE 客户端 → LS 组件 → Capability Hub 权限边界”四个可钻取页面。

## 职责边界

| 模块 | 负责 | 明确不负责 |
| --- | --- | --- |
| `cloudflow-engine-core` | 领域 AST、诊断、表达式、IR 和执行语义核心 | HTTP/MQ/数据库、IDE 协议 |
| `pcd-cloudflow-runtime` 的 `compiler-api` 面 | DSL Pest Parser、YAML 前端、现有语义规则与编译调度入口 | Runtime 服务面（HTTP、持久化、MQ、gRPC Agent） |
| `cloudflow-ls` | LSP 会话、文档版本、UTF-16 坐标、Compiler 分析投影、动态能力适配 | 工作流执行、能力注册、直接访问业务数据库 |
| `syntax-highlight` | 静态 TextMate/Monarch/Highlight.js、Snippet、基础补全规范的生成与转换 | 动态类型、符号、权限或 Capability Hub 查询 |
| Capability Hub | 已注册能力、Schema、权限和可用性策略的控制面事实 | 编辑器协议、浏览器 DSL 解析 |

`cloudflow-ls` 通过 `pcd-cloudflow-runtime` 的 `compiler-api` feature 调用实际的 `parse_frontend_detailed` 与 `semantic::validate_with_rules`，因此 DSL 和 YAML 的诊断、表达式行为与 `cloudflowc`/Runtime 一致。`runtime-service` feature 下的 HTTP、MQ、持久化与 Agent 代码被显式条件编译隔离，LS 不调用工作流执行面。

## 能力与认证模型

Action 不是静态语言关键字。LS 在需要 Action completion/Hover/signature help 时调用：

```text
GET {CLOUDFLOW_CAPABILITY_API}/capabilities?page=1&size=100
Authorization: Bearer <access-token>
X-Space-Id: <optional-space>
X-Tenant-Id: <optional-tenant>
```

Gateway 验证 Bearer Token 后注入可信 `X-User-Id`；Workflow Service 的 `CapabilityHubService.searchVisibleTo` 再按以下条件筛选：

1. 请求必须有认证主体；缺失时返回 `401 WF-CAPABILITY-UNAUTHENTICATED`。
2. `required_permissions_json` 必须被当前用户在当前空间得到的授权集合完全覆盖。
3. `availability_policy_json.enabled=false`、不匹配的 `tenant_ids`/`tenantIds` 或 `space_ids`/`spaceIds` 均拒绝显示。
4. 策略 JSON 不合法、列表类型不合法、要求 tenant/space 但上下文缺失时采用 **deny-by-default**。
5. 缓存键是 `SHA-256(token) + tenantId + spaceId`，从不存储或输出 Token 明文；默认 TTL 为 5 分钟。

LS 收到 `cloudflow/capabilitiesChanged` 自定义 LSP notification（由 Web Studio/IDE 的平台插件或权限事件桥接）后，只失效当前会话的能力缓存，并重新分析其打开文档。用户也可以执行 `workspace/executeCommand` 的 `cloudflow.clearCapabilityCache`。

认证失败或离线不会阻塞本地语言能力：LS 仍复用 Compiler 提供 DSL/YAML 解析和语义诊断，只是不把“能力目录暂不可用”误判为“所有 Action 不存在”。

## 传输与启动

| 场景 | 命令 / 协议 | 说明 |
| --- | --- | --- |
| VS Code 本地 | `cloudflow-ls --stdio` | 标准 `Content-Length` LSP framing；扩展启动子进程。 |
| JetBrains / Desktop 本地服务 | `cloudflow-ls --tcp 127.0.0.1:5007` | TCP + 标准 LSP framing。仅绑定 loopback，除非已有 mTLS/网络隔离。 |
| macOS/Linux 本地服务 | `cloudflow-ls --unix-socket /tmp/cloudflow-ls.sock` | Unix Domain Socket；创建后权限设为 `0600`，若路径已存在则拒绝覆盖。 |
| Web Studio | `cloudflow-ls --websocket 127.0.0.1:5007` | `/lsp` 路径的 JSON-RPC WebSocket。生产环境必须经 Nginx/网关以 WSS 终止 TLS。 |

常用环境变量：

```bash
export CLOUDFLOW_CAPABILITY_API='https://gateway.example.com/api/v1'
export CLOUDFLOW_TOKEN_FILE="$HOME/.cloudflow/token"
chmod 600 "$CLOUDFLOW_TOKEN_FILE"
export CLOUDFLOW_TENANT_ID='tenant-a'        # 可选
export CLOUDFLOW_SPACE_ID='space-a'          # 可选
cargo run -p cloudflow-ls -- --stdio
```

`CLOUDFLOW_TOKEN` 优先于 Token 文件；本地文件默认是 `~/.cloudflow/token`，Unix 下若权限对 group/other 开放，LS 会拒绝启动。WebSocket 浏览器不能自由设置 Authorization header，因此 Web Studio 仅在 **WSS** 连接上通过 `initialize.initializationOptions.accessToken` 提供短生命周期 Access Token；服务端不得记录该字段，并建议由同源网关替代为已认证 WebSocket 握手。

## LSP 功能矩阵

| LSP 方法 | CloudFlow LS 行为 |
| --- | --- |
| `initialize` / `initialized` / `shutdown` / `exit` | 协商 UTF-16 坐标、增量文本同步和服务器能力。 |
| `didOpen` / `didChange` / `didClose` | 会话内文档管理、版本冲突检查、UTF-16 → UTF-8 Span 转换、诊断发布。 |
| `completion` | 关键字、变量、步骤输出和当前用户已授权的 Capability Hub Action。 |
| `hover` / `signatureHelp` | AST 符号说明；已授权能力的描述、输入 Schema 和参数。 |
| `definition` / `references` / `rename` | 当前文档变量、步骤、Action 等符号投影；rename 仅接受 CloudFlow 标识符。 |
| `documentSymbol` / `semanticTokens/full` / `foldingRange` | 从 AST/symbol 投影文档轮廓、语义 token 与块折叠。 |
| `formatting` | 确定性空白格式化；绝不改写表达式或工作流语义。 |
| `cloudflow/capabilitiesChanged` | 当前会话能力缓存失效并重新发布打开文档诊断。 |

文档同步是 LSP `TextDocumentSyncKind::Incremental`：LS 只接收并应用变更区间，未改变版本直接命中 AST/诊断缓存。当前底层 Pest Parser 的公共 API 仍以完整前端 AST 作为原子输入，因此发生有效编辑时会用**同一份** Compiler Parser 重新生成该文档 AST；这不是浏览器或 LS 自行实现的第二套增量 Parser。后续若 Compiler Core 暴露子树级增量 Parse API，`Document.dirty_lines` 已保留变更窗口供无破坏接入。

## IDE 集成

### VS Code

`syntax-highlight/vscode` 扩展保留 TextMate、高亮、Snippet 和离线基础补全，并使用 `vscode-languageclient` 启动 `cloudflow-ls --stdio`。发布 VSIX 时，`npm run vsce:package` 会从当前 Compiler 规范重新生成 Grammar，并把目标平台的 `bin/<platform>-<arch>/cloudflow-ls[.exe]` 一起打入扩展；用户默认无需单独下载 LS。`cloudflow.lsp.serverPath` 默认值为 `bundled`，仍可由绝对路径、PATH 命令名或 `~/` 路径覆盖。

LS 的 `executeCommandProvider` 会声明 `cloudflow.clearCapabilityCache`。`vscode-languageclient` 的 ExecuteCommandFeature 会根据该声明自动注册 VS Code 命令，因此扩展入口**不能**再次调用 `vscode.commands.registerCommand` 注册同名命令；扩展只保留登录、登出、状态展示和重启命令。重复注册会在初始化阶段产生 `command already exists`，导致整个 LSP client 连接失败。

扩展命令：

- `CloudFlow: 登录平台`：打开可配置 OAuth2 页面并把短期 Access Token 保存到 VS Code Secret Storage。
- `CloudFlow: 登出平台`：清除 Secret Storage Token 并重启 LS。
- `CloudFlow: 清理能力缓存`：发送 `cloudflow.clearCapabilityCache`。
- `CloudFlow: 重启语言服务`：重新启动 stdio 子进程。

配置项见扩展 `package.json` 的 `cloudflow.lsp.*` 与 `cloudflow.auth.loginUrl`。生产 OAuth2 应使用平台回调/Device Flow 自动交付短期令牌；当前通用扩展保留可配置授权页和安全输入，避免把某个部署的 OAuth endpoint 硬编码进开源语言扩展。内置 LS 的查找优先级是当前平台目录、兼容的 `bin/cloudflow-ls` 回退、PATH 中的 `cloudflow-ls`；自定义路径不会经 shell 解析。

### Web Studio / Monaco

设置 `VITE_CLOUDFLOW_LSP_URL=wss://<host>/cloudflow-lsp/lsp` 后，`WorkflowEditorView` 将 Token/空间上下文交给 `CloudFlowLspMonacoBridge`。该 Bridge 只做 JSON-RPC 适配，注册 completion、Hover、definition、references、rename 和 diagnostics；它不复制 Rust Parser、正则 DSL 检查或 Capability 权限逻辑。未配置该变量时，Monarch 与基础 completion 规则继续由 `syntax-highlight` 提供。

推荐由 Nginx/网关把 `/cloudflow-lsp/lsp` 反向代理到 `cloudflow-ls --websocket`，并严格配置 WebSocket Origin、TLS、认证和最大连接数。不要把 Runtime internal service token 或 Capability Hub 数据库凭据发送到浏览器。

### JetBrains 与 Desktop Studio

JetBrains 可用通用 LSP Client 连接 TCP/UDS 模式；Desktop Studio 可启动同一个 LS 进程、通过 UDS/TCP 连接，或在未来将 `cloudflow-ls` 嵌入 Rust 宿主。它们使用同一 LSP 协议和 Capability Provider，不自行复制语言语义。

## 安全与资源边界

- 单个 LSP 文档与单条 JSON-RPC/WS 帧上限均为 256 KiB / 1 MiB 级保护（见源码常量）；超限返回协议错误而非耗尽内存。
- 每个 stdio 进程或服务器连接创建独立 `LspSession`：打开文档、认证、取消标记、AST/分析缓存不会跨用户共享。
- TCP/WS 服务端不直接访问业务数据库；能力查询仅经 HTTPS Platform API。生产 WS 使用 WSS；TCP 部署在 loopback、私网或 mTLS 网关之后。
- 日志和错误不得记录 bearer token、密码或内部资源路径；缓存 identity 只保留 token SHA-256。
- `$/cancelRequest`、Content-Length 校验、WebSocket frame 限制、Unix Socket `0600` 与文档版本校验共同限制恶意请求与资源耗尽。

## 验证命令

本次可重复验证与仍需真实部署环境执行的端到端项见 [VALIDATION.md](VALIDATION.md)。

```bash
cd PrivateCloudDisk-cloudflow-runtime
cargo fmt --all -- --check
cargo check -p cloudflow-ls
cargo test -p cloudflow-ls
# 证明 LS 只编译 Compiler API 面，不启用 Runtime service module：
cargo check -p pcd-cloudflow-runtime --no-default-features --features compiler-api

cd syntax-highlight/vscode
npm ci
node --check src/extension.js
npm run test:extension
npm run prepare:extension
# 生成并校验内置 Grammar、LS 二进制和 vscode-languageclient 运行时依赖：
# 历史 *.vsix 不会再嵌入新扩展包：
npx @vscode/vsce package --out /tmp/cloudflow-language.vsix
unzip -l /tmp/cloudflow-language.vsix | grep -E 'extension/(bin/.*/cloudflow-ls|syntaxes/cloudflow.tmLanguage.json)'
```

跨服务定向测试：

```bash
cd PrivateCloudDisk-workflow-service
./gradlew test --tests org.project.workflow.service.CapabilityHubServiceExtTest \
  --tests org.project.workflow.controller.CapabilityInvokeWebTest
```

架构图结构校验：

```bash
python3 /Users/user/.codex/skills/drawio-skill/scripts/validate.py \
  docs/architecture/cloudflow-language-server.drawio --score
```
