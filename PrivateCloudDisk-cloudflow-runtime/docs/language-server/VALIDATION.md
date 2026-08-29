# CloudFlow Language Server 验证记录

本文件记录 `cloudflow-ls` 的可重复构建、协议边界和跨服务验证命令。它区分已在工作树执行的自动化验证与仍需部署环境完成的端到端验证，避免把编译通过误写成生产联通结论。

## 已执行的定向验证

| 范围 | 命令 | 通过标准 |
| --- | --- | --- |
| Rust 格式 | `cargo fmt --manifest-path Cargo.toml --all -- --check` | 没有格式差异。 |
| LS 单元测试 | `cargo test --manifest-path Cargo.toml -p cloudflow-ls` | 文档 UTF-16 编辑、Token 哈希、能力缓存租户/空间隔离、Compiler Parser/语义复用、Content-Length framing、初始化诊断和 capability-change 会话刷新均通过。 |
| Compiler/Runtime 隔离 | `cargo check --manifest-path Cargo.toml -p pcd-cloudflow-runtime --no-default-features --features compiler-api` | LS 所依赖的 compiler-api 不启用 Runtime service 模块。 |
| LS 启动面 | `cargo run --manifest-path Cargo.toml -p cloudflow-ls -- --help` | `--stdio`、`--tcp`、`--unix-socket`、`--websocket` 和认证/缓存参数可发现。 |
| Web Studio | `npm run build`（`PrivateCloudDisk-web`） | Monaco LSP bridge、编辑器接入和生产 bundle 可通过 Vite 类型/打包检查。 |
| VS Code 语法 | `node --check src/extension.js` | Extension host 客户端脚本可解析。 |
| VS Code 扩展契约 | `npm run test:extension` | 命令只由 LSP ExecuteCommandFeature 注册一次；内置 Grammar 与 `build/` 完全一致；配置默认使用 `bundled`。 |
| VS Code 静态产物 | `npm run prepare:extension` | 从统一规范重新生成 Grammar/补全，并将当前平台 release `cloudflow-ls` 复制到 `bin/<platform>-<arch>/`。 |
| VS Code 包 | `npx @vscode/vsce package --out /tmp/cloudflow-language.vsix` + `unzip -l` | 扩展包包含 `vscode-languageclient` 生产依赖、同步后的 Grammar 和 `bin/.../cloudflow-ls`；旧 `*.vsix` 不会嵌套进新包。 |
| VS Code LSP 握手 | 对内置 `bin/.../cloudflow-ls --stdio` 发送 `initialize` | 返回 `jsonrpc=2.0`、`serverInfo.name=CloudFlow Language Server` 和 `executeCommandProvider`，不再出现重复命令初始化错误。 |
| Capability Hub | `./gradlew test --tests org.project.workflow.service.CapabilityHubServiceExtTest --tests org.project.workflow.controller.CapabilityInvokeWebTest` | 缺失用户身份、权限、tenant/space policy、畸形策略和既有 Web 调用回归通过。 |
| 架构图 | `python3 /Users/user/.codex/skills/drawio-skill/scripts/validate.py docs/architecture/cloudflow-language-server.drawio --score` | draw.io XML 结构校验为 `0 error(s), 0 warning(s)`。 |

## 尚需在真实部署环境执行

以下项依赖实际 Gateway、OAuth2、Capability Hub、Nginx/WSS 和至少两个 IDE 客户端，因此不能由离线工作树构建替代：

1. 使用有效短期 Token，通过 Gateway 请求 `/api/v1/capabilities`，分别验证用户、空间、tenant 和权限变更后的可见能力集合。
2. 以 WSS 运行 Web Studio，验证 Token 不出现在浏览器日志、反向代理日志或 URL 中；验证 Origin allowlist、并发连接和断线恢复。
3. 在 VS Code Extension Development Host 中启动 `cloudflow-ls --stdio`，覆盖 DSL 与 YAML 的 completion、Hover、definition、references、rename、diagnostics 与 Token 过期后的静态降级。
4. 使用 JetBrains 的 LSP Client 和 Desktop Studio 分别连接 TCP/UDS；TCP 必须置于 loopback、mTLS 或可信私网中。
5. 在插件安装/下架、空间授权、tenant policy 变更后，通过客户端事件桥接发送 `cloudflow/capabilitiesChanged`，确认只刷新目标会话。
6. 对大文档、畸形 JSON-RPC、超大帧、连接风暴和跨用户并发会话做压测/安全测试。

## 解释增量解析边界

LSP 文本同步已经按增量 range 应用，未变版本会复用分析缓存。现有 Compiler Frontend 的公共 Pest API 仍以完整文档作为 AST 原子输入，因此一次有效编辑会通过同一份 Parser 重新构建该文档 AST；LS 没有伪造第二套 Parser。`Document.dirty_lines` 已记录变更窗口，待 Compiler Core 提供子树级 API 后可无破坏接入真正的语法树增量重解析。
