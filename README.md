# PrivateCloudDisk — 企业级私有云盘系统

企业级私有云盘系统，支持文件的上传（断点续传）、下载（流式）、预览、分享、回收站，具备完善的安全认证、权限控制与分布式限流能力。

---

## 系统架构全景图

```mermaid
graph TB
    subgraph Client["🖥 客户端层"]
        Browser["Web 浏览器<br/>Vue 3 + Tailwind CSS"]
    end

    subgraph Gateway["🚪 网关层 :8080"]
        GW["Spring Cloud Gateway<br/>认证 | 限流 | 路由 | CORS"]
    end

    subgraph Business["⚙ 业务服务层 :8081"]
        BS["Spring Boot<br/>用户 | 文件 | 目录树 | 配额"]
    end

    subgraph FileService["📁 文件服务层 :8000"]
        FS["FastAPI + Uvicorn<br/>上传 | 下载 | 缩略图 | 凭证"]
    end

    subgraph Data["💾 数据层"]
        MySQL[("MySQL 8.0<br/>业务数据")]
        Redis[("Redis<br/>缓存 | 限流 | 会话")]
        RabbitMQ[("RabbitMQ<br/>异步任务 | 文件处理")]
        Storage[("本地磁盘<br/>文件物理存储")]
    end

    Browser -->|HTTP/HTTPS| GW
    GW -->|JWT 鉴权 + 路由| BS
    GW -->|凭证鉴权 + 路由| FS
    BS -->|读写| MySQL
    BS -->|读写| Redis
    BS -->|发布消息| RabbitMQ
    FS -->|读写| Redis
    FS -->|消费消息| RabbitMQ
    FS -->|读写| Storage
    BS -->|内部 API 调用| FS

    style Client fill:#e3f2fd
    style Gateway fill:#fff3e0
    style Business fill:#e8f5e9
    style FileService fill:#fce4ec
    style Data fill:#f3e5f5
```

---

## 技术栈总览

| 层级 | 技术 | 说明 |
|------|------|------|
| **Web 前端** | Vue 3 + Vite + Tailwind CSS + Pinia | SPA 单页应用，主用户端 |
| **管理后台** | React 19 + TypeScript + Ant Design 6 | 超级管理员后台 |
| **桌面端** | Electron + React + macFUSE | macOS / Windows / Linux 桌面客户端 |
| **移动端-跨端** | uni-app (Vue 3) + uView Plus | iOS / Android / 微信小程序 / H5 |
| **移动端-原生** | SwiftUI (iOS) / Kotlin Compose (Android) | 原生 iOS / Android 客户端 |
| **桌面端-原生** | SwiftUI (macOS) / WPF+.NET (Windows) | 原生 macOS / Windows 客户端 |
| **网关** | Spring Cloud Gateway + WebFlux + Spring Security | 响应式 API 网关 |
| **业务服务** | Spring Boot 4.0.6 + MyBatis + Spring AMQP | RESTful API 核心业务 |
| **文件服务** | FastAPI + Uvicorn (Python 3.11) | 文件 I/O 处理 |
| **即时通讯** | Spring Boot + Netty + WebRTC | 实时消息 + 音视频通话 |
| **数据库** | MySQL 8.0 (InnoDB, utf8mb4) | 业务数据持久化 |
| **缓存** | Redis 7 | 缓存 / 限流 / 分布式锁 |
| **消息队列** | RabbitMQ (AMQP 0-9-1) | 异步任务处理 |
| **全文检索** | OpenSearch 2.10 | 文件内容搜索 |
| **对象存储** | MinIO (S3 兼容) | 文件物理存储 |
| **认证** | JWT (RSA-256) + BCrypt | 无状态认证 |
| **图片处理** | libvips (via pyvips) | 缩略图生成 |
| **部署** | Docker + Docker Compose | 容器化部署 |
| **监控** | Prometheus + Grafana + SkyWalking | 指标 + 链路追踪 |

---

## 核心业务流程

