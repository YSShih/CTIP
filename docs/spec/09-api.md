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
POST   /api/v1/iocs/{id}/report-false-positive     ioc:report-fp   誤判回報
```

> **本版新增。** v1.1 的三十個端點裡沒有任何寫入 IOC 的方式，導致三個懸空定義：Premium 有 `tenant_bloom_capacity` 卻沒有管道產生私有 IOC；`IndicatorStatus.FALSE_POSITIVE` 與 `SourceRecordStatus.FALSE_POSITIVE` 定義了卻永遠不可能被賦值（§62 第 16 條禁止 placeholder，一個永不可達的狀態就是資料模型層級的 placeholder）。

### 統計（Dashboard）

```text
GET    /api/v1/stats/summary                       stats:read    公開統計（匿名持有此權限）
GET    /api/v1/stats/sources                       stats:read    各來源筆數與健康狀態
```

> **本版新增。** v1.1 的前端頁面表列了 M1 的 Dashboard 且標「匿名可存取（公開統計）」，但端點清單裡沒有任何統計端點。

### Threat `[M2]`

```text
GET    /api/v1/threats                             匿名
GET    /api/v1/threats/{id}                        匿名
GET    /api/v1/threats/{id}/indicators             匿名
```

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
GET    /api/v1/subscription
GET    /api/v1/subscription/usage
GET    /api/v1/api-keys                            apikey:create
POST   /api/v1/api-keys                            apikey:create
DELETE /api/v1/api-keys/{id}                       apikey:revoke
```

### 通知與稽核 `[M3]`

```text
GET    /api/v1/webhooks                            webhook:manage
POST   /api/v1/webhooks                            webhook:manage
DELETE /api/v1/webhooks/{id}                       webhook:manage
GET    /api/v1/notifications
PATCH  /api/v1/notifications/{id}/read
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
| `tlp` 預設 `AMBER`（私有）。要設 `CLEAR`／`GREEN` 需額外權限 `ioc:publish` |
| 來源記為系統來源 `MANUAL`，`redistribution_policy = INTERNAL_ONLY` |
| 走完整 pipeline（驗證、正規化、去重、合併），**不繞過任何 stage** |
| 配額：`plans.max_manual_submissions_per_day`，超出回 `429 RATE_LIMIT_EXCEEDED` |
| 回應 `201` + 完整 Indicator DTO（若合併至既有 IOC 則回 `200`） |

### `POST /api/v1/iocs/import`

- `Content-Type: text/csv` 或 `application/json`（STIX 2.1 bundle）
- 需要權限 `ioc:import`
- 單檔筆數上限 `plans.max_import_rows_per_file`，超出回 `413 PAYLOAD_TOO_LARGE`
- **非同步處理**：回 `202 Accepted` + `importJobId`，以 `GET /api/v1/iocs/import/{jobId}` 查詢進度
- 回應含逐筆結果摘要（accepted / merged / rejected 及各 rejection reason 計數）

### `POST /api/v1/iocs/{id}/report-false-positive`

```json
{ "reason": "legitimate CDN endpoint", "evidenceUrl": null }
```

| 規則 |
|---|
| 需要權限 `ioc:report-fp` |
| 在該 tenant 的 `indicator_sources` 中，將 `MANUAL` 來源那一列的 `status` 設為 `FALSE_POSITIVE`（若不存在則建立） |
| 隨後重跑 `IndicatorMergePolicy.determineStatus`——是否真的變成 `FALSE_POSITIVE` 由合併規則決定（[07](07-domain-intel.md#status-判定順序強制短路求值)），**不由呼叫端直接指定** |
| 只影響該 tenant 自己的 Indicator；**不得**影響 public tenant 的公開情資 |
| 發出 `IndicatorFalsePositiveReported` 事件 |

> 對公開情資的誤判回報屬於「向平台營運方申訴」，不是 API 操作。M1–M3 不提供此流程，需在 `docs/api/` 說明並提供聯絡方式。

---

*檔案結束。端點數：47。上次校對：2026-08-21。*
