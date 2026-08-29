import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, ws } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { sessionEstablished } from '../stores/authSlice';
import { sampleNotification, sampleNotificationPage, sampleSession } from '../test/handlers';
import { renderRoute } from '../test/render';
import { server } from '../test/server';
import NotificationCenterPage from './NotificationCenterPage';

const routes: RouteObject[] = [{ path: '/notifications', element: <NotificationCenterPage /> }];

/** 推播端點的攔截點;預設 handler 沒有 WebSocket,每個案例自己決定連線行為。 */
const stream = ws.link('ws://localhost:3000/api/v1/ws');

function authenticatedStore(): AppStore {
  const store = makeStore();
  store.dispatch(
    sessionEstablished({
      accessToken: sampleSession.accessToken,
      refreshToken: sampleSession.refreshToken,
      user: { id: sampleSession.user.userId, name: sampleSession.user.displayName },
      tenantId: sampleSession.user.tenantId,
      role: sampleSession.user.role,
      permissions: [...sampleSession.user.permissions, 'notification:read'],
    }),
  );
  return store;
}

function render() {
  return renderRoute({ routes, initialEntry: '/notifications', store: authenticatedStore() });
}

describe('NotificationCenterPage', () => {
  it('shows a loading skeleton before the list arrives', () => {
    render();
    expect(screen.getByRole('status', { name: '載入通知' })).toBeInTheDocument();
  });

  it('lists notifications and marks the unread ones', async () => {
    render();
    expect(await screen.findByText(sampleNotification.title)).toBeInTheDocument();
    const items = screen.getAllByTestId('notification-item');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveAttribute('data-read', 'false');
    expect(items[1]).toHaveAttribute('data-read', 'true');
    // 已讀的那一列沒有「標為已讀」按鈕
    expect(screen.getAllByRole('button', { name: '標為已讀' })).toHaveLength(1);
  });

  it('shows an empty state when there is nothing to read', async () => {
    server.use(
      http.get('*/api/v1/notifications', () =>
        HttpResponse.json({ items: [], nextCursor: null, hasMore: false }),
      ),
    );
    render();
    expect(await screen.findByText('尚無通知')).toBeInTheDocument();
  });

  it('marks a notification as read', async () => {
    let patched: string | null = null;
    server.use(
      http.patch('*/api/v1/notifications/:id/read', ({ params }) => {
        patched = String(params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    render();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: '標為已讀' }));
    expect(patched).toBe(sampleNotification.id);
  });

  /** 連上之後指示器必須說出「即時連線中」。 */
  it('reports the realtime connection as open once the socket connects', async () => {
    server.use(stream.addEventListener('connection', () => undefined));
    render();
    await screen.findByText(sampleNotification.title);
    await waitFor(() =>
      expect(screen.getByTestId('stream-status')).toHaveAttribute('data-status', 'open'),
    );
    expect(screen.getByTestId('stream-status')).toHaveTextContent('即時連線中');
  });

  /**
   * 指示器必須誠實:伺服器把連線關掉之後,頁面不得看起來一切正常
   * ——它會自動重連,而在那之前使用者收不到即時通知。
   */
  it('reports a dropped connection as reconnecting', async () => {
    server.use(stream.addEventListener('connection', ({ client }) => client.close()));
    render();
    await screen.findByText(sampleNotification.title);
    await waitFor(() =>
      expect(screen.getByTestId('stream-status')).toHaveAttribute('data-status', 'reconnecting'),
    );
    expect(screen.getByTestId('stream-status')).toHaveTextContent('重試中');
  });

  /** 推播只是「有新東西了」的訊號;內容仍以 Query 為真相來源(§12.3)。 */
  it('refetches the list when a push arrives', async () => {
    let requests = 0;
    server.use(
      http.get('*/api/v1/notifications', () => {
        requests += 1;
        return HttpResponse.json(sampleNotificationPage);
      }),
      // 延後送出:client 端的 onmessage 是在 WebSocket 建構之後才掛上的
      stream.addEventListener('connection', ({ client }) => {
        setTimeout(
          () =>
            client.send(
              JSON.stringify({ type: 'NEW_IOC', eventId: 'pushed-1', payload: { title: 'x' } }),
            ),
          20,
        );
      }),
    );
    render();
    await screen.findByText(sampleNotification.title);
    await waitFor(() => expect(requests).toBeGreaterThan(1));
  });

  it('surfaces a failed load instead of rendering an empty page', async () => {
    server.use(
      http.get('*/api/v1/notifications', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', message: 'boom' }, { status: 500 }),
      ),
    );
    render();
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
