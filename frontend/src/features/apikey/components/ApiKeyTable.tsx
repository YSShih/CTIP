import type { ApiKeyDto } from '../api/apiKeyApi';
import { Badge } from '../../../components/ui/badge';
import { Button } from '../../../components/ui/button';

export interface ApiKeyTableProps {
  keys: ApiKeyDto[];
  revokingId: string | null;
  onRevoke: (id: string) => void;
}

function formatInstant(value: string | undefined | null): string {
  return value ? new Date(value).toLocaleString() : '—';
}

export function ApiKeyTable({ keys, revokingId, onRevoke }: ApiKeyTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">本租戶的 API key 清單</caption>
        <thead className="text-xs uppercase tracking-[0.12em] text-muted-foreground">
          <tr>
            <th scope="col" className="py-2 pr-4">
              名稱
            </th>
            <th scope="col" className="py-2 pr-4">
              前綴
            </th>
            <th scope="col" className="py-2 pr-4">
              權限
            </th>
            <th scope="col" className="py-2 pr-4">
              最後使用
            </th>
            <th scope="col" className="py-2 pr-4">
              狀態
            </th>
            <th scope="col" className="py-2">
              操作
            </th>
          </tr>
        </thead>
        <tbody>
          {keys.map((key) => {
            const revoked = Boolean(key.revokedAt);
            return (
              <tr key={key.id} className="border-t">
                <td className="py-2 pr-4">{key.name}</td>
                <td className="py-2 pr-4 font-mono text-xs">{key.keyPrefix}…</td>
                <td className="py-2 pr-4 font-mono text-xs">{(key.scopes ?? []).join(' ')}</td>
                <td className="py-2 pr-4 text-xs">{formatInstant(key.lastUsedAt)}</td>
                <td className="py-2 pr-4">
                  <Badge variant={revoked ? 'outline' : 'default'}>
                    {revoked ? '已撤銷' : '啟用中'}
                  </Badge>
                </td>
                <td className="py-2">
                  {revoked ? null : (
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={revokingId === key.id}
                      onClick={() => onRevoke(key.id ?? '')}
                    >
                      撤銷
                    </Button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
