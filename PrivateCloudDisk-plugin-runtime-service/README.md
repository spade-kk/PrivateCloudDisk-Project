# PrivateCloudDisk plugin-runtime-service

云插件运行时服务。负责插件 manifest/包的运行前校验、Python/JS 入口验证、沙箱进程管理、
资源边界和 Broker 通信；不负责插件市场和平台业务数据。

## 技术栈

- Go 1.24（标准 `testing` 表驱动 + `-race` 支持）
- Python AST 静态校验（`validator/validate_python.py`）
- Node.js AST 静态校验（`validator/validate_js.mjs` + vendored acorn，见 `validator/js/LICENSE`）
- Docker 沙箱（`sandbox/python/`），生产按部署启用 gVisor runsc、seccomp、AppArmor
- HTTP API（`/internal/v1/validation/*`、执行链与 Broker 接口）

## 职责边界

- 校验插件入口、manifest 与运行参数
- 双语言 AST 发布门禁：只解析不执行，拒绝危险调用/模块/原型链，错误信息脱敏
- 在受控运行时启动插件进程（rootless Docker + gVisor、只读 RootFS、无网络、cap-drop=ALL）
- 容器内运行时受限 Python 层（`/opt/pcd-sdk/bin/runner.py` + `restricted.py`，36.x）：
  白名单 import 钩子、危险内置删除、双下划线逃逸链改写、PEP 578 审计钩子，所有云插件
  都必须经该受控启动器执行（默认开启，生产 `config.Load()` 强制）
- 所有内容读写只经 Broker 数据面内部接口，Runtime 不直连业务数据库
- 插件能力调用只经每实例 Unix Domain Socket：`internal/uds` 创建不可预测的专属 Socket，容器只看到
  `/runtime/runtime.sock`；SDK 以 protobuf 帧和 `runner.py` argv 传入的单实例 Token 通信，Runtime 注入
  用户/空间/插件上下文并将声明权限与独立的安装授权快照交由 Capability Hub 最终判定
- 能力调用审计在 Runtime Agent 的 RPC 管线生成；SDK 不写 `capability-audit.jsonl`，Runner 不扫描插件工作目录
- 返回执行状态和错误信息，统一脱敏（`internal/sanitize`），避免暴露内部路径与凭证

## 测试

    make test-unit                 # Go 单测 + AST 校验器 + pycloud SDK 测试（无需 Docker）
    make test-integration          # Docker 集成测试（需本机 Docker/daemon）
    make test-all                  # 单元 + 集成全量
    make baseline                  # 重新生成 realworld golden 基线
    make test-packages             # 重新打包 realworld 插件
    sh scripts/test.sh --integration   # 等价底层命令（scripts/test.sh 全量）
    sh scripts/check_sandbox_profiles.sh   # 校验 sandbox 镜像策略产物与 deploy/ 一致

- 单元测试覆盖（`go test -short ./...`）：`containerArgs` 安全参数、`extractPackage` 恶意包、
  镜像摘要门禁、Execute/PostAvailable/Capability 分支、`sanitize`/`audit`/`broker`/`pkgclient`/
  `config`/`model`；`realworld_package_test.go` 校验 `.pcdpkg` 插件包回环、manifest.yaml 清单与基线模式；
  `sandbox/python/tests/test_restricted.py` 覆盖运行时受限 Python 层（36.26-36.31）。
- Docker 集成测试（`go test -tags=integration`）：真实容器验证成功/失败/超时/日志截断/加载隔离/
  无网络/非 root/资源限制/容器清理/重试/摘要门禁，以及真实业务插件
  `testdata/plugins/realworld/`（文本统计/JSON 清洗/CSV/Excel/能力导出/超时/资源耗尽/恶意/逃逸/
  无效输出/多入口/并发）输出与 `testdata/expected/*.golden` 逐字节一致；能力调用走沙箱内
  `/runtime/runtime.sock` 的专属 UDS 通道，宿主侧 `capabilityRelay` 作为 `uds.Invoker` 应答。
  本机无 Docker 或 `testing.Short()` 时自动 Skip；本地 Docker Desktop 未注册 runsc 时使用
  development 配置（`PLUGIN_SANDBOX_RUNTIME=runc`），生产 runsc/seccomp/AppArmor 门禁仍由
  `config.Load()` 强制；真实 Docker 中恶意样本（`import os`/逃逸链/`eval`）被受限层拦截且
  错误脱敏（`TestIntegrationRunContainerRestrictedPythonIntercepts`）。
- 测试夹具：`testdata/plugins/`（容器边界样本）+ `testdata/plugins/realworld/`（真实场景，
  每个含 manifest.yaml/src/README/输入/基线，打包为 `testdata/packages/*.pcdpkg`）；输入 `testdata/input/`。
- 测试指南与覆盖矩阵：`docs/PLUGIN_RUNTIME_TESTING.md`、`docs/PLUGIN_RUNTIME_COVERAGE_MATRIX.md`。
- 手动调试测试单元（自定义插件目录 / .pcdpkg / 单个 .py → 真实容器）：
  `sh scripts/run_manual_plugin.sh --help` 或 `make debug-plugin ARGS="..."`。
  经 `pkg.ParseManifestBytes` + `pkg.Parse` 校验后由 `runner.go` 直接执行，插件输出/日志/
  异常/能力调用全打印，不落库不落盘；`restricted` 子命令可在宿主机直接探针受限 Python 层
  （`sys.addaudithook`/`_AttrGuard`/`exec_plugin`，无需 Docker）。
  容器日志（`print`/`pycloud.log`/`runner.py`、`restricted.py` 输出）由
  `Execute / ExecutePostAvailable / ExecuteCapability` 返回结果的 `logs` 字段携带，
  本单元用 `====> [容器日志]` 标记单独打印；`output` 字段携带入口函数返回值，
  便于直接观察容器内行为与插件返回值。
  详见 `docs/PLUGIN_RUNTIME_MANUAL_HARNESS.md`。

## 运行

    PCD_INTERNAL_SERVICE_TOKEN=test go run ./cmd/runtime

生产部署参考 `deploy/`（systemd、environment 示例、seccomp/AppArmor、gVisor 安装校验）。
协议、信任边界、迁移与测试门禁见 [`../docs/PLUGIN_RUNTIME_UNIX_SOCKET_ARCHITECTURE.md`](../docs/PLUGIN_RUNTIME_UNIX_SOCKET_ARCHITECTURE.md)。
