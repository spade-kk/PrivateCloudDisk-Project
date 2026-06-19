# API 接口概览

## 1. 接口规范

### 1.1 基础信息

| 属性 | 值 |
|------|-----|
| 网关地址 | `http://localhost:8080` |
| 内容类型 | `application/json` |
| 字符编码 | UTF-8 |
| 认证方式 | Bearer JWT (Header: `Authorization: Bearer <token>`) |

### 1.2 统一响应格式

```json
{
  "code": 200,
  "message": null,
  "data": {}
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突 |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |

### 1.3 路由前缀

| 前缀 | 目标服务 | 说明 |
|------|----------|------|
| `/api/v1/business/**` | platform-service :8081 | 业务 API |
| `/api/v1/files/**` | shortage-service :8000 | 文件 API |
| `/api/v1/im/**` | im-platform | 即时通讯 API |

## 2. 用户模块 API

### 2.1 认证

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/business/users/login` | 用户登录 | 否 |
| POST | `/api/v1/business/users/` | 用户注册 | 否 |
| POST | `/api/v1/business/users/logout` | 用户登出 | 是 |

### 2.2 用户信息

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/business/users/profile` | 获取个人信息 | 是 |
| PUT | `/api/v1/business/users/profile` | 更新个人信息 | 是 |
| PUT | `/api/v1/business/users/password` | 修改密码 | 是 |
| PUT | `/api/v1/business/users/avatar` | 上传头像 | 是 |

### 2.3 验证

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/business/verification/send` | 发送验证码 | 否 |
| POST | `/api/v1/business/verification/verify` | 验证验证码 | 否 |

## 3. 文件模块 API

### 3.1 文件操作

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/business/files/{id}` | 获取文件信息 | 是 |
| DELETE | `/api/v1/business/files/{id}` | 删除文件 (移入回收站) | 是 |
| PATCH | `/api/v1/business/files/{id}/rename` | 重命名文件 | 是 |
| PATCH | `/api/v1/business/files/{id}/position` | 移动文件 | 是 |
| POST | `/api/v1/business/files/{id}/copy` | 复制文件 | 是 |

### 3.2 目录节点

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/business/nodes/{id}/children/paged` | 分页获取子节点 | 是 |
| POST | `/api/v1/business/nodes/` | 创建文件夹 | 是 |
| DELETE | `/api/v1/business/nodes/{id}` | 删除文件夹 | 是 |
| PATCH | `/api/v1/business/nodes/{id}/rename` | 重命名文件夹 | 是 |
| PATCH | `/api/v1/business/nodes/{id}/position` | 移动文件夹 | 是 |

## 4. 上传模块 API

### 4.1 上传会话

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/business/uploads/` | 创建上传会话 | 是 |
| GET | `/api/v1/business/uploads/{id}` | 查询上传状态 | 是 |
| POST | `/api/v1/business/uploads/{id}/complete` | 通知上传完成 | 是 |

### 4.2 操作凭证

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/files/operation-tokens` | 签发操作凭证 | 是 |
| DELETE | `/api/v1/files/operation-tokens/{id}` | 撤销操作凭证 | 是 |

### 4.3 文件分片

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/files/uploads/{id}/chunks` | 上传分片 | 凭证 |
| GET | `/api/v1/files/uploads/{id}/chunks` | 查询已上传分片 | 凭证 |

## 5. 下载模块 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/files/files/{id}/content` | 下载文件内容 (支持 Range) | 凭证 |
| GET | `/api/v1/files/files/{id}/metadata` | 获取文件元数据 | 是 |
| GET | `/api/v1/files/thumbnails/{id}` | 获取缩略图 | 是 |

## 6. 回收站模块 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/business/trash/` | 获取回收站列表 | 是 |
| POST | `/api/v1/business/trash/{id}/restore` | 恢复文件 | 是 |
| DELETE | `/api/v1/business/trash/{id}` | 彻底删除 | 是 |
| DELETE | `/api/v1/business/trash/clear` | 清空回收站 | 是 |

## 7. 收藏模块 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/business/stars/` | 获取收藏列表 | 是 |
| POST | `/api/v1/business/stars/{fileId}` | 添加收藏 | 是 |
| DELETE | `/api/v1/business/stars/{fileId}` | 取消收藏 | 是 |

## 8. 分享模块 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/business/shares/` | 创建分享链接 | 是 |
| GET | `/api/v1/business/shares/` | 获取我的分享 | 是 |
| DELETE | `/api/v1/business/shares/{id}` | 删除分享 | 是 |
| GET | `/api/v1/business/shares/{code}/access` | 访问分享内容 | 否 |

## 9. 配额模块 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/business/quotas/` | 获取配额信息 | 是 |

## 10. 即时通讯 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/im/messages/` | 发送消息 | 是 |
| GET | `/api/v1/im/messages/` | 获取消息历史 | 是 |
| GET | `/api/v1/im/conversations/` | 获取会话列表 | 是 |
| POST | `/api/v1/im/groups/` | 创建群组 | 是 |
| WS | `/ws/im` | WebSocket 长连接 | JWT |

## 11. 管理员 API

### 11.1 管理员认证

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/admin/auth/login` | 管理员登录 | 否 |

### 11.2 用户管理

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/admin/users/` | 用户列表 | 管理员 |
| GET | `/api/v1/admin/users/{id}` | 用户详情 | 管理员 |
| PUT | `/api/v1/admin/users/{id}/status` | 启用/禁用用户 | 管理员 |

### 11.3 仪表盘

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/admin/dashboard/` | 管理仪表盘数据 | 管理员 |

### 11.4 审计与安全

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/admin/audit-logs/` | 审计日志 | 管理员 |
| GET | `/api/v1/admin/security-events/` | 安全事件 | 管理员 |
| GET | `/api/v1/admin/ip-blacklist/` | IP 黑名单 | 管理员 |
| POST | `/api/v1/admin/ip-blacklist/` | 添加 IP 黑名单 | 管理员 |

## 12. 搜索 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/business/search/files` | 全文搜索文件 | 是 |
| GET | `/api/v1/business/search/files/basic` | 文件名搜索 | 是 |