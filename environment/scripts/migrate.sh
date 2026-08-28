#!/usr/bin/env bash
# 手動觸發 Flyway migration(docs/spec/05-environment.md §5.10)。
# 用法:./environment/scripts/migrate.sh <mvp|dev|staging|prod>
#
# 正常情況下 schema 由應用啟動時的 Flyway 自動套用(§5.9);
# 本腳本用於「不啟動應用、只跑 migration」的場景。
# 依賴 backend 的 Flyway 設定與 migration 檔(Phase 3 起提供)。

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

ENV_NAME="${1:-}"
validate_env "$ENV_NAME"
require_env_file "$ENV_NAME"
ENV_FILE="$(env_file_path "$ENV_NAME")"

DB="$(env_get "$ENV_FILE" POSTGRES_DB)"
USER="$(env_get "$ENV_FILE" POSTGRES_USER)"
PASSWORD="$(env_get "$ENV_FILE" POSTGRES_PASSWORD)"
BIND="$(env_get "$ENV_FILE" POSTGRES_BIND)"
BIND="${BIND:-127.0.0.1:5432}"

[ -n "$DB" ] && [ -n "$USER" ] && [ -n "$PASSWORD" ] \
  || die "environment/.env.${ENV_NAME} 缺少 POSTGRES_DB / POSTGRES_USER / POSTGRES_PASSWORD。"

# 從 host 連進 compose 發布的 PostgreSQL port
JDBC_URL="jdbc:postgresql://${BIND}/${DB}"

info "對 ${ENV_NAME}(${JDBC_URL})執行 Flyway migration……"
# -N(非遞迴):plugin 宣告在 parent,locations 直指 ctip-app 的 migration 目錄。
# 不遞迴就不必解析模組相依——單獨 -pl ctip-app 會因 SNAPSHOT 未安裝而失敗。
"${REPO_ROOT}/backend/mvnw" -f "${REPO_ROOT}/backend/pom.xml" -N \
  flyway:migrate \
  -Dflyway.url="${JDBC_URL}" \
  -Dflyway.user="${USER}" \
  -Dflyway.password="${PASSWORD}"
ok "Migration 完成。"
