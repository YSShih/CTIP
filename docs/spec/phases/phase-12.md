# Phase 12 — IOC 頁面 + PostgreSQL 搜尋  `[M1]`

## 前置條件
- Phase 11 完成判準全綠

## 交付物

### 後端
- `PostgresSearchAdapter`（JPA `Specification` + GIN 索引 + `pg_trgm`）
- 端點 `GET /api/v1/stats/summary`、`GET /api/v1/stats/sources`

### 前端
- `pages/IocSearchPage`：`IocFilterBar` + `IocTable`（虛擬化）+ `CursorPager`，搜尋條件存於 URL search params
- `pages/IocDetailPage`：`IocSummaryCard` + `SourceAttributionList`（顯示 `attribution`）+ `TlpBadge` + `StixJsonViewer`
- `pages/DashboardPage`：統計卡 + Recharts 趨勢圖（匿名可存取）
- `features/ioc/hooks/`：`useIocSearch`、`useIocDetail`（Query key 依 [12 §12.3](../12-frontend.md#123-狀態歸屬強制) 慣例）
- 四種狀態（loading / empty / error / forbidden）皆呈現

## 治理規格
- [12-frontend.md §12.5、§12.6](../12-frontend.md#125-頁面)
- [13-platform-ops.md §13.7](../13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch)
- [03-diagrams.md §3.5.4](../03-diagrams.md#354-元件樹代表性頁面)

## 完成判準
```bash
cd frontend && npm run test -- IocSearchPage IocDetailPage DashboardPage
./backend/mvnw -f backend/pom.xml verify -Ptest-integration
./environment/scripts/dod.sh mvp        # ← 整個 DoD-MVP，38 項
```

## 不得做的事
- 不得實作 Elasticsearch（Phase 19）
- 不得實作模糊查詢（M2）
- 不得把搜尋條件放進 Redux（必須在 URL）
- 不得在匿名使用者看到需登入資料時顯示空白或假資料（必須 `ForbiddenState`）

## 里程碑閘門
**此 Phase 結束後執行 `./environment/scripts/dod.sh mvp`。38 項全綠才可進入 Phase 13。**
