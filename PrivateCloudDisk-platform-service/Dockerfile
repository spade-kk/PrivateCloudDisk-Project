# ============================================================
# 第一阶段：构建（使用完整 JDK）
# ============================================================
FROM eclipse-temurin:18-jdk AS builder

WORKDIR /backend_platform_service

# 1. 先复制 Gradle Wrapper 和配置文件（便于缓存依赖）
COPY gradle gradle
COPY gradlew gradlew.bat ./
COPY build.gradle settings.gradle ./

# 2. 下载依赖（这一步会缓存，除非 build.gradle/settings.gradle 变化）
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 3. 复制全部源码并构建 jar
COPY src src
RUN ./gradlew bootJar --no-daemon

# ============================================================
# 第二阶段：下载 SkyWalking Java Agent
# ============================================================
FROM eclipse-temurin:18-jdk AS agent-downloader

WORKDIR /agent
RUN apt-get update && apt-get install -y curl && \
    curl -L --retry 3 -o skywalking-agent.tgz \
    https://archive.apache.org/dist/skywalking/java-agent/9.4.0/apache-skywalking-java-agent-9.4.0.tgz && \
    tar -xzf skywalking-agent.tgz --strip-components=1 && \
    rm skywalking-agent.tgz

# ============================================================
# 第三阶段：运行（精简 JRE）
# ============================================================
FROM eclipse-temurin:18-jre

WORKDIR /backend_platform_service

# 复制 SkyWalking Agent
COPY --from=agent-downloader /agent /backend_platform_service/skywalking-agent

# 复制构建好的 jar 包
COPY --from=builder /backend_platform_service/build/libs/*.jar app.jar

EXPOSE 8081

# SkyWalking Agent 参数
ENTRYPOINT ["sh", "-c", "\
java \
  -javaagent:/backend_platform_service/skywalking-agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_AGENT_NAME:-PrivateCloudDisk-platform-service} \
  -Dskywalking.collector.backend_service=${SW_AGENT_BACKEND:-skywalking-oap:11800} \
  -Dskywalking.agent.namespace=${SW_AGENT_NAMESPACE:-PrivateCloudDisk} \
  -Dskywalking.agent.instance_name=${HOSTNAME:-platform-1} \
  -Dskywalking.logging.level=${SW_LOGGING_LEVEL:-INFO} \
  -Dskywalking.plugin.toolkit.log.grpc.reporter.server_host=${SW_AGENT_BACKEND:-skywalking-oap} \
  -Dskywalking.plugin.toolkit.log.grpc.reporter.server_port=11800 \
  -Dskywalking.agent.sample_n_per_3_secs=${SW_SAMPLE_RATE:--1} \
  -Xms256m -Xmx512m \
  -jar app.jar"]