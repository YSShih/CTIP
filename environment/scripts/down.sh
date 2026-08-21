#!/usr/bin/env bash
# 停止 CTIP(docs/spec/05-environment.md §5.10)。
# 用法:./environment/scripts/down.sh <mvp|dev|staging|prod> [--volumes]
# --volumes 會一併刪除 named volumes(資料庫資料!),需二次確認。

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

ENV_NAME="${1:-}"
validate_env "$ENV_NAME"
require_env_file "$ENV_NAME"

REMOVE_VOLUMES=false
if [ "${2:-}" = "--volumes" ]; then
  REMOVE_VOLUMES=true
elif [ -n "${2:-}" ]; then
  die "未知參數:'${2}'(僅支援 --volumes)"
fi

if [ "$REMOVE_VOLUMES" = true ]; then
  warn "--volumes 會刪除 ${ENV_NAME} 的所有 named volumes(含 PostgreSQL 資料),不可復原。"
  printf '確定要刪除?輸入 yes 繼續:'
  read -r ANSWER
  [ "$ANSWER" = "yes" ] || die "已取消。"
  compose "$ENV_NAME" down --volumes
  ok "${ENV_NAME} 已停止,volumes 已刪除。"
else
  compose "$ENV_NAME" down
  ok "${ENV_NAME} 已停止。"
fi
