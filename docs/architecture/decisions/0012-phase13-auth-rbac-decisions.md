# ADR 0012 — Phase 13(認證 · RBAC · API Key · 租戶隔離)的規格衝突處置與實作決策

- **狀態**:accepted
- **日期**:2026-08-27
- **範圍**:`docs/spec/phases/phase-13.md` 的全部交付物;治理規格 10 §10.1–§10.5、02(User/ApiKey 不變量)、04 表 10–16
- **背景**:M2 的第一個 phase。平台在此之前只有匿名身分(`AuthState` 兩態、`AnonymousTenantFilter`
  對所有請求綁 public tenant、無 Spring Security)。本 phase 加入使用者、RBAC、JWT 與 API key,
  硬性約束是「只加身分,不改 query」——Phase 4 建立的 `TlpSpecifications` 一行都不能動。

---

## 規格自身衝突(2 項,依 §0.4 優先序處置並回寫規格)

### 決策 1:權限清單為 19 個字串,而非標題所寫的「18 項」

`10 §10.3` 標題與 `04 表 12` 的種子說明都寫「18 項」,但兩處列出的 code 實際有 **19 個**:

```
ioc:read ioc:export ioc:submit ioc:import ioc:report-fp ioc:publish
threat:read stix:export sync:bloom sync:delta apikey:create apikey:revoke
webhook:manage tenant:manage user:manage audit:read source:manage source:sync system:admin
```

**決策**:以清單為準種入 19 個。清單是矩陣與 `@PreAuthorize` 的實際依據,計數只是敘述;
砍掉任何一個都會使矩陣出現無法表達的格。已回寫規格計數。
`RbacMatrixTest` 以獨立謄寫的 `RbacMatrix`(19 × 5 = 95 格)與 `V24` 種子互相驗證。

### 決策 2:`key_prefix` 取自隨機段前 8 碼,而非完整 key 的前 8 碼

`10 §10.5` 同時要求三件互斥的事:

1. 完整 key 格式 `ctip_<env>_<32 random base62>`
2. 「只儲存 SHA-256 與**前 8 碼**明碼前綴」
3. 「取請求中的前 8 碼 → 以 `ux_api_keys_prefix` 定位**單一列**」

照字面實作,前 8 碼恆為 `ctip_mvp` / `ctip_dev` / `ctip_stg` / `ctip_pro`(prod 被截斷),
同一環境**第二把 key 就會撞 `ux_api_keys_prefix` 唯一約束**,且第 3 點永遠不可能成立。

**決策**:`keyPrefix` = 完整 key 去掉 `ctip_<env>_` 之後的**隨機段前 8 碼**。這是唯一能同時滿足
schema(`CHAR(8)` + UNIQUE)與「以前綴定位單一列」的讀法,且仍保有「前綴可公開顯示於 UI」的原意。
已回寫 `10 §10.5` 與 `04 表 16`。`ApiKeyAggregateTest.k2PrefixComesFromTheRandomSegmentNotTheEnvelope`
是這條規則的回歸鎖。

---

## 規格未定義而必須決定的項目(§0.4 優先序:安全性 > 可維護性 > …)

### 決策 3:`AuthState` 維持兩態列舉,完整身分另以 `AuthenticatedIdentity` 承載

`phase-13.md` 寫「`AuthState` 由 `ANONYMOUS|AUTHENTICATED` 擴充為完整身分」,但同一份執行單的
「不得做的事」又寫「**不得改動 Phase 4 建立的 TLP 過濾邏輯**」。`AuthState` 正是 TLP 可見度的軸
(`01 §1.11`、`07 §7.7` 的對照表以它為輸入),把它改成 record 會直接改動該邏輯。

**決策**:`AuthState` 不動;新增 `application/identity/AuthenticatedIdentity`
(`userId, tenantId, role, permissions, apiKeyId`),由 `TenantContext.bindAuthenticated` 承載。
`TlpSpecifications` 與 `Visibility` 零修改。可見度規則與方案、角色仍然完全解耦(§10.6)。

### 決策 4:`RefreshTokenRepository` 獨立為 out-port

`RefreshToken` 是 `User` 聚合的內部實體,依 DDD 應只經 `UserRepository` 存取。但認證熱路徑是
「以 SHA-256 雜湊定位單一枚」,隨 `User` 全量載入其所有 token 不可行。

