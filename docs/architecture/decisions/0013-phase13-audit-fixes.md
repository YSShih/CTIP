# ADR 0013 — Phase 13 收尾稽核:端點層授權與憑證撤銷的修正

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:Phase 13 交付物的複查修正;治理規格 10 §10.3–§10.5、09 §9.1、14 §14.4、04 §4.7
- **背景**:Phase 13 已 commit 並通過判準(138/138)、`clean verify`(487 tests)、`dod.sh mvp` 38/38。
  使用者要求三件事:**逐端點**對照 §10.3 的 RBAC 矩陣稽核、資深架構師視角複查、資訊安全專家視角複查。
  本 ADR 記錄稽核發現與處置。**沒有新增 phase,這是 Phase 13 的收尾。**

---

## 稽核方法與「判準通過但軸沒覆蓋到」的發現

`phase-13.md` 的判準寫「`RbacMatrixTest` 必須以參數化涵蓋 §10.3 矩陣**每一格**」。
實作的 95 格 = **19 permission × 5 role**,斷言 `V24__seed_rbac.sql` 的 `role_permissions`
與測試常數 `RbacMatrix` 一致。判準字面達成、三份矩陣來源逐格比對無差異。

但**「端點 → 需要哪個權限」是另一條軸,完全沒有守門**:全庫 21 個 handler 只有 3 個路徑出現在該測試裡。
逐端點對照後發現 5 個端點沒有任何授權宣告(決策 1)。這條軸現在由 `EndpointAuthorizationTest` 守住。

---

## 決策 1:`/sources` ×3 與 `/stats` ×2 補上授權宣告,並新增 `source:read` / `stats:read`

`SourceController` 與 `StatsController` 整檔沒有 `@PreAuthorize`。
`SecurityConfig` 是 `anyRequest().permitAll()` + 純方法層授權,所以**沒有標註等於完全開放**。

實際影響不只是「規格要求用 `@PreAuthorize`」:`ScopeSet` 允許窄範圍(`scopes` 由呼叫端指定),
一把 scope 只有 `["apikey:create"]` 的 API key **讀不到 `/iocs`(403)卻讀得到 `/sources` 與 `/stats/*`**。
`/stats/summary` 還帶 `tenantContext.visibility()`,連該租戶私有 IOC 的統計數字都一起給。
這使 §14.4 條號 6「API key 的 scope 無法超出建立者權限」在這 5 個端點上失效。

**決策**:§10.3 原本沒有對應的 permission code,新增 `source:read` 與 `stats:read`
(權限總數 **19 → 21**,矩陣 95 格 → **105 格**)。兩者比照 `ioc:read` / `threat:read` / `sync:bloom`,
**五個角色全部持有**,因此匿名行為完全不變、openapi 的 `@SecurityRequirements`(空)語意仍正確、
`openapi.json` 不需重產。

沿用 `ioc:read` 是更省事的替代方案,但「來源清單」與「公開統計」不是 IOC 讀取,
用同一個 code 表達會讓日後想單獨收緊統計端點時無處施力。既然要改,一次改對。

種子以 `V27__seed_rbac_read_permissions.sql` 補入(冪等)。**版本號取 V27**:§4.7 的 M2 區段
V20–V26 已全部指名(V22/V23 = plans、V25 = threats、V26 = bloom),V27–V29 未指派。

> ⚠️ **既有金鑰的行為變更**:Phase 13 之後、本次修正之前建立的 API key,其 scopes 不會包含
> 這兩個新 code,因此**會失去 `/sources` 與 `/stats` 的存取權**。這正是本修正的目的
> (scope 就該是有效的收斂),但升級時應告知使用者重新建立金鑰。

## 決策 2:所有 handler 的授權宣告由測試守門

新增 `EndpointAuthorizationTest`:以 `RequestMappingHandlerMapping.getHandlerMethods()` 列舉
**每一個** `com.ctip` handler,要求「有 `@PreAuthorize`(方法上或其 openapi 介面上)」
或「在明列的免授權白名單內」。白名單只有 6 項且每一項都寫了理由:
`GET /health`、`GET /version`(不讀任何情資)與四個 `/auth/*`(取得憑證的入口,要求權限會雞生蛋)。

另外三條斷言:白名單不得放進已有標註的端點;每個被引用的 authority 都必須存在於種子
(拼錯的 code 會讓端點對所有人 403);§9.1 標「匿名」的端點所需權限必須真的在 ANONYMOUS 手上。

**已實測**:把 `StatsController` 的一個標註拿掉,此測試立刻 FAIL 並指名該端點。

## 決策 3:停權與成員資格移除必須讓既有憑證失效(fail-closed)

