# Phase 14 — Plan · Subscription · 配額 ＋ IOC 寫入端點  `[M2]`

## 前置條件
- Phase 13 完成判準全綠

## 交付物
- Flyway `V28`–`V29`：`plans`、`subscriptions` + 四個方案種子（**14** 個配額維度；
  原寫 15，實際 §10.6 配額表與 §4.x `plans` 欄位皆為 14，見 ADR 0016）
- **新權限 `subscription:read`**：seed migration + [10 §10.3](../10-identity-plans.md#角色與權限矩陣) 的清單與矩陣
  + `RbacMatrix` 測試常數，**三處同步**（`RbacMatrixTest` 會逐格比對）
- **放寬三處配額型別**以承載種子表的 `0`（停用）與 `null`（無限制）：`ApiKeySettings`、
  `StixExportSettings`、`CtipProperties.Api` 的 `@Positive`——否則應用啟動即失敗（ADR 0019）
- **`RateLimiterPort` 改為可傳 per-key 限額**、`RateLimitResult.limit` 可表示「無上限」（ADR 0019）
- `import_jobs` 表（`V28`）：`GET /iocs/import/{jobId}` 的狀態承載
- `db/seed/sample_data.sql` 補方案／訂閱樣本（[14 §14.7](../14-testing.md#147-測試資料) 要求，
  Phase 16 的 `SyncEndToEndTest` 依賴它）
- `Subscription` 聚合（5 條不變量）+ `QuotaService`
- `.env` 覆寫機制（`CTIP_PLAN_<CODE>_<FIELD>`）
- 配額強制：分頁上限、批次上限、STIX 物件數、webhook/apikey 數量、手動提交/日、匯入筆數
- **IOC 寫入**：
  - `ManualSubmissionAdapter`（`sourceType = MANUAL`、`defaultTlp = AMBER`、`redistributionPolicy = INTERNAL_ONLY`）
  - `POST /api/v1/iocs`（`ioc:submit`）
  - `POST /api/v1/iocs/import`（`ioc:import`，非同步，回 202 + jobId）
  - `POST /api/v1/iocs/{id}/report-false-positive`（`ioc:report-fp`）
- 端點：`/subscription`、`/subscription/usage`
- 測試：`QuotaEnforcementTest`、`ManualSubmissionTest`、`FalsePositiveReportTest`

## 治理規格
- [10-identity-plans.md §10.6](../10-identity-plans.md#106-方案)
- [09-api.md §9.7](../09-api.md#97-寫入端點細節-m2)
- [08-ingestion-sdk.md](../08-ingestion-sdk.md#manualsubmissionadapter-phase-14--m2)

> **不含限流維度 1–3**（2026-08-28 定調；[ADR 0020](../../architecture/decisions/0020-phase17-19-spec-resolutions.md)）：
> 它們需要依方案查表的 per-key 限額，與 Redis 後端一起在 **Phase 17** 做。
> 本 phase 只負責 `plans` 表與配額值本身。

- 前端頁面 `/iocs/new`、`/iocs/import`、`/settings/subscription`（[12](../12-frontend.md) 標 M2，原本無 phase 承接；ADR 0022）

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration \
  -Dtest='QuotaEnforcementTest,ManualSubmissionTest,FalsePositiveReportTest'
```
`ManualSubmissionTest` 必須驗證：預設 `TLP:AMBER`、走完整 pipeline（含驗證與去重）、擁有租戶看得到自己的資料（再散布過濾不作用於自己）。
`FalsePositiveReportTest` 必須驗證最終 status 由 `IndicatorMergePolicy` 決定，而非呼叫端指定。

## 不得做的事
- **不得 hard-code 任何配額數值**（一律讀 `plans` 表）
- 不得在 `plans` 表新增任何 TLP 相關欄位
- 不得讓提交者指定 `owner_tenant_id`
- 不得讓非 `SYSTEM_ADMIN` 把提交的 IOC 設為 `CLEAR`/`GREEN`（需 `ioc:publish`）
- 不得為手動提交寫第二套資料品質邏輯（複用 pipeline）
- 不得串接真實金流
- 誤判回報不得影響 public tenant 的公開情資
