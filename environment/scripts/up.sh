#!/usr/bin/env bash
# 啟動 CTIP(docs/spec/05-environment.md §5.10)。
# 用法:./environment/scripts/up.sh <mvp|dev|staging|prod>

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

ENV_NAME="${1:-}"

# 1. 驗證 env 參數
validate_env "$ENV_NAME"

# 2. 檢查 environment/.env.<env> 存在
require_env_file "$ENV_NAME"
ENV_FILE="$(env_file_path "$ENV_NAME")"

# 3. prod 額外檢查
if [ "$ENV_NAME" = "prod" ]; then
  check_prod_env_file "$ENV_FILE"
  ok "prod 前置檢查通過(JWT_SECRET、CORS_ALLOWED_ORIGINS)"
fi

# 4. 驗證 Docker / Compose 版本
check_docker

# 5. 啟動
# 開發模式的容器依賴 named volume 快取(maven-cache / node-modules),首次啟動前先預熱:
# - backend 以離線模式跑 ./mvnw -o spring-boot:run,空的 /root/.m2 必然失敗
# - frontend 的 node_modules volume 遮罩 host 版本,空 volume 內無 vite
if [ "$(env_get "$ENV_FILE" BACKEND_BUILD_TARGET)" = "development" ]; then
  # 守衛以離線 go-offline 驗證快取完整性:首次啟動與「pom 相依變更後」都會觸發重新預熱
  # (2026-08-26 Phase 10 實測:只檢查目錄存在無法偵測相依漂移,舊快取使 -o 容器必然啟動失敗)
  if ! compose "$ENV_NAME" run --rm --no-deps backend \
       ./mvnw -o -B -q -pl ctip-app -am -DskipTests dependency:go-offline >/dev/null 2>&1; then
    info "預熱 backend Maven 相依快取(首次啟動或相依變更;下載至 maven-cache volume,需數分鐘)……"
    compose "$ENV_NAME" run --rm --no-deps backend \
      ./mvnw -B -q -pl ctip-app -am -DskipTests dependency:go-offline package
  fi
fi
if [ "$(env_get "$ENV_FILE" FRONTEND_BUILD_TARGET)" = "development" ]; then
  # 守衛以 lockfile 戳記驗證相依完整性:首次啟動與「package-lock 變更後」都會觸發重新預熱
  # (2026-08-26 Phase 12 實測:只驗 vite 存在偵測不到相依漂移,加新套件後容器必然啟動失敗;
  #  與 Phase 10 修正 backend go-offline 守衛同類問題)
  if ! compose "$ENV_NAME" run --rm --no-deps frontend \
       sh -c 'test -x /workspace/node_modules/.bin/vite \
              && cmp -s /workspace/package-lock.json /workspace/node_modules/.ctip-lock-stamp' \
       >/dev/null 2>&1; then
    info "預熱 frontend 相依(首次啟動或 package-lock 變更;npm ci 至 node-modules volume)……"
    compose "$ENV_NAME" run --rm --no-deps frontend \
      sh -c 'npm ci && cp /workspace/package-lock.json /workspace/node_modules/.ctip-lock-stamp'
  fi
fi
# 切換環境時,先收掉「上一個 profile 有、這個 profile 沒有」的服務(§5.10 第 6 步)。
# 四個環境共用同一個 compose 專案名,服務差異只靠 profile,而 compose 刻意<不>把
# profile 停用的服務視為 orphan(--remove-orphans 對它們無效)——因此必須自己算差集。
# 少了這一步,先跑 staging(full)再跑 mvp 會留下 redis/kafka/elasticsearch/prometheus/grafana
# 五個容器,M1-14 的「只有三個容器」變成不可能通過,gate 跑完一次就再也重跑不了。
SURPLUS="$(comm -23 \
  <(compose "$ENV_NAME" ps --services 2>/dev/null | sort) \
  <(compose "$ENV_NAME" config --services | sort) | tr '\n' ' ')"
if [ -n "${SURPLUS// /}" ]; then
  info "移除不屬於 ${ENV_NAME} 的服務:${SURPLUS}"
  # shellcheck disable=SC2086
  compose "$ENV_NAME" rm -sfv ${SURPLUS} >/dev/null
fi

info "啟動 ${ENV_NAME} 環境……"
compose "$ENV_NAME" up -d --remove-orphans

# 6. 等待 healthcheck 並印出狀態與存取網址
info "等待服務 healthcheck……"
DEADLINE=$(( $(date +%s) + 300 ))
while :; do
  PS_STATE="$(compose "$ENV_NAME" ps -a --format '{{.Service}} {{.State}} {{.Health}}')"
  # 任何服務異常退出即失敗,不空等 healthcheck
  DEAD="$(printf '%s\n' "$PS_STATE" | awk '$2 == "exited" || $2 == "dead" { print $1 }')"
  if [ -n "$DEAD" ]; then
    compose "$ENV_NAME" ps -a
    die "服務異常退出:$(printf '%s' "$DEAD" | tr '\n' ' ')。請看 logs.sh <service> ${ENV_NAME}"
  fi
  # 仍在 starting / unhealthy / created / restarting 的服務(無 healthcheck 的執行中服務視為就緒)
  NOT_READY="$(printf '%s\n' "$PS_STATE" \
    | awk '$3 == "starting" || $3 == "unhealthy" || $2 == "created" || $2 == "restarting" { print $1 }')"
  [ -z "$NOT_READY" ] && break
  if [ "$(date +%s)" -ge "$DEADLINE" ]; then
    compose "$ENV_NAME" ps -a
    die "等待 healthcheck 逾時(300s)。未就緒:$(printf '%s' "$NOT_READY" | tr '\n' ' ')"
  fi
  sleep 3
done

ok "全部服務就緒。"
compose "$ENV_NAME" ps

FRONTEND_BIND="$(env_get "$ENV_FILE" FRONTEND_BIND)"
BACKEND_BIND="$(env_get "$ENV_FILE" BACKEND_BIND)"
SWAGGER="$(env_get "$ENV_FILE" SWAGGER_ENABLED)"
info ""
info "存取網址:"
info "  Frontend : http://${FRONTEND_BIND:-127.0.0.1:3000}"
info "  Backend  : http://${BACKEND_BIND:-127.0.0.1:8080}"
info "  Health   : http://${BACKEND_BIND:-127.0.0.1:8080}/actuator/health"
if [ "${SWAGGER:-false}" = "true" ]; then
  info "  Swagger  : http://${BACKEND_BIND:-127.0.0.1:8080}/swagger-ui/index.html"
fi
