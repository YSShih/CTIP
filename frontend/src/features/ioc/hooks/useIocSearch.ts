import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';
import { fetchIocPage, type IocFilters } from '../api/iocApi';

const FILTER_KEYS = ['q', 'type', 'severity', 'status', 'tlp'] as const;

function readFilters(params: URLSearchParams): IocFilters {
  return {
    q: params.get('q') ?? '',
    type: params.get('type') ?? '',
    severity: params.get('severity') ?? '',
    status: params.get('status') ?? '',
    tlp: params.get('tlp') ?? '',
  };
}

/**
 * §12.3:已送出的搜尋條件與 cursor 一律存 URL search params(可分享、重新整理保留);
 * server 資料在 TanStack Query。Query key 慣例:['ioc', 'list', { filters, cursor }]。
 */
export function useIocSearch() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(() => readFilters(searchParams), [searchParams]);
  const cursor = searchParams.get('cursor') ?? undefined;

  const query = useQuery({
    queryKey: ['ioc', 'list', { filters, cursor: cursor ?? null }],
    queryFn: () => fetchIocPage(filters, cursor),
    placeholderData: keepPreviousData,
  });

  /** 套用新條件:寫入 URL 並重置 cursor(新條件從第一頁開始)。 */
  const applyFilters = useCallback(
    (next: IocFilters) => {
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

  return { filters, cursor, query, applyFilters, goToCursor, backToFirstPage };
}
