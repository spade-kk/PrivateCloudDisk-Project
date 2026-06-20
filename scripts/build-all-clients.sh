#!/usr/bin/env bash
# ============================================================
# PrivateCloudDisk - 全客户端构建脚本
# ============================================================
# 功能: 一键构建所有客户端应用，输出到 downloads/binaries/
#
# 支持构建:
#   - Go CLI 客户端        (Linux/macOS/Windows × amd64/arm64)
#   - Electron 桌面客户端   (macOS .dmg / Windows .exe / Linux .AppImage)
#   - Android 原生客户端    (.apk)
#   - iOS 原生客户端        (.ipa, 占位)
#   - uni-app 跨端客户端    (H5/小程序, 占位)
#   - Admin Web 管理后台    (静态资源)
#
# 用法:
#   ./scripts/build-all-clients.sh                    # 构建所有平台
#   ./scripts/build-all-clients.sh --platform macos   # 仅构建 macOS
#   ./scripts/build-all-clients.sh --platform android # 仅构建 Android
#   ./scripts/build-all-clients.sh --cli-only         # 仅构建 CLI
#   ./scripts/build-all-clients.sh --version 3.2.0    # 指定版本号
#   ./scripts/build-all-clients.sh --skip-upload      # 构建但不上传
#   ./scripts/build-all-clients.sh --dry-run          # 模拟运行
#
# 依赖:
#   - Go 1.22+        (CLI 构建)
#   - Node.js 20+     (Electron/Admin Web 构建)
#   - JDK 17+         (Android 构建)
#   - Android SDK      (Android 构建)
#   - Xcode 15+        (iOS 构建, 仅 macOS)
#   - electron-builder (Electron 打包)
# ============================================================

set -euo pipefail
IFS=$'\n\t'

# ============================================================
# 配置
# ============================================================

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 输出目录
OUTPUT_DIR="${PROJECT_ROOT}/downloads/binaries"
CHECKSUM_DIR="${OUTPUT_DIR}/checksums"
MANIFEST_FILE="${OUTPUT_DIR}/manifest.json"

# 版本号
VERSION="${VERSION:-}"
if [ -z "$VERSION" ]; then
	VERSION=$(git describe --tags --always --dirty 2>/dev/null || echo "0.0.0-dev")
	VERSION="${VERSION#v}"
fi
BUILD_TIME=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

# 构建开关
BUILD_CLI=true
BUILD_ELECTRON=true
BUILD_ANDROID=true
BUILD_IOS=true
BUILD_UNIAPP=true
BUILD_ADMIN_WEB=true
SKIP_UPLOAD=false
DRY_RUN=false
TARGET_PLATFORM="all"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# ============================================================
# 参数解析
# ============================================================

usage() {
	cat <<-EOF
	${BOLD}PrivateCloudDisk 全客户端构建脚本${NC}

	用法: $0 [选项]

	选项:
	  --platform <name>      仅构建指定平台 (macos|windows|linux|android|ios|all)
	  --cli-only             仅构建 Go CLI 客户端
	  --electron-only        仅构建 Electron 桌面客户端
	  --android-only         仅构建 Android 客户端
	  --admin-only           仅构建 Admin Web 管理后台
	  --version <ver>        指定版本号 (默认从 git tag 获取)
	  --skip-upload          跳过上传到下载服务器
	  --dry-run              模拟运行，不实际构建
	  --help, -h             显示帮助

	示例:
	  $0                                    # 构建所有客户端
	  $0 --platform macos                   # 仅构建 macOS 桌面客户端
	  $0 --cli-only --version 3.2.0         # 仅构建 CLI v3.2.0
	  $0 --dry-run                          # 预览构建计划
	EOF
	exit 0
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--platform)
			TARGET_PLATFORM="$2"
			shift 2
			;;
		--cli-only)
			BUILD_ELECTRON=false
			BUILD_ANDROID=false
			BUILD_IOS=false
			BUILD_UNIAPP=false
			BUILD_ADMIN_WEB=false
			shift
			;;
		--electron-only)
			BUILD_CLI=false
			BUILD_ANDROID=false
			BUILD_IOS=false
			BUILD_UNIAPP=false
			BUILD_ADMIN_WEB=false
			shift
			;;
		--android-only)
			BUILD_CLI=false
			BUILD_ELECTRON=false
			BUILD_IOS=false
			BUILD_UNIAPP=false
			BUILD_ADMIN_WEB=false
			shift
			;;
		--admin-only)
			BUILD_CLI=false
			BUILD_ELECTRON=false
			BUILD_ANDROID=false
			BUILD_IOS=false
			BUILD_UNIAPP=false
			shift
			;;
		--version)
			VERSION="$2"
			shift 2
			;;
		--skip-upload)
			SKIP_UPLOAD=true
			shift
			;;
		--dry-run)
			DRY_RUN=true
			shift
			;;
		--help|-h)
			usage
			;;
		*)
			echo -e "${RED}未知选项: $1${NC}"
			usage
			;;
	esac
