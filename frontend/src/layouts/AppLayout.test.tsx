import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { selectIsAuthenticated, sessionEstablished } from '../stores/authSlice';
import { sampleSession } from '../test/handlers';
import { renderRoute } from '../test/render';
import { AppLayout } from './AppLayout';

const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    children: [{ index: true, element: <p>頁面內容</p> }],
  },
];

function renderLayout(store: AppStore = makeStore()) {
  return renderRoute({ routes, initialEntry: '/', store });
}

function signedInStore(permissions: string[]): AppStore {
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

describe('AppLayout', () => {
  it('renders brand, primary navigation, and the outlet', () => {
    renderLayout();
    expect(screen.getByLabelText('CTIP 首頁')).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: '主導覽' });
    expect(nav).toHaveTextContent('儀表板');
    expect(nav).toHaveTextContent('IOC 檢索');
    expect(screen.getByText('頁面內容')).toBeInTheDocument();
  });

  it('cycles theme preference on the toggle button', async () => {
    const store = makeStore();
    renderLayout(store);
    await userEvent.click(screen.getByRole('button', { name: '主題:跟隨系統' }));
    expect(store.getState().ui.theme).toBe('light');
    await userEvent.click(screen.getByRole('button', { name: '主題:亮色' }));
    expect(store.getState().ui.theme).toBe('dark');
  });

  it('toggles the mobile navigation menu', async () => {
    const store = makeStore();
    renderLayout(store);
    expect(screen.getByRole('navigation', { name: '行動版導覽' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '開關選單' }));
    expect(screen.queryByRole('navigation', { name: '行動版導覽' })).not.toBeInTheDocument();
    expect(store.getState().ui.sidebarCollapsed).toBe(true);
  });

  it('offers a login link while anonymous', () => {
    renderLayout();
    expect(screen.getByRole('link', { name: /登入/ })).toHaveAttribute('href', '/login');
    expect(screen.queryByRole('button', { name: /登出/ })).not.toBeInTheDocument();
  });

  it('shows the signed-in user and an API key shortcut when permitted', () => {
    renderLayout(signedInStore(['ioc:read', 'apikey:create']));
    expect(screen.getByText(sampleSession.user.displayName)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /API Key/ })).toHaveAttribute(
      'href',
      '/settings/api-keys',
    );
  });

  it('hides the API key shortcut without apikey:create', () => {
    renderLayout(signedInStore(['ioc:read']));
    expect(screen.queryByRole('link', { name: /API Key/ })).not.toBeInTheDocument();
  });

  it('clears the session on logout', async () => {
    const store = signedInStore(['ioc:read', 'apikey:create']);
    renderLayout(store);
    await userEvent.click(screen.getByRole('button', { name: /登出/ }));
    await waitFor(() => expect(selectIsAuthenticated(store.getState())).toBe(false));
  });
});