三條認證路徑對使用者狀態的處理原本不一致:

| 路徑 | 原本 | 問題 |
|---|---|---|
| 登入 | 檢查 `user.isActive()` | ✅ |
| refresh 輪替 | **完全不檢查** | 停權帳號可每 30 天輪替一次,無限期換到新的 access token |
| API key 驗證 | **完全不檢查** | 停權帳號的金鑰持續有效到金鑰自己過期 |
| 成員資格查無 | 兩處都 `orElse(RoleCode.USER)` | 移除成員資格只是**靜默降級**,不是撤銷 |

`User.suspend()` 目前沒有 production 呼叫端(使用者管理是 M3),所以停權只能直接改 DB ——
而這正是 M3 之前唯一的事故處置手段,卻不生效。

**決策**:新增 `AccountAccessPolicy`(application/identity)作為「這個帳號現在還能不能持有 session」
的單一判定點,規則統一為**非 ACTIVE、或在該租戶沒有成員資格,就沒有身分**。
`RefreshTokenRotator`、`ApiKeyAuthenticator`、`IdentityResolver` 全部改走它;
`IdentityResolver.resolve` 改回 `Optional`,`CtipAuthenticationFilter.roleFrom` 也不再退回 `USER`
(缺漏或無法辨識的 roles claim 一律視為無效憑證 401 —— 舊寫法的 `RoleCode.valueOf` 還會丟例外變成 500)。

登出(`revokeSession`)**刻意不要求 ACTIVE**:被停權的使用者仍應能撤銷自己的 token family。

## 決策 4:登入鎖定的訊息不得與「帳號不存在」可區分

`AuthService.login` 原本對 `LoginFailure.LOCKED` 回 `"Account temporarily locked"`、
對 `INVALID_CREDENTIALS` 回 `"Invalid credentials"`。攻擊者對候選 email 連送 10 次錯密碼,
第 11 次的訊息就分辨出帳號是否存在 —— **直接抵銷 ADR 0012 決策 17** 才修掉的 BCrypt 時間側信道
(7ms vs 440ms),也違反 `LoginFailure` 自己 javadoc 寫的「對外一律映射為同一則 401 訊息」。

**決策**:兩者統一回 `Invalid credentials`;鎖定事實只以 `log.info` 記在伺服器端(不含 email)。
代價是使用者無法從回應得知自己被鎖定 —— §10.4 沒有要求告知,依 §0.4 安全性優先。
既有的兩個單元測試原本斷言 `hasMessageContaining("locked")`,已改為斷言訊息與密碼錯誤**完全相同**。

## 決策 5:密碼上限對齊 BCrypt 的 72 bytes

`RawPassword` 宣告 12–256 **字元**、`RegisterRequest` 也是 `@Size(min=12, max=256)`,
但已解析的 `spring-security-crypto:7.1.0` 的 `BCrypt` 對超過 72 bytes 的輸入
**直接丟 `IllegalArgumentException`**(字串 `password cannot be more than 72 bytes` 在 jar 內確認),
不是靜默截斷。後果:一個 80 字元的密碼管理器密碼在註冊時被
`ApiExceptionHandler` 映射成 400 `INVALID_REQUEST` "Invalid request",沒有任何欄位說明;
登入路徑同理,而且丟在 `found.isEmpty()` 判斷之前,回 400 而非 401。

**決策**:`RawPassword` 上限改為 **UTF-8 位元組數 ≤ 72**(字元數擋不住:25 個中文字就是 75 bytes),
DTO 的 `@Size(max)` 同步為 72,讓純 ASCII 使用者拿到欄位級訊息。§10.4 只寫「BCrypt(cost 12)或 Argon2id」,
未定上限,這是實作層決定。

不採「先 SHA-256 預雜湊再 BCrypt」的常見解法:那會改變既存雜湊的格式並需要遷移,
為了「支援 72 bytes 以上的密碼」這個近乎無價值的目標付出的代價不成比例。

## 決策 6:API key 數量上限先以 property 承載

§10.5 明文「數量上限 `plans.max_api_keys`」、§10.6 配額表列出 0/1/10/100,
但 `ApiKeyService.issue` **沒有任何上限檢查**,而 `countActive` 已備妥卻無呼叫端
(違反規則 16「不留永不可達的程式」)。任何具 `apikey:create` 的身分可無限量鑄造金鑰;
一把帶該 scope 的金鑰還能自我複製出更多金鑰。

**決策**:比照 **ADR 0004**(匿名限流數值先以 property 承載、Phase 14 移入 plans 表)的前例,
新增 `ctip.api-key.max-per-tenant`(預設 **10**,對齊 §10.6 的 PREMIUM 列),超限丟
`ApiKeyLimitExceededException` → 既有的 `PLAN_LIMIT_EXCEEDED`(不新增錯誤碼)。
**Phase 14 必須把它換成 `plans.max_api_keys` 查表。**

