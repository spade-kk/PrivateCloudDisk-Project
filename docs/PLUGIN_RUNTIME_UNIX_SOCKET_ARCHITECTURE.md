# CloudFlow 插件 Runtime：Unix Domain Socket 与多租户隔离架构

> 文档版本：1.0.0  
> 变更标识：`CF-PLUGIN-UDS-001` / `PLUGIN-RUNTIME-AUDIT-001`  
> 状态：实现门禁；本文件优先于此前“工作目录请求/响应文件通道”的历史描述。  
> 适用范围：`PrivateCloudDisk-plugin-runtime-service`、`pycloud` SDK、Automation Service、Plugin Service，以及 Capability Hub 的内部调用边界。

## 1. 审计结论与必须迁移的边界

2026-08-24 的迁移前审计发现 Runtime 已有 Docker `--network none`、只读根文件系统、非 root、
gVisor/seccomp/AppArmor 生产门禁，以及 Runtime HTTP 的内部服务令牌认证；旧能力调用通道则未达到
多租户隔离和可信审计要求。下表保留审计痕迹，并记录本次实施后的结论：

| 审计项目 | 迁移前事实 | 本次实施结论 |
|---|---|---|
| SDK ↔ Runtime 通信 | `pycloud.capabilities` 在 `/workspace/work/capabilities/requests|responses` 写 JSON 并轮询 | 已删除为能力调用通道；固定 `/runtime/runtime.sock`，每实例一个 UDS protobuf RPC |
| 审计事实 | SDK 写 `/workspace/work/capability-audit.jsonl`，Runner 事后读取 | 已删除；Runtime Agent 在已认证 RPC 的入口创建 `RUNNING` 事实、在出口更新终态 |
| 调用方身份 | JSON 请求携带 `user_id`/`space_id`/`execution_id` | 插件可构造另一租户/空间的字段 | Runtime 从不可修改的 Session 上下文注入，不信任 SDK 传入值 |
| Runtime 能力转发 | 未有统一可信 Hub 客户端 | Runtime 仅以内部服务认证调用 Capability Hub；Hub 为最终能力/空间权限裁决方 |
| Socket 管理 | Runtime 中没有 UDS Session Manager | 单 Runtime Agent 统一管理多个实例 Socket，启动时仅清理受控目录中的残留 Socket |
| 容器边界 | `--network none`、无 Docker Socket 挂载已存在 | 每个容器仅 bind mount 一个不可预测宿主 Socket 到固定容器路径 |
| 测试 | SDK/realworld 测试依赖文件 relay | 单元、SDK、基线生成器均迁移到 UDS；Docker 集成在具备 Docker/镜像的节点执行 |

迁移不改变插件的公开调用方式：`pycloud.call_api()`、`pycloud.user_info()`、
`pycloud.space_members_list()` 等 API 保持不变。兼容性的含义是插件业务代码与 Runtime HTTP 执行接口
保持兼容，**不是保留不安全的文件轮询回退路径**。

## 2. 目标拓扑、职责与信任边界

```text
Automation Service (internal HTTP + service identity)
        |
        v
Plugin Runtime Agent ──────── mTLS / internal token ───────> Capability Hub
        |                                                         |
        | SessionManager                                           | capability registry / schema /
        | creates one UDS per plugin instance                      | user-space-plugin permission decision
        |
        +-- /run/pcd/plugins/<random-instance-id>.sock --bind--> sandbox container
                                                                  |
                                                        /runtime/runtime.sock
                                                                  |
                                                        pycloud SDK (protobuf RPC)
```

- **PyCloud SDK（不可信侧）**：只构造能力键与参数，使用固定容器路径 `/runtime/runtime.sock` 发出请求。
  它不能选择其他 Socket、不能提供用户/空间上下文，也不能写审计事实。
- **Runtime Agent（可信执行边界）**：持有 `PluginSession`、Socket 到实例的绑定、短生命周期实例 Token、
  限流器、请求大小上限与 Runtime 侧审计缓冲；它从 Session 注入用户、空间、插件、安装、版本和执行上下文。
- **Capability Hub（最终授权边界）**：验证能力存在、输入 Schema、插件声明权限与当前用户/空间授权的
  交集，并路由 BUILTIN/API/PLUGIN 能力。Runtime 不能用自身信任绕过 Hub 的结论。
- **Plugin/Workflow Service（查询边界）**：Plugin Service 保存插件实例审计事实并向插件所有者或当前空间
  管理者展示；Workflow Service 保存工作流审计事实；Capability Hub 保存全局审计事实。普通客户端不直接查询 Hub。

Unix Socket 只提供命名空间隔离和降低网络攻击面，**不是认证或授权依据的替代品**。

## 3. Socket 与 Session 生命周期

### 3.1 文件系统约束

- Runtime 启动时创建 `RUNTIME_SOCKET_ROOT`（生产默认 `/run/pcd/plugins`），目录必须为绝对路径、
  由 Runtime 进程拥有且权限为 `0700`。
