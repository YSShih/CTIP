# 13 — 平台營運（Kafka · 通知 · 安全 · 隱私 · 稽核 · 可觀測性 · CI/CD）

> **規範等級：強制。** 安全規則、保留政策、稽核 append-only 約束為規範性內容。
>
> 相關檔案：[02-ddd-model.md](02-ddd-model.md#24-domain-event-清單)、[10-identity-plans.md](10-identity-plans.md)

---

## 13.1 事件與 Kafka `[Phase 20 · M3]`

### 演進路徑（發佈端程式碼永不修改）

```text
M1–M2:  聚合 → DomainEvent → ApplicationEventPublisher → 程序內 listener
M3:     聚合 → DomainEvent → ApplicationEventPublisher → 程序內 listener
                                                       ↘ KafkaForwardingListener → Kafka
```

M3 只新增一個 `@TransactionalEventListener(phase = AFTER_COMMIT)` 的 listener 把 domain event 轉發到 Kafka。**不修改任何發佈端。**

### 部署

使用 **KRaft 模式，不使用 ZooKeeper**（Kafka 4.x 已完全移除 ZooKeeper 支援）。

### Topics

命名格式：`ctip.<domain>.<event>.v<schema-version>`

```text
ctip.threat.ingest.v1
ctip.threat.normalized.v1
ctip.indicator.updated.v1
ctip.audit.events.v1
ctip.system.alert.v1
ctip.notification.events.v1
```

Domain event → topic 對應表必須寫入 `docs/api/events/README.md`。

### 規則

| # | 規則 |
|---|---|
| 1 | 事件必須使用**版本化 schema**（JSON Schema 或 Avro，存於 `docs/api/events/`） |
| 2 | **不得**直接把 JPA entity 當作 Kafka payload |
| 3 | 事件為 domain event，欄位獨立於持久化模型 |
| 4 | 每個事件含 `eventId`、`eventType`、`occurredAt`、`tenantId`、`traceId` |
| 5 | 消費端必須**冪等**（以 `eventId` 去重，去重表或 Redis SETNX） |
| 6 | 事件於 `AFTER_COMMIT` 發佈 |
| 7 | Kafka 不可用時**不得**使業務操作失敗——轉發 listener 失敗只記錄並排入重試 |

---

## 13.2 通知 `[Phase 20 · M3]`

支援：WebSocket、SSE（fallback）、Webhook、未來的 FCM/APNs adapter。

### 事件型別

```text
NEW_IOC | THREAT_UPDATED | IOC_REVOKED | SOURCE_FAILURE
SUBSCRIPTION_CHANGED | SYNC_SNAPSHOT_READY | SYSTEM_ALERT
```

### 實作

| 規則 |
|---|
| 使用 `ApplicationEventPublisher`（M1–M2）與 Kafka consumer（M3） |
| **不得**自行實作 listener registry |
| Webhook 必須有 **HMAC-SHA256 簽章**（標頭 `X-CTIP-Signature: sha256=<hex>`），簽章對象為原始 request body |
| 重試指數退避，最多 5 次；連續失敗 5 次 → `DISABLED` 並發出 `WebhookDisabled` |
| 訂閱過濾**在伺服器端執行**，不得把全部事件推給 client 再過濾 |
| WebSocket 僅 `plans.websocket_enabled` 為 true 的方案可連線 |

### Webhook 送達標頭

```text
X-CTIP-Signature: sha256=<hmac hex>
X-CTIP-Event-Id: <uuid>
X-CTIP-Event-Type: NEW_IOC
X-CTIP-Delivery-Attempt: 1
X-CTIP-Timestamp: 1755763200
```

簽章計算：`HMAC-SHA256(secret, timestamp + "." + body)`——含 timestamp 以防重放。接收端應拒絕 timestamp 偏差超過 5 分鐘的請求，此規則必須寫入 `docs/api/`。

---

## 13.3 安全

實作：密碼雜湊、JWT、RBAC、API key scope、限流、CORS、CSRF 考量、安全標頭、輸入驗證、以參數化查詢防 SQL injection、輸出編碼、相依弱點掃描、secret 掃描。

### 安全標頭

```text
Content-Security-Policy          （前端 nginx 設定，見 environment/config/nginx/）
Strict-Transport-Security         由反向代理層設定
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy
```

### CORS

`CORS_ALLOWED_ORIGINS` 由環境設定。**prod 絕不允許 `*`。**
啟動時若 `ENVIRONMENT=prod` 且設定含 `*`，**拒絕啟動**。

### CSRF

API 使用 Bearer token / API key，無 cookie session，因此 CSRF 保護可停用。
**必須在 `docs/architecture/security.md` 明確記錄此決策與理由（ADR）。** 若日後引入 cookie-based session，必須重新啟用。

### Secrets

正式環境 secret 絕不進 Git。來源限：環境變數、secret manager、部署平台 secret。
`.env.*.example` 僅為樣板，其中的值必須是**明顯的假值**（例如 `CHANGE_ME_MIN_32_BYTES_REPLACE_THIS`）。
`.gitignore` 必須包含 `environment/.env*` 並以 `!environment/.env*.example` 例外放行。

---

## 13.4 隱私與資料保留

### 個資考量

⚠️ **IP 位址在 GDPR 下可能構成個人資料。**

- `docs/deployment/privacy.md` 必須說明處理的資料類型與法律基礎（正當利益：網路與資訊安全，GDPR Recital 49）
- 提供資料主體查詢與刪除的操作程序（M3 提供管理端點）
- **不主動關聯 IOC 與可識別自然人**
- `audit_logs.ip` 與 `refresh_tokens.ip` 屬個資，受 `AUDIT_RETENTION_DAYS` 保留政策約束

### 保留政策

| 資料 | 保留期 | 環境變數 | 執行方式 |
|---|---|---|---|
| `audit_logs` | 180 天 | `AUDIT_RETENTION_DAYS` | 專用 DB 角色刪除（見 13.5） |
| `indicator_sources.raw_payload` | 30 天後**清空該欄位**（保留其餘欄位） | `RAW_PAYLOAD_RETENTION_DAYS` | `UPDATE ... SET raw_payload = NULL` |
| `ingestion_rejections` | 30 天 | `REJECTION_RETENTION_DAYS` | DELETE |
| `webhook_deliveries` | 30 天 | `DELIVERY_RETENTION_DAYS` | DELETE |
| `EXPIRED` 狀態的 indicator | 1 年後軟刪除（`deleted_at`） | `INDICATOR_RETENTION_DAYS` | UPDATE |
| Bloom artifact | 最近 30 個版本 | `BLOOM_ARTIFACT_KEEP` | DELETE + 檔案移除 |

由 [08-ingestion-sdk.md](08-ingestion-sdk.md#排程) 的排程任務執行。每個清理任務必須：

1. 分批執行（每批上限 10,000 列），避免長交易鎖表
2. 記錄清理筆數至日誌
3. 失敗不影響其他任務

### 情資再散布

見 [07-domain-intel.md](07-domain-intel.md#79-再散布政策法遵強制)。**這是法遵要求，不是選配。**

---

## 13.5 稽核 `[Phase 21 · M3]`

追蹤行為與欄位見 [04-data-dictionary.md](04-data-dictionary.md)（`audit_logs` 表與 4.5 列舉）。

### 規則

| # | 規則 |
|---|---|
| 1 | **僅新增（append-only）**：資料庫層以 `REVOKE UPDATE, DELETE ON audit_logs FROM <app_role>` 強制 |
| 2 | 保留清理任務使用**專用 DB 角色**（`ctip_retention`），該角色有 DELETE 權限但無 SELECT 業務表之權限 |
| 3 | 稽核寫入失敗**不得**使主要業務操作失敗（非同步寫入 + 本地有界佇列 + 溢出時記錄 ERROR） |
| 4 | 高頻的 `API_ACCESS` 使用取樣：**寫入操作 100%、讀取操作 1%**（可設定 `AUDIT_SAMPLE_READ_RATE`） |
| 5 | `metadata` JSONB **絕不含**憑證、token 原文、密碼、完整 `Authorization` 標頭 |
| 6 | `audit_logs` 表**沒有 `updated_at` 欄位**——加上它即為設計錯誤 |

第 1、2 條需在 migration `V33__create_audit_logs.sql` 中以 SQL 實作，並有一條整合測試驗證應用角色的 UPDATE/DELETE 被 DB 拒絕。

### 觸發點對照表（強制，26 種行為）

規格若只列出行為代碼而不說明**在哪裡發出**，實作者只能猜。以下為唯一的對照來源。

| `action` | 觸發點 | `resource_type` | 取樣 |
|---|---|---|---|
| `LOGIN` | `AuthService.login` 成功 | `user` | 100% |
| `LOGIN_FAILED` | `AuthService.login` 失敗（含帳號鎖定） | `user` | 100% |
| `LOGOUT` | `AuthService.logout` | `user` | 100% |
| `TOKEN_REFRESH` | `AuthService.refresh` 成功輪替 | `refresh_token` | 100% |
| `TOKEN_REUSE_DETECTED` | `User.rotateRefreshToken` 偵測到重用（不變量 U5） | `user` | 100% |
| `API_ACCESS` | security filter chain 尾端，所有已認證請求 | `endpoint` | 寫 100% / 讀 1% |
| `IOC_QUERY` | `GET /iocs`、`POST /iocs/search`、`POST /iocs/lookup` | `indicator` | 1% |
| `IOC_DOWNLOAD` | `GET /iocs` 回應筆數 > 單頁上限的一半，或 `GET /iocs/{id}/sources` | `indicator` | 100% |
| `IOC_SUBMIT` | `POST /iocs` | `indicator` | 100% |
| `IOC_IMPORT` | `POST /iocs/import`（每個 job 一筆，非每列） | `import_job` | 100% |
| `IOC_REPORT_FP` | `POST /iocs/{id}/report-false-positive` | `indicator` | 100% |
| `STIX_EXPORT` | `GET /stix/bundle` | `stix_bundle` | 100% |
| `SYNC_MANIFEST` | `GET /sync/manifest` | `bloom_version` | 1% |
| `SYNC_BLOOM` | `GET /sync/bloom` | `bloom_artifact` | 100% |
| `SYNC_DELTA` | `GET /sync/delta`（含 409 SNAPSHOT_REQUIRED） | `bloom_artifact` | 100% |
| `INGESTION_STARTED` | `SourceSyncService` 開始處理某來源 | `source` | 100% |
| `INGESTION_COMPLETED` | 該來源處理結束且 `result ∈ {SUCCESS, PARTIAL}` | `source` | 100% |
| `INGESTION_FAILED` | 該來源處理結束且 `result = FAILURE` | `source` | 100% |
| `ADMIN_ACTION` | 所有 `/api/v1/admin/**` 端點 | 依端點 | 100% |
| `TENANT_CREATED` | `TenantCreated` 事件 listener | `tenant` | 100% |
| `USER_CREATED` | `UserRegistered` 事件 listener | `user` | 100% |
| `API_KEY_CREATED` | `ApiKeyService.issue` | `api_key` | 100% |
| `API_KEY_REVOKED` | `ApiKeyService.revoke` | `api_key` | 100% |
| `SUBSCRIPTION_CHANGED` | `Subscription.changePlan` / `cancel` | `subscription` | 100% |
| `WEBHOOK_CREATED` | `POST /webhooks` | `webhook` | 100% |
| `WEBHOOK_DELETED` | `DELETE /webhooks/{id}`，以及 `Webhook` 因連續失敗被自動 `DISABLED` | `webhook` | 100% |

`result` 欄位：操作成功 → `SUCCESS`；業務失敗 → `FAILURE`；權限或配額拒絕 → `DENIED`。

`AuditCompletenessTest` 必須驗證：上表每一個 `action` 都至少被一條測試路徑實際寫入過一次——**沒有永不可達的稽核行為**。

---

## 13.6 監控、日誌、追蹤 `[Phase 22 · M3]`

### 監控

Spring Boot Actuator + Micrometer + Prometheus + Grafana。

必要指標：

```text
http.server.requests            請求數、延遲 p50/p95/p99、錯誤率
jvm.memory.*  jvm.gc.*
hikaricp.connections.*          DB 連線池
lettuce.*                       Redis
kafka.consumer.lag              Kafka consumer lag
elasticsearch.cluster.health
ctip.ingestion.records{result}  成功／失敗／合併數
ctip.ingestion.stage.duration{stage}   ← 每個 pipeline stage 的耗時（強制）
ctip.source.sync.lag{source}    來源同步延遲
ctip.bloom.generation.duration{scope}
ctip.ratelimit.rejected{dimension}
ctip.redistribution.filtered{policy}   被再散布政策過濾掉的筆數
```

`ctip.ingestion.stage.duration` 是 [08](08-ingestion-sdk.md) 選擇「顯式 stage 列表而非 Template Method」的直接收益——每個 stage 可獨立度量。

Actuator 端點在 prod 僅暴露 `health`、`info`、`prometheus`（`ACTUATOR_EXPOSED_ENDPOINTS`），且 `prometheus` 需限制來源 IP。

### 日誌

結構化 JSON 日誌（`logstash-logback-encoder`）。

必含欄位：`timestamp`、`level`、`service`、`environment`、`traceId`、`spanId`、`requestId`、`tenantId`、`userId`。

**絕不記錄**：密碼、JWT secret、API key 原文、refresh token 原文、任何憑證、完整的 `Authorization` 標頭、`X-API-Key` 標頭值。

必須有一組測試驗證 log 中不出現敏感欄位（見 [14-testing.md](14-testing.md)）。

### 追蹤

OpenTelemetry。追蹤鏈：

```text
API request → application service → DB / Redis / Kafka / Elasticsearch
```

`traceId` 必須**同時**出現在錯誤回應（[09-api.md](09-api.md#94-統一錯誤回應)）與日誌中——這是使用者回報問題時唯一的關聯線索。

---

## 13.7 搜尋 `[Phase 12 · M1（PostgreSQL）；Phase 19 · M2（Elasticsearch）]`

```java
public interface SearchPort {
    CursorPage<IndicatorSummary> search(IndicatorQuery query, Cursor cursor, int limit);
}
```

| 實作 | Phase | 說明 |
|---|---|---|
| `PostgresSearchAdapter` | M1 | JPA `Specification` + GIN 索引 + `pg_trgm` |
| `ElasticsearchSearchAdapter` | M2 | 大規模搜尋 |

**PostgreSQL 永遠是 source of truth。** Elasticsearch 僅為讀取索引，可隨時從 DB 重建。

搜尋欄位：IOC value、normalized value、type、tags、source、severity、confidence、score、status、TLP、時間區間。
能力：精確查詢、前綴查詢（domain/URL）、模糊查詢（**僅 M2**，用於 typosquatting 偵測）、分頁、排序、篩選。

> **實作回饋修訂（2026-08-26，Phase 12 實測；詳見 ADR 0009）**
> 1. **`SearchPort` 實作簽章**（Phase 9 既成、Phase 12 確認）：
>    `CursorPage<Indicator> searchByValue(String term, IndicatorFilter filter, Visibility visibility, Cursor after, int limit)`
>    ——§1.11 要求可見度是查詢輸入（不得事後過濾），故簽章帶 `Visibility`；
>    `IndicatorSummary` 投影在 M1 無消費者，回傳完整 `Indicator`。語意與上方原型等價。
> 2. **搜尋欄位已於 Phase 12 全數落實**：`IndicatorFilter` 擴充 tags（**全部包含**語意，
>    `@>` 走 `ix_indicators_tags` GIN）、sourceId（EXISTS `indicator_sources`）、
>    confidence/score 閉區間、lastSeen 時間區間；GET /iocs 與 POST /iocs/search 同步支援。
> 3. **排序在 M1 為固定 `lastSeen DESC, id DESC`**——keyset cursor 分頁的前提
>    （04 表 4 `ix_indicators_last_seen` 不可移除）；自由排序需每種排序鍵一套 cursor 編碼，
>    留待 M2 與 Elasticsearch 一併設計。
> 4. **Hibernate 地雷**：`String[]` 屬性綁 `varchar[]`，PostgreSQL 的 anyarray 運算子不對
>    `text[] @> varchar[]` 做隱式統一；tags 過濾需自訂 HQL 函式顯式 `cast(? as text[])`
>    （`PostgresFunctionContributor`），否則 SQL 直接報 operator does not exist。

### 一致性

| 規則 |
|---|
| 索引更新為非同步（M2 起經 Kafka；M1 直接同步寫入，因為 M1 沒有 ES） |
| 提供 reconciliation 排程比對 DB 與 ES 的筆數與版本（每日 05:00） |
| **索引失敗不得使 ingestion 失敗**，只記錄並排入重試 |
| **ES 不可用時 API 自動降級為 PostgreSQL 搜尋**，回 200 並在回應 header 帶 `X-Search-Backend: postgres`，不得回 500 |

降級邏輯以 Resilience4j circuit breaker 實作於 `SearchPort` 的組合實作 `FallbackSearchAdapter`，**不在 controller 判斷**。

---

## 13.8 CI/CD `[Phase 23 · M3（基本流程自 M1 就要有）]`

### Workflows

| Workflow | Phase | 內容 |
|---|---|---|
| `backend-test.yml` | M1 | L1–L3 測試、JaCoCo、ArchUnit |
| `backend-lint.yml` | M1 | **Spotless check + Checkstyle**（本版新增） |
| `frontend-test.yml` | M1 | ESLint、Prettier check、`tsc --noEmit`、Vitest |
| `build.yml` | M1 | Maven package、Vite build |
| `compose-validate.yml` | M1 | 四種 env 的 `docker compose config` + 兩項防呆檢查 |
| `openapi-check.yml` | M1 | 產生 openapi.json、比對前端 generated 型別、檢查破壞性變更 |
| `docker-build.yml` | M2 | 建置並推送映像檔（含 SBOM） |
| `security.yml` | M2 | 相依弱點掃描、secret 掃描、容器映像掃描 |
| `heavy-test.yml` | M3 | nightly L4 測試 |
| `deploy-staging.yml` | M3 | placeholder |
| `deploy-prod.yml` | M3 | placeholder，**必須使用 protected environment 並要求人工核准** |

### 安全掃描

| 類型 | 工具 |
|---|---|
| 相依弱點 | OWASP Dependency-Check 或 GitHub Dependabot alerts |
| Secret 掃描 | Gitleaks |
| 容器映像 | Trivy |
| SBOM | CycloneDX（backend）+ `npm sbom`（frontend） |

### 相依更新

啟用 Dependabot 或 Renovate：patch/minor 自動開 PR、major 需人工審核。
⚠️ 依 [06-tech-stack.md](06-tech-stack.md#612-凍結與浮動強制)，**Coding LLM 不得自行合併版本升級 PR**。

---

*檔案結束。上次校對：2026-08-21。*
