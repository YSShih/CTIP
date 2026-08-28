# Phase 10 — OpenAPI / Swagger  `[M1]`

## 前置條件
- Phase 9 完成判準全綠

## 交付物
- springdoc-openapi 3.1.0 設定（`SWAGGER_ENABLED` 控制）
- 每個端點的 `@Operation`：summary、description、request/response schema、錯誤回應、認證需求、**至少一個範例**
- `docs/api/openapi.json` 產出並 commit
- `.github/workflows/openapi-check.yml`：產生 → 比對 committed 版本 → 檢查破壞性變更
- `OpenApiCompletenessTest`（解析 `/v3/api-docs` 逐端點檢查必要欄位齊備）

## 治理規格
- [09-api.md §9.6](../09-api.md#96-openapi--swagger)

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration -Dtest=OpenApiCompletenessTest
./environment/scripts/up.sh mvp
curl -fsS http://localhost:8080/swagger-ui/index.html > /dev/null
curl -fsS http://localhost:8080/v3/api-docs | jq -e '.paths | length > 0'
```

## 不得做的事
- 不得使用 springdoc 2.x（不相容 Spring Boot 4）
- 不得移除 `-parameters` 編譯旗標
- 不得在 prod 預設開啟 Swagger
- 不得手改 `docs/api/openapi.json`（由建置產生）
