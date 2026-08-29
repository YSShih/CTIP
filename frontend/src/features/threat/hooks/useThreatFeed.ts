import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';
import { emptyThreatFilters, fetchThreatPage, type ThreatFilters } from '../api/threatApi';

const FILTER_KEYS = ['name', 'type', 'status', 'severity', 'tlp'] as const;

function readFilters(params: URLSearchParams): ThreatFilters {
  return {
    name: params.get('name') ?? '',
    type: params.get('type') ?? '',
    status: params.get('status') ?? '',
    severity: params.get('severity') ?? '',
    tlp: params.get('tlp') ?? '',
  };
}

/**
 * §12.3:已送出的條件與 cursor 一律存 URL search params(可分享、重新整理保留);
 * server 資料在 TanStack Query。Query key 慣例:['threat', 'list', { filters, cursor }]。
 */
export function useThreatFeed() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(() => readFilters(searchParams), [searchParams]);
  const cursor = searchParams.get('cursor') ?? undefined;

  const query = useQuery({
    queryKey: ['threat', 'list', { filters, cursor: cursor ?? null }],
    queryFn: () => fetchThreatPage(filters, cursor),
    placeholderData: keepPreviousData,
  });

  /** 套用新條件:寫入 URL 並重置 cursor(新條件從第一頁開始)。 */
  const applyFilters = useCallback(
    (next: ThreatFilters) => {
      const params = new URLSearchParams();
      for (const key of FILTER_KEYS) {
        if (next[key] !== '') {
          params.set(key, next[key]);
        }
      }
      setSearchParams(params);
    },
    [setSearchParams],
  );

  const goToCursor = useCallback(
    (nextCursor: string) => {
      setSearchParams((previous) => {
        const params = new URLSearchParams(previous);
        params.set('cursor', nextCursor);
        return params;
      });
    },
    [setSearchParams],
  );

  const backToFirstPage = useCallback(() => {
    setSearchParams((previous) => {
      const params = new URLSearchParams(previous);
      params.delete('cursor');
      return params;
    });
  }, [setSearchParams]);

  return { filters, cursor, query, applyFilters, goToCursor, backToFirstPage, emptyThreatFilters };
}
