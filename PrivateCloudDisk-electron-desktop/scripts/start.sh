#!/bin/bash
# ============================================
# PrivateCloudDisk 一键启动脚本
# ============================================
# 自动检查依赖、安装依赖、编译原生模块并启动开发环境
# 用法: bash scripts/start.sh
# ============================================

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

echo -e "${GREEN}${BOLD}========================================${NC}"
echo -e "${GREEN}${BOLD} PrivateCloudDisk 开发环境启动${NC}"
echo -e "${GREEN}${BOLD}========================================${NC}"
echo ""

# ============================================
# 1. 环境依赖检查
# ============================================
echo -e "${CYAN}[1/5]${NC} 检查环境依赖..."
echo ""

bash "$PROJECT_DIR/scripts/check-deps.sh" || {
    echo ""
    echo -e "${RED}依赖检查未通过，请修复后重试。${NC}"
    exit 1
}

echo ""

# ============================================
# 2. 配置 npm 镜像 (可选加速)
# ============================================
echo -e "${CYAN}[2/5]${NC} 检查 npm 配置..."
echo ""

# 如果使用默认 registry 且网络较慢，提示配置镜像
current_registry=$(npm config get registry 2>/dev/null || echo "https://registry.npmjs.org/")
if [ "$current_registry" = "https://registry.npmjs.org/" ]; then
    echo -e "  ${YELLOW}提示:${NC} 当前使用 npm 官方源，国内网络可能较慢。"
    echo -e "  可运行以下命令切换到国内镜像:"
    echo -e "    npm config set registry https://registry.npmmirror.com"
    echo -e "    npm config set ELECTRON_MIRROR https://npmmirror.com/mirrors/electron/"
    echo ""
fi

# ============================================
# 3. 安装项目依赖
# ============================================
echo -e "${CYAN}[3/5]${NC} 检查项目依赖..."
echo ""

needs_install=false

if [ ! -d "node_modules" ]; then
    needs_install=true
elif [ ! -f "node_modules/.package-lock.json" ] && [ package.json -nt node_modules ]; then
    needs_install=true
fi

if [ "$needs_install" = true ]; then
    echo "  正在安装项目依赖 (npm install)..."
    echo "  这可能需要几分钟，请耐心等待..."
    echo ""

    npm install || {
        echo ""
        echo -e "${RED}依赖安装失败。${NC}"
        echo "常见解决方案:"
        echo "  1. 清理缓存: rm -rf node_modules package-lock.json && npm cache clean --force"
        echo "  2. 使用国内镜像: npm config set registry https://registry.npmmirror.com"
        echo "  3. 重试: npm install"
        exit 1
    }

    echo ""
    echo -e "  ${GREEN}依赖安装完成。${NC}"
else
    echo -e "  ${GREEN}依赖已是最新，跳过安装。${NC}"
fi

echo ""

# ============================================
# 4. 编译原生模块
# ============================================
echo -e "${CYAN}[4/5]${NC} 编译原生模块 (better-sqlite3, fuse-bindings)..."
echo ""

# 检查是否需要重新编译
needs_rebuild=false

if [ ! -f "node_modules/.rebuild-done" ]; then
    needs_rebuild=true
elif [ ! -d "node_modules/better-sqlite3/build" ]; then
    needs_rebuild=true
fi

if [ "$needs_rebuild" = true ]; then
    if npm run rebuild 2>/dev/null; then
        touch node_modules/.rebuild-done
        echo -e "  ${GREEN}原生模块编译成功。${NC}"
    else
        echo ""
        echo -e "  ${YELLOW}警告:${NC} 原生模块编译失败，虚拟磁盘功能可能不可用。"
        echo "  基础文件管理功能不受影响。"
        echo ""
        # 检查是否在 macOS 上但缺少 macFUSE
        if [ "$(uname)" = "Darwin" ]; then
            if [ ! -d "/Library/Filesystems/macfuse.fs" ]; then
                echo -e "  ${YELLOW}原因:${NC} 未检测到 macFUSE，fuse-bindings 编译需要 macFUSE。"
                echo "  安装: brew install --cask macfuse (安装后重启系统)"
            fi
        fi
    fi
else
    echo -e "  ${GREEN}原生模块无需重新编译，跳过。${NC}"
fi

echo ""

# ============================================
# 5. 启动开发服务器
# ============================================
echo -e "${CYAN}[5/5]${NC} 启动开发服务器..."
echo ""
echo -e "  ${GREEN}Vite 前端:${NC}  http://localhost:5173"
echo -e "  ${GREEN}Electron:${NC}  窗口将自动打开"
echo -e "  ${YELLOW}提示:${NC}      按 Ctrl+C 停止所有服务"
echo -e "  ${YELLOW}调试:${NC}      Electron 窗口内 Cmd+Option+I 打开 DevTools"
echo ""

npm run dev