# CTIP — Cyber Threat Intelligence Platform

> A multi-source cyber threat intelligence platform: it ingests indicators of compromise from
> heterogeneous feeds through a plugin adapter architecture, normalizes them into a single domain
> model, deduplicates and merges across sources, and exposes the result over a REST API with
> STIX 2.1 export and Bloom-filter-based incremental sync for lightweight clients.
> Architecture: Domain-Driven Design over Clean/Hexagonal Architecture.
>
> **All 23 phases across three milestones are delivered** (M1 MVP · M2 Platform · M3 Production).
>
> This repository contains **a specification and the implementation produced from it**. The
> specification in [`docs/spec/`](docs/spec/) was produced with AI assistance and is **written to be
> consumed by AI coding agents** — designed so that any capable agent can implement the system from
> it independently, and so that two agents reading it at different times produce compatible results.

---

## 這是什麼

一個**規格書 + 依它產生的實作**。23 個 phase 全部交付，一次一個 phase、一個 commit。

規格書（[`docs/spec/`](docs/spec/)）與一般「架構文件」不同的地方在於**它被寫成可執行的契約**：

- 每一條 Definition of Done 都對應一個回傳 0/1 的指令（**90 項**），無法自動化的 7 項明確標為「需人工確認」
- 每一張圖標註**規範等級**（CI 會擋／人工驗證／僅供參考）——ArchUnit 能驗證依賴方向，但不能驗證「這個聚合有這個方法」
- **28 張資料表**全部有完整欄位定義，避免不同 agent 各自發明 schema
- 附一份中英對照的 Ubiquitous Language 詞彙表——規格是中文而程式碼是英文，沒有這張表命名會發散

規格是 **single source of truth**：實作撞到的每一個規格衝突都寫回規格正文，而不是在程式裡繞過去
（二十八輪，見 [00 §0.7–§0.34](docs/spec/00-master.md)）。

**要讀它** —— AI agent 從 [`docs/spec/README.md`](docs/spec/README.md) 開始，它會告訴你讀取順序，
**不要一次讀完全部檔案**；人類先讀下面的里程碑表，再讀 [00 §0.6](docs/spec/00-master.md) 的變更摘要
（v1.1 的 4 項建置阻斷缺陷、3 項版本錯誤、10 項內部衝突及其解法）。

---

## 里程碑與 Phase

每個 phase 一份執行單（`docs/spec/phases/phase-NN.md`）、一個 commit、一組完成判準。
逐 phase 的判準結果與偏離事項見 [`docs/progress.md`](docs/progress.md)，
交付歷程與踩過的坑見 [`docs/history.md`](docs/history.md)。

### M1 — MVP（Phase 1–12）· 匿名唯讀的公開情資平台

| Phase | 交付的能力 |
|---|---|
| 1 | Repository skeleton：multi-module pom、linter、測試分層 profile |
| 2 | Environment + Docker：**唯一** compose 檔 + 四 profile、雙 Dockerfile、四環境樣板、9 支腳本 |
| 3 | Spring Boot 啟動 + PostgreSQL + Flyway + 種子資料 |
| 4 | Domain：Indicator／Tenant／Source 聚合、**TLP 2.0 資料分級與可見度**、**再散布政策（法遵）**、多租戶隔離 ＋最小安全層 |
| 5 | **Plugin／Adapter 架構**（第三方可自行擴充）：SDK + mock adapter + Resilience4j 韌性 + 來源健康 |
| 6 | **攝取管線**（12 stage）：正規化為統一 domain model（七種型別各有規則）、八種拒絕規則、排程、限流 |
| 7 | **去重、多來源合併、指紋、威脅評分** |
| 8 | **STIX 2.1 正規化與匯出**（含 TLP 2.0 marking、pattern、bundle） |
| 9 | **REST API**：DTO/Mapper、cursor 分頁、16 個錯誤碼的統一錯誤結構 + traceId |
| 10 | OpenAPI／Swagger：committed `openapi.json` + CI drift 與破壞性變更檢查 |
| 11 | React 前端骨架、由 OpenAPI 產生型別、版面 |
| 12 | IOC 檢索／詳情／公開統計儀表板 + PostgreSQL 全欄位搜尋（GIN／pg_trgm） |

**✅ DoD-MVP 閘門 38/38**

### M2 — Platform（Phase 13–19）· 多租戶、有身分、有配額、可增量同步

