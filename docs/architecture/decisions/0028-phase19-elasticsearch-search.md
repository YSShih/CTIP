# ADR 0028 — Phase 19:Elasticsearch 搜尋、降級與 reconciliation

- **狀態**:accepted
- **日期**:2026-08-29
- **範圍**:`13 §13.7`、`09 §9.1`、`05 §5.4/§5.6/§5.7/§5.8.2`、`06 §6.3.6/§6.5`、`01 §1.9`、`15 §15.2`
- **背景**:phase-19 執行單 + [ADR 0020](0020-phase17-19-spec-resolutions.md) 第 3、8 節的定調

`13 §13.7` 只有一頁:一個 port 原型、一張兩列的實作表、一行搜尋欄位、四條一致性規則。
索引名、mapping、查詢形狀、模糊查詢的參數與 API 契約、對帳演算法全部未定義。
以下是依 `00 §0.4`「安全性 > 可維護性 > 可測試性 > 可擴充性」所做的決定。

---

## 1. `SearchPort` 的簽章:包成 `SearchQuery`,回傳 `SearchResult`

`X-Search-Backend` 沒有傳遞通道(ADR 0020 §8):`searchByValue` 回 `CursorPage<Indicator>`,
而 §13.7 又禁止在 controller 判斷降級——只有實際執行查詢的 `FallbackSearchAdapter` 知道答案。

```java
public record SearchQuery(String term, boolean fuzzy, IndicatorFilter filter,
                          Visibility visibility, Cursor after, int limit) {}
public record SearchResult(CursorPage<Indicator> page, SearchBackend backend) {}
public interface SearchPort { SearchResult search(SearchQuery query); }
```

輸入也一併包成 record:`fuzzy` 加進原簽章會變成 6 個參數,違反本規格自己的 checkstyle
`ParameterNumber ≤ 5`(`01 §1.8`)。包起來之後形狀反而回到 §13.7 原型的 `search(query, cursor, limit)`。

`IocController` 改回 `ResponseEntity<PageResponse<IocDto>>` 以寫入標頭;
`X-Search-Backend` 加進 `WebCorsConfig.exposedHeaders`——不加的話瀏覽器 client 讀不到,
「降級已告知」等於沒有發生(同 `X-Bloom-*` 七個標頭的前例)。

## 2. ⚠️ ES 只回答「哪些 id、依什麼順序」,資料一律從 PostgreSQL 取回

這是本 phase 最重要的一個決定,兩層防護缺一不可:

1. **ES 端完整重建可見度述詞**(`SearchVisibilityQuery`、`SearchFilterQuery`)。
   §13.7 的搜尋欄位清單不含 `ownerTenantId`、`deletedAt` 與來源的再散布政策,
   但那三者是 `TlpSpecifications` 與 `IndicatorFilterSpecs` 的全部依據(ADR 0015、ADR 0020 §8)。
2. **回傳的 `Indicator` 一律以 `IndicatorRepository.findVisibleByIds` 從 PostgreSQL 取回**,
   等於在 source of truth 再過濾一次。

只做第 2 層不夠:分頁與 `hasMore` 會建立在錯誤的候選集合上,呼叫端會拿到
「0 筆但 `hasMore=true`」——而「本頁少了幾筆」本身就是側信道。
只做第 1 層也不夠:索引落後、mapping 少一個欄位、或有人直接對 ES 寫入,都會直接變成跨租戶洩漏。

兩層都以測試獨立驗證,且都做過反向驗證(拿掉任一層,對應的測試轉紅):

| 拿掉的東西 | 轉紅的測試 |
|---|---|
| ES 端的可見度述詞 | `invisibleDocumentsDoNotConsumeThePage`、`filtersAndCursorPaginationBehaveLikeTheDatabasePath` |
| PostgreSQL 的補齊 | `poisonedIndexDocumentsStillCannotEscapeVisibility` |

代價是每頁多一次資料庫查詢。依 §0.4 的優先序(安全性 > 可擴充性)接受。

## 3. 索引名、mapping 與側信道欄位