### 用户登录全流程

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Web as 🌐 Vue前端
    participant GW as 🚪 Gateway
    participant BS as ⚙ 业务服务
    participant Redis as 📦 Redis
    participant MySQL as 🗄 MySQL

    User->>Web: 输入账号/密码 + 滑块验证码
    Web->>Web: 获取 Turnstile Token
    Web->>GW: POST /api/v1/business/users/login
    GW->>GW: 白名单路径 → 放行
    GW->>BS: 转发请求

    BS->>Redis: 检查IP/账号是否被锁定
    alt 已被锁定
        BS-->>GW: 429 Too Many Requests
        GW-->>Web: 429 | 请稍后再试
    end

    BS->>BS: 验证 Turnstile Token
    alt 验证失败
        BS-->>GW: 400 人机验证失败
    end

    BS->>MySQL: SELECT * FROM pcd_user_info_table WHERE account=? OR phone=?
    MySQL-->>BS: 用户数据 (BCrypt 密码哈希)

    BS->>BS: BCrypt.verify(password, hash)
    alt 密码不匹配
        BS->>Redis: INCR login:fail:{account} ← 失败计数器
        BS-->>GW: 401 账号或密码错误
        GW-->>Web: 401
    end

    BS->>BS: 签发 JWT (RSA-256, 24h 有效期)
    BS->>Redis: SET login:success:{account} ← 登录成功标记
    BS->>MySQL: INSERT pcd_login_audit_table (审计日志)
    BS-->>GW: 200 { token: "eyJhbG..." }
    GW-->>Web: 200 { code: 200, data: "eyJhbG..." }

    Web->>Web: localStorage.setItem('token', token)
    Web->>Web: $router.push('/home') ← 跳转主页
```

### 文件分片上传全流程

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Web as 🌐 Vue前端
    participant GW as 🚪 Gateway
    participant BS as ⚙ 业务服务
    participant FS as 📁 文件服务
    participant Redis as 📦 Redis
    participant MQ as 🐇 RabbitMQ
    participant Disk as 💾 磁盘

    Note over User,Disk: === 阶段1: 创建上传会话 ===

    User->>Web: 选择文件 / 拖拽文件
    Web->>Web: 计算文件 SHA-256
    Web->>Web: 文件切片 (5MB/chunk)

    Web->>GW: POST /api/v1/business/uploads/<br/>{total_chunks, file_size, checksum, ...}
    GW->>BS: 转发 → 创建上传会话
    BS->>MySQL: INSERT pcd_uploads_session_table
    MySQL-->>BS: uploads_id (UUID)
    BS-->>Web: { data: "uploads_id" }

    Note over User,Disk: === 阶段2: 获取操作凭证 ===

    Web->>GW: POST /api/v1/files/operation-tokens<br/>{operation_type: "upload", file_name: "xxx"}
    GW->>FS: 转发 → 签发JWT操作凭证
    FS->>FS: 签发 JWT (jti, sub, rlimit=300, exp=1h)
    FS-->>Web: { operation_token: "eyJ..." }

    Note over User,Disk: === 阶段3: 分片上传 (带断点续传) ===

    loop 每个分片 (并发3个)
        Web->>FS: POST /api/v1/files/uploads/{id}/chunks<br/>X-Operation-Token: xxx<br/>X-Chunk-Index: 5<br/>Body: [binary data]
        FS->>Redis: Lua原子脚本: 并发检查 + 速率检查
        alt 分片已存在 (断点续传)
            FS-->>Web: 200 { status: "already_uploaded" }
        else 正常上传
            FS->>Disk: 写入 {upload_dir}/{id}-5.part
            FS->>BS: POST /business/internal/storage/uploads/{id}/chunks/5/complete
            BS->>MySQL: UPDATE pcd_upload_chunks_table SET status='completed'
            FS-->>Web: 200 { status: "uploaded" }
        end
    end

    Note over User,Disk: === 阶段4: 完成通知 & 异步处理 ===

    Web->>BS: POST /api/v1/business/uploads/{id}/complete
    BS->>MySQL: UPDATE pcd_uploads_session_table SET status='merging'
    BS->>MQ: 发布消息 → pcd.file.process.queue
    BS-->>Web: 200 { status: "processing" }

    MQ->>FS: 消费消息 {uploads_id, action: "process"}
    FS->>Disk: 合并所有 .part 文件
    FS->>Disk: SHA-256 校验
    FS->>Disk: 病毒扫描
    FS->>Disk: 生成缩略图 (libvips)
    FS->>BS: 通知处理完成
    BS->>MySQL: UPDATE file SET status='active'
    FS->>MQ: ACK 消息

    Note over Web: 前端轮询 /api/v1/business/uploads/{id}?status
    Web-->>User: ✅ 上传完成
```

### 目录树闭包表查询

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Web as 🌐 Vue前端
    participant GW as 🚪 Gateway
    participant BS as ⚙ 业务服务
    participant MySQL as 🗄 MySQL

    User->>Web: 点击文件夹 A
    Web->>GW: GET /api/v1/business/nodes/{node_id}/children/paged
    GW->>BS: 转发 + X-User-Id

    BS->>MySQL: SELECT * FROM pcd_directory_tree_table<br/>WHERE node_parent_id = {node_id}<br/>AND node_user_id = {user_id}
    MySQL-->>BS: 当前目录下的子节点列表

    BS->>MySQL: SELECT COUNT(*) FROM pcd_file_info_table<br/>WHERE file_node_id = node_id AND file_status = 'active'
    MySQL-->>BS: 各文件夹内文件数量

    BS->>BS: 组装 NodeVO: {node_id, node_type, node_name, node_size}
    BS-->>GW: 200 { items: [...], total: N, page: 1 }
    GW-->>Web: 200 OK
    Web->>Web: 渲染文件列表
