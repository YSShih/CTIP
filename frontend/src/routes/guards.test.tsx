import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { sessionEstablished } from '../stores/authSlice';
import { RequireAuth } from './RequireAuth';
import { RequirePermission } from './RequirePermission';

function renderGuarded(store: AppStore, guarded: RouteObject) {
  const router = createMemoryRouter([guarded], { initialEntries: ['/secure'] });
  return render(
    <Provider store={store}>
      <RouterProvider router={router} />
    </Provider>,
  );
}

const session = {
  accessToken: 'token',
  user: { id: 'u1', name: 'Analyst' },
  permissions: ['ioc:write'],
};

describe('RequireAuth', () => {
  const route: RouteObject = {
    path: '/secure',
    element: <RequireAuth />,
    children: [{ index: true, element: <p>機密內容</p> }],
  };

  it('shows ForbiddenState for anonymous users instead of blank content', () => {
    renderGuarded(makeStore(), route);
    expect(screen.getByText('需要登入')).toBeInTheDocument();
    expect(screen.queryByText('機密內容')).not.toBeInTheDocument();
  });

  it('renders the outlet once a session is established', () => {
    const store = makeStore();
    store.dispatch(sessionEstablished(session));
    renderGuarded(store, route);
    expect(screen.getByText('機密內容')).toBeInTheDocument();
  });
});

describe('RequirePermission', () => {
  const route: RouteObject = {
    path: '/secure',
    element: <RequirePermission permission="admin:read" />,
    children: [{ index: true, element: <p>管理內容</p> }],
  };

  it('shows upgrade guidance when the permission is missing', () => {
    const store = makeStore();
    store.dispatch(sessionEstablished(session));
    renderGuarded(store, route);
    expect(screen.getByText('權限不足')).toBeInTheDocument();
    expect(screen.queryByText('管理內容')).not.toBeInTheDocument();
  });

  it('renders the outlet when the permission is granted', () => {
    const store = makeStore();
    store.dispatch(sessionEstablished({ ...session, permissions: ['admin:read'] }));
    renderGuarded(store, route);
    expect(screen.getByText('管理內容')).toBeInTheDocument();
  });
});
