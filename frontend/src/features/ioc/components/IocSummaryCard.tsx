import { Badge } from '../../../components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '../../../components/ui/card';
import { Separator } from '../../../components/ui/separator';
import { TlpBadge } from '../../../components/TlpBadge/TlpBadge';
import type { IocDto } from '../types';

export interface IocSummaryCardProps {
  ioc: IocDto;
}

function formatInstant(value: string | undefined): string {
  return value ? value.replace('T', ' ').replace(/(\.\d+)?Z$/, ' UTC') : '—';
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="font-mono text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
        {label}
      </dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}

/** IOC 詳情摘要卡(§3.5.4);每個 IOC 顯示處必有 TlpBadge(§12.6 #1)。 */
export function IocSummaryCard({ ioc }: IocSummaryCardProps) {
  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4">
        <div className="min-w-0">
          <CardTitle>Indicator of Compromise</CardTitle>
          <p className="mt-1 break-all font-mono text-lg font-semibold">{ioc.value}</p>
        </div>
        <TlpBadge tlp={ioc.tlp} className="shrink-0" />
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3 lg:grid-cols-4">
          <Field label="型別">
            <Badge variant="muted">{ioc.type ?? '?'}</Badge>
            {ioc.hashType ? <span className="ml-2 font-mono text-xs">{ioc.hashType}</span> : null}
          </Field>
          <Field label="狀態">
            <Badge variant={ioc.status === 'ACTIVE' ? 'ok' : 'muted'}>{ioc.status ?? '?'}</Badge>
          </Field>
          <Field label="嚴重度">{ioc.severity ?? '—'}</Field>
          <Field label="威脅分數">
            <span className="font-mono tabular-nums">{ioc.score ?? '—'}</span>
            <span className="text-muted-foreground"> / 100</span>
          </Field>
          <Field label="信心值">
            <span className="font-mono tabular-nums">{ioc.confidence ?? '—'}</span>
          </Field>
          <Field label="來源數">
            <span className="font-mono tabular-nums">{ioc.sourceCount ?? '—'}</span>
          </Field>
          <Field label="首次觀測">
            <span className="font-mono text-xs">{formatInstant(ioc.firstSeen)}</span>
          </Field>
          <Field label="最後觀測">
            <span className="font-mono text-xs">{formatInstant(ioc.lastSeen)}</span>
          </Field>
          <Field label="有效期限">
            <span className="font-mono text-xs">{formatInstant(ioc.validUntil)}</span>
          </Field>
        </dl>
        {ioc.tags && ioc.tags.length > 0 ? (
          <>
            <Separator className="my-4" />
            <div className="flex flex-wrap gap-1.5" aria-label="標籤">
              {ioc.tags.map((tag) => (
                <Badge key={tag} variant="outline">
                  {tag}
                </Badge>
              ))}
            </div>
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}
