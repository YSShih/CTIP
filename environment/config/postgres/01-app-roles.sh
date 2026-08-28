#!/usr/bin/env bash
# 建立非特權的應用角色(docs/spec/13-platform-ops.md §13.5;ADR 0021)。
#
# 為什麼需要:POSTGRES_USER(預設 ctip)是 postgres image 的**初始 superuser**
# (實測 rolsuper = t),而 superuser 繞過所有 GRANT/REVOKE 檢查。
# Phase 21 的 `REVOKE UPDATE, DELETE ON audit_logs` 對它完全無效,
# 而 M3-09 明文要求「應用角色的 UPDATE/DELETE 必須被 **DB** 拒絕(不是被應用碼拒絕)」
# ——以單一 superuser 連線的模型,那一項永遠不可能通過。
#
# 角色分工:
#   ctip(POSTGRES_USER,superuser) — 只給 Flyway 跑 migration(需要 DDL 與擴充)
#   ctip_app                      — 應用執行期的連線,只有 DML
#   ctip_retention                — 保留政策清理任務專用(Phase 21 才會真正使用)
#
# 注意:initdb 腳本只在**資料目錄為空**時執行一次。既有的開發資料庫需重建 volume,
# 或手動執行本檔內容。
set -euo pipefail

APP_USER="${POSTGRES_APP_USER:-ctip_app}"
APP_PASSWORD="${POSTGRES_APP_PASSWORD:?POSTGRES_APP_PASSWORD 未設定}"
RETENTION_USER="${POSTGRES_RETENTION_USER:-ctip_retention}"
RETENTION_PASSWORD="${POSTGRES_RETENTION_PASSWORD:?POSTGRES_RETENTION_PASSWORD 未設定}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
-- CREATE ROLE 沒有 IF NOT EXISTS,以 DO 區塊做冪等
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${APP_USER}') THEN
    CREATE ROLE ${APP_USER} LOGIN PASSWORD '${APP_PASSWORD}' NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${RETENTION_USER}') THEN
    CREATE ROLE ${RETENTION_USER} LOGIN PASSWORD '${RETENTION_PASSWORD}' NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
END
\$\$;

GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO ${APP_USER}, ${RETENTION_USER};
GRANT USAGE ON SCHEMA public TO ${APP_USER}, ${RETENTION_USER};

-- 既有物件(initdb 階段通常為空,保留以支援手動補跑)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${APP_USER};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${APP_USER};

-- 關鍵:Flyway 之後才建表,必須靠 default privileges 讓新表自動授權給應用角色
ALTER DEFAULT PRIVILEGES FOR ROLE ${POSTGRES_USER} IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${APP_USER};
ALTER DEFAULT PRIVILEGES FOR ROLE ${POSTGRES_USER} IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO ${APP_USER};

-- ctip_retention 的實際授權由 Phase 21 的 V33 依 audit_logs 逐表給,
-- 這裡只建立角色與連線權限,不預先開放任何表(規則 16:不留 placeholder 授權)。
SQL
