# PrivateCloudDisk 文件服务

企业级 FastAPI 应用，提供文件上传、下载、缩略图生成等核心功能。

## 项目结构

```
app/
├── __init__.py                 # 应用包初始化
├── main.py                     # FastAPI 应用入口和生命周期管理
├── api/                        # API 路由层
│   ├── __init__.py
│   └── v1/                     # API v1 版本
│       ├── __init__.py
│       ├── router.py           # 路由聚合
│       └── endpoints/          # API 端点
│           ├── __init__.py
│           ├── operation_tokens.py  # 操作凭证接口
│           ├── files.py             # 文件下载、缩略图接口
│           ├── uploads.py           # 文件上传、合并接口
│           └── tasks.py             # 任务状态查询接口
├── core/                       # 核心功能层
│   ├── __init__.py
│   ├── config.py              # 配置管理（已存在）
│   ├── redis_client.py        # Redis 客户端和 Lua 脚本
│   ├── security.py            # JWT 操作凭证验证
│   ├── rate_limiter.py        # 多维度限流器
│   └── rabbitmq.py            # RabbitMQ 服务（已存在）
├── services/                   # 业务服务层
│   ├── __init__.py
│   └── thumbnail_service.py   # 缩略图生成服务
├── models/                     # 数据模型层
│   ├── __init__.py
│   └── schemas.py             # Pydantic 数据模型
├── middleware/                 # 中间件层
│   ├── __init__.py
│   └── timing.py              # 请求时间记录中间件
├── utils/                      # 工具函数层
│   ├── __init__.py
│   └── helpers.py             # 通用辅助函数
└── consumers/                  # 消息消费者层（已存在）
    ├── __init__.py
    ├── file_process_consumer.py
    └── file_delete_consumer.py
```

## 分层说明

### API 层 (`api/`)
- 负责处理 HTTP 请求和响应
- 参数验证和类型转换
- 调用服务层处理业务逻辑
- 不包含业务逻辑，只做请求转发

### 核心层 (`core/`)
- 提供基础设施功能
- Redis 客户端管理
- 安全验证（JWT）
- 限流控制
- 配置管理

### 服务层 (`services/`)
- 封装业务逻辑
- 可被多个 API 端点复用
- 调用核心层和外部服务

### 模型层 (`models/`)
- 定义数据结构
- Pydantic 模型用于请求/响应验证
- 数据传输对象（DTO）

### 中间件层 (`middleware/`)
- 请求预处理和后处理
- 日志记录
- 性能监控

### 工具层 (`utils/`)
- 通用辅助函数
- 与业务无关的工具方法

### 消费者层 (`consumers/`)
- 消息队列消费者
- 异步任务处理
- 文件处理流水线

## 启动方式

```bash
# 激活虚拟环境
source .venv/bin/activate

# 启动服务
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## API 文档

启动服务后访问：
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

## 架构优势

1. **职责分离**：每层职责明确，易于维护
2. **可测试性**：服务层可独立测试
3. **可扩展性**：新增功能只需添加对应模块
4. **可复用性**：服务层方法可被多处调用
5. **标准化**：遵循 FastAPI 最佳实践
