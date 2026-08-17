# PrivateCloudDisk-workflow-service

工作流定义、能力中心、执行和市场服务。负责工作流 DSL 校验、版本与发布、能力发现、执行记录以及工作流模板市场。

## 技术栈

- Java 21（以构建配置为准）
- Spring Boot 3.4.7
- MyBatis、Flyway、MySQL
- RabbitMQ、REST API

## 职责边界

- 管理工作流定义、版本、发布和执行记录
- 校验 DSL 并维护可用能力中心
- 接收自动化或调度触发，执行插件/平台能力链
- 提供工作流市场的发现、发布和导入入口
- 不负责文件存储、插件包生命周期或 Cron 扫描

## 快速开始

    ./gradlew test
    ./gradlew bootRun

服务通过根目录 Compose 的 automation profile 参与联调。运行参数以 src/main/resources/application.yml 为准。
