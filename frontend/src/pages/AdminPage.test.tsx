import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { makeStore, type AppStore } from '../stores';
import { sessionEstablished } from '../stores/authSlice';
import { sampleDataSubjectReport, sampleSession, sampleTenantOverview } from '../test/handlers';
import { renderRoute } from '../test/render';
import { server } from '../test/server';
import AdminPage from './AdminPage';

const routes: RouteObject[] = [{ path: '/admin', element: <AdminPage /> }];

function authenticatedStore(): AppStore {
  const store = makeStore();
  store.dispatch(
    sessionEstablished({
      accessToken: sampleSession.accessToken,
      refreshToken: sampleSession.refreshToken,
      user: { id: sampleSession.user.userId, name: sampleSession.user.displayName },
      tenantId: sampleSession.user.tenantId,
      role: sampleSession.user.role,
      permissions: [...sampleSession.user.permissions, 'system:admin'],
    }),
  );
  return store;
}

function render() {
  return renderRoute({ routes, initialEntry: '/admin', store: authenticatedStore() });
}

describe('AdminPage', () => {
  it('lists every tenant with its current plan', async () => {
    render();

    const row = await screen.findByTestId('tenant-row');
    expect(row).toHaveTextContent(sampleTenantOverview.slug);
    expect(row).toHaveTextContent(sampleTenantOverview.planCode);
  });

  it('assigns a plan to a tenant', async () => {
    const assigned: string[] = [];
    server.use(
      http.patch('*/api/v1/admin/tenants/:id/subscription', async ({ request }) => {
        const body = (await request.json()) as { planCode: string };
        assigned.push(body.planCode);
        return HttpResponse.json({
          subscriptionId: '7c1f0a35-2b6d-4c11-9e83-5f0a1b2c3d4e',
          tenantId: sampleTenantOverview.id,
          planCode: body.planCode,
          status: 'ACTIVE',
        });
      }),
    );
    render();

    await userEvent.selectOptions(
      await screen.findByLabelText(`${sampleTenantOverview.slug} 的方案`),
      'ENTERPRISE',
    );
    await userEvent.click(screen.getByRole('button', { name: '套用' }));

    expect(assigned).toEqual(['ENTERPRISE']);
  });

  it('rebuilds the STIX projections and reports how many were rebuilt', async () => {
    render();

    await userEvent.click(await screen.findByRole('button', { name: '重建全部 STIX 投影' }));

    expect(await screen.findByTestId('stix-rebuild-result')).toHaveTextContent('1020');
  });

  /** 刪除必須說清楚稽核軌跡不在刪除範圍內(13 §13.4、§13.5 規則 1)。 */
  it('reports what a data subject erasure deleted and what it retained', async () => {
    render();

    await userEvent.type(await screen.findByLabelText('使用者 ID'), sampleDataSubjectReport.userId);
    await userEvent.click(screen.getByRole('button', { name: '查詢持有的個資' }));
    expect(await screen.findByTestId('data-subject-report')).toHaveTextContent(
      sampleDataSubjectReport.email,
    );

    await userEvent.click(screen.getByRole('button', { name: '執行刪除' }));
    expect(await screen.findByTestId('data-subject-erasure')).toHaveTextContent(
      '417 筆稽核紀錄依保留政策留存',
    );
  });
});
