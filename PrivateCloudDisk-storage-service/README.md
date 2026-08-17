# PrivateCloudDisk-storage-service

企业级文件处理服务，基于 FastAPI + Uvicorn (Python 3.11) 构建，负责文件的分片上传、流式下载、缩略图生成、操作凭证管理等核心文件 I/O 功能。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| FastAPI | latest | Web 框架 |
| Uvicorn | latest | ASGI 服务器 |
| Redis (async) | latest | 缓存 + 并发/速率控制 |
| PyJWT | latest | JWT 操作凭证签发/验证 (RSA-256) |
| aiofiles | latest | 异步文件 I/O |
| pyvips | latest | 高性能缩略图生成 (libvips 绑定) |
| aio-pika | latest | RabbitMQ 异步消费者 |
| aiohttp | latest | 异步 HTTP 客户端 |
| python-multipart | latest | 文件上传解析 |
| pydantic-settings | latest | 配置管理 |

---

## 项目结构

```
PrivateCloudDisk-storage-service/
├── server.py                              # FastAPI 应用主入口
│   ├── 操作凭证管理 (签发/撤销)
│   ├── 文件分片接收 (断点续传)
│   ├── 流式文件下载 (Range 请求)
│   ├── 缩略图生成与缓存
│   └── Redis Lua 原子并发控制
│
├── app/                                   # FastAPI 模块化应用
│   ├── main.py                            # 应用入口 + 生命周期管理
│   ├── api/v1/
│   │   ├── router.py                      # 路由聚合
│   │   └── endpoints/
│   │       ├── uploads.py                 # 文件分片上传
│   │       ├── downloads.py               # 授权下载 + Range
│   │       ├── files.py                   # 直接下载 + 缩略图
│   │       ├── operation_tokens.py        # 操作凭证签发/撤销
│   │       └── tasks.py                   # 任务状态查询
│   ├── core/
│   │   ├── redis_client.py               # 异步 Redis 客户端
│   │   ├── security.py                   # JWT 操作凭证验证
│   │   ├── rate_limiter.py               # 多维度限流器
│   │   ├── download_grant.py             # 下载授权管理 (Opaque Token)
│   │   └── logging_config.py             # 日志配置
│   ├── services/
│   │   └── thumbnail_service.py           # 缩略图生成服务 (libvips)
│   ├── models/
│   │   └── schemas.py                     # Pydantic 数据模型
│   ├── middleware/
│   │   └── timing.py                      # 请求耗时中间件
│   └── utils/
│       └── helpers.py                     # 辅助函数
│
├── core/                                  # 配置与消费者
│   ├── config.py                          # 全局配置 (Pydantic Settings)
│   ├── rabbitmq.py                        # RabbitMQ 异步服务
│   └── consumers/
│       ├── file_process_consumer.py        # 文件处理消费者
│       └── file_delete_consumer.py         # 文件删除消费者
│
├── requirements.txt                       # Python 依赖
├── Dockerfile                             # Docker 镜像构建
└── tests.py                               # 测试文件
```

---

## 完整 API 接口文档

### 基础信息

| 属性 | 值 |
|------|-----|
| 服务地址 | `http://localhost:8000` |
| 网关路径前缀 | `/api/v1/files` |

### 统一响应格式

```json
{
  "code": 200,
  "message": null,
  "data": {}
}
```

---

### 模块一：操作凭证 `/files/operation-tokens`

操作凭证 (Operation Token) 是执行文件上传/下载操作前的必要凭据，有效期 1 小时。

#### 1.1 签发操作凭证

```http
POST /api/v1/files/operation-tokens
```

| 属性 | 说明 |
|------|------|
| 认证 | **需要** JWT (网关透传 X-User-Id) |
| 限流 | 用户级: 30次/60s · IP级: 120次/60s |

**请求体：**

```json
{
  "file_id": "file-uuid",
  "operation_type": "download"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file_id` | String | 是 | 目标文件 UUID |
| `operation_type` | String | 是 | `download` / `preview` / `stream` / `upload` |

**操作凭证 JWT Payload：**

```json
{
  "jti": "unique-token-id",
  "sub": "user-uuid",
  "file_id": "file-uuid",
  "operation_type": "download",
  "rlimit": 300,
  "exp": 1768123456,
  "iat": 1768123456
}
```

| Claim | 说明 |
|-------|------|
| `jti` | 唯一凭证 ID（用于并发控制 Redis key） |
| `sub` | 用户 UUID |
| `file_id` | 文件 UUID |
| `operation_type` | 操作类型 |
| `rlimit` | 最大请求次数限制 (默认 300 次) |
| `exp` | 过期时间 (1 小时) |

