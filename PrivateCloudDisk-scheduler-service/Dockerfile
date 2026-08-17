FROM gradle:8.14.2-jdk21 AS builder
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /nonexistent --shell /usr/sbin/nologin pcd
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/scheduler-service.jar
USER 10001
EXPOSE 8088
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/scheduler-service.jar"]
