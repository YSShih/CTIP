# ADR 0023 — Phase 14:方案／配額與 IOC 寫入端點

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:`backend/`(plans/subscriptions/import_jobs、QuotaService、IOC 寫入端點)、
  `frontend/`(`/iocs/new`、`/iocs/import`、`/settings/subscription`)、
  `docs/spec/{04,05,09,10}`、`environment/`(compose 與五份 `.env` 樣板)
- **背景**:phase-14 執行單。[ADR 0019](0019-phase14-16-spec-resolutions.md) 已把「照字面實作
  會做不出來」的規格缺口定調;本 ADR 記錄**實作當下才浮現**的決策與偏離。

---

## 1. `0` = 停用時回 403,而不是 429

§9.7 的三種語意把「手動提交／日」歸在**時間窗內的計數 → 429**。但 FREE 的
`max_manual_submissions_per_day` 是 `0`,而 ADR 0019 已定調 `0` = **停用**(不是「用完」)。

回 `429 RATE_LIMIT_EXCEEDED` + `Retry-After` 等於告訴 client「等一下再試就會過」,
而那**永遠不會發生**——配額不會隨視窗恢復,要解除只能升級方案,那正是
`403 PLAN_LIMIT_EXCEEDED` 的定義(「不會自己恢復,等待無用」)。

**定調**:同一個配額欄位依值決定語意——`0` → 403;正整數在視窗內用罄 → 429。
兩者都有測試(`QuotaEnforcementTest.freePlanCannotSubmitAtAll` /
`dailySubmissionQuotaExhaustionIsRateLimited`)。

## 2. 發布(`ioc:publish`)必須同時放寬再散布政策,否則仍然沒有作用

ADR 0019 第 2 節定調 `ioc:publish` 是**擁有權轉移**(owner → public tenant)。
照那樣實作之後,**實測仍然沒有任何人看得到**那筆 IOC:

- §9.7 規定手動提交的來源記錄 `redistribution_policy = INTERNAL_ONLY`
- 不變量 I14:全來源皆 `INTERNAL_ONLY` 者不得出現在**非擁有租戶**的任何回應中
- 擁有租戶豁免刻意不適用於 public tenant(否則公開輸出的再散布過濾會全面失效)

三者相加,發布後的 IOC 對所有人都不可見——`ioc:publish` 又一次成為沒有作用的權限
(規則 16 禁止的 placeholder)。

**定調**:發布時該筆 MANUAL 來源記錄記為 `PUBLIC_REDISTRIBUTABLE`。
「發布」這個動作本身就是租戶對再散布的授權;§9.7 的 `INTERNAL_ONLY` 指的是**私有提交**。
`ManualSubmissionTest.publishTransfersOwnershipToThePublicTenant` 以「匿名讀得到」驗證,
`ManualSubmissionServiceTest` 另以 `eligibleForBloom()` 釘住(Phase 15 的 public bloom 成員條件)。

## 3. 匯入的筆數會扣減每日提交配額

§10.6 有兩個獨立的欄位:`max_manual_submissions_per_day` 與 `max_import_rows_per_file`。
若匯入不計入每日配額,則每日上限可被「改用匯入端點」完全繞過——PREMIUM 的每日 1,000 筆
會變成「每天無限次 × 每檔 10,000 筆」。依 §0.4 的優先序(安全性優先)取安全側。

**定調**:匯入的每一筆都扣減 `max_manual_submissions_per_day`;越界的記錄逐筆寫入
`ingestion_rejections`(reason `QUOTA_EXCEEDED`),請求本身仍回 202
——這正是 ADR 0019 第 1 節「批次處理中途跨越每日配額」描述的行為。
**副作用**:PREMIUM 一次匯入 10,000 筆,當日只有 1,000 筆會被接受。這是 §10.6 兩個欄位
本身的數值關係,不是實作缺陷;需要調整時以 `CTIP_PLAN_OVERRIDES` 或管理端點(M3)改配額。

## 4. `X-RateLimit-Limit` 的「無上限」以字面值 `unlimited` 表達

§10.7 要求三個標頭在**所有**回應都要帶,而 ENTERPRISE 的 `requests_per_day` 是 `null`
(依合約)。印 `-1` 或某個巨大數字都會被 client 當成真實配額。故以 `unlimited` 表達;
數值型的方案一律照舊印數字,格式不變(`RateLimitHeaders`)。

## 5. 移除四個 property,而不是「放寬型別」

phase-14 交付物寫「**放寬三處配額型別**以承載種子表的 `0` 與 `null`」。實作時發現:那三處
(`ApiKeySettings.maxPerTenant`、`StixExportSettings.maxObjects`、`CtipProperties.Api` 的
`maxPageSize`/`maxBatchLookup`)在配額改讀 `plans` 表之後**完全沒有呼叫端**。
留著等於留下第二個真相來源,而 phase-14 的「不得做的事」第一條就是
「不得 hard-code 任何配額數值(一律讀 plans 表)」。

**定調**:直接移除,連同 `STIX_EXPORT_MAX_OBJECTS`、`API_MAX_PAGE_SIZE`、
`API_MAX_BATCH_LOOKUP`、`RATE_LIMIT_ANONYMOUS_PER_MINUTE`、`RATE_LIMIT_ANONYMOUS_PER_DAY`
五個環境變數(compose、五份樣板、`05 §5.4` 同步)。`API_DEFAULT_PAGE_SIZE` 保留
——`plans` 表沒有「預設分頁大小」這一格,它不是配額。
依規則 17 回報:這是對執行單字面的偏離,方向是更嚴格(消滅第二真相來源)。

