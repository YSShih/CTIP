import { ChevronRight, ChevronsLeft } from 'lucide-react';
import { Button } from '../../../components/ui/button';

export interface CursorPagerProps {
  hasMore: boolean;
  /** 目前是否在第一頁(URL 無 cursor) */
  atFirstPage: boolean;
  isFetching: boolean;
  shownCount: number;
  onNext: () => void;
  onFirst: () => void;
}

/** cursor 分頁只能前進;「回到第一頁」以移除 cursor 實作(§9.3)。 */
export function CursorPager({
  hasMore,
  atFirstPage,
  isFetching,
  shownCount,
  onNext,
  onFirst,
}: CursorPagerProps) {
  return (
    <nav aria-label="分頁" className="flex items-center justify-between">
      <p className="font-mono text-xs text-muted-foreground">
        本頁 {shownCount} 筆{hasMore ? ' · 還有更多' : ' · 已到最後'}
      </p>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" onClick={onFirst} disabled={atFirstPage || isFetching}>
          <ChevronsLeft aria-hidden />
          回到第一頁
        </Button>
        <Button variant="outline" size="sm" onClick={onNext} disabled={!hasMore || isFetching}>
          下一頁
          <ChevronRight aria-hidden />
        </Button>
      </div>
    </nav>
  );
}
