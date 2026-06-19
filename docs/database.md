# 数据库设计文档

## 1. 数据库概览

| 属性 | 值 |
|------|-----|
| 数据库名 | `private_cloud_disk` |
| 引擎 | InnoDB |
| 字符集 | utf8mb4 |
| 排序规则 | utf8mb4_unicode_ci |
| 主键策略 | BINARY(16) UUID (不可遍历) |
| 外键策略 | ON DELETE CASCADE |

## 2. 表结构总览

### 2.1 用户与认证 (5 张表)

| 表名 | 说明 | 主键 |
|------|------|------|
| `pcd_user_info_table` | 用户信息表 | `user_id` BINARY(16) |
| `pcd_user_device_table` | 用户登录设备表 | `device_id` BINARY(16) |
| `pcd_login_session_table` | 登录会话表 | `login_session_id` BINARY(16) |
| `pcd_login_audit_table` | 登录审计表 | `audit_id` BIGINT AUTO_INCREMENT |
| `pcd_admin_user_table` | 管理员用户表 | `admin_id` BINARY(16) |

### 2.2 目录树与文件 (3 张表)

| 表名 | 说明 | 主键 |
|------|------|------|
| `pcd_directory_tree_table` | 目录树节点表 | `node_id` BINARY(16) |
| `pcd_directory_closure_table` | 目录树闭包表 | `(ancestor_id, descendant_id)` |
| `pcd_file_info_table` | 文件信息表 | `file_id` BINARY(16) |

### 2.3 上传管理 (2 张表)

| 表名 | 说明 | 主键 |
|------|------|------|
| `pcd_uploads_session_table` | 上传会话表 | `uploads_id` BINARY(16) |
| `pcd_upload_chunks_table` | 文件切片表 | `(chunk_uploads_id, chunk_index)` |

### 2.4 回收站与收藏 (2 张表)

| 表名 | 说明 | 主键 |
|------|------|------|
| `pcd_trash_target_table` | 回收站表 | `trash_id` BIGINT AUTO_INCREMENT |
| `pcd_file_star_table` | 文件收藏表 | `star_id` BIGINT AUTO_INCREMENT |

### 2.5 配额与分享 (3 张表)

| 表名 | 说明 | 主键 |
|------|------|------|
| `pcd_user_quota_table` | 用户配额表 | `quota_id` BIGINT AUTO_INCREMENT |
| `pcd_user_quota_log_table` | 配额变更日志表 | `quota_log_id` BIGINT AUTO_INCREMENT |
| `pcd_sharing_Link_mange_table` | 分享链接表 | `sharing_link_id` BINARY(16) |

### 2.6 安全与管理 (4 张表)

| 表名 | 说明 | 主键 |
|------|------|------|
| `pcd_admin_audit_log_table` | 管理员审计日志 | `audit_log_id` BIGINT AUTO_INCREMENT |
| `pcd_security_event_table` | 安全事件表 | `event_id` BIGINT AUTO_INCREMENT |
| `pcd_ip_blacklist_table` | IP 黑名单表 | `blacklist_id` BIGINT AUTO_INCREMENT |
| `pcd_system_config_table` | 系统配置表 | `config_id` BIGINT AUTO_INCREMENT |

## 3. 核心设计：闭包表

### 3.1 设计原理

闭包表 (Closure Table) 用于高效管理目录树结构，通过 `(ancestor_id, descendant_id, depth)` 三元组表示节点间的所有祖先-后代关系。

### 3.2 表结构

```sql
CREATE TABLE pcd_directory_closure_table (
    user_id       BINARY(16) NOT NULL,
    ancestor_id   BINARY(16) NOT NULL,
    descendant_id BINARY(16) NOT NULL,
    depth         INT NOT NULL,
    PRIMARY KEY (ancestor_id, descendant_id),
    INDEX idx_depth (depth)
);
```

### 3.3 操作示例

**根节点自引用 (depth=0)**:
```
ancestor_id = root_id, descendant_id = root_id, depth = 0
```

**插入子节点 A 到根目录**:
```
(root, A, 1)           -- 根 → A, depth=1
(A, A, 0)              -- A 自引用
```

**查询某目录下所有子节点**:
```sql
SELECT descendant_id FROM pcd_directory_closure_table
WHERE ancestor_id = ? AND depth > 0;
```

**查询某节点的所有祖先**:
```sql
SELECT ancestor_id FROM pcd_directory_closure_table
WHERE descendant_id = ? AND depth > 0
ORDER BY depth DESC;
```

### 3.4 优势

- 查询子树 O(1) 复杂度
- 支持任意深度层级
- 无需递归查询
- 写入时自动维护闭包关系

## 4. 索引策略

### 4.1 用户表索引

| 索引 | 类型 | 字段 |
|------|------|------|
| `user_account` | UNIQUE | 用户账号 |
| `user_phone_number` | UNIQUE | 手机号 |
| `user_email` | UNIQUE | 邮箱 |

### 4.2 文件表索引

| 索引 | 类型 | 字段 |
|------|------|------|
| `uk_file_info` | UNIQUE | (file_id, file_author_id, file_node_id) |
| `idx_file_node_status` | INDEX | (file_node_id, file_status) |
| `idx_file_author` | INDEX | (file_author_id) |

### 4.3 回收站索引

| 索引 | 类型 | 字段 |
|------|------|------|
| `idx_user_deleted` | INDEX | (trash_user_id, trash_deleted_at) |
| `idx_expires` | INDEX | (trash_expires_at) |

## 5. UUID 主键策略

### 5.1 使用方式

```sql
-- 插入时
INSERT INTO pcd_user_info_table (user_id, ...)
VALUES (UNHEX(REPLACE('a1b2c3d4-e5f6-7890-abcd-ef1234567890', '-', '')), ...);

-- 查询时
SELECT HEX(user_id) AS user_id, ... FROM pcd_user_info_table;
```

### 5.2 优势

- 不可遍历，防止 ID 枚举攻击
- 分布式环境无需协调 ID 生成
- 应用层生成，减少数据库压力

## 6. 乐观锁设计

配额表使用版本号实现乐观锁，防止并发更新冲突：

```sql
UPDATE pcd_user_quota_table
SET quota_used_capacity = quota_used_capacity + ?,
    quota_version = quota_version + 1
WHERE quota_user_id = ? AND quota_version = ?;
```

如 `affected_rows = 0`，说明版本号已变更，需重试。

## 7. 初始化脚本

完整建表 + 默认数据初始化脚本位于 `scripts/init_database.sql`，包含：

- 19 张表完整 DDL
- 超级管理员账号 (superadmin / admin123)
- 测试用户 (pcd_test001 / test123456)
- 测试用户根目录 + 闭包表 + 配额

```bash
mysql -u root -p < scripts/init_database.sql
```