# PrivateCloudDisk-web

PrivateCloudDisk Web 前端，基于 Vue 3 + TypeScript + Vite + Tailwind CSS 构建。它同时承载登录后的文件工作台、空间协作、预览与传输页面，以及面向项目介绍的官网页面。

## 当前能力

- 文件与文件夹：创建、重命名、移动、复制、删除、搜索、最近使用、收藏、标签和回收站。
- 传输：文件上传、分片上传、断点续传、文件夹上传、文件下载、文件夹下载和传输记录。
- 预览：图片、视频、音频、PDF、Office、Markdown、代码、文本和归档等类型，具体格式以服务端预览接口为准。
- 空间协作：空间创建与管理、成员和权限上下文、空间文件、公开空间和协作入口。
- 扩展能力：插件管理、插件 IDE、插件执行、云插件/本地运行时入口、插件市场。
- 自动化：工作流编辑、执行、工作流市场，以及与调度、插件能力和文件事件的集成入口。
- 实时能力：通知、即时通讯、通话和 WebRTC 页面入口；实际连接地址由网关和部署配置决定。

官网页面只展示仓库中能够由代码、接口或部署配置核验的能力，不固化客户数量、用户规模、吞吐量、可用性、认证结果、固定价格或默认测试账号。

## 技术栈

| 技术 | 用途 |
|------|------|
| Vue 3 + TypeScript | 页面组件与类型安全 |
| Vite | 开发服务器和生产构建 |
| Tailwind CSS | 页面样式和响应式布局 |
| Pinia | 登录、文件、传输、空间、预览、插件、工作流等状态管理 |
| Vue Router | 官网、工作台、预览、插件、工作流和管理页面路由 |
| Axios | 统一 HTTP 请求、认证和错误处理 |
| GSAP / Three.js | 官网动效和登录页视觉效果，具体页面按现有实现启用 |

## 目录结构

```text
src/
├── api/
│   ├── index.ts                 # API 统一导出
│   ├── im/                      # IM/WebSocket/WebRTC 接口
│   └── modules/                 # 认证、文件、空间、传输、预览、插件、工作流等接口
├── components/                  # 布局、文件、上传、空间、插件、预览等组件
├── stores/                      # Pinia 业务状态
├── views/
│   ├── website/                 # 官网、文档、下载、状态和项目介绍页面
│   ├── preview/                 # 文件预览页面
│   ├── plugins/                 # 插件管理、IDE、执行和市场页面
│   ├── workflows/               # 工作流管理、编辑和市场页面
│   ├── public-space/             # 公开空间页面
│   ├── security/                # 账户与安全设置页面
│   └── ...                      # 工作台、分享、回收站、标签、通知等页面
├── runtime/                     # Web 客户端身份和本地插件运行时入口
├── router/                      # 路由与访问控制
├── types/                       # TypeScript 类型定义
└── utils/                       # 请求、加密、缓存和预览辅助逻辑
```

## API 与部署约定

生产环境使用浏览器 → Nginx → Spring Cloud Gateway → 后端服务的同源链路，前端默认通过 `/api/v1` 访问网关路由。不要把容器内的 `localhost` 当作其他服务地址；跨服务地址应使用 Compose 服务名或部署环境提供的服务发现地址。

前端环境变量由 Vite 在构建时注入。修改生产 API 前缀或网关地址后，需要重新构建前端镜像；实际路由和接口以 `src/api`、网关配置及后端服务实现为准。

## 环境要求

- Node.js：以 `package.json` 的 engines/构建工具要求为准。
- npm：与仓库中的 `package-lock.json` 配套使用。
- 后端：需要可访问的 Gateway 和对应的认证、平台、存储、预览、空间等服务；插件、工作流、IM 和计费能力按部署 profile 启用。

## 本地开发

```bash
cd PrivateCloudDisk-web
npm ci
npm run dev
```

开发环境 API 代理和端口以 `vite.config.ts`、`.env.development` 及当前 Nginx/Gateway 配置为准。

## 构建与检查

```bash
npm run build
npm run preview
```

构建只能验证前端源码和类型/打包链路；完整联调还需要启动网关、数据库、对象存储、消息队列及所需微服务。仓库没有在 README 中承诺固定性能或可用性数字，生产容量应结合实例规格、连接池、队列、存储吞吐和压测结果制定。

## 相关文档

- 根项目：[README.md](../README.md)
- 架构：[docs/architecture.md](../docs/architecture.md)
- API 导航：[docs/api-overview.md](../docs/api-overview.md)
- 文档索引：[docs/README.md](../docs/README.md)
- 插件/自动化设计：[docs/PLUGIN_AUTOMATION_PLATFORM_DESIGN.md](../docs/PLUGIN_AUTOMATION_PLATFORM_DESIGN.md)
