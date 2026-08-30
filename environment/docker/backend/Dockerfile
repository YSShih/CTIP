# syntax=docker/dockerfile:1.7
# build context = repo root (CTIP/)
# 唯一的 backend Dockerfile(docs/spec/05-environment.md §5.3,強制契約)。
# 禁止 Dockerfile.<env>;行為差異以 multi-stage build + target 區分。

########## development ##########
FROM eclipse-temurin:25-jdk AS development
# healthcheck 需要 curl;base image 不保證內含,明確安裝
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl inotify-tools \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
ENV SPRING_DEVTOOLS_RESTART_ENABLED=true
EXPOSE 8080 5005
# 原始碼由 bind mount 提供於 /workspace(見 §5.2)
CMD ["./mvnw", "-o", "-pl", "ctip-app", "-am", "spring-boot:run"]

########## build ##########
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
# 先只複製 pom,讓相依層可快取
COPY backend/.mvn/            .mvn/
COPY backend/mvnw             ./
COPY backend/pom.xml          ./
COPY backend/ctip-sdk/pom.xml      ctip-sdk/
COPY backend/ctip-core/pom.xml     ctip-core/
COPY backend/ctip-adapters/pom.xml ctip-adapters/
COPY backend/ctip-app/pom.xml      ctip-app/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY backend/ ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

########## production ##########
FROM eclipse-temurin:25-jre AS production
# 基底映像放了一個 9.9MB 的 /usr/bin/pebble(Canonical 的服務管理器)。它**不受 apt 管理**
# (`dpkg -S /usr/bin/pebble` 查無所屬套件),因此 apt-get upgrade 永遠修不到它內嵌的 Go stdlib
# ——2026-08-30 實測那支 binary 帶進 8 個 HIGH(Go stdlib v1.26.5,上游 1.26.6 已修)。
# 本容器的 entrypoint 是 `java -jar`,從不使用 pebble,留著只是把弱點帶進正式映像(ADR 0049)。
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && rm -f /usr/bin/pebble \
 && useradd -r -u 10001 ctip
WORKDIR /app
COPY --from=build /src/ctip-app/target/*.jar app.jar
RUN chown ctip:ctip /app/app.jar
USER ctip
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
