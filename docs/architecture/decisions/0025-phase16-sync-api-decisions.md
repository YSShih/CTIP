# ADR 0025 — Phase 16:增量同步 API 與 client 契約

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:`backend/`(`application/sync`、`interfaces/rest`、`infrastructure/ratelimit`、
  `infrastructure/web`)、`frontend/`(`features/sync`、`pages/SyncPage`、`e2e/` + Playwright)、
  `docs/api/{README.md,sync-client-contract.md}`、`docs/spec/{05,09,11,12,14,15}`
- **背景**:phase-16 執行單。[ADR 0019](0019-phase14-16-spec-resolutions.md) 已列出三項待決
  (同步間隔的窗、簽章下載 URL 的設定缺口、匿名對 `scope=TENANT` 的語意);
  [ADR 0024](0024-phase15-bloom-decisions.md) 把 `addedBits` 的第 4 步(base64url)與 HTTP
  表述留給本 phase。

---

## 1. `min_sync_interval_seconds` 另立 port,不塞進限流器

ADR 0019 指出 `RateLimitKey.Window` 只有 `MINUTE` / `DAY`,而配額值是 86400 / 21600 / 300 / 60,
且**沒有任何欄位記錄某租戶上次同步時間**。三個選項:

| 選項 | 為什麼不採 |
|---|---|
| 擴充 `Window` 列舉 | 值是**依方案查表**的秒數,不是固定幾種窗;列舉表達不了 |
| 用 1 token 的桶模擬 | 「每視窗一次」與「距上次至少 N 秒」不同:視窗邊界會連續放行兩次 |
| 新增資料表 | 記帳對象含**匿名 IP**(見下),等於為每個 IP 建一列;而這是純節流狀態,遺失只會讓某個 client 早一點可以再同步 |

**定調**:新增 `SyncThrottlePort`(`lastSyncAt` / `recordSync`),M2 以
`InMemorySyncThrottle` 實作(僅單一實例正確,與 `InMemoryRateLimiter` 同一定位),
Phase 17 隨 Redis 換成 `SETEX`(TTL = interval,逐出自動發生)。
記帳對象是 `ClientSubject`:API key → 使用者 → 匿名 IP(§10.7 維度 1、2、4 的順序);
**刻意不用 tenantId**——匿名一律綁 public tenant,以 tenant 記帳會讓全世界的匿名 client
共用一個額度,第一個同步完其他人整天都拿 429。

`min_sync_interval_seconds = 0` 解為「不限制」(04 表 17 的約束是 `>= 0`,0 沒有別的讀法),
此時完全不記帳。

## 2. 節流只套資料端點,`/sync/manifest` 不套

§11.6 的流程第 1 步就是 manifest:client 得先讀它才知道要不要同步、以及自己的參數是否已作廢。
ANONYMOUS 的間隔是 86,400 秒——把 manifest 也節流,等於匿名 client 一天只能問一次
「有沒有新版本」,而它真正該省的是 18MB 的傳輸,不是幾百 bytes 的 metadata。

## 3. 409 SNAPSHOT_REQUIRED **不**消耗同步間隔

檢查刻意排在「已確定會回 200」之後。client 收到 409 依 §11.6 必須改下載 full snapshot;
若 409 也記帳,那一步會立刻撞上 429,**整條復原路徑永遠走不完**。
`SyncEndToEndTest` 與 `SyncServiceTest` 各有一條測試守這件事。

## 4. `/sync/bloom` 直接串流,不走「302 至簽章 URL」

§11.5 允許兩種。簽章 URL 需要一組簽章金鑰,而 §5.4 沒有任何對應的環境變數——為了目前只有
`FILESYSTEM` 一種 `storage_kind` 的情境新增設定項,是為未來需求預先建置(規則 18)。
因此 `BloomStoragePort` 增加 `readStored`(回儲存體中的原始位元組),回應直接串流。
`compression` 依 §11.4「僅影響傳輸」:伺服器不在下載路徑上解壓再重壓。

## 5. 下載回應必須自報版本(`X-Bloom-*`),否則會產生 false negative

