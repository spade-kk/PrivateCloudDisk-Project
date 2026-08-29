# IM v2 二进制协议测试客户端

PrivateCloudDisk IM v2 协议命令行调试工具，支持完整的二进制协议收发闭环测试。

## 功能特性

- **二进制协议**：基于 Protobuf 序列化 + AES-256-GCM 加密 + HMAC-SHA256 签名
- **ECDH 密钥协商**：P-256 曲线密钥交换，自动完成握手流程
- **心跳保活**：定时心跳 + RTT 测量 + 超时检测
- **消息收发**：文本消息发送/接收，不再发送 ACK（服务端推送即视为送达）
- **推送回执解析**：解析服务端推送的 RECEIPT 回执（送达/推送失败/发送失败），展示消息推送结果
- **状态追踪**：消息状态变更（sent → delivered → read）
- **断线重连**：指数退避自动重连
- **压力测试**：并发发送多条消息（flood 命令）
- **彩色输出**：使用 chalk 区分不同类型日志
- **调试模式**：`--verbose` 输出帧十六进制 dump
- **配置灵活**：命令行参数 + JSON 配置文件

## 快速开始

### 1. 安装依赖

```bash
cd tools/im-test-client
npm install
```

### 2. 运行单元测试

```bash
npm test
```

### 3. 启动客户端

```bash
# 交互模式
node src/index.js \
  --host localhost \
  --port 9090 \
  --token <JWT_TOKEN> \
  --user <YOUR_USER_ID> \
  --to <RECEIVER_USER_ID>

# 非交互模式（发送单条消息后退出）
node src/index.js \
  --token <JWT_TOKEN> \
  --user <YOUR_USER_ID> \
  --to <RECEIVER_ID> \
  --conv <CONVERSATION_ID> \
  --send "Hello from CLI"

# 详细调试模式
node src/index.js --token <TOKEN> --user <UID> --verbose
```

### 4. 使用配置文件

创建 `my-config.json`：

```json
{
  "server": {
    "host": "192.168.1.100",
    "port": 9090
  },
  "auth": {
    "token": "eyJhbGciOi..."
  },
  "user": {
    "userId": "user-001",
    "receiverId": "user-002",
    "conversationId": "conv-001"
  },
  "heartbeat": {
    "intervalMs": 30000
  }
}
```

```bash
node src/index.js --config my-config.json
```

## 命令列表

在交互模式下输入以下命令：

| 命令 | 说明 |
|------|------|
| `help [command]` | 显示帮助 |
| `send <content> [--to <uid>] [--conv <cid>]` | 发送文本消息 |
| `to <userId>` | 设置默认接收方 |
| `conv <conversationId>` | 切换会话 |
| `read [convId] [msgId1 msgId2 ...]` | 发送已读回执 |
| `typing [on\|off]` | 发送正在输入状态 |
| `heartbeat` | 手动发送心跳 |
| `pause-heartbeat` | 暂停心跳（测试超时断开） |
| `resume-heartbeat` | 恢复心跳 |
| `status` | 显示连接状态和统计 |
| `messages` | 列出已发送消息及状态 |
| `reconnect` | 手动触发重连 |
| `disconnect` | 断开连接 |
| `flood <count> [content]` | 并发发送多条消息（压力测试） |
| `offline [limit]` | 通过 HTTP 拉取离线消息（PREPARING，拉取后置 DELIVERED） |
| `history <conversationId> [limit] [cursor]` | 通过 HTTP 游标分页拉取会话历史（仅终态） |
| `quit` | 退出程序 |

**快捷发送**：直接输入文本（非命令）即发送给当前接收方。

## 协议格式

### 帧结构

```
┌────────────────┬────────────────┬──────────────────┬──────────────────┐
│  Total Length  │  Header Length │  Encrypted Data  │  HMAC Signature  │
│   (4 bytes BE) │   (4 bytes BE) │   (variable)     │   (32 bytes)     │
└────────────────┴────────────────┴──────────────────┴──────────────────┘
```

### 握手流程

```
Client                              Server
  │                                    │
  │──── WebSocket Connect (?token=) ──→│
  │                                    │
  │←──────── SERVER_HELLO ─────────────│  (JSON 文本帧)
  │                                    │
  │───────── KEY_EXCHANGE ────────────→│  (JSON 文本帧)
  │                                    │
  │←────── KEY_EXCHANGE_RESPONSE ──────│  (JSON 文本帧)
  │                                    │
  │═══════ Binary Frames ══════════════│  (AES-256-GCM + HMAC)
  │                                    │
```

## 项目结构

```
tools/im-test-client/
├── package.json              # 项目配置
├── README.md                 # 本文档
├── config/
│   └── default.json          # 默认配置模板
├── proto/
│   └── im_protocol_v2.proto  # Protobuf 协议定义
├── src/
│   ├── index.js              # 主入口，命令行解析
│   ├── config.js             # 配置管理
│   ├── proto-loader.js       # Protobuf 消息加载
│   ├── crypto.js             # ECDH/AES-GCM/HMAC 加密
│   ├── codec.js              # 二进制帧编解码
│   ├── connection.js         # WebSocket 连接管理
│   ├── commands.js           # 命令处理器
│   ├── http.js               # HTTP REST 客户端（离线/历史拉取）
│   └── logger.js             # 彩色日志输出
└── tests/
    └── codec.test.js         # 编解码单元测试
```

## HTTP 离线/历史拉取（仿 box-im 客户端设计）

客户端通过 WebSocket 建立实时连接，同时通过 HTTP 主动拉取离线消息与历史消息，
两条渠道互补：

1. **连接就绪自动拉取离线消息**：认证成功后自动调用
   `GET /api/v1/im/messages/offline` 拉取当前用户 PREPARING 状态的离线消息
   （服务端拉取后置为 DELIVERED），并在终端展示。
2. **`offline [limit]` 命令**：手动再次拉取离线消息。
3. **`history <conversationId> [limit] [cursor]` 命令**：游标分页拉取会话历史，
   仅返回已送达/已读/失败终态消息（不含未送达 PREPARING）。

HTTP 基础地址默认 `http://localhost:8088/api/v1/im`，可通过配置
`http.baseUrl` 覆盖。发送 `Authorization: Bearer <token>` 头。

## 测试场景覆盖

| 场景 | 命令 | 说明 |
|------|------|------|
| 心跳保活 | 自动 | 连接后自动发送心跳 |
| 心跳超时 | `pause-heartbeat` | 暂停心跳观察服务端断开 |
| 文本消息 | `send` | 发送文本到指定接收方 |
| 消息接收 | 自动 | 自动解析并打印接收的消息 |
| 消息送达 | 自动 | 服务端推送即视为送达，客户端无需回复 ACK |
| 推送回执 | 自动 | 解析 RECEIPT 回执并显示送达/推送失败/发送失败状态 |
| 已读回执 | `read` | 手动发送已读回执 |
| 状态追踪 | `messages` | 查看消息状态变更 |
| 断线重连 | `reconnect` | 手动触发重连 |
| 压力测试 | `flood 1000` | 并发发送 1000 条消息 |
| 多会话 | `conv` | 切换不同会话 |

## 常见问题

### Q: 连接后立即断开

**A**: 检查 JWT Token 是否有效，服务端 WebSocket 端口是否正确（默认 9090）。

### Q: HMAC 验证失败

**A**: 密钥协商可能未完成。查看日志确认收到 `KEY_EXCHANGE_RESPONSE`，检查 ECDH 曲线是否为 P-256。

### Q: 心跳超时频繁

**A**: 调整心跳间隔 `--heartbeat 60000`（60秒），或检查网络连接质量。
