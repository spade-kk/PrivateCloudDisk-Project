# PrivateCloudDisk 插件与自动化开发者指南

> 文档版本：1.1.0<br>
> 适用平台协议：`workflow.cloudflow.io/v1`、`manifest_version: 1`<br>
> 插件包格式：受约束 ZIP（`.pcdpkg`），一律携带 `manifest.yaml`<br>
> 架构与安全细节以 `PLUGIN_AUTOMATION_PLATFORM_DESIGN.md`（第 7/8 章）为准。

## 1. 生命周期与不可变边界

平台把文件内容处理明确分成两个阶段：

```text
file.merge.completed
  -> pcd.file.content.ready.v1
  -> 云插件 preprocess(context)
  -> pcd.file.content.processed.v1
  -> hash -> scan -> active
  -> pcd.file.available.v1
  -> 云插件 on_available(context)
```

- `preprocess` 在最终哈希和安全扫描前运行。插件必须同时声明
  `file.content.read_staging` 与 `file.content.write_pre_activation` 才能提交内容修改。
- `on_available` 在文件可访问后运行。此时原始内容不可变，只允许读取内容、修改业务元数据、
  发送通知或触发工作流。
- 两个入口可以位于同一个云插件版本中；它们共享版本号，但分别声明权限。
- 超时、失败、无匹配插件和自动化服务不可用都不会永久阻塞文件。平台会选择原始副本并继续
  哈希与安全扫描。

## 2. 云插件快速入门

### 2.1 插件包结构（受约束 `.pcdpkg` ZIP）

云插件以 `.pcdpkg` 扩展名的受约束 ZIP 发布，禁止松散 zip 与旧 `plugin.yaml` 格式。
唯一允许的顶层目录是 `src/`、`schemas/`、`assets/`，顶层文件仅限
`manifest.yaml`、`README.md`、`LICENSE`：

```text
plugin.pcdpkg
├── manifest.yaml          # 必须：清单（manifest_version: 1）
├── src/                   # 必须：插件源码（入口模块在此之下）
│   └── main.py
├── schemas/               # 可选：config.schema.json / capability.*.json
│   └── config.schema.json
├── README.md              # 可选：业务说明与预期输出
├── LICENSE                # 可选
└── assets/                # 可选：只读辅助资源
```

包级安全强制（与设计文档第 7 章一致，Runtime 在 `extractPackage`/`Parse` 阶段强制拒绝）：

- 禁止绝对路径、`..`、符号链接、硬链接、设备文件与特殊文件。
- 解压文件数 ≤ 1,000；解压后总体积 ≤ 20 MiB。
- 单脚本 ≤ 1 MiB、≤ 5,000 行、≤ 20,000 AST 节点。
- 禁止 `.env`、私钥（`*.pem`、`id_rsa` 等）、二进制可执行文件与动态库（`.so`/`.dll`/`.dylib`）。
- 解压后文件只读（如 `0400`），插件运行时不可改回。

### 2.2 manifest.yaml 清单

```yaml
manifest_version: 1
plugin:
  id: 8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7  # 合法 UUID
  name: image-compressor
  type: CLOUD_PLUGIN
  version: 1.0.0
runtime:
  language: python
  version: "3.11"          # 必须在沙箱运行时允许列表内
permissions:
  - file.content.read_staging
  - file.content.write_pre_activation
  - file.content.read
  - file.metadata.write
  - file.location.move
  - notification.send
entrypoints:
  events:
  - event: pcd.file.content.ready.v1
    module: src/main.py            # 必须位于包内 src/ 之下
    function: preprocess           # 必须存在于该模块且通过 AST 校验
    priority: 100
    conditions:                    # 触发前预筛，Runtime 执行时复核
      mime_types: ["image/jpeg", "image/png"]
      max_size_bytes: 52428800
    permissions:
      - file.content.read_staging
      - file.content.write_pre_activation
  - event: pcd.file.available.v1
    module: src/main.py
    function: after_available
    permissions:
      - file.content.read
      - file.metadata.write
      - file.location.move
      - notification.send
exports:                     # 可选：工作流可调用的命名能力
  - name: compress
    module: src/main.py
    function: main
    input_schema: schemas/capability.compress.input.json
    output_schema: schemas/capability.compress.output.json
    permissions:
      - file.content.read
limits:                      # 可选：默认上限之下的个性化限制
  timeout_seconds: 120
  memory_mb: 256
```

要点：

