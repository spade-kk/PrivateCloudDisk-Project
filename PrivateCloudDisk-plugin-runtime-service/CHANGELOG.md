# Changelog

All notable changes to `PrivateCloudDisk-plugin-runtime-service` are documented here.

## [Unreleased]

### Unix Domain Socket 能力通信与 Agent 侧审计（CF-PLUGIN-UDS-001）

- 新增 `proto/capability_socket.proto`、`internal/uds` 和每实例 `PluginSession`：Runtime 单进程为每个
  插件实例创建独立 Socket、Session ID 和 Token，仅将该 Socket bind mount 为 `/runtime/runtime.sock`。
- `pycloud.capabilities` 改为固定路径的长度分帧 protobuf RPC；不回退 TCP 或文件轮询，公共 SDK API 保持兼容。
- Token 仅经 `runner.py --pcd-instance-*` 受控启动参数传入，既不进入环境变量、`context.json`、日志，也不由插件
  提供用户/空间/插件身份。Runtime 对 Socket Session、Token、帧、连接数、速率和请求超时执行校验。
- Runtime 到 Capability Hub 的客户端只转发独立的受信安装授权快照，修复“声明权限被错误当作授予权限”的风险；
  Hub 继续执行最终声明权限 ∩ 授予权限、Schema 和空间权限验证。
- SDK 不再写 `capability-audit.jsonl`；Agent 在已认证调用入口创建 `RUNNING` 审计事实并在响应出口更新终态，
  随 `RuntimeChainResult.audit_trails` 或 `CapabilityExecutionResult.audit_trails` 返回受信调用方；
  插件链由 Automation 写入 Plugin Service，工作流调用可按稳定调用 ID 与 Hub 审计关联。审计输入/输出递归
  脱敏凭据与绝对路径。
- 新增 UDS 连接、protobuf 编解码、伪造实例/Token、授权快照、并发隔离、限流、路径清理、断线/超时和审计测试。
- 新增受内部服务令牌保护的 UDS 聚合指标接口（会话、连接、请求、失败数和错误率）；不泄露 Socket 路径、Token
  或租户上下文。

## [0.6.0] - 2026-08-23

### 容器日志与输出可观测性修复（logs/output 字段）

- `internal/sandbox/runner.go`：修复 `runContainer` 成功路径只返回 `result.json` 原文、
  丢弃容器 stdout/stderr 的问题——现在成功/失败统一返回容器日志
  （插件 `print`/`pycloud.log`/`runner.py`、`restricted.py` 输出、退出信息）。
- `internal/model/model.go`：`RuntimeChainResult` 新增 `output`（最后入口函数返回值）与
  `logs`（脱敏容器日志，保留换行）；`CapabilityExecutionResult` 新增 `logs`。
- `Execute / ExecutePostAvailable / ExecuteCapability` 全部把 `logs`/`output` 带回
  结果模型（失败路径 `failed`/`capabilityFailed` 也携带日志），不再被调用方丢弃；
  `FailureSummary` 语义保持既有行为不变。
- `sanitize` 复用：`logs` 经 `sanitize.Sanitize` 脱敏（绝对路径/容器 ID/IP/内存地址
  打码）并限长 64 KiB；不影响候选内容提交与脱敏门禁。
- `internal/sandbox/manual_plugin_test.go`：新增 `dumpLogs`，把 `logs` 用
  `====> [容器日志]` 标记单独打印成段，方便直接观察容器内行为。
- 测试：既有单元/集成断言均为字段级，不回归；`internal/sandbox` 单测通过。
- 文档：`docs/PLUGIN_RUNTIME_MANUAL_HARNESS.md` §4/§5（新增 §5.1 结果字段与日志
  可观测性）、`docs/PLUGIN_RUNTIME_TESTING.md` §2.2、README 测试章节、本 CHANGELOG。

### 手动调试测试单元（Manual Harness）

- 新增 `internal/sandbox/manual_plugin_test.go`：
  - `TestManualPluginDriver`（`//go:build integration`，env 驱动）：接收插件**目录**
    （未打包工程，含 manifest.yaml + src/ + schemas/，可带 input.json）、**已打包 .pcdpkg**
    或**单个 python 入口文件**，三种来源统一经 `pkg.ParseManifestBytes` + `pkg.Parse`
    校验后，由 `runner.go` 的 `Execute / ExecutePostAvailable / ExecuteCapability` 在
    真实 Docker 沙箱执行；插件输出/日志/异常/能力调用全部打印，不写数据库、不落盘。
  - `TestManualRestrictedProbe`（无需 Docker）：宿主直接用 `restricted.py` 的
    `exec_plugin`/`_AttrGuard`/PEP 578 `sys.addaudithook` 探针（import os / eval /
    双下划线逃逸链 / 经 SDK 句柄调 os.system / 白名单 json;math），并可注入自定义源码。
  - 复用 `integrationConfig`/`newTestRunner`/`fakeBroker`/`fakePackages`/`capabilityRelay`
    /`preprocessRequest`/`assertNoResidual`，不重复实现 runner/校验/打包逻辑。
