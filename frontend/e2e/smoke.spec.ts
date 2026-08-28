import { expect, test } from '@playwright/test';
import { stubApi } from './stubs';

/**
 * 最小 E2E 案例(Phase 16 交付的骨架;M2-26 的其餘情境見 phase-19 執行單)。
 *
 * 兩件事:應用外框在真實瀏覽器裡跑得起來(bundle、路由、Query、Redux 都接上了),
 * 以及 Bloom 說明頁真的把「命中不代表惡意 / 未命中不代表安全」印在畫面上
 * ——那是 12 §12.6 第 3 條的強制要求,單元測試綠不代表 bundle 出去的頁面也有。
 */
test.beforeEach(async ({ page }) => {
  await stubApi(page);
});

test('匿名可載入 IOC 檢索並看到結果', async ({ page }) => {
  await page.goto('/iocs');

  await expect(page.getByRole('navigation', { name: '主導覽' })).toContainText('IOC 檢索');
  await expect(page.getByText('mal-8.ctip-sample.net').first()).toBeVisible();
});

test('Bloom 說明頁明文說明命中與未命中的語意', async ({ page }) => {
  await page.goto('/sync');

  await expect(page.getByRole('heading', { name: 'Bloom 同步' })).toBeVisible();
  await expect(page.getByText('命中(PRESENT)不代表確定惡意')).toBeVisible();
  await expect(page.getByText('未命中(NOT PRESENT)不代表安全')).toBeVisible();
  await expect(page.getByRole('note')).toContainText('TLP:GREEN');
  await expect(page.getByRole('row', { name: /覆蓋範圍/ })).toContainText('TLP:CLEAR only');
});
