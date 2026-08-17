# syntax=docker/dockerfile:1.7
FROM gradle:8.12.1-jdk21-alpine AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S pcd && adduser -S -G pcd -u 10001 pcd
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER 10001:10001
EXPOSE 8085
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

