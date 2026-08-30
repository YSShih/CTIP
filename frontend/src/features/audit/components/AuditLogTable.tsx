import { Badge } from '../../../components/ui/badge';
import type { AuditLogDto } from '../types';

export interface AuditLogTableProps {
  entries: AuditLogDto[];
}

const RESULT_VARIANT: Record<string, 'ok' | 'warn' | 'danger'> = {
  SUCCESS: 'ok',
  FAILURE: 'danger',
  DENIED: 'warn',
};

function formatInstant(value: string | undefined | null): string {
  return value ? new Date(value).toLocaleString() : '—';
}

/** 行為者以類型 + 識別碼呈現;匿名沒有識別碼(表 27 的 actor_id 可為 null)。 */
function formatActor(entry: AuditLogDto): string {
  return entry.actorId
    ? `${entry.actorType} ${entry.actorId.slice(0, 8)}`
    : (entry.actorType ?? '—');
}

export function AuditLogTable({ entries }: AuditLogTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">本租戶的稽核軌跡</caption>
        <thead className="text-xs uppercase tracking-[0.12em] text-muted-foreground">
          <tr>
            <th scope="col" className="py-2 pr-4">
              時間
            </th>
            <th scope="col" className="py-2 pr-4">
              行為
            </th>
            <th scope="col" className="py-2 pr-4">
              行為者
            </th>
            <th scope="col" className="py-2 pr-4">
              對象
            </th>
            <th scope="col" className="py-2 pr-4">
              來源 IP
            </th>
            <th scope="col" className="py-2">
              結果
            </th>
          </tr>
        </thead>
        <tbody className="divide-y">
          {entries.map((entry) => (
            <tr key={entry.id} data-testid="audit-row">
              <td className="py-2 pr-4 font-mono text-xs">{formatInstant(entry.occurredAt)}</td>
              <td className="py-2 pr-4 font-mono text-xs">{entry.action}</td>
              <td className="py-2 pr-4 font-mono text-xs">{formatActor(entry)}</td>
              <td className="py-2 pr-4 text-xs">{entry.resourceType ?? '—'}</td>
              <td className="py-2 pr-4 font-mono text-xs">{entry.ip ?? '—'}</td>
              <td className="py-2">
                <Badge variant={RESULT_VARIANT[entry.result ?? ''] ?? 'warn'}>{entry.result}</Badge>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
