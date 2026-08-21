# 05 — 環境、Docker 與部署契約

> **規範等級：強制。** 單一 compose 檔、單一 Dockerfile 對、四種 profile 的規定不得變更。
>
> 本檔修正 v1.1 的**四項建置阻斷缺陷**（見 5.8）。
>
> 相關檔案：[06-tech-stack.md](06-tech-stack.md)、[04-data-dictionary.md](04-data-dictionary.md#47-flyway-migration-對應)

---

## 5.1 儲存庫結構契約（強制）

```text
CTIP/
├── environment/
│   ├── docker/
│   │   ├── backend/Dockerfile
│   │   └── frontend/Dockerfile
│   ├── docker-compose.yml          ← 唯一的 compose 檔
│   ├── .noop/.gitkeep              ← 必須 commit
│   ├── .env.example
│   ├── .env.mvp.example
│   ├── .env.dev.example
│   ├── .env.staging.example
│   ├── .env.prod.example
│   ├── config/
│   │   ├── postgres/  redis/  kafka/  elasticsearch/
│   │   ├── nginx/default.conf
│   │   └── monitoring/{prometheus,grafana}/
│   ├── scripts/
│   │   ├── up.sh  down.sh  restart.sh  logs.sh
│   │   ├── migrate.sh  reload.sh  dod.sh
│   │   └── _common.sh
│   └── README.md
├── frontend/
├── backend/                        ← Maven multi-module，見 01-architecture.md
├── docs/
│   ├── architecture/{overview.md,security.md,decisions/}
│   ├── api/{openapi.json,events/}
│   ├── deployment/{licensing.md,privacy.md}
│   ├── development/{getting-started.md,plugin-sdk.md,version-audit.md}
│   └── spec/                       ← 本規格書（本版新增）
├── .github/workflows/
├── .gitignore  README.md  SECURITY.md  CONTRIBUTING.md  LICENSE
```

**不得增減頂層目錄。** `docs/spec/` 是 `docs/` 之下的子目錄，不違反此規則。

| 目錄 | 只放 | 絕不放 |
|---|---|---|
| `environment/` | Dockerfile、compose、環境樣板、基礎設施設定、部署腳本 | **任何業務邏輯** |
| `frontend/` | React 應用 | 基礎設施檔案 |
| `backend/` | Spring Boot（4 個 Maven module） | 基礎設施檔案 |

---

## 5.2 唯一的 Compose 檔

**禁止建立**：`docker-compose.dev.yml`、`.staging.yml`、`.prod.yml`、`.mvp.yml`、`.override.yml`。

環境差異一律透過**環境變數**與 **Compose profiles** 控制：

| 要控制的事 | 機制 |
|---|---|
| 這個服務要不要啟動 | `profiles:` + `COMPOSE_PROFILES` |
| 這個服務怎麼跑（build target、mount、port、JVM 參數、restart policy） | 環境變數 |

### Mount 的變數化

Compose 的 `volumes:` 無法用環境變數整段消失。解法是讓 source、target、mode 三者都變成變數，正式環境掛一個無害的空目錄：

```yaml
volumes:
  - ${BACKEND_MOUNT_SRC:-./.noop}:${BACKEND_MOUNT_DST:-/opt/noop}:${BACKEND_MOUNT_MODE:-ro}
```

- 本機開發：`../backend` → `/workspace`（rw）
- staging / prod：使用預設值，掛 `environment/.noop` 到 `/opt/noop`（唯讀空目錄，不影響映像檔）

### 防呆檢查（CI 強制）

```bash
# 1. prod 設定下不得出現原始碼掛載
docker compose --env-file environment/.env.prod.example \
  -f environment/docker-compose.yml config \
  | grep -E '\.\./(backend|frontend)' && exit 1 || true

# 2. prod 設定下不得出現 JDWP debug agent
docker compose --env-file environment/.env.prod.example \
  -f environment/docker-compose.yml config \
  | grep -i 'jdwp' && exit 1 || true

# 3. 四種環境皆須通過 config 驗證
for e in mvp dev staging prod; do
  docker compose --env-file "environment/.env.$e.example" \
    -f environment/docker-compose.yml config -q || exit 1
done
```

`.env.prod.example` 與 `.env.staging.example` 中**不得出現任何 `*_MOUNT_*` 變數**（依賴預設值）。

---

## 5.3 Dockerfile 契約

只能有 `environment/docker/backend/Dockerfile` 與 `environment/docker/frontend/Dockerfile`。
禁止 `Dockerfile.dev` / `.staging` / `.prod`。以 **multi-stage build + `target`** 區分行為。

> ⚠️ **build context 為 repo root**（compose 中 `context: ..`）。**所有 `COPY` 路徑必須含模組前綴。**
> v1.1 的骨架假設 context 是 `backend/` 與 `frontend/`，導致兩個 image 都建不起來——這是本版修正的第一項阻斷缺陷。

### Backend Dockerfile

```dockerfile
# syntax=docker/dockerfile:1.7
# build context = repo root (CTIP/)

########## development ##########
FROM eclipse-temurin:25-jdk AS development
# healthcheck 需要 curl；base image 不保證內含，明確安裝
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl inotify-tools \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
ENV SPRING_DEVTOOLS_RESTART_ENABLED=true
EXPOSE 8080 5005
# 原始碼由 bind mount 提供於 /workspace（見 5.2）
CMD ["./mvnw", "-o", "-pl", "ctip-app", "-am", "spring-boot:run"]

########## build ##########
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
# 先只複製 pom，讓相依層可快取
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
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 10001 ctip
WORKDIR /app
COPY --from=build /src/ctip-app/target/*.jar app.jar
RUN chown ctip:ctip /app/app.jar
USER ctip
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

> **為什麼明確安裝 curl**：`eclipse-temurin` 的 JDK/JRE image 不保證內含 `curl`，而 compose 與 Dockerfile 的 healthcheck 都依賴它。v1.1 的 `HEALTHCHECK CMD ["java","-version"]` 等於沒有檢查（JVM 能啟動不代表應用健康）。依賴一個你不控制的 base image 是否內含某工具，是本版修正的第二項阻斷缺陷。

### Frontend Dockerfile

```dockerfile
# syntax=docker/dockerfile:1.7
# build context = repo root (CTIP/)

########## development ##########
FROM node:24-alpine AS development
WORKDIR /workspace
EXPOSE 5173
# 原始碼由 bind mount 提供於 /workspace
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]

