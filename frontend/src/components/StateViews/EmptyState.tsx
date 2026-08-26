import { Inbox } from 'lucide-react';
import type { ReactNode } from 'react';

export interface EmptyStateProps {
  title?: string;
  /** §12.6:空狀態必須含說明文字與行動建議 */
  description: string;
  action?: ReactNode;
}

export function EmptyState({ title = '沒有資料', description, action }: EmptyStateProps) {
  return (
    <div className="flex w-full flex-col items-center gap-3 rounded-lg border border-dashed px-6 py-14 text-center">
      <Inbox aria-hidden className="size-8 text-muted-foreground" />
      <p className="font-mono text-sm font-semibold uppercase tracking-wider">{title}</p>
      <p className="max-w-md text-sm text-muted-foreground">{description}</p>
      {action}
    </div>
  );
}
