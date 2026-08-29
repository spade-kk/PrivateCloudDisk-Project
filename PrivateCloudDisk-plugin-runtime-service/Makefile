# Plugin Runtime 测试入口（需求九 9.14 / 四 4.29 / 三 3.7）
#   make test-unit        -> 单元测试 + AST 校验器 + pycloud SDK 测试
#   make test-integration -> 仅 Docker 集成（需本机 Docker/daemon）
#   make test-all         -> 单元 + 集成全量
#   make test-packages    -> 重新打包 realworld 插件（发布格式 .pcdpkg）
#   make baseline         -> 重新生成 realworld golden 基线
#   make coverage         -> 单元测试覆盖率报告
.PHONY: test-unit test-integration test-all test-packages baseline coverage debug-plugin

GOCACHE ?= /tmp/pcd-gocache
GOTMPDIR ?= /tmp/pcd-gotmp
PYTHON ?= python3
NODE ?= node
SANDBOX_IMAGE ?= pcd/plugin-sandbox-python:0.1.2
SANDBOX_RUNTIME ?= runc

test-unit:
	@echo "==> 单元测试（-short）"
	GOCACHE=$(GOCACHE) GOTMPDIR=$(GOTMPDIR) go test -short -count=1 ./...
	@echo "==> Python AST 校验器规则（需求四）"
	PYTHONPATH=. $(PYTHON) validator/test_validate_python.py
	@echo "==> JS AST 校验器规则（需求五）"
	$(NODE) validator/test_validate_js.mjs
	@echo "==> pycloud SDK 单测（需求六）"
	PYTHONPATH=sandbox/python $(PYTHON) -m unittest discover -s sandbox/python/tests -p 'test_*.py'
	@echo "==> 运行时受限 Python 层单测（36.26-36.31）"
	PYTHONPATH=sandbox/python $(PYTHON) -m unittest discover -s sandbox/python/tests -p 'test_restricted.py'
	@echo "==> 沙箱加固策略产物与 deploy/ 一致"
	sh scripts/check_sandbox_profiles.sh

test-integration:
	@echo "==> Docker 集成测试（需 Docker；生产 runsc，本地回退 runc）"
	GOCACHE=$(GOCACHE) GOTMPDIR=$(GOTMPDIR) \
	  PLUGIN_SANDBOX_IMAGE=$(SANDBOX_IMAGE) PLUGIN_SANDBOX_RUNTIME=$(SANDBOX_RUNTIME) \
	  go test -short=false -tags=integration -count=1 -timeout 30m ./internal/sandbox/

test-all: test-unit test-integration

test-packages:
	sh scripts/package_test_plugins.sh

baseline:
	$(PYTHON) scripts/gen_baselines.py

coverage:
	@echo "==> 单元测试覆盖率"
	GOCACHE=$(GOCACHE) GOTMPDIR=$(GOTMPDIR) go test -short -count=1 -coverprofile=/tmp/pcd-cover.out ./...
	GOCACHE=$(GOCACHE) GOTMPDIR=$(GOTMPDIR) go tool cover -func=/tmp/pcd-cover.out | tail -1

# 手动调试测试单元（自定义插件目录/pkg/py 的一次性沙箱执行，详见
# docs/PLUGIN_RUNTIME_MANUAL_HARNESS.md）：
#   make debug-plugin ARGS="dir <插件目录> [--capability x --input '{"k":1}']"
#   make debug-plugin ARGS="restricted"
debug-plugin:
	@sh scripts/run_manual_plugin.sh $(ARGS)
