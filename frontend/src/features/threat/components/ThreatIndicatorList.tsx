import { Link } from 'react-router';
import { TlpBadge } from '../../../components/TlpBadge/TlpBadge';
import { Badge } from '../../../components/ui/badge';
import { Button } from '../../../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../../components/ui/card';
import type { ThreatIndicatorDto } from '../types';

export interface ThreatIndicatorListProps {
  links: ThreatIndicatorDto[];
  /** 威脅上記錄的關聯總數;與 links.length 的差額是「你看不到的那些」 */
  totalCount: number;
  isError?: boolean;
  onRetry?: () => void;
}

/**
 * 關聯的 IOC(§9.1 `GET /threats/{id}/indicators`)。
 *
 * 後端只回呼叫者看得到的 IOC(07 §7.7:關聯不是可見度的旁路),因此清單可能比
 * {@code indicatorCount} 短。**差額必須明說**(§12.6 #4:不得靜默留白,
 * 否則使用者會以為這個威脅只關聯到這幾筆)。
 */
export function ThreatIndicatorList({
  links,
  totalCount,
  isError = false,
  onRetry,
}: ThreatIndicatorListProps) {
  const hidden = Math.max(0, totalCount - links.length);
  return (
    <Card>
      <CardHeader>
        <CardTitle>關聯的 IOC</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {isError ? (
          <div className="flex items-center gap-3 text-sm text-muted-foreground">
            <span>關聯清單載入失敗。</span>
            {onRetry ? (
              <Button type="button" variant="ghost" onClick={onRetry}>
                重試
              </Button>
            ) : null}
          </div>
        ) : null}
        {!isError && links.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            {totalCount === 0
              ? '這個威脅目前沒有關聯任何 IOC。'
              : '這個威脅的關聯 IOC 目前都不在你的可見範圍內。'}
          </p>
        ) : null}
        {links.length > 0 ? (
          <ul className="divide-y" aria-label="關聯的 IOC">
            {links.map((link) => (
              <li key={link.ioc?.id ?? ''} className="flex items-center gap-3 py-2 text-sm">
                <Badge variant="muted">{link.role ?? 'UNKNOWN'}</Badge>
                <Link
                  to={`/iocs/${link.ioc?.id ?? ''}`}
                  className="min-w-0 flex-1 truncate font-mono text-[13px] hover:underline"
                >
                  {link.ioc?.value}
                </Link>
                <Badge variant="outline">{link.ioc?.type ?? '?'}</Badge>
                <TlpBadge tlp={link.ioc?.tlp} />
              </li>
            ))}
          </ul>
        ) : null}
        {hidden > 0 && !isError ? (
          <p className="text-xs text-muted-foreground">
            另有 <span className="font-mono tabular-nums">{hidden}</span> 筆關聯的 IOC
            不在你的可見範圍內(TLP 或再散布政策);登入後可見範圍可能不同。
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}
