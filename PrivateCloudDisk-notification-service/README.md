# PrivateCloudDisk-notification-service

通知服务。负责验证码、邮件/短信适配、系统通知和实时通知通道；具体渠道是否启用由配置决定。

## 技术栈

- Go、HTTP API
- WebSocket/通知通道（按代码与配置启用）
- 外部邮件或短信提供商（可选）

## 职责边界

- 接收通知请求并按渠道发送
- 管理验证码和通知状态
- 对外部渠道失败进行状态记录和重试边界处理
- 不负责用户、文件、空间或账单主数据

## 快速开始

    go test ./...
    go run ./server

请先配置 config/config.yaml 和对应环境变量；不要在文档或提交中写入真实密钥。
