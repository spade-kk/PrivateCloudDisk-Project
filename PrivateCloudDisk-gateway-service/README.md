# PrivateCloudDisk-gateway-service

企业级 API 网关服务，基于 Spring Cloud Gateway (WebFlux) 构建，是整个私有云盘系统的统一入口，负责请求路由、JWT 认证鉴权、分布式限流和请求日志记录。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.6 | 应用框架 (WebFlux) |
| Spring Cloud Gateway | 2025.1.1 | API 网关 (响应式) |
| Spring Security | 6.x (WebFlux) | 安全框架 |
| Spring Data Redis Reactive | - | 响应式 Redis (限流计数) |
| JJWT | 0.13.0 | JWT 令牌解析与验证 |
| Lombok | - | 代码简化 |

---

## 项目结构

```
src/main/java/org/project/privateclouddiskgatewayservice/
├── PrivateCloudDiskGatewayServiceApplication.java  # 启动类
│
├── config/                               # 配置类
│   ├── SecurityConfig.java              # Spring Security WebFlux 配置 (放行网关)
│   ├── CorsConfig.java                  # CORS 跨域配置
│   └── properties/
│       └── GatewayRateLimitProperties.java # 网关限流规则属性类
│
├── filter/                               # 全局过滤器 (GlobalFilter)
│   └── global/
│       ├── AuthGlobalFilter.java        # JWT 认证过滤器
│       ├── GatewayRateLimitFilter.java  # 分布式限流过滤器 (固定窗口算法)
│       └── RequestLoggingFilter.java    # 请求日志过滤器
│
├── handler/                              # 异常处理器
│   ├── GlobalExceptionHandler.java      # 全局异常处理
│   └── AccessDeniedHandler.java         # 访问拒绝处理
│
├── dto/
│   └── ApiErrorResponse.java            # 统一错误响应体
│
└── utils/
    └── JwtUtil.java                     # JWT 解析工具 (验证签名/提取用户ID)
```

---

## 网关过滤器执行链全流程

```mermaid
sequenceDiagram
    actor C as 🌐 Client
    participant GW as 🚪 Gateway
    participant Redis as 📦 Redis
    participant BS as ⚙ Business :8081
    participant FS as 📁 File :8000

    C->>GW: HTTP Request

    Note over GW: === Filter 1: RequestLoggingFilter ===
    GW->>GW: 记录 startTime
    GW->>GW: 提取 method, path, clientIp

    Note over GW: === Filter 2: GatewayRateLimitFilter ===
    GW->>GW: 匹配限流规则 (path + method)
    alt 匹配到限流规则
        GW->>GW: 构造 Redis Key<br/>pcd:gateway:rate-limit:{rule}:{SHA256(ip)}
        GW->>Redis: EVAL Lua 脚本 (INCR + TTL)
        Redis-->>GW: count

        alt count > limit
            GW-->>C: 429 Too Many Requests<br/>{ "code": 429, "message": "请求过于频繁" }
        end
    end

    Note over GW: === Filter 3: AuthGlobalFilter ===
    GW->>GW: 提取请求 path + method
    GW->>GW: 检查白名单匹配

    alt 在白名单中
        GW->>GW: 直接放行
    else 需要认证
        GW->>GW: 提取 Authorization: Bearer <token>
        alt 缺少 Token
            GW-->>C: 401 { "code": 401, "message": "缺少认证令牌" }
        end

        GW->>GW: RSA 公钥验证 JWT 签名
        GW->>GW: 检查 JWT 过期时间

        alt JWT 无效或过期
            GW->>GW: 解析 Token 失败
            GW-->>C: 401 { "code": 401, "message": "令牌无效或已过期" }
        end

        GW->>GW: 解析出 user_id (sub claim)
        GW->>GW: 注入 X-User-Id 请求头
        GW->>GW: 剥离客户端伪造的内部头:<br/>X-User-Id, X-Internal, X-Forwarded-*
    end

    Note over GW: === 路由转发 ===
    alt path 匹配 /api/v1/business/**
        GW->>BS: 转发 (StripPrefix=2)
        BS-->>GW: Response
    else path 匹配 /api/v1/files/**
        GW->>FS: 转发 (StripPrefix=2)
        FS-->>GW: Response
    end

    Note over GW: === Filter 1: RequestLoggingFilter (Response) ===
    GW->>GW: 计算耗时 = now - startTime
    GW->>GW: 记录日志: [method] [path] [status] [duration]ms

    GW-->>C: HTTP Response
```

