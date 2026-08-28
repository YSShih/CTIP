# ADR 0015 — 先行清掉後續 phase 會踩到的已知缺口

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:[ADR 0011](0011-m1-review-fixes.md) 延後表與 [ADR 0013](0013-phase13-audit-fixes.md)
  「已知並刻意不在本次處理」兩份清單;`06 §6.2` 版本表
- **背景**:使用者指示把「之後的 phase 會遇到的問題」先修掉。本 ADR 記錄哪些修了、怎麼修,
  以及哪些**刻意仍不修**與其理由。

---

## 修正 1:`/stats/sources` 的筆數必須經可見度過濾

`StatsAdapter.sources()` 直接對 `indicator_sources` 全表 `GROUP BY source_id`,不看可見度,
而 `summary()` 有過濾。M1 的資料全是 public 情資,所以無實害——
但 **Phase 14 的手動提交上線後,租戶私有(含 `INTERNAL_ONLY`)情資的提交量會即時出現在
匿名可讀的公開統計裡**。`plans` 的配額、客戶的提交節奏都能從這裡讀出來。

**修法**:`StatsPort.sources(Visibility)`,查詢以 `IndicatorEntity` 為 root 再 `join("sources")`,
就能原封不動重用 `TlpSpecifications`(§1.11 唯一一套過濾邏輯,不寫 SQL 複本)。
`StatsIntegrationTest` 的期望值 SQL 同步加上可見度條件,並新增一條迴歸鎖:
植入一筆他租戶的 AMBER 情資後,匿名看到的來源筆數不得改變(含「樣本真的寫進去了」的空轉防護)。

## 修正 2:`sourceId` 查詢參數不得成為還原被遮蔽來源歸屬的 oracle

`RedistributionFilter.visibleSourceRecords`(§7.9 規則 5)對非擁有租戶遮蔽
`DERIVED_ONLY` 與 `INTERNAL_ONLY` 的來源明細,但 `IndicatorFilterSpecs.reportedBySource`
比對**任何政策**的來源記錄。於是「輸出遮蔽了來源明細,查詢卻能用該來源過濾」——
逐一試 `?sourceId=` 就能把被遮蔽的歸屬完整還原。

**修法**:把同一條揭露規則搬進查詢述詞。命中條件加上
`viewer 是擁有租戶(非 public) OR 政策 ∈ {PUBLIC_REDISTRIBUTABLE, ATTRIBUTION_REQUIRED}`。
擁有租戶對自家來源的過濾能力不變(§13.7 的搜尋需求未被削掉),匿名對 `DERIVED_ONLY` 來源
的過濾則回空集合。

這是 §7.9 規則 5 與 §13.7 搜尋欄位的交互,規格兩處都沒有明講;依 §0.4 安全性優先取此解。

**迴歸鎖**:`IocSearchIntegrationTest` 以種子中既有的 `MOCK_ABUSEIPDB`(`DERIVED_ONLY`)
與 `MOCK_OPENPHISH`(`ATTRIBUTION_REQUIRED`)一正一反驗證。

## 修正 3:`InMemoryRateLimiter` 的 bucket map 會逐出

鍵含 client IP(IPv6 取 `/64`),不逐出就是一條隨流量成長、永不回收的記憶體路徑。

**修法**:超過 10,000 個 bucket 後才觸發清掃,且以 10 分鐘節流(清掃是 O(n),不能每個請求都掃);
只移除「已回滿**且**閒置超過一天」的項目。回滿代表它此刻不限制任何人,移除後重建的 bucket
狀態相同——**逐出不會放寬任何配額**。

## 修正 4:STIX `name` 截斷不得切斷 surrogate pair

`truncate` 直接 `substring(0, 255)`。URL 型 IOC 的 normalized 值可達 2048 char;
若第 255 個 char 恰好是 astral 字元(emoji 等)的高代理,會切出半個 char——無效的 UTF-16,
序列化出去就是壞掉的 JSON 字串。

**修法**:最後保留的 char 是高代理就退一格。

> 第一版我寫成 `offsetByCodePoints(0, codePointCount(0, 255))`,那對「尾端是未配對高代理」
> 的情況**不會退格**,等於沒修。是靠「把修正還原、確認測試轉紅」才發現的——
> 新增的迴歸測試已驗證能判別新舊行為。

