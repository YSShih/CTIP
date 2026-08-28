import { apiGet, type ApiSchemas } from '../../../api/client';

/** 訂閱端點的薄包裝(§9.1「訂閱與 API Key」)。 */

export type SubscriptionDto = ApiSchemas['SubscriptionDto'];
export type SubscriptionUsageDto = ApiSchemas['SubscriptionUsageDto'];
export type PlanQuotasDto = ApiSchemas['PlanQuotasDto'];

export function fetchSubscription(): Promise<SubscriptionDto> {
  return apiGet('/api/v1/subscription');
}

export function fetchSubscriptionUsage(): Promise<SubscriptionUsageDto> {
  return apiGet('/api/v1/subscription/usage');
}