- 每个插件**实例**（不是插件版本或安装记录）生成不可预测的 192-bit 随机 Session ID；宿主 Socket 为
  `plugin-<session-id>.sock`。路径不可由插件 ID 或执行 ID 推导。
- Socket bind 前以 `Lstat` 拒绝符号链接、目录或预存普通文件；历史残留只能在 Runtime 启动时经同样校验
  后清理，绝不对未验证路径递归删除。
- Socket 权限目标为 `0660`，属主 Runtime 用户、属组 `cloud-runtime`；生产部署必须让容器运行 UID/GID
  与该组匹配。由于 Socket 的连接语义不是普通文件写入，Docker `readonly` bind mount 仅用于禁止修改挂载点
  元数据，不能也不应阻断 SDK 的 `connect()`。
- 仅将该单一 Socket bind mount 到容器 `/runtime/runtime.sock`。容器不挂载 Socket 根目录、Docker Socket、
  宿主 `/run` 或其他宿主路径。

### 3.2 生命周期顺序

1. Automation 请求 Runtime 执行，Runtime 从受信请求建立不可预测的 `PluginSession`。
2. Session Manager 生成 48-byte 随机实例 Token，仅保存在 Agent 内存；创建并监听专属 Socket。
3. Runtime 通过受控 `runner.py --pcd-instance-token <opaque-token>` 启动器参数传入 Token。启动器在载入用户
   模块前调用 SDK 内部配置函数；Token 不写入环境变量、`context.json`、插件包、工作目录或日志。
4. Runtime 以 `--mount type=bind,src=<socket>,dst=/runtime/runtime.sock,readonly` 启动无网络容器。
5. Socket Accept 循环在独立 goroutine 处理连接；每条连接由独立 goroutine 顺序处理帧，SDK 可建立并复用多条连接以并发请求，所有请求经过统一 RPC Pipeline。
6. 容器退出、超时、启动失败、手动取消或 Runtime 优雅停止时：停止新连接、取消 Session Context、关闭监听器/
   连接、删除 Socket，并从并发安全 Session 表移除。清理必须幂等。

Runtime 单进程能管理多个 Socket；每 Socket 一个监听 goroutine、每连接一个读取/响应 goroutine，连接内帧按顺序
处理以保持 request/response 对应关系；SDK 可建立复用多条连接实现受控并发。Go `net` 在 Linux 上使用 runtime
netpoll/epoll。实际容量以压测为准；“100,000 QPS”是容量目标，不是未经压测的生产承诺。

受内部服务令牌保护的 `GET /internal/v1/metrics/uds` 输出**聚合**的 `sessions`、`connections`、`requests`、
`failed_requests`、`error_rate`，供 Runtime 容量监控和告警使用；接口不返回 Socket 路径、Token、插件 ID、租户或参数。

链式插件执行使用 `RuntimeChainResult.audit_trails`；工作流经 `ExecuteCapability` 调用插件能力时使用
`CapabilityExecutionResult.audit_trails`。两种结果都只携带 Agent 生成、已递归脱敏的事实，调用方可按
`audit_id`、`parent_audit_id`、`execution_id` 与 Capability Hub 的全局审计关联；任何一条路径都不得回读插件
工作目录或 `capability-audit.jsonl`。

## 4. Protobuf RPC 契约

协议采用 **length-delimited protobuf**：每帧前置 4-byte big-endian 长度，默认最大帧 `1 MiB`（可通过受限配置下调）；超过限制立即
关闭连接并在 Runtime 记录脱敏安全事件。protobuf 定义是跨 Python/Go 的唯一协议源，禁止 JSON 文件、轮询或
自定义文本协议作为回退通道。

```proto
syntax = "proto3";
package pcd.plugin.runtime.v1;

message ErrorInfo {
  string code = 1;
  string message = 2;
  bool retryable = 3;
}

message CapabilityRequest {
  string request_id = 1;
  string capability_key = 2;
  bytes parameters_json = 3;      // UTF-8 JSON object; capped before decode
  string plugin_instance_id = 4;  // must equal socket-bound Session ID
  bytes instance_token = 5;       // 当前实现为 48-byte 随机 token；constant-time verified
}

message CapabilityResponse {
  string request_id = 1;
  string status = 2;              // SUCCESS | FAILED | TIMEOUT | RATE_LIMITED
  bytes result_json = 3;          // UTF-8 JSON object; capped before encode
  ErrorInfo error = 4;
}
```

参数和结果封装为 JSON bytes 是有意选择：能力 Schema 已以 JSON Schema 管理，避免无界 `Struct` 嵌套和跨语言
数值歧义；RPC 信封、长度限制、错误模型和身份字段仍使用 protobuf。大文件不经 Socket 传递，只使用 Runtime/
Broker 签发的短期逻辑 Lease。

## 5. 三层身份、授权、限流与审计

