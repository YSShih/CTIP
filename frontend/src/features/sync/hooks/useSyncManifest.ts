import { useQuery } from '@tanstack/react-query';
import { ApiError } from '../../../api/client';
import { fetchSyncManifest, type SyncManifestDto } from '../api/syncApi';

/** Query key 慣例(§12.3):['sync','manifest']。 */
export function useSyncManifest() {
  return useQuery<SyncManifestDto, ApiError>({
    queryKey: ['sync', 'manifest'],
    queryFn: fetchSyncManifest,
  });
}