## 決策 7:`last_used_at` 改為定向 UPDATE,不得整列覆寫

`ApiKeyAuthenticator.touch` 走 `ApiKeyRepository.save`,而 `ApiKeyRepositoryAdapter.save` 是
`findById` → `mapper.updateEntity` → `save` 的**整列覆寫**(含 `keyHash`、`scopes`、`revokedAt`),
`api_keys` 無 `@Version`、無悲觀鎖。撤銷與另一個請求的 `touch` 交錯時,`touch` 手上的舊快照
(`revokedAt == null`)會把剛寫入的 `revoked_at` **覆寫回 null → 撤銷失效**。

這與 M1 複查抓到的 **`IndicatorSource.mergeReport` 無條件把 RETRACTED 設回 ACTIVE**(ADR 0011 第 1 項)
是同一類缺陷:用整列快照回寫,沖掉別的路徑剛做的狀態變更。

**決策**:port 新增 `markUsed(ApiKeyId, Instant)`,以 `@Modifying` JPQL 只寫 `last_used_at` 與 `updated_at`。
順帶消除「每個帶 `X-API-Key` 的請求都在 filter 階段做一次 SELECT + 一次全欄位 UPDATE」的成本,
以及 `@PreUpdate` 用 `Instant.now()` 繞過 `ClockPort` 的問題(JPQL bulk update 不觸發 lifecycle callback)。

## 決策 8:refresh token family 的絕對存活上限取 90 天

每次輪替都給滿 30 天 TTL,`refresh_tokens` 沒有 family 起始時間上限;`User.changePassword` 也不撤銷任何 family。
**竊得一枚 refresh token 的人只要每 30 天輪替一次即可無限期維持存取**,而重用偵測只在
「兩邊都用同一枚」時才觸發 —— 攻擊者安靜地獨占輪替鏈就不會被偵測到。

§10.4 只寫「預設 30 天,單次使用並輪替」,**未定義 family 絕對上限**。依 §0.4(安全性優先)須決定。

**決策**:`RefreshTokenSettings` 新增 `familyMaxLifetime`(`ctip.jwt.refresh-token-family-max-days`,
預設 **90 天**)。`User.rotateRefreshToken` 以 family 最早一枚的 `issuedAt` 起算,逾期即拒絕輪替
並撤銷整個 family。撤銷原因沿用 `RevokedReason.EXPIRED_CLEANUP`,**刻意不新增列舉值** ——
新增會需要改 `ck_rt_reason` CHECK 約束(新 migration)與 04 表 15,而「family 過期」本就落在該語意內。

判定順序在 `isRevoked/isExpired` 之後:呈交的那一枚永遠是新的(≤ 30 天),老的是 family 本身。

## 決策 9:`Authorization` 的 auth-scheme 大小寫不敏感,非 Bearer 回 401

`CtipAuthenticationFilter` 用 `startsWith("Bearer ")` 精確比對。RFC 7235 的 auth-scheme
**大小寫不敏感**,所以 `Authorization: bearer <token>` 會掉進 `else` 分支綁**匿名**並回 200 + 公開資料。
錯誤方向是 fail-safe(降權不是提權),但「無聲吃掉憑證」會讓 client 端整合問題極難查。

**決策**:scheme 比對改大小寫不敏感;`Authorization` 存在但 scheme 不是 Bearer(例如 `Basic`)
一律回 401 `UNAUTHENTICATED`,不得降級為匿名。

## 決策 10:唯一約束衝突映射為 409

`UserRegistrar` 先 `existsByEmail` 再 insert、`TenantProvisioner.uniqueSlug` 先 `findBySlug` 再 insert,
兩者都是 TOCTOU。併發同 email(或同 tenantName)註冊時,輸家會撞 `ux_users_email` / `ux_tenants_slug`,
`DataIntegrityViolationException` 落到兜底成 **500**。

**決策**:`ApiExceptionHandler` 加映射到 `CONFLICT`,訊息固定為 `Conflicting request`(不揭露是哪個約束)。
不改為「先 insert 再處理衝突」:`EmailAlreadyRegisteredException` 的 409 語意更精確,
這裡只是把罕見的競態從 500 收斂成正確的狀態碼。

## 決策 11:`ApiKeyCreateRequest.expiresAt` 加 `@FutureOrPresent`

原本無任何驗證:給過去的時間會建出一把「出生即死」的金鑰(建立成功、立刻不可用)。
擋在欄位層,讓它變成明確的 400 而非沉默的無用金鑰。上限不設 —— §10.5 未要求,
且「永不過期」(null)本來就是允許的,設上限只會製造規格外的限制。

