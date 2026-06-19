# PrivateCloudDisk-android

企业级私有云盘 Android 原生客户端，基于 Kotlin + Jetpack Compose 构建，支持文件管理、即时通讯、虚拟磁盘等功能。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.9+ | 开发语言 |
| Jetpack Compose | 1.5+ | 声明式 UI 框架 |
| Hilt | - | 依赖注入 |
| Room | - | 本地数据库 (SQLite) |
| Retrofit | - | HTTP 网络请求 |
| OkHttp | - | WebSocket 长连接 |
| DataStore | - | 键值对持久化 |
| Navigation Compose | - | 页面导航 |
| Coil | - | 图片加载 |

---

## 项目结构

```
PrivateCloudDisk-android/
├── app/
│   ├── src/main/
│   │   ├── java/com/privateclouddisk/android/
│   │   │   ├── MainActivity.kt           # 主 Activity
│   │   │   ├── App.kt                    # Application 入口
│   │   │   ├── di/                       # 依赖注入模块
│   │   │   ├── data/                     # 数据层
│   │   │   │   ├── remote/               # 远程 API
│   │   │   │   ├── local/                # 本地存储 (Room)
│   │   │   │   └── repository/           # 数据仓库
│   │   │   ├── domain/                   # 业务逻辑层
│   │   │   │   ├── model/                # 领域模型
│   │   │   │   └── usecase/              # 用例
│   │   │   ├── ui/                       # UI 层
│   │   │   │   ├── theme/                # 主题
│   │   │   │   ├── navigation/           # 导航
│   │   │   │   └── screens/              # 各页面
│   │   │   └── util/                     # 工具类
│   │   └── res/                          # 资源文件
│   │       ├── drawable/                 # 图标
│   │       ├── layout/                   # 布局文件
│   │       ├── menu/                     # 菜单
│   │       └── values/                   # 颜色/字符串/主题
│   └── build.gradle
├── gradle/
│   ├── wrapper/
│   └── libs.versions.toml                # 版本目录
├── build.gradle                          # 根构建脚本
├── settings.gradle
└── gradle.properties
```

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 文件管理 | 浏览、上传、下载、移动、重命名、删除 |
| 文件预览 | 图片、视频、音频、PDF、Office 文档 |
| 即时通讯 | 单聊、群聊、语音/视频通话 (WebRTC) |
| 同步管理 | 文件同步状态、断点续传 |
| 搜索 | 文件名搜索、全文检索 |
| 收藏 | 文件收藏管理 |
| 回收站 | 文件删除与恢复 |
| 设置 | 个人信息、安全设置、存储管理 |

---

## 环境要求

| 依赖 | 版本要求 |
|------|----------|
| Android Studio | Hedgehog (2023.1.1) 或更高 |
| Android SDK | minSdk 26, targetSdk 35 |
| JDK | 17+ |
| Gradle | 8.x |

---

## 快速开始

```bash
# 1. 克隆项目
git clone <repo-url>

# 2. 用 Android Studio 打开 PrivateCloudDisk-android 目录

# 3. 同步 Gradle 依赖

# 4. 配置 API 地址 (app/build.gradle)
# buildConfigField "API_BASE_URL" → 修改为你的服务地址

# 5. 运行到模拟器或真机
./gradlew installDebug
```

---

## 构建配置

可在 `app/build.gradle` 中修改以下 `buildConfigField`：

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `API_BASE_URL` | `https://api.privateclouddisk.com` | 后端 API 地址 |
| `WS_BASE_URL` | `wss://ws.privateclouddisk.com` | WebSocket 连接地址 |
| `APP_NAME` | `PrivateCloudDisk` | 应用名称 |