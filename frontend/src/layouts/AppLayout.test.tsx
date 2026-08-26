import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { AppLayout } from './AppLayout';

function renderLayout(store: AppStore = makeStore()) {
  const router = createMemoryRouter(
    [
      {
        path: '/',
        element: <AppLayout />,
        children: [{ index: true, element: <p>頁面內容</p> }],
      },
    ],
    { initialEntries: ['/'] },
  );
  return render(
    <Provider store={store}>
      <RouterProvider router={router} />
    </Provider>,
  );
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
});
