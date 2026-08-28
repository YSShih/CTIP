import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { sampleSyncManifest } from '../test/handlers';
import { renderRoute } from '../test/render';
import { server } from '../test/server';
import SyncPage from './SyncPage';

const routes: RouteObject[] = [{ path: '/sync', element: <SyncPage /> }];

function render() {
  return renderRoute({ routes, initialEntry: '/sync' });
}

describe('SyncPage', () => {
  it('明文說明命中不代表惡意、未命中不代表安全(12 §12.6 第 3 條)', async () => {
    render();

    expect(await screen.findByText(/命中\(PRESENT\)不代表確定惡意/)).toBeInTheDocument();
    expect(screen.getByText(/未命中\(NOT PRESENT\)不代表安全/)).toBeInTheDocument();
    expect(screen.getByText('TLP:GREEN')).toBeInTheDocument();
    expect(screen.getByText(/沒有任何 Bloom 覆蓋/)).toBeInTheDocument();
    expect(screen.getByText(/撤銷與過期不會經 delta 消失/)).toBeInTheDocument();
  });

  it('列出 public 層的 manifest 欄位,含覆蓋範圍與完全同步後的 checksum', async () => {
    render();

    expect(await screen.findByRole('row', { name: /覆蓋範圍/ })).toHaveTextContent(
      'TLP:CLEAR only',
    );
    expect(screen.getByRole('row', { name: /bitSize/ })).toHaveTextContent('143,775,880');
    expect(screen.getByRole('row', { name: /hashFunctionCount/ })).toHaveTextContent('10');
    expect(screen.getByRole('row', { name: /完全同步後的 checksum/ })).toHaveTextContent(
      sampleSyncManifest.public.checksum,
    );
  });

  it('沒有租戶層時說明它的條件,不顯示空白或假資料(12 §12.6 第 4 條)', async () => {
    render();

    expect(await screen.findByText('沒有租戶層 Bloom')).toBeInTheDocument();
    expect(screen.getByText(/AMBER_STRICT/)).toBeInTheDocument();
  });

  it('租戶層存在時一併呈現', async () => {
    server.use(
      http.get('*/api/v1/sync/manifest', () =>
        HttpResponse.json({
          ...sampleSyncManifest,
          tenant: {
            ...sampleSyncManifest.public,
            scope: 'TENANT',
            datasetVersion: 12,
            bloomVersion: 3,
            capacity: 1000000,
            coverage: 'TLP:AMBER, TLP:AMBER_STRICT of your tenant',
          },
        }),
      ),
    );
    render();

    expect(await screen.findByText('租戶層(tenant)')).toBeInTheDocument();
    expect(
      screen.getAllByRole('row', { name: /覆蓋範圍/ }).map((row) => row.textContent),
    ).toContainEqual(expect.stringContaining('TLP:AMBER, TLP:AMBER_STRICT of your tenant'));
  });

  it('manifest 取不到時顯示錯誤狀態與重試', async () => {
    server.use(
      http.get('*/api/v1/sync/manifest', () =>
        HttpResponse.json(
          {
            timestamp: '2026-08-28T08:00:00Z',
            status: 403,
            code: 'FORBIDDEN',
            message: 'Missing sync:bloom permission',
            path: '/api/v1/sync/manifest',
            traceId: 'trace-403',
          },
          { status: 403 },
        ),
      ),
    );
    render();

    expect(await screen.findByRole('button', { name: /重試/ })).toBeInTheDocument();
  });

  it('沒有 public snapshot 時說明原因,不顯示假版本號', async () => {
    server.use(
      http.get('*/api/v1/sync/manifest', () =>
        HttpResponse.json({ notCovered: ['TLP:GREEN'], maxDeltaChain: 24 }),
      ),
    );
    render();

    expect(await screen.findByText('目前沒有可同步的公開 Bloom')).toBeInTheDocument();
  });
});
