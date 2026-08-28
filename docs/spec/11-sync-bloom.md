# 11 — Bloom Filter 與增量同步 `[M2]`

> **規範等級：強制。** Bloom 語意、兩層架構、delta 格式、client 契約為規範性內容。
>
> 相關檔案：[02-ddd-model.md](02-ddd-model.md#bloomversion)（不變量 L1–L8）、[07-domain-intel.md](07-domain-intel.md#tlp-可見度)

---

## 11.1 目的與語意（最重要的一節）

讓 Browser Extension / App 能以極低成本判斷某 IOC **是否可能**存在於情資集合中。

```text
Bloom 說 NOT PRESENT → 一定不在此 Bloom 的成員集合中
Bloom 說 PRESENT     → 可能存在 → 必須再呼叫 API 精確驗證
```

**系統絕不得將 Bloom 命中視為「確定惡意」。** API 文件、SDK 文件、前端 UI 都必須明確說明此點（不變量 L8）。

### ⚠️ 覆蓋範圍的第二層限制（本版新增，必須寫入 client 文件）

public Bloom **只含 `TLP:CLEAR`**。因此：

```text
Bloom miss 不代表「此值不在 CTIP 情資集合中」
Bloom miss 只代表「此值不在公開（CLEAR）集合中」
```

`TLP:GREEN` 的情資**沒有任何 Bloom 覆蓋**——public Bloom 設計為可放 CDN、無租戶隔離，而 TLP 2.0 明確排除把 `GREEN` 放上公開可存取通道。tenant Bloom 只含該租戶的私有 IOC。

**不為 `GREEN` 另建第三份 Bloom 的理由**：那需要新增一個 bloom scope、一條發布路徑、以及「CDN 上的認證閘」的部署問題，而 `GREEN` 目前零資料量——違反抽象判準（[01](01-architecture.md#17-抽象判準強制)）。若日後 `GREEN` 量大，擴充點是 `BloomScope` enum 新增一個成員。

---

## 11.2 兩層架構

單份 Bloom 以 10M 容量 @ 0.1% FPR 計算約 18 MB；100 個 tenant 每次重建就是 1.8 GB。因此不做「每個 tenant 一份」，改為兩層：

```text
public bloom   ← 所有 TLP:CLEAR 且 status=ACTIVE 且可再散布的 IOC
                 全體共用一份，可放 CDN，無 tenant 隔離問題

tenant bloom   ← 僅含該 tenant 的私有 IOC（AMBER / AMBER_STRICT）
                 通常小兩到三個數量級
```

Client 同時查兩份，任一命中即走 API 精確驗證。

**成員條件（強制）**

| Bloom | 條件 |
|---|---|
| public | `owner_tenant_id = PUBLIC` AND `tlp = 'CLEAR'` AND `status = 'ACTIVE'` AND 非全部來源 `INTERNAL_ONLY` |
| tenant | `owner_tenant_id = :tenantId` AND `tlp IN ('AMBER','AMBER_STRICT')` AND `status = 'ACTIVE'` |

成員值一律為 `indicators.fingerprint`（`SHA-256(normalized_value)`），**不是** `normalized_value` 本身——client 無需傳送原值即可比對。

tenant Bloom 容量依 `plans.tenant_bloom_capacity`；`null` 表示該方案無 tenant Bloom。

---

## 11.3 Bloom 無法刪除元素

標準 Bloom filter **不支援移除成員**。因此：

```text
delta = 從 baseVersion 到 targetVersion 之間「新增」的 bit 索引集合
撤銷（REVOKED）或過期（EXPIRED）的 IOC 無法透過 delta 移除
移除只能靠 full snapshot 重建
```

這是 `datasetVersion`（full）與 `bloomVersion`（delta）兩個版號並存的**唯一理由**。

### 快照政策

| 項目 | 值 |
|---|---|
| Full snapshot | 每日一次（`BLOOM_SNAPSHOT_CRON`，預設 04:00），`datasetVersion` +1 |
| Delta | 日內每小時一次（`BLOOM_DELTA_CRON`），`bloomVersion` +1 |
| 強制重下 full 的條件 | delta 鏈 > `BLOOM_MAX_DELTA_CHAIN`（預設 24 段）**或** 累計 delta 大小 > full 的 30% |
| Artifact 保留 | 最近 `BLOOM_ARTIFACT_KEEP`（預設 30）個版本 |

超過條件時，`GET /api/v1/sync/delta` 回：

```json
{
  "timestamp": "2026-08-21T08:00:00Z",
  "status": 409,
  "code": "SNAPSHOT_REQUIRED",
  "message": "Delta chain too long, download full snapshot",
  "path": "/api/v1/sync/delta",
  "traceId": "..."
}
```

---

## 11.4 位元陣列格式（強制，互通性關鍵）

`resultingChecksum` 要求 client 能產生**位元組完全相同**的陣列，因此格式必須完全確定：

| 項目 | 規格 |
|---|---|
| 位元序 | bit index `i` 位於 byte `i / 8`，該 byte 內的第 `i % 8` 個 **最低有效位**（LSB-first） |
| 陣列長度 | `ceil(bitSize / 8)` bytes |
| 未使用的尾端位元 | 必須為 0 |
| 雜湊函式族 | 以 `fingerprint` 的十六進位字串解為位元組後，用 **Kirsch-Mitzenmacher** 雙雜湊：`h1 = 前 8 bytes 之 big-endian int64`、`h2 = 次 8 bytes 之 big-endian int64`，第 `i` 個索引 = `((h1 + i * h2) mod bitSize + bitSize) mod bitSize`

> **實作回饋修訂（2026-08-28；[ADR 0019](../architecture/decisions/0019-phase14-16-spec-resolutions.md)）**——兩項本節原本沒定義、但決定位元陣列內容的東西：
>
> 1. **`hashFunctionCount` 以公式為準。** 下方 manifest 範例原寫 `7`，但同一組參數
>    （n=10,000,000、p=0.001）代入本節公式得 `k = round((m/n) · ln2) = round(9.9648) = 10`。
>    範例的另外兩個數字（`bitSize` 143775880、`sizeBytes` 17971985）都是公式算出來的，
>    只有 k 不是。範例已改為 **10**。`BloomBitLayoutTest` 要斷言確切的 byte 陣列，
>    k 取 7 或 10 會產出完全不同的結果——這個值必須先定案才有辦法實作。
> 2. **`h1 + i * h2` 以 unsigned 64-bit wraparound（mod 2^64）計算。**
>    `h1`、`h2` 宣告為 int64，`i` 最大到 k-1，加乘在 64 位元下**必然溢位**；
>    而本節寫的是數學上的 `mod`，沒說是「先在 mod 2^64 下截斷」還是「任意精度」。
>    Java 的 `long` 會 wrap、JavaScript 的 `BigInt` 不會，**兩端算出的 index 不同**
>    ——而本節存在的理由正是「client 能產生位元組完全相同的陣列」。
>    定調為 wraparound（即 Java `long` 的自然行為）；非 Java client 必須自行截斷至 64 位元。（`i` 從 0 到 `k-1`） |
| `checksum` | **未壓縮**位元陣列的 `SHA-256`，十六進位小寫 |
| 壓縮 | `ZSTD`（預設）／`GZIP`／`NONE`，僅影響傳輸 |

參數推導（生成端）：

```text
m = ceil(-n * ln(p) / (ln 2)^2)         // bitSize
k = max(1, round((m / n) * ln 2))       // hashFunctionCount
```

`m` 向上取整至 8 的倍數。

> 不指定到這個程度的話，client 與伺服器算出的 bit index 會不同，`resultingChecksum` 永遠不符，delta 機制完全無法使用。v1.1 只寫「base64 varint-encoded delta」，不足以互通。

---

## 11.5 Metadata 與 API

### `GET /api/v1/sync/manifest`

```json
{
  "public": {
    "scope": "PUBLIC",
    "datasetVersion": 128,
    "bloomVersion": 42,
    "fingerprintAlgorithm": "SHA256",
    "hashFunctionCount": 10,
    "bitSize": 143775880,
    "capacity": 10000000,
    "falsePositiveRate": 0.001,
    "memberCount": 8342119,
    "checksum": "3f5a...",
    "sizeBytes": 17971985,
    "compression": "ZSTD",
    "generatedAt": "2026-08-21T04:00:00Z",
    "coverage": "TLP:CLEAR only"
  },
  "tenant": {
    "scope": "TENANT",
    "datasetVersion": 12,
    "bloomVersion": 3,
    "capacity": 1000000,
    "coverage": "TLP:AMBER, TLP:AMBER_STRICT of your tenant"
  },
  "notCovered": ["TLP:GREEN"],
  "maxDeltaChain": 24
}
```

`coverage` 與 `notCovered` 為**必填**——client 開發者必須在 manifest 就看到覆蓋範圍限制。

### `GET /api/v1/sync/bloom?scope=PUBLIC|TENANT`

回 `302` 至簽章下載 URL，或直接回二進位串流（依 `storage_kind`）。
下載授權依方案（`plans.public_bloom_enabled` / `tenant_bloom_capacity`）。

### `GET /api/v1/sync/delta?base=<n>&scope=`

```json
{
  "scope": "PUBLIC",
  "datasetVersion": 128,
  "baseVersion": 40,
  "targetVersion": 42,
  "addedBits": "<base64url of varint-delta-encoded sorted bit indices>",
  "addedMemberCount": 15320,
  "checksum": "sha256 of the addedBits payload before base64",
  "resultingChecksum": "sha256 of the bit array after applying this delta"
}
```

`addedBits` 編碼（強制）：

1. 收集新增的 bit 索引，**升序排序**、去重
2. 轉為差分序列：`d[0] = idx[0]`，`d[i] = idx[i] - idx[i-1]`
3. 每個 `d[i]` 以 **LEB128 unsigned varint** 編碼
4. 串接後 base64url（無 padding）

`resultingChecksum` 讓 client 套用後可自我驗證。不符則丟棄並重下 full。

---

## 11.6 Client 同步流程

```text
1. GET /api/v1/sync/manifest
2. 比對 fingerprintAlgorithm / hashFunctionCount / bitSize 與本地是否相同
      不同 → 本地 Bloom 立即作廢，跳至 4（下載 full）
3. 比對 datasetVersion
      不同 → 跳至 4
      相同 → GET /sync/delta?base=<本地 bloomVersion>
              409 SNAPSHOT_REQUIRED → 跳至 4
              200 → 套用 → 驗證 resultingChecksum
                     符合 → 更新本地版本，結束
                     不符 → 丟棄，跳至 4
4. GET /api/v1/sync/bloom → 驗證 checksum → 取代本地 → 更新版本
```

Client 必須能保存自己最後成功同步的 `datasetVersion` 與 `bloomVersion`。

同步頻率受 `plans.min_sync_interval_seconds` 限制；過於頻繁回 `429`。

### 精確驗證

Bloom 命中後以 `POST /api/v1/iocs/lookup` 精確驗證（**不是** `/sync/check`，該端點已移除，見 [09-api.md](09-api.md)）。

```json
{ "values": ["203.0.113.5", "evil.example.com"] }
```

單次筆數上限 `plans.max_batch_lookup`，超出回 `413 PAYLOAD_TOO_LARGE`。

---

## 11.7 Client 契約摘要（必須複製進 SDK 與 API 文件）

| # | 契約 |
|---|---|
| 1 | Bloom **命中不代表惡意**，必須呼叫 `/iocs/lookup` 精確驗證 |
| 2 | Bloom **未命中不代表安全**——public Bloom 只覆蓋 `TLP:CLEAR`，`TLP:GREEN` 完全無覆蓋 |
| 3 | 撤銷與過期的 IOC **無法透過 delta 移除**，只有 full snapshot 才會反映 |
| 4 | `fingerprintAlgorithm`／`hashFunctionCount`／`bitSize` 任一改變即須重下 full |
| 5 | 套用 delta 後必須驗證 `resultingChecksum`，不符則重下 full |
| 6 | 位元序為 LSB-first；索引算法見 11.4，**不得自行選用其他雜湊族** |

---

*檔案結束。上次校對：2026-08-21。*
