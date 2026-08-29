import { ArrowLeft } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { ErrorState, ForbiddenState, LoadingState } from '../components/StateViews';
import { StixJsonViewer } from '../features/stix/components/StixJsonViewer';
import { ExternalReferenceList } from '../features/threat/components/ExternalReferenceList';
import { ThreatIndicatorList } from '../features/threat/components/ThreatIndicatorList';
import { ThreatSummaryCard } from '../features/threat/components/ThreatSummaryCard';
import { useThreatDetail, useThreatIndicators } from '../features/threat/hooks/useThreatDetail';
import { stixIdOf } from '../features/threat/types';

/**
 * 威脅詳情(§12.5 /threats/:id,匿名):摘要卡 + 關聯 IOC + 外部參照 + STIX 投影
 * (F4:跨 threat / stix / ioc 三個 feature 的組合在頁面層完成)。
 *
 * STIX 投影只有 MALWARE_FAMILY 與 ATTACK_PATTERN 有(07 §7.8.1);其餘型別不顯示該區塊,
 * 而不是顯示一個永遠 404 的面板。
 */
export default function ThreatDetailPage() {
  const { id = '' } = useParams<'id'>();
  const detail = useThreatDetail(id);
  const links = useThreatIndicators(id);

  let content: React.ReactNode;
  if (detail.isPending) {
    content = <LoadingState rows={6} label="載入威脅詳情" />;
  } else if (detail.isError) {
    content =
      detail.error instanceof ApiError && detail.error.status === 403 ? (
        <ForbiddenState reason="login" />
      ) : (
        <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />
      );
  } else {
    const stixId = stixIdOf(detail.data);
    content = (
      <div className="space-y-4">
        <ThreatSummaryCard threat={detail.data} />
        <ThreatIndicatorList
          links={links.data ?? []}
          totalCount={detail.data.indicatorCount ?? 0}
          isError={links.isError}
          onRetry={() => void links.refetch()}
        />
        <ExternalReferenceList references={detail.data.externalReferences ?? []} />
        {stixId ? <StixJsonViewer stixId={stixId} /> : null}
      </div>
    );
  }

  return (
    <section aria-labelledby="threat-detail-title" className="space-y-4">
      <header className="flex items-center gap-3">
        <Link
          to="/threats"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft aria-hidden className="size-4" />
          返回威脅清單
        </Link>
        <h1 id="threat-detail-title" className="font-mono text-xl font-bold tracking-tight">
          威脅詳情
        </h1>
      </header>
      {content}
    </section>
  );
}
