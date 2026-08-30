import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { sessionEstablished } from '../stores/authSlice';
import { sampleSession } from '../test/handlers';
import { renderRoute } from '../test/render';
import SettingsPage from './SettingsPage';

const routes: RouteObject[] = [{ path: '/settings', element: <SettingsPage /> }];

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
  return renderRoute({ routes, initialEntry: '/settings', store: authenticatedStore() });
}

async function fillPasswords(user: ReturnType<typeof userEvent.setup>, current: string) {
  await user.type(screen.getByLabelText('目前密碼'), current);
  await user.type(screen.getByLabelText('新密碼'), 'brand-new-password-1234');
  await user.type(screen.getByLabelText('再輸入一次新密碼'), 'brand-new-password-1234');
}

describe('SettingsPage', () => {
  it('顯示目前身分:使用者、角色、租戶與權限數', () => {
    render();

    const account = screen.getByLabelText('帳號資訊');
    expect(account).toHaveTextContent(sampleSession.user.displayName);
    expect(account).toHaveTextContent(sampleSession.user.role);
    expect(account).toHaveTextContent(sampleSession.user.tenantId);
    expect(account).toHaveTextContent(`${sampleSession.user.permissions.length} 項`);
  });

  it('主題切換寫進 uiSlice(§12.3:主題屬 Redux + localStorage)', async () => {
    const user = userEvent.setup();
    const { store } = render();
    expect(store.getState().ui.theme).toBe('system');

    await user.click(screen.getByRole('button', { name: '深色' }));

    expect(store.getState().ui.theme).toBe('dark');
    expect(screen.getByRole('button', { name: '深色' })).toHaveAttribute('aria-pressed', 'true');
  });

  /** 這一頁存在的主要理由:change-password 端點在此之前沒有任何前端入口。 */
  it('變更密碼成功後清掉 session 並說明有幾個工作階段被登出', async () => {
    const user = userEvent.setup();
    const { store } = render();

    await fillPasswords(user, 'current-password-1234');
    await user.click(screen.getByRole('button', { name: '變更密碼' }));

    await waitFor(() => expect(store.getState().auth.accessToken).toBeNull());
    expect(store.getState().auth.refreshToken).toBeNull();
    // toast 由 AppLayout 的 Toaster 呈現,頁面單獨渲染時只驗佇列(§12.3:toast 屬 Redux)
    expect(store.getState().toast.toasts).toContainEqual(
      expect.objectContaining({
        kind: 'success',
        message: expect.stringContaining('3 個工作階段已登出'),
      }),
    );
  });

  it('目前密碼錯誤時顯示錯誤且不動 session', async () => {
    const user = userEvent.setup();
    const { store } = render();

    await fillPasswords(user, 'wrong-password');
    await user.click(screen.getByRole('button', { name: '變更密碼' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('目前密碼不正確');
    expect(store.getState().auth.accessToken).not.toBeNull();
  });

  /** 打錯確認欄的代價是被鎖在門外,而伺服器端沒有任何機會攔它。 */
  it('兩次新密碼不一致時擋住送出', async () => {
    const user = userEvent.setup();
    const { store } = render();

    await user.type(screen.getByLabelText('目前密碼'), 'current-password-1234');
    await user.type(screen.getByLabelText('新密碼'), 'brand-new-password-1234');
    await user.type(screen.getByLabelText('再輸入一次新密碼'), 'typo-typo-typo-1234');

    expect(screen.getByRole('alert')).toHaveTextContent('兩次輸入的新密碼不一致');
    expect(screen.getByRole('button', { name: '變更密碼' })).toBeDisabled();
    expect(store.getState().auth.accessToken).not.toBeNull();
  });

  it('列出目前角色有權限的其他設定頁', () => {
    render();

    expect(screen.getByRole('link', { name: /API Key 管理/ })).toHaveAttribute(
      'href',
      '/settings/api-keys',
    );
  });
});
