import { useVirtualizer } from '@tanstack/react-virtual';
import { useRef, type ReactNode } from 'react';
import { cn } from '../../utils/cn';

export interface VirtualTableColumn<T> {
  key: string;
  header: ReactNode;
  /** CSS 寬度(如 '20%'、'12rem');未給則平均分配 */
  width?: string;
  cell: (row: T) => ReactNode;
}

export interface VirtualTableProps<T> {
  rows: T[];
  columns: VirtualTableColumn<T>[];
  rowKey: (row: T) => string;
  /** 固定行高(px),虛擬化的 estimateSize 常數 */
  rowHeight?: number;
  overscan?: number;
  /** 捲動容器高度 */
  height?: number | string;
  onRowClick?: (row: T) => void;
}

/**
 * §12.6:大量 IOC 必須虛擬化(TanStack Virtual)。
 * 空資料不是本元件的責任:rows 為空時僅渲染表頭,呼叫端應改 render EmptyState。
 */
export function VirtualTable<T>({
  rows,
  columns,
  rowKey,
  rowHeight = 44,
  overscan = 10,
  height = '60vh',
  onRowClick,
}: VirtualTableProps<T>) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => rowHeight,
    overscan,
  });

  const gridTemplate = columns.map((column) => column.width ?? `minmax(0, 1fr)`).join(' ');

  return (
    <div className="w-full overflow-hidden rounded-lg border bg-surface" role="table">
      <div
        role="row"
        className="grid items-center border-b bg-muted/60 px-3"
        style={{ gridTemplateColumns: gridTemplate, height: rowHeight - 6 }}
      >
        {columns.map((column) => (
          <div
            key={column.key}
            role="columnheader"
            className="truncate px-2 font-mono text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground"
          >
            {column.header}
          </div>
        ))}
      </div>
      <div ref={scrollRef} className="overflow-auto" style={{ height }}>
        <div
          className="relative w-full"
          style={{ height: virtualizer.getTotalSize() }}
          aria-rowcount={rows.length}
        >
          {virtualizer.getVirtualItems().map((item) => {
            const row = rows[item.index];
            return (
              <div
                key={rowKey(row)}
                role="row"
                data-testid="virtual-row"
                className={cn(
                  'absolute left-0 grid w-full items-center border-b border-border/60 px-3',
                  'transition-colors',
                  onRowClick &&
                    'cursor-pointer hover:bg-accent/60 hover:shadow-[inset_2px_0_0_var(--primary)]',
                )}
                style={{
                  gridTemplateColumns: gridTemplate,
                  height: item.size,
                  transform: `translateY(${item.start}px)`,
                }}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
              >
                {columns.map((column) => (
                  <div key={column.key} role="cell" className="truncate px-2 text-sm">
                    {column.cell(row)}
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
