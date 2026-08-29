# plugin-runtime 手动调试测试单元（Manual Harness）

面向 `PrivateCloudDisk-plugin-runtime-service` 的**手工/教学调试单元**：接收你指定的
插件工程目录（未打包的云插件项目，含 `manifest.yaml` + `src/` + `schemas/`，可带
`input.json`/`input.txt`）、已打包的 `.pcdpkg` 文件，或单个 python 入口文件，然后
像生产环境一样：**manifest 校验 → 受约束包结构校验 → Docker 沙箱容器执行 → 收集
结果**，并把插件输出、日志、异常、能力调用全部 `print` 出来——不写数据库、不落盘。

> 设计约束：**严禁重复实现**。打包比对只做"目录 → 内存 zip"构造；校验全部走
> `internal/package`（`manifest.go` 的 `ParseManifestBytes` = 插件清单规范解析，
> `parse.go` 的 `Parse` = 受约束 ZIP/目录结构安全校验）；容器运行全部复用 `runner.go`
> 的 `Runner.Execute / ExecutePostAvailable / ExecuteCapability`，与
> `integration_docker_test.go` / `integration_realworld_test.go` 同一套 harness
> （`integrationConfig` / `newTestRunner` / `fakeBroker` / `fakePackages` /
> `capabilityRelay` / `preprocessRequest` / `assertNoResidual`）。

## 1. 测试单元位置

- Go 测试：`PrivateCloudDisk-plugin-runtime-service/internal/sandbox/manual_plugin_test.go`
  - `TestManualPluginDriver`：Docker 集成（`-tags=integration`），env 驱动，跑真实容器。
  - `TestManualRestrictedProbe`：宿主直接探针 `sandbox/python/restricted.py`（**无需 Docker**）。
- 包装脚本：`PrivateCloudDisk-plugin-runtime-service/scripts/run_manual_plugin.sh`
- Make 入口：`make debug-plugin ARGS="<子命令> ..."`（等价于直接调脚本）

## 2. 快速开始

```sh
cd PrivateCloudDisk-plugin-runtime-service

# ① 已有 realworld 夹具目录（文本统计 + 输入注入）
sh scripts/run_manual_plugin.sh dir testdata/plugins/realworld/text_stats \
    --input-file testdata/input/text_stats.txt

# ② 你自己的插件工程目录（未打包），能力模式 + JSON 参数注入
sh scripts/run_manual_plugin.sh dir ~/my-plugin --capability generate_report \
    --input '{"text":"hello"}'

# ③ 已打包 .pcdpkg
sh scripts/run_manual_plugin.sh pkg testdata/packages/capability_report_1.0.0.pcdpkg \
    --capability generate_report --input '{"rows":2}'

# ④ 单个 python 文件快速验证受限层（容器内 import os 会被拦截）
sh scripts/run_manual_plugin.sh src my_probe.py

# ⑤ 宿主直接探针 restricted.py（无需 Docker）
sh scripts/run_manual_plugin.sh restricted
```

等价底层命令（不经脚本）：

```sh
PCD_DEBUG_PLUGIN_DIR=~/my-plugin PCD_DEBUG_INPUT='{"text":"hi"}' PCD_DEBUG_CAPABILITY=gen \
  PLUGIN_SANDBOX_RUNTIME=runc \
  go test -tags=integration -v -count=1 \
    -run '^TestManualPluginDriver$' ./internal/sandbox/
```

## 2.1 子命令与选项

| 子命令 | 参数 | 说明 |
| --- | --- | --- |
| `dir` | 插件项目目录 | 未打包工程（`manifest.yaml`/`src/`/`schemas/`，可带 `input.json`） |
| `pkg` | `.pcdpkg` 文件 | 已打包包，走 `pkg.Parse` 受约束校验 |
| `src` | 单个 `*.py` | 自动合成最小 manifest（函数 `main`、事件 ready、能力 exports `run`） |
| `restricted` | 可选 python 源码 | 宿主用 `restricted.exec_plugin` 跑任意源码；不传用内置 5 组探针 |

