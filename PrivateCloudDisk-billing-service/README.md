# PrivateCloudDisk-billing-service

可选计费与订阅服务。负责订阅、账单、支付适配和计费状态的独立边界；当前是否启用由部署配置和业务授权决定，不代表官网固定价格或公开套餐承诺。

## 技术栈

- Java 21（以构建配置为准）
- Spring Boot 3.4.7
- MyBatis、Flyway、MySQL
- 可选支付适配

## 职责边界

- 管理订阅、账单和计费状态
- 对接经授权启用的支付或结算适配
- 不负责文件、空间、插件和工作流业务
- 官网展示不引用此服务推导固定价格、配额或 SLA

## 快速开始

    ./gradlew test
    ./gradlew bootRun

是否启用以及外部支付配置以根目录 Compose 和服务配置为准。
