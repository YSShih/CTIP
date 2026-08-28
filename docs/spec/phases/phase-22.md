# Phase 22 — 監控 · 日誌 · 追蹤  `[M3]`

## 前置條件
- Phase 21 完成判準全綠

## 交付物
- Actuator + Micrometer + Prometheus + Grafana（`full` profile）
- `environment/config/monitoring/prometheus/`、`grafana/`（provisioning）
- 完整指標，**含 `ctip.ingestion.stage.duration{stage}`**（每個 pipeline stage 獨立度量）
- prod 僅暴露 `health`、`info`、`prometheus`，且 `prometheus` 限制來源 IP
- 結構化 JSON 日誌（`logstash-logback-encoder`），九個必含欄位
- 敏感欄位遮罩
- OpenTelemetry 追蹤：API → service → DB / Redis / Kafka / ES
- `traceId` 同時出現在錯誤回應與日誌
- 測試：`MetricsCompletenessTest`、`SensitiveLogTest`、`TracePropagationTest`

## 治理規格
- [13-platform-ops.md §13.6](../13-platform-ops.md#136-監控日誌追蹤-phase-22--m3)
- [09-api.md §9.4](../09-api.md#94-統一錯誤回應)（`traceId`）

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-all \
  -Dtest='MetricsCompletenessTest,SensitiveLogTest,TracePropagationTest'
./environment/scripts/up.sh dev
curl -fsS http://localhost:8080/actuator/prometheus | grep -q 'ctip_ingestion_stage_duration'
```
`MetricsCompletenessTest` 必須比對 [13 §13.6](../13-platform-ops.md#監控) 的指標清單逐項存在。
`SensitiveLogTest` 必須驗證日誌不含密碼、JWT secret、API key 原文、refresh token 原文、`Authorization` 值。

## 不得做的事
- 不得在 prod 暴露 `env`、`beans`、`configprops`、`heapdump` 等端點
- 不得記錄任何憑證
- 不得省略 `ctip.ingestion.stage.duration`（這是顯式 stage 列表設計的直接收益）