## 修正 5:filter 逸出的例外也要回統一錯誤結構

filter 在 MVC 之前執行,`@RestControllerAdvice` 接不到;逸出的例外會落到 Boot 預設的
`/error`,回出一個**沒有 `code` 與 `traceId`** 的結構。§9.4 的統一錯誤契約在這條路徑上是破的,
而以 `code` 分支的 client 會在這裡壞掉。

**修法**:`TraceIdFilter`(`HIGHEST_PRECEDENCE`,已在最外層且 MDC 已設好)加一層
try/catch,以既有的 `FilterErrorWriter` 寫出 `INTERNAL_ERROR`。
**回應已 committed 時原樣往上拋**——那時再寫任何東西只會產生半截的 body。

ADR 0011 當初評為「收益低」而延後,理由是需要覆寫 `ErrorController`。
實際上 `FilterErrorWriter` 在 Phase 13 就已存在,成本只剩一層 try/catch。

**迴歸鎖**:`FilterErrorContractTest` 注入一個會丟例外的 filter(排在 TraceIdFilter 之後),
驗證回應仍具 `code`/`traceId`/`path`;另有對照組確認正常路徑不受影響。

## 修正 6:`06 §6.2` 版本表補上三項實作已在使用的相依

| 項目 | 用途 | 此前回報 |
|---|---|---|
| JWT(Nimbus JOSE+JWT,隨 Spring Security) | Phase 13 的 HS256 簽發／驗證 | §0.14、ADR 0012 |
| Flyway Maven Plugin | `migrate.sh` | §0.16、ADR 0014 |
| networknt json-schema-validator(test scope) | STIX schema 離線驗證 | ADR 0005 |

三者**皆不新增版本 property**,版本仍由 Spring Boot BOM 或既有 property 決定,不改變任何 pin。
規則 17 已回報四次,本次依使用者指示寫入。

---

## 刻意仍不修(與理由)

| 項目 | 為什麼現在不動 |
|---|---|
| `User.changePassword` 不撤銷 token family | 改密碼端點是 M3,現在加上撤銷是**沒有呼叫端的推測性行為**(規則 16)。M3 實作該端點時必須一併做——已寫進 progress.md |
| `POST /auth/register` 的 409 可枚舉已註冊 email | 沒有 email 驗證管道(M2 無寄信基礎設施)就無法在不破壞註冊流程的前提下消除。受匿名 IP 限流節流,列為已知殘餘風險 |
| `Tenant.suspend()` / `TenantStatus.SUSPENDED` 在認證路徑未被檢查 | 租戶停權的語意(既有資料是否仍可讀?已簽發的 token?)§10 完全未定義。**猜一個語意實作下去比不做更糟** |
| 自助註冊即得 `TENANT_ADMIN`(含 `ioc:submit`) | 這是 ADR 0012 決策 5 的刻意設計;**方案配額才是正確的閘門**。Phase 14 必須確保 `plans.manual_submissions_per_day` 對 FREE 是 0 且真的被檢查 |
| IDNA2003 代 IDNA2008(`ß`→`ss` 可能錯誤合併) | 需要**新增 ICU4J 這個 runtime 相依**,是版本表的實質變更而非補記錄。與上面三項補列的性質不同,應由使用者明確決定 |
| `VITE_API_URL` 進不了 staging/prod bundle(M2-25) | §5.6 的規格層缺陷,兩種修法(Dockerfile build args vs runtime `env.js` + nginx 反代 `/api`)**架構影響不同**,且第二種會連帶要求設 `forward-headers-strategy` 與 `internal-proxies`(否則整個平台共用一個限流桶)。需要規格決策 |
| `MAX_PAGES_PER_RUN` 截斷後 cursor/since 語意矛盾 | 需要定義 `FetchContext` 的 cursor/since 優先序契約;M2 接真實 adapter 前必須決定,現在猜會綁死錯的語意 |
| FilterBar 同頁 back/forward 草稿不同步 | 純前端 UX 取捨,已有註解記錄;非資料正確性問題 |
