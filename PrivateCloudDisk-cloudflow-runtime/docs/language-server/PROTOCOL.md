# CloudFlow LS 协议与客户端契约

## JSON-RPC 与 framing

CloudFlow LS 遵循 LSP 的 JSON-RPC 2.0 请求/响应格式：

```json
{"jsonrpc":"2.0","id":1,"method":"textDocument/completion","params":{"textDocument":{"uri":"file:///demo.flow"},"position":{"line":8,"character":10}}}
```

stdio、TCP 和 Unix Domain Socket 使用 LSP 标准 framing：

```text
Content-Length: <UTF-8 JSON byte length>\r\n
\r\n
<JSON-RPC payload>
```

WebSocket `/lsp` 直接传 JSON 文本帧或 UTF-8 二进制帧；消息体仍是相同 JSON-RPC。四种传输最终都进入同一个 `LspSession`，所以行为、错误码、认证和文档隔离完全一致。

## 初始化

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "processId": null,
    "rootUri": null,
    "capabilities": {"textDocument": {"completion": {"completionItem": {"snippetSupport": true}}}},
    "initializationOptions": {
      "accessToken": "short-lived-token-for-wss-only",
      "tenantId": "optional-tenant",
      "spaceId": "optional-space"
    }
  }
}
```

服务端声明 `positionEncoding=utf-16`、`textDocumentSync.change=2`，以及 completion、hover、signature help、definition、references、rename、symbol、semantic tokens、formatting、folding 与 `cloudflow.clearCapabilityCache`。`accessToken` 仅允许在 WSS 或受信本地通道上使用；stdio/TCP/UDS 建议通过进程环境或 `0600` token 文件供给。

`cloudflow.clearCapabilityCache` 是服务端通过 `executeCommandProvider` 声明的 LSP 命令。VS Code 的 `vscode-languageclient` 会在客户端初始化时自动注册该命令，因此 VS Code 扩展不得再次调用 `vscode.commands.registerCommand` 注册同名命令，否则会触发 `command ... already exists` 并导致 LanguageClient 初始化失败。扩展自身只注册登录、登出、查看能力和重启服务等客户端命令。

## 文档同步与诊断

客户端必须先 `initialize` 再发 `didOpen`。`didChange` 应携带严格递增的 `textDocument.version`，range 使用 UTF-16 行/列。收到语法、YAML Schema、表达式、类型、DAG 或已授权能力不存在的诊断时，服务端发送：

```json
{
  "jsonrpc":"2.0",
  "method":"textDocument/publishDiagnostics",
  "params":{
    "uri":"file:///demo.flow",
    "version":2,
    "diagnostics":[{
      "range":{"start":{"line":5,"character":2},"end":{"line":5,"character":10}},
      "severity":1,
      "code":"CFxxxx",
      "source":"cloudflow-compiler",
      "message":"..."
    }]
  }
}
```

`didClose` 会删除会话内文档/分析缓存并发布空 diagnostics。版本倒退、越界 UTF-16 位置、surrogate pair 中间位置、未打开文档和超大文档均返回 `InvalidParams`，不会尝试修复或猜测用户文本。

## Capability 更新通知

平台事件桥接在检测到插件安装/卸载、能力状态变更、空间授权或租户策略变化时，对相应客户端会话发送：

```json
{"jsonrpc":"2.0","method":"cloudflow/capabilitiesChanged","params":{}}
```

这不是对所有客户端广播全量能力目录；LS 以当前会话 token、tenant 和 space 重新拉取过滤后的 `/capabilities`，然后重新发布已打开文档的 diagnostics。若桥接不可用，客户端可调用：

```json
{"jsonrpc":"2.0","id":7,"method":"workspace/executeCommand","params":{"command":"cloudflow.clearCapabilityCache","arguments":[]}}
```

## 错误与降级

| 情况 | LSP / 平台结果 | 客户端行为 |
| --- | --- | --- |
| 未初始化即编辑请求 | JSON-RPC `InvalidRequest` | 完成初始化后重试。 |
| Token 缺失或过期 | Platform API `401`；LS 不发布“所有 Action 未知” | 提示登录；保留本地 Compiler 诊断和静态高亮。 |
| 无权限或隐藏能力 | Platform API 仅返回可见集合，或 `404/403` | 不在动态补全或 Hover 中泄露能力。 |
| Capability API 离线 | LS 捕获网络错误 | 保留 AST/表达式/语义检查，待 TTL/刷新命令后恢复。 |
| 请求取消 | `$/cancelRequest` | 客户端丢弃对应请求结果；服务端不影响其他会话。 |
| 消息/文档超限 | `InvalidParams` / I/O 连接关闭 | 缩小文档或拆分工作流。 |

## Nginx WebSocket 示例

以下仅示范 WSS 反向代理。生产系统还必须配合平台 SSO、Origin allowlist、访问日志脱敏、连接数/速率限制和网络策略。

```nginx
location /cloudflow-lsp/lsp {
    proxy_pass http://cloudflow-ls:5007/lsp;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 120s;
    proxy_send_timeout 120s;
    # TLS 在外层 server 块终止；不要把 Runtime 内部令牌注入此连接。
}
```
