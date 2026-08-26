# ADR 0005 — Phase 8:STIX 投影與匯出的實作決策

- 狀態:accepted(2026-08-26,Phase 8)
- 依據:`docs/spec/00-master.md` §0.4「遇到規格模糊時」優先序(安全性 > 可維護性 > 可測試性 > …)

## 決策 1:投影「建構」在 stage 8、「寫出」在批次交易提交後

08 §8.2 把 StixProject 排在 stage 8(Persist 之前),但 `stix_objects.indicator_id` 的 FK 指向
`indicators`(表 8)——新 indicator 在 stage 8 時尚未落庫,同交易先寫 stix 必然 FK 違規;
且 §7.8.6 要求「投影失敗不得使 ingestion 失敗」,同交易內任何 stix 寫入失敗都會污染交易、
使整批 rollback。

**做法**:`StixProjectionStage`(stage 8)只**建構** `StixProjection` 放入 context(映射邏輯所在,
任何錯誤 log 後繼續、不 reject);`IngestionBatchProcessor` 把投影收進 `BatchOutcome`;
新增 `IngestionBatchExecutor` 於批次交易**提交後**經 `StixProjectionWriter` 逐筆寫出
(單筆 try/catch,adapter 自帶交易)。stage 順序與 §8.2 一致,寫出時點是實作細節。
失敗的投影待下次同步重投影(stix_objects 本就可隨時由 domain 重建;M3 提供 rebuild 端點)。

## 決策 2:STIX created/modified 以 ClockPort + UPSERT 保留 created 近似

7.8.2 規定 `created` ← `indicators.created_at`、`modified` ← `indicators.updated_at`,
但兩者由 DB 在 persist 時產生,stage 8 執行當下不存在。**做法**:`modified` = ClockPort.now()、
`created` = 既有投影的 `stix_created`(`StixObjectPort.findCreated`),無既有投影則 = now。
語意等價:created_at ≈ 首次投影時間、updated_at ≈ 本次合併時間;UPSERT 保證 created 穩定。

## 決策 3:marking-definition 由常數供應,不落 stix_objects

五個 TLP marking 是 OASIS 固定值(§7.8.4),無 domain 來源、無租戶歸屬;若落表,
`owner_tenant_id NOT NULL` 與 `tlp` 欄位會迫使它們套用可見度過濾(RED marking 對匿名 404,
語意錯誤)。**做法**:`GET /api/v1/stix/{stixId}` 與 bundle 對 marking 一律由
`StixTlpMarkings` 常數供應;stix_objects 於 M1 只存 indicator 投影。

## 決策 4:bundle 端點 M1 對匿名回 403

09 §9.1 標 `GET /api/v1/stix/bundle` 需 `stix:export`,10 §10.6 的配額表明示匿名
「STIX bundle 匯出 ✗」、權限矩陣匿名無 `stix:export`;但 RBAC 是 Phase 13。安全優先:
**M1 以 `AuthState != AUTHENTICATED` → 403 近似**(所有登入方案均有 stix:export,語意等價),
Phase 13 換成正式權限檢查。M1 實際全匿名,故 bundle 在 M1 環境不可達——`StixExportService`
以單元測試(service 層)與 Phase 13 後的整合驗證覆蓋。`GET /{stixId}` 維持匿名可存取。

## 決策 5:匯出上限以 property 承載(預設 1000)

`plans.stix_export_max_objects` 是 M2 表(Phase 14)。M1 新增
`ctip.stix.export-max-objects`(env `STIX_EXPORT_MAX_OBJECTS`,預設取 FREE 方案值 1000;
比照 ADR 0004 對匿名限流數值的處置)。物件數計法:marking + indicator 合計(bundle 內全部物件)。
超過丟 `StixExportLimitExceededException` → API 層 403 PLAN_LIMIT_EXCEEDED。

## 決策 6:JSON 序列化只在 app 層;測試引入 networknt json-schema-validator(test scope)

core 不碰 JSON:投影 content 是 `LinkedHashMap`(§7.8.2 順序),序列化在
`StixObjectAdapter`(落庫)與 `StixBundleWriter`(bundle 組裝)。
`StixSchemaValidationTest`(M1-29)需以 STIX 2.1 JSON Schema 驗證實際產出,
版本表(06 §6.2)沒有任何 JSON Schema 驗證器——比照 IDNA(ADR 0004)本應以現有工具替代,
但 JDK/既有依賴無可用替代,依規則 17 不得靜默移除需求。**做法**:引入
`com.networknt:json-schema-validator:1.5.6`,**僅 test scope**(不進交付物 classpath),
並 vendor OASIS `cti-stix2-json-schemas`(BSD-3-Clause)至
`ctip-app/src/test/resources/stix-schemas/` 供離線解析。**已依規則 6/17 回報**。

## 決策 7:external_references 需附 description

7.8.2 只說「附上來源標註」;OASIS schema 要求 external-reference 除 `source_name` 外
至少有 `description`/`url`/`external_id` 之一(僅 source_name 會被驗證器拒絕)。
Source 聚合無 homepage 欄位,故附固定 `description`。M2 若補 homepage,改附 `url`。

## 決策 8:RedistributionFilter 落在 core `application/stix`

7.9 規則 2 要求輸出過濾集中於一個 `RedistributionFilter`。01 §1.4 的結構契約沒有給它位置;
M1 唯二呼叫端(StixQueryService/StixExportService)都在 `application/stix`,故先落此處
(規則 3 委派 domain 的 I14)。Phase 9 的 DTO 遮罩(規則 4/5)在同一類別擴充,屆時如有
更中立的套件位置再移(單一引用點,搬移成本低)。
