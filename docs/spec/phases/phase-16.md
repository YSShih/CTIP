# Phase 16 — 增量同步 API 與 client 契約  `[M2]`

## 前置條件
- Phase 15 完成判準全綠

## 交付物
- `GET /api/v1/sync/manifest`（**必含 `coverage` 與 `notCovered`**）
- `GET /api/v1/sync/bloom?scope=`
- `GET /api/v1/sync/delta?base=&scope=`（含 `409 SNAPSHOT_REQUIRED`）
- `addedBits` 編碼：升序去重 → 差分 → LEB128 varint → base64url（無 padding）
- 強制重下 full 的判定（delta 鏈 > 24 或累計 > full 的 30%）
- 同步頻率限制（`plans.min_sync_interval_seconds`）
- `docs/api/` 的 client 契約文件（六條，見 [11 §11.7](../11-sync-bloom.md#117-client-契約摘要必須複製進-sdk-與-api-文件)）
- 前端 `pages/SyncPage`（Bloom 說明頁，明文說明命中與未命中的語意）
- 測試：`SyncEndToEndTest`

## 治理規格
- [11-sync-bloom.md §11.5–§11.7](../11-sync-bloom.md#115-metadata-與-api)
- [09-api.md](../09-api.md#同步-m2)

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration -Dtest=SyncEndToEndTest
cd frontend && npm run test -- SyncPage
```
`SyncEndToEndTest` 必須跑完整流程：manifest → delta → 套用 → 驗證 `resultingChecksum` → 更新版本，並包含一次 `409 SNAPSHOT_REQUIRED` 分支。

## 不得做的事
- **不得**建立 `POST /api/v1/sync/check`（與 `/iocs/lookup` 重複，已移除）
- manifest 不得省略 `coverage` / `notCovered`
- 不得在文件或 UI 暗示 Bloom miss 代表安全
- 不得省略 `resultingChecksum`
