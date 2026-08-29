# PrivateCloudDisk-automation-service

文件生命周期自动化服务。消费 Storage 发布的文件可用、内容处理等事件，根据插件入口和工作流规则匹配任务，并持久化执行状态、重试、恢复和 Outbox 发布。

## 技术栈

- Java 21（以构建配置为准）
- Spring Boot 3.4.7
- MyBatis、Flyway、MySQL
- RabbitMQ、REST 内部 API

## 职责边界

- 接收并规范化文件生命周期事件
- 根据空间和规则匹配插件/工作流入口
- 通过 Inbox/Outbox、幂等键和恢复任务持久化执行
- 调用 Plugin、Workflow、Runtime 服务完成扩展执行
- 不负责文件内容存储、用户文件 CRUD 或插件包管理

## 与 Plugin Runtime 的执行结果契约

`RuntimeChainResult` 与 `plugin-runtime-service` 的 `model.RuntimeChainResult` JSON
契约一一对应（`@JsonProperty` 字段名/`omitempty` 语义一致），并通过 `PluginRuntimeClient`
反序列化 Runtime 回包：

- `output`：最后一个已执行插件入口函数的序列化返回值（`Map<String,Object>`，可 `null`）。
- `logs`：容器 stdout/stderr 的脱敏文本（插件 `print`/`pycloud.log`/`runner.py`、
  `restricted.py` 输出与退出信息；保留换行、≤64 KiB，已在 Runtime 侧脱敏）。
- 控制面自行构造的 skipped/failed/timeout 结果两字段传 `null`；
  `PluginRuntimeClient` 收到回包后在 DEBUG 级记录 `output`/`logs` 摘录，供追踪执行面。

## MQ 生命周期拓扑与运维

Automation 是文件内容预处理链路的编排环节（Storage Worker 发 `file.content.ready`
→ Automation 按 priority 调 Plugin Runtime 沙箱执行云插件 → Automation 发
`file.content.processed` 回 Storage 闸门做 CAS 选择），拓扑由
`org.project.automation.config.RabbitLifecycleConfig` 声明：

- 主队列 `pcd.automation.file.content.ready.q` / `pcd.automation.file.available.q`：
  durable + quorum，`x-message-ttl=7 天`，TTL 到期 dead-letter 到专属 DLX。
- 重试队列 `pcd.automation.file.content.ready.q.retry.{1..3}`：1s/4s/16s 三段指数退避后
  回流主队列，`FileContentReadyConsumer` 确认可靠发布并 ACK 后才算重投。
- DLQ `pcd.automation.file.content.ready.dlq` / `pcd.automation.file.available.dlq`：
  `x-message-ttl=30 天（2_592_000_000 ms，Long）`，与 Storage Worker/Platform Service 约定一致。

> ⚠️ 运维注意：DLQ TTL 必须用 Long 声明（`withArgument("x-message-ttl", 2_592_000_000L)`）。
> 曾因 `30 * 24 * 60 * 60 * 1000` int 字面量乘法溢出为 `-1_702_967_296`，与 Broker 既有
> 队列 `x-message-ttl=2592000000` 不等，触发 `PRECONDITION_FAILED`（406）导致
> `RabbitListener` 启动失败、应用无法就绪。若某环境 Broker 上的
> `pcd.automation.file.content.ready.dlq` / `pcd.automation.file.available.dlq` 已被
> 旧版以负 TTL 创建，需删除该队列后重启应用由声明重建（删除前确认无未消费死信或先完成人工重放）。

## 快速开始

    ./gradlew test
    ./gradlew bootRun

在根目录 Compose 中使用 automation profile 启用，具体依赖和环境变量以 src/main/resources/application.yml 为准。
