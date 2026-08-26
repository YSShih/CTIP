import { useQuery } from '@tanstack/react-query';
import { fetchIocDetail, fetchIocSources } from '../api/iocApi';

/** Query key 慣例(§12.3):['ioc', 'detail', id] 與 ['ioc', 'sources', id]。 */
export function useIocDetail(id: string) {
  return useQuery({
    queryKey: ['ioc', 'detail', id],
    queryFn: () => fetchIocDetail(id),
  });
}

export function useIocSources(id: string) {
  return useQuery({
    queryKey: ['ioc', 'sources', id],
    queryFn: () => fetchIocSources(id),
  });
}
