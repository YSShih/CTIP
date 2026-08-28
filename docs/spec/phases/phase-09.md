# Phase 9 — REST API + DTO/Mapper + 錯誤處理 + cursor 分頁  `[M1]`

## 前置條件
- Phase 8 完成判準全綠

## 交付物
- Controller：health、version、iocs（list/detail/sources/search/lookup）、stats、sources、stix
- 所有 DTO 為 `record`，置於 `interfaces/rest/dto/`
- MapStruct mapper（domain ↔ DTO）
- `CursorCodec`（base64url of `{"ls":…,"id":…}`）
- `@RestControllerAdvice` + 16 個錯誤碼
- 輸出過濾五步順序（TenantContext → TLP → status → RedistributionFilter → DTO）
- `RedistributionFilter`（**集中一處**，作用域為跨租戶與公開輸出）
- 測試：`CursorPaginationIntegrationTest`、`IocSearchIntegrationTest`、`ErrorResponseTest`、`SecurityTest`

## 治理規格
- [09-api.md](../09-api.md) 全檔（特別是 §9.3 分頁、§9.4 錯誤、§9.5 DTO 與輸出過濾順序）
- [07-domain-intel.md §7.9](../07-domain-intel.md#79-再散布政策法遵強制)

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration \
  -Dtest='CursorPaginationIntegrationTest,IocSearchIntegrationTest,ErrorResponseTest,SecurityTest'
./backend/mvnw -f backend/pom.xml test -Dtest=ArchitectureTest
```
`CursorPaginationIntegrationTest` 必須連續翻頁至最後一頁並驗證無重複、無遺漏。
`SecurityTest` 必須包含安全測試 1、3、7、9。

## 不得做的事
- 不得回傳 JPA entity
- 不得使用 Spring Data 的 `Page`（用 `CursorPage`）
- 不得在 controller 手動傳 `tenantId`
- 不得把 TLP 過濾寫在 controller（必須在 Specification 層）
- 不得讓 `RedistributionFilter` 邏輯散落各 controller
- 不得洩漏 stack trace 給 client
- 不得實作 M2／M3 端點
