import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { sessionEstablished } from '../stores/authSlice';
import { sampleIoc, sampleSession } from '../test/handlers';
import { renderRoute } from '../test/render';
import { server } from '../test/server';
import IocSubmitPage from './IocSubmitPage';

const routes: RouteObject[] = [
  { path: '/iocs/new', element: <IocSubmitPage /> },
  { path: '/iocs/:id', element: <p>detail</p> },
];

function authenticatedStore(): AppStore {
  const store = makeStore();
  store.dispatch(
    sessionEstablished({
      accessToken: sampleSession.accessToken,
      refreshToken: sampleSession.refreshToken,
      user: { id: sampleSession.user.userId, name: sampleSession.user.displayName },
      tenantId: sampleSession.user.tenantId,
      role: sampleSession.user.role,
      permissions: ['ioc:submit'],
    }),
  );
  return store;
}

function render() {
  return renderRoute({ routes, initialEntry: '/iocs/new', store: authenticatedStore() });
}

async function submitValue(value: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('IOC 值'), value);
  await user.click(screen.getByRole('button', { name: '提交 IOC' }));
}

function errorResponse(status: number, code: string, message: string) {
  return HttpResponse.json(
    {
      timestamp: '2026-08-28T09:00:00Z',
      status,
      code,
      message,
      path: '/api/v1/iocs',
      traceId: 'trace-1',
      details: [],
    },
    { status },
  );
}

describe('IocSubmitPage', () => {
  it('預設 TLP 為 AMBER —— 提交的情資是租戶私有的(§9.7)', () => {
    render();
    expect(screen.getByLabelText('TLP')).toHaveValue('AMBER');
  });

  it('提交成功後給出可點進詳情的確認,而不是只說「已送出」', async () => {
    render();
    await submitValue('203.0.113.5');
    expect(await screen.findByRole('status')).toHaveTextContent(sampleIoc.value);
  });

  it('方案不允許提交時說明要升級,而不是丟出原始錯誤碼', async () => {
    server.use(
      http.post('*/api/v1/iocs', () =>
        errorResponse(
          403,
          'PLAN_LIMIT_EXCEEDED',
          'Manual submission is not available on this plan',
        ),
      ),
    );
    render();
    await submitValue('203.0.113.6');
    expect(await screen.findByRole('alert')).toHaveTextContent('升級方案');
  });

  it('每日配額用罄回 429 時說明何時可再試', async () => {
    server.use(
      http.post('*/api/v1/iocs', () =>
        errorResponse(429, 'RATE_LIMIT_EXCEEDED', 'Daily manual submission quota exhausted'),
      ),
    );
    render();
    await submitValue('203.0.113.7');
    expect(await screen.findByRole('alert')).toHaveTextContent('額度已用罄');
  });

  it('pipeline 拒絕(私有 IP 等)必須顯示原因,不得看起來像成功', async () => {
    server.use(
      http.post('*/api/v1/iocs', () =>
        errorResponse(400, 'INVALID_IOC_FORMAT', 'PRIVATE_OR_RESERVED_IP: 私有位址'),
      ),
    );
    render();
    await submitValue('10.0.0.1');
    expect(await screen.findByRole('alert')).toHaveTextContent('PRIVATE_OR_RESERVED_IP');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
