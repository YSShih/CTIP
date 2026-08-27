# ADR 0011 — M1 總複查(Phase 1–12 re-review)的缺陷修正與延後決策

- **狀態**:accepted
- **日期**:2026-08-27(M1 閘門之後、Phase 13 之前;使用者指示重新 review Phase 1–12)
- **背景**:以四個獨立視角(安全/TLP、攝取/合併、STIX/API、前端/環境)對 M1 全部產出做
  對照規格的複查。發現一項破壞 §7.5 撤回語意的實質缺陷、一項規格明文要求但實作缺漏的
  守門,與多項防禦性缺口。本 ADR 記錄修正決策與刻意延後的項目。

## 修正 1:同來源 UPSERT 不得沖掉撤回(`IndicatorSource.mergeReport`)

### 問題

`mergeReport` 無條件把來源記錄 status 設回 `ACTIVE`,完全忽略新回報的 status。
`MergeStage` 把 `revoked=true` 映射成 `RETRACTED` 的語意只在「跨來源新增記錄」路徑生效;
同來源重同步(全量重抓、失敗重試皆會發生)會把 `RETRACTED` 翻回 `ACTIVE`——
**撤回被撤回它的同一筆資料復活**,若來源 reputation ≥ 80,indicator 會從 `REVOKED`
翻回 `ACTIVE`,直接違反 §7.5 status 判定規則 1。

### 決策

UPSERT 的 status 規則:

| 既有 \ 新回報 | `ACTIVE` | `RETRACTED` |
|---|---|---|
| `ACTIVE` | `ACTIVE` | `RETRACTED` |
| `RETRACTED` | **維持 `RETRACTED`** | `RETRACTED` |
| `FALSE_POSITIVE` | **維持 `FALSE_POSITIVE`** | `RETRACTED` |
| `EXPIRED` | `ACTIVE`(新觀測復活) | `RETRACTED` |

理由:`RETRACTED` 對齊 STIX 2.1 `revoked` 的單向性(revoked 物件不可 un-revoke);
`FALSE_POSITIVE` 是使用者斷言,不得被例行同步靜默清除;`EXPIRED` 是時間性狀態,
新觀測理當復活。規格 §7.5 未明文定義 UPSERT status,依 §0.4 優先序(安全性最先)採保守語意。
已回寫 07 §7.5。

### 測試

`IndicatorTest`(撤回生效、撤回不被 ACTIVE 復活、EXPIRED 復活、FALSE_POSITIVE 不復活)、
`IngestionEndToEndTest` Order 5(重同步後 `shared-phish-2` 的 AlienVault 記錄維持 `RETRACTED`)。

## 修正 2:TLP:RED 攝取拒收守門(§7.7 明文要求,實作缺漏)

§7.7:「`RED` 不進入平台。ingestion 階段遇到來源標記 `RED` 的資料一律拒絕
(`MALFORMED_VALUE`,detail = "TLP:RED not accepted")」。實作只有 `TlpRedAbsenceTest`
驗「目前 DB 無 RED」(狀態),沒有拒收行為。補在 `ValidateStage` 最前
(M1 的 TLP 唯一來源是 `sources.default_tlp` 快照)。測試:`RejectionRuleTest.redSourceTlpIsRejected`。

## 修正 3:儲存長度上限守門(防 flush 期整批 rollback)

`ValidateStage` 只驗 cleaned 長度,落庫的是 raw(V5 欄位 VARCHAR(2048));raw 帶零寬字元
可通過驗證但在 JPA flush 期炸掉**整批交易(含拒絕記錄)**。同理 URL 正規化補「/」可使
normalized 比 cleaned 長 1。補兩道 `LENGTH_EXCEEDED`:raw > 2048(Validate)、
normalized > 2048(Normalize)。規格未明文,依「單筆失敗不 rollback 整批」的既有契約推導。

## 修正 4:cursor 內部編碼保留奈秒精度

`Cursor.encode()` 用 `toEpochMilli()`,而 `last_seen` 是 TIMESTAMPTZ(微秒)。頁界截斷到
毫秒會使下一頁 keyset 條件漏掉同毫秒內的資料列(list 分頁與 STIX bundle 內部翻頁皆中)。
M1 的 mock 資料是秒精度所以未觸發;M2 真實 feed 必踩。內部格式改 `epochSecond.nano:id`
(對外 CursorCodec 本就是 ISO-8601 全精度,格式不變)。

## 修正 5:限流三項 hardening(`RateLimitFilter`)

1. §10.7「依序檢查,任一超限即拒絕」:minute 超限的請求不再消耗 day 配額
   (原實作會讓被 429 的猛打流量在十幾分鐘內燒光整個 IP 的 1000/day)。
