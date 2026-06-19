# 系统架构设计文档

## 1. 架构概览

PrivateCloudDisk 采用 **微服务 + 前后端分离** 架构，支持多客户端接入（Web、桌面、移动端），后端通过 API 网关统一鉴权和路由分发。

```
┌──────────────────────────────────────────────────────────────┐
│                      客户端层 (Multi-Client)                   │
│  Web (Vue 3) │ 管理后台 (React) │ 桌面端 (Electron)             │
│  Android (Kotlin) │ iOS (SwiftUI) │ macOS (SwiftUI)            │
│  Windows (WPF) │ 小程序 (uni-app) │ H5 (uni-app)               │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTPS
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                   Nginx 反向代理 (TLS 终止)                     │
│              静态资源托管  │  Gzip  │  Rate Limiting             │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│              Gateway Service (Spring Cloud Gateway)           │
│              :8080  │  JWT 鉴权  │  路由分发  │  限流             │
└──────┬──────────────────────────────────┬────────────────────┘
       │ /api/v1/business/*               │ /api/v1/files/*
       ▼                                  ▼
┌──────────────────────┐    ┌──────────────────────────────────┐
│  Platform Service    │    │  File Service (FastAPI)           │
│  Spring Boot         │    │  :8000                           │
│  :8081               │    │  分片上传  │  流式下载  │  缩略图    │
│  用户/文件/目录/配额    │    │  操作凭证  │  全文检索              │
└──────┬───────────────┘    └──────────────┬───────────────────┘
       │                                   │
       │         ┌─────────────────────────┤
       │         │                         │
       ▼         ▼                         ▼
┌──────────────────────────────────────────────────────────────┐
│                       中间件层                                 │
│  MySQL 8.0 │ Redis 7 │ RabbitMQ │ MinIO │ OpenSearch          │
│  Nacos │ Sentinel │ Seata │ Canal │ SkyWalking               │
└──────────────────────────────────────────────────────────────┘
       │                                   │
       ▼                                   ▼
┌──────────────────────┐    ┌──────────────────────────────────┐
│  IM Service          │    │  File Worker (Python)             │
│  Spring Boot + Netty │    │  病毒扫描  │  转码  │  内容提取      │
│  消息推送/音视频通话   │    │  缩略图  │  全文索引               │
└──────────────────────┘    └──────────────────────────────────┘
```

## 2. 微服务拓扑

### 2.1 服务清单

| 服务 | 技术栈 | 端口 | 职责 |
|------|--------|------|------|
| **gateway-service** | Spring Cloud Gateway + WebFlux | 8080 | 统一入口、JWT 鉴权、路由分发、限流 |
| **platform-service** | Spring Boot 4.0.6 + MyBatis | 8081 | 核心业务：用户/文件/目录树/配额/收藏/回收站 |
| **shortage-service** | FastAPI + Uvicorn (Python) | 8000 | 文件 I/O：分片上传/流式下载/缩略图/凭证 |
| **im-platform** | Spring Boot + MyBatis | - | IM 业务：消息/会话/群组管理 |
| **im-server** | Netty WebSocket | - | 长连接推送：万级并发、多端登录 |
| **file-worker** | Python (RabbitMQ Consumer) | - | 异步任务：病毒扫描/转码/缩略图/内容提取 |

### 2.2 服务间通信

| 通信方式 | 适用场景 | 示例 |
|----------|----------|------|
| HTTP REST | 同步调用 | Gateway → Platform Service |
| 内部 API (X-Internal) | 服务间内部调用 | Platform → File Service |
| RabbitMQ | 异步消息 | 文件处理任务、欢迎邮件 |
| WebSocket | 实时推送 | IM 消息推送 |
| WebRTC | 音视频通话 | P2P 音视频传输 |

### 2.3 中间件依赖

| 中间件 | 用途 | 消费方 |
|--------|------|--------|
| MySQL 8.0 | 业务数据存储 | platform-service, im-platform |
| Redis 7 | 缓存/限流/会话/分布式锁 | gateway-service, platform-service, shortage-service |
| RabbitMQ | 异步任务/事件驱动 | platform-service, shortage-service, im-service |
| MinIO | 对象存储 (S3) | shortage-service |
| OpenSearch | 全文检索 | shortage-service (索引), platform-service (查询) |
| Nacos | 服务发现/配置中心 | gateway-service, platform-service |
| Sentinel | 流量控制/熔断 | gateway-service, platform-service |
| Seata | 分布式事务 | platform-service |

## 3. 技术选型

### 3.1 后端技术栈

| 技术 | 选型 | 理由 |
|------|------|------|
| 微服务框架 | Spring Boot 4.0.6 + Spring Cloud | 生态成熟，社区活跃 |
| API 网关 | Spring Cloud Gateway (WebFlux) | 响应式非阻塞，高性能 |
| ORM | MyBatis 3.0.4 | SQL 可控，复杂查询灵活 |
| 文件处理 | FastAPI + Python 3.11 | 异步 I/O 性能优异，文件处理生态好 |
| 实时通信 | Netty 4.1 + WebSocket | 高性能 NIO，万级并发 |
| 认证 | JWT (RSA-256) | 无状态，适合微服务 |
| 密码加密 | PBKDF2-SHA256 + BCrypt | 双层哈希，防彩虹表 |

### 3.2 前端技术栈

| 客户端 | 技术 | 理由 |
|--------|------|------|
| Web 用户端 | Vue 3 + Vite + Tailwind CSS | 开发效率高，生态丰富 |
| Web 管理端 | React 19 + Ant Design 6 | 后台管理场景成熟方案 |
| 桌面端 | Electron + React | 跨平台，虚拟磁盘集成 |
| 跨端移动端 | uni-app (Vue 3) | 一套代码多端部署 |
| iOS 原生 | SwiftUI | Apple 生态原生体验 |
| Android 原生 | Kotlin + Jetpack Compose | Google 推荐现代方案 |
| macOS 原生 | SwiftUI + AppKit | 深度系统集成 |
| Windows 原生 | WPF + .NET 8.0 | Windows 生态最佳实践 |

## 4. 部署架构

### 4.1 Docker Compose 部署

所有服务通过 Docker Compose 统一编排，支持一键部署：

```bash
docker compose up -d
```

### 4.2 容器网络

| 网络 | 用途 | 暴露 |
|------|------|------|
| `frontend-net` | 前端服务 | 对外暴露 80/443 |
| `backend-net` | 后端服务 | 内部通信 |

### 4.3 数据卷

| 数据卷 | 用途 |
|--------|------|
| `mysql-data` | MySQL 数据持久化 |
| `redis-data` | Redis 持久化 |
| `rabbitmq-data` | RabbitMQ 数据 |
| `uploads-data` | 用户上传文件 |
| `thumbnails-data` | 缩略图缓存 |
| `minio-data` | 对象存储数据 |
| `opensearch-data` | 全文索引数据 |

## 5. 高可用设计

### 5.1 网关层

- Nginx 反向代理 + 多实例部署
- 健康检查自动摘除故障节点

### 5.2 服务层

- 无状态设计，支持水平扩展
- Sentinel 熔断降级，防止雪崩
- Nacos 服务发现，动态路由

### 5.3 数据层

- MySQL 主从复制 + 读写分离
- Redis 哨兵/集群模式
- RabbitMQ 镜像队列
- MinIO 纠删码模式