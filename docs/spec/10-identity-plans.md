# 10 — 身分 · 租戶 · 方案 · 配額 · 限流

> **規範等級：強制。** 租戶隔離規則、JWT 規格、配額數值、限流維度為規範性內容。
>
> 相關檔案：[02-ddd-model.md](02-ddd-model.md)（User/ApiKey/Subscription 不變量）、[04-data-dictionary.md](04-data-dictionary.md)

---

## 10.1 多租戶

Tenant 模型從一開始就存在。一個 tenant 可代表個人使用者、組織或企業客戶。

```text
TenantType: SYSTEM | INDIVIDUAL | ORGANIZATION | ENTERPRISE
```

### Public System Tenant

```text
id:     00000000-0000-0000-0000-000000000000
slug:   public
name:   Public
type:   SYSTEM
status: ACTIVE
```

| # | 規則 |
|---|---|
| 1 | 所有匿名請求在 security filter 層綁定到此 tenant |
| 2 | 公開情資的 `owner_tenant_id` = public tenant，`tlp ∈ {CLEAR, GREEN}` |
| 3 | Public tenant **無使用者、無 API key、無 webhook、無訂閱、不可登入**（DB 層以 CHECK 約束強制，見 [04](04-data-dictionary.md)） |
| 4 | 由 `V2__seed_system_tenant.sql` 種入，**不可刪除、不可更名、不可變更 type** |

這樣 tenant 隔離只有一套邏輯，不需要為匿名開特例。

### 隔離實作（強制）

| 規則 |
|---|
| 每個 tenant-scoped 資料表都有 `tenant_id`（或 `owner_tenant_id`），且 `NOT NULL` |
| 過濾條件恆為 **`owner_tenant_id IN (:current, PUBLIC)`**，以統一的 JPA `Specification` 自動附加 |
| `TenantContext`（`@RequestScope`）由 security filter 設定 |
| **禁止**在 controller 中手動傳遞 `tenantId` 做過濾 |
| 跨租戶存取一律回 **`404`**（非 403），避免資源存在性洩漏 |

> v1.1 §25.1 寫「自動附加 `tenant_id` 條件」（單數），如此登入者看不到公開情資——§24.2 聲稱消除的特例分支其實還在。`IN (current, public)` 是唯一自洽的寫法。

---

## 10.2 匿名存取

基本公開情資存取**不得要求登入**。匿名存取仍必須受到：限流、濫用防護、端點限制、無管理權限、無私有情資。

