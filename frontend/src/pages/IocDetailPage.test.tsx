import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { RouteObject } from 'react-router';
import { server } from '../test/server';
import { renderRoute } from '../test/render';
import { notFoundError, sampleIoc, sampleStixObject } from '../test/handlers';
import IocDetailPage from './IocDetailPage';

const routes: RouteObject[] = [{ path: '/iocs/:id', element: <IocDetailPage /> }];

const detailUrl = `/iocs/${sampleIoc.id}`;

describe('IocDetailPage', () => {
  it('shows a loading skeleton before data arrives', () => {
    renderRoute({ routes, initialEntry: detailUrl });
    expect(screen.getByRole('status', { name: '載入 IOC 詳情' })).toBeInTheDocument();
  });

  it('renders summary card, attribution, source records, and STIX JSON', async () => {
    renderRoute({ routes, initialEntry: detailUrl });
    expect(await screen.findByText(sampleIoc.value)).toBeInTheDocument();
    expect(screen.getByLabelText('TLP:CLEAR')).toBeInTheDocument();

    // §12.6 #2:attribution 必須顯示來源名稱與連結
    const attribution = await screen.findByLabelText('attribution');
    expect(attribution).toHaveTextContent('Mock OpenPhish');
    expect(screen.getByRole('link', { name: /來源連結/ })).toHaveAttribute(
      'href',
      'https://example.test',
    );

    expect(await screen.findByLabelText('來源觀測明細')).toHaveTextContent('ATTRIBUTION_REQUIRED');
    expect(await screen.findByText(new RegExp(sampleStixObject.id))).toBeInTheDocument();
    sampleIoc.tags.forEach((tag) => expect(screen.getAllByText(tag).length).toBeGreaterThan(0));
  });

  it('shows ErrorState with NOT_FOUND copy for missing indicators', async () => {
    renderRoute({ routes, initialEntry: '/iocs/00000000-0000-0000-0000-000000000000' });
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('NOT_FOUND')).toBeInTheDocument();
    expect(screen.getByText(/找不到這筆資料/)).toBeInTheDocument();
  });

  it('does not render non-http(s) attribution homepage as a link', async () => {
    // 來源登錄資料屬半信任面:javascript: 等 scheme 不得進入 href sink
    server.use(
      http.get('*/api/v1/iocs/:id', () =>
        HttpResponse.json({
          ...sampleIoc,
          attribution: [{ sourceName: 'Evil Source', homepage: 'javascript:alert(1)' }],
        }),
      ),
    );
    renderRoute({ routes, initialEntry: detailUrl });
    const attribution = await screen.findByLabelText('attribution');
    expect(attribution).toHaveTextContent('Evil Source');
    expect(screen.queryByRole('link', { name: /來源連結/ })).not.toBeInTheDocument();
  });

  it('surfaces an error for the source records section instead of silently omitting it', async () => {
    // §12.6 #4:sources 查詢失敗不得讓明細靜默消失
    server.use(
      http.get('*/api/v1/iocs/:id/sources', () =>
        HttpResponse.json(
          { ...notFoundError, code: 'INTERNAL_ERROR', status: 500 },
          { status: 500 },
        ),
      ),
    );
    renderRoute({ routes, initialEntry: detailUrl });
    expect(await screen.findByText(sampleIoc.value)).toBeInTheDocument();
    expect(await screen.findByText(/來源觀測明細載入失敗/)).toBeInTheDocument();
    expect(screen.queryByLabelText('來源觀測明細')).not.toBeInTheDocument();
  });

  it('shows ForbiddenState instead of blank content on 403', async () => {
    server.use(
      http.get('*/api/v1/iocs/:id', () =>
        HttpResponse.json({ ...notFoundError, code: 'FORBIDDEN', status: 403 }, { status: 403 }),
      ),
    );
    renderRoute({ routes, initialEntry: detailUrl });
    expect(await screen.findByText('需要登入')).toBeInTheDocument();
  });
});