**成功响应：**
```json
{
  "code": 200,
  "data": {
    "operation_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "message": null
}
```

**失败响应：**
```json
// 404 - 文件不存在
{ "code": 404, "message": "文件不存在或无权访问" }

// 429 - 请求频率过高
{ "code": 429, "message": "请求过于频繁，请稍后再试" }

// 503 - 业务服务不可用
{ "code": 503, "message": "服务暂时不可用" }
```

#### 1.2 撤销操作凭证

```http
DELETE /api/v1/files/operation-tokens
```

| 属性 | 说明 |
|------|------|
| 认证 | **需要** JWT |
| 限流 | 用户级: 60次/60s · IP级: 180次/60s |

**请求体：**
```json
{
  "operation_token": "eyJhbGciOiJSUzI1NiIs..."
}
```

**成功响应：**
```json
{ "code": 200, "message": null }
```

---

### 模块二：文件上传 `/files/uploads`

#### 2.1 上传文件分片

```http
POST /api/v1/files/uploads/{uploads_id}/chunks
```

| 属性 | 说明 |
|------|------|
| 认证 | **需要** 操作凭证 (X-Operation-Token) |
| 请求体 | `multipart/form-data` (二进制流) |

**请求头：**

| 请求头 | 必填 | 说明 |
|--------|------|------|
| `X-Operation-Token` | 是 | 操作凭证 JWT |
| `X-Chunk-Index` | 是 | 分片索引 (从 1 开始) |
| `X-User-Id` | 是 | 用户 UUID |
| `Content-Type` | 是 | `multipart/form-data` |

**流程：**

```mermaid
sequenceDiagram
    participant Client as 🌐 Client
    participant FS as 📁 File Service
    participant Redis as 📦 Redis
    participant BS as ⚙ Business :8081
    participant Disk as 💾 Disk

    Client->>FS: POST /files/uploads/{id}/chunks<br/>X-Operation-Token + chunk_index

    FS->>FS: 验证操作凭证 JWT (RSA-256)
    FS->>FS: 从 JWT 提取 jti, sub, file_id

    FS->>Redis: INCR concurrency:{jti} (Lua 原子)
    Redis-->>FS: current

    alt current > max_concurrent (3)
        FS->>Redis: DECR concurrency:{jti}
        FS-->>Client: 429 Too Many Concurrent Requests
    end

    FS->>Redis: INCR rate:{jti}:{now_sec}
    alt rate > RATE_PER_SEC (10)
        FS->>Redis: DECR concurrency:{jti}
        FS-->>Client: 429 Rate Limit Exceeded
    end

    FS->>BS: GET /internal/storage/uploads/{id}/chunks/{index}
    BS-->>FS: { status: "pending" / "completed" }

    alt 分片已上传 (断点续传)
        FS->>Redis: DECR concurrency:{jti}
        FS-->>Client: 200 { status: "already_uploaded" }
    end

    FS->>Disk: aiofiles.write {upload_dir}/{id}-{index}.part
    FS->>BS: POST /internal/storage/uploads/{id}/chunks/{index}/complete?storage_path=...
    BS-->>FS: 200 OK

    FS->>Redis: DECR concurrency:{jti}
    FS-->>Client: 200 { "status": "uploaded", "chunk_index": 5 }
```

**成功响应：**
```json
{
  "code": 200,
  "data": {
    "status": "uploaded",
    "chunk_index": 5,
    "storage_path": "/Uploads/uploads_id-5.part"
  }
}
```

**断点续传响应：**
```json
{
  "code": 200,
  "data": {
    "status": "already_uploaded",
    "chunk_index": 5
  }
}
```

---

### 模块三：文件下载 `/files/files` `/downloads/files`

#### 3.1 直接下载 (操作凭证)

```http
GET /api/v1/files/files/{file_id}/content
```

| 属性 | 说明 |
|------|------|
| 认证 | **需要** 操作凭证 (X-Operation-Token) |
| 并发控制 | Redis Lua 原子脚本 |

**请求头：**

| 请求头 | 必填 | 说明 |
|--------|------|------|
| `X-Operation-Token` | 是 | 下载操作凭证 JWT |
| `X-User-Id` | 是 | 用户 UUID |
| `Range` | 否 | `bytes=start-end` |

**限制：**
- 最大并发连接数：3
- 每秒最大请求数：10
- 单凭证最大请求数：300
- Range 最大字节数：100MB

