import { Badge } from '../../../components/ui/badge';
import { Button } from '../../../components/ui/button';
import type { WebhookDto } from '../api/notificationApi';

export interface WebhookTableProps {
  webhooks: WebhookDto[];
  deletingId: string | null;
  onDelete: (id: string) => void;
}

const STATUS_TONE: Record<string, 'ok' | 'warn' | 'danger'> = {
  ACTIVE: 'ok',
  SUSPENDED: 'warn',
  DISABLED: 'danger',
};

function formatInstant(value: string | undefined | null): string {
  return value ? new Date(value).toLocaleString() : '—';
}

export function WebhookTable({ webhooks, deletingId, onDelete }: WebhookTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">本租戶的 webhook 清單</caption>
        <thead className="text-xs uppercase tracking-[0.12em] text-muted-foreground">
          <tr>
            <th scope="col" className="py-2 pr-4">
              名稱
            </th>
            <th scope="col" className="py-2 pr-4">
              目標
            </th>
            <th scope="col" className="py-2 pr-4">
              訂閱事件
            </th>
            <th scope="col" className="py-2 pr-4">
              狀態
            </th>
            <th scope="col" className="py-2 pr-4">
              最近成功
            </th>
            <th scope="col" className="py-2">
              <span className="sr-only">操作</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y">
          {webhooks.map((webhook) => (
            <tr key={webhook.id} data-testid="webhook-row">
              <td className="py-2 pr-4">{webhook.name}</td>
              <td className="py-2 pr-4 font-mono text-xs break-all">{webhook.targetUrl}</td>
              <td className="py-2 pr-4">
                <span className="flex flex-wrap gap-1">
                  {(webhook.eventTypes ?? []).map((eventType) => (
                    <Badge key={eventType} variant="outline">
                      {eventType}
                    </Badge>
                  ))}
                </span>
              </td>
              <td className="py-2 pr-4">
                <Badge variant={STATUS_TONE[webhook.status ?? 'ACTIVE'] ?? 'muted'}>
                  {webhook.status}
                </Badge>
                {webhook.status === 'DISABLED' ? (
                  <p className="mt-1 text-xs text-muted-foreground">
                    連續 {webhook.consecutiveFailures} 次送達失敗後由系統停用,需重新建立。
                  </p>
                ) : null}
              </td>
              <td className="py-2 pr-4 font-mono text-xs">
                {formatInstant(webhook.lastSuccessAt)}
              </td>
              <td className="py-2">
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={deletingId === webhook.id}
                  onClick={() => onDelete(webhook.id!)}
                >
                  刪除
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
