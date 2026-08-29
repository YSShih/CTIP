import type { ApiSchemas, PageOf } from '../../api/client';
import type { paths } from '../../api/generated/schema';

/** §12.4:僅 re-export 與窄化 generated 型別,不得重新定義後端型別。 */

export type ThreatDto = ApiSchemas['ThreatDto'];
export type ThreatIndicatorDto = ApiSchemas['ThreatIndicatorDto'];
export type ExternalReferenceDto = ApiSchemas['ExternalReferenceDto'];
export type ThreatPage = PageOf<ThreatDto>;

type ListQuery = NonNullable<paths['/api/v1/threats']['get']['parameters']['query']>;

export type ThreatTypeParam = NonNullable<ListQuery['type']>;
export type ThreatStatusParam = NonNullable<ListQuery['status']>;
export type SeverityParam = NonNullable<ListQuery['severity']>;
export type TlpParam = NonNullable<ListQuery['tlp']>;

/** 篩選選單的選項值:satisfies 讓後端列舉演進時在 tsc 就爆(值仍源自 generated union)。 */
export const THREAT_TYPE_OPTIONS = [
  'CAMPAIGN',
  'MALWARE_FAMILY',
  'THREAT_ACTOR',
  'ATTACK_PATTERN',
  'PHISHING_KIT',
] as const satisfies readonly ThreatTypeParam[];

export const THREAT_STATUS_OPTIONS = [
  'ACTIVE',
  'DORMANT',
  'RETIRED',
] as const satisfies readonly ThreatStatusParam[];

export const SEVERITY_OPTIONS = [
  'INFO',
  'LOW',
  'MEDIUM',
  'HIGH',
  'CRITICAL',
] as const satisfies readonly SeverityParam[];

export const TLP_OPTIONS = [
  'CLEAR',
  'GREEN',
  'AMBER',
  'AMBER_STRICT',
  'RED',
] as const satisfies readonly TlpParam[];

/** 只有這兩型在 M2 有 STIX SDO(07 §7.8.1);詳情頁據此決定要不要顯示 STIX 投影。 */
export function stixIdOf(threat: ThreatDto): string | null {
  if (threat.type === 'MALWARE_FAMILY') return `malware--${threat.id}`;
  if (threat.type === 'ATTACK_PATTERN') return `attack-pattern--${threat.id}`;
  return null;
}
