# ADR 0031 — Phase 21:稽核軌跡與資料保留

- **狀態**:accepted
- **日期**:2026-08-30
- **範圍**:`docs/spec/phases/phase-21.md`(Audit Log + 資料保留)
- **相關**:[ADR 0015](0015-future-phase-hardening.md)(改密碼撤銷 token family 的 M3 責任)、
  [ADR 0021](0021-phase20-23-spec-resolutions.md)(三個 DB 角色、六項保留任務)、
  [ADR 0022](0022-orphan-deliverables.md)(Admin Panel 與 `POST /auth/change-password` 歸位本 phase)

本 phase 的規格本身相當完整(§13.5 的觸發點對照表是全書最明確的一張表),
以下記錄的是**照字面實作會出錯**的地方,以及規格未定義而必須決定的選擇。

---

## 1. 稽核的兩個消費端:filter 與 event listener(而非改動業務服務)

§13.5 的觸發點對照表把 26 種行為分別指到 `AuthService.login`、`ApiKeyService.issue`、
`SourceSyncService` 等**具體方法**上。照字面做,等於在十幾個 application service 的建構子上
各加一個 `AuditPort` 參數——其中 `AuthService` 與 `RefreshTokenRotator` 的建構子**已經是 5 個參數**
(checkstyle 上限,01 §1.8 規則 3),加上去就違規。

**決定**:稽核在 `ctip-app` 以兩個橫切消費端實作,業務服務一行都不改:

| 消費端 | 行為數 | 依據 |
|---|---|---|
| `AuditAccessFilter`(security chain 尾端) | 17 種以請求為觸發點的行為 | 對照表的 `API_ACCESS` 本來就指定「filter chain 尾端」;其餘 16 種都是「某支端點被呼叫」 |
| `AuditEventListener`(`@EventListener`) | 9 種以 domain event 為觸發點的行為 | 這些事件早已存在且已被 Kafka 轉發(Phase 20) |

這與 §13.1「發佈端程式碼永不修改」是同一個原則;副作用是**稽核寫入的失敗在結構上不可能
傳回業務路徑**(規則 3),而不是靠每個呼叫端記得包 try/catch。

`AuditEventListener` 接的是**程序內**的 `DomainEventEnvelope`,不是
`ctip.audit.events.v1` 的 Kafka 消費端:mvp／dev 根本沒有 broker,稽核不能只在 staging/prod 才寫得出來。

兩件 filter 看不到的事以 request attribute(`AuditSignals`)由 handler 交出:
登入成功後的**行為者**(`/auth/*` 是匿名端點),與 `GET /iocs` 的**回應筆數**
(`IOC_DOWNLOAD` 的判準是「回應筆數 > 單頁上限的一半」)。

## 2. 清理角色不可能「完全沒有 SELECT」

§13.5 規則 2 寫「該角色有 DELETE 權限但無 SELECT 業務表之權限」。
**PostgreSQL 對 `DELETE ... WHERE` 與 `UPDATE ... WHERE` 仍要求 WHERE 子句所引用欄位的 SELECT 權限**,
而六項保留任務全部都有 WHERE(保留期)。照字面授權,每一項清理都會 `permission denied`。

**決定**:以**欄位層級**授權(`GRANT SELECT (id, occurred_at) ON audit_logs`)。
清理角色因此讀不到 `action`／`metadata`／`ip` 等稽核內容,只讀得到主鍵與時間欄位——
規則的**目的**(清理角色不得成為讀取稽核內容的第二條路)完全成立,而語句可執行。
批次也因此以 `id IN (SELECT id … LIMIT n)` 表達,不用 `ctid`(系統欄位不在欄位層級授權範圍內)。

已回寫 13 §13.5 規則 2 與 §13.4。

## 3. Bloom artifact 清理沿用既有實作(以應用角色執行)