```

### 用户注册全流程

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Web as 🌐 Vue前端
    participant GW as 🚪 Gateway
    participant BS as ⚙ 业务服务
    participant Redis as 📦 Redis
    participant MySQL as 🗄 MySQL
    participant MQ as 🐇 RabbitMQ

    User->>Web: 填写手机号/密码/验证码 + 滑块验证
    Web->>Web: 获取 Turnstile Token
    Web->>GW: POST /api/v1/business/users/
    GW->>GW: 白名单路径 → 放行
    GW->>BS: 转发 POST /business/users/

    BS->>Redis: 检查IP注册频率 🔑 register:limit:{ip}
    alt 超过限制 (10次/1h)
        BS-->>GW: 429 注册过于频繁
        GW-->>Web: 429
    end

    BS->>BS: 验证 Turnstile Token
    alt 验证失败
        BS-->>GW: 400 人机验证失败
    end

    BS->>BS: 校验验证码 (code) 有效性
    alt 验证码错误或过期
        BS-->>GW: 400 验证码错误或已过期
    end

    BS->>MySQL: SELECT 检查手机号是否已注册
    alt 手机号已注册
        BS-->>GW: 409 该手机号已被注册
    end

    BS->>BS: BCrypt.hash(password) 密码加密
    BS->>MySQL: BEGIN TRANSACTION
    BS->>MySQL: INSERT pcd_user_info_table
    BS->>MySQL: INSERT pcd_user_quota_table (默认10GB配额)
    BS->>MySQL: INSERT pcd_directory_tree_table (根目录节点)
    BS->>MySQL: INSERT pcd_directory_closure_table (根节点自引用)
    BS->>MySQL: COMMIT

    BS->>BS: 生成唯一账号 (从手机号派生)
    BS->>MQ: 发布 welcome.email / welcome.sms 消息

    BS-->>GW: 200 { data: "auto_generated_account" }
    GW-->>Web: 200 { code: 200, data: "user_abc123" }
    Web-->>User: ✅ 注册成功 → 跳转登录页
```

### 文件删除与回收站恢复

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Web as 🌐 Vue前端
    participant GW as 🚪 Gateway
    participant BS as ⚙ 业务服务
    participant MySQL as 🗄 MySQL
    participant MQ as 🐇 RabbitMQ
    participant FS as 📁 文件服务
    participant Disk as 💾 磁盘

    Note over User,MySQL: === 阶段1: 删除文件（软删除 → 回收站）===

    User->>Web: 右键文件 → 删除
    Web->>GW: DELETE /api/v1/business/files/{file_id}
    GW->>BS: 转发 + X-User-Id

    BS->>MySQL: BEGIN TRANSACTION
    BS->>MySQL: UPDATE pcd_file_info_table SET file_status='trashed'
    BS->>MySQL: INSERT pcd_trash_target_table<br/>(trash_target_id, trash_target_type, trash_user_id,<br/> trash_file_name, trash_file_type, trash_file_size,<br/> trash_original_node_id, trash_expires_at)
    BS->>MySQL: UPDATE pcd_user_quota_table<br/>SET quota_used_capacity -= file_size,<br/>quota_file_count -= 1, quota_version += 1
    BS->>MySQL: COMMIT
    BS-->>GW: 200 OK
    GW-->>Web: 200 删除成功
    Web->>Web: Toast: "已移入回收站，30天后自动清理"
    Web-->>User: ✅ 文件已移至回收站

    Note over User,Disk: === 阶段2: 恢复文件（从回收站还原）===

    User->>Web: 进入回收站 → 点击恢复
    Web->>GW: POST /api/v1/business/trash/{trash_id}/restore
    GW->>BS: 转发 + X-User-Id

    BS->>MySQL: SELECT * FROM pcd_trash_target_table WHERE trash_id=?
    MySQL-->>BS: 回收站记录 {file_id, original_node_id}

    BS->>MySQL: BEGIN TRANSACTION
    BS->>MySQL: UPDATE pcd_file_info_table<br/>SET file_status='active', file_node_id=original_node_id
    BS->>MySQL: DELETE FROM pcd_trash_target_table WHERE trash_id=?
    BS->>MySQL: UPDATE pcd_user_quota_table<br/>SET quota_used_capacity += file_size,<br/>quota_file_count += 1, quota_version += 1
    BS->>MySQL: COMMIT
    BS-->>GW: 200 OK
    GW-->>Web: 200 恢复成功
    Web-->>User: ✅ 文件已恢复至原目录

    Note over User,Disk: === 阶段3: 彻底删除（物理删除）===

    User->>Web: 回收站 → 彻底删除
    Web->>GW: DELETE /api/v1/business/trash/{trash_id}
    GW->>BS: 转发 + X-User-Id

    BS->>MySQL: SELECT * FROM pcd_trash_target_table WHERE trash_id=?
    MySQL-->>BS: 回收站记录

    BS->>MySQL: BEGIN TRANSACTION
    BS->>MySQL: DELETE FROM pcd_file_info_table WHERE file_id=?
    BS->>MySQL: DELETE FROM pcd_trash_target_table WHERE trash_id=?
    BS->>MySQL: COMMIT

    BS->>MQ: 发布消息 → pcd.file.delete.queue<br/>{file_id, storage_path}
    BS-->>GW: 200 OK
    GW-->>Web: 200 彻底删除成功

    MQ->>FS: 消费删除消息
    FS->>Disk: 删除物理文件 + 缩略图缓存
    FS->>MQ: ACK 消息