- `runtime.language` 必须为 `python`，`runtime.version` 必须在沙箱允许列表（当前 `3.11`）。
- 每个入口的 `permissions` 必须是插件级 `permissions` 的子集；超集在运行前被拒绝。
- `entrypoints.events` 按 `priority` 升序执行；`module` 路径必须在 `src/` 下。
- Runner 已改为 **manifest 驱动**：入口模块/函数只从清单读取，请求中外部传入的旧式
  entrypoint 会被拒绝（错误码 `MANIFEST_DRIVEN_REQUIRED`）。

### 2.3 Python 入口

入口函数签名固定为 `def main(context)`（或清单声明的 `function` 名），通过 `pycloud`
SDK 触达受限能力，禁止直接触碰文件系统/网络：

```python
from pycloud import file


def preprocess(context):
    """激活前入口：仅在授权时读取暂存并提交候选内容。"""
    raw = file.read()                       # 自动映射 read_staging
    text = raw.decode("utf-8", errors="replace")
    output = optimize(text)
    file.write_pre_activation(output.encode("utf-8"))   # 原子候选写
    return {"modified": output != text}


def after_available(context):
    """激活后入口：原始内容不可变，只读/元数据/通知。"""
    meta = file.metadata()
    file.write_pre_activation  # 此处不可用：激活后调用将抛 PermissionError
    notify("已处理", {"size": meta["size"]})
    return {"ok": True}
```

## 3. pycloud SDK（沙箱内唯一触达通道）

| 方法 | 权限 | 生命周期限制 |
| --- | --- | --- |
| `pycloud.file.read(max_bytes=None)` | 自动映射 `read_staging`/`read` | 两阶段 |
| `pycloud.file.read_staging(max_bytes=None)` | `file.content.read_staging` | 仅 `content.ready`，激活后拒绝 |
| `pycloud.file.write_pre_activation(content)` | `file.content.write_pre_activation` | 仅 `content.ready`，激活后拒绝 |
| `pycloud.file.write(content)` | 同上 | 旧 SDK 兼容别名，新插件用 `write_pre_activation` |
| `pycloud.file.metadata()` | `file.content.read_staging` | 两阶段（本地 stat） |
| `pycloud.file.move(destination)` | `file.content.write_pre_activation` | 经能力通道 |
| `pycloud.capabilities.call_api(key, params)` | `platform.capability.invoke` | 经 Agent/能力中心 |
| `pycloud.capabilities.user_info()` | `platform.capability.invoke` | 能力调用 |
| `pycloud.capabilities.space_members_list()` | `platform.capability.invoke` | 能力调用 |
| `pycloud.capabilities.notification_send(users, message)` | `notification.send` | 能力调用 |
| `pycloud.log.info/warning/error(message, fields)` | `plugin.log.write` | 两阶段，受限 stdout |
| `pycloud.capability(name)` 装饰器 | — | 导出命名能力（见第 7 章） |
| `pycloud.require_permission(perm)` | — | 运行期权限自检 |

SDK 不暴露数据库连接、物理存储路径或用户令牌；文件物理路径由 SDK 内部固定，插件无法指定。

### 3.1 能力调用安全传输

`pycloud.capabilities.*` 不使用网络、共享目录或文件轮询。每次运行时，Runtime Agent 仅把当前插件实例的
Unix Domain Socket 挂载到 `/runtime/runtime.sock`；`runner.py` 从其受控启动参数取得一次性实例凭据并在加载
用户模块前完成 SDK 内部配置。插件代码不应、也不能读取 Socket 路径、实例 Token、用户/空间身份或构造原始
协议帧。公开 SDK 函数签名不变。

当 SDK 调用能力时，Runtime Agent 根据 Socket 绑定的实例、Token、安装授权快照和服务端执行上下文向
Capability Hub 转发；Hub 继续执行能力 Schema 与“插件声明权限 ∩ 已授予权限”的最终判定。失败会映射为
`CapabilityError` / `CapabilityTimeout`，不会回退至 TCP、环境变量凭据或文件通道。
每次调用均携带一次性执行能力令牌，由下游服务复核用户、空间、插件声明权限与生命周期阶段。

## 4. 沙箱约束（静态预检 + 运行时受限 Python 双层）

AST 静态校验（`validator/validate_python.py`）只是**预检**，不是安全边界。真正的边界在
沙箱容器内由受限启动器 `/opt/pcd-sdk/bin/runner.py` + `restricted.py` 强制（设计文档 §8.9，
插件安全改造 36.x）：

- 所有云插件必须经该受控启动器执行（镜像 ENTRYPOINT `python3 -I -S
  /opt/pcd-sdk/bin/runner.py`），不允许 `python your_plugin.py` 直跑用户代码。
