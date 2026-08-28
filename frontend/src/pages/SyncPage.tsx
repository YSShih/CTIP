import { EmptyState, ErrorState, LoadingState } from '../components/StateViews';
import { Badge } from '../components/ui/badge';
import { Card } from '../components/ui/card';
import { BloomLayerTable } from '../features/sync/components/BloomLayerTable';
import { BloomSemanticsNotice } from '../features/sync/components/BloomSemanticsNotice';
import { useSyncManifest } from '../features/sync/hooks/useSyncManifest';

/**
 * §12.5 /sync(匿名可存取):Bloom 同步的說明頁。
 *
 * 三件事必須在這一頁講清楚(12 §12.6 第 3 條、11 §11.1、§11.7):
 * 命中不代表惡意、未命中不代表安全、撤銷與過期只有 full snapshot 會反映。
 * 頁面本身<strong>不</strong>下載 Bloom——那是 client SDK 的事(§11.6),
 * 這裡只呈現 manifest 讓使用者確認自己的 client 應該同步到哪個版本。
 */
export default function SyncPage() {
  const manifest = useSyncManifest();

  if (manifest.isPending) {
    return <LoadingState rows={6} label="載入同步 manifest" />;
  }
  if (manifest.isError) {
    return <ErrorState error={manifest.error} onRetry={() => void manifest.refetch()} />;
  }

  const data = manifest.data;
  const notCovered = data.notCovered ?? [];

  return (
    <section aria-labelledby="sync-title" className="space-y-4">
      <div className="space-y-1">
        <h1 id="sync-title" className="font-mono text-xl font-bold tracking-tight">
          Bloom 同步
        </h1>
        <p className="text-sm text-muted-foreground">
          Browser Extension 與 App 可下載 Bloom filter,在本機以極低成本判斷某個值是否
          <span className="font-semibold">可能</span>存在於情資集合中。契約與端點見{' '}
          <code className="font-mono text-xs">docs/api/sync-client-contract.md</code>。
        </p>
      </div>

      <BloomSemanticsNotice notCovered={notCovered} />

      <Card className="p-6">
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <h2 className="text-sm font-semibold uppercase tracking-[0.12em] text-muted-foreground">
            公開層(public)
          </h2>
          <Badge variant="outline">GET /api/v1/sync/bloom?scope=PUBLIC</Badge>
        </div>
        {data.public ? (
          <BloomLayerTable layer={data.public} />
        ) : (
          <EmptyState
            title="目前沒有可同步的公開 Bloom"
            description="尚未產生第一份 snapshot,或目前方案不含 public Bloom 的下載權限。"
          />
        )}
      </Card>

      <Card className="p-6">
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <h2 className="text-sm font-semibold uppercase tracking-[0.12em] text-muted-foreground">
            租戶層(tenant)
          </h2>
          <Badge variant="outline">GET /api/v1/sync/bloom?scope=TENANT</Badge>
        </div>
        {data.tenant ? (
          <BloomLayerTable layer={data.tenant} />
        ) : (
          <EmptyState
            title="沒有租戶層 Bloom"
            description="租戶層只含該租戶自己的私有情資(AMBER / AMBER_STRICT),需登入且方案含 tenant Bloom 容量。"
          />
        )}
      </Card>

      <Card className="p-6">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-[0.12em] text-muted-foreground">
          同步流程
        </h2>
        <ol className="list-decimal space-y-2 pl-5 text-sm text-muted-foreground">
          <li>
            取 <code className="font-mono text-xs">GET /api/v1/sync/manifest</code>,比對
            fingerprintAlgorithm / hashFunctionCount / bitSize;任一不同,本地 Bloom 立即作廢。
          </li>
          <li>
            datasetVersion 相同時取{' '}
            <code className="font-mono text-xs">GET /api/v1/sync/delta?base=&lt;本地版本&gt;</code>
            ,套用後必須驗證 resultingChecksum,不符即丟棄。
          </li>
          <li>
            收到 <code className="font-mono text-xs">409 SNAPSHOT_REQUIRED</code>(delta 鏈超過{' '}
            {data.maxDeltaChain ?? 24} 段或累計過大)時,改下載 full snapshot。
          </li>
          <li>
            下載 full 後以回應標頭 <code className="font-mono text-xs">X-Bloom-Checksum</code> 驗證,
            並把本地版本記成
            <code className="mx-1 font-mono text-xs">X-Bloom-Dataset-Version</code>/
            <code className="mx-1 font-mono text-xs">X-Bloom-Version</code>。
          </li>
          <li>
            同步頻率受方案的 min_sync_interval_seconds 限制,過於頻繁回{' '}
            <code className="font-mono text-xs">429</code>(依 Retry-After 重試)。
          </li>
        </ol>
      </Card>
    </section>
  );
}
