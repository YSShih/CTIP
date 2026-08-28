import { expect, test } from '@playwright/test';
import { STUB_ISSUED_KEY, STUB_IOC, stubApi } from './stubs';

/**
 * M2-26 的其餘三個情境:登入、建立 API key、提交 IOC(docs/spec/12-frontend.md §12.8)。
 *
 * 寫成<strong>一條連續的旅程</strong>而非三個獨立測試,是因為後兩者需要一個已登入的 session,
 * 而 session 存在記憶體(§12.3:token 不落 localStorage)——分開跑就得偽造 Redux 狀態,
 * 那就不再是 E2E 了。
 */
test('登入後可建立 API key 並提交 IOC', async ({ page }) => {
  await stubApi(page);

  // 1. 登入
  await page.goto('/login');
  await page.getByLabel('電子郵件').fill('analyst@example.org');
  await page.getByLabel('密碼').fill('test-password-1234');
  await page.getByRole('button', { name: '登入' }).click();

  await expect(page.getByRole('button', { name: '登出' })).toBeVisible();

  // 2. 建立 API key —— 完整金鑰只顯示這一次(不變量 K1)
  await page.getByRole('link', { name: 'API Key' }).click();
  await page.getByLabel('名稱').fill('ci-pipeline');
  await page.getByRole('checkbox', { name: 'ioc:read' }).check();
  await page.getByRole('button', { name: '建立 API key' }).click();

  const notice = page.getByRole('status');
  await expect(notice).toContainText(STUB_ISSUED_KEY.key);
  await expect(notice).toContainText('只會顯示這一次');

  // 3. 提交 IOC —— 預設 TLP 為 AMBER(§9.7:提交的情資是租戶私有的)
  await page.getByRole('link', { name: '提交 IOC' }).click();
  await expect(page.getByLabel('TLP')).toHaveValue('AMBER');
  await page.getByLabel('IOC 值').fill('203.0.113.5');
  await page.getByRole('button', { name: '提交 IOC' }).click();

  await expect(page.getByRole('status')).toContainText(STUB_IOC.value);
});
