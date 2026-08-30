# 開發環境上手

> 給**第一次動這個 repo 的人**。環境契約的規範性內容在
> [05-environment.md](../spec/05-environment.md);腳本細節在
> [`environment/README.md`](../../environment/README.md)。

---

## 1. 先備條件

| 工具 | 版本 | 用途 |
|---|---|---|
| Docker | ≥ 27(Compose ≥ 2.24) | 跑整套環境 |
| JDK | 25 | 在 host 上直接跑 Maven 與測試 |
| Node.js | 24 | 前端 |
| `jq`、`python3` | 任意 | `dod.sh` 的部分檢查需要 |
| `gh`(GitHub CLI) | 任意 | **只有 `dod.sh full` 的 M3-19 需要**(它查最後一次 CI run) |

Maven 用專案內的 wrapper,**不要另外安裝**:指令一律是
`./backend/mvnw -f backend/pom.xml …`(repo 根目錄沒有 `./mvnw`)。

---

## 2. 三分鐘啟動

```bash
[ -f environment/.env.mvp ] || cp environment/.env.mvp.example environment/.env.mvp
./environment/scripts/up.sh mvp
curl -fsS http://localhost:8080/actuator/health
```

`up.sh` 會驗證 Docker 版本、啟動、等 healthcheck、印出網址。
**首次啟動會預熱容器內的 Maven 與 npm 快取,需要數分鐘**(相依變更後會自動重新預熱)。

起來之後:

| | 位置 |
|---|---|
| 前端 | <http://127.0.0.1:5173> |
| 後端健康檢查 | <http://127.0.0.1:8080/actuator/health> |
| Swagger UI | <http://127.0.0.1:8080/swagger-ui/index.html> |
| PostgreSQL | `127.0.0.1:5432`(帳密見 `environment/.env.mvp`) |

停止:`./environment/scripts/down.sh mvp`(加 `--volumes` 才會刪資料,需二次確認)。

### 四種環境

`mvp`(前端+後端+PG)、`dev`(+Redis)、`staging` / `prod`(+Kafka、Elasticsearch、
Prometheus、Grafana,跑 production 映像)。差異表見
[`environment/README.md`](../../environment/README.md)。

---

## 3. 改了程式怎麼辦

| 改了什麼 | 做什麼 |
|---|---|
| 後端 Java | `./environment/scripts/reload.sh backend mvp`(容器內編譯 + trigger file 觸發熱重啟) |
| 前端 | 不用做事,Vite HMR(容器內以 `--host 0.0.0.0`;**host 與容器 port 必須一致(皆 5173)**,否則 HMR client 會連向錯誤的 port 而靜默失效) |
| Flyway migration | `./environment/scripts/migrate.sh mvp`,或重啟 backend |
| 相依(pom / package.json) | 重跑 `up.sh`,預熱守衛會偵測到漂移並重新預熱 |

