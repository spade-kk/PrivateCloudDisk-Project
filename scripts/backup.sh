#!/bin/bash
# ============================================================
# PrivateCloudDisk - 备份脚本
# 用法: bash scripts/backup.sh
# 建议加入 crontab: 0 2 * * * /opt/privateclouddisk/scripts/backup.sh
# ============================================================

set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

# 加载环境变量
if [ -f .env ]; then source .env; fi

BACKUP_DIR="${BACKUP_DIR:-/backup/privateclouddisk}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p "$BACKUP_DIR"

echo -e "${GREEN}=== PrivateCloudDisk 备份 $(date) ===${NC}"

# ---- 1. MySQL 备份 ----
echo -e "${GREEN}[1/3]${NC} 备份 MySQL..."
DB_BACKUP="$BACKUP_DIR/db_$DATE.sql.gz"
docker compose exec -T mysql \
  mysqldump -u root -p"${MYSQL_ROOT_PASSWORD:-123456}" \
  --single-transaction --routines --triggers --events \
  private_cloud_disk 2>/dev/null | gzip > "$DB_BACKUP"

if [ -f "$DB_BACKUP" ]; then
  echo -e "  ${GREEN}[OK]${NC} 数据库备份: $(du -h "$DB_BACKUP" | cut -f1)"
else
  echo -e "  ${RED}[FAIL]${NC} 数据库备份失败"
fi

# ---- 2. 文件备份 (仅备份元数据, 不上传文件本身) ----
echo -e "${GREEN}[2/3]${NC} 备份文件元数据..."
UPLOADS_BACKUP="$BACKUP_DIR/uploads_$DATE.tar.gz"
docker compose run --rm -v uploads-data:/data -v "$BACKUP_DIR:/backup" alpine \
  tar czf "/backup/uploads_$DATE.tar.gz" -C /data . 2>/dev/null || {
  echo -e "  ${YELLOW}[SKIP]${NC} 文件数据备份跳过 (无数据或权限问题)"
}

if [ -f "$UPLOADS_BACKUP" ]; then
  echo -e "  ${GREEN}[OK]${NC} 文件备份: $(du -h "$UPLOADS_BACKUP" | cut -f1)"
fi

# ---- 3. 清理旧备份 ----
echo -e "${GREEN}[3/3]${NC} 清理 ${RETENTION_DAYS} 天前的备份..."
OLD_COUNT=$(find "$BACKUP_DIR" -type f -mtime +$RETENTION_DAYS | wc -l)
if [ "$OLD_COUNT" -gt 0 ]; then
  find "$BACKUP_DIR" -type f -mtime +$RETENTION_DAYS -delete
  echo -e "  ${GREEN}[OK]${NC} 已清理 $OLD_COUNT 个旧备份"
else
  echo "  无需要清理的旧备份"
fi

echo ""
echo -e "${GREEN}=== 备份完成 ===${NC}"
echo "备份目录: $BACKUP_DIR"
echo "当前备份:"
ls -lh "$BACKUP_DIR" | tail -5