########## build ##########
FROM node:24-alpine AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

########## production ##########
FROM nginx:1.30-alpine AS production
COPY --from=build /src/dist /usr/share/nginx/html
COPY environment/config/nginx/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s \
  CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
```

`nginx:alpine` 內含 busybox 的 `wget`，故前端 healthcheck 用 `wget` 而非 `curl`。

---

## 5.4 環境變數清單

### 5.4.1 執行模式

```text
PROJECT_NAME
ENVIRONMENT                    # mvp | dev | staging | prod  ← 必須傳入容器
COMPOSE_PROFILES               # 空 | standard | full
RESTART_POLICY                 # no | unless-stopped | always
```

### 5.4.2 建置與掛載

```text
BACKEND_BUILD_TARGET           # development | production
BACKEND_MOUNT_SRC / _DST / _MODE
MAVEN_CACHE_SRC / _DST
BACKEND_JAVA_OPTS
FRONTEND_BUILD_TARGET
FRONTEND_MOUNT_SRC / _DST / _MODE
FRONTEND_CONTAINER_PORT        # 5173 (vite dev) | 80 (nginx)
```

### 5.4.3 網路綁定

```text
BACKEND_BIND  BACKEND_DEBUG_BIND  FRONTEND_BIND  POSTGRES_BIND  REDIS_BIND
```

> 開發環境一律綁 `127.0.0.1`，避免服務意外曝露到區網。

### 5.4.4 資料儲存

```text
POSTGRES_DB  POSTGRES_USER  POSTGRES_PASSWORD  POSTGRES_HOST  POSTGRES_PORT
REDIS_HOST  REDIS_PORT  REDIS_PASSWORD
KAFKA_BOOTSTRAP_SERVERS
ELASTICSEARCH_URL  ELASTICSEARCH_USERNAME  ELASTICSEARCH_PASSWORD
ES_JAVA_OPTS                   # 預設 -Xms1g -Xmx1g
ES_SECURITY_ENABLED            # 預設 false（僅 dev/staging；prod 必須 true）
GRAFANA_ADMIN_PASSWORD         # 僅 full profile 需要
```

> `COMPOSE_PROFILES` 不出現在 compose 檔內——它是 Docker Compose **CLI** 讀取的變數，用於決定啟動哪些帶 `profiles:` 的服務。

### 5.4.5 應用程式

```text
SPRING_PROFILES_ACTIVE
BACKEND_PORT  FRONTEND_PORT

