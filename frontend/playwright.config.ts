import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E(docs/spec/15-dod-gates.md M2-26 / M3-05;骨架由 Phase 16 交付,ADR 0022)。
 *
 * 預設跑<strong>建置後的產出</strong>(`vite build` + `vite preview`),不是 dev server:
 * M2-26 檢查的是使用者實際會拿到的 bundle。若 `E2E_BASE_URL` 有值則改連那個位址
 * (例如 compose 起好的整套環境),此時不自行啟動任何伺服器。
 *
 * `webServer.command` 刻意含 build:CI 上不保證 `dist/` 存在,而 `vite preview`
 * 在沒有 `dist/` 時直接失敗——把 build 放在這裡,`npx playwright test` 才是單一指令。
 */
const PORT = Number(process.env.E2E_PORT ?? 4173);
const externalBaseUrl = process.env.E2E_BASE_URL;
const baseURL = externalBaseUrl ?? `http://127.0.0.1:${PORT}`;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [['github'], ['list']] : [['list']],
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: externalBaseUrl
    ? undefined
    : {
        command: `npm run build && npm run preview -- --port ${PORT} --strictPort`,
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 180_000,
      },
});