#### 3.2 授权下载 (Opaque Token)

```http
GET /api/v1/downloads/files/{file_id}/content
```

| 属性 | 说明 |
|------|------|
| 认证 | **需要** 下载授权 Token (X-Download-Token) |

> Opaque Token 是存储在 Redis 中的一次性或限次下载授权，适用于分享链接下载场景。

**Range 请求流程：**

```mermaid
sequenceDiagram
    participant Client as 🌐 Client
    participant FS as 📁 File Service
    participant Disk as 💾 Disk

    Client->>FS: GET /files/{file_id}/content<br/>X-Operation-Token + Range: bytes=0-1048575
    FS->>FS: JWT 验证 + 并发/速率/次数检查

    FS->>Disk: os.stat() 获取文件大小
    Disk-->>FS: file_size = 52428800

    FS->>FS: 验证 Range 不超过 max_range_bytes (100MB)
    FS->>Disk: aiofiles open + seek(0)
    FS->>Disk: read(1048576) ~= 1MB chunk
    Disk-->>FS: binary_data

    FS->>FS: 设置响应头:<br/>Content-Range: bytes 0-1048575/52428800<br/>Accept-Ranges: bytes

    FS-->>Client: HTTP 206 Partial Content<br/>[1048576 bytes binary data]
```

**成功响应头：**

```
HTTP 206 Partial Content
Content-Range: bytes 0-1048575/52428800
Content-Length: 1048576
Accept-Ranges: bytes
Content-Type: application/octet-stream
Cache-Control: no-cache
X-File-Checksum: sha256:abc123...
```

---

### 模块四：缩略图 `/files/thumbnails`

```http
GET /api/v1/files/thumbnails/{file_id}
```

| 属性 | 说明 |
|------|------|
| 认证 | **需要** JWT |
| 缓存 | Redis (TTL: 1 小时) |

**支持的图片格式：** JPEG, PNG, WebP, TIFF, GIF, BMP

**流程：**

```mermaid
sequenceDiagram
    participant Client as 🌐 Client
    participant FS as 📁 File Service
    participant Redis as 📦 Redis
    participant BS as ⚙ Business :8081
    participant Disk as 💾 Disk

    Client->>FS: GET /files/thumbnails/{file_id}
    FS->>FS: 验证 JWT

    FS->>Redis: GET thumbnail:{file_id}
    alt 缓存命中
        Redis-->>FS: base64 编码的缩略图数据
        FS-->>Client: 200 { thumbnail: "base64..." }
    end

    FS->>BS: GET /internal/storage/files/{file_id}/metadata
    BS-->>FS: { storage_path, file_type }

    FS->>Disk: pyvips.Image.new_from_file(path)
    Disk-->>FS: vips_image

    FS->>FS: thumbnail_image(400) 按比例缩放
    FS->>FS: vips_image.write_to_buffer(".jpg[Q=80]")

    FS->>Redis: SETEX thumbnail:{file_id} 3600 base64_data

    FS-->>Client: 200 { thumbnail: "base64...", format: "jpeg" }
```

**成功响应：**
```json
{
  "code": 200,
  "data": {
    "thumbnail": "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBD...",
    "format": "jpeg",
    "width": 400,
    "height": 300
  }
}
```

---

### 模块五：任务查询 `/files/tasks`

```http
GET /api/v1/files/tasks/{task_id}/status
```

**成功响应：**
```json
{
  "code": 200,
  "data": {
    "task_id": "task-uuid",
    "task_type": "file_process",
    "file_id": "file-uuid",
    "status": "processing",
    "progress": {
      "merge": "completed",
      "hash_calculate": "completed",
      "virus_scan": "processing",
      "thumbnail": "pending",
      "video_transcode": "pending"
    },
    "created_at": "2026-06-11T10:30:00",
    "updated_at": "2026-06-11T10:30:15"
  }
}
```

---

## 核心设计

### 操作凭证 (Operation Token) 机制全流程

