import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { sessionEstablished } from '../stores/authSlice';
import { issuedWebhook, sampleSession, sampleWebhook } from '../test/handlers';
import { renderRoute } from '../test/render';
import { server } from '../test/server';
import WebhooksPage from './WebhooksPage';

const routes: RouteObject[] = [{ path: '/settings/webhooks', element: <WebhooksPage /> }];

function authenticatedStore(): AppStore {
  const store = makeStore();
  store.dispatch(
    sessionEstablished({
      accessToken: sampleSession.accessToken,
      refreshToken: sampleSession.refreshToken,
      user: { id: sampleSession.user.userId, name: sampleSession.user.displayName },
      tenantId: sampleSession.user.tenantId,
      role: sampleSession.user.role,
      permissions: [...sampleSession.user.permissions, 'webhook:manage'],
    }),
  );
  return store;
}

function render() {
  return renderRoute({ routes, initialEntry: '/settings/webhooks', store: authenticatedStore() });
}

describe('WebhooksPage', () => {
  it('lists the tenant webhooks without ever revealing the signing secret', async () => {
    render();
    expect(await screen.findByText(sampleWebhook.name)).toBeInTheDocument();
    expect(screen.getByText(sampleWebhook.targetUrl)).toBeInTheDocument();
    expect(screen.queryByText(issuedWebhook.secret)).not.toBeInTheDocument();
  });

  it('shows an empty state when the tenant has no webhook', async () => {
    server.use(http.get('*/api/v1/webhooks', () => HttpResponse.json([])));
    render();
    expect(await screen.findByText('尚未建立任何 webhook')).toBeInTheDocument();
  });

  /** 不變量 W2 的對外契約:密鑰只顯示這一次。 */
  it('shows the signing secret exactly once after creation', async () => {
    render();
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('名稱'), 'soc');
    await user.type(screen.getByLabelText('目標 URL'), 'hooks.example.test/x');
    await user.click(screen.getByRole('button', { name: '建立 webhook' }));

    const notice = await screen.findByRole('status');
    expect(notice).toHaveTextContent(issuedWebhook.secret);
    expect(notice).toHaveTextContent('只會顯示這一次');
  });

  it('reports the plan limit instead of failing silently', async () => {
    server.use(
      http.post('*/api/v1/webhooks', () =>
        HttpResponse.json(
          { code: 'PLAN_LIMIT_EXCEEDED', message: 'Webhook quota exhausted' },
          { status: 403 },
        ),
      ),
    );
    render();
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('名稱'), 'over-quota');
    await user.type(screen.getByLabelText('目標 URL'), 'hooks.example.test/x');
    await user.click(screen.getByRole('button', { name: '建立 webhook' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Webhook quota exhausted');
  });

  it('explains why a webhook was disabled', async () => {
    server.use(
      http.get('*/api/v1/webhooks', () =>
        HttpResponse.json([{ ...sampleWebhook, status: 'DISABLED', consecutiveFailures: 5 }]),
      ),
    );
    render();
    expect(await screen.findByText(/連續 5 次送達失敗/)).toBeInTheDocument();
  });

  it('deletes a webhook', async () => {
    let deleted: string | null = null;
    server.use(
      http.delete('*/api/v1/webhooks/:id', ({ params }) => {
        deleted = String(params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    render();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: '刪除' }));
    await waitFor(() => expect(deleted).toBe(sampleWebhook.id));
  });
});
