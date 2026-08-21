# Phase 2 — Environment + Docker  `[M1]`

## 前置條件
- Phase 1 完成判準全綠

## 交付物
- `environment/docker-compose.yml`（唯一，依 [05 §5.6](../05-environment.md#56-compose-骨架) 骨架）
- `environment/docker/backend/Dockerfile`、`environment/docker/frontend/Dockerfile`（三個 stage，**COPY 路徑含模組前綴**）
- 五份 `.env.*.example`（`.env.example` 為說明用，列出全部變數與註解）
- `environment/config/nginx/default.conf`（含安全標頭與 SPA fallback）
- `environment/scripts/`：`up.sh`、`down.sh`、`restart.sh`、`logs.sh`、`migrate.sh`、`reload.sh`、`dod.sh`、`_common.sh`
- `environment/README.md`
- `.github/workflows/compose-validate.yml`

## 治理規格
- [05-environment.md](../05-environment.md) 全檔（特別是 §5.3 Dockerfile、§5.5 profile 差異表、§5.8 四項阻斷缺陷、§5.10 腳本契約）

## 完成判準
```bash
for e in mvp dev staging prod; do
  docker compose --env-file environment/.env.$e.example -f environment/docker-compose.yml config -q
done
# prod 不得含原始碼掛載
! docker compose --env-file environment/.env.prod.example -f environment/docker-compose.yml config | grep -qE '\.\./(backend|frontend)'
# prod 不得含 JDWP
! docker compose --env-file environment/.env.prod.example -f environment/docker-compose.yml config | grep -qi jdwp
# staging/prod 樣板不得出現 MOUNT 變數
! grep -q '_MOUNT_' environment/.env.prod.example environment/.env.staging.example
docker compose --env-file environment/.env.mvp.example -f environment/docker-compose.yml build
```

## 不得做的事
- 不得建立 `docker-compose.<env>.yml` 或 `Dockerfile.<env>`
- 不得在 `.env.prod.example` / `.env.staging.example` 放 `*_MOUNT_*` 變數
- 不得在任何 `.example` 放真實 secret（值必須是明顯假值，如 `CHANGE_ME_MIN_32_BYTES`）
- 不得讓 `dev` 使用 `COMPOSE_PROFILES=full`（應為 `standard`）
- 不得讓 dev 的 `FRONTEND_BIND` 與 `FRONTEND_CONTAINER_PORT` 不一致
