# ============================================================
# PrivateCloudDisk - Makefile
# 简化常用命令，便于开发和维护
# ============================================================

.DEFAULT_GOAL := help

# 加载环境变量
-include .env

# 颜色
GREEN  := $(shell tput -Txterm setaf 2)
YELLOW := $(shell tput -Txterm setaf 3)
RED    := $(shell tput -Txterm setaf 1)
RESET  := $(shell tput -Txterm sgr0)

# 帮助信息
.PHONY: help
help:
	@echo 'PrivateCloudDisk Makefile'
	@echo ''
	@echo 'Usage:'
	@echo '  ${YELLOW}make${RESET} ${GREEN}<target>${RESET}'
	@echo ''
	@echo 'Targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  ${YELLOW}%-15${RESET} %s\n", $$1, $$2}' $(MAKEFILE_LIST)

# --------------------------
# 开发
# --------------------------

.PHONY: up
up: ## 启动所有服务 (后台)
	docker compose up -d

.PHONY: up-dev
up-dev: ## 启动开发环境
	docker compose -f deploy/dev/docker-compose.yml up -d

.PHONY: logs
logs: ## 查看所有服务日志
	docker compose logs -f

.PHONY: ps
ps: ## 查看服务状态
	docker compose ps

.PHONY: down
down: ## 停止所有服务 (保留数据卷)
	docker compose down

.PHONY: down-v
down-v: ## 停止并删除所有数据卷 (⚠️ 清空数据)
	docker compose down -v

# --------------------------
# 构建
# --------------------------

.PHONY: build
build: ## 构建所有镜像
	docker compose build --parallel

.PHONY: pull
pull: ## 拉取最新镜像
	docker compose pull

.PHONY: prune
prune: ## 清理未使用的镜像和容器
	docker system prune -f

# --------------------------
# 部署
# --------------------------

.PHONY: deploy
deploy: ## 一键部署 (拉取代码 + 构建 + 启动)
	@bash scripts/deploy.sh

.PHONY: backup
backup: ## 备份数据库和文件
	@bash scripts/backup.sh

.PHONY: rollback
rollback: ## 回滚到指定备份
	@bash scripts/rollback.sh

# --------------------------
# 运维
# --------------------------

.PHONY: health
health: ## 检查所有服务健康
	@docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Health}}"

.PHONY: stats
stats: ## 显示资源使用
	docker stats --no-stream

.PHONY: df
df: ## 显示磁盘使用
	docker system df

# --------------------------
# 证书
# --------------------------

.PHONY: cert-issue
cert-issue: ## 申请新证书 (需要先停止 frontend)
	@docker compose stop frontend
	@docker compose run --rm certbot certonly --standalone \
		-d $(DOMAIN) \
		-d www.$(DOMAIN) \
		--email $(ADMIN_EMAIL) \
		--agree-tos \
		--non-interactive
	@docker compose up -d

.PHONY: cert-renew
cert-renew: ## 续期证书
	@docker compose run --rm certbot renew

# --------------------------
# 数据库
# --------------------------

.PHONY: db-backup
db-backup: ## 备份数据库到本地
	@docker compose exec -T mysql mysqldump \
		-u root -p$(MYSQL_ROOT_PASSWORD) \
		--single-transaction --routines --triggers \
		private_cloud_disk > backup/$(shell date +%Y%m%d_%H%M%S).sql
	@gzip backup/*.sql
	@echo "Backup completed"

.PHONY: db-restore
db-restore: ## 恢复数据库 (BACKUP=path/to/backup.sql.gz)
	@if [ -z "$(BACKUP)" ]; then \
		echo "Usage: make db-restore BACKUP=path/to/backup.sql[.gz]"; \
		exit 1; \
	fi
	@if [[ "$(BACKUP)" == *.gz ]]; then \
		gunzip -c $(BACKUP) | docker compose exec -T mysql mysql -u root -p$(MYSQL_ROOT_PASSWORD) private_cloud_disk; \
	else \
		docker compose exec -T mysql mysql -u root -p$(MYSQL_ROOT_PASSWORD) private_cloud_disk < $(BACKUP); \
	fi
	@echo "Restore completed"

# ============================================================
# 客户端构建 (全平台)
# ============================================================

# 版本号（可从环境变量或 git 获取）
VERSION ?= $(shell git describe --tags --always --dirty 2>/dev/null || echo "0.0.0-dev")
VERSION := $(subst v,,$(VERSION))
BUILD_TIME := $(shell date -u '+%Y-%m-%dT%H:%M:%SZ')

.PHONY: clients-all
clients-all: ## 构建所有客户端（完整矩阵）
	@bash scripts/build-all-clients.sh --version $(VERSION)

.PHONY: clients-dry
clients-dry: ## 模拟构建（预览构建计划）
	@bash scripts/build-all-clients.sh --dry-run --version $(VERSION)

.PHONY: cli
cli: ## 仅构建 Go CLI 客户端
	@bash scripts/build-all-clients.sh --cli-only --version $(VERSION)

.PHONY: cli-install
cli-install: cli ## 构建并安装 CLI 到本地
	@echo "安装 CLI 到 /usr/local/bin/pcd..."
	@cp downloads/binaries/cli/pcd_$(VERSION)_$(shell uname -s | tr '[:upper:]' '[:lower:]')_$(shell uname -m)/pcd /usr/local/bin/pcd 2>/dev/null || \
		cp downloads/binaries/cli/pcd_$(VERSION)_$(shell uname -s | tr '[:upper:]' '[:lower:]')_$(shell uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')/pcd /usr/local/bin/pcd
	@chmod +x /usr/local/bin/pcd
	@echo "CLI 已安装! pcd --version"

.PHONY: desktop
desktop: ## 仅构建 Electron 桌面客户端
	@bash scripts/build-all-clients.sh --electron-only --version $(VERSION)

.PHONY: desktop-mac
desktop-mac: ## 构建 macOS 桌面客户端
	@bash scripts/build-all-clients.sh --electron-only --platform macos --version $(VERSION)

.PHONY: desktop-win
desktop-win: ## 构建 Windows 桌面客户端
	@bash scripts/build-all-clients.sh --electron-only --platform windows --version $(VERSION)

.PHONY: desktop-linux
desktop-linux: ## 构建 Linux 桌面客户端
	@bash scripts/build-all-clients.sh --electron-only --platform linux --version $(VERSION)

.PHONY: android
android: ## 仅构建 Android 客户端
	@bash scripts/build-all-clients.sh --android-only --version $(VERSION)

.PHONY: admin-web
admin-web: ## 仅构建 Admin Web 管理后台
	@bash scripts/build-all-clients.sh --admin-only --version $(VERSION)

.PHONY: clients-upload
clients-upload: clients-all ## 构建并上传到下载服务器
	@echo "客户端已构建并上传"

.PHONY: clients-clean
clients-clean: ## 清理客户端构建产物
	@echo "清理客户端构建产物..."
	@rm -rf downloads/binaries/*
	@echo "已清理 downloads/binaries/"

# --------------------------
# 下载服务器部署
# --------------------------

.PHONY: serve-downloads
serve-downloads: ## 启动本地下载服务器（开发用）
	@echo "启动下载服务器 http://localhost:9090"
	@echo "访问: http://localhost:9090/binaries/"
	@cd downloads && python3 -m http.server 9090 || \
		cd downloads && npx http-server -p 9090 -c-1

.PHONY: deploy-downloads
deploy-downloads: ## 部署下载页面到 Nginx 静态服务器
	@echo "部署下载页面到 downloads/..."
	@bash scripts/deploy-downloads.sh
