# PrivateCloudDisk-plugin-runtime-service

云插件运行时服务。负责插件 manifest/包的运行前校验、Python 入口验证、沙箱进程管理、资源边界和 Broker 通信；不负责插件市场和平台业务数据。

## 技术栈

- Go 1.24
- Python 运行时校验与 SDK
- HTTP API
- 独立 sandbox 镜像、AppArmor/seccomp 配置（按部署启用）

## 职责边界

- 校验插件入口、manifest 和运行参数
- 在受控运行时启动插件进程
- 通过 Broker 暴露限定的文件与平台能力
- 返回执行状态和错误信息，避免直接暴露内部凭证
- 隔离策略、网络权限和资源限制必须结合部署配置验证

## 快速开始

    go test ./...
    PCD_INTERNAL_SERVICE_TOKEN=test go run ./cmd/runtime

生产部署参考 deploy/ 下的 unit、sandbox 和环境变量示例。
