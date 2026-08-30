import {
  apiDeleteJson,
  apiGet,
  apiPatchJson,
  apiPostAction,
  type ApiSchemas,
} from '../../../api/client';

/**
 * 平台管理端點(docs/spec/09-api.md §9.1「管理」與 13 §13.4 的資料主體端點)。
 * 每一次呼叫在後端都會留下 `ADMIN_ACTION` 稽核(13 §13.5)。
 */
export type TenantOverviewDto = ApiSchemas['TenantOverviewDto'];
export type SubscriptionAssignmentDto = ApiSchemas['SubscriptionAssignmentDto'];
export type SourceAdminDto = ApiSchemas['SourceAdminDto'];
export type SourceSyncResultDto = ApiSchemas['SourceSyncResultDto'];
export type StixRebuildResultDto = ApiSchemas['StixRebuildResultDto'];
export type DataSubjectReportDto = ApiSchemas['DataSubjectReportDto'];
export type DataSubjectErasureDto = ApiSchemas['DataSubjectErasureDto'];

/** 由 generated schema 推導,不手寫:allowableValues 一改,呼叫端立刻在 tsc 失敗。 */
export type AssignablePlanCode = ApiSchemas['AssignPlanRequest']['planCode'];

export function fetchTenants(): Promise<TenantOverviewDto[]> {
  return apiGet('/api/v1/admin/tenants');
}

/** planCode 為 `CANCEL` 代表取消目前訂閱(不變量 B3:取消後不得回到 ACTIVE)。 */
export function assignPlan(
  tenantId: string,
  planCode: AssignablePlanCode,
): Promise<SubscriptionAssignmentDto> {
  return apiPatchJson(
    '/api/v1/admin/tenants/{id}/subscription',
    { planCode },
    { path: { id: tenantId } },
  );
}

export function syncSourceNow(sourceId: string): Promise<SourceSyncResultDto> {
  return apiPostAction('/api/v1/admin/sources/{id}/sync', { path: { id: sourceId } });
}

export function setSourceEnabled(sourceId: string, enabled: boolean): Promise<SourceAdminDto> {
  return apiPatchJson('/api/v1/admin/sources/{id}', { enabled }, { path: { id: sourceId } });
}

export function rebuildStix(): Promise<StixRebuildResultDto> {
  return apiPostAction('/api/v1/admin/stix/rebuild');
}

export function fetchDataSubject(userId: string): Promise<DataSubjectReportDto> {
  return apiGet('/api/v1/admin/data-subjects/{userId}', { path: { userId } });
}

export function eraseDataSubject(userId: string): Promise<DataSubjectErasureDto> {
  return apiDeleteJson('/api/v1/admin/data-subjects/{userId}', { path: { userId } });
}
