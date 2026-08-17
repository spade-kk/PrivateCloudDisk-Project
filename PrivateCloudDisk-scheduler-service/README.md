# PrivateCloudDisk-scheduler-service

工作流定时调度服务。负责 Cron 表达式解析、调度扫描、租约/抢占、幂等触发和 Outbox 发布，不执行工作流本身。

## 技术栈

- Java 21（以构建配置为准）
- Spring Boot 3.4.7
- MyBatis、Flyway、MySQL
- RabbitMQ

## 职责边界

- 保存并扫描工作流定时计划
- 解析 Cron，使用租约避免重复触发
- 以幂等键发布调度事件
- 由 Workflow Service 消费事件并负责实际执行
- 不负责文件处理、插件包存储或用户权限业务

## 快速开始

    ./gradlew test
    ./gradlew bootRun

根目录 Compose 使用 automation profile 启用，端口与消息配置以 src/main/resources/application.yml 为准。
