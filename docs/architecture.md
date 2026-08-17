# 系统架构设计

## 1. 总体架构

PrivateCloudDisk 采用前后端分离的微服务架构：

- Web/桌面/移动/原生客户端通过 Nginx 和 Gateway 进入系统。
- Gateway 负责认证、路由、限流、请求头治理和 WebSocket 转发。
- Platform Service 负责用户、目录、文件元数据、空间、成员权限、分享、标签、收藏、回收站和配额。
- Storage Service 与 Worker 负责文件内容、分片、下载、预览资源和生命周期处理。
- Notification、IM、Billing、Client Registration 提供外围业务能力。
- Plugin、Runtime、Automation、Workflow、Scheduler 组成扩展和自动化平台。
- MySQL、Redis、RabbitMQ、MinIO、OpenSearch 和 SkyWalking 提供数据、消息、对象、搜索和观测基础。

## 2. 服务清单与边界

| 服务 | 技术/端口 | 负责内容 |
| --- | --- | --- |
| Gateway | Spring Cloud Gateway / 8080 | 统一 API 入口、JWT、路由、限流和 WebSocket |
| Platform | Spring Boot + MyBatis / 8081 | 用户、文件元数据、目录树、空间、权限、分享、标签、收藏、回收站、配额 |
| Storage API | FastAPI / 8000 | 上传、下载、Range、预览令牌、缩略图、内容和对象访问 |
| Storage Worker | Python + RabbitMQ | 文件合并、扫描、派生资源、索引和生命周期消费者 |
| Billing | Spring Boot / 8083 | 订单、订阅、支付回调、发票和退款，按配置启用 |
| Notification | Go | 验证码、通知模板、邮件、短信、系统推送 |
| IM Platform/Server | Spring Boot + Netty | IM 业务与实时长连接 |
| Client Registration | Go / 8089 | 客户端挑战、身份绑定、签名证明和扩展分发 |
| Plugin Service | Spring Boot / 8085 | 插件控制面、包、版本、权限、安装、执行和市场 |
| Plugin Runtime | Go / 8090 | 运行时、沙箱、Broker、资源限制和回收 |
| Automation | Spring Boot + RabbitMQ / 8084 | 文件事件匹配、插件执行持久化、Inbox/Outbox 和恢复 |
| Workflow | Spring Boot + RabbitMQ / 8087 | DSL、能力中心、工作流版本、执行和市场 |
| Scheduler | Spring Boot + RabbitMQ / 8088 | Cron、租约、幂等触发和调度消息 |
| AI | FastAPI / 8001 | 可选 AI 任务和异步模型处理 |

## 3. 数据流与一致性

### 3.1 文件上传

1. Platform 创建上传会话并保存元数据。
2. Storage 签发短期操作凭证，客户端上传分片。
3. Storage 记录分片状态并通知 Platform。
4. Platform 收到完成请求后发布处理事件。
5. Worker 合并、校验、生成派生资源并回写状态。
6. 内容就绪/可用事件继续触发搜索、通知或自动化。

### 3.2 文件访问

文件列表和权限由 Platform 决定；文件内容、预览和下载由 Storage 执行。预览/下载通过短期操作凭证、Range 和大小/并发边界控制，避免把业务权限和物理存储路径直接暴露给客户端。

### 3.3 扩展执行

Plugin Service 保存插件包和安装关系，Automation 匹配文件事件，Workflow 负责流程定义和能力解析，Scheduler 负责定时触发，Runtime 在隔离环境中执行。插件只通过 Broker 使用声明过的能力。

## 4. 通信方式

| 方式 | 用途 |
| --- | --- |
| HTTP REST | Gateway 到业务服务、服务间同步 API |
| 内部 API + 服务凭证 | Platform、Storage、Plugin、Workflow 等受信调用 |
| RabbitMQ | 文件生命周期、通知、插件自动化和工作流执行 |
| Redis | 会话、限流、临时授权、幂等和缓存 |
| WebSocket | IM 与系统通知 |
| WebRTC | 音视频通话 |

## 5. 性能与可靠性

项目包含分片上传、断点续传、Range 下载、并发控制、异步 Worker、缓存、健康检查、指标和链路追踪基础。性能不以静态页面数字承诺；生产环境需针对文件大小、并发用户、消息堆积、数据库索引、对象存储吞吐和网络带宽压测。

## 6. 部署边界

Compose 使用单一 `app-networks`。中间件和内部服务使用 `expose`，前端/Nginx 是公开入口；容器间使用服务 DNS 名称。自动化服务位于 `automation` profile，并要求独立数据库、Runtime 地址和插件签名配置。
