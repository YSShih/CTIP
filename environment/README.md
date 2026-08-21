# CTIP — Environment

本目錄包含 CTIP 的全部基礎設施檔案:唯一的 compose 檔、兩份 multi-stage Dockerfile、
環境樣板、基礎設施設定與部署腳本。**不放任何業務邏輯。**
規格出處:`docs/spec/05-environment.md`(強制契約)。

## 快速開始

```bash
# 1. 由樣板建立環境檔(真實 .env 不進版控)
cp environment/.env.mvp.example environment/.env.mvp

# 2. 啟動(自動驗證 Docker 版本、等待 healthcheck、印出網址)
./environment/scripts/up.sh mvp
```

## 四種環境

環境差異一律透過**環境變數 + Compose profiles** 控制;
**禁止**建立 `docker-compose.<env>.yml` 或 `Dockerfile.<env>`。

| | mvp | dev | staging | prod |
|---|---|---|---|---|
| 服務 | frontend, backend, postgres | +redis | +kafka, es, prometheus, grafana | 同 staging |
| `COMPOSE_PROFILES` | *(空)* | `standard` | `full` | `full` |
| build target | development | development | production | production |
| 原始碼掛載 | ✔(rw) | ✔(rw) | ✘(.noop) | ✘(.noop) |
| JDWP debug | ✔ | ✔ | ✘ | ✘ |
| Swagger | ✔ | ✔ | ✔ | ✘ |
| 綁定 | 127.0.0.1 | 127.0.0.1 | 0.0.0.0 | 由代理層決定 |

完整差異表見 `docs/spec/05-environment.md` §5.5。

## Port 一覽(開發環境預設)

| 服務 | Host | 容器 |
|---|---|---|
| frontend(Vite dev) | 127.0.0.1:5173 | 5173(兩端一致,HMR 才會生效) |
| backend | 127.0.0.1:8080 | 8080 |
| backend JDWP | 127.0.0.1:5005 | 5005(staging/prod 無 agent 監聽,對映惰性) |
| postgres | 127.0.0.1:5432 | 5432 |
| redis(dev 起) | 127.0.0.1:6379 | 6379 |

## 腳本

| 腳本 | 用法 | 說明 |
|---|---|---|
| `up.sh` | `up.sh <env>` | 驗證參數/env 檔/Docker 版本(≥27, Compose ≥2.24)→ 啟動 → 等 healthcheck → 印狀態與網址;prod 額外檢查 JWT_SECRET 與 CORS |
| `down.sh` | `down.sh <env> [--volumes]` | 停止;`--volumes` 會刪資料,需輸入 `yes` 二次確認 |
| `restart.sh` | `restart.sh <env> [service]` | 重啟全部或單一服務 |
| `logs.sh` | `logs.sh <service> <env>` | 追蹤日誌 |
| `migrate.sh` | `migrate.sh <env>` | 手動觸發 `mvn flyway:migrate`(migration 檔自 Phase 3 起提供) |
| `reload.sh` | `reload.sh <service> <env>` | 後端熱替換,見下節 |
| `dod.sh` | `dod.sh <mvp\|phase2\|full> [id] [--only id] [--skip id]` | 執行 DoD Gate(`docs/spec/15-dod-gates.md`);逐項 PASS/FAIL、不因單項失敗中止、結尾列出失敗與需人工確認清單 |

共用邏輯在 `_common.sh`(被其他腳本 source,不直接執行)。

## Hot reload(`docs/spec/05-environment.md` §5.11)

- **frontend:真 HMR。** Vite dev server 在容器內以 `--host 0.0.0.0` 執行,
  host 與容器 port 一致(皆 5173),修改 `.tsx` 後瀏覽器自動更新,無需任何指令。
- **backend:腳本觸發。** 容器內沒有程序會自動編譯 `.java`;修改後執行:

  ```bash
  ./environment/scripts/reload.sh backend mvp
  ```

  內部在容器裡跑 `./mvnw -o -q -pl ctip-app -am compile`,寫入被掛載的
  `target/classes`,Spring DevTools 偵測 classpath 變更後重啟 application context。

## Secrets

- 真實 `.env.*` 已被 `.gitignore` 排除,**絕不 commit**;只有 `.env.*.example` 進版控。
- 樣板中所有 secret 都是明顯假值(`CHANGE_ME_*`);prod 的值必須來自
  secret manager / 部署平台 secret。
- `up.sh prod` 會拒絕樣板 `JWT_SECRET`(或 < 32 bytes)與含 `*` 的 `CORS_ALLOWED_ORIGINS`。
- 任何 `VITE_` 開頭的變數都會打包進前端 bundle,視為公開資訊。

## CI

`.github/workflows/compose-validate.yml` 對每次 push / PR 驗證:
四種環境的 `docker compose config`、prod 無原始碼掛載、prod 無 JDWP、
staging/prod 樣板無 `*_MOUNT_*` 變數。
