# 基础镜像：轻量级 JDK18（SpringBoot3 推荐；SpringBoot2 可改为 8/11）
FROM eclipse-temurin:18-jdk AS builder

# 容器工作目录
WORKDIR /backend_gateway_service

# 1. 先复制 Gradle Wrapper 和配置文件（便于缓存依赖）
COPY gradle gradle
COPY gradlew .
COPY build.gradle settings.gradle ./

# 下载 Gradle 发行版和依赖（只执行一次，缓存加速）
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 再构建 jar 包
RUN ./gradlew bootJar --no-daemon

# 第二阶段：运行（使用精简 JRE）
FROM eclipse-temurin:18-jre

# 容器工作目录
WORKDIR /backend_gateway_service

# 复制本地打包好的 jar 包到容器（修改为你的 jar 包名称）
COPY --from=builder backend_gateway_service/build/libs/PrivateCloudDisk-gateway-service-0.0.1-SNAPSHOT.jar app.jar

# 暴露 SpringBoot 默认端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]