JWT_SECRET                     # >= 32 bytes，prod 必須來自 secret manager
JWT_ACCESS_TOKEN_EXPIRATION    # 秒，預設 900
JWT_REFRESH_TOKEN_EXPIRATION   # 秒，預設 2592000

CORS_ALLOWED_ORIGINS
RATE_LIMIT_ENABLED
RATE_LIMIT_BACKEND             # memory | redis

BLOOM_PUBLIC_CAPACITY
BLOOM_PUBLIC_FALSE_POSITIVE_RATE
BLOOM_TENANT_DEFAULT_CAPACITY
BLOOM_SNAPSHOT_CRON
BLOOM_DELTA_CRON
BLOOM_MAX_DELTA_CHAIN          # 預設 24

SCHEDULER_ENABLED
INGESTION_ENABLED
INGESTION_BATCH_SIZE           # 預設 500

SWAGGER_ENABLED
ACTUATOR_EXPOSED_ENDPOINTS

AUDIT_RETENTION_DAYS           # 預設 180
RAW_PAYLOAD_RETENTION_DAYS     # 預設 30
REJECTION_RETENTION_DAYS       # 預設 30
DELIVERY_RETENTION_DAYS        # 預設 30
INDICATOR_RETENTION_DAYS       # 預設 365
BLOOM_ARTIFACT_KEEP            # 預設 30
```

> 後六個保留政策變數在 v1.1 只出現於 §55.3，變數清單裡沒有——本版補齊。這是第三項缺陷（變數宣告與使用不對稱）。

### 5.4.6 前端（公開）

```text
VITE_ENVIRONMENT  VITE_API_URL  VITE_WS_URL
```

> 任何 `VITE_` 開頭的變數都會被打包進 bundle，**視為公開資訊**。絕不放後端 secret。

---

## 5.5 四種 Profile 差異表

| 變數 | mvp | dev | staging | prod |
|---|---|---|---|---|
| `ENVIRONMENT` | `mvp` | `dev` | `staging` | `prod` |
| `COMPOSE_PROFILES` | *(空)* | **`standard`** | `full` | `full` |
| 啟動的服務 | frontend, backend, postgres | ＋redis | ＋kafka, es, prometheus, grafana | 同 staging |
| `BACKEND_BUILD_TARGET` | development | development | production | production |
| `BACKEND_MOUNT_SRC` | `../backend` | `../backend` | *(不設定)* | *(不設定)* |
| `BACKEND_MOUNT_DST` | `/workspace` | `/workspace` | *(不設定)* | *(不設定)* |
| `BACKEND_MOUNT_MODE` | `rw` | `rw` | *(不設定)* | *(不設定)* |
| `BACKEND_JAVA_OPTS` | JDWP | JDWP | *(空)* | `-XX:MaxRAMPercentage=75` |
| `FRONTEND_CONTAINER_PORT` | 5173 | 5173 | 80 | 80 |
| `FRONTEND_BIND` | **`127.0.0.1:5173`** | **`127.0.0.1:5173`** | `0.0.0.0:80` | 由代理層決定 |
| `RESTART_POLICY` | `no` | `no` | `unless-stopped` | `always` |
| `SPRING_PROFILES_ACTIVE` | `mvp` | `dev` | `staging` | `prod` |
| `RATE_LIMIT_BACKEND` | `memory` | `redis` | `redis` | `redis` |
| `SWAGGER_ENABLED` | `true` | `true` | `true` | `false` |
| `SCHEDULER_ENABLED` | `true` | `true` | `true` | `true` |
| `*_BIND`（其餘） | `127.0.0.1:*` | `127.0.0.1:*` | `0.0.0.0:*` | 由代理層決定 |

**兩項相對 v1.1 的修正**

1. **`dev` 改用 `standard` profile**（v1.1 用 `full`）。v1.1 定義了 `standard` 但沒有任何環境使用它，導致 dev 在 M1 階段就會啟動 Kafka + Elasticsearch + Prometheus + Grafana（約 3GB RAM），而這些服務的程式碼要到 M2／M3 才存在。`dev` 需要 Redis（`RATE_LIMIT_BACKEND=redis`），故用 `standard`。
2. **`FRONTEND_BIND` 在 dev 改為 `127.0.0.1:5173`**（v1.1 為 `:3000`）。Vite 的 HMR client 預設連回它自己認知的 port；host 3000 對映容器 5173 會使 HMR 靜默失效——而 DoD-MVP 有一條測 hot reload。讓兩端 port 一致比設定 `server.hmr.clientPort` 少一個變數。

---

## 5.6 Compose 骨架

```yaml
name: ${PROJECT_NAME:-ctip}

