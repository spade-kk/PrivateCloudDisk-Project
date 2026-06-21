# ============================================================
# SkyWalking OAP Server — 分布式链路追踪
# ============================================================
# 版本: 9.4.0 (推荐企业稳定版)
# 端口:
#   11800 — gRPC（Java Agent 上报）
#   12800 — HTTP（Python Agent 等上报）
#   1234  — HTTP（Prometheus 指标拉取，可选）
# ============================================================

FROM apache/skywalking-oap-server:9.4.0

LABEL maintainer="PrivateCloudDisk Team"
LABEL description="SkyWalking OAP Server - Observability Analysis Platform"

# 环境变量（可通过 docker-compose 覆盖）
ENV SW_STORAGE=elasticsearch \
    SW_STORAGE_ES_CLUSTER_NODES=${SW_ES_HOST:-opensearch:9200} \
    SW_STORAGE_ES_USER=${SW_ES_USER:-admin} \
    SW_STORAGE_ES_PASSWORD=${SW_ES_PASSWORD:-MySecureP@ssw0rd} \
    SW_STORAGE_ES_SSL_ENABLED=true \
    SW_STORAGE_ES_TRUST_CA_PATH="" \
    SW_STORAGE_ES_INDEX_SHARDS_NUMBER=1 \
    SW_STORAGE_ES_INDEX_REPLICAS_NUMBER=0 \
    SW_STORAGE_ES_BULK_ACTIONS=2000 \
    SW_STORAGE_ES_BULK_SIZE=20 \
    SW_STORAGE_ES_FLUSH_INTERVAL=10 \
    SW_STORAGE_ES_CONCURRENT_REQUESTS=2 \
    SW_CORE_RECORD_DATA_TTL=7 \
    SW_CORE_METRICS_DATA_TTL=7 \
    SW_CORE_TRACE_DATA_TTL=7 \
    SW_CORE_GRPC_PORT=11800 \
    SW_CORE_REST_PORT=12800 \
    SW_CORE_GRPC_SSL_ENABLED=false \
    SW_CORE_REST_SSL_ENABLED=false \
    SW_CORE_DEFAULT_LOG_LEVEL=INFO \
    SW_CORE_DEFAULT_SERVICE_LOG_LEVEL=INFO \
    SW_TELEMETRY=prometheus \
    SW_TELEMETRY_PROMETHEUS_HOST=0.0.0.0 \
    SW_TELEMETRY_PROMETHEUS_PORT=1234 \
    SW_HEALTH_CHECKER_ENABLED=true \
    SW_ALARM=default \
    JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=60s \
    CMD wget -qO- http://localhost:12800/healthz || exit 1

EXPOSE 11800 12800 1234