- 新增 `scripts/run_manual_plugin.sh`：CLI → `PCD_DEBUG_*` 环境变量映射包装
  （`dir|pkg|src|restricted` 子命令、`--input/--input-file/--capability/--event/
  --timeout/--memory-mb/--no-restricted/--verify-digest`）。
- `Makefile` 新增 `debug-plugin` 目标（`make debug-plugin ARGS="dir <目录> ..."`）；
  `README.md` 测试章节补充手动调试用法。
- 文档：`docs/PLUGIN_RUNTIME_MANUAL_HARNESS.md`（完整用法/观测点/FAQ）、
  `docs/PLUGIN_RUNTIME_TESTING.md` §2.2 新增手动调试章节、§1 分层表补行。
- 测试数据文档同步：`testdata/packages/README.md` 与 `testdata/plugins/realworld/README.md`
  重写为 `.pcdpkg`/manifest.yaml + src/ 结构；`testdata/plugins/README.md` 补
  `restricted_*.py` 受限层样本表项。
- 设计/覆盖文档一致性：`docs/PLUGIN_AUTOMATION_PLATFORM_DESIGN.md` §7.6 迁移说明改为自包含并
  引用真实脚本、§8.8 文件数上限由 256 修正为 1000 并对齐 `internal/package.Parse` 命名；
  `docs/PLUGIN_RUNTIME_COVERAGE_MATRIX.md` 同步 `extractPackage` 引用。

## [0.5.0] - 2026-08-22


### 运行时受限 Python 层（插件安全改造 36.x）

- 新增 `sandbox/python/restricted.py`：运行时强制实施设计文档 §8.4 的 Python 白名单/黑名单，
  不再只依赖 AST 静态预检——白名单 import 钩子、危险内置删除（`eval/exec/compile/open/input/
  globals/locals/vars/getattr/setattr/delattr/breakpoint/help/__import__`）、双下划线逃逸链 AST
  改写、PEP 578 审计钩子（`os.system/exec/spawn/kill/fork`、`subprocess.*`、`socket.*` 调用瞬间
  阻断，`open/import` 记录 `security.log`）、递归深度上限、stdout/stderr 截断。
- 受控启动器 `sandbox/python/runner.py` 移入 `/opt/pcd-sdk/bin/`（36.4），镜像
  `pcd/plugin-sandbox-python:0.1.2` 更新 ENTRYPOINT `python3 -I -S
  /opt/pcd-sdk/bin/runner.py`；默认经 `restricted.exec_plugin` 执行所有云插件，插件无法改写
  只读 SDK 层（36.25）。
- Go 配置 `PLUGIN_SANDBOX_DISABLE_RESTRICTED_PYTHON`：生产模式 `config.Load()` 强制 false；
  `containerArgs` 注入 `PCD_RESTRICTED_PYTHON=1`（仅 Docker 隔离探针测试显式关闭）。
- 测试：`sandbox/python/tests/test_restricted.py` 23 个受限层用例（36.26-36.31）；
  集成 `TestIntegrationRunContainerRestrictedPythonIntercepts`（真实 Docker 恶意样本拦截与脱敏）；
  `container_args_test.go`/`config_test.go` 覆盖开关与生产门禁。
- 文档：`docs/PLUGIN_AUTOMATION_PLATFORM_DESIGN.md` 新增 §7.6/§8.9 代码现状（未改动设计内容）、
  §8.8 补充受限层测试矩阵；`deploy/README.md`、`deploy/plugin-runtime.env.example`、
  `README.md`、`Makefile` 同步。

### 沙盒执行正确性修复

- `internal/package/parse.go`：修复魔数检测消费前 4 字节未回放导致每个解压文件丢失开头字节的
  严重缺陷（曾令所有 Docker 沙箱执行失败）；新增 `TestParsePreservesFileBytes` 回归。
- `internal/sandbox/runner.go`：预激活 `Execute` 链路修正 `contentFrozen=false`（此前误传 true，
  导致 `file.read()` 错误地要求 `file.content.read` 权限）；Execute 失败映射补上容器日志
  （`summarizeWithLogs`），便于定位脱敏后的插件错误。
- `internal/pkgclient/client.go`：下载失败清理残留目标文件，修复重试时 `O_EXCL` 撞上"已存在"。
- 集成测试修正（manifest 驱动）：`capability_user_info` 用 export `user_info`、
  `path_escape` 用 export `read_file`，与 `manifest.yaml` 的 exports 对齐。

## [0.4.0] - 2026-08-22

### 真实场景 Python 云插件测试（真实场景测试清单）

- `pycloud` SDK 新增能力调用通道：`call_api`/`user_info`/`space_members_list`/`notification_send`
  走 `/workspace/work/capabilities` 请求/响应文件通道（沙箱 `--network none` 下唯一出网途径，
  需 `platform.capability.invoke` 权限），`file.metadata`/`file.move` 补充；
  `sandbox/python/tests/test_pycloud_sdk.py` 新增 14 个能力通道/生命周期用例。
