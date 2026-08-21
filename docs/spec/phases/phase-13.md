# Phase 13 — 認證 · RBAC · API Key · 租戶隔離強制  `[M2]`

## 前置條件
- **`./environment/scripts/dod.sh mvp` 38 項全綠**

## 交付物
- Flyway `V20`–`V21`、`V24`：`users`、`roles`、`permissions`、`role_permissions`、`tenant_users`、`refresh_tokens`、`api_keys` + RBAC 種子
- `User` 聚合（7 條不變量）+ `RefreshToken` 內部實體
- `ApiKey` 聚合（7 條不變量）
- `AuthService`：register / login / refresh（輪替 + 重用偵測）/ logout
- `ApiKeyService`：issue（`IssuedApiKey`，原文只回一次）/ revoke / 驗證（前綴定位 + 雜湊比對）
- 安全層擴充：`AuthState` 由 `ANONYMOUS|AUTHENTICATED` 擴充為完整身分；`@PreAuthorize` + 集中 `PermissionEvaluator`
- `StartupValidator` 的 JWT 守衛生效
- 端點：`/auth/*`、`/api-keys`
- 測試：`AuthFlowIntegrationTest`、`RefreshTokenRotationTest`、`RbacMatrixTest`、`ApiKeyTest`、`CrossTenantIsolationTest`、`SecurityTest`（1–9 全部）

## 治理規格
- [10-identity-plans.md §10.3–§10.5](../10-identity-plans.md#103-使用者與-rbac-phase-13--m2)
- [02-ddd-model.md](../02-ddd-model.md#user)（User / ApiKey 不變量）
- [04-data-dictionary.md](../04-data-dictionary.md)（表 10–16）

## 完成判準
```bash
./mvnw -f backend/pom.xml verify -Ptest-integration \
  -Dtest='AuthFlowIntegrationTest,RefreshTokenRotationTest,RbacMatrixTest,ApiKeyTest,CrossTenantIsolationTest,SecurityTest'
```
`RbacMatrixTest` 必須以參數化涵蓋 [10 §10.3](../10-identity-plans.md#角色與權限矩陣) 矩陣**每一格**。
`CrossTenantIsolationTest` 必須以參數化涵蓋**每一個** tenant-scoped 端點，全部回 404。

## 不得做的事
- 不得在 controller 散落 `if (role == ...)`
- 不得儲存 refresh token 原文
- 不得讓 API key 的 scope 超出建立者權限
- 不得讓 public tenant 有 user / api key（DB CHECK 已強制）
- 跨租戶不得回 403（必須 404）
- 不得把 email 或姓名放進 JWT claims
- **不得改動 Phase 4 建立的 TLP 過濾邏輯**（本 Phase 只加身分，不改 query）
