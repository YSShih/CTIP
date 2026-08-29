import { TlpBadge } from '../../../components/TlpBadge/TlpBadge';
import { Badge } from '../../../components/ui/badge';
import {
  VirtualTable,
  type VirtualTableColumn,
} from '../../../components/VirtualTable/VirtualTable';
import type { ThreatDto } from '../types';

export interface ThreatTableProps {
  items: ThreatDto[];
  onSelect: (threat: ThreatDto) => void;
}

const SEVERITY_VARIANT: Record<string, 'muted' | 'ok' | 'warn' | 'danger'> = {
  INFO: 'muted',
  LOW: 'ok',
  MEDIUM: 'warn',
  HIGH: 'danger',
  CRITICAL: 'danger',
};

const STATUS_VARIANT: Record<string, 'muted' | 'ok' | 'warn'> = {
  ACTIVE: 'ok',
  DORMANT: 'warn',
  RETIRED: 'muted',
};

function formatInstant(value: string | undefined): string {
  return value ? value.replace('T', ' ').replace(/(\.\d+)?Z$/, 'Z') : '—';
}

const COLUMNS: VirtualTableColumn<ThreatDto>[] = [
  {
    key: 'name',
    header: '名稱',
    width: 'minmax(0, 2.2fr)',
    cell: (threat) => (
      <span className="truncate font-mono text-[13px] font-semibold">{threat.name}</span>
    ),
  },
  {
    key: 'type',
    header: '型別',
    width: '10rem',
    cell: (threat) => <Badge variant="muted">{threat.type ?? '?'}</Badge>,
  },
  {
    key: 'tlp',
    header: 'TLP',
    width: '8.5rem',
    cell: (threat) => <TlpBadge tlp={threat.tlp} />,
  },
  {
    key: 'severity',
    header: '嚴重度',
    width: '6.5rem',
    cell: (threat) => (
      <Badge variant={SEVERITY_VARIANT[threat.severity ?? ''] ?? 'muted'}>
        {threat.severity ?? '?'}
      </Badge>
    ),
  },
  {
    key: 'status',
    header: '狀態',
    width: '6.5rem',
    cell: (threat) => (
      <Badge variant={STATUS_VARIANT[threat.status ?? ''] ?? 'muted'}>{threat.status ?? '?'}</Badge>
    ),
  },
  {
    key: 'indicatorCount',
    header: '關聯 IOC',
    width: '6rem',
    cell: (threat) => <span className="font-mono tabular-nums">{threat.indicatorCount ?? 0}</span>,
  },
  {
    key: 'lastSeen',
    header: '最後觀測',
    width: '12rem',
    cell: (threat) => (
      <span className="font-mono text-xs text-muted-foreground">
        {formatInstant(threat.lastSeen)}
      </span>
    ),
  },
];

/** §12.6:清單一律虛擬化;空資料由呼叫端 render EmptyState。 */
export function ThreatTable({ items, onSelect }: ThreatTableProps) {
  return (
    <VirtualTable
      rows={items}
      columns={COLUMNS}
      rowKey={(threat) => threat.id ?? threat.name ?? ''}
      onRowClick={onSelect}
    />
  );
}
