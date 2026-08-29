# 真实场景 Python 云插件测试 — 覆盖矩阵

清单逐点 → 证据映射。证据路径均相对
`PrivateCloudDisk-plugin-runtime-service/`。集成测试以 `[I]` 标注（`-tags=integration`，
真实 Docker）；其余为单元/Python 测试或版本化数据。

| 清单点 | 证据 | 说明 |
| --- | --- | --- |
| 一 1.1 总目标 | [I] `internal/sandbox/integration_realworld_test.go` | 完整执行链路（Execute/PostAvailable/Capability） |
| 一 1.2 预激活修改链 | [I] `TestRealWorld*`（`Execute`） | 修改 → 候选上传 |
| 一 1.3 激活后只读链 | [I] `integration_docker_test.go::TestIntegrationPostAvailable` | CONTENT_FROZEN / DownloadActive |
| 一 1.4 能力函数导出链 | [I] `TestRealWorldCapabilityReport` | ExecuteCapability + `@capability` |
| 一 1.5 数据处理场景 | `testdata/plugins/realworld/{text_stats,json_cleaner,csv_report,excel_generate,excel_parse}` | 文本/JSON/CSV/报表 |
| 一 1.6 异常场景 | [I] `TestRealWorldTimeoutSim/ResourceHog/InvalidOutput/PathEscape` | 超时/OOM/无效输出/逃逸 |
| 一 1.7 多入口链 | [I] `TestRealWorldMultiEntryChain` | step_a→step_b |
| 一 1.8 下载/解压/校验 | `internal/sandbox/extract_package_test.go`、`realworld_package_test.go` | 包回环/权限 |
| 一 1.9 摘要与安全参数 | `internal/sandbox/digest_verify_test.go`、`container_args_test.go` | 镜像摘要/参数 |
| 一 1.10 审计/脱敏 | `internal/audit/`、`internal/sanitize/`、[I] 审计落盘 | 审计事件与脱敏 |
| 一 1.11 资源清理 | [I] `assertNoResidual` | `--rm` + forceRemove |
| 一 1.12 并发隔离 | [I] `TestRealWorldConcurrentIsolation` | 多实例并行 |
| 一 1.13 pycloud SDK | `sandbox/python/tests/test_pycloud_sdk.py` | 能力通道/文件/生命周期 |
| 一 1.14 Agent 转发(mock) | `integration_realworld_test.go::capabilityRelay` + `internal/uds/` | 每实例 UDS + mock Hub invoker |
| 一 1.15 Docker 隔离 | [I] `integration_docker_test.go`（hostfs/network/rootuser/pids） | 网络/文件/用户/资源 |
| 一 1.16 错误码规范 | `model`/`runner.go` 错误码，[I] 失败断言 | PLUGIN_* 码 |
| 一 1.17 脱敏 | [I] 失败摘要断言、`sanitize_test.go` | 无宿主路径 |
| 一 1.17b 结果日志/输出可观测性 | `model`(`RuntimeChainResult.Logs/Output`、`CapabilityExecutionResult.Logs`)、`runner.go`(`runContainer`/`Execute*`)、[I] 手动 harness `====> [容器日志]` | 容器 stdout/stderr 脱敏后进 `logs`（保留换行，≤64KiB），入口函数返回值进 `output`；成功/失败路径均携带 |
| 一 1.18 超时终止 | [I] `TestRealWorldTimeoutSim` | 容器强杀 |
| 一 1.19 重试/幂等 | [I] `TestIntegrationExecuteRetry`、单测 | 上限 3 |
| 一 1.20 候选上传 | [I] `assertOutputBinGolden`（`uploadContent`） | Upload 捕获 |
| 一 1.21 候选大小 | `runner.go` CandidateMaxBytes + 单测 | 超限拒绝 |
| 一 1.22 result.json 格式 | `runner.go::readSandboxResult` + [I] 超大/无效 | 1MiB 限界 |
| 一 1.23 日志捕获/限长 | `limited_buffer.go`、[I] `TestIntegrationRunContainerLogBuffer` | 截断标记 |
| 一 1.24 多租户资源 | `config_test.go`、[I] pids/memory | 配额隔离 |
| 一 1.25 摘要不匹配 | [I] `TestIntegrationDigestGateBlocksContainer` | 拒绝启动 |
| 一 1.26 seccomp/AppArmor | `scripts/check_sandbox_profiles.sh`、deploy 策略 | 门禁（生产强制） |
| 一 1.27 用户命名空间 | `container_args_test.go`（`--userns-remap`） | 可选注入 |
| 一 1.28 只读根文件系统 | [I] hostfs 断言、containerArgs `--read-only` | 系统目录只读 |
| 一 1.29 停止超时/强清 | `--stop-timeout 2` + `forceRemoveContainer` | [I] 验证 |
| 一 1.30 回归套件 | `scripts/test.sh`、`Makefile`、CI | 可重复执行 |
| 二 2.1-2.18 插件设计 | `testdata/plugins/realworld/`（14 个插件目录） | 每个含 yaml/README/输入/基线 |
| 二 2.19 场景 README | 各插件 `README.md` | 描述与期望 |
| 二 2.20 AST 白名单 | `validator/test_validate_python.py::PythonRealWorldFixtureTests` | 合规必过 |
| 二 2.21 SDK 文档一致 | `docs/PLUGIN_AUTOMATION_PLATFORM_DESIGN.md §9.5` | 方法与权限对齐 |
| 二 2.22 mock 策略 | `docs/PLUGIN_RUNTIME_TESTING.md §3` | capabilityRelay |
| 二 2.23 无硬编码绝对路径 | 全部插件源码 + 字符串门禁 | 基线脱敏 |
| 二 2.24 样例输入 | `testdata/input/`、`TestRealWorldSampleInputs` | 每插件输入 |
| 二 2.25 预期基线 | `testdata/expected/*.golden` | 逐字节对比 |
| 二 2.26 打包 `.pcdpkg` | `testdata/packages/*.pcdpkg` | 版本对齐（受约束 ZIP） |
| 二 2.27 权限正确 | `realworld_package_test.go` | 入口只读 |
| 二 2.28-2.29 大小/文件名 | `extract_package_test.go` | 限界 |
| 二 2.30 无链接/特殊文件 | `internal/package.Parse` + 回环测试 | 拒绝 |
| 三 3.1-3.6 数据目录 | `testdata/input/`、`testdata/expected/` | UTF-8/≤1MB/版本化 |
| 三 3.7-3.12 打包脚本 | `scripts/package_test_plugins.sh` | 命名/清单/AST 门禁 |
| 三 3.18-3.19 基线工具 | `scripts/gen_baselines.py` | 离线生成/漂移门禁 |
| 三 3.20-3.22 断言方式 | `assertOutputBinGolden`/`assertReturnJSONGolden` | golden 对比 |
| 三 3.23 环境覆盖 | `Makefile`（SANDBOX_IMAGE/RUNTIME） | 环境变量 |
| 三 3.25 清理 | 临时目录 `t.TempDir()` | 测试无残留 |
| 三 3.27-3.29 边界/非法/炸弹 | `extract_package_test.go`、`testdata/input/{empty,big,special}` | 边界样本 |
| 四 4.1-4.3 mock Broker | `execution_fakes_test.go::fakeBroker` | Exchange/Download/Upload/DownloadActive |
| 四 4.4-4.6 mock 包客户端 | `fakePackages` | 失败注入 |
| 四 4.7-4.8 mock Audit | `audit_test.go`、[I] 审计落盘 | 字段验证 |
| 四 4.9-4.10 Docker mock | `container_args_test.go`、[I] 集成 | 真实/模拟 |
| 四 4.11-4.13 Docker 可用性 | `dockerAvailable(t)` | 自动 Skip |
| 四 4.14 配置注入 | `newTestRunner` | 结构体注入 |
| 四 4.15-4.16 超时/并发 | [I] timeout/concurrent | 参数化 |
| 四 4.17-4.18 mock 能力 | `capabilityRelay` | 固定 JSON |
| 四 4.19-4.20 docker inspect/ps | [I] `assertNoResidual` | 安全参数/无残留 |
| 四 4.21 脱敏 | [I] 失败摘要断言 | 无宿主路径 |
| 四 4.22-4.23 golden/表驱动 | `assert*Golden`、`t.Run` 表驱动 | 复用 |
| 四 4.29 make 目标 | `Makefile` | test-unit/integration/all |
| 五 5.1-5.30 集成实现 | [I] `integration_realworld_test.go` + `integration_docker_test.go` | 逐项对应（成功/mock/多入口/超时/资源/恶意/逃逸/只读/网络/非 root/清理/日志/脱敏/摘要/重试/并发） |
| 六 6.1-6.30 pycloud SDK | `sandbox/python/tests/test_pycloud_sdk.py`（14 用例） | file.read/write/metadata/move、call_api、user_info、space/notification、超时、脱敏、权限 |
| 七 7.1-7.30 安全隔离 | [I] `integration_docker_test.go`（uid/cap/mount/网络/DNS/只读/noexec/pids/FD/内存/CPU/磁盘 symlink/设备/no-new-privileges/告警）+ `container_args_test.go` | 可测项实测；userns/seccomp/AppArmor 为生产激活项，`config.Load()` 门禁 + 部署文档 |
| 八 8.1-8.30 专项 | [I] `TestRealWorld*`（逐基线断言）+ 表驱动 | 每专项与基线对比 |
| 九 9.1-9.30 执行/CI | `scripts/test.sh`、`Makefile`、`.github/workflows/plugin-platform-security.yml` | 单元/集成/安全/CI 门禁/覆盖率 |
| 十 10.1-10.30 文档 | `docs/PLUGIN_RUNTIME_TESTING.md`、本文、README、CHANGELOG、deploy/README、设计文档 §8.8/9.5 | 全部交付物 |

