# Scripts

项目脚本工具集，包含数据库初始化、密码哈希生成、部署与备份等运维脚本。

---

## 脚本清单

| 脚本 | 用途 | 语言 |
|------|------|------|
| `init_database.sql` | 完整数据库初始化 (建表 + 默认数据) | SQL |
| `generate_admin_password.py` | 密码哈希生成工具 (二次哈希) | Python 3 |
| `deploy.sh` | 一键部署脚本 | Bash |
| `backup.sh` | 数据库与文件备份 | Bash |
| `rollback.sh` | 备份回滚脚本 | Bash |

---

## 使用说明

### 1. 数据库初始化

```bash
# 创建数据库并导入所有表结构和默认数据
mysql -u root -p < scripts/init_database.sql

# 脚本包含:
# - 19 张业务表完整建表语句
# - 超级管理员账号 (superadmin / admin123)
# - 测试用户账号 (pcd_test001 / test123456)
# - 测试用户根目录、闭包表、配额数据
```

### 2. 密码哈希生成

由于系统采用 **前端 PBKDF2 预哈希 + 后端 BCrypt 二次加密** 的双层密码架构，不能直接使用普通 BCrypt 工具生成密码哈希。使用此脚本生成符合加密流程的哈希值。

```bash
# 安装依赖
pip3 install bcrypt

# 生成密码哈希
python3 scripts/generate_admin_password.py 新密码

# 批量生成
python3 scripts/generate_admin_password.py --batch 密码1 密码2 密码3

# 交互式 (密码不显示在终端历史中)
python3 scripts/generate_admin_password.py
```

加密流程:
```
原始密码 → PBKDF2-SHA256(密码, pepper, 60万次迭代) → 64位 hex
                                                        ↓
                                            BCrypt(12 rounds, hex)
                                                        ↓
                                              存入数据库的哈希值
```

### 3. 部署脚本

```bash
# 一键部署
bash scripts/deploy.sh
```

### 4. 备份脚本

```bash
# 备份数据库和文件
bash scripts/backup.sh
```

### 5. 回滚脚本

```bash
# 回滚到指定备份
bash scripts/rollback.sh
```