```

### 文件预览全流程

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Web as 🌐 Vue前端
    participant GW as 🚪 Gateway
    participant FS as 📁 文件服务
    participant BS as ⚙ 业务服务
    participant Redis as 📦 Redis
    participant Disk as 💾 磁盘

    User->>Web: 双击文件 / 点击预览

    Note over Web,FS: === 阶段1: 元数据加载 ===

    Web->>GW: GET /api/v1/business/files/{file_id}
    GW->>BS: 转发 + X-User-Id
    BS->>MySQL: SELECT * FROM pcd_file_info_table WHERE file_id=?
    MySQL-->>BS: {file_type, file_size, file_name}
    BS-->>Web: 200 { type: "image/png", size: 512000, ... }

    Note over Web,FS: === 阶段2: 根据文件类型选择预览策略 ===

    alt 图片预览 (image/jpeg, image/png, image/webp)
        Web->>GW: GET /api/v1/files/thumbnails/{file_id}
        GW->>FS: 转发 + X-User-Id
        FS->>Redis: GET thumbnail:{file_id}
        alt 缓存命中
            Redis-->>FS: base64 缩略图
            FS-->>Web: 200 { thumbnail: "base64..." }
        else 缓存未命中
            FS->>BS: GET /internal/storage/files/{file_id}/metadata
            BS-->>FS: { storage_path }
            FS->>Disk: pyvips 读取图片 → 缩放到 800px
            FS->>Redis: SETEX thumbnail:{file_id} 3600 base64_data
            FS-->>Web: 200 { thumbnail: "...", width: 800, height: 600 }
        end
        Web->>Web: ImagePreview.vue 渲染全分辨率

    else 视频预览 (video/mp4, video/webm)
        Web->>GW: POST /api/v1/files/operation-tokens<br/>{file_id, operation_type: "stream"}
        GW->>FS: 转发 + X-User-Id

        FS->>BS: 验证文件存在 + 权限
        BS-->>FS: 200 OK
        FS->>FS: 签发 stream JWT (rlimit: 300)
        FS-->>Web: 200 { operation_token }

        Web->>FS: GET /files/files/{file_id}/content<br/>X-Operation-Token + Range: bytes=0-1048576
        FS->>Disk: 流式读取视频数据
        FS-->>Web: 206 Partial Content (首1MB)
        Web->>Web: VideoPreview.vue 使用 HLS/dash 流式播放

    else PDF 预览
        Web->>GW: POST /api/v1/files/operation-tokens<br/>{file_id, operation_type: "preview"}
        FS-->>Web: 200 { operation_token }
        Web->>FS: GET /files/files/{file_id}/content<br/>X-Operation-Token
        FS->>Disk: 流式读取整个 PDF
        FS-->>Web: 200 (完整PDF二进制)
        Web->>Web: PdfPreview.vue 使用 pdf.js 渲染

    else 文本/代码预览 (text/*, application/json, etc.)
        Web->>GW: POST /api/v1/files/operation-tokens
        FS-->>Web: 200 { operation_token }
        Web->>FS: GET /files/files/{file_id}/content<br/>X-Operation-Token
        FS->>Disk: 流式读取文本内容
        FS-->>Web: 200 text/plain
        Web->>Web: CodePreview.vue 语法高亮渲染 (Prism.js)

    else Office文档预览 (docx, xlsx, pptx)
        Web->>Web: OfficePreview.vue<br/>通过 Microsoft Office Online / Google Docs 在线预览
    end
```

