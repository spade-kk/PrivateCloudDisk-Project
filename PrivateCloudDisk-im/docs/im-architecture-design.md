# PrivateCloudDisk-IM 分布式架构设计文档

> 本文档为 IM 子项目（im-common / im-platform / im-server / im-router / im-client）
> 的架构设计说明，重点覆盖：消息推送链路、送达/失败事件处理、
> 消息类型枚举（MessageType）及其扩展方式。

---

## 1. 总体拓扑

```
                         ┌──────────────────┐
                         │  IM Platform      │  im-platform (Spring Boot)
  发送方 HTTP/WS ───────▶│  (IM Business)   │  权限校验 / 会话校验 / 入库 / 状态管理
                         └────────┬─────────┘
                                  │ im.message.push.command (MQ)
                                  ▼
                         ┌──────────────────┐
                         │  IM Router       │  im-router (Go)
                         │  路由查询(Redis)  │  消费 PushCommand → gRPC 推送
                         │  回执通知        │  消费送达/失败事件 → 回推给发送方
                         └────────┬─────────┘
                                  │ gRPC PushMessageRequest
                                  ▼
                         ┌──────────────────┐
                         │  IM Server       │  im-server (Netty)
                         │  WebSocket 推送   │  推送即送达 → DeliveredEvent
                         └────────┬─────────┘  推送失败 → FailedEvent
                                  │
                             客户端 WebSocket
```

---

## 2. 消息推送链路与送达/失败事件、回执通知

1. **发送**：发送方通过 HTTP 或 WebSocket 提交消息到 IM Platform。
2. **业务处理**：IM Platform 校验权限、会话、内容，持久化消息（初始状态 PREPARING），
   产生 `PushMessageCommand` 发布到命令队列。
3. **路由**：IM Router 消费 `PushMessageCommand`，查询 Redis 得到接收方所在 IM Server 节点，
   通过 gRPC 调用该节点的 `PushMessage`。
4. **推送**：IM Server 通过 WebSocket 将消息帧推送给接收方客户端。
5. **送达/失败事件（消息一经发出即视为送达，无 ACK、无重发）**：
   - 推送成功 → 直接发布 `MessageDeliveredEvent`（消息已送达）。
   - 未找到接收方连接或推送异常 → 发布 `MessageFailedEvent`（消息推送失败）。
   - 无论消息类型如何，IM Server 都产生送达/失败事件（不做类型过滤）。
6. **回执通知（IM Router 消费送达/失败事件，回推给发送方）**：
   - IM Router 消费 `MessageDeliveredEvent` / `MessageFailedEvent` / `MessageSendFailedEvent`。
   - 按消息类型判定是否需要向发送方回推回执（见第 3 节防闭环策略）。
   - 需要回执 → 以 `RECEIPT` 类型、携带原始事件负载，通过 gRPC 推送到发送方所在节点。
   - IM Server 将事件转换为客户端可识别的回执信封（`ReceiptPayload`），
     推送给该账户**所有**连接（多端同步：手机/网页都能看到发送状态）。
7. **状态消费**：IM Platform 消费送达/失败事件，按 `message_type` 过滤，
   仅对普通聊天消息更新消息状态（DELIVERED / FAILED）。
8. **离线消息（客户端主动拉取）**：接收方离线时，IM Router 将消息写入 Redis 离线队列
   `im:offline:{userId}`（多级缓存第一层），同时消息在数据库为 PREPARING。
   接收方客户端上线后主动调用 `GET /im/messages/offline` 拉取离线消息，
   接口优先读 Redis、未命中降级查库，拉取后批量置为 DELIVERED（详见
   《IM 离线拉取与消息状态精简文档》）。

---

## 3. 消息类型枚举（MessageType）与回执防闭环策略

所有推送相关消息体通过 `message_type` 字段引用统一的
`MessageType` 枚举（定义于 `im-common/src/main/proto/im_mq.proto`）。

| 枚举值 | 含义 | 是否向发送方回推回执 |
|--------|------|---------------------|
| `MESSAGE_TYPE_UNSPECIFIED` | 未指定（旧事件向后兼容，按 CHAT_MESSAGE 处理） | 是 |
| `CHAT_MESSAGE` | 普通聊天消息（文本/图片/文件等），有发送方概念 | **是** |
| `RECEIPT` | 回执通知本身（无发送方概念） | 否（切断闭环） |
| `ERROR_MESSAGE` | 服务端错误消息（如权限拒绝、禁言） | 否 |
| `SYSTEM_NOTIFICATION` | 系统通知（如群聊解散、好友申请、群事件） | 否 |
| `CUSTOM_NOTIFICATION` | 自定义业务通知（预留扩展） | 否 |