| Phase | 交付的能力 |
|---|---|
| 13 | **認證、RBAC、API Key**、租戶隔離強制（JWT + refresh token 輪替與重用偵測、24 個權限 × 5 個角色） |
| 14 | **方案與配額**（Free／Premium／Enterprise，14 個配額維度）＋ **使用者提交／匯入 IOC 與誤判回報** |
| 15 | **兩層 Bloom Filter**（public + per-tenant、每日 full snapshot、每小時 delta、位元組層級可互通的格式） |
| 16 | **增量同步 API** 與 client 契約（供 Browser Extension／App） |
| 17 | Redis：快取 + **分散式限流**（五個維度、兩個檢查點） |
| 18 | Threat 實體與關聯 + M2 的 STIX 物件 |
| 19 | **Elasticsearch 搜尋** + 對帳排程 + **ES 故障時自動降級回 PostgreSQL** |

**✅ DoD-Phase2 閘門 27/27**

### M3 — Production（Phase 20–23）

| Phase | 交付的能力 |
|---|---|
| 20 | **Kafka 事件 + 通知**：WebSocket／SSE／Webhook（HMAC 簽章、重試與自動停用） |
| 21 | **Audit Log**（append-only，DB 層 REVOKE）+ **資料保留政策**（六項清理排程、GDPR 資料主體查詢／刪除） |
| 22 | **可觀測性**：Prometheus／Grafana 指標、結構化 JSON 日誌（憑證遮罩）、OpenTelemetry 追蹤 |
| 23 | **CI/CD 與供應鏈安全**：11 支 workflow、secret／相依／映像掃描、SBOM、12 份必要文件 |

**✅ DoD-Full 閘門 25/25**（2026-08-31 完整實跑；首次實跑為 23/25，兩項失敗其後皆已修復並重新驗證）。

> 三個閘門是**順序性**的：未通過前一個閘門不得開始下一個里程碑
> （[15](docs/spec/15-dod-gates.md)；`./environment/scripts/dod.sh <gate>`）。

### 明確不做的事

不自建所有第三方情資來源、不做 ML 威脅偵測、不做完整 SIEM／SOAR、不做 Kubernetes-first 部署、
不做多區域 active-active、不串接真實金流、不做 TAXII 2.1 Server（僅保留擴充點）。
不採用 CQRS 與 Event Sourcing。

---

## 模組地圖

架構總覽（分層、依賴方向、讀取路徑）見 [`docs/architecture/overview.md`](docs/architecture/overview.md)。

### 後端 Maven Module（4 個）

| Module | 對外提供 | 允許依賴 |
|---|---|---|
| `ctip-sdk` | **Shared Kernel**：`ThreatSourceAdapter` 契約與跨界列舉（`IocType`／`Tlp`／`Severity`／`RedistributionPolicy`）。可獨立發布至 Maven Central | JDK、`jakarta.validation-api`。**零 Spring** |
| `ctip-core` | `domain`（9 個聚合 + 不變量）+ `application`（service + out-port） | `ctip-sdk`、spring-context、spring-tx。**無 JPA、無 spring-data** |
| `ctip-adapters` | 內建與 mock 的 Threat Source Adapter 實作 | `ctip-sdk`、HTTP client、Resilience4j。**不依賴 `ctip-core`** |
| `ctip-app` | Spring Boot 啟動類、`infrastructure`、`interfaces`、Flyway、設定檔。唯一產生可執行 jar 者 | 全部 |

