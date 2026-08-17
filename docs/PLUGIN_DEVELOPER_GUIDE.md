# PrivateCloudDisk 插件与自动化开发者指南

> 文档版本：1.0.0  
> 适用平台协议：`workflow.cloudflow.io/v1`、`manifest_version: 1`
> 架构与安全细节以 `PLUGIN_AUTOMATION_PLATFORM_DESIGN.md` 为准。

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

### 2.1 清单

```yaml
manifest_version: 1
plugin:
  id: 00000000-0000-0000-0000-000000000000
  type: CLOUD_PLUGIN
  version: 1.0.0
  runtime: PYTHON_3_11
  entrypoint: src/main.py
  permissions:
    - file.content.read_staging
    - file.content.write_pre_activation
    - file.metadata.read
    - plugin.log.write
  entrypoints:
    - event: pcd.file.content.ready.v1
      function: preprocess
      priority: 100
      permissions:
        - file.content.read_staging
        - file.content.write_pre_activation
    - event: pcd.file.available.v1
      function: on_available
      priority: 100
      permissions:
        - file.metadata.read
        - plugin.log.write
```

### 2.2 Python 入口

```python
import pycloud


def preprocess(context):
    """激活前入口：仅在已授权时修改暂存内容。"""
    content = pycloud.file.read_staging(context["file_id"])
    output = optimize(content)
    if output != content:
        pycloud.file.write_pre_activation(context["file_id"], output)
        return {"modified": True}
    return {"modified": False}


def on_available(context):
    """激活后入口：原始文件已不可变。"""
    pycloud.log.info("文件已通过安全扫描", {"file_id": context["file_id"]})
    return {"metadata_updated": False}
```

## 3. pycloud SDK

| 能力 | 权限 | 生命周期限制 |
|---|---|---|
| `pycloud.file.read_staging(file_id)` | `file.content.read_staging` | 仅 `content.ready` |
| `pycloud.file.write_pre_activation(file_id, bytes)` | `file.content.write_pre_activation` | 仅 `content.ready` |
| `pycloud.file.read(file_id)` | `file.content.read` | 文件已激活 |
| `pycloud.file.metadata(file_id)` | `file.metadata.read` | 两阶段 |
| `pycloud.file.update_metadata(file_id, patch)` | `file.metadata.write` | 激活后可用 |
| `pycloud.notification.send(title, body)` | `notification.send` | 两阶段 |
| `pycloud.log.info/warn/error(message, fields)` | `plugin.log.write` | 两阶段 |

SDK 不暴露数据库连接、物理存储路径或用户令牌。每次调用均携带一次性执行能力令牌，
由被调用服务重新校验用户、空间、插件声明权限和当前生命周期阶段。

## 4. 沙箱约束

- 默认 1 vCPU、512 MiB、120 秒；到期后强制终止。
- 根文件系统只读，`/tmp/pcd-work` 为独立临时目录；任务结束后销毁。
- 默认无出站网络；禁止挂载 Docker Socket、宿主 `/proc` 和业务数据库凭据。
- Python 仅允许导入安全白名单模块和 `pycloud`。禁止 `os`、`sys`、`subprocess`、
  `socket`、动态导入、`eval`、`exec` 与 `compile`。
- 平台同时限制源码字节数、行数、AST 节点、日志长度、输入输出大小和子进程数。

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
能力存在性、参数 schema 及权限交集。CloudFlow 不再接受 `automation.pcd/v1` YAML；旧版本需
在编辑器中按上面的块结构重新保存。运行时表达式不是 Python/JavaScript `eval`。

## 7. 能力函数导出

云插件可以在清单中声明供工作流调用的能力：

```yaml
capabilities:
  - name: generate_report
    description: 按模板生成脱敏报告
    input_schema:
      type: object
      required: [template_id, rows]
    output_schema:
      type: object
      required: [file_id]
    permissions:
      - file.metadata.read
      - file.content.write_pre_activation
```

能力标识为 `plugin:<plugin-slug>.<function>`。调用仍进入服务端沙箱，并使用调用用户与空间
权限和插件声明权限的最小交集。

## 8. 版本、发布与市场

1. 创建插件或工作流草稿。
2. 上传不可执行的候选包，完成哈希、解压边界、语法与安全扫描。
3. 校验通过后发布不可变版本；同一版本号不得覆盖。
4. 公共项目提交市场审核。审核通过后才能被其他用户或空间安装。
5. 安装页面展示权限差异；高风险权限需再次确认。
6. 更新采用新版本安装记录，不覆盖历史版本和执行证据。

## 9. 运行日志与调试

执行记录包含触发来源、用户、空间、客户端、插件/工作流版本、开始结束时间、状态和脱敏摘要。
用户代码异常只返回用户源码行号，不返回宿主绝对路径、内部类名、凭据或完整环境变量。
失败实例可以从原版本和原输入重跑；已经成功的副作用步骤需依赖幂等键避免重复写入。

## 10. 发布前检查

- [ ] 清单版本、插件 ID、SemVer 和入口文件一致。
- [ ] 每个入口只声明实际需要的最小权限。
- [ ] `on_available` 不包含原始内容写入逻辑。
- [ ] 所有输出均可 JSON 序列化且不包含敏感信息。
- [ ] 插件对超时、空文件、重复事件和重试具备幂等行为。
- [ ] 工作流图无环，所有 `needs` 指向已存在步骤。
- [ ] 在个人空间和只读/可编辑空间角色下分别验证权限。
- [ ] 验证成功、失败、超时和平台降级路径。
