# 开发指南

## 1. 环境搭建

### 1.1 基础环境要求

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 18+ | Java 开发环境 |
| Python | 3.11+ | 文件服务开发 |
| Node.js | 18+ | 前端开发 |
| MySQL | 8.0+ | 数据库 |
| Redis | 7.0+ | 缓存 |
| RabbitMQ | 3.10+ | 消息队列 |
| Docker | 24+ | 容器化部署 |

### 1.2 推荐工具

| 工具 | 用途 |
|------|------|
| IntelliJ IDEA | Java 开发 (platform-service, gateway-service, im) |
| VS Code | 前端开发 (web, admin-web, desktop) |
| PyCharm | Python 开发 (storage-service) |
| Xcode | iOS/macOS 开发 |
| Android Studio | Android 开发 |
| Visual Studio 2022 | Windows 开发 |
| HBuilderX | uni-app 开发 |
| DataGrip | 数据库管理 |
| Postman | API 调试 |
| RedisInsight | Redis 管理 |

## 2. 本地开发

### 2.1 启动中间件

```bash
# 方式一: Docker Compose 启动中间件
docker compose up -d mysql redis rabbitmq

# 方式二: 本地安装并启动
brew services start redis
brew services start rabbitmq
```

### 2.2 初始化数据库

```bash
mysql -u root -p < scripts/init_database.sql
```

### 2.3 启动后端服务

```bash
# 1. 启动平台业务服务 (:8081)
cd PrivateCloudDisk-platform-service
./gradlew bootRun

# 2. 启动文件服务 (:8000)
cd PrivateCloudDisk-storage-service
pip install -r requirements.txt
uvicorn server:app --host 0.0.0.0 --port 8000 --reload

# 3. 启动网关服务 (:8080)
cd PrivateCloudDisk-gateway-service
./gradlew bootRun

# 4. 启动 IM 服务 (可选)
cd PrivateCloudDisk-im
# 启动 im-platform
cd im-platform && mvn spring-boot:run
# 启动 im-server
cd im-server && mvn spring-boot:run
```

### 2.4 启动前端

```bash
# Web 前端 (:5173)
cd PrivateCloudDisk-web
npm install
npm run dev

# 管理后台 (:5174)
cd PrivateCloudDisk-admin-web
npm install
npm run dev

# 桌面客户端
cd PrivateCloudDisk-desktop
npm install
npm run dev

# uni-app 跨端
cd PrivateCloudDisk-uni-app
npm install
npm run dev:h5
```

### 2.5 启动原生客户端

```bash
# Android
# 用 Android Studio 打开 PrivateCloudDisk-android 目录

# iOS
# 用 Xcode 打开 PrivateCloudDisk-ios/PrivateCloudDisk-ios.xcodeproj

# macOS
# 用 Xcode 打开 PrivateCloudDisk-macos/PrivateCloudDisk-macos.xcodeproj

# Windows
# 用 Visual Studio 打开 PrivateCloudDisk-win/PrivateCloudDisk.sln
```

## 3. 项目结构速查

| 子项目 | 类型 | 构建工具 | 入口 |
|--------|------|----------|------|
| PrivateCloudDisk-web | Vue 3 前端 | npm/vite | `src/main.ts` |
| PrivateCloudDisk-admin-web | React 管理后台 | npm/vite | `src/main.tsx` |
| PrivateCloudDisk-desktop | Electron 桌面端 | npm/vite | `src/main/main.js` |
| PrivateCloudDisk-uni-app | uni-app 跨端 | npm/vite | `src/main.js` |
| PrivateCloudDisk-android | Android 原生 | Gradle | `app/` |
| PrivateCloudDisk-ios | iOS 原生 | Xcode | `PrivateCloudDisk_iosApp.swift` |
| PrivateCloudDisk-macos | macOS 原生 | Xcode | `PrivateCloudDiskApp.swift` |
| PrivateCloudDisk-win | Windows 原生 | .NET/msbuild | `App.xaml.cs` |
| PrivateCloudDisk-gateway-service | Java 网关 | Gradle | `*Application.java` |
| PrivateCloudDisk-platform-service | Java 业务 | Gradle | `*Application.java` |
| PrivateCloudDisk-storage-service | Python 文件处理服务 | pip | `app/main.py` |
| PrivateCloudDisk-im | Java IM | Maven | `im-platform/`, `im-server/` |
| PrivateCloudDisk-db | SQL 脚本 | - | `database_init.sql` |
| PrivateCloudDisk-infra | Docker 配置 | Docker | 各中间件目录 |
| scripts | 运维脚本 | Bash/Python | - |

