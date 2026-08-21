# Phase 6 — Ingestion Pipeline + 資料品質 + 排程 ＋記憶體限流  `[M1]`

## 前置條件
- Phase 5 完成判準全綠

## 交付物
- `IngestionStage` 介面 + 十個 stage 實作（Parse → EventPublish）
- `IngestionPipeline`（持有 `List<IngestionStage>`）+ `IngestionPipelineConfig`（顯式 `List.of(...)`）
- 正規化：每個 `IocType` 一個 `IocNormalizer`（七條規則）
- 拒絕規則：八種 `RejectionReason`，寫入 `ingestion_rejections`
- 批次與交易邊界（fetch 在交易外、每批 500 一交易、單筆失敗不 rollback）
- `source_sync` 記錄
- 排程：來源同步、IOC 過期標記（每日 03:00）、失敗重試（每 15 分）
- **記憶體限流**：`RateLimiterPort` + `InMemoryRateLimiter`（Bucket4j），套用全端點
- 測試：`NormalizationTest`、`RejectionRuleTest`、`IngestionEndToEndTest`、限流 429 測試

## 治理規格
- [08-ingestion-sdk.md §8.2、§8.7](../08-ingestion-sdk.md)
- [07-domain-intel.md §7.2、§7.3](../07-domain-intel.md#72-正規化規則強制)
- [10-identity-plans.md §10.7](../10-identity-plans.md#107-限流)（Phase 歸屬修正）
- [03-diagrams.md §3.4](../03-diagrams.md#34-ingestion-協作圖)

## 完成判準
```bash
./mvnw -f backend/pom.xml verify -Ptest-integration \
  -Dtest='NormalizationTest,RejectionRuleTest,IngestionEndToEndTest,RateLimitTest'
```
`RejectionRuleTest` 必須覆蓋八種 reason 各至少一案例。
`NormalizationTest` 必須覆蓋 [07 §7.2](../07-domain-intel.md#72-正規化規則強制) 表格每一列。

## 不得做的事
- **不得**用抽象基底類別 + 繼承實作 pipeline
- 不得依賴 `@Order` 決定 stage 順序（用顯式 `List.of`）
- 不得把 adapter fetch 包在交易內
- 不得讓單筆失敗導致整批 rollback
- `ALLOWLISTED_DOMAIN` **不得**做後綴比對（只做 exact match，且不套用於 URL 型別）
- 不得靜默丟棄任何記錄（一律寫入 `ingestion_rejections`）
