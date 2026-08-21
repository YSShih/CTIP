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
info "啟動 ${ENV_NAME} 環境……"
compose "$ENV_NAME" up -d

# 6. 等待 healthcheck 並印出狀態與存取網址
info "等待服務 healthcheck……"
DEADLINE=$(( $(date +%s) + 300 ))
while :; do
  # 統計仍在 starting / unhealthy 的容器數(無 healthcheck 的服務視為就緒)
  NOT_READY="$(compose "$ENV_NAME" ps --format '{{.Service}} {{.Health}}' \
    | awk '$2 == "starting" || $2 == "unhealthy" { print $1 }')"
  [ -z "$NOT_READY" ] && break
  if [ "$(date +%s)" -ge "$DEADLINE" ]; then
    compose "$ENV_NAME" ps
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
