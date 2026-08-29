# IM 离线拉取与消息状态精简 — 审计与改造报告

> 本报告为需求清单《IM 离线消息主动拉取与消息状态精简需求清单》
> 与《IM 离线消息 Redis 多级缓存优化、历史记录接口与客户端渠道整合需求清单》
> 实施前的审计结论与改造交付说明。

---

## 1. 现状审计结论

### 1.1 IM Business（im-platform）消息发送 / 状态更新现状

| 关注点 | 现状 | 结论 |
|--------|------|------|
| 状态枚举 | `MessageStatus` 7 态：SENDING/SENT/DELIVERED/READ/FAILED/RECALLED/DELETED | 需精简为 4 态 |
| 入库初始状态 | `MessageServiceImpl.sendMessage` 置为 SENDING | 改为 PREPARING |
| 送达事件消费 | `EventConsumer.onMessageDelivered` 仅 TODO 注释，未更新状态 | 实现 → DELIVERED |
| 失败事件消费 | `EventConsumer.onMessageFailed` 仅 TODO 注释，未更新状态 | 实现 → FAILED |
| 已读事件消费 | `EventConsumer.onMessageRead` 已调用 `markAsRead` | 保持不变 |
| 历史查询 | `getHistory`（conversationId+page+size，offset 分页） | 新增游标版本，过滤 PREPARING |

### 1.2 IM Router（im-router）离线存储 / 用户上线处理现状

| 关注点 | 现状 | 结论 |
|--------|------|------|
| 离线写入 Redis | `im-router/internal/offline/offline.go` 已实现 `im:offline:{userId}`（Protobuf 二进制，TTL 7 天，LTRIM 限 1000） | 保留（多级缓存第一层） |
| 用户上线事件 | Router **不消费** user online 事件；离线重投本应由 IM Business 触发 | 保持不消费，改为客户端主动拉取 |
| 自动重投 | `EventConsumer.onUserOnline` 仅 TODO（`compensateOfflineMessages` 未实现） | 移除自动重投，改为客户端主动拉取 |

### 1.3 IM Server 现状

- 推送链路已是"消息一经发出即视为送达"：推送成功发布 `MessageDeliveredEvent`，
  失败发布 `MessageFailedEvent`，无 ACK、无指数重发（沿用既有改造）。
- 无离线消息主动拉取接口，离线由 Router 落 Redis + 客户端 HTTP 拉取。

### 1.4 客户端 SDK 现状

- Java `im-client`：仅有消息发送/撤回/会话/群组等接口，无离线拉取、无游标历史。
- Web `ImWebSocketClient`：连接后通过 WebSocket `SYNC_OFFLINE` 命令同步离线，
  无 HTTP 离线拉取、无 `loadHistory`、无统一渠道回调。

---

## 2. 改造交付清单

### 2.1 状态精简（四态）

- [x] `im-common/.../enums/MessageStatus.java`：精简为 PREPARING/DELIVERED/READ/FAILED。
- [x] `ImConstants.java`：新增 `RECALLED_STATUS=5`、`DELETED_STATUS=6` 及拉取条数常量。
- [x] `ImMessage.java`、`sql/init.sql`：更新 status 字段注释与默认值。
- [x] `ImMessageMapper.xml`：历史查询过滤 PREPARING；`batchUpdateRead` 使用新 READ 码。
- [x] `sql/migration_status_4state.sql`：旧状态值映射 + 新增索引。

### 2.2 离线消息主动拉取接口

- [x] `GET /im/messages/offline`（`MessageController`）+ `MessageServiceImpl.getOfflineMessages`
  （Redis 多级缓存 → DB 降级 → 批量置 DELIVERED）。
- [x] `MessageService` / `ImMessageMapper` 新增 `selectOfflineMessages`、`batchUpdateStatus`。
- [x] `RedisConfig` 新增 `byteArrayRedisTemplate` 以读取 Router 写入的二进制离线队列。

### 2.3 游标历史消息接口

- [x] `GET /im/messages/history/cursor`（`getHistoryByCursor`），仅返回
  `status IN (DELIVERED, READ, FAILED)`，游标 = `server_seq`，支持 `before`。

### 2.4 事件消费状态更新

- [x] `EventConsumer.onMessageDelivered` → `updateStatus(messageId, DELIVERED)`。
- [x] `EventConsumer.onMessageFailed` → `updateStatus(messageId, FAILED)`。
- [x] `EventConsumer.onUserOnline` → 仅记录上线状态，移除离线补偿（改客户端主动拉取）。
- [x] `MessageService.updateStatus` 实现。

### 2.5 客户端 SDK

- [x] Java `im-client`：新增 `getOfflineMessages` / `getHistoryByCursor`。
- [x] Web `imApi.ts`：新增 `getOfflineMessagesApi` / `getMessageHistoryByCursorApi`。
- [x] Web `ImWebSocketClient.ts`：新增 `connectAndSync` / `loadHistory` / `onMessageReceived`、
      `pullOfflineViaHttp`；连接成功后优先 HTTP 拉取离线消息。
- [x] Web `notificationStore.connectRealtime`：为客户端注入 `userId` 以启用 HTTP 离线拉取。

### 2.6 测试

- [x] `EventConsumerTest`：送达→DELIVERED、失败→FAILED、通知类不更新、上线仅记录。
- [x] `MessageServiceImplTest`（新增）：离线拉取 Redis 命中/未命中降级、批量置 DELIVERED；
      游标历史仅查终态白名单。

### 2.7 文档

- [x] `docs/im-architecture-design.md`：更新初始状态与离线拉取小节。
- [x] `docs/im-offline-history-and-status.md`：四态、离线拉取、游标历史、SDK 整合详述。
- [x] `docs/im-offline-history-audit-report.md`：本报告。
