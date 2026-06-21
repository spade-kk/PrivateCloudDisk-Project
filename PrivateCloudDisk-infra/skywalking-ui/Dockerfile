# ============================================================
# SkyWalking UI — 分布式链路追踪可视化界面
# ============================================================
# 版本: 9.4.0
# 端口: 8080
# 默认账号: admin / admin
# ============================================================

FROM apache/skywalking-ui:9.4.0

LABEL maintainer="PrivateCloudDisk Team"
LABEL description="SkyWalking UI - Observability Dashboard"

# 环境变量
ENV SW_OAP_ADDRESS=http://skywalking-oap:12800 \
    SW_TIMEOUT=60000 \
    SW_OAP_MAX_QUERY_SIZE=2000 \
    JAVA_OPTS="-Xms256m -Xmx512m"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=30s \
    CMD wget -qO- http://localhost:8080 || exit 1

EXPOSE 8080