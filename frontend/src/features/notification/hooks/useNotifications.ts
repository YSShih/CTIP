import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, type PageOf } from '../../../api/client';
import {
  fetchNotifications,
  markNotificationRead,
  type NotificationDto,
  type NotificationPageParams,
} from '../api/notificationApi';

/** Query key 慣例(§12.3):['notifications', params]。 */
export function useNotifications(params: NotificationPageParams = {}) {
  return useQuery<PageOf<NotificationDto>, ApiError>({
    queryKey: ['notifications', params],
    queryFn: () => fetchNotifications(params),
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation<void, ApiError, string>({
    mutationFn: markNotificationRead,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}