### 文件移动/复制流程

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Web as 🌐 Vue前端
    participant Dialog as MoveCopyDialog
    participant Picker as TreeFolderPicker
    participant GW as 🚪 Gateway
    participant BS as ⚙ 业务服务
    participant MySQL as 🗄 MySQL

    User->>Web: 右键文件 → 移动到...
    Web->>Dialog: 打开 MoveCopyDialog<br/>props: { fileIds, mode: 'move' }

    Dialog->>GW: GET /api/v1/business/nodes/root/children/paged
    GW->>BS: 获取根目录子节点
    BS->>MySQL: SELECT + Closure Table 查询
    MySQL-->>BS: 根目录内容
    BS-->>Dialog: 200 { items: [{id, type: folder}, ...] }
    Dialog->>Picker: 初始化 folderTree[[{...}, {...}]]

    Note over Picker: 用户浏览目标目录
    User->>Picker: 点击 "我的文档" 文件夹
    Picker->>Dialog: @select-node (node_id, colIndex)

    Dialog->>GW: GET /api/v1/business/nodes/{node_id}/children/paged
    GW->>BS: 转发
    BS->>MySQL: SELECT WHERE node_parent_id=? AND node_user_id=?
    MySQL-->>BS: 该文件夹子节点
    BS-->>Dialog: 200 { items: [...] }
    Dialog->>Picker: 追加新列 folderTree.push(children)
    Picker->>Picker: 自动滚动到最新列

    User->>Picker: 选择目标文件夹 → 点击确定
    Picker->>Dialog: @confirm (selectedFolderId)

    alt 模式 = 移动
        Dialog->>GW: PATCH /api/v1/business/files/{file_id}/position<br/>{ target_node_id: selectedFolderId }
        GW->>BS: 转发
        BS->>MySQL: BEGIN TRANSACTION
        BS->>MySQL: UPDATE pcd_file_info_table SET file_node_id=?
        BS->>MySQL: COMMIT
        BS-->>Dialog: 200 OK
    else 模式 = 复制
        Dialog->>GW: POST /api/v1/business/files/{file_id}/copy<br/>{ target_node_id: selectedFolderId }
        GW->>BS: 转发
        BS->>MySQL: BEGIN TRANSACTION
        BS->>MySQL: INSERT pcd_file_info_table (新file_id, 新node_id)
        BS->>MySQL: UPDATE pcd_user_quota_table (增加用量)
        BS->>MySQL: COMMIT
        BS-->>Dialog: 200 OK
    end

    Dialog->>Web: close + refresh
    Web->>Web: 刷新当前文件列表
    Web-->>User: ✅ 操作成功
```

### 流式下载（支持断点续传）

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Web as 🌐 Vue前端
    participant Store as downloaderStore
    participant GW as 🚪 Gateway
    participant FS as 📁 文件服务
    participant BS as ⚙ 业务服务
    participant Redis as 📦 Redis
    participant Disk as 💾 磁盘

    User->>Web: 点击下载文件 (100MB)
    Web->>Store: startDownload(fileId)

    Note over Web,FS: === 阶段1: 获取文件元数据 ===
    Store->>GW: GET /api/v1/business/files/{file_id}
    GW->>BS: 转发
    BS-->>Store: 200 { name, size: 104857600, type }

    Note over Store,FS: === 阶段2: 申请操作凭证 ===
    Store->>GW: POST /api/v1/files/operation-tokens<br/>{file_id, operation_type: "download"}
    GW->>FS: 转发 + X-User-Id
    FS->>BS: 验证文件权限
    BS-->>FS: 200 OK
    FS->>FS: 签发 JWT (rlimit: N个分片)
    FS-->>Store: 200 { operation_token }

    Note over Store,Disk: === 阶段3: 并发分段下载 ===
    Store->>Store: 计算分段：100MB → 20个5MB分片

    par 并发下载 (4个并发)
        Store->>FS: GET /files/files/{file_id}/content<br/>X-Operation-Token<br/>Range: bytes=0-5242879
        FS->>Redis: Lua: INCR total:{jti}, INCR concurrency:{jti}
        alt 超过并发限制
            FS-->>Store: 429 → 等待后重试
        end
        FS->>Disk: aiofiles seek(0) + read(5242880)
        Disk-->>FS: 分片数据
        FS-->>Store: 206 { data: bytes, offset: 0 }

    and
        Store->>FS: Range: bytes=5242880-10485759
        FS-->>Store: 206 { data: bytes, offset: 5242880 }

    and
        Store->>FS: Range: bytes=10485760-15728639
        FS-->>Store: 206 { data: bytes, offset: 10485760 }

    and
        Store->>FS: Range: bytes=15728640-20971519
        FS-->>Store: 206 { data: bytes, offset: 15728640 }
    end

    Note over Store: === 阶段4: 组装与保存 ===
    Store->>Store: 按 offset 排序 all chunks
    Store->>Store: 合并 all chunks → Blob
    Store->>Store: 触发浏览器下载 (URL.createObjectURL)

    alt 下载中断 (网络断开)
        Store->>Store: 记录已完成的 chunk offsets
        Note over Store: 重试时跳过已完成的chunks
        Store->>FS: 继续下载剩余的 chunks
        FS-->>Store: 206 (剩余数据)
    end

    Store-->>Web: downloadProgress: 100%
    Web-->>User: ✅ 下载完成
```

