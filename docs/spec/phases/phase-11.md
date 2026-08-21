# Phase 11 — React 前端骨架 + 型別產生 + 版面  `[M1]`

## 前置條件
- Phase 10 完成判準全綠

## 交付物
- `src/app/`：provider 組合（QueryClient、Redux store、Router、Theme）
- `src/api/generated/`（由 `docs/api/openapi.json` 產生，進版控，勿手改）+ `src/api/client.ts`
- `src/stores/`：`authSlice`、`uiSlice`、`toastSlice`、`filterDraftSlice`
- `src/components/`：shadcn/ui 基礎元件、`VirtualTable`、`StateViews`（Loading/Empty/Error/Forbidden）、`TlpBadge`
- `src/layouts/AppLayout`、`src/routes/`（含 `RequireAuth`、`RequirePermission` 骨架）
- Tailwind CSS v4 設定（`@theme` 區塊 + `@tailwindcss/vite`，**無 `tailwind.config.js`**）
- 深色模式、響應式
- `vite.config.ts`（`host 0.0.0.0`、`port 5173`、`strictPort`）
- `npm run api:generate` / `api:check` scripts
- MSW 設定（handlers 由 generated 型別驅動）

## 治理規格
- [12-frontend.md](../12-frontend.md) 全檔
- [03-diagrams.md §3.5](../03-diagrams.md#35-前端圖)（四張前端圖）
- [06-tech-stack.md §6.3.3](../06-tech-stack.md#633-tailwind-css-v4--不要用-v3-lts-tag)

## 完成判準
```bash
cd frontend
npx tsc --noEmit
npx eslint . --max-warnings 0        # 含四條 feature 依賴規則
npm run api:check                    # generated 型別與 OpenAPI 一致
npm run build
npm run test -- --coverage
```

## 不得做的事
- **不得使用 class component**
- 不得手寫後端型別（一律用 generated）
- 不得手改 `src/api/generated/`
- 不得建立 `tailwind.config.js`（v4 為 CSS-first）
- 不得使用 `tailwindcss@v3-lts`
- 不得把 server 資料放進 Redux
- 不得讓 feature 之間橫向 import
