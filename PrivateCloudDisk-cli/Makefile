# PrivateCloudDisk CLI Makefile
# ================================

APP_NAME := pcd
VERSION := $(shell git describe --tags --always --dirty 2>/dev/null || echo "1.0.0")
COMMIT := $(shell git rev-parse --short HEAD 2>/dev/null || echo "unknown")
BUILD_TIME := $(shell date -u '+%Y-%m-%d_%H:%M:%S')
LDFLAGS := -X main.Version=$(VERSION) -X main.Commit=$(COMMIT) -X main.BuildTime=$(BUILD_TIME) -s -w

# Go 参数
GOCMD := go
GOBUILD := $(GOCMD) build
GOCLEAN := $(GOCMD) clean
GOTEST := $(GOCMD) test
GOGET := $(GOCMD) get
GOMOD := $(GOCMD) mod
GOFMT := gofmt

# 输出目录
DIST_DIR := dist
BIN_DIR := bin

.PHONY: all build clean test run fmt vet lint deps dist install

all: clean deps fmt vet test build

# 安装依赖
deps:
	$(GOMOD) tidy
	$(GOMOD) download

# 格式化代码
fmt:
	$(GOFMT) -s -w .

# 代码检查
vet:
	$(GOCMD) vet ./...

# 静态分析
lint:
	@which golangci-lint > /dev/null || (echo "请安装 golangci-lint: go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest" && exit 1)
	golangci-lint run ./...

# 运行测试
test:
	$(GOTEST) -v -race -count=1 ./...

# 测试覆盖率
test-coverage:
	$(GOTEST) -v -race -coverprofile=coverage.out -covermode=atomic ./...
	$(GOCMD) tool cover -html=coverage.out -o coverage.html

# 本地构建
build:
	$(GOBUILD) -ldflags "$(LDFLAGS)" -o $(BIN_DIR)/$(APP_NAME) .

# 快速运行
run: build
	./$(BIN_DIR)/$(APP_NAME)

# 安装到系统
install:
	$(GOBUILD) -ldflags "$(LDFLAGS)" -o $(GOPATH)/bin/$(APP_NAME) .

# 清理
clean:
	$(GOCLEAN)
	rm -rf $(BIN_DIR) $(DIST_DIR) coverage.out coverage.html

# ============================================================
# 多平台交叉编译
# ============================================================

# Linux amd64
build-linux-amd64:
	GOOS=linux GOARCH=amd64 $(GOBUILD) -ldflags "$(LDFLAGS)" -o $(DIST_DIR)/$(APP_NAME)_$(VERSION)_linux_amd64/$(APP_NAME) .

# Linux arm64
build-linux-arm64:
	GOOS=linux GOARCH=arm64 $(GOBUILD) -ldflags "$(LDFLAGS)" -o $(DIST_DIR)/$(APP_NAME)_$(VERSION)_linux_arm64/$(APP_NAME) .

# macOS amd64 (Intel)
build-darwin-amd64:
	GOOS=darwin GOARCH=amd64 $(GOBUILD) -ldflags "$(LDFLAGS)" -o $(DIST_DIR)/$(APP_NAME)_$(VERSION)_darwin_amd64/$(APP_NAME) .

# macOS arm64 (Apple Silicon)
build-darwin-arm64:
	GOOS=darwin GOARCH=arm64 $(GOBUILD) -ldflags "$(LDFLAGS)" -o $(DIST_DIR)/$(APP_NAME)_$(VERSION)_darwin_arm64/$(APP_NAME) .

# Windows amd64
build-windows-amd64:
	GOOS=windows GOARCH=amd64 $(GOBUILD) -ldflags "$(LDFLAGS)" -o $(DIST_DIR)/$(APP_NAME)_$(VERSION)_windows_amd64/$(APP_NAME).exe .

# 构建所有平台
dist: build-linux-amd64 build-linux-arm64 build-darwin-amd64 build-darwin-arm64 build-windows-amd64
	@echo "=== 开始打包各平台二进制文件 ==="
	@for dir in $(DIST_DIR)/*; do \
		if [ -d "$$dir" ]; then \
			echo "打包: $$dir"; \
			tar -czf "$$dir.tar.gz" -C $(DIST_DIR) "$$(basename $$dir)" || true; \
			(cd $(DIST_DIR) && zip -r "$$(basename $$dir).zip" "$$(basename $$dir)") || true; \
		fi; \
	done
	@echo ""
	@echo "=== 构建完成 ==="
	@echo "版本: $(VERSION)"
	@echo "文件列表:"
	@ls -lh $(DIST_DIR)/*.tar.gz $(DIST_DIR)/*.zip 2>/dev/null || true
	@echo ""
	@echo "各平台 SHA256:"
	@for dir in $(DIST_DIR)/$(APP_NAME)_*; do \
		if [ -d "$$dir" ]; then \
			shasum -a 256 "$$dir/$(APP_NAME)"* 2>/dev/null || true; \
		fi; \
	done

# 构建 checksums
checksums:
	cd $(DIST_DIR) && shasum -a 256 *.tar.gz *.zip > checksums.txt

# 安装开发依赖
dev-deps:
	$(GOGET) github.com/golangci/golangci-lint/cmd/golangci-lint@latest
	$(GOGET) github.com/goreleaser/goreleaser@latest

# 帮助
help:
	@echo "PrivateCloudDisk CLI 构建工具"
	@echo ""
	@echo "Usage:"
	@echo "  make build             构建当前平台"
	@echo "  make dist              构建所有平台"
	@echo "  make test              运行测试"
	@echo "  make clean             清理构建产物"
	@echo "  make install           安装到 GOPATH/bin"
	@echo "  make run               构建并运行"
	@echo "  make lint              运行代码检查"
	@echo "  make deps              安装依赖"
	@echo "  make help              显示帮助"