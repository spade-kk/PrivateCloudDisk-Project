#!/usr/bin/env bash
# ============================================================
# PrivateCloudDisk - 下载服务器部署脚本
# ============================================================
# 功能: 将 downloads/binaries/ 部署到静态资源服务器
#
# 支持的部署方式:
#   1. rsync  (推荐) — 增量同步到远程服务器
#   2. scp     — 全量复制到远程服务器
#   3. rclone  — 同步到对象存储 (S3/OSS/COS)
#   4. local   — 本地 Nginx 目录
#
# 环境变量:
#   DEPLOY_METHOD        部署方式: rsync|scp|rclone|local (默认: rsync)
#   DEPLOY_HOST          远程服务器地址
#   DEPLOY_USER          SSH 用户名
#   DEPLOY_PATH          远程目标路径 (默认: /var/www/downloads)
#   DEPLOY_SSH_KEY       SSH 私钥路径
#   DEPLOY_RCLONE_REMOTE rclone 远程配置名 (如: s3:my-bucket)
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE_DIR="${PROJECT_ROOT}/downloads"
BINARIES_DIR="${SOURCE_DIR}/binaries"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_success() { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }

# 配置
DEPLOY_METHOD="${DEPLOY_METHOD:-rsync}"
DEPLOY_HOST="${DEPLOY_HOST:-}"
DEPLOY_USER="${DEPLOY_USER:-root}"
DEPLOY_PATH="${DEPLOY_PATH:-/var/www/downloads}"
DEPLOY_SSH_KEY="${DEPLOY_SSH_KEY:-}"
DEPLOY_RCLONE_REMOTE="${DEPLOY_RCLONE_REMOTE:-}"

# SSH 选项
SSH_OPTS="-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"
if [ -n "$DEPLOY_SSH_KEY" ] && [ -f "$DEPLOY_SSH_KEY" ]; then
	SSH_OPTS="$SSH_OPTS -i $DEPLOY_SSH_KEY"
fi

# ============================================================
# 检查产物是否存在
# ============================================================

check_artifacts() {
	if [ ! -d "$BINARIES_DIR" ]; then
		log_error "构建产物目录不存在: $BINARIES_DIR"
		log_info "请先运行: make clients-all"
		exit 1
	fi

	local count=$(find "$BINARIES_DIR" -type f ! -name "README.txt" ! -path "*/checksums/*" 2>/dev/null | wc -l)
	if [ "$count" -eq 0 ]; then
		log_error "没有构建产物"
		log_info "请先运行: make clients-all"
		exit 1
	fi

	log_info "发现 $count 个构建产物"
}

# ============================================================
# 生成下载页面
# ============================================================

