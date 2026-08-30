import { useState } from 'react';
import { EmptyState, ErrorState, LoadingState } from '../components/StateViews';
import { Card } from '../components/ui/card';
import { Select } from '../components/ui/select';
import { AuditLogTable } from '../features/audit/components/AuditLogTable';
import { useAuditLogs } from '../features/audit/hooks/useAuditLogs';
import { AUDIT_ACTIONS } from '../features/audit/types';

/**
 * §12.5 /audit(需登入 + `audit:read`)。
 *
 * 稽核軌跡是 append-only 的(13 §13.5 規則 1),因此這一頁只讀:沒有刪除、沒有編輯。
 * 範圍固定為呼叫者自己的租戶,後端不接受任何指定別人租戶的參數。
 */
export default function AuditLogPage() {
  const [action, setAction] = useState('');
  const logs = useAuditLogs(action ? { action } : {});

  let list: React.ReactNode;
  if (logs.isPending) {
    list = <LoadingState rows={5} label="載入稽核軌跡" />;
  } else if (logs.isError) {
    list = <ErrorState error={logs.error} onRetry={() => void logs.refetch()} />;
  } else if (logs.data.items.length === 0) {
    list = (
      <EmptyState
        title="這個範圍內沒有稽核紀錄"
        description="讀取類的操作依取樣率記錄(預設 1%),寫入類則全部記錄。"
      />
    );
  } else {
    list = <AuditLogTable entries={logs.data.items} />;
  }

  return (
    <section aria-labelledby="audit-title" className="space-y-4">
      <h1 id="audit-title" className="font-mono text-xl font-bold tracking-tight">
        稽核軌跡
      </h1>

      <Card className="p-6">
        <div className="mb-4 flex items-end gap-2">
          <div>
            <label className="mb-1 block text-xs text-muted-foreground" htmlFor="audit-action">
              行為
            </label>
            <Select
              id="audit-action"
              value={action}
              onChange={(event) => setAction(event.target.value)}
            >
              <option value="">全部</option>
              {AUDIT_ACTIONS.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </Select>
          </div>
        </div>
        {list}
      </Card>
    </section>
  );
}