---

## 认证白名单

以下路径 **跳过 JWT 认证**，直接放行：

| 路径 | HTTP 方法 | 说明 |
|------|-----------|------|
| `/api/v1/business/users/login` | POST | 用户登录 |
| `/api/v1/business/users/` | POST | 用户注册 |
| `/api/v1/business/users/email/verification-code` | POST | 获取邮箱验证码 |
| `/api/v1/business/internal/**` | * | 内部服务间通信 |

> **安全约束**：白名单限定 HTTP Method。例如 `/api/v1/business/users/login` 仅允许 POST，GET 请求仍需要认证。

---

## 限流算法详解

### 固定窗口计数器 (Fixed Window Counter)

```mermaid
graph LR
    subgraph "时间窗口 (60s)"
        T0["t0<br/>窗口开始"] --> T1["t0+60s<br/>窗口结束"]
    end

    subgraph "请求计数"
        R1["请求1<br/>INCR key → 1 ✓"]
        R2["请求2<br/>INCR key → 2 ✓"]
        RDot["..."]
        R30["请求30<br/>INCR key → 30 ✓"]
        R31["请求31<br/>INCR key → 31 ✗"]
    end

    style R1 fill:#c8e6c9
    style R2 fill:#c8e6c9
    style R30 fill:#c8e6c9
    style R31 fill:#ffcdd2
```

**Redis Lua 原子脚本：**

```lua
-- KEYS[1]: 限流 key (如 pcd:gateway:rate-limit:public-login-ip:SHA256_HASH)
-- ARGV[1]: limit 值
-- ARGV[2]: window 秒数
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end
if current > tonumber(ARGV[1]) then
    return 0  -- 限流触发
end
return 1  -- 允许通过
```

**Redis Key 结构：**

```
pcd:gateway:rate-limit:{ruleName}:{SHA256(identity)}

示例:
pcd:gateway:rate-limit:public-login-ip:3a2b1c...
pcd:gateway:rate-limit:upload-session-user:e5f6a7...
```

### 限流规则一览

| 规则名称 | 匹配路径 | 方法 | 维度 | 限制 | 窗口 | 业务含义 |
|----------|----------|------|------|------|------|----------|
| `public-login-ip` | `/**/users/login` | POST | IP | 30 | 60s | 防暴力破解 |
| `public-register-ip` | `/**/users/` | POST | IP | 10 | 1h | 防批量注册 |
| `upload-session-user` | `/**/uploads/` | POST | USER | 20 | 60s | 防止传会话创建 |
| `operation-token-issue-user` | `/**/operation-tokens` | POST | USER | 30 | 60s | 防凭证签发滥用 |
| `operation-token-issue-ip` | `/**/operation-tokens` | POST | IP | 120 | 60s | 防凭证签发滥用 |
| `operation-token-destroy-user` | `/**/operation-tokens` | DELETE | USER | 60 | 60s | 防凭证撤销滥用 |
| `operation-token-destroy-ip` | `/**/operation-tokens` | DELETE | IP | 180 | 60s | 防凭证撤销滥用 |

**Fail-Open 机制**：当 Redis 不可用时，`gateway.rate-limit.fail-open=true` 允许请求正常通过，保证系统可用性。

---

## JWT 认证流程

```mermaid
flowchart TD
    A[请求到达 AuthGlobalFilter] --> B{路径在白名单?}
    B -->|是| C[直接放行]
    B -->|否| D{Authorization 头存在?}
    D -->|否| E[返回 401]
    D -->|是| F{格式为 Bearer xxx?}
    F -->|否| E
    F -->|是| G[RSA 公钥验证签名]
    G --> H{签名有效?}
    H -->|否| I[返回 401 令牌无效]
    H -->|是| J{令牌未过期?}
    J -->|否| K[返回 401 令牌过期]
    J -->|是| L[解析 user_id]
    L --> M[注入 X-User-Id 头]
    M --> N[剥离客户端伪造头]
    N --> C

    style C fill:#c8e6c9
    style E fill:#ffcdd2
    style I fill:#ffcdd2
    style K fill:#ffcdd2
```

**请求头安全清洗：**

网关层会剥离以下客户端发送的请求头，防止身份伪造：

```
被剥离的请求头:
X-User-Id          ← 防止伪造用户ID
X-Internal         ← 防止伪造内部调用标识
X-Forwarded-For    ← 防止伪造来源IP
X-Forwarded-Proto  ← 防止伪造协议
```

