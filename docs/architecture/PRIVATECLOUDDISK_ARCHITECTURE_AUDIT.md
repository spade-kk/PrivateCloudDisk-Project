# PrivateCloudDisk 平台架构审计与图例说明

本目录的架构图根据当前仓库代码、根目录 `docker-compose.yml`、`deploy/local/docker-compose.apisix.yml`、Gateway 路由配置，以及各微服务 README / 配置文件生成。图中只把可以从这些来源确认的职责和依赖画入；“仓库中存在”不等于“根 Compose 默认启动”。

## 部署状态

| 类别 | 审计结论 |
| --- | --- |
| 根 Compose 默认服务 | MySQL、Redis、RabbitMQ、MinIO、OpenSearch、SkyWalking OAP/UI、Gateway、Client Registration、Platform、Billing、Storage API、Storage Worker、Git、IM Platform、IM Server、AI、Frontend、Certbot |
| `automation` profile | Plugin Service、Automation Service、Workflow Service、CloudFlow Runtime、Scheduler Service |
| 独立/可选组件 | `im-router`、Notification Service、Plugin Runtime Service、APISIX；这些目录或配置存在，但没有作为根 Compose 默认服务编排 |
| 网页边缘层 | `PrivateCloudDisk-web` 镜像内 Nginx 提供 `www/api/ws/admin` 域名入口、静态资源、REST 反向代理和 WebSocket 反向代理；Gateway 是内部 Spring Cloud Gateway 路由层 |

## 服务边界

- Platform Service：用户、空间、文件元数据、分享、权限、配额和业务协调；用户目录公共查询由主业务边界提供。
- Storage API / Worker：Storage API 负责上传、下载、预览、Range 和操作授权；Worker 消费 RabbitMQ Task Bus 执行合并、哈希、病毒、索引和可用状态等后台处理。
- Git Service：管理公开空间 Git 仓库、refs、Smart HTTP/SSH、审计和 push 事件；通过 Platform 校验空间/成员权限，通过 Storage 内部 Object Broker 共享物理对象池。
- IM Platform / Server / Router：IM Platform 提供 REST 业务，IM Server 提供 Netty WebSocket/Protobuf 长连接，Router 负责在线路由和 gRPC 推送；IM 不直接访问用户表。
- Plugin / Automation / Workflow / Scheduler / CloudFlow：插件市场、文件事件匹配、工作流 DSL 与能力中心、Cron 触发，以及 Rust 执行面分工协作；Plugin Runtime 是受控沙箱执行面。
- Notification、Billing、AI、Client Registration：分别承担通知、计费、可选 AI 推理和客户端设备身份边界；是否对公网开放以 Gateway 路由和部署配置为准。

## 图形约定

- 蓝色：客户端与核心业务服务；黄色：边缘层、消息总线或决策型入口；紫色：自动化、插件和工作流；绿色：数据与可观测性；灰色：说明或外部/独立部署组件。
- 实线箭头表示 HTTP/REST 或 gRPC 等同步调用；黄色虚线表示 RabbitMQ 异步事件、Task Bus、Outbox 或 DLQ；绿色点划线表示数据存储、索引或观测连接。
- 虚线边框表示 `automation` profile、独立运行时或仓库中存在但没有被根 Compose 默认编排的组件。

## 交付文件

- `privateclouddisk-platform-architecture.drawio`：两页可编辑源文件：全局拓扑、服务依赖。
- `privateclouddisk-platform-architecture-overview.svg/png`：全局拓扑导出。
- `privateclouddisk-platform-architecture-services.svg/png`：微服务边界与依赖导出。
- `generate_privateclouddisk_architecture.py`：从固定审计模型重新生成上述文件。
