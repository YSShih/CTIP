import { Skeleton } from '../ui/skeleton';

export interface LoadingStateProps {
  /** skeleton 列數,依呼叫端版面調整 */
  rows?: number;
  label?: string;
}

/** §12.6:loading 一律以 skeleton 呈現,統一元件、不得每頁自寫。 */
export function LoadingState({ rows = 4, label = '載入中' }: LoadingStateProps) {
  return (
    <div role="status" aria-label={label} className="w-full space-y-3 py-4">
      <span className="sr-only">{label}</span>
      <Skeleton className="h-4 w-1/3" />
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className="h-9 w-full" />
      ))}
    </div>
  );
}
