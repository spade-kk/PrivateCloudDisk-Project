FROM mysql:8.0

# 自定义 MySQL 配置（字符集、连接数、InnoDB 调优）
COPY my.cnf /etc/mysql/conf.d/my.cnf

# 初始化 SQL 脚本（建表 + 种子数据）
COPY init.sql /docker-entrypoint-initdb.d/10-platform-init.sql

# 插件生态 Sprint 0：独立服务数据库与最小权限账号初始化。
COPY 20-create-automation-databases.sh /docker-entrypoint-initdb.d/20-create-automation-databases.sh
RUN chmod 0755 /docker-entrypoint-initdb.d/20-create-automation-databases.sh
