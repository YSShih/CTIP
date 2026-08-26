import { useQuery } from '@tanstack/react-query';
import { fetchStixObject } from '../api/stixApi';

/** Query key 慣例(§12.3 / §3.5.2 stixObject):['stix', 'object', stixId]。 */
export function useStixObject(stixId: string) {
  return useQuery({
    queryKey: ['stix', 'object', stixId],
    queryFn: () => fetchStixObject(stixId),
  });
}