phase-21 的「不得做的事」寫著「不得以應用角色執行保留清理(用 `ctip_retention`)」。
六項任務中的第六項「Bloom artifact 保留最近 30 份」在 **Phase 15 就已實作**
(`BloomRetentionService`),而它不是一句 SQL:

- 必須避開「仍有存活版本的 dataset 的 full snapshot」——先刪掉它,那條 delta 鏈就永遠重建不了;
- 必須一併刪除檔案系統上的 artifact 檔。

**決定(使用者裁示)**:第六項排程直接呼叫既有的 `BloomRetentionService`,以應用角色連線執行;
不以 SQL 重寫一份。理由:保留策略只留一份實作;重寫的風險是刪錯 full snapshot 使 `/sync/delta` 斷鏈,
而這一項刪的是**平台自己的衍生產物**,不是稽核軌跡——規則 2 的原意(稽核表的刪除權不得落在應用角色上)
並未被繞過(`audit_logs` 的 `REVOKE UPDATE, DELETE` 依然成立)。

已回寫 13 §13.4 的保留政策表。

## 4. `SUBSCRIPTION_CHANGED` 原本永不可達 → 補上管理端點

`Subscription.changePlan` / `cancel` 自 Phase 14 起就存在、`SUBSCRIPTION_CHANGED` 是 §13.5 強制的
26 種行為之一,而 **09 §9.1 沒有任何端點會呼叫它們**——全專案找不到一個生產呼叫端。
`AuditCompletenessTest`(M3-11b)明文要求「沒有永不可達的稽核行為」,所以這不是可以延後的事。

**決定**:新增 `PATCH /api/v1/admin/tenants/{id}/subscription`(`system:admin`),
`planCode` 為 `CANCEL` 時代表取消。04 表 18 早就寫明 M2 的訂閱「由 SYSTEM_ADMIN 手動指派
(provider = MANUAL)」——這支端點就是那條路徑。已回寫 09 §9.1。

## 5. 資料主體的「刪除」不包含稽核軌跡

§13.4 要求提供資料主體查詢與刪除的管理端點,但**沒有定義路徑、方法與權限**,
也沒有說稽核軌跡怎麼辦——而 §13.5 規則 1 說 `audit_logs` 是 append-only 且由 DB 強制。
兩者直接衝突。

**決定**:

| 端點 | 行為 |
|---|---|
| `GET /api/v1/admin/data-subjects/{userId}` | 回報平台持有的個資:使用者列、存活的 refresh token 數、稽核列數與時間範圍(不回內容——那可能牽涉他人的操作) |
| `DELETE /api/v1/admin/data-subjects/{userId}` | 刪除該使用者全部 refresh token(表 15 的 `ip`／`user_agent` 是個資);使用者的可識別欄位以佔位值取代並停權 |

`audit_logs` **不在刪除範圍內**:刪除權在此讓位給「網路與資訊安全」的正當利益,
並以 `AUDIT_RETENTION_DAYS`(180 天)的保留上限收斂;刪除後留在稽核列上的只有 `actor_id`
這個化名識別碼。回應會明講保留了幾列。理由與法律基礎寫在 `docs/deployment/privacy.md`。

以 `userId` 而不是 email／IP 定位:路徑會進反向代理與存取日誌,個資不得出現在 URL。

## 6. 改密碼:撤銷原因沿用 `ADMIN`

ADR 0015 指定「`User.changePassword` 必須一併撤銷該使用者全部 token family」為 M3 責任。
本 phase 交付 `POST /api/v1/auth/change-password` 並實作全撤。

**決定**:撤銷原因用既有的 `RevokedReason.ADMIN`,不新增 `PASSWORD_CHANGED`。
04 表 15 的列舉固定五值且有 DB CHECK,新增一個值等於同時改 schema 與規格,
而「因帳號安全事件而由系統撤銷」正是 `ADMIN` 涵蓋的語意。