`vite.config.ts` 開著 `watch.usePolling`([12 §12.7](../spec/12-frontend.md#127-dev-環境)):
bind mount 在部分平台需要 polling 才偵測得到變更。代價是較高的 CPU;
**若在你的平台上 inotify 正常運作,可以關掉它**——這是每台機器自己決定的事,不是專案設定。

⚠️ **不要在 host 上跑 `mvnw` 的同時讓 dev 容器跑著**——兩邊共享 `target/`。
熱重啟已改由 trigger file 控制([ADR 0010](../architecture/decisions/0010-devtools-trigger-file.md)),
但 gate 執行期間仍**不得**在 host 端跑任何 Maven 指令
([15 §15.0](../spec/15-dod-gates.md#150-執行方式) 第 4 點)。

---

## 4. 測試

```bash
# 後端 L1–L3(整合測試自帶 Testcontainers,不需先啟動環境)
./backend/mvnw -f backend/pom.xml clean verify -Ptest-integration
```

測試分層以 JUnit tag 控制([14 §14.1](../spec/14-testing.md)):

| Profile | 涵蓋 | 用在 |
|---|---|---|
| *(預設)* | `unit` | 最快的回饋 |
| `-Ptest-slice` | + `slice` | |
| `-Ptest-integration` | + `integration` | **每個 phase 收尾跑這個** |
| `-Ptest-all` | + `heavy`(L4) | nightly CI |

`verify` 同時綁 Spotless、Checkstyle 與 JaCoCo 門檻——格式或覆蓋率不足會讓 build 失敗。

前端:

```sh
cd frontend && npm ci
npm run lint && npm run format:check && npx tsc --noEmit && npm run test
npm run api:check     # generated 型別必須與 committed 的 openapi.json 一致
npx playwright install chromium && npm run e2e
```

⚠️ **`npm run api:check` 在 commit 之前必然紅**:它比對的是**已 commit** 的 generated 型別。

---

## 5. DoD Gate

```bash
./environment/scripts/dod.sh mvp        # M1,38 項
./environment/scripts/dod.sh phase2     # M2,27 項
./environment/scripts/dod.sh full       # M3,25 項
./environment/scripts/dod.sh full --only M3-23
```

規則:逐項執行不中止、全綠才 exit 0、結尾列出「需人工確認」清單。
**同一個 repo 同時只能有一個 gate 在執行**(互斥鎖);
判斷是否結束一律看行程退出碼,**不要比對 log 內容**
([15 §15.0](../spec/15-dod-gates.md#150-執行方式) 第 5 點)。

`dod.sh full` 有三項本機前置,腳本自己備不了:

| 項目 | 需要 |
|---|---|
| M3-17 | 真實的 `environment/.env.prod`(依 `.gitignore` 不進版控,須由樣板建立並填入真值) |
| M3-19 | 安裝 `gh`、已推上 GitHub 並跑過 CI |
| M3-20 | 先跑過一次建置(`mvnw package` 產生 `backend/*/target/bom.json`,`npm run sbom` 產生 `frontend/sbom.json`) |

---

## 6. CI/CD

`.github/workflows/` 共 **11 支**([13 §13.8](../spec/13-platform-ops.md#138-cicd-phase-23--m3基本流程自-m1-就要有)):

| Workflow | 觸發 | 內容 |
|---|---|---|
| `backend-test` | push / PR | L1–L3 + JaCoCo + ArchUnit |
| `backend-lint` | push / PR | Spotless check + Checkstyle |
| `frontend-test` | push / PR | ESLint、Prettier、`tsc`、Vitest |
| `build` | push / PR | Maven package、Vite build、兩份 SBOM |
| `compose-validate` | push / PR | 四種環境的 `compose config` + 防呆檢查 |
| `openapi-check` | push / PR | 產生 openapi.json、比對 committed、破壞性變更檢查 |
| `docker-build` | push / PR | 建置兩個映像(推 main 時推 GHCR,含 SBOM/provenance attestation) |
| `security` | push / PR / 每日 | Gitleaks(整段歷史)、Trivy fs、Trivy image |
| `heavy-test` | nightly / 手動 | `-Ptest-all`(含 L4) |
| `deploy-staging` | push main / 手動 | placeholder,綁 `staging` environment |
| `deploy-prod` | **只能手動** | placeholder,綁 `production` protected environment |

`dod.sh full` 的 **M3-19 會先檢查這 11 個檔案都在**,再看最後一次 run 的結論——
「只有兩支且都綠」曾經讓六支逾期的 workflow 藏了十個 phase
([ADR 0022](../architecture/decisions/0022-orphan-deliverables.md))。

### 首次啟用 CI 時必做的兩件事(GitHub 設定,版控檔案表達不了)

1. **建立 `production` environment 並加上 required reviewers**——
   `deploy-prod.yml` 綁了這個 environment,但**核准規則存在 repo 設定裡**。
   沒有這一步,workflow 仍會執行,只是沒有人工關卡。
   (Settings → Environments → New environment → `production` → Required reviewers)
2. **啟用 Dependabot alerts**(Settings → Code security)。
   `.github/dependabot.yml` 負責的是「定期開升級 PR」,alerts 是另一個開關。

⚠️ **不得自行合併版本升級 PR**([06 §6.1.2](../spec/06-tech-stack.md#612-凍結與浮動強制));
major 升級須人工核准並寫 ADR。

---

## 7. 佈署

`deploy-staging.yml` / `deploy-prod.yml` 目前是 **placeholder**:本專案沒有實機,
寫死一段 `ssh` / `kubectl` 會是[規則 16](../spec/00-master.md#04-coding-llm-執行規則) 禁止的假實作。
接上主機時只需填入各自的「佈署」步驟——觸發條件、環境綁定與佈署前檢查已經就位。

正式環境的前置:

- `environment/.env.prod` 由樣板建立,`JWT_SECRET` ≥ 32 bytes、`CORS_ALLOWED_ORIGINS` 不得含 `*`、
  `SWAGGER_ENABLED=false`(啟動守衛與 `dod.sh full M3-17` 都會檢查)
- **多實例佈署前必須先為 `@Scheduled` 任務引入 ShedLock**
  ([08 §8.7](../spec/08-ingestion-sdk.md)、[`../deployment/rate-limiting.md`](../deployment/rate-limiting.md));
  現行排程假設單一實例
- 反向代理層負責 TLS 與 `Strict-Transport-Security`

---

## 8. 專案慣例

- **不使用 Lombok**([ADR 0036](../architecture/decisions/0036-no-lombok.md));logger 與建構子手寫
- **不得自行升版任何 Maven / npm 相依**;版本表是 [06 §6.2](../spec/06-tech-stack.md#62-版本表)
- 命名依 [02 §2.1](../spec/02-ddd-model.md#21-ubiquitous-language-詞彙表中英對照) 的詞彙表
- 功能與測試同時產生;**不留假 TODO 或 placeholder**
- 規格模糊時的優先序:安全性 > 可維護性 > 可測試性 > 可擴充性 > 向後相容 > Clean Architecture 邊界,
  做了決定就在 [`docs/architecture/decisions/`](../architecture/decisions/) 寫 ADR

貢獻流程見 [`CONTRIBUTING.md`](../../CONTRIBUTING.md)。
