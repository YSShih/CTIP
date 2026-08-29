import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { sampleThreat, secondThreat } from '../test/handlers';
import { renderRoute } from '../test/render';
import { server } from '../test/server';
import ThreatDetailPage from './ThreatDetailPage';

const routes: RouteObject[] = [
  { path: '/threats/:id', element: <ThreatDetailPage /> },
  { path: '/threats', element: <div>威脅清單替身</div> },
  { path: '/iocs/:id', element: <div>IOC 詳情替身</div> },
];

function render(id: string) {
  return renderRoute({ routes, initialEntry: `/threats/${id}` });
}

describe('ThreatDetailPage', () => {
  it('顯示摘要、別名與外部參照', async () => {
    render(sampleThreat.id);

    expect(await screen.findByText('AgentTesla')).toBeInTheDocument();
    expect(screen.getAllByLabelText('TLP:CLEAR').length).toBeGreaterThan(0);
    expect(screen.getByText('Agent Tesla')).toBeInTheDocument();
    expect(screen.getByText('mitre-attack')).toBeInTheDocument();
    expect(screen.getByText('S0331')).toBeInTheDocument();
  });

  it('關聯 IOC 少於 indicatorCount 時明說差額(不得靜默留白)', async () => {
    render(sampleThreat.id);

    expect(await screen.findByRole('list', { name: '關聯的 IOC' })).toBeInTheDocument();
    expect(screen.getByText('C2')).toBeInTheDocument();
    expect(screen.getByText(/不在你的可見範圍內/)).toBeInTheDocument();
  });

  it('MALWARE_FAMILY 顯示 STIX 投影;其他型別不顯示該區塊(§7.8.1 M2 只投影兩型)', async () => {
    render(sampleThreat.id);
    expect(await screen.findByText(/"is_family": true/)).toBeInTheDocument();
  });

  it('CAMPAIGN 沒有 STIX 投影區塊,也不顯示假的 404 面板', async () => {
    render(secondThreat.id);

    expect(await screen.findByText('Operation Nightjar')).toBeInTheDocument();
    expect(screen.queryByText('STIX 2.1 投影')).not.toBeInTheDocument();
    expect(screen.getByText(/這個威脅目前沒有關聯任何 IOC/)).toBeInTheDocument();
  });

  it('查無威脅時顯示錯誤狀態(跨租戶不可見一律 404)', async () => {
    server.use(
      http.get('*/api/v1/threats/:id', () =>
        HttpResponse.json(
          {
            timestamp: '2026-08-29T08:00:00Z',
            status: 404,
            code: 'NOT_FOUND',
            message: 'Resource not found',
            path: '/api/v1/threats/unknown',
            traceId: 'trace-404',
          },
          { status: 404 },
        ),
      ),
    );
    render('11111111-1111-4111-8111-111111111111');

    expect(await screen.findByRole('button', { name: /重試/ })).toBeInTheDocument();
  });
});
