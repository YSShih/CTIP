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

> **實作回饋修訂（2026-08-28；[ADR 0016](../architecture/decisions/0016-phase1-13-spec-backfill.md)）**
> 「M1–M2 有程序內 listener」目前**不成立**：`SpringEventPublisherAdapter` 發佈的
> `DomainEventEnvelope` 至今沒有任何消費者（全庫 `@EventListener` 零命中）。
> 發佈端已就位、消費端尚無需求，這是刻意的——但本圖不得讀成「已有 listener 在運作」。
> Phase 20 的 `KafkaForwardingListener` 會是**第一個**消費者。

M3 只新增一個 listener 把 domain event 轉發到 Kafka。**不修改任何發佈端。**

> **修訂(2026-08-29,Phase 20;[ADR 0029](../architecture/decisions/0029-phase20-kafka-and-notifications.md) 第 3 節)**:
> 原文寫 `@TransactionalEventListener(phase = AFTER_COMMIT)`,但 `SpringEventPublisherAdapter`
> **自 Phase 6 起就已經在 `afterCommit` 回呼裡才發佈信封**——事件抵達 listener 時交易早已提交,
> 再宣告一次 transactional phase 沒有作用。轉發 listener 一律 `@EventListener`
> (與 Phase 18 的 `ThreatConsistencyListener` 同一個判斷)。

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
| 7 | Kafka 不可用時**不得**使業務操作失敗——轉發 listener 失敗只記錄並排入重試,**且不得阻塞業務執行緒** |

> **規則 7 的「不得阻塞」是 2026-08-29(Phase 20;ADR 0029 第 4 節)補的**:
> 照原文實作(在 listener 裡直接 `KafkaTemplate.send()`)滿足字面,但 `send()` 在取不到 metadata 時
> 會**同步阻塞**到 `max.block.ms`(預設 60 秒)——broker 掛掉時,每一個事件都讓剛提交完交易的
> 那個請求多等一分鐘。回 200 但要等一分鐘,實務上與失敗沒有差別。
> 實作:轉發交給單執行緒 + **有界**佇列的 executor(滿了丟棄並記錄;無界佇列在長時間斷線下
> 會把堆積吃光,那才是真的讓業務操作失敗),producer 的 `max.block.ms` 一併收到 5 秒。
>
> **`KafkaAdmin` 只看得到 `NewTopic` 與 `KafkaAdmin.NewTopics` 兩種型別的 bean**
> ——宣告成 `List<NewTopic>` 完全不會被讀到,topic 只能靠 broker 的 auto-create 產生
> (分割數變成 broker 預設值),關閉 auto-create 的正式環境則直接沒有 topic。
> 對應的測試因此必須斷言**分割數**,只驗「topic 存在」會被 auto-create 蒙混過去(06 §6.3.6 第 12 條)。

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

> **簽章對象定調（2026-08-28；[ADR 0021](../architecture/decisions/0021-phase20-23-spec-resolutions.md)）**：
> 本節下方又寫「簽章計算：`HMAC-SHA256(secret, timestamp + "." + body)`——含 timestamp 以防重放」，
> 與上一句的「簽章對象為原始 request body」**互斥**。
> **以 `timestamp + "." + body` 為準**（`phases/phase-20.md` 與 M3-06 都採這一種，且它才有防重放）。
| 重試指數退避，最多 5 次；連續失敗 5 次 → `DISABLED` 並發出 `WebhookDisabled` |

> **事件名定調（2026-08-28；ADR 0021）**：[02 §2.3](02-ddd-model.md) 的不變量 W3 寫
> 「發出 **`SystemAlert`**」，但同一份 02 的 §2.4 事件清單裡**沒有 `SystemAlert` 這個事件**，
> 而 §2.4 與本節、`phase-20.md` 都寫 `WebhookDisabled`。**以 `WebhookDisabled` 為準**；
> 02 的 W3 已更正。
| 訂閱過濾**在伺服器端執行**，不得把全部事件推給 client 再過濾 |
| WebSocket 僅 `plans.websocket_enabled` 為 true 的方案可連線 |