generate_download_page() {
	log_info "生成下载页面..."

	# 从 manifest.json 读取版本
	local version="unknown"
	if [ -f "$BINARIES_DIR/manifest.json" ]; then
		version=$(python3 -c "import json; print(json.load(open('$BINARIES_DIR/manifest.json'))['version'])" 2>/dev/null || echo "unknown")
	fi

	# 生成各平台表格行
	generate_table_row() {
		local platform="$1"
		local label="$2"
		local icon="$3"
		local pattern="$4"
		local dir="$5"

		local files=$(find "$BINARIES_DIR/$dir" -maxdepth 1 -type f ! -name "*.txt" ! -name "README*" 2>/dev/null | sort)
		if [ -z "$files" ]; then
			echo "          <tr><td>${icon} ${label}</td><td colspan=\"3\">待构建</td></tr>"
			return
		fi

		for f in $files; do
			local name=$(basename "$f")
			local size=$(ls -lh "$f" | awk '{print $5}')
			local sha=""
			# 从校验和文件读取
			local checksum_file="$BINARIES_DIR/checksums/${dir}_sha256.txt"
			if [ -f "$checksum_file" ]; then
				sha=$(grep "$name" "$checksum_file" 2>/dev/null | awk '{print $1}' || echo "-")
			fi
			[ -z "$sha" ] && sha="-"
			echo "          <tr><td>${icon} ${label}</td><td><a href=\"binaries/${dir}/${name}\">${name}</a></td><td>${size}</td><td><code>${sha:0:16}...</code></td></tr>"
		done
	}

	cat > "$SOURCE_DIR/index.html" <<-HTML
	<!DOCTYPE html>
	<html lang="zh-CN">
	<head>
	  <meta charset="UTF-8">
	  <meta name="viewport" content="width=device-width, initial-scale=1.0">
	  <title>PrivateCloudDisk 下载中心</title>
	  <style>
	    * { margin: 0; padding: 0; box-sizing: border-box; }
	    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f1419; color: #e7e9ea; min-height: 100vh; }
	    .container { max-width: 960px; margin: 0 auto; padding: 40px 20px; }
	    h1 { font-size: 2em; margin-bottom: 8px; background: linear-gradient(135deg, #60a5fa, #a78bfa); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
	    .version { color: #6b7280; margin-bottom: 32px; }
	    h2 { font-size: 1.3em; margin: 32px 0 16px; padding-bottom: 8px; border-bottom: 1px solid #2f3336; }
	    table { width: 100%; border-collapse: collapse; }
	    th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid #2f3336; }
	    th { color: #6b7280; font-weight: 500; font-size: 0.85em; text-transform: uppercase; }
	    td { font-size: 0.95em; }
	    a { color: #60a5fa; text-decoration: none; }
	    a:hover { text-decoration: underline; }
	    code { background: #1d2329; padding: 2px 6px; border-radius: 4px; font-size: 0.85em; }
	    .install-cmd { background: #1d2329; border: 1px solid #2f3336; border-radius: 8px; padding: 16px; margin: 16px 0; overflow-x: auto; }
	    .install-cmd code { background: none; padding: 0; }
	    .footer { margin-top: 48px; padding-top: 16px; border-top: 1px solid #2f3336; color: #6b7280; font-size: 0.85em; text-align: center; }
	  </style>
	</head>
	<body>
	  <div class="container">
	    <h1>PrivateCloudDisk</h1>
	    <p class="version">版本: ${version} | 构建时间: $(date -u '+%Y-%m-%d %H:%M UTC')</p>

	    <h2>🚀 快速安装</h2>
	    <div class="install-cmd">
	      <code># Go CLI 一键安装 (Linux / macOS)</code><br>
	      <code>curl -fsSL https://dl.example.com/cli/install.sh | bash</code>
	    </div>

	    <h2>📦 桌面客户端</h2>
	    <table>
	      <thead><tr><th>平台</th><th>文件名</th><th>大小</th><th>SHA256</th></tr></thead>
	      <tbody>
	$(generate_table_row "macos" "macOS" "🍎" "*dmg*" "desktop")
	$(generate_table_row "windows" "Windows" "🪟" "*exe*" "desktop")
	$(generate_table_row "linux" "Linux" "🐧" "*AppImage*" "desktop")
	      </tbody>
	    </table>

	    <h2>📱 移动客户端</h2>
	    <table>
	      <thead><tr><th>平台</th><th>文件名</th><th>大小</th><th>SHA256</th></tr></thead>
	      <tbody>
	$(generate_table_row "android" "Android" "🤖" "*apk*" "android")
	$(generate_table_row "ios" "iOS" "📱" "*ipa*" "ios")
	      </tbody>
	    </table>

	    <h2>⌨️ 命令行工具 (CLI)</h2>
	    <table>
	      <thead><tr><th>平台</th><th>文件名</th><th>大小</th><th>SHA256</th></tr></thead>
	      <tbody>
	$(generate_table_row "linux" "Linux" "🐧" "*linux*" "cli")
	$(generate_table_row "macos" "macOS" "🍎" "*darwin*" "cli")
	$(generate_table_row "windows" "Windows" "🪟" "*windows*" "cli")
	      </tbody>
	    </table>

	    <h2>🌐 Admin Web 管理后台</h2>
	    <table>
	      <thead><tr><th>平台</th><th>文件名</th><th>大小</th><th>SHA256</th></tr></thead>
	      <tbody>
	$(generate_table_row "web" "Web" "🌐" "*tar.gz*" "admin-web")
	      </tbody>
	    </table>

	    <div class="footer">
	      <p>PrivateCloudDisk &copy; 2024-2025 | 校验和文件: <a href="binaries/checksums/all_sha256.txt">all_sha256.txt</a></p>
	    </div>
	  </div>
	</body>
	</html>
	HTML

	log_success "下载页面: downloads/index.html"
}

# ============================================================
# rsync 部署
# ============================================================

deploy_rsync() {
	if [ -z "$DEPLOY_HOST" ]; then
		log_error "未设置 DEPLOY_HOST"
		return 1
	fi

	log_info "rsync 部署到 ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}"

	# 确保远程目录存在
	ssh $SSH_OPTS "${DEPLOY_USER}@${DEPLOY_HOST}" "mkdir -p ${DEPLOY_PATH}" 2>/dev/null || true

	# 增量同步
	rsync -avz --delete --progress \
		-e "ssh $SSH_OPTS" \
		"$SOURCE_DIR/" \
		"${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/"

	log_success "rsync 部署完成"
	log_info "访问: https://${DEPLOY_HOST}/downloads/"
}

# ============================================================
# scp 部署
# ============================================================

deploy_scp() {
	if [ -z "$DEPLOY_HOST" ]; then
		log_error "未设置 DEPLOY_HOST"
		return 1
	fi

	log_info "scp 部署到 ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}"

	# 确保远程目录存在
	ssh $SSH_OPTS "${DEPLOY_USER}@${DEPLOY_HOST}" "mkdir -p ${DEPLOY_PATH}" 2>/dev/null || true

	# 全量复制
	scp -r $SSH_OPTS "$SOURCE_DIR/"* "${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/"

	log_success "scp 部署完成"
}

# ============================================================
# rclone 部署 (对象存储)
# ============================================================

deploy_rclone() {
	if [ -z "$DEPLOY_RCLONE_REMOTE" ]; then
		log_error "未设置 DEPLOY_RCLONE_REMOTE"
		return 1
	fi

	log_info "rclone 部署到 ${DEPLOY_RCLONE_REMOTE}/downloads/"

	if ! command -v rclone &>/dev/null; then
		log_error "rclone 未安装，请先安装: https://rclone.org/install/"
		return 1
	fi

	# 同步
	rclone sync "$SOURCE_DIR" "${DEPLOY_RCLONE_REMOTE}/downloads" \
		--progress \
		--checksum \
		--transfers 8

	log_success "rclone 部署完成"
}

# ============================================================
# 本地部署 (Nginx)
# ============================================================

deploy_local() {
	local nginx_root="${NGINX_ROOT:-/usr/share/nginx/html}"
	local target="${nginx_root}/downloads"

	log_info "本地部署到 ${target}"

	if [ ! -d "$nginx_root" ]; then
		log_warn "Nginx 根目录不存在: $nginx_root"
		# 尝试常见路径
		for path in /usr/share/nginx/html /var/www/html /opt/homebrew/var/www; do
			if [ -d "$path" ]; then
				nginx_root="$path"
				target="${nginx_root}/downloads"
				break
			fi
		done
	fi

	ensure_dir "$target"
	cp -r "$SOURCE_DIR/"* "$target/"
	log_success "本地部署完成: ${target}"
}

# ============================================================
# 主流程
# ============================================================

main() {
	echo ""
	echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
	echo -e "${BLUE}║     PrivateCloudDisk - 下载服务器部署                       ║${NC}"
	echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
	echo ""

	check_artifacts
	generate_download_page

	case "$DEPLOY_METHOD" in
		rsync)
			deploy_rsync
			;;
		scp)
			deploy_scp
			;;
		rclone)
			deploy_rclone
			;;
		local)
			deploy_local
			;;
		*)
			log_error "未知部署方式: $DEPLOY_METHOD"
			log_info "支持: rsync, scp, rclone, local"
			exit 1
			;;
	esac

	echo ""
	log_success "部署完成!"
}

main "$@"