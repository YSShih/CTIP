import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { makeStore } from '../stores';
import { sessionEstablished } from '../stores/authSlice';
import { sampleSession } from '../test/handlers';
import { renderRoute } from '../test/render';
import { routes } from './index';

function storeWith(permissions: string[]) {
  const store = makeStore();
  store.dispatch(
    sessionEstablished({
      accessToken: sampleSession.accessToken,
      refreshToken: sampleSession.refreshToken,
      user: { id: sampleSession.user.userId, name: sampleSession.user.displayName },
      tenantId: sampleSession.user.tenantId,
      role: sampleSession.user.role,
      permissions,
    }),
  );
  return store;
}

describe('routes', () => {
  it('renders DashboardPage at /', async () => {
    renderRoute({ routes, initialEntry: '/' });
    expect(await screen.findByRole('heading', { name: '儀表板' })).toBeInTheDocument();
  });

  it('renders IocSearchPage at /iocs', async () => {
    renderRoute({ routes, initialEntry: '/iocs' });
    expect(await screen.findByRole('heading', { name: 'IOC 檢索' })).toBeInTheDocument();
  });

  it('renders IocDetailPage at /iocs/:id', async () => {
    renderRoute({ routes, initialEntry: '/iocs/1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e' });
    expect(await screen.findByRole('heading', { name: 'IOC 詳情' })).toBeInTheDocument();
  });

  it('renders NotFoundPage for unknown paths inside the layout', async () => {
    renderRoute({ routes, initialEntry: '/does-not-exist' });
    expect(await screen.findByText('404 — 頁面不存在')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '回到儀表板' })).toBeInTheDocument();
  });

  it('renders LoginPage at /login for anonymous visitors', async () => {
    renderRoute({ routes, initialEntry: '/login' });
    expect(await screen.findByRole('heading', { name: '登入' })).toBeInTheDocument();
  });

  it('renders RegisterPage at /register for anonymous visitors', async () => {
    renderRoute({ routes, initialEntry: '/register' });
    expect(await screen.findByRole('heading', { name: '建立帳號' })).toBeInTheDocument();
  });

  /** /settings 只掛 RequireAuth(不需額外權限):匿名仍必須被擋下並看到明確提示。 */
  it('blocks /settings for anonymous visitors', async () => {
    renderRoute({ routes, initialEntry: '/settings' });
    expect(await screen.findByText('需要登入')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '設定' })).not.toBeInTheDocument();
  });

  it('renders SettingsPage at /settings for any authenticated user', async () => {
    renderRoute({ routes, initialEntry: '/settings', store: storeWith([]) });
    expect(await screen.findByRole('heading', { name: '設定' })).toBeInTheDocument();
  });

  /** RequireAuth 已掛載:匿名不得看到 API key 頁,且必須是明確提示而非空白(§12.6 #4)。 */
  it('blocks /settings/api-keys for anonymous visitors', async () => {
    renderRoute({ routes, initialEntry: '/settings/api-keys' });
    expect(await screen.findByText('需要登入')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'API Key 管理' })).not.toBeInTheDocument();
  });

  /** RequirePermission 已掛載:登入但無 apikey:create 一樣進不去。 */
  it('blocks /settings/api-keys when the permission is missing', async () => {
    renderRoute({ routes, initialEntry: '/settings/api-keys', store: storeWith(['ioc:read']) });
    expect(await screen.findByText('權限不足')).toBeInTheDocument();
  });

  it('renders ApiKeysPage when authenticated with apikey:create', async () => {
    renderRoute({
      routes,
      initialEntry: '/settings/api-keys',
      store: storeWith(['ioc:read', 'apikey:create']),
    });
    expect(await screen.findByRole('heading', { name: 'API Key 管理' })).toBeInTheDocument();
  });

  /**
   * /iocs/new 是靜態段,必須贏過 /iocs/:id。react-router 依評分而非宣告順序決定,
   * 但那是相依函式庫的行為——用測試釘住,升版改了排序規則才不會變成一個看起來像
   * 「詳情頁查無此 IOC」的謎樣 404。
   */
  it('renders IocSubmitPage at /iocs/new instead of the detail page', async () => {
    renderRoute({ routes, initialEntry: '/iocs/new', store: storeWith(['ioc:submit']) });
    expect(await screen.findByRole('heading', { name: '提交 IOC' })).toBeInTheDocument();
  });

  it('renders IocImportPage at /iocs/import instead of the detail page', async () => {
    renderRoute({ routes, initialEntry: '/iocs/import', store: storeWith(['ioc:import']) });
    expect(await screen.findByRole('heading', { name: '批次匯入 IOC' })).toBeInTheDocument();
  });

  it('blocks /iocs/new when ioc:submit is missing', async () => {
    renderRoute({ routes, initialEntry: '/iocs/new', store: storeWith(['ioc:read']) });
    expect(await screen.findByText('權限不足')).toBeInTheDocument();
  });

  it('renders SubscriptionPage at /settings/subscription with subscription:read', async () => {
    renderRoute({
      routes,
      initialEntry: '/settings/subscription',
      store: storeWith(['subscription:read']),
    });
    expect(await screen.findByRole('heading', { name: '方案與用量' })).toBeInTheDocument();
  });

  it('blocks /settings/subscription for anonymous visitors', async () => {
    renderRoute({ routes, initialEntry: '/settings/subscription' });
    expect(await screen.findByText('需要登入')).toBeInTheDocument();
  });
});