> **通知型別的對應與過濾輸入(2026-08-29,Phase 20;ADR 0029 第 1、2、7 節)**
>
> | 項目 | 定調 |
> |---|---|
> | 過濾的輸入型別 | `Webhook.matches` / `WebhookFilter.accepts` 收的是 **`NotificationEvent`**(domain event 的通知形狀投影),不是 `DomainEvent`。§2.4 的事件身上沒有 severity / tags / sourceIds,而 §13.1 禁止修改發佈端;投影由 application 層在送出前從聚合補齊,**過濾仍完全在伺服器端**(W5 不變) |
> | 七種型別容不下的三個事件 | `SourceRecovered`、`IngestionFailed` → `SOURCE_FAILURE`(來源健康頻道,severity 各異);`IndicatorMerged` → `NEW_IOC`。**不新增第八種型別** |
> | 送達 body | 是 `notifications` 那一列的**純函數**(欄位順序寫死)。表 25 沒有 payload 欄位,而重試在數分鐘後才發生——各自重新組裝會讓 body 漂移,而 body 是簽章的一部分,接收端第二次驗簽必失敗 |
> | 完整對照表 | [`docs/api/events/README.md`](../api/events/README.md);接收端契約 [`docs/api/webhooks.md`](../api/webhooks.md) |

### Webhook 送達標頭

```text
X-CTIP-Signature: sha256=<hmac hex>
X-CTIP-Event-Id: <uuid>
X-CTIP-Event-Type: NEW_IOC
X-CTIP-Delivery-Attempt: 1
X-CTIP-Timestamp: 1755763200
```

簽章計算：`HMAC-SHA256(secret, timestamp + "." + body)`——含 timestamp 以防重放。接收端應拒絕 timestamp 偏差超過 5 分鐘的請求，此規則必須寫入 `docs/api/`。

### 送達目標的限制（SSRF，2026-08-29 補；ADR 0030）

送達是「**伺服器主動對租戶指定的 URL 發出 POST**」，那正是 SSRF 的定義。只驗 `https://`
（不變量 W1 的原始寫法）遠遠不夠：任何持 `webhook:manage` 的租戶都能存進
`https://169.254.169.254/latest/meta-data/`、`https://localhost:9200/_cluster/health` 或
`https://10.0.0.5:8080/admin`，平台就會替它去打自己網路裡的東西。即使回應本文被丟棄，
`webhook_deliveries` 仍會留下狀態碼與延遲——那是一台可用的內網掃描器。

**兩道防線，缺一不可**（單獨任一道都擋不住另一種）：

| # | 位置 | 判定對象 | 擋掉的是 |
|---|---|---|---|
| 1 | 建立時（domain，`WebhookTarget`） | URL **字串** | 字面內網 IP、`localhost` / `*.internal` 一類的名稱、URL 內嵌帳密、非 https |
| 2 | 每次送達前（infrastructure，`WebhookTargetGuard`） | `InetAddress` **解析結果** | 主機名解析到內網、**DNS rebinding**（建立時是公網、之後才改指內網） |

封鎖的位址範圍（兩道防線共用同一組判定，不得各寫一份）：
`0/8`、`10/8`、`100.64/10`（CGNAT）、`127/8`、`169.254/16`（雲端 metadata）、`172.16/12`、
`192.0.0/24`、`192.168/16`、`198.18/15`、`224/4` 起（multicast 與保留）；IPv6 `::`、`::1`、
`fc00::/7`（ULA）、`fe80::/10`、`ff00::/8`，以及 `::ffff:a.b.c.d` 形式的 IPv4-mapped 位址。

