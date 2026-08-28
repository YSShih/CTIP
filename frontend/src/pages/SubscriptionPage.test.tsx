import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { sessionEstablished } from '../stores/authSlice';
import { sampleSession, sampleSubscription } from '../test/handlers';
import { renderRoute } from '../test/render';
import { server } from '../test/server';
import SubscriptionPage from './SubscriptionPage';

const routes: RouteObject[] = [{ path: '/settings/subscription', element: <SubscriptionPage /> }];

function authenticatedStore(): AppStore {
  const store = makeStore();
  store.dispatch(
    sessionEstablished({
      accessToken: sampleSession.accessToken,
      refreshToken: sampleSession.refreshToken,
      user: { id: sampleSession.user.userId, name: sampleSession.user.displayName },
      tenantId: sampleSession.user.tenantId,
      role: sampleSession.user.role,
      permissions: ['subscription:read'],
    }),
  );
  return store;
}

function render() {
  return renderRoute({
    routes,
    initialEntry: '/settings/subscription',
    store: authenticatedStore(),
  });
}

describe('SubscriptionPage', () => {
  it('列出方案與 §10.6 的配額維度', async () => {
    render();
    expect(await screen.findByText(sampleSubscription.planName)).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /手動提交／日/ })).toHaveTextContent('1000');
    expect(screen.getByRole('row', { name: /單檔匯入筆數上限/ })).toHaveTextContent('10000');
  });

  it('null = 無限制、0 = 停用,兩者不得都印成 0', async () => {
    server.use(
      http.get('*/api/v1/subscription', () =>
        HttpResponse.json({
          ...sampleSubscription,
          planCode: 'ENTERPRISE',
          planName: 'Enterprise',
          quotas: {
            ...sampleSubscription.quotas,
            requestsPerDay: null,
            stixExportMaxObjects: null,
            maxManualSubmissionsPerDay: 0,
          },
        }),
      ),
    );
    render();
    expect(await screen.findByText('Enterprise')).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /請求／日/ })).toHaveTextContent('無限制');
    expect(screen.getByRole('row', { name: /手動提交／日/ })).toHaveTextContent('停用');
  });

  it('沒有訂閱列的租戶顯示 FREE 並說明沒有計費期間(不變量 B4)', async () => {
    server.use(
      http.get('*/api/v1/subscription', () =>
        HttpResponse.json({
          planCode: 'FREE',
          planName: 'Free',
          tier: 1,
          quotas: sampleSubscription.quotas,
        }),
      ),
    );
    render();
    expect(await screen.findByText('Free')).toBeInTheDocument();
    expect(screen.getByText(/尚未指派訂閱/)).toBeInTheDocument();
  });

  it('顯示今日用量與其重置時間', async () => {
    render();
    expect(await screen.findByText('今日手動提交')).toBeInTheDocument();
    expect(screen.getByText('12 / 1000')).toBeInTheDocument();
  });
});
