# PrivateCloudDisk-IM 离线拉取与消息状态精简设计文档

> 版本：v3.0
> 覆盖：消息状态精简为四态、离线消息客户端主动 HTTP 拉取（Redis 多级缓存）、
>       游标分页历史消息、客户端 SDK 的 HTTP 与 WebSocket 渠道整合。

---

## 1. 背景与目标

原架构中消息状态多达 7 态（SENDING / SENT / DELIVERED / READ / FAILED / RECALLED / DELETED），
离线消息依赖"用户上线事件 → 服务端自动重投"链路，状态语义冗余、离线补偿链路复杂。

本次改造目标：

1. **消息状态精简为四种核心投递状态**：PREPARING / DELIVERED / READ / FAILED。
2. **离线消息由服务端自动推送改为客户端主动 HTTP 拉取**，简化补偿链路、减少服务端状态机。
3. **引入 Redis 多级缓存**，加速离线消息拉取，并保证与数据库的一致性。
4. **新增游标分页历史消息接口**，仅返回终态消息，避免未送达消息进入历史。

---

## 2. 消息状态（四态）

`MessageStatus` 枚举（`im-common/.../enums/MessageStatus.java`）精简为四种：

| 状态 | code | 含义 | 进入条件 |
|------|------|------|----------|
| `PREPARING` | 0 | 准备中：已持久化，尚未推送/拉取 | 消息入库 |
| `DELIVERED` | 1 | 已送达：已推送或已拉取 | 在线推送成功（DeliveredEvent）/ 离线拉取接口 |
| `READ` | 2 | 已读：接收方已查看 | 客户端上报已读回执（MessageReadEvent） |
| `FAILED` | 3 | 失败：无法送达 | 推送失败（FailedEvent）/ 权限校验失败不入库 |

### 状态转换规则

```
入库 ──────────────► PREPARING
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
 在线推送成功      离线拉取接口     推送失败事件
 (DeliveredEvent)  (GET /offline)   (FailedEvent)
        │             │             │
        ▼             ▼             ▼
   DELIVERED ──► READ ◄───────── FAILED
       （客户端上报已读）
```

- 在线推送成功后：IM Platform 消费 `MessageDeliveredEvent` → 状态置为 `DELIVERED`。
- 客户端拉取离线消息后：接口将消息批量置为 `DELIVERED`。
- 推送失败：IM Platform 消费 `MessageFailedEvent` → 状态置为 `FAILED`。
- 已读：IM Platform 消费 `MessageReadEvent` → 状态置为 `READ`。
- 权限校验失败：直接不入库（不产生 PREPARING 记录），必要时回推发送失败事件。

### 可见性状态（不属于投递生命周期）

- `RECALLED = 5`、`DELETED = 6` 作为可见性状态保留（撤回 / 删除可见性过滤），
  由 `ImConstants.RECALLED_STATUS` / `DELETED_STATUS` 表示，不参与四态生命周期。

### 数据库迁移

见 `sql/migration_status_4state.sql`：旧值 SENDING(0)/SENT(1) → PREPARING(0)，
DELIVERED(2)→1、READ(3)→2、FAILED(4)→3，RECALLED/DELETED 保持不变；
并新增 `(receiver_id, status)` 与 `(conversation_id, server_seq, status)` 联合索引。

---

## 3. 离线消息客户端主动拉取（Redis 多级缓存）

### 3.1 写入端（IM Router，保持不变）

- IM Router 消费 `PushMessageCommand` 时，若接收方离线（Redis 无节点映射）或 gRPC 返回
  用户不在线，将消息写入 Redis List `im:offline:{userId}`（`im-router/internal/offline/offline.go`）。
- 存储格式：Protobuf 序列化的 `PushMessageCommand` 二进制，便于直接解析。
- TTL 7 天，超过容量上限（1000）用 `LTRIM` 淘汰最旧。

### 3.2 拉取接口（IM Platform 新增）

`GET /api/im/messages/offline?userId={userId}&limit={limit}`（默认 100，最大 100）

业务逻辑（`MessageServiceImpl.getOfflineMessages`）：

1. **优先读 Redis** `im:offline:{userId}`（LRANGE 前 limit 条）。
   - 命中：弹出/清空该 key，解析 Protobuf 为 `MessageDTO`，返回客户端。
   - 未命中：**降级查询数据库** `receiver_id = userId AND status = PREPARING`，按 `server_seq` 倒序。
