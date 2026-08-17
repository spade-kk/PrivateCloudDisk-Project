# PrivateCloudDisk-client-registration-service

客户端身份注册服务。负责客户端挑战、设备身份、扩展绑定、证明材料和签名校验，为 Web、桌面端、移动端和 CLI 提供可追踪的客户端身份边界。

## 技术栈

- Go、Gin、MySQL、HTTP API

## 职责边界

- 创建并校验客户端注册挑战
- 保存客户端身份、设备绑定和扩展范围
- 校验请求签名/证明材料，具体算法以代码和配置为准
- 为 Gateway、Plugin Service 等服务提供身份查询依据
- 不负责用户文件、插件包存储或工作流执行

## 快速开始

    go test ./...
    go run ./cmd/server

数据库迁移、监听地址和密钥配置以 config/config.yaml 与环境变量为准。
