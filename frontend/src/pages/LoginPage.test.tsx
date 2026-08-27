import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore } from '../stores';
import { selectIsAuthenticated } from '../stores/authSlice';
import { renderRoute } from '../test/render';
import { sampleSession } from '../test/handlers';
import LoginPage from './LoginPage';
import RegisterPage from './RegisterPage';

const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/', element: <p>儀表板</p> },
];

async function fillCredentials(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('電子郵件'), email);
  await user.type(screen.getByLabelText('密碼'), password);
  return user;
}

describe('LoginPage', () => {
  it('renders an accessible form with labelled fields', () => {
    renderRoute({ routes, initialEntry: '/login' });
    expect(screen.getByRole('heading', { name: '登入' })).toBeInTheDocument();
    expect(screen.getByLabelText('電子郵件')).toBeRequired();
    expect(screen.getByLabelText('密碼')).toBeRequired();
  });

  it('establishes a session and navigates home on success', async () => {
    const store = makeStore();
    renderRoute({ routes, initialEntry: '/login', store });
    const user = await fillCredentials('analyst@example.org', 'test-password-1234');
    await user.click(screen.getByRole('button', { name: '登入' }));

    expect(await screen.findByText('儀表板')).toBeInTheDocument();
    await waitFor(() => expect(selectIsAuthenticated(store.getState())).toBe(true));
    expect(store.getState().auth.permissions).toEqual(sampleSession.user.permissions);
    expect(store.getState().auth.role).toBe('TENANT_ADMIN');
  });

  it('shows an inline alert when credentials are rejected', async () => {
    const store = makeStore();
    renderRoute({ routes, initialEntry: '/login', store });
    const user = await fillCredentials('analyst@example.org', 'wrong-password');
    await user.click(screen.getByRole('button', { name: '登入' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('帳號或密碼不正確');
    expect(selectIsAuthenticated(store.getState())).toBe(false);
  });
});

describe('RegisterPage', () => {
  it('explains the tenant that registration creates and signs the user in', async () => {
    const store = makeStore();
    renderRoute({ routes, initialEntry: '/register', store });
    expect(screen.getByText(/註冊會建立一個專屬租戶/)).toBeInTheDocument();

    const user = await fillCredentials('new-analyst@example.org', 'test-password-1234');
    await user.click(screen.getByRole('button', { name: '建立帳號' }));

    expect(await screen.findByText('儀表板')).toBeInTheDocument();
    await waitFor(() => expect(selectIsAuthenticated(store.getState())).toBe(true));
  });
});