done

# ============================================================
# 工具函数
# ============================================================

log_info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_success() { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()    { echo -e "\n${CYAN}${BOLD}━━━ $* ━━━${NC}"; }
log_build()   { echo -e "${BOLD}  ▶ $*${NC}"; }

# 检查命令是否存在
require_cmd() {
	if ! command -v "$1" &>/dev/null; then
		log_error "缺少依赖: $1 (请先安装)"
		if [ "${2:-}" != "optional" ]; then
			exit 1
		fi
		return 1
	fi
	return 0
}

# 创建输出目录
ensure_dir() {
	if [ "$DRY_RUN" = true ]; then
		log_info "[DRY-RUN] mkdir -p $1"
		return
	fi
	mkdir -p "$1"
}

# 复制文件到输出目录
copy_artifact() {
	local src="$1"
	local dest_name="$2"

	if [ "$DRY_RUN" = true ]; then
		log_info "[DRY-RUN] cp $src → $OUTPUT_DIR/$dest_name"
		return
	fi

	if [ -f "$src" ]; then
		cp "$src" "$OUTPUT_DIR/$dest_name"
		log_success "已输出: $dest_name"
	else
		log_warn "产物不存在: $src"
	fi
}

# 计算 SHA256
compute_sha256() {
	local file="$1"
	if [ "$DRY_RUN" = true ]; then
		echo "DRY_RUN_SHA256"
		return
	fi
	shasum -a 256 "$file" | awk '{print $1}'
}

# 记录产物到 manifest
record_artifact() {
	local name="$1"
	local file="$2"
	local platform="$3"
	local arch="$4"

	if [ "$DRY_RUN" = true ]; then
		return
	fi

	local sha256=""
	local size=""
	if [ -f "$file" ]; then
		sha256=$(compute_sha256 "$file")
		size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo 0)
	fi

	# 追加到 manifest 数组
	MANIFEST_ENTRIES+=("$(cat <<-JSON
	    {
	      "name": "$name",
	      "file": "$(basename "$file")",
	      "platform": "$platform",
	      "arch": "$arch",
	      "version": "$VERSION",
	      "size": $size,
	      "sha256": "$sha256",
	      "build_time": "$BUILD_TIME"
	    }
	JSON
	)")
}

# ============================================================
# 环境检查
# ============================================================

check_environment() {
	log_step "环境检查"

	# 操作系统
	log_info "操作系统: $(uname -s) $(uname -m)"
	log_info "项目根目录: $PROJECT_ROOT"
	log_info "输出目录: $OUTPUT_DIR"
	log_info "版本: $VERSION"
	log_info "Commit: $COMMIT"
	echo ""

	# 检查依赖
	if [ "$BUILD_CLI" = true ]; then
		require_cmd "go" "required" && log_info "Go: $(go version)"
	fi
	if [ "$BUILD_ELECTRON" = true ] || [ "$BUILD_ADMIN_WEB" = true ]; then
		require_cmd "node" "required" && log_info "Node.js: $(node --version)"
		require_cmd "npm" "required" && log_info "npm: $(npm --version)"
	fi
	if [ "$BUILD_ANDROID" = true ]; then
		require_cmd "java" "optional" && log_info "Java: $(java -version 2>&1 | head -1)"
		require_cmd "gradle" "optional" || require_cmd "gradlew" "optional"
	fi
	if [ "$BUILD_IOS" = true ]; then
		require_cmd "xcodebuild" "optional" && log_info "Xcode: $(xcodebuild -version | head -1)"
	fi
	echo ""
}

