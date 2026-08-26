import { apiGet, apiPost } from '../../../api/client';
import type {
  IocDto,
  IocPage,
  IocSourceDto,
  IocTypeParam,
  SeverityParam,
  StatusParam,
  TlpParam,
} from '../types';

/** 已送出的搜尋條件(存於 URL search params,§12.3);空字串 = 不過濾。 */
export interface IocFilters {
  q: string;
  type: string;
  severity: string;
  status: string;
  tlp: string;
}

export const PAGE_SIZE = 50;

function orUndefined(value: string): string | undefined {
  return value === '' ? undefined : value;
}

/**
 * 有 q → POST /iocs/search(子字串搜尋);無 q → GET /iocs(純篩選清單)。
 * URL 來的字串直接交後端驗證(非法列舉值 → 400 INVALID_REQUEST,由 ErrorState 呈現)。
 */
export async function fetchIocPage(
  filters: IocFilters,
  cursor: string | undefined,
): Promise<IocPage> {
  if (filters.q !== '') {
    const page = await apiPost('/api/v1/iocs/search', {
      query: filters.q,
      type: orUndefined(filters.type),
      severity: orUndefined(filters.severity),
      status: orUndefined(filters.status),
      tlp: orUndefined(filters.tlp),
      cursor,
      limit: PAGE_SIZE,
    });
    return page as IocPage;
  }
  const page = await apiGet('/api/v1/iocs', {
    query: {
      type: orUndefined(filters.type) as IocTypeParam | undefined,
      severity: orUndefined(filters.severity) as SeverityParam | undefined,
      status: orUndefined(filters.status) as StatusParam | undefined,
      tlp: orUndefined(filters.tlp) as TlpParam | undefined,
      cursor,
      limit: PAGE_SIZE,
    },
  });
  return page as IocPage;
}

export async function fetchIocDetail(id: string): Promise<IocDto> {
  return apiGet('/api/v1/iocs/{id}', { path: { id } });
}

export async function fetchIocSources(id: string): Promise<IocSourceDto[]> {
  return apiGet('/api/v1/iocs/{id}/sources', { path: { id } });
}
