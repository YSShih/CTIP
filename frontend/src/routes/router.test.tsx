import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore } from '../stores';
import { routes } from './index';

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  return render(
    <Provider store={makeStore()}>
      <RouterProvider router={router} />
    </Provider>,
  );
}

describe('routes', () => {
  it('renders DashboardPage at /', () => {
    renderAt('/');
    expect(screen.getByRole('heading', { name: '儀表板' })).toBeInTheDocument();
  });

  it('renders IocSearchPage at /iocs', () => {
    renderAt('/iocs');
    expect(screen.getByRole('heading', { name: 'IOC 檢索' })).toBeInTheDocument();
  });

  it('renders IocDetailPage at /iocs/:id with the id visible', () => {
    renderAt('/iocs/1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e');
    expect(screen.getByRole('heading', { name: 'IOC 詳情' })).toBeInTheDocument();
    expect(screen.getByText(/1f0d2c4e/)).toBeInTheDocument();
  });

  it('renders NotFoundPage for unknown paths inside the layout', () => {
    renderAt('/does-not-exist');
    expect(screen.getByText('404 — 頁面不存在')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '回到儀表板' })).toBeInTheDocument();
  });
});
