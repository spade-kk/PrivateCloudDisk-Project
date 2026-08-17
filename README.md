# PrivateCloudDisk

PrivateCloudDisk 是面向团队与业务的私有云文件平台，围绕文件管理、空间协作、在线预览、文件生命周期处理、插件、工作流和市场能力构建。

> 本 README 只描述当前仓库中可由代码、配置、数据库迁移或接口契约核验的能力。容量、性能、可用性、合规认证和商业支持范围，须结合实际部署环境确认。

## 核心能力

- 文件与文件夹 CURD：创建、读取、重命名、移动、复制、删除和目录浏览。
- 文件传输：文件上传、分片上传、断点续传、文件下载、流式 Range 下载、文件夹上传与下载。
- 文件管理：分享链接、回收站恢复与清理、收藏夹、标签、最近访问、搜索和配额管理。
- 在线预览：图片、PDF、Office、Markdown、代码、压缩包、音频和视频等入口，实际格式以配置为准。
- 空间协作：个人空间、团队空间、成员、角色、权限、资源范围和空间级插件。
- 平台扩展：云插件、本地扩展、插件运行时、能力中心、工作流 DSL、调度、插件市场和工作流市场。
- 实时通信：消息、会话、群组、通知、WebSocket 推送和 WebRTC 音视频通话。
- 多客户端：Vue Web、React 管理后台、Electron、uni-app、原生 iOS/Android/macOS/Windows 和 Go CLI。

## 技术栈

| 层次 | 技术 | 主要用途 |
| --- | --- | --- |
| Web | Vue 3、TypeScript、Vite、Tailwind CSS、Pinia、GSAP | 官网、文件、空间、预览、插件和工作流页面 |
| 管理后台 | React、TypeScript、Ant Design | 管理员与运营后台 |
| Java 服务 | Spring Boot、Spring Cloud Gateway、WebFlux、Spring Security、MyBatis | 网关、平台、计费、插件、工作流、自动化和调度 |
| Python 服务 | FastAPI、Uvicorn、Python 3.11、aiofiles、pyvips、PyMuPDF、Pillow | 文件 I/O、预览资源、Worker 和可选 AI |
| Go 服务 | Gin、Redis、MySQL、RabbitMQ、Go Runtime | 通知、客户端注册、插件运行时 |
| 实时通信 | Netty、WebSocket、WebRTC | IM 业务、长连接和音视频通话 |
| 基础设施 | Docker Compose、Nginx、MySQL 8、Redis 7、RabbitMQ、MinIO、OpenSearch | 部署、缓存、消息、对象存储和检索 |
| 观测 | Prometheus、SkyWalking、SkyWalking UI | 健康检查、指标和链路诊断 |

## 微服务职责边界

| 服务 | 技术/端口 | 责任边界 |
| --- | --- | --- |
| `gateway-service-backend` | Spring Cloud Gateway / `8080` | 统一入口、认证、路由、限流、CORS 和 WebSocket 转发 |
| `platform-service-backend` | Spring Boot / `8081` | 用户、目录树、文件元数据、空间、成员权限、分享、标签、收藏、回收站和配额 |
| `file-service-backend` | FastAPI / `8000` | 分片上传、合并、校验、下载、Range、预览令牌、缩略图和存储访问 |
| `file-service-worker` | Python Worker | 消费文件生命周期事件，执行异步处理、扫描、缩略图和索引 |
| `billing-service-backend` | Spring Boot / `8083` | 订单、订阅、优惠券、发票、退款和支付回调，按配置启用 |
| `notification-service` | Go | 验证码、模板、邮件、短信、系统通知、设备和 WebSocket 推送 |
| `im-platform-backend` | Spring Boot / `8088` | 会话、消息、群组、好友和通话记录等 IM 业务 |
| `im-server-backend` | Netty / WebSocket `9090` | 实时长连接和消息推送 |
| `client-registration-service` | Go / `8089` | 客户端注册挑战、设备身份、用户绑定、签名证明和插件绑定 |
| `plugin-service-backend` | Spring Boot / `8085` | 插件定义、版本、清单、权限、安装、包仓库、签名、执行记录和插件市场 |
| `plugin-runtime-service` | Go / `8090` | 受控运行时、Python 沙箱、资源限制、Broker 和执行回收 |
| `automation-service-backend` | Spring Boot + RabbitMQ / `8084` | 文件事件匹配、插件入口选择、执行持久化、Inbox/Outbox 和恢复 |
| `workflow-service-backend` | Spring Boot + RabbitMQ / `8087` | 工作流定义、DSL 校验、能力中心、版本发布、执行、市场和调度对接 |
| `scheduler-service-backend` | Spring Boot + RabbitMQ / `8088` | Cron 计划、租约、幂等触发和调度消息发布 |
| `ai-service-backend` | FastAPI / `8001` | 可选 AI 任务、推荐、聚类和异步模型处理 |

服务之间通过 Docker Compose 服务名、内部 API 和 RabbitMQ 事件协作。浏览器链路为“浏览器 → Nginx → Gateway → 业务/文件服务”；容器内部不能用 `localhost` 代替其他服务。

