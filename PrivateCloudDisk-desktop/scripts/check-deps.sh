#!/bin/bash
# ============================================
# PrivateCloudDisk 环境依赖检查脚本
# ============================================
# 检查开发环境和运行所需的所有依赖
# 用法: bash scripts/check-deps.sh
# ============================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo "========================================"
echo " PrivateCloudDisk 环境依赖检查"
echo "========================================"
echo ""

pass_count=0
fail_count=0
warn_count=0

# --- 工具函数 ---

check() {
    local name="$1"
    shift
    if "$@" &>/dev/null; then
        echo -e "  ${GREEN}[PASS]${NC} $name"
        ((pass_count++))
    else
        echo -e "  ${RED}[FAIL]${NC} $name"
        ((fail_count++))
    fi
}

check_version() {
    local name="$1"; shift
    local cmd="$1"; shift
    local min_ver="$1"
    local version=""
    version=$($cmd 2>/dev/null | grep -oE '[0-9]+' | head -1 || true)
    if [ -n "$version" ] && [ "$version" -ge "$min_ver" ]; then
        echo -e "  ${GREEN}[PASS]${NC} $name"
        ((pass_count++))
    else
        echo -e "  ${RED}[FAIL]${NC} $name (需要 >= $min_ver, 当前: ${version:-未知})"
        ((fail_count++))
    fi
}

check_python() {
    if python3 --version &>/dev/null; then
        local ver
        ver=$(python3 --version 2>&1 | grep -oE '[0-9]+\.[0-9]+' | head -1)
        local major
        major=$(echo "$ver" | cut -d. -f1)
        local minor
        minor=$(echo "$ver" | cut -d. -f2)
        if [ "$major" -ge 3 ] && [ "$minor" -ge 8 ]; then
            echo -e "  ${GREEN}[PASS]${NC} Python 3 (>= 3.8)"
            echo "    版本: $ver"
            ((pass_count++))
            return
        fi
    fi
    echo -e "  ${RED}[FAIL]${NC} Python 3 (>= 3.8)"
    ((fail_count++))
}

check_macfuse() {
    local platform
    platform=$(uname)
    if [ "$platform" != "Darwin" ]; then
        echo -e "  ${CYAN}[SKIP]${NC} macFUSE (非 macOS 平台, 跳过)"
        return
    fi

    local found=false
    local locations=(
        "/Library/Filesystems/macfuse.fs"
        "/usr/local/lib/libfuse.dylib"
        "/usr/local/lib/libosxfuse.dylib"
        "/opt/local/lib/libfuse.dylib"
        "/opt/homebrew/lib/libfuse.dylib"
    )

    for loc in "${locations[@]}"; do
        if [ -e "$loc" ]; then
            echo -e "  ${GREEN}[PASS]${NC} macFUSE"
            echo "    路径: $loc"
            ((pass_count++))
            found=true
            break
        fi
    done

    if [ "$found" = false ]; then
        echo -e "  ${YELLOW}[WARN]${NC} macFUSE 未安装"
        echo "    虚拟磁盘挂载功能将不可用。"
        echo "    安装方法 1: brew install --cask macfuse"
        echo "    安装方法 2: 下载 https://osxfuse.github.io/"
        echo "    安装后请重启系统。"
        ((warn_count++))
    fi
}

check_fuse_linux() {
    local platform
    platform=$(uname)
    if [ "$platform" != "Linux" ]; then
        echo -e "  ${CYAN}[SKIP]${NC} libfuse (非 Linux 平台, 跳过)"
        return
    fi

    if ldconfig -p 2>/dev/null | grep -q libfuse || \
       find /usr/lib* -name "libfuse*.so*" 2>/dev/null | grep -q . ; then
        echo -e "  ${GREEN}[PASS]${NC} libfuse"
        ((pass_count++))
    else
        echo -e "  ${YELLOW}[WARN]${NC} libfuse 未安装"
        echo "    安装: sudo apt install libfuse-dev  (Debian/Ubuntu)"
        echo "    或:   sudo yum install fuse-devel    (RHEL/CentOS)"
        ((warn_count++))
    fi
}

check_xcode() {
    if [ "$(uname)" != "Darwin" ]; then
        echo -e "  ${CYAN}[SKIP]${NC} Xcode CLI Tools (非 macOS, 跳过)"
        return
    fi
    if xcode-select -p &>/dev/null; then
        echo -e "  ${GREEN}[PASS]${NC} Xcode Command Line Tools"
        ((pass_count++))
    else
        echo -e "  ${RED}[FAIL]${NC} Xcode Command Line Tools"
        echo "    安装: xcode-select --install"
        ((fail_count++))
    fi
}

check_disk_space() {
    local required_gb=2
    local available_gb
    available_gb=$(df -g . 2>/dev/null | tail -1 | awk '{print $4}')
    if [ -z "$available_gb" ]; then
        available_gb=999
    fi
    if [ "$available_gb" -ge "$required_gb" ]; then
        echo -e "  ${GREEN}[PASS]${NC} 磁盘空间 (可用 ${available_gb}GB)"
        ((pass_count++))
    else
        echo -e "  ${YELLOW}[WARN]${NC} 磁盘空间不足 (可用 ${available_gb}GB, 建议 >= ${required_gb}GB)"
        ((warn_count++))
    fi
}

# ============================================
# 检查开始
# ============================================

echo "--- 基础环境 ---"
check_version  "Node.js >= 18"      "node -v"       18
echo "  Node.js 版本: $(node -v 2>/dev/null || echo '未安装')"
check_version  "npm >= 9"           "npm -v"        9
echo "  npm 版本: $(npm -v 2>/dev/null || echo '未安装')"
check          "Git"                git --version
echo ""

echo "--- 编译工具链 ---"
check_python
check_xcode
if [ "$(uname)" = "Linux" ]; then
    check "build-essential / make"  make --version
fi
check_disk_space
echo ""

echo "--- 虚拟磁盘驱动 ---"
check_macfuse
check_fuse_linux
echo ""

echo "--- Node.js 原生模块编译环境 ---"
if command -v node-gyp &>/dev/null; then
    echo -e "  ${GREEN}[PASS]${NC} node-gyp"
    ((pass_count++))
else
    echo -e "  ${YELLOW}[INFO]${NC} node-gyp (将通过 npm 自动安装)"
fi

# 检查 npm 原生编译工具
if node -e "try{require('node-gyp')}catch(e){process.exit(1)}" 2>/dev/null; then
    echo -e "  ${GREEN}[PASS]${NC} node-gyp 模块可用"
    ((pass_count++))
else
    echo -e "  ${YELLOW}[INFO]${NC} node-gyp 模块 (npm install 时将安装)"
fi
echo ""

# --- 汇总 ---
echo "========================================"
echo " 检查结果汇总"
echo "========================================"
echo -e "  通过: ${GREEN}${pass_count}${NC}"
echo -e "  失败: ${RED}${fail_count}${NC}"
echo -e "  警告: ${YELLOW}${warn_count}${NC}"
echo ""

if [ "$fail_count" -gt 0 ]; then
    echo -e "${RED}部分必需依赖未安装，请先修复后再继续。${NC}"
    exit 1
elif [ "$warn_count" -gt 0 ]; then
    echo -e "${YELLOW}存在警告项，部分功能可能受限。建议安装缺失依赖以获得完整体验。${NC}"
else
    echo -e "${GREEN}所有依赖检查通过，可以启动项目。${NC}"
fi
echo ""
echo "运行 'npm run dev' 启动开发环境。"
echo "或运行 'bash scripts/start.sh' 一键启动。"