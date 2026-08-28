import { EmptyState, ErrorState, LoadingState } from '../components/StateViews';
import { Badge } from '../components/ui/badge';
import { Card } from '../components/ui/card';
import { PlanQuotaTable } from '../features/subscription/components/PlanQuotaTable';
import { UsageList } from '../features/subscription/components/UsageList';
import {
  useSubscription,
  useSubscriptionUsage,
} from '../features/subscription/hooks/useSubscription';

/**
 * §12.5 /settings/subscription(需登入 + subscription:read)。
 * 沒有訂閱列的租戶生效方案是 FREE(不變量 B4),此時不顯示訂閱期間——
 * 那是「沒有訂閱」與「有一份訂閱」的差別,不該用假的日期抹平。
 */
export default function SubscriptionPage() {
  const subscription = useSubscription();
  const usage = useSubscriptionUsage();

  let plan: React.ReactNode;
  if (subscription.isPending) {
    plan = <LoadingState rows={4} label="載入方案" />;
  } else if (subscription.isError) {
    plan = <ErrorState error={subscription.error} onRetry={() => void subscription.refetch()} />;
  } else {
    const data = subscription.data;
    plan = (
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-lg font-semibold">{data.planName}</span>
          <Badge variant="outline">{data.planCode}</Badge>
          {data.status ? <Badge variant="muted">{data.status}</Badge> : null}
        </div>
        {data.status ? (
          <p className="text-xs text-muted-foreground">
            計費期間{' '}
            {data.currentPeriodStart ? new Date(data.currentPeriodStart).toLocaleDateString() : '—'}
            {' – '}
            {data.currentPeriodEnd
              ? new Date(data.currentPeriodEnd).toLocaleDateString()
              : '無期限'}
            ,供應商 {data.provider}
          </p>
        ) : (
          <p className="text-xs text-muted-foreground">
            尚未指派訂閱,目前適用 FREE 方案的配額。變更方案請聯絡平台管理員。
          </p>
        )}
        {data.quotas ? (
          <PlanQuotaTable quotas={data.quotas} />
        ) : (
          <EmptyState title="沒有配額資料" description="方案定義尚未載入。" />
        )}
      </div>
    );
  }

  let consumption: React.ReactNode;
  if (usage.isPending) {
    consumption = <LoadingState rows={2} label="載入用量" />;
  } else if (usage.isError) {
    consumption = <ErrorState error={usage.error} onRetry={() => void usage.refetch()} />;
  } else {
    consumption = <UsageList usage={usage.data} />;
  }

  return (
    <section aria-labelledby="subscription-title" className="space-y-4">
      <h1 id="subscription-title" className="font-mono text-xl font-bold tracking-tight">
        方案與用量
      </h1>
      <Card className="p-6">{plan}</Card>
      <Card className="p-6">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-[0.12em] text-muted-foreground">
          目前用量
        </h2>
        {consumption}
      </Card>
    </section>
  );
}