```mermaid
sequenceDiagram
    actor U as 👤 User
    participant Web as 🌐 Vue
    participant GW as 🚪 Gateway
    participant FS as 📁 File Service
    participant BS as ⚙ Business
    participant Redis as 📦 Redis
    participant Disk as 💾 Disk

    U->>Web: 点击下载/预览文件

    Note over Web,FS: === 阶段 1: 申请操作凭证 ===
    Web->>GW: POST /api/v1/files/operation-tokens<br/>{file_id, operation_type}
    GW->>FS: 转发 + X-User-Id

    FS->>Redis: FIXED_WINDOW(user+file:issue:count, 60s)
    alt 超过频率限制
        FS-->>GW: 429
        GW-->>Web: 429
    end

    FS->>BS: GET /internal/storage/files/{file_id}?uid={user_id}
    BS->>BS: 验证文件存在 + 用户权限
    BS-->>FS: 200 { metadata }

    FS->>FS: 签发 JWT (RSA-256)<br/>Claims: {jti, sub, file_id, op_type, rlimit:300, exp:1h}
    FS->>Redis: SET file:meta:{file_id} TTL:1h (缓存元数据加速)

    FS-->>GW: 200 { operation_token }
    GW-->>Web: 200 { operation_token }

    Note over Web,Disk: === 阶段 2: 执行下载 (3D 并发控制) ===

    Web->>FS: GET /files/files/{file_id}/content<br/>X-Operation-Token + X-User-Id

    FS->>FS: RSA 公钥验证 JWT 签名
    FS->>FS: 提取 {jti, sub, file_id}

    FS->>Redis: Lua: INCR total:{jti} (次数检查)
    alt total > rlimit (300)
        FS-->>Web: 429 Too Many Requests
    end

    FS->>Redis: Lua: INCR rate:{jti}:{sec} (速率检查)
    alt rate > 10
        FS-->>Web: 429 Rate Exceeded
    end

    FS->>Redis: Lua: INCR concurrency:{jti} (并发检查)
    alt concurrency > 3
        FS->>Redis: DECR concurrency:{jti}
        FS-->>Web: 429 Too Many Concurrent
    end

    FS->>Disk: StreamingResponse (async generator)
    Disk-->>FS: yield [chunk_data]

    FS->>Redis: DECR concurrency:{jti}
    FS-->>Web: HTTP 206 (或 200) 流式响应
```

### 多维度并发控制 (3D Control)

```mermaid
graph TB
    subgraph "3D 并发控制 (Redis Lua 原子脚本)"
        D1["维度 1: Request Count<br/>total:{jti}<br/>INCR → ≤ rlimit (300)<br/>TTL: 1小时"]
        D2["维度 2: Rate Per Second<br/>rate:{jti}:{now_sec}<br/>INCR → ≤ 10/sec<br/>TTL: 1秒"]
        D3["维度 3: Concurrent Connections<br/>concurrency:{jti}<br/>INCR → ≤ 3<br/>TTL: 30秒"]
    end

    Request[请求] --> D1
    D1 -->|通过| D2
    D2 -->|通过| D3
    D3 -->|通过| Process[处理请求]
    D3 -->|释放| Release[DECR concurrency]

    D1 -->|拒绝| R1[429 超次数]
    D2 -->|拒绝| R2[429 超速率]
    D3 -->|拒绝| R3[429 超并发]

    style Process fill:#c8e6c9
    style R1 fill:#ffcdd2
    style R2 fill:#ffcdd2
    style R3 fill:#ffcdd2
```

### 断点续传检查流程

```mermaid
flowchart TD
    A[收到分片上传请求] --> B{验证操作凭证 JWT}
    B -->|无效| C[返回 401]
    B -->|有效| D{并发检查通过?}
    D -->|否| E[返回 429]
    D -->|是| F[查询业务服务:<br/>分片是否已上传?]
    F -->|已上传| G[返回 200<br/>already_uploaded<br/>跳过此分片]
    F -->|未上传| H[aiofiles 写入磁盘]
    H --> I[通知业务服务<br/>记录分片完成]
    I --> J[返回 200<br/>uploaded]

    style G fill:#fff3e0
    style J fill:#c8e6c9
    style C fill:#ffcdd2
    style E fill:#ffcdd2
```

---

## RabbitMQ 消费者

### Backend Task Bus — 文件后台处理流水线

```mermaid
flowchart TD
    A[上传完成] -->|pcd.file.backend.exchange<br/>file.backend.merge| M[merge task queue]
    M --> MC[MergeConsumer<br/>既有合并逻辑]
    MC --> G[DB Gate + ready/timeout Outbox]
    G -->|file.content.ready| AUTO[Automation 预处理]
    AUTO -->|file.content.processed| CAS[Gate CAS]
    G -->|timeout / sweeper| CAS
    CAS -->|pcd.file.backend.exchange<br/>file.backend.hash| H[hash task queue]
    H --> HC[HashConsumer<br/>既有哈希逻辑]
    HC -->|pcd.file.backend.exchange<br/>file.backend.virus| V[virus task queue]
    V --> VC[VirusConsumer<br/>既有扫描逻辑]
    VC -->|pcd.file.backend.exchange<br/>file.backend.mark_active| MA[mark_active task queue]
    MA --> AC[MarkActiveConsumer<br/>既有激活逻辑]
    AC -->|file.available| BS[主业务服务]
    AC -->|file.enhance.* tasks| E[增强 Task Bus 并发队列]
    H -. retry / DLQ .-> R[当前阶段 retry 或专属 DLQ]
    V -. retry / DLQ .-> R
    MA -. retry / DLQ .-> R
```