**決策**:獨立 port。輪替與撤銷的**規則**仍全部在 `User.rotateRefreshToken`(不變量 U4–U6),
repository 只負責載入與寫回;application 層不得自行判斷 token 狀態。

### 決策 5:註冊建立一個 `INDIVIDUAL` 租戶,並把使用者指派為 `TENANT_ADMIN`

`users.primary_tenant_id` 為 `NOT NULL` 且 `ck_users_not_public` 禁止 public tenant,
但 §9.1 的註冊端點沒有定義租戶從哪裡來。

**決策**:一次註冊 = 一個新 `INDIVIDUAL` 租戶 + 使用者成為其 `TENANT_ADMIN`。
slug 由組織名或 email local part 導出,碰撞時附加 UUID 前 8 碼。
邀請既有租戶成員屬 `user:manage`,是後續 phase 的範圍。

### 決策 6:密碼最小長度 12 碼(`RawPassword`)

§10.4 定義了雜湊演算法與鎖定門檻,但沒有定義密碼強度。依安全優先取 12 碼為硬性下限,
以值物件 `RawPassword` 強制(不是散落在 controller 的驗證)。

### 決策 7:JWT 以 `spring-security-oauth2-jose`(Nimbus)實作,不新增版本 property

`06 §6.2.2` 只列 Spring Security 7.1.x「由 BOM 決定」,**沒有列任何 JWT 函式庫**。
採 Spring Security 自帶的 JOSE 支援,版本同樣由 Boot BOM 納管,因此不觸犯規則 6
(不得自行升版/新增未列版本)。BCrypt 亦由 Spring Security 內建提供。
**依規則 17 回報:版本表缺 JWT 函式庫項目,建議補列「隨 Spring Security(Nimbus JOSE+JWT)」。**

### 決策 8:匿名是正當身分,權限不足回 403 而非 401

§10.2 明定「基本公開情資存取**不得要求登入**」——匿名不是「缺少憑證的錯誤狀態」,
而是一個具備 `ANONYMOUS` 角色權限的正當身分。

**決策**:security filter 對無憑證請求也建立一個非 Spring-anonymous 的 `CtipAuthenticationToken`,
authority 取自 `roles` 表的 `ANONYMOUS` 列。因此:

| 情況 | 回應 |
|---|---|
| 無憑證,端點需要匿名沒有的權限(如 `stix:export`) | **403 FORBIDDEN** |
| 憑證無效／格式錯誤 | 401 `UNAUTHENTICATED` |
| access token 過期 | 401 `TOKEN_EXPIRED` |
| 已認證但權限不足 | 403 `FORBIDDEN` |

副作用是 M1 的「bundle 端點對匿名回 403」(ADR 0005)語意不變,只是判定從 controller 內的
`AuthState` 檢查改為 `@PreAuthorize("hasAuthority('stix:export')")`——業務規則不再留在 controller(規則 10)。

### 決策 9:失敗路徑的副作用必須隨交易提交,因此失敗以回傳值表達

**這是實作期發現的實質缺陷**:`LoginAuthenticator` 與 `RefreshTokenRotator` 原本在
`@Transactional` 方法內「寫入 → 丟例外」。Spring 對 RuntimeException 預設 rollback,
於是:

- 登入失敗計數被 rollback ⇒ **不變量 U7 的鎖定機制完全失效**
- 重用偵測的 family 全撤被 rollback ⇒ **不變量 U5 完全失效**(整合測試實測抓到)

**決策**:兩者改以回傳值(`LoginResult` / `RotatedTokens`)交出失敗,交易在協作者內提交;
`AuthService.login` / `refresh` **刻意不標 `@Transactional`**,例外只在交易之外丟出。
`RefreshTokenRotationTest.u5ReuseOfAConsumedTokenRevokesTheWholeFamily` 是這條規則的回歸鎖。

### 決策 10:RBAC 參考資料以 60 秒 TTL 記憶化

匿名請求也要取得 `ANONYMOUS` 角色的權限,若每請求查一次 DB 會成為所有讀取路徑的固定成本。
`roles` / `permissions` / `role_permissions` 只由 migration 變更,以 60 秒 TTL 記憶化仍保留
§10.3「可在資料庫調整」的語意(最多延遲一分鐘生效)。分散式快取為 Phase 17。

