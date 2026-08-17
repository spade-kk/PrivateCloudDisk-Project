# PrivateCloudDisk Electron Desktop - 企业私有云盘桌面客户端 跨平台 Native

基于 Electron 的跨平台桌面客户端，目标平台为 macOS / Windows / Linux，包含文件访问、本地能力和虚拟磁盘相关模块；实际挂载能力取决于平台依赖、权限和构建结果。

---

## 架构概览

```
┌──────────────────────────────────────────────────────┐
│                Electron 渲染进程 (React)               │
│  文件管理 / 虚拟磁盘管理 / 用户设置                       │
├──────────────────────────────────────────────────────┤
│              contextBridge IPC (preload.js)            │
├──────────────────────────────────────────────────────┤
│                Electron 主进程 (Node.js)               │
│  ┌─────────────────────────────────────────────────┐ │
│  │  VirtualDiskManager                             │ │
│  │  ├─ fork() → 守护子进程                          │ │
│  │  ├─ Unix Socket IPC (JSON 消息协议)               │ │
│  │  └─ macFUSE 依赖检测                             │ │
│  └─────────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────┤
│              守护进程 (daemon.js) - 独立子进程           │
│  ┌──────────┬──────────┬──────────┬───────────────┐ │
│  │ CloudFS  │ Metadata │ Cache    │ SyncManager   │ │
│  │ (FUSE)   │ Store    │ Manager  │ (chokidar)    │ │
│  │          │ (SQLite) │ (LRU)    │               │ │
│  └──────────┴──────────┴──────────┴───────────────┘ │
├──────────────────────────────────────────────────────┤
│                 macOS 内核层                           │
│                 macFUSE (VFS 驱动)                     │
└──────────────────────────────────────────────────────┘
```

---

## 环境要求

| 依赖 | 版本要求 | 说明 |
|---|---|---|
| Node.js | >= 18.x | 推荐 20 LTS |
| npm | >= 9.x | 随 Node.js 附带 |
| Python | >= 3.8 | 编译原生模块（better-sqlite3, fuse-bindings） |
| Xcode Command Line Tools | - | macOS 编译原生模块必需 |
| macFUSE | >= 4.x | macOS 虚拟磁盘驱动（**仅 macOS 需要**） |
| Git | >= 2.x | 版本控制 |

### 平台特定要求

| 平台 | 额外依赖 |
|---|---|
| macOS | macFUSE, Xcode Command Line Tools |
| Windows | Visual Studio Build Tools (`npm install --global windows-build-tools`) |
| Linux | libfuse-dev (`sudo apt install libfuse-dev` / `sudo yum install fuse-devel`) |

---

## macFUSE 安装与依赖检查

### 自动检查脚本

项目内置 macFUSE 检测功能。启动应用后，虚拟磁盘管理页面会自动检测 macFUSE 状态。

也可通过命令行检查：

```bash
# 一键检查脚本
bash scripts/check-deps.sh
```

### 手动安装 macFUSE (macOS)

**方法一：Homebrew 安装（推荐）**

```bash
brew install --cask macfuse
```

安装后**重启系统**使驱动生效。

**方法二：手动下载安装**

