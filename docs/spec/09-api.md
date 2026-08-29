# 09 — REST API 契約

> **規範等級：強制。** 端點清單、分頁契約、錯誤結構、錯誤碼、DTO 規則為規範性內容。
>
> 相關檔案：[10-identity-plans.md](10-identity-plans.md)（認證與配額）、[07-domain-intel.md](07-domain-intel.md#79-再散布政策法遵強制)（輸出過濾）

Base path：`/api/v1`

---

## 9.1 端點清單

### 系統

```text
GET    /api/v1/health                              匿名
GET    /api/v1/version                             匿名
```

### IOC — 讀取

```text
GET    /api/v1/iocs                                匿名   cursor 分頁 + 篩選
GET    /api/v1/iocs/{id}                           匿名
GET    /api/v1/iocs/{id}/sources                   匿名   來源明細（依再散布政策過濾）
POST   /api/v1/iocs/search                         匿名   複雜查詢（body 傳條件）
POST   /api/v1/iocs/lookup                         匿名   批次精確驗證
```

### IOC — 寫入 `[Phase 14 · M2]`

```text
POST   /api/v1/iocs                                ioc:submit      單筆提交
POST   /api/v1/iocs/import                         ioc:import      批次匯入（CSV / STIX bundle）
GET    /api/v1/iocs/import/{jobId}                 ioc:import      匯入進度查詢
POST   /api/v1/iocs/{id}/report-false-positive     ioc:report-fp   誤判回報
```

> **實作回饋修訂（2026-08-28；[ADR 0019](../architecture/decisions/0019-phase14-16-spec-resolutions.md)）**：
> `GET /iocs/import/{jobId}` 原本只出現在 §9.7 的內文，端點清單裡沒有，且**沒有任何資料表**
> 能承載 job 狀態——「非同步 + 202 + jobId + 進度查詢」無處持久化。本次補進清單，
> 並於 [04](04-data-dictionary.md) 新增 `import_jobs` 表（Phase 14 的 `V28`）。

> **本版新增。** v1.1 的三十個端點裡沒有任何寫入 IOC 的方式，導致三個懸空定義：Premium 有 `tenant_bloom_capacity` 卻沒有管道產生私有 IOC；`IndicatorStatus.FALSE_POSITIVE` 與 `SourceRecordStatus.FALSE_POSITIVE` 定義了卻永遠不可能被賦值（§62 第 16 條禁止 placeholder，一個永不可達的狀態就是資料模型層級的 placeholder）。

### 統計（Dashboard）

```text
GET    /api/v1/stats/summary                       stats:read    公開統計（匿名持有此權限）
GET    /api/v1/stats/sources                       stats:read    各來源筆數與健康狀態
```

> **本版新增。** v1.1 的前端頁面表列了 M1 的 Dashboard 且標「匿名可存取（公開統計）」，但端點清單裡沒有任何統計端點。

### Threat `[M2]`

```text
GET    /api/v1/threats                             匿名   cursor 分頁 + 篩選
GET    /api/v1/threats/{id}                        匿名
GET    /api/v1/threats/{id}/indicators             匿名   關聯的 IOC(各自再過一次 IOC 可見度)
```

### Threat — 寫入 `[Phase 18 · M2]`

```text
POST   /api/v1/threats                             threat:manage   建立
PUT    /api/v1/threats/{id}/indicators/{iocId}     threat:manage   建立/更新關聯(role)
DELETE /api/v1/threats/{id}/indicators/{iocId}     threat:manage   解除關聯
POST   /api/v1/threats/{id}/external-references    threat:manage   新增外部參照
PUT    /api/v1/threats/{id}/status                 threat:manage   ACTIVE/DORMANT/RETIRED
```

> **實作回饋修訂（2026-08-29，Phase 18；[ADR 0027](../architecture/decisions/0027-phase18-threat-and-m2-stix.md)）**
>
> **本節的五個寫入端點為本版新增。** 原本 Threat 只有三個 `GET`，而 ingestion pipeline 不產生
> Threat（`RawThreatRecord` 沒有任何威脅欄位）、Phase 19–23 也沒有任何建立管道——照原樣實作，
> `threats`／`threat_indicators`／`threat_external_references` 三張表與 `Threat.linkIndicator`／
> `unlinkIndicator`／`addExternalReference`／`retire` 在正式環境**永遠不可達**，正是
> [§0.4 規則 16](00-master.md#04-coding-llm-執行規則) 禁止的 placeholder。處置與 v2.0 為
> `FALSE_POSITIVE` 補上 IOC 寫入端點完全同源（見本節「IOC — 寫入」的說明）。
>
> | 規則 | 內容 |
> |---|---|
> | 歸屬 | 請求**不得**指定 `ownerTenantId`；由呼叫者身分決定 |
> | TLP 與發布 | 與 §9.7 手動提交同一條規則:預設 `AMBER`(私有);`CLEAR`/`GREEN` 需 `ioc:publish`，且擁有者轉為 public tenant;`RED` 不進入平台 → 400 |
> | 可寫入範圍 | 自家租戶的 Threat，或 public tenant 的 Threat 但持有 `ioc:publish`；其餘一律 404（不洩漏存在性） |
> | H6 | 建立關聯時以關聯 IOC 的 TLP 收緊 Threat 的 TLP，**永不放寬**（解除關聯也不放寬）。把私有 IOC 關聯到公開威脅會把該威脅收緊到公開範圍之外——這是 H6 的必然結果，不是缺陷 |
> | 衝突 | H1（同租戶同 `(type, name)`）、H4（同 Threat 內同 `(sourceName, externalId)`，`external_id` 為 null 亦算重複）、對已 `RETIRED` 的 Threat 再變更、設定成它已經是的狀態 → 一律 `409 CONFLICT` |
> | 狀態 | `RETIRED` 是**終態**（要復活就建立新的 Threat）;端點採 `PUT /status` 而非 `POST /retire`，否則 `ThreatStatus.DORMANT` 永遠不可達(同樣是規則 16) |

### STIX

```text
GET    /api/v1/stix/{stixId}                       匿名
GET    /api/v1/stix/bundle                         stix:export   依方案限制物件數
```

### 同步 `[M2]`

```text
GET    /api/v1/sync/manifest                       sync:bloom
GET    /api/v1/sync/bloom?scope=PUBLIC|TENANT      sync:bloom
GET    /api/v1/sync/delta?base=<n>&scope=          sync:delta
```

> `POST /api/v1/sync/check` **已移除**——它與 `POST /api/v1/iocs/lookup` 功能完全相同（批次精確驗證）。保留兩個端點會產生兩套配額、兩套稽核、兩份文件。client 同步流程改用 `/iocs/lookup`。

> **實作回饋修訂（2026-08-28，Phase 16；[ADR 0025](../architecture/decisions/0025-phase16-sync-api-decisions.md)）**
>
> | 端點 | 回應 | 備註 |
> |---|---|---|
> | `GET /sync/manifest` | `200` JSON | 沒有可同步的那一層整個欄位省略；**不受** `min_sync_interval_seconds` 限制 |
> | `GET /sync/bloom` | `200` `application/octet-stream` | 直接串流（不採 302 簽章 URL，§5.4 沒有簽章金鑰）；必帶 `X-Bloom-*` 七個標頭並列入 CORS `exposedHeaders` |
> | `GET /sync/delta` | `200` JSON / `409 SNAPSHOT_REQUIRED` | `409` 不消耗同步間隔——否則 client 依 §11.6 轉去下載 full 時會立刻撞 `429` |
>
> 三個端點的錯誤出口：方案不含該層 Bloom → `403 PLAN_LIMIT_EXCEEDED`（非時間窗的能力上限）；
> 尚未產生 snapshot → `404`；同步過於頻繁 → `429` + `Retry-After`。
> client 契約全文在 [`docs/api/sync-client-contract.md`](../api/sync-client-contract.md)（[11 §11.7](11-sync-bloom.md#117-client-契約摘要必須複製進-sdk-與-api-文件) 要求）。

> **實作回饋修訂（2026-08-29，Phase 19；[ADR 0028](../architecture/decisions/0028-phase19-elasticsearch-search.md)）**
>
> `POST /iocs/search` 的契約補兩項（[13 §13.7](13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch) 只定義了行為，未定義 API 形狀）：
>
> | 項目 | 內容 |
> |---|---|
> | 請求欄位 `fuzzy`（optional，預設 `false`） | typosquatting 用的模糊比對；**僅 Elasticsearch 後端有效**，降級為 PostgreSQL 時忽略 |
> | 回應標頭 `X-Search-Backend: elasticsearch\|postgres` | 每一個 `200` 都必帶；ES 不可用時回 `200` + `postgres`，**不得回 500** |
>
> `X-Search-Backend` **必須列入 CORS `exposedHeaders`**（與 `X-RateLimit-*`、`X-Bloom-*` 同一份清單）——
> 讀不到就等於沒有降級告知，瀏覽器 client 會把降級後的結果當成完整結果。
> 降級的判斷不得在 controller（§13.7 明令），controller 只是把 `SearchPort` 已經決定好的答案寫進標頭。

### 來源

```text
GET    /api/v1/sources                             source:read   匿名持有此權限
GET    /api/v1/sources/{id}                        source:read
GET    /api/v1/sources/{id}/status                 source:read
```

### 認證 `[M2]`

```text
POST   /api/v1/auth/register                       （匿名）
POST   /api/v1/auth/login                          （匿名）
POST   /api/v1/auth/refresh                        （以請求主體的 refresh token 認證）
POST   /api/v1/auth/logout                         （以請求主體的 refresh token 認證）
```

> **實作回饋修訂（2026-08-27,Phase 13;ADR 0012 決策 13)**
> 本節與 API Key 各端點原本只有路徑與所需權限,沒有 request/response schema。
> 四個 `/auth/*` 端點為取得憑證的入口,一律匿名可存取(`refresh`/`logout` 以主體中的
> refresh token 自我認證,不需 `Authorization` 標頭)。DTO 依 §9.5 慣例(全部為 record、
> 置於 `interfaces/rest/dto/{auth,apikey}/`、經 `interfaces/rest/openapi/*Api` 標註)由實作定義,
> 對外契約以 `docs/api/openapi.json` 為單一來源。
> `POST /auth/register` 回 `201`,`login`/`refresh` 回 `200`,`logout` 回 `204`;
> `POST /api-keys` 回 `201` 且**完整金鑰只在此回傳一次**(不變量 K1)。

### 訂閱與 API Key `[M2]`

```text
GET    /api/v1/subscription                        subscription:read
GET    /api/v1/subscription/usage                  subscription:read
GET    /api/v1/api-keys                            apikey:create
POST   /api/v1/api-keys                            apikey:create
DELETE /api/v1/api-keys/{id}                       apikey:revoke
```

### 即時推送 `[M3]`

```text
GET    /api/v1/ws                                  （見下）      WebSocket 升級
GET    /api/v1/events                              （見下）      SSE fallback
```

> **本節 2026-08-28 新增（[ADR 0021](../architecture/decisions/0021-phase20-23-spec-resolutions.md)）**：
> `phase-20.md` 要交付「WebSocket + SSE fallback」、[12 §12.x](12-frontend.md) 有 `VITE_WS_URL`、
> `01` 有 `interfaces/websocket/ [M3]`，而 **M3-05 要用 Playwright 測它**——
> 但本檔原本**沒有任何 WS/SSE 的路徑、協定或認證方式定義**，測試無從得知要連哪裡。
>
> | 項目 | 規格 |
> |---|---|
> | 協定 | **原生 WebSocket**，不使用 STOMP／SockJS（單一訊息型態，不需要 broker 語意） |
> | 認證 | 升級請求以 `Sec-WebSocket-Protocol: ctip.auth.<jwt>` 攜帶 access token——瀏覽器的 WS API 無法設自訂標頭。**不接受 query string 傳 token**（會進 access log） |
> | 授權 | 需 `plans.websocket_enabled`；否則升級回 `403` |
> | 訊息 | 伺服器→client 單向；JSON，形狀為 `{ "type": <NotificationType>, "payload": {...}, "eventId": "..." }` |
> | 訂閱範圍 | 連線綁 `tenantId`，只推送該租戶可見的事件（沿用 §7.9 的輸出過濾） |
> | SSE fallback | `GET /api/v1/events`，`text/event-stream`；認證走一般 `Authorization` 標頭。事件格式同上 |
> | 心跳 | WS 每 30s ping；SSE 每 30s 送 `: keepalive` 註解行 |
>
> **補列(2026-08-29,Phase 20;[ADR 0029](../architecture/decisions/0029-phase20-kafka-and-notifications.md) 第 6 節)**:
>
> | 項目 | 規格 |
> |---|---|
> | SSE 的授權 | **與 WebSocket 共用同一個閘門**:同樣需要 `plans.websocket_enabled` 與 `notification:read`。只擋 WebSocket 等於任何 client 改連 `/events` 就繞過方案限制——兩者是同一個能力的兩種傳輸 |
> | 伺服器回選的子協定 | client 同時提供 `ctip.auth` 與 `ctip.auth.<jwt>`,**伺服器選前者**。回應標頭會進反向代理與瀏覽器的 log,不得把 token 原樣送回 |

### 通知與稽核 `[M3]`

```text
GET    /api/v1/webhooks                            webhook:manage
POST   /api/v1/webhooks                            webhook:manage
DELETE /api/v1/webhooks/{id}                       webhook:manage
GET    /api/v1/notifications                       notification:read
PATCH  /api/v1/notifications/{id}/read             notification:read
GET    /api/v1/audit-logs                          audit:read
```

### 管理 `[M3]`

```text
GET    /api/v1/admin/tenants                       system:admin
POST   /api/v1/admin/sources/{id}/sync             source:sync
PATCH  /api/v1/admin/sources/{id}                  source:manage
POST   /api/v1/admin/stix/rebuild                  system:admin
```

---

## 9.2 認證方式

```text
Authorization: Bearer <jwt>        已登入使用者
X-API-Key: ctip_<env>_<key>        機器對機器
（無標頭）                          匿名
```

三者皆經同一條 security filter chain，統一設定 `TenantContext` 與 `AuthState`（見 [01-architecture.md](01-architecture.md#111-m1-最小安全層強制phase-4)）。

> **實作回饋修訂（2026-08-28，Phase 13 收尾稽核；ADR 0013 決策 1、9）**
>
> 1. `/sources`（×3）與 `/stats`（×2）原本標「匿名」，實作因此完全沒有 `@PreAuthorize`。
>    filter chain 對路徑一律 `permitAll`，**沒有標註等於完全開放**——scope 不含 `ioc:read` 的
>    API key 仍讀得到。改標 `source:read` / `stats:read`（ANONYMOUS 亦持有，匿名行為不變）。
>    標「匿名」的端點一律是「所需權限恰好是 ANONYMOUS 角色也有的權限」，不是「不做授權檢查」。
> 2. `Authorization` 的 auth-scheme 依 RFC 7235 **大小寫不敏感**；標頭存在但 scheme 不是 Bearer
>    （例如 `Basic`）一律回 `401 UNAUTHENTICATED`，**不得靜默降級為匿名**。

同時提供 `Authorization` 與 `X-API-Key` 時，**以 `Authorization` 為準**，並記錄一則 WARN。

---

## 9.3 分頁契約（強制 cursor-based）

IOC 資料量大，offset 分頁在深頁會嚴重劣化。

```text
GET /api/v1/iocs?cursor=<opaque>&limit=50
```

回應：

```json
{
  "items": [ ],
  "nextCursor": "eyJscyI6IjIwMjYtMDgtMjBUMTA6MDA6MDBaIiwiaWQiOiIuLi4ifQ==",
  "hasMore": true
}
```

| 規則 |
|---|
| `cursor` = base64url(JSON of `{"ls": <lastSeen ISO-8601>, "id": <uuid>}`)，**對外不透明** |
| 排序鍵固定 `(last_seen DESC, id DESC)`，對應索引 `ix_indicators_last_seen` |
| `limit` 預設 50，上限依方案（`plans.max_page_size`）；超過上限則**夾到上限**，不報錯 |
| 無 `nextCursor` 時該欄位為 `null` 且 `hasMore = false` |
| cursor 無法解析 → `400 INVALID_CURSOR` |
| **僅**當 UI 需要頁碼時允許 offset：`?offset=&limit=`，且強制 `offset <= 10000`，超過回 `400 OFFSET_TOO_LARGE` |

回傳型別對應 `CursorPage<T>`（[02-ddd-model.md](02-ddd-model.md#26-值物件清單)）。
**不使用 Spring Data 的 `Page`**——它帶 `totalElements`，需要 COUNT query，正是 cursor 分頁要避免的；且它屬 spring-data-commons，不在 `ctip-core` 允許的依賴內（ArchUnit 規則 8）。

編解碼集中於 `interfaces/rest/CursorCodec`。

---

## 9.4 統一錯誤回應

```json
{
  "timestamp": "2026-08-21T08:00:00Z",
  "status": 400,
  "code": "INVALID_IOC_FORMAT",
  "message": "Invalid IOC format",
  "path": "/api/v1/iocs",
  "traceId": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
  "details": [
    { "field": "value", "issue": "not a valid IPv4 address" }
  ]
}
```

- 使用 `@RestControllerAdvice` 集中處理
- **絕不將 stack trace 洩漏給 client**
- `traceId` 必須與日誌可對應（[13-platform-ops.md](13-platform-ops.md)）
- `message` 為英文（機器可讀友善）；UI 文案由前端依 `code` 對映

### 錯誤碼清單

| Code | HTTP | 說明 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 泛用參數錯誤 |
| `INVALID_IOC_FORMAT` | 400 | IOC 格式不合 |
| `OFFSET_TOO_LARGE` | 400 | offset 超過 10000 |
| `INVALID_CURSOR` | 400 | cursor 無法解析 |
| `UNAUTHENTICATED` | 401 | 缺少或無效憑證 |
| `TOKEN_EXPIRED` | 401 | token 過期 |
| `FORBIDDEN` | 403 | 權限不足 |
| `PLAN_LIMIT_EXCEEDED` | 403 | 超出方案能力（非流量），例：STIX 匯出物件數、webhook 數量 |
| `NOT_FOUND` | 404 | 資源不存在**或跨租戶** |
| `CONFLICT` | 409 | 狀態衝突 |
| `SNAPSHOT_REQUIRED` | 409 | Bloom delta 鏈過長（[11](11-sync-bloom.md)） |
| `PAYLOAD_TOO_LARGE` | 413 | 批次筆數或檔案大小超限 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 匯入檔案格式不支援 |
| `RATE_LIMIT_EXCEEDED` | 429 | 限流 |
| `INTERNAL_ERROR` | 500 | 內部錯誤 |
| `SOURCE_UNAVAILABLE` | 503 | 上游來源不可用 |

> **跨租戶一律回 `404 NOT_FOUND`，不回 403**——避免洩漏資源存在性。這一點必須有測試（[14-testing.md](14-testing.md)）。

---

## 9.5 DTO 與 Mapper

Controller **不得**直接暴露 JPA entity。

```text
Request DTO → Mapper → Application Service → Domain
Domain → Mapper → Response DTO
```

| 規則 |
|---|
| 使用 MapStruct（唯一的 annotation processor，見 [06-tech-stack.md](06-tech-stack.md#631-不使用-lombok強制)） |
| 所有 DTO 為 `record`（ArchUnit 規則 7） |
| 三模型分離見 [01-architecture.md](01-architecture.md#15-三模型-vs-兩模型強制分類)：核心聚合三模型，記錄型表兩模型 |
| DTO 放 `interfaces/rest/dto/`，依資源分子套件 |
| Mapper 放 `infrastructure/persistence/mapper/`（domain ↔ entity）與 `interfaces/rest/mapper/`（domain ↔ DTO） |
| **DTO 不得含 domain 行為方法**，只做資料承載 |

### 輸出過濾順序（強制）

每個回傳 IOC 資料的端點必須依序套用：

```text
1. TenantContext 過濾    owner_tenant_id IN (current, public)      ← Specification 層
2. TLP 過濾              tlp <= maxVisibleTlp                       ← Specification 層
3. status 過濾           預設排除 EXPIRED（除非 ?includeExpired=true）← Specification 層
4. RedistributionFilter  跨租戶時依政策遮罩／排除                     ← 輸出層
5. DTO 映射
```

1–3 在 query 層（不得依賴 controller 自律）；4 集中於一個 `RedistributionFilter`（不得散落各 controller）。

---

## 9.6 OpenAPI / Swagger

> **實作回饋修訂（2026-08-26，Phase 12;ADR 0009）— springdoc 註解陷阱(照字面實作文件必與行為不符)**
> 1. 以 record 承載 GET query 參數(如 `IocListParams`)時,handler 參數**必須**加
>    `@ParameterObject`,否則 openapi 會把整個 record 呈現為單一物件 query 參數
>    (`?params={...}`),與 Spring 實際的攤平繫結不符——generated client 會送錯 wire 格式。
> 2. 回傳 `List<T>` 的端點,`@ApiResponse` 的 content **必須**用
>    `array = @ArraySchema(schema = @Schema(implementation = T.class))`;
>    誤用單物件 `@Schema` 會使 generated 型別是單物件而非陣列
>    (Phase 12 修正:`/iocs/{id}/sources`、`/sources`、`/stats/sources`)。

使用 springdoc-openapi 3.1.0（3.x 才相容 Spring Boot 4，**不得使用 2.x**）。

```text
Swagger UI:   /swagger-ui/index.html
OpenAPI JSON: /v3/api-docs
```

| 規則 |
|---|
| `mvp`/`dev`/`staging` 開啟；`prod` 預設關閉，可經 `SWAGGER_ENABLED` 開啟但須加保護 |
| 每個公開 API 必須有：summary、description、request schema、response schema、錯誤回應、認證需求、**至少一個範例** |
| CI 必須把產生的 `openapi.json` 存為 artifact 並 commit 至 `docs/api/openapi.json` |
| CI 必須比對是否有未預期的破壞性變更（移除端點、移除必填欄位、變更型別）→ fail |
| `-parameters` 編譯旗標為 springdoc 正確推導參數名稱所必需（[06](06-tech-stack.md)） |

前端型別由此檔產生，見 [12-frontend.md](12-frontend.md)。

---

## 9.7 寫入端點細節 `[M2]`

### `POST /api/v1/iocs`

```json
{
  "type": "IPV4",
  "value": "203.0.113.5",
  "hashType": null,
  "confidence": 80,
  "severity": "HIGH",
  "tlp": "AMBER",
  "validUntil": null,
  "tags": ["internal-incident-2026-08"],
  "note": "observed in phishing campaign"
}
```

| 規則 |
|---|
| 需要權限 `ioc:submit` |
| `owner_tenant_id` = 提交者的 tenant（**不可指定**） |
| `tlp` 預設 `AMBER`（私有）。要設 `CLEAR`／`GREEN` 需額外權限 `ioc:publish`，且**同時把 `owner_tenant_id` 轉為 public tenant**（見本節末「`ioc:publish` 的語意」） |
| 來源記為系統來源 `MANUAL`，`redistribution_policy = INTERNAL_ONLY` |
| 走完整 pipeline（驗證、正規化、去重、合併），**不繞過任何 stage** |
| 配額：`plans.max_manual_submissions_per_day`，超出回 `429 RATE_LIMIT_EXCEEDED`（見本節末「配額超限的三種語意」） |
| 回應 `201` + 完整 Indicator DTO（若合併至既有 IOC 則回 `200`） |

### `POST /api/v1/iocs/import`

- `Content-Type: text/csv` 或 `application/json`（STIX 2.1 bundle）
- 需要權限 `ioc:import`
- 單檔筆數上限 `plans.max_import_rows_per_file`，超出回 `413 PAYLOAD_TOO_LARGE`
- 請求本文的位元組上限 **64 MB**，超出回 `413 PAYLOAD_TOO_LARGE`（見下方修訂）
- **非同步處理**：回 `202 Accepted` + `importJobId`，以 `GET /api/v1/iocs/import/{jobId}` 查詢進度
- 回應含逐筆結果摘要（accepted / merged / rejected 及各 rejection reason 計數）

> **本文上限必須在容器層生效（2026-08-29 補；ADR 0030）**：這是全平台唯一以原始 byte 陣列收檔的
> 端點。`@RequestBody byte[]` 會先把**整包**讀進記憶體，端點層的 64 MB 檢查在那之後才跑；
> 而 Tomcat 對**非表單**的請求本文沒有任何預設上限（`max-http-form-post-size` 只管
> `application/x-www-form-urlencoded`）。因此一個持 `ioc:import` 的帳號送一份數 GB 的本文
> 就能把 JVM 的堆積吃光——一次請求換一次 OOM。
>
> 上限改由一支排在 **security chain 之前**的 filter 強制，兩種情形都要擋：宣告了
> `Content-Length` 的直接看標頭回 413；**沒有** `Content-Length` 的（chunked）由包裝過的
> input stream 在讀滿上限的下一個位元組時中止。只檢查標頭等於沒擋——後者才是攻擊者會用的那一種。
> 端點層的檢查保留為兜底（filter 未註冊時仍有上限），兩處共用同一個常數。

### `POST /api/v1/iocs/{id}/report-false-positive`

```json
{ "reason": "legitimate CDN endpoint", "evidenceUrl": null }
```

| 規則 |
|---|
| 需要權限 `ioc:report-fp` |
| 在該 tenant 的 `indicator_sources` 中，將 `MANUAL` 來源那一列的 `status` 設為 `FALSE_POSITIVE`（若不存在則建立） |
| 隨後重跑 `IndicatorMergePolicy.determineStatus`——是否真的變成 `FALSE_POSITIVE` 由合併規則決定（[07](07-domain-intel.md#status-判定順序強制短路求值)），**不由呼叫端直接指定** |
| **只接受 `owner_tenant_id` = 呼叫者 tenant 的 Indicator**；對 public tenant 的公開情資一律回 `403 FORBIDDEN`（見本節末「誤判回報的作用域」） |
| 發出 `IndicatorFalsePositiveReported` 事件 |

> 對公開情資的誤判回報屬於「向平台營運方申訴」，不是 API 操作。M1–M3 不提供此流程，需在 `docs/api/` 說明並提供聯絡方式。

---

### 配額超限的三種語意 `[2026-08-28 定調]`

> **實作回饋修訂（[ADR 0019](../architecture/decisions/0019-phase14-16-spec-resolutions.md)）**：
> 同一件事在三處寫了三種答案——§9.7 說 `429 RATE_LIMIT_EXCEEDED`、§9.4 的錯誤碼表說
> `403 PLAN_LIMIT_EXCEEDED`「超出方案能力（非流量）」、[07 §7.3](07-domain-intel.md) 又有
> `QUOTA_EXCEEDED` 逐筆寫入 `ingestion_rejections`。三者其實各有適用情境，本次明確劃分：

| 情境 | 回應 | 為什麼 |
|---|---|---|
| **時間窗內的計數**（請求/分、請求/日、**手動提交/日**） | `429 RATE_LIMIT_EXCEEDED` + `X-RateLimit-*` + `Retry-After` | 有重置時間，client 知道何時可再試。與 §10.7 的限流同一套語意 |
| **非時間窗的能力上限**（`max_api_keys`、`max_webhooks`、`stix_export_max_objects`、`websocket_enabled`） | `403 PLAN_LIMIT_EXCEEDED` | 不會自己恢復，等待無用；要解除只能升級方案 |
| **單次請求的尺寸上限**（`max_batch_lookup`、`max_import_rows_per_file`） | `413 PAYLOAD_TOO_LARGE` | 是這一次請求太大，拆小就能過 |
| **單次分頁上限**（`max_page_size`） | 夾到上限，**不報錯** | §9.3 既有行為 |
| **批次處理中途跨越每日配額** | 請求本身成功（`202`/`200`），越界的記錄逐筆寫入 `ingestion_rejections`，reason = `QUOTA_EXCEEDED` | 已接受的部分不該因為後半超額而整批失敗 |

> **實作回饋修訂（2026-08-28，Phase 14；[ADR 0023](../architecture/decisions/0023-phase14-plans-and-write-endpoints.md)）**：
> 上表把「手動提交／日」歸在 429，但配額值 `0` 的語意是**停用**而非「用完」
> （[ADR 0019](../architecture/decisions/0019-phase14-16-spec-resolutions.md)）。
> 回 `429` + `Retry-After` 等於告訴 client「等一下再試就會過」，而配額不會隨視窗恢復。
> **同一個欄位依值分流**：`0` → `403 PLAN_LIMIT_EXCEEDED`；正整數在視窗內用罄 → `429`。
>
> **匯入的每一筆也扣減 `max_manual_submissions_per_day`**：否則每日上限可被「改用匯入端點」
> 完全繞過（PREMIUM 的每日 1,000 筆會變成每天無限次 × 每檔 10,000 筆）。
> 越界的記錄逐筆 `QUOTA_EXCEEDED`，請求本身仍回 `202`——即上表最後一列描述的行為。

### `ioc:publish` 的語意 `[2026-08-28 定調]`

> 照原文字面實作會產生「`owner_tenant_id` = 某租戶、`tlp` = `CLEAR`」的 Indicator，而它：
> 不符 [10 §10.1](10-identity-plans.md) 對公開情資的定義（owner 必須是 public tenant）、
> 不符 [11 §11.2](11-sync-bloom.md) public bloom 的成員條件、
> 也不會被其他租戶看到（`Indicator.isVisibleTo` 對非自家資料要求 `ownerTenantId.isPublic()`）。
> **「publish」不產生任何公開效果**——一個永遠沒有作用的權限就是規則 16 禁止的 placeholder。
>
> **定調**：`ioc:publish` 是**擁有權轉移**——把 `owner_tenant_id` 設為 public tenant 並套用
> `CLEAR`／`GREEN`。轉移後該 IOC 依 §7.9 的一般規則對所有人可見，且進入 public bloom。
> 原租戶的來源記錄保留（`indicator_sources` 不變），attribution 因此仍然成立。
>
> **實作回饋修訂（2026-08-28，Phase 14；[ADR 0023](../architecture/decisions/0023-phase14-plans-and-write-endpoints.md)）**：
> 只做擁有權轉移**仍然沒有任何人看得到**那筆 IOC——上表規定手動提交的來源記錄是
> `INTERNAL_ONLY`，而不變量 I14「全來源皆 `INTERNAL_ONLY` 者不得出現在非擁有租戶的任何回應中」
> 加上「擁有租戶豁免不適用於 public tenant」，等於對所有人隱藏。
> 因此**發布時該筆 MANUAL 來源記錄記為 `PUBLIC_REDISTRIBUTABLE`**：
> 上表的 `INTERNAL_ONLY` 說的是**私有提交**，而「發布」這個動作本身就是租戶對再散布的授權。

### 誤判回報的作用域 `[2026-08-28 定調]`

> 原文「在該 tenant 的 `indicator_sources` 中把 `MANUAL` 那一列設為 `FALSE_POSITIVE`」
> 與「只影響該 tenant 自己」在資料模型上**互相矛盾**：`sources` 對 `source_type` 有唯一約束
> （`ux_sources_source_type`），全平台只有**一列** `MANUAL` 來源；而 `indicator_sources`
> 是 `UNIQUE (indicator_id, source_id)`。因此對一筆 public Indicator 建立 MANUAL 誤判列，
> 改到的是**共用的公開資料**，而且第二個租戶回報同一筆時會直接撞唯一約束。
>
> **定調**：本端點**只接受 `owner_tenant_id` = 呼叫者 tenant 的 Indicator**。
> 租戶自己的 IOC 每個 tenant 各有獨立的 indicator 列，不會相撞，「只影響自己」自然成立。
> 對公開情資回 `403 FORBIDDEN`，並在錯誤訊息指向下方的申訴流程。

---

*檔案結束。端點數：43。上次校對：2026-08-28（Phase 16；端點數不變——同步三個端點自 v2.0 起即在清單內）。*
