# ============================================================
# IM Platform — Docker 多阶段构建
# ============================================================
# 阶段 1：Maven 编译（JDK 18 + Maven 3.9）
# 阶段 2：JRE 运行时（精简镜像）
# ============================================================

# ---- 阶段 1：构建 ----
FROM maven:3.9-eclipse-temurin-18 AS builder

WORKDIR /build

# 1. 先复制父 POM 和所有模块的 pom.xml（利用 Docker 层缓存）
COPY pom.xml .
COPY im-common/pom.xml im-common/
COPY im-platform/pom.xml im-platform/
COPY im-server/pom.xml im-server/
COPY im-client/pom.xml im-client/

# 2. 下载依赖（此层在 pom.xml 不变时会被缓存）
RUN mvn dependency:go-offline -pl im-platform -am -B -q || true

# 3. 复制全部源码
COPY im-common/src im-common/src/
COPY im-platform/src im-platform/src/

# 4. 编译 im-platform（同时编译其依赖模块 im-common）
RUN mvn clean package -pl im-platform -am -DskipTests -B -q

# ---- 阶段 2：运行 ----
FROM eclipse-temurin:18-jre

WORKDIR /app

# 创建非 root 用户
RUN groupadd -r appuser && useradd -r -g appuser -d /app -s /sbin/nologin appuser

# 复制构建产物
COPY --from=builder /build/im-platform/target/*.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs && chown -R appuser:appuser /app

# 切换到非 root 用户
USER appuser

# HTTP API 端口
EXPOSE 8088

# JVM 参数
# -XX:+UseContainerSupport: 启用容器感知（自动检测内存/CPU 限制）
# -XX:MaxRAMPercentage=75.0: 最大堆内存为容器内存的 75%
# -XX:+ExitOnOutOfMemoryError: OOM 时自动退出（由编排工具重启）
# -XX:+HeapDumpOnOutOfMemoryError: OOM 时生成 heap dump
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/"

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
  CMD curl -f http://localhost:8088/im/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]