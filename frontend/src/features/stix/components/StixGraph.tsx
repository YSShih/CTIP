import cytoscape from 'cytoscape';
import { useEffect, useMemo, useRef } from 'react';
import type { StixGraph as StixGraphData } from '../graph';

export interface StixGraphProps {
  graph: StixGraphData;
  selectedId: string | null;
  onSelect: (stixId: string) => void;
}

/** canvas 讀不到 Tailwind 的 utility class,顏色一律從 CSS 變數取(深色模式因此跟著切)。 */
function readColors(element: HTMLElement): Record<string, string> {
  const style = getComputedStyle(element);
  const read = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
  return {
    node: read('--muted', '#e6e9ee'),
    nodeText: read('--foreground', '#22262d'),
    border: read('--border', '#c9ced6'),
    primary: read('--primary', '#2f7f96'),
    muted: read('--muted-foreground', '#6b7280'),
  };
}

function stylesheet(colors: Record<string, string>): cytoscape.StylesheetJson {
  return [
    {
      selector: 'node',
      style: {
        'background-color': colors.node,
        'border-color': colors.border,
        'border-width': 1,
        label: 'data(label)',
        color: colors.nodeText,
        'font-size': 10,
        'text-valign': 'bottom',
        'text-margin-y': 4,
        'text-wrap': 'ellipsis',
        'text-max-width': '140px',
        width: 26,
        height: 26,
      },
    },
    // 尚未展開的節點畫成虛線空心圓——「這裡還有東西,點我」必須看得出來
    {
      selector: 'node[?pending]',
      style: { 'background-opacity': 0.25, 'border-style': 'dashed' },
    },
    {
      selector: 'node.selected',
      style: { 'border-color': colors.primary, 'border-width': 3 },
    },
    {
      selector: 'edge',
      style: {
        width: 1,
        'line-color': colors.border,
        'target-arrow-color': colors.border,
        'target-arrow-shape': 'triangle',
        'curve-style': 'bezier',
        label: 'data(label)',
        color: colors.muted,
        'font-size': 9,
        'text-rotation': 'autorotate',
      },
    },
  ];
}

/**
 * Cytoscape.js 圖形檢視(§12.6 STIX Viewer)。
 * 節點點擊往上拋:載入與選取狀態屬於頁面,元件只負責畫與收事件。
 */
export function StixGraph({ graph, selectedId, onSelect }: StixGraphProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<cytoscape.Core | null>(null);
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;

  // 元素內容的簽章:react 每次 render 都會給新的陣列,靠識別碼比對才不會每次重排版
  const signature = useMemo(
    () =>
      [
        ...graph.nodes.map((node) => `${node.id}:${node.loaded ? 1 : 0}:${node.label}`),
        ...graph.edges.map((edge) => edge.id),
      ].join('|'),
    [graph],
  );

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const cy = cytoscape({ container, style: stylesheet(readColors(container)) });
    cy.on('tap', 'node', (event) => onSelectRef.current(event.target.id() as string));
    cyRef.current = cy;
    return () => {
      cy.destroy();
      cyRef.current = null;
    };
  }, []);

  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) return;
    cy.elements().remove();
    cy.add([
      ...graph.nodes.map((node) => ({
        group: 'nodes' as const,
        data: {
          id: node.id,
          label: `${node.stixType}\n${node.label}`,
          pending: node.loaded ? undefined : 1,
        },
      })),
      ...graph.edges.map((edge) => ({
        group: 'edges' as const,
        data: { id: edge.id, source: edge.source, target: edge.target, label: edge.label },
      })),
    ]);
    cy.layout({ name: 'breadthfirst', directed: true, padding: 24, spacingFactor: 1.1 }).run();
    // 依 signature 而非 graph:signature 就是 graph 的內容雜湊,同內容不重排版
  }, [signature]);

  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) return;
    cy.nodes().removeClass('selected');
    if (selectedId) cy.getElementById(selectedId).addClass('selected');
  }, [selectedId, signature]);

  return (
    <div
      ref={containerRef}
      data-testid="stix-graph-canvas"
      role="img"
      aria-label={`STIX 關聯圖,${graph.nodes.length} 個物件、${graph.edges.length} 條關聯`}
      className="h-[420px] w-full rounded-md border bg-surface"
    />
  );
}