## Unix Socket 与 Agent 审计迁移覆盖（CF-PLUGIN-UDS-001）

| 清单点 | 证据 | 说明 |
| --- | --- | --- |
| UDS/protobuf 契约 | `proto/capability_socket.proto`、`internal/uds/protocol.go`、`socket_communication_test.go` | 4-byte frame + protobuf wire；不依赖 JSON 文件 |
| 实例隔离与身份 | `internal/uds/manager.go`、`TestSocketRejectsSpoofedInstanceAndToken`、`TestSocketSessionsAreIsolatedAndCleanupSocketFiles` | 每实例 Socket、常量时间 Token、清理 |
| 授权快照 | `internal/capability/client.go`、`client_test.go::TestInvokeUsesTrustedGrantSnapshot` | 不再把声明权限伪装成已授予权限 |
| 审计上移 | `Session.beginAudit/recordAudit`、`withAuditTrails`、`withCapabilityAuditTrails`、`TestSocketRecordsRunningAuditBeforeHubCompletes` | Agent 记录，链式/导出能力结果均回传脱敏事实；SDK/Runner 不读取 `capability-audit.jsonl` |
| Socket 残留安全 | `TestManagerStartupCleansOnlyStaleSocketEndpoints` | 仅清理受控目录中的真实 socket，不删除普通文件 |
| Docker 挂载 | `[I] integration_docker_test.go::runStepDirect` | 每步骤 Session 挂载 `/runtime/runtime.sock`；需 Docker 环境执行 |

