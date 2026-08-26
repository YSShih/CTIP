import { useQuery } from '@tanstack/react-query';
import { apiGet, type ApiSchemas } from '../api/client';

/** Dashboard 統計(§12.3 Query key 慣例:['stats', ...];跨 feature 共用故置於 hooks/)。 */

export function useStatsSummary() {
  return useQuery({
    queryKey: ['stats', 'summary'],
    queryFn: async (): Promise<ApiSchemas['StatsSummaryDto']> => apiGet('/api/v1/stats/summary'),
  });
}

export function useSourceStats() {
  return useQuery({
    queryKey: ['stats', 'sources'],
    queryFn: async (): Promise<ApiSchemas['SourceStatsDto'][]> => apiGet('/api/v1/stats/sources'),
  });
}
