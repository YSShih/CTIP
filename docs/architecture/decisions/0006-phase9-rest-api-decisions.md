# ADR 0006 — Phase 9 REST API 實作決策

- 狀態:accepted(2026-08-26,Phase 9)
- 依據:`docs/spec/00-master.md` §0.4「遇到規格模糊時」優先序(安全性最先)

## 1. 再散布過濾的擁有租戶豁免排除 public tenant(安全性缺陷修正)

07 §7.9 作用域修正寫「`viewerTenantId == indicator.ownerTenantId` → 不套用再散布過濾」。
但匿名身分綁定的 viewerTenantId **就是** public tenant(01 §1.11),而 feed 攝取的資料
owner 也是 public tenant——照字面實作,匿名對全部公開情資都算「擁有租戶」,
再散布過濾(規則 3/4/5)對公開輸出**完全失效**,這正是 §7.9 要防的法遵場景。

**決策**:豁免僅限「viewer == owner 且 owner 非 public tenant」。public tenant 無成員,
對 public 資料的任何存取都是公開輸出。三處同一規則:domain `Indicator.canBeRedistributedTo`
(I14)、`TlpSpecifications.ownerOrRedistributable`(query 層)、`RedistributionFilter`(輸出層)。
§7.9 作用域修正的原意(租戶看得到自己提交的資料)不受影響——手動提交歸屬提交者租戶(非 public)。
**建議回寫 07 §7.9**。

## 2. RedistributionFilter 定形於 application/indicator

Phase 8 暫放 `application/stix`;Phase 9 起 IOC 端點也要用,移至 `com.ctip.application.indicator`。
規則 3 委派 domain I14;規則 4(attribution 來源集合)與規則 5(可揭露來源明細集合)在此判定;
DTO 組裝(`IocResponseAssembler`)只消費判定結果,不含政策(§9.5 輸出過濾第 4 步單點)。

## 3. 規則 5 的遮罩粒度

DERIVED_ONLY:摘要欄位(value/type/score/severity/status/tlp/時間欄位)照常回傳
(「可回答此 IP 有風險」),來源明細(`/iocs/{id}/sources`)排除該來源記錄、attribution 不含之。
INTERNAL_ONLY 記錄一律不出現在跨租戶/公開輸出。

## 4. attribution 的 homepage

規則 4 要求「顯示名稱與 homepage」;`sources` 表有 `homepage_url` 但 domain `SourceSnapshot`
原本未承載——本 phase 補上(V4 種子未填,M1 值為 null)。

## 5. 匿名讀取配額以 property 承載

10 §10.6 匿名列:單次分頁上限 50、批次驗證上限 20。plans 表是 M2(Phase 14);
M1 以 `ctip.api.*`(`API_DEFAULT_PAGE_SIZE`/`API_MAX_PAGE_SIZE`/`API_MAX_BATCH_LOOKUP`)承載,
Phase 14 改查 plans。offset 上限 10000 是 §9.3 固定值,以常數實作。

## 6. traceId(§9.4「與日誌可對應」)

M3 才有 OpenTelemetry;M1 以 `TraceIdFilter` 實作:尊重傳入 W3C `traceparent` 的 trace-id 段,
否則產生 32 hex;置於 MDC(`logging.pattern.correlation` 帶進每行日誌),錯誤回應取同一值。
RateLimitFilter 的 429 回應改為同一 ErrorResponse 結構(手工 JSON,filter 在 MVC 之前)。

## 7. lookup 的未命中語意

無法清理/推斷/正規化的值與不可見(跨租戶、TLP)的值一律 `found=false`,不回錯誤——
與「跨租戶一律 404」同一存在性不洩漏原則;批次超限才回 413。

## 8. 實作層面(無規格衝突)

- `IndicatorRepository.findVisible` 擴充 `IndicatorFilter` 參數(status 預設排除 EXPIRED 在
  Specification 層落實);`SearchPort` 同步擴充;`SourceRepository` 增 `findAll`。
- offset 分頁以 EntityManager `setFirstResult/setMaxResults` 實作,與 cursor 同一排序鍵
  `(last_seen DESC, id DESC)`。
- GET /iocs 的 8 個查詢參數以 record(`IocListParams`)建構子繫結(checkstyle ParameterNumber ≤ 5,
  record 豁免);繫結失敗經 `BindException` → 400 INVALID_REQUEST。
- REST 層不得直接依賴 Repository port(ArchUnit 規則 4):新增 `SourceQueryService`。
- `/api/v1/health` 為 liveness 語意(依賴健康走 /actuator/health,compose healthcheck 不變)。