- 新增 `testdata/plugins/realworld/`（14 个业务插件：文本统计/JSON 清洗/CSV 报表/Excel mock 生成/
  数据解析/能力导出/用户信息/超时/资源耗尽/恶意导入/路径逃逸/内容反转/无效输出/多入口链），
  每个含 `plugin.yaml`/`README.md`/样例输入/基线；`testdata/input/` + `testdata/expected/*.golden`
  版本化提交。
- 新增 `internal/sandbox/integration_realworld_test.go`（`//go:build integration`）：真实 Docker
  执行 realworld 插件并断言输出与基线一致；`capabilityRelay` 扮演能力网关与数据面权限守卫（白名单
  应答、`/etc/**` 路径拒绝）；覆盖专项基线、超时/OOM/无效输出失败语义、并发隔离。
- 新增 `internal/sandbox/realworld_package_test.go`（单测）：插件包 extractPackage 回环、plugin.yaml
  清单与入口一致性、基线模式与样例输入完整性。
- 工具：`scripts/gen_baselines.py`（离线基线生成）、`scripts/package_test_plugins.sh`（AST 门禁打包）、
  `Makefile`（test-unit/test-integration/test-all/baseline/test-packages/coverage）；CI `go-runtime`
  与 `plugin-runtime-integration` 增加 SDK 单测、realworld 集成与包/基线漂移门禁。
- 文档：`docs/PLUGIN_RUNTIME_TESTING.md`（测试指南）、`docs/PLUGIN_RUNTIME_COVERAGE_MATRIX.md`
  （覆盖矩阵）、`docs/PLUGIN_AUTOMATION_PLATFORM_DESIGN.md` §8.8/§9.5（realworld 层与能力通道）、
  `README.md`（测试章节）、`deploy/README.md`（镜像版本与验证）。
- 沙盒镜像 `pcd/plugin-sandbox-python:0.1.2` 内置能力通道 SDK（本地构建验证）。

## [0.3.0] - 2026-08-22

### 测试覆盖增强（对照插件运行时测试清单五/六/七）

- `internal/sandbox/container_args_test.go`：锁定 `containerArgs` 全部安全参数（资源、命名空间、
  只读挂载、cap-drop、非 root、可选 userns/seccomp/AppArmor、镜像末位）。Docker 29.x 起 PID/UTS
  私有为默认，移除显式 `--pid=private/--uts=private`（保留 `--ipc=private/--cgroupns=private`）。
- `internal/sandbox/execution_fakes_test.go`：注入式 `BrokerClient`/`PackageClient` fakes +
  插件包构建助手；`execution_logic_test.go`：Execute/PostAvailable/Capability 的分支与错误码映射；
  `extract_package_test.go`/`digest_verify_test.go`/`runner_helpers_test.go`：恶意包、摘要门禁并发、
  工具函数边界。
- `internal/sandbox/integration_docker_test.go`（`//go:build integration`）：真实 Docker 验证成功/
  失败脱敏/超时强杀/日志截断/宿主网络隔离/非 root/只读挂载/工作目录可写/pids-limit/容器清理/重试/
  摘要门禁/审计落盘；无 Docker 自动 Skip。
- `internal/broker`、`internal/pkgclient`：httptest 契约测试（下载/上传/兑换、限界、哈希不一致、
  O_EXCL、上下文头、取消）；`internal/config`：默认值与生产门禁表驱动；`internal/model` 序列化；
  `internal/sanitize`/`audit` 补充 `Path`/`RawJSON`/并发规则/审计单行 JSON。

### 沙盒加固与兼容

- `runner.go`：`verifySandboxImageDigestOnce` 修复空摘要绕过与并发竞态（`digestMu`）；`extractPackage`
  迁移为受约束 `.pcdpkg` 安全解析，禁止路径穿越/链接/特殊文件/敏感文件并收紧只读权限；`containerArgs`
  注入 userns-remap/seccomp/AppArmor 可选加固参数；生产门禁由 `config.Load()` 强制 runsc/seccomp/
  AppArmor/无网络/禁 Debug。
- `internal/package/`（新）：`manifest.yaml` 解析（plugin id/type/version/runtime/permissions/
  entrypoints/exports/limits）与受约束 ZIP 校验，`Parse` 返回结构化元数据供 Runner manifest 驱动执行。
- `internal/pkgclient/`（新）：`.pcdpkg` 安全下载客户端（扩展名/ZIP 魔数/大小上限/SHA256/内部令牌/
  隔离落盘），`DownloadPcdpkg` 替代并 deprecated 旧 `Download` 语义。
- 沙箱镜像 `pcd/plugin-sandbox-python:0.1.2` 内置 pycloud 能力通道 SDK；`deploy/README.md` 与
  `script/check_sandbox_profiles.sh` 保证 seccomp/AppArmor 策略产物与 `deploy/` 权威源一致。
