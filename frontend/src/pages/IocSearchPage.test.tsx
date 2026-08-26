import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { RouteObject } from 'react-router';
import { server } from '../test/server';
import { renderRoute } from '../test/render';
import { notFoundError, sampleIoc } from '../test/handlers';
import IocSearchPage from './IocSearchPage';

const routes: RouteObject[] = [
  { path: '/iocs', element: <IocSearchPage /> },
  { path: '/iocs/:id', element: <p>詳情頁</p> },
];

const originalOffsetHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetHeight');
const originalOffsetWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth');

beforeEach(() => {
  Object.defineProperty(HTMLElement.prototype, 'offsetHeight', { configurable: true, value: 400 });
  Object.defineProperty(HTMLElement.prototype, 'offsetWidth', { configurable: true, value: 800 });
});

afterEach(() => {
  if (originalOffsetHeight) {
    Object.defineProperty(HTMLElement.prototype, 'offsetHeight', originalOffsetHeight);
  }
  if (originalOffsetWidth) {
    Object.defineProperty(HTMLElement.prototype, 'offsetWidth', originalOffsetWidth);
  }
});

describe('IocSearchPage', () => {
  it('shows a loading skeleton before data arrives', () => {
    renderRoute({ routes, initialEntry: '/iocs' });
    expect(screen.getByRole('status', { name: '載入 IOC' })).toBeInTheDocument();
  });

  it('renders the virtualized result table with TLP badges', async () => {
    renderRoute({ routes, initialEntry: '/iocs' });
    expect(await screen.findByText(sampleIoc.value)).toBeInTheDocument();
    expect(screen.getAllByLabelText('TLP:CLEAR').length).toBeGreaterThan(0);
    expect(screen.getByRole('form', { name: 'IOC 篩選' })).toBeInTheDocument();
    expect(screen.getByText(/還有更多/)).toBeInTheDocument();
  });

  it('writes submitted filters to URL search params and resets cursor', async () => {
    const { router } = renderRoute({ routes, initialEntry: '/iocs?cursor=abc' });
    await screen.findByText(sampleIoc.value);
    await userEvent.type(screen.getByPlaceholderText(/IOC 值子字串/), 'ctip-sample');
    await userEvent.selectOptions(screen.getByLabelText('型別'), 'DOMAIN');
    await userEvent.click(screen.getByRole('button', { name: /搜尋/ }));
    await waitFor(() => {
      const params = new URLSearchParams(router.state.location.search);
      expect(params.get('q')).toBe('ctip-sample');
      expect(params.get('type')).toBe('DOMAIN');
      expect(params.get('cursor')).toBeNull();
    });
  });

  it('advances the cursor via the pager', async () => {
    const { router } = renderRoute({ routes, initialEntry: '/iocs' });
    await screen.findByText(sampleIoc.value);
    await userEvent.click(screen.getByRole('button', { name: /下一頁/ }));
    await waitFor(() => {
      expect(new URLSearchParams(router.state.location.search).get('cursor')).toBe('cursor-page-2');
    });
  });

  it('shows EmptyState when nothing matches', async () => {
    server.use(http.get('*/api/v1/iocs', () => HttpResponse.json({ items: [], hasMore: false })));
    renderRoute({ routes, initialEntry: '/iocs' });
    expect(await screen.findByText('查無符合的 IOC')).toBeInTheDocument();
  });

  it('shows ErrorState with retry on server errors', async () => {
    server.use(
      http.get('*/api/v1/iocs', () =>
        HttpResponse.json(
          { code: 'INTERNAL_ERROR', message: 'boom', traceId: 'trace-500' },
          { status: 500 },
        ),
      ),
    );
    renderRoute({ routes, initialEntry: '/iocs' });
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('INTERNAL_ERROR')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重試' })).toBeInTheDocument();
  });

  it('shows ForbiddenState instead of blank content on 403', async () => {
    server.use(
      http.get('*/api/v1/iocs', () =>
        HttpResponse.json({ ...notFoundError, code: 'FORBIDDEN', status: 403 }, { status: 403 }),
      ),
    );
    renderRoute({ routes, initialEntry: '/iocs' });
    expect(await screen.findByText('需要登入')).toBeInTheDocument();
  });
});