## 4. 编码规范

### 4.1 Java

- 遵循阿里巴巴 Java 开发手册
- Controller → Service → Mapper 三层架构
- 使用 Lombok 简化代码
- 参数校验使用 Jakarta Validation (JSR-380)
- 异常统一使用 ServiceException

### 4.2 Python

- 遵循 PEP 8
- 使用 Pydantic 进行数据验证
- 异步 I/O 使用 `async/await`
- 类型注解使用 Python 3.10+ 语法

### 4.3 TypeScript/Vue/React

- 使用 Composition API (Vue 3)
- 状态管理使用 Pinia (Vue) / Zustand (React)
- API 请求封装在 `api/` 目录
- 组件遵循单一职责原则

### 4.4 Swift

- 遵循 Swift API Design Guidelines
- 使用 SwiftUI 声明式 UI
- 异步使用 `async/await` (Swift 5.5+)
- 数据流使用 `@Observable` (iOS 17+)

### 4.5 Kotlin

- 遵循 Kotlin Coding Conventions
- 使用 Jetpack Compose 声明式 UI
- 依赖注入使用 Hilt
- 数据层使用 Repository 模式

## 5. 调试技巧

### 5.1 后端调试

```bash
# 远程调试 platform-service (端口 5005)
cd PrivateCloudDisk-platform-service
./gradlew bootRun --debug-jvm
```

### 5.2 前端调试

- Vue DevTools (Chrome 扩展)
- React DevTools (Chrome 扩展)
- 浏览器开发者工具 Network 面板

### 5.3 API 调试

- 使用 Postman 或 curl 直接测试 API
- 网关服务启动后访问 Swagger UI: `http://localhost:8081/swagger-ui.html`

### 5.4 数据库调试

```bash
# 查看数据库状态
docker compose exec mysql mysql -u root -p private_cloud_disk

# 查看表结构
SHOW CREATE TABLE pcd_user_info_table;

# 查看测试数据
SELECT HEX(user_id), user_account FROM pcd_user_info_table;
```

## 6. 常见问题

### 6.1 密码登录失败

1. 确认密码是否经过前端 PBKDF2 预哈希
2. 确认数据库中的哈希格式为 `$2b$12$...`
3. 使用 `scripts/generate_admin_password.py` 重新生成密码哈希

### 6.2 文件上传失败

1. 检查 MinIO 服务是否正常运行
2. 检查操作凭证是否有效
3. 检查 Redis 操作凭证计数器

### 6.3 数据库连接失败

1. 检查 MySQL 服务状态
2. 检查 `application.properties` 中的数据库配置
3. 确认数据库 `private_cloud_disk` 已创建

### 6.4 跨域问题 (CORS)

1. 确认网关 CORS 配置
2. 检查请求头是否包含正确的 Origin
3. 开发环境使用 Vite 代理

## 7. 测试

### 7.1 测试账号

| 账号 | 密码 | 角色 |
|------|------|------|
| 管理员账户 | 由部署初始化或环境变量提供 | 超级管理员 |
| 测试用户 | 由测试 fixture 或测试环境提供 | 普通用户 |

### 7.2 运行测试

```bash
# Java 单元测试
cd PrivateCloudDisk-platform-service
./gradlew test

# Python 测试
cd PrivateCloudDisk-storage-service
python tests.py

# 前端测试
cd PrivateCloudDisk-web
npm run test
```