這是實作時才發現的**規格陷阱**。manifest 的 `bloomVersion` 是「delta 可以到達的最新版本」,
而 `/sync/bloom` 回的是該 dataset 的 **full snapshot**(`bloomVersion = 0`)。
§11.6 第 4 步只寫「取代本地 → 更新版本」,沒說更新成哪一個數字——照 manifest 記,
client 的陣列會少掉那些 delta 的位元卻自認最新,**而 Bloom 的 false negative 是不可接受的錯誤**。

**定調**:下載回應帶 `X-Bloom-Scope` / `-Dataset-Version` / `-Version` / `-Checksum` /
`-Compression` / `-Bit-Size` / `-Hash-Count`,client 一律以回應標頭更新本地版本;
CORS 的 `exposedHeaders` 一併補上(瀏覽器 client 讀不到未 expose 的標頭)。
第二道防線是 §11.5 的自我驗證:記錯版本的 client 下次要 delta 時 `resultingChecksum` 會對不上,
於是丟棄重下 full——**這也是「空區間也必須給得出 `resultingChecksum`」的理由**。

## 6. manifest 的 `checksum` 是「完全同步後」的陣列雜湊,`sizeBytes` 是未壓縮長度

§11.5 的欄位沒說 checksum 屬於哪個版本。若照字面取「最新版本 artifact 的 checksum」,
最新版本是 delta 時那算的是 varint payload 的雜湊,client 拿它驗自己的陣列**永遠不會相符**。

**定調**:`checksum = BloomVersion.arrayChecksum()`(full 用 artifact checksum、delta 用
`resultingChecksum`,不變量 L6),語意是「完全同步後你的陣列應有的 SHA-256」;
`sizeBytes` 取 `ceil(bitSize / 8)`——§11.5 範例的 17,971,985 正是 `143775880 / 8`,
而該範例的 `compression` 是 `ZSTD`,若那個欄位指壓縮後大小,兩個數字不可能相等。
壓縮後的實際位元組數由 `Content-Length` 表達。

`arrayChecksum()` 放在聚合上,讓 manifest 與 `/sync/delta` **共用同一個判定**——兩處各自
判斷 full/delta,任一邊寫錯就會讓所有 client 的驗證恆為失敗。

## 7. `base` 一律解讀為現行 dataset 內的版號

§11.5 的請求參數只有 `base` 與 `scope`,沒有 dataset。因此:
`base` 不在現行 dataset 的鏈上 → `409 SNAPSHOT_REQUIRED`(與「鏈太長」「尚無 snapshot」同一出口,
因為 client 的動作完全相同)。舊 dataset 的版號若剛好也存在於新 dataset,`resultingChecksum`
會對不上而讓 client 重下 full——安全的失敗方向。

`addedBits` 是區間內各段 delta 的**併集**後重新編碼:Bloom 只會把位元由 0 設為 1,
併集與逐段依序套用對陣列的作用完全相同(§11.3),因此 `resultingChecksum` 取最後一段的即可。

## 8. 匿名對 `scope=TENANT` 回 `403 PLAN_LIMIT_EXCEEDED`

ADR 0019 的第三項待決。匿名綁 public tenant,而 public tenant 沒有也不得有訂閱(不變量 T3),
其方案 ANONYMOUS 的 `tenant_bloom_capacity` 為 `NULL` → 依 §11.2 的 fail-closed 判定「無 tenant Bloom」。
判定點收在 `BloomScopePlanner.hasTenantBloom`,**生成端與下載端共用**:兩邊各寫一份的話,
任一邊漂移就會出現「manifest 說有、下載回 403」,或更糟的「方案沒有卻仍生成」。
manifest 對這種呼叫者**整個省略** `tenant` 欄位(不是給 `null`)——springdoc 不會把 Java 端的
`Optional` 對應成 nullable,省略才讓 openapi 與 wire 的形狀一致。

## 9. `OpenApiCompletenessTest` 的 2xx 檢查改為「不限媒體型別」

原檢查寫死 `content/application~1json`。`GET /sync/bloom` 回 `application/octet-stream`
的位元陣列(§11.5),照原檢查會逼它假裝自己回 JSON。§9.6 要求的是「記載回應內容」,不是 JSON,
故改為「任一 2xx 有非空的 `content`」。這是**測試的判準修正**,不是放寬:三個同步端點的
schema、範例、錯誤回應、認證說明一項都沒少。

