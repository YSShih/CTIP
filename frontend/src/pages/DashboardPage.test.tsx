import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { RouteObject } from 'react-router';
import { server } from '../test/server';
import { renderRoute } from '../test/render';
import { notFoundError, sampleStatsSummary } from '../test/handlers';
import DashboardPage from './DashboardPage';

const routes: RouteObject[] = [{ path: '/', element: <DashboardPage /> }];

describe('DashboardPage', () => {
  it('shows a loading skeleton before data arrives', () => {
    renderRoute({ routes, initialEntry: '/' });
    expect(screen.getByRole('status', { name: '載入統計' })).toBeInTheDocument();
  });

  it('renders stat cards, type distribution, trend chart, and source health', async () => {
    renderRoute({ routes, initialEntry: '/' });
    expect(await screen.findByText('可見活躍 IOC')).toBeInTheDocument();
    expect(screen.getByText('254')).toBeInTheDocument();
    expect(screen.getByText('DOMAIN')).toBeInTheDocument();
    expect(screen.getByTestId('trend-chart')).toBeInTheDocument();
    const sourceList = screen.getByLabelText('來源清單');
    expect(sourceList).toHaveTextContent('Mock OpenPhish');
    expect(sourceList).toHaveTextContent('ACTIVE');
    // 近 7 日合計 = trend 各日加總
    const weekTotal = sampleStatsSummary.trend.reduce((sum, day) => sum + day.count, 0);
    expect(screen.getByText(weekTotal.toLocaleString())).toBeInTheDocument();
  });

  it('shows EmptyState when there is no visible intel', async () => {
    server.use(
      http.get('*/api/v1/stats/summary', () =>
        HttpResponse.json({ totalActive: 0, byType: {}, trend: [] }),
      ),
    );
    renderRoute({ routes, initialEntry: '/' });
    expect(await screen.findByText('尚無公開情資')).toBeInTheDocument();
  });

  it('shows ErrorState with retry on server errors', async () => {
    server.use(
      http.get('*/api/v1/stats/summary', () =>
        HttpResponse.json(
          { code: 'INTERNAL_ERROR', message: 'boom', traceId: 'trace-501' },
          { status: 500 },
        ),
      ),
    );
    renderRoute({ routes, initialEntry: '/' });
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重試' })).toBeInTheDocument();
  });

  it('shows ForbiddenState instead of blank content on 403', async () => {
    server.use(
      http.get('*/api/v1/stats/summary', () =>
        HttpResponse.json({ ...notFoundError, code: 'FORBIDDEN', status: 403 }, { status: 403 }),
      ),
    );
    renderRoute({ routes, initialEntry: '/' });
    expect(await screen.findByText('需要登入')).toBeInTheDocument();
  });
});
