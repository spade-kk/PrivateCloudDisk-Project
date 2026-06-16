#!/bin/bash
# ============================================================
# PrivateCloudDisk - 部署脚本
# 用法: bash scripts/deploy.sh [--no-backup] [--no-build]
# ============================================================

set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

NO_BACKUP=false
NO_BUILD=false

for arg in "$@"; do
  case "$arg" in
    --no-backup) NO_BACKUP=true ;;
    --no-build)  NO_BUILD=true ;;
  esac
done

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN} PrivateCloudDisk 生产环境部署${NC}"
echo -e "${GREEN}========================================${NC}"
echo "开始时间: $(date)"
echo ""

# ---- 1. 检查 .env 文件 ----
if [ ! -f .env ]; then
  echo -e "${RED}[错误] 未找到 .env 文件, 请从 .env.example 复制并配置${NC}"
  echo "  cp .env.example .env"
  echo "  vim .env"
  exit 1
fi
source .env
echo -e "${GREEN}[1/6]${NC} 环境变量已加载"

# ---- 2. 备份数据库 ----
if [ "$NO_BACKUP" = false ]; then
  echo -e "${GREEN}[2/6]${NC} 备份数据库..."
  BACKUP_FILE="$BACKUP_DIR/db_pre_deploy_$(date +%Y%m%d_%H%M%S).sql.gz"
  mkdir -p "$BACKUP_DIR"

  docker compose exec -T mysql \
    mysqldump -u root -p"${MYSQL_ROOT_PASSWORD}" \
    --single-transaction --routines --triggers \
    private_cloud_disk 2>/dev/null | gzip > "$BACKUP_FILE" || {
    echo -e "${YELLOW}[警告] 数据库备份失败, 继续部署...${NC}"
  }
  echo "  备份文件: $BACKUP_FILE"
else
  echo -e "${YELLOW}[2/6]${NC} 跳过备份 (--no-backup)"
fi

# ---- 3. 拉取最新代码 ----
echo -e "${GREEN}[3/6]${NC} 拉取最新代码..."
git pull origin main 2>/dev/null || echo -e "${YELLOW}  跳过 git pull (非 git 仓库或网络问题)${NC}"

# ---- 4. 构建镜像 ----
if [ "$NO_BUILD" = false ]; then
  echo -e "${GREEN}[4/6]${NC} 构建 Docker 镜像..."
  docker compose build --parallel \
    platform-service-backend \
    gateway-service-backend \
    file-service-backend \
    file-service-worker \
    frontend 2>&1 | tail -20
else
  echo -e "${YELLOW}[4/6]${NC} 跳过构建 (--no-build)"
fi

# ---- 5. 滚动更新 ----
echo -e "${GREEN}[5/6]${NC} 滚动更新服务..."
docker compose up -d --remove-orphans

# 等待健康检查
echo "  等待服务健康检查..."
sleep 5

# 检查关键服务状态
HEALTHY=true
for svc in mysql redis rabbitmq platform-service-backend gateway-service-backend; do
  STATUS=$(docker compose ps -q "$svc" 2>/dev/null | xargs docker inspect -f '{{.State.Health.Status}}' 2>/dev/null || echo "unknown")
  if [ "$STATUS" = "healthy" ] || [ "$STATUS" = "running" ]; then
    echo -e "  ${GREEN}[OK]${NC} $svc ($STATUS)"
  else
    echo -e "  ${RED}[FAIL]${NC} $svc ($STATUS)"
    HEALTHY=false
  fi
done

# ---- 6. 清理 ----
echo -e "${GREEN}[6/6]${NC} 清理旧镜像..."
docker image prune -f 2>/dev/null || true

echo ""
echo -e "${GREEN}========================================${NC}"
if [ "$HEALTHY" = true ]; then
  echo -e "${GREEN} 部署成功!${NC}"
else
  echo -e "${YELLOW} 部署完成, 但部分服务状态异常, 请检查日志:${NC}"
  echo "  docker compose logs -f"
fi
echo -e "${GREEN}========================================${NC}"
echo "完成时间: $(date)"
echo ""
echo "验证命令:"
echo "  curl http://localhost:8080/actuator/health"
echo "  curl http://localhost:8000/api/v1/health"