### Docker Compose 部署架构

```mermaid
graph TB
    subgraph DockerHost["Docker Host"]
        subgraph Networks["网络"]
            FrontNet["🌐 frontend-net<br/>(对外暴露)"]
            BackNet["🔒 backend-net<br/>(内部通信)"]
        end

        subgraph Containers["容器"]
            Nginx["nginx:alpine<br/>🖥 Web Server<br/>Port: 80→80"]

            Gateway["Gateway Service<br/>🚪 Spring Cloud Gateway<br/>Port: 8080 (内部)"]

            Business["Business Service<br/>⚙ Spring Boot<br/>Port: 8081 (内部)"]

            FileService["File Service<br/>📁 FastAPI + Uvicorn<br/>Port: 8000 (内部)"]

            MySQL[("mysql:8.0<br/>🗄 MySQL<br/>Port: 3306 (内部)")]

            Redis[("redis:7-alpine<br/>📦 Redis<br/>Port: 6379 (内部)")]

            RabbitMQ[("rabbitmq:3-management<br/>🐇 RabbitMQ<br/>Port: 5672/15672")]
        end

        subgraph Volumes["数据卷"]
            MysqlVol["mysql-data"]
            UploadsVol["uploads-data"]
            ThumbsVol["thumbnails-data"]
        end
    end

    Internet((🌍 Internet)) -->|HTTPS:443| Nginx

    Nginx -->|"/api/*"| Gateway
    Nginx -->|"Static Files"| Nginx
    Nginx --> FrontNet

    Gateway --> BackNet
    Business --> BackNet
    FileService --> BackNet
    MySQL --> BackNet
    Redis --> BackNet
    RabbitMQ --> BackNet

    Gateway --> Business
    Gateway --> FileService
    Business -->|"内部API"| FileService
    Business --> MySQL
    Business --> Redis
    Business -->|"发布消息"| RabbitMQ
    FileService --> Redis
    FileService -->|"消费消息"| RabbitMQ
    FileService --> UploadsVol
    FileService --> ThumbsVol
    RabbitMQ -->|"处理消息"| FileService
    MySQL --> MysqlVol
```

### 前端组件架构全景

```mermaid
graph TB
    subgraph App["App.vue — 根组件"]
        Router["<router-view/>"]
    end

    subgraph AuthPages["认证页面 (无需登录)"]
        Login["LoginView.vue<br/>Three.js 3D背景<br/>GSAP 动画切换"]
        Register["RegisterView.vue<br/>与登录页联动动画"]
    end

    subgraph MainLayout["Layout.vue — 主布局 (需登录)"]
        Sidebar["Sidebar.vue<br/>导航菜单"]
        UserDropdown["UserDropdown.vue<br/>用户头像/下拉"]
        NotifCenter["NotificationCenter.vue<br/>通知面板"]
        Content["<router-view/> — 内容区"]
    end

    subgraph Pages["页面视图"]
        Dashboard["DashboardView.vue<br/>我的网盘 — 主文件浏览页"]
        Profile["ProfileView.vue<br/>个人中心 — 企业级UI"]
        Starred["StarredView.vue<br/>收藏文件"]
        Trash["TrashView.vue<br/>回收站"]
        Shares["SharesView.vue<br/>我的分享"]
        Transfers["TransfersView.vue<br/>传输记录"]
        Preview["FilePreviewView.vue<br/>文件预览页"]
    end

    subgraph FileComponents["文件操作子组件"]
        FileList["FileListView.vue"]
        FileGrid["FileGridView.vue"]
        PathNav["PathNavigator.vue<br/>面包屑导航"]
        RenameDialog["RenameDialog.vue"]
        MoveCopyDialog["MoveCopyDialog.vue"]
        TreePicker["TreeFolderPicker.vue<br/>分栏目录选择器"]
        BatchBar["BatchActionsBar.vue"]
        StorageInfo["StorageInfo.vue"]
        FileDetail["FileDetailDrawer.vue"]
    end

    subgraph UploadComponents["上传组件"]
        UploadPanel["UploadProgressPanel.vue"]
        FileUploader["FileUploader.vue<br/>(核心上传器)"]
    end

    subgraph PreviewComponents["预览组件"]
        ImagePreview["ImagePreview.vue"]
        VideoPreview["VideoPreview.vue"]
        AudioPreview["AudioPreview.vue"]
        PdfPreview["PdfPreview.vue"]
        CodePreview["CodePreview.vue"]
        OfficePreview["OfficePreview.vue"]
    end

    App --> Router
    Router --> AuthPages
    Router --> MainLayout

    MainLayout --> Sidebar
    MainLayout --> UserDropdown
    MainLayout --> NotifCenter
    MainLayout --> Content

    Content --> Pages

    Dashboard --> FileComponents
    Dashboard --> UploadComponents
    Dashboard --> UploadPanel
    Dashboard --> FileUploader

    Preview --> PreviewComponents

    MoveCopyDialog --> TreePicker

    style App fill:#e3f2fd
    style MainLayout fill:#fff3e0
    style AuthPages fill:#f3e5f5
    style Pages fill:#e8f5e9
    style FileComponents fill:#fce4ec
    style UploadComponents fill:#ede7f6
    style PreviewComponents fill:#e0f2f1
```

