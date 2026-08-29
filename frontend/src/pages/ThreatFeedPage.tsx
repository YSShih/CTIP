import { useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from '../components/StateViews';
import { CursorPager } from '../features/ioc/components/CursorPager';
import { ThreatFilterBar } from '../features/threat/components/ThreatFilterBar';
import { ThreatTable } from '../features/threat/components/ThreatTable';
import { useThreatFeed } from '../features/threat/hooks/useThreatFeed';
import type { ThreatDto } from '../features/threat/types';

/**
 * §12.5 /threats(匿名可存取):威脅清單 + 篩選 + cursor 分頁;條件存 URL。
 *
 * 可見範圍與 IOC 同一套規則(07 §7.7):匿名只看得到公開的 TLP:CLEAR,
 * 登入後另加自家租戶的全部與公開的 GREEN。
 */
export default function ThreatFeedPage() {
  const navigate = useNavigate();
  const { filters, cursor, query, applyFilters, goToCursor, backToFirstPage } = useThreatFeed();

  const items = (query.data?.items ?? []) as ThreatDto[];

  let content: React.ReactNode;
  if (query.isPending) {
    content = <LoadingState rows={8} label="載入威脅" />;
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
        title="查無符合的威脅"
        description="沒有符合目前條件的威脅。放寬條件後重試;已退役(RETIRED)的威脅預設不列出。"
      />
    );
  } else {
    content = (
      <div className="space-y-3">
        <ThreatTable items={items} onSelect={(threat) => void navigate(`/threats/${threat.id}`)} />
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
    <section aria-labelledby="threat-feed-title" className="space-y-4">
      <header>
        <h1 id="threat-feed-title" className="font-mono text-xl font-bold tracking-tight">
          威脅情報
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          campaign、malware family、threat actor 等高階威脅實體,以及它們關聯的 IOC。
          篩選條件保存在網址列,可直接分享。
        </p>
      </header>
      <ThreatFilterBar applied={filters} onApply={applyFilters} />
      {content}
    </section>
  );
}
