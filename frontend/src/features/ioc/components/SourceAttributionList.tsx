import { ExternalLink } from 'lucide-react';
import { Badge } from '../../../components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '../../../components/ui/card';
import type { AttributionDto, IocSourceDto } from '../types';

export interface SourceAttributionListProps {
  attribution: AttributionDto[];
  sources: IocSourceDto[];
  sourcesError?: boolean;
  onRetrySources?: () => void;
}

/** 來源登錄資料屬半信任面:homepage 僅在 http/https scheme 時渲染為連結(阻擋 javascript: 等)。 */
function safeHomepage(homepage: string | null | undefined): string | null {
  if (!homepage) return null;
  return /^https?:\/\//i.test(homepage) ? homepage : null;
}

/**
 * §12.6 #2:ATTRIBUTION_REQUIRED 的資料必須顯示 attribution(來源名稱與連結)。
 * sources 為每來源觀測明細(再散布政策遮罩後可能為空,§7.9 規則 5);
 * 明細查詢失敗時必須顯示錯誤而非靜默留空(§12.6 #4)。
 */
export function SourceAttributionList({
  attribution,
  sources,
  sourcesError = false,
  onRetrySources,
}: SourceAttributionListProps) {
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
                {safeHomepage(entry.homepage) ? (
                  <a
                    href={safeHomepage(entry.homepage) ?? undefined}
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

        {sourcesError ? (
          <p role="alert" className="text-sm text-destructive">
            來源觀測明細載入失敗。
            {onRetrySources ? (
              <button
                type="button"
                onClick={onRetrySources}
                className="ml-2 underline underline-offset-4"
              >
                重試
              </button>
            ) : null}
          </p>
        ) : sources.length > 0 ? (
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
