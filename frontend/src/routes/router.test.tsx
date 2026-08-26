import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderRoute } from '../test/render';
import { routes } from './index';

describe('routes', () => {
  it('renders DashboardPage at /', async () => {
    renderRoute({ routes, initialEntry: '/' });
    expect(await screen.findByRole('heading', { name: '儀表板' })).toBeInTheDocument();
  });

  it('renders IocSearchPage at /iocs', async () => {
    renderRoute({ routes, initialEntry: '/iocs' });
    expect(await screen.findByRole('heading', { name: 'IOC 檢索' })).toBeInTheDocument();
  });

  it('renders IocDetailPage at /iocs/:id', async () => {
    renderRoute({ routes, initialEntry: '/iocs/1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e' });
    expect(await screen.findByRole('heading', { name: 'IOC 詳情' })).toBeInTheDocument();
  });

  it('renders NotFoundPage for unknown paths inside the layout', async () => {
    renderRoute({ routes, initialEntry: '/does-not-exist' });
    expect(await screen.findByText('404 — 頁面不存在')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '回到儀表板' })).toBeInTheDocument();
  });
});
