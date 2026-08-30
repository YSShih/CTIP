import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../../api/client';
import {
  assignPlan,
  eraseDataSubject,
  fetchDataSubject,
  fetchTenants,
  rebuildStix,
  setSourceEnabled,
  syncSourceNow,
  type AssignablePlanCode,
  type DataSubjectErasureDto,
  type DataSubjectReportDto,
  type SourceAdminDto,
  type SourceSyncResultDto,
  type StixRebuildResultDto,
  type SubscriptionAssignmentDto,
  type TenantOverviewDto,
} from '../api/adminApi';

/** Query key 慣例(§12.3):['admin', <resource>, ...]。 */
export function useTenants() {
  return useQuery<TenantOverviewDto[], ApiError>({
    queryKey: ['admin', 'tenants'],
    queryFn: fetchTenants,
  });
}

export function useAssignPlan() {
  const queryClient = useQueryClient();
  return useMutation<
    SubscriptionAssignmentDto,
    ApiError,
    { tenantId: string; planCode: AssignablePlanCode }
  >({
    mutationFn: ({ tenantId, planCode }) => assignPlan(tenantId, planCode),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'tenants'] });
    },
  });
}

export function useSyncSource() {
  const queryClient = useQueryClient();
  return useMutation<SourceSyncResultDto, ApiError, string>({
    mutationFn: syncSourceNow,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['sources'] });
    },
  });
}

export function useSetSourceEnabled() {
  const queryClient = useQueryClient();
  return useMutation<SourceAdminDto, ApiError, { sourceId: string; enabled: boolean }>({
    mutationFn: ({ sourceId, enabled }) => setSourceEnabled(sourceId, enabled),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['sources'] });
    },
  });
}

export function useRebuildStix() {
  return useMutation<StixRebuildResultDto, ApiError, void>({ mutationFn: rebuildStix });
}

export function useDataSubject() {
  return useMutation<DataSubjectReportDto, ApiError, string>({ mutationFn: fetchDataSubject });
}

export function useEraseDataSubject() {
  return useMutation<DataSubjectErasureDto, ApiError, string>({ mutationFn: eraseDataSubject });
}
