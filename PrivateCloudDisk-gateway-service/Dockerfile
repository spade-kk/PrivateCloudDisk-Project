# ============================================================
# 第一阶段：构建
# ============================================================
FROM eclipse-temurin:18-jdk AS builder

WORKDIR /backend_gateway_service

# 1. 先复制 Gradle Wrapper 和配置文件（便于缓存依赖）
COPY gradle gradle
COPY gradlew gradlew.bat ./
COPY build.gradle settings.gradle ./

# 下载 Gradle 发行版和依赖（只执行一次，缓存加速）
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

# ============================================================
# 第二阶段：下载 SkyWalking Java Agent
# ============================================================
FROM eclipse-temurin:18-jdk AS agent-downloader

WORKDIR /agent
RUN apt-get update && apt-get install -y curl && \
    curl -L -o skywalking-agent.tar.gz \
    https://dlcdn.apache.org/skywalking/java-agent/9.4.0/apache-skywalking-java-agent-9.4.0.tgz && \
    tar -xzf skywalking-agent.tar.gz --strip-components=1 && \
    rm skywalking-agent.tar.gz

# ============================================================
# 第三阶段：运行
# ============================================================
FROM eclipse-temurin:18-jre

WORKDIR /backend_gateway_service

# 复制 SkyWalking Agent
COPY --from=agent-downloader /agent /backend_gateway_service/skywalking-agent

# 复制构建产物
COPY --from=builder /backend_gateway_service/build/libs/PrivateCloudDisk-gateway-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# SkyWalking Agent 参数（通过环境变量覆盖）
# SW_AGENT_NAME: 服务名
# SW_AGENT_BACKEND: OAP 后端地址
# SW_AGENT_NAMESPACE: 命名空间
ENTRYPOINT ["sh", "-c", "\
java \
  -javaagent:/backend_gateway_service/skywalking-agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_AGENT_NAME:-PrivateCloudDisk-gateway-service} \
  -Dskywalking.collector.backend_service=${SW_AGENT_BACKEND:-skywalking-oap:11800} \
  -Dskywalking.agent.namespace=${SW_AGENT_NAMESPACE:-PrivateCloudDisk} \
  -Dskywalking.agent.instance_name=${HOSTNAME:-gateway-1} \
  -Dskywalking.logging.level=${SW_LOGGING_LEVEL:-INFO} \
  -Dskywalking.plugin.toolkit.log.grpc.reporter.server_host=${SW_AGENT_BACKEND:-skywalking-oap} \
  -Dskywalking.plugin.toolkit.log.grpc.reporter.server_port=11800 \
  -Dskywalking.agent.sample_n_per_3_secs=${SW_SAMPLE_RATE:--1} \
  -Xms256m -Xmx512m \
  -jar app.jar"]