#!/bin/bash
# ============================================================
# PrivateCloudDisk - 回滚脚本
# 用法: bash scripts/rollback.sh <backup-file>
# 示例: bash scripts/rollback.sh /backup/privateclouddisk/db_20240101_020000.sql.gz
# ============================================================

set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

if [ -f .env ]; then source .env; fi

BACKUP_FILE="$1"

if [ -z "$BACKUP_FILE" ]; then
  echo -e "${RED}用法: bash scripts/rollback.sh <backup-file>${NC}"
  echo ""
  echo "可用的备份文件:"
  ls -lht "${BACKUP_DIR:-/backup/privateclouddisk}/" 2>/dev/null | head -10
  echo ""
  echo "示例: bash scripts/rollback.sh /backup/privateclouddisk/db_20240101_020000.sql.gz"
  exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
  echo -e "${RED}[错误] 备份文件不存在: $BACKUP_FILE${NC}"
  exit 1
fi

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW} 警告: 即将回滚数据库到 $BACKUP_FILE${NC}"
echo -e "${YELLOW} 此操作将覆盖当前数据库中的所有数据!${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
read -p "确认回滚? 输入 YES 继续: " CONFIRM

if [ "$CONFIRM" != "YES" ]; then
  echo "已取消回滚"
  exit 0
fi

echo -e "${GREEN}[1/3]${NC} 先备份当前数据库..."
CURRENT_BACKUP="${BACKUP_DIR:-/backup/privateclouddisk}/db_before_rollback_$(date +%Y%m%d_%H%M%S).sql.gz"
docker compose exec -T mysql \
  mysqldump -u root -p"${MYSQL_ROOT_PASSWORD:-123456}" \
  --single-transaction --routines --triggers \
  private_cloud_disk 2>/dev/null | gzip > "$CURRENT_BACKUP"
echo "  当前数据已备份至: $CURRENT_BACKUP"

echo -e "${GREEN}[2/3]${NC} 回滚数据库..."
if [[ "$BACKUP_FILE" == *.gz ]]; then
  gunzip -c "$BACKUP_FILE" | docker compose exec -T mysql mysql -u root -p"${MYSQL_ROOT_PASSWORD:-123456}" private_cloud_disk
else
  docker compose exec -T mysql mysql -u root -p"${MYSQL_ROOT_PASSWORD:-123456}" private_cloud_disk < "$BACKUP_FILE"
fi

echo -e "${GREEN}[3/3]${NC} 重启业务服务..."
docker compose restart platform-service-backend gateway-service-backend

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN} 回滚完成!${NC}"
echo -e "${GREEN}========================================${NC}"
echo "如需撤销回滚, 恢复文件: $CURRENT_BACKUP"