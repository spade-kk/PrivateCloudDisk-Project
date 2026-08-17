# PrivateCloudDisk-IM

企业级实时通信（IM）子项目，为 PrivateCloudDisk 提供即时通讯能力。

## 项目结构

```
PrivateCloudDisk-im/
├── pom.xml                  # 父工程 POM（统一依赖管理）
├── im-common/               # 公共模块（DTO、协议、事件、枚举、常量）
├── im-platform/             # IM 业务平台（HTTP REST API 服务）
├── im-server/               # WebSocket 长连接推送服务（Netty）
├── im-client/               # 后端内部 SDK（供其他业务模块集成）
├── sql/
│   └── init.sql             # 数据库初始化脚本
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
- **AuthHandler**：WebSocket 握手认证，JWT Token 校验
- **MessageHandler**：命令字路由（心跳/消息/ACK/已读/同步）
- **MessagePushService**：在线推送 + 离线队列存储
- **MessageConsumer**：RabbitMQ 消费者，异步消息推送

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

```
客户端 → WebSocket → im-server → RabbitMQ → im-platform（持久化）
                                              ↓
客户端 ← WebSocket ← im-server ← RabbitMQ ←──┘
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `IM_PLATFORM_PORT` | 8088 | im-platform 端口 |
| `NETTY_WS_PORT` | 9090 | WebSocket 端口 |
| `MYSQL_HOST` | localhost | MySQL 地址 |
| `REDIS_HOST` | localhost | Redis 地址 |
| `RABBITMQ_HOST` | localhost | RabbitMQ 地址 |
