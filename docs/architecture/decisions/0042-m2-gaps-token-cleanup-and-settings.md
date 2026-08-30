# ADR 0042 — 補上兩項 M2 遺漏:過期 token 清理與 Settings 頁

- 狀態:accepted(2026-08-30,Phase 23 補件;使用者指派)
- 範圍:`ctip-core/application/identity/ExpiredTokenCleanupService`、
  `RefreshTokenRepository`(新增 `revokeExpired`)、`IdentitySchedulers`、
  `application.yml` / compose / 五份 `.env*.example` / [05 §5.4](../../spec/05-environment.md);
  `frontend/src/pages/SettingsPage.tsx`、`features/auth`(changePassword)、`/settings` 路由
- 前一則:[ADR 0041](0041-phase23-cicd-security-docs.md)(其 §9 的第 2、3 項由本則承接)

---

## 背景

兩項標 `[M2]` 的交付物從未實作,自 Phase 21 起連續回報三次
([ADR 0031](0031-phase21-audit-and-retention.md) 末段、[ADR 0041](0041-phase23-cicd-security-docs.md) §9):

1. **`TOKEN_CLEANUP_CRON`**([08 §8.7](../../spec/08-ingestion-sdk.md#排程) 的「過期 token 清理」)
2. **[12 §12.5](../../spec/12-frontend.md#125-頁面) 的 Settings 頁(`/settings`)**——
   `POST /api/v1/auth/change-password` 在 Phase 21 就交付了,但**沒有任何前端入口**

兩者都不在任何 phase 執行單的交付物清單裡,因此不會在任何一次收尾被抓到。使用者指派於本 phase 補齊。

---

## 1. 過期 token 清理:資料庫早就為它留好了位置

`04` 表 15 有兩個只為這個任務存在的設計:

| 設計 | 用途 |
|---|---|
| `revoked_reason` 的 `EXPIRED_CLEANUP` | 這個任務寫入的值 |
| 索引 `ix_rt_gc ON refresh_tokens (expires_at)` | 名字就叫 gc |

**決策**:把 `expires_at` 已過而 `revoked_at IS NULL` 的列標為
`revoked_at = now, revoked_reason = 'EXPIRED_CLEANUP'`。**不刪除列。**

### 為什麼不刪除

`ip` 與 `user_agent` 是個資,移除它們是**資料主體刪除**的職責
([13 §13.4](../../spec/13-platform-ops.md#134-隱私與資料保留));而 §13.4 的六項保留政策
**不含** `refresh_tokens`。清理任務去刪列等於偷偷新增第七項保留政策。

### 這不是安全邊界,是衛生

過期的 token 本來就用不了——不變量 U6 的 `isUsable()` 已經檢查 expiry,
所以少了這個任務**不會**有任何過期 token 能通過認證。它真正的價值是讓
「`revoked_at IS NULL`」這個狀態真的只代表「還能用」:
`findActiveByUser` 一類的查詢否則會隨時間累積無用的列,而 `revoked_reason`
也就答不出「這一枚是怎麼結束的」。這一點寫進了服務的 javadoc——
否則下一個讀到它的人會以為刪掉它會有安全後果。

### 為什麼是批次 UPDATE,不是載入聚合再改

`RefreshToken` 是 `User` 聚合的內部實體,正常路徑一律經聚合。但這是**跨全體使用者的清掃**,
而 `RefreshTokenRepository` 這個 port 存在的理由(ADR 0012 決策 4)正是
「隨 `User` 全量載入不可行」。因此新增 port 方法
`int revokeExpired(Instant now, RevokedReason reason, int batchSize)`,
以 `id IN (SELECT … ORDER BY expires_at LIMIT n)` 分批(同
[`RetentionTasks`](../../../backend/ctip-app/src/main/java/com/ctip/infrastructure/retention/RetentionTasks.java) 的形狀,避免長交易鎖表)。

**領域規則沒有被繞過,而是被翻譯**:「已撤銷不可清除,重複撤銷保留最初原因」
(`RefreshToken.revoke` 的行為)由述詞 `revoked_at IS NULL` 保證。
`TokenCleanupTest.keepsTheOriginalRevocationReason` 與 `isIdempotent` 直接驗這一點——
後者尤其重要:述詞若漏掉 `revoked_at IS NULL`,每次清理都會重寫全表並洗掉所有撤銷原因,
而任務本身照樣回報「成功」。

**撤銷原因由 application 層決定**(`revokeExpired` 收 `RevokedReason` 參數),
不寫死在 adapter 裡:那是政策,不是持久化細節。

---

## 2. Settings 頁:一頁的存在理由是一個端點

**決策**:`/settings` 只掛 `RequireAuth`,不需額外權限——帳號資訊、外觀與變更密碼是每個登入者都有的東西。
內容四塊:帳號(使用者/角色/租戶/權限數)、外觀(主題,寫 `uiSlice`)、
**變更密碼**、以及通往 `/settings/{subscription,api-keys,webhooks}` 的入口(依權限顯示)。

### 成功後就地清掉 session

`POST /auth/change-password` 會撤銷該使用者**全部** refresh token family
([ADR 0015](0015-future-phase-hardening.md) 指定的行為),**包含呼叫端自己這一枚**。
因此前端在成功後 `dispatch(sessionCleared())`,由路由守衛帶回登入頁,
並以 toast 說明「N 個工作階段已登出」。

不這麼做的話,使用者會保留一個**再也輪替不了**的 session:access token 還能用 15 分鐘,
然後在毫無預兆的情況下被踢出。那是同一件事的兩種呈現,而只有一種說了實話。

### 新密碼要求輸入兩次

送出成功等於全裝置登出,**打錯字的代價是被鎖在門外**,而這個錯誤在送出當下
沒有任何伺服器端能攔的機會(伺服器只看得到一個合法的新密碼)。
確認欄不一致時直接 disable 送出鈕。

---

## 影響與回寫

- 新環境變數 `TOKEN_CLEANUP_CRON`(預設 `0 0 2 * * *`):同步 `application.yml`、
  `docker-compose.yml`、五份 `.env*.example` 與 [05 §5.4](../../spec/05-environment.md)
  ——`ConfigSymmetryTest` 會擋不對稱
- `CtipProperties.Scheduler` 多一個 `@NotBlank` 欄位,兩處測試的建構子呼叫一併更新
- 規格回寫:[08 §8.7](../../spec/08-ingestion-sdk.md#排程)、
  [12 §12.5](../../spec/12-frontend.md#125-頁面)、[00 §0.30](../../spec/00-master.md)
- **沒有新增任何 API 端點**,因此 `openapi.json`、`WebCorsConfig.allowedMethods`
  與 §13.5 的稽核對照表都不需要動
