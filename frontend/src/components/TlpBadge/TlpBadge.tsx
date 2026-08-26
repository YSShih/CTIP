import { parseTlp, type TlpLevel } from '../../types/tlp';
import { cn } from '../../utils/cn';

export interface TlpBadgeProps {
  /** 後端 openapi 的 tlp 是自由 string;未知值走中性 fallback,不得空白 */
  tlp: string | null | undefined;
  className?: string;
}

const STYLES: Record<TlpLevel, string> = {
  CLEAR: 'border-tlp-clear-border bg-tlp-clear text-tlp-clear-foreground',
  GREEN: 'border-transparent bg-tlp-green text-tlp-green-foreground',
  AMBER: 'border-transparent bg-tlp-amber text-tlp-amber-foreground',
  AMBER_STRICT:
    'border-tlp-amber-foreground/60 border-dashed bg-tlp-amber text-tlp-amber-foreground',
  RED: 'border-transparent bg-tlp-red text-tlp-red-foreground',
};

const LABELS: Record<TlpLevel, string> = {
  CLEAR: 'TLP:CLEAR',
  GREEN: 'TLP:GREEN',
  AMBER: 'TLP:AMBER',
  AMBER_STRICT: 'TLP:AMBER+STRICT',
  RED: 'TLP:RED',
};

/** §12.6 #1:每個 IOC 顯示處必須有 TlpBadge;顏色不作唯一資訊載體(文字恆顯示)。 */
export function TlpBadge({ tlp, className }: TlpBadgeProps) {
  const level = parseTlp(tlp);
  const label = level ? LABELS[level] : `TLP:${tlp ?? '?'}`;
  return (
    <span
      aria-label={label}
      className={cn(
        'inline-flex items-center rounded-sm border px-1.5 py-0.5 font-mono text-[11px] font-semibold tracking-wider',
        level ? STYLES[level] : 'border-border bg-muted text-muted-foreground',
        className,
      )}
    >
      {label}
    </span>
  );
}
