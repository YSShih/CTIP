import type { StixObject } from './types';

/**
 * STIX 物件集合 → 圖形元素(§12.6 STIX Viewer)。
 *
 * 純函式,不碰 Cytoscape 也不碰 React——圖的正確性因此可以單獨測。
 * 兩條 STIX 2.1 的慣例:
 *  1. SRO(`relationship`)本身不畫成節點,畫成兩端之間的一條邊(這才是它的語意)
 *  2. 其餘 `*_ref` / `*_refs` 內嵌參照畫成邊,標籤取自欄位名
 */

export interface GraphNode {
  id: string;
  stixType: string;
  label: string;
  /** false 代表只是被別的物件參照到,內容尚未載入——點擊即展開 */
  loaded: boolean;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  label: string;
}

export interface StixGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

/** `type--uuid`;marking-definition 等固定物件也是同一形狀。 */
const STIX_ID = /^[a-z][a-z0-9-]*--[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isStixId(value: unknown): value is string {
  return typeof value === 'string' && STIX_ID.test(value);
}

export function stixTypeOf(stixId: string): string {
  return stixId.slice(0, stixId.indexOf('--'));
}

/** 節點文字:優先用人看得懂的欄位,都沒有才退回截短的 id。 */
function labelOf(stixId: string, object: StixObject | undefined): string {
  const named = object as Record<string, unknown> | undefined;
  for (const key of ['name', 'value', 'pattern'] as const) {
    const candidate = named?.[key];
    if (typeof candidate === 'string' && candidate.length > 0) {
      return candidate.length > 48 ? `${candidate.slice(0, 45)}…` : candidate;
    }
  }
  return `${stixTypeOf(stixId)}--${stixId.slice(stixId.indexOf('--') + 2, stixId.indexOf('--') + 10)}…`;
}

function refsOf(value: unknown): string[] {
  if (isStixId(value)) return [value];
  if (Array.isArray(value)) return value.filter(isStixId);
  return [];
}

function edgeLabelOf(key: string): string {
  return key.replace(/_refs?$/, '').replace(/_/g, ' ');
}

interface Accumulator {
  nodes: Map<string, GraphNode>;
  edges: Map<string, GraphEdge>;
}

function touch(accumulator: Accumulator, stixId: string, object: StixObject | undefined): void {
  const existing = accumulator.nodes.get(stixId);
  if (existing?.loaded) return;
  accumulator.nodes.set(stixId, {
    id: stixId,
    stixType: stixTypeOf(stixId),
    label: labelOf(stixId, object),
    loaded: object !== undefined,
  });
}

function addEdge(accumulator: Accumulator, source: string, target: string, label: string): void {
  const id = `${source}|${label}|${target}`;
  if (!accumulator.edges.has(id)) accumulator.edges.set(id, { id, source, target, label });
}

/** SRO:兩端之間一條邊,relationship 物件本身不入圖。 */
function addRelationship(accumulator: Accumulator, object: StixObject): boolean {
  const record = object as Record<string, unknown>;
  const source = record.source_ref;
  const target = record.target_ref;
  if (!isStixId(source) || !isStixId(target)) return false;
  touch(accumulator, source, undefined);
  touch(accumulator, target, undefined);
  const type = record.relationship_type;
  addEdge(accumulator, source, target, typeof type === 'string' ? type : 'related-to');
  return true;
}

function addEmbeddedRefs(accumulator: Accumulator, stixId: string, object: StixObject): void {
  for (const [key, value] of Object.entries(object as Record<string, unknown>)) {
    if (!key.endsWith('_ref') && !key.endsWith('_refs')) continue;
    for (const ref of refsOf(value)) {
      touch(accumulator, ref, undefined);
      addEdge(accumulator, stixId, ref, edgeLabelOf(key));
    }
  }
}

export interface BuildGraphOptions {
  /** 被隱藏的 STIX 型別(基本篩選);邊只要有一端被隱藏就一併移除 */
  hiddenTypes?: ReadonlySet<string>;
}

/**
 * @param objects stixId → 已載入的物件;值為 undefined 代表尚未展開
 */
export function buildGraph(
  objects: ReadonlyMap<string, StixObject | undefined>,
  options: BuildGraphOptions = {},
): StixGraph {
  const accumulator: Accumulator = { nodes: new Map(), edges: new Map() };

  for (const [stixId, object] of objects) {
    if (object !== undefined && stixTypeOf(stixId) === 'relationship') {
      if (addRelationship(accumulator, object)) continue;
    }
    touch(accumulator, stixId, object);
    if (object !== undefined) addEmbeddedRefs(accumulator, stixId, object);
  }

  const hidden = options.hiddenTypes ?? new Set<string>();
  const nodes = [...accumulator.nodes.values()].filter((node) => !hidden.has(node.stixType));
  const visible = new Set(nodes.map((node) => node.id));
  const edges = [...accumulator.edges.values()].filter(
    (edge) => visible.has(edge.source) && visible.has(edge.target),
  );
  return { nodes, edges };
}

/** 篩選選單的選項:圖上實際出現過的型別(含尚未載入的參照)。 */
export function stixTypesIn(objects: ReadonlyMap<string, StixObject | undefined>): string[] {
  const { nodes } = buildGraph(objects);
  return [...new Set(nodes.map((node) => node.stixType))].sort();
}
