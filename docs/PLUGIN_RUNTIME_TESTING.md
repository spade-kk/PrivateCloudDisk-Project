# plugin-runtime-service 测试指南

面向「真实场景 Python 云插件测试与 Docker 沙盒集成验证清单」的测试运行、编写与维护说明。

## 1. 测试分层与运行

| 层 | 命令 | 依赖 | 内容 |
| --- | --- | --- | --- |
| 单元 | `make test-unit` | 无 | Go 单测、双语言 AST 校验器、pycloud SDK 单测、策略产物一致性 |
| 集成 | `make test-integration` | Docker | 真实沙箱执行既有隔离/超时/脱敏 + realworld 业务插件 |
| 全量 | `make test-all` | Docker | 单元 + 集成 |
| 打包 | `make test-packages` | 无 | 重新生成 `testdata/packages/*.pcdpkg` |
| 基线 | `make baseline` | 无 | 重新生成 `testdata/expected/*.golden` |
| 覆盖率 | `make coverage` | 无 | `go test -cover` 报告 |
| UDS 性能基线 | `go test -run '^$' -bench 'BenchmarkSocketParallelRoundTrip|BenchmarkSessionLifecycleOneThousand' -benchtime=1s ./internal/uds` | Linux CI 建议 | 并发 protobuf UDS request/response，以及 1,000 实例 Socket 生命周期的基线 |
| 手动调试 | `sh scripts/run_manual_plugin.sh --help` | Docker | 自定义插件目录/pkg/py 的一次性沙箱执行与观测（详见 §2.2） |

等价底层命令：

    go test -short -count=1 ./...
    go test -tags=integration -count=1 -timeout 30m ./internal/sandbox/
    PLUGIN_SANDBOX_IMAGE=pcd/plugin-sandbox-python:0.1.2 PLUGIN_SANDBOX_RUNTIME=runc \
      go test -tags=integration ./internal/sandbox/ -run 'TestIntegration|TestRealWorld'

- 无 Docker 或 `testing.Short()` 时集成测试自动 `Skip`。
- 本地 Docker Desktop 未注册 runsc，须用 development 配置 `PLUGIN_SANDBOX_RUNTIME=runc`；
  生产 runsc/seccomp/AppArmor/无网络门禁由 `config.Load()` 强制，测试不绕过。
- **Unix Socket 挂载前置条件**：Docker Desktop 将宿主机路径代理到 Linux VM，不能可靠地
  bind mount 宿主 Unix Socket；带 `integration` 标签的测试会明确 `Skip`，不会降级为文件
  通信。真实 Socket 挂载、容器间隔离和挂载权限须在 Linux Docker Engine CI 执行。
- CI：`.github/workflows/plugin-platform-security.yml` 的 `go-runtime` 与
  `plugin-runtime-integration` job；包与基线漂移会阻塞合并。

## 2. 真实场景夹具与基线

- 插件：`PrivateCloudDisk-plugin-runtime-service/testdata/plugins/realworld/<id>/`
  （`manifest.yaml` + `src/main.py`、可选的 `schemas/`、`README.md` 与样例输入）。
- 输入：`testdata/input/`；期望：`testdata/expected/<id>.golden`（+ `.golden.mode`）。
- 基线生成：`scripts/gen_baselines.py`（离线模拟沙箱上下文与能力网关，不依赖 Docker）。
  入口由插件 `manifest.yaml` 驱动（`entrypoints.events` 按 `priority`，能力走 `exports`），
  与 Runner manifest 驱动执行一一对应。
- 打包：`scripts/package_test_plugins.sh` → `testdata/packages/{id}_{version}.pcdpkg`（受约束 ZIP），
  递归打包 `manifest.yaml`+`src/`+`schemas/`、剔除样例输入、打包前过 Python AST 门禁，
  红色恶意样本需 `--all` 才打包。
- 插件逻辑变更后必须重跑 `make baseline` 并提交漂移后的基线，否则 CI 门禁拒绝。

## 2.1 运行时受限 Python 层（36.x）测试

- 宿主单测：`PYTHONPATH=sandbox/python python3 -m unittest discover -s
  sandbox/python/tests -p 'test_restricted.py'`（`make test-unit` 已并入）——
  覆盖白名单模块放行、黑名单（`os`/`subprocess`/`sys`/`pip`/相对导入）拒绝、危险内置
  （`eval`/`exec`/`compile`/`open`/`input`/`globals`/`locals`/`vars`）拒绝、双下划线
  逃逸链改写（36.28）、PEP 578 审计钩子经 SDK 属性拿到 `os` 句柄后调用 `os.system`
  仍被拦截、`pycloud` 注入调用、stdout 截断。
- Docker 集成：`TestIntegrationRunContainerRestrictedPythonIntercepts` 在真实沙箱执行
  `restricted_import_os.py`/`restricted_dunder.py`/`restricted_eval.py` 恶意样本，断言
  运行时拦截且错误不含宿主路径（脱敏）。
- 模式开关：业务插件默认受限模式开启（`PCD_RESTRICTED_PYTHON=1`）；仅 Docker 隔离探针
  夹具（hostfs/network/rootuser/pids/write_raw_output/context_probe）通过
  `DisableRestrictedPython=true` 关闭，用于验证 Docker 边界本身，不属业务插件路径。

