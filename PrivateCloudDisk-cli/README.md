# PrivateCloudDisk-cli

PrivateCloudDisk 命令行客户端，基于 Go 构建，用于认证、文件/目录操作、搜索、上传下载、任务和同步命令。

## 当前能力

- 认证与本地配置
- 文件、目录、搜索和状态命令
- 上传、下载和传输进度
- 任务查询与同步入口
- API 地址、凭证和客户端身份按配置管理

## 技术栈

- Go、Cobra 命令行框架
- HTTP API
- 本地配置与任务存储

## 快速开始

    go test ./...
    go run .

构建使用仓库提供的 Makefile；实际可用命令以 pcd --help 和当前 API 兼容性为准。