Backend Task Bus 交换机为 `pcd.file.backend.exchange`，DLX 为 `pcd.file.backend.dlx`。
Merge、Hash、Virus、Mark Active 的业务处理方法保持不变；消费者完成当前任务后直接投递
下一阶段任务。内容预处理仍通过独立的生命周期事件和 Gate CAS fail-open，不改变 Backend
Task Bus 的阶段路由。完整拓扑、重试、死信和降级流程见
[`docs/STORAGE_WORKER_TASK_BUS_AUDIT.md`](../docs/STORAGE_WORKER_TASK_BUS_AUDIT.md)。

### FileDeleteConsumer — 文件删除

监听 `pcd.file.delete.queue`：

```mermaid
sequenceDiagram
    participant MQ as 🐇 RabbitMQ
    participant FS as 📁 File Service
    participant Disk as 💾 Disk
    participant BS as ⚙ Business

    MQ->>FS: {file_id, storage_path, action: "permanent_delete"}
    FS->>Disk: os.remove(storage_path) 删除物理文件
    FS->>Disk: os.remove(thumbnail_cache) 清理缩略图
    FS->>BS: PUT /internal/storage/files/{file_id}/status<br/>{status: "deleted"}
    BS-->>FS: 200 OK
    FS->>MQ: ACK
```

---

## 文件存储结构

```
Uploads/
├── storage/                                    # 已完成文件 (永久存储)
│   └── {yyyy}/{mm}/{dd}/{file_id}.ext          # 按日期分目录
├── {uploads_id}-{chunk_index}.part              # 上传中的临时分片
├── thumbnails/                                  # 缩略图缓存 (Redis 为主)
│   └── {file_id}_400x300.jpg
```

---

## 配置说明

核心配置通过环境变量 / `.env` 文件管理：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `redis_url` | Redis 连接 URL | `redis://localhost:6379/0` |
| `file_upload_dir` | 文件存储根目录 | `../Uploads` |
| `business_service_url` | 业务服务地址 | `http://127.0.0.1:8080` |
| `max_concurrent` | 单操作最大并发连接数 | `3` |
| `operation_token_expire_seconds` | 操作凭证有效期 | `3600` |
| `max_requests_per_operation_token` | 单凭证最大请求次数 | `300` |
| `rate_per_sec` | 单操作每秒最大请求数 | `10` |
| `max_range_bytes` | 单次 Range 最大字节数 | `104857600` (100MB) |
| `thumbnail_ttl` | 缩略图缓存 TTL (秒) | `3600` |
| `rabbitmq_host` | RabbitMQ 地址 | `localhost` |
| `private_key_path` | JWT 签发私钥路径 | `keys/private.pem` |
| `public_key_path` | JWT 验证公钥路径 | `keys/public.pem` |

---

## 核心流程时序图

### 完整断点续传与 Task Bus 后台处理

