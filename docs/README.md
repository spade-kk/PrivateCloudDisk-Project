# PrivateCloudDisk 文档中心

本目录记录当前仓库的架构、接口、数据库、开发、安全和扩展平台说明。文档以代码、配置、迁移脚本和事件契约为准；未经过实际部署或压测验证的指标不写入文档。

## 导航

| 文档 | 内容 |
| --- | --- |
| [架构设计](./architecture.md) | 技术栈、服务边界、通信方式、部署与性能设计 |
| [API 概览](./api-overview.md) | 网关前缀、认证、文件/空间/分享/扩展接口族 |
| [数据库设计](./database.md) | 用户、目录、文件、空间、分享、标签、扩展数据 |
| [开发指南](./development.md) | 本地环境、Compose、服务启动和验证 |
| [安全设计](./security.md) | JWT、内部服务凭证、限流、预览/下载授权 |
| [密码加密](./password-encryption.md) | 注册、登录和密码处理链路 |
| [空间集成审计](./SPACE_FULL_INTEGRATION_AUDIT.md) | 空间上下文、资源范围和生命周期集成记录 |
| [插件自动化平台设计](./PLUGIN_AUTOMATION_PLATFORM_DESIGN.md) | 插件、Runtime、自动化、工作流、调度和市场 |
| [插件开发指南](./PLUGIN_DEVELOPER_GUIDE.md) | 插件包、权限、SDK、工作流 DSL 与发布 |
| [CloudFlow 编译器指南](./CLOUDFLOW_COMPILER_GUIDE.md) | Rust Compiler CLI、Workflow IR、Runtime HTTP 契约和验证命令 |
| [事件契约](../contracts/README.md) | 文件生命周期和插件清单 Schema |

## 阅读顺序

1. 从根目录 [README](../README.md) 了解产品能力、技术栈和服务职责。
2. 阅读 [架构设计](./architecture.md) 理解浏览器、网关、业务、存储和扩展边界。
3. 需要接入接口时阅读 [API 概览](./api-overview.md)，再以对应 Controller、前端 API 模块和 Gateway 路由核对路径。
4. 修改数据或异步链路时同时阅读 [数据库设计](./database.md) 和 [事件契约](../contracts/README.md)。
5. 涉及插件或工作流时，必须阅读平台设计、开发指南和 Runtime 部署说明。

## 当前文档约定

- “支持”表示仓库已有实现入口，不代表所有部署配置默认启用。
- “按配置”表示能力受环境变量、服务 profile、存储后端或客户端平台影响。
- 不使用虚构的客户数量、性能、SLA、认证结果或商业价格。
- 变更 API、数据库、消息或权限时，应同步更新根 README、服务 README、docs 和 contracts。
