# ADR 0024 — Phase 15:兩層 Bloom Filter(snapshot / delta)

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:`backend/`(`domain/bloom`、`application/bloom`、`infrastructure/bloom`、
  `V30__create_bloom.sql`、pipeline stage 10、排程)、`docs/spec/{02,04,11}`
- **背景**:phase-15 執行單。[ADR 0019](0019-phase14-16-spec-resolutions.md) 已定調
  `hashFunctionCount = 10` 與雙雜湊的 unsigned 64-bit wraparound;本 ADR 記錄**實作當下**
  才浮現的決策與偏離。Phase 16 才做 `/api/v1/sync/*` 端點與 client 契約文件。

---

## 1. 位元運算放在 domain,不是 infrastructure

執行單把「位元運算與序列化」列在 `infrastructure/bloom/`。實作改為:

| 放在哪 | 內容 |
|---|---|
| `domain/bloom` | `BloomBitArray`(LSB-first 佈局)、`BloomIndexer`(雙雜湊)、`BloomDeltaCodec`(varint 差分)、`BloomParameters`、`Checksum`、`BloomVersion` 聚合 |
| `infrastructure/bloom` | 檔案系統儲存與 ZSTD/GZIP 壓縮(`FilesystemBloomStorage`) |

§11.4 的位元佈局是**互通性契約**,不是儲存細節:它決定 client 能不能算出位元組完全相同的
陣列。放在 domain 才受 ArchUnit 規則 1 保護(不得混入任何框架型別)與 domain 85% 的覆蓋門檻,
且 `02 §2.5` 本來就把 `BloomParameters` / `Checksum` 定位在 `core/bloom`。
壓縮與檔案 I/O 留在 infrastructure——它們**不影響 checksum**(§11.4:checksum 算在未壓縮內容上)。

## 2. tenant 述詞不含再散布條件,且放在 `BloomMembership`

ADR 0019 的「沒有動的」那一項在此定調:`Indicator.eligibleForBloom()` 內含
`hasRedistributableSource()`,只適用 **public** 層。§11.2 的 tenant 成員條件
(owner + `AMBER`/`AMBER_STRICT` + `ACTIVE`)**沒有再散布條件**——私有 Bloom 只發給該租戶自己,
不涉及再散布;沿用會使 tenant bloom 恆為空(手動提交固定 `INTERNAL_ONLY`)。

兩個述詞收在 `domain/bloom/BloomMembership`(而非再往 `Indicator` 加方法):
`Indicator.java` 已達 297 行,checkstyle 上限 300 行(01 §1.8)。`BloomMembership` 也讓
§11.2 的兩條規則在同一個檔案裡對照著讀。

> `BloomCoverageTest.theSqlPredicateAgreesWithTheDomainPredicate` 逐筆比對 domain 述詞與
> 資料庫端的述詞。兩邊各改一次就會安靜漂移——public 少一筆是可用性問題,
> **tenant 多一筆是跨租戶外洩**。

## 3. `tenant_bloom_capacity = NULL` 的語意與平台慣例相反,採 fail-closed

`QuotaLimit` 的平台慣例是 `0` = 停用、`NULL` = **無限制**(ADR 0019 第 7 節)。
但 §11.2 明文:「`null` 表示該方案無 tenant Bloom」——同一個欄位,兩種相反的讀法。

**定調**:以 §11.2 為準,只有**正整數**才產生 tenant bloom(`BloomScopePlanner.tenantTarget`
對 `isUnlimited()` 與 `isDisabled()` 一律回空)。安全優先:誤判成「無限制」會替沒有這項
權利的方案生成私有 Bloom。`PlanMapper` 與 `PlanQuotasDto` 不動,對外仍呈現 `null`。

## 4. tenant bloom 的尺寸:`min(方案上限, max(預設尺寸, 成員數))`