---

## 路由配置

```mermaid
graph LR
    C[Client] -->|"GET /api/v1/business/users/me"| GW[Gateway :8080]

    GW -->|"StripPrefix=2<br/>转发 /business/users/me"| BS["Business :8081<br/>/business/**"]

    GW -->|"StripPrefix=2<br/>转发 /files/operation-tokens"| FS["File :8000<br/>/files/**"]

    style GW fill:#fff3e0
    style BS fill:#e8f5e9
    style FS fill:#fce4ec
```

| 路由 ID | 匹配路径 | 转发目标 | StripPrefix | 说明 |
|----------|----------|----------|-------------|------|
| `business-service` | `/api/v1/business/**` | `http://localhost:8081` | 2 | 业务服务 |
| `file-service` | `/api/v1/files/**` | `http://localhost:8000` | 2 | 文件服务 |

**StripPrefix 说明**：

```
/api/v1/business/users/me  → (StripPrefix=2)  → /business/users/me
 ^^^^ ^^^                          去掉这2段        转发到 :8081 的路径
```

---

## 错误响应格式

### 认证错误

```json
// 401 - 缺少令牌
{
  "code": 401,
  "message": "缺少认证令牌",
  "timestamp": "2026-06-11T12:00:00"
}

// 401 - 令牌无效
{
  "code": 401,
  "message": "令牌无效或已过期",
  "timestamp": "2026-06-11T12:00:00"
}
```

### 限流错误

```json
// 429 - 请求频率过高
{
  "code": 429,
  "message": "请求过于频繁，请稍后再试",
  "timestamp": "2026-06-11T12:00:00"
}
```

### 路由错误

```json
// 503 - 服务不可用
{
  "code": 503,
  "message": "服务暂时不可用",
  "timestamp": "2026-06-11T12:00:00"
}
```

---

## 请求生命周期总览

```mermaid
sequenceDiagram
    participant Client as 🌐 Client
    participant Netty as Netty Server
    participant Logging as RequestLoggingFilter
    participant RateLimit as RateLimitFilter
    participant Auth as AuthGlobalFilter
    participant Route as RouteLocator
    participant Target as Target Service

    Client->>Netty: HTTP Request
    Netty->>Logging: pre-filter (order: -100)
    Logging->>RateLimit: pre-filter (order: -90)
    RateLimit->>Redis: 检查限流
    RateLimit->>Auth: pre-filter (order: -80)
    Auth->>Auth: JWT 验证 + 请求头清洗
    Auth->>Route: 路由匹配
    Route->>Route: StripPrefix + 路径重写
    Route->>Target: 代理转发
    Target-->>Route: Response
    Route-->>Auth: post-filter
    Auth-->>RateLimit: post-filter
    RateLimit-->>Logging: post-filter
    Logging->>Logging: 计算耗时 (duration_ms)
    Logging-->>Netty: Response
    Netty-->>Client: HTTP Response
```

---

## 核心架构图

### 请求路由选择全流程