| 选项 | 作用（对应用户想调的参数） |
| --- | --- |
| `--event <类型>` | 事件：`pcd.file.content.ready.v1`（默认）/ `pcd.file.available.v1` |
| `--capability <name>` | 切换到 `ExecuteCapability`（`manifest.yaml` exports.name） |
| `--input '<json>'` | 注入参数/内容；capability 模式必须是合法 JSON 对象 |
| `--input-file <path>` | 从文件注入内容（优先级高于 `--input`） |
| `--timeout <秒>` | 覆盖容器执行超时（默认 60s） |
| `--memory-mb <MB>` | 覆盖容器内存（默认 256 MiB） |
| `--no-restricted` | 关闭受限 Python 层（探针夹具专用，默认开启） |
| `--verify-digest` | 开启镜像摘要门禁（需 `PLUGIN_SANDBOX_IMAGE_DIGEST`） |
| `--image` / `--runtime` | 沙箱镜像 / 运行时（默认 `pcd/plugin-sandbox-python:0.1.2` / `runc`） |

对应环境变量为 `PCD_DEBUG_*`（`PCD_DEBUG_PLUGIN_DIR`、`PCD_DEBUG_INPUT`、
`PCD_DEBUG_CAPABILITY`、`PCD_DEBUG_EVENT`、`PCD_DEBUG_TIMEOUT`、
`PCD_DEBUG_MEMORY_MB`、`PCD_DEBUG_DISABLE_RESTRICTED`、`PCD_DEBUG_VERIFY_DIGEST`、
`PCD_DEBUG_RESTRICTED_PROBE`/`PCD_DEBUG_RESTRICTED_SNIPPET`），见
`manual_plugin_test.go` 顶部注释。

## 3. 输入注入与错误处理

- 输入来源优先级：`--input-file` > `--input` > `<目录>/input.json` > `<目录>/input.txt` > 空。
- 能力模式（`--capability`）的输入必须是 **JSON 对象**；解析失败会直接报出明确错误
  并终止：`能力输入参数必须是合法 JSON 对象，当前内容无法解析（<err>）：<原文截断>`。
- 内容模式（`--event` / 默认）把输入按原始字节写入输入文件，插件经 `pycloud.file.read`
  读取（与 realworld `inputFor` 同一语义）。

## 4. 驱动流程（与生产一致）

```text
用户插件目录 / .pcdpkg / .py
  -> manifest.go ParseManifestBytes（插件清单 YAML 规范校验 + 打印解析结果）
  -> parse.go Parse（受约束 ZIP + 路径/链接/特殊文件/数量/体积/行数/敏感文件校验 + 打印）
  -> 解析输入
  -> runner.go 公开方法（Execute / ExecutePostAvailable / ExecuteCapability）
       外包 Docker 沙箱镜像，runContainer 注入全部安全参数（--network none、只读挂载、
       --cap-drop ALL、非 root、受限 Python 层 PCD_RESTRICTED_PYTHON=1 ...）
  -> 能力调用经 pycloud 文件通道 -> 宿主 capabilityRelay mock（注册调用被打印）
  -> 打印 Runner 结果（struct + JSON）+ 单独打印容器日志 + 容器无残留校验
```

> 容器日志（插件 `print()` / `pycloud.log()` / `runner.py`、`restricted.py` 的输出、
> 异常退出信息）此前在 `Execute / ExecutePostAvailable / ExecuteCapability` 成功路径被
> 丢弃；现已修复：三者返回的模型新增 `logs` 字段（脱敏、保留换行），并在本单元中
> 用 `====> [容器日志] ... =====> [容器日志结束]` 单独打印成段，便于直接观察
> 容器内行为，不必再去翻 struct/JSON。

## 5. 各模块可观测点

