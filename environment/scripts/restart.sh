#!/usr/bin/env bash
# 重啟 CTIP 服務(docs/spec/05-environment.md §5.10)。
# 用法:./environment/scripts/restart.sh <mvp|dev|staging|prod> [service]

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

ENV_NAME="${1:-}"
SERVICE="${2:-}"
validate_env "$ENV_NAME"
require_env_file "$ENV_NAME"

if [ -n "$SERVICE" ]; then
  compose "$ENV_NAME" restart "$SERVICE"
  ok "已重啟 ${ENV_NAME}/${SERVICE}。"
else
  compose "$ENV_NAME" restart
  ok "已重啟 ${ENV_NAME} 全部服務。"
fi
