# ============================================================
# IM Server — Docker 多阶段构建（含 SkyWalking Agent）
# ============================================================
# 阶段 1：Maven 编译（JDK 18 + Maven 3.8）
# 阶段 2：下载 SkyWalking Java Agent
# 阶段 3：JRE 运行时（精简镜像）
# ============================================================

# ---- 阶段 1：构建 ----
FROM maven:3.9.9-eclipse-temurin-18 AS builder

WORKDIR /build

# 1. 先复制父 POM 和所有模块的 pom.xml（利用 Docker 层缓存）
COPY pom.xml .
COPY im-common/pom.xml im-common/
COPY im-platform/pom.xml im-platform/
COPY im-server/pom.xml im-server/
COPY im-client/pom.xml im-client/

# 2. 下载依赖（此层在 pom.xml 不变时会被缓存）
RUN mvn dependency:go-offline -pl im-server -am -B -q || true

# 3. 复制全部源码
COPY im-common/src im-common/src/
COPY im-server/src im-server/src/

# 4. 编译 im-server（同时编译其依赖模块 im-common）
RUN mvn clean package -pl im-server -am -DskipTests -B -q

# ---- 阶段 2：下载 SkyWalking Agent ----
FROM eclipse-temurin:18-jdk AS agent-downloader

WORKDIR /agent
RUN apt-get update && apt-get install -y curl && \
    curl -L --retry 3 -o skywalking-agent.tgz \
    https://archive.apache.org/dist/skywalking/java-agent/9.4.0/apache-skywalking-java-agent-9.4.0.tgz && \
    tar -xzf skywalking-agent.tgz --strip-components=1 && \
    rm skywalking-agent.tgz

# ---- 阶段 3：运行 ----
FROM eclipse-temurin:18-jre

WORKDIR /app

# 创建非 root 用户
RUN groupadd -r appuser && useradd -r -g appuser -d /app -s /sbin/nologin appuser

# 复制 SkyWalking Agent
COPY --from=agent-downloader /agent /app/skywalking-agent

# 复制构建产物
COPY --from=builder /build/im-server/target/*.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs && chown -R appuser:appuser /app

# 切换到非 root 用户
USER appuser

# HTTP 管理端口 + WebSocket 端口
EXPOSE 8089 9090

# JVM 参数
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/"

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
  CMD curl -f http://localhost:8089/actuator/health || exit 1

# SkyWalking Agent 参数
ENTRYPOINT ["sh", "-c", "\
java ${JAVA_OPTS} \
  -javaagent:/app/skywalking-agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_AGENT_NAME:-PrivateCloudDisk-im-server} \
  -Dskywalking.collector.backend_service=${SW_AGENT_BACKEND:-skywalking-oap:11800} \
  -Dskywalking.agent.namespace=${SW_AGENT_NAMESPACE:-PrivateCloudDisk} \
  -Dskywalking.agent.instance_name=${HOSTNAME:-im-server-1} \
  -Dskywalking.logging.level=${SW_LOGGING_LEVEL:-INFO} \
  -Dskywalking.plugin.toolkit.log.grpc.reporter.server_host=${SW_AGENT_BACKEND:-skywalking-oap} \
  -Dskywalking.plugin.toolkit.log.grpc.reporter.server_port=11800 \
  -Dskywalking.agent.sample_n_per_3_secs=${SW_SAMPLE_RATE:--1} \
  -jar app.jar"]