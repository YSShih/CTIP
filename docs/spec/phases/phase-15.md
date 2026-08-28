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
./mvnw -f backend/pom.xml verify -Ptest-integration \
  -Dtest='BloomGenerationTest,BloomBitLayoutTest,BloomDeltaTest,BloomCoverageTest'
```
`BloomBitLayoutTest` 必須以固定的 fingerprint 輸入斷言**確切的 byte 陣列**（不只是「命中」）。
`BloomCoverageTest` 必須驗證 `TLP:GREEN` 的 IOC **不在** public bloom 中。
`BloomDeltaTest` 必須驗證套用 delta 後的陣列 checksum 等於 `resultingChecksum`。

## 不得做的事
- **不得把 Bloom 命中視為確定惡意**（任何此類程式碼皆為違規）
- 不得把 `TLP:GREEN` 放進 public bloom
- 不得為 `GREEN` 建立第三份 bloom
- 不得每個 tenant 一份 full-size bloom（用兩層架構）
- 不得以 `normalized_value` 作為 bloom 成員（用 `fingerprint`）
- 不得自行選用其他雜湊族