索引名 `ctip-indicators`(§13.7 未指定)。mapping 為 `dynamic: strict`——欄位名打錯時寧可整筆寫入
失敗,也不要靜默多出一個沒人查詢的欄位,那會讓可見度述詞看起來有寫、實際比對到空值。

除 §13.7 明列的搜尋欄位外,文件另外帶三個**規格清單沒有**的欄位:

| 欄位 | 對應的規則 |
|---|---|
| `ownerTenantId` | 租戶範圍(`07 §7.7`) |
| `redistributable` | 存在非 `INTERNAL_ONLY` 的來源記錄(I14 / `07 §7.9` 規則 3) |
| `disclosableSourceIds` | `sourceId` 過濾的揭露規則([ADR 0015](0015-future-phase-hardening.md) 修正 2) |

`redistributable` 與 `disclosableSourceIds` 在寫入時先算好,查詢端因此不需要 `nested` 查詢,
兩條規則各自只剩一個 term 條件。**軟刪除的 indicator 完全不進索引**(而不是以旗標標記):
不在索引裡就不可能被查出來,比多一個必須每次都記得加的條件安全;殘留的孤兒由對帳刪除。

`lastSeenNanos` / `updatedAtNanos` 兩個 `long`:ES 的 `date` 只有毫秒精度,而 keyset 分頁的鍵是
`(last_seen, id)`、對帳的版本是 `updated_at`。截斷到毫秒會讓同一毫秒內的資料在翻頁時被跳過
(`Cursor` 已為 PostgreSQL 路徑記過同一件事),版本比對則兩邊永遠對不齊。

## 4. 查詢形狀與模糊查詢的 API 契約

