import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { RouteObject } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { sampleStixMarking, sampleStixObject, sampleStixRelationship } from '../test/handlers';
import { renderRoute } from '../test/render';
import StixViewerPage from './StixViewerPage';

/**
 * Cytoscape 在 jsdom 沒有 canvas,而且圖形本身不是要驗的東西——
 * 要驗的是「頁面餵給它什麼元素」與「它回報點擊時頁面怎麼反應」。
 */
interface FakeCytoscape {
  added: { data: { id: string } }[];
  tap: (id: string) => void;
  destroyed: boolean;
}

const instances: FakeCytoscape[] = [];

vi.mock('cytoscape', () => ({
  default: () => {
    const handlers: ((event: { target: { id: () => string } }) => void)[] = [];
    const instance: FakeCytoscape = {
      added: [],
      tap: (id) => handlers.forEach((handler) => handler({ target: { id: () => id } })),
      destroyed: false,
    };
    instances.push(instance);
    const collection = {
      remove: () => undefined,
      removeClass: () => collection,
      addClass: () => collection,
    };
    return {
      on: (
        _event: string,
        _selector: string,
        handler: (event: { target: { id: () => string } }) => void,
      ) => handlers.push(handler),
      elements: () => collection,
      nodes: () => collection,
      getElementById: () => collection,
      add: (elements: { data: { id: string } }[]) => {
        instance.added = elements;
      },
      layout: () => ({ run: () => undefined }),
      destroy: () => {
        instance.destroyed = true;
      },
    };
  },
}));

const routes: RouteObject[] = [{ path: '/stix/:id', element: <StixViewerPage /> }];

const MARKING_ID = sampleStixMarking.id;

function currentGraph() {
  return instances[instances.length - 1];
}

describe('StixViewerPage', () => {
  beforeEach(() => {
    instances.length = 0;
  });

  it('先顯示載入狀態', () => {
    renderRoute({ routes, initialEntry: `/stix/${sampleStixObject.id}` });

    expect(screen.getByRole('status', { name: '載入 STIX 物件' })).toBeInTheDocument();
  });

  it('畫出起點物件與它的內嵌參照,並顯示原始 JSON', async () => {
    renderRoute({ routes, initialEntry: `/stix/${sampleStixObject.id}` });

    expect(
      await screen.findByRole('img', { name: 'STIX 關聯圖,2 個物件、1 條關聯' }),
    ).toBeInTheDocument();
    expect(currentGraph().added.map((element) => element.data.id)).toEqual([
      sampleStixObject.id,
      MARKING_ID,
      `${sampleStixObject.id}|object marking|${MARKING_ID}`,
    ]);
    expect(await screen.findByText(new RegExp(sampleStixObject.pattern_type))).toBeInTheDocument();
  });

  it('點擊尚未載入的節點會展開它並切換詳情面板', async () => {
    renderRoute({ routes, initialEntry: `/stix/${sampleStixObject.id}` });
    await screen.findByRole('img', { name: /STIX 關聯圖/ });

    currentGraph().tap(MARKING_ID);

    // 詳情面板的 <pre> 是一整塊文字節點,只能以片段比對
    expect(await screen.findByText(/"name": "TLP:CLEAR"/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '回到起點物件' })).toBeInTheDocument();
  });

  it('取消勾選型別會把該型別的節點與相連的邊都移出圖', async () => {
    const user = userEvent.setup();
    renderRoute({ routes, initialEntry: `/stix/${sampleStixObject.id}` });
    await screen.findByRole('img', { name: /STIX 關聯圖/ });

    await user.click(screen.getByRole('checkbox', { name: 'marking-definition' }));

    await waitFor(() =>
      expect(currentGraph().added.map((element) => element.data.id)).toEqual([sampleStixObject.id]),
    );
  });

  it('SRO 起點畫成兩端之間的一條邊', async () => {
    renderRoute({ routes, initialEntry: `/stix/${sampleStixRelationship.id}` });

    expect(
      await screen.findByRole('img', { name: 'STIX 關聯圖,2 個物件、1 條關聯' }),
    ).toBeInTheDocument();
    expect(currentGraph().added.map((element) => element.data.id)).toEqual([
      sampleStixRelationship.source_ref,
      sampleStixRelationship.target_ref,
      `${sampleStixRelationship.source_ref}|indicates|${sampleStixRelationship.target_ref}`,
    ]);
  });

  it('查無起點物件時顯示空狀態而不是空白畫布', async () => {
    renderRoute({ routes, initialEntry: '/stix/indicator--00000000-0000-0000-0000-000000000000' });

    expect(await screen.findByText('查無此 STIX 物件')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /STIX 關聯圖/ })).not.toBeInTheDocument();
  });
});
