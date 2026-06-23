# 第一阶段：构建（使用完整 JDK）
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

# 第二阶段：运行（精简 JRE）
FROM eclipse-temurin:18-jre

WORKDIR /backend_platform_service

# 复制构建好的 jar 包（请确认实际生成的 jar 文件名）
COPY --from=builder /backend_platform_service/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]