## 6. `V29` 同時種入方案與 `subscription:read` 權限

phase-14 只配到 `V28`–`V29` 兩個版本號,而本 phase 有兩組種子要寫(四個方案、新權限)。
另開 `V30` 會推移 Phase 15/18/20/21 已指派的號碼(ADR 0014)。
**定調**:合併於 `V29__seed_plans_and_permissions.sql`,檔名據實反映內容;
`04 §4.7` 的檔名同步更新。權限的三處同步(規格 §10.3 清單與矩陣、seed、`RbacMatrix` 常數)
由 `RbacMatrixTest` 逐格比對,矩陣 105 → **110 格**。

## 7. CSV 由 adapter 解、STIX bundle 由 ctip-app 解

§8.3 要求 `ManualSubmissionAdapter.fetch()` 從 `FetchContext.config` 取出提交批次。
CSV 只需要 SDK 型別,`ctip-adapters` 可以自足;但 STIX bundle 需要 JSON 解析器,
而該模組刻意不依賴任何 JSON 函式庫(「只認識 SDK 契約」),`ctip-core` 也沒有 JSON 相依。

**定調**:新增 port `ImportPayloadParserPort`(core)。CSV 分支呼叫 adapter 的
`fetch()`(§8.3 的字面實作),STIX 分支在 `ctip-app` 以 Jackson 解析後產生同一種
`RawThreatRecord`。兩者之後走**完全相同的 pipeline**——§8.3 真正要保證的
「不需要第二套資料品質邏輯」不受影響。

STIX pattern 的反解新增 `StixPatternParser`(與 `StixPatternBuilder` 互為反向,
只認 §7.8.3 的六個固定模板);認不得的 pattern 回 empty,由 pipeline 逐筆拒絕
——猜測式解析會把「看起來像 IOC 的字串」寫進資料庫。

## 8. `indicator_sources.raw_payload` 改為真的會寫入

§9.7 的提交本文有 `note`、誤判回報有 `reason`/`evidenceUrl`,而 `04` 表 5 沒有為它們開欄位。
現成的 `raw_payload`(JSONB)自 M1 起就存在、甚至有 GC 索引
(`ix_is_payload_gc ... WHERE raw_payload IS NOT NULL`),但**從來沒有任何程式碼寫入它**。

**定調**:`IndicatorSourceSnapshot` 增加 `rawPayload`,由 `MergeStage` 從
`RawThreatRecord.rawPayload` 帶入,持久化層序列化為 JSONB。它是**只寫不讀**的:
聚合不解讀它(解讀在 `ParseStage`,輸入來自 `RawThreatRecord`),因此重建時為空 Map,
而持久化層**只在新快照帶有內容時覆寫**——否則重建後的合併會把既有 payload 抹成 null。

## 9. 匯入 job 的非同步邊界

- `ImportService.submit` **刻意不加 `@Transactional`**:`ImportJobRunner.run` 是 `@Async`,
  背景執行緒會立刻讀寫那一列 job。整個 submit 包在一個交易裡的話,PENDING 列在方法回傳前
  尚未提交,背景執行緒查不到它、於是以同一個 id 再 INSERT 一次,外層交易提交時直接撞主鍵。
- 執行緒池**有界** + `CallerRunsPolicy`:佇列滿了就由呼叫端執行緒自己跑(退化為同步、變慢),
  而不是無限堆積——每個 job 還帶著整份已解碼的記錄清單。
- 配額必須**跨批遞減**:`BatchState` 是一批一個,沿用同一個 `IngestionRun`
  會讓每一批都重新拿到完整餘額,整個上限形同虛設。

## 10. 誤判回報建立來源記錄時沿用 Indicator 現值的 TLP

`strictestTlp` 會把新記錄一起算進去。若新建的 MANUAL 記錄用來源預設(AMBER),
一筆自家的 CLEAR IOC 只要被回報一次就會變成 AMBER——回報誤判不該改變分級。

---

## 給後續 phase 的影響

| 事項 | 影響 |
|---|---|
| `RateLimiterPort` 已改為 `tryConsume(key, tokens, QuotaLimit)` + `peek` | Phase 17 的 Redis 實作直接對這個介面實作;維度 1–3 仍未做 |
| `RateLimitFilter` 仍只有維度 4(匿名 IP)且**必須留在認證之前** | Phase 17 加維度 1–3 時不得把它一起移到認證之後(ADR 0012 決策 16) |
| `plans.tenant_bloom_capacity` 已可讀 | Phase 15 的 tenant bloom 直接查表 |
| 發布的 IOC 來源記錄為 `PUBLIC_REDISTRIBUTABLE` | Phase 15 的 `eligibleForBloom()` 因此對它成立(ADR 0019「沒有動的」那一項) |
| `plans.min_sync_interval_seconds` 已可讀,但**沒有任何欄位記錄租戶上次同步時間** | Phase 16 仍須自行決定存放位置(ADR 0019 第 8 節) |
| `import_jobs` 已有 `resource_type = import_job` 可稽核 | Phase 21 的稽核直接用 |
