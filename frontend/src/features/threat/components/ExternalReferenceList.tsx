import { ExternalLink } from 'lucide-react';
import { Badge } from '../../../components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '../../../components/ui/card';
import type { ExternalReferenceDto } from '../types';

export interface ExternalReferenceListProps {
  references: ExternalReferenceDto[];
}

/** 外部參照是使用者輸入的半信任面:只有 http/https 才渲染成連結(阻擋 javascript: 等)。 */
function safeUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  return /^https?:\/\//i.test(url) ? url : null;
}

/** 外部參照(04 表 21):MITRE ATT&CK、CVE 等。 */
export function ExternalReferenceList({ references }: ExternalReferenceListProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>外部參照</CardTitle>
      </CardHeader>
      <CardContent>
        {references.length === 0 ? (
          <p className="text-sm text-muted-foreground">尚未登錄外部參照。</p>
        ) : (
          <ul className="space-y-2" aria-label="外部參照">
            {references.map((reference) => {
              const href = safeUrl(reference.url);
              return (
                <li
                  key={`${reference.sourceName}-${reference.externalId ?? reference.url ?? ''}`}
                  className="flex flex-wrap items-center gap-2 text-sm"
                >
                  <Badge variant="muted">{reference.sourceName}</Badge>
                  {reference.externalId ? (
                    <span className="font-mono text-[13px]">{reference.externalId}</span>
                  ) : null}
                  {reference.description ? (
                    <span className="text-muted-foreground">{reference.description}</span>
                  ) : null}
                  {href ? (
                    <a
                      href={href}
                      target="_blank"
                      rel="noreferrer noopener"
                      className="inline-flex items-center gap-1 text-xs hover:underline"
                    >
                      {href}
                      <ExternalLink aria-hidden className="size-3" />
                    </a>
                  ) : null}
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
