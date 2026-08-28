# Phase 15 — Bloom Filter（兩層 · snapshot · delta）  `[M2]`

## 前置條件
- Phase 14 完成判準全綠

## 交付物
- Flyway `V30`：`bloom_versions`、`bloom_artifacts`
- `BloomVersion` 聚合（8 條不變量）+ `BloomArtifact` + `BloomParameters` + `Checksum`
- `BloomStoragePort` + `infrastructure/bloom/`：位元運算與序列化
- **位元格式嚴格依 [11 §11.4](../11-sync-bloom.md#114-位元陣列格式強制互通性關鍵)**：LSB-first、Kirsch-Mitzenmacher 雙雜湊、`m` 向上取整至 8 的倍數
- 兩層生成：public（`tlp = CLEAR`）與 tenant（`AMBER`/`AMBER_STRICT`）
- 排程：full snapshot（每日 04:00）、delta（每小時）
- `BloomUpdateStage` 插入 pipeline（`PersistStage` 之後）
- 測試：`BloomGenerationTest`、`BloomBitLayoutTest`、`BloomDeltaTest`、`BloomCoverageTest`

## 治理規格
- [11-sync-bloom.md §11.1–§11.4](../11-sync-bloom.md)
- [02-ddd-model.md](../02-ddd-model.md#bloomversion)（L1–L8）

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration \
  -Dtest='BloomGenerationTest,BloomBitLayoutTest,BloomDeltaTest,BloomCoverageTest'
```
`BloomBitLayoutTest` 必須以固定的 fingerprint 輸入斷言**確切的 byte 陣列**（不只是「命中」）。
`BloomCoverageTest` 必須驗證 `TLP:GREEN` 的 IOC **不在** public bloom 中。
`BloomDeltaTest` 必須驗證套用 delta 後的陣列 checksum 等於 `resultingChecksum`。

> **實作前必讀（2026-08-28；[ADR 0019](../../architecture/decisions/0019-phase14-16-spec-resolutions.md)）**
>
> - `hashFunctionCount` 以 [§11.4](../11-sync-bloom.md) 的公式為準（範例已由 7 更正為 10）
> - `h1 + i * h2` 以 **unsigned 64-bit wraparound** 計算——非 Java client 必須自行截斷
> - **不得引入任何 Bloom 函式庫**（[06 §6.2.2](../06-tech-stack.md#62-版本表) 明列「自行實作」）：
>   §11.4 的 layout 排除所有現成實作
> - ⚠️ `Indicator.eligibleForBloom()` 內含 `hasRedistributableSource()`。**若沿用它判斷 tenant scope，
>   tenant bloom 會恆為空**——手動提交的來源政策固定 `INTERNAL_ONLY`，而 §11.2 的 tenant bloom
>   成員條件（owner + AMBER/AMBER_STRICT + ACTIVE）**沒有再散布條件**。兩種 scope 需要不同的述詞
> - `IndicatorRepository` 目前 8 個方法都是單筆／分頁／過期查詢，**沒有串流全部 fingerprint 的方法**；
>   full snapshot（10M 成員）需新增
> - artifact 路徑用 `BLOOM_STORAGE_DIR`，已有 `bloom-data` volume

## 不得做的事
- **不得把 Bloom 命中視為確定惡意**（任何此類程式碼皆為違規）
- 不得把 `TLP:GREEN` 放進 public bloom
- 不得為 `GREEN` 建立第三份 bloom
- 不得每個 tenant 一份 full-size bloom（用兩層架構）
- 不得以 `normalized_value` 作為 bloom 成員（用 `fingerprint`）
- 不得自行選用其他雜湊族
