FROM eclipse-temurin:18-jdk AS debug

WORKDIR /backend_platform_service

# 先复制 gradle 相关文件，利用缓存
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 下载 Gradle 发行版和依赖（只执行一次，缓存加速）不需要gradle守护进程
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY . .

EXPOSE 8080

CMD ["./gradlew", "bootRun", "--no-daemon"]