§11.2 說「tenant Bloom 容量依 `plans.tenant_bloom_capacity`」。照字面只用方案值,
`BLOOM_TENANT_DEFAULT_CAPACITY`(§5.4.5、compose、四份樣板都有)就會變成**綁了卻沒有任何
呼叫端的設定**——規則 16 禁止的死程式碼。而且 ENTERPRISE 的 10,000,000 會讓一個只有幾百筆
私有 IOC 的租戶,每小時產生一份 18MB 的陣列。

**定調**:方案值是**權利上限**,`BLOOM_TENANT_DEFAULT_CAPACITY` 是**實際尺寸預設**,
取 `min(方案上限, max(預設尺寸, 目前成員數))`。PREMIUM(1,000,000 = 預設值)兩種算法結果相同。
tenant 的偽陽性率沿用 `BLOOM_PUBLIC_FALSE_POSITIVE_RATE`(§5.4 沒有 tenant 專屬變數)。

> 每份 full snapshot 都會起新的 `datasetVersion`(§11.3),client 本來就每日重下 full,
> 因此尺寸隨成員數變動不會額外造成作廢。

## 5. `BloomUpdateStage` 是「哪個 scope 變了」的訊號,不是成員的真相來源

執行單要求把 stage 插在 `PersistStage` 之後。實作**刻意不在記憶體累積成員集合**:
緩衝若因重啟遺失,會產生 **Bloom false negative**——client 據此認定該值「不在集合中」,
而 §11.1 最強調的正是「未命中不代表安全」。實作不該再自行製造更多假陰性。

成員的真相來源是資料庫(生成時以水位查詢取得)。stage 只把受影響的 scope 標記進
`BloomChangeTracker`,用途是**跳過沒有變動的 scope**:每小時產生空 delta 會白白吃掉
§11.3 的 24 段 chain 預算,逼 client 無謂地重下 full。
tracker 沒有涵蓋範圍時(剛啟動)一律視為有變動(fail-safe)。

比照 `StixProjectionStage`:例外只記錄,絕不使該筆 IOC 被拒絕——Bloom 是衍生資料。

## 6. delta 的水位用 `last_seen`,並往回退一分鐘

`indicators.updated_at` **沒有索引**(04 表 5),`ix_indicators_last_seen` 才有。
任何使 IOC 成為新成員的路徑(建立、再次回報、過期後復活、手動提交)都會推進 `last_seen`,
因此以它為水位。水位往回退一分鐘:重複套用已存在的位元**不會有任何效果**(`set` 只回報
由 0 變 1 者),漏掉成員則會造成 false negative。水位真的漏掉的部分由每日 full snapshot 收斂
——delta 本來就只能新增、不能移除(§11.3)。

## 7. delta artifact 的 `checksum` 是 payload 的雜湊,不是位元陣列的

04 表 23 寫「`checksum` = 未壓縮**位元陣列**的 SHA-256」,§11.5 對 delta 卻寫
「`checksum`: sha256 of the addedBits payload before base64」。兩者對 full 一致、對 delta 相反。

**定調**:`checksum` = 該版本**未壓縮 artifact payload** 的 SHA-256——full 的 payload 是位元陣列,
delta 的 payload 是 `addedBits` 的 varint 編碼。這是唯一能讓兩句話同時成立的讀法,
也是 client 驗證下載內容完整性所需的值;套用後的自我驗證用 `resulting_checksum`(不變量 L6)。

因此 **varint 差分編碼(§11.5 步驟 1–3)屬於 Phase 15**:不先產生那個 payload 就算不出
`checksum`。Phase 16 只負責第 4 步(base64url)與 HTTP 表述。

## 8. `requiresFullSnapshot` 多接一個 `BloomChainPolicy`

`02 §2.3` 列的簽章是 `requiresFullSnapshot(int chainLength, long cumulativeDeltaBytes)`,
但門檻之一(`BLOOM_MAX_DELTA_CHAIN`)是**設定值**,domain 不得讀設定,也不該把 24 寫死。
第三個參數是值物件 `BloomChainPolicy(maxDeltaChain, maxCumulativeDeltaRatio)`,
30% 這個比例仍由規格固定。方法只允許對 **full snapshot** 版本呼叫(比例的分母是完整陣列大小)。