## 插件安全改造 36.x — 运行时受限 Python 层覆盖

| 清单点 | 证据 | 说明 |
| --- | --- | --- |
| 36.4 受控启动器 | `sandbox/python/Dockerfile`（ENTRYPOINT `/opt/pcd-sdk/bin/runner.py`）、`runner.py` | 镜像只读层，禁止直接执行用户代码 |
| 36.5-36.6 受限执行/隔离模式 | `runner.py::_run_entry`、`python3 -I -S` | 忽略环境变量/用户 site，仅 `/opt/pcd-sdk` |
| 36.7-36.10 导入白/黑名单 | `sandbox/python/restricted.py::_GuardImport` | `os/sys/subprocess/socket/shutil/pathlib/ctypes/multiprocessing/threading/asyncio/pickle/marshal/shelve/importlib/inspect/pip` 拒绝；白名单 `pycloud`+8 模块 |
| 36.11 危险内置删除 | `restricted.guarded_builtins` | eval/exec/compile/open/input/globals/locals/vars/getattr/setattr/delattr/breakpoint/help/__import__ |
| 36.12 双下划线逃逸链 | `restricted._AttrGuard`、`Test*Restricted` 单测 | AST 改写 → `_pcd_deny` |
| 36.13 PEP 578 审计钩子 | `restricted._install_audit_hook`、单测（经 SDK 属性拿到 `os`） | os.system/exec/spawn/kill/fork、subprocess、socket 阻断；open/import 记录 security.log |
| 36.18 递归深度 | `exec_plugin(recursion_limit=...)` | 默认 2000 |
| 36.21 stdout/stderr 截断 | `restricted.LimitedTextIO`、`LogLimitBytes` | 截断标记 |
| 36.23-36.24 结构化结果/脱敏 | `runner.py::write_result`、`sanitize` | result.json 无原始堆栈/路径 |
| 36.26-36.31 行为测试 | `sandbox/python/tests/test_restricted.py`（23 用例） | import os/pip/相对、eval/exec/compile/open/input、逃逸链、白名单模块、pycloud 注入、审计阻断 |
| 36.36 Docker 恶意样本 | [I] `TestIntegrationRunContainerRestrictedPythonIntercepts` | `restricted_import_os/dunder/eval` 被拦截且错误脱敏 |
| 36.32-36.40 构建/文档/门禁 | `Dockerfile`、`deploy/README.md`、`deploy/plugin-runtime.env.example`、设计文档 §8.9、README、CHANGELOG | `PLUGIN_SANDBOX_DISABLE_RESTRICTED_PYTHON` 生产强制 false |