## 2.2 手动调试测试单元（调参 / 教学 / 异常观测）

`PrivateCloudDisk-plugin-runtime-service/internal/sandbox/manual_plugin_test.go`
是按需启用的调试/教学单元（与本节其余测试互补，**不做重复实现**）：

- 来源三选一：未打包插件**目录**（带 `input.json`）、已打包 `.pcdpkg`、单个 `*.py`。
- 三种来源统一走 `pkg.ParseManifestBytes` + `pkg.Parse` 校验后，再由 `runner.go` 公开
  方法（Execute / ExecutePostAvailable / ExecuteCapability）在真实 Docker 沙箱中执行。
- 参数/输入通过 `PCD_DEBUG_*` 环境变量注入（可显式指定 JSON 参数；非 JSON 直接报错）。
- 插件输出、日志、异常、能力调用全部打印；不写数据库、不落盘。
  其中容器日志（`print`/`pycloud.log`/`runner.py`、`restricted.py` 输出）由返回结果的
  `logs` 字段携带并单独打印成段（`====> [容器日志]`），`output` 字段携带入口函数
  返回值，无需再依赖平台上被截断的 `failure_summary`。
- 附带 `TestManualRestrictedProbe`：宿主机直接探针 `restricted.py`（白名单/危险内置/
  `_AttrGuard` 逃逸链/PEP 578 审计钩子），**无需 Docker**。

包装脚本与用法：`scripts/run_manual_plugin.sh`（`dir`/`pkg`/`src`/`restricted` 子命令、
`--input`/`--input-file`/`--capability`/`--event`/`--timeout`/`--memory-mb`/
`--no-restricted`/`--verify-digest`），完整说明见
[`docs/PLUGIN_RUNTIME_MANUAL_HARNESS.md`](PLUGIN_RUNTIME_MANUAL_HARNESS.md)。

示例：

```sh
cd PrivateCloudDisk-plugin-runtime-service
sh scripts/run_manual_plugin.sh dir testdata/plugins/realworld/text_stats     --input-file testdata/input/text_stats.txt
sh scripts/run_manual_plugin.sh dir my-plugin --capability gen     --input '{"text":"hi"}'
sh scripts/run_manual_plugin.sh restricted "import os\ndef main(c):\n    return os.getuid()"
```

未设置任何 `PCD_DEBUG_*` 时 `TestManualPluginDriver` 自动 `Skip`，不进入常规 CI 计数。

## 3. 能力调用 mock 策略（沙箱 --network none）

插件能力调用走每个实例独占的 `/runtime/runtime.sock`。集成测试
`internal/sandbox/integration_realworld_test.go` 的 `capabilityRelay` 实现 Runtime Agent 的 `uds.Invoker`：

- 白名单能力（`api.file.generate_excel`、`api.user.info`）→ 返回预置结构化输出；
- 敏感路径（`api.file.content.get` + `/etc/...`）→ `CAPABILITY_FORBIDDEN`；
- 未注册能力 → `CAPABILITY_UNKNOWN`。

生产侧由 Runtime Agent 验证 Socket Session、实例 Token、帧大小和速率，向 Capability Hub 注入可信上下文后
调用；Hub 完成能力、Schema、安装授权快照和空间权限的最终校验。mock 是该受信转发边界的最小模型，不能回退至
文件轮询。

UDS 专项测试位于 `internal/uds/socket_communication_test.go` 与 `internal/capability/client_test.go`：覆盖
protobuf 帧、伪造实例/Token、声明权限与独立授权快照、并发租户隔离、限流、超时、审计入口/终态与崩溃残留 Socket
清理。Docker 集成测试在 Linux Docker Engine 与沙箱镜像齐备时验证实际 bind mount；Docker Desktop
会以明确原因跳过（而非产生误导性的文件通信回退）。本地 `-tags=integration -run '^$'` 只能证明
其能编译，不能替代 Linux 上的挂载验证。

`TestManagerMaintainsManyIsolatedSessions` 在常规单测中创建 128 个独立 listener 并检查路径、会话统计与
零连接初态；`BenchmarkSocketParallelRoundTrip` 与 `BenchmarkSessionLifecycleOneThousand` 是 Linux CI 的性能
基线，报告实测值而非把“100,000 QPS”写成未经环境与容量规划验证的承诺。

## 4. 编写新测试

- 单元：`internal/sandbox/*_test.go`（无 build tag），用 `t.TempDir()` 与注入 fakes。
- 集成：`integration_*_test.go` 带 `//go:build integration`，复用 `integrationConfig`/
  `realworldRunner`/`assertOutputBinGolden`/`assertReturnJSONGolden`/`assertNoResidual`。
- 新业务插件：在 `testdata/plugins/realworld/` 建目录（`manifest.yaml` + `src/main.py`，合规源码过 AST），补输入与基线（`scripts/gen_baselines.py`），再在 `integration_realworld_test.go` 加断言。
- 恶意/红色样本：只验证拒绝/失败语义，不作为合规插件基线。

## 5. 交付清单

`docs/PLUGIN_RUNTIME_COVERAGE_MATRIX.md` 提供逐清单点的证据映射；本文档覆盖运行方式、
夹具说明、mock 策略与新增用例步骤。
