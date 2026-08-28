# ADR 0019 — Phase 14–16 的規格定調(批 4)

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:`04`(新增表 18b)、`05 §5.4.5`、`09 §9.1/§9.7`、`10 §10.3/§10.6/§10.7`、`11 §11.4`、
  `phases/phase-14.md`;`docker-compose.yml` 與五份 `.env` 樣板
- **背景**:清障計畫的批 4。**不預先實作 Phase 14–16 的功能**,只把「照字面實作會做不出來」
  的規格缺口定調,讓那三個 phase 動工時不必停下來猜。

---

## 1. 配額超限的三種語意(規格三處三種答案)

| 出處 | 說法 |
|---|---|
| `09 §9.7` | 手動提交超額 → `429 RATE_LIMIT_EXCEEDED` |
| `09 §9.4` 錯誤碼表 | `PLAN_LIMIT_EXCEEDED` = `403`,「超出方案能力(非流量)」 |
| `07 §7.3` | `QUOTA_EXCEEDED` 逐筆寫入 `ingestion_rejections`(已實作於 `RejectionReason`) |

三者其實各有適用情境,只是沒人把界線畫出來。定調:

- **時間窗內的計數**(請求/分、請求/日、手動提交/日)→ `429` + `X-RateLimit-*` + `Retry-After`。
  有重置時間,client 知道何時可再試。
- **非時間窗的能力上限**(`max_api_keys`、`max_webhooks`、`stix_export_max_objects`、
  `websocket_enabled`)→ `403 PLAN_LIMIT_EXCEEDED`。不會自己恢復,等待無用。
- **單次請求的尺寸上限**(`max_batch_lookup`、`max_import_rows_per_file`)→ `413`。拆小就能過。
- **單次分頁上限** → 夾到上限不報錯(§9.3 既有行為)。
- **批次處理中途跨越每日配額** → 請求本身成功,越界記錄逐筆 `QUOTA_EXCEEDED`。
  已接受的部分不該因為後半超額而整批失敗。

## 2. `ioc:publish` 照字面實作不產生任何公開效果

§9.7 說「`owner_tenant_id` = 提交者的 tenant(**不可指定**)」,又說「要設 `CLEAR`/`GREEN`
需 `ioc:publish`」。兩條合起來產生的是「owner = 某租戶、tlp = CLEAR」的 Indicator,而它:

- 不符 `10 §10.1` 對公開情資的定義(owner 必須是 public tenant)
- 不符 `11 §11.2` public bloom 的成員條件(`owner_tenant_id = PUBLIC AND tlp = 'CLEAR'`)
- 不會被其他租戶看到——`Indicator.isVisibleTo` 對非自家資料要求 `ownerTenantId.isPublic()`

**一個永遠沒有作用的權限就是規則 16 禁止的 placeholder。**

**定調**:`ioc:publish` 是**擁有權轉移**——把 `owner_tenant_id` 設為 public tenant 並套用
`CLEAR`/`GREEN`。原租戶的來源記錄保留,attribution 因此仍然成立。

## 3. 誤判回報在資料模型上自相矛盾

§9.7 要求「在該 tenant 的 `indicator_sources` 把 `MANUAL` 那一列設為 `FALSE_POSITIVE`
(若不存在則建立)」,同時要求「只影響該 tenant 自己」。但:

- `sources` 對 `source_type` 有唯一約束(`ux_sources_source_type`),**全平台只有一列 `MANUAL`**
- `indicator_sources` 是 `UNIQUE (indicator_id, source_id)`

因此對一筆 **public** Indicator 建立 MANUAL 誤判列,改到的是共用的公開資料;
而且第二個租戶回報同一筆時會直接撞唯一約束。

**定調**:本端點**只接受 `owner_tenant_id` = 呼叫者 tenant 的 Indicator**,對公開情資回 `403`。
租戶自己的 IOC 每個 tenant 各有獨立的 indicator 列,不會相撞,「只影響自己」自然成立。

> 附帶:`Indicator.reportFalsePositive` 目前呼叫 `requireRecord(by)`,找不到來源記錄時丟
> `IllegalArgumentException`——與 §9.7 的「若不存在則建立」相反。Phase 14 須改。

## 4. 匯入 job 無處持久化

§9.7 定義「非同步 + `202` + `importJobId` + `GET /iocs/import/{jobId}`」,`13 §13.5` 也把
`import_job` 當稽核的 `resource_type`——但 **`04` 的 27 張表裡沒有 import job 表**,
§9.1 的端點清單也沒有那個查詢端點。

**定調**:新增 `04` 表 **18b `import_jobs`**(編號用 `18b` 以免動到既有表號),
納入 Phase 14 的 `V28`;`ingestion_rejections` 加 nullable 的 `import_job_id`
(來源同步的 rejection 仍為 null);§9.1 補上該端點。

## 5. Bloom:兩個決定位元陣列內容、卻沒定義的東西

