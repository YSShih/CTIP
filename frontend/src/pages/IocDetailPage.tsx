import { ArrowLeft } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { ErrorState, ForbiddenState, LoadingState } from '../components/StateViews';
import { IocSummaryCard } from '../features/ioc/components/IocSummaryCard';
import { SourceAttributionList } from '../features/ioc/components/SourceAttributionList';
import { useIocDetail, useIocSources } from '../features/ioc/hooks/useIocDetail';
import { StixJsonViewer } from '../features/stix/components/StixJsonViewer';

/** IOC 詳情(§12.5 /iocs/:id,匿名):摘要卡 + 來源歸屬 + STIX 投影(F4:跨 ioc/stix 兩個 feature)。 */
export default function IocDetailPage() {
  const { id = '' } = useParams<'id'>();
  const detail = useIocDetail(id);
  const sources = useIocSources(id);

  let content: React.ReactNode;
  if (detail.isPending) {
    content = <LoadingState rows={6} label="載入 IOC 詳情" />;
  } else if (detail.isError) {
    content =
      detail.error instanceof ApiError && detail.error.status === 403 ? (
        <ForbiddenState reason="login" />
      ) : (
        <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />
      );
  } else {
    content = (
      <div className="space-y-4">
        <IocSummaryCard ioc={detail.data} />
        <SourceAttributionList
          attribution={detail.data.attribution ?? []}
          sources={sources.data ?? []}
        />
        <StixJsonViewer stixId={`indicator--${id}`} />
      </div>
    );
  }

  return (
    <section aria-labelledby="ioc-detail-title" className="space-y-4">
      <header className="flex items-center gap-3">
        <Link
          to="/iocs"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft aria-hidden className="size-4" />
          返回檢索
        </Link>
        <h1 id="ioc-detail-title" className="font-mono text-xl font-bold tracking-tight">
          IOC 詳情
        </h1>
      </header>
      {content}
    </section>
  );
}