- **`runner.py` / 容器入口启动器**：`dir/pkg/src` 任意模式都会走容器内
  `/opt/pcd-sdk/bin/runner.py`；插件 stdout/异常写入 `result.json` 后由 Runner 解析，
  结果打印在 `Execute 结果 JSON`；`print()`/`pycloud.log()` 等 stdout 输出由
  `runContainer` 捕获并回溯到返回值，落进 `logs` 字段，单独打印成段。
- **`restricted.py`（受限 Python 层）**：
  - 真实容器：`src` 模式传入 `import os`/`eval`/双下划线逃逸链/经 `pycloud` 拿 `os`
    句柄调 `os.system` 的源码，容器内会被 `exec_plugin` 拦截，Runner 返回
    `PLUGIN_EXECUTION_FAILED` 且错误已脱敏（如 `受限环境拒绝：禁止导入模块：os`）。
  - 无 Docker 快速观察：`sh scripts/run_manual_plugin.sh restricted`（内置 5 组探针：
    import os / eval / 逃逸链 / 审计钩子 os.system / 白名单 json;math），或
    `restricted "你的源码"`。
  - 组合开关：`--no-restricted` = 关停受限层（`PCD_RESTRICTED_PYTHON=0`），用于对照
    "受限层遮挡前"的行为（如 `import os` 成功并返回 uid）。
- **`manifest.go` / `parse.go`**：目录与 pcdpkg 两种来源都先打印 `manifest.go` 解析
  JSON 与 `pkg.Parse` 摘要（files/bytes/modules/limits），改清单 YAML 后重跑即可观察
  校验差异。
- **`pycloud` 能力通道**：插件发起的能力调用（`api.user.info`、
  `api.file.generate_excel` 等）会被打印；未注册能力返回 `CAPABILITY_UNKNOWN`。
  默认安全回显 handler 见 `registerManualRelayDefaults`，可按需增删。
- **资源/超时**：`--timeout`、`--memory-mb` 直接改 `Runner.Config`，打印中的
  沙箱配置行会同步显示；`resource_hog`/`timeout_sim` 样本可直接运行观察 OOM/超时。

## 5.1 结果字段与日志可观测性

- `Execute` / `ExecutePostAvailable` 返回的 `RuntimeChainResult` 现含：
  - `output`：最后一个已执行入口函数的序列化返回值（JSON 对象）；
  - `logs`：容器 stdout/stderr 的脱敏文本（保留换行，≤64 KiB）。
- `ExecuteCapability` 返回的 `CapabilityExecutionResult` 同样含 `logs`；
  `output` 为能力函数返回值（原有）。
- 单独打印日志的意义：`FailureSummary` 是压平单行且 ≤1000 字符，而 `logs` 保留
  原始换行，适合观察多行 `print` 与堆栈摘要；两者均已按 `sanitize` 规则脱敏
  （绝对路径、容器 ID、IP、内存地址等被替换为 `[path]`/`[id]`/`[ip]`/`[addr]`）。

## 6. 不做什么

- 不写数据库、不落任何产物文件（仅有测试框架临时目录 `t.TempDir()`，自动清理）。
- 不重复实现 runner/校验/打包校验逻辑——全部复用现有模块。
- 不替换 `make test-unit` / `make test-integration`；本单元是**可选调试入口**，
  未设置任何 `PCD_DEBUG_*` 时 `TestManualPluginDriver` 自动 `Skip`，不影响 CI。

## 7. 常见问题

- 输出看不到？给 `go test` 加 `-v`（脚本已带）。
- 相对路径找不到？脚本与单元会按"仓库根相对路径"解析；建议传绝对路径。
- 容器不启动 / 摘要门禁拒绝？`--verify-digest` 需先设 `PLUGIN_SANDBOX_IMAGE_DIGEST`；
  本地 Docker Desktop 用 `--runtime runc`。
- 想再测一遍发布侧 AST 门禁？受限层是运行时边界；发布门禁仍由
  `validator/validate_python.py` 负责，二者互补。
