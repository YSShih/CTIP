# Phase 5 — SDK + Mock Adapter + 韌性 + 來源健康  `[M1]`

## 前置條件
- Phase 4 完成判準全綠

## 交付物
- `ctip-sdk`：`ThreatSourceAdapter`、`SourceMetadata`、`FetchContext`、`FetchResult`、`RawThreatRecord`
- `ctip-adapters/mock/`：`MockOpenPhishAdapter`、`MockAbuseIPDBAdapter`、`MockAlienVaultAdapter`
- `ctip-adapters/http/`：共用 HTTP 基礎設施 + Resilience4j 裝配（timeout / retry+jitter / circuit breaker / bulkhead）
- `ctip-app/infrastructure/source/AdapterRegistry`
- `SourceHealth` 狀態機（S2–S4）+ `SourceHealthService`
- `SourceSyncService`（逐一處理各來源，單一失敗不影響其他）
- 三個 mock 的確定性測試 + 韌性測試

## 治理規格
- [08-ingestion-sdk.md §8.1、§8.3、§8.5、§8.6](../08-ingestion-sdk.md)
- [02-ddd-model.md §2.5](../02-ddd-model.md#25-shared-kernelctip-sdk)（Shared Kernel 邊界）

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration \
  -Dtest='MockAdapterDeterminismTest,ResilienceTest,SourceHealthTest'
./backend/mvnw -f backend/pom.xml test -Dtest=ArchitectureTest
```
確定性測試必須：同一 `FetchContext` 連續呼叫兩次，結果 `equals`。

## 不得做的事
- mock adapter 不得使用 `Math.random()` 或無 seed 的 `Random`
- 不得啟用任何真實外部來源（`sources.enabled = false`）
- 不得在 `sources.config` 存憑證原文
- 不得寫 Factory 類別（用 Spring 注入 `List<ThreatSourceAdapter>`）
- `ctip-adapters` 不得依賴 `ctip-core`
- MVP 只啟用 `MockOpenPhishAdapter`