生成端的呼叫端是 `BloomDeltaService`:鏈太長或參數不相容時回 `FULL_SNAPSHOT_REQUIRED`,
由 `BloomGenerationService` 改跑 full snapshot。Phase 16 以同一個方法產生 `409 SNAPSHOT_REQUIRED`。

## 9. 保留政策不得刪掉仍被依賴的 full snapshot

§11.3 寫「保留最近 `BLOOM_ARTIFACT_KEEP`(30)個版本」。照字面實作會出事:同一 dataset 內
full snapshot 的 `bloomVersion` 最小(= 0),因此是**最舊的一筆**——先被刪掉的是 full,
而它的 delta 還留著,那條鏈永遠無法重建。

**定調**:刪除前排除「該 dataset 仍有存活版本」的 full snapshot。判定放在
`BloomRetentionService`(policy),port 只提供「由新到舊最多 N 筆」的查詢。

## 10. 成員掃描另立 `BloomMemberPort`,不擴充 `IndicatorRepository`

`IndicatorRepository` 現有八個方法都會 hydrate 完整聚合(含來源記錄),full snapshot 的規模是
10M 成員。新 port 只取 `(id, fingerprint)` 投影,以 keyset 分頁(`id > :afterId ORDER BY id`,
每批 10,000)掃描,且掃描**不包在單一交易內**——長交易會佔住連線。
keyset 起點用全零 UUID(indicator id 是隨機 UUID,不會等於它),避開 `:param IS NULL` 在
PostgreSQL 上的型別推導問題。

掃描期間新進的 IOC 可能落在本次之外:Bloom 本就是近似結構,下一次 delta 就會補上。

## 11. 生成 delta 前先跑一次 client 的驗證路徑

`BloomArrayLoader` 重建現行陣列時,會走**與 client 相同的 §11.6 驗證**:驗 full snapshot 的
`checksum`、驗每段 delta payload 的 `checksum`、每套用一段就比對該段的 `resultingChecksum`。

不驗會怎樣:損壞(位元被改、檔案被截斷)不會有任何徵兆,但下一段 delta 的
`resultingChecksum` 會**依損壞後的陣列算出**——於是每一個 client 套用後自我驗證都失敗、
丟棄、重下 full,而伺服器端的日誌上什麼都看不到。發現不符時回
`FULL_SNAPSHOT_REQUIRED`,由編排者立刻重建,而不是讓損壞往下傳。

> 這也是 `Checksum.matches` 的呼叫端。該方法原本沒有呼叫端——規則 16 禁止的死程式碼。

---

## 其他實作註記

- `bloom_artifacts.checksum` 是 `CHAR(64)`,entity 需 `@JdbcTypeCode(SqlTypes.CHAR)`,
  否則 `ddl-auto: validate` 會以 `bpchar` vs `varchar` 拒絕啟動(與 `api_keys` 同一前例)。
- `FilesystemBloomStorage` 是本專案 **main source 的第一個寫檔實作**:暫存檔 + `ATOMIC_MOVE`
  (排程與下載併發時讀到的必是完整檔案)、資料庫存**相對於根目錄**的路徑(換掛載點不失效)、
  解析後驗證仍在根目錄之下(`storage_path` 來自資料庫,不得成為目錄跳脫的入口)。
- 排程沿用 `ctip.scheduler.enabled` 作總開關(cron 值本身在 `ctip.bloom.*`),
  整合測試靠 `SCHEDULER_ENABLED=false` 關閉。
- `ctip.bloom.storage-dir` / `compression` 原本**只有 compose 與 §5.4 有、application.yml 沒有**
  ——`ConfigSymmetryTest` 是「yml → compose/spec」單向檢查,抓不到這個反向缺漏。已補上。