> `InetAddress` 自帶的述詞**不足以**表達這組範圍：`isSiteLocalAddress()` 對 IPv6 只認已廢止的
> `fec0::/10`，不認實際在用的 ULA `fc00::/7`；IPv4 也不含 CGNAT 與 metadata 以外的保留段。
>
> **完整的目標檢查只在「建立」時做，`reconstitute` 只驗 scheme。** 規則收緊之後，
> 一列舊資料若讓聚合重建失敗，整個租戶的送達扇出會一起停擺；既存的違規目標由防線 2 擋下——
> 該處本來就必須擋（DNS 可以在建立之後才指回內網），因此放寬重建不會留下缺口。

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
- 提供資料主體查詢與刪除的操作程序（M3 提供管理端點：`GET`／`DELETE /api/v1/admin/data-subjects/{userId}`，
  權限 `system:admin`；程序與法律基礎見 `docs/deployment/privacy.md`）
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
| Bloom artifact | 最近 30 個版本 | `BLOOM_ARTIFACT_KEEP` | DELETE + 檔案移除（**由應用角色執行**，見下方註）|

由 [08-ingestion-sdk.md](08-ingestion-sdk.md#排程) 的排程任務執行。每個清理任務必須：

1. 分批執行（每批上限 10,000 列），避免長交易鎖表
2. 記錄清理筆數至日誌
3. 失敗不影響其他任務

> **實作回饋修訂（2026-08-30，Phase 21；[ADR 0031](../architecture/decisions/0031-phase21-audit-and-retention.md) 第 3 節）**：
> **Bloom artifact 的清理沿用 Phase 15 就有的 `BloomRetentionService`，以應用角色連線執行。**
> 「保留最近 N 份」不是一句 SQL：它必須避開「仍有存活版本的 dataset 的 full snapshot」
> （先刪掉它，那條 delta 鏈就永遠重建不了），並一併刪除檔案系統上的 artifact 檔。
> 以 SQL 重寫一份等於讓保留策略有兩份實作，而寫錯的後果是 `/sync/delta` 斷鏈。
> 這一項刪的是平台自己的**衍生產物**、不涉及個資，`audit_logs` 的
> `REVOKE UPDATE, DELETE`（規則 1）不受影響。其餘五項一律走 `ctip_retention`。
>
> 另：`AUDIT_SAMPLE_READ_RATE` 與四個保留 cron（`AUDIT_CLEANUP_CRON`、`PAYLOAD_CLEANUP_CRON`、
> `REJECTION_CLEANUP_CRON`、`BLOOM_ARTIFACT_CLEANUP_CRON`）原本只出現在本檔與 08 §8.7 的內文，
> 05 §5.4 的變數清單與 compose 都沒有宣告——已補齊（`ConfigSymmetryTest` 現在會擋）。

### 情資再散布

見 [07-domain-intel.md](07-domain-intel.md#79-再散布政策法遵強制)。**這是法遵要求，不是選配。**

---

## 13.5 稽核 `[Phase 21 · M3]`

追蹤行為與欄位見 [04-data-dictionary.md](04-data-dictionary.md)（`audit_logs` 表與 4.5 列舉）。

### 規則

| # | 規則 |
|---|---|
| 1 | **僅新增（append-only）**：資料庫層以 `REVOKE UPDATE, DELETE ON audit_logs FROM <app_role>` 強制 |
| 2 | 保留清理任務使用**專用 DB 角色**（`ctip_retention`），該角色有 DELETE 權限，且**只有**判斷保留期所需欄位的欄位層級 SELECT（見下方修訂） |
| 3 | 稽核寫入失敗**不得**使主要業務操作失敗（非同步寫入 + 本地有界佇列 + 溢出時記錄 ERROR） |
| 4 | 高頻的 `API_ACCESS` 使用取樣：**寫入操作 100%、讀取操作 1%**（可設定 `AUDIT_SAMPLE_READ_RATE`） |
| 5 | `metadata` JSONB **絕不含**憑證、token 原文、密碼、完整 `Authorization` 標頭 |
| 6 | `audit_logs` 表**沒有 `updated_at` 欄位**——加上它即為設計錯誤 |

第 1、2 條需在 migration `V33__create_audit_logs.sql` 中以 SQL 實作，並有一條整合測試驗證應用角色的 UPDATE/DELETE 被 DB 拒絕。

> **實作回饋修訂（2026-08-30，Phase 21；[ADR 0031](../architecture/decisions/0031-phase21-audit-and-retention.md) 第 2 節）**：
> 規則 2 原文寫「無 SELECT 業務表之權限」，**照字面授權，六項清理任務全部會 `permission denied`**：
> PostgreSQL 對 `DELETE … WHERE` 與 `UPDATE … WHERE` 仍要求 WHERE 子句所引用欄位的 SELECT 權限，
> 而每一項清理的條件都是保留期。V33 因此以**欄位層級**授權
> （`GRANT SELECT (id, occurred_at) ON audit_logs TO <retentionRole>`）——
> 清理角色讀不到 `action`／`metadata`／`ip` 等稽核內容，規則的目的成立而語句可執行。
> 批次也因此以 `id IN (SELECT id … LIMIT n)` 表達，不用 `ctid`（系統欄位不在欄位層級授權範圍內）。
>
> **觸發點的實作位置**：對照表把行為指到具體的 service 方法上，但實作以兩個**橫切消費端**承接
> ——`AuditAccessFilter`（security chain 尾端，17 種以請求為觸發點的行為）與
> `AuditEventListener`（`@EventListener`，9 種以 domain event 為觸發點的行為）。
> 業務服務一行都不改（同 §13.1「發佈端程式碼永不修改」的原則），
> 稽核寫入的失敗因此在結構上不可能傳回業務路徑（規則 3）。
> 消費端接的是**程序內**的 `DomainEventEnvelope` 而非 `ctip.audit.events.v1` 的 Kafka 消費端：
> mvp／dev 沒有 broker，稽核不能只在 staging/prod 才寫得出來。

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
| `SUBSCRIPTION_CHANGED` | `Subscription.changePlan` / `cancel`（唯一的呼叫端是 `PATCH /api/v1/admin/tenants/{id}/subscription`，Phase 21 補；ADR 0031 第 4 節） | `subscription` | 100% |
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
| 索引更新在 **M2 為 pipeline 內同步**（`SearchIndexStage`）、**M3 起改經 Kafka**——見下方修訂 |
| 提供 reconciliation 排程比對 DB 與 ES 的筆數與版本（每日 05:00，`ES_RECONCILE_CRON`） |
| **索引失敗不得使 ingestion 失敗**，只記錄並排入重試 |
| **ES 不可用時 API 自動降級為 PostgreSQL 搜尋**，回 200 並在回應 header 帶 `X-Search-Backend: postgres`，不得回 500 |

> **實作回饋修訂（2026-08-28；[ADR 0020](../architecture/decisions/0020-phase17-19-spec-resolutions.md)）**：
> 第一列原寫「M2 起經 Kafka」，但 [§13.1](#131-事件與-kafka-phase-20--m3) 標明 Kafka 是
> `[Phase 20 · M3]`，而 Phase 19（ES）在 M2。[08 §8.2](08-ingestion-sdk.md) 也寫
> 「M2 起在 `pe` 之後插入 `BloomUpdateStage` 與 `SearchIndexStage`」——即 pipeline 內同步。
> **定調以 08 與 phase-19 為準**：M2 是 pipeline 內同步寫入，Phase 20 引入 Kafka 後才改為非同步。
> 「索引失敗不得使 ingestion 失敗」在兩種模式下都必須成立。
>
> （2026-08-29 排版修正：本修訂原本插在表頭與後三列之間，使那三條規則沒有 render 成表格列；
> 已移到表格之後。三條規則的內容未變，一律為強制。）

降級邏輯以 Resilience4j circuit breaker 實作於 `SearchPort` 的組合實作 `FallbackSearchAdapter`，**不在 controller 判斷**。

> **實作回饋修訂（2026-08-29，Phase 19 實測；詳見 [ADR 0028](../architecture/decisions/0028-phase19-elasticsearch-search.md)）**
>
> 本節原文只定義了「要有什麼」，索引名、mapping、查詢形狀、模糊查詢的 API 契約與對帳演算法皆未定義。
> 以下為實作定案，`13 §13.7` 自本版起以此為準。
>
> 1. **`SearchPort` 簽章再次調整**（承 2026-08-26 的修訂 1）：
>    ```java
>    public record SearchQuery(String term, boolean fuzzy, IndicatorFilter filter,
>                              Visibility visibility, Cursor after, int limit) {}
>    public record SearchResult(CursorPage<Indicator> page, SearchBackend backend) {}
>    public interface SearchPort { SearchResult search(SearchQuery query); }
>    ```
>    回傳型別必須承載「哪個後端服務了這次查詢」——`X-Search-Backend` 否則沒有傳遞通道，
>    而本節同時禁止在 controller 判斷降級（ADR 0020 §8）。輸入包成 record 是因為多一個
>    `fuzzy` 會使簽章變成 6 個參數，違反 [01 §1.8](01-architecture.md#18-可讀性硬性規則與執行機制)
>    的 `ParameterNumber ≤ 5`。
> 2. **⚠️ ES 只回答「哪些 id、依什麼順序」，資料一律由 PostgreSQL 取回**
>    （`IndicatorRepository.findVisibleByIds`）。兩層防護缺一不可：ES 端仍必須完整重建可見度述詞
>    （否則分頁與 `hasMore` 建立在錯誤的候選集合上，「本頁少了幾筆」本身就是側信道），
>    而 source of truth 的再過濾則使索引落後、mapping 疏漏或索引被直接寫入時都不會變成跨租戶洩漏。
> 3. **索引名 `ctip-indicators`；mapping 為 `dynamic: strict`**。除本節列出的搜尋欄位外，
>    文件**必須**另帶三個欄位——漏掉任何一個，ES 路徑就會繞過整套過濾：
>
>    | 欄位 | 對應規則 |
>    |---|---|
>    | `ownerTenantId` | 租戶範圍（[07 §7.7](07-domain-intel.md#tlp-可見度)） |
>    | `redistributable` | 存在非 `INTERNAL_ONLY` 的來源記錄（I14 / [07 §7.9](07-domain-intel.md#79-再散布政策法遵強制)） |
>    | `disclosableSourceIds` | `sourceId` 過濾的揭露規則（[ADR 0015](../architecture/decisions/0015-future-phase-hardening.md) 修正 2） |
>
>    **軟刪除的 indicator 不進索引**（而非以旗標標記）；殘留的孤兒由對帳刪除。
>    另存 `lastSeenNanos` / `updatedAtNanos` 兩個 `long`：ES 的 `date` 只有毫秒精度，
>    而 keyset 分頁的鍵是 `(last_seen, id)`、對帳的版本是 `updated_at`，截斷到毫秒會使同一毫秒內的
>    資料在翻頁時被跳過、版本兩邊永遠對不齊。
> 4. **查詢形狀**：`normalizedValue` 為 keyword；子字串以 `wildcard` 表達（涵蓋精確與前綴查詢），
>    語意與 M1 的 `LIKE '%term%'` 逐字相同——換後端不得讓同一個查詢回不同的結果集。
>    使用者輸入的 `* ? \` 一律跳脫。排序仍固定 `lastSeen DESC, id DESC`：降級可以發生在翻頁的任何一頁，
>    兩邊的 cursor 必須可以互換；**修訂 3 提到的「自由排序留待 M2 與 ES 一併設計」未於 Phase 19 實作**
>    （它需要每種排序鍵一套 cursor 編碼，與降級的 cursor 互換性直接衝突），依規則 17 明確回報。
> 5. **模糊查詢的 API 契約**：`POST /iocs/search` 的 optional `fuzzy` 旗標明示啟用
>    （`fuzziness=AUTO`、`prefixLength=1`、`maxExpansions=50`）。降級為 PostgreSQL 時該旗標無效，
>    呼叫端由 `X-Search-Backend` 得知。
> 6. **對帳演算法**：兩邊皆以文件 id 昇冪掃描，**只在兩批共同涵蓋的 id 區間內判定漂移**——
>    不設邊界的話，批次尾端之後的文件會在每一輪被誤判成孤兒刪掉，對帳會把索引愈修愈空。
>    修正方向永遠是以 DB 為準：缺漏補寫、版本落後重寫、DB 沒有的刪除。
>    本節「只記錄並**排入重試**」即由此排程承擔（不另建重啟即遺失的記憶體佇列）。
>    **另外**：索引為空而資料庫非空時（全新的 ES 叢集、或索引被刪除後），啟動後在背景補建一次——
>    否則新叢集要等到 05:00 才有資料，而搜尋在那之前照樣回 `200` 並宣稱
>    `X-Search-Backend: elasticsearch`，比降級更糟：降級至少會說出來，空索引是靜默的錯誤答案。
> 7. **circuit breaker 參數**（本節未給值）：`slidingWindowSize=10`、`minimumNumberOfCalls=3`、
>    `failureRateThreshold=50%`、`waitDurationInOpenState=30s`——比 [08 §8.5](08-ingestion-sdk.md)
>    的來源抓取靈敏，因為使用者查詢等不起 20 次逾時。
> 8. **後端切換為軟切換**：`SEARCH_BACKEND=postgres|elasticsearch`（預設 `postgres`；
>    es 只屬 `full` profile）只決定「有沒有 ES 這條路」，執行期的降級一律由 circuit breaker 負責。
>    這與 [10 §10.7](10-identity-plans.md) 的 `RATE_LIMIT_BACKEND` 硬切換語意不同。
>    ⚠️ **`ELASTICSEARCH_URL` 的 compose 預設值不得為空字串**：空值一方面讓 Boot 的 ES autoconfig
>    直接丟 `hosts must not be null nor empty`（應用完全無法啟動，即使後端是 postgres），
>    另一方面在 ES 後端下會變成「每次查詢先逾時再降級」的靜默錯誤——降級會把它蓋成看起來正常的 200。
>    守衛在 `ConfigSymmetryTest`（設定層）；寫成啟動時的 bean 檢查是**不可達**的，autoconfig 更早失敗。
> 9. **mvp/dev 必須關掉 actuator 的 elasticsearch 健康檢查**（見
>    [06 §6.3.6](06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷) 第 11 條）。

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

> **實作回饋修訂（2026-08-28；[ADR 0016](../architecture/decisions/0016-phase1-13-spec-backfill.md)）**
>
> 截至 Phase 13，`.github/workflows/` 只有 `compose-validate.yml` 與 `openapi-check.yml` 兩支。
> 本表標 **M1** 的 `backend-test`／`backend-lint`／`frontend-test`／`build` 四支與標 **M2** 的
> `docker-build`／`security` 兩支**全部逾期**——Phase 1–12 的執行單沒有任何一份列它們為交付物，
> 而 `dod.sh` 也沒有任何一項檢查 workflow 檔案是否存在（M3-19 只看最後一次 run 的結論，
> 因此「只有兩支且都綠」也會通過）。
>
> 六支的內容都已由本機判準涵蓋（`clean verify` 含 Spotless／Checkstyle／JaCoCo／ArchUnit、
> 前端六項、`dod.sh`），因此**不是品質缺口而是自動化缺口**：本機忘了跑就不會有第二道網。
> 全部併入 Phase 23 一次交付（該 phase 本就列了 11 支），並在 `dod.sh` 增設檢查
> 「11 支 workflow 檔案皆存在」，避免同樣的逾期再發生一次。

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
