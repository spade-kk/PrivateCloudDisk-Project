# PrivateCloudDisk-IM 架构文档（本项目）

> 本文档基于对 `PrivateCloudDisk-im` 源码的完整审计整理，描述本项目的整体架构、
> 模块职责、服务间调用、消息发送链路、中间件使用、多节点横向扩展与部署方式。
> 审计基线：当前 `feature/space-full-integration` 分支代码（消息采用"推送即送达 + RECEIPT 回执"模型）。

---

## 1. 模块拆分与职责

| 模块 | 技术栈 | 职责 |
|------|--------|------|
| `im-common` | Java, Protobuf | 协议契约（`im_mq.proto` / `im_protocol_v2.proto` / `im_grpc.proto`）、DTO、枚举、常量、安全编解码（`IMCryptoCodec` / `IMSessionKeyManager`）、v2 Payload Codec 注册表（`MessageTypeDispatcher`） |
| `im-platform` | Java Spring Boot 3.4.7 | IM 业务平台：HTTP REST API、权限/会话/内容校验、消息持久化、`PushMessageCommand` 发布、事件消费更新状态（`EventConsumer` / `CommandConsumer` / `DeadLetterConsumer`） |
| `im-server` | Java Spring Boot + Netty 4.1.115 | WebSocket 长连接推送服务：连接管理（`SessionManager`）、v2 二进制协议握手/加解密（`V2AuthHandler` / `V2MessageHandler` / `V2MessageRouter`）、gRPC 服务端（`IMServerServiceImpl`）、事件发布（`EventPublisher`）、节点注册与心跳 |
| `im-router` | Go | 实时路由服务：消费 RabbitMQ 推送命令、Redis 查询接收方节点、gRPC 转发到 `im-server`、离线补偿、消费送达/失败事件生成 `RECEIPT` 回执（防闭环）、Prometheus 监控 |
| `im-client` | Java | 后端内部 SDK（Spring Boot Starter），供其他业务模块集成发送/查询 IM 能力 |
| `im-test-client` | Node.js | 协议调试/测试客户端（编解码、加密握手、回执解析） |
| `PrivateCloudDisk-web` | Vue3 | Web 端前端，`ImWebSocketClient` 对接 v2 二进制协议，解析回执做多端状态同步 |

---

## 2. 服务间调用与中间件

```
   发送方客户端（Web / App / im-test-client）
        │  HTTP 业务请求 或  WebSocket(v2 二进制, AES-256-GCM+HMAC)
        ▼
   ┌───────────────────┐        ┌───────────────────────────┐
   │   im-platform      │        │   im-server (Netty)       │
   │  (Spring Boot)     │        │   · SessionManager        │
   │  · 业务鉴权/入库     │        │   · v2 协议握手/加密        │
   │  · 发 PushCommand   │        │   · gRPC 服务端           │
   └─────────┬─────────┘        └────────────┬──────────────┘
             │ RabbitMQ 命令队列               │ RabbitMQ 事件
             │ (im.command.exchange)           │ (im.event.exchange)
             ▼                                │
   ┌───────────────────┐        ┌─────────────▼────────────┐
   │   im-router (Go)   │        │   im-platform (EventConsumer)│
   │  · 消费 PushCommand │        │   · 更新消息状态          │
   │  · Redis 查节点     │        └─────────────────────────┘
   │  · gRPC 推 im-server│
   │  · 消费事件→回执     │
   └─────────┬─────────┘
             │ gRPC PushMessage / BatchPushMessages
             ▼
   ┌───────────────────┐
   │   im-server (目标节点) │ ──WebSocket──▶ 接收方客户端
   └───────────────────┘
```

