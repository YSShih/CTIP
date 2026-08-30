import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { sessionEstablished } from '../stores/authSlice';
import { sampleAuditLog, sampleSession } from '../test/handlers';
import { renderRoute } from '../test/render';
import { server } from '../test/server';
import AuditLogPage from './AuditLogPage';

const routes: RouteObject[] = [{ path: '/audit', element: <AuditLogPage /> }];

function authenticatedStore(): AppStore {
  const store = makeStore();
  store.dispatch(
    sessionEstablished({
      accessToken: sampleSession.accessToken,
      refreshToken: sampleSession.refreshToken,
      user: { id: sampleSession.user.userId, name: sampleSession.user.displayName },
      tenantId: sampleSession.user.tenantId,
      role: sampleSession.user.role,
      permissions: [...sampleSession.user.permissions, 'audit:read'],
    }),
  );
  return store;
}

function render() {
  return renderRoute({ routes, initialEntry: '/audit', store: authenticatedStore() });
}

describe('AuditLogPage', () => {
  it('lists the tenant audit trail', async () => {
    render();

    // 以列為斷言對象:action 的字串在篩選下拉的 <option> 裡也有,findByText 會先命中那個
    const row = await screen.findByTestId('audit-row');
    expect(row).toHaveTextContent(sampleAuditLog.action);
    expect(row).toHaveTextContent(sampleAuditLog.ip);
    expect(row).toHaveTextContent(sampleAuditLog.result);
  });

  it('filters by action and passes the choice to the API', async () => {
    const requested: string[] = [];
    server.use(
      http.get('*/api/v1/audit-logs', ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('action') ?? '');
        return HttpResponse.json({ items: [], nextCursor: undefined, hasMore: false });
      }),
    );
    render();

    await userEvent.selectOptions(await screen.findByLabelText('行為'), 'LOGIN_FAILED');

    expect(await screen.findByText('這個範圍內沒有稽核紀錄')).toBeInTheDocument();
    expect(requested).toContain('LOGIN_FAILED');
  });

  it('shows the error state when the trail cannot be read', async () => {
    server.use(http.get('*/api/v1/audit-logs', () => new HttpResponse(null, { status: 500 })));
    render();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