2. 429 手工 JSON 的 `path` 是 client 可控值,補最小 JSON 跳脫(不押注 Tomcat 對
   request line 的過濾行為);`RateLimitTest` 補 429 body 七欄位斷言。
3. `/actuator` 豁免以未正規化 URI 前綴判斷,URI 含 `..` 者不再豁免(路徑穿越防禦)。

## 修正 6:trend 統計的時區錯位(`StatsAdapter`)

`date_trunc('day', last_seen)` 對 timestamptz 依 **Postgres session TimeZone** 切日
(PGJDBC 會把 JVM 時區帶進連線),Java 端卻以 UTC 對桶——非 UTC 環境(本機 Asia/Taipei)
會把 UTC 16:00–24:00 的資料列切到鄰日、掉出 7 日窗,`/stats/summary` 的 trend 少計。
CI(UTC)永遠測不到;本機因種子資料是 now() 相對值,是否踩中取決於跑測試的時刻
(時刻依賴的 flake)。改為 `to_char(timezone('UTC', last_seen), 'YYYY-MM-DD')` 分組,
讀回字串直接 `LocalDate.parse`,徹底移除兩端時區假設;`StatsIntegrationTest` 期望值 SQL
同時補上再散布 EXISTS 條件(原缺,靠新 SecurityTest fixture 暴露)。

## 修正 7:其餘防禦性修正

- **CORS**:origins 解析 trim + 濾空(「a.com, b.com」慣用寫法第二項原本靜默失效);
  mvp/dev 樣板 `CORS_ALLOWED_ORIGINS` 補列 `http://127.0.0.1:5173`(up.sh 印的是
  127.0.0.1,與原白名單 localhost 不一致,照指示開頁會全數被瀏覽器 CORS 擋下)。
- **空 bundle**:OASIS bundle schema 的 `objects` 為 minItems 1;零筆匯出改為省略
  `objects` 屬性(`StixBundleWriter`)。
- **allowlist**:`ctip.data-quality.domain-allowlist` 項目套用與 feed 值相同的 DOMAIN
  正規化(大小寫/尾點/空白差異原本使比對靜默失效)。
- **前端**:attribution `homepage` 僅 http/https 才渲染為連結(來源登錄資料屬半信任面,
  阻擋 `javascript:`);IOC 詳情頁的 sources 查詢失敗改為顯示錯誤 + 重試,不再靜默留空。

## 延後決策(記錄於 progress.md「M2 前置」;皆為 M1 實際不可達或需規格層決定)

| 項目 | 原因 |
|---|---|
| staging/prod 前端 `VITE_API_URL` 進不了 bundle(Vite 是 build 期變數,compose 只在執行期塞給 nginx;§5.6 規格自身缺陷) | 需回寫規格(Dockerfile build args 或 runtime env.js 注入 + nginx `/api` 反代);M1 只跑 mvp/dev(Vite dev server 執行期讀 env),M2-25 `up.sh staging` 前必修 |
| `MAX_PAGES_PER_RUN` 截斷後 cursor 與 since 語意矛盾(續抓時 since 前進使 offset cursor 失效) | 需定義 `FetchContext` 的 cursor/since 優先序契約;M1 mock 資料集遠小於 1000 頁上限,不可達 |
| `sourceId` 查詢參數側信道(可探測 INTERNAL_ONLY/DERIVED_ONLY 來源與 IOC 的關聯) | 需釐清 §7.9 規則 3/5 與 §13.7 搜尋欄位的交互(owner 過濾自家 INTERNAL_ONLY 來源是合法需求);M1 只啟用一個 ATTRIBUTION_REQUIRED 來源,實際暴露有限 |
| `/stats/sources` 筆數不經可見度過濾 | 規格 §9.1 未定口徑;M1 資料全 public 無實害,M2 手動提交(INTERNAL_ONLY)上線前依安全優先序改為過濾 |
| `InMemoryRateLimiter` bucket map 無上限、永不逐出 | M1 綁 127.0.0.1 實害為零;M2 Phase 17 Redis 後端一併處理 |
| servlet filter 內例外回 Boot 預設錯誤結構(無 code/traceId) | 無洩漏(stacktrace never);統一化需 ErrorController 覆寫,收益低 |
| STIX `name` 截斷 255 可能切斷 surrogate pair | 極端 edge case(astral 字元恰跨邊界) |
| FilterBar 同頁 back/forward 草稿不同步;INVALID_CURSOR 文案與重試按鈕不符 | UX 取捨已有註解記錄;非資料正確性問題 |
| IDNA2003 的 `ß`→`ss` 可能與 IDNA2008 註冊網域錯誤合併;§7.2 先剝 ZWJ 的共通規則使 ZWJ 差異成死議題 | ICU4J 不在版本表(規則 6);已於 ADR 0004 回報,本次補充分析 |