匿名可存取的端點見 [09-api.md](09-api.md#91-端點清單)（標「匿名」者）。

---

## 10.3 使用者與 RBAC `[Phase 13 · M2]`

### 角色

```text
ANONYMOUS | USER | PREMIUM_USER | TENANT_ADMIN | SYSTEM_ADMIN
```

### 權限（23 項，完整清單見 [04-data-dictionary.md](04-data-dictionary.md)）

```text
ioc:read       ioc:export      ioc:submit    ioc:import    ioc:report-fp   ioc:publish
threat:read    threat:manage   stix:export
source:read    stats:read
sync:bloom     sync:delta
apikey:create  apikey:revoke
webhook:manage subscription:read
tenant:manage  user:manage
audit:read
source:manage  source:sync
system:admin
```

### 角色與權限矩陣

種子資料由 `V24__seed_rbac.sql`、`V27__seed_rbac_read_permissions.sql` 與
`V29__seed_plans_and_permissions.sql` 寫入（皆冪等）。

| 權限 | ANONYMOUS | USER | PREMIUM_USER | TENANT_ADMIN | SYSTEM_ADMIN |
|---|---|---|---|---|---|
| `ioc:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `threat:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `source:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `stats:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ioc:export` | — | ✓ | ✓ | ✓ | ✓ |
| `stix:export` | — | ✓ | ✓ | ✓ | ✓ |
| `sync:bloom` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `sync:delta` | — | ✓ | ✓ | ✓ | ✓ |
| `ioc:submit` | — | — | ✓ | ✓ | ✓ |
| `ioc:import` | — | — | ✓ | ✓ | ✓ |
| `ioc:report-fp` | — | ✓ | ✓ | ✓ | ✓ |
| `ioc:publish` | — | — | — | — | ✓ |
| `apikey:create` / `apikey:revoke` | — | ✓ | ✓ | ✓ | ✓ |
| `webhook:manage` | — | — | ✓ | ✓ | ✓ |
| `subscription:read` | — | ✓ | ✓ | ✓ | ✓ |
| `threat:manage` | — | — | — | ✓ | ✓ |
| `user:manage` | — | — | — | ✓ | ✓ |
| `tenant:manage` | — | — | — | ✓ | ✓ |
| `audit:read` | — | — | — | ✓ | ✓ |
| `source:manage` / `source:sync` | — | — | — | — | ✓ |
| `system:admin` | — | — | — | — | ✓ |

> **`threat:manage` 已於 Phase 18 加入（2026-08-29；[ADR 0027](../architecture/decisions/0027-phase18-threat-and-m2-stix.md)）**：
> [09 §9.1](09-api.md#91-端點清單) 原本只有三個 `GET /threats`，平台因此沒有任何建立 Threat 的
> 管道——`threats` 三張表與 Threat 聚合的四個行為永遠不可達（規則 16 的 placeholder）。
> 五個寫入端點與本權限一併補上；歸屬 `ADMIN_UP`：把 IOC 歸因到 campaign／malware family
> 是租戶層級的情資策展決策，不是一般使用者的自助操作。發布到 public tenant 另需 `ioc:publish`
> （§9.7 既有規則，`SYSTEM_ADMIN` 才有）。三處同步:本節的清單與矩陣、`V31__create_threats.sql`
> 的冪等種子、`RbacMatrix` 測試常數。矩陣格數 105 → **110**。
>
> **Phase 20 必須新增 `notification:read`（2026-08-28；[ADR 0021](../architecture/decisions/0021-phase20-23-spec-resolutions.md)）**：
> `GET /notifications` 與 `PATCH /notifications/{id}/read` 原本也沒有權限碼。
> 同樣三處同步（本節清單與矩陣、seed migration、`RbacMatrix` 常數）。建議歸屬 `LOGGED_IN`。
>
> **`subscription:read` 已於 Phase 14 加入（2026-08-28；[ADR 0023](../architecture/decisions/0023-phase14-plans-and-write-endpoints.md)）**：
> [09 §9.1](09-api.md#91-端點清單) 的 `GET /subscription` 與 `/subscription/usage` 原本沒有權限欄，
> 而本節的權限清單也沒有對應碼——但下方「實作要求」明訂**每一個 handler 都必須宣告授權**。
> 三處已同步：本節的清單與矩陣、`V29__seed_plans_and_permissions.sql`、`RbacMatrix` 測試常數。
> 歸屬 `LOGGED_IN`（USER 以上皆可看自己的方案；匿名沒有訂閱可看）。
>
> `ioc:publish`（把自己提交的 IOC 標為 `CLEAR`/`GREEN`）只給 `SYSTEM_ADMIN`——把租戶資料推入公開情資池是平台營運決策，不是租戶自助操作。

### 實作要求

| 規則 |
|---|
| **不得**在 controller 中散落 `if (role == ...)` 判斷 |
| 使用 `@PreAuthorize("hasAuthority('ioc:export')")` |
| **每一個** handler 都必須宣告授權，或列入明確的免授權白名單（`/health`、`/version`、`/auth/*`）——filter chain 對路徑一律 `permitAll`，沒有標註等於完全開放 |
| 角色→權限對應存於資料庫（`roles`、`permissions`、`role_permissions`），可調整 |
| 集中的 `PermissionEvaluator` 處理 tenant-scoped 權限 |
| API key 的 `scopes` 必須 ⊆ 建立者在該 tenant 的角色所擁有的權限（不得提權，不變量 K4） |

---

## 10.4 JWT `[Phase 13 · M2]`

| 項目 | 規格 |
|---|---|
| 演算法 | HS256（單體）；預留 RS256 供未來多服務使用 |
| Access token | 預設 15 分鐘（`JWT_ACCESS_TOKEN_EXPIRATION=900`） |
| Refresh token | 預設 30 天，**單次使用並輪替** |
| Refresh token 儲存 | 資料庫存 `SHA-256` 雜湊，**絕不存原文** |
| 撤銷 | refresh token 撤銷清單；access token 短命，不做黑名單 |
| 重用偵測 | 已使用的 refresh token 再次出現 → 撤銷該 `familyId` 全部 token，記錄 `TOKEN_REUSE_DETECTED` 稽核事件 |
| 密碼雜湊 | BCrypt（cost 12）或 Argon2id |
| 登入鎖定 | 連續失敗 10 次 → 鎖定 15 分鐘 |

> **實作回饋修訂（2026-08-28，Phase 13 收尾稽核；ADR 0013 決策 3、4、5、8）**
>
> 1. **refresh 輪替必須檢查使用者狀態。** 原本只有登入路徑檢查 `UserStatus`，輪替與 API key 驗證
>    完全不看——被停權的帳號可每 30 天輪替一次，無限期換到新的 access token。成員資格查無時
>    兩處都退回 `USER` 角色，等於「移除成員資格」只是靜默降級而非撤銷。規則統一為
>    **非 ACTIVE、或在該租戶無成員資格，就沒有身分**（`AccountAccessPolicy`）。登出不受此限
>    ——被停權者仍應能撤銷自己的 token family。
> 2. **輪替家族有絕對存活上限，預設 90 天**（`ctip.jwt.refresh-token-family-max-days`）。本節原本只寫
>    「30 天、單次使用並輪替」，而每次輪替都給滿 30 天：竊得一枚 token 的人只要持續輪替即可
>    無限期維持存取，重用偵測只在「兩邊都用同一枚」時才觸發。逾期的 family 整組撤銷
>    （`revoked_reason = EXPIRED_CLEANUP`）。
> 3. **登入失敗的訊息一律相同。** 鎖定原本回 `Account temporarily locked`、密碼錯回
>    `Invalid credentials`，連送 10 次錯密碼即可列舉帳號——抵銷了先前才修掉的 BCrypt 時間側信道。
>    鎖定事實只記伺服器端。
> 4. **密碼上限為 UTF-8 72 bytes**（BCrypt 的硬性上限；Spring Security 7 對超長輸入丟例外而非截斷）。
>    字元數擋不住：25 個中文字就是 75 bytes。

`JWT_SECRET` 必須來自環境設定，長度 ≥ 32 bytes，**絕不 commit**。
啟動時若 `ENVIRONMENT=prod` 且 `JWT_SECRET` 為樣板值或長度不足，**拒絕啟動**（見 [05-environment.md](05-environment.md#57-spring-設定對應本版新增)）。

Access token claims：`sub`（userId）、`tid`（tenantId）、`roles`、`perms`、`iat`、`exp`、`jti`。
**不放** email、姓名或任何個資。

---

## 10.5 API Key `[Phase 13 · M2]`

已認證的使用者／租戶可建立 API key。支援建立、撤銷、輪替、最後使用時間、過期、scope、租戶關聯。

- 完整 key 格式：`ctip_<env>_<32 random base62>`，`env ∈ {mvp, dev, stg, prod}`
- **完整 key 僅在建立當下回傳一次**，之後永不可查
- 只儲存 `SHA-256(fullKey)` 與**隨機段**前 8 碼的明碼前綴
- 驗證流程：取請求中的隨機段前 8 碼 → 以 `ux_api_keys_prefix` 定位單一列 → 比對雜湊（**避免全表雜湊比對**）
- `last_used_at` 非同步更新，容許最多 60 秒延遲（避免每次請求一次 UPDATE）
- 數量上限 `plans.max_api_keys`（M2 先以 `ctip.api-key.max-per-tenant` 承載，預設 10；見下方修訂）

> **實作回饋修訂（2026-08-28，Phase 13 收尾稽核；ADR 0013 決策 1、2）**
>
> 1. 權限自 19 項增為 **21 項**：新增 `source:read`、`stats:read`。原因是 `GET /sources`（×3）與
>    `GET /stats`（×2）在 §9.1 標「匿名」，實作因此完全沒有 `@PreAuthorize`——而 filter chain 是
>    `anyRequest().permitAll()`，**沒有標註等於完全開放**。一把 scope 不含 `ioc:read` 的 API key
>    讀不到 `/iocs`（403）卻讀得到這五個端點，§14.4 條號 6 的保證在此失效。兩個新權限比照
>    `ioc:read`／`threat:read`，五個角色全部持有，匿名行為不變。矩陣格數 95 → **105**。
> 2. 上表「實作要求」新增一列：每個 handler 都必須宣告授權或列入白名單，由
>    `EndpointAuthorizationTest` 逐 handler 守門（判準原本只涵蓋「權限 × 角色」這條軸，
>    「端點 → 權限」那條軸沒有任何自動化檢查）。

> **實作回饋修訂（2026-08-27，Phase 13；ADR 0012 決策 1、2）**
>
> 1. 上一節標題原寫「權限（18 項）」，但其程式碼區塊列出的 code 實際有 **19 個**。
>    清單是矩陣與 `@PreAuthorize` 的實際依據，計數只是敘述，故以清單為準改為 19；
>    `V24__seed_rbac.sql` 種入 19 個。
> 2. 前綴原寫「完整 key 的前 8 碼」，但完整格式為 `ctip_<env>_<32 base62>`，其前 8 碼恆為
>    `ctip_mvp` / `ctip_dev` / `ctip_stg` / `ctip_pro`（prod 被截斷）——同一環境**第二把 key 就會撞
>    `ux_api_keys_prefix` 唯一約束**，且「定位單一列」永不成立。改為取**隨機段**前 8 碼，
>    這是唯一同時滿足 `CHAR(8)` + UNIQUE 與定位語意的讀法（前綴仍可公開顯示於 UI）。

> **實作回饋修訂（2026-08-28，Phase 13 收尾稽核；ADR 0013 決策 6、7、11）**
>
> 1. **數量上限在 M2 以 property 承載。** `plans` 表要到 Phase 14 才存在，而 Phase 13 完全沒有
>    任何上限檢查（`countActive` 是無呼叫端的死程式），任何具 `apikey:create` 的身分可無限量鑄造金鑰。
>    比照 [ADR 0004](../architecture/decisions/0004-phase6-ingestion-pipeline-decisions.md) 匿名限流的前例，
>    先以 `ctip.api-key.max-per-tenant`（預設 10，對齊 §10.6 的 PREMIUM 列）承載，超限回
>    `PLAN_LIMIT_EXCEEDED`。**Phase 14 必須改為 `plans.max_api_keys` 查表。**
> 2. **`last_used_at` 必須以定向 UPDATE 寫入**，不得走整列覆寫的 `save`：`touch` 手上的快照是
>    認證那一刻讀的，期間若另一個請求撤銷了金鑰，回寫會把 `revoked_at` 覆寫回 null——撤銷被沖掉。
>    這與 [ADR 0011](../architecture/decisions/0011-m1-review-fixes.md) 第 1 項的
>    `IndicatorSource.mergeReport` 沖掉撤回是同一類缺陷。
> 3. `expiresAt` 由請求指定時必須是未來時間（`@FutureOrPresent`），否則會建出一把出生即死的金鑰。

---

## 10.6 方案

### 能力概述

| 方案 | 說明 |
|---|---|
| **匿名** | 唯讀公開 `CLEAR` 情資、有限查詢、僅 public Bloom、最低限流 |
| **FREE** | 需登入。額外可見 public tenant 的 `GREEN` 與自家 tenant 資料、可匯出、可建 1 支 API key |
| **PREMIUM** | 更高額度、更快同步、可下載 tenant bloom、WebSocket、Webhook、**可提交與匯入 IOC** |
| **ENTERPRISE** | 自訂 feed、最高限流、進階同步、即時事件整合、管理控制項 |

> ⚠️ **方案不決定 TLP 可見度。** TLP 由認證狀態與資料歸屬決定（[07-domain-intel.md](07-domain-intel.md#tlp-可見度)）。`plans` 表**沒有** TLP 相關欄位。

### 配額（強制，存於 `plans` 表，不得 hard-code）

| 項目 | 匿名 | FREE | PREMIUM | ENTERPRISE |
|---|---|---|---|---|
| 請求／分鐘 | 60 | 300 | 1,200 | 6,000 |
| 請求／日 | 1,000 | 20,000 | 500,000 | 依合約 |
| 單次分頁上限 | 50 | 100 | 500 | 1,000 |
| 批次驗證單次上限 | 20 | 100 | 1,000 | 5,000 |
| 同步最短間隔 | 24h | 6h | 5min | 1min |
| Public Bloom | ✓ | ✓ | ✓ | ✓ |
| Tenant Bloom 容量 | — | — | 1,000,000 | 10,000,000 |
| WebSocket | ✗ | ✗ | ✓ | ✓ |
| Webhook 數量 | 0 | 0 | 5 | 50 |
| API Key 數量 | 0 | 1 | 10 | 100 |
| 自訂 feed | ✗ | ✗ | ✗ | ✓ |
| STIX bundle 匯出 | ✗ | ≤1,000 物件 | ≤50,000 | 無限 |
| **手動提交／日** | 0 | 0 | 1,000 | 50,000 |
| **單檔匯入筆數上限** | 0 | 0 | 10,000 | 500,000 |

> **配額值的 `0` 與 `null`（2026-08-28 定調；ADR 0019）**：`0` = **停用**（不是「無限制」），
> `null` = **無限制**。這兩個值目前撞上既有實作的建構子不變量——
> `ApiKeySettings`（`maxPerTenant < 1` 丟例外）vs ANONYMOUS `max_api_keys = 0`、
> `StixExportSettings`（`maxObjects <= 0` 丟例外）vs ANONYMOUS `0` / ENTERPRISE `null`、
> `CtipProperties.Api` 的 `@Positive`。**Phase 14 必須先放寬這三處型別**（改為允許 0 與
> nullable），否則種子表的合法值會讓應用啟動即失敗。

所有數值必須可由 `.env` 覆寫，啟動時載入並更新 `plans` 表。

> **匯入與每日提交配額的關係（2026-08-28，Phase 14；[ADR 0023](../architecture/decisions/0023-phase14-plans-and-write-endpoints.md)）**：
> `max_import_rows_per_file` 是**單檔尺寸**上限，`max_manual_submissions_per_day` 是**每日總量**。
> 匯入的每一筆都扣減後者——否則每日上限可被「改用匯入端點」完全繞過。
> 兩者的數值關係因此有意義：PREMIUM 一次匯入 10,000 筆時，當日只有 1,000 筆會被接受，
> 其餘逐筆記為 `QUOTA_EXCEEDED`（[09 §9.7](09-api.md#97-寫入端點細節-m2)）。

> **實作回饋修訂（2026-08-28；[ADR 0019](../architecture/decisions/0019-phase14-16-spec-resolutions.md)）**——
> 原本的 `CTIP_PLAN_<CODE>_<FIELD>` 命名慣例**到不了容器,也綁不上屬性**,兩個獨立的問題:
>
> 1. **compose 沒有 `env_file`**,backend 的環境變數是[明列白名單](05-environment.md#54-環境變數清單)。
>    寫進 `.env` 的 `CTIP_PLAN_*` 不會被傳進容器——設定看起來可調,實際完全無效
>    （與 [ADR 0016](../architecture/decisions/0016-phase1-13-spec-backfill.md) 的 §5.5 對稱性缺陷同一類）。
> 2. **Spring relaxed binding 會把 `CTIP_PLAN_PREMIUM_MAX_API_KEYS` 對到
>    `ctip.plan.premium.max.api.keys`**,不是 `…max-api-keys`——底線一律變成點,不會變成連字號。
>
> **定調**:方案配額**不走環境變數逐項覆寫**。`plans` 表由 `V29__seed_plans.sql`（冪等）種入，
> 之後由 `SYSTEM_ADMIN` 經管理端點調整（§10.6 金流段已定 M2 由 `SYSTEM_ADMIN` 手動操作）。
> 需要在部署期覆寫時,以**單一 JSON 變數** `CTIP_PLAN_OVERRIDES`（compose 白名單已列，
> 內容為 `{"PREMIUM":{"maxApiKeys":20}}` 形式）承載，避免 relaxed binding 的命名陷阱。

### 金流

**MVP 與 M2 皆不串接金流。** 建立 `SubscriptionProvider` 抽象，讓 Stripe 或其他供應商日後可加入。`subscriptions` 表保留 `provider`、`external_subscription_id` 欄位。

方案變更於 M2 由 `SYSTEM_ADMIN` 手動操作（`provider = MANUAL`）。

---

## 10.7 限流

### 抽象

```java
public interface RateLimiterPort {
    RateLimitResult tryConsume(RateLimitKey key, int tokens);
}

public record RateLimitResult(boolean allowed, long limit, long remaining, Instant resetAt) {}
```

| 實作 | 啟用條件 | 說明 |
|---|---|---|
| `InMemoryRateLimiter` | `RATE_LIMIT_BACKEND=memory` | Bucket4j 本地。**僅單一實例正確** |
| `RedisRateLimiter` | `RATE_LIMIT_BACKEND=redis` | Bucket4j + `bucket4j-redis`（Lettuce）。桶存在 Redis，多實例共用同一份配額 |

啟動時若 `ENVIRONMENT != mvp` 但 `RATE_LIMIT_BACKEND=memory`，輸出 WARN。

> **Redis 不可用時 fail-fast（2026-08-29，Phase 17；[ADR 0026](../architecture/decisions/0026-phase17-redis-cache-and-distributed-rate-limit.md)）**：
> `RATE_LIMIT_BACKEND=redis` 而 Redis 連不上時**啟動失敗**，不得降級為記憶體實作
> ——限流是安全機制，「後端掛了就等於沒有限流」正是攻擊者要的狀態。
> 同一個 Redis 上的 `CachePort`（方案配額、RBAC 對應）則相反：讀寫失敗只記 WARN 並改為
> 每次重新載入，快取失效不該讓請求失敗。兩者的差異與部署注意事項見
> `docs/deployment/rate-limiting.md`。
>
> **Redis 內的桶鍵多帶一段容量**：bucket4j 把 `BucketConfiguration` 一併存進 Redis，
> 建立後不會因為呼叫端傳了不同的限額而更新——方案**降級**時舊桶會沿用較寬的容量直到過期（fail-open）。
> 因此鍵為 `{本節的鍵}:{capacity}`，即「限額改變就是換一個桶」，與記憶體實作
> 「限額改變即重建 bucket」同語意；舊鍵由 TTL 自行消失。此偏離僅存在於 Redis 內部，
> 不影響任何對外契約。

> **實作回饋修訂（2026-08-25，Phase 6；ADR 0004）**：
> 1. `RedisRateLimiter` 於 Phase 17 才存在。在此之前若設 `RATE_LIMIT_BACKEND=redis`
>    （dev/staging/prod 樣板預設值），啟動**暫以記憶體實作代替並 WARN**——fail-fast 會讓
>    dev 環境完全無法啟動，而 M1–M2 為單一實例，限流語意等價且仍然生效。
> 2. Port 簽章自 Phase 6 起定形為本節的 `tryConsume(RateLimitKey, tokens) → RateLimitResult`
>    （取代 Phase 4 的暫行 `tryAcquire(String)`）；`RateLimitKey` 即下方鍵格式的型別化表述。

### Phase 歸屬（本版修正）

v1.1 把整節限流標為 `[Phase 17 · M2]`，但 §24.1 要求匿名存取必須受限流（M1），而 §58 的 Phase 表只把它掛在 Phase 17，DoD-MVP 完全沒測限流——三處互相矛盾。

**修正後**：

| 階段 | 內容 |
|---|---|
| **Phase 6（M1）** | `RateLimiterPort` + `InMemoryRateLimiter`，套用於所有端點。匿名超限回 `429`（列入 DoD-MVP） |
| **Phase 17（M2）** | `RedisRateLimiter`，多實例正確性驗證 |

### 維度

限流鍵由以下組合而成，**由最specific到最general依序檢查，任一超限即拒絕**：

```text
1. API key       ratelimit:key:{apiKeyId}:{window}
2. 使用者         ratelimit:user:{userId}:{window}
3. 租戶           ratelimit:tenant:{tenantId}:{window}
4. 匿名 IP        ratelimit:ip:{normalizedIp}:{window}
5. 端點類別       ratelimit:{scope}:{subject}:{endpointClass}:{window}
```

> **維度 5 的鍵含 `{subject}`（2026-08-29，Phase 17 修正；[ADR 0026](../architecture/decisions/0026-phase17-redis-cache-and-distributed-rate-limit.md)）**：
> 原本寫的是 `ratelimit:{scope}:{endpointClass}:{window}`——**沒有主體**。照字面實作，
> `ratelimit:tenant:read:minute` 是全平台共用一個桶，任何一個租戶（或任何一個匿名 IP）
> 把它打滿，**其他所有人都會被拒絕**；一行 `curl` 迴圈即可讓整個平台的讀取端點回 429。
> 且下方比例上限的理由是「分類上限恆低於**總上限**」，那句話只有在 per-subject 時才成立。
> `{subject}` 沿用當下最 specific 的維度（已認證取 apiKey／user，匿名取 IP）。

- `window` ∈ `{minute, day}`
- 匿名 IP 正規化：IPv4 取完整位址；**IPv6 取 `/64` 前綴**（避免單一使用者以 `/64` 內的位址繞過）
- `endpointClass` 分三類：`read`（GET/查詢）、`write`（POST/PATCH/DELETE）、`heavy`（bloom 下載、STIX bundle、import）

> **以 POST 表達的查詢屬 `read`（2026-08-29，Phase 17）**：`POST /iocs/search` 與
> `POST /iocs/lookup` 不改變狀態，本節的 `read` 也明文含「查詢」。照 HTTP 方法字面歸成 `write`
> 會把前端唯一的搜尋路徑壓到總配額的 20%。`heavy` 取本節明列的三支
> （`/sync/bloom`、`/stix/bundle`、`/iocs/import`），且優先於方法判定。

> **`endpointClass` 的配額值（2026-08-28 定調；ADR 0020）**：`plans` 表只有
> `requests_per_minute` / `requests_per_day` **各一組**，04 與本節都沒有定義三類各自的數值。
> **定調**:維度 5 不另設數值,而是**以方案總配額的比例**表示——
> `read` = 100%、`write` = 20%、`heavy` = 5%(取整,至少 1)。
> 這樣不必為每個方案多開六個欄位,也保證分類上限恆低於總上限。比例值為常數,不進 `plans` 表。

> **限流維度 1–3 的歸屬（2026-08-28 定調；[ADR 0020](../architecture/decisions/0020-phase17-19-spec-resolutions.md)）**：
> 三處各說各話——本節下方寫「隨 API key／方案於 **Phase 14/17** 加入」、
> `phases/phase-17.md` 把「五個限流維度」列為自己的交付物、`docs/progress.md` 寫「Phase 14 直接取用」。
> **定調為 Phase 17**：維度 1–3 需要**依方案查表的 per-key 限額**，而那需要
> `RateLimiterPort` 改簽章（見下表）與 Redis 後端一起做才有意義；Phase 14 只負責
> `plans` 表與配額值本身。`phase-14.md` 與 `phase-17.md` 已同步。
>
> ⚠️ **維度 4（匿名 IP）必須留在認證之前**——`RateLimitFilter` 現在排在
> `SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1`，那是 Phase 13 修掉「認證失敗完全繞過限流」
> 的迴歸([ADR 0012](../architecture/decisions/0012-phase13-auth-rbac-decisions.md) 決策 16)。
> 維度 1–3 需要已解析的身分，只能在認證之後——**兩者必須分成兩個檢查點,不得把維度 4 一起搬後面**。

> **實作回饋修訂（2026-08-28；[ADR 0019](../architecture/decisions/0019-phase14-16-spec-resolutions.md)）**——
> 現行 port 簽章承載不了本節的五個維度,Phase 14／17 動工前必須先改:
>
> | 缺口 | 現況 | 需要 |
> |---|---|---|
> | **per-key 限額** | `RateLimiterPort.tryConsume(key, tokens)` 沒有任何參數傳「這把 key 的上限」;`InMemoryRateLimiter.limitFor(window)` 只看 window,回傳建構子注入的單一數值 | 限額必須隨呼叫傳入（依方案查表），否則 60/300/1200/6000 的分級無法表達 |
> | **「無上限」** | `RateLimitResult.limit` 是 `long` 原始型別 | ENTERPRISE 的 `requests_per_day` 是 `null`（依合約），而 §10.7 要求 `X-RateLimit-*` 出現在**所有**回應——需要可表示無限的型別 |
> | **同步間隔的窗** | `RateLimitKey.Window` 只有 `MINUTE`／`DAY` | `min_sync_interval_seconds` 的值是 86400／21600／300／60，其中 6h／5min／1min 表達不了；且**沒有任何欄位記錄某租戶上次同步時間**（`last_sync_at` 只在 `sources` 表） |
>
> **M1 實作範圍（2026-08-25，Phase 6；ADR 0004）**：只有匿名身分，故 Phase 6 實作維度 4
> （匿名 IP × minute/day）；維度 1–3 與 `endpointClass` 隨 API key／方案於 Phase 14/17 加入。
> 匿名數值（60/min、1000/day，§10.6）在 plans 表存在前以 property 預設值承載
> （`ctip.rate-limit.anonymous-per-minute` / `-per-day`），Phase 14 移入 plans 表。
> `/actuator/*` **不套用限流**——它是 compose healthcheck 與探針路徑，限流會使容器永遠 unhealthy。

> ⚠️ **反向代理下的 IP 取得**：必須設定 `server.forward-headers-strategy=framework` 並限定信任的代理來源。若無法確定真實 client IP，`docs/deployment/` 必須明確記載此限制——否則匿名限流可被單一 IP 偽造繞過。

> **信任來源的實作（2026-08-29，Phase 17;[ADR 0026](../architecture/decisions/0026-phase17-redis-cache-and-distributed-rate-limit.md)）**：
> Boot 的 `framework` 策略註冊的 `ForwardedHeaderFilter` **無條件採信** `X-Forwarded-*`
> ——應用只要有一條路徑能被直接連到，任何人都可以自稱來自任意 IP，維度 4 等於不存在。
> 因此以同型別的 `TrustedProxyForwardedHeaderFilter` 取代它（Boot 的 bean 標了
> `@ConditionalOnMissingFilterBean`，會自動退讓），只有直連對端落在 `TRUSTED_PROXIES`
> （新增環境變數，CIDR 清單，[05 §5.4.5](05-environment.md#545-應用程式)）之內時才處理轉發標頭。
> **預設為空 = 誰都不信（fail-closed）**：代理後方忘了設定會讓限流過嚴（所有 client 算成同一個
> 位址），而不是被繞過。`ENVIRONMENT != mvp` 而該值為空時啟動記 WARN，
> 限制與設定方式記於 **`docs/deployment/rate-limiting.md`**（本節明文要求的記載）。

### 回應

超限回 `429`，並帶標頭：

```text
X-RateLimit-Limit: 300
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1755763200
Retry-After: 42
```

`X-RateLimit-*` 三個標頭在**所有**回應（含成功）都必須帶上，反映當下最緊的維度。

> **無上限的表達（2026-08-28，Phase 14；[ADR 0023](../architecture/decisions/0023-phase14-plans-and-write-endpoints.md)）**：
> ENTERPRISE 的 `requests_per_day` 是 `null`（依合約），而標頭必須有值。
> `X-RateLimit-Limit` 與 `X-RateLimit-Remaining` 以字面值 `unlimited` 表達
> ——印 `-1` 或某個巨大數字都會被 client 當成真實配額。數值型的方案格式不變。

### 實作方式

**使用 Spring Security filter 或 `HandlerInterceptor`，禁止用 Decorator 堆疊。**

> **兩個檢查點與維度 4 的「先扣後退」（2026-08-29，Phase 17;[ADR 0026](../architecture/decisions/0026-phase17-redis-cache-and-distributed-rate-limit.md)）**
>
> | 檢查點 | 位置 | 維度 |
> |---|---|---|
> | `RateLimitFilter` | security filter chain **之前** | 4（匿名 IP） |
> | `IdentityRateLimitFilter` | 認證 filter **之後**（`addFilterAfter`） | 1、2、3、5 |
>
> 兩者共用同一個 `RateLimitResponder`（標頭、最緊維度的比較、429 的寫出）與同一份豁免規則，
> 因此「集中一處」仍然成立——不是兩份各自演化的限流邏輯。
>
> **維度 4 對已認證請求必須歸還**：限流排在認證之前（否則無效憑證完全繞過限流），
> 但那時還不知道請求會不會認證成功；而維度 4 是「**匿名** IP」，已認證者該受的是自己方案的
> 維度 1–3。若不歸還，ENTERPRISE 的 client 會被匿名方案的 60/min 綁死——**方案分級形同虛設**。
> 因此 `RateLimiterPort` 新增 `refund(key, tokens, limit)`，認證成功後歸還維度 4 的 token
> 並重寫 `X-RateLimit-*`。副作用是明確的：對已認證流量，維度 4 從速率上限變成
> 「同一 IP **同時進行中**的請求數上限」（歸還發生在 controller 之前）；
> 認證**失敗**者沒有歸還的機會，暴力破解仍然被擋。

> **實作回饋修訂（2026-08-27，Phase 13；ADR 0012 決策 16)**
> 限流器與認證的**先後順序**是安全需求,不只是實作細節。認證 filter 在憑證無效時會直接寫出 401
> 並中止 filter chain;限流器若排在認證之後就完全不會執行,**只要掛一個亂寫的 `Authorization`
> 標頭即可無限量發送請求**(實測:75 次無效 token 全回 401、零個 429,而同 IP 的匿名請求 60 次後
> 正常 429)。每次嘗試都會查一次資料庫,同時是暴力破解與資源耗盡的入口。
>
> 因此:**維度 4(匿名 IP)必須在認證之前檢查**;維度 1–3(apiKey / user / tenant)需要已解析的身分,
> 只能在認證之後。Phase 14 加入維度 1–3 時不得把維度 4 一起移到認證之後。 集中一處，可讀可除錯（見 [01-architecture.md](01-architecture.md#17-抽象判準強制)）。

---

*檔案結束。上次校對：2026-08-21。*
