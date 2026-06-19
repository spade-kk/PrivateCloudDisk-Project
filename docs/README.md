# PrivateCloudDisk 项目文档

企业级私有云盘系统完整文档，涵盖架构设计、安全方案、数据库设计、API 接口、开发指南和部署运维。

---

## 文档导航

| 文档 | 说明 |
|------|------|
| [架构设计文档](./architecture.md) | 系统整体架构、微服务拓扑、技术选型、部署架构 |
| [安全设计文档](./security.md) | 认证鉴权、密码加密、限流防护、数据安全 |
| [数据库设计文档](./database.md) | 表结构设计、闭包表、索引策略、主键策略 |
| [API 接口概览](./api-overview.md) | 接口规范、认证方式、各模块 API 一览 |
| [密码加密流程](./password-encryption.md) | 双层哈希详解、前端 PBKDF2、后端 BCrypt |
| [开发指南](./development.md) | 环境搭建、本地开发、调试技巧、编码规范 |

---

## 项目快速导航

```
docs/
├── README.md                 # 文档索引 (本文件)
├── architecture.md           # 系统架构设计
├── security.md               # 安全设计文档
├── database.md               # 数据库设计
├── api-overview.md           # API 接口概览
├── password-encryption.md    # 密码加密流程详解
└── development.md            # 开发指南
```

---

## 相关文档

- [根目录 README](../README.md) — 项目总览
- [部署文档](../DEPLOYMENT.md) — 生产环境部署指南
- [各子项目 README](../README.md#各子项目导航) — 各模块详细文档