展示从创建上传会话、分片上传、合并，到内容预处理 Gate、哈希、病毒扫描、激活和增强
事件扇出的关键链路。后台阶段采用原有 file backend task 队列；内容预处理使用独立生命周期事件。

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Web as 🌐 Vue 前端
    participant BS as ⚙ Business :8081
    participant FS as 📁 File Service
    participant Redis as 📦 Redis
    participant Disk as 💾 磁盘
    participant MQ as 🐇 RabbitMQ
    participant Viz as 🔬 病毒扫描
    participant Auto as 🔌 Automation

    Note over User,BS: === 阶段 1: 创建上传会话 ===

    User->>Web: 选择文件 → 开始上传
    Web->>Web: 计算文件 SHA-256<br/>按 5MB 分片计算总分片数
    Web->>BS: POST /api/v1/business/uploads/<br/>{ total_chunks, file_size, file_checksum, ... }
    BS->>BS: 验证参数 + 配额检查
    BS->>BS: INSERT pcd_uploads_session_table<br/>status = 'uploading'
    BS-->>Web: 200 { uploads_id, active_session_count, remaining_concurrent_sessions }

    Note over User,FS: === 阶段 2: 文件/分块并发上传（默认各 3） ===

    Web->>Web: chunk_index = 1

    loop Promise 池：最多 3 个分片同时上传，完成一个立即补充下一个
        Web->>BS: POST /api/v1/business/files/operation-tokens<br/>{ file_id? → 不需要, upload 操作 }
        BS-->>Web: 200 { operation_token }

        Web->>FS: POST /files/uploads/{uploads_id}/chunks<br/>X-Operation-Token + X-Chunk-Index: {chunk_index}<br/>multipart/form-data: chunk_data

        FS->>FS: RSA 验证 operation_token JWT

        FS->>Redis: Lua: INCR concurrency:{jti}
        alt 并发超过 3
            FS->>Redis: DECR concurrency:{jti}
            FS-->>Web: 429 Too Many Concurrent<br/>(前端重试队列)
        end

        FS->>BS: GET /internal/storage/uploads/{uploads_id}/chunks/{chunk_index}
        BS-->>FS: { status: "pending" }

        FS->>Disk: aiofiles.write<br/>{upload_dir}/{uploads_id}-{chunk_index}.part
        FS->>Disk: sha256_hash.update(chunk_data)

        FS->>BS: POST /internal/storage/uploads/{uploads_id}/chunks/{chunk_index}/complete<br/>?storage_path=...

        FS->>Redis: DECR concurrency:{jti}

        FS-->>Web: 200 { "status": "uploaded", "chunk_index": N }

        Web->>Web: chunk_index++
    end

    Note over User,FS: === ⚡ 断点续传场景 ===

    Web--xFS: 💥 网络中断! (chunk_index = 8)

    Web->>Web: 断点续传探测:<br/>逐一查询分片状态

    loop 离线分片检查
        Web->>BS: GET /internal/storage/uploads/{uploads_id}/chunks/{chunk_index}
        alt 分片未上传
            BS-->>Web: { status: "pending" }
            Web->>Web: 记录缺失分片: [8, 9, 10]
        else 分片已上传
            BS-->>Web: { status: "uploaded" }
        end
    end

    Note over Web: 仅重传缺失的分片 [8, 9, 10]
    Web->>FS: 重传分片 8, 9, 10 ...

    Note over User,BS: === 阶段 3: 触发合并 ===

    Web->>BS: POST /api/v1/business/uploads/{uploads_id}/complete
    BS->>BS: UPDATE uploads_status → 'completed'
    Note over BS: 仅表示分块已保存且 merge task 已发布；后处理状态由文件元数据独立维护

    BS->>MQ: 发布 pcd.file.backend.exchange<br/>file.backend.merge

    BS-->>Web: 200 OK (合并已触发)

    Note over MQ,Auto: === 阶段 4: Task Bus 后台处理 ===

    MQ->>FS: merge task queue → MergeConsumer

    FS->>FS: MERGE: 按 chunk_index 顺序合并分片<br/>→ {storage_dir}/{file_id}.ext
    alt 合并失败 (磁盘空间不足/读写错误)
        FS->>Disk: 清理临时分片
        FS->>BS: 删除 completed 上传会话及分块元数据
    end

    FS->>FS: 写入 Gate + ready/timeout Outbox<br/>事务成功后 ACK merge 事件
    FS->>Auto: file.content.ready
    Auto-->>FS: file.content.processed 或 timeout
    FS->>FS: Gate CAS：选择候选内容或原始合并内容
    FS->>MQ: 发布 pcd.file.backend.exchange<br/>file.backend.hash
    MQ->>FS: hash task queue → HashConsumer
    FS->>FS: HASH_CALCULATE: SHA-256 校验合并后文件
    alt 校验不匹配
        FS->>Disk: 删除合并文件
        FS->>BS: 删除 completed 上传会话及分块元数据
    end

    FS->>MQ: 发布 pcd.file.backend.exchange<br/>file.backend.virus
    MQ->>FS: virus task queue → VirusConsumer
    FS->>Viz: VIRUS_SCAN: 提交病毒扫描
    alt 检测到恶意文件
        FS->>Disk: 删除文件
    end

    FS->>MQ: 发布 pcd.file.backend.exchange<br/>file.backend.mark_active
    MQ->>FS: mark_active task queue → MarkActiveConsumer
    FS->>BS: PUT /internal/storage/files/{file_id}/status<br/>{ status: "active" }
    BS->>BS: INSERT pcd_file_info_table<br/>UPDATE pcd_user_quota_table<br/>UPDATE pcd_directory_tree_table

    FS->>Disk: 清理临时分片文件

    FS->>MQ: ACK；MarkActiveConsumer 发布 file.available 和 enhancement tasks
    BS-->>FS: 200 OK
