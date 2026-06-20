# PrivateCloudDisk-infra

企业级私有云盘基础设施配置，包含所有中间件的 Docker 镜像构建、配置文件和数据初始化脚本。通过 Docker Compose 统一编排管理。

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
| **Nacos** | latest | 8848 | 服务发现 + 配置中心 |
| **Sentinel** | latest | 8080 | 流量控制 + 熔断降级 |
| **Seata** | latest | 8091 | 分布式事务 |
| **Canal** | latest | 11111 | MySQL Binlog 同步 |
| **APISIX** | latest | 9080 | API 网关 (可选，生产环境) |
| **Elasticsearch** | 7.x | 9200 | 日志存储 (可选) |
| **Kibana** | 7.x | 5601 | 日志可视化 (可选) |
| **Logstash** | 7.x | 5044 | 日志采集 (可选) |
| **Grafana** | latest | 3000 | 监控可视化 |
| **Prometheus** | latest | 9090 | 指标采集 |
| **Loki** | latest | 3100 | 日志聚合 |
| **SkyWalking** | latest | 11800, 12800 | 分布式链路追踪 |
| **SkyWalking UI** | latest | 8080 | 链路追踪可视化 |
| **XXL-Job** | latest | 8080 | 分布式任务调度 |

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
├── nacos/
│   └── Dockerfile
├── sentinel/
│   └── Dockerfile
├── seata/
│   ├── Dockerfile
│   ├── application.yml
│   └── seata_init.sql
├── canal/
│   ├── Dockerfile
│   └── canal.properties
├── apisix/
│   ├── Dockerfile
│   ├── apisix.yaml
│   └── config.yaml
├── elasticsearch/
│   └── Dockerfile
├── kibana/
│   └── Dockerfile
├── logstash/
│   └── logstash.conf
├── grafana/
│   └── datasources.yml
├── prometheus/
│   ├── prometheus.yml
│   └── alert_rules.yml
├── loki/
│   └── loki-config.yaml
├── skywalking/
│   ├── Dockerfile
│   └── agent.config
├── skywalking-ui/
│   └── Dockerfile
├── xxl-job/
│   ├── Dockerfile
│   └── xxl_job_init.sql
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