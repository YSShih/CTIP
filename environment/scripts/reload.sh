#!/usr/bin/env bash
# 熱替換(docs/spec/05-environment.md §5.11)。
# 用法:./environment/scripts/reload.sh <service> <mvp|dev|staging|prod>
#
# backend:在容器內重新編譯,寫入被掛載的 target/classes;
#         Spring DevTools 偵測 classpath 變更後自動重啟 application context。
# frontend:Vite HMR 全自動,無需(也不應)手動 reload。

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

SERVICE="${1:-}"
ENV_NAME="${2:-}"
[ -n "$SERVICE" ] || die "用法:reload.sh <service> <mvp|dev|staging|prod>"
validate_env "$ENV_NAME"

case "$SERVICE" in
  backend|frontend) ;;
  *) die "未知 service:'${SERVICE}'(支援 backend | frontend)" ;;
esac
case "$ENV_NAME" in
  staging|prod) die "reload 僅適用於掛載原始碼的環境(mvp / dev);${ENV_NAME} 跑的是 production 映像,請重新 build。" ;;
esac

require_env_file "$ENV_NAME"

case "$SERVICE" in
  backend)
    info "在 backend 容器內重新編譯(離線模式)……"
    compose "$ENV_NAME" exec backend ./mvnw -o -q -pl ctip-app -am compile
    ok "編譯完成;Spring DevTools 將偵測 classpath 變更並重啟 application context。"
    ;;
  frontend)
    info "frontend 使用 Vite HMR:修改 .tsx 檔即自動生效,無需 reload。"
    ;;
  *)
    die "未知 service:'${SERVICE}'(支援 backend | frontend)"
    ;;
esac
