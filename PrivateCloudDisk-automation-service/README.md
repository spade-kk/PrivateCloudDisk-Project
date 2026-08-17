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

## 快速开始

    ./gradlew test
    ./gradlew bootRun

在根目录 Compose 中使用 automation profile 启用，具体依赖和环境变量以 src/main/resources/application.yml 为准。
