/**
 * 展示層 TLP 詞彙(07 §7.7 五級)。
 * 後端 openapi 將 tlp 宣告為自由 string,這裡僅定義前端呈現所需的已知等級;
 * 未知值一律走 fallback 呈現(TlpBadge),不重新定義後端型別。
 */
export const TLP_LEVELS = ['CLEAR', 'GREEN', 'AMBER', 'AMBER_STRICT', 'RED'] as const;

export type TlpLevel = (typeof TLP_LEVELS)[number];

export function parseTlp(value: string | null | undefined): TlpLevel | null {
  if (!value) return null;
  const upper = value.toUpperCase();
  return (TLP_LEVELS as readonly string[]).includes(upper) ? (upper as TlpLevel) : null;
}
