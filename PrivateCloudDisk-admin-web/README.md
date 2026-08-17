# PrivateCloudDisk-admin-web

管理端前端，基于 React 19 + TypeScript + Ant Design 6 构建，用于按后端权限开放用户、空间、文件和系统管理能力。实际菜单和接口以当前代码与 Gateway 路由为准。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| React | 19.2 | 前端框架 |
| TypeScript | 6.0 | 类型安全 |
| Vite | 8.0 | 构建工具 |
| Ant Design | 6.4 | UI 组件库 |
| React Router | 7.17 | 路由管理 |
| Zustand | 5.0 | 状态管理 |
| TanStack Query | 5.101 | 服务端状态管理 |
| Axios | 1.17 | HTTP 客户端 |

---

## 项目结构

```
src/
├── api/                                # API 接口层
│   ├── auth.ts                         # 管理员认证 API
│   ├── users.ts                        # 用户管理 API
│   ├── files.ts                        # 文件管理 API
│   ├── audit.ts                        # 审计日志 API
│   ├── security.ts                     # 安全事件 API
│   └── system.ts                       # 系统配置 API
├── pages/                              # 页面
│   ├── LoginPage.tsx                   # 管理员登录
│   ├── RegisterPage.tsx                # 管理员注册
│   ├── DashboardPage.tsx               # 仪表盘
│   ├── UsersPage.tsx                   # 用户管理
│   ├── FilesPage.tsx                   # 文件管理
│   ├── StorageStatsPage.tsx            # 存储统计
│   ├── AuditLogsPage.tsx               # 审计日志
│   ├── SecurityEventsPage.tsx          # 安全事件
│   ├── IPBlacklistPage.tsx             # IP 黑名单
│   ├── OnlineUsersPage.tsx             # 在线用户
│   ├── QuarantinedFilesPage.tsx        # 隔离文件
│   ├── SystemConfigPage.tsx            # 系统配置
│   ├── SystemResourcesPage.tsx         # 系统资源
│   ├── ApiDocsPage.tsx                 # API 文档
│   └── NotFoundPage.tsx                # 404
├── components/                         # 通用组件
│   ├── PageHeader.tsx                  # 页面标题
│   ├── StatCard.tsx                    # 统计卡片
│   └── TurnstileWidget.tsx            # 人机验证组件
├── layouts/
│   └── AdminLayout.tsx                 # 管理后台布局
├── stores/                             # 状态管理
│   ├── authStore.ts                    # 认证状态
│   ├── usersStore.ts                   # 用户管理状态
│   ├── filesStore.ts                   # 文件管理状态
│   ├── dashboardStore.ts               # 仪表盘状态
│   ├── auditStore.ts                   # 审计日志状态
│   ├── securityStore.ts                # 安全事件状态
│   └── systemStore.ts                  # 系统配置状态
├── types/
│   └── api.ts                          # API 类型定义
├── utils/
│   ├── request.ts                      # Axios 请求封装
│   └── storage.ts                      # 本地存储工具
├── App.tsx                             # 根组件
├── main.tsx                            # 入口文件
└── index.css                           # 全局样式
```

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 管理仪表盘 | 系统概览、统计数据、图表展示 |
| 用户管理 | 用户列表、搜索、禁用/启用、详情查看 |
| 文件管理 | 全局文件浏览、搜索、删除 |
| 存储统计 | 存储使用量统计、趋势分析 |
| 审计日志 | 登录审计、操作审计、导出 |
| 安全事件 | 安全事件监控、告警处理 |
| IP 黑名单 | IP 封禁管理、自动解封 |
| 在线用户 | 实时在线用户监控 |
| 隔离文件 | 病毒扫描隔离文件管理 |
| 系统配置 | 系统参数配置、动态生效 |
| 系统资源 | CPU、内存、磁盘监控 |
| API 文档 | Swagger 文档内嵌 |

---

## 环境要求

| 依赖 | 版本要求 |
|------|----------|
| Node.js | 18+ |
| npm | 9+ |

---

## 快速开始

```bash
# 1. 安装依赖
cd PrivateCloudDisk-admin-web
npm install

# 2. 配置环境变量
# 编辑 .env.development 设置 API 地址

# 3. 启动开发服务器
npm run dev

# 4. 构建生产版本
npm run build

# 5. 预览生产构建
npm run preview
```

---

## 默认管理员账号

| 账号 | 密码 | 角色 |
|------|------|------|
| 管理员账户 | 不在文档中固化默认密码 | 由部署初始化与权限配置决定 |

> 密码经过前端 PBKDF2 预哈希 + 后端 BCrypt 二次加密存储。如需创建新管理员，请使用 `scripts/generate_admin_password.py` 生成密码哈希。详见 [scripts/README.md](../scripts/README.md)。
