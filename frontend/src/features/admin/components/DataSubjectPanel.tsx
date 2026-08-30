import { useState } from 'react';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import type { DataSubjectErasureDto, DataSubjectReportDto } from '../api/adminApi';

export interface DataSubjectPanelProps {
  report: DataSubjectReportDto | null;
  erasure: DataSubjectErasureDto | null;
  busy: boolean;
  onLookup: (userId: string) => void;
  onErase: (userId: string) => void;
}

/**
 * 資料主體查詢與刪除(13 §13.4)。刪除只抹除可識別欄位並刪掉 refresh token;
 * 稽核軌跡是 append-only 的,依 `AUDIT_RETENTION_DAYS` 到期——畫面必須說清楚這件事,
 * 否則操作者會以為「刪除」把一切都刪了。
 */
export function DataSubjectPanel({
  report,
  erasure,
  busy,
  onLookup,
  onErase,
}: DataSubjectPanelProps) {
  const [userId, setUserId] = useState('');

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-2">
        <div className="grow">
          <label className="mb-1 block text-xs text-muted-foreground" htmlFor="data-subject-id">
            使用者 ID
          </label>
          <Input
            id="data-subject-id"
            value={userId}
            placeholder="a2f1c0d4-9b8e-4a71-8c33-0e1d2f3a4b5c"
            onChange={(event) => setUserId(event.target.value)}
          />
        </div>
        <Button variant="outline" disabled={busy || !userId} onClick={() => onLookup(userId)}>
          查詢持有的個資
        </Button>
        <Button variant="destructive" disabled={busy || !userId} onClick={() => onErase(userId)}>
          執行刪除
        </Button>
      </div>

      {report ? (
        <dl className="grid grid-cols-2 gap-2 text-sm" data-testid="data-subject-report">
          <dt className="text-muted-foreground">Email</dt>
          <dd className="font-mono text-xs">{report.email}</dd>
          <dt className="text-muted-foreground">狀態</dt>
          <dd className="font-mono text-xs">{report.status}</dd>
          <dt className="text-muted-foreground">存活的 refresh token</dt>
          <dd className="font-mono text-xs">{report.activeRefreshTokens}</dd>
          <dt className="text-muted-foreground">稽核列數</dt>
          <dd className="font-mono text-xs">{report.auditEntries}</dd>
        </dl>
      ) : null}

      {erasure ? (
        <p className="text-sm" data-testid="data-subject-erasure">
          已刪除 {erasure.deletedRefreshTokens} 筆 refresh token 並抹除可識別欄位;
          {erasure.retainedAuditEntries} 筆稽核紀錄依保留政策留存(append-only,不得刪除)。
        </p>
      ) : null}
    </div>
  );
}
