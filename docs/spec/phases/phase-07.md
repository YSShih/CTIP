# Phase 7 — 去重 · 合併 · 指紋 · 評分  `[M1]`

## 前置條件
- Phase 6 完成判準全綠

## 交付物
- `FingerprintStrategy` + `Sha256FingerprintStrategy`（**M1 只實作 SHA256**）
- `hash_records` 寫入
- `IndicatorMergePolicy` 完整實作：八欄聚合規則 + `status` 四條判定分支 + `confidence` 加權公式
- 三步 `valid_until` 計算（`COALESCE(source_valid_until, source_last_seen + defaultTtl(type))` → `MAX`）
- `indicator_sources` UPSERT 語意 + `report_count` 遞增
- `ThreatScorer` + `RuleBasedThreatScorer`（四項權重）
- 測試：`IndicatorMergePolicyTest`、`ValidityPeriodTest`、`ThreatScorerTest`、`FingerprintTest`

## 治理規格
- [07-domain-intel.md §7.4、§7.5、§7.6](../07-domain-intel.md#74-去重與指紋)
- [04-data-dictionary.md §4.6](../04-data-dictionary.md#46-過期與-ttl-規則強制跨表)（TTL 三步規則）
- [02-ddd-model.md](../02-ddd-model.md#indicator)（不變量 I5–I11）

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration \
  -Dtest='IndicatorMergePolicyTest,ValidityPeriodTest,ThreatScorerTest,FingerprintTest'
```
`ValidityPeriodTest` 必須包含兩個關鍵分支：來源**未明示** `validUntil`（走型別預設 TTL）、`FILE_HASH`（結果為 null）。
`IndicatorMergePolicyTest` 必須包含三來源重疊、其中一個 `RETRACTED`（reputation ≥ 80）的案例。
`ThreatScorerTest` 必須包含一個驗證「`confidence` 與 `score` 的來源數定義一致」的案例。

## 不得做的事
- 不得以 `fingerprint` 作為識別鍵（識別鍵是 `(type, normalized_value, owner_tenant_id)`）
- 不得對 `value` 計算指紋（必須對 `normalized_value`）
- 不得實作 `Sha512FingerprintStrategy`（介面保留即可）
- 不得實作任何 ML 評分
- 不得讓 `IndicatorMergePolicy` 持有 repository（reputation 以參數傳入）
- 不得沿用 v1.1 的「任一來源 `validUntil` 為 null 則結果為 null」