# ============================================================
# 创建输出目录
# ============================================================

prepare_output_dir() {
	log_step "准备输出目录"
	ensure_dir "$OUTPUT_DIR"
	ensure_dir "$CHECKSUM_DIR"
	ensure_dir "${OUTPUT_DIR}/cli"
	ensure_dir "${OUTPUT_DIR}/desktop"
	ensure_dir "${OUTPUT_DIR}/android"
	ensure_dir "${OUTPUT_DIR}/ios"
	ensure_dir "${OUTPUT_DIR}/uniapp"
	ensure_dir "${OUTPUT_DIR}/admin-web"

	# 初始化 manifest 条目数组
	MANIFEST_ENTRIES=()

	log_info "输出根目录: $OUTPUT_DIR"
	echo ""
}

# ============================================================
# 1. 构建 Go CLI 客户端
# ============================================================

build_cli() {
	log_step "1. 构建 Go CLI 客户端 (pcd)"

	local cli_dir="$PROJECT_ROOT/PrivateCloudDisk-cli"
	cd "$cli_dir"

	# 确保依赖
	if [ "$DRY_RUN" = false ]; then
		go mod tidy
		go mod download
	fi

	# 多平台构建矩阵
	declare -A CLI_PLATFORMS=(
		["linux_amd64"]="linux/amd64"
		["linux_arm64"]="linux/arm64"
		["darwin_amd64"]="darwin/amd64"
		["darwin_arm64"]="darwin/arm64"
		["windows_amd64"]="windows/amd64"
	)

	local ldflags="-s -w -X main.Version=${VERSION} -X main.Commit=${COMMIT} -X main.BuildTime=${BUILD_TIME}"

	for key in "${!CLI_PLATFORMS[@]}"; do
		IFS='/' read -r goos goarch <<< "${CLI_PLATFORMS[$key]}"

		# 平台过滤
		if [ "$TARGET_PLATFORM" != "all" ]; then
			case "$TARGET_PLATFORM" in
				macos)   [[ "$goos" != "darwin" ]] && continue ;;
				windows) [[ "$goos" != "windows" ]] && continue ;;
				linux)   [[ "$goos" != "linux" ]] && continue ;;
			esac
		fi

		local binary_name="pcd"
		if [ "$goos" = "windows" ]; then
			binary_name="pcd.exe"
		fi

		local output_name="pcd_${VERSION}_${goos}_${goarch}"
		local output_path="${OUTPUT_DIR}/cli/${output_name}"

		log_build "构建 CLI: ${goos}/${goarch}"

		if [ "$DRY_RUN" = false ]; then
			GOOS="$goos" GOARCH="$goarch" CGO_ENABLED=0 \
				go build -ldflags "$ldflags" -o "${output_path}/${binary_name}" .
		fi

		# 打包
		if [ "$goos" = "windows" ]; then
			if [ "$DRY_RUN" = false ]; then
				cd "$OUTPUT_DIR/cli"
				zip -r "${output_name}.zip" "${output_name}" >/dev/null 2>&1
				cd "$cli_dir"
			fi
			copy_artifact "${OUTPUT_DIR}/cli/${output_name}.zip" "cli/${output_name}.zip"
			record_artifact "pcd-cli" "${OUTPUT_DIR}/cli/${output_name}.zip" "$goos" "$goarch"
		else
			if [ "$DRY_RUN" = false ]; then
				cd "$OUTPUT_DIR/cli"
				tar -czf "${output_name}.tar.gz" "${output_name}" 2>/dev/null
				cd "$cli_dir"
			fi
			copy_artifact "${OUTPUT_DIR}/cli/${output_name}.tar.gz" "cli/${output_name}.tar.gz"
			record_artifact "pcd-cli" "${OUTPUT_DIR}/cli/${output_name}.tar.gz" "$goos" "$goarch"
		fi
	done

	# 生成安装脚本
	generate_cli_install_script

	log_success "Go CLI 客户端构建完成"
	cd "$PROJECT_ROOT"
}

