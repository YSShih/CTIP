#!/usr/bin/env bash
# CTIP 部署腳本共用邏輯(docs/spec/05-environment.md §5.10)。
# 由其他腳本 source,不直接執行。

set -euo pipefail

_COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="$(cd "${_COMMON_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${ENV_DIR}/.." && pwd)"
COMPOSE_FILE="${ENV_DIR}/docker-compose.yml"

# JWT_SECRET 的樣板值:up.sh 與 StartupValidator 皆以此判定「未設定真實 secret」
JWT_SECRET_TEMPLATE="CHANGE_ME_MIN_32_BYTES_REPLACE_THIS"

if [ -t 1 ]; then
  C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RESET=$'\033[0m'
else
  C_RED=""; C_GREEN=""; C_YELLOW=""; C_RESET=""
fi

info() { printf '%s\n' "$*"; }
ok()   { printf '%s[OK]%s %s\n' "${C_GREEN}" "${C_RESET}" "$*"; }
warn() { printf '%s[WARN]%s %s\n' "${C_YELLOW}" "${C_RESET}" "$*" >&2; }
die()  { printf '%s[ERROR]%s %s\n' "${C_RED}" "${C_RESET}" "$*" >&2; exit 1; }

# 啟動失敗時,把「訊息看不出原因、但只有一種成因」的症狀翻譯成可執行的修法。
# 用法:diagnose_startup_failure <env> <未就緒的服務清單>
#
# 比對一律用 bash 的 case 而非 `| grep -q`:pipefail 下 grep -q 提早退出會讓左側吃 SIGPIPE,
# 條件因此可能假性不成立(§5.10 已記過同一個坑)。
diagnose_startup_failure() {
  local e="$1" services="$2" logs project
  # shellcheck disable=SC2086
  logs="$(compose "$e" logs --tail 200 $services 2>&1 || true)"
  project="$(env_get "$(env_file_path "$e")" PROJECT_NAME)"
  project="${project:-ctip}"

  # 四個環境共用同一個 compose 專案名與 postgres-data volume(§5.5)。volume 一旦被某個
  # 環境初始化,POSTGRES_PASSWORD 就固定了——換一組密碼的環境只會拿到認證失敗,
  # 而錯誤訊息裡完全沒有「你換了環境」這件事(2026-08-29 Phase 19 實測)。
  case "$logs" in
    *"password authentication failed"*)
      warn "偵測到資料庫認證失敗。四個環境共用同一個 compose 專案與 postgres-data volume,"
      warn "volume 已被另一個環境以不同的 POSTGRES_PASSWORD 初始化。二選一:"
      warn "  1) 讓 environment/.env.${e} 的 POSTGRES_* 與先前啟動過的環境一致(建議)"
      warn "  2) 清掉資料重來:docker volume rm ${project}_postgres-data"
      ;;
  esac

  # Boot 的 ES autoconfig 對空 uris 直接讓應用死掉,即使 SEARCH_BACKEND=postgres(13 §13.7)
  case "$logs" in
    *"hosts must not be null nor empty"*)
      warn "偵測到 Elasticsearch client 因空的 uris 而無法建立。"
      warn "請確認 compose 對 ELASTICSEARCH_URL 的預設值不是空字串(05 §5.8.2 第 7 項)。"
      ;;
  esac

  # image 沿用了另一個 build target(05 §5.8.2 第 5 項)
  case "$logs" in
    *"mvnw: No such file or directory"*|*"Could not read package.json"*)
      warn "偵測到 production 的檔案系統配上 development 的 CMD(或反之)——image 沿用了另一個"
      warn "build target。重建該服務:"
      warn "  docker compose --env-file environment/.env.${e} -f environment/docker-compose.yml build <service>"
      ;;
  esac
}

# 驗證環境參數為 mvp|dev|staging|prod
validate_env() {
  case "${1:-}" in
    mvp|dev|staging|prod) ;;
    *) die "環境參數必須為 mvp | dev | staging | prod(收到:'${1:-}')" ;;
  esac
}

env_file_path() { printf '%s/.env.%s' "${ENV_DIR}" "$1"; }

# 檢查 environment/.env.<env> 存在;不存在則提示由 .example 複製並結束
require_env_file() {
  local e="$1" f
  f="$(env_file_path "$e")"
  if [ ! -f "$f" ]; then
    die "找不到 environment/.env.${e}。請先建立:cp environment/.env.${e}.example environment/.env.${e},並填入真實值。"
  fi
}

# 讀取 env 檔中某變數的值(取最後一次出現;不 source,避免執行任意內容)
env_get() {
  local file="$1" key="$2"
  grep -E "^${key}=" "$file" | tail -n 1 | cut -d= -f2- || true
}

# 統一的 compose 呼叫:compose <env> <args...>
compose() {
  local e="$1"; shift
  docker compose --env-file "$(env_file_path "$e")" -f "${COMPOSE_FILE}" "$@"
}

# 版本比較:version_ge <實際版本> <最低版本>
version_ge() {
  [ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -n 1)" = "$2" ]
}

# 驗證 Docker >= 27 且 Compose >= 2.24(§5.10 第 4 步)
check_docker() {
  command -v docker >/dev/null 2>&1 || die "找不到 docker,請先安裝 Docker。"
  local dv cv
  dv="$(docker version --format '{{.Server.Version}}' 2>/dev/null)" \
    || die "Docker daemon 未執行或無法連線。"
  version_ge "$dv" "27.0.0" || die "Docker 版本過舊:${dv}(需 >= 27)"
  cv="$(docker compose version --short 2>/dev/null)" \
    || die "docker compose 不可用(需 Compose plugin >= 2.24)。"
  cv="${cv#v}"
  version_ge "$cv" "2.24.0" || die "Docker Compose 版本過舊:${cv}(需 >= 2.24)"
}

# prod 額外檢查(§5.10 第 3 步):JWT_SECRET 非樣板值且 >= 32 bytes、CORS 非 *
check_prod_env_file() {
  local f="$1" jwt cors
  jwt="$(env_get "$f" JWT_SECRET)"
  case "$jwt" in
    ""|*CHANGE_ME*|"${JWT_SECRET_TEMPLATE}")
      die "prod 的 JWT_SECRET 仍是樣板值,請改為 >= 32 bytes 的真實 secret(來源:secret manager)。" ;;
  esac
  if [ "$(printf '%s' "$jwt" | LC_ALL=C wc -c)" -lt 32 ]; then
    die "prod 的 JWT_SECRET 長度不足 32 bytes。"
  fi
  cors="$(env_get "$f" CORS_ALLOWED_ORIGINS)"
  case "$cors" in
    ""|*'*'*) die "prod 的 CORS_ALLOWED_ORIGINS 不得為空或含 *(目前:'${cors}')。" ;;
  esac
}
