# Scripts

项目脚本工具集，包含数据库初始化、测试 JWT 生成、客户端构建、下载部署、部署、备份、回滚和签名校验。脚本默认不替代生产发布流程，执行前请检查目标环境和环境变量。

---

## 脚本清单

| 脚本 | 用途 | 语言 |
|------|------|------|
| `init_database.sql` | 本地数据库初始化参考 | SQL |
| `gen_test_user_login_jwt.py` | 测试用户 JWT 生成 | Python 3 |
| `generate_admin_password.py` | 密码哈希生成工具 | Python 3 |
| `build-all-clients.sh` | 多客户端构建入口 | Bash |
| `deploy-downloads.sh` | 下载产物部署入口 | Bash |
| `deploy.sh` | 一键部署脚本 | Bash |
| `backup.sh` | 数据库与文件备份 | Bash |
| `rollback.sh` | 备份回滚脚本 | Bash |

---

## 使用说明

### 1. 数据库初始化

```bash
# 创建数据库并导入所有表结构和默认数据
mysql -u root -p < scripts/init_database.sql

# 脚本内容和迁移顺序以当前 SQL 文件为准，不在 README 中固化默认账号或密码
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
