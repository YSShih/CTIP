import type { SubscriptionUsageDto } from '../api/subscriptionApi';

export interface UsageListProps {
  usage: SubscriptionUsageDto;
}

type UsageItem = NonNullable<SubscriptionUsageDto['manualSubmissionsToday']>;

function describeLimit(item: UsageItem): string {
  if (item.limit === null || item.limit === undefined) return '無限制';
  if (item.limit === 0) return '停用';
  return `${String(item.used ?? 0)} / ${String(item.limit)}`;
}

/** 只列真的有計數來源的兩項(webhook 數量要到 Phase 20 才有資料表)。 */
export function UsageList({ usage }: UsageListProps) {
  const rows: ReadonlyArray<{ label: string; item: UsageItem | undefined; resetAt?: boolean }> = [
    { label: '今日手動提交', item: usage.manualSubmissionsToday, resetAt: true },
    { label: '有效 API key', item: usage.apiKeys },
  ];
  return (
    <dl className="space-y-3">
      {rows.map(({ label, item, resetAt }) =>
        item === undefined ? null : (
          <div key={label} className="flex items-baseline justify-between gap-4">
            <dt className="text-sm text-muted-foreground">{label}</dt>
            <dd className="text-right">
              <span className="font-mono text-sm">{describeLimit(item)}</span>
              {resetAt === true && item.resetAt ? (
                <span className="ml-2 text-xs text-muted-foreground">
                  重置於 {new Date(item.resetAt).toLocaleString()}
                </span>
              ) : null}
            </dd>
          </div>
        ),
      )}
    </dl>
  );
}