`normalizedValue` 是 keyword 欄位。子字串以 `wildcard` 表達,它同時涵蓋 §13.7 要求的精確與前綴查詢,
語意與 M1 的 `LIKE '%term%'` **逐字相同**——換後端不得讓同一個查詢回不同的結果集。
使用者輸入的 `* ? \` 一律跳脫(對應 PostgreSQL 路徑對 `% _ \` 的處理)。

模糊查詢(§13.7「僅 M2,用於 typosquatting 偵測」)**以 `POST /iocs/search` 的 optional `fuzzy`
旗標明示啟用**(使用者裁示)。自動退而求其次的作法會讓呼叫端無法區分「精確命中」與「拼字相近」,
而 typosquatting 偵測要的正是這個區分。參數 `fuzziness=AUTO`、`prefixLength=1`、`maxExpansions=50`
(§13.7 未指定)。降級到 PostgreSQL 時該旗標無效,呼叫端由 `X-Search-Backend` 得知。

排序維持固定 `lastSeen DESC, id DESC`,與 PostgreSQL 路徑逐字相同:降級可以發生在翻頁的任何一頁,
兩邊的 cursor 必須可以互換。§13.7 修訂 3 提到的「自由排序留待 M2 與 ES 一併設計」**不在本 phase 交付**
——它需要每種排序鍵一套 cursor 編碼,且與降級的 cursor 互換性直接衝突;此處明確回報未實作(規則 17)。

## 5. 三個 `SearchPort` bean 的歧義

`PostgresSearchAdapter` 是 package-private 的 `@Component`,`IndicatorQueryService` 注入單一 `SearchPort`。
`SearchConfig` 以 `@Primary` 提供組合實作,PostgreSQL 的以 bean 名稱 qualifier 取用(型別在 `config` 看不到)。
ES 相關 bean 全部收在 `@ConditionalOnProperty(ctip.search.backend=elasticsearch)` 的巢狀 `@Configuration`
裡——`SEARCH_BACKEND=postgres` 時一個都不建立,mvp/dev 的 compose 根本不啟動 Elasticsearch,
憑空多一條打不通的路只會讓每個查詢先等一次逾時。

與 `ctip.rate-limit.backend`(ADR 0026)的語意差別要記住:限流是**硬**切換(Redis 連不上就啟動失敗,
因為限流是安全機制);搜尋是**軟**切換,§13.7 明文要求 ES 不可用時降級回 200,
執行期的降級由 circuit breaker 負責,屬性只決定「有沒有 ES 這條路」。

## 6. circuit breaker 的參數

§13.7 只寫「以 Resilience4j circuit breaker 實作」,沒有給值。取比 `08 §8.5` 的來源抓取更靈敏的一組:
`slidingWindowSize=10`、`minimumNumberOfCalls=3`、`failureRateThreshold=50%`、`waitDurationInOpenState=30s`。
理由是使用者查詢等不起 20 次逾時——沒有斷路器的話,「降級成功」會伴隨每個請求數秒的延遲,
對使用者而言服務仍然是壞的。`SearchFallbackTest` 明確斷言斷路器會開路。

## 7. `SearchIndexStage` 只標記,寫出在交易提交後

比照 `StixProjectionStage`(ADR 0005):stage 11 在批次交易內執行,若在此寫 ES,
交易 rollback 後索引會留下不存在的資料;而外部系統的失敗會污染交易、使整批 rollback,
違反 §13.7「索引失敗不得使 ingestion 失敗」。

stage 只把 indicator id 放進 context,`IngestionBatchExecutor` 於提交後交給 `SearchIndexWriter`,
由它**從 source of truth 重新讀出文件**再 bulk 寫入——`updated_at` 只有提交後才確定,
而對帳正是拿它來比對版本。所有例外只記 WARN。

§13.7 的「只記錄並**排入重試**」由每日 05:00 的對帳承擔:它本來就會把缺漏與版本落後的文件補回來。
另建一個記憶體重試佇列只會多一個重啟即遺失的真相來源,而那與 [ADR 0024](0024-phase15-bloom-decisions.md)
對 Bloom 成員集合的判斷同一個道理。

## 8. 對帳:歸併比對,只在共同涵蓋的區間內下判斷

兩邊都以文件 id 昇冪掃描、每次各取一批,**只在兩批共同涵蓋的 id 區間內判定漂移**。
不設這個邊界的話,一批尾端之後、下一批之前的文件會在每一輪被誤判成孤兒刪掉,對帳會把索引愈修愈空。
修正方向永遠是以 DB 為準:缺漏補寫、版本落後重寫、DB 沒有的刪除。
UUID 的規範字串以字典序比較,與 PostgreSQL 的 `uuid` 位元組序一致(dash 位置固定,不參與區分)。

排程變數沿用 `08 §8.7` 已經命名好的 `ES_RECONCILE_CRON`(每日 05:00);
它與 `SEARCH_BACKEND` 一併補進 compose、`05 §5.4` 與五份 `.env` 樣板(§5.5 對稱性,`ConfigSymmetryTest` 強制)。
排程只在 ES 後端且 `SCHEDULER_ENABLED=true` 時註冊——PostgreSQL 後端沒有外部索引可對帳,
註冊了只會每天產生一份「整個索引都缺」的假警報。

**啟動時的補建**(實跑 staging 才發現的缺口):全新的 ES 叢集在 05:00 之前索引是空的,
而搜尋照樣回 `200` 並宣稱 `X-Search-Backend: elasticsearch`——**那比降級更糟**,
降級至少會說出來,空索引是靜默的錯誤答案。`SearchIndexBootstrap` 在
`ApplicationReadyEvent` 後檢查「索引空而資料庫非空」,成立才在背景執行緒補建一次;
正常重啟不付出任何代價,例外只記錄(05:00 仍會再試)。§13.7 明文的
「可隨時從 DB 重建」正是這個能力的出處。

## 9. ⚠️ mvp/dev 必須關掉 actuator 的 elasticsearch 健康檢查

`spring-boot-elasticsearch` 一在 classpath 上,actuator 就會加一個 ES 健康檢查。
Elasticsearch 只屬 `full` profile(staging/prod),mvp 與 dev 都沒有它——不關掉的話
`/actuator/health` 永遠 DOWN、容器 healthcheck 永遠失敗、`depends_on` 卡死,
`dod.sh mvp` 的回歸會整批紅。這與 Phase 17 的 Redis(`00 §0.23` 第 10 項)是同一個地雷,
差別在 redis 屬 `standard,full` 而 ES 只屬 `full`,因此 **dev 也要關**。已補進 `06 §6.3.6`。

## 10. `ELASTICSEARCH_URL` 為空的守衛,放在設定層而不是 `StartupValidator`

compose 對這個變數原本用 `${ELASTICSEARCH_URL:-}`,未設定時是**空字串**而不是「缺少」,
`application.yml` 的預設值因此不會生效。

第一版把守衛寫進 `StartupValidator`(空值即拒絕啟動),**但那是一條永遠不會觸發的規則**:
Boot 的 ES autoconfig 在 context refresh 期間就先失敗了(見第 15 節),任何 bean 形式的檢查
都來不及執行。留著它等於留一段不可達的程式碼(執行規則 16),因此移除。

守衛改放在真正的執行點:`ConfigSymmetryTest` 斷言 compose 對「有 autoconfig 綁在上面的變數」
不得給空字串預設值。把 compose 改回 `${ELASTICSEARCH_URL:-}` 該測試立刻轉紅,已實測。

## 11. ArchUnit 規則 11 擴充,而非新增規則 12

phase-19 的「不得讓 `ElasticsearchSearchAdapter` 的型別洩漏到 `application` 層」與規則 11
(Phase 17 為 Redis 建立)是同一條規則的兩個實例。擴充既有規則的套件清單
(`co.elastic.clients..`、`org.elasticsearch..`、`org.springframework.data.elasticsearch..`、
`io.github.resilience4j..`),維持 `00 §0.3` 的「11 條 ArchUnit 規則」契約不變。

## 12. `15 §15.2` 的 M2-22 判準是空轉通過的

`M2-22` 是 DoD 全表唯一用 `verify` 的過濾式判準,違反 `15 §15.0` 自訂的規則
(`verify` 綁 JaCoCo `check`,單一測試類不可能滿足門檻);更嚴重的是它因此**繞過了 `dod.sh` 的
`mvn_test` 存在性守衛**([ADR 0017](0017-gate-credibility.md))——`ElasticsearchSearchTest`
不存在時,surefire 跑 0 個測試、build 成功、該項 `[PASS]`。已與 M2-23/M2-24 統一為 `mvn_test`,
規格與 `dod.sh` 同步修正。

## 13. compose 的兩個服務沒有 `image:`,兩個 build target 因此共用同一個 image

`M2-25`(`up.sh staging`)是 DoD 中**唯一會切換 build target** 的項目,而先前的 phase 都只跑
`--only` 的子集,所以這個從 Phase 2 就存在的缺陷到本 phase 才浮現。

`docker compose up` **只在 image 不存在時才建置**。`backend` / `frontend` 沒有 `image:` 鍵時,
compose 對兩個 target 推導出同一個名稱,於是「先跑過 mvp(`development`)再跑 staging(`production`)」
會直接沿用 development 的 image。症狀是 production 的環境配上 development 的 CMD:
backend crash-loop 於 `/workspace/mvnw: No such file or directory`、frontend 於找不到 `package.json`,
而 `up.sh` 只看得到「服務一直在 restart」——完全看不出原因。

**修正**:兩個服務加 `image: ${PROJECT_NAME:-ctip}-<service>:${*_BUILD_TARGET:-production}`。
不同 target 即不同 tag,compose 會自行建置缺少的那一個,也不必為此改成每次 `--build`
(那會讓 dev 的每次啟動都付一次建置成本)。已寫回 `05 §5.6` 骨架與新增的 `§5.8.2`。

`dod.sh phase2` 的 `M2-01` 會先把環境切回 mvp、`M2-25` 再切到 staging——**同一次閘門內就會來回切換兩次**,
沒有這個修正 M2-25 永遠不可能通過。

## 14. frontend 的 `HEALTHCHECK` 用 `localhost`,而容器內 `localhost` 只有 IPv6

同樣是 `M2-25` 才照得到的角落:production stage 只有它會實際跑起來。

`nginx:alpine` 內 `localhost` 只解析到 `::1`,而 `config/nginx/default.conf` 的 `listen 80;`
只綁 IPv4。**busybox 的 `wget` 不會回退到 IPv4**,healthcheck 因此永遠失敗、容器永遠 `unhealthy`,
`up.sh` 等到逾時而失敗。手動用 `curl` 驗證時完全看不出來——curl 會回退到 IPv4。
改為 `http://127.0.0.1/`,規格 `05 §5.3` 同步。

