import { TlpBadge } from '../../../components/TlpBadge/TlpBadge';
import { Badge } from '../../../components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '../../../components/ui/card';
import { Separator } from '../../../components/ui/separator';
import type { ThreatDto } from '../types';

export interface ThreatSummaryCardProps {
  threat: ThreatDto;
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

/** 威脅詳情摘要卡;每個顯示處必有 TlpBadge(§12.6 #1)。 */
export function ThreatSummaryCard({ threat }: ThreatSummaryCardProps) {
  const aliases = threat.aliases ?? [];
  const tags = threat.tags ?? [];
  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4">
        <div className="min-w-0">
          <CardTitle>Threat</CardTitle>
          <p className="mt-1 break-all font-mono text-lg font-semibold">{threat.name}</p>
        </div>
        <TlpBadge tlp={threat.tlp} className="shrink-0" />
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3 lg:grid-cols-4">
          <Field label="型別">
            <Badge variant="muted">{threat.type ?? '?'}</Badge>
          </Field>
          <Field label="狀態">
            <Badge variant={threat.status === 'ACTIVE' ? 'ok' : 'muted'}>
              {threat.status ?? '?'}
            </Badge>
          </Field>
          <Field label="嚴重度">{threat.severity ?? '—'}</Field>
          <Field label="信心值">
            <span className="font-mono tabular-nums">{threat.confidence ?? '—'}</span>
          </Field>
          <Field label="關聯 IOC 數">
            <span className="font-mono tabular-nums">{threat.indicatorCount ?? 0}</span>
          </Field>
          <Field label="首次觀測">
            <span className="font-mono text-xs">{formatInstant(threat.firstSeen)}</span>
          </Field>
          <Field label="最後觀測">
            <span className="font-mono text-xs">{formatInstant(threat.lastSeen)}</span>
          </Field>
        </dl>
        {threat.description ? (
          <>
            <Separator className="my-4" />
            <p className="text-sm text-muted-foreground">{threat.description}</p>
          </>
        ) : null}
        {aliases.length > 0 ? (
          <>
            <Separator className="my-4" />
            <div className="flex flex-wrap items-center gap-1.5" aria-label="別名">
              <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
                別名
              </span>
              {aliases.map((alias) => (
                <Badge key={alias} variant="outline">
                  {alias}
                </Badge>
              ))}
            </div>
          </>
        ) : null}
        {tags.length > 0 ? (
          <div className="mt-3 flex flex-wrap gap-1.5" aria-label="標籤">
            {tags.map((tag) => (
              <Badge key={tag} variant="outline">
                {tag}
              </Badge>
            ))}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