## 決策 12:`CtipPermissionEvaluator` 保留但把陷阱寫進 javadoc 與測試

§10.3 明文要求「集中的 `PermissionEvaluator` 處理 tenant-scoped 權限」。它存在且邏輯正確
(先驗 authority 再驗租戶、`SYSTEM_ADMIN` 免租戶限制),但 main 目前沒有任何
`hasPermission(...)` 呼叫點 —— tenant-scoped 授權在 M1/M2 是由 repository 層的可見度過濾
(查無 → 404)實現的,而需要它的端點(`user:manage`、`tenant:manage`)是 M3。

**決策**:保留(它是 §10.3 強制的擴充點,不是 placeholder),但把一個真實陷阱寫進 javadoc 並以測試鎖住:
2 參數的 `hasPermission(#target, 'perm')` 只要 `target` 是 `UUID` 就**一律解讀為 tenantId**。
Phase 14 若寫成 `hasPermission(#id, 'ioc:report-fp')` 而 `#id` 是 indicator 的 UUID,
會拿 indicatorId 去比 tenantId → 對所有人恆為 false,變成**全員 403 的靜默 bug**。
非租戶目標必須用 4 參數重載並給 `targetType`。

---

## 查證後確認「沒有問題」的項目(避免重工)

- **JWT algorithm confusion**:`JwtAccessTokenAdapter.verify` 沒有自己檢查 `alg`,但 Nimbus 在
  `JWSHeader.parse` 就拒絕 `alg:none`,`MACVerifier` 對非 HMAC 演算法丟 `JOSEException`
  → 被 catch 收成 `invalid()`。**無漏洞**。但這是相依函式庫的行為,升版可能改變 ——
  已以 `JwtAccessTokenAdapterTest` 的兩條否定案例釘住。
- 無 `iss`/`aud`:單一服務 + 專屬 secret,無 audience confusion 面;§10.4 未要求。
- refresh token 熵 48 base62 ≈ 285 bits、API key 32 base62 ≈ 190 bits;`SecureRandom.nextInt(62)` 無偏。
- `KeyHash.matches` 用 `MessageDigest.isEqual`(ADR 0012 決策 19);refresh token 走 hash 唯一索引查詢,
  比對在 DB 端,攻擊者無法對自己不控制的摘要做前綴試探。
- 前端 access/refresh token **只存記憶體**(`authSlice`,不進 localStorage);401 自動輪替以單一
  in-flight promise 去重,避免並行輪替誤觸重用偵測。
- `ApiKeyController` 的 tenantId 全部取自 `TenantContext`,`revoke`/`list` 都經租戶過濾 → 無 IDOR。
- `/auth/*` 受 `RateLimitFilter` 涵蓋(全端點,且排在 security chain 之前,ADR 0012 決策 16)。
- `refresh` 的四種失敗(不存在／已撤銷／已過期／重用偵測)回應完全相同的 401;
  `isReuseDetected` 旗標不進回應主體。
- `TenantSlugs.sanitize` + `withSuffix` 對空字串、單字元、純符號輸入都產生合法 slug,
  `ck_tenants_slug_format` 不會被違反。
- actuator 四個環境都是 `health,info`;prod `SWAGGER_ENABLED=false`。

## 已知並刻意不在本次處理

- **自助註冊即得 `TENANT_ADMIN`**(ADR 0012 決策 5),而 `TENANT_ADMIN` 持有 `ioc:submit` /
  `ioc:import` / `webhook:manage`。這代表 **Phase 14 的方案配額是唯一阻止「免費取得 PREMIUM 能力」的閘門**
  —— `plans.manual_submissions_per_day` 對 FREE 必須是 0,而且必須真的被檢查。已寫入 `docs/progress.md`。
- **`POST /auth/register` 的 409 可列舉已註冊的 email**。沒有 email 驗證管道(M2 無寄信基礎設施)時
  無法在不破壞註冊流程的前提下消除;受匿名 IP 限流(60/min、1000/day)節流。列為已知殘餘風險。
- **`User.changePassword` 不撤銷 token family**。改密碼端點是 M3,現在加上撤銷會是沒有呼叫端的
  推測性行為(規則 16)。M3 實作改密碼時必須一併撤銷,已寫入 `docs/progress.md`。
- **`Tenant.suspend()` / `TenantStatus.SUSPENDED` 在任何認證路徑都沒有被檢查**。與使用者停權同一類,
  但租戶停權的語意(既有資料是否仍可讀?)§10 未定義,留待 Phase 14 方案/訂閱一併定義。
