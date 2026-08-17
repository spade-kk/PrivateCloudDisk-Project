#!/bin/sh
set -eu

# 插件生态 Sprint 0：为插件、自动化、工作流与调度服务建立独立 Schema 和最小权限账号。
# 该脚本只在 MySQL 数据卷首次初始化时执行；存量环境请使用运维迁移流程创建账号，
# 不得为了重跑初始化脚本删除生产数据卷。
: "${MYSQL_ROOT_PASSWORD:?必须配置 MYSQL_ROOT_PASSWORD}"
: "${PLUGIN_DATASOURCE_PASSWORD:?必须配置 PLUGIN_DATASOURCE_PASSWORD}"
: "${AUTOMATION_DATASOURCE_PASSWORD:?必须配置 AUTOMATION_DATASOURCE_PASSWORD}"
: "${WORKFLOW_DATASOURCE_PASSWORD:?必须配置 WORKFLOW_DATASOURCE_PASSWORD}"
: "${CLOUDFLOW_DATASOURCE_PASSWORD:?必须配置 CLOUDFLOW_DATASOURCE_PASSWORD}"
: "${SCHEDULER_DATASOURCE_PASSWORD:?必须配置 SCHEDULER_DATASOURCE_PASSWORD}"
: "${CLIENT_REGISTRATION_DB_PASSWORD:?必须配置 CLIENT_REGISTRATION_DB_PASSWORD}"

escape_sql_literal() {
  printf '%s' "$1" | sed "s/'/''/g"
}

plugin_password="$(escape_sql_literal "$PLUGIN_DATASOURCE_PASSWORD")"
automation_password="$(escape_sql_literal "$AUTOMATION_DATASOURCE_PASSWORD")"
workflow_password="$(escape_sql_literal "$WORKFLOW_DATASOURCE_PASSWORD")"
cloudflow_password="$(escape_sql_literal "$CLOUDFLOW_DATASOURCE_PASSWORD")"
scheduler_password="$(escape_sql_literal "$SCHEDULER_DATASOURCE_PASSWORD")"
client_registration_password="$(escape_sql_literal "$CLIENT_REGISTRATION_DB_PASSWORD")"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE DATABASE IF NOT EXISTS pcd_plugin
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS pcd_automation
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS pcd_workflow
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS pcd_cloudflow
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS pcd_scheduler
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'pcd_plugin'@'%' IDENTIFIED BY '${plugin_password}';
ALTER USER 'pcd_plugin'@'%' IDENTIFIED BY '${plugin_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON pcd_plugin.* TO 'pcd_plugin'@'%';

CREATE USER IF NOT EXISTS 'pcd_automation'@'%' IDENTIFIED BY '${automation_password}';
ALTER USER 'pcd_automation'@'%' IDENTIFIED BY '${automation_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON pcd_automation.* TO 'pcd_automation'@'%';

CREATE USER IF NOT EXISTS 'pcd_workflow'@'%' IDENTIFIED BY '${workflow_password}';
ALTER USER 'pcd_workflow'@'%' IDENTIFIED BY '${workflow_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON pcd_workflow.* TO 'pcd_workflow'@'%';

CREATE USER IF NOT EXISTS 'pcd_cloudflow'@'%' IDENTIFIED BY '${cloudflow_password}';
ALTER USER 'pcd_cloudflow'@'%' IDENTIFIED BY '${cloudflow_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON pcd_cloudflow.* TO 'pcd_cloudflow'@'%';

CREATE USER IF NOT EXISTS 'pcd_scheduler'@'%' IDENTIFIED BY '${scheduler_password}';
ALTER USER 'pcd_scheduler'@'%' IDENTIFIED BY '${scheduler_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON pcd_scheduler.* TO 'pcd_scheduler'@'%';

-- 客户端注册仍使用既有主库身份表；独立账号避免服务持有 root 凭证。
CREATE USER IF NOT EXISTS 'pcd_client'@'%' IDENTIFIED BY '${client_registration_password}';
ALTER USER 'pcd_client'@'%' IDENTIFIED BY '${client_registration_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON private_cloud_disk.* TO 'pcd_client'@'%';
FLUSH PRIVILEGES;
EOSQL