---

## 微服务间通信

```mermaid
graph LR
    subgraph "前端服务间调用"
        GW["🚪 Gateway<br/>:8080"]
        BS["⚙ Business<br/>:8081"]
        FS["📁 File<br/>:8000"]
    end

    GW -->|"外部请求<br/>JWT 鉴权"| BS
    GW -->|"外部请求<br/>凭证鉴权"| FS
    BS -->|"内部 API<br/>X-Internal 头"| FS

    subgraph "异步消息通信"
        MQ["🐇 RabbitMQ"]
        BS -->|"发布<br/>file.process.queue<br/>file.delete.queue<br/>welcome.email.queue<br/>welcome.sms.queue"| MQ
        MQ -->|"消费"| FS
    end
```

---

## 安全性设计全景

```mermaid
graph TB
    subgraph "安全防线"
        L1["🛡 第一道防线<br/>网关层"]
        L2["🛡 第二道防线<br/>业务服务层"]
        L3["🛡 第三道防线<br/>文件服务层"]
        L4["🛡 第四道防线<br/>数据层"]
    end

    L1 --> L1A["JWT 签名验证 (RSA-256)"]
    L1 --> L1B["分布式限流 (7条规则)"]
    L1 --> L1C["请求头清洗 (防伪造)"]
    L1 --> L1D["CORS 跨域控制"]

    L2 --> L2A["BCrypt 密码哈希"]
    L2 --> L2B["登录失败锁定"]
    L2 --> L2C["注册频率限制"]
    L2 --> L2D["Turnstile 人机验证"]
    L2 --> L2E["API 滥用防护"]
    L2 --> L2F["UUID 主键 (防遍历)"]
    L2 --> L2G["参数校验 (JSR-380)"]

    L3 --> L3A["操作凭证 JWT"]
    L3 --> L3B["多维度并发控制 (Lua)"]
    L3 --> L3C["下载授权 Opaque Token"]
    L3 --> L3D["文件完整性校验 (SHA-256)"]

    L4 --> L4A["外键级联约束"]
    L4 --> L4B["乐观锁 (版本号)"]
    L4 --> L4C["软删除 (回收站)"]
    L4 --> L4D["审计日志"]
```

---

## 项目结构

```
PrivateCloudDisk-project/
│
├── PrivateCloudDisk-web/                    # 🌐 Web 前端 (Vue 3 + Vite)
│   └── README.md
│
├── PrivateCloudDisk-admin-web/              # 🔧 管理后台 (React 19 + Ant Design)
│   └── README.md
│
├── PrivateCloudDisk-desktop/                # 💻 桌面客户端 (Electron + React)
│   └── README.md
│
├── PrivateCloudDisk-uni-app/                # 📱 跨端移动端 (uni-app + Vue 3)
│   └── README.md
│
├── PrivateCloudDisk-android/                # 🤖 Android 原生客户端 (Kotlin + Compose)
│   └── README.md
│
├── PrivateCloudDisk-ios/                    # 🍎 iOS 原生客户端 (SwiftUI)
│   └── README.md
│
├── PrivateCloudDisk-macos/                  # 🖥 macOS 原生客户端 (SwiftUI)
│   └── README.md
│
├── PrivateCloudDisk-win/                    # 🪟 Windows 原生客户端 (WPF + .NET)
│   └── README.md
│
├── PrivateCloudDisk-gateway-service/         # 🚪 API 网关 (Spring Cloud Gateway)
│   └── README.md
│
├── PrivateCloudDisk-platform-service/        # ⚙ 业务服务 (Spring Boot)
│   └── README.md
│
├── PrivateCloudDisk-shortage-service/        # 📁 文件服务 (FastAPI + Python)
│   └── README.md
│
├── PrivateCloudDisk-im/                      # 💬 即时通讯 (Spring Boot + Netty)
│   └── README.md
│
├── PrivateCloudDisk-db/                      # 🗄 数据库脚本 (MySQL)
│   ├── database_init.sql
│   └── README.md
│
├── PrivateCloudDisk-infra/                   # 🏗 基础设施配置 (Docker 中间件)
│   └── README.md
│
├── scripts/                                  # 🔨 脚本工具集
│   ├── init_database.sql                    #    完整数据库初始化 (19张表)
│   ├── generate_admin_password.py           #    密码哈希生成工具
│   ├── deploy.sh                            #    一键部署
│   ├── backup.sh                            #    数据备份
│   ├── rollback.sh                          #    备份回滚
│   └── README.md
│
├── docs/                                     # 📚 项目文档
│
├── docker-compose.yml                       # Docker Compose 编排
├── Makefile                                 # 常用命令快捷操作
├── .env.example                             # 环境变量模板
├── DEPLOYMENT.md                            # 部署文档
└── README.md                                # 📋 本文件
```

