# PrivateCloudDisk-IM

企业级实时通信（IM）子项目，为 PrivateCloudDisk 提供即时通讯能力。

## 项目结构

```
PrivateCloudDisk-im/
├── pom.xml                  # 父工程 POM（统一依赖管理）
├── im-common/               # 公共模块（DTO、协议、事件、枚举、常量）
├── im-platform/             # IM 业务平台（HTTP REST API 服务）
├── im-server/               # WebSocket 长连接推送服务（Netty）
├── im-router/               # 实时路由服务（Go：路由查询 / 回调通知策略）
├── im-client/               # 后端内部 SDK（供其他业务模块集成）
├── sql/
│   └── init.sql             # 数据库初始化脚本
├── docs/
│   ├── im-architecture-design.md          # IM 分布式架构设计文档
│   ├── im-message-loop-risk-report.md     # IM 送达/失败事件循环风险分析报告
│   ├── im-offline-history-and-status.md   # 离线主动拉取 + 消息状态精简 + 游标历史 + SDK 整合
│   └── im-offline-history-audit-report.md # 离线/状态改造审计与交付报告
└── README.md
```

## 模块说明

### im-common — 公共模块

| 类别 | 内容 |
|------|------|
| 枚举 | `MessageType`, `MessageStatus`, `ConversationType`, `GroupRole`, `CommandType`, `ResponseCode` |
| DTO | `MessageDTO`, `ConversationDTO`, `GroupDTO`, `GroupMemberDTO`, `Result<T>` |
| 协议 | `MessageProtocol`（WebSocket 通信协议，含命令字路由） |
| 事件 | `MessageEvent`, `UserOnlineEvent`, `ConversationEvent` |
| 常量 | `ImConstants`（Redis Key、MQ 路由、业务限制） |

### im-platform — HTTP 业务服务

基于 Spring Boot 3.4.7 + MyBatis + Redis + RabbitMQ，提供 REST API：

| 模块 | 端点 | 功能 |
|------|------|------|
| 消息管理 | `/api/v1/messages/**` | 发送、撤回、已读、历史查询、增量同步 |
| 会话管理 | `/api/v1/conversations/**` | 创建、列表、置顶、免打扰、删除 |
| 群组管理 | `/api/v1/groups/**` | 创建、加入、退出、踢人、禁言、解散 |
| 健康检查 | `/api/v1/health` | 存活探针 |

### im-server — WebSocket 推送服务

基于 Netty 的高性能长连接服务：

- **NettyWebSocketServer**：Boss/Worker 线程模型，负责 WebSocket 长连接和实时消息推送；连接规模与部署资源、配置和压测结果有关
- **SessionManager**：用户-Channel 映射，多端登录，连接数限制
- **V2AuthHandler**：WebSocket 握手认证，JWT Token 校验
- **V2MessageHandler / V2MessageRouter**：v2 协议命令字路由（心跳/消息/已读/同步/信令）
- **MessagePushService**：在线推送 + 离线队列存储
- **IMServerServiceImpl（gRPC）**：接收 IM Router 的 PushMessage 推送，**推送即送达**，
  成功后发布 `MessageDeliveredEvent`，失败（未找到连接/异常）发布 `MessageFailedEvent`
- **EventPublisher**：统一发布用户上下线、消息送达/失败、已读等 MQ 事件

### im-client — 后端 SDK

Spring Boot Starter 自动装配，业务模块引入依赖后直接注入使用：

```java
@Autowired
private ImClient imClient;

// 发送消息
imClient.sendMessage(messageDTO);

// 获取会话列表
imClient.getConversations(userId);

// 创建群组
imClient.createGroup(ownerId, "群组名", null);
```

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.4.7 | 应用框架 |
| Netty | 4.1.115 | WebSocket 长连接 |
| MyBatis | 3.0.4 | ORM |
| Redis | - | 在线状态、离线队列、分布式锁 |
| RabbitMQ | - | 消息异步投递 |
| Snowflake | - | 全局唯一 ID 生成 |
| Jackson | 2.18.x | JSON 序列化 |
| SpringDoc | 2.8.x | API 文档 |

## 快速开始

### 1. 初始化数据库

```sql
mysql -u root -p private_cloud_disk < sql/init.sql
```

### 2. 编译项目

```bash
cd PrivateCloudDisk-im
mvn clean install -DskipTests

export MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" && cd /Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-im/im-server && "$MVN" spring-boot:run

export MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" && cd /Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-im/im-platform && "$MVN" spring-boot:run

cd /Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-im/im-router && go run ./cmd/router

export MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" && java -version 2>&1 | head -3 && cd /Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-im && "$MVN" clean install -DskipTests
```

