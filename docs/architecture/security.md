# 安全架構

> 治理規格:[13 §13.3](../spec/13-platform-ops.md#133-安全)、
> [10](../spec/10-identity-plans.md)(身分、RBAC、配額)、
> [07 §7.7](../spec/07-domain-intel.md#tlp-可見度)/[§7.9](../spec/07-domain-intel.md#79-再散布政策法遵強制)(可見度與再散布)。
> 本檔說明**實際成立的防線**與**為什麼**;規範性內容以規格為準。

---

## 1. 認證

| 憑證 | 攜帶方式 | 有效期 | 儲存 |
|---|---|---|---|
| Access token(JWT,HS256) | `Authorization: Bearer <jwt>` | 15 分鐘 | 不落庫 |
| Refresh token | 請求 body(`POST /api/v1/auth/refresh`) | 較長,**輪替制** | 只存雜湊 |
| API key | `X-API-Key` | 由建立者撤銷 | 只存雜湊 + prefix |
| WebSocket | `Sec-WebSocket-Protocol: ctip.auth.<jwt>` | 同 access token | — |

**refresh token 輪替與重用偵測**:每次輪替發新的、撤銷舊的;
若舊 token 再次被使用,整個 token family 一併撤銷(被竊取的訊號)。
改密碼同樣撤銷該使用者的全部 family([ADR 0015](decisions/0015-future-phase-hardening.md))。

**WebSocket 的 token 不接受 query string**——query string 會進 access log
([ADR 0021](decisions/0021-phase20-23-spec-resolutions.md) 第 4 點)。

---

## 2. 授權

- **RBAC**:角色 → 權限,權限碼形如 `ioc:read`、`webhook:manage`、`audit:read`、`system:admin`。
  矩陣由 `RbacMatrixTest` 鎖住
- **前端守衛只是 UX**:`RequireAuth` / `RequirePermission` 讓使用者少撞一次 403,
  **後端一律再驗一次**([12 §12.5](../spec/12-frontend.md))
- **方案配額不是權限**:額度與功能開關(API 速率、Bloom 容量、匯出上限)屬 [10](../spec/10-identity-plans.md),
  超限回 `429`(時間窗)或 `403 PLAN_LIMIT_EXCEEDED`(能力上限)

---

## 3. 租戶隔離與資料可見度

**同一套述詞,三處套用**(domain 不變量 I14、query 層、輸出層):

```sql
owner_tenant_id IN (:currentTenantId, '00000000-0000-0000-0000-000000000000')
AND tlp <= :maxVisibleTlp
```

| AuthState | 可見範圍 |
|---|---|
| `ANONYMOUS` | public tenant 的 `CLEAR` |
| `AUTHENTICATED` | public tenant 的 `CLEAR` + `GREEN`,**加上**自家 tenant 的全部 TLP |

- **TLP 與方案完全解耦**:付費不會讓你看到更多情資([ADR 0038](decisions/0038-tlp-decoupled-from-plans.md))
- **`TLP:RED` 不進入平台**:ingestion 階段直接拒絕
- **再散布過濾**是獨立的第二道([07 §7.9](../spec/07-domain-intel.md#79-再散布政策法遵強制)):
  來源標為 `INTERNAL_ONLY` 的情資不對外輸出,`ATTRIBUTION_REQUIRED` 必須附上來源署名。
  ⚠️ 豁免**排除 public tenant**——public 沒有成員,對 public 資料的存取一律是公開輸出
  ([00 §0.10](../spec/00-master.md))
- **關聯不是可見度的旁路**:`GET /threats/{id}/indicators` 對每個關聯 IOC 再走一次完整可見度

---

## 4. CSRF:停用(決策記錄)

> [13 §13.3](../spec/13-platform-ops.md#133-安全) 明文要求此決策必須以 ADR 記錄於本檔。
> **完整 ADR:[0037 — 停用 CSRF 保護](decisions/0037-csrf-disabled.md)。**

**決策**:`SecurityConfig` 停用 Spring Security 的 CSRF 保護。

**理由**:CSRF 成立的前提是瀏覽器**自動附帶**憑證(cookie session、HTTP Basic、TLS client cert)。
CTIP 三種憑證(Bearer JWT、`X-API-Key`、`Sec-WebSocket-Protocol`)**全部要由 JS 明確設定**,
瀏覽器不會自動帶上;系統沒有任何 cookie session,refresh token 也由 client 自行保管。
惡意站台可以發出跨源 POST,但帶不上 `Authorization`,後端一律視為匿名,而匿名沒有寫入權限。

**重新啟用的條件(強制)**:若日後引入**任何** cookie-based session
(含「把 refresh token 放進 HttpOnly cookie」這種折衷),必須同時重新啟用 CSRF 保護——
那個變更會一次讓上述三個前提全部失效。

---

## 5. 傳輸與瀏覽器層防護

安全標頭在**前端 nginx**(`environment/config/nginx/default.conf`)以 `add_header … always` 設定:

| 標頭 | 值 |
|---|---|
| `Content-Security-Policy` | `default-src 'self'` 起頭,`frame-ancestors 'none'`、`base-uri 'self'`、`form-action 'self'` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=()` |
| `Strict-Transport-Security` | **不在此設定**——依 [13 §13.3](../spec/13-platform-ops.md#133-安全) 由 TLS 終結的反向代理層負責。佈署時必須確認它真的有設(這一項沒有自動檢查) |

CORS 由後端的 `WebCorsConfig` 依 `CORS_ALLOWED_ORIGINS` 設定,只開放 `/api/**`;
憑證走標頭而非 cookie,因此**不開 `allowCredentials`**。**prod 含 `*` 即拒絕啟動**(DoD M3-18)。

---

## 6. 注入與輸出

- 一律參數化查詢;唯一的動態 SQL 是 HQL 的自訂函式
  (`text[] @> cast(? as text[])`,[00 §0.12](../spec/00-master.md))
- **JPA entity 絕不暴露於 API**([00 §0.4](../spec/00-master.md#04-coding-llm-執行規則) 規則 9);
  DTO 由 MapStruct 映射
- Webhook 送達目標受 SSRF 限制(禁止內網位址與非 http(s) scheme,
  [13 §13.2](../spec/13-platform-ops.md#送達目標的限制ssrf2026-08-29adr-0030))

---

## 7. Secrets

- **正式環境 secret 絕不進 Git**。來源限:環境變數、secret manager、部署平台 secret
- `.gitignore` 含 `environment/.env*`,以 `!environment/.env*.example` 放行樣板
- 樣板值必須是**明顯的假值**(`CHANGE_ME_MIN_32_BYTES_REPLACE_THIS`);
  prod 啟動守衛會拒絕樣板 `JWT_SECRET`(DoD M3-18),
  `dod.sh full M3-17` 另外對真實 `.env.prod` 檢查長度、CORS 與 Swagger 開關
- **webhook secret 是唯一以加密而非雜湊儲存的憑證**:簽章需要原文,
  因此以 AES-GCM 加密、金鑰來自 `WEBHOOK_SECRET_KEK`([ADR 0021](decisions/0021-phase20-23-spec-resolutions.md) 第 3 點)
- CI 每次 push 與每日跑 Gitleaks 掃**整段歷史**(`.github/workflows/security.yml`)

---

## 8. 資料庫權限(縱深防禦)

應用**不以 superuser 連線**([ADR 0021](decisions/0021-phase20-23-spec-resolutions.md)):

| 角色 | 用途 | 權限 |
|---|---|---|
| `ctip`(`POSTGRES_USER`) | 只給 Flyway 跑 migration | DDL + 擴充 |
| `ctip_app` | 應用執行期連線 | 只有 DML |
| `ctip_retention` | 保留清理任務 | 由 migration 逐表授權 |

這是 `audit_logs` 的 append-only 能成立的前提:`REVOKE UPDATE, DELETE` 對 superuser 無效,
而 DoD **M3-09** 要求的是「**DB** 拒絕」,不是「應用碼拒絕」。

---

## 9. 稽核

26 種行為必須寫稽核軌跡([13 §13.5](../spec/13-platform-ops.md#觸發點對照表強制26-種行為));
`AuditCompletenessTest` 會把 26 條路徑各走一遍,確保沒有「永不可達的行為」。
**稽核寫入失敗不得影響主要業務操作**(DoD M3-10),軌跡本身 append-only 且只回自己租戶的資料。

---

## 10. 可觀測性與敏感資料

結構化 JSON 日誌對密碼、token、API key、webhook secret 做遮罩,
`SensitiveLogTest`(DoD M3-15)驗證日誌不含敏感欄位。
`/actuator` 在 prod 只暴露 `health,info,prometheus`,
且 `prometheus` 受 `PROMETHEUS_ALLOWED_IPS` 來源 IP 白名單限制;
`env`/`beans`/`configprops`/`heapdump`/`metrics` 一律 404。

---

## 11. 漏洞回報

見 [`SECURITY.md`](../../SECURITY.md)。
