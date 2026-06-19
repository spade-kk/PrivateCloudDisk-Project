# ============================================================
# 第一阶段：构建（使用完整 JDK）
# ============================================================
FROM eclipse-temurin:18-jdk AS builder

WORKDIR /backend_platform_service

# 1. 先复制 Gradle Wrapper 和配置文件（便于缓存依赖）
COPY gradle gradle
COPY gradlew .
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
# 下载 SkyWalking Java Agent 9.4.0 (支持 Spring Boot 3.x)
RUN apt-get update && apt-get install -y curl && \
    curl -L -o skywalking-agent.tar.gz \
    https://dlcdn.apache.org/skywalking/java-agent/9.4.0/apache-skywalking-java-agent-9.4.0.tgz && \
    tar -xzf skywalking-agent.tar.gz --strip-components=1 && \
    rm skywalking-agent.tar.gz

# ============================================================
# 第三阶段：运行（精简 JRE + SkyWalking Agent）
# ============================================================
FROM eclipse-temurin:18-jre

WORKDIR /backend_platform_service

# 复制 SkyWalking Agent
COPY --from=agent-downloader /agent /backend_platform_service/skywalking-agent

# 复制构建好的 jar 包
COPY --from=builder /backend_platform_service/build/libs/*.jar app.jar

EXPOSE 8081

# JVM 参数:
#   -javaagent: SkyWalking Agent 路径
#   -Dskywalking.agent.service_name: 服务名 (由环境变量控制)
#   -Dskywalking.collector.backend_service: OAP 地址
#   -Xms/Xmx: 堆内存
ENTRYPOINT ["sh", "-c", "\
java \
  -javaagent:/backend_platform_service/skywalking-agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_AGENT_NAME:-PrivateCloudDisk-platform-service} \
  -Dskywalking.collector.backend_service=${SW_AGENT_BACKEND:-skywalking-oap:11800} \
  -Dskywalking.agent.namespace=PrivateCloudDisk \
  -Xms256m -Xmx512m \
  -jar app.jar"]