1. **Socket 绑定身份**：连接进入的 listener 已唯一映射一个 Session。SDK 声称的 `plugin_instance_id` 与该
   Session 不同，立即拒绝 `RUNTIME_INSTANCE_AUTH_FAILED`。
2. **实例 Token**：Token 为 48-byte 随机值，每次实例启动重生；Runtime 用常量时间比较 Token 与 Session。
   缺失/错误 Token 返回 `RUNTIME_INSTANCE_AUTH_FAILED`。Token 永不写入日志、Context、环境变量或审计输入摘要。
3. **Capability Hub 最终授权**：Runtime 不信任请求中的 user/space/plugin/version/install/permissions；全部
   从不可变 Session 上下文构建 AgentCapabilityInvocation。Hub 验证能力键、Schema、插件声明权限 ∩ 当前授权权限，
   并依据用户/空间最终允许或拒绝。

附加防护：

- 每实例限制最大连接数、并发请求数和令牌桶请求速率；达到上限返回 `RUNTIME_RATE_LIMITED`，不阻塞其他实例。
- Runtime ↔ Capability Hub 使用内部服务认证；生产优先 mTLS，最少使用现有内部令牌并限制私网访问。
- 请求/响应、Hub 调用均有超时；只有 Hub 明示为幂等且可重试的错误才进行有界重试。不可用时返回结构化失败，
  不崩溃、不卡住 Socket。
- 输入、输出和错误均经 Runtime 脱敏与大小截断；审计记录请求开始和结束事实，包含调用 ID、父调用 ID、
  来源 Session、能力键/类型、参数/结果摘要、状态、耗时、错误摘要和重试数。

## 6. 审计分层修正

`capability-audit.jsonl` 是被废弃的插件侧审计方案，必须删除：

- SDK 不创建、不写入、不读取 `capability-audit.jsonl`。
- Runner 不扫描工作目录审计文件，也不把它当作 Runtime 事实来源。
- Socket RPC Pipeline 在解码并认证后记录 `RUNNING`，在 Capability Hub 返回、超时、限流或连接异常后记录终态。
- Runtime Agent 将终态审计事实与同一次受信 Runtime 执行结果返回给 Automation；Automation 已通过内部
  Plugin Service 接口持久化执行可观测性。跨进程持久 Outbox/重放仍是部署增强项，不能错误描述为当前
  Runtime 本地已实现的 durable queue。
- Capability Hub 自身仍记录全局事实；三个层次通过调用 ID/父调用 ID 关联，不能由普通前端跨服务直查全局表。

## 7. 兼容、迁移与回滚

- `pycloud` 公共函数签名不变；支持的 Python 版本仍为 3.11。
- Runtime HTTP 执行请求不新增由业务端可伪造的身份字段。Session 只在 Runtime 内部创建。
- 新旧镜像不能在同一执行中混用。升级顺序：部署 Runtime Agent → 发布带 UDS SDK 的 sandbox image → 运行 UDS
  单元/Docker 集成验证 → 禁用并删除文件 relay 代码和测试夹具。
- 如需紧急回滚，回滚整个 Runtime + sandbox image 版本；禁止在新镜像中重新启用文件轮询开关。

## 8. 测试与发布门禁

单元测试必须覆盖：protobuf 编解码、帧长度、无效 Token、伪造实例 ID、未授权能力、连接/请求限流、并发隔离、
断线重连、Session 清理、审计脱敏和 Capability Hub 超时。Docker 集成测试必须覆盖两个容器的独立 Socket 挂载、
没有其他 Socket/Docker Socket/网络、超时清理、真实 SDK 调用以及审计由 Agent 产生。发布前还必须执行：

```text
go test -short ./...
go test -race ./...
go test -tags=integration ./...
PYTHONPATH=sandbox/python python3 -m unittest discover -s sandbox/python/tests
```

生产容量、1000+ 并发实例、长时间 Socket 泄漏和 100,000 QPS 目标必须在 Linux 节点压测报告中验证；它们不能由
macOS 本地单元测试或静态代码审计替代。

本次可复现的本机微基准及其边界记录于[UDS 性能基线](./PLUGIN_RUNTIME_UDS_PERFORMANCE.md)；发布评审必须同时
附上目标 Linux Docker Engine 的端到端性能报告。

## 9. 可编辑架构图与时序图

- [UDS 多租户安全架构（draw.io）](./architecture/plugin-runtime-uds-architecture.drawio)：信任边界、Session、
  Socket、Hub 授权与审计回写。
- [能力调用时序（draw.io）](./architecture/plugin-runtime-uds-sequence.drawio)：从 Automation 建立实例到 Agent
  认证、Hub 授权、审计回传和 Session 清理。
- 对应机器可读输入为 `architecture/plugin-runtime-uds-c4.json` 与
  `architecture/plugin-runtime-uds-sequence.json`。当前环境没有 Graphviz/draw.io CLI，未伪造 PNG/PDF 导出；
  两个 `.drawio` 源文件已通过 XML 结构校验。
