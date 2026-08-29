import { useQuery } from '@tanstack/react-query';
import { fetchThreat, fetchThreatIndicators } from '../api/threatApi';

/** Query key 慣例(§12.3):['threat', 'detail', id] 與 ['threat', 'indicators', id]。 */
export function useThreatDetail(id: string) {
  return useQuery({
    queryKey: ['threat', 'detail', id],
    queryFn: () => fetchThreat(id),
  });
}

export function useThreatIndicators(id: string) {
  return useQuery({
    queryKey: ['threat', 'indicators', id],
    queryFn: () => fetchThreatIndicators(id),
  });
}