## 15. `ELASTICSEARCH_URL` 的空字串預設值讓 mvp 完全無法啟動

`dod.sh phase2` 的 `M2-01`(mvp 回歸)抓到的:compose 對這個變數用 `${ELASTICSEARCH_URL:-}`,
而 Boot 的 ES autoconfig 對空 `uris` 直接丟 `hosts must not be null nor empty`。
**backend crash-loop,即使 `SEARCH_BACKEND=postgres`、`SearchConfig` 一個 ES bean 都沒建立**
——autoconfig 是 Boot 自己的,不受本專案的條件裝配影響。

這與 `06 §6.3.6` 第 1 條(Redis:autoconfig 不在 classpath 上,`spring.data.redis.*` 靜默失效)
剛好是一體兩面:**那條是屬性靜默無效,這條是空屬性直接讓應用死掉**。

**修正**:compose 預設值改為 `http://elasticsearch:9200`。mvp/dev 連不到它,但也永遠不會用到
(健康檢查已關、`SearchConfig` 不建立任何 ES bean、Rest5Client 不會在啟動時連線)。
第 10 節的 `StartupValidator` 守衛仍然保留——compose 之外的部署方式(直接 `java -jar`)
仍可能給出空值,而那時的症狀是「每次查詢先逾時再降級」的靜默錯誤。

