# PrivateCloudDisk-infra

PrivateCloudDisk 基础设施目录，提供 Docker Compose 所需的数据库、缓存、消息、对象存储、搜索和观测组件镜像与配置。服务是否启用、端口、账号和持久化策略以根目录 `docker-compose.yml` 与环境变量为准。

---

## 中间件清单

| 中间件 | 版本 | 端口 | 用途 |
|--------|------|------|------|
| **MySQL** | 8.0 | 3306 | 业务数据持久化 |
| **Redis** | 7 | 6379 | 缓存 / 限流 / 分布式锁 / 会话 |
| **RabbitMQ** | 3.x | 5672, 15672 | 异步消息队列 |
| **MinIO** | latest | 9000, 9001 | 对象存储 (S3 兼容) |
| **OpenSearch** | 2.10 | 9200 | 全文检索引擎 |
| **OpenSearch Dashboards** | 2.10 | 5601 | 可视化查询界面 |
| **Grafana** | 按配置 | 3000 | 监控可视化 |
| **Prometheus** | 按配置 | 9090 | 指标采集 |
| **SkyWalking** | 按配置 | 11800, 12800 | 分布式链路追踪 |

---

## 目录结构

```
PrivateCloudDisk-infra/
├── mysql/                              # MySQL 配置
│   ├── Dockerfile                      # MySQL 镜像构建
│   ├── init.sql                        # 初始化 SQL
│   └── my.cnf                          # MySQL 配置
├── redis/
│   ├── Dockerfile
│   └── redis.conf                      # Redis 配置
├── rabbitmq/
│   ├── Dockerfile
│   └── rabbitmq.conf                   # RabbitMQ 配置
├── minio/
│   └── Dockerfile
├── opensearch/
│   └── Dockerfile
├── opensearch-dashboards/
│   ├── Dockerfile
│   └── opensearch_dashboards.yml
├── grafana/
│   └── datasources.yml
├── prometheus/
│   ├── prometheus.yml
│   └── alert_rules.yml
├── skywalking/
│   ├── Dockerfile
│   └── agent.config
└── README.md
```

---

## 核心中间件配置

### MySQL 8.0

- **字符集**: utf8mb4
- **引擎**: InnoDB
- **初始化**: `init.sql` 自动创建数据库

### Redis 7

- **持久化**: AOF + RDB 混合
- **内存策略**: allkeys-lru
- **密码认证**: 通过环境变量 `REDIS_PASSWORD` 设置

### RabbitMQ 3

- **管理插件**: 已启用 (15672)
- **用户**: 通过环境变量配置
- **消息持久化**: 已启用

### MinIO

- **API 端口**: 9000
- **Console 端口**: 9001
- **存储**: 本地磁盘 + S3 兼容

### OpenSearch 2.10

- **全文检索**: 文件名、文件内容搜索
- **安全**: 密码认证

---

## 使用方式

基础设施通过项目根目录的 `docker-compose.yml` 统一编排，无需单独启动。

```bash
# 在项目根目录执行
docker compose up -d
```

如需单独构建某个中间件镜像：

```bash
cd PrivateCloudDisk-infra/<中间件目录>
docker build -t privateclouddisk/<中间件名称> .
```
