# Phase 8 — STIX 2.1 正規化與匯出  `[M1]`

## 前置條件
- Phase 7 完成判準全綠

## 交付物
- `ctip-core/domain/stix/`：STIX 物件模型 + **允許手寫 builder**（唯一例外）
- `StixTlpMarkings`：五個 `static final` marking-definition 常數（**固定 UUID**）
- `StixPatternBuilder`：六種 `IocType` 的 pattern 模板 + `IocHashType` → hashing-algorithm-ov 對應（`SHA256` → `SHA-256`）
- `StixPatternEscaper`（單引號與反斜線跳脫）
- `StixProjectionStage`（pipeline 第 8 stage）→ `stix_objects`
- `StixExportService`：`bundle` 匯出（只含被引用的 marking、依方案限物件數、經 TLP 與再散布過濾）
- 端點 `GET /api/v1/stix/{stixId}`、`GET /api/v1/stix/bundle`
- 測試：`StixTlpMarkingsTest`、`StixPatternTest`、`StixSchemaValidationTest`

## 治理規格
- [07-domain-intel.md §7.8](../07-domain-intel.md#78-stix-21-映射) 全節
- [04-data-dictionary.md](../04-data-dictionary.md)（`stix_objects`、`stix_relationships`）

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration \
  -Dtest='StixTlpMarkingsTest,StixPatternTest,StixSchemaValidationTest'
```
`StixTlpMarkingsTest` 必須以字面字串斷言五個 UUID 與 extension-definition ID 完全相符。
`StixSchemaValidationTest` 必須以 STIX 2.1 JSON Schema 驗證實際產出。

## 不得做的事
- **不得**以 `UUID.randomUUID()` 產生 marking-definition ID
- 不得把 enum 名稱直接塞進 pattern（`SHA256` 必須轉為 `SHA-256`）
- **M1 不得產生** `malware`、`attack-pattern`、`observed-data`、`identity`、`relationship`、`course-of-action`（無資料來源，M2 才做）
- 不得把 `stix_objects` 當 source of truth
- STIX 投影失敗不得使 ingestion 失敗
