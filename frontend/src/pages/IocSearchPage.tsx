import { useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from '../components/StateViews';
import { CursorPager } from '../features/ioc/components/CursorPager';
import { IocFilterBar } from '../features/ioc/components/IocFilterBar';
import { IocTable } from '../features/ioc/components/IocTable';
import { useIocSearch } from '../features/ioc/hooks/useIocSearch';
import type { IocDto } from '../features/ioc/types';

/** IOC 檢索(§12.5 /iocs,匿名):FilterBar + 虛擬化表格 + cursor 分頁;條件存 URL。 */
export default function IocSearchPage() {
  const navigate = useNavigate();
  const { filters, cursor, query, applyFilters, goToCursor, backToFirstPage } = useIocSearch();

  const items = (query.data?.items ?? []) as IocDto[];

  let content: React.ReactNode;
  if (query.isPending) {
    content = <LoadingState rows={8} label="載入 IOC" />;
  } else if (query.isError) {
    content =
      query.error instanceof ApiError && query.error.status === 403 ? (
        <ForbiddenState reason="login" />
      ) : (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      );
  } else if (items.length === 0) {
    content = (
      <EmptyState
        title="查無符合的 IOC"
        description="沒有符合目前條件的公開情資。放寬關鍵字或清除部分篩選後重試。"
      />
    );
  } else {
    content = (
      <div className="space-y-3">
        <IocTable items={items} onSelect={(ioc) => void navigate(`/iocs/${ioc.id}`)} />
        <CursorPager
          hasMore={query.data?.hasMore ?? false}
          atFirstPage={cursor === undefined}
          isFetching={query.isFetching}
          shownCount={items.length}
          onNext={() => {
            if (query.data?.nextCursor) {
              goToCursor(query.data.nextCursor);
            }
          }}
          onFirst={backToFirstPage}
        />
      </div>
    );
  }

  return (
    <section aria-labelledby="ioc-search-title" className="space-y-4">
      <header>
        <h1 id="ioc-search-title" className="font-mono text-xl font-bold tracking-tight">
          IOC 檢索
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          以值、型別、嚴重度、TLP 等條件檢索公開情資;搜尋條件保存在網址列,可直接分享。
        </p>
      </header>
      <IocFilterBar applied={filters} onApply={applyFilters} />
      {content}
    </section>
  );
}
