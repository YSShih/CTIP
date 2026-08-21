# Phase 19 — Elasticsearch 搜尋 + reconciliation + 降級  `[M2]`

## 前置條件
- Phase 18 完成判準全綠

## 交付物
- `ElasticsearchSearchAdapter` 實作 `SearchPort`
- `FallbackSearchAdapter`（Resilience4j circuit breaker，ES 不可用時降級至 `PostgresSearchAdapter`）
- 回應標頭 `X-Search-Backend: elasticsearch|postgres`
- `SearchIndexStage` 插入 pipeline
- 模糊查詢（typosquatting 偵測）
- Reconciliation 排程（每日 05:00）比對 DB 與 ES 筆數與版本並修正
- 測試：`ElasticsearchSearchTest`（L4）、`SearchFallbackTest`、`SearchReconciliationTest`

## 治理規格
- [13-platform-ops.md §13.7](../13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch)
- [06-tech-stack.md §6.5](../06-tech-stack.md#65-授權注意事項)（OpenSearch 替代）

## 完成判準
```bash
./mvnw -f backend/pom.xml verify -Ptest-all \
  -Dtest='ElasticsearchSearchTest,SearchFallbackTest,SearchReconciliationTest'
./environment/scripts/up.sh staging
./environment/scripts/dod.sh phase2        # ← 整個 DoD-Phase2，27 項
```
`SearchFallbackTest` 必須驗證 ES 停止時 API 回 **200**（非 500）且帶 `X-Search-Backend: postgres`。

## 不得做的事
- 不得讓 ES 成為 source of truth
- 不得讓索引失敗使 ingestion 失敗
- 不得在 controller 判斷降級（用 `FallbackSearchAdapter`）
- 不得使用 Elasticsearch 9.3（已 EOL，用 9.5.x）
- 不得讓 `ElasticsearchSearchAdapter` 的型別洩漏到 `application` 層

## 里程碑閘門
**此 Phase 結束後執行 `./environment/scripts/dod.sh phase2`。27 項全綠才可進入 Phase 20。**
