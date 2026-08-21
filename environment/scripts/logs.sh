#!/usr/bin/env bash
# 追蹤 CTIP 服務日誌(docs/spec/05-environment.md §5.10)。
# 用法:./environment/scripts/logs.sh <service> <mvp|dev|staging|prod>

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

SERVICE="${1:-}"
ENV_NAME="${2:-}"
[ -n "$SERVICE" ] || die "用法:logs.sh <service> <mvp|dev|staging|prod>"
validate_env "$ENV_NAME"
require_env_file "$ENV_NAME"

exec docker compose --env-file "$(env_file_path "$ENV_NAME")" -f "${COMPOSE_FILE}" \
  logs -f --tail 200 "$SERVICE"