1. 访问 [https://osxfuse.github.io/](https://osxfuse.github.io/)
2. 下载最新 `.dmg` 安装包
3. 双击安装，按向导完成
4. 在「系统设置 → 隐私与安全性」中允许 macFUSE 扩展
5. **重启系统**

### 验证 macFUSE 安装

```bash
# 检查 macFUSE 4.x 安装路径
ls /Library/Filesystems/macfuse.fs

# 检查 fuse 库
ls /usr/local/lib/libfuse.dylib /opt/homebrew/lib/libfuse.dylib 2>/dev/null

# 检查内核扩展
kextstat | grep -i fuse
```

预期输出应包含 `macfuse` 相关条目。

---

## 快速开始（开发环境）

### 1. 克隆项目

```bash
git clone <repository-url>
cd PrivateCloudDisk-desktop
```

### 2. 安装依赖

```bash
# 配置国内镜像（可选，加速下载）
npm config set registry https://registry.npmmirror.com
npm config set ELECTRON_MIRROR https://npmmirror.com/mirrors/electron/

# 安装所有依赖
npm install

# 编译原生模块（better-sqlite3, fuse-bindings）
npm run rebuild
```

### 3. 启动开发服务器

```bash
# 同时启动 Vite 前端 + Electron
npm run dev

# 或分别启动
npm run dev:renderer   # 启动 Vite 开发服务器 (localhost:5173)
npm run dev:electron   # 另一个终端启动 Electron
```

### 4. 配置后端 API 地址

在应用设置中配置后端 API 地址，或在启动时通过环境变量指定：

```bash
VD_API_URL=http://your-server:8000 npm run dev
```

---

## 生产构建

### 构建安装包

```bash
# macOS (生成 .dmg)
npm run build:mac

# Windows (生成 .exe 安装包)
npm run build:win

# Linux (生成 .AppImage 和 .deb)
npm run build:linux

# 仅构建渲染进程
npm run build:renderer
```

构建产物位于 `release/` 目录。

### macOS 代码签名（可选）

```bash
export CSC_LINK=~/path/to/certificate.p12
export CSC_KEY_PASSWORD=your-password
export APPLE_ID=your@email.com
export APPLE_APP_SPECIFIC_PASSWORD=xxxx-xxxx-xxxx-xxxx
npm run build:mac
```

---

## 项目结构

```
PrivateCloudDisk-desktop/
├── src/
│   ├── main/                          # Electron 主进程
│   │   ├── main.js                    # 应用入口
│   │   ├── window.js                  # 窗口管理
│   │   ├── menu.js                    # 菜单栏
│   │   ├── tray.js                    # 系统托盘
│   │   ├── ipc.js                     # IPC 处理器
│   │   ├── updater.js                 # 自动更新
│   │   └── virtual-disk/              # 虚拟磁盘核心
│   │       ├── index.js               # VirtualDiskManager（子进程管理 + Socket 通信）
│   │       ├── daemon.js              # FUSE 守护进程入口
│   │       ├── cloud-fs.js            # FUSE 虚拟文件系统操作
│   │       ├── metadata-store.js      # SQLite 元数据管理
│   │       ├── cache-manager.js       # LRU 缓存管理
│   │       ├── sync-manager.js        # 文件变更监听与同步
│   │       ├── ipc-bridge.js          # 主进程 ↔ 渲染进程 IPC 桥接
│   │       └── utils.js               # 通用工具函数
│   ├── preload/
│   │   └── preload.js                 # contextBridge 预加载脚本
│   └── renderer/                      # React 渲染进程
│       ├── pages/
│       │   └── VirtualDisk/           # 虚拟磁盘管理页面
│       ├── components/                # 通用组件
│       ├── api/                       # API 请求封装
│       ├── store/                     # Zustand 状态管理
│       └── utils/                     # 工具函数
├── scripts/                           # 辅助脚本
├── resources/                         # 图标资源
├── package.json                       # 项目配置
├── vite.config.js                     # Vite 构建配置
└── README.md                          # 本文件
```

---

## 启动脚本

### `scripts/check-deps.sh` - 环境依赖检查

```bash
#!/bin/bash
# 一键检查开发环境和运行依赖

set -e
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
echo "========================================"
echo " PrivateCloudDisk 环境依赖检查"
echo "========================================"
echo ""

pass_count=0; fail_count=0; warn_count=0

check() {
    local name="$1"; shift
    if "$@" &>/dev/null; then
        echo -e "  ${GREEN}[PASS]${NC} $name"
        ((pass_count++))
    else
        echo -e "  ${RED}[FAIL]${NC} $name"
        ((fail_count++))
    fi
}

check_macfuse() {
    if [ "$(uname)" != "Darwin" ]; then
        echo -e "  ${GREEN}[SKIP]${NC} macFUSE (非 macOS, 跳过)"
        return
    fi
    if [ -d "/Library/Filesystems/macfuse.fs" ] || \
       [ -f "/usr/local/lib/libfuse.dylib" ] || \
       [ -f "/opt/homebrew/lib/libfuse.dylib" ]; then
        echo -e "  ${GREEN}[PASS]${NC} macFUSE"
        ((pass_count++))
    else
        echo -e "  ${YELLOW}[WARN]${NC} macFUSE 未安装 (虚拟磁盘功能不可用)"
        echo "    安装: brew install --cask macfuse"
        echo "    下载: https://osxfuse.github.io/"
        ((warn_count++))
    fi
}

echo "--- 基础环境 ---"
check "Node.js >= 18" node -e "process.exit(parseInt(process.version.slice(1)) < 18)"
echo "  Node.js 版本: $(node -v)"
check "npm >= 9" npm -v
echo "  npm 版本: $(npm -v)"
check "Git" git --version
echo ""

echo "--- 编译工具 ---"
check "Python 3" python3 --version
if [ "$(uname)" = "Darwin" ]; then
    check "Xcode CLI Tools" xcode-select -p
fi
if [ "$(uname)" = "Linux" ]; then
    check "build-essential / make" make --version
fi
echo ""

echo "--- 虚拟磁盘驱动 ---"
check_macfuse
echo ""

echo "--- 汇总 ---"
echo -e "  通过: ${GREEN}${pass_count}${NC}"
echo -e "  失败: ${RED}${fail_count}${NC}"
echo -e "  警告: ${YELLOW}${warn_count}${NC}"

if [ "$fail_count" -gt 0 ]; then
    echo ""
    echo -e "${RED}部分必需依赖未安装, 请先修复后再启动项目。${NC}"
    exit 1
elif [ "$warn_count" -gt 0 ]; then
    echo ""
    echo -e "${YELLOW}存在警告项, 部分功能可能受限。${NC}"
fi
echo ""
echo "检查完成。"
```

### `scripts/start.sh` - 一键启动开发环境

```bash
#!/bin/bash
# 一键启动开发环境

set -e
GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN} PrivateCloudDisk 开发环境启动${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查依赖
echo "正在检查环境依赖..."
bash "$(dirname "$0")/check-deps.sh" || {
    echo -e "${RED}依赖检查未通过, 请修复后重试。${NC}"
    exit 1
}
echo ""

# 安装依赖
if [ ! -d "node_modules" ]; then
    echo "正在安装项目依赖..."
    npm install
fi

# 编译原生模块
echo "正在编译原生模块..."
npm run rebuild 2>/dev/null || echo "  原生模块编译跳过（首次启动需要）"

echo ""
echo -e "${GREEN}启动开发服务器...${NC}"
echo "  Vite 前端: http://localhost:5173"
echo "  Electron 窗口将自动打开"
echo ""

npm run dev
```

---

## 配置说明

### 应用配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `mountPoint` | `~/PrivateCloudDisk` | 虚拟磁盘挂载点 |
| `cacheMaxSize` | 5 GB | 本地缓存最大容量 |
| `apiBaseUrl` | `http://localhost:8000` | 后端 API 地址 |
| `debounceMs` | 2000 | 文件同步防抖间隔（毫秒） |
| `cacheTTL` | 7 天 | 缓存文件有效期 |

### 后端 API 要求

虚拟磁盘功能需要后端提供以下 API：

| 端点 | 方法 | 说明 |
|---|---|---|
| `/files/{path}/content` | GET | 下载文件内容（支持 Range） |
| `/files/list?path={path}` | GET | 列出目录内容 |
| `/files/stat?path={path}` | GET | 获取文件元信息 |
| `/files/upload/sessions` | POST | 创建上传会话 |
| `/files/upload/sessions/{id}/chunks` | POST | 上传分片 |
| `/files/upload/sessions/{id}/complete` | POST | 完成上传 |
| `/files/create-folder` | POST | 创建文件夹 |
| `/files/delete` | POST | 删除文件/目录 |
| `/files/rename` | POST | 重命名文件/目录 |

---

## 常见问题

### Q: 启动后虚拟磁盘管理页面显示 "macFUSE 未安装"

按照上方 [macFUSE 安装](#macfuse-安装与依赖检查) 步骤安装，安装后**必须重启系统**。

### Q: `npm install` 报错 `better-sqlite3` 编译失败

```bash
# macOS: 确保安装了 Xcode Command Line Tools
xcode-select --install

# 清理缓存重试
rm -rf node_modules package-lock.json
npm cache clean --force
npm install
```

### Q: 挂载失败，提示权限不足

```bash
# macOS: 确保 macFUSE 内核扩展已加载
# 系统设置 → 隐私与安全性 → 允许 macFUSE
sudo kextload /Library/Filesystems/macfuse.fs/Support/macfuse.kext 2>/dev/null || true
```

### Q: 文件修改后未自动同步上传

1. 检查后端 API 是否可达
2. 观察虚拟磁盘管理页面的事件日志面板
3. 确认同步防抖间隔（默认 2 秒），修改后等待 2 秒再检查

### Q: Electron 下载失败（国内网络）

```bash
export ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
npm install
```

### Q: `fuse-bindings` 编译失败

```bash
# macOS: 确保 macFUSE 已安装
brew install --cask macfuse

# Linux: 安装 fuse 开发库
sudo apt install libfuse-dev pkg-config

# Windows: 不支持 FUSE，虚拟磁盘功能仅在 macOS/Linux 可用
```

---

## 技术栈

| 层级 | 技术 |
|---|---|
| 桌面框架 | Electron 28 |
| 前端框架 | React 18 + Vite 5 |
| UI 组件库 | Ant Design 5 |
| 状态管理 | Zustand |
| 构建工具 | electron-builder + Vite |
| 数据库 | better-sqlite3 (元数据) |
| 文件系统 | fuse-bindings + macFUSE |
| 文件监听 | chokidar |
| 进程通信 | Unix Socket (JSON 消息协议) |

---

## License

UNLICENSED - Private Use Only
