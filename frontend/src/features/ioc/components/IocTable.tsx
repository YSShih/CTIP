import { Badge } from '../../../components/ui/badge';
import { TlpBadge } from '../../../components/TlpBadge/TlpBadge';
import {
  VirtualTable,
  type VirtualTableColumn,
} from '../../../components/VirtualTable/VirtualTable';
import type { IocDto } from '../types';

export interface IocTableProps {
  items: IocDto[];
  onSelect: (ioc: IocDto) => void;
}

const SEVERITY_VARIANT: Record<string, 'muted' | 'ok' | 'warn' | 'danger'> = {
  INFO: 'muted',
  LOW: 'ok',
  MEDIUM: 'warn',
  HIGH: 'danger',
  CRITICAL: 'danger',
};

function formatInstant(value: string | undefined): string {
  return value ? value.replace('T', ' ').replace(/(\.\d+)?Z$/, 'Z') : '—';
}

const COLUMNS: VirtualTableColumn<IocDto>[] = [
  {
    key: 'value',
    header: 'IOC 值',
    width: 'minmax(0, 2.4fr)',
    cell: (ioc) => <span className="font-mono text-[13px]">{ioc.value}</span>,
  },
  {
    key: 'type',
    header: '型別',
    width: '7rem',
    cell: (ioc) => <Badge variant="muted">{ioc.type ?? '?'}</Badge>,
  },
  {
    key: 'tlp',
    header: 'TLP',
    width: '8.5rem',
    cell: (ioc) => <TlpBadge tlp={ioc.tlp} />,
  },
  {
    key: 'severity',
    header: '嚴重度',
    width: '6.5rem',
    cell: (ioc) => (
      <Badge variant={SEVERITY_VARIANT[ioc.severity ?? ''] ?? 'muted'}>{ioc.severity ?? '?'}</Badge>
    ),
  },
  {
    key: 'score',
    header: '分數',
    width: '4.5rem',
    cell: (ioc) => <span className="font-mono tabular-nums">{ioc.score ?? '—'}</span>,
  },
  {
    key: 'lastSeen',
    header: '最後觀測',
    width: '12rem',
    cell: (ioc) => (
      <span className="font-mono text-xs text-muted-foreground">{formatInstant(ioc.lastSeen)}</span>
    ),
  },
];

/** §12.6:大量 IOC 一律虛擬化;空資料由呼叫端 render EmptyState。 */
export function IocTable({ items, onSelect }: IocTableProps) {
  return (
    <VirtualTable
      rows={items}
      columns={COLUMNS}
      rowKey={(ioc) => ioc.id ?? ioc.value ?? ''}
      onRowClick={onSelect}
    />
  );
}
