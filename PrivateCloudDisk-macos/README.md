# PrivateCloudDisk-macos

macOS 原生客户端，基于 SwiftUI + AppKit 构建，包含文件访问、系统集成和虚拟磁盘相关模块；挂载、即时通讯和后台能力以工程配置、系统权限和构建结果为准。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Swift | 5.9+ | 开发语言 |
| SwiftUI | 5.x | 声明式 UI 框架 |
| AppKit | - | 原生 macOS 组件 (菜单栏、Dock) |
| Combine | - | 响应式数据流 |
| Keychain | - | 安全凭证存储 |
| URLSession | - | HTTP 网络请求 |
| macFUSE | 4.x | 虚拟磁盘挂载 |
| WebRTC | - | 音视频通话 |

---

## 项目结构

```
PrivateCloudDisk-macos/
├── App/
│   ├── PrivateCloudDiskApp.swift          # 应用入口
│   ├── AppDelegate.swift                  # AppDelegate 生命周期
│   ├── MainMenu.swift                     # 菜单栏配置
│   ├── Info.plist                         # 应用配置
│   └── PrivateCloudDisk.entitlements      # 权限声明
├── Models/
│   ├── ApiResponse.swift                  # API 响应模型
│   ├── FileModels.swift                   # 文件模型
│   ├── UploadModels.swift                 # 上传模型
│   ├── UserModels.swift                   # 用户模型
│   └── VirtualDiskModels.swift            # 虚拟磁盘模型
├── ViewModels/
│   ├── ContentViewModel.swift             # 主内容视图模型
│   ├── FileListViewModel.swift            # 文件列表
│   ├── LoginViewModel.swift               # 登录
│   ├── UploadViewModel.swift              # 上传
│   ├── SettingsViewModel.swift            # 设置
│   ├── FavoritesTrashViewModel.swift      # 收藏/回收站
│   ├── FileDetailViewModel.swift          # 文件详情
│   └── VirtualDiskViewModel.swift         # 虚拟磁盘
├── Views/
│   ├── ContentView.swift                  # 根视图
│   ├── Main/MainView.swift                # 主界面
│   ├── Home/HomeView.swift                # 文件浏览
│   ├── Login/LoginView.swift              # 登录
│   ├── Profile/ProfileView.swift          # 个人中心
│   ├── Settings/SettingsView.swift        # 设置
│   ├── Upload/UploadProgressView.swift    # 上传进度
│   ├── VirtualDisk/VirtualDiskView.swift  # 虚拟磁盘
│   ├── Favorites/FavoritesTrashViews.swift # 收藏回收站
│   ├── FileDetail/FileDetailView.swift    # 文件详情
│   └── Common/CommonComponents.swift       # 通用组件
├── Services/
│   ├── Network/
│   │   ├── APIClient.swift                # HTTP 客户端
│   │   ├── AuthService.swift              # 认证服务
│   │   ├── FileService.swift              # 文件服务
│   │   ├── DownloadManager.swift          # 下载管理
│   │   └── UploadManager.swift            # 上传管理
│   ├── Security/
│   │   ├── CryptoService.swift            # 加密服务 (PBKDF2)
│   │   ├── KeychainManager.swift          # 钥匙串管理
│   │   └── CertificateValidator.swift     # 证书验证
│   ├── System/
│   │   ├── DockManager.swift              # Dock 图标管理
│   │   ├── LoginItemManager.swift         # 开机启动
│   │   ├── MenuBarManager.swift           # 菜单栏
│   │   ├── NotificationManager.swift      # 系统通知
│   │   ├── SpotlightIndexer.swift         # Spotlight 集成
│   │   └── URLSchemeHandler.swift         # URL Scheme
│   ├── VirtualDisk/
│   │   └── VirtualDiskManager.swift       # 虚拟磁盘管理
│   └── IM/
│       └── IMService.swift                # 即时通讯
├── Extensions/
│   └── FileProviderHelper.swift           # File Provider 扩展
├── Assets.xcassets/                       # 资源文件
└── PrivateCloudDisk-macos.xcodeproj/       # Xcode 工程
```

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 文件管理 | 浏览、上传、下载、移动、重命名、删除 |
| 虚拟磁盘 | macFUSE 挂载云盘为本地磁盘，按需下载 |
| 文件预览 | 图片、视频、PDF、文档 |
| 即时通讯 | 单聊、群聊、语音/视频通话 (WebRTC) |
| 系统集成 | Dock 图标、菜单栏、Spotlight 搜索、通知中心 |
| 安全 | 钥匙串凭证存储、PBKDF2 密码预哈希、证书校验 |
| 开机启动 | Login Item 自动启动 |
| 文件共享 | Share Extension、URL Scheme 处理 |

---

## 环境要求

| 依赖 | 版本要求 |
|------|----------|
| macOS | 14.0 (Sonoma) 或更高 |
| Xcode | 15.0+ |
| Swift | 5.9+ |
| macFUSE | 4.x (虚拟磁盘功能需要) |

---

## 快速开始

```bash
# 1. 安装 macFUSE (虚拟磁盘功能需要)
brew install --cask macfuse

# 2. 打开 Xcode 工程
open PrivateCloudDisk-macos.xcodeproj

# 3. 配置签名团队 (Signing & Capabilities)

# 4. 运行
# Product → Run (⌘R)
```

---

## 权限配置

应用需要以下系统权限 (在 `PrivateCloudDisk.entitlements` 中配置)：

| 权限 | 用途 |
|------|------|
| File Access | 虚拟磁盘文件访问 |
| Network | 网络通信 |
| Keychain Sharing | 安全凭证存储 |
| Apple Events | 系统集成 |
