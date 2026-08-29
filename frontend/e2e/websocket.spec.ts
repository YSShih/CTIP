import { expect, test, type Page } from '@playwright/test';
import { STUB_NOTIFICATION, stubApi } from './stubs';

/**
 * DoD M3-05:WebSocket 通知端對端,斷線自動重連(docs/spec/15-dod-gates.md)。
 *
 * <p>與其他 E2E 一樣攔在 API 邊界:`page.routeWebSocket` 讓瀏覽器連到一個由測試控制的
 * 伺服器端替身。測到的仍是真實的 bundle、真實的 `WebSocket` 物件、真實的重連狀態機
 * ——只有伺服器那一端是固定的(§12.8 的測試邊界)。
 */

const WS_PATTERN = '**/api/v1/ws';

async function login(page: Page): Promise<void> {
  await stubApi(page);
  await page.goto('/login');
  await page.getByLabel('電子郵件').fill('analyst@example.org');
  await page.getByLabel('密碼').fill('test-password-1234');
  await page.getByRole('button', { name: '登入' }).click();
  await expect(page.getByRole('button', { name: '登出' })).toBeVisible();
}

test('通知中心連上 WebSocket 並顯示連線狀態', async ({ page }) => {
  const protocols: (string | undefined)[] = [];
  await page.routeWebSocket(WS_PATTERN, (server) => {
    protocols.push(server.url());
    // 不呼叫 connectToServer:由測試扮演伺服器端,連線視為已建立
  });

  await login(page);
  await page.getByRole('link', { name: '通知' }).click();

  await expect(page.getByText(STUB_NOTIFICATION.title)).toBeVisible();
  const status = page.getByTestId('stream-status');
  await expect(status).toHaveAttribute('data-status', 'open');
  await expect(status).toContainText('即時連線中');

  // token 不得出現在 URL(§9.1:query string 會進 access log)
  expect(protocols[0]).not.toContain('e2e-access-token');
});

test('伺服器推送的通知會讓清單重新取得', async ({ page }) => {
  let pushed: (() => void) | null = null;
  await page.routeWebSocket(WS_PATTERN, (server) => {
    pushed = () =>
      server.send(
        JSON.stringify({
          type: 'NEW_IOC',
          eventId: '11111111-2222-4333-8444-555555555555',
          payload: {
            id: 'a1b2c3d4-0000-4000-8000-000000000001',
            title: '推播進來的新 IOC',
            body: null,
            severity: 'HIGH',
            resourceType: 'indicator',
            resourceId: null,
            createdAt: '2026-08-29T10:00:00Z',
          },
        }),
      );
  });

  await login(page);

  // 註冊順序有意義:Playwright 由後往前比對,這條必須蓋過 stubApi 的萬用攔截
  let listRequests = 0;
  await page.route('**/api/v1/notifications**', async (route) => {
    listRequests += 1;
    await route.fulfill({
      status: 200,
      json: { items: [STUB_NOTIFICATION], hasMore: false, nextCursor: null },
    });
  });

  await page.getByRole('link', { name: '通知' }).click();
  await expect(page.getByTestId('stream-status')).toHaveAttribute('data-status', 'open');
  await expect.poll(() => listRequests).toBeGreaterThan(0);

  const before = listRequests;
  pushed?.();
  // 推播只是「有新東西了」的訊號,內容仍由 Query 重新取得(§12.3)
  await expect.poll(() => listRequests).toBeGreaterThan(before);
});

test('連線被切斷後自動重連', async ({ page }) => {
  let connections = 0;
  await page.routeWebSocket(WS_PATTERN, (server) => {
    connections += 1;
    if (connections === 1) {
      // 第一次連上就切斷:client 必須自己接回來,不能要求使用者重新整理
      server.close();
    }
  });

  await login(page);
  await page.getByRole('link', { name: '通知' }).click();

  const status = page.getByTestId('stream-status');
  await expect(status).toHaveAttribute('data-status', 'reconnecting');
  await expect(status).toContainText('重試中');

  // 退避第一段是 1 秒 × 抖動,第二次連線會在數秒內發生
  await expect.poll(() => connections, { timeout: 15_000 }).toBeGreaterThan(1);
  await expect(status).toHaveAttribute('data-status', 'open', { timeout: 15_000 });
});
