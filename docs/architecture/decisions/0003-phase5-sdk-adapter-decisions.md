# ADR 0003 — Phase 5:SDK / Adapter / 韌性 / 來源健康的實作決策

- 狀態:已採納(2026-08-25)
- 依據:`00-master.md` §0.4 模糊時優先序(安全性 > 可維護性 > 可測試性 …),決策須立 ADR 並於回報指出

## 1. `AdapterRegistryPort`(core)+ `AdapterRegistry`(app)實作它

**問題**:結構契約(01 §1.4)把 `SourceSyncService` 放在 `ctip-core/application/source`、
`AdapterRegistry` 放在 `ctip-app/infrastructure/source`;模組依賴方向 core ↛ app,
core 的 service 無法直接持有 app 的類別。

**決策**:於 `application/port/AdapterRegistryPort` 新增單方法 port
(`Optional<ThreatSourceAdapter> find(SourceType)`),`AdapterRegistry` 除 08 §8.1 的
逐字實作外僅追加 `implements AdapterRegistryPort`。這是標準六角形結構:plugin 屬
infrastructure,core 經 port 取用;§8.1 的「不寫 Factory、Spring 集合注入、重複型別
啟動即炸」語意完整保留。

## 2. `ctip-adapters` 維持零 Spring 相依;bean 與韌性裝配集中在 app 的 `AdaptersConfig`

02 §2.5 的賣點是「第三方只需依賴 ctip-sdk」;讓 mock adapter 掛 `@Component` 會把
Spring 拖進 adapters 模組。改由 `AdaptersConfig` 宣告 bean,並在註冊前以
`FetchResilience.decorate(...)` 統一套用 §8.5 的 retry / circuit breaker / bulkhead
(以組態方式套用於所有 adapter,mock 也一致對待——韌性層因此是 M1 起就上線的活代碼,
非佔位)。timeout(connect 5s / read 30s)屬 HTTP 層,由 `HttpFeedClients` 對
未來的真實 adapter(08 §8.4,M2)提供;已有單元測試鎖定契約值。

## 3. Mock 確定性:固定手寫資料集,完全不用亂數

08 §8.3 要求「固定 seed」;固定資料集是比 seeded Random 更強的確定性(不依賴
JDK Random 演算法的跨版本穩定性),且髒資料/重疊 IOC 可精確對應拒絕規則與合併測試。
`SharedIocs` 列出 11 個跨來源重疊 IOC(≥ 10),含 confidence 差異大、severity 不同、
TLP 不同(AbuseIPDB=GREEN vs AlienVault=CLEAR)與一筆撤回。

## 4. 「來源標為 RETRACTED」以 STIX `revoked=true` 表達

`RawThreatRecord`(§8.1 逐字)沒有撤回欄位。MockAlienVault 是「STIX 風格 payload」
來源,STIX 2.1 Indicator 本就有 `revoked` 布林;約定 `rawPayload["revoked"] == true`
→ ingestion 映射為該來源記錄 `RETRACTED`(Phase 6 的 pipeline 實作此映射)。

## 5. `Source` 聚合補 `nextCursor` / `totalRecordsIngested`

`sources` 表自 V2 就有這兩欄,Phase 4 的聚合未載入。同步流程需要續抓游標與累計筆數,
納入聚合(`recordSuccess` 累計、`advanceCursor` 前進)而非繞過 domain 直寫 entity。
`SourceSnapshot` 追加兩個尾端欄位,mapper 同步更新。

## 6. `EventPublisherPort` 實作提前至 Phase 5

`SourceHealthService` 發佈 SourceDegraded/Failed/Recovered,沒有實作 bean 則 context
起不來。`SpringEventPublisherAdapter` 依 §8.2:補齊信封(eventId/occurredAt/traceId,
經 ClockPort/IdGeneratorPort)、交易內註冊 AFTER_COMMIT 發佈、無交易立即發佈;
M3 加 Kafka listener 時發佈端不改。

## 7. 其他解讀

- **Retry「3 次」**= 3 次重試(間隔 1s/2s/4s + jitter)→ Resilience4j `maxAttempts = 4`。
- **`FetchContext.config` 在 M1 一律 `Map.of()`**:S6 規定 `sources.config` 只存環境變數
  名稱,解析憑證屬真實外部來源(M2)的範圍;mock 皆 `requiresCredentials = false`。
- **mock 髒資料覆蓋 §7.3 八種 reason 中的七種**;`QUOTA_EXCEEDED` 屬手動提交/匯入
  (Phase 14),無法由 feed 資料觸發,Phase 6 以拒絕規則單元測試覆蓋該分支。
- **單輪分頁上限 `MAX_PAGES_PER_RUN = 1000`**:防止行為異常的 adapter(hasMore 永真)
  造成無界迴圈;中斷時游標已保存,下輪續抓。
