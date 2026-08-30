import { describe, expect, it } from 'vitest';
import { buildGraph, isStixId, stixTypesIn, stixTypeOf } from './graph';
import type { StixObject } from './types';

const INDICATOR = 'indicator--1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e';
const MALWARE = 'malware--5b8f9d2e-1c3a-4f7b-9e0d-2a4c6b8d0f13';
const MARKING = 'marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487';
const RELATIONSHIP = 'relationship--0f9c1a2b-3d4e-4a5b-8c7d-9e0f1a2b3c4d';

function object(payload: Record<string, unknown>): StixObject {
  return payload as unknown as StixObject;
}

const indicator = object({
  type: 'indicator',
  id: INDICATOR,
  name: 'DOMAIN: mal-8.ctip-sample.net',
  object_marking_refs: [MARKING],
});

const relationship = object({
  type: 'relationship',
  id: RELATIONSHIP,
  relationship_type: 'indicates',
  source_ref: INDICATOR,
  target_ref: MALWARE,
  object_marking_refs: [MARKING],
});

describe('isStixId', () => {
  it('接受 type--uuid,拒絕其他字串', () => {
    expect(isStixId(INDICATOR)).toBe(true);
    expect(isStixId('indicator--not-a-uuid')).toBe(false);
    expect(isStixId(42)).toBe(false);
  });

  it('stixTypeOf 取 -- 之前的型別', () => {
    expect(stixTypeOf(MARKING)).toBe('marking-definition');
  });
});

describe('buildGraph', () => {
  it('內嵌參照成為邊,被參照但未載入的物件成為 pending 節點', () => {
    const graph = buildGraph(new Map([[INDICATOR, indicator]]));

    expect(graph.nodes.map((node) => node.id)).toEqual([INDICATOR, MARKING]);
    expect(graph.nodes[0].loaded).toBe(true);
    expect(graph.nodes[1].loaded).toBe(false);
    expect(graph.edges).toEqual([
      {
        id: `${INDICATOR}|object marking|${MARKING}`,
        source: INDICATOR,
        target: MARKING,
        label: 'object marking',
      },
    ]);
  });

  it('SRO 畫成兩端之間的一條邊,relationship 本身不入圖', () => {
    const graph = buildGraph(new Map([[RELATIONSHIP, relationship]]));

    expect(graph.nodes.map((node) => node.id)).toEqual([INDICATOR, MALWARE]);
    expect(graph.edges).toHaveLength(1);
    expect(graph.edges[0]).toMatchObject({
      source: INDICATOR,
      target: MALWARE,
      label: 'indicates',
    });
  });

  it('未載入的 relationship 仍以節點呈現(尚不知道兩端是誰)', () => {
    const graph = buildGraph(new Map([[RELATIONSHIP, undefined]]));

    expect(graph.nodes.map((node) => node.id)).toEqual([RELATIONSHIP]);
    expect(graph.edges).toEqual([]);
  });

  it('展開後的節點覆蓋掉先前的 pending 狀態,節點不重複', () => {
    const graph = buildGraph(
      new Map([
        [RELATIONSHIP, relationship],
        [INDICATOR, indicator],
      ]),
    );

    expect(graph.nodes.filter((node) => node.id === INDICATOR)).toHaveLength(1);
    expect(graph.nodes.find((node) => node.id === INDICATOR)?.loaded).toBe(true);
    expect(graph.nodes.map((node) => node.id)).toContain(MARKING);
  });

  it('隱藏型別時,連到被隱藏節點的邊一併移除', () => {
    const graph = buildGraph(new Map([[INDICATOR, indicator]]), {
      hiddenTypes: new Set(['marking-definition']),
    });

    expect(graph.nodes.map((node) => node.id)).toEqual([INDICATOR]);
    expect(graph.edges).toEqual([]);
  });

  it('節點標籤優先取 name,沒有才退回截短的 id', () => {
    const graph = buildGraph(new Map([[INDICATOR, indicator]]));

    expect(graph.nodes[0].label).toBe('DOMAIN: mal-8.ctip-sample.net');
    expect(graph.nodes[1].label).toMatch(/^marking-definition--94868c89…$/);
  });

  it('過長的標籤截斷', () => {
    const long = object({ type: 'indicator', id: INDICATOR, name: 'x'.repeat(80) });

    expect(buildGraph(new Map([[INDICATOR, long]])).nodes[0].label).toHaveLength(46);
  });

  it('stixTypesIn 回傳圖上出現過的型別,已排序', () => {
    expect(stixTypesIn(new Map([[INDICATOR, indicator]]))).toEqual([
      'indicator',
      'marking-definition',
    ]);
  });
});
