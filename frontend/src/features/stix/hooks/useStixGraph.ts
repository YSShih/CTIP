import { useQueries } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchStixObject } from '../api/stixApi';
import type { StixObject } from '../types';

/**
 * STIX 圖的資料來源(§12.6「節點展開」)。
 *
 * 起點是路由上的 stixId;每次展開就把該節點加進要抓的清單,由 react-query 各自快取
 * (query key 與 {@link import('./useStixObject')} 同一組 `['stix','object',id]`,詳情面板不會重抓)。
 *
 * <p>沒有反查端點:`GET /api/v1/stix/{stixId}` 只給單一物件,平台沒有
 * 「哪些 relationship 指向我」的查詢。因此圖的成長方向只能是**順著物件本身的參照往外走**。
 */
export function useStixGraph(rootId: string) {
  const [requested, setRequested] = useState<string[]>([rootId]);

  useEffect(() => {
    setRequested([rootId]);
  }, [rootId]);

  const { objects, isPending, isError, error, rootMissing } = useQueries({
    queries: requested.map((stixId) => ({
      queryKey: ['stix', 'object', stixId],
      queryFn: () => fetchStixObject(stixId),
      retry: false,
    })),
    combine: (results) => {
      const objects = new Map<string, StixObject | undefined>();
      requested.forEach((stixId, index) => objects.set(stixId, results[index]?.data));
      const root = results[0];
      return {
        objects,
        isPending: root?.isPending ?? true,
        isError: results.some((result) => result.isError),
        error: results.find((result) => result.isError)?.error ?? null,
        rootMissing: root?.isError ?? false,
      };
    },
  });

  const expand = useCallback((stixId: string) => {
    setRequested((current) => (current.includes(stixId) ? current : [...current, stixId]));
  }, []);

  const expanded = useMemo(() => new Set(requested), [requested]);

  return { objects, expand, expanded, isPending, isError, error, rootMissing };
}
