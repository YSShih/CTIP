import { Activity, Database, Layers, ShieldCheck } from 'lucide-react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ApiError } from '../api/client';
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from '../components/StateViews';
import { Badge } from '../components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { useSourceStats, useStatsSummary } from '../hooks/useStats';

function StatCard({
  icon: Icon,
  label,
  value,
  hint,
}: {
  icon: typeof Activity;
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>{label}</CardTitle>
        <Icon aria-hidden className="size-4 text-primary" />
      </CardHeader>
      <CardContent>
        <p className="font-mono text-3xl font-bold tabular-nums tracking-tight">{value}</p>
        {hint ? <p className="mt-1 text-xs text-muted-foreground">{hint}</p> : null}
      </CardContent>
    </Card>
  );
}

/** 儀表板(§12.5 /,匿名公開統計):統計卡 + Recharts 近 7 日趨勢 + 型別分布 + 來源健康。 */
export default function DashboardPage() {
  const summary = useStatsSummary();
  const sources = useSourceStats();

  let content: React.ReactNode;
  if (summary.isPending || sources.isPending) {
    content = <LoadingState rows={6} label="載入統計" />;
  } else if (summary.isError || sources.isError) {
    const error = summary.error ?? sources.error;
    content =
      error instanceof ApiError && error.status === 403 ? (
        <ForbiddenState reason="login" />
      ) : (
        <ErrorState
          error={error}
          onRetry={() => {
            void summary.refetch();
            void sources.refetch();
          }}
        />
      );
  } else if ((summary.data.totalActive ?? 0) === 0) {
    content = (
      <EmptyState
        title="尚無公開情資"
        description="平台目前沒有可見的活躍 IOC。來源同步完成後,統計會自動出現。"
      />
    );
  } else {
    const byType = Object.entries(summary.data.byType ?? {}).sort(([, a], [, b]) => b - a);
    const maxType = byType.length > 0 ? byType[0][1] : 1;
    const trend = (summary.data.trend ?? []).map((day) => ({
      date: (day.date ?? '').slice(5),
      count: day.count ?? 0,
    }));
    const healthy = (sources.data ?? []).filter((s) => s.status === 'ACTIVE').length;

    content = (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            icon={ShieldCheck}
            label="可見活躍 IOC"
            value={(summary.data.totalActive ?? 0).toLocaleString()}
            hint="匿名可見(public TLP:CLEAR)"
          />
          <StatCard icon={Layers} label="IOC 型別數" value={String(byType.length)} />
          <StatCard
            icon={Activity}
            label="近 7 日新觀測"
            value={trend.reduce((sum, day) => sum + day.count, 0).toLocaleString()}
          />
          <StatCard
            icon={Database}
            label="情資來源"
            value={`${healthy}/${sources.data?.length ?? 0}`}
            hint="健康來源 / 全部來源"
          />
        </div>

        <div className="grid gap-4 lg:grid-cols-5">
          <Card className="lg:col-span-3">
            <CardHeader>
              <CardTitle>近 7 日觀測趨勢</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="h-56 w-full" data-testid="trend-chart">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={trend} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                    <defs>
                      <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="var(--primary)" stopOpacity={0.35} />
                        <stop offset="100%" stopColor="var(--primary)" stopOpacity={0.02} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
                    <XAxis
                      dataKey="date"
                      tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }}
                      tickLine={false}
                      axisLine={{ stroke: 'var(--border)' }}
                    />
                    <YAxis
                      allowDecimals={false}
                      tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }}
                      tickLine={false}
                      axisLine={false}
                    />
                    <Tooltip
                      cursor={{ stroke: 'var(--ring)' }}
                      contentStyle={{
                        backgroundColor: 'var(--surface)',
                        border: '1px solid var(--border)',
                        borderRadius: 8,
                        color: 'var(--foreground)',
                        fontFamily: 'var(--font-mono)',
                        fontSize: 12,
                      }}
                    />
                    <Area
                      type="monotone"
                      dataKey="count"
                      name="觀測數"
                      stroke="var(--primary)"
                      strokeWidth={2}
                      fill="url(#trendFill)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>

          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>型別分布</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2.5">
              {byType.map(([type, count]) => (
                <div key={type} className="space-y-1">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-mono font-semibold">{type}</span>
                    <span className="font-mono tabular-nums text-muted-foreground">
                      {count.toLocaleString()}
                    </span>
                  </div>
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-primary"
                      style={{ width: `${Math.max(4, (count / maxType) * 100)}%` }}
                    />
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>來源健康</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="grid gap-2 sm:grid-cols-2" aria-label="來源清單">
              {(sources.data ?? []).map((source) => (
                <li
                  key={source.sourceId}
                  className="flex items-center justify-between gap-2 rounded-md border bg-muted/40 px-3 py-2"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{source.displayName}</p>
                    <p className="font-mono text-xs text-muted-foreground">
                      {(source.indicatorCount ?? 0).toLocaleString()} 筆觀測
                    </p>
                  </div>
                  <Badge
                    variant={
                      source.status === 'ACTIVE'
                        ? 'ok'
                        : source.status === 'DEGRADED'
                          ? 'warn'
                          : 'danger'
                    }
                  >
                    {source.status ?? '?'}
                  </Badge>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <section aria-labelledby="dashboard-title" className="space-y-4">
      <header>
        <h1 id="dashboard-title" className="font-mono text-xl font-bold tracking-tight">
          儀表板
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">公開情資統計總覽(匿名可存取)。</p>
      </header>
      {content}
    </section>
  );
}
