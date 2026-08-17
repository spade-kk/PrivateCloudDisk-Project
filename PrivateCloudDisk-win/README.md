# PrivateCloudDisk-win

Windows 原生客户端，基于 .NET WPF + MVVM 构建，包含文件访问、上传下载、虚拟磁盘、即时通讯和系统集成模块；具体能力以当前实现、驱动依赖和系统权限为准。

---

## 项目结构

```
PrivateCloudDisk-win/
├── PrivateCloudDisk/                      # 主应用
│   ├── App.xaml / App.xaml.cs             # 应用入口
│   ├── MainWindow.xaml / .cs              # 主窗口
│   ├── PrivateCloudDisk.csproj            # 项目文件
│   ├── Models/                            # 数据模型
│   │   ├── ApiResponse.cs                 # API 响应模型
│   │   ├── CallModels.cs                  # 通话模型
│   │   ├── FeatureModels.cs               # 功能模型
│   │   ├── NodeModels.cs                  # 目录节点模型
│   │   ├── UploadModels.cs                # 上传模型
│   │   └── UserModels.cs                  # 用户模型
│   ├── ViewModels/                        # 视图模型 (MVVM)
│   │   ├── MainViewModel.cs               # 主视图模型
│   │   ├── HomeViewModel.cs               # 文件浏览
│   │   ├── LoginViewModel.cs              # 登录
│   │   ├── ProfileViewModel.cs            # 个人中心
│   │   ├── SettingsViewModel.cs           # 设置
│   │   ├── SearchViewModel.cs             # 搜索
│   │   ├── FavoritesViewModel.cs          # 收藏
│   │   ├── TrashViewModel.cs              # 回收站
│   │   ├── FileDetailViewModel.cs         # 文件详情
│   │   ├── IMChatViewModel.cs             # 即时通讯
│   │   ├── CallViewModel.cs               # 通话
│   │   ├── CallHistoryViewModel.cs        # 通话记录
│   │   ├── SplashViewModel.cs             # 启动页
│   │   └── VirtualDiskViewModel.cs        # 虚拟磁盘
│   ├── Views/                             # 页面
│   │   ├── HomePage.xaml / .cs            # 文件浏览
│   │   ├── LoginPage.xaml / .cs           # 登录
│   │   ├── ProfilePage.xaml / .cs         # 个人中心
│   │   ├── SettingsPage.xaml / .cs        # 设置
│   │   ├── SearchPage.xaml / .cs          # 搜索
│   │   ├── FavoritesPage.xaml / .cs       # 收藏
│   │   ├── TrashPage.xaml / .cs           # 回收站
│   │   ├── FileDetailPage.xaml / .cs      # 文件详情
│   │   ├── IMChatPage.xaml / .cs          # 即时通讯
│   │   ├── CallPage.xaml / .cs            # 通话
│   │   ├── CallHistoryPage.xaml / .cs     # 通话记录
│   │   ├── SplashScreen.xaml / .cs        # 启动页
│   │   └── VirtualDiskPage.xaml / .cs     # 虚拟磁盘
│   ├── Services/                          # 服务层
│   │   ├── Interfaces/                    # 服务接口
│   │   │   ├── IAuthService.cs
│   │   │   ├── IBusinessServices.cs
│   │   │   ├── ISettingsService.cs
│   │   │   ├── IUploadDownloadServices.cs
│   │   │   ├── IIMWebSocketService.cs
│   │   │   ├── IWebRTCMediaService.cs
│   │   │   └── IWebRTCSignalingService.cs
│   │   ├── Implementations/               # 服务实现
│   │   │   ├── AuthService.cs             # 认证
│   │   │   ├── AuthTokenStore.cs          # Token 存储
│   │   │   ├── BusinessServices.cs        # 业务服务
│   │   │   ├── CredentialManagerService.cs # 凭证管理
│   │   │   ├── IMWebSocketService.cs      # IM WebSocket
│   │   │   ├── JumpListService.cs         # 跳转列表
│   │   │   ├── NetworkMonitorService.cs   # 网络监控
│   │   │   ├── ProtocolHandlerService.cs  # 协议处理
│   │   │   ├── SearchIndexService.cs      # 本地搜索索引
│   │   │   ├── SettingsService.cs         # 设置管理
│   │   │   ├── ShareTargetService.cs      # 分享目标
│   │   │   ├── SystemTrayService.cs       # 系统托盘
│   │   │   ├── TaskbarProgressService.cs  # 任务栏进度
│   │   │   ├── ThumbnailService.cs        # 缩略图
│   │   │   ├── ToastNotificationService.cs # 系统通知
│   │   │   ├── UploadDownloadServices.cs  # 上传下载
│   │   │   ├── WebRTCMediaService.cs      # WebRTC 媒体
│   │   │   ├── WebRTCSignalingService.cs  # WebRTC 信令
│   │   │   ├── AdaptiveEncoderService.cs  # 自适应编码
│   │   │   └── WindowsSecurityHardening.cs # 安全加固
│   │   └── VirtualDisk/                   # 虚拟磁盘服务
│   │       ├── VirtualDiskService.cs
│   │       ├── CloudFilesSyncEngine.cs
│   │       └── VirtualDiskModels.cs
│   ├── Helpers/                           # 辅助工具
│   │   ├── FormatHelpers.cs
│   │   ├── ObservableObject.cs
│   │   └── RelayCommand.cs
│   ├── Converters/                        # 值转换器
│   │   └── Converters.cs
│   └── Resources/                         # 资源
│       └── Styles/                        # 样式
│           ├── ButtonStyles.xaml
│           ├── CommonStyles.xaml
│           └── DataGridStyles.xaml
├── PrivateCloudDisk.Downloader/           # 下载器模块
│   ├── Services/
│   │   ├── DownloadService.cs
│   │   └── InstallService.cs
│   ├── ViewModels/
│   │   └── DownloaderViewModel.cs
│   └── Views/
│       ├── MainWindow.xaml / .cs
│       └── App.xaml / .cs
├── PrivateCloudDisk.VirtualDisk/          # 虚拟磁盘驱动
│   ├── CloudFileSystemDriver.cs
│   └── PrivateCloudDisk.VirtualDisk.csproj
└── PrivateCloudDisk.sln                   # 解决方案文件
```

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 文件管理 | 浏览、上传、下载、移动、重命名、删除 |
| 虚拟磁盘 | 云盘挂载为本地磁盘驱动器，按需下载 |
| 文件预览 | 图片、视频、音频、PDF、Office 文档 |
| 即时通讯 | 单聊、群聊、语音/视频通话 (WebRTC) |
| 系统托盘 | 最小化到系统托盘，后台运行 |
| 任务栏进度 | 上传/下载进度显示在任务栏图标 |
| 系统通知 | Toast 通知推送 |
| 跳转列表 | 任务栏右键快速访问常用功能 |
| 安全 | 凭证加密存储、Windows 安全加固 |
| 搜索 | 文件名搜索、本地搜索索引 |
| Thumbnail | 文件缩略图预览 |
| 自适应编码 | 音视频自适应编码 |

---

## 环境要求

| 依赖 | 版本要求 |
|------|----------|
| Windows | 10 1809+ / 11 |
| .NET | 8.0+ |
| Visual Studio | 2022+ (含 .NET 桌面开发工作负载) |

---

## 快速开始

```bash
# 1. 打开解决方案
start PrivateCloudDisk.sln

# 2. 还原 NuGet 包
dotnet restore

# 3. 配置 API 地址
# 修改 PrivateCloudDisk/Services/Implementations/AuthService.cs 中的 API_BASE_URL

# 4. 编译运行
# F5 或 dotnet run
```

---

## 虚拟磁盘驱动

虚拟磁盘功能需要安装 `PrivateCloudDisk.VirtualDisk` 驱动程序。该驱动基于 Cloud Files API 实现，将云端文件挂载为本地磁盘，支持按需下载和离线缓存。

安装方式：
```powershell
# 以管理员身份运行
Install-Module -Name PrivateCloudDisk.VirtualDisk
```