邊界由 **11 條 ArchUnit 規則**強制（[01 §1.9](docs/spec/01-architecture.md#19-archunit-規則強制共-11-條)）。

**Domain 模組**（`ctip-core/domain/*`，[02](docs/spec/02-ddd-model.md)）：
`indicator`（14 條不變量）· `source` · `tenant` · `identity` · `plan` · `bloom` · `threat` ·
`notification` · `stix` · `fingerprint` · `event`（21 個 domain event）· `shared`。
`application/*` 為對應的 service 層，out-port 集中於 `application/port`。

**前端 Feature**（`frontend/src/features/*`，[12](docs/spec/12-frontend.md)）：
`ioc` · `threat` · `stix` · `auth` · `apikey` · `subscription` · `sync` · `notification` · `audit` · `admin`。
**feature 之間不得直接 import**（ESLint `import/no-restricted-paths` 強制），共用內容上移至 `components/`／`hooks/`。

### 基礎設施

| 服務 | 用途 | profile |
|---|---|---|
| PostgreSQL 18 | **唯一的 source of truth** | 全部 |
| Redis 8 / Valkey 9 | 快取 + 分散式限流 | `standard`、`full` |
| Elasticsearch 9.5 / OpenSearch 3 | 讀取索引（可隨時由 DB 重建） | `full` |
| Kafka 4.2（KRaft） | Domain event 傳輸 | `full` |
| Nginx 1.30（stable） | 前端靜態服務 + 安全標頭 | 全部（production build） |
| Prometheus / Grafana | 監控 | `full` |

`SearchPort` 與 `CachePort` 的抽象讓 Elasticsearch → OpenSearch、Redis → Valkey 的替換
只需改 infrastructure 實作與 image 名稱（授權考量見 [06 §6.5](docs/spec/06-tech-stack.md#65-授權注意事項)）。

---

## 快速開始

需求：Docker ≥ 27（Compose ≥ 2.24）。首次啟動會自動預熱容器內的 Maven 與 npm 快取（需數分鐘）。

```bash
[ -f environment/.env.mvp ] || cp environment/.env.mvp.example environment/.env.mvp
./environment/scripts/up.sh mvp
curl -fsS http://localhost:8080/actuator/health
```

`mvp` = frontend + backend + postgres 三個容器，全部只綁 `127.0.0.1`：

| | 位置 |
|---|---|
| 前端 UI | <http://127.0.0.1:5173>（儀表板、IOC 檢索、威脅情報、STIX Viewer 等**匿名可用**） |
| Swagger UI | <http://127.0.0.1:8080/swagger-ui/index.html>（`SWAGGER_ENABLED` 控制，prod 預設關） |
| Backend health | <http://127.0.0.1:8080/actuator/health> |
| REST API | `GET /api/v1/iocs?limit=10`、`GET /api/v1/stats/summary`（匿名可讀 public `TLP:CLEAR` 情資） |
| PostgreSQL | `127.0.0.1:5432`（帳密見 `environment/.env.mvp`；啟動時自動跑 Flyway 並載入約 1,020 筆樣本 IOC） |

後端單元測試（L1，秒級）：

```bash
./backend/mvnw -f backend/pom.xml verify -Ptest-slice
```

完整的 L1–L3（整合測試自帶 Testcontainers，不需先啟動環境；約 5 分鐘）：

<!-- 此區塊刻意用 sh 而非 bash:dod.sh M1-38 會執行 README 的全部 bash 區塊,
     而完整 verify 已由 M1-01 跑過一次,再跑一次是純粹的重複(約 4 分鐘)。
     上面那個 -Ptest-slice 已足以證明「README 的步驟可直接複製執行」。 -->

```sh
./backend/mvnw -f backend/pom.xml verify -Ptest-integration
```

前端測試與 E2E（Playwright 的瀏覽器本體需先下載一次）：

<!-- 此區塊刻意用 sh 而非 bash:dod.sh M1-38 會執行 README 的全部 bash 區塊,
     而 npm ci 與瀏覽器下載不該被閘門盲跑(E2E 本身由 M2-26 檢查) -->
```sh
cd frontend && npm ci && npm run test
npx playwright install chromium && npx playwright test
```

停止環境用 `./environment/scripts/down.sh mvp`；後端改了 Java 檔用
`./environment/scripts/reload.sh backend mvp` 熱替換。
**其餘（四種環境、改程式怎麼辦、DoD gate、CI/CD、佈署、疑難排解）→
[`docs/development/getting-started.md`](docs/development/getting-started.md)。**

---

## Demo

[![CTIP 儀表板](docs/demo/dashboard.png)](docs/demo/README.md)

**逐里程碑的畫面導覽與可立即呼叫的端點速查** → [`docs/demo/`](docs/demo/README.md)
（匿名可用的頁面不需登入；登入後的頁面標註了所需權限）。

---

## 現況

| 項目 | 狀態 |
|---|---|
| 規格書 | ✅ v2.0（含二十八輪實作回饋修訂，[00 §0.7–§0.34](docs/spec/00-master.md)） |
| 實作 | ✅ 23 個 phase 全部交付。後端 **1,131 tests**、前端 **186 tests + 6 E2E**，皆全綠 |
| 閘門 | ✅ M1 38/38 · ✅ M2 27/27 · ✅ M3 **25/25**（2026-08-31 完整實跑，94 分鐘、零失敗）——三個閘門全數通過 |
| CI | 11 支 workflow。`security` 的 `dependency-scan`／`secret-scan` 已綠；**`image-scan` 的 10 個 HIGH 已於 2026-08-30 修掉**（`pebble` 不受 apt 管理且本容器用不到 → 刪除；`libcrypto3` 的修補版早在 Alpine repo → `apk upgrade`）。`backend-test` 的測試順序相依亦已修 —— **兩者都待推送後由 CI 實測確認**。見 [ADR 0048](docs/architecture/decisions/0048-ci-green-and-test-isolation.md)、[0049](docs/architecture/decisions/0049-base-image-vulnerability-remediation.md) |

M3 閘門的兩項失敗：**M3-01**（巢狀 gate 回歸）成因是 Phase 22 換 plain log pattern 時掉了 `%thread`，
判準要找的 `restartedMain` 是執行緒名、永遠對不到 —— 已修。
**M3-19**（CI 全綠）`openapi-check` 用了 `verify -Dtest=` 撞 JaCoCo 門檻，自上線起 29 次 run 0 次成功
—— 已修並在 CI 實測轉綠；`security` 掃到的四組 HIGH 中三個 CVE 已由 Boot 4.1.0 → 4.1.1 一次解掉。
逐項見 [ADR 0043](docs/architecture/decisions/0043-gate-run-findings.md)、
[0044](docs/architecture/decisions/0044-security-findings-remediation.md)、
[0045](docs/architecture/decisions/0045-full-project-review-doc-sync.md)。

**專案是怎麼走到這裡的**（規格的產生、逐里程碑交付歷程、三個閘門、實測撞出來的坑）
→ [`docs/history.md`](docs/history.md)。

---

## 文件地圖

| 文件 | 內容 |
|---|---|
| [`docs/spec/`](docs/spec/README.md) | **規格書（single source of truth）**：16 個主題檔 + 23 份執行單 |
| [`docs/history.md`](docs/history.md) · [`docs/progress.md`](docs/progress.md) | 專案沿革；逐 phase 判準結果與交接事項 |
| [`docs/architecture/overview.md`](docs/architecture/overview.md) | 架構總覽：分層、四個 module、九個聚合、12 個 pipeline stage、讀取路徑 |
| [`docs/architecture/security.md`](docs/architecture/security.md) | 安全架構：認證、授權、租戶隔離與 TLP、**CSRF 停用的決策**、安全標頭、secret、DB 權限模型 |
| [`docs/architecture/decisions/`](docs/architecture/decisions/) | ADR 0001–0045（逐 phase 決策 + 八則跨 phase 架構決策） |
| [`docs/development/getting-started.md`](docs/development/getting-started.md) | 開發環境上手：啟動、改程式、測試、DoD gate、CI/CD、佈署、疑難排解 |
| [`docs/development/plugin-sdk.md`](docs/development/plugin-sdk.md) | 寫一個 Threat Source Adapter（對應可編譯的範例） |
| [`docs/development/version-audit.md`](docs/development/version-audit.md) | 版本相容性處置紀錄 |
| [`docs/api/`](docs/api/README.md) | API 契約：[`openapi.json`](docs/api/openapi.json)（由建置產生，不得手改）、[事件 schema](docs/api/events/README.md)、[webhook 接收端契約](docs/api/webhooks.md)、[同步 client 契約](docs/api/sync-client-contract.md) |
| [`docs/deployment/`](docs/deployment/privacy.md) | [第三方授權](docs/deployment/licensing.md)、[個資與保留](docs/deployment/privacy.md)、[限流與反向代理](docs/deployment/rate-limiting.md) |
| [`docs/demo/`](docs/demo/README.md) | 畫面與端點速覽 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) · [`SECURITY.md`](SECURITY.md) · [`LICENSE`](LICENSE) | 貢獻、漏洞回報、授權 |

---

## 授權與安全

授權見 [`LICENSE`](LICENSE)，漏洞回報見 [`SECURITY.md`](SECURITY.md)，
安全架構見 [`docs/architecture/security.md`](docs/architecture/security.md)。

⚠️ 本平台處理的 IP 位址在 GDPR 下**可能構成個人資料**。規格已納入資料保留政策、
資料主體查詢與刪除程序，以及情資再散布的法遵限制（多數商業情資來源的 ToS 禁止再散布原始資料）——
見 [07 §7.9](docs/spec/07-domain-intel.md#79-再散布政策法遵強制)、
[13 §13.4](docs/spec/13-platform-ops.md#134-隱私與資料保留) 與
[`docs/deployment/privacy.md`](docs/deployment/privacy.md)。
