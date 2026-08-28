# Bloom 同步 — Client 契約

> 對象:Browser Extension、行動 App、自建整合。
> 規範來源:[`docs/spec/11-sync-bloom.md`](../spec/11-sync-bloom.md) §11.5–§11.7、[`09-api.md`](../spec/09-api.md) §9.1。
> 端點的完整 schema 以 [`openapi.json`](openapi.json) 為單一來源;本檔是那份 schema 讀不出來的**語意**。

---

## 0. 一句話

```text
Bloom 說 NOT PRESENT → 一定不在「這一份 Bloom 的成員集合」中
Bloom 說 PRESENT     → 可能存在 → 必須呼叫 POST /api/v1/iocs/lookup 精確驗證
```

**Bloom 命中不是「確定惡意」,未命中不是「安全」。** 下面六條契約是強制的;不遵守會產生
誤封鎖(把偽陽性當惡意)或漏判(把「不在公開集合中」當成乾淨)。

---

## 1. 六條契約(§11.7,必須複製進 SDK 與 API 文件)

| # | 契約 |
|---|---|
| 1 | Bloom **命中不代表惡意**,必須呼叫 `POST /api/v1/iocs/lookup` 精確驗證 |
| 2 | Bloom **未命中不代表安全**——public Bloom 只覆蓋 `TLP:CLEAR`,`TLP:GREEN` 完全無覆蓋 |
| 3 | 撤銷與過期的 IOC **無法透過 delta 移除**,只有 full snapshot 才會反映 |
| 4 | `fingerprintAlgorithm` / `hashFunctionCount` / `bitSize` 任一改變即須重下 full |
| 5 | 套用 delta 後必須驗證 `resultingChecksum`,不符則重下 full |
| 6 | 位元序為 LSB-first;索引算法見 §11.4,**不得自行選用其他雜湊族** |

---

## 2. 覆蓋範圍(第 2 條的細節)

| Bloom | 成員 | 誰拿得到 |
|---|---|---|
| `PUBLIC` | `TLP:CLEAR`、`status = ACTIVE`、且非全部來源皆 `INTERNAL_ONLY` 的公開情資 | 方案 `public_bloom_enabled` 為真者(含匿名) |
| `TENANT` | 該租戶自己的 `TLP:AMBER` / `TLP:AMBER_STRICT`、`status = ACTIVE` | 方案 `tenant_bloom_capacity` 為正整數的租戶 |
| — | `TLP:GREEN` **沒有任何 Bloom 覆蓋** | manifest 的 `notCovered` 會列出它 |

manifest 的 `coverage`(每層)與 `notCovered`(全域)是**必填欄位**。若你的 client 沒有把這兩個
欄位呈現給使用者或記錄下來,就等於在傳遞「miss = 安全」的錯誤結論。

---

## 3. 成員值

成員一律是 `indicators.fingerprint` = `SHA-256(normalized_value)` 的位元組,**不是原值**
——client 因此不必把使用者正在瀏覽的網址送到伺服器就能比對。

正規化規則見 [`07-domain-intel.md` §7.2](../spec/07-domain-intel.md);同一個值兩端正規化結果不同時,
指紋不同,Bloom 一定 miss。

---

## 4. 位元陣列格式(互通性關鍵)

| 項目 | 規格 |
|---|---|
| 位元序 | bit index `i` 位於 byte `i / 8` 的第 `i % 8` 個**最低有效位**(LSB-first) |
| 陣列長度 | `ceil(bitSize / 8)` bytes |
| 未使用的尾端位元 | 必須為 0 |
| 索引算法 | Kirsch-Mitzenmacher 雙雜湊:`h1` = fingerprint 前 8 bytes 之 big-endian int64、`h2` = 次 8 bytes;第 `i` 個索引 = `((h1 + i * h2) mod bitSize + bitSize) mod bitSize`,`i` 從 0 到 `k-1` |
| 溢位 | `h1 + i * h2` 以 **unsigned 64-bit wraparound**(mod 2^64)計算——Java 的 `long` 是這個行為,JavaScript 的 `BigInt` **不是**,非 Java client 必須自行截斷至 64 位元 |
| `checksum` | **未壓縮**位元陣列的 SHA-256,十六進位小寫 |

`hashFunctionCount` 一律以 manifest 的值為準(現行參數下為 10)。

---

## 5. 同步流程(§11.6)