- **导入白名单（运行时拦截）**：仅允许 `pycloud`、`math`、`json`、`datetime`、
  `collections`、`itertools`、`functools`、`statistics`、`decimal`。
- **导入黑名单（运行时拒绝）**：`os`、`sys`、`subprocess`、`socket`、`shutil`、
  `pathlib`、`ctypes`、`multiprocessing`、`threading`、`asyncio`、`pickle`、`marshal`、
  `shelve`、`importlib`、`inspect`、`pip`（禁止运行时安装包；容器网络同时默认关闭）。
- **危险内置删除/改写**：`eval`、`exec`、`compile`、`open`、`input`、`globals`、`locals`、
  `vars`、`getattr`、`setattr`、`delattr`、`breakpoint`、`help`、`__import__`。
- **双下划线逃逸链**：`__class__`/`__subclasses__`/`__globals__`/`__bases__` 等在源码 AST
  改写层与运行层都不可用（`restricted._AttrGuard`）。
- **PEP 578 审计钩子**：`os.system`/`os.exec*`/`os.spawn*`/`os.kill*`/`os.fork*`/
  `subprocess.*`/`socket.*` 等事件即使经 SDK 属性拿到句柄，也在调用瞬间被拦截；
  `open`/`import` 写 `security.log`。
- 容器层兜底：`--runtime runsc`（生产强制）、`--network none`、只读 RootFS、`--cap-drop
  ALL`、`--no-new-privileges`、非 root（`65532:65532`）、CPU/内存/swap/PID/`nofile` 限制、
  `tmpfs` 临时目录、seccomp + AppArmor（生产强制）。
- 默认上限 1 vCPU、512 MiB、120 秒；清单 `limits` 可下调（不允许超过全局上限）；到期强制终止。
- stdout/stderr 受控捕获并按 `LogLimitBytes` 截断；所有错误统一脱敏（不含绝对路径/堆栈/地址）。

**开发者正确姿势**：文件读写用 `pycloud.file`，平台能力用 `pycloud.capabilities.*`，
日志用 `pycloud.log.*`。不要尝试 `import os` 或拼接物理路径——会被运行时拦截并判定为失效插件。

## 5. 本地插件

本地插件使用 ES2022 模块，并声明目标平台、客户端类型与权限。Web 端先验证包的 SHA-256
和 Ed25519 平台签名，再在不具备同源权限、禁止网络的 sandbox iframe 中执行。

```javascript
export async function activate(context) {
  await plugin.system.notify('插件已启用', context.plugin.name)
}

export async function run(context) {
  const content = await plugin.file.read({ fileId: context.fileId })
  return { byteLength: content.byteLength }
}
```

宿主只通过结构化消息代理已授权能力。插件不能读取 Token、Cookie、宿主 DOM 或未经用户选择
的本地文件。桌面与移动端还需要通过各平台原生沙箱和系统权限再次限制。

## 6. 工作流 DSL（CloudFlow）

```text
workflow "ArchiveContract" {
    metadata { display_name = "合同归档" version = "1.0" }
    trigger { event { name = "pcd.file.available.v1" } }
    step inspect {
        action file.metadata { file_id = vars.file_id }
        output metadata
    }
    step archive {
        depends_on inspect
        action file.move { file_id = vars.file_id target = "/合同归档/" }
    }
}
```

`uses` 必须来自能力中心。保存前，前后端都会校验 CloudFlow 语法、依赖图无环、变量引用安全、
能力存在性、参数 schema 及权限交集。CloudFlow YAML 前端**不接受旧版 `automation.pcd/v1` YAML**
（`apiVersion/kind/metadata/spec/limits`、`uses/needs/result`）；旧示例需用新版字段
（`action: plugin:<id>:<fn>@<v>`、`depends`、`when`、`output`）并以 `${{ }}` 表达式重写，示例见
`examples/yaml/weekly_sales_report.flow.yaml`。运行时表达式不是 Python/JavaScript `eval`。

## 7. 能力函数导出

云插件通过在 `manifest.yaml` 的 `exports` 声明可供工作流调用的命名能力，并在源码中用
`@capability(name)` 装饰器注册同名词条：

```yaml
exports:
  - name: generate_report
    module: src/main.py
    function: build_report
    input_schema: schemas/capability.generate_report.input.json
    output_schema: schemas/capability.generate_report.output.json
    permissions:
      - file.metadata.read
```

