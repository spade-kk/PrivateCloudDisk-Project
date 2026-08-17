# PrivateCloudDisk-ios

iOS 原生客户端，基于 SwiftUI 构建，包含文件访问、上传下载和原生扩展相关模块；Widget、Share Extension 与即时通讯能力以工程配置和当前构建结果为准。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Swift | 5.9+ | 开发语言 |
| SwiftUI | 5.x | 声明式 UI 框架 |
| Combine | - | 响应式数据流 |
| Keychain | - | 安全凭证存储 |
| URLSession | - | HTTP 网络请求 |
| WebSocket | - | 即时通讯长连接 |
| LocalAuthentication | - | Face ID / Touch ID 生物认证 |
| WidgetKit | - | 桌面小组件 |
| Share Extension | - | 系统分享扩展 |

---

## 项目结构

```
PrivateCloudDisk-ios/
├── PrivateCloudDisk_iosApp.swift          # 应用入口
├── Models/
│   ├── APIResponse.swift                  # API 响应模型
│   ├── FileItem.swift                     # 文件模型
│   ├── Message.swift                      # 消息模型
│   ├── ShareLink.swift                    # 分享模型
│   ├── StarItem.swift                     # 收藏模型
│   ├── User.swift                         # 用户模型
│   └── VideoStreamInfo.swift              # 视频流信息
├── ViewModels/
│   ├── AuthViewModel.swift                # 认证视图模型
│   ├── FileBrowserViewModel.swift         # 文件浏览
│   ├── MessagesViewModel.swift            # 消息
│   ├── SharesViewModel.swift              # 分享
│   ├── StarredViewModel.swift             # 收藏
│   └── VideoPlayerViewModel.swift         # 视频播放
├── Views/
│   ├── ContentView.swift                  # 根视图
│   ├── Auth/
│   │   └── LoginView.swift                # 登录页
│   ├── Files/
│   │   ├── FileBrowserView.swift          # 文件浏览
│   │   └── FileDetailView.swift           # 文件详情
│   ├── Messages/
│   │   └── MessagesView.swift             # 消息列表
│   ├── Shares/
│   │   └── SharesView.swift               # 分享管理
│   ├── Stars/
│   │   └── StarredView.swift              # 收藏
│   └── Video/
│       └── VideoPlayerView.swift          # 视频播放
├── Network/
│   ├── APIClient.swift                    # HTTP 客户端
│   ├── AuthService.swift                  # 认证服务
│   ├── FileService.swift                  # 文件服务
│   ├── IMService.swift                    # 即时通讯
│   ├── ShareService.swift                 # 分享服务
│   ├── StarService.swift                  # 收藏服务
│   ├── VideoService.swift                 # 视频服务
│   └── WebSocketClient.swift              # WebSocket 客户端
├── Services/
│   ├── AppGroupManager.swift              # App Group 管理
│   ├── BackgroundTaskManager.swift        # 后台任务
│   ├── BiometricAuthManager.swift         # 生物认证
│   ├── FileCacheManager.swift             # 文件缓存
│   └── KeychainManager.swift              # 钥匙串
├── ShareExtension/                        # 分享扩展
│   ├── ShareViewController.swift
│   └── Info.plist
├── WidgetExtension/                       # 桌面小组件
│   ├── WidgetExtension.swift
│   ├── WidgetExtensionBundle.swift
│   ├── WidgetExtensionControl.swift
│   ├── WidgetExtensionLiveActivity.swift
│   └── AppIntent.swift
├── Resources/
│   └── Assets.xcassets/                   # 资源文件
└── PrivateCloudDisk-ios.xcodeproj/         # Xcode 工程
```

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 文件管理 | 浏览、上传、下载、预览、删除 |
| 文件预览 | 图片、视频、PDF、Office 文档 |
| 即时通讯 | 单聊、群聊、消息推送 |
| 视频播放 | HLS 流媒体播放 |
| 分享 | 创建分享链接、外部分享 |
| 收藏 | 文件收藏管理 |
| 生物认证 | Face ID / Touch ID 快速解锁 |
| Widget | 桌面小组件，快速查看存储状态 |
| Share Extension | 从其他 App 直接分享文件到云盘 |
| Live Activity | 灵动岛实时显示上传/下载进度 |

---

## 环境要求

| 依赖 | 版本要求 |
|------|----------|
| iOS | 17.0+ |
| Xcode | 15.0+ |
| Swift | 5.9+ |

---

## 快速开始

```bash
# 1. 打开 Xcode 工程
open PrivateCloudDisk-ios.xcodeproj

# 2. 配置 Bundle Identifier 和签名团队

# 3. 配置 App Group (用于 Share Extension 和 Widget 数据共享)

# 4. 运行到模拟器或真机
# Product → Run (⌘R)
```