### 決策 11:API key 的有效權限 = `scopes ∩ 建立者當下角色的權限`

不變量 K4 只約束**建立當下**。若建立者事後被降級,原本簽出的 key 仍帶著舊 scope。
依安全優先,驗證時再取交集,使降級即時生效。

### 決策 12:登入鎖定門檻以 yml 字面量承載,不開環境變數

§10.4 把「10 次 / 15 分鐘」寫成固定值。若寫成 `${LOGIN_MAX_FAILED_ATTEMPTS:10}`,
會出現「application.yml 使用、compose 與 `.env` 樣板未宣告」的不對稱(v1.1 已修正過的缺陷類型)。
因此以 `CtipProperties.Security` 綁定,值直接寫在 yml(仍非散落的 `@Value`)。

### 決策 13:`/auth/*` 與 `/api-keys` 的 DTO 由實作定義

§9.1 只列端點路徑與所需權限,沒有任何 request/response schema。DTO 依既有慣例設計
(全部為 record、放 `interfaces/rest/dto/{auth,apikey}/`、經 `*Api` 文件介面標註 OpenAPI),
並以 `docs/api/openapi.json` 作為對外契約的單一來源。
`GET /api/v1/api-keys` 的所需權限依 §9.1 明文為 `apikey:create`(語意可疑但照抄)。

### 決策 14:domain 不得出現名為 `now` 的方法

ArchUnit 規則 9 的實作(`01 §1.9`)以「呼叫目標方法名為 `now`」判定,owner 條件在該規則的
組合方式下未生效。`RefreshTokenRotationCommand` 的時間欄位因此命名為 `at` 而非 `now`。
規則維持原樣(它比規格描述更保守,方向上安全),此處只記錄命名約束。

### 決策 15:`.env.*.example` 的 JWT_SECRET 樣板值必須自己就 ≥ 32 bytes

Phase 13 之前沒有任何程式碼消費 `JWT_SECRET`,因此沒人發現規格與樣板沿用的假值
`CHANGE_ME_MIN_32_BYTES` **自己只有 22 bytes**——比它名字宣稱的下限還短。
HS256 要求 256-bit 金鑰,`JwtAccessTokenAdapter` 在所有環境的建構期強制此下限,
於是「照 README 快速開始複製 `.env.mvp.example`」的全新環境會**直接啟動失敗**
(實測:`ctip-backend-1` unhealthy,bean 建立期丟 `JWT_SECRET 長度必須 >= 32 bytes`)。

**決策**:樣板值改為 `CHANGE_ME_MIN_32_BYTES_REPLACE_THIS`(35 bytes)。它仍含 `CHANGE_ME`,
所以 `StartupValidator`(prod 拒絕啟動)與 `_common.sh`(`up.sh` 守衛)的判定完全不受影響;
放寬程式端的長度檢查則是錯的方向——那是密碼學要求,不是可調參數。
同步更新五份 `.env*.example`、`_common.sh`、13 §與 phase-02 的引用。
`EnvTemplateSecretTest` 逐檔驗「含 CHANGE_ME 且 ≥ 32 bytes」,鎖住這個回歸。

> **維運注意**:dev 容器的 `spring-boot:run` 在啟動時就算好 classpath,DevTools restart 只換
> app classes。**新增 Maven 相依後必須重建容器**(`docker compose up -d --force-recreate backend`),
> 只跑 `reload.sh` 會得到 `NoClassDefFoundError`。且 `up.sh` 對「已在執行但 unhealthy」的容器
> 不會重建,只會等 healthcheck 逾時。

---

## Phase 13 收尾複查發現的安全與完整性缺陷(4 項,皆已修正並加回歸測試)

以下不是規格衝突,是**我在 Phase 13 引入或遺漏的實作缺陷**,於「確認 Phase 13」時實測發現。

### 決策 16:限流必須排在認證之前 — 認證失敗曾完全繞過限流(我引入的迴歸)

`CtipAuthenticationFilter` 在憑證無效時直接寫出 401 並中止 filter chain,而 `RateLimitFilter`
排在 Spring Security chain **之後**(Boot 對 Filter bean 的預設順序是 `LOWEST_PRECEDENCE`)。
結果:**任何帶著無效憑證的請求都不受限流**。