```

可重试错误先发布到当前事件路由的 `.retry` TTL 队列，发布确认成功后 ACK；不可重试、
未知异常或超过次数进入当前阶段专属 DLQ。内容预处理失败只触发 fail-open，核心文件仍
继续进入 hash → scan → active。完整拓扑和 DLQ 流程见
[`docs/STORAGE_WORKER_TASK_BUS_AUDIT.md`](../docs/STORAGE_WORKER_TASK_BUS_AUDIT.md)。

### 多分片并发上传流程

```mermaid
sequenceDiagram
    participant Web as 🌐 Vue (uploaderStore)
    participant BS as ⚙ Business
    participant FS as 📁 File Service
    participant Redis as 📦 Redis

    Note over Web: 并发控制配置<br/>maxConcurrent = 3 (默认同时上传3个分片，可配置)

    Web->>BS: 创建上传会话 → { uploads_id, total_chunks: 20 }

    par 并发组 1: 分片 1 & 2
        Web->>BS: 申请 operation_token (×2)
        BS-->>Web: token_1, token_2
        Web->>FS: POST /uploads/{id}/chunks chunk=1
        Web->>FS: POST /uploads/{id}/chunks chunk=2
        FS->>Redis: Lua: INCR concurrency:{jti}
        FS->>FS: aiofiles.write (并行)
        FS-->>Web: chunk=1 uploaded
        FS-->>Web: chunk=2 uploaded
        FS->>Redis: DECR concurrency:{jti}
    end

    par 并发组 2: 分片 3 & 4
        Web->>FS: POST chunk=3
        Web->>FS: POST chunk=4
        FS-->>Web: chunk=3 uploaded
        FS-->>Web: chunk=4 uploaded
    end

    Note over Web: ... 持续至 chunk 20 ...

    Web->>BS: POST /uploads/{id}/complete
    BS-->>Web: ✅ 合并已触发

    Note over Web,Redis: 💡 前端 uploaderStore 维护并发队列<br/>每完成1个分片，自动补充下一个<br/>保持始终有 maxConcurrent 个分片在传输
```

### 流式下载与 Range 请求

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Web as 🌐 Vue (downloads.js)
    participant FS as 📁 File Service
    participant Redis as 📦 Redis
    participant Disk as 💾 磁盘

    User->>Web: 点击下载大文件 (500MB)

    Note over Web,FS: === 阶段 1: 获取操作凭证 ===

    Web->>FS: POST /files/operation-tokens<br/>{ file_id, operation_type: "download" }
    FS->>FS: 签发 JWT (rlimit: 300, TTL: 1h)
    FS-->>Web: { operation_token }

    Note over Web,Disk: === 阶段 2: 流式下载 ===

    Web->>FS: GET /files/files/{file_id}/content<br/>X-Operation-Token<br/>Range: bytes=0-1048575

    FS->>Redis: Lua 3D 检查 (次数/速率/并发)
    FS->>Disk: os.stat() → file_size = 524288000 (500MB)
    FS->>Disk: aiofiles.open + seek(0)
    FS->>Disk: aiofiles.read(1048576) → 1MB chunk

    FS-->>Web: HTTP 206<br/>Content-Range: bytes 0-1048575/524288000<br/>[1MB binary]

    Web->>Web: 写入本地缓冲区<br/>进度: 1/500 MB (0.2%)

    Note over Web: 🔄 循环发送 Range 请求...

    Web->>FS: GET /files/files/{file_id}/content<br/>Range: bytes=1048576-2097151
    FS-->>Web: [1MB binary] → 进度: 2/500 MB

    Note over Web: ... (省略中间 496 次) ...

    Web->>FS: GET /files/files/{file_id}/content<br/>Range: bytes=523239424-524287999
    FS-->>Web: [1MB binary] → 进度: 500/500 MB

    Web->>Web: 合并所有 chunk → 另存为文件
    Web-->>User: ✅ 下载完成 (500MB)

    Note over Web,Disk: 💡 断点续传: 如 Range 请求失败<br/>记录已下载字节数<br/>下次从断点处继续请求
```

### 缩略图缓存策略