## `.pcdpkg` / manifest 驱动改造覆盖（插件包分发与运行时安全统一改造清单）

| 清单点 | 证据 | 说明 |
| --- | --- | --- |
| 1.3/1.15-1.18 包格式差距→实现 | `internal/package/parse.go::Parse`、`internal/package/manifest.go` | `.pcdpkg` 受约束解析：`manifest.yaml` 必需、`src/` 必需、顶层目录白名单（`src`/`schemas`/`assets`/`README.md`/`LICENSE`/`manifest.yaml`） |
| 1.20-1.23 pkgclient 下载校验 | `internal/pkgclient/client.go::DownloadPcdpkg` | `.pcdpkg` 扩展名、ZIP 魔数、大小上限（`maxBytes`）、`0700`/`O_EXCL` 隔离落盘 |
| 2.8/2.18-2.19 完整性 + 新/旧方法 | `client.go::downloadOnce`（SHA256 头校验）、`Download`（保留 deprecated） | 下载完整性校验；旧 `Download` 标记 deprecated 以兼容 |
| 3.2/3.3 manifest / src 必需 | `parse.go::Parse`（`containsEntry`+`containsSrc`） | 拒绝旧松散格式（无 manifest / 非 src 结构） |
| 3.4 目录结构白名单 | `parse.go::allowedTopLevel` | 仅 `src`/`schemas`/`assets`/`README.md`/`LICENSE`/`manifest.yaml` |
| 3.5-3.15 manifest 解析与校验 | `manifest.go::ParseManifestBytes`、`manifest_test.go` | `manifest_version: 1`、UUID id、type/version、runtime、entrypoints、exports、limits 合法性与格式校验 |
| 3.16-3.22 包内安全限制 | `parse.go::validateEntryPath`/`isSensitivePath`/`hasExecutableMagic`/`enforceLineLimits`；`extract_package_test.go` | 路径穿越/绝对路径/符号链接/硬链接/设备文件、文件数 ≤1000、体积 ≤20 MiB、单脚本 1 MiB/5000 行、`.env`/私钥/二进制/动态库拒绝 |
| 3.23-3.26 只读 + 结构化返回 | `parse.go::Parsed`（manifest+路径统一返回）；`realworld_package_test.go` | 解压后入口文件只读（`0400`），解析失败返回结构化错误 |
| 4.1-4.25 manifest 驱动执行 | `internal/sandbox/runner.go`（Execute/PostAvailable/Capability 从 manifest 读 module/function/permissions/limits） | 请求外部旧 entrypoint 被拒（`MANIFEST_DRIVEN_REQUIRED`）；事件按 priority、能力按 exports 匹配 |
| 5.1-5.25 manifest 限制应用到沙箱 | `runner.go::containerArgs`、`integration_docker_test.go` | 超时/内存取 manifest limits（不超全局上限）；挂载只读、`PCD_MODULE_PATH/PCD_FUNCTION_NAME` 由 manifest 入口拼装；镜像摘要门禁保持 |
| 6.1-6.25 测试数据迁移 | `testdata/plugins/realworld/*/manifest.yaml` + `src/main.py`（+可选 `schemas/`）；`scripts/package_test_plugins.sh` → `*.pcdpkg`；`scripts/gen_baselines.py` | 基线/打包均 manifest 驱动；测试包可被新的安全解析通过 |
| 7.1-7.20 测试更新 | `realworld_package_test.go`、`extract_package_test.go`、`execution_logic_test.go`、`pcdpkg_builder_test.go` | 新格式回环、拒绝旧式 entrypoint、多入口按 manifest 顺序、能力 exports 匹配、包解析缓存 |