## 10. Playwright 骨架涵蓋 M2-26 的全部四個情境(以 stub 邊界)

[ADR 0022](0022-orphan-deliverables.md) 把骨架歸給本 phase,執行單只要求「最小案例」。
但 M2-26 的判準是 `npx playwright test`,只交付一個 smoke 案例會讓那一項**假綠**。

§12.8 把 E2E 列在「**前端**測試」之下,四個情境(匿名搜尋、登入、建立 API key、提交 IOC)
因此以 `page.route` 攔截 `/api/v1/**` 執行:測到的是真實的 bundle、路由、Query 快取與渲染,
只有 HTTP 回應是固定的。整套環境的驗證由 M2-25(compose)與 M3-05(WebSocket)負責。
`E2E_BASE_URL` 有值時不安裝任何攔截、也不啟動 webServer,可直接對 compose 起好的環境跑同一組案例。

`webServer.command` 刻意含 `npm run build`:CI 上不保證 `dist/` 存在,而 `vite preview`
沒有 `dist/` 會直接失敗——放在這裡,`npx playwright test` 才是單一指令。
瀏覽器本體(`npx playwright install chromium`)是**本機/CI 前置**,不是專案交付物
(同 ADR 0022 對 `gh` CLI 的處置)。

## 11. `M2-15` 的判準改指向 `SyncEndToEndTest`

原判準跑 `BloomDeltaTest`,而該測試驗的是**生成端**「鏈太長 → 改生 full」;
`409 SNAPSHOT_REQUIRED` 這個 HTTP 行為在 Phase 15 根本還不存在(progress.md 已標記為「假綠的一半」)。
`SyncEndToEndTest` 真的產生 25 段 delta 讓 `chainLength > BLOOM_MAX_DELTA_CHAIN` 在資料庫裡成立,
再斷言 `409` + `code = SNAPSHOT_REQUIRED`。`15 §15.2` 與 `dod.sh` 同步更新。

---

## 其他實作註記

- `ClientIp.normalize` 從 `RateLimitFilter` 抽到 `infrastructure/web`:限流(維度 4)與同步節流
  必須用**同一份** IPv6 `/64` 收斂規則,兩份各自實作等於留一個繞過缺口
- `SyncService` 只讀已生成的版本與 artifact,**不觸發任何生成**——排程之外的路徑若能觸發
  18MB 陣列重建,就成了放大攻擊的入口
- `BloomTestHarness` 由 package-private 改 public,供 `application/sync` 的單元測試共用同一組
  Bloom 參數與替身;另建一份會讓兩邊的參數各自漂移
- 前端 `SyncPage` 只呈現 manifest,**不在瀏覽器裡重建位元陣列**:下載與套用 delta 是 client SDK
  的職責(§11.6),頁面的規格責任是把 §12.6 第 3 條的語意講清楚
- `download()` **刻意沒有方法層交易**:各 repository 呼叫自帶交易即足夠,而把 18MB 的檔案讀取
  包在同一個交易裡等於在磁碟 I/O 期間握著連線;宣告 `readOnly` 更會讓 `download_count` 的
  UPDATE 被 PostgreSQL 直接拒絕(實測即如此)
- **已知限制**:回應主體整份讀進記憶體(`BloomStoragePort` 回 `byte[]`,Phase 15 的形狀)。
  public bloom 一份 18MB,並發下載會是堆積壓力;同步間隔限制了頻率,但真要支撐大量 client
  仍應改為串流(`InputStream`)或把 artifact 放上 CDN。M2 不改 port 形狀,記錄於此
- `bloom_artifacts.download_count` 自 Phase 15 就存在卻**從未被寫入**(規則 16 的「永不可達欄位」),
  下載端點是它唯一可能的呼叫端 → 本 phase 補上定向 UPDATE(`recordDownload`),
  不走「讀出聚合 → 改 → 存回」以免與排程的生成互相沖掉(同 ADR 0013 對 `last_used_at` 的處置)
