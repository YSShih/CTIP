import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { renderRoute } from '../test/render';
import { server } from '../test/server';
import ThreatFeedPage from './ThreatFeedPage';

const routes: RouteObject[] = [
  { path: '/threats', element: <ThreatFeedPage /> },
  { path: '/threats/:id', element: <p>威脅詳情替身</p> },
];

// 虛擬化表格在 jsdom 需要非零的容器尺寸,否則不會渲染任何一列
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

describe('ThreatFeedPage', () => {
  it('列出威脅,每一列都帶 TLP 標記(§12.6 #1)', async () => {
    renderRoute({ routes, initialEntry: '/threats' });

    expect(await screen.findByText('AgentTesla')).toBeInTheDocument();
    expect(screen.getByText('Operation Nightjar')).toBeInTheDocument();
    expect(screen.getAllByLabelText('TLP:CLEAR').length).toBeGreaterThan(0);
    expect(screen.getAllByLabelText('TLP:GREEN').length).toBeGreaterThan(0);
    expect(screen.getByRole('form', { name: '威脅篩選' })).toBeInTheDocument();
  });

  it('點一列進入詳情頁', async () => {
    renderRoute({ routes, initialEntry: '/threats' });

    await userEvent.click(await screen.findByText('AgentTesla'));

    expect(await screen.findByText('威脅詳情替身')).toBeInTheDocument();
  });

  it('送出篩選後寫進 URL 並重置 cursor(§12.3)', async () => {
    const { router } = renderRoute({ routes, initialEntry: '/threats?cursor=abc' });
    await screen.findByText('AgentTesla');

    await userEvent.type(screen.getByPlaceholderText(/名稱子字串/), 'AgentTesla');
    await userEvent.selectOptions(screen.getByLabelText('型別'), 'MALWARE_FAMILY');
    await userEvent.click(screen.getByRole('button', { name: /搜尋/ }));

    await waitFor(() => {
      const params = new URLSearchParams(router.state.location.search);
      expect(params.get('name')).toBe('AgentTesla');
      expect(params.get('type')).toBe('MALWARE_FAMILY');
      expect(params.get('cursor')).toBeNull();
    });
  });

  it('查無資料時說明「已退役預設不列出」,不留白', async () => {
    server.use(
      http.get('*/api/v1/threats', () =>
        HttpResponse.json({ items: [], hasMore: false, nextCursor: null }),
      ),
    );
    renderRoute({ routes, initialEntry: '/threats' });

    expect(await screen.findByText('查無符合的威脅')).toBeInTheDocument();
    expect(screen.getByText(/已退役\(RETIRED\)的威脅預設不列出/)).toBeInTheDocument();
  });

  it('查詢失敗時顯示錯誤狀態與重試', async () => {
    server.use(
      http.get('*/api/v1/threats', () =>
        HttpResponse.json(
          {
            timestamp: '2026-08-29T08:00:00Z',
            status: 400,
            code: 'INVALID_REQUEST',
            message: 'Invalid value for type',
            path: '/api/v1/threats',
            traceId: 'trace-400',
          },
          { status: 400 },
        ),
      ),
    );
    renderRoute({ routes, initialEntry: '/threats?type=NOPE' });

    expect(await screen.findByRole('button', { name: /重試/ })).toBeInTheDocument();
  });
});