```python
from pycloud import capability

@capability("generate_report")
def build_report(input_data):
    text = input_data.get("text", "")
    return {
        "status": "ok",
        "line_count": len(text.splitlines()),
    }

def main(context):
    input_data = context.get("input") or {}   # ExecuteCapability 传入结构化 input
    return build_report(input_data)
```

- 工作流通过能力标识 `plugin:<plugin-slug>.<name>` 调用；Runtime 从 manifest `exports` 按
  名称匹配得到 `module`/`function`，而不是信任外部模块路径。
- 调用仍进入服务端沙箱，权限取调用用户/空间授权与插件声明权限的**最小交集**。
- 执行入口假若声明了预激活写权限（`file.content.write_pre_activation`），能力调用将返回
  `CONTENT_FROZEN`；能力输出必须走结构化返回，不允许直接写预激活候选。

## 8. 版本、发布与市场

1. 创建插件或工作流草稿；插件源码放入 `src/`，补齐 `manifest.yaml`。
2. 以受约束 `.pcdpkg` ZIP 打包（`scripts/package_test_plugins.sh` 即参考实现：递归打包前过
   Python AST 门禁、剔除输入样本、生成 `{plugin_id}_{version}.pcdpkg`）。
3. 上传不可执行候选包后先过 `manifest_version: 1` 与包安全校验（路径穿越/链接/敏感文件/
   数量/体积/单脚本复杂度），再完成语法与安全扫描；校验失败直接拒绝，不进入执行阶段。
4. 校验通过后发布不可变版本；同一版本号不得覆盖。发布侧保存 `manifest.yaml` 解析结果，
   供能力中心按插件 ID + 版本 ID 找到 `exports`。
5. 公共项目提交市场审核；审核通过后才能被其他用户或空间安装。
6. 安装页面展示权限差异；高风险权限需再次确认。
7. 更新采用新版本安装记录，不覆盖历史版本和执行证据。
8. 旧格式插件包（无 `manifest.yaml`/非 `src/` 结构）**不允许继续执行**，须迁移为 `.pcdpkg`。

## 9. 运行日志与调试

执行记录包含触发来源、用户、空间、客户端、插件/工作流版本、开始结束时间、状态和脱敏摘要。

### 执行日志与能力调用审计

插件 `print`、`pycloud.log` 和受控容器错误输出会以日志行保存；请勿把 Token、密码或宿主绝对路径写入日志。服务端在 Runtime 与 Plugin Service 两个边界再次脱敏，但开发者不应依赖脱敏规则修复不安全输出。

通过 `pycloud.capabilities.call_api()` 调用平台能力时，**Runtime Agent**（而非不可信插件 SDK）记录实际调用事实
（能力键、已脱敏参数/返回、状态、耗时和受信执行上下文）。插件中心执行详情默认显示后端生成的自然语言摘要；
拥有查看权限的用户可切换到详情模式查看脱敏 JSON。完整 API、分页和 SSE 约定见
[插件执行记录、日志与能力审计](./PLUGIN_EXECUTION_OBSERVABILITY.md)。
用户代码异常只返回用户源码行号，不返回宿主绝对路径、内部类名、凭据或完整环境变量。
失败实例可以从原版本和原输入重跑；已经成功的副作用步骤需依赖幂等键避免重复写入。
生产默认关闭 Debug；`--debug` 完整堆栈仅限开发环境。

## 10. 发布前检查

- [ ] 包扩展名为 `.pcdpkg`，顶层结构只含 `manifest.yaml`、`src/`、可选 `schemas/`/`assets/`/
  `README.md`/`LICENSE`。
- [ ] `manifest.yaml`：`manifest_version: 1`、合法 UUID `plugin.id`、`type: CLOUD_PLUGIN`、
      SemVer、`runtime.language: python` / 允许版本。
- [ ] `entrypoints.events[].module` 均在 `src/` 下且函数存在；按 `priority` 排列。
- [ ] 每个入口/导出声明全局 `permissions` 的子集；能力调用入口不带
      `file.content.write_pre_activation`。
- [ ] 只通过 `pycloud.*` 读写文件、调用能力、记日志；不 `import os/sys/subprocess/...`，
      不碰 `eval/exec/open/__class__` 等受限设施。
- [ ] `on_available` 不含原始内容写入逻辑；能力导出走结构化返回。
- [ ] 所有输出均可 JSON 序列化且不包含敏感信息（绝对路径/堆栈/地址）。
- [ ] 插件对超时、空文件、重复事件和重试具备幂等行为。
- [ ] 在个人空间和只读/可编辑空间角色下分别验证权限；验证成功、失败、超时与平台降级路径。
