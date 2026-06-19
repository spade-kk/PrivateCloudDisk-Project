# PrivateCloudDisk-uni-app

企业级私有云盘跨平台客户端，基于 uni-app (Vue 3) 构建，支持 iOS / Android / 微信小程序 / H5 多端部署。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| uni-app | 3.0 (alpha) | 跨平台框架 |
| Vue 3 | 3.x | 前端框架 (Composition API) |
| Vite | 5.x | 构建工具 |
| Pinia | 2.x | 状态管理 |
| uView Plus | 3.x | UI 组件库 |
| Day.js | 1.x | 日期处理 |

---

## 项目结构

```
PrivateCloudDisk-uni-app/
├── src/
│   ├── pages/                          # 页面
│   │   ├── index/                      # 首页
│   │   ├── login/                      # 登录
│   │   ├── register/                   # 注册
│   │   ├── file-list/                  # 文件列表
│   │   ├── file-detail/                # 文件详情
│   │   ├── search/                     # 搜索
│   │   ├── favorites/                  # 收藏
│   │   ├── trash/                      # 回收站
│   │   ├── upload/                     # 上传
│   │   ├── profile/                    # 个人中心
│   │   ├── profile/edit/               # 编辑资料
│   │   └── settings/                   # 设置
│   ├── api/                            # API 接口层
│   │   ├── main.js                     # 统一导出
│   │   ├── user.js                     # 用户 API
│   │   ├── file.js                     # 文件 API
│   │   ├── node.js                     # 目录节点 API
│   │   ├── upload.js                   # 上传 API
│   │   ├── download.js                 # 下载 API
│   │   ├── task.js                     # 任务 API
│   │   ├── quota.js                    # 配额 API
│   │   ├── star.js                     # 收藏 API
│   │   └── trash.js                    # 回收站 API
│   ├── store/                          # 状态管理
│   │   ├── app.js                      # 应用全局状态
│   │   └── user.js                     # 用户状态
│   ├── utils/                          # 工具函数
│   │   ├── const.js                    # 常量
│   │   ├── crypto.js                   # 加密 (PBKDF2)
│   │   ├── fingerprint.js              # 设备指纹
│   │   ├── helper.js                   # 辅助函数
│   │   ├── request.js                  # HTTP 请求封装
│   │   ├── storage.js                  # 本地存储
│   │   └── validator.js                # 表单验证
│   ├── App.vue                         # 根组件
│   ├── main.js                         # 入口文件
│   ├── pages.json                      # 页面配置
│   ├── manifest.json                   # 应用配置
│   └── uni.scss                        # 全局样式
├── package.json
└── vite.config.js
```

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 文件管理 | 浏览、上传、下载、删除 |
| 文件预览 | 图片、视频、PDF |
| 搜索 | 文件名搜索 |
| 收藏 | 文件收藏管理 |
| 回收站 | 文件删除与恢复 |
| 用户认证 | 登录、注册、密码加密 |
| 个人中心 | 资料编辑、设置管理 |
| 多端适配 | iOS / Android / 微信小程序 / H5 |

---

## 环境要求

| 依赖 | 版本要求 |
|------|----------|
| Node.js | 18+ |
| HBuilderX | 最新版 (可选，或用 CLI) |

---

## 快速开始

```bash
# 1. 安装依赖
cd PrivateCloudDisk-uni-app
npm install

# 2. 开发模式
# H5 开发
npm run dev:h5

# 微信小程序开发
npm run dev:mp-weixin

# App 开发 (需 HBuilderX)
npm run dev:app

# 3. 构建
# H5 构建
npm run build:h5

# 微信小程序构建
npm run build:mp-weixin

# App 打包构建
npm run build:app
```

---

## 多端支持

| 平台 | 启动命令 | 说明 |
|------|----------|------|
| H5 | `npm run dev:h5` | 浏览器调试 |
| 微信小程序 | `npm run dev:mp-weixin` | 需要微信开发者工具 |
| App (iOS/Android) | `npm run dev:app` | 需要 HBuilderX |