沒有為改密碼新增第 27 種稽核行為:§13.5 明訂 26 種,而該端點是寫入操作,
`API_ACCESS` 對它取樣 100%。

## 7. `ck_al_action`:表 27 沒列,但 §4.0 要求

04 表 27 的 DDL 只有三條 constraint,沒有 `action` 的 CHECK;
而 §4.0 通用約定明文「列舉以 VARCHAR + CHECK 對應」。少了它,拼錯的 action 會靜靜寫進一張
**永不更新**的表——沒有任何後續操作會發現。V33 補上 26 值的 CHECK,已回寫表 27。

## 8. 稽核寫入的排空判斷不能看佇列長度

`AuditWriter` 是「有界佇列 + 單一寫入執行緒」。測試要等它排空,而
`getQueue().isEmpty() && getActiveCount() == 0` 在**工作被取出佇列到 activeCount 加一之間**
會同時為真——實測到 `AuditAppendOnlyTest` 因此讀到空表。改以自己的 pending 計數判斷。

## 9. `StixProjectionStage` 抽出 `StixProjectionFactory`

`POST /admin/stix/rebuild` 要重算的投影組(indicator + 每個來源記錄的 observed-data +
每個來源的 identity)與 ingestion stage 8 完全相同。若各算一份,兩條路徑產生的 id 或
`created` 一旦漂移,重建就會製造重複的 STIX 物件。抽成 `StixProjectionFactory`,兩邊共用。

## 10. `RetentionConnection` 不是 `DataSource` 型別的 bean

Boot 的 `DataSourceAutoConfiguration` 帶 `@ConditionalOnMissingBean(DataSource.class)`:
多宣告一個 `DataSource` bean 會讓**主**資料源整個不建立,應用起不來。
清理連線因此包在 `RetentionConnection`(非 `DataSource` 型別)裡。

## 11. `OpenApiCompletenessTest` 的「POST 必有 request schema」

原規則是「method 是 post 就必須有 `application/json` 的 request schema」。
§9.1 的管理端點裡有兩支是**無本文的動作端點**(`POST /admin/stix/rebuild`、
`POST /admin/sources/{id}/sync`),照字面判會被判成缺文件——而正確的做法不是替它們捏一個
空的請求物件(執行規則 16、18)。規則改為「**宣告了 requestBody** 就必須有 schema」;
springdoc 只在 handler 真的收 `@RequestBody` 時才產生 requestBody,兩者等價。

## 12. 前端新增 `features/admin/`

12 §12.2 的目錄樹列了 `audit/` 但沒有 `admin/`,而 §12.5 的頁面表有 Admin Panel(`/admin`)。
新增 `features/admin/` 並同步 `eslint.config.js` 的 `FEATURES` 清單(F1 zones)與 §12.2。

`api/client.ts` 補三個包裝:無 body 的 POST(動作端點)、帶 body 且有回應的 PATCH、
有回應本文的 DELETE——既有的三個包裝的型別推導對這些端點會得到 `never`。

---

## 未做而必須回報的事

1. **`TOKEN_CLEANUP_CRON`(08 §8.7 的「過期 token 清理」,標 M2)至今沒有任何實作**。
   它不在 §13.4 的六項保留政策內,也不在 phase-21 的交付物內,因此本 phase 未實作。
   建議指派給 Phase 23(它與 §13.4 的保留任務同性質,但屬 M2 的遺漏)。
2. **09 §9.1「管理」的四支端點在本 phase 之前無人承接**(ADR 0022 沒有指派它們)。
   本 phase 依使用者裁示一併實作,並補上 §9.1 缺少的訂閱變更與資料主體端點。
3. 12 §12.5 的 **Settings 頁(`/settings`,M2)** 仍然不存在——改密碼端點目前沒有前端入口。
   本 phase 只交付 `/audit` 與 `/admin` 兩頁(執行單的範圍),該頁屬 M2 的遺漏,一併回報。
