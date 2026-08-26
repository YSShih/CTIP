import type { ApiSchemas, PageOf } from '../../api/client';
import type { paths } from '../../api/generated/schema';

/** §12.4:僅 re-export 與窄化 generated 型別,不得重新定義後端型別。 */

export type IocDto = ApiSchemas['IocDto'];
export type IocSourceDto = ApiSchemas['IocSourceDto'];
export type AttributionDto = ApiSchemas['AttributionDto'];
export type IocPage = PageOf<IocDto>;

type ListQuery = NonNullable<paths['/api/v1/iocs']['get']['parameters']['query']>;

export type IocTypeParam = NonNullable<ListQuery['type']>;
export type SeverityParam = NonNullable<ListQuery['severity']>;
export type StatusParam = NonNullable<ListQuery['status']>;
export type TlpParam = NonNullable<ListQuery['tlp']>;

/** 篩選選單的選項值:satisfies 讓後端列舉演進時在 tsc 就爆(值仍源自 generated union)。 */
export const TYPE_OPTIONS = [
  'IPV4',
  'IPV6',
  'DOMAIN',
  'URL',
  'FILE_HASH',
  'EMAIL',
] as const satisfies readonly IocTypeParam[];

export const SEVERITY_OPTIONS = [
  'INFO',
  'LOW',
  'MEDIUM',
  'HIGH',
  'CRITICAL',
] as const satisfies readonly SeverityParam[];

export const STATUS_OPTIONS = [
  'ACTIVE',
  'EXPIRED',
  'REVOKED',
  'FALSE_POSITIVE',
] as const satisfies readonly StatusParam[];

export const TLP_OPTIONS = [
  'CLEAR',
  'GREEN',
  'AMBER',
  'AMBER_STRICT',
  'RED',
] as const satisfies readonly TlpParam[];
