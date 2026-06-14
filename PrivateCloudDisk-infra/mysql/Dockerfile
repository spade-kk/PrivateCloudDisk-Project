FROM mysql:8.0

# 自定义 MySQL 配置（字符集、连接数、InnoDB 调优）
COPY my.cnf /etc/mysql/conf.d/my.cnf

# 初始化 SQL 脚本（建表 + 种子数据）
COPY init.sql /docker-entrypoint-initdb.d/init.sql