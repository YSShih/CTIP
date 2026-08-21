# Phase 18 — Threat 實體與關聯 + M2 的 STIX 物件  `[M2]`

## 前置條件
- Phase 17 完成判準全綠

## 交付物
- Flyway `V25`：`threats`、`threat_indicators`、`threat_external_references`
- `Threat` 聚合（6 條不變量）+ `ThreatIndicatorLink` + `ExternalReference`
- `ThreatRepository` port + adapter
- 端點：`GET /threats`、`GET /threats/{id}`、`GET /threats/{id}/indicators`
- M2 的 STIX 物件：`malware`、`attack-pattern`、`observed-data`、`identity`、`relationship`、`course-of-action`
- `stix_relationships` 寫入
- 前端 `pages/ThreatFeedPage`、`pages/ThreatDetailPage`
- 測試：`ThreatIntegrationTest`、STIX M2 物件的 schema 驗證

## 治理規格
- [02-ddd-model.md](../02-ddd-model.md#threat)（H1–H6）
- [04-data-dictionary.md](../04-data-dictionary.md)（表 19–21）
- [07-domain-intel.md §7.8.1](../07-domain-intel.md#781-支援範圍與里程碑)
- [03-diagrams.md §3.2.7](../03-diagrams.md#327-threat-聚合-m2)

## 完成判準
```bash
./mvnw -f backend/pom.xml verify -Ptest-integration \
  -Dtest='ThreatIntegrationTest,StixSchemaValidationTest'
cd frontend && npm run test -- ThreatFeedPage ThreatDetailPage
```

## 不得做的事
- `ThreatIndicatorLink` **不得持有 `Indicator` 物件**（只存 `IndicatorId`）
- `ExternalReference` 不得存為 JSONB（必須是 `threat_external_references` 表）
- `Threat.tlp` 不得比其任一關聯 Indicator 更寬鬆（H6）
