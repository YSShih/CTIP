import { useParams } from 'react-router';

/** IOC 詳情頁(摘要卡 + 來源歸屬 + STIX 檢視於 Phase 12 接上)。 */
export default function IocDetailPage() {
  const { id } = useParams<'id'>();
  return (
    <section aria-labelledby="ioc-detail-title" className="space-y-4">
      <h1 id="ioc-detail-title" className="font-mono text-xl font-bold tracking-tight">
        IOC 詳情
      </h1>
      <p className="font-mono text-sm text-muted-foreground">id: {id}</p>
    </section>
  );
}
