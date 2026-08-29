import { apiGet } from '../../../api/client';
import type {
  SeverityParam,
  ThreatDto,
  ThreatIndicatorDto,
  ThreatPage,
  ThreatStatusParam,
  ThreatTypeParam,
  TlpParam,
} from '../types';

/** 已送出的篩選條件(存於 URL search params,§12.3);空字串 = 不過濾。 */
export interface ThreatFilters {
  name: string;
  type: string;
  status: string;
  severity: string;
  tlp: string;
}

export const PAGE_SIZE = 50;

export const emptyThreatFilters: ThreatFilters = {
  name: '',
  type: '',
  status: '',
  severity: '',
  tlp: '',
};

function orUndefined(value: string): string | undefined {
  return value === '' ? undefined : value;
}

/** GET /threats;URL 來的字串直接交後端驗證(非法列舉值 → 400,由 ErrorState 呈現)。 */
export async function fetchThreatPage(
  filters: ThreatFilters,
  cursor: string | undefined,
): Promise<ThreatPage> {
  const page = await apiGet('/api/v1/threats', {
    query: {
      name: orUndefined(filters.name),
      type: orUndefined(filters.type) as ThreatTypeParam | undefined,
      status: orUndefined(filters.status) as ThreatStatusParam | undefined,
      severity: orUndefined(filters.severity) as SeverityParam | undefined,
      tlp: orUndefined(filters.tlp) as TlpParam | undefined,
      cursor,
      limit: PAGE_SIZE,
    },
  });
  return page as ThreatPage;
}

export async function fetchThreat(id: string): Promise<ThreatDto> {
  return apiGet('/api/v1/threats/{id}', { path: { id } });
}

/**
 * 關聯的 IOC。後端只回<strong>呼叫者看得到</strong>的那些(07 §7.7:關聯不是可見度的旁路),
 * 因此這個清單可能比 {@code threat.indicatorCount} 短——詳情頁必須把差額講清楚,
 * 不得讓使用者以為情資不見了。
 */
export async function fetchThreatIndicators(id: string): Promise<ThreatIndicatorDto[]> {
  return apiGet('/api/v1/threats/{id}/indicators', { path: { id } });
}