1. **`hashFunctionCount` 範例 7 vs 公式 10**。同一組參數(n=10⁷、p=0.001)代入 §11.4 公式得
   `k = round(9.9648) = 10`;範例的另兩個數字(`bitSize`、`sizeBytes`)都是公式算出來的,
   只有 k 不是。**以公式為準,範例改 10**。`BloomBitLayoutTest` 要斷言確切的 byte 陣列,
   k 取 7 或 10 會產出完全不同的結果——不定案就無法實作。
2. **`h1 + i * h2` 的溢位語意未定義**。宣告為 int64,加乘必然溢位;規格寫的是數學上的 `mod`。
   Java 的 `long` 會 wrap、JS 的 `BigInt` 不會,**兩端算出的 index 不同**——而本節存在的理由
   正是「client 能產生位元組完全相同的陣列」。**定調為 unsigned 64-bit wraparound(mod 2⁶⁴)**,
   即 Java `long` 的自然行為;非 Java client 必須自行截斷。

另補 `BLOOM_STORAGE_DIR` / `BLOOM_COMPRESSION` 兩個環境變數與 `bloom-data` volume——
表 23 的 `storage_path` 是 `NOT NULL`,但原本沒有任何設定項告訴應用要寫到哪,
也沒有 volume 讓 artifact 活過容器重建。

## 6. `CTIP_PLAN_<CODE>_<FIELD>` 覆寫機制到不了容器,也綁不上屬性

兩個獨立的問題:

1. **compose 沒有 `env_file`**,backend 的環境變數是明列白名單。寫進 `.env` 的 `CTIP_PLAN_*`
   不會被傳進容器(與 ADR 0016 的 §5.5 對稱性缺陷同一類)。
2. **Spring relaxed binding 會把 `CTIP_PLAN_PREMIUM_MAX_API_KEYS` 對到
   `ctip.plan.premium.max.api.keys`**——底線一律變成點,不會變成連字號,綁不到 `max-api-keys`。

**定調**:方案配額**不走環境變數逐項覆寫**。由 `V29__seed_plans.sql` 種入,之後由
`SYSTEM_ADMIN` 經管理端點調整(§10.6 金流段本就定 M2 由 `SYSTEM_ADMIN` 手動操作)。
需要部署期覆寫時以**單一 JSON 變數** `CTIP_PLAN_OVERRIDES` 承載,避開命名陷阱。

## 7. 種子表的合法值會讓應用啟動即失敗

`plans` 種子中 `0` = 停用、`null` = 無限制,但三處既有實作的建構子拒絕它們:

| 實作 | 不變量 | 撞到的種子值 |
|---|---|---|
| `ApiKeySettings` | `maxPerTenant < 1` 丟例外 | ANONYMOUS `max_api_keys = 0` |
| `StixExportSettings` | `maxObjects <= 0` 丟例外 | ANONYMOUS `0`、ENTERPRISE `null` |
| `CtipProperties.Api` | `@Positive` | 同上 |

**Phase 14 必須先放寬這三處型別**,已寫進 phase-14 交付物。

## 8. 限流 port 承載不了 §10.7 的五個維度

| 缺口 | 現況 |
|---|---|
| **per-key 限額** | `tryConsume(key, tokens)` 沒有參數傳「這把 key 的上限」;`limitFor(window)` 只看 window,回傳建構子注入的單一數值 → 60/300/1200/6000 的分級無法表達 |
| **「無上限」** | `RateLimitResult.limit` 是 `long` 原始型別,而 ENTERPRISE 的 `requests_per_day` 是 `null` |
| **同步間隔的窗** | `Window` 只有 `MINUTE`/`DAY`;`min_sync_interval_seconds` 需要 6h/5min/1min,且**沒有任何欄位記錄某租戶上次同步時間** |

已寫進 phase-14 交付物與 `10 §10.7` 的修訂區塊。

## 9. `/subscription` 兩個端點沒有權限碼

§9.1 只有路徑沒有權限欄,21 項權限清單也沒有對應碼——但 §10.3「實作要求」明訂每個 handler
都必須宣告授權,`EndpointAuthorizationTest` 也會擋。

**定調**:Phase 14 新增 `subscription:read`(歸屬 `LOGGED_IN`)。
**刻意不現在加進矩陣**——矩陣有三份來源(規格表、seed migration、`RbacMatrix` 常數),
而 `RbacMatrixTest.theSpecificationMatrixMatchesTheSeededMatrix`(ADR 0017)會逐格比對。
現在只改規格表會立刻讓那條測試轉紅。改為記入 §10.3 的修訂區塊與 phase-14 交付物,
由 Phase 14 三處一起加。

---

## 沒有動的

`Indicator.eligibleForBloom()` 內含 `hasRedistributableSource()`,若沿用它判斷 tenant scope,
tenant bloom 會恆為空(手動提交的來源政策固定 `INTERNAL_ONLY`,而 §11.2 的 tenant bloom
成員條件沒有再散布條件)。這是 **Phase 15 的實作細節**,規格本身(§11.2)沒有矛盾——
已列入 `phase-15.md` 的注意事項,不在此定調。
