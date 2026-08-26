# ADR 0009 — Phase 12 IOC 頁面 + PostgreSQL 搜尋實作決策

- 狀態:accepted(2026-08-26)
- 範圍:Phase 12(IOC Search/Detail/Dashboard + PostgreSQL 搜尋),治理規格 12 §12.5–12.6、13 §13.7、03 §3.5.4

## 1. §13.7 搜尋欄位補齊(使用者決策:全部補齊)

Phase 9 的搜尋只支援 type/severity/status/tlp/includeExpired,§13.7 的欄位清單
(tags、source、confidence、score、時間區間)未落實,`ix_indicators_tags` GIN 索引無人使用。
本 phase 擴充 `IndicatorFilter`(+ `IntRange`/`TimeRange` 值物件)、`IndicatorFilterSpecs`、
`IocListParams`、`SearchRequest`,GET /iocs 與 POST /iocs/search 同步支援:
tags(全部包含)、sourceId(EXISTS indicator_sources)、confidenceMin/Max、scoreMin/Max、
lastSeenFrom/To(閉區間)。既有五欄位建構子保留為便利多載,Phase 9 呼叫端不動。

- **tags 採 AND(全部包含)語意**:篩選的目的是收斂結果,AND 是典型 filter 語意;
  且 `@>`(contains)是 GIN 索引支援的運算子。OR(交集非空)留給 M2 Elasticsearch。
- **排序維持固定 `lastSeen DESC, id DESC`**:這是 keyset cursor 分頁的前提
  (04 表 4:`ix_indicators_last_seen` 不可移除);自由排序需要每種排序鍵一套 cursor 編碼,
  §13.7 的「排序」能力在 M1 以固定排序滿足,自由排序留待 M2 與 ES 一併設計。**已回寫 §13.7。**

## 2. Hibernate `String[]` 綁 varchar[] 與 text[] 欄位的 `@>` 型別衝突

`indicators.tags` 是 `text[]`(04 表 4),Hibernate 7 將 `String[]` 屬性綁為 `varchar[]`,
PostgreSQL 的 anyarray 運算子不做 varchar[] → text[] 隱式統一,
`tags @> cast(array[?] as varchar array)` 直接報 `operator does not exist`。
解法:`PostgresFunctionContributor`(`META-INF/services` 註冊)註冊 pattern-based HQL 函式
`ctip_tags_contain_all` → `(?1 @> cast(?2 as text[]))`,顯式 cast 後運算子解析正常且可用 GIN。
不改 schema(04 的 text[] 是契約)、不改 entity 映射(影響面過大)。

## 3. `SearchAdapter` 改名 `PostgresSearchAdapter`

規格(13 §13.7、phase-12)命名為 `PostgresSearchAdapter`;M2 將有
`ElasticsearchSearchAdapter` 與 `FallbackSearchAdapter`,泛名會撞語意。行為不變。

## 4. `SearchPort` 簽章維持 Phase 9 形態(規格回寫)

§13.7 原文簽章 `CursorPage<IndicatorSummary> search(IndicatorQuery, Cursor, int)`;
實作自 Phase 9 起為 `searchByValue(String term, IndicatorFilter, Visibility, Cursor, int)`
且回 `CursorPage<Indicator>`(§1.11 可見度必須是查詢輸入;IndicatorSummary 投影 M1 無消費者)。
判定為既成偏離、語意等價,**回寫 §13.7** 而非改碼。

## 5. OpenAPI 文件缺陷修正(產出契約變更,破壞性檢查 PASS)

1. `GET /iocs` 的 `IocListParams` 缺 `@ParameterObject`,openapi 呈現為單一 `params` 物件
   query 參數——與 Spring 實際的攤平繫結不符,任何 generated client 都會送錯 wire 格式。補註解後
   openapi 呈現 16 個獨立 query 參數。
2. 三個回傳 List 的端點(`/iocs/{id}/sources`、`/sources`、`/stats/sources`)在 @ApiResponse
   誤用單物件 `@Schema` → generated 型別是單物件非陣列。改 `@ArraySchema`。
   兩者皆為「文件與實際行為不符」的修正;`openapi-breaking-check.py` 對 base 比對 PASS。

## 6. CORS 缺口:`WebCorsConfig`(照規格環境變數字面接線,實測必然缺席的一塊)

05 §5.7 定義 `CORS_ALLOWED_ORIGINS → ctip.cors.allowed-origins`,`StartupValidator` 也有
prod 萬用字元守衛,但從 Phase 3 至 Phase 11 沒有任何 `WebMvcConfigurer` 把值套用到 MVC——
瀏覽器端一接就 CORS 全擋(本 phase 前端首次跨源呼叫即發現)。新增 `WebCorsConfig`:
`/api/**`、GET/POST、`exposedHeaders` 帶 X-RateLimit-* 與 Retry-After(§10.7 標頭對瀏覽器可見)、
不開 allowCredentials(M1 無 cookie 憑證)。**建議回寫 05 §5.7。**

## 7. `up.sh` 前端預熱守衛改 lockfile 戳記

原守衛只驗 `node_modules/.bin/vite` 存在,偵測不到相依漂移(Phase 11/12 加套件後,
dev 容器啟動必失敗)——與 Phase 10 修 backend go-offline 守衛同類問題。改為
`cmp package-lock.json node_modules/.ctip-lock-stamp`,npm ci 成功後寫戳記。
**建議回寫 05 §5.10。**

## 8. 前端組合層決策

- stats hooks(`useStatsSummary`/`useSourceStats`)置於共用 `src/hooks/`:stats 不屬於
  九個 feature 之一,Dashboard 是純組合頁;Query key 仍依 §12.3(`['stats','summary']`)。
- Dashboard 來源健康以 `status === 'ACTIVE'` 計(SourceStatus 列舉:ACTIVE/DEGRADED/FAILED/DISABLED;
  badge 色彩三態映射)。
- IocSearchPage 的資料路徑:有關鍵字 → POST /iocs/search,無關鍵字 → GET /iocs
  (search 的 query @NotBlank 是 Phase 9 契約);兩者共用同一 Query key 形態與 filter。
- 403 → `ForbiddenState`、404/其他 → `ErrorState`、零筆 → `EmptyState`、pending → skeleton
  (§12.6 #4:不得空白或假資料)。
