import { useQuery } from '@tanstack/react-query';
import { ApiError } from '../../../api/client';
import {
  fetchSubscription,
  fetchSubscriptionUsage,
  type SubscriptionDto,
  type SubscriptionUsageDto,
} from '../api/subscriptionApi';

/** Query key 慣例(§12.3):['subscription'] / ['subscription','usage']。 */
export function useSubscription() {
  return useQuery<SubscriptionDto, ApiError>({
    queryKey: ['subscription'],
    queryFn: fetchSubscription,
  });
}

export function useSubscriptionUsage() {
  return useQuery<SubscriptionUsageDto, ApiError>({
    queryKey: ['subscription', 'usage'],
    queryFn: fetchSubscriptionUsage,
  });
}