## 架构与性能设计

1. 客户端负责展示、交互、任务进度和本地能力适配。
2. Gateway 负责统一入口和认证后的路由分发。
3. Platform Service 负责业务元数据、空间权限和状态；Storage Service 负责文件内容与派生资源。
4. RabbitMQ 解耦文件处理、通知、插件自动化和工作流执行。
5. MySQL 保存业务数据，Redis 保存缓存、限流、会话和短期授权，MinIO/本地卷保存对象，OpenSearch 支撑检索。
6. 插件和工作流通过能力中心访问受控能力，不直接取得宿主文件路径、数据库凭证或用户 JWT。

性能设计包括分片上传、断点续传、SHA-256 校验、Range/流式下载、并发控制、异步文件处理、预览资源缓存、健康检查、指标和链路追踪。仓库没有未经压测的吞吐、延迟或 SLA 承诺，生产容量须结合实例、连接池、消息堆积、存储吞吐和压测结果制定。

## 与传统单体应用的区别

| 对比项 | PrivateCloudDisk | 传统单体 |
| --- | --- | --- |
| 业务边界 | 网关、平台、文件、通知、IM、插件、工作流等服务分工 | 大部分能力集中在一个进程 |
| 文件处理 | 内容 I/O 与元数据分离，可异步处理 | 常由同一进程承担 |
| 扩展方式 | 插件、工作流、能力中心和市场可独立治理 | 常通过修改主应用重新发布 |
| 故障影响 | 可按服务和队列定位与恢复 | 故障影响面更集中 |
| 运维代价 | 边界清晰但服务和基础设施更多 | 初期部署简单，长期耦合可能更高 |

## 与 Nextcloud 的区别

两者都支持私有化文件管理，但定位不同：

| 对比项 | PrivateCloudDisk | Nextcloud |
| --- | --- | --- |
| 核心定位 | 空间协作、文件生命周期和业务自动化平台 | 通用私有云协作套件与应用生态 |
| 扩展模型 | 云插件、本地扩展、工作流、能力中心和双市场 | Nextcloud Apps、外部应用和官方生态 |
| 文件处理 | 独立 Storage Service + Worker，围绕事件和预览演进 | 遵循 Nextcloud 文件与应用机制 |
| 空间模型 | 空间作为成员、角色、资源和插件授权上下文 | 常见团队/群组/共享机制，具体语义依版本与应用 |
| 选择建议 | 适合希望按本项目服务边界定制业务的团队 | 适合优先采用成熟通用协作生态的团队 |

## 项目结构

```text
PrivateCloudDisk-project/
├── PrivateCloudDisk-web/                  # Vue Web 与官网
├── PrivateCloudDisk-admin-web/             # 管理后台
├── PrivateCloudDisk-gateway-service/       # API 网关
├── PrivateCloudDisk-platform-service/      # 核心业务
├── PrivateCloudDisk-storage-service/       # 文件 I/O
├── PrivateCloudDisk-notification-service/  # 通知
├── PrivateCloudDisk-im/                    # IM
├── PrivateCloudDisk-client-registration-service/
├── PrivateCloudDisk-plugin-service/        # 插件控制面
├── PrivateCloudDisk-plugin-runtime-service/# 插件 Runtime
├── PrivateCloudDisk-automation-service/    # 事件自动化
├── PrivateCloudDisk-workflow-service/      # 工作流
├── PrivateCloudDisk-scheduler-service/     # 调度
├── PrivateCloudDisk-billing-service/       # 计费
├── PrivateCloudDisk-ai-service/            # 可选 AI
├── PrivateCloudDisk-db/  PrivateCloudDisk-infra/
├── contracts/  deploy/  docs/
```

## 快速开始

环境要求：Docker Compose v2、Node.js/npm、Java 21、Python 3.11 和 Go。

```bash
cp .env.example .env
# 设置数据库、Redis、RabbitMQ、MinIO 和内部服务凭证
docker compose config --quiet
docker compose up -d
cd PrivateCloudDisk-web
npm install
npm run dev
```

插件和工作流服务位于 Compose `automation` profile，启用前需配置独立数据库口令、Runtime 地址和插件签名密钥：

```bash
docker compose --profile automation up -d
```

生产前端使用同源 API（例如 `VITE_API_BASE_URL=/api/v1`）；修改 Vite 环境变量后必须重新构建前端镜像。

## 文档导航

- [文档中心](./docs/README.md)
- [架构设计](./docs/architecture.md)
- [API 概览](./docs/api-overview.md)
- [数据库设计](./docs/database.md)
- [开发指南](./docs/development.md)
- [安全说明](./docs/security.md)
- [空间集成审计](./docs/SPACE_FULL_INTEGRATION_AUDIT.md)
- [插件自动化平台设计](./docs/PLUGIN_AUTOMATION_PLATFORM_DESIGN.md)
- [插件开发指南](./docs/PLUGIN_DEVELOPER_GUIDE.md)
