# PrivateCloudDisk-plugin-service

插件管理与市场服务。负责云插件的定义、版本、包存储、清单校验、权限声明、安装记录、执行入口和插件市场；插件实际运行由 plugin-runtime-service 或本地扩展运行时承担。

## 技术栈

- Java 21（以构建配置为准）
- Spring Boot 3.4.7
- MyBatis、Flyway、MySQL
- REST API；与 client-registration-service、platform-service、plugin-runtime-service 协作

## 职责边界

- 管理插件定义、版本和发布状态
- 校验插件包与 manifest，记录权限和安装范围
- 提供云插件市场和空间级安装/执行入口
- 通过内部服务调用完成授权与运行时编排
- 不直接承担沙箱执行；运行安全由 Runtime 与部署策略负责

## 快速开始

    ./gradlew test
    ./gradlew bootRun

服务端口、数据库和内部服务地址以 src/main/resources/application.yml 与根目录 Compose 配置为准。