x-app-common: &app-common
  restart: ${RESTART_POLICY:-unless-stopped}
  networks: [ ctip ]
  logging:
    driver: json-file
    options:
      max-size: "10m"
      max-file: "3"

services:

  backend:
    <<: *app-common
    build:
      context: ..
      dockerfile: environment/docker/backend/Dockerfile
      target: ${BACKEND_BUILD_TARGET:-production}
    environment:
      ENVIRONMENT:                    ${ENVIRONMENT:?ENVIRONMENT is required}
      SPRING_PROFILES_ACTIVE:         ${SPRING_PROFILES_ACTIVE:-prod}
      JAVA_TOOL_OPTIONS:              ${BACKEND_JAVA_OPTS:-}
      SERVER_PORT:                    ${BACKEND_PORT:-8080}
      POSTGRES_HOST:                  ${POSTGRES_HOST:-postgres}
      POSTGRES_PORT:                  ${POSTGRES_PORT:-5432}
      POSTGRES_DB:                    ${POSTGRES_DB:?}
      POSTGRES_USER:                  ${POSTGRES_USER:?}
      POSTGRES_PASSWORD:              ${POSTGRES_PASSWORD:?}
      REDIS_HOST:                     ${REDIS_HOST:-redis}
      REDIS_PORT:                     ${REDIS_PORT:-6379}
      REDIS_PASSWORD:                 ${REDIS_PASSWORD:-}
      KAFKA_BOOTSTRAP_SERVERS:        ${KAFKA_BOOTSTRAP_SERVERS:-}
      ELASTICSEARCH_URL:              ${ELASTICSEARCH_URL:-}
      ELASTICSEARCH_USERNAME:         ${ELASTICSEARCH_USERNAME:-}
      ELASTICSEARCH_PASSWORD:         ${ELASTICSEARCH_PASSWORD:-}
      JWT_SECRET:                     ${JWT_SECRET:?}
      JWT_ACCESS_TOKEN_EXPIRATION:    ${JWT_ACCESS_TOKEN_EXPIRATION:-900}
      JWT_REFRESH_TOKEN_EXPIRATION:   ${JWT_REFRESH_TOKEN_EXPIRATION:-2592000}
      CORS_ALLOWED_ORIGINS:           ${CORS_ALLOWED_ORIGINS:?}
      SWAGGER_ENABLED:                ${SWAGGER_ENABLED:-false}
      ACTUATOR_EXPOSED_ENDPOINTS:     ${ACTUATOR_EXPOSED_ENDPOINTS:-health,info}
      RATE_LIMIT_ENABLED:             ${RATE_LIMIT_ENABLED:-true}
      RATE_LIMIT_BACKEND:             ${RATE_LIMIT_BACKEND:-redis}
      SCHEDULER_ENABLED:              ${SCHEDULER_ENABLED:-true}
      INGESTION_ENABLED:              ${INGESTION_ENABLED:-true}
      INGESTION_BATCH_SIZE:           ${INGESTION_BATCH_SIZE:-500}
      BLOOM_PUBLIC_CAPACITY:          ${BLOOM_PUBLIC_CAPACITY:-10000000}
      BLOOM_PUBLIC_FALSE_POSITIVE_RATE: ${BLOOM_PUBLIC_FALSE_POSITIVE_RATE:-0.001}
      BLOOM_TENANT_DEFAULT_CAPACITY:  ${BLOOM_TENANT_DEFAULT_CAPACITY:-1000000}
      BLOOM_SNAPSHOT_CRON:            ${BLOOM_SNAPSHOT_CRON:-0 0 4 * * *}
      BLOOM_DELTA_CRON:               ${BLOOM_DELTA_CRON:-0 0 * * * *}
      BLOOM_MAX_DELTA_CHAIN:          ${BLOOM_MAX_DELTA_CHAIN:-24}
      AUDIT_RETENTION_DAYS:           ${AUDIT_RETENTION_DAYS:-180}
      RAW_PAYLOAD_RETENTION_DAYS:     ${RAW_PAYLOAD_RETENTION_DAYS:-30}
      REJECTION_RETENTION_DAYS:       ${REJECTION_RETENTION_DAYS:-30}
      DELIVERY_RETENTION_DAYS:        ${DELIVERY_RETENTION_DAYS:-30}
      INDICATOR_RETENTION_DAYS:       ${INDICATOR_RETENTION_DAYS:-365}
      BLOOM_ARTIFACT_KEEP:            ${BLOOM_ARTIFACT_KEEP:-30}
    volumes:
      - ${BACKEND_MOUNT_SRC:-./.noop}:${BACKEND_MOUNT_DST:-/opt/noop}:${BACKEND_MOUNT_MODE:-ro}
      - ${MAVEN_CACHE_SRC:-maven-cache}:${MAVEN_CACHE_DST:-/opt/noop-m2}
    ports:
      - "${BACKEND_BIND:-127.0.0.1:8080}:8080"
      - "${BACKEND_DEBUG_BIND:-127.0.0.1:5005}:5005"
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:8080/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 90s

  frontend:
    <<: *app-common
    build:
      context: ..
      dockerfile: environment/docker/frontend/Dockerfile
      target: ${FRONTEND_BUILD_TARGET:-production}
    environment:
      VITE_ENVIRONMENT: ${VITE_ENVIRONMENT:?}
      VITE_API_URL:     ${VITE_API_URL:?}
      VITE_WS_URL:      ${VITE_WS_URL:-}
    volumes:
      - ${FRONTEND_MOUNT_SRC:-./.noop}:${FRONTEND_MOUNT_DST:-/opt/noop}:${FRONTEND_MOUNT_MODE:-ro}
    ports:
      - "${FRONTEND_BIND:-127.0.0.1:3000}:${FRONTEND_CONTAINER_PORT:-80}"
    depends_on: [ backend ]

  postgres:
    <<: *app-common
    image: postgres:18-alpine
    environment:
      POSTGRES_DB:       ${POSTGRES_DB:?}
      POSTGRES_USER:     ${POSTGRES_USER:?}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?}
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./config/postgres:/docker-entrypoint-initdb.d:ro
    ports:
      - "${POSTGRES_BIND:-127.0.0.1:5432}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 10

  redis:
    <<: *app-common
    profiles: [ "standard", "full" ]
    image: redis:8-alpine
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:?}", "--appendonly", "yes"]
    volumes:
      - redis-data:/data
    ports:
      - "${REDIS_BIND:-127.0.0.1:6379}:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "--no-auth-warning", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10

  kafka:
    <<: *app-common
    profiles: [ "full" ]
    image: apache/kafka:4.2.1
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
    volumes:
      - kafka-data:/var/lib/kafka/data

  elasticsearch:
    <<: *app-common
    profiles: [ "full" ]
    image: elasticsearch:9.5.1
    environment:
      discovery.type: single-node
      ES_JAVA_OPTS: ${ES_JAVA_OPTS:--Xms1g -Xmx1g}
      xpack.security.enabled: ${ES_SECURITY_ENABLED:-false}
    volumes:
      - es-data:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:9200/_cluster/health || exit 1"]
      interval: 20s
      timeout: 10s
      retries: 15

  prometheus:
    <<: *app-common
    profiles: [ "full" ]
    image: prom/prometheus:v3.6.0
    volumes:
      - ./config/monitoring/prometheus:/etc/prometheus:ro
      - prometheus-data:/prometheus

  grafana:
    <<: *app-common
    profiles: [ "full" ]
    image: grafana/grafana:12.2.0
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:?}
    volumes:
      - ./config/monitoring/grafana:/etc/grafana/provisioning:ro
      - grafana-data:/var/lib/grafana