```text
1. GET /api/v1/sync/manifest
2. 比對 fingerprintAlgorithm / hashFunctionCount / bitSize 與本地
      不同 → 本地 Bloom 立即作廢,跳至 4
3. 比對 datasetVersion
      不同 → 跳至 4
      相同 → GET /api/v1/sync/delta?base=<本地 bloomVersion>&scope=<PUBLIC|TENANT>
              409 SNAPSHOT_REQUIRED → 跳至 4
              200 → 套用 → 驗證 resultingChecksum
                     符合 → 更新本地版本,結束
                     不符 → 丟棄,跳至 4
4. GET /api/v1/sync/bloom?scope=<PUBLIC|TENANT>
      → 依回應的 X-Bloom-Compression 解壓
      → 驗 X-Bloom-Checksum
      → 取代本地陣列
      → 本地版本記為 X-Bloom-Dataset-Version / X-Bloom-Version
```

Client 必須能保存自己最後成功同步的 `datasetVersion` 與 `bloomVersion`。

### ⚠️ 本地版本要記哪一個數字

`GET /sync/bloom` 回的是該 dataset 的 **full snapshot**(`bloomVersion = 0`),而 manifest 的
`bloomVersion` 是「delta 可以到達的最新版本」。**下載完 full 之後把本地版本記成 manifest 的版號
會造成 false negative**(你的陣列少了那些 delta 的位元,卻以為自己已經最新)。
因此一律以下載回應的標頭為準:

| 標頭 | 意義 |
|---|---|
| `X-Bloom-Scope` | `PUBLIC` / `TENANT` |
| `X-Bloom-Dataset-Version` | 這份 artifact 的 `datasetVersion` |
| `X-Bloom-Version` | 這份 artifact 的 `bloomVersion`(full snapshot 為 `0`) |
| `X-Bloom-Checksum` | **未壓縮**位元陣列的 SHA-256 |
| `X-Bloom-Compression` | `ZSTD` / `GZIP` / `NONE`——回應主體的編碼 |
| `X-Bloom-Bit-Size` / `X-Bloom-Hash-Count` | `m` / `k`,與 manifest 應一致 |

manifest 的 `checksum` 是「**完全同步後**你的陣列應有的 SHA-256」,`sizeBytes` 是未壓縮陣列長度。
兩者用途不同:前者驗「整段套用完的結果」,後者用來配置緩衝區。

---

## 6. `addedBits` 的編碼(強制)

`GET /api/v1/sync/delta` 的 `addedBits` 是這樣產生的,解碼請反向操作:

1. 收集新增的 bit 索引,**升序排序、去重**
2. 轉為差分序列:`d[0] = idx[0]`、`d[i] = idx[i] - idx[i-1]`
3. 每個 `d[i]` 以 **LEB128 unsigned varint** 編碼
4. 串接後 **base64url(無 padding)**

- `checksum` = 第 3 步結果(**base64 之前**的位元組)的 SHA-256
- `resultingChecksum` = 套用本段之後**整個位元陣列**的 SHA-256
- `addedBits` 可能是空字串:代表你已經是最新版本。此時 `resultingChecksum` 仍然有值,
  **仍然必須驗**——那正是唯一能發現「本地陣列其實不是這個版本」的檢查

`base` 一律解讀為**現行 dataset 內**的 `bloomVersion`(請求裡沒有 dataset 參數)。
所以第 3 步的 dataset 比對不可省略;萬一省略了,`resultingChecksum` 會對不上而讓你重下 full。

---

## 7. 錯誤與頻率

| 狀況 | 回應 | 該做什麼 |
|---|---|---|
| delta 鏈過長 / 累計過大 / 尚無 snapshot / `base` 不在現行 dataset | `409 SNAPSHOT_REQUIRED` | 下載 full snapshot |
| 同步太頻繁(`plans.min_sync_interval_seconds`) | `429 RATE_LIMIT_EXCEEDED` + `Retry-After` | 依 `Retry-After` 等待。**`/sync/manifest` 不受此限制**,可持續輪詢 |
| 方案不含該層 Bloom | `403 PLAN_LIMIT_EXCEEDED` | 升級方案;等待無用 |
| 尚未產生任何 snapshot | `404 NOT_FOUND` | 稍後再試(伺服器每日 04:00 產生 full) |
| 缺少權限 | `403 FORBIDDEN` | `/sync/manifest`、`/sync/bloom` 需 `sync:bloom`(匿名亦持有);`/sync/delta` 需 `sync:delta`(匿名**不**持有) |

`429` 只表示「這個 client 太頻繁」;它不代表 Bloom 有問題,不得因此清空本地陣列。

---

## 8. 命中之後:精確驗證

```http
POST /api/v1/iocs/lookup
Content-Type: application/json

{ "values": ["203.0.113.5", "evil.example.com"] }
```

單次筆數上限為方案的 `max_batch_lookup`,超出回 `413 PAYLOAD_TOO_LARGE`。
`POST /api/v1/sync/check` **不存在**(與本端點重複,已於規格 v2.0 移除)。

---

*上次校對:2026-08-28(Phase 16)。*
