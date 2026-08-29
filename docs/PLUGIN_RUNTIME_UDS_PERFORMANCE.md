# Plugin Runtime Unix Socket 性能基线

> 日期：2026-08-24  
> 变更：`CF-PLUGIN-UDS-001`  
> 目的：记录可复现的 UDS 协议与多 Socket 生命周期基线；它不是生产容量承诺。

## 测试环境

| 项目 | 值 |
| --- | --- |
| 主机 | Apple M1（8 logical CPUs） |
| OS / Go | Darwin arm64 / 当前仓库 `go.mod` 工具链 |
| RPC | 每实例 Unix Domain Socket、4-byte length-delimited protobuf frame、JSON 参数体 |
| Invoker | 内存 `fakeInvoker`；**不包含** HTTP Capability Hub、mTLS、数据库或 Docker 容器 |
| 命令 | `go test -run '^$' -bench BenchmarkSocketParallelRoundTrip -benchtime=1s ./internal/uds` |

## 实测结果

```text
BenchmarkSocketParallelRoundTrip-8    118012    10141 ns/op    6998 B/op    102 allocs/op
BenchmarkSessionLifecycleOneThousand-8     1    168255959 ns/op
```

- 并发 request/response 微基准约为 **98,600 次/秒**（`1e9 / 10141`），仅说明本机、内存
  Invoker 和当前协议实现的量级；不能宣称真实部署已满足 100,000 QPS。
- 1,000 个独立 `PluginSession`（listener、随机 ID/Token、Socket 文件）从创建到统一关闭为约
  **168 ms** 的一次性基线。该数字包含本机文件系统和 goroutine 调度，不包含 Docker 启动。
- 常规 `TestManagerMaintainsManyIsolatedSessions` 覆盖 128 个 listener 的隔离与统计；1,000
  实例由 `BenchmarkSessionLifecycleOneThousand` 覆盖，避免把耗时压入每次单元测试。

## 解释与发布门槛

1. 此基准验证 Go `net` 的本机 UDS 处理路径，不能替代 Linux Docker Engine 中真实 bind mount、
   容器 UID/GID、Capability Hub HTTP/mTLS、授权、限流和审计回写的端到端压测。
2. Docker Desktop 通过 VM 代理宿主路径，不能可靠挂载宿主 Unix Socket；集成测试会明确跳过，绝不回退为
   文件轮询。生产与容量测试必须使用 Linux Docker Engine。
3. 发布前应在目标 Linux 节点至少分别测量：1,000 个真实容器实例、目标 Hub 延迟/故障率、连接与文件描述符
   上限、每实例限流命中率、连续运行后的 Socket 残留与内存曲线。
4. 指标入口 `GET /internal/v1/metrics/uds` 仅对内部服务凭据开放，供告警采集聚合的会话、连接、请求、失败和
   错误率；它不暴露 Socket 路径、Token、插件或租户标识。

## 复现命令

```sh
cd PrivateCloudDisk-plugin-runtime-service
go test ./internal/uds
go test -race ./internal/uds
go test -run '^$' -bench BenchmarkSocketParallelRoundTrip -benchtime=1s ./internal/uds
go test -run '^$' -bench BenchmarkSessionLifecycleOneThousand -benchtime=1x ./internal/uds
```