networks:
  ctip:
    driver: bridge

volumes:
  postgres-data:
  redis-data:
  kafka-data:
  es-data:
  prometheus-data:
  grafana-data:
  maven-cache:          # ← 必須宣告，見 5.8
```

**關於 debug port**：`5005` 在所有環境都會發布，但只綁 `127.0.0.1`，且 **staging/prod 的 `BACKEND_JAVA_OPTS` 不含 JDWP agent**，因此容器內沒有任何程序在 5005 監聽，此對映是惰性的。5.2 的防呆檢查 #2 驗證 prod 設定不含 `jdwp`。

---

## 5.7 Spring 設定對應（本版新增）

v1.1 定義了環境變數，卻從未說明它們如何對映到 Spring 屬性，也沒有 `application.yml` 契約——AI 會自行發明。以下為強制對應。

`ctip-app/src/main/resources/` 必須有：`application.yml` 與 `application-{mvp,dev,staging,prod}.yml`。

**`application.yml`（共用）關鍵對應**

```yaml
spring:
  application.name: ctip
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB}
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
  jpa:
    open-in-view: false          # 強制：避免 lazy loading 洩漏到 view 層
    hibernate.ddl-auto: validate # 強制：schema 由 Flyway 管理
  flyway:
    enabled: true
    locations: classpath:db/migration
  data.redis:
    host: ${REDIS_HOST:redis}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}