# 生成 CLI 安装脚本
generate_cli_install_script() {
	if [ "$DRY_RUN" = true ]; then
		return
	fi

	cat > "$OUTPUT_DIR/cli/install.sh" <<-'INSTALL_SCRIPT'
#!/usr/bin/env bash
# PrivateCloudDisk CLI 一键安装脚本
set -euo pipefail

BASE_URL="${PCD_DOWNLOAD_URL:-https://download.example.com/cli}"
VERSION="${PCD_VERSION:-latest}"
INSTALL_DIR="${PCD_INSTALL_DIR:-/usr/local/bin}"

OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)
case "$ARCH" in
	x86_64)  ARCH="amd64" ;;
	aarch64) ARCH="arm64" ;;
	arm64)   ARCH="arm64" ;;
esac

echo "安装 PrivateCloudDisk CLI v${VERSION} (${OS}/${ARCH})..."

if [ "$OS" = "windows" ]; then
	EXT="zip"
	BINARY="pcd.exe"
else
	EXT="tar.gz"
	BINARY="pcd"
fi

URL="${BASE_URL}/pcd_${VERSION}_${OS}_${ARCH}.${EXT}"
TMP_DIR=$(mktemp -d)
curl -fsSL "$URL" -o "$TMP_DIR/pcd.${EXT}"

if [ "$EXT" = "zip" ]; then
	unzip -q "$TMP_DIR/pcd.zip" -d "$TMP_DIR"
else
	tar -xzf "$TMP_DIR/pcd.tar.gz" -C "$TMP_DIR"
fi

sudo mv "$TMP_DIR/pcd_${VERSION}_${OS}_${ARCH}/${BINARY}" "$INSTALL_DIR/pcd"
sudo chmod +x "$INSTALL_DIR/pcd"
rm -rf "$TMP_DIR"

echo "安装完成! 运行 pcd --version 验证"
INSTALL_SCRIPT
	chmod +x "$OUTPUT_DIR/cli/install.sh"
	log_success "CLI 安装脚本: cli/install.sh"
}

# ============================================================
# 2. 构建 Electron 桌面客户端
# ============================================================

