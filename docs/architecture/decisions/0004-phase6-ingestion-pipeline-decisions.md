# ADR 0004 — Phase 6:Ingestion pipeline / 資料品質 / 排程 / 記憶體限流的實作決策

- 狀態:已採納(2026-08-25)
- 依據:`00-master.md` §0.4 模糊時優先序,決策須立 ADR 並於回報指出

## 1. Pipeline 於 Phase 6 裝配 9 個 stage;StixProjectionStage 留給 Phase 8

phase-06 執行單寫「十個 stage 實作」,但 `StixProjectionStage` 同時是 phase-08 執行單的
明列交付物(含 STIX builder、markings、escaper 整組)。在 Phase 6 放一個空殼 stage 違反
規則 16(不得留 placeholder)。裁決:Phase 6 裝配 Parse→Validate→Normalize→Fingerprint→
Deduplicate→Merge→Score→Persist→PublishEvent 共 9 個;Phase 8 在 Score 之後插入
StixProjectionStage——與 §8.2 的「M2 只改這一個 List.of」是同一種演化方式。
ScoreStage 所需的 `ThreatScorer`/`RuleBasedThreatScorer`(§7.6 全公式)已在本 phase 完整實作
(Phase 7 執行單所列,先行;同 Phase 4 先行完成 IndicatorMergePolicy 的先例)。

## 2. `IngestionPipelineConfig` 以內聯建構取代規格的 10 參數 bean 簽章

§8.2 範例的 `ingestionPipeline(p, v, n, f, d, m, s, x, pe, ev)` 有 10 個參數,違反規格自身的
checkstyle `ParameterNumber ≤ 5`(01 §1.8)。改為單一 @Bean 方法內聯 `new` 出所有 stage,
順序仍以顯式 `List.of(...)` 在一處看完;stage 皆為純類別,單元測試直接建構。

## 3. 拒絕規則的 stage 分工:需要 canonical 值的規則在 NormalizeStage

§8.2 的圖把拒絕規則掛在 Validate(stage 2),但私有/保留 IP 與 allowlist 依 §7.3 必須比對
**正規化後**的值(allowlist「只比對完整正規化值」是明文)。裁決:ValidateStage 做前置檢查
(配額、長度上限、宣告雜湊長度),NormalizeStage 在 canonical 化後緊接執行 MALFORMED_VALUE、
PRIVATE_OR_RESERVED_IP、ALLOWLISTED_DOMAIN。任一 stage 拒絕都寫入 ingestion_rejections,
行為與 §7.3 完全一致,只是判定點不同。

## 4. `QUOTA_EXCEEDED` 的 M1 觸發路徑

八種 reason 是硬性契約,但配額屬手動提交/匯入(Phase 14)。以 `BatchState.remainingQuota`
(null = 無配額;M1 的 feed 一律 null)承載擴充點,`RejectionRuleTest` 以配額歸零的 BatchState
覆蓋該分支。mock feed 覆蓋其餘七種。

## 5. IDN 以 JDK `java.net.IDN`(IDNA2003)實作,非規格點名的 IDNA2008

版本表(06 §6.2)沒有 ICU4J,規則 6 禁止自行加依賴。兩者差異僅在少數字元(ß、ZWJ 等)。
**依規則 17 回報**:若需嚴格 IDNA2008,請在版本表加入 ICU4J 後再改。

## 6. `RATE_LIMIT_BACKEND=redis` 在 M1 暫以記憶體實作 + WARN

RedisRateLimiter 是 Phase 17 交付物。fail-fast 會讓 dev/staging 樣板(預設 redis)完全無法
啟動;fallback 後限流仍然生效,且 M1–M2 為單一實例,語意等價。啟動時 WARN 明示。
另:§10.7 的 `RateLimiterPort.tryConsume(RateLimitKey, tokens) → RateLimitResult` 取代
Phase 4 的簡化簽章 `tryAcquire(String)`(當時即標註 Phase 6 會定形)。

## 7. M1 限流維度:匿名 IP × {minute, day}

- 數值 60/min、1000/day(§10.6 匿名列)以 property 預設值承載
  (`ctip.rate-limit.anonymous-per-*`);§10.6 要求存 plans 表——plans 是 M2 表,Phase 14 移轉。
- endpointClass(read/write/heavy)維度與 API key/user/tenant 維度是 M2(Phase 14/17)範圍。
- `/actuator/*` 排除:它是 compose healthcheck 與探針路徑,限流會使容器永遠 unhealthy。
- IPv6 取 /64 前綴為 key;反向代理的真實 IP 議題依 §10.7 屬部署文件範圍(M1 無代理)。

## 8. `source_sync`「append-only,不更新」的解讀

表 3 同時定義 `result` 預設 `RUNNING` 與「finished_at null 表示仍在執行或異常中斷」——
這隱含列在開始時建立、結束時回寫一次。裁決:start 落 RUNNING 列(REQUIRES_NEW,fetch 前即可見)、
finish 回寫一次終態;終態後不再更新。

## 9. 合併時補入既有來源的信譽(Phase 4 交接事項)

重建後聚合的 reputations 為空,直接 merge 會使既有來源以中性值 50 計權(實測 confidence 55 vs 60)。
`Indicator.mergeFrom` 新增 3 參數 overload(補入 known reputations 再合併),
MergeStage 由 SourceRepository 查出既有來源記錄的信譽傳入。

## 10. 其他

- 非預期 stage 例外映射 `MALFORMED_VALUE` + 例外訊息進 detail(reason 受 DB CHECK 限 8 值),
  單筆失敗不 rollback 整批(§8.2)。
- `RateLimitFilter` 由 `RateLimitConfig` 以 @Bean 建立並注入 primitive,避免
  infrastructure → config 反向依賴(ArchUnit 規則 5 實測會成環)。
- Boot 4 模組化再一例(06 §6.3.6 同型態):MockMvc 測試支援在 `spring-boot-webmvc-test`,
  `@AutoConfigureMockMvc` 套件為 `org.springframework.boot.webmvc.test.autoconfigure`。
- 排程:SOURCE_SYNC_CRON 預設每 5 分鐘「掃描」,每來源是否到期由自身 recommendedInterval
  決定(§8.7 表的語意);IOC_EXPIRY_CRON 每日 03:00、INGESTION_RETRY_CRON 每 15 分。
  整合測試基底把 SCHEDULER_ENABLED 設為 false(§8.7 總開關的用途)。
