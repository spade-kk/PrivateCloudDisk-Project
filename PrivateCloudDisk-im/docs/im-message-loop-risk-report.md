# IM 消息送达/失败事件循环风险分析报告

> 对应需求：全面审计循环风险点、区分消息类型、建立可扩展的通知策略、杜绝死循环。
> 审计对象：IM Server 事件产生场景、IM Router 事件消费逻辑、IM Business 状态更新逻辑。
> 模型基线：**推送即送达**（无客户端 ACK 跟踪、无指数级重发）；送达/失败事件统一产生，
> 由 IM Router 按消息类型判定是否向发送方回推 `RECEIPT` 回执。

---

## 1. 审计范围与结论

### 1.1 IM Server 的送达/失败事件产生（无 ACK 跟踪）

IM Server **不再**对 gRPC 推送的消息启动 ACK 跟踪，也不再指数级重发。消息一经发出即视为送达：

- 推送成功 → 直接发布 `MessageDeliveredEvent`（消息已送达）。
- 未找到接收方连接或推送异常 → 发布 `MessageFailedEvent`（消息推送失败，`fail_code` 1/2）。
- **无论消息类型如何**，IM Server 都产生送达/失败事件（不做类型过滤）。

推送消息可归纳为以下类型（对应 `MessageType` 枚举）：
- 普通聊天消息（`CHAT_MESSAGE`）：TEXT / IMAGE / FILE / VOICE / VIDEO / STICKER / CALL 等，有发送方概念。
- 回执通知（`RECEIPT`）：IM Router 生成、推回给原发送方的送达/失败/发送失败回执，本身无发送方概念。
- 错误消息（`ERROR_MESSAGE`）：如权限拒绝、禁言。
- 系统通知（`SYSTEM_NOTIFICATION`）：如群聊解散、好友申请、群事件。
- 自定义通知（`CUSTOM_NOTIFICATION`）：预留扩展。

### 1.2 高风险循环路径（及对应防闭环策略）

| # | 循环路径 | 风险等级 | 防闭环策略 |
|---|----------|----------|-----------|
| 1 | 普通消息 → 送达事件 → Router 回推回执 → 回执又产生送达事件 → 再回执... | **高** | 回执消息类型为 `RECEIPT`，Router 判定不通知 → 切断 |
| 2 | 普通消息 → 失败事件 → Router 回推失败回执 → 回执推送失败 → 再回执... | **高** | 回执类型 `RECEIPT` 不通知；回执推送失败仅记录日志 |
| 3 | 任意通知类送达/失败事件 → Router 一律回调 | **高** | 仅 `CHAT_MESSAGE`/`UNSPECIFIED` 回执，通知类不回调 |

---

## 2. 原问题路径（旧模型，已移除）

```
发送方 ──发送──▶ IM Business ──PushCommand──▶ IM Router ──gRPC──▶ IM Server ──WS──▶ 接收方
                                                                                     │ 未 ACK
                                                                                     ▼
                                                                              FailedEvent ──▶ IM Router ──通知发送方──▶ IM Server ──WS──▶ 发送方
                                                                                                                │ 未 ACK（网络/客户端异常）
                                                                                                                ▼
                                                                                                         FailedEvent(回执) ──▶ IM Router
                                                                                                                              │ 仍按"通知发送方"处理
                                                                                                                              ▼
                                                                                                                     再次推送通知 → 无限循环 ✗
```

**旧模型根因**：IM Server 对包括回执在内的所有消息都做 ACK 跟踪与指数重发；IM Router 消费
`delivered/failed` 事件时未区分"普通消息回调"与"通知类回调"，导致回执通知自身失败/送达后
再次触发向发送方推送通知，形成无限回调闭环。此模型已整体移除（无 `AckRetryManager`、
无 `DELIVERY_ACK` / `FAILURE_ACK`、无 `MESSAGE_ACK` 命令）。

---

## 3. 现网流转图（推送即送达 + RECEIPT 回执）