## 16. `up.sh` 少了 `--remove-orphans`,gate 跑完一次就重跑不了

`dod.sh phase2` 的最後一段(`M2-25`)把環境留在 staging(`full` profile,八個容器)。
再跑一次時 `M1-14`(「`up.sh mvp` 成功,且只有 frontend/backend/postgres 三個容器」)必然失敗:
四個環境共用同一個 compose 專案名,服務差異只靠 profile,而 `compose up -d` **不會**收掉
上一個 profile 留下的服務。實測訊息是「預期 3 個服務,實際 8 個」。

⚠️ **`--remove-orphans` 解決不了這件事**(第一次的修法,實測無效):compose 刻意不把
profile 停用的服務視為 orphan——它們畢竟寫在同一份 compose 檔裡。

正確的修法是自己算差集:`ps --services`(專案內執行中的)減去 `config --services`
(本 profile 啟用的),對差集 `rm -sfv`,之後才 `up -d`。這不只是 gate 的問題——
日常在 mvp / dev / staging 之間切換也會累積殘留容器。規格 `05 §5.10` 同步。

## 17. 其他

- **索引寫入不強制 refresh**:索引是最終一致的讀取副本,強制 refresh 會把每一批攝取變成一次段合併。
  需要立刻可見的只有測試,由測試自己 refresh(`SearchIndexControl`)。
- **`SearchFallbackTest` 不使用容器**:「ES 停止」以一個沒有服務在聽的位址表達,連線被拒是最乾淨的
  不可用形式,測試因此留在 L3(`integration`)、也會被 M2-27 的 `verify -Ptest-integration` 跑到。
  測試明確斷言注入的 `SearchPort` 真的是 `FallbackSearchAdapter`,否則「永遠回 postgres」會是假綠。
- **`ElasticsearchSearchTest` / `SearchReconciliationTest` 是 L4(`heavy`)**,共用單例容器
  (`ElasticsearchTestContainer`);image 與 compose 一致(`elasticsearch:9.5.1`)。
- **`bulk` 的部分失敗只記 WARN 不丟例外**:整批因為一筆而重做只會放大問題,留給對帳補。
- **Threat 的搜尋不在本 phase 交付**(執行單交付物未列);`ThreatUpdated` 事件與
  `ThreatConsistencyListener` 是現成的接入點。