server.port: ${SERVER_PORT:8080}

management:
  endpoints.web.exposure.include: ${ACTUATOR_EXPOSED_ENDPOINTS:health,info}
  endpoint.health.probes.enabled: true

springdoc:
  api-docs.enabled: ${SWAGGER_ENABLED:false}
  swagger-ui.enabled: ${SWAGGER_ENABLED:false}

ctip:
  environment: ${ENVIRONMENT}
  cors.allowed-origins: ${CORS_ALLOWED_ORIGINS}
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiration: ${JWT_ACCESS_TOKEN_EXPIRATION:900}
    refresh-token-expiration: ${JWT_REFRESH_TOKEN_EXPIRATION:2592000}
  rate-limit:
    enabled: ${RATE_LIMIT_ENABLED:true}
    backend: ${RATE_LIMIT_BACKEND:redis}
  ingestion:
    enabled: ${INGESTION_ENABLED:true}
    batch-size: ${INGESTION_BATCH_SIZE:500}
  scheduler.enabled: ${SCHEDULER_ENABLED:true}
  bloom: { ... }        # 對應 5.4.5 的 BLOOM_* 變數
  retention: { ... }    # 對應 5.4.5 的 *_RETENTION_DAYS 變數
```

所有 `ctip.*` 屬性必須以 `@ConfigurationProperties` 綁定為 `record`，並加 `jakarta.validation` 註解。**禁止散落的 `@Value`。**

**啟動守衛（強制，`ctip-app/config/StartupValidator.java`）**

| 條件 | 行為 |
|---|---|
| `ENVIRONMENT=prod` 且 `JWT_SECRET` 為樣板值或長度 < 32 bytes | **拒絕啟動** |
| `ENVIRONMENT=prod` 且 `CORS_ALLOWED_ORIGINS` 含 `*` | **拒絕啟動** |
| `ENVIRONMENT=prod` 且 `SWAGGER_ENABLED=true` | WARN（允許，但須有保護） |
| `ENVIRONMENT != mvp` 且 `RATE_LIMIT_BACKEND=memory` | WARN |
| `ENVIRONMENT != mvp` 且 `spring.jpa.hibernate.ddl-auto != validate` | **拒絕啟動** |

> 這些守衛在 v1.1 就已規定，但 compose 從未把 `ENVIRONMENT` 傳入容器，因此**永遠不會生效**。5.6 已修正（`ENVIRONMENT` 使用 `:?` 語法，缺少即 config 失敗）。這是第四項阻斷缺陷。

---

## 5.8 相對 v1.1 修正的四項建置阻斷缺陷

| # | 缺陷 | 症狀 | 本檔修正處 |
|---|---|---|---|
| 1 | Dockerfile 的 `COPY` 路徑假設 context 為 `backend/`／`frontend/`，但 compose 設 `context: ..`（repo root） | `docker compose build` 直接失敗：找不到 `mvnw`、`pom.xml`、`package.json` | 5.3，所有 COPY 加模組前綴 |
| 2 | healthcheck 依賴 `curl`，但 `eclipse-temurin` 不保證內含；Dockerfile 的 `HEALTHCHECK` 是 `java -version` | 容器永遠 unhealthy，`depends_on: service_healthy` 卡死 | 5.3，明確 `apt-get install curl` |
| 3 | `.env.mvp.example` 設 `MAVEN_CACHE_SRC=maven-cache`（named volume），但頂層 `volumes:` 未宣告 | `docker compose config` 失敗：`service refers to undefined volume` | 5.6，宣告 `maven-cache` |
| 4 | compose 未把 `ENVIRONMENT` 傳入 backend container | §27／§54.2／§29.1 的所有啟動守衛永遠不觸發 | 5.6 + 5.7 |

---

## 5.9 Flyway

Schema 一律由 Flyway 管理，應用啟動時自動執行。**`ddl-auto: validate`，絕不 `update` 或 `create`。**

- 版本號區段：`V1–V19` = M1、`V20–V29` = M2、`V30+` = M3
- 完整 migration 對應見 [04-data-dictionary.md](04-data-dictionary.md#47-flyway-migration-對應)
- Migration 檔必須 commit
- **絕不修改已套用的 migration**，一律新增
- 種子資料使用獨立 migration，且必須冪等（`ON CONFLICT DO NOTHING`）
- 大量開發樣本資料放 `db/seed/`，以 `spring.sql.init` 僅於 `dev`/`mvp` profile 載入
- CI 必須驗證：從空資料庫執行全部 migration 成功（Testcontainers）

---

## 5.10 腳本契約

`up.sh <env>` 必須：

1. 驗證 env 參數為 `mvp|dev|staging|prod`
2. 檢查 `environment/.env.<env>` 存在（不存在則提示由 `.example` 複製並結束）
3. 對 prod 額外檢查：`JWT_SECRET` 非樣板值且 ≥ 32 bytes、`CORS_ALLOWED_ORIGINS` 非 `*`
4. 驗證 Docker 可用且版本 ≥ 27，Compose ≥ 2.24
5. 執行 `docker compose --env-file environment/.env.<env> -f environment/docker-compose.yml up -d`
6. 等待 healthcheck 並印出服務狀態與存取網址

其餘腳本：

| 腳本 | 用途 |
|---|---|
| `down.sh <env>` | 停止（`--volumes` 為可選旗標，需二次確認） |
| `restart.sh <env> [service]` | 重啟 |
| `logs.sh <service> <env>` | 追蹤日誌 |
| `migrate.sh <env>` | 手動觸發 Flyway（`mvn flyway:migrate`） |
| **`reload.sh <service> <env>`** | **重新編譯並熱替換，見 5.11** |
| **`dod.sh <gate>`** | **執行 DoD Gate 檢查，見 [15-dod-gates.md](15-dod-gates.md)** |

共用邏輯放 `_common.sh`。**不得要求開發者記憶複雜的 Compose 指令。**

---

## 5.11 Hot reload 契約（本版修正）

### 前端：真 HMR

Vite dev server 在容器內以 `--host 0.0.0.0` 執行，host port 與容器 port **一致（皆 5173）**，HMR 直接生效。修改 `.tsx` 後瀏覽器自動更新。

### 後端：腳本觸發，非自動

**v1.1 的 DoD-MVP 有一條「修改 backend Java 檔自動生效」，但在其設計下必定不通過**：dev stage 執行 `spring-boot:run`，DevTools 監看的是 `target/classes`，而**容器內沒有任何程序會編譯 `.java`**。正常人靠 host 上的 IDE 編譯進被掛載的 `target/classes`——但全 AI 實作沒有 IDE。

因此改為**腳本化、可測、誠實**：

```bash
./environment/scripts/reload.sh backend mvp
# 內部執行：
#   docker compose exec backend ./mvnw -o -q -pl ctip-app -am compile
#   → 寫入被掛載的 target/classes
#   → Spring DevTools 偵測 classpath 變更並重啟 application context
```

DoD 判準改為：**「修改 Java 檔後執行 `reload.sh`，10 秒內新行為生效」**——可執行、可計時、必定準確。

> 未選擇「dev 容器內跑 `inotifywait` + supervisor 自動重編」的理由：multi-process 容器的失敗模式（其中一個 process 死掉但容器還活著）正是全 AI 實作難以診斷的類型。dev stage 已安裝 `inotify-tools`，若日後要改為自動監看，擴充點在此。

---

*檔案結束。上次校對：2026-08-21。*