```mermaid
flowchart TD
    Start([客户端发起请求]) --> Parse[解析 URL Path + Method]

    Parse --> RateLimit{限流规则匹配}

    RateLimit -->|匹配 rule_x| RateKey[构造 Redis Key<br/>pcd:gateway:rate-limit:rule_x:SHA256_IP_or_User]
    RateLimit -->|无匹配规则| Auth

    RateKey --> RedisExec[EVAL Lua 原子脚本<br/>INCR + EXPIRE]
    RedisExec --> CheckCount{count ≤ limit?}

    CheckCount -->|否| RateLimit429[🚫 429 Too Many Requests<br/>Retry-After: window_seconds]
    CheckCount -->|是| Auth

    Auth[进入 AuthGlobalFilter] --> WhiteList{路径 + 方法<br/>在白名单中?}

    WhiteList -->|是| Forward
    WhiteList -->|否| ExtractToken{Authorization<br/>Bearer token?}

    ExtractToken -->|缺失| Auth401[🚫 401 缺少认证令牌]
    ExtractToken -->|存在| VerifyJWT[RSA 公钥<br/>验证 JWT 签名]

    VerifyJWT --> SigValid{签名有效?}
    SigValid -->|否| Auth401_2[🚫 401 令牌无效或已过期]
    SigValid -->|是| CheckExp{令牌过期?}
    CheckExp -->|是| Auth401_2
    CheckExp -->|否| ParseSub[解析 sub claim<br/>→ user_id]

    ParseSub --> InjectHeader[注入 X-User-Id 请求头]
    InjectHeader --> CleanHeaders[剥离客户端伪造的头:<br/>X-User-Id, X-Internal,<br/>X-Forwarded-*]

    CleanHeaders --> Forward

    Forward[路由匹配] --> MatchPath{Path 前缀?}

    MatchPath -->|/api/v1/business/**| StripB[StripPrefix=2<br/>去 /api/v1]
    MatchPath -->|/api/v1/files/**| StripF[StripPrefix=2<br/>去 /api/v1]
    MatchPath -->|其他| NoRoute[🚫 404 No Route Found]

    StripB --> TargetB[转发 → Business Service<br/>http://localhost:8081]
    StripF --> TargetF[转发 → File Service<br/>http://localhost:8000]

    TargetB --> LB[WebClient 发起<br/>响应式 HTTP 调用]
    TargetF --> LB

    LB --> Response{后端响应?}
    Response -->|200 OK| Log[RequestLoggingFilter<br/>记录耗时: duration_ms]
    Response -->|连接超时| GW503[🤖 503 服务不可用]
    Response -->|500| GW500[🤖 500 服务器内部错误]

    Log --> Return([返回给客户端])

    style Return fill:#c8e6c9
    style RateLimit429 fill:#ffcdd2
    style Auth401 fill:#ffcdd2
    style Auth401_2 fill:#ffcdd2
    style NoRoute fill:#ffcdd2
    style GW503 fill:#ffcdd2
    style GW500 fill:#ffcdd2
```

### 异常处理全流程

```mermaid
flowchart TD
    subgraph "过滤器链异常"
        E1["限流检查失败<br/>Redis 连接超时"]
        E2["JWT 验证异常<br/>签名无效/格式错误"]
        E3["路由匹配失败<br/>无匹配路由规则"]
        E4["后端服务超时<br/>ReadTimeout / ConnectTimeout"]
        E5["后端服务错误<br/>HTTP 500"]
    end

    subgraph "GlobalExceptionHandler 处理"
        H1{异常类型判断}

        H1 -->|RateLimitException| HR1[构造 429 响应<br/>code: 429, message: '请求过于频繁'<br/>Retry-After 头]
        H1 -->|AuthenticationException| HR2[构造 401 响应<br/>code: 401, message: '令牌无效'<br/>WWW-Authenticate: Bearer]
        H1 -->|AccessDeniedException| HR3[构造 403 响应<br/>code: 403, message: '无权限操作']
        H1 -->|NotFoundException| HR4[构造 404 响应<br/>code: 404, message: '资源不存在']
        H1 -->|ConnectTimeoutException| HR5[构造 503 响应<br/>code: 503, message: '服务不可用']
        H1 -->|ReadTimeoutException| HR5
        H1 -->|UnknownException| HR6[构造 500 响应<br/>code: 500, message: '服务器内部错误']
    end

    E1 --> H1
    E2 --> H1
    E3 --> H1
    E4 --> H1
    E5 --> H1

    HR1 --> Return
    HR2 --> Return
    HR3 --> Return
    HR4 --> Return
    HR5 --> Return
    HR6 --> Return

    Return([统一 JSON 错误响应<br/>{code, message, timestamp}])

    style Return fill:#fff3e0

    subgraph "Fail-Open 机制"
        FO["⚠ Redis 不可用时<br/>gateway.rate-limit.fail-open=true<br/>→ 跳过限流检查，允许请求通过<br/>保证系统可用性"]
    end

    E1 -.->|fail-open=true| FO

    style FO fill:#e3f2fd
```

### Netty 线程模型