**核心防闭环原则（消息类型驱动）**：
> 仅当 `message_type` 为 `CHAT_MESSAGE`（或旧事件 `UNSPECIFIED`）时，
> IM Router 才向原始发送方回推回执；回执本身（`RECEIPT`）、系统/错误/通知类消息
> 没有发送方概念，不触发回执，因此"回执再触发回执"的无限闭环被从根上切断。
> 若回执推送失败 → 记录日志；若回执已送达 → 直接 ACK，不再产生新回执。

### 相关字段

- `PushMessageCommand.message_type` / `PushMessageRequest.message_type`：
  推送请求的统一定位类型（由 IM Business / IM Router 设定，IM Server 原样保留）。
- `MessageDeliveredEvent` / `MessageFailedEvent` 额外携带：
  - `message_type`：产生该事件的消息类型。
  - `original_message_id`：最原始的聊天消息 ID（通知类指向其触发的原始消息）。
  - `original_sender_id`：最原始的发送方用户 ID。

### 回执信封（im_protocol_v2.proto）

- `IMMessageType.RECEIPT = 93`：回执消息类型。
- `ReceiptPayload`：`original_message_id`、`conversation_id`、`sender_id`、`receiver_id`、
  `status`（`RECEIPT_DELIVERED` / `RECEIPT_PUSH_FAILED` / `RECEIPT_SEND_FAILED`）、
  `fail_code`、`fail_reason`、`receipt_at`。
- 客户端据此将该条消息标记为已送达 / 推送失败（如红色感叹号）/ 发送失败，
  并支持同一账户多端通过 WebSocket 同步聊天发送状态。

---

## 4. 回执链路与状态更新

### IM Router 回执通知（消费送达/失败/发送失败事件）

- `DeliveredHandler` / `FailedHandler` / `SendFailedHandler`：反序列化对应事件并调用 `Notifier`。
- `NotifyDelivered` / `NotifyFailed`：按 `message_type` 判定（仅 CHAT_MESSAGE/UNSPECIFIED 回执），
  查询发送方所在节点后以 `RECEIPT` 类型回推。
- `NotifySendFailed`：发送失败事件（业务层校验/入库失败）总是向发送方回推 `SEND_FAILED` 回执。
- 发送方离线或回推失败 → 记录日志，不重试、不产生新回执。

### IM Platform 状态更新（消费送达/失败事件）

`im-platform/.../mq/EventConsumer.java`：

- `CHAT_MESSAGE`（含 UNSPECIFIED 兼容）→ 更新对应消息状态（DELIVERED / FAILED）。
- `RECEIPT` / `ERROR_MESSAGE` / `SYSTEM_NOTIFICATION` / `CUSTOM_NOTIFICATION`
  → 无对应业务记录，仅记录日志并 ACK，不更新业务状态。

---

## 5. 新增消息类型扩展指南（extension guide）

新增一种需要推送的消息类型时，按以下步骤即可：

1. **`.proto` 层**：在 `im_mq.proto` 的 `MessageType` 枚举中新增取值，
   并在需要区分时补充字段说明注释；随后重新生成 Go（`im-router/pkg/proto/generate.sh`）
   与 Java（Maven `im-common` 的 protobuf 插件）代码。
2. **IM Server**：在推送该类型消息时，于 `PushMessageRequest.message_type` 设置新类型；
   IM Server 会在产生 Delivered/FailedEvent 时原样保留并带上 `original_message_id/sender_id`。
3. **IM Router**：若新类型有发送方概念需要回执，加入回执判定集合；
   无需回执（通知类）则保持默认（不加入）。
4. **IM Business**：在 `EventConsumer` 中决定是否需要处理该类型的状态更新。
5. **文档**：同步更新本架构文档第 3 节表格与 `im_mq.proto` 注释。

---

## 6. 相关文档

- 《IM 消息循环风险分析报告》：`docs/im-message-loop-risk-report.md`
- Protobuf 接口定义：`im-common/src/main/proto/im_mq.proto`、`im-common/src/main/proto/im_grpc.proto`