2. 拉取到的消息 `message_id` 批量更新为 `DELIVERED`（幂等：重复拉取不重复更新）。
3. 只允许用户拉取自己的离线消息（`userId` 即当前登录用户）。

### 3.3 数据一致性保障

- **缓存与 DB 一致**：同一条消息既在 Redis（Router 写入）也在 DB（PREPARING）；
  拉取时无论命中哪一层，最终都以 DB 的 `message_id` 批量置为 `DELIVERED`，并清空 Redis 缓存。
- **幂等**：`batchUpdateStatus` 按 `message_id` 更新，重复调用安全。
- **兜底**：Redis 异常/丢失时自动降级查库，不影响功能。
- 需要定期补偿扫描离线队列与数据库不一致时可扩展定时任务（预留）。

---

## 4. 游标分页历史消息接口

`GET /api/im/messages/history/cursor?conversationId={}&userId={}&limit={}&cursor={}&before={}`

- `limit`：默认 20，最大 100。
- `cursor`：上一页最小 `server_seq`（首次不传），游标分页避免 offset 深分页性能问题。
- `before`：可选，拉取该时间之前的消息。

业务规则（`MessageServiceImpl.getHistoryByCursor`）：

- 仅返回终态消息：`DELIVERED / READ / FAILED`（`status IN (1,2,3)`）。
- **不含 PREPARING**：未送达消息应由离线拉取接口或 WebSocket 实时推送获取，
  不进入历史记录，避免与其他渠道重复/漏取。
- 权限：仅允许查询与当前用户相关的会话。

> 兼容说明：原有 `GET /api/im/messages/history`（conversationId + page + size，offset 分页）
> 保留用于向后兼容，并同样过滤掉 PREPARING 消息。

---

## 5. 客户端 SDK 渠道整合（HTTP + WebSocket）

### 5.1 Java im-client

`ImClient` 新增：

- `getOfflineMessages(userId, limit)`：封装 `/api/v1/messages/offline`。
- `getHistoryByCursor(conversationId, userId, limit, cursor, before)`：封装游标历史。

### 5.2 Web SDK（`PrivateCloudDisk-web/src/api/im`）

`imApi.ts` 新增 `getOfflineMessagesApi` / `getMessageHistoryByCursorApi`。

`ImWebSocketClient` 新增整合能力：

- `connectAndSync()`：连接 → 握手认证 → 拉取离线消息 → 启动实时监听的一体化入口。
- `loadHistory(conversationId, cursor?, limit?)`：游标分页拉取历史。
- `onMessageReceived(callback)`：统一消息回调，同时覆盖 WebSocket 实时推送与 HTTP 拉取消息。
- 连接成功（密钥协商完成）后，若配置了 `userId`，自动调用 `pullOfflineViaHttp()`
  拉取 PREPARING 离线消息并派发；未配置 `userId` 时回退到 WebSocket `SYNC_OFFLINE` 命令。

### 5.3 推荐流程

```
首次连接：
  WebSocket 连接 + 握手认证
      → 连接成功
      → HTTP GET /im/messages/offline 拉取离线消息（置为 DELIVERED）
      → 展示到对应会话
      → 监听 WebSocket 实时消息，新消息实时追加

进入聊天详情：
  HTTP GET /im/messages/history/cursor 拉取第一页
      → 上滑时用 cursor 继续拉取更早消息
      → WebSocket 该会话新消息实时追加到底部

WebSocket 重连：
  重连成功后重新拉取离线消息，恢复实时监听
```

---

## 6. 相关文件清单

- 状态枚举：`im-common/.../enums/MessageStatus.java`
- 常量：`im-common/.../constant/ImConstants.java`
- 实体/表：`im-platform/.../entity/ImMessage.java`、`sql/init.sql`
- Mapper：`im-platform/.../mapper/ImMessageMapper.java` + `.xml`
- 服务：`im-platform/.../service/impl/MessageServiceImpl.java`
- 接口：`im-platform/.../controller/MessageController.java`
- 事件消费：`im-platform/.../mq/EventConsumer.java`
- 迁移脚本：`sql/migration_status_4state.sql`
- 离线存储（Router，不变）：`im-router/internal/offline/offline.go`
- Java 客户端：`im-client/.../ImClient.java`、`impl/ImClientHttpImpl.java`
- Web SDK：`PrivateCloudDisk-web/src/api/im/imApi.ts`、`ImWebSocketClient.ts`