### 3. 启动 im-platform

```bash
cd im-platform
mvn spring-boot:run
```

### 4. 启动 im-server

```bash
cd im-server
mvn spring-boot:run
```

### 5. 在其他服务中集成 im-client

```xml
<dependency>
    <groupId>org.project</groupId>
    <artifactId>im-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 消息流转

### 正常消息推送链路（推送即送达，无 ACK、无重发）

```
发送方客户端 → WebSocket → im-server
                              ↓ SendMessageCommand
                        im-platform（权限校验、持久化）
                              ↓ PushMessageCommand
                          im-router → gRPC → im-server（接收方所在节点）
                                              ↓ WebSocket
                                          接收方客户端（一经发出即视为送达）
                                              ↓
                                     发布 MessageDeliveredEvent（已送达）
```

### 送达/失败事件与回执通知链路（防闭环设计）

`im-server` 消息一经发出即视为送达：推送成功发布 `MessageDeliveredEvent`，
推送失败（未找到连接 / 异常）发布 `MessageFailedEvent`。IM Router 消费送达/失败事件后，
按消息类型判定是否向发送方回推 `RECEIPT` 回执：

- `CHAT_MESSAGE` / `UNSPECIFIED`：有发送方概念 → 回推回执（已送达 / 推送失败 / 发送失败）。
- `RECEIPT` / `ERROR_MESSAGE` / `SYSTEM_NOTIFICATION` / `CUSTOM_NOTIFICATION`：
  无发送方概念 → 仅记录日志并 ACK，不回推新回执（切断闭环）。
- `MessageSendFailedEvent`（业务层校验/入库失败）→ 始终向发送方回推 `SEND_FAILED` 回执。

```
im-server 推送成功 / 推送失败
        ↓
发布 MessageDeliveredEvent / MessageFailedEvent（携带 message_type）
        ↓ RabbitMQ im.message.delivered/failed.event
        ├──→ im-platform：普通消息更新状态为 FAILED；通知消息仅记录日志
        └──→ im-router：
                ├── CHAT_MESSAGE / UNSPECIFIED → 查询发送方所在 im-server
                │           → 以 RECEIPT 类型推送回执（送达/推送失败/发送失败）
                └── RECEIPT / ERROR / SYSTEM / CUSTOM → 仅记录日志 + ACK，
                        不再通知发送方（切断无限闭环）
```

> **防闭环关键点**：回执本身（`RECEIPT`）没有发送方概念，若对回执再次生成回执会形成
> 无限回调闭环。故 IM Router 仅对普通聊天消息（`CHAT_MESSAGE`/`UNSPECIFIED`）回推回执，
> 对回执/通知/错误类消息仅记录日志并 ACK，不再生成新回执。
> 回执推送失败时仅记录日志，不重试、不产生新回执。

## 消息状态（四态）与离线消息主动拉取

- **状态精简**：`MessageStatus` 精简为四种投递状态 `PREPARING / DELIVERED / READ / FAILED`；
  撤回/删除作为可见性状态保留（`RECALLED_STATUS=5`、`DELETED_STATUS=6`）。
  数据库迁移见 `sql/migration_status_4state.sql`。
- **离线消息改为客户端主动拉取**：接收方离线时由 IM Router 写入 Redis `im:offline:{userId}`，
  客户端上线后调用 `GET /im/messages/offline`（Redis 多级缓存 → DB 降级）拉取，
  拉取后批量置为 `DELIVERED`。
- **游标历史接口**：`GET /im/messages/history/cursor` 仅返回
  `DELIVERED / READ / FAILED` 终态消息，游标 = `server_seq`，支持分页。
- **SDK 整合**：Web `ImWebSocketClient` 新增 `connectAndSync / loadHistory / onMessageReceived`，
  连接成功后优先 HTTP 拉取离线消息；Java `im-client` 新增 `getOfflineMessages / getHistoryByCursor`。

详细设计见 `docs/im-offline-history-and-status.md`。

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `IM_PLATFORM_PORT` | 8088 | im-platform 端口 |
| `NETTY_WS_PORT` | 9090 | WebSocket 端口 |
| `MYSQL_HOST` | localhost | MySQL 地址 |
| `REDIS_HOST` | localhost | Redis 地址 |
| `RABBITMQ_HOST` | localhost | RabbitMQ 地址 |