```mermaid
flowchart TD
    Start([GET /files/thumbnails/{file_id}]) --> Cache{Redis: GET<br/>thumbnail:{file_id}}

    Cache -->|命中| Hit[返回 base64 缓存数据<br/>响应时间: ~5ms]
    Cache -->|未命中| Type{检查文件类型}

    Type -->|图片 (JPEG/PNG/WebP/TIFF/GIF/BMP)| GetMeta[查询业务服务<br/>获取 storage_path]
    Type -->|非图片 (PDF/视频/其他)| NoThumb[返回 200<br/>thumbnail: null]

    GetMeta --> DiskRead[pyvips.Image.new_from_file<br/>读取原始文件]
    DiskRead --> Scale[thumbnail_image(400)<br/>按比例缩放至宽度 400px]
    Scale --> Convert[write_to_buffer<br/>'.jpg[Q=80]'<br/>JPEG 质量 80%]

    Convert --> StoreCache[Redis: SETEX<br/>thumbnail:{file_id} 3600<br/>base64_encoded_data]

    StoreCache --> Return[200 返回缩略图<br/>响应时间首次: ~500ms]

    Hit --> Return

    style Hit fill:#c8e6c9
    style Return fill:#c8e6c9
    style NoThumb fill:#fff3e0

    subgraph "缓存清除策略"
        Dir1[主动清除:<br/>DELETE /files/thumbnails/{file_id}]
        Dir2[被动过期:<br/>Redis TTL 3600s 自动删除]
        Dir3[文件变更清除:<br/>更新文件时 DEL thumbnail:{file_id}]
    end
```

### 并发控制架构全景

```mermaid
graph TB
    subgraph "入口层: Gateway"
        G1["IP 级限流<br/>120次/60s"]
        G2["用户级限流<br/>30次/60s"]
    end

    subgraph "应用层: File Service (Redis Lua 原子)"
        A1["维度 1: 请求总数<br/>total:{jti}<br/>≤ rlimit (300次/凭证)"]
        A2["维度 2: 请求速率<br/>rate:{jti}:{sec}<br/>≤ 10次/秒"]
        A3["维度 3: 并发连接<br/>concurrency:{jti}<br/>≤ 3个同时"]
        A4["维度 4: 全局并发<br/>global:dl:{file_id}:cc<br/>≤ 5个文件同时下载"]
    end

    subgraph "存储层: 磁盘 I/O"
        S1["aiofiles 异步 I/O<br/>非阻塞读写"]
        S2["文件锁 flock<br/>防止并发写入同一文件"]
    end

    G1 --> A1
    G2 --> A1
    A1 -->|通过| A2
    A2 -->|通过| A3
    A3 -->|通过| A4
    A4 -->|通过| S1
    S1 --> S2

    A1 -->|拒绝| R1["429 超次数"]
    A2 -->|拒绝| R2["429 超速率"]
    A3 -->|拒绝| R3["429 超并发"]
    A4 -->|拒绝| R4["429 全局超限"]

    style S1 fill:#e3f2fd
    style S2 fill:#e3f2fd
    style R1 fill:#ffcdd2
    style R2 fill:#ffcdd2
    style R3 fill:#ffcdd2
    style R4 fill:#ffcdd2

    subgraph "Redis Lua 原子脚本示例"
        Lua["-- 一次 Redis 调用执行三个检查<br/>local r1 = redis.call('INCR', 'total:'..jti)<br/>redis.call('EXPIRE', 'total:'..jti, 3600)<br/>if r1 > rlimit then return {err='COUNT'} end<br/><br/>local r2 = redis.call('INCR', 'rate:'..jti..':'..ts)<br/>redis.call('EXPIRE', 'rate:'..jti..':'..ts, 1)<br/>if r2 > 10 then return {err='RATE'} end<br/><br/>local r3 = redis.call('INCR', 'cc:'..jti)<br/>redis.call('EXPIRE', 'cc:'..jti, 30)<br/>if r3 > 3 then return {err='CC'} end<br/><br/>return {ok='PASS'}"]
    end
```

---

## 开发指南

### 环境要求
- Python 3.11+
- Redis
- RabbitMQ
- libvips (系统级)

### 安装依赖
```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

macOS 安装 libvips：
```bash
brew install vips
```

### 启动服务
```bash
uvicorn server:app --host 0.0.0.0 --port 8000 --reload
```

### Docker 部署
```bash
docker build -t privateclouddisk-file-service .
docker run -p 8000:8000 -v /data/uploads:/Uploads privateclouddisk-file-service
```
