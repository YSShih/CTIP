import { ExternalLink } from 'lucide-react';
import { Badge } from '../../../components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '../../../components/ui/card';
import type { AttributionDto, IocSourceDto } from '../types';

export interface SourceAttributionListProps {
  attribution: AttributionDto[];
  sources: IocSourceDto[];
}

/**
 * §12.6 #2:ATTRIBUTION_REQUIRED 的資料必須顯示 attribution(來源名稱與連結)。
 * sources 為每來源觀測明細(再散布政策遮罩後可能為空,§7.9 規則 5)。
 */
export function SourceAttributionList({ attribution, sources }: SourceAttributionListProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>來源歸屬</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {attribution.length > 0 ? (
          <ul className="space-y-1" aria-label="attribution">
            {attribution.map((entry) => (
              <li key={entry.sourceName} className="flex items-center gap-2 text-sm">
                <span className="font-medium">{entry.sourceName}</span>
                {entry.homepage ? (
                  <a
                    href={entry.homepage}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="inline-flex items-center gap-1 text-primary underline underline-offset-4"
                  >
                    來源連結
                    <ExternalLink aria-hidden className="size-3.5" />
                  </a>
                ) : null}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-muted-foreground">
            此 IOC 無需標示的來源歸屬,或來源明細依再散布政策(TLP 2.0)遮罩。
          </p>
        )}

        {sources.length > 0 ? (
          <ul className="space-y-2" aria-label="來源觀測明細">
            {sources.map((source) => (
              <li
                key={`${source.sourceId}-${source.sourceFirstSeen}`}
                className="flex flex-wrap items-center gap-2 rounded-md border bg-muted/40 px-3 py-2 text-sm"
              >
                <span className="font-medium">{source.sourceName}</span>
                <Badge variant="muted">{source.redistributionPolicy ?? '?'}</Badge>
                <span className="font-mono text-xs text-muted-foreground">
                  信心 {source.sourceConfidence ?? '—'} · 回報 {source.reportCount ?? 0} 次 · 狀態{' '}
                  {source.status ?? '—'}
                </span>
              </li>
            ))}
          </ul>
        ) : null}
      </CardContent>
    </Card>
  );
}
