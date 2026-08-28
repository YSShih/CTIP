import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { sessionEstablished } from '../stores/authSlice';
import { sampleSession } from '../test/handlers';
import { renderRoute } from '../test/render';
import { server } from '../test/server';
import IocImportPage from './IocImportPage';

const routes: RouteObject[] = [{ path: '/iocs/import', element: <IocImportPage /> }];

function authenticatedStore(): AppStore {
  const store = makeStore();
  store.dispatch(
    sessionEstablished({
      accessToken: sampleSession.accessToken,
      refreshToken: sampleSession.refreshToken,
      user: { id: sampleSession.user.userId, name: sampleSession.user.displayName },
      tenantId: sampleSession.user.tenantId,
      role: sampleSession.user.role,
      permissions: ['ioc:import'],
    }),
  );
  return store;
}

function render() {
  return renderRoute({ routes, initialEntry: '/iocs/import', store: authenticatedStore() });
}

async function pasteAndImport(payload: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('內容(可直接貼上)'), payload);
  await user.click(screen.getByRole('button', { name: '開始匯入' }));
}

describe('IocImportPage', () => {
  it('接受後顯示 job 進度,而不是只回一句「已送出」(§9.7 非同步)', async () => {
    render();
    await pasteAndImport('value{enter}a.example.org');

    const progress = await screen.findByLabelText('匯入進度');
    expect(progress).toHaveTextContent('部分完成');
    expect(progress).toHaveTextContent('0f2d7b3c-9a41-4a7e-8b2f-1c5d6e7f8a90');
  });

  it('逐筆結果摘要包含新建立/合併/拒絕三個計數', async () => {
    render();
    await pasteAndImport('value{enter}a.example.org');

    const progress = await screen.findByLabelText('匯入進度');
    expect(progress).toHaveTextContent('新建立');
    expect(progress).toHaveTextContent('已合併');
    expect(progress).toHaveTextContent('已拒絕');
  });

  it('413 說明是「這一次太大,拆小就能過」而不是方案不支援', async () => {
    server.use(
      http.post('*/api/v1/iocs/import', () =>
        HttpResponse.json(
          {
            timestamp: '2026-08-28T09:00:00Z',
            status: 413,
            code: 'PAYLOAD_TOO_LARGE',
            message: 'Import exceeds limit of 10000 rows',
            path: '/api/v1/iocs/import',
            traceId: 'trace-2',
            details: [],
          },
          { status: 413 },
        ),
      ),
    );
    render();
    await pasteAndImport('value{enter}a.example.org');
    expect(await screen.findByRole('alert')).toHaveTextContent('拆成多個檔案');
  });

  it('方案不允許匯入時回 403 並說明要升級', async () => {
    server.use(
      http.post('*/api/v1/iocs/import', () =>
        HttpResponse.json(
          {
            timestamp: '2026-08-28T09:00:00Z',
            status: 403,
            code: 'PLAN_LIMIT_EXCEEDED',
            message: 'Bulk import is not available on this plan',
            path: '/api/v1/iocs/import',
            traceId: 'trace-3',
            details: [],
          },
          { status: 403 },
        ),
      ),
    );
    render();
    await pasteAndImport('value{enter}a.example.org');
    expect(await screen.findByRole('alert')).toHaveTextContent('升級方案');
  });
});
