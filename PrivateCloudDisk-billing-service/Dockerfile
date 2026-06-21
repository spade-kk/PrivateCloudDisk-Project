# ============================================================
# 第一阶段：构建
# ============================================================
FROM eclipse-temurin:18-jdk AS builder

WORKDIR /billing_service

COPY gradle gradle
COPY gradlew .
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

# ============================================================
# 第二阶段：下载 SkyWalking Agent
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

WORKDIR /billing_service

COPY --from=agent-downloader /agent /billing_service/skywalking-agent
COPY --from=builder /billing_service/build/libs/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["sh", "-c", "\
java \
  -javaagent:/billing_service/skywalking-agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_AGENT_NAME:-PrivateCloudDisk-billing-service} \
  -Dskywalking.collector.backend_service=${SW_AGENT_BACKEND:-skywalking-oap:11800} \
  -Dskywalking.agent.namespace=${SW_AGENT_NAMESPACE:-PrivateCloudDisk} \
  -Dskywalking.agent.instance_name=${HOSTNAME:-billing-1} \
  -Dskywalking.logging.level=${SW_LOGGING_LEVEL:-INFO} \
  -Dskywalking.plugin.toolkit.log.grpc.reporter.server_host=${SW_AGENT_BACKEND:-skywalking-oap} \
  -Dskywalking.plugin.toolkit.log.grpc.reporter.server_port=11800 \
  -Dskywalking.agent.sample_n_per_3_secs=${SW_SAMPLE_RATE:--1} \
  -Xms256m -Xmx512m \
  -jar app.jar"]