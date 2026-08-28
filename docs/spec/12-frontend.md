# 12 — 前端

> **規範等級：強制。** 結構、feature 依賴規則、狀態歸屬、型別產生流程為規範性內容。
>
> 相關檔案：[03-diagrams.md](03-diagrams.md#35-前端圖)（四張前端圖）、[06-tech-stack.md](06-tech-stack.md#623-frontend)（版本）

---

## 12.1 技術與原則

React 19 + TypeScript 7 + Vite 8 + React Router 8 + TanStack Query 5 + Redux Toolkit 2 + Tailwind CSS 4 + shadcn/ui + React Hook Form + Zod 4 + Vitest 4 + React Testing Library + Playwright（M2 起）。

| 原則 |
|---|
| **Function component + hooks。禁止 class component。** |
| Feature-based architecture（依業務能力切分，非依技術類型） |
| Server state 與 client state 嚴格分離 |
| 型別**由 OpenAPI 產生**，不手寫 |
| 前端授權僅為 UX，**後端必須再次驗證** |

---

## 12.2 目錄結構

```text
frontend/src/
├── app/            應用進入點、provider 組合（QueryClient、Redux store、Router、Theme）
├── api/
│   ├── generated/  ← 由 OpenAPI 產生，進版控，⚠️ 勿手改
│   └── client.ts   薄包裝：baseURL、認證標頭、錯誤轉換
├── components/     共用展示元件（含 shadcn/ui、VirtualTable、StateViews）
├── features/
│   ├── ioc/  threat/  stix/  sync/  auth/
│   └── subscription/  apikey/  notification/  audit/
├── hooks/          跨 feature 的共用 hook
├── layouts/        AppLayout、AuthLayout
├── pages/          每個路由一個頁面元件
├── routes/         路由定義與守衛
├── stores/         Redux slices
├── types/          純前端型別（不含後端型別）
├── utils/  constants/  styles/
└── test/           測試設定、MSW handlers
```

每個 `features/*` 內部結構固定：`components/`、`hooks/`、`api/`、`types.ts`。

### Feature 依賴規則（ESLint 強制）

| # | 規則 |
|---|---|
| F1 | `features/A` **不得** import `features/B/**` 的任何檔案 |
| F2 | `components/`、`hooks/`、`utils/` **不得** import `features/**` |
| F3 | `api/generated/**` 不得手改 |
| F4 | 只有 `pages/`、`routes/`、`app/` 可跨多個 feature |

以 `eslint-plugin-import` 的 `no-restricted-paths` 實作，設定於 `eslint.config.js`（flat config）。**違規即 CI fail。**

共用內容一律上移到 `components/` 或 `hooks/`，**不得**在 feature 之間橫向引用。

---

## 12.3 狀態歸屬（強制）

判準：**「重新整理頁面後應該重新取得的，屬 Query；應該保留的，屬 Redux 或 URL。」**

| 資料 | 歸屬 | 禁止 |
|---|---|---|
| 任何來自 API 的實體資料 | **TanStack Query** | 放入 Redux |
| 已送出的搜尋條件、cursor、排序 | **URL search params** | 放入 Redux 或 Query |
| 尚未送出的表單草稿 | RHF 本地狀態或 Redux `filterDraftSlice` | 放入 Query |
| access token、使用者身分、權限集合 | Redux `authSlice` | 放入 Query（會被快取失效清掉） |
| 主題、表格欄位設定、側欄狀態 | Redux `uiSlice` + localStorage | — |
| toast 佇列 | Redux `toastSlice` | — |
| WebSocket 推來的即時事件 | 寫入 Query cache（`setQueryData`）或 `toastSlice` | 建立第三套 store |

完整圖見 [03-diagrams.md](03-diagrams.md#352-狀態歸屬圖)。

### Query key 慣例（強制）

```ts
['ioc', 'list', { filters, cursor }]
['ioc', 'detail', id]
['ioc', 'sources', id]
['threat', 'list', { filters, cursor }]
['source', 'list']
['stats', 'summary']
['subscription', 'usage']
```

第一段為 feature 名，第二段為操作，第三段為參數。失效以前綴進行：`queryClient.invalidateQueries({ queryKey: ['ioc'] })`。

---

## 12.4 型別產生（強制流程）

```text
後端 controller + DTO record
   ↓ springdoc
docs/api/openapi.json          （CI artifact，進版控）
   ↓ openapi-typescript
frontend/src/api/generated/    （進版控，勿手改）
   ↓
frontend/src/api/client.ts     （薄包裝）
   ↓
features/*/api/                （useQuery / useMutation）
```

| 規則 |
|---|
| 後端 OpenAPI 規格是**唯一**的型別來源 |
| CI 必須驗證：重新產生的型別與 committed 版本一致，不一致則 **fail** |
| `features/*/types.ts` 只能 re-export 或窄化 generated 型別，**不得重新定義**後端型別 |
| **不得**手動重複定義後端型別 |

產生指令（寫入 `package.json` scripts）：

```bash
npm run api:generate    # openapi-typescript ../docs/api/openapi.json -o src/api/generated/schema.d.ts
npm run api:check       # 重新產生後 git diff --exit-code src/api/generated/
```

---

## 12.5 頁面

| 頁面 | Phase | 匿名可存取 | 路徑 |
|---|---|---|---|
| IOC Search | M1 | ✓ | `/iocs` |
| IOC Detail | M1 | ✓ | `/iocs/:id` |
| Dashboard | M1 | ✓（公開統計） | `/` |
| Threat Feed | M2 | ✓ | `/threats` |
| Threat Detail | M2 | ✓ | `/threats/:id` |
| Login / Register | M2 | ✓ | `/login`, `/register` |
| IOC Submit / Import | M2 | ✗ | `/iocs/new`, `/iocs/import` |
| Subscription | M2 | ✗ | `/settings/subscription` |
| API Key Management | M2 | ✗ | `/settings/api-keys` |
| Sync / Bloom 說明頁 | M2 | ✓ | `/sync` |
| Settings | M2 | ✗ | `/settings` |
| STIX Viewer | M3 | ✓ | `/stix/:id` |
| Notification Center | M3 | ✗ | `/notifications` |
| Audit Log | M3 | ✗ | `/audit` |
| Admin Panel | M3 | ✗ | `/admin` |

需登入的頁面必須在路由層強制授權（`RequireAuth` / `RequirePermission` 元件），**並在後端再次驗證**。

---

## 12.6 UI 要求

必須提供：響應式設計、深色模式、loading 狀態、空狀態、錯誤狀態、skeleton、toast 通知、無障礙表單（label、aria、鍵盤操作）。

大量 IOC 使用**虛擬化表格**（TanStack Virtual），元件為 `components/VirtualTable`。

四種狀態必須以統一元件表達，不得每頁自寫：

```text
components/StateViews/
├── LoadingState.tsx     skeleton
├── EmptyState.tsx       含說明文字與行動建議
├── ErrorState.tsx       依錯誤 code 顯示對應文案 + 重試按鈕
└── ForbiddenState.tsx   權限不足時的引導（升級方案 / 登入）
```

### TLP 與 Bloom 的 UI 責任（強制）

| # | 要求 |
|---|---|
| 1 | 每個 IOC 顯示處必須有 `TlpBadge`，顏色對應 TLP 等級 |
| 2 | `ATTRIBUTION_REQUIRED` 的資料必須顯示 `attribution` 欄位（來源名稱與連結） |
| 3 | Sync / Bloom 說明頁必須明文說明：**Bloom 命中不代表確定惡意**、**未命中不代表安全**（`TLP:GREEN` 無覆蓋） |
| 4 | 匿名使用者看到需登入的資料時，顯示 `ForbiddenState` 並說明原因，**不得**顯示空白或假資料 |

### STIX Viewer `[M3]`

支援：物件詳情、關聯、圖形檢視（Cytoscape.js）、節點展開、基本篩選。
**圖形視覺化不得成為 MVP 的阻塞項。**

### 即時更新 `[M3]`

```text
WebSocket → notification event → Query cache 更新 / toastSlice → UI
```

SSE 作為 fallback。連線中斷時自動重連（指數退避）並在 UI 顯示連線狀態。

---

## 12.7 Dev 環境

Vite dev server 在容器內以 `--host 0.0.0.0` 執行。
**host port 與容器 port 必須一致（皆 5173）**，否則 Vite 的 HMR client 會連向錯誤的 port 而靜默失效——見 [05-environment.md](05-environment.md#511-hot-reload-契約本版修正)。

`vite.config.ts` 必須設定：

```ts
server: {
  host: '0.0.0.0',
  port: 5173,
  strictPort: true,          // port 被占用時直接失敗，不靜默改 port
  watch: { usePolling: true } // bind mount 在部分平台需要 polling 才能偵測變更
}
```

`usePolling` 會提高 CPU 使用；若在你的平台上 inotify 正常運作可關閉，並在 `docs/development/getting-started.md` 記錄。

---

## 12.8 前端測試

| 層級 | 內容 |
|---|---|
| 元件測試 | React Testing Library，含四種狀態 |
| Hook 測試 | 自訂 hook 的邏輯 |
| 頁面測試 | 路由 + provider 組合 |
| API 整合測試 | MSW mock，驗證 request/response 契約 |
| 認證流程測試 | 登入、token 刷新、權限守衛 |
| E2E（Playwright，M2 起） | 匿名搜尋、登入、建立 API key、提交 IOC |

**MSW handlers 必須由 generated 型別驅動**，不得手寫 response 形狀——否則測試會與真實 API 漂移。

> **實作回饋修訂（2026-08-28，Phase 16；[ADR 0025](../architecture/decisions/0025-phase16-sync-api-decisions.md)）**——
> E2E 的位置與執行方式原本沒有規定（Playwright 只出現在版本表與 DoD 的 M2-26／M3-05，
> 見 [ADR 0022](../architecture/decisions/0022-orphan-deliverables.md)）：
>
> | 項目 | 規格 |
> |---|---|
> | 位置 | `frontend/playwright.config.ts` + `frontend/e2e/`（`*.spec.ts`；不得放進 `src/`——Vitest 會收進去而失敗） |
> | 執行 | `cd frontend && npx playwright test`（等同 `npm run e2e`）。`webServer` 跑 `npm run build && npm run preview`：M2-26 檢查的是使用者實際拿到的 bundle |
> | API 邊界 | 預設以 `page.route` 攔截 `/api/v1/**`（`e2e/stubs.ts`）——本表把 E2E 列在**前端**測試之下，測的是 bundle、路由、Query 快取與渲染 |
> | 對整套環境跑 | `E2E_BASE_URL=<origin>` 時不安裝攔截、也不啟動 webServer；全端驗證由 M2-25（compose）與 M3-05（WebSocket）負責 |
> | 瀏覽器本體 | `npx playwright install chromium` 屬本機／CI 前置，非專案交付物 |
>
> 四個情境已於 Phase 16 全數交付（`e2e/smoke.spec.ts`、`e2e/session.spec.ts`），
> Phase 20 再加 WebSocket 案例。

---

*檔案結束。上次校對：2026-08-28（Phase 16：Sync 頁與 Playwright 骨架）。*