實測:同一 IP 送 75 次無效 Bearer token → **75 次 401、零個 429**;對照組 75 次匿名請求 → 60 次 200 後正常 429。

這不只是暴力破解的入口,每一次嘗試都會查一次資料庫(API key 路徑查 `api_keys`),也是廉價的資源耗盡向量。
M1 沒有 security chain,`RateLimitFilter` 看得到每一個請求;是我加上 chain 才打破這個性質。

**決策**:以 `FilterRegistrationBean` 明確把 `RateLimitFilter` 排在
`SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1`(= -101),即 security chain 之前。
回歸鎖:`RateLimitTest.rejectedCredentialsStillConsumeTheRateLimitBudget` 與
`rejectedApiKeysStillConsumeTheRateLimitBudget`。

> ⚠️ **Phase 14 注意**:加入 key/user/tenant 維度時,那些維度需要已解析的身分、只能在認證之後檢查;
> 但 **IP 維度必須留在認證之前**,否則這個繞過會原封不動地回來。

### 決策 17:登入的回應時間不得洩漏帳號是否存在

`LoginAuthenticator` 原本在「查無此 email」時立刻返回,不跑 BCrypt;帳號存在才跑(cost 12 ≈ 400ms)。
於是回應時間本身就是一個帳號列舉神諭——**錯誤訊息刻意寫成一致完全沒有意義**。

實測:已存在帳號 + 錯密碼 ≈ **440ms**;不存在帳號 + 同樣密碼 ≈ **7ms**(60 倍差距,跨網路也量得到)。

**決策**:密碼比對一律執行,帳號不存在時比對 `PasswordHasherPort.dummyHash()`
(由實作在啟動時算一次的固定雜湊,不對應任何帳號)。鎖定中的帳號亦然,不得提早返回。
回歸鎖:`AuthServiceTest.loginPerformsAPasswordComparisonEvenWhenTheAccountDoesNotExist`、
`lockedAccountsAlsoPerformThePasswordComparison`(以比對次數斷言,不依賴計時)。

> 仍保留的取捨:鎖定中的帳號回 `Account temporarily locked`,與一般失敗訊息不同,
> 因此攻擊者可用「先打 10 次錯密碼、看是否轉為 locked」來確認帳號存在。
> 保留的理由是:改成一律回同一句話會讓被鎖住的真實使用者完全無法理解自己為何登不進去;
> 而這條列舉路徑每個候選 email 要花 10 次請求,且本身受 IP 限流與帳號鎖定雙重壓制。

### 決策 18:輪替的「消耗舊枚」與「持久化新枚」必須同一交易

`RefreshTokenRotator.rotate` 消耗舊枚後提交,新枚卻延到 `SessionIssuer` 才寫。中間任何失敗都會讓
**舊枚已作廢、新枚不存在**——使用者被無聲登出,該 family 也沒有可用的後繼。

**決策**:新枚在 rotator 的同一交易內持久化;`SessionIssuer.complete` 拆成 `issueNewSession`
(登入:建立 + 持久化 + 簽章)與 `resume`(輪替:只簽章,不再寫入)。
回歸鎖:`AuthServiceTest.rotationPersistsTheReplacementInTheSameStepThatConsumesTheOldToken`。

### 決策 19:API key 雜湊以常數時間比對

`KeyHash.equals` 走 `String.equals`,會在第一個相異字元短路。實際可利用性很低
(攻擊者只控制原文,無法針對 SHA-256 摘要前綴逐位元試探),但「比對憑證用常數時間」
不該有例外。改用 `MessageDigest.isEqual`(`KeyHash.matches`)。

---

## 不變事項

- `TlpSpecifications` / `Visibility` / `Indicator.canBeRedistributedTo`:**零修改**
- 錯誤碼:未新增任何一個(`UNAUTHENTICATED` / `TOKEN_EXPIRED` / `FORBIDDEN` / `PLAN_LIMIT_EXCEEDED`
  在 Phase 9 即已定義為跨里程碑契約)
- 跨租戶一律 404:`CrossTenantIsolationTest` 以參數化涵蓋每一個 tenant-scoped 端點
- Flyway 版本號依 `04 §4.7` 的區段設計(V1–V19 = M1、V20–V29 = M2),中間跳號是刻意的
