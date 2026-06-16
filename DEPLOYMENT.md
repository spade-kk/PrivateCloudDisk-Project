# PrivateCloudDisk - 企业级后端服务部署文档

> 面向运维工程师的企业私有云盘后端服务部署手册，涵盖 Docker Compose 一键部署、生产环境调优、安全加固和 CI/CD 自动化。

---

## 目录

- [1. 系统架构概览](#1-系统架构概览)
- [2. 环境要求](#2-环境要求)
- [3. 快速开始（Docker Compose）](#3-快速开始docker-compose)
- [4. 服务拓扑与端口规划](#4-服务拓扑与端口规划)
- [5. 中间件配置详解](#5-中间件配置详解)
- [6. 业务服务部署](#6-业务服务部署)
- [7. 生产环境最佳实践](#7-生产环境最佳实践)
- [8. 安全加固](#8-安全加固)
- [9. 监控与健康检查](#9-监控与健康检查)
- [10. 日志管理](#10-日志管理)
- [11. 备份与恢复](#11-备份与恢复)
- [12. CI/CD 自动化部署](#12-cicd-自动化部署)
- [13. 常见问题排查](#13-常见问题排查)

---

## 1. 系统架构概览

```
┌──────────────────────────────────────────────────────────────┐
│                        用户入口层                              │
│  Web 浏览器  │  Electron 桌面端  │  UniApp 移动端  │  API 客户端 │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTPS :443
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    Nginx 反向代理 (frontend)                    │
│  静态资源托管  │  SSL 终止  │  Gzip 压缩  │  rate limiting        │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│              Apache APISIX API 网关 (可选)                      │
│  路由分发  │  CORS  │  WAF (Coraza)  │  限流                     │
└──────┬──────────────────────────────────┬────────────────────┘
       │ /api/v1/*                        │
       ▼                                  ▼
┌──────────────────────┐    ┌──────────────────────────────────┐
│  Gateway Service     │    │  File Service (FastAPI)           │
│  Spring Boot 3       │    │  :8000                           │
│  :8080               │    │  文件上传/下载/缩略图/操作令牌       │
│  JWT 鉴权/路由分发     │    │  OpenSearch 全文检索               │
└──────┬───────────────┘    └──────────────┬───────────────────┘
       │                                   │
       ▼                                   │
┌──────────────────────┐                   │
│  Platform Service    │                   │
│  Spring Boot 3       │                   │
│  :8081               │                   │
│  用户/文件/配额/收藏    │                   │
│  上传会话/回收站       │                   │
└──────┬───────────────┘                   │
       │                                   │
       ├───────────────────────────────────┤
       │         消息队列 (RabbitMQ)        │
       │                                   │
       ▼                                   ▼
┌──────────────────────────────────────────────────────────────┐
│                      中间件层                                  │
│  MySQL 8.0  │  Redis 7  │  RabbitMQ 3  │  MinIO  │  OpenSearch │
└──────────────────────────────────────────────────────────────┘
       │                                 │
       ▼                                 ▼
┌──────────────────────┐    ┌──────────────────────────────────┐
│  File Worker         │    │  MinIO 对象存储                    │
│  (文件处理消费者)      │    │  :9000 (API) / :9001 (Console)    │
│  病毒扫描/转码/缩略图   │    │  文件分片/合并/存储                │
│  内容提取/全文索引      │    │                                  │
└──────────────────────┘    └──────────────────────────────────┘
```

### 服务清单

| 服务 | 技术栈 | 端口 | 说明 |
|---|---|---|---|
| **frontend** | Nginx + Vue 3 | 80, 443 | 前端静态资源 + SSL 终止 |
| **gateway-service-backend** | Spring Boot 3 + Java 18 | 8080 | API 网关，JWT 鉴权，路由分发 |
| **platform-service-backend** | Spring Boot 3 + Java 18 | 8081 | 核心业务：用户、文件、配额、上传会话 |
| **file-service-backend** | FastAPI + Python 3 | 8000 | 文件服务：上传/下载/缩略图/全文检索 |
| **file-service-worker** | Python 3 | — | 异步任务：病毒扫描、转码、内容提取 |
| **mysql** | MySQL 8.0 | 3306 | 关系型数据库 |
| **redis** | Redis 7 | 6379 | 缓存 + 会话 + 限流 |
| **rabbitmq** | RabbitMQ 3 | 5672, 15672 | 消息队列 |
| **minio** | MinIO | 9000, 9001 | 对象存储（兼容 S3） |
| **opensearch** | OpenSearch 2.10 | 9200 | 全文检索引擎 |
| **certbot** | Certbot | — | Let's Encrypt 证书自动续期 |

---

## 2. 环境要求

### 服务器最低配置

| 环境 | CPU | 内存 | 磁盘 | 说明 |
|---|---|---|---|---|
| 开发/测试 | 4 核 | 8 GB | 50 GB SSD | 仅基础功能 |
| 生产环境 | 8 核+ | 16 GB+ | 200 GB+ SSD | 含 OpenSearch、MinIO |
| 生产高可用 | 16 核+ | 32 GB+ | 500 GB+ SSD | 多副本、高并发 |

### 软件依赖

| 软件 | 最低版本 | 安装说明 |
|---|---|---|
| Docker | 24.0+ | [https://docs.docker.com/engine/install/](https://docs.docker.com/engine/install/) |
| Docker Compose | 2.20+ | 随 Docker Desktop 附带，Linux 需单独安装 |
| Git | 2.30+ | 用于拉取代码 |
| make | — | 可选，用于执行 Makefile 命令 |

### 验证安装

```bash
docker --version          # Docker version 24.0.x+
docker compose version    # Docker Compose version v2.20.x+
```

---

## 3. 快速开始（Docker Compose）

### 3.1 克隆项目

```bash
git clone <repository-url>
cd PrivateCloudDisk-project
```

### 3.2 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，修改以下关键配置:
#   MYSQL_ROOT_PASSWORD    - 数据库 root 密码
#   MINIO_ROOT_PASSWORD    - MinIO 管理员密码
#   OPENSEARCH_PASSWORD    - OpenSearch 管理员密码
#   VITE_API_BASE_URL      - 前端 API 地址
#   DOMAIN                 - 你的域名（用于 SSL 证书）
```

### 3.3 一键启动

```bash
# 构建并启动所有服务（首次约 5-10 分钟）
docker compose up -d --build

# 查看启动日志
docker compose logs -f

# 查看所有服务状态
docker compose ps
```

### 3.4 验证服务

```bash
# API 网关健康检查
curl http://localhost:8080/actuator/health

# 平台服务健康检查
curl http://localhost:8081/actuator/health

# 文件服务健康检查
curl http://localhost:8000/api/v1/health

# 前端访问
open http://localhost
```

### 3.5 数据库初始化

首次启动后，MySQL 容器会自动执行 `PrivateCloudDisk-db/database_init.sql` 初始化表结构。

```bash
# 手动初始化（如果自动初始化失败）
docker compose exec mysql mysql -u root -p123456 private_cloud_disk \
  < PrivateCloudDisk-db/database_init.sql
```

### 3.6 停止服务

```bash
# 停止所有服务（保留数据卷）
docker compose down

# 停止并删除数据卷（⚠️ 删除所有数据）
docker compose down -v
```

---

## 4. 服务拓扑与端口规划

### 端口映射

| 服务 | 容器内端口 | 宿主机端口 | 外部访问 | 说明 |
|---|---|---|---|---|
| Nginx (frontend) | 80 | 80 | ✅ | HTTP 入口 |
| Nginx (frontend) | 443 | 443 | ✅ | HTTPS 入口 |
| Gateway Service | 8080 | 8080 | ⚠️ | 仅开发环境暴露 |
| Platform Service | 8081 | 8081 | ⚠️ | 仅开发环境暴露 |
| File Service | 8000 | — | ❌ | 仅内部网络 |
| MySQL | 3306 | — | ❌ | 仅内部网络 |
| Redis | 6379 | — | ❌ | 仅内部网络 |
| RabbitMQ | 5672 | — | ❌ | 仅内部网络 |
| RabbitMQ Management | 15672 | 15672 | ⚠️ | 管理面板 |
| MinIO API | 9000 | — | ❌ | 仅内部网络 |
| MinIO Console | 9001 | 9001 | ⚠️ | 管理面板 |
| OpenSearch | 9200 | — | ❌ | 仅内部网络 |

> ⚠️ 生产环境应通过防火墙仅暴露 80/443 端口，其余通过 VPN 或跳板机访问。

### 网络拓扑

```
                    ┌──────────────┐
                    │   Internet   │
                    └──────┬───────┘
                           │ :80, :443
                    ┌──────▼───────┐
                    │   Nginx      │
                    │  frontend    │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ Gateway  │ │  File    │ │  Static  │
        │ :8080    │ │  :8000   │ │  Files   │
        └─────┬────┘ └─────┬────┘ └──────────┘
              │            │
              ▼            │
        ┌──────────┐       │
        │ Platform │       │
        │ :8081    │       │
        └─────┬────┘       │
              │            │
    ┌─────────┼────────────┼──────────┐
    │         │            │          │
    ▼         ▼            ▼          ▼
┌──────┐ ┌──────┐  ┌──────────┐ ┌──────────┐
│MySQL │ │Redis │  │ RabbitMQ │ │  MinIO   │
│:3306 │ │:6379 │  │  :5672   │ │  :9000   │
└──────┘ └──────┘  └──────────┘ └──────────┘
```

---

## 5. 中间件配置详解

### 5.1 MySQL 8.0

```yaml
# docker-compose.yml 片段
mysql:
  build: ./PrivateCloudDisk-infra/mysql
  container_name: project-mysql
  environment:
    MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-123456}
    MYSQL_DATABASE: private_cloud_disk
  volumes:
    - mysql-data:/var/lib/mysql
  command: --default-authentication-plugin=mysql_native_password
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    interval: 10s
    timeout: 5s
    retries: 5
```

**自定义配置** (`PrivateCloudDisk-infra/mysql/my.cnf`):

```ini
[mysqld]
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
max_connections=200
innodb_buffer_pool_size=512M
innodb_log_file_size=256M
slow_query_log=1
long_query_time=2
```

### 5.2 Redis 7

```yaml
redis:
  build: ./PrivateCloudDisk-infra/redis
  environment:
    # 生产环境必须设置密码
    REDIS_PASSWORD: ${REDIS_PASSWORD:-}
  volumes:
    - redis-data:/data
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 10s
    timeout: 5s
    retries: 5
```

**自定义配置** (`PrivateCloudDisk-infra/redis/redis.conf`):

```conf
maxmemory 512mb
maxmemory-policy allkeys-lru
save 900 1
save 300 10
save 60 10000
```

### 5.3 RabbitMQ 3

```yaml
rabbitmq:
  build: ./PrivateCloudDisk-infra/rabbitmq
  environment:
    RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-guest}
    RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASS:-guest}
  volumes:
    - rabbitmq-data:/var/lib/rabbitmq
  healthcheck:
    test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
    interval: 10s
    timeout: 5s
    retries: 5
```

### 5.4 MinIO 对象存储

```yaml
minio:
  build: ./PrivateCloudDisk-infra/minio
  environment:
    MINIO_ROOT_USER: ${MINIO_ROOT_USER:-minioadmin}
    MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-minioadmin}
  command: server /data --console-address ":9001"
  volumes:
    - minio-data:/data
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
    interval: 10s
    timeout: 5s
    retries: 5
```

**首次启动后创建 Bucket:**

```bash
# 进入 MinIO 容器
docker compose exec minio sh

# 配置 mc 客户端
mc alias set local http://localhost:9000 minioadmin minioadmin

# 创建文件存储桶
mc mb local/privateclouddisk-uploads
mc mb local/privateclouddisk-thumbnails
```

### 5.5 OpenSearch 2.10

```yaml
opensearch:
  build: ./PrivateCloudDisk-infra/opensearch
  environment:
    - discovery.type=single-node
    - OPENSEARCH_INITIAL_ADMIN_PASSWORD=${OPENSEARCH_PASSWORD:-MySecureP@ssw0rd}
    - bootstrap.memory_lock=true
    - "OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m"
  ulimits:
    memlock:
      soft: -1
      hard: -1
  volumes:
    - opensearch-data:/usr/share/opensearch/data
```

**生产环境调优:**

```bash
# 增加 vm.max_map_count（宿主机执行）
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
```

---

## 6. 业务服务部署

### 6.1 Gateway Service (Spring Boot)

```yaml
gateway-service-backend:
  build: ./PrivateCloudDisk-gateway-service
  container_name: backend-gateway-service
  ports:
    - "8080:8080"
  environment:
    BUSINESS_SERVICE_URL: http://platform-service-backend:8081
    FILE_SERVICE_URL: http://file-service-backend:8000
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATA_REDIS_HOST: redis
```

**关键配置** (`application-docker.properties`):

```properties
# 网关路由配置
spring.cloud.gateway.routes[0].id=platform-service
spring.cloud.gateway.routes[0].uri=${BUSINESS_SERVICE_URL}
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/v1/**

# JWT 公钥路径
jwt.public-key-path=classpath:keys/public_key.pem

# Redis 连接
spring.data.redis.host=${SPRING_DATA_REDIS_HOST:localhost}
spring.data.redis.port=6379
```

### 6.2 Platform Service (Spring Boot)

```yaml
platform-service-backend:
  build: ./PrivateCloudDisk-platform-service
  container_name: backend-platform-service
  ports:
    - "8081:8081"
  depends_on:
    mysql:
      condition: service_healthy
    redis:
      condition: service_healthy
    rabbitmq:
      condition: service_healthy
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/private_cloud_disk?useSSL=false&allowPublicKeyRetrieval=true
    SPRING_DATASOURCE_USERNAME: root
    SPRING_DATASOURCE_PASSWORD: ${MYSQL_ROOT_PASSWORD:-123456}
    SPRING_DATA_REDIS_HOST: redis
    SPRING_RABBITMQ_HOST: rabbitmq
```

**生产环境 JVM 参数:**

```yaml
environment:
  JAVA_OPTS: "-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### 6.3 File Service (FastAPI)

```yaml
file-service-backend:
  build: ./PrivateCloudDisk-shortage-service
  container_name: backend-file-service
  expose:
    - "8000"
  environment:
    ENABLE_DOCS: ${ENABLE_DOCS:-false}
    FILE_UPLOAD_DIR: /data/uploads
    REDIS_URL: redis://redis:6379/0
    BUSINESS_SERVICE_URL: http://platform-service-backend:8081
    RABBITMQ_HOST: rabbitmq
    OPENSEARCH_HOST: https://opensearch:9200
    OPENSEARCH_USERNAME: admin
    OPENSEARCH_PASSWORD: ${OPENSEARCH_PASSWORD:-MySecureP@ssw0rd}
    MAX_CONCURRENT: 3
    OPERATION_TOKEN_EXPIRE_SECONDS: 3600
    MAX_REQUESTS_PER_OPERATION_TOKEN: 300
    RATE_PER_SEC: 10
  volumes:
    - uploads-data:/data/uploads
```

**Uvicorn 生产配置:**

```bash
# 生产环境推荐使用 gunicorn + uvicorn workers
pip install gunicorn
gunicorn app.main:app \
  --workers 4 \
  --worker-class uvicorn.workers.UvicornWorker \
  --bind 0.0.0.0:8000 \
  --timeout 120 \
  --access-logfile -
```

### 6.4 File Worker (异步消费者)

```yaml
file-service-worker:
  build: ./PrivateCloudDisk-shortage-service
  container_name: backend-file-worker
  command: python worker.py
  environment:
    RABBITMQ_HOST: rabbitmq
    RABBITMQ_PORT: 5672
    FILE_UPLOAD_DIR: /data/uploads
    REDIS_URL: redis://redis:6379/0
    OPENSEARCH_HOST: https://opensearch:9200
    MAX_CONCURRENT: 3
  volumes:
    - uploads-data:/data/uploads
```

### 6.5 Frontend (Nginx + Vue)

```yaml
frontend:
  build:
    context: ./PrivateCloudDisk-web
    args:
      VITE_API_BASE_URL: /api/v1
  ports:
    - "80:80"
    - "443:443"
  volumes:
    - ./certbot/www:/var/www/certbot
    - ./certbot/conf:/etc/letsencrypt
```

---

## 7. 生产环境最佳实践

### 7.1 资源限制

在 `docker-compose.yml` 中为每个服务设置资源限制：

```yaml
services:
  platform-service-backend:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '0.5'
          memory: 512M

  mysql:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
```

### 7.2 多副本部署

```bash
# 水平扩展无状态服务
docker compose up -d --scale platform-service-backend=3
docker compose up -d --scale file-service-backend=2
```

### 7.3 使用外部数据库（生产强烈推荐）

```yaml
# 使用云数据库替代容器化 MySQL
platform-service-backend:
  environment:
    SPRING_DATASOURCE_URL: jdbc:mysql://rds-instance.xxx.rds.aliyuncs.com:3306/private_cloud_disk
    SPRING_DATASOURCE_USERNAME: ${DB_USERNAME}
    SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
```

### 7.4 使用外部对象存储

```yaml
# 使用阿里云 OSS / AWS S3 替代 MinIO
file-service-backend:
  environment:
    STORAGE_BACKEND: s3
    S3_ENDPOINT: https://s3.amazonaws.com
    S3_BUCKET: privateclouddisk-uploads
    S3_ACCESS_KEY: ${S3_ACCESS_KEY}
    S3_SECRET_KEY: ${S3_SECRET_KEY}
```

### 7.5 系统调优

```bash
# /etc/sysctl.conf 添加
vm.max_map_count=262144          # OpenSearch 需要
vm.swappiness=1                   # 减少 swap 使用
net.core.somaxconn=65535          # 增加连接队列
fs.file-max=65535                 # 增加文件句柄数
fs.inotify.max_user_watches=524288

# 应用配置
sudo sysctl -p
```

### 7.6 Docker Daemon 配置

```json
// /etc/docker/daemon.json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2",
  "live-restore": true
}
```

---

## 8. 安全加固

### 8.1 密码管理

```bash
# 生成强密码
openssl rand -base64 32

# 使用 Docker secrets（生产环境）
echo "MySecurePassword123!" | docker secret create mysql_root_password -
```

### 8.2 防火墙规则

```bash
# 仅开放必要端口
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 22/tcp      # SSH
sudo ufw enable
```

### 8.3 SSL/TLS 证书

```yaml
# docker-compose.yml 中已配置 certbot 自动续期
certbot:
  image: certbot/certbot:latest
  volumes:
    - ./certbot/www:/var/www/certbot
    - ./certbot/conf:/etc/letsencrypt
  entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew; sleep 12h & wait $${!}; done;'"
```

**首次申请证书:**

```bash
# 停止 Nginx（释放 80 端口）
docker compose stop frontend

# 申请证书
docker compose run --rm certbot certonly \
  --standalone \
  -d your-domain.com \
  -d www.your-domain.com \
  --email admin@your-domain.com \
  --agree-tos \
  --non-interactive

# 重启服务
docker compose up -d
```

### 8.4 数据库安全

```sql
-- 为应用创建专用用户（而非使用 root）
CREATE USER 'app_user'@'%' IDENTIFIED BY 'StrongPassword123!';
GRANT SELECT, INSERT, UPDATE, DELETE ON private_cloud_disk.* TO 'app_user'@'%';
FLUSH PRIVILEGES;

-- 限制 root 远程登录
DELETE FROM mysql.user WHERE User='root' AND Host NOT IN ('localhost', '127.0.0.1');
```

### 8.5 Redis 安全

```conf
# redis.conf
requirepass ${REDIS_PASSWORD}
rename-command FLUSHDB ""
rename-command FLUSHALL ""
rename-command CONFIG ""
rename-command DEBUG ""
```

---

## 9. 监控与健康检查

### 9.1 内置健康检查端点

| 服务 | 端点 | 说明 |
|---|---|---|
| Gateway | `GET /actuator/health` | Spring Boot Actuator |
| Platform | `GET /actuator/health` | Spring Boot Actuator |
| File Service | `GET /api/v1/health` | 自定义健康检查 |
| MySQL | `mysqladmin ping` | 内置 |
| Redis | `redis-cli ping` | 内置 |
| RabbitMQ | `rabbitmq-diagnostics check_port_connectivity` | 内置 |
| MinIO | `GET /minio/health/live` | 内置 |
| OpenSearch | `GET /_cluster/health` | 内置 |

### 9.2 Docker Compose 健康检查

```yaml
# 所有服务均已配置 healthcheck
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 40s
```

### 9.3 资源监控

```bash
# 查看容器资源使用
docker stats

# 查看服务日志
docker compose logs -f --tail=100 platform-service-backend

# 检查磁盘使用
docker system df
```

### 9.4 推荐监控工具

- **Prometheus + Grafana**: 采集 Spring Boot Actuator metrics
- **ELK Stack**: 集中日志收集和分析
- **Uptime Kuma**: 简易服务可用性监控
- **Portainer**: Docker 可视化管理

---

## 10. 日志管理

### 10.1 日志查看

```bash
# 查看所有服务日志
docker compose logs -f

# 查看特定服务日志（最近 100 行）
docker compose logs --tail=100 platform-service-backend

# 按时间过滤
docker compose logs --since 2024-01-01T00:00:00

# 导出日志到文件
docker compose logs platform-service-backend > platform-service.log
```

### 10.2 日志轮转

```yaml
# 在 docker-compose.yml 中配置
services:
  platform-service-backend:
    logging:
      driver: "json-file"
      options:
        max-size: "50m"
        max-file: "10"
```

### 10.3 集中日志收集

```yaml
# 添加 Filebeat 或 Fluentd 容器收集日志
filebeat:
  image: docker.elastic.co/beats/filebeat:8.11.0
  volumes:
    - ./filebeat.yml:/usr/share/filebeat/filebeat.yml
    - /var/lib/docker/containers:/var/lib/docker/containers:ro
    - /var/run/docker.sock:/var/run/docker.sock
```

---

## 11. 备份与恢复

### 11.1 MySQL 备份

```bash
# 备份数据库
docker compose exec mysql mysqldump \
  -u root -p123456 \
  --single-transaction \
  --routines \
  --triggers \
  private_cloud_disk \
  > backup_$(date +%Y%m%d_%H%M%S).sql

# 压缩备份
gzip backup_*.sql

# 恢复数据库
docker compose exec -T mysql mysql -u root -p123456 private_cloud_disk < backup.sql
```

### 11.2 文件备份

```bash
# 备份上传文件
docker compose run --rm -v uploads-data:/data -v $(pwd)/backup:/backup alpine \
  tar czf /backup/uploads_$(date +%Y%m%d).tar.gz -C /data .

# 备份 MinIO 数据
docker compose run --rm -v minio-data:/data -v $(pwd)/backup:/backup alpine \
  tar czf /backup/minio_$(date +%Y%m%d).tar.gz -C /data .
```

### 11.3 自动化备份脚本

```bash
#!/bin/bash
# scripts/backup.sh
BACKUP_DIR="/backup/privateclouddisk"
DATE=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS=7

mkdir -p "$BACKUP_DIR"

# 备份 MySQL
docker compose exec -T mysql mysqldump -u root -p"${MYSQL_ROOT_PASSWORD}" \
  --single-transaction private_cloud_disk | gzip > "$BACKUP_DIR/db_$DATE.sql.gz"

# 备份文件
tar czf "$BACKUP_DIR/uploads_$DATE.tar.gz" -C /data/uploads .

# 清理旧备份
find "$BACKUP_DIR" -type f -mtime +$RETENTION_DAYS -delete

echo "Backup completed: $DATE"
```

### 11.4 定时备份 (Crontab)

```bash
# 每天凌晨 2 点自动备份
0 2 * * * /opt/privateclouddisk/scripts/backup.sh >> /var/log/backup.log 2>&1
```

---

## 12. CI/CD 自动化部署

### 12.1 GitHub Actions 示例

```yaml
# .github/workflows/deploy.yml
name: Deploy to Production

on:
  push:
    branches: [main]
  workflow_dispatch:

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Deploy via SSH
        uses: appleboy/ssh-action@v1.0.0
        with:
          host: ${{ secrets.SSH_HOST }}
          username: ${{ secrets.SSH_USER }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          script: |
            cd /opt/privateclouddisk
            git pull origin main
            docker compose pull
            docker compose up -d --build
            docker compose exec -T platform-service-backend ./gradlew flywayMigrate
            docker image prune -f
```

### 12.2 部署脚本

```bash
#!/bin/bash
# scripts/deploy.sh - 生产环境部署脚本

set -e

echo "=== PrivateCloudDisk 部署脚本 ==="
echo "开始时间: $(date)"

# 1. 拉取最新代码
echo "[1/6] 拉取最新代码..."
git pull origin main

# 2. 备份数据库
echo "[2/6] 备份数据库..."
bash scripts/backup.sh

# 3. 构建镜像
echo "[3/6] 构建 Docker 镜像..."
docker compose build --parallel

# 4. 滚动更新（零停机）
echo "[4/6] 滚动更新服务..."
docker compose up -d --remove-orphans

# 5. 等待健康检查
echo "[5/6] 等待服务就绪..."
sleep 10
docker compose ps

# 6. 清理旧镜像
echo "[6/6] 清理旧镜像..."
docker image prune -f

echo "=== 部署完成: $(date) ==="
```

### 12.3 回滚方案

```bash
#!/bin/bash
# scripts/rollback.sh - 回滚到上一个版本

# 查看最近的镜像
docker images | grep privateclouddisk

# 使用特定标签的镜像
TAG=$1
docker compose -f docker-compose.yml \
  -f docker-compose.prod.yml \
  up -d

# 或从备份恢复
docker compose exec -T mysql mysql -u root -p123456 private_cloud_disk \
  < backup/db_20240101_020000.sql
```

---

## 13. 常见问题排查

### 13.1 服务启动失败

```bash
# 查看失败容器的日志
docker compose logs <service-name>

# 检查容器退出码
docker compose ps -a

# 检查端口占用
sudo lsof -i :8080
sudo lsof -i :3306

# 重新构建并启动
docker compose up -d --build --force-recreate <service-name>
```

### 13.2 数据库连接失败

```bash
# 检查 MySQL 容器是否就绪
docker compose exec mysql mysqladmin ping -h localhost

# 检查网络连通性
docker compose exec platform-service-backend ping mysql

# 检查数据库是否已初始化
docker compose exec mysql mysql -u root -p123456 \
  -e "SHOW DATABASES;"
```

### 13.3 OpenSearch 启动失败

```bash
# 检查 vm.max_map_count
sysctl vm.max_map_count

# 如果小于 262144，设置
sudo sysctl -w vm.max_map_count=262144

# 检查 OpenSearch 日志
docker compose logs opensearch
```

### 13.4 磁盘空间不足

```bash
# 检查磁盘使用
df -h

# 清理 Docker 资源
docker system prune -a -f --volumes

# 清理日志文件
truncate -s 0 /var/lib/docker/containers/*/*-json.log
```

### 13.5 内存不足

```bash
# 检查容器内存使用
docker stats --no-stream

# 减少 Java 堆内存
# 在 docker-compose.yml 中添加:
# environment:
#   JAVA_OPTS: "-Xms256m -Xmx512m"

# 关闭不必要的服务
docker compose stop opensearch  # 如果不需要全文检索
```

### 13.6 证书过期

```bash
# 手动续期证书
docker compose run --rm certbot renew

# 检查证书有效期
docker compose run --rm certbot certificates

# 强制重新加载 Nginx 配置
docker compose exec frontend nginx -s reload
```

---

## 附录

### A. 目录结构

```
PrivateCloudDisk-project/
├── docker-compose.yml              # 主编排文件
├── .env                             # 环境变量
├── PrivateCloudDisk-db/
│   ├── database_init.sql            # 数据库初始化脚本
│   └── README.md
├── PrivateCloudDisk-infra/          # 基础设施 Dockerfile
│   ├── mysql/
│   ├── redis/
│   ├── rabbitmq/
│   ├── minio/
│   ├── opensearch/
│   └── apisix/
├── PrivateCloudDisk-gateway-service/ # Spring Boot 网关
│   ├── Dockerfile
│   └── src/
├── PrivateCloudDisk-platform-service/ # Spring Boot 平台服务
│   ├── Dockerfile
│   └── src/
├── PrivateCloudDisk-shortage-service/ # FastAPI 文件服务
│   ├── Dockerfile
│   └── app/
├── PrivateCloudDisk-web/             # Vue 前端
│   └── Dockerfile
├── deploy/
│   ├── local/                        # 本地开发配置
│   └── dev/                          # 开发环境配置
├── scripts/
│   ├── deploy.sh                     # 部署脚本
│   ├── backup.sh                     # 备份脚本
│   └── rollback.sh                   # 回滚脚本
└── certbot/                          # SSL 证书
    ├── www/
    └── conf/
```

### B. 快速命令参考

```bash
# 启动
docker compose up -d

# 停止
docker compose down

# 重启
docker compose restart

# 查看日志
docker compose logs -f [service]

# 进入容器
docker compose exec [service] sh

# 重建单个服务
docker compose up -d --build [service]

# 查看资源使用
docker stats

# 清理
docker system prune -a
```

### C. 版本兼容性

| 组件 | 版本 | 说明 |
|---|---|---|
| Docker | 24.0+ | 支持 Compose v2 |
| Java | 18 | 网关和平台服务 |
| Python | 3.11+ | 文件服务 |
| MySQL | 8.0 | 数据库 |
| Redis | 7.x | 缓存 |
| RabbitMQ | 3.x | 消息队列 |
| MinIO | latest | 对象存储 |
| OpenSearch | 2.10 | 全文检索 |
| Nginx | 1.25+ | 反向代理 |
| APISIX | 3.x | API 网关（可选） |