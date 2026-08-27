import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { sessionEstablished } from '../stores/authSlice';
import { issuedApiKey, sampleApiKey, sampleSession } from '../test/handlers';
import { renderRoute } from '../test/render';
import { server } from '../test/server';
import ApiKeysPage from './ApiKeysPage';

const routes: RouteObject[] = [{ path: '/settings/api-keys', element: <ApiKeysPage /> }];

function authenticatedStore(): AppStore {
  const store = makeStore();
  store.dispatch(
    sessionEstablished({
      accessToken: sampleSession.accessToken,
      refreshToken: sampleSession.refreshToken,
      user: { id: sampleSession.user.userId, name: sampleSession.user.displayName },
      tenantId: sampleSession.user.tenantId,
      role: sampleSession.user.role,
      permissions: [...sampleSession.user.permissions],
    }),
  );
  return store;
}

function render() {
  return renderRoute({ routes, initialEntry: '/settings/api-keys', store: authenticatedStore() });
}

describe('ApiKeysPage', () => {
  it('shows a loading skeleton before the list arrives', () => {
    render();
    expect(screen.getByRole('status', { name: '載入 API key' })).toBeInTheDocument();
  });

  it('lists existing keys without ever revealing key material', async () => {
    render();
    expect(await screen.findByText(sampleApiKey.name)).toBeInTheDocument();
    expect(screen.getByText(`${sampleApiKey.keyPrefix}…`)).toBeInTheDocument();
    expect(screen.queryByText(issuedApiKey.key)).not.toBeInTheDocument();
  });

  it('shows an empty state when the tenant has no keys', async () => {
    server.use(http.get('*/api/v1/api-keys', () => HttpResponse.json([])));
    render();
    expect(await screen.findByText('尚未建立任何 API key')).toBeInTheDocument();
  });

  it('shows the full key exactly once after creation', async () => {
    render();
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('名稱'), 'ci-pipeline');
    await user.click(screen.getByRole('checkbox', { name: 'ioc:read' }));
    await user.click(screen.getByRole('button', { name: '建立 API key' }));

    const notice = await screen.findByRole('status');
    expect(notice).toHaveTextContent(issuedApiKey.key);
    expect(notice).toHaveTextContent('只會顯示這一次');
  });

  it('surfaces a scope-escalation rejection from the backend', async () => {
    server.use(
      http.post('*/api/v1/api-keys', () =>
        HttpResponse.json(
          {
            timestamp: '2026-08-27T08:00:00Z',
            status: 400,
            code: 'INVALID_REQUEST',
            message: 'Invalid request',
            path: '/api/v1/api-keys',
            traceId: 'trace-400',
            details: [],
          },
          { status: 400 },
        ),
      ),
    );
    render();
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('名稱'), 'escalation');
    await user.click(screen.getByRole('checkbox', { name: 'ioc:read' }));
    await user.click(screen.getByRole('button', { name: '建立 API key' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('權限範圍不得超出你自己的權限');
  });

  it('revokes a key and refreshes the list', async () => {
    let revokedId: string | null = null;
    server.use(
      http.delete('*/api/v1/api-keys/:id', ({ params }) => {
        revokedId = String(params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    render();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: '撤銷' }));
    await waitFor(() => expect(revokedId).toBe(sampleApiKey.id));
  });

  it('shows an error state with a retry action when the list fails', async () => {
    server.use(http.get('*/api/v1/api-keys', () => HttpResponse.error()));
    render();
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /重試/ })).toBeInTheDocument();
  });
});