```mermaid
graph TB
    subgraph "Netty Reactor 线程模型"
        subgraph "Boss Group (1线程)"
            Boss["Boss EventLoop<br/>NioEventLoopGroup(1)"]
            Boss -->|"accept() 新连接"| Register["注册到 Worker<br/>EventLoop"]
        end

        subgraph "Worker Group (CPU核数×2)"
            Worker1["Worker-1<br/>EventLoop"]
            Worker2["Worker-2<br/>EventLoop"]
            WorkerN["Worker-N<br/>EventLoop"]

            Worker1 -->|"读取请求"| Decode["HTTP 解码"]
            Worker1 -->|"写入响应"| Encode["HTTP 编码"]

            Worker2 -->|"读取请求"| Decode2["HTTP 解码"]
            Worker2 -->|"写入响应"| Encode2["HTTP 编码"]
        end

        subgraph "Spring WebFlux 异步非阻塞"
            Filter1["GatewayFilter Chain<br/>pre-filter → route → post-filter"]
            Filter1 --> WC["WebClient (Reactor Netty)<br/>响应式 HTTP 客户端"]

            WC -->|"event-driven"| Backend["后端服务<br/>Business :8081 / File :8000"]
        end

        Register --> Worker1
        Register --> Worker2
        Register --> WorkerN

        Decode --> Filter1
        Filter1 --> Encode
    end

    subgraph "线程绑定"
        T1["✅ 每个连接绑定到一个 Worker EventLoop<br/>保证线程安全，无需 synchronized"]
        T2["✅ 所有 I/O 操作异步非阻塞<br/>不会阻塞 EventLoop 线程"]
        T3["✅ WebClient 使用 Reactor Netty<br/>共享 EventLoop Group，零上下文切换"]
    end

    style Boss fill:#e3f2fd
    style Worker1 fill:#e8f5e9
    style Worker2 fill:#e8f5e9
    style WorkerN fill:#e8f5e9
    style WC fill:#fff3e0
    style Backend fill:#fce4ec
```

### 请求并发处理模型

```mermaid
sequenceDiagram
    participant C1 as Client 1
    participant C2 as Client 2
    participant C3 as Client 3
    participant Boss as Boss EventLoop
    participant W1 as Worker-1
    participant W2 as Worker-2
    participant WC as WebClient
    participant BS as Business

    C1->>Boss: TCP SYN (连接建立)
    C2->>Boss: TCP SYN
    C3->>Boss: TCP SYN

    Boss->>W1: register Client 1
    Boss->>W2: register Client 2
    Boss->>W1: register Client 3

    Note over W1,W2: 连接均匀分配到 Worker 线程

    C1->>W1: HTTP Request 1 (JWT 验证)
    W1->>W1: AuthGlobalFilter<br/>RSA 签名验证

    C2->>W2: HTTP Request 2 (限流检查)
    W2->>W2: RateLimitFilter<br/>Redis EVAL Lua

    C3->>W1: HTTP Request 3 (路由转发)

    W1->>WC: 异步代理 → Business Service
    W2->>WC: 异步代理 → Business Service
    W1->>WC: 异步代理 → Business Service

    Note over WC,BS: 🔄 3个并发请求同时转发<br/>非阻塞 I/O → 不占线程

    BS-->>WC: Response 1
    WC-->>W1: onComplete callback
    W1-->>C1: HTTP Response 200

    BS-->>WC: Response 2
    WC-->>W2: onComplete callback
    W2-->>C2: HTTP Response 200

    BS-->>WC: Response 3
    WC-->>W1: onComplete callback
    W1-->>C3: HTTP Response 200

    Note over C1,BS: 💡 WebFlux 响应式模型下<br/>3个并发请求无需 3 个线程<br/>2 个 Worker 线程即可并发处理
```

---

## 配置说明

核心配置参见 `application.properties`：

```properties
server.port=8080

# 路由 - 业务服务
spring.cloud.gateway.server.webflux.routes[0].id=business-service
spring.cloud.gateway.server.webflux.routes[0].uri=http://localhost:8081
spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/v1/business/**
spring.cloud.gateway.server.webflux.routes[0].filters[0]=StripPrefix=2

# 路由 - 文件服务
spring.cloud.gateway.server.webflux.routes[1].id=file-service
spring.cloud.gateway.server.webflux.routes[1].uri=http://localhost:8000
spring.cloud.gateway.server.webflux.routes[1].predicates[0]=Path=/api/v1/files/**
spring.cloud.gateway.server.webflux.routes[1].filters[0]=StripPrefix=2

# JWT 公钥
jwt.public-key-path=classpath:keys/public.pem

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# 限流
gateway.rate-limit.enabled=true
gateway.rate-limit.fail-open=true
```

---

## 开发指南

### 环境要求
- JDK 18+
- Redis

### 启动服务
```bash
./gradlew bootRun
```

### 构建 JAR
```bash
./gradlew bootJar
```

### Docker 部署
```bash
docker build -t privateclouddisk-gateway .
docker run -p 8080:8080 privateclouddisk-gateway
```