build_electron() {
	log_step "2. 构建 Electron 桌面客户端"

	local electron_dir="$PROJECT_ROOT/PrivateCloudDisk-electron-desktop"
	cd "$electron_dir"

	# 安装依赖
	if [ "$DRY_RUN" = false ]; then
		log_info "安装 npm 依赖..."
		npm ci --silent 2>/dev/null || npm install --silent 2>/dev/null
	fi

	# 构建 macOS
	build_electron_macos() {
		if [ "$TARGET_PLATFORM" != "all" ] && [ "$TARGET_PLATFORM" != "macos" ]; then
			return
		fi
		if [ "$(uname -s)" != "Darwin" ] && [ "$DRY_RUN" = false ]; then
			log_warn "macOS 构建需要在 macOS 环境下执行，跳过"
			return
		fi

		log_build "构建 macOS (.dmg)"
		if [ "$DRY_RUN" = false ]; then
			npm run build:mac 2>&1 || log_warn "macOS 构建失败，请检查环境"
		fi

		# 收集 mac 产物
		local dist_dir="$electron_dir/dist"
		if [ -d "$dist_dir" ]; then
			for dmg in "$dist_dir"/*.dmg; do
				[ -f "$dmg" ] || continue
				local name=$(basename "$dmg")
				copy_artifact "$dmg" "desktop/$name"
				record_artifact "electron-desktop" "$OUTPUT_DIR/desktop/$name" "macos" "universal"
			done
			for zip in "$dist_dir"/*-mac.zip; do
				[ -f "$zip" ] || continue
				local name=$(basename "$zip")
				copy_artifact "$zip" "desktop/$name"
				record_artifact "electron-desktop" "$OUTPUT_DIR/desktop/$name" "macos" "universal"
			done
		fi
	}

	# 构建 Windows
	build_electron_windows() {
		if [ "$TARGET_PLATFORM" != "all" ] && [ "$TARGET_PLATFORM" != "windows" ]; then
			return
		fi

		log_build "构建 Windows (.exe)"
		if [ "$DRY_RUN" = false ]; then
			npm run build:win 2>&1 || log_warn "Windows 构建失败，请检查环境"
		fi

		local dist_dir="$electron_dir/dist"
		if [ -d "$dist_dir" ]; then
			for exe in "$dist_dir"/*.exe; do
				[ -f "$exe" ] || continue
				local name=$(basename "$exe")
				copy_artifact "$exe" "desktop/$name"
				record_artifact "electron-desktop" "$OUTPUT_DIR/desktop/$name" "windows" "x64"
			done
		fi
	}

	# 构建 Linux
	build_electron_linux() {
		if [ "$TARGET_PLATFORM" != "all" ] && [ "$TARGET_PLATFORM" != "linux" ]; then
			return
		fi

		log_build "构建 Linux (.AppImage / .deb / .rpm)"
		if [ "$DRY_RUN" = false ]; then
			npm run build:linux 2>&1 || log_warn "Linux 构建失败，请检查环境"
		fi

		local dist_dir="$electron_dir/dist"
		if [ -d "$dist_dir" ]; then
			for pkg in "$dist_dir"/*.AppImage "$dist_dir"/*.deb "$dist_dir"/*.rpm; do
				[ -f "$pkg" ] || continue
				local name=$(basename "$pkg")
				copy_artifact "$pkg" "desktop/$name"
				record_artifact "electron-desktop" "$OUTPUT_DIR/desktop/$name" "linux" "x64"
			done
		fi
	}

	build_electron_macos
	build_electron_windows
	build_electron_linux

	log_success "Electron 桌面客户端构建完成"
	cd "$PROJECT_ROOT"
}

# ============================================================
# 3. 构建 Android 原生客户端
# ============================================================

build_android() {
	log_step "3. 构建 Android 原生客户端 (.apk)"

	if [ "$TARGET_PLATFORM" != "all" ] && [ "$TARGET_PLATFORM" != "android" ]; then
		return
	fi

	local android_dir="$PROJECT_ROOT/PrivateCloudDisk-android"
	cd "$android_dir"

	# 构建 release APK
	log_build "构建 Android APK (release)"

	if [ "$DRY_RUN" = false ]; then
		# 确保 gradlew 可执行
		chmod +x gradlew 2>/dev/null || true

		# 设置版本信息
		if [ -f "gradle.properties" ]; then
			# 更新版本号（如果文件中有 VERSION_NAME）
			sed -i.bak "s/^VERSION_NAME=.*/VERSION_NAME=${VERSION}/" gradle.properties 2>/dev/null || true
			sed -i.bak "s/^VERSION_CODE=.*/VERSION_CODE=${VERSION//./}/" gradle.properties 2>/dev/null || true
		fi

		# 构建
		./gradlew assembleRelease 2>&1 || log_warn "Android 构建失败，请检查 SDK 配置"
	fi

	# 收集 APK 产物
	local apk_paths=(
		"$android_dir/app/build/outputs/apk/release/app-release.apk"
		"$android_dir/app/build/outputs/apk/release/app-release-unsigned.apk"
		"$android_dir/app/build/outputs/bundle/release/app-release.aab"
	)

	for apk in "${apk_paths[@]}"; do
		if [ -f "$apk" ]; then
			local name="PrivateCloudDisk_${VERSION}_android_$(basename "$apk")"
			copy_artifact "$apk" "android/$name"
			record_artifact "android" "$OUTPUT_DIR/android/$name" "android" "arm64-v8a"
		fi
	done

	log_success "Android 客户端构建完成"
	cd "$PROJECT_ROOT"
}

# ============================================================
# 4. 构建 iOS 原生客户端
# ============================================================

build_ios() {
	log_step "4. 构建 iOS 原生客户端 (.ipa)"

	if [ "$TARGET_PLATFORM" != "all" ] && [ "$TARGET_PLATFORM" != "ios" ]; then
		return
	fi

	if [ "$(uname -s)" != "Darwin" ] && [ "$DRY_RUN" = false ]; then
		log_warn "iOS 构建需要在 macOS 环境下执行，跳过"
		return
	fi

	local ios_dir="$PROJECT_ROOT/PrivateCloudDisk-ios"
	if [ ! -d "$ios_dir" ]; then
		log_warn "iOS 项目目录不存在: PrivateCloudDisk-ios，跳过"
		log_info "iOS 项目占位符已创建，请在 Xcode 中完成配置后重新构建"
		# 创建占位符
		cat > "$OUTPUT_DIR/ios/README.txt" <<-EOF
		iOS 构建占位符
		===============
		版本: $VERSION
		构建时间: $BUILD_TIME

		iOS 项目路径: PrivateCloudDisk-ios/
		构建步骤:
		  1. 在 Xcode 中打开 PrivateCloudDisk-ios/
		  2. 配置签名证书和 Provisioning Profile
		  3. Archive → Distribute App → 导出 .ipa
		  4. 将 .ipa 放入此目录
		EOF
		return
	fi

	cd "$ios_dir"

	log_build "构建 iOS (.ipa)"

	if [ "$DRY_RUN" = false ]; then
		# 构建 archive
		xcodebuild -workspace "PrivateCloudDisk.xcworkspace" \
			-scheme "PrivateCloudDisk" \
			-configuration Release \
			-archivePath "$OUTPUT_DIR/ios/PrivateCloudDisk.xcarchive" \
			archive 2>&1 || log_warn "iOS 构建失败，请检查 Xcode 配置"

		# 导出 ipa
		xcodebuild -exportArchive \
			-archivePath "$OUTPUT_DIR/ios/PrivateCloudDisk.xcarchive" \
			-exportPath "$OUTPUT_DIR/ios/" \
			-exportOptionsPlist "ExportOptions.plist" 2>&1 || log_warn "iOS 导出失败"
	fi

	# 收集产物
	if [ -d "$OUTPUT_DIR/ios" ]; then
		for ipa in "$OUTPUT_DIR/ios"/*.ipa; do
			[ -f "$ipa" ] || continue
			record_artifact "ios" "$ipa" "ios" "arm64"
		done
	fi

	log_success "iOS 客户端构建完成"
	cd "$PROJECT_ROOT"
}

# ============================================================
# 5. 构建 uni-app 跨端客户端
# ============================================================

build_uniapp() {
	log_step "5. 构建 uni-app 跨端客户端"

	if [ "$TARGET_PLATFORM" != "all" ]; then
		return
	fi

	local uniapp_dir="$PROJECT_ROOT/PrivateCloudDisk-uniapp"
	if [ ! -d "$uniapp_dir" ]; then
		log_warn "uni-app 项目目录不存在: PrivateCloudDisk-uniapp，跳过"
		cat > "$OUTPUT_DIR/uniapp/README.txt" <<-EOF
		uni-app 构建占位符
		==================
		版本: $VERSION
		构建时间: $BUILD_TIME

		uni-app 项目路径: PrivateCloudDisk-uniapp/
		构建步骤:
		  1. npm install
		  2. npm run build:h5        # H5 版本
		  3. npm run build:mp-weixin  # 微信小程序
		  4. 将 dist/ 内容放入此目录
		EOF
		return
	fi

	cd "$uniapp_dir"

	if [ "$DRY_RUN" = false ]; then
		npm ci --silent 2>/dev/null || npm install --silent 2>/dev/null
	fi

	# H5 构建
	log_build "构建 uni-app H5"
	if [ "$DRY_RUN" = false ]; then
		npm run build:h5 2>&1 || log_warn "H5 构建失败"
		if [ -d "dist/build/h5" ]; then
			cp -r dist/build/h5 "$OUTPUT_DIR/uniapp/h5"
			log_success "H5 → uniapp/h5/"
		fi
	fi

	# 微信小程序
	log_build "构建 uni-app 微信小程序"
	if [ "$DRY_RUN" = false ]; then
		npm run build:mp-weixin 2>&1 || log_warn "微信小程序构建失败"
		if [ -d "dist/build/mp-weixin" ]; then
			cp -r dist/build/mp-weixin "$OUTPUT_DIR/uniapp/mp-weixin"
			log_success "微信小程序 → uniapp/mp-weixin/"
		fi
	fi

	log_success "uni-app 客户端构建完成"
	cd "$PROJECT_ROOT"
}

# ============================================================
# 6. 构建 Admin Web 管理后台
# ============================================================

build_admin_web() {
	log_step "6. 构建 Admin Web 管理后台"

	local admin_dir="$PROJECT_ROOT/PrivateCloudDisk-admin-web"
	cd "$admin_dir"

	if [ "$DRY_RUN" = false ]; then
		npm ci --silent 2>/dev/null || npm install --silent 2>/dev/null
	fi

	log_build "构建 Admin Web (Vite)"
	if [ "$DRY_RUN" = false ]; then
		npm run build 2>&1 || log_warn "Admin Web 构建失败"
	fi

	# 收集产物
	if [ -d "dist" ]; then
		local archive_name="admin-web_${VERSION}.tar.gz"
		if [ "$DRY_RUN" = false ]; then
			tar -czf "$OUTPUT_DIR/admin-web/$archive_name" -C dist . 2>/dev/null
		fi
		copy_artifact "$OUTPUT_DIR/admin-web/$archive_name" "admin-web/$archive_name"
		record_artifact "admin-web" "$OUTPUT_DIR/admin-web/$archive_name" "web" "any"
		log_success "Admin Web → admin-web/$archive_name"
	fi

	log_success "Admin Web 构建完成"
	cd "$PROJECT_ROOT"
}

# ============================================================
# 生成校验和和清单
# ============================================================

generate_checksums() {
	log_step "生成校验和"

	if [ "$DRY_RUN" = true ]; then
		return
	fi

	ensure_dir "$CHECKSUM_DIR"

	# 为每个平台生成校验和
	for platform_dir in "$OUTPUT_DIR"/*/; do
		local platform=$(basename "$platform_dir")
		[ "$platform" = "checksums" ] && continue

		local checksum_file="$CHECKSUM_DIR/${platform}_sha256.txt"
		> "$checksum_file"

		find "$platform_dir" -type f ! -name "*.txt" ! -name "README.txt" -print0 | while IFS= read -r -d '' file; do
			local sha=$(compute_sha256 "$file")
			local rel_path="${file#$OUTPUT_DIR/}"
			echo "$sha  $rel_path" >> "$checksum_file"
		done

		if [ -s "$checksum_file" ]; then
			log_success "校验和: checksums/${platform}_sha256.txt"
		fi
	done

	# 总校验和
	local all_checksum="$CHECKSUM_DIR/all_sha256.txt"
	cat "$CHECKSUM_DIR"/*_sha256.txt > "$all_checksum" 2>/dev/null || true
	compute_sha256 "$all_checksum" > "$CHECKSUM_DIR/all.sha256"
	log_success "总校验和: checksums/all_sha256.txt"
}

# 生成 manifest.json
generate_manifest() {
	log_step "生成版本清单"

	if [ "$DRY_RUN" = true ]; then
		return
	fi

	cat > "$MANIFEST_FILE" <<-JSON
	{
	  "version": "$VERSION",
	  "commit": "$COMMIT",
	  "build_time": "$BUILD_TIME",
	  "artifacts": [
	$(IFS=,; echo "${MANIFEST_ENTRIES[*]}")
	  ]
	}
	JSON

	log_success "清单: manifest.json"
}

# ============================================================
# 上传到下载服务器
# ============================================================

upload_to_server() {
	if [ "$SKIP_UPLOAD" = true ] || [ "$DRY_RUN" = true ]; then
		log_info "跳过上传"
		return
	fi

	# 检查是否配置了上传目标
	local upload_method="${UPLOAD_METHOD:-rsync}"
	local upload_host="${UPLOAD_HOST:-}"
	local upload_path="${UPLOAD_PATH:-/var/www/downloads/binaries}"

	if [ -z "$upload_host" ]; then
		log_info "未配置 UPLOAD_HOST，跳过上传"
		log_info "设置方式: export UPLOAD_HOST=your-server.example.com"
		return
	fi

	log_step "上传到下载服务器"

	case "$upload_method" in
		rsync)
			log_info "通过 rsync 上传到 $upload_host:$upload_path"
			if [ "$DRY_RUN" = false ]; then
				rsync -avz --progress "$OUTPUT_DIR/" \
					"${upload_host}:${upload_path}/" 2>&1
			fi
			;;
		scp)
			log_info "通过 scp 上传到 $upload_host:$upload_path"
			if [ "$DRY_RUN" = false ]; then
				scp -r "$OUTPUT_DIR/"* "${upload_host}:${upload_path}/"
			fi
			;;
		*)
			log_warn "未知上传方式: $upload_method"
			;;
	esac

	log_success "上传完成"
}

# ============================================================
# 输出构建摘要
# ============================================================

print_summary() {
	log_step "构建摘要"

	echo ""
	echo -e "  ${BOLD}版本:${NC}      $VERSION"
	echo -e "  ${BOLD}Commit:${NC}    $COMMIT"
	echo -e "  ${BOLD}时间:${NC}      $BUILD_TIME"
	echo -e "  ${BOLD}输出目录:${NC}  $OUTPUT_DIR"
	echo ""

	# 统计产物
	if [ -d "$OUTPUT_DIR" ] && [ "$DRY_RUN" = false ]; then
		echo -e "  ${BOLD}产物列表:${NC}"
		find "$OUTPUT_DIR" -type f ! -path "*/checksums/*" ! -name "*.txt" ! -name "README.txt" \
			-exec ls -lh {} \; 2>/dev/null | while read -r line; do
			echo -e "    ${GREEN}▶${NC} $line"
		done
	fi

	echo ""
	echo -e "  ${BOLD}安装说明:${NC}"
	echo -e "    Go CLI:     curl -fsSL https://dl.example.com/cli/install.sh | bash"
	echo -e "    macOS:      打开 downloads/binaries/desktop/*.dmg"
	echo -e "    Windows:    运行 downloads/binaries/desktop/*.exe"
	echo -e "    Android:    安装 downloads/binaries/android/*.apk"
	echo -e "    iOS:        通过 TestFlight / MDM 分发 .ipa"
	echo -e "    Admin Web:  解压 admin-web/*.tar.gz 到 Nginx 目录"
	echo ""
	echo -e "  ${GREEN}${BOLD}构建完成!${NC} 产物位于: ${CYAN}$OUTPUT_DIR${NC}"
	echo ""
}

# ============================================================
# 主流程
# ============================================================

main() {
	echo ""
	echo -e "${CYAN}${BOLD}╔══════════════════════════════════════════════════════════════╗${NC}"
	echo -e "${CYAN}${BOLD}║     PrivateCloudDisk - 全客户端构建系统 v2.0               ║${NC}"
	echo -e "${CYAN}${BOLD}╚══════════════════════════════════════════════════════════════╝${NC}"
	echo ""

	# 环境检查
	check_environment

	# 准备输出目录
	prepare_output_dir

	# 构建各客户端
	if [ "$BUILD_CLI" = true ]; then
		build_cli
	fi

	if [ "$BUILD_ELECTRON" = true ]; then
		build_electron
	fi

	if [ "$BUILD_ANDROID" = true ]; then
		build_android
	fi

	if [ "$BUILD_IOS" = true ]; then
		build_ios
	fi

	if [ "$BUILD_UNIAPP" = true ]; then
		build_uniapp
	fi

	if [ "$BUILD_ADMIN_WEB" = true ]; then
		build_admin_web
	fi

	# 生成校验和
	generate_checksums

	# 生成清单
	generate_manifest

	# 上传
	upload_to_server

	# 摘要
	print_summary
}

main "$@"