# CTIP — Cyber Threat Intelligence Platform

> **This repository contains a specification and an implementation in progress**
> (currently through Phase 23 of 23 — **all three milestones' phases delivered (M1 MVP, M2 Platform, M3 Production)**: environment, Docker,
> Spring Boot bootstrap, database schema, domain model with minimal security layer,
> ingestion SDK + mock adapters + resilience, ingestion pipeline with data quality and rate
> limiting, dedup/merge/fingerprint/scoring, STIX 2.1 projection & export, the anonymous read
> REST API with cursor pagination and unified error handling, OpenAPI/Swagger documentation
> with a committed, drift-checked openapi.json, full-field PostgreSQL search (GIN/pg_trgm),
> and a React 19 frontend — typed from the OpenAPI contract — with IOC search/detail pages and
> a public statistics dashboard; M2 adds authentication, RBAC, JWT with refresh-token
> rotation and reuse detection, API keys, enforced tenant isolation, plans/subscriptions with
> plan-driven quotas and the IOC write endpoints, the two-tier Bloom filter — public and
> per-tenant — with daily full snapshots, hourly deltas and a byte-exact, interoperable bit layout,
> Redis-backed distributed rate limiting, threat entities with the M2 STIX objects, and Elasticsearch
> search with typosquatting-oriented fuzzy matching, a circuit-breaking fallback to PostgreSQL and a
> nightly index reconciliation job. M3 adds Kafka with WebSocket/SSE/webhook notifications,
> an append-only audit trail with six data-retention jobs, and full observability: Prometheus metrics
> — including a timer per ingestion pipeline stage — structured JSON logging with credential masking,
> and OpenTelemetry tracing correlated with the traceId on every error response; and finally the complete
> CI/CD surface — eleven GitHub Actions workflows, Gitleaks/Trivy security scanning with SHA-pinned actions,
> CycloneDX and npm SBOMs, Dependabot — a compile-and-test-checked example adapter for the plugin SDK,
> a Cytoscape.js STIX relationship viewer, and the twelve required documents).
>
> CTIP is a multi-source cyber threat intelligence platform: it ingests indicators of compromise from
> heterogeneous feeds through a plugin adapter architecture, normalizes them into a single domain model,
> deduplicates and merges across sources, and exposes the result over a REST API with STIX 2.1 export and
> Bloom-filter-based incremental sync for lightweight clients.
>
> The specification in [`docs/spec/`](docs/spec/) was **produced with AI assistance and is written to be
> consumed by AI coding agents**. It is designed so that any capable agent can implement the system from it
> independently, and so that two agents reading it at different times produce compatible results.
> Architecture: Domain-Driven Design over Clean/Hexagonal Architecture.

---

## 這是什麼

這個 repository 包含**規格書與依它產生的實作**（23 個 phase 全部交付 —— **M1（MVP，Phase 1–12）**、
**M2（Platform，Phase 13–19）**、**M3（Production，Phase 20–23）**；M1 與 M2 的 DoD Gate 已通過，
**M3 的 `dod.sh full` 首次實跑為 23/25** —— 失敗的兩項與其後續見
[`docs/progress.md`](docs/progress.md) 與
[ADR 0043](docs/architecture/decisions/0043-gate-run-findings.md)）。
啟動方式見下方[快速開始](#快速開始目前可跑的部分)）。

規格書位於 [`docs/spec/`](docs/spec/)，它是**用 AI 輔助產生、給 AI 使用**的軟體規格：

```text
GPT 產生初稿（v1.0）
  → Claude 第一輪調整（v1.1，3,038 行單檔）
  → Claude 逐行審查、24 輪設計決策訪談、對 Maven Central / npm / OASIS 等外部來源查證（v2.0）
```

它與一般的「架構文件」不同的地方在於**它被寫成可執行的契約**：

- 每一條 Definition of Done 都對應一個回傳 0/1 的指令（90 項），無法自動化的 7 項被明確標為「需人工確認」
- 每一張圖標註**規範等級**（CI 會擋／人工驗證／僅供參考），因為 ArchUnit 能驗證依賴方向但不能驗證「這個聚合有這個方法」
- 27 張資料表全部有完整欄位定義，避免不同 agent 各自發明 schema
- 附一份中英對照的 Ubiquitous Language 詞彙表，因為規格是中文而程式碼是英文，沒有這張表命名會發散

規格書的導覽與檔案職責見 [`docs/spec/README.md`](docs/spec/README.md)。

---

## 系統摘要

CTIP 的核心能力與所屬里程碑：

| # | 能力 | 里程碑 |
|---|---|---|
| 1 | 從不同 Threat Intelligence Source 收集情資 | M1 |
| 2 | 透過 Adapter / Plugin 架構整合異質來源（第三方可自行擴充） | M1 |
| 3 | 正規化為統一 Domain Model（七種型別各有正規化規則） | M1 |
| 4 | 去重、多來源合併、指紋、威脅評分 | M1 |
| 5 | STIX 2.1 匯出（含 TLP 2.0 marking） | M1 |
| 6 | REST API（cursor 分頁、統一錯誤結構、OpenAPI） | M1 |
| 7 | 多租戶資料隔離 + 匿名唯讀公開情資 | M1（模型與過濾）／M2（完整認證） |
| 8 | TLP 2.0 資料分級與可見度控制 | M1 |
| 9 | 情資再散布政策（法遵：第三方 ToS 與 GDPR） | M1 |
| 10 | 認證、RBAC、API Key | M2 |
| 11 | Free / Premium / Enterprise 方案與配額 | M2 |
| 12 | 使用者提交與匯入 IOC、誤判回報 | M2 |
| 13 | 兩層 Bloom Filter + 增量同步（供 Browser Extension / App） | M2 |
| 14 | Elasticsearch 搜尋（含 ES 故障時降級至 PostgreSQL） | M2 |
| 15 | Kafka 事件、WebSocket / SSE / Webhook 通知 | M3 |
| 16 | Audit Log（append-only）、資料保留政策 | M3 |
| 17 | Prometheus / Grafana / OpenTelemetry 可觀測性 | M3 |
| 18 | CI/CD 與供應鏈安全（11 支 workflow、secret／相依／映像掃描、SBOM）＋ 12 份必要文件 | M3 |

三個里程碑各有獨立的驗收閘門：**M1（Phase 1–12，38 項）→ M2（Phase 13–19，27 項）→ M3（Phase 20–23，25 項）**。
未通過前一個閘門不得開始下一個里程碑。

### 明確不做的事

不自建所有第三方情資來源、不做 ML 威脅偵測、不做完整 SIEM / SOAR、不做 Kubernetes-first 部署、不做多區域 active-active、不串接真實金流、不做 TAXII 2.1 Server（僅保留擴充點）。不採用 CQRS 與 Event Sourcing。

---

## 模組功能摘要

### 後端 Maven Module（4 個）

| Module | 對外提供 | 允許依賴 | Phase | 治理規格 |
|---|---|---|---|---|
| `ctip-sdk` | **Shared Kernel**：`ThreatSourceAdapter` 契約、跨界列舉（`IocType`／`Tlp`／`Severity`／`RedistributionPolicy` 等）。可獨立發布至 Maven Central | JDK、`jakarta.validation-api`。**零 Spring** | 1, 5 | [01 §1.3](docs/spec/01-architecture.md#13-maven-multi-module)、[02 §2.5](docs/spec/02-ddd-model.md#25-shared-kernelctip-sdk) |
| `ctip-core` | `domain`（9 個聚合 + 不變量）+ `application`（service + out-port） | `ctip-sdk`、spring-context、spring-tx。**無 JPA、無 spring-data** | 4, 6–8 | [01](docs/spec/01-architecture.md)、[02](docs/spec/02-ddd-model.md) |
| `ctip-adapters` | 內建與 mock 的 Threat Source Adapter 實作 | `ctip-sdk`、HTTP client、Resilience4j。**不依賴 `ctip-core`** | 5, 14 | [08 §8.1、§8.3](docs/spec/08-ingestion-sdk.md) |
| `ctip-app` | Spring Boot 啟動類、`infrastructure`、`interfaces`、Flyway、設定檔。唯一產生可執行 jar 者 | 全部 | 3, 9–10, 13+ | [01 §1.4](docs/spec/01-architecture.md#14-package-結構)、[05](docs/spec/05-environment.md) |

### 後端 Domain 模組（`ctip-core/domain/*`）

| 模組 | 對外提供 | 允許依賴 | Phase | 治理規格 |
|---|---|---|---|---|
| `indicator` | `Indicator` 聚合根（14 條不變量）、`IndicatorSource`、`HashRecord`、`IndicatorMergePolicy`、正規化器 | sdk、`shared` | 4, 6, 7 | [07 §7.1–§7.5](docs/spec/07-domain-intel.md) |
| `source` | `Source` 聚合根、`SourceHealth` 狀態機、`Reputation` | sdk、`shared` | 4, 5 | [08 §8.6](docs/spec/08-ingestion-sdk.md#86-來源健康) |
| `tenant` | `Tenant` 聚合根、`TenantSlug`、public tenant 常數 | sdk、`shared` | 4 | [10 §10.1](docs/spec/10-identity-plans.md#101-多租戶) |
| `fingerprint` | `FingerprintStrategy`、`Sha256FingerprintStrategy`、`Fingerprint` | sdk | 7 | [07 §7.4](docs/spec/07-domain-intel.md#74-去重與指紋) |
| `stix` | STIX 物件模型與 builder、`StixTlpMarkings`、`StixPatternBuilder` | sdk、`indicator`、`threat` | 8, 18 | [07 §7.8](docs/spec/07-domain-intel.md#78-stix-21-映射) |
| `event` | `DomainEvent` 型別 + 19 個具體事件 | sdk、`shared` | 4+ | [02 §2.4](docs/spec/02-ddd-model.md#24-domain-event-清單) |
| `shared` | `Cursor`、`CursorPage`、共用型別 | sdk | 4 | [02 §2.6](docs/spec/02-ddd-model.md#26-值物件清單) |
| `identity` | `User` 聚合根（7 條）、`RefreshToken`、`ApiKey` 聚合根（7 條） | sdk、`tenant`、`shared` | 13 | [10 §10.3–§10.5](docs/spec/10-identity-plans.md) |
| `plan` | `Subscription` 聚合根（5 條）、`BillingPeriod`、`Plan`（方案定義，參考資料）、`QuotaLimit`（0 = 停用／null = 無限制） | sdk、`tenant`、`shared` | 14 | [10 §10.6](docs/spec/10-identity-plans.md#106-方案) |
| `bloom` | `BloomVersion` 聚合根（8 條）、`BloomArtifact`、`BloomParameters`、`Checksum` | sdk、`tenant`、`fingerprint` | 15 | [11](docs/spec/11-sync-bloom.md) |
| `threat` | `Threat` 聚合根（6 條）、`ThreatIndicatorLink`、`ExternalReference` | sdk、`indicator`、`shared` | 18 | [02](docs/spec/02-ddd-model.md#threat)、[04](docs/spec/04-data-dictionary.md) |
| `notification` | `Webhook` 聚合根（6 條）、`WebhookFilter` | sdk、`tenant`、`event` | 20 | [13 §13.2](docs/spec/13-platform-ops.md#132-通知-phase-20--m3) |

`application` 之下的模組（`ingestion`、`search`、`sync`、`audit` 等）為對應的 service 層，out-port 集中於 `application/port`。

### 前端 Feature（`frontend/src/features/*`）

**feature 之間不得直接 import**（ESLint `import/no-restricted-paths` 強制）。共用內容上移至 `components/` 或 `hooks/`。

| Feature | 對外提供 | 允許依賴 | Phase | 治理規格 |
|---|---|---|---|---|
| `ioc` | IOC 搜尋／詳情／提交／匯入的元件與 hook | `api/`、`components/`、`hooks/`、`utils/` | 12, 14 | [12 §12.5](docs/spec/12-frontend.md#125-頁面) |
| `stix` | STIX JSON 檢視、關聯圖（Cytoscape.js） | 同上 | 12, 20, 23 | [12 §12.6](docs/spec/12-frontend.md#126-ui-要求) |
| `auth` | 登入／註冊／token 刷新／路由守衛 | 同上 + `stores/authSlice` | 13 | [12](docs/spec/12-frontend.md) |
| `sync` | Bloom 說明頁與同步狀態 | 同上 | 16 | [11 §11.7](docs/spec/11-sync-bloom.md#117-client-契約摘要必須複製進-sdk-與-api-文件) |
| `subscription` | 方案與用量檢視 | 同上 | 14 | [10 §10.6](docs/spec/10-identity-plans.md#106-方案) |
| `apikey` | API key 建立／撤銷（原文只顯示一次） | 同上 | 13 | [10 §10.5](docs/spec/10-identity-plans.md#105-api-key-phase-13--m2) |
| `threat` | Threat feed 與詳情 | 同上 | 18 | [12 §12.5](docs/spec/12-frontend.md#125-頁面) |
| `notification` | 通知中心、WebSocket 連線狀態 | 同上 + `stores/toastSlice` | 20 | [13 §13.2](docs/spec/13-platform-ops.md#132-通知-phase-20--m3) |
| `audit` | 稽核紀錄檢視 | 同上 | 21 | [13 §13.5](docs/spec/13-platform-ops.md#135-稽核-phase-21--m3) |
| `admin` | 管理面板:租戶與方案指派、STIX 重建、資料主體查詢／刪除 | 同上 | 21 | [13 §13.4](docs/spec/13-platform-ops.md#134-隱私與資料保留) |

### 基礎設施

| 服務 | 用途 | 啟動於哪個 profile |
|---|---|---|
| PostgreSQL 18 | **唯一的 source of truth** | 全部 |
| Redis 8 / Valkey 9 | 快取 + 分散式限流 | `standard`、`full` |
| Elasticsearch 9.5 / OpenSearch 3 | 讀取索引（可隨時由 DB 重建） | `full` |
| Kafka 4.2（KRaft） | Domain event 傳輸 | `full` |
| Nginx 1.30（stable） | 前端靜態服務 + 安全標頭 | 全部（production build） |
| Prometheus / Grafana | 監控 | `full` |

`SearchPort` 與 `CachePort` 的抽象讓 Elasticsearch → OpenSearch、Redis → Valkey 的替換只需改 infrastructure 實作與 image 名稱（授權與支援窗口考量見 [06 §6.5](docs/spec/06-tech-stack.md#65-授權注意事項)）。

---

## 如何使用這份規格

**如果你是 AI agent**：從 [`docs/spec/README.md`](docs/spec/README.md) 開始，它會告訴你讀取順序。不要一次讀完全部檔案。

**如果你是人類**：先讀上面的系統摘要與模組表，再讀 [`docs/spec/00-master.md`](docs/spec/00-master.md) 的 §0.6 變更摘要（那裡列出 v1.1 的 4 項建置阻斷缺陷、3 項版本錯誤、10 項內部衝突及其解法），以及 **§0.7–§0.30 實作回饋修訂**（Phase 2–23 實測與 M1/Phase 1–20 兩次總複查發現的規格衝突與修正索引；照字面實作會踩的坑集中在 [05 §5.8.1](docs/spec/05-environment.md#581-實作回饋修正2026-08-21phase-23-實測發現詳見-adr-0001) 與 [06 §6.3.6](docs/spec/06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷)）。

---

## 現況

| 項目 | 狀態 |
|---|---|
| 規格書 | ✅ v2.0 完成（含 2026-08-21 / 2026-08-25 / 2026-08-26 / 2026-08-27 / 2026-08-28 / 2026-08-29 / 2026-08-30 **二十四輪**實作回饋修訂，見 [00 §0.7–§0.30](docs/spec/00-master.md)） |
| `environment/` | ✅ Phase 2 完成：唯一 compose 檔、雙 Dockerfile、四環境樣板、8 支腳本（含 90 項 DoD gate 的 `dod.sh`）、CI compose 驗證。`migrate.sh` 於 2026-08-28 修好——它從 Phase 2 起就呼叫 `flyway:migrate`，但專案從未加過 flyway-maven-plugin（[ADR 0014](docs/architecture/decisions/0014-flyway-monotonic-versions.md)）。Phase 13 修正樣板 `JWT_SECRET`——原值 `CHANGE_ME_MIN_32_BYTES` 自己只有 22 bytes，HS256 上線後會讓照樣板複製的全新環境啟動失敗 |
| `backend/` | ✅ **M1（Phase 1–12）完成**：四模組骨架與 Spring Boot 4 啟動、Flyway V1–V7 + 1,020 筆種子資料、Indicator/Tenant/Source 聚合與最小安全層（tenant + TLP + 再散布統一過濾）、SDK + 三個確定性 mock adapter + Resilience4j 韌性、10-stage ingestion pipeline（正規化、八種拒絕規則、去重合併、評分、STIX 投影）、排程與記憶體限流、STIX 2.1 匯出（TLP 2.0 marking、pattern、bundle）、匿名讀取 REST API 全套（IOC 清單／明細／搜尋／批次驗證、統計、來源、cursor 分頁、16 錯誤碼統一錯誤結構 + traceId）、OpenAPI/Swagger（springdoc 3.1.0、逐端點完整性測試、`docs/api/openapi.json` 產出 + CI drift／破壞性變更檢查）、PostgresSearchAdapter 全欄位搜尋（tags GIN `@>`、來源、confidence/score/時間區間、pg_trgm 子字串）與 CORS 接線（262 tests，Testcontainers 整合驗證）<br>✅ **M2（Phase 13–19）完成**——**Phase 13**：Flyway V20/V21/V24（users、roles、permissions、role_permissions、tenant_users、refresh_tokens、api_keys + RBAC 種子）、User／ApiKey 聚合（U1–U7、K1–K7 逐條測試）、JWT HS256 + refresh token 輪替與重用偵測（family 全撤）、BCrypt cost 12 與登入鎖定、API key（原文僅回一次、前綴定位、scope 不可提權）、Spring Security filter chain + `@PreAuthorize` + 集中 PermissionEvaluator、跨租戶一律 404（參數化涵蓋每個端點）、安全測試 1–9 全綠。**Phase 13 收尾稽核**（逐端點對照 §10.3 矩陣 + 架構 / 資安複查，[ADR 0013](docs/architecture/decisions/0013-phase13-audit-fixes.md)）再補 12 項修正：`/sources`／`/stats` 五個端點原本完全沒有授權宣告（filter chain 是 `permitAll`，等於全開）→ 新增 `source:read`／`stats:read`（權限 19 → 21、矩陣 95 → 105 格）並以 `EndpointAuthorizationTest` 逐 handler 守門；停權與移除成員資格對 refresh／API key 原本完全無效 → `AccountAccessPolicy` fail-closed;refresh token family 90 天絕對上限;登入鎖定訊息不再洩漏帳號存在;密碼上限對齊 BCrypt 72 bytes;API key 數量上限;`last_used_at` 改定向 UPDATE（避免沖掉撤銷）。另**先行清掉後續 phase 的已知缺口**（[ADR 0015](docs/architecture/decisions/0015-future-phase-hardening.md)）：`/stats/sources` 筆數補可見度過濾與 `sourceId` 查詢參數的來源歸屬 oracle（兩者都是 Phase 14 手動提交上線後才會真正洩漏）、限流 bucket 逐出、STIX name 截斷不切 surrogate pair、filter 逸出例外也回統一錯誤結構（**537 tests**）<br>**Phase 14**：Flyway V28/V29（`plans`／`subscriptions`／`import_jobs` + `ingestion_rejections.import_job_id`、四個方案種子、新權限 `subscription:read`）、`Subscription` 聚合（B1–B5）與 `QuotaService` 單一判定點——§10.6 的 **14 個配額維度全部讀 `plans` 表**，property 版本（`STIX_EXPORT_MAX_OBJECTS`、`API_MAX_*`、`RATE_LIMIT_ANONYMOUS_PER_*`）連同五個環境變數一併移除，避免第二真相來源；§9.7 的三種超限語意各有出口（429 時間窗／403 能力上限／413 單次尺寸／分頁夾值不報錯）；**IOC 寫入端點**：`POST /iocs`（走完整 pipeline，預設 TLP:AMBER、歸屬不可指定、`ioc:publish` = 擁有權轉移且來源記錄轉為可再散布，否則發布沒有任何公開效果）、`POST /iocs/import`（CSV／STIX bundle，202 + jobId 非同步、逐筆結果摘要、越界筆數逐筆 `QUOTA_EXCEEDED`）、`GET /iocs/import/{jobId}`、`POST /iocs/{id}/report-false-positive`（最終狀態由 `IndicatorMergePolicy` 決定、公開情資回 403）、`GET /subscription`／`/subscription/usage`；`ManualSubmissionAdapter` 與 `StixPatternParser`（§7.8.3 六個模板的反向，本平台匯出的 bundle 可再匯入）；`indicator_sources.raw_payload` 改為真的寫入（承載提交備註與誤判理由）。詳見 [ADR 0023](docs/architecture/decisions/0023-phase14-plans-and-write-endpoints.md)（**644 tests**）<br>**Phase 15**：Flyway V30（`bloom_versions`／`bloom_artifacts`）、**兩層 Bloom filter** —— public（`TLP:CLEAR`、可再散布）與 per-tenant（`AMBER`／`AMBER_STRICT`，**刻意不含再散布條件**，否則私有提交固定 `INTERNAL_ONLY` 會使 tenant bloom 恆為空）；位元格式逐條依 §11.4 自行實作（LSB-first、Kirsch-Mitzenmacher 雙雜湊、`h1 + i*h2` 以 unsigned 64-bit wraparound 計算、`m` 向上取整至 8 的倍數、k 由公式導出 = 10），`BloomBitLayoutTest` 以固定 fingerprint 斷言**確切的 byte 陣列**而非「有沒有命中」——§11.4 存在的理由就是 client 要能產生位元組完全相同的陣列；full snapshot（每日 04:00，`datasetVersion` +1）與 delta（每小時，varint 差分編碼 + `resultingChecksum` 供 client 自我驗證）、鏈過長或參數不相容改生 full、artifact 保留不刪掉仍被 delta 依賴的 full snapshot、`BloomUpdateStage` 插在 `PersistStage` 之後只作為「哪個 scope 變了」的訊號（成員真相在資料庫——記憶體緩衝遺失會產生 Bloom **false negative**）；`BloomArrayLoader` 在生成 delta 前先跑一次 **client 的 §11.6 驗證路徑**（驗 full 的 checksum、每段 delta payload 的 checksum、每套用一段比對 `resultingChecksum`）——不驗的話,損壞的 artifact 會讓下一段 delta 的 `resultingChecksum` 依損壞後的陣列算出,每個 client 套用後都失敗、重下 full,而伺服器端毫無徵兆。另定調三處規格互相矛盾之處(皆採安全優先):`plans.tenant_bloom_capacity` 的 `NULL` 在 §11.2 是「**無** tenant Bloom」、在 `QuotaLimit` 通用語意卻是「無限制」→ fail-closed,只有正整數才生成;tenant bloom 尺寸取 `min(方案上限, max(BLOOM_TENANT_DEFAULT_CAPACITY, 成員數))`——方案值是權利上限、環境變數是實際尺寸預設,只用方案值會讓該變數變成沒有呼叫端的死設定,且 ENTERPRISE 的小租戶每小時都會產生一份 18MB 陣列;04 表 23 與 §11.5 對 delta `checksum` 的說法相反 → 定調為「未壓縮 **artifact payload**」的 SHA-256(因此 varint 差分編碼屬 Phase 15,base64url 屬 Phase 16)。詳見 [ADR 0024](docs/architecture/decisions/0024-phase15-bloom-decisions.md)（**705 tests**）<br>**Phase 16**：**增量同步 API** —— `GET /sync/manifest`（兩層 metadata，`coverage` 與 `notCovered` 為必填：client 開發者必須在 manifest 就看到「public 只覆蓋 `TLP:CLEAR`、`TLP:GREEN` 完全無覆蓋」）、`GET /sync/bloom?scope=`（直接串流儲存體中的原始位元組——不採「302 至簽章 URL」，§5.4 沒有簽章金鑰，為它新增設定項屬預先建置）、`GET /sync/delta?base=&scope=`（區間內各段 delta 的併集 → 升序去重 → 差分 → LEB128 varint → base64url 無 padding，含 `409 SNAPSHOT_REQUIRED`）；同步頻率限制以新的 `SyncThrottlePort` 承載（`RateLimitKey.Window` 只有 MINUTE/DAY，表達不了 86400/21600/300/60，且平台原本沒有任何欄位記錄「上次同步時間」），記帳對象是**呼叫者身分**而非 tenant——匿名一律綁 public tenant，以 tenant 記帳等於全體匿名 client 共用一個額度。另定調三處規格陷阱：manifest 的 `checksum` 若照字面取「最新版本 artifact 的 checksum」，最新版本是 delta 時算的是 varint payload 的雜湊，**client 拿它驗自己的陣列永遠不會相符** → 定調為 `BloomVersion.arrayChecksum()`（完全同步後陣列應有的 SHA-256，manifest 與 `/sync/delta` 共用同一方法）；§11.6 第 4 步「更新版本」沒說更新成哪個數字，照 manifest 記會產生 Bloom 的 **false negative**（下載到的是 full snapshot、`bloomVersion = 0`，而 manifest 的版號是 delta 可到達的最新版）→ 下載回應必帶 `X-Bloom-*` 七個標頭，空區間也一定給得出 `resultingChecksum` 作為第二道防線；`409` **不消耗**同步間隔，否則 client 依 §11.6 轉去下載 full 時會立刻撞 `429`，整條復原路徑永遠走不完。並修掉 **M2-15 的假綠**（判準原本跑生成端的 `BloomDeltaTest`，而 `409` 的 HTTP 行為在 Phase 15 根本不存在 → 改指向 `SyncEndToEndTest`，它真的產生 25 段 delta）。詳見 [ADR 0025](docs/architecture/decisions/0025-phase16-sync-api-decisions.md)（**727 tests**）<br>**Phase 17**：**Redis 快取 + 分散式限流** —— `CachePort`／`RedisCacheAdapter`（只用 `GET`／`SET EX`／`DEL`，換 Valkey 只需改 image 名稱）與 `RedisRateLimiter`（Bucket4j + `bucket4j-redis`），由 `RATE_LIMIT_BACKEND` 切換；**五個限流維度**（apiKey → user → tenant → ip → endpointClass，由 specific 到 general 依序檢查）分成**兩個檢查點**：維度 4 在認證**之前**（否則無效憑證完全繞過限流，ADR 0012 決策 16），維度 1–3／5 在認證**之後**。修掉四處照字面實作會出事的地方：①維度 5 的鍵在 §10.7 **沒有主體**，照字面是全平台共用一個桶——任一租戶打滿它，所有人都被 429；②維度 4 對已認證請求**先扣後退**（不歸還的話 ENTERPRISE 的 client 會被匿名方案的 60/min 綁死，方案分級形同虛設）；③bucket4j 把桶的設定一併存進 Redis、建立後不隨限額更新——方案**降級**時 fail-open，故 Redis 鍵多帶一段容量（限額改變即換桶）；④Boot 的 `forward-headers-strategy=framework` **無條件採信** `X-Forwarded-*`，改為只信任 `TRUSTED_PROXIES` 內的對端（預設空 = fail-closed）。Redis 不可用時限流 **fail-fast**（不得降級記憶體——「後端掛了就等於沒有限流」正是攻擊者要的狀態），快取則 fail-soft；`CachePort` 的消費者是兩個**既有的**行程內快取（方案配額、RBAC 對應），行程內的 map 無法跨實例失效正是要修的缺陷。`DistributedRateLimitTest` 真的起**兩個 Spring context**（各自的 web server 與連線池，共用同一個 Postgres 與 Redis）驗證「單實例耗盡後另一實例也被拒」——把後端切回 memory 時三個案例全紅。部署注意事項見 [`docs/deployment/rate-limiting.md`](docs/deployment/rate-limiting.md)，決策見 [ADR 0026](docs/architecture/decisions/0026-phase17-redis-cache-and-distributed-rate-limit.md)（**757 tests**）<br>**Phase 18**:**Threat 實體與關聯 + M2 的 STIX 物件** —— Flyway `V31`(`threats`／`threat_indicators`／`threat_external_references`、V7 保留的 `fk_so_threat` + 缺漏的 `ix_so_threat`、`threat:manage` 種子)、`Threat` 聚合(H1–H5 在聚合與 DB 約束強制;**H6 依 [ADR 0020](docs/architecture/decisions/0020-phase17-19-spec-resolutions.md) 由 application 層強制**——建立關聯時以關聯 IOC 的 TLP 收緊,並由新的 `IndicatorTlpTightened` 事件維持事後一致性)、五種 M2 STIX 投影(`malware`／`attack-pattern` ← Threat、`observed-data` ← IndicatorSource、`identity` ← Source、`relationship` ← ThreatIndicatorLink)並補齊 [§7.8.7](docs/spec/07-domain-intel.md) 的欄位對照表、`GET /stix/{stixId}` 擴充為服務全部 M2 物件(可見度依**來源 domain 物件**判定)。**本 phase 補上 Threat 的建立管道**:§9.1 原本只有三個 `GET`,而 ingestion 不產生 Threat、Phase 19–23 也沒有——照原樣實作,三張表與聚合的四個行為在正式環境永遠不可達(規則 16 的 placeholder)。新增五個寫入端點與 `threat:manage` 權限(第 23 個),歸屬與 TLP 完全沿用 §9.7 手動提交的規則(預設 AMBER;CLEAR/GREEN 需 `ioc:publish` 且轉為 public tenant);`POST /{id}/retire` 改為 `PUT /{id}/status`,否則 `ThreatStatus.DORMANT` 同樣永不可達。另抓到兩個實測缺陷:**AFTER_COMMIT 的事件消費端用預設傳播行為寫資料庫,寫入不落庫也不報錯**(回呼仍在已提交交易的 synchronization 範圍內)→ 一律 `REQUIRES_NEW`,規則寫進 [02 §2.4](docs/spec/02-ddd-model.md);`/threats` 三端點的可見度述詞未定義 → 定調為 §7.7 的通則,且 `GET /{id}/indicators` 必須對每個關聯 IOC **再走一次** Indicator 的可見度(關聯不是可見度的旁路)。詳見 [ADR 0027](docs/architecture/decisions/0027-phase18-threat-and-m2-stix.md)（**815 tests**）<br>**Phase 19**(M2 收官):**Elasticsearch 搜尋 + 降級 + 對帳** —— `ElasticsearchSearchAdapter`(索引 `ctip-indicators`、`dynamic: strict` mapping、`wildcard` 子字串與 §13.7 的模糊查詢)、`FallbackSearchAdapter`(Resilience4j circuit breaker,ES 不可用時回 **200** + `X-Search-Backend: postgres`,判斷完全不在 controller)、`SearchIndexStage`(pipeline 第 11 格,只標記;寫出在批次交易提交後,索引失敗不得使 ingestion 失敗)、每日 05:00 的對帳排程(`ES_RECONCILE_CRON`,比對筆數與版本並修正缺漏/落後/孤兒)。**兩處實測缺陷**:①[§13.7](docs/spec/13-platform-ops.md) 的搜尋欄位清單**不含** `ownerTenantId`、`deletedAt` 與來源的再散布政策,而那三者是可見度與側信道防護的全部依據——照字面實作,ES 路徑會整套繞過過濾。索引因此另帶 `ownerTenantId`／`redistributable`／`disclosableSourceIds`(軟刪除完全不進索引),**且回傳的 Indicator 一律以 `findVisibleByIds` 從 PostgreSQL 取回**,等於在 source of truth 再過濾一次;兩層防護各以測試反向驗證(拿掉 ES 端述詞 → 分頁測試轉紅;拿掉 PostgreSQL 補齊 → 索引投毒測試轉紅)。②**`spring-boot-elasticsearch` 一在 classpath 上就會加 actuator 的 ES 健康檢查**,而 ES 只屬 `full` profile——mvp 與 dev 不關掉的話容器永遠 unhealthy、`dod.sh mvp` 整批紅(同 Phase 17 的 Redis,但這次 dev 也要關)。另修掉 **M2-22 的假綠**:它是 DoD 全表唯一用 `verify` 的過濾式判準,因此繞過 `dod.sh` 的測試類存在性守衛,測試不存在時 build 成功、該項 `[PASS]`。`fuzzy` 旗標與 `X-Search-Backend` 一併寫入 [09 §9.1](docs/spec/09-api.md) 與 CORS `exposedHeaders`;授權與替代方案(ES → OpenSearch、Redis → Valkey)記於 [`docs/deployment/licensing.md`](docs/deployment/licensing.md)。詳見 [ADR 0028](docs/architecture/decisions/0028-phase19-elasticsearch-search.md)（**831 tests**)<br>✅ **M3(Phase 20–23)完成**——**Phase 20**:**Kafka + 通知(WebSocket／SSE／Webhook)** —— Kafka(KRaft,`apache/kafka:4.2.1`)與六個 topic、`KafkaEventForwarder`(**不修改任何發佈端**,只是 `DomainEventEnvelope` 的又一個消費端)、事件的版本化 JSON Schema 與 domain event → topic 對照表([`docs/api/events/`](docs/api/events/README.md))、Flyway `V32`(`webhooks`／`webhook_deliveries`／`notifications` + `notification:read` 權限)、`Webhook` 聚合(W1–W6)、HMAC-SHA256 送達簽章與五個 `X-CTIP-*` 標頭、指數退避重試與連續五次後停用、原生 WebSocket `GET /api/v1/ws`(token 走 `Sec-WebSocket-Protocol`,不進 access log)與 SSE fallback `GET /api/v1/events`。**四處照字面實作會出事的地方**:①`WebhookFilter` 要的 severity／tags／sourceIds **不在 domain event 上**(它們是多來源合併之後才定的),而 §13.1 禁止修改發佈端 → 新增 `NotificationEvent` 投影,由 application 層在送出前從聚合補齊,過濾仍完全在伺服器端(W5);②§13.1 規則 7 只說「不得使業務操作失敗」,但 `KafkaTemplate.send()` 取不到 metadata 時**同步阻塞 60 秒** —— 回 200 卻等一分鐘與失敗沒有差別 → 轉發移出業務執行緒(單執行緒 + 有界佇列);③`KafkaAdmin` **看不見 `List<NewTopic>` 型別的 bean**,topic 只能靠 broker auto-create 產生(分割數變成預設值),關閉 auto-create 的正式環境則直接沒有 topic —— 被「斷言分割數而不只是 topic 存在」的測試抓到;④SSE fallback 原本**沒有方案閘門**,任何 client 改連 `/events` 就繞過 `websocket_enabled`。另修正 02 §2.4 的一句既成事實:實測顯示 AFTER_COMMIT 消費端改用預設 `REQUIRED` **仍會落庫**(連線歸還時一併提交),規則照留但敘述不再宣稱那個症狀;**反過來**,聚合發出的 `WebhookDisabled` 必須在 `REQUIRES_NEW` 交易**內**發佈,否則會掛到已走完 afterCommit 的交易上而永不觸發(以反向驗證確認)。詳見 [ADR 0029](docs/architecture/decisions/0029-phase20-kafka-and-notifications.md)（**931 tests**)<br>**Phase 21**:**稽核軌跡 + 資料保留** —— Flyway `V33`(`audit_logs` + `REVOKE UPDATE, DELETE` 使**應用角色連 DB 層都刪不掉稽核**、清理角色的欄位層級授權)、非同步有界佇列的 `AuditWriter`(稽核寫入失敗不得使業務操作失敗——結構上不可能:業務服務根本不知道稽核存在)、**26 種稽核行為**由兩個橫切消費端承接(`AuditAccessFilter` 17 種以請求為觸發點、`AuditEventListener` 9 種以 domain event 為觸發點)、讀取取樣(`AUDIT_SAMPLE_READ_RATE`,寫入 100%／讀取 1%)、`GET /api/v1/audit-logs`、**六項保留清理**(分批 ≤10,000 列、記錄筆數、單項失敗不影響其他)、`POST /auth/change-password`(撤銷該使用者**全部** token family,ADR 0015 指定的 M3 責任)、`/api/v1/admin/**` 七支管理端點(租戶總覽、方案指派、來源手動同步與啟用切換、STIX 重建、資料主體查詢與刪除)。**四處照字面實作會出事的地方**:①§13.5 規則 2 說清理角色「無 SELECT 業務表之權限」,而 **PostgreSQL 對 `DELETE/UPDATE … WHERE` 仍要求 WHERE 欄位的 SELECT 權限** —— 照字面授權,六項清理全部 `permission denied` → 改以欄位層級授權(只給主鍵與時間欄位,讀不到稽核內容);②`SUBSCRIPTION_CHANGED` 是強制的 26 種行為之一,但 `09` **沒有任何端點**呼叫 `Subscription.changePlan`／`cancel` —— 該行為與那兩個聚合方法都永不可達(規則 16)→ 補管理端點;③§13.4 要求資料主體刪除,而 §13.5 規則 1 說稽核是 append-only —— 兩者直接衝突 → 刪除涵蓋使用者可識別欄位與 refresh token,**稽核軌跡以 180 天保留期收斂**,法律基礎寫入 [`docs/deployment/privacy.md`](docs/deployment/privacy.md);④表 27 沒有 `action` 的 CHECK,拼錯的行為會靜靜寫進一張永不更新的表 → V33 補 26 值的 `ck_al_action`。`AuditCompletenessTest` 真的把 26 條路徑各走一遍再問資料庫留下了哪些 `action`——比對程式碼裡出現過哪些列舉值對「有程式碼但永遠不會被呼叫」完全無感。詳見 [ADR 0031](docs/architecture/decisions/0031-phase21-audit-and-retention.md)（**1,055 tests**)<br>**Phase 22**:**監控／日誌／追蹤** —— Actuator + Micrometer + Prometheus registry(§13.6 的必要指標全數就位,含**每個 ingestion stage 一支計時器**;六個 `ctip.*` 指標在啟動時就註冊,因為 Prometheus 的「序列不存在」與「值為 0」在告警規則上是兩件事)、Grafana dashboard 補上九張圖、結構化 JSON 日誌(`logstash-logback-encoder`,九個必含欄位由編碼器保證、沒有值也輸出空字串——缺欄位與空值在下游查詢是兩件事)與**兩道憑證防線**(不把憑證交給 logger + 輸出端遮罩;刻意不遮罩十六進位摘要,指紋與 traceId 是查問題的主線索)、OpenTelemetry 追蹤(API → application service → DB／Redis／Kafka／ES,以單一切面建立 span)、`/actuator/prometheus` 的來源 IP 白名單與 prod 的 actuator 暴露啟動守衛。**四處照字面實作會失敗的地方**:①沒有 collector 時照直覺關掉 `management.tracing.export.enabled`,**會連「接收傳入的 `traceparent`」一起關掉**(Boot 的 `TextMapPropagator` bean 也掛在同一個條件上)——傳入的 trace 被忽略、server span 另開一個 trace,§13.6 要的唯一關聯線索等於不存在;改為只關 `…export.otlp.enabled`;②追蹤切面若以整個套件當切入點,套件內的 `final` 類別(`IndicatorSearchIndex`、`KafkaTopics`)會使 CGLIB 建不出代理,**整個 context 起不來**;③Prometheus 的 **exemplar** 會在「記錄指標的那條執行緒」上向 bean factory 要 `Tracer`,而 Lettuce 的命令延遲是在 netty event loop 上記錄的——啟動時主執行緒握著 singleton 建立鎖等 Redis 連線,那條連線又只能由同一個 event loop 完成:`RATE_LIMIT_BACKEND=redis` 的環境(dev／staging／prod)**卡在啟動且沒有任何錯誤訊息**(是 thread dump 才看出來的);④`logback-spring.xml` 若讀 `ctip.environment`,其值是必填佔位符,而日誌系統在 environment-prepared 階段就初始化——佔位符解不開同樣讓 context 起不來。另修正規格自身的衝突:判準用 `up.sh staging` 驗 `/actuator/prometheus`,而 05 §5.5 把 staging 列為 `health,info`,照字面設定必然 404。詳見 [ADR 0032](docs/architecture/decisions/0032-phase22-observability.md)<br>**Phase 23**:`ctip-sdk` 的可編譯範例 adapter(`ExampleThreatSourceAdapter` + 11 個測試,放**測試原始碼**並沿用既有 `SourceType` ——為一份範例在列舉新增成員會留下永不可達的值)、CycloneDX SBOM 接線(`makeAggregateBom` 綁 `package`)<br>**Phase 23 補件**:補上兩項標 M2 卻不在任何 phase 交付物清單裡、因此連續三次只被回報的遺漏([ADR 0042](docs/architecture/decisions/0042-m2-gaps-token-cleanup-and-settings.md)):**`TOKEN_CLEANUP_CRON`** 的過期 token 清理(表 15 的 `EXPIRED_CLEANUP` 與名為 `ix_rt_gc` 的索引自 Phase 13 起就沒有寫入者;**標記不刪除**——刪列等於偷偷新增第七項保留政策,而 `ip`／`user_agent` 的移除屬資料主體刪除)（**1,128 tests**) |
| `frontend/` | ✅ **M1（Phase 11–12）完成**：React 19 + Vite 8 + Tailwind CSS v4（CSS-first）、OpenAPI 型別產生鏈（`api:generate`/`api:check`，手寫 typed fetch client）、Redux Toolkit 四 slice + TanStack Query（狀態歸屬依規格：server 資料進 Query、搜尋條件進 URL）、shadcn 風格元件 + 四態 StateViews + TlpBadge + TanStack Virtual 虛擬化表格、IOC 檢索／詳情（含來源歸屬與 STIX JSON）與公開統計儀表板（Recharts）、深色模式與響應式、MSW 型別驅動測試（70 tests，coverage ≥ 70% 門檻實測 90%+）<br>✅ **M2（Phase 13–19）完成**——**Phase 13**：登入／註冊頁、API Key 管理頁（原文一次性顯示）、路由層 `RequireAuth`／`RequirePermission` 掛載、401 自動輪替 refresh token（並行請求共用單次輪替）、header 登入／登出與身分顯示;API key 可授予的 scope 補上 `source:read`／`stats:read`（97 tests）<br>**Phase 14**：IOC 提交／匯入頁（`/iocs/new`、`/iocs/import`，含匯入進度輪詢）與方案用量頁（`/settings/subscription`：`null` = 無限制、`0` = 停用兩者不得都印成 0）<br>**Phase 16**：Bloom 同步說明頁 `/sync`（匿名可存取）——明文說明**命中不代表確定惡意、未命中不代表安全**（`TLP:GREEN` 無覆蓋）與「撤銷／過期只有 full snapshot 會反映」，並列出兩層的 manifest（含「完全同步後應有的 checksum」）與 §11.6 的同步步驟；**Playwright E2E 骨架**（[ADR 0022](docs/architecture/decisions/0022-orphan-deliverables.md) 歸位的無主交付物）：`playwright.config.ts`（webServer 跑 `build && preview`，測的是使用者實際拿到的 bundle；`E2E_BASE_URL` 可改對整套環境跑）+ `e2e/`，以 `page.route` 攔截 API 邊界，**M2-26 的四個情境（匿名搜尋、登入、建立 API key、提交 IOC）全數覆蓋**（121 tests + 3 E2E）<br>**Phase 18**:威脅情報頁 `/threats` 與詳情頁 `/threats/:id`(皆匿名可存取)——清單走 cursor 分頁與 URL 篩選,詳情呈現摘要／別名／外部參照／關聯 IOC／STIX 投影(只有 `MALWARE_FAMILY` 與 `ATTACK_PATTERN` 有 SDO,其餘型別不顯示該區塊,而不是顯示一個永遠 404 的面板)。關聯清單比 `indicatorCount` 短時**明說差額**——那是 TLP 或再散布政策擋掉的,靜默留白會讓使用者以為情資不見了（131 tests + 3 E2E）<br>✅ **M3(Phase 20–23)完成**——**Phase 20**:通知中心 `/notifications` 與 webhook 管理頁 `/settings/webhooks`([ADR 0022](docs/architecture/decisions/0022-orphan-deliverables.md) 歸位的無主交付物——`09` 有三個 `/webhooks` 端點與權限,而 `12` 的頁面表沒有對應頁)。即時推送以原生 WebSocket 連線,**指數退避 + 抖動自動重連**(沒有抖動的話伺服器重啟時所有 client 會在同一毫秒一起重連),連線狀態指示器**誠實**說出「連線中斷,重試中」而不是讓頁面看起來一切正常;推播只是「有新東西了」的訊號,清單仍以 Query 為真相來源(§12.3),因此漏掉的推播會在下一次 refetch 補上。簽章密鑰只在建立當下顯示一次(不變量 W2)<br>**Phase 21**:稽核軌跡頁 `/audit`(`audit:read`)與平台管理頁 `/admin`(`system:admin`)。稽核頁**只讀**——軌跡是 append-only 的,沒有刪除與編輯;管理頁收攏租戶方案指派、STIX 投影重建與資料主體查詢/刪除,而刪除的回應**明說仍保留幾列稽核紀錄**,否則操作者會以為「刪除」把一切都刪了<br>**Phase 23**:**STIX Viewer** `/stix/:id`(匿名可存取;Cytoscape.js 關聯圖、節點展開、型別篩選;SRO 畫成邊而不是節點,圖只能順著物件自身的參照往外長——平台沒有反查端點,這一點寫在 UI 與規格裡而不是用假資料掩蓋。唯一 code-split 的路由)<br>**Phase 23 補件**:**Settings 頁** `/settings`([ADR 0042](docs/architecture/decisions/0042-m2-gaps-token-cleanup-and-settings.md))——它的存在理由是一個端點:`POST /auth/change-password` 在 Phase 21 就交付了,卻**沒有任何前端入口**。變更密碼成功後**就地清掉本地 session**,因為後端撤銷的是含呼叫端自己在內的全部 token family;留著一個再也輪替不了的 session,使用者會在 15 分鐘後莫名被踢出（186 tests + 6 E2E) |
| `.github/` | ✅ **Phase 23 完成**:11 支 workflow(後端測試/lint、前端測試、build、compose 驗證、OpenAPI 比對、映像建置、安全掃描、nightly L4、staging/prod 佈署骨架)、`dependabot.yml`(四個 ecosystem,major 不自動開 PR)。其中六支標 M1/M2 的 workflow **逾期了十個 phase**——`dod.sh` 當時沒有任何一項檢查 workflow 檔案是否存在,M3-19 只看最後一次 run 的結論,「只有兩支且都綠」照樣通過([ADR 0022](docs/architecture/decisions/0022-orphan-deliverables.md));M3-19 已就地擴充為「11 支檔案存在 → `deploy-prod` 綁定 protected environment → CI 全綠」 |
| 必要文件 | ✅ **Phase 23 完成**(12 份,DoD M3-23):見下方「文件地圖」 |
| M3 閘門 | 🟠 **`dod.sh full` 首次實跑 23/25**(2026-08-30)。失敗兩項:**M3-01** —— 巢狀的 `dod.sh mvp` 37/38,掛在 M1-37(後端 reload);成因是 Phase 22 換 plain log pattern 時掉了 `%thread`,判準要找的 `restartedMain` 是執行緒名、永遠對不到 —— **已修並單獨驗過**。**M3-19** —— CI 不是全綠:`openapi-check` 自上線起 29 次 run 0 次成功(用了 `verify -Dtest=` 撞 JaCoCo 門檻,**已修**)、`security` 抓到四組真實 HIGH 弱點(**掃描器正常,四組全部要動版本,依 06 §6.1.2 不得由 AI 自行升版**),且 host 尚未安裝 `gh`。逐項見 [ADR 0043](docs/architecture/decisions/0043-gate-run-findings.md) |
| 進度與交接 | [`docs/progress.md`](docs/progress.md)（逐 phase 判準結果、偏離事項、給下一 session 的注意事項） |
| 架構決策 | [`docs/architecture/decisions/`](docs/architecture/decisions/)（ADR 0001–0042:各 phase 的規格衝突處置、實作決策、環境維護、M1 總複查、Phase 13 認證層決策與其收尾稽核修正、Flyway 版本號策略、後續 phase 缺口的先行清理、Phase 1–13 規格漏補、Phase 14–23 前置清障七批、**Phase 14 方案／配額與寫入端點**、**Phase 15 兩層 Bloom**、**Phase 16 增量同步 API 與 client 契約**、**Phase 17 Redis 快取與分散式限流**、**Phase 18 Threat 實體與 M2 的 STIX 物件**、**Phase 19 Elasticsearch 搜尋／降級／對帳**、**Phase 20 Kafka／通知／Webhook**、**Phase 21 稽核軌跡與資料保留**、**Phase 22 監控／日誌／追蹤**、**Phase 23 CI/CD／安全掃描／文件**、**兩項 M2 遺漏的補件**,以及 **0033–0040 八則跨 phase 的架構決策**——不採用 CQRS、單一 compose、兩層 Bloom、移除 Lombok、停用 CSRF、TLP 與方案解耦、`ctip-sdk` 作為 Shared Kernel、Repository port 分層) |

23 個 phase 全部交付；本檔隨每個里程碑**擴充**而不覆寫。
**既有段落（這是什麼／系統摘要／模組功能摘要）不得被覆寫。**

---

## 快速開始（目前可跑的部分）

需求：Docker ≥ 27（Compose ≥ 2.24）。首次啟動會自動預熱容器內的 Maven 與 npm 快取（需數分鐘）。

```bash
[ -f environment/.env.mvp ] || cp environment/.env.mvp.example environment/.env.mvp
./environment/scripts/up.sh mvp
curl -fsS http://localhost:8080/actuator/health
```

啟動後（mvp = frontend + backend + postgres 三個容器，全部只綁 `127.0.0.1`）：

| 服務 | 位置 |
|---|---|
| Backend health | <http://127.0.0.1:8080/actuator/health> |
| Frontend（Vite dev） | <http://127.0.0.1:5173> |
| PostgreSQL | `127.0.0.1:5432`（帳密見 `environment/.env.mvp`；啟動時自動跑 Flyway V1–V7 + V20/V21/V24 並載入約 1,020 筆樣本 IOC） |
| REST API（Phase 9 起） | `GET /api/v1/iocs?limit=10`、`GET /api/v1/stats/summary`、`GET /api/v1/sources`、`POST /api/v1/iocs/lookup`（匿名可讀 public TLP:CLEAR 情資；cursor 分頁、統一錯誤結構） |
| 認證與 API Key（Phase 13 起） | `POST /api/v1/auth/{register,login,refresh,logout}`（匿名可存取，登入回 access + refresh token）、`GET/POST /api/v1/api-keys`、`DELETE /api/v1/api-keys/{id}`（需 `apikey:create` / `apikey:revoke`；機器對機器改帶 `X-API-Key: ctip_<env>_<32 碼>`） |
| Swagger UI（Phase 10 起） | <http://127.0.0.1:8080/swagger-ui/index.html>（OpenAPI JSON：<http://127.0.0.1:8080/v3/api-docs>；`SWAGGER_ENABLED` 控制，prod 預設關） |
| 前端 UI（Phase 11–13 起） | <http://127.0.0.1:5173>（儀表板 `/`、IOC 檢索 `/iocs`、IOC 詳情 `/iocs/:id`：匿名可用；登入 `/login`、註冊 `/register`、API Key 管理 `/settings/api-keys`：需登入與 `apikey:create`；深色模式） |
| 設定頁（Phase 23 起） | <http://127.0.0.1:5173/settings>(需登入,不需額外權限):帳號資訊、主題、**變更密碼**(`POST /api/v1/auth/change-password` 的前端入口;送出成功後全部裝置登出,含目前這一個),以及依權限顯示的其他設定頁入口 |
| STIX Viewer（Phase 23 起） | <http://127.0.0.1:5173/stix/indicator--…>（匿名可用；Cytoscape.js 關聯圖、節點展開、型別篩選 + 原始 JSON。入口在 IOC 詳情與威脅詳情的 STIX 面板。⚠️ 圖只能順著物件自身的參照往外長——平台沒有「哪些 relationship 指向我」的反查端點） |
| 增量同步（Phase 15–16 起） | `GET /api/v1/sync/manifest`（需 `sync:bloom`，匿名亦持有）、`GET /api/v1/sync/bloom?scope=PUBLIC`、`GET /api/v1/sync/delta?base=0`（需 `sync:delta`，匿名不持有）。**Bloom 由排程產生**（full 每日 04:00、delta 每小時），剛啟動時尚無 snapshot，manifest 的 `public` 會缺席；client 契約見 [`docs/api/sync-client-contract.md`](docs/api/sync-client-contract.md) |
| 前端 Bloom 說明頁（Phase 16 起） | <http://127.0.0.1:5173/sync>（匿名可用；明文說明「命中不代表惡意、未命中不代表安全」） |
| 限流標頭（Phase 6／17 起） | 每個回應都帶 `X-RateLimit-Limit`／`-Remaining`／`-Reset`（無上限的方案印字面值 `unlimited`）；超限回 `429` + `Retry-After`。`RATE_LIMIT_BACKEND=redis` 時配額跨實例共用，反向代理後方需設 `TRUSTED_PROXIES`，見 [`docs/deployment/rate-limiting.md`](docs/deployment/rate-limiting.md) |
| 搜尋（Phase 12／19 起） | `POST /api/v1/iocs/search`（body 傳條件；回應必帶 `X-Search-Backend: elasticsearch\|postgres`）。mvp 的 `SEARCH_BACKEND=postgres`（`pg_trgm` 子字串），staging/prod 為 `elasticsearch`；ES 不可用時自動降級回 PostgreSQL 並回 **200**。`{"query":"...","fuzzy":true}` 的 typosquatting 模糊比對**僅 Elasticsearch 後端有效** |
| 即時通知（Phase 20 起） | 通知中心 <http://127.0.0.1:5173/notifications>；WebSocket `GET /api/v1/ws`（token 走 `Sec-WebSocket-Protocol: ctip.auth.<jwt>`，**不接受 query string**）與 SSE fallback `GET /api/v1/events`。兩者都需要方案的 `websocket_enabled` |
| Webhook（Phase 20 起） | `POST /api/v1/webhooks`（`webhook:manage`）；簽章密鑰只在建立當下回傳一次。接收端契約（五個標頭、`HMAC-SHA256(secret, timestamp + "." + body)`、5 分鐘時鐘偏差、重試與停用）見 [`docs/api/webhooks.md`](docs/api/webhooks.md) |
| 稽核與管理（Phase 21 起） | `GET /api/v1/audit-logs`(`audit:read`;只回自己租戶的軌跡,append-only)、`POST /api/v1/auth/change-password`(撤銷該使用者全部 token family,之後必須重新登入)、`/api/v1/admin/**`(租戶總覽與方案指派、來源手動同步、STIX 重建、資料主體查詢/刪除;`system:admin` 等)。前端 <http://127.0.0.1:5173/audit> 與 </admin>。個資處理、保留期與資料主體程序見 [`docs/deployment/privacy.md`](docs/deployment/privacy.md) |
| 資料保留（Phase 21 起） | 六項清理排程(稽核 180 天、raw payload／拒絕記錄／送達記錄 30 天、EXPIRED indicator 1 年後軟刪除、Bloom artifact 保留 30 份),由 `SCHEDULER_ENABLED` 總開關控制;稽核與四項清理走**專用 DB 角色** `ctip_retention`(應用角色對 `audit_logs` 連 DELETE 權限都沒有) |
| 監控與追蹤（Phase 22 起） | `GET /actuator/prometheus`（**只在 staging／prod 暴露**,且受 `PROMETHEUS_ALLOWED_IPS` 的來源 IP 白名單限制;mvp／dev 只有 `health`／`info`）。`up.sh staging` 會一併啟動 Prometheus 與 Grafana（`full` profile,dashboard 由 provisioning 自動載入）。日誌格式由 profile 決定:mvp／dev 為單行純文字、staging／prod 為 JSON（九個必含欄位,憑證一律遮罩）。追蹤:傳入的 W3C `traceparent` 會被延續,`traceId` 同時出現在錯誤回應與日誌;有 OTLP collector 時設 `TRACING_EXPORT_ENABLED=true` |
| STIX 2.1（Phase 8 起） | `GET /api/v1/stix/{stixId}`，例：<http://127.0.0.1:8080/api/v1/stix/marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487>（TLP:CLEAR marking；indicator 投影於來源同步後產生） |

後端測試（L1–L3，整合測試用 Testcontainers 自帶 PostgreSQL，不需先啟動環境）：

```bash
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
`./environment/scripts/reload.sh backend mvp` 熱替換；其餘腳本見
[`environment/README.md`](environment/README.md)。

### 疑難排解：backend 沒回應（Swagger / API 打不開，容器卻顯示 Up）

dev 容器與 host **共享 `backend/*/target/classes`**（bind mount）。此問題**已於 ADR 0010 根治**：
DevTools 的 restart 改由 trigger file 觸發（`reload.sh` 編譯成功後 touch
`backend/ctip-app/.devtools/.reloadtrigger`），host 上跑 `mvnw verify`／`clean` 不再引發
容器內熱重啟，也就不會再撞上「半寫入」的 classpath 使 application context 死亡。

若仍遇到 backend 無回應（`curl http://localhost:8080/actuator/health` 空回應、
`docker ps` 卻顯示 Up），restart 即恢復：

<!-- 此區塊刻意用 sh 而非 bash:dod.sh M1-38 會執行 README 的全部 bash 區塊,疑難排解指令不應被閘門盲跑 -->
```sh
docker compose --env-file environment/.env.mvp -f environment/docker-compose.yml restart backend
```

`dod.sh` 的 M1-14／M1-33 也內建同款自我修復（縱深防禦,ADR 0009／0010）。

---

## CI/CD 與安全掃描

`.github/workflows/` 共 **11 支**([13 §13.8](docs/spec/13-platform-ops.md#138-cicd-phase-23--m3基本流程自-m1-就要有)):

| Workflow | 觸發 | 內容 |
|---|---|---|
| `backend-test` | push / PR | `clean verify -Ptest-integration`(L1–L3 + JaCoCo 門檻 + ArchUnit) |
| `backend-lint` | push / PR | Spotless check + Checkstyle |
| `frontend-test` | push / PR | ESLint、Prettier check、`tsc --noEmit`、Vitest |
| `build` | push / PR | Maven package、Vite build、兩份 SBOM |
| `compose-validate` | push / PR | 四種環境的 `docker compose config` + 三項防呆檢查 |
| `openapi-check` | push / PR | 產生 openapi.json、比對 committed 版本、破壞性變更檢查 |
| `docker-build` | push / PR | 建置兩個映像;推 main 時推 GHCR,含 SBOM 與 provenance attestation |
| `security` | push / PR / 每日 | Gitleaks(整段歷史)、Trivy fs(相依弱點)、Trivy image(容器映像) |
| `heavy-test` | nightly / 手動 | `-Ptest-all`(含 L4) |
| `deploy-staging` | push main / 手動 | placeholder,綁 `staging` environment |
| `deploy-prod` | **只能手動** | placeholder,綁 `production` **protected** environment + 確認字串 |

安全掃描的 action **釘 commit SHA**([06 §6.1.2](docs/spec/06-tech-stack.md#612-凍結與浮動強制)):
它們讀得到 repo 內容與 token,浮動 tag 等於把供應鏈信任交給第三方。
相依更新由 [`.github/dependabot.yml`](.github/dependabot.yml) 負責(maven / npm / github-actions / docker;
major 不自動開 PR)——⚠️ **不得自行合併版本升級 PR**。

SBOM 是**建置產物**,不進版控:backend 由 CycloneDX maven plugin 綁在 `package`
(`backend/ctip-app/target/bom.json`),frontend 由 `npm run sbom`(`frontend/sbom.json`)。
不 commit 的理由是**沒有任何檢查能驗證它是最新的**,而過期的 SBOM 看起來像有效的供應鏈證據。

### 首次啟用 CI 時必做的兩件事(GitHub 設定,版控檔案表達不了)

1. 建立 `production` environment 並加上 **required reviewers**——`deploy-prod.yml` 綁了這個
   environment,但核准規則存在 repo 設定裡。沒有這一步,workflow 仍會跑,只是沒有人工關卡
   (已列入 [15 §15.5](docs/spec/15-dod-gates.md#155-需人工確認未被自動驗證) 的需人工確認項 **P-07**)
2. 啟用 **Dependabot alerts**(Settings → Code security)

---

## 文件地圖

| 文件 | 內容 |
|---|---|
| [`docs/architecture/overview.md`](docs/architecture/overview.md) | 架構總覽:分層、四個 module、九個聚合、12 個 pipeline stage、讀取路徑、基礎設施 |
| [`docs/architecture/security.md`](docs/architecture/security.md) | 安全架構:認證、授權、租戶隔離與 TLP、**CSRF 停用的決策**、安全標頭、secret、DB 權限模型、稽核 |
| [`docs/architecture/decisions/`](docs/architecture/decisions/) | ADR 0001–0042(逐 phase 決策 + 八則跨 phase 架構決策) |
| [`docs/development/getting-started.md`](docs/development/getting-started.md) | 開發環境上手:先備條件、啟動、改程式、測試、DoD gate、CI/CD、佈署 |
| [`docs/development/plugin-sdk.md`](docs/development/plugin-sdk.md) | 寫一個 Threat Source Adapter(八節,對應可編譯的範例) |
| [`docs/development/version-audit.md`](docs/development/version-audit.md) | 版本相容性處置紀錄 |
| [`docs/api/README.md`](docs/api/README.md) · [`openapi.json`](docs/api/openapi.json) | API 契約(由建置產生,不得手改) |
| [`docs/api/events/`](docs/api/events/README.md) · [`webhooks.md`](docs/api/webhooks.md) | 事件 JSON Schema 與 topic 對照、webhook 接收端契約(含 timestamp 偏差規則) |
| [`docs/api/sync-client-contract.md`](docs/api/sync-client-contract.md) | Bloom / 增量同步的 client 契約 |
| [`docs/deployment/licensing.md`](docs/deployment/licensing.md) · [`privacy.md`](docs/deployment/privacy.md) · [`rate-limiting.md`](docs/deployment/rate-limiting.md) | 第三方授權、個資與保留、限流與多實例前置(**ShedLock**) |
| [`docs/spec/`](docs/spec/README.md) | 規格書(single source of truth) |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) · [`SECURITY.md`](SECURITY.md) · [`LICENSE`](LICENSE) | 貢獻、漏洞回報、授權 |

---

## Demo(畫面速覽)

M1 的四個主要畫面——儀表板、IOC 檢索、IOC 詳情、Swagger UI——的截圖與說明見
[`docs/demo/`](docs/demo/README.md)(匿名唯讀,只呈現 public TLP:CLEAR 情資)。

[![儀表板](docs/demo/dashboard.png)](docs/demo/README.md)

---

## 授權與安全

- 授權：見 [`LICENSE`](LICENSE)
- 安全政策與漏洞回報：見 [`SECURITY.md`](SECURITY.md)
- **安全架構**（認證、授權、租戶隔離與 TLP 可見度、CSRF 停用的決策、secret、資料庫權限模型）：
  [`docs/architecture/security.md`](docs/architecture/security.md)
- 第三方元件授權說明（Redis / Elasticsearch 的 copyleft 考量與替代方案）：[`docs/deployment/licensing.md`](docs/deployment/licensing.md)（Phase 19 產出；規格要求見 [06 §6.5](docs/spec/06-tech-stack.md#65-授權注意事項)）。**Redis → Valkey 的實際替換步驟**（只需改 image 名稱與 healthcheck 指令，程式零修改）已記於 [`docs/deployment/rate-limiting.md`](docs/deployment/rate-limiting.md) §4
- 個資與資料保留：[`docs/deployment/privacy.md`](docs/deployment/privacy.md)（Phase 21 產出；規格要求見 [13 §13.4](docs/spec/13-platform-ops.md#134-隱私與資料保留)）

⚠️ 本平台處理的 IP 位址在 GDPR 下**可能構成個人資料**。規格已納入資料保留政策、資料主體查詢與刪除程序，以及情資再散布的法遵限制（多數商業情資來源的 ToS 禁止再散布原始資料）。詳見 [07 §7.9](docs/spec/07-domain-intel.md#79-再散布政策法遵強制) 與 [13 §13.4](docs/spec/13-platform-ops.md#134-隱私與資料保留)。