- **服务间通信**：`im-router ↔ im-server` 使用 **gRPC**（HTTP/2，连接池，round-robin）；其余用 RabbitMQ 异步解耦。
- **中间件**：
  - **RabbitMQ**：命令队列（`send.command` / `push.command`）+ 事件队列（`delivered` / `failed` / `send.failed` / `online` / `offline` / `read`），topic 交换机 + 独立死信队列（DLX/DLQ）+ 重试队列（TTL）。
  - **Redis**：用户→节点映射（`im:user:{userId}`，TTL=90s）、节点注册与心跳（`im:server:{nodeId}` / `im:servers`）、离线消息队列（`im:offline:{userId}`）。
  - **MySQL**：业务消息/会话/群组持久化。
  - **Protobuf**：MQ 消息体（二进制）+ v2 WebSocket 协议（`IMEnvelope`，ECDH 密钥协商 + AES-256-GCM 加解密 + HMAC 签名）。

---

## 3. 消息发送链路（推送即送达 + RECEIPT 回执）

1. **发送**：发送方经 WebSocket `SEND_MESSAGE`（或 HTTP API）提交消息。
   - WebSocket 路径：`im-server` → 发布 `SendMessageCommand` → `im-platform.CommandConsumer` 消费，走与 HTTP 相同的业务逻辑。
2. **业务处理**：`im-platform.MessageServiceImpl` 校验权限/会话/内容，生成消息 ID，持久化（状态 SENDING），发布 `PushMessageCommand`（`message_type=CHAT_MESSAGE`）到 RabbitMQ 命令队列。
3. **路由**：`im-router.PushToUser` 消费命令 → Redis 查询接收方所在节点（含节点存活/心跳校验）→ 在线则 gRPC 调用目标 `im-server`；离线则写入离线队列。
4. **推送**：`im-server.IMServerServiceImpl.PushMessage` 通过 WebSocket 推送 v2 二进制帧；**消息一经发出即视为送达**。
5. **事件**：推送成功发布 `MessageDeliveredEvent`；未找到连接/异常发布 `MessageFailedEvent`（failCode 1/2）。无论消息类型均产生事件。
6. **回执（防闭环）**：`im-router` 消费送达/失败事件，按 `message_type` 判定（仅 `CHAT_MESSAGE`/`UNSPECIFIED` 有发送方概念）→ 以 `RECEIPT` 类型回推给发送方所在节点；回执/通知/错误类仅记日志并 ACK，切断"回执再触发回执"闭环。
7. **状态消费**：`im-platform.EventConsumer` 消费事件，仅对普通聊天消息更新消息状态（DELIVERED/FAILED）。

---

## 4. 横向扩展与多节点部署

- **节点自治**：每个 `im-server` 实例启动时在 Redis 注册（`SADD im:servers` + `SET im:server:{nodeId}`），每 30s 心跳更新 `lastHeartbeat`，关闭时优雅注销。
- **用户粘性路由**：用户连接后写入 `im:user:{userId} → {nodeId}`（TTL=90s），`im-router` 据此把消息 gRPC 转推到正确节点。
- **节点存活校验**：`im-router` 路由时校验活跃集合 + 心跳时间，节点失效视为离线并异步清理。
- **路由服务本身**：`im-router` 为无状态服务，多实例消费同一 RabbitMQ 队列（Competing Consumers）天然水平扩展；gRPC 连接池按 nodeID 维护。
- **WebSocket 网关**：本项目 `im-server` 即 WebSocket 入口；可前置 Nginx/LB 做连接分发，`im-router` 通过 Redis 节点注册自动发现新节点（动态发现），无需客户端感知节点变更。

---

## 5. 设计思想小结

- **分层解耦**：协议/业务/推送/路由四层分离，业务（platform）与推送（server）不直接耦合，通过 MQ + gRPC 解耦。
- **异步事件驱动**：命令/事件走 RabbitMQ，推送即送达 + 事件回执，天然支持异步与失败补偿。
- **独立路由层**：用独立 Go 服务做路由与回执策略，职责单一、可独立扩容、可通过 Prometheus 观测。
- **安全内建**：v2 二进制协议自带 ECDH 密钥协商、AES-256-GCM 加密、HMAC 防篡改。
- **可靠性内建**：MQ 事件总线带重试/死信；节点注册心跳；离线补偿。
