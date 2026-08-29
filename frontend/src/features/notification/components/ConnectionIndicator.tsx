import type { StreamStatus } from '../api/notificationStream';

export interface ConnectionIndicatorProps {
  status: StreamStatus;
}

const LABELS: Record<StreamStatus, string> = {
  connecting: '連線中',
  open: '即時連線中',
  reconnecting: '連線中斷,重試中',
  offline: '未連線',
};

const DOT: Record<StreamStatus, string> = {
  connecting: 'bg-amber-500 animate-pulse',
  open: 'bg-emerald-500',
  reconnecting: 'bg-amber-500 animate-pulse',
  offline: 'bg-muted-foreground',
};

/**
 * WebSocket 連線狀態指示(phase-20 交付物)。
 *
 * <p>{@code aria-live="polite"}:連線狀態會自己改變,讀屏使用者需要知道「現在收不到即時通知」
 * ——這正是頁面在說謊與誠實之間的差別。
 */
export function ConnectionIndicator({ status }: ConnectionIndicatorProps) {
  return (
    <span
      className="inline-flex items-center gap-2 text-xs text-muted-foreground"
      role="status"
      aria-live="polite"
      data-testid="stream-status"
      data-status={status}
    >
      <span aria-hidden="true" className={`inline-block size-2 rounded-full ${DOT[status]}`} />
      {LABELS[status]}
    </span>
  );
}
