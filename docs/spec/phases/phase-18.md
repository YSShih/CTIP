# Phase 18 — Threat 實體與關聯 + M2 的 STIX 物件  `[M2]`

## 前置條件
- Phase 17 完成判準全綠

## 交付物
- Flyway `V31`：`threats`、`threat_indicators`、`threat_external_references`
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

> **實作前必讀（2026-08-28；[ADR 0020](../../architecture/decisions/0020-phase17-19-spec-resolutions.md)）**
>
> - `threats.aliases` 是 `TEXT[]`，與 `indicators.tags` 同型——**`@>` 包含查詢必須重用
>   `PostgresFunctionContributor` 的 `cast(? as text[])` 模式**，否則 Hibernate 會把
>   `String[]` 綁成 `varchar[]` 而 `operator does not exist`（[13 §13.7](../13-platform-ops.md)）
> - **H6 改由 application 層強制**（見 [02 §2.3](../02-ddd-model.md)）；Indicator TLP 收緊時
>   須連帶收緊關聯 Threat
> - `ux_ter_identity` 必須以 `COALESCE(external_id, '')` 建唯一索引，否則
>   `external_id IS NULL` 時 H4 不被強制（[04 表 21](../04-data-dictionary.md)）
> - `stix_objects` **沒有 `threat_id` 索引**，而 `V31` 要加的 FK 帶 `ON DELETE CASCADE`——一併補索引
> - `/threats` 三端點的**可見度述詞未定義**：`threats` 表無 `deleted_at`、無來源記錄，
>   §7.9 規則 3 沒有對應物。`TlpSpecifications` 是 `Specification<IndicatorEntity>`，不能直接重用
> - M2 只投影 `MALWARE_FAMILY` 與 `ATTACK_PATTERN`；`course-of-action` 已自 §7.8 移除
>   （沒有資料來源，屬規則 16 的 placeholder）

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration \
  -Dtest='ThreatIntegrationTest,StixSchemaValidationTest'
cd frontend && npm run test -- ThreatFeedPage ThreatDetailPage
```

## 不得做的事
- `ThreatIndicatorLink` **不得持有 `Indicator` 物件**（只存 `IndicatorId`）
- `ExternalReference` 不得存為 JSONB（必須是 `threat_external_references` 表）
- `Threat.tlp` 不得比其任一關聯 Indicator 更寬鬆（H6）
