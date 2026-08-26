import { AlertTriangle } from 'lucide-react';
import { ApiError } from '../../api/client';
import { Button } from '../ui/button';

export interface ErrorStateProps {
  error: unknown;
  /** §12.6:錯誤狀態必須提供重試 */
  onRetry?: () => void;
}

const MESSAGES: Record<string, string> = {
  NOT_FOUND: '找不到這筆資料,它可能已被移除或您沒有存取權。',
  VALIDATION_FAILED: '查詢條件無效,請調整後重試。',
  INVALID_CURSOR: '分頁游標已失效,請回到第一頁重新查詢。',
  RATE_LIMITED: '請求過於頻繁,已被限流。請稍候再試。',
  PAYLOAD_TOO_LARGE: '一次送出的資料量超過上限,請縮小批次。',
  NETWORK_ERROR: '無法連線到伺服器,請確認網路狀態後重試。',
  INTERNAL_ERROR: '伺服器發生內部錯誤,請稍後重試。',
};

const FALLBACK_MESSAGE = '發生未預期的錯誤,請稍後重試。';

/** §12.6:依錯誤 code 顯示對應文案 + 重試;traceId 供回報問題時引用。 */
export function ErrorState({ error, onRetry }: ErrorStateProps) {
  const apiError = error instanceof ApiError ? error : null;
  const message = (apiError && MESSAGES[apiError.code]) ?? FALLBACK_MESSAGE;

  return (
    <div
      role="alert"
      className="flex w-full flex-col items-center gap-3 rounded-lg border border-danger/40 bg-danger/5 px-6 py-14 text-center"
    >
      <AlertTriangle aria-hidden className="size-8 text-danger" />
      <p className="font-mono text-sm font-semibold uppercase tracking-wider text-danger">
        {apiError ? apiError.code : 'ERROR'}
      </p>
      <p className="max-w-md text-sm text-muted-foreground">{message}</p>
      {apiError?.traceId ? (
        <p className="font-mono text-xs text-muted-foreground">trace: {apiError.traceId}</p>
      ) : null}
      {onRetry ? (
        <Button variant="outline" size="sm" onClick={onRetry}>
          重試
        </Button>
      ) : null}
    </div>
  );
}
