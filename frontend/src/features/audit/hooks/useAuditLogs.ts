import { useQuery } from '@tanstack/react-query';
import { ApiError, type PageOf } from '../../../api/client';
import { fetchAuditLogs } from '../api/auditApi';
import type { AuditLogDto, AuditLogPageParams } from '../types';

/** Query key 慣例(§12.3):['audit-logs', params]。 */
export function useAuditLogs(params: AuditLogPageParams = {}) {
  return useQuery<PageOf<AuditLogDto>, ApiError>({
    queryKey: ['audit-logs', params],
    queryFn: () => fetchAuditLogs(params),
  });
}