---

## 快速开始

### 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 18+ |
| Python | 3.11+ |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| RabbitMQ | 3.10+ |
| libvips | 8.10+ |

### 本地开发

```bash
# 1. 初始化数据库
mysql -u root -p < PrivateCloudDisk-db/database_init.sql

# 2. 启动 Redis & RabbitMQ
brew services start redis
brew services start rabbitmq

# 3. 启动业务服务 (:8081)
cd PrivateCloudDisk-platform-service
./gradlew bootRun

# 4. 启动文件服务 (:8000)
cd PrivateCloudDisk-shortage-service
uvicorn server:app --host 0.0.0.0 --port 8000 --reload

# 5. 启动网关 (:8080)
cd PrivateCloudDisk-gateway-service
./gradlew bootRun

# 6. 启动前端
cd PrivateCloudDisk-web
npm install && npm run dev
```

访问 `http://localhost:5173` 即可。

### Docker 一键部署

```bash
docker compose up -d
```

---

## 各子项目导航

| 子项目 | 技术栈 | 端口 | 说明 |
|--------|--------|------|------|
| [PrivateCloudDisk-web](./PrivateCloudDisk-web/) | Vue 3 + Vite + Tailwind CSS + Pinia | 5173 | Web 前端，文件浏览器/上传管理/个人中心 |
| [PrivateCloudDisk-admin-web](./PrivateCloudDisk-admin-web/) | React 19 + TypeScript + Ant Design 6 | 5174 | 管理后台，用户管理/审计/安全/系统配置 |
| [PrivateCloudDisk-desktop](./PrivateCloudDisk-desktop/) | Electron + React + macFUSE | - | 桌面客户端，虚拟磁盘挂载/文件管理 |
| [PrivateCloudDisk-uni-app](./PrivateCloudDisk-uni-app/) | uni-app (Vue 3) + uView Plus | - | 跨端移动端，iOS/Android/小程序/H5 |
| [PrivateCloudDisk-android](./PrivateCloudDisk-android/) | Kotlin + Jetpack Compose | - | Android 原生客户端 |
| [PrivateCloudDisk-ios](./PrivateCloudDisk-ios/) | SwiftUI + Combine | - | iOS 原生客户端 |
| [PrivateCloudDisk-macos](./PrivateCloudDisk-macos/) | SwiftUI + AppKit | - | macOS 原生客户端，虚拟磁盘/系统集成 |
| [PrivateCloudDisk-win](./PrivateCloudDisk-win/) | WPF + .NET 8.0 | - | Windows 原生客户端，虚拟磁盘/系统托盘 |
| [PrivateCloudDisk-gateway-service](./PrivateCloudDisk-gateway-service/) | Spring Cloud Gateway + WebFlux | 8080 | API 网关，JWT 认证/限流/路由 |
| [PrivateCloudDisk-platform-service](./PrivateCloudDisk-platform-service/) | Spring Boot 4.0.6 + MyBatis | 8081 | 核心业务，用户/文件/目录树/配额 |
| [PrivateCloudDisk-shortage-service](./PrivateCloudDisk-shortage-service/) | FastAPI + Uvicorn (Python) | 8000 | 文件处理，分片上传/流式下载/缩略图 |
| [PrivateCloudDisk-im](./PrivateCloudDisk-im/) | Spring Boot + Netty + WebRTC | - | 即时通讯，消息推送/音视频通话 |
| [PrivateCloudDisk-db](./PrivateCloudDisk-db/) | MySQL 8.0 DDL Scripts | 3306 | 数据库初始化脚本 |
| [PrivateCloudDisk-infra](./PrivateCloudDisk-infra/) | Docker 中间件配置 | - | 基础设施，MySQL/Redis/RabbitMQ 等 |
| [scripts](./scripts/) | Bash + Python + SQL | - | 运维工具集，部署/备份/密码生成 |