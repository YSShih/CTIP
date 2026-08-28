import { Badge } from '../../../components/ui/badge';
import type { ImportJobDto } from '../api/iocWriteApi';

export interface ImportJobStatusProps {
  job: ImportJobDto;
}

/** 匯入 job 的狀態(§9.7:逐筆結果摘要;逐筆拒絕明細留在 ingestion_rejections)。 */
const STATUS_LABEL: Record<string, string> = {
  PENDING: '等待處理',
  RUNNING: '處理中',
  SUCCESS: '完成',
  PARTIAL: '部分完成',
  FAILURE: '失敗',
};

const BADGE_VARIANT: Record<string, 'ok' | 'warn' | 'danger' | 'muted'> = {
  PENDING: 'muted',
  RUNNING: 'muted',
  SUCCESS: 'ok',
  PARTIAL: 'warn',
  FAILURE: 'danger',
};

export function ImportJobStatus({ job }: ImportJobStatusProps) {
  const status = job.status ?? 'PENDING';
  return (
    <dl className="space-y-3" aria-label="匯入進度">
      <div className="flex items-center gap-2">
        <dt className="text-sm text-muted-foreground">狀態</dt>
        <dd>
          <Badge variant={BADGE_VARIANT[status] ?? 'muted'}>{STATUS_LABEL[status] ?? status}</Badge>
        </dd>
      </div>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <div>
          <dt className="text-xs text-muted-foreground">總筆數</dt>
          <dd className="font-mono text-sm">{job.totalRows ?? '—'}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">新建立</dt>
          <dd className="font-mono text-sm">{job.acceptedCount ?? 0}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">已合併</dt>
          <dd className="font-mono text-sm">{job.mergedCount ?? 0}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">已拒絕</dt>
          <dd className="font-mono text-sm">{job.rejectedCount ?? 0}</dd>
        </div>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Job ID</dt>
        <dd className="font-mono text-xs break-all">{job.importJobId}</dd>
      </div>
      {job.errorMessage ? (
        <div>
          <dt className="text-xs text-muted-foreground">錯誤</dt>
          <dd role="alert" className="text-sm text-destructive">
            {job.errorMessage}
          </dd>
        </div>
      ) : null}
    </dl>
  );
}
