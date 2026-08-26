# ADR 0008 — Phase 11 前端骨架實作決策

- 狀態:accepted(2026-08-26)
- 範圍:Phase 11(React 前端骨架 + 型別產生 + 版面),治理規格 12、06 §6.2.3/§6.3.3、03 §3.5、14 §14.6

## 1. shadcn/ui 以手寫等價元件落地,不執行 CLI

`npx shadcn add` 每次執行都需存取遠端 registry,且會自行 `npm install` 相依——與本專案
dev 容器的 node-modules named volume 預熱流程(ADR 0001 決策 5)衝突,離線環境不可重現。
M1 三頁皆匿名唯讀,無 dialog/dropdown 等需要 Radix primitives 的互動。
決策:依 shadcn 慣例(`cn()` util + cva variants + 同名 API)手寫
`components/ui/{button,badge,card,input,skeleton,separator}`,日後可無痛以 CLI 產出替換;
M1 不引入任何 `@radix-ui/*`。

## 2. 版本表未列、但為指定套件必要配套的相依(規則 6/17 回報)

以下皆非「自行升版」,而是 06 §6.2.3 指定套件的必要 peer 或測試/建置基礎設施,
版本由 lockfile 固定:

| 套件 | 版本 | 理由 |
|---|---|---|
| react-redux | 9.3.0 | Redux Toolkit 2.x 的 React 綁定(無替代品) |
| jsdom | 27.4.0 | vitest 4 DOM environment(RTL 參考環境) |
| @testing-library/jest-dom / user-event | 6.9.x / 14.6.x | RTL 16 配套 matcher 與互動模擬 |
| @vitest/coverage-v8 | 4.1.11 | 14 §14.6 指定 v8 provider(與 vitest 同 minor) |
| clsx / tailwind-merge / class-variance-authority | 2.x / 3.x / 0.7.x | shadcn 慣例的必要 util(決策 1) |
| @fontsource-variable/archivo / @fontsource/ibm-plex-mono | 5.3.x | 字體自託管:零 runtime 外連(威脅情資平台不外洩流量給字體 CDN) |

## 3. api/client.ts 手寫 typed fetch wrapper,不引入 openapi-fetch

版本表只 pin `openapi-typescript`(型別產生器);openapi-fetch 是額外 runtime 相依。
以 generated `paths` 推導 `apiGet`/`apiPost` 的參數與回應型別(約 60 行),
錯誤統一轉 `ApiError(status, code, message, traceId, details)`,
`setAuthTokenProvider` 由 app 層注入 token(api 層不 import stores)。方向可逆。

## 4. springdoc 將 query record 包成單一 `params` 物件(openapi 缺陷,Phase 12 修正)

`GET /api/v1/iocs` 的 `IocListParams` 在 openapi.json 中呈現為
`query: { params: IocListParams }`,但 Spring 實際綁定是攤平的 query 欄位——
照 openapi 產生的 client 會送出錯誤的 wire 格式。
處置:client 的 query 序列化對物件值做一層攤平(兩種形狀皆相容);
Phase 12 後端在 controller 參數補 `@ParameterObject` 並重產 openapi.json。

## 5. 測試慣例(規格空白處)

12 §12.8 / 14 §14.6 未規定測試檔位置:採 co-located `*.test.ts(x)`(與受測檔同目錄);
`src/test/` 只放 setup 與 MSW handlers(規格明文)。coverage 門檻以
`include: [components/**, features/**]` + 全域 `thresholds.lines: 70` 表達
(include 已把分母限縮到規格點名的兩個範圍,語意等價且避免 barrel 檔 per-file 假紅)。
實測 Phase 11 行覆蓋 100%。

## 6. 深色模式:class 策略 + 防 FOUC inline script

規格只規定「必須提供深色模式」與狀態存 uiSlice + localStorage。
採 `@custom-variant dark (&:where(.dark, .dark *))` class 策略,
`ThemeApplier` 依 `uiSlice.theme`(light/dark/system)控制 `<html class>`,
system 監聽 `prefers-color-scheme`;`index.html` 內含 3 行 inline script
在 React 掛載前讀 `ctip.ui.v1` 先掛 class,避免深色使用者看到白屏閃爍。

## 7. RequireAuth / RequirePermission:行為完整、M1 不掛載

M1 三頁皆匿名可存取(§12.5),把守衛掛進路由表反而違規。
守衛本體為完整真行為(未登入/缺權限 → `ForbiddenState`,§12.6 #4)並有單元測試;
M2 加入需登入頁面時直接掛載即可——這是「擴充點」,非規則 16 禁止的假 placeholder。

## 8. 不引入 `@/` path alias

`eslint-plugin-import` 的 `no-restricted-paths` zones(F1/F2/F4)以檔案路徑解析,
alias 需另加 `eslint-import-resolver-typescript` 並三處同步(tsconfig/vite/eslint)。
零收益換一個相依與一個漂移點:全案用相對路徑,zones 直接生效。