### 3.1 普通聊天消息（需要回执）

```
发送方 ──发送──▶ IM Business ──PushCommand(CHAT_MESSAGE)──▶ IM Router ──gRPC──▶ IM Server ──WS──▶ 接收方
                                                                                                  │ 推送即送达（无 ACK）
                                                                                                  ▼
                                                          DeliveredEvent / FailedEvent(CHAT_MESSAGE)
                                                                                                  │
                                                                                                  ▼
                                                      IM Router: policy.ShouldNotify(CHAT_MESSAGE)=true
                                                                                                  │
                                                                                                  ▼
                                                      gRPC 推送回执（RECEIPT，携带原事件负载）
                                                                                                  │
                                                                                                  ▼
                                                                  发送方客户端（多端）展示"已送达/推送失败/发送失败"
```

- `MessageSendFailedEvent`（业务层权限/会话/入库失败）**始终**向发送方回推 `SEND_FAILED` 回执。
- 送达/推送失败事件仅在 `message_type` 为 `CHAT_MESSAGE` / `UNSPECIFIED` 时才回推回执。

### 3.2 回执/通知类（切断闭环）

```
回执通知（RECEIPT）被推送 → IM Server 产生 DeliveredEvent(RECEIPT)
                            → IM Router: policy.ShouldNotify(RECEIPT)=false
                            → 记录日志 + ACK，不推送任何新通知 ✓（闭环切断）
通知/错误类（SYSTEM/ERROR/CUSTOM）送达或失败 → policy.ShouldNotify(通知类)=false
                            → 记录日志 + ACK，不推送新通知 ✓
回执推送本身失败 → 记录日志，不重试、不产生新回执 ✓
```

### 3.3 未知类型 / 旧事件

- 未知 `message_type`：保守策略不通知，并触发告警。
- 旧事件（`message_type` 未设置 = `UNSPECIFIED`）：归一化为 `CHAT_MESSAGE`，
  保持向后兼容的通知行为。

---

## 4. 修复落点对照

| 需求点 | 落点 |
|--------|------|
| 统一 `MessageType` 枚举（含 `RECEIPT`） | `im_mq.proto`（`MessageType` 枚举 + 各消息体 `message_type` 字段） |
| 事件携带原始消息 ID / 发送方 | `MessageDeliveredEvent` / `MessageFailedEvent` 新增 `original_message_id` / `original_sender_id` |
| IM Server 推送即送达、产生并透传类型 | `EventPublisher` / `IMServerServiceImpl`（不再有 `AckRetryManager`） |
| Router 回执判定策略（防闭环） | `im-router/internal/router/policy.go`（`shouldNotifyMessageType`） |
| Router 送达/失败/发送失败分支 | `NotifyDelivered` / `NotifyFailed` / `NotifySendFailed` |
| 回执信封（客户端识别） | `im_protocol_v2.proto`：`IMMessageType.RECEIPT=93`、`ReceiptPayload`、`ReceiptStatus` |
| 客户端展示与多端同步 | `PrivateCloudDisk-web`（`notificationStore.handleReceipt`）、`im-test-client`（RECEIPT 解析） |
| IM Business 状态过滤 | `im-platform/.../mq/EventConsumer.java` |

---

## 5. 验证方式

- **Go 单测**：`im-router` 的 `policy_test.go`、`router_test.go`
  （覆盖 `shouldNotifyMessageType` 各类型、送达/失败通知类切断闭环、旧事件向后兼容）。
- **Java**：im-server / im-platform 离线编译通过。
- **Web**：`PrivateCloudDisk-web` 的 `vue-tsc` 对 `api/im` 模块无类型错误。
- **测试客户端**：`im-test-client` 的 `npm test` 通过（含 `ReceiptPayload` 编解码用例）。
- **集成（建议）**：模拟普通聊天消息推送失败（应产生一次失败回执）与回执通知推送失败
  （应仅记录日志不再产生新事件），验证不再产生循环事件。
