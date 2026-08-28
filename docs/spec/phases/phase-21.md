# Phase 21 — Audit Log + 資料保留  `[M3]`

## 前置條件
- Phase 20 完成判準全綠

## 交付物
- Flyway `V33`：`audit_logs` + `REVOKE UPDATE, DELETE ON audit_logs FROM <app_role>` + 專用角色 `ctip_retention`
- `AuditPort` + 非同步寫入 + 本地**有界**佇列（溢出記 ERROR，不阻塞）
- 26 種稽核行為，依 [13 §13.5 觸發點對照表](../13-platform-ops.md#觸發點對照表強制26-種行為) 逐一實作（含新增的 `IOC_SUBMIT`、`IOC_IMPORT`、`IOC_REPORT_FP`）
- 取樣：寫入 100%、讀取 1%（`AUDIT_SAMPLE_READ_RATE`）
- 六個保留清理任務（分批 ≤10,000 列，記錄清理筆數，失敗不影響其他）
- 端點 `GET /api/v1/audit-logs`（`audit:read`）
- 前端 `pages/AuditLogPage`
- 資料主體查詢與刪除的管理端點
- 測試：`AuditAppendOnlyTest`、`AuditFailureIsolationTest`、`RetentionTaskTest`、`AuditCompletenessTest`

## 治理規格
- [13-platform-ops.md §13.4、§13.5](../13-platform-ops.md#134-隱私與資料保留)
- [04-data-dictionary.md](../04-data-dictionary.md)（表 27 + §4.5 稽核行為）

- 前端頁面 **Admin Panel**（`/admin`，[12](../12-frontend.md) 標 M3，原本無 phase 承接；ADR 0022）

- **`POST /api/v1/auth/change-password`**（[09](../09-api.md) 全文沒有這個端點，而
  [ADR 0015](../../architecture/decisions/0015-future-phase-hardening.md) 把「`User.changePassword`
  必須一併撤銷該使用者全部 token family」指定為「M3 責任」——但 M3 四個 phase 都沒有承接它。
  本 phase 補上端點 **與** family 撤銷；ADR 0022）

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration \
  -Dtest='AuditAppendOnlyTest,AuditFailureIsolationTest,RetentionTaskTest,AuditCompletenessTest'
```
`AuditAppendOnlyTest` 必須驗證應用角色的 `UPDATE` 與 `DELETE` 被 **DB** 拒絕（不是被應用碼拒絕）。
`AuditFailureIsolationTest` 必須驗證稽核寫入失敗時業務操作仍成功。
`AuditCompletenessTest` 必須驗證 26 種 `action` 皆有實際寫入路徑（無永不可達的行為）。

## 不得做的事
- `audit_logs` **不得有 `updated_at` 欄位**
- `metadata` 不得含憑證、token 原文、密碼、`Authorization` 標頭
- 不得讓稽核寫入失敗使業務操作失敗
- 不得以應用角色執行保留清理（用 `ctip_retention`）
- 不得用單一大交易清理（必須分批）
