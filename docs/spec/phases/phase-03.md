# Phase 3 — Spring Boot + PostgreSQL + Flyway + 種子資料  `[M1]`

## 前置條件
- Phase 2 完成判準全綠

## 交付物
- `CtipApplication.java`
- `application.yml` + `application-{mvp,dev,staging,prod}.yml`，依 [05 §5.7](../05-environment.md#57-spring-設定對應本版新增) 的對應
- 所有 `ctip.*` 屬性以 `@ConfigurationProperties` 綁定為 `record` + `jakarta.validation`（**禁止散落的 `@Value`**）
- `StartupValidator`（五條啟動守衛）
- Flyway migration `V1`–`V7`：M1 的 9 張表 + extension（`pgcrypto`、`pg_trgm`）
- `V2__seed_system_tenant.sql`、`V4__seed_sources.sql`（皆冪等）
- `db/seed/` 開發樣本資料（約 1,000 筆 IOC），`spring.sql.init` 僅 `dev`/`mvp` 載入
- `MigrationIntegrationTest`、`PublicTenantIntegrationTest`、`SampleDataIntegrationTest`、`RequiredIndexTest`、`TlpRedAbsenceTest`

## 治理規格
- [04-data-dictionary.md](../04-data-dictionary.md)（M1 的 9 張表 + §4.7 Flyway 對應）
- [05-environment.md §5.7、§5.9](../05-environment.md#59-flyway)
- [14-testing.md §14.7](../14-testing.md#147-測試資料)

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration \
  -Dtest='MigrationIntegrationTest,PublicTenantIntegrationTest,SampleDataIntegrationTest,RequiredIndexTest,TlpRedAbsenceTest'
./environment/scripts/up.sh mvp
curl -fsS http://localhost:8080/actuator/health | jq -e '.status == "UP"'
```

## 不得做的事
- 不得使用 `ddl-auto: update` 或 `create`（必須 `validate`）
- 不得建立 M2／M3 的表
- 不得種入 `TLP:RED` 資料
- 不得種入真實 secret
- 不得新增 JSONB 欄位（白名單見 [04 §4.0](../04-data-dictionary.md#